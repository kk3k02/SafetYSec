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
        videoUriProvider: suspend () -> Uri?
    ): Boolean {
        val alert = Alert(
            type = ruleType,
            protectedId = user.uid,
            protectedName = user.name,
            location = locationProvider(),
            status = "CANCELLED"
        )

        val cancelled = waitForCancel(user.alertCancelCode, cancelCodeProvider)
        
        if (cancelled) {
            saveAlertToProtected(user.uid, alert.copy(status = "CANCELLED"))
            return false
        }

        val sentAlert = alert.copy(status = "SENT")
        saveAlertToProtected(user.uid, sentAlert)

        if (user.monitors.isNotEmpty()) {
            user.monitors.forEach { monitorId ->
                firestore.collection("users").document(monitorId)
                    .collection("alerts").document(sentAlert.id).set(sentAlert).await()
            }
        }
        return true
    }

    private suspend fun saveAlertToProtected(uid: String, alert: Alert) {
        firestore.collection("users").document(uid)
            .collection("my_alerts").document(alert.id).set(alert).await()
    }

    /**
     * Removes the alert from the monitor's active alerts collection.
     * This ensures the alert won't pop up again after being dismissed.
     */
    suspend fun deleteAlertFromMonitor(monitorUid: String, alertId: String) {
        try {
            firestore.collection("users").document(monitorUid)
                .collection("alerts").document(alertId).delete().await()
        } catch (e: Exception) {
            Log.e("AlertRepository", "Failed to delete alert $alertId for monitor $monitorUid", e)
        }
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
