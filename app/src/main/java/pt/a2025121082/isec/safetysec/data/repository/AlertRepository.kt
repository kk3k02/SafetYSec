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
    // SharedFlow to communicate between Background Service and UI
    private val _detectionEvents = MutableSharedFlow<RuleType>(extraBufferCapacity = 1)
    val detectionEvents = _detectionEvents.asSharedFlow()

    /**
     * Notify that a sensor detected a potential alert (like a fall).
     * This will be picked up by the ViewModel to show the UI dialog.
     */
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
        // 1) Wait for 10s cancel window
        val cancelled = waitForCancel(user.alertCancelCode, cancelCodeProvider)
        if (cancelled) {
            Log.d("AlertRepository", "Alert cancelled by user.")
            return false
        }

        // 2) Send to Firestore
        val location = locationProvider()
        val alert = Alert(
            type = ruleType,
            protectedId = user.uid,
            protectedName = user.name,
            location = location
        )

        if (user.monitors.isEmpty()) return true

        user.monitors.forEach { monitorId ->
            firestore.collection("users").document(monitorId)
                .collection("alerts").document(alert.id).set(alert).await()
        }
        return true
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
