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
        locationProvider: suspend () -> GeoPoint?,
        videoUriProvider: suspend () -> Uri? // PROVIDER FOR RECORDED VIDEO
    ): Boolean {
        // 1) Wait for 10s cancel window
        val cancelled = waitForCancel(user.alertCancelCode, cancelCodeProvider)
        
        if (cancelled) {
            saveAlertToProtected(user.uid, Alert(type = ruleType, protectedId = user.uid, protectedName = user.name, status = "CANCELLED"))
            return false
        }

        // 2) Collect context
        val location = locationProvider()
        val videoUri = videoUriProvider()
        
        // 3) Upload video to Storage if exists
        val videoUrl = videoUri?.let { uploadVideo(user.uid, it) }

        val sentAlert = Alert(
            type = ruleType,
            protectedId = user.uid,
            protectedName = user.name,
            location = location,
            videoUrl = videoUrl,
            status = "SENT"
        )
        
        // 4) Save locally and to monitors
        saveAlertToProtected(user.uid, sentAlert)

        if (user.monitors.isNotEmpty()) {
            user.monitors.forEach { monitorId ->
                firestore.collection("users").document(monitorId)
                    .collection("alerts").document(sentAlert.id).set(sentAlert).await()
            }
        }
        return true
    }

    private suspend fun uploadVideo(uid: String, uri: Uri): String {
        val fileName = "alert_${uid}_${System.currentTimeMillis()}.mp4"
        val ref = storage.reference.child("alerts_videos/$fileName")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    private suspend fun saveAlertToProtected(uid: String, alert: Alert) {
        firestore.collection("users").document(uid)
            .collection("my_alerts").document(alert.id).set(alert).await()
    }

    suspend fun deleteAlertFromMonitor(monitorUid: String, alertId: String) {
        firestore.collection("users").document(monitorUid)
            .collection("alerts").document(alertId).delete().await()
    }

    suspend fun getProtectedAlertHistory(uid: String): List<Alert> {
        val snapshot = firestore.collection("users").document(uid)
            .collection("my_alerts")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get().await()
        return snapshot.toObjects(Alert::class.java)
    }

    private suspend fun waitForCancel(
        correctCode: String,
        cancelCodeProvider: suspend () -> String?
    ): Boolean {
        val maxMs = 10_000L
        val stepMs = 250L
        var waited = 0L
        while (waited < maxMs) {
            val typed = cancelCodeProvider()?.trim()
            if (!typed.isNullOrBlank() && typed == correctCode) return true
            delay(stepMs)
            waited += stepMs
        }
        return false
    }
}
