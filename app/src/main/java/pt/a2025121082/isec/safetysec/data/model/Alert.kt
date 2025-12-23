package pt.a2025121082.isec.safetysec.data.model

import com.google.firebase.firestore.GeoPoint
import java.util.UUID

/**
 * Alert data model.
 *
 * Represents a security alert stored in Firebase Firestore.
 * Firestore works best with simple data types and GeoPoint
 * instead of custom coordinate pairs like Pair<Double, Double>.
 */
data class Alert(

    /** Unique identifier of the alert */
    val id: String = UUID.randomUUID().toString(),

    /** Type of the alert (e.g. PANIC, ZONE_EXIT, etc.) */
    val type: RuleType = RuleType.PANIC,

    /** Timestamp when the alert was created (milliseconds since epoch) */
    val timestamp: Long = System.currentTimeMillis(),

    /** ID of the protected user who triggered the alert */
    val protectedId: String = "",

    /** Display name of the protected user */
    val protectedName: String = "",

    /** Geographic location of the alert */
    val location: GeoPoint? = null,

    /** Optional URL of the recorded video associated with the alert */
    val videoUrl: String? = null
)