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

        val sentAlert = Alert(
            type = ruleType,
            protectedId = user.uid,
            protectedName = user.name,
            location = locationProvider(),
            status = "SENT"
        )
        
        // 1. Zapis u Protected (Źródło prawdy)
        saveAlertToProtected(user.uid, sentAlert)

        // 2. Rozsyłanie do Monitorów (Fan-out)
        val freshUserSnap = firestore.collection("users").document(user.uid).get().await()
        val monitorIds = freshUserSnap.get("monitors") as? List<String> ?: emptyList()

        monitorIds.forEach { monitorId ->
            try {
                firestore.collection("users").document(monitorId)
                    .collection("alerts").document(sentAlert.id)
                    .set(sentAlert).await()
            } catch (e: Exception) {
                Log.e("AlertRepo", "Fan-out failed for $monitorId: ${e.message}")
            }
        }
        return sentAlert.id
    }

    suspend fun updateAlertWithVideo(alertId: String, user: User, videoUri: Uri) {
        try {
            delay(1000)
            val videoFile = File(videoUri.path ?: return)
            if (!videoFile.exists()) return

            val bytes = videoFile.readBytes()
            val storageRef = storage.reference.child("alerts_videos/alert_${alertId}.mp4")
            storageRef.putBytes(bytes).await()
            val videoUrl = storageRef.downloadUrl.await().toString()

            val updates = mapOf("videoUrl" to videoUrl)
            
            // Aktualizacja u Protected
            firestore.collection("users").document(user.uid).collection("my_alerts").document(alertId).update(updates).await()
            
            // Aktualizacja u wszystkich Monitorów (natychmiastowe odświeżenie wideo)
            val freshUserSnap = firestore.collection("users").document(user.uid).get().await()
            val monitorIds = freshUserSnap.get("monitors") as? List<String> ?: emptyList()

            monitorIds.forEach { mid ->
                try {
                    firestore.collection("users").document(mid).collection("alerts").document(alertId).update(updates).await()
                } catch (e: Exception) { /* Alert might be dismissed */ }
            }
            videoFile.delete()
        } catch (e: Exception) {
            Log.e("AlertRepo", "Video Update FAILED: ${e.message}")
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
        repeat(40) {
            if (provider()?.trim() == code) return true
            delay(250)
        }
        return false
    }
}
