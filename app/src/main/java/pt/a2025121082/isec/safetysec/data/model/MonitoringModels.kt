package pt.a2025121082.isec.safetysec.data.model

import com.google.firebase.firestore.Exclude
import java.util.UUID

/**
 * Monitoring rules definitions and helper data structures.
 */

/** 
 * Monitoring rule types supported by the application. 
 * Each type represents a specific safety check.
 */
enum class RuleType {
    FALL,
    ACCIDENT,
    GEOFENCE,
    SPEED,
    PROLONGED_INACTIVITY,
    INACTIVITY,
    PANIC;

    /** Returns a human-readable name for the rule type. */
    fun displayName(): String = when (this) {
        FALL -> "Fall"
        ACCIDENT -> "Accident"
        GEOFENCE -> "Geofencing"
        SPEED -> "Speed"
        PROLONGED_INACTIVITY -> "Prolonged inactivity"
        INACTIVITY -> "Inactivity"
        PANIC -> "Panic"
    }
}

/**
 * Parameters associated with a monitoring rule.
 * Not all fields are used by every [RuleType].
 */
data class RuleParams(
    /** Maximum allowed speed in km/h (used by SPEED). */
    val maxSpeed: Float? = null,
    /** Minimum minutes of no movement before alert (used by PROLONGED_INACTIVITY). */
    val inactivityDurationMin: Int? = null,
    /** List of safe zones (used by GEOFENCE). */
    val geofenceAreas: List<GeofenceArea>? = null,
    /** Radius for a single geofence area in meters. */
    val geofenceRadiusMeters: Double? = null
)

/**
 * Represents a circular geographic area for geofencing.
 */
data class GeofenceArea(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 0.0
)

/**
 * Represents a specific monitoring configuration requested or authorized.
 */
data class MonitoringRule(
    val type: RuleType = RuleType.PANIC,
    val params: RuleParams = RuleParams(),
    val enabled: Boolean = true
)

/**
 * Time window when monitoring rules are allowed to be active.
 * Used to limit protection to specific parts of the day/week.
 */
data class TimeWindow(
    /** Unique identifier for the time window (used for Firestore doc name). */
    val id: String = UUID.randomUUID().toString(),

    /** 
     * Days of week when the window applies.
     * Values: 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun.
     */
    val daysOfWeek: List<Int> = emptyList(),

    /** Starting hour (0-23). */
    val startHour: Int = 0,
    /** Ending hour (0-23). Must be greater than [startHour]. */
    val endHour: Int = 0
) {
    /**
     * Validates the time window configuration.
     * Marked with @Exclude to prevent Firestore from attempting to persist this as a field.
     */
    @Exclude
    fun checkValid(): Boolean {
        if (startHour !in 0..23) return false
        if (endHour !in 0..23) return false
        if (startHour >= endHour) return false
        if (daysOfWeek.isEmpty()) return false
        return true
    }

    /** Helper to display the list of selected days as a localized string. */
    fun daysToString(): String {
        val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        return daysOfWeek.sorted().joinToString(", ") { names.getOrNull(it - 1) ?: "?" }
    }
}
