package pt.a2025121082.isec.safetysec.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import pt.a2025121082.isec.safetysec.data.model.Alert
import pt.a2025121082.isec.safetysec.data.model.RuleType
import pt.a2025121082.isec.safetysec.data.model.User
import javax.inject.Inject

/**
 * Repository responsible for alert handling:
 * - gives the Protected user a 10-second window to cancel the alert
 * - stores the alert under each linked Monitor in Firestore
 * - uploads an optional video to Firebase Storage (if a Uri is provided)
 */
class AlertRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    /**
     * Call this when a monitoring rule detects an event.
     *
     * @param ruleType The type of rule/event that triggered the alert.
     * @param user The Protected user who triggered the alert.
     *
     * @param cancelCodeProvider A suspend function that returns the code typed by the user
     * (or null if nothing has been typed yet). This allows the UI to show a dialog and provide
     * the entered PIN during the 10-second cancellation window.
     *
     * @param locationProvider A suspend function returning the current location as a GeoPoint
     * (e.g., from GPS). May return null if location is unavailable.
     *
     * @param videoUriProvider A suspend function returning a Uri pointing to a recorded video
     * (e.g., a 30-second clip). May return null if no recording was made.
     *
     * @return True if the alert was triggered and processed (even if no monitors are linked),
     * false if it was cancelled by the Protected user.
     */
    suspend fun triggerAlert(
        ruleType: RuleType,
        user: User,
        cancelCodeProvider: suspend () -> String?,          // UI/VM: return typed code or null
        locationProvider: suspend () -> GeoPoint?,          // GPS: GeoPoint or null
        videoUriProvider: suspend () -> Uri?                // Camera: Uri or null
    ): Boolean {
        // 1) Give the user 10 seconds to cancel the alert.
        val cancelled = waitForCancel(user.alertCancelCode, cancelCodeProvider)
        if (cancelled) {
            Log.d("AlertRepository", "Alert cancelled by protected user.")
            return false
        }

        // 2) Collect optional context data.
        val location = locationProvider()
        val videoUri = videoUriProvider()

        // 3) Upload video (optional).
        val videoUrl = videoUri?.let { uploadVideo(user.uid, it) }

        // 4) Build the alert object.
        val alert = Alert(
            type = ruleType,
            protectedId = user.uid,
            protectedName = user.name,
            location = location,
            videoUrl = videoUrl
        )

        // 5) Save the alert for every linked Monitor.
        if (user.monitors.isEmpty()) {
            Log.d("AlertRepository", "No monitors linked – alert not delivered.")
            return true
        }

        user.monitors.forEach { monitorId ->
            firestore.collection("users")
                .document(monitorId)
                .collection("alerts")
                .document(alert.id)
                .set(alert)
                .await()
        }

        Log.d("AlertRepository", "Alert sent to monitors: ${user.monitors}")
        return true
    }

    /**
     * Waits up to 10 seconds for the user to enter the correct cancellation code.
     * The provider is polled every 250ms.
     *
     * @return True if the correct code was entered in time, otherwise false.
     */
    private suspend fun waitForCancel(
        correctCode: String,
        cancelCodeProvider: suspend () -> String?
    ): Boolean {
        val maxMs = 10_000L
        val stepMs = 250L
        var waited = 0L

        while (waited < maxMs) {
            val typed = cancelCodeProvider()?.trim()
            if (!typed.isNullOrBlank() && typed == correctCode) {
                return true
            }
            delay(stepMs)
            waited += stepMs
        }
        return false
    }

    /**
     * Uploads a video file to Firebase Storage and returns its public download URL.
     */
    private suspend fun uploadVideo(protectedUid: String, uri: Uri): String {
        val fileName = "${protectedUid}_${System.currentTimeMillis()}.mp4"
        val ref = storage.reference.child("alerts_videos/$fileName")
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }
}