package pt.a2025121082.isec.safetysec.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import pt.a2025121082.isec.safetysec.data.model.Alert
import pt.a2025121082.isec.safetysec.data.model.RuleType
import pt.a2025121082.isec.safetysec.data.model.User
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    private val _detectionEvents = MutableSharedFlow<RuleType>(extraBufferCapacity = 1)
    val detectionEvents = _detectionEvents.asSharedFlow()

    suspend fun emitDetectionEvent(type: RuleType) {
        _detectionEvents.emit(type)
    }

    suspend fun triggerAlert(
        ruleType: RuleType,
        user: User,
        cancelCodeProvider: suspend () -> String?,
        locationProvider: suspend () -> GeoPoint?
    ): String? {
        val cancelled = waitForCancel(user.alertCancelCode, cancelCodeProvider)
        if (cancelled) {
            saveAlertToProtected(user.uid, Alert(type = ruleType, protectedId = user.uid, protectedName = user.name, status = "CANCELLED"))
            return null
        }

        val location = locationProvider()
        val sentAlert = Alert(
            type = ruleType,
            protectedId = user.uid,
            protectedName = user.name,
            location = location,
            status = "SENT"
        )
        saveAlertToProtected(user.uid, sentAlert)

        if (user.monitors.isNotEmpty()) {
            user.monitors.forEach { monitorId ->
                firestore.collection("users").document(monitorId)
                    .collection("alerts").document(sentAlert.id).set(sentAlert).await()
            }
        }
        return sentAlert.id
    }

    suspend fun updateAlertWithVideo(alertId: String, user: User, videoUri: Uri) {
        try {
            // Safe delay to ensure file is closed by CameraX
            delay(2000)
            
            val videoFile = File(videoUri.path ?: return)
            if (!videoFile.exists() || videoFile.length() <= 0) {
                Log.e("AlertRepo", "Video file not ready for upload")
                return
            }

            Log.d("AlertRepo", "Reading video bytes for upload...")
            val bytes = videoFile.readBytes()
            
            val fileName = "alert_${alertId}.mp4"
            val storageRef = storage.reference.child("alerts_videos/$fileName")

            Log.d("AlertRepo", "Starting upload to Firebase Storage...")
            storageRef.putBytes(bytes).await()
            val videoUrl = storageRef.downloadUrl.await().toString()

            // Update database
            val updates = mapOf("videoUrl" to videoUrl)
            firestore.collection("users").document(user.uid)
                .collection("my_alerts").document(alertId).update(updates).await()

            user.monitors.forEach { monitorId ->
                firestore.collection("users").document(monitorId)
                    .collection("alerts").document(alertId).update(updates).await()
            }
            Log.d("AlertRepo", "Video upload and DB link SUCCESS for alert: $alertId")
        } catch (e: Exception) {
            Log.e("AlertRepo", "CRITICAL ERROR: Video update failed", e)
        }
    }

    private suspend fun saveAlertToProtected(uid: String, alert: Alert) {
        firestore.collection("users").document(uid).collection("my_alerts").document(alert.id).set(alert).await()
    }

    suspend fun deleteAlertFromMonitor(monitorUid: String, alertId: String) {
        firestore.collection("users").document(monitorUid).collection("alerts").document(alertId).delete().await()
    }

    suspend fun getProtectedAlertHistory(uid: String): List<Alert> {
        val snap = firestore.collection("users").document(uid).collection("my_alerts")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING).get().await()
        return snap.toObjects(Alert::class.java)
    }

    private suspend fun waitForCancel(code: String, provider: suspend () -> String?): Boolean {
        val maxMs = 10_000L
        val stepMs = 500L
        var waited = 0L
        while (waited < maxMs) {
            val typed = provider()?.trim()
            if (!typed.isNullOrBlank() && typed == code) return true
            delay(stepMs)
            waited += stepMs
        }
        return false
    }
}
