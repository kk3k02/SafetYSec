package pt.a2025121082.isec.safetysec.data.model

import com.google.firebase.firestore.Exclude

/**
 * Monitoring rules definitions and helper data structures.
 */

/** Monitoring rule types supported by the application. */
enum class RuleType {
    /** Fall detection */
    FALL,

    /** Road accident / sudden braking */
    ACCIDENT,

    /** Leaving a predefined safe area */
    GEOFENCE,

    /** Speed limit exceeded */
    SPEED,

    /** No movement detected for a specified time */
    INACTIVITY,

    /** Panic button (manual alert trigger) */
    PANIC;

    /**
     * Human-readable name for UI display.
     */
    fun displayName(): String = when (this) {
        FALL -> "Fall"
        ACCIDENT -> "Accident"
        GEOFENCE -> "Geofencing"
        SPEED -> "Speed"
        INACTIVITY -> "Inactivity"
        PANIC -> "Panic"
    }
}

/**
 * Optional parameters for rules (depending on the rule type).
 */
data class RuleParams(

    /** SPEED: maximum allowed speed in km/h */
    val maxSpeed: Float? = null,

    /** INACTIVITY: inactivity duration threshold in minutes */
    val inactivityDurationMin: Int? = null,

    /** GEOFENCE: list of allowed/monitored areas (GPS center + radius) */
    val geofenceAreas: List<GeofenceArea>? = null
)

/**
 * Single geofence area definition (center point + radius).
 */
data class GeofenceArea(
    /** Latitude of the geofence center */
    val latitude: Double = 0.0,

    /** Longitude of the geofence center */
    val longitude: Double = 0.0,

    /** Radius around the center point (in meters) */
    val radiusMeters: Double = 0.0
)

/**
 * A monitoring rule configured by the Monitor for a specific Protected user.
 */
data class MonitoringRule(
    /** Rule type */
    val type: RuleType = RuleType.PANIC,

    /** Rule parameters (may be empty for some types) */
    val params: RuleParams = RuleParams(),

    /** Whether this rule is currently enabled */
    val enabled: Boolean = true
)

/**
 * Time window when rules are allowed to be active.
 */
data class TimeWindow(
    /** Days of week when the window applies (e.g., Calendar.MONDAY, Calendar.TUESDAY...) */
    val daysOfWeek: List<Int> = emptyList(),

    /** Start hour (0-23) */
    val startHour: Int = 0,

    /** End hour (0-23) */
    val endHour: Int = 0
) {
    /**
     * Validates the time window configuration.
     * Use @get:Exclude to ensure Firestore ignores the 'valid' property during serialization.
     */
    @get:Exclude
    val isValid: Boolean
        get() {
            if (startHour !in 0..23) return false
            if (endHour !in 0..23) return false
            if (startHour >= endHour) return false
            if (daysOfWeek.isEmpty()) return false
            return true
        }
}
