package pt.a2025121082.isec.safetysec.data.model

import com.google.firebase.firestore.GeoPoint
import java.util.UUID

/**
 * Data model representing an emergency alert triggered in the system.
 * This class is used for storing and transmitting alert information between 
 * the Protected user and their Monitors.
 */
data class Alert(
    /** Unique identifier for the alert. Generated as a random UUID by default. */
    val id: String = UUID.randomUUID().toString(),
    
    /** The type of rule that triggered this alert (e.g., FALL, PANIC, SPEED). */
    val type: RuleType = RuleType.PANIC,
    
    /** Timestamp of when the alert was triggered. */
    val timestamp: Long = System.currentTimeMillis(),
    
    /** The UID of the Protected user who triggered the alert. */
    val protectedId: String = "",
    
    /** The display name of the Protected user at the time the alert was triggered. */
    val protectedName: String = "",
    
    /** Geographic coordinates (latitude/longitude) where the alert occurred. */
    val location: GeoPoint? = null,
    
    /** 
     * URL of the evidence video recorded during the alert. 
     * Usually uploaded to Firebase Storage. 
     */
    val videoUrl: String? = null,
    
    /** 
     * Current status of the alert.
     * Possible values: "SENT" (dispatched to monitors) or "CANCELLED" (stopped by the user).
     */
    val status: String = "SENT"
)
