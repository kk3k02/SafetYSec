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
import javax.inject.Inject
import kotlin.math.sqrt

@AndroidEntryPoint
class FallDetectionService : Service(), SensorEventListener {

    @Inject lateinit var alertRepo: AlertRepository

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // TEST MODE: Low threshold for easy triggering
    private val TEST_TRIGGER_THRESHOLD = 15.0f 
    private var isProcessing = false

    override fun onCreate() {
        super.onCreate()
        Log.d("FallDetection", "Service started in TEST MODE")
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        createNotificationChannel()
        startForeground(1, createNotification("Monitoring for falls (Test Mode active)"))
        
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)

        // If movement exceeds threshold and we aren't already showing a popup
        if (!isProcessing && magnitude > TEST_TRIGGER_THRESHOLD) {
            Log.i("FallDetection", "!!! TRIGGER !!! Magnitude: $magnitude")
            isProcessing = true
            
            serviceScope.launch {
                // This triggers the 10s countdown window on your PHONE SCREEN
                alertRepo.emitDetectionEvent(RuleType.FALL)
                
                // Pause detection for a while to let user handle the popup
                delay(15000)
                isProcessing = false
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        serviceScope.cancel()
        super.onDestroy()
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
