package pt.a2025121082.isec.safetysec.util

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import pt.a2025121082.isec.safetysec.data.model.RuleType
import pt.a2025121082.isec.safetysec.data.repository.AlertRepository
import pt.a2025121082.isec.safetysec.data.repository.AuthRepository
import pt.a2025121082.isec.safetysec.data.repository.MonitoringRepository
import pt.a2025121082.isec.safetysec.data.model.TimeWindow
import javax.inject.Inject
import java.util.Calendar
import kotlin.math.sqrt

@AndroidEntryPoint
class FallDetectionService : Service(), SensorEventListener {

    @Inject lateinit var alertRepo: AlertRepository
    @Inject lateinit var authRepo: AuthRepository
    @Inject lateinit var monitoringRepo: MonitoringRepository

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val fallImpactThreshold = 18.5f
    private val freeFallThreshold = 2.0f
    private val freeFallWindowMs = 900L
    private val minFreeFallMs = 120L
    private val fallCooldownMs = 12_000L
    private var lastFallTriggerAtMs: Long = 0L
    private var fallCheckInProgress = false
    private var lastDebugLogAtMs: Long = 0L
    private var lastFreeFallAtMs: Long = 0L
    private var freeFallStartAtMs: Long? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("FallDetection", "Service started")
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            Log.w("FallDetection", "Accelerometer not available")
        } else {
            Log.d("FallDetection", "Accelerometer available")
        }

        createNotificationChannel()
        startForeground(1, createNotification("Monitoring for falls"))
        Log.d("FallDetection", "Foreground notification started")

        accelerometer?.let {
            val registered = sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Log.d("FallDetection", "Accelerometer listener registered=$registered")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val now = System.currentTimeMillis()
        if (magnitude < freeFallThreshold) {
            if (freeFallStartAtMs == null) freeFallStartAtMs = now
            val start = freeFallStartAtMs
            if (start != null && now - start >= minFreeFallMs) {
                lastFreeFallAtMs = now
            }
        } else {
            freeFallStartAtMs = null
        }
        if (now - lastDebugLogAtMs >= 1000L) {
            Log.d(
                "FallDetection",
                "Accel magnitude=$magnitude impact=$fallImpactThreshold freeFall=$freeFallThreshold"
            )
            lastDebugLogAtMs = now
        }

        val hasRecentFreeFall = now - lastFreeFallAtMs <= freeFallWindowMs
        if (magnitude > fallImpactThreshold && hasRecentFreeFall) {
            Log.d("FallDetection", "Fall candidate magnitude=$magnitude")
            maybeTriggerFall()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d("FallDetection", "Service stopped")
        sensorManager.unregisterListener(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun maybeTriggerFall() {
        val now = System.currentTimeMillis()
        if (fallCheckInProgress) {
            Log.d("FallDetection", "Skip: check in progress")
            return
        }
        if (now - lastFallTriggerAtMs < fallCooldownMs) {
            Log.d("FallDetection", "Skip: cooldown active")
            return
        }
        fallCheckInProgress = true

        serviceScope.launch(Dispatchers.IO) {
            try {
                if (!isFallAuthorizedAndWithinWindow()) return@launch
                lastFallTriggerAtMs = System.currentTimeMillis()
                Log.i("FallDetection", "Fall detected and authorized")
                alertRepo.emitDetectionEvent(RuleType.FALL)
            } catch (e: Exception) {
                Log.e("FallDetection", "Fall trigger failed: ${e.message}")
            } finally {
                fallCheckInProgress = false
            }
        }
    }

    private suspend fun isFallAuthorizedAndWithinWindow(): Boolean {
        val uid = authRepo.getCurrentUid() ?: run {
            Log.w("FallDetection", "Skip: no authenticated user")
            return false
        }
        val me = authRepo.getUserProfile(uid)
        if (!me.roles.contains("Protected")) {
            Log.w("FallDetection", "Skip: not in Protected role")
            return false
        }

        val bundles = monitoringRepo.getRulesForProtected(uid)
        val fallAuthorized = bundles.any { it.authorizedTypes.contains(RuleType.FALL) }
        if (!fallAuthorized) {
            Log.w("FallDetection", "Skip: FALL not authorized")
            return false
        }

        val windows = monitoringRepo.listTimeWindows(uid)
        val withinWindow = isWithinAnyWindow(windows)
        if (!withinWindow) {
            Log.w("FallDetection", "Skip: outside time window")
        }
        return withinWindow
    }

    private fun isWithinAnyWindow(windows: List<TimeWindow>): Boolean {
        if (windows.isEmpty()) return true
        val cal = Calendar.getInstance()
        val day = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return windows.any { window ->
            day in window.daysOfWeek && hour in window.startHour until window.endHour
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "fall_detection", "SafetySec Monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "fall_detection")
            .setContentTitle("SafetYSec")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }
}

