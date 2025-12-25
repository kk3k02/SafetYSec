package pt.a2025121082.isec.safetysec.data.model

import com.google.firebase.firestore.Exclude
import java.util.UUID

/**
 * Monitoring rules definitions and helper data structures.
 */

/** Monitoring rule types supported by the application. */
enum class RuleType {
    FALL,
    ACCIDENT,
    GEOFENCE,
    SPEED,
    INACTIVITY,
    PANIC;

    fun displayName(): String = when (this) {
        FALL -> "Fall"
        ACCIDENT -> "Accident"
        GEOFENCE -> "Geofencing"
        SPEED -> "Speed"
        INACTIVITY -> "Inactivity"
        PANIC -> "Panic"
    }
}

data class RuleParams(
    val maxSpeed: Float? = null,
    val inactivityDurationMin: Int? = null,
    val geofenceAreas: List<GeofenceArea>? = null
)

data class GeofenceArea(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 0.0
)

data class MonitoringRule(
    val type: RuleType = RuleType.PANIC,
    val params: RuleParams = RuleParams(),
    val enabled: Boolean = true
)

/**
 * Time window when rules are allowed to be active.
 */
data class TimeWindow(
    /** Unique identifier for the time window (used for Firestore doc name) */
    val id: String = UUID.randomUUID().toString(),

    /** Days of week when the window applies (1=Mon, 2=Tue, ..., 7=Sun) */
    val daysOfWeek: List<Int> = emptyList(),

    val startHour: Int = 0,
    val endHour: Int = 0
) {
    /**
     * Validates the time window configuration.
     * Changed to a function instead of a property to avoid Firestore 'No setter/field' warnings.
     */
    @Exclude
    fun checkValid(): Boolean {
        if (startHour !in 0..23) return false
        if (endHour !in 0..23) return false
        if (startHour >= endHour) return false
        if (daysOfWeek.isEmpty()) return false
        return true
    }

    /** Helper to display selected days in UI */
    fun daysToString(): String {
        val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return daysOfWeek.sorted().joinToString(", ") { names.getOrNull(it - 1) ?: "?" }
    }
}
