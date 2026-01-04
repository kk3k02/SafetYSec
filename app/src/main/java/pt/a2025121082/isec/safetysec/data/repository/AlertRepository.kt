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

/**
 * Repository responsible for managing emergency alerts and their associated media.
 * Handles triggering alerts, broadcasting them to monitors (fan-out), and uploading video evidence.
 */
@Singleton
class AlertRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    // Flow used to signal detection events (e.g., fall or speed) across the app
    private val _detectionEvents = MutableSharedFlow<RuleType>(extraBufferCapacity = 1)
    val detectionEvents = _detectionEvents.asSharedFlow()

    /**
     * Emits a new detection event that can trigger an alert process.
     */
    suspend fun emitDetectionEvent(type: RuleType) {
        _detectionEvents.emit(type)
    }

    /**
     * Triggers the alert lifecycle.
     * Includes a cancellation window where the user can enter a PIN to stop the alert.
     * If not cancelled, the alert is saved for the user and broadcast to all linked monitors.
     */
    suspend fun triggerAlert(
        ruleType: RuleType,
        user: User,
        cancelCodeProvider: suspend () -> String?,
        locationProvider: suspend () -> GeoPoint?
    ): String? {
        // Wait for user to potentially cancel the alert
        val cancelled = waitForCancel(user.alertCancelCode, cancelCodeProvider)
        if (cancelled) {
            saveAlertToProtected(user.uid, Alert(type = ruleType, protectedId = user.uid, protectedName = user.name, status = "CANCELLED"))
            return null
        }

        // Prepare the alert data
        val sentAlert = Alert(
            type = ruleType,
            protectedId = user.uid,
            protectedName = user.name,
            location = locationProvider(),
            status = "SENT"
        )

        // 1. Save to Protected user's history (Source of Truth)
        saveAlertToProtected(user.uid, sentAlert)

        // 2. Broadcast to Monitors (Fan-out pattern)
        val freshUserSnap = firestore.collection("users").document(user.uid).get().await()
        val monitorIds = (freshUserSnap.get("monitors") as? List<*>)
            ?.mapNotNull { it as? String }
            ?: emptyList()

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

    /**
     * Uploads a video file to Firebase Storage and updates the alert record with the download URL.
     * Updates both the protected user's history and all linked monitors' views.
     */
    suspend fun updateAlertWithVideo(alertId: String, user: User, videoUri: Uri) {
        try {
            delay(1000) // Small delay to ensure file readiness
            val videoFile = File(videoUri.path ?: return)
            if (!videoFile.exists()) return

            val bytes = videoFile.readBytes()
            val storageRef = storage.reference.child("alerts_videos/alert_${alertId}.mp4")
            storageRef.putBytes(bytes).await()
            val videoUrl = storageRef.downloadUrl.await().toString()

            val updates = mapOf("videoUrl" to videoUrl)

            // Update Protected user's record
            firestore.collection("users").document(user.uid).collection("my_alerts").document(alertId).update(updates).await()

            // Update records for all linked Monitors
            val freshUserSnap = firestore.collection("users").document(user.uid).get().await()
            val monitorIds = (freshUserSnap.get("monitors") as? List<*>)
                ?.mapNotNull { it as? String }
                ?: emptyList()

            monitorIds.forEach { mid ->
                try {
                    firestore.collection("users").document(mid).collection("alerts").document(alertId).update(updates).await()
                } catch (e: Exception) { /* Alert might have been dismissed by monitor */ }
            }
            videoFile.delete() // Clean up local file after upload
        } catch (e: Exception) {
            Log.e("AlertRepo", "Video Update FAILED: ${e.message}")
        }
    }

    /** Helper to save alert records to the Protected user's personal collection. */
    private suspend fun saveAlertToProtected(uid: String, alert: Alert) {
        firestore.collection("users").document(uid).collection("my_alerts").document(alert.id).set(alert).await()
    }

    /** Removes an alert from a Monitor's dashboard. */
    suspend fun deleteAlertFromMonitor(monitorUid: String, alertId: String) {
        firestore.collection("users").document(monitorUid).collection("alerts").document(alertId).delete().await()
    }

    /** Retrieves the full alert history for a Protected user, sorted by timestamp. */
    suspend fun getProtectedAlertHistory(uid: String): List<Alert> {
        val snap = firestore.collection("users").document(uid).collection("my_alerts")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING).get().await()
        return snap.toObjects(Alert::class.java)
    }

    /** Deletes all alert history records for a Protected user. */
    suspend fun clearProtectedAlertHistory(uid: String) {
        val snap = firestore.collection("users").document(uid).collection("my_alerts").get().await()
        if (snap.isEmpty) return
        val batch = firestore.batch()
        snap.documents.forEach { doc -> batch.delete(doc.reference) }
        batch.commit().await()
    }

    /**
     * Logic for the countdown/cancellation window.
     * Periodically checks if the user provided the correct PIN via the provider.
     */
    private suspend fun waitForCancel(code: String, provider: suspend () -> String?): Boolean {
        repeat(40) { // Approx 10 seconds (40 * 250ms)
            if (provider()?.trim() == code) return true
            delay(250)
        }
        return false
    }
}
