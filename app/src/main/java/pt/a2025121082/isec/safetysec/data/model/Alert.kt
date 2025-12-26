package pt.a2025121082.isec.safetysec.data.model

import com.google.firebase.firestore.GeoPoint
import java.util.UUID

/**
 * Alert data model.
 */
data class Alert(
    val id: String = UUID.randomUUID().toString(),
    val type: RuleType = RuleType.PANIC,
    val timestamp: Long = System.currentTimeMillis(),
    val protectedId: String = "",
    val protectedName: String = "",
    val location: GeoPoint? = null,
    val videoUrl: String? = null,
    /** Status of the alert: "SENT" or "CANCELLED" */
    val status: String = "SENT"
)
