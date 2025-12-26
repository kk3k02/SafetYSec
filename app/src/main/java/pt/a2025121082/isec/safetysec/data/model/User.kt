package pt.a2025121082.isec.safetysec.data.model

/**
 * Represents a user in the application.
 */
data class User(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val roles: List<String> = listOf("Protected"),
    val alertCancelCode: String = "0000",
    val monitors: List<String> = emptyList(),
    val protectedUsers: List<String> = emptyList(),
    val associationCode: String? = null,
    val associationCodeCreatedAt: Long? = null,
    /** Global inactivity threshold in minutes, configured in profile */
    val inactivityDurationMin: Int = 15
)
