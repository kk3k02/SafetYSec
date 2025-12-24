package pt.a2025121082.isec.safetysec.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import pt.a2025121082.isec.safetysec.data.model.MonitoringRule
import pt.a2025121082.isec.safetysec.data.model.RuleParams
import pt.a2025121082.isec.safetysec.data.model.RuleType
import pt.a2025121082.isec.safetysec.data.model.TimeWindow
import javax.inject.Inject

/**
 * Represents a rules bundle created per Monitor for a specific Protected user.
 *
 * - [requested] contains rules requested by the Monitor
 * - [authorizedTypes] contains rule types approved by the Protected user
 */
data class MonitorRulesBundle(
    /** Monitor user ID (document id in rulesByMonitor collection). */
    val monitorId: String,

    /** List of rules requested by this Monitor. */
    val requested: List<MonitoringRule>,

    /** List of rule types authorized/approved by the Protected user. */
    val authorizedTypes: List<RuleType>
)

/**
 * Repository responsible for:
 * - storing monitoring rule requests per Monitor (under a Protected user)
 * - storing which rule types are authorized by the Protected user
 * - managing rule activation time windows
 *
 * Firestore structure used here:
 * users/{protectedUid}/rulesByMonitor/{monitorUid}
 * users/{protectedUid}/timeWindows/{autoDocId}
 */
class MonitoringRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    /**
     * Returns a reference to the rules document for a given Protected user and Monitor.
     */
    private fun ruleDoc(protectedUid: String, monitorUid: String) =
        firestore.collection("users").document(protectedUid)
            .collection("rulesByMonitor").document(monitorUid)

    /**
     * Stores a list of monitoring rules requested by a Monitor.
     * Initially, the authorized types list is empty (Protected must approve).
     */
    suspend fun requestRules(protectedUid: String, monitorUid: String, rules: List<MonitoringRule>) {
        ruleDoc(protectedUid, monitorUid).set(
            mapOf(
                "requested" to rules,
                "authorizedTypes" to emptyList<String>()
            )
        ).await()
    }

    /**
     * Loads all rule requests and authorizations for a given Protected user.
     *
     * @return A list of [MonitorRulesBundle] entries (one per Monitor).
     */
    suspend fun getRulesForProtected(protectedUid: String): List<MonitorRulesBundle> {
        val qs = firestore.collection("users").document(protectedUid)
            .collection("rulesByMonitor").get().await()

        return qs.documents.map { d ->
            // Parse requested rules (stored as a list of maps when not using typed serialization)
            val requested = (d.get("requested") as? List<*>)?.mapNotNull { it as? Map<*, *> }?.map {
                val typeStr = it["type"] as? String ?: "PANIC"
                val enabled = it["enabled"] as? Boolean ?: true
                val paramsMap = it["params"] as? Map<*, *>

                // Parse RuleParams (extend this if you later serialize geofenceAreas)
                val params = RuleParams(
                    maxSpeed = (paramsMap?.get("maxSpeed") as? Number)?.toFloat(),
                    inactivityDurationMin = (paramsMap?.get("inactivityDurationMin") as? Number)?.toInt(),
                    geofenceAreas = null
                )

                MonitoringRule(type = RuleType.valueOf(typeStr), params = params, enabled = enabled)
            } ?: emptyList()

            // Parse authorized rule types
            val authorized = (d.get("authorizedTypes") as? List<*>)?.mapNotNull { it as? String }
                ?.mapNotNull { runCatching { RuleType.valueOf(it) }.getOrNull() }
                ?: emptyList()

            MonitorRulesBundle(
                monitorId = d.id,
                requested = requested,
                authorizedTypes = authorized
            )
        }
    }

    /**
     * Saves the list of rule types authorized by the Protected user for a specific Monitor.
     */
    suspend fun saveAuthorizations(protectedUid: String, monitorUid: String, authorized: List<RuleType>) {
        ruleDoc(protectedUid, monitorUid).update(
            "authorizedTypes",
            authorized.map { it.name }
        ).await()
    }

    // -------------------------
    // Time windows
    // -------------------------

    /**
     * Returns the collection reference for time windows of a Protected user.
     */
    private fun windowsCol(protectedUid: String) =
        firestore.collection("users").document(protectedUid).collection("timeWindows")

    /**
     * Adds a new time window during which monitoring rules may be active.
     */
    suspend fun addTimeWindow(protectedUid: String, window: TimeWindow) {
        windowsCol(protectedUid).add(window).await()
    }

    /**
     * Lists all configured time windows for the given Protected user.
     */
    suspend fun listTimeWindows(protectedUid: String): List<TimeWindow> {
        val qs = windowsCol(protectedUid).get().await()
        return qs.documents.mapNotNull { it.toObject(TimeWindow::class.java) }
    }
}