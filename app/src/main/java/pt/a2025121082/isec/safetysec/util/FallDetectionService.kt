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

/**
 * Foreground Service that monitors the accelerometer sensor to detect potential falls.
 * It uses a combination of free-fall detection and impact magnitude to trigger an alert.
 */
@AndroidEntryPoint
class FallDetectionService : Service(), SensorEventListener {

    @Inject lateinit var alertRepo: AlertRepository
    @Inject lateinit var authRepo: AuthRepository
    @Inject lateinit var monitoringRepo: MonitoringRepository

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Fall detection thresholds and constants
    private val fallImpactThreshold = 18.5f // Magnitude indicating a hard impact
    private val freeFallThreshold = 2.0f    // Magnitude indicating weightlessness/free fall
    private val freeFallWindowMs = 900L     // Time window after free fall to look for impact
    private val minFreeFallMs = 120L        // Minimum duration of weightlessness to count as free fall
    private val fallCooldownMs = 12_000L    // Delay between consecutive fall alerts
    
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

        // Required for foreground services in Android O and above
        createNotificationChannel()
        startForeground(1, createNotification("Monitoring for falls"))
        Log.d("FallDetection", "Foreground notification started")

        accelerometer?.let {
            val registered = sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Log.d("FallDetection", "Accelerometer listener registered=$registered")
        }
    }

    /**
     * Analyzes accelerometer data to identify fall patterns.
     * Logic: Look for a period of free fall followed by a high-magnitude impact.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val now = System.currentTimeMillis()

        // 1. Detect free fall (low acceleration)
        if (magnitude < freeFallThreshold) {
            if (freeFallStartAtMs == null) freeFallStartAtMs = now
            val start = freeFallStartAtMs
            if (start != null && now - start >= minFreeFallMs) {
                lastFreeFallAtMs = now // Track last moment of confirmed free fall
            }
        } else {
            freeFallStartAtMs = null
        }

        // Debug logging roughly once per second
        if (now - lastDebugLogAtMs >= 1000L) {
            Log.d(
                "FallDetection",
                "Accel magnitude=$magnitude impact=$fallImpactThreshold freeFall=$freeFallThreshold"
            )
            lastDebugLogAtMs = now
        }

        // 2. Detect impact (high acceleration) following a recent free fall
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
        serviceScope.cancel() // Stop background authorization checks
        super.onDestroy()
    }

    /**
     * Checks authorization and emits a fall event if the detection is valid.
     */
    private fun maybeTriggerFall() {
        val now = System.currentTimeMillis()
        
        // Prevent overlapping checks and respect cooldown
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
                // Verify if the user is authorized to be monitored at this time
                if (!isFallAuthorizedAndWithinWindow()) return@launch
                
                lastFallTriggerAtMs = System.currentTimeMillis()
                Log.i("FallDetection", "Fall detected and authorized")
                
                // Emit event to AlertRepository to notify monitors
                alertRepo.emitDetectionEvent(RuleType.FALL)
            } catch (e: Exception) {
                Log.e("FallDetection", "Fall trigger failed: ${e.message}")
            } finally {
                fallCheckInProgress = false
            }
        }
    }

    /**
     * Validates if fall detection should be active based on user role, 
     * explicit authorization from monitors, and active time windows.
     */
    private suspend fun isFallAuthorizedAndWithinWindow(): Boolean {
        val uid = authRepo.getCurrentUid() ?: run {
            Log.w("FallDetection", "Skip: no authenticated user")
            return false
        }
        
        // Check if user is in 'Protected' mode
        val me = authRepo.getUserProfile(uid)
        if (!me.roles.contains("Protected")) {
            Log.w("FallDetection", "Skip: not in Protected role")
            return false
        }

        // Check if at least one monitor has authorized 'FALL' detection
        val bundles = monitoringRepo.getRulesForProtected(uid)
        val fallAuthorized = bundles.any { it.authorizedTypes.contains(RuleType.FALL) }
        if (!fallAuthorized) {
            Log.w("FallDetection", "Skip: FALL not authorized")
            return false
        }

        // Check if current time falls within any defined monitoring windows
        val windows = monitoringRepo.listTimeWindows(uid)
        val withinWindow = isWithinAnyWindow(windows)
        if (!withinWindow) {
            Log.w("FallDetection", "Skip: outside time window")
        }
        return withinWindow
    }

    /**
     * Helper to check if current time is inside any of the provided time windows.
     */
    private fun isWithinAnyWindow(windows: List<TimeWindow>): Boolean {
        if (windows.isEmpty()) return true // No windows defined means active 24/7
        
        val cal = Calendar.getInstance()
        // Convert Calendar day to 1-7 (Mon-Sun) to match TimeWindow logic
        val day = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        
        return windows.any { window ->
            day in window.daysOfWeek && hour in window.startHour until window.endHour
        }
    }

    /**
     * Creates the notification channel for the foreground service.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "fall_detection", "SafetySec Monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /**
     * Builds the notification displayed while the service is running.
     */
    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "fall_detection")
            .setContentTitle("SafetYSec")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
}
