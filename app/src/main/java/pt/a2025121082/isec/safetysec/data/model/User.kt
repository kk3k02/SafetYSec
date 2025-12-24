package pt.a2025121082.isec.safetysec.data.model

/**
 * Represents a user in the application.
 *
 * User data model with extended fields for roles and relationships (Protected / Monitor).
 */
data class User(
    /** Unique user identifier (Firebase Auth UID) */
    val uid: String = "",

    /** User email address */
    val email: String = "",

    /** Display name */
    val name: String = "",

    /**
     * List of user roles (e.g., "Protected", "Monitor").
     * A user may have one or both roles.
     */
    val roles: List<String> = listOf("Protected"),

    /**
     * PIN code used to cancel an active alert.
     * Default is "0000" and can be changed in the user profile.
     */
    val alertCancelCode: String = "0000",

    /**
     * List of Monitor user IDs supervising this user (when the user acts as Protected).
     */
    val monitors: List<String> = emptyList(),

    /**
     * List of Protected user IDs supervised by this user (when the user acts as Monitor).
     */
    val protectedUsers: List<String> = emptyList(),

    /**
     * One-time association code (OTP) used to link users.
     * Generated on demand and invalid after being used.
     */
    val associationCode: String? = null,

    /**
     * Timestamp when the association code was created.
     * Used to handle code expiration (TTL).
     */
    val associationCodeCreatedAt: Long? = null
)
