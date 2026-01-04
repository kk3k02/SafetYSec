package pt.a2025121082.isec.safetysec.data.model

/**
 * Data model representing a user within the SafetYSec ecosystem.
 * This class is used for both Firebase Auth synchronization and Firestore document mapping.
 */
data class User(
    /** Unique identifier from Firebase Authentication. */
    val uid: String = "",
    
    /** User's primary email address. */
    val email: String = "",
    
    /** Full name or display name of the user. */
    val name: String = "",
    
    /** 
     * List of roles assigned to the user (e.g., "Protected", "Monitor"). 
     * Default role for new users is "Protected".
     */
    val roles: List<String> = listOf("Protected"),
    
    /** 
     * A 4-digit PIN used by a Protected user to cancel an emergency alert 
     * before it is dispatched to their monitors. 
     */
    val alertCancelCode: String = "0000",
    
    /** List of UIDs for users who are monitoring this specific user. */
    val monitors: List<String> = emptyList(),
    
    /** List of UIDs for users that this user is currently monitoring (if they have the Monitor role). */
    val protectedUsers: List<String> = emptyList(),
    
    /** 
     * A temporary 6-digit One-Time Password (OTP) used to link a Protected user 
     * with a new Monitor. 
     */
    val associationCode: String? = null,
    
    /** Timestamp of when the association code was generated, used to enforce expiration. */
    val associationCodeCreatedAt: Long? = null,
    
    /** 
     * Threshold in minutes for detecting prolonged inactivity. 
     * If the user doesn't move for this long, an alert may be triggered. 
     */
    val inactivityDurationMin: Int = 15,
    
    /** 
     * Timestamp indicating when the monitor last cleared their incoming alerts dashboard. 
     * Helps in filtering historical vs. new alerts.
     */
    val monitorAlertsClearedAt: Long = 0L
)
