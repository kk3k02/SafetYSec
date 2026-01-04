package pt.a2025121082.isec.safetysec.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import pt.a2025121082.isec.safetysec.data.model.*
import javax.inject.Inject

/**
 * Repository responsible for managing monitoring rules and time windows.
 * It handles the storage and retrieval of authorizations between Protected users and Monitors,
 * as well as the scheduling of protection periods.
 */
class MonitoringRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    /** 
     * Helper to get a reference to the specific rule document for a Protected/Monitor pair.
     */
    private fun ruleDoc(protectedUid: String, monitorUid: String) =
        firestore.collection("users").document(protectedUid)
            .collection("rulesByMonitor").document(monitorUid)

    /**
     * Saves the authorizations granted by a Protected user to a specific Monitor.
     * Updates authorized rule types and their specific parameters like inactivity or geofence areas.
     */
    suspend fun saveAuthorizations(
        protectedUid: String,
        monitorUid: String,
        authorized: List<RuleType>,
        inactivityMin: Int?,
        geofenceAreas: List<GeofenceArea>?
    ) {
        val data = mutableMapOf<String, Any>(
            "authorizedTypes" to authorized.map { it.name }
        )
        if (inactivityMin != null) {
            data["inactivityDuration"] = inactivityMin
        }
        if (geofenceAreas != null) {
            data["geofenceAreas"] = geofenceAreas.map {
                mapOf(
                    "latitude" to it.latitude,
                    "longitude" to it.longitude,
                    "radiusMeters" to it.radiusMeters
                )
            }
        }
        // Use merge to avoid overwriting the 'requested' rules field
        ruleDoc(protectedUid, monitorUid).set(data, SetOptions.merge()).await()
    }

    /**
     * Retrieves all monitoring rule bundles for a given Protected user.
     * Returns a list containing what each monitor requested and what was authorized.
     */
    suspend fun getRulesForProtected(protectedUid: String): List<MonitorRulesBundle> {
        val qs = firestore.collection("users").document(protectedUid)
            .collection("rulesByMonitor").get().await()

        return qs.documents.map { d ->
            // Parse geofence areas from the document
            val storedAreas = (d.get("geofenceAreas") as? List<*>)
                ?.mapNotNull { it as? Map<*, *> }
                ?.mapNotNull {
                    val lat = (it["latitude"] as? Number)?.toDouble() ?: return@mapNotNull null
                    val lon = (it["longitude"] as? Number)?.toDouble() ?: return@mapNotNull null
                    val radius = (it["radiusMeters"] as? Number)?.toDouble() ?: return@mapNotNull null
                    GeofenceArea(latitude = lat, longitude = lon, radiusMeters = radius)
                }

            // Parse requested rules from the document
            val requested = (d.get("requested") as? List<*>)
                ?.mapNotNull { it as? Map<*, *> }
                ?.mapNotNull {
                    val typeStr = it["type"] as? String ?: return@mapNotNull null
                    val type = runCatching { RuleType.valueOf(typeStr) }.getOrNull() ?: return@mapNotNull null
                    val paramsMap = it["params"] as? Map<*, *>
                    val params = RuleParams(
                        maxSpeed = (paramsMap?.get("maxSpeed") as? Number)?.toFloat(),
                        inactivityDurationMin = (paramsMap?.get("inactivityDurationMin") as? Number)?.toInt(),
                        geofenceAreas = if (type == RuleType.GEOFENCE) storedAreas else null,
                        geofenceRadiusMeters = (paramsMap?.get("geofenceRadiusMeters") as? Number)?.toDouble()
                    )
                    MonitoringRule(type = type, params = params)
                } ?: emptyList()

            MonitorRulesBundle(
                monitorId = d.id,
                requested = requested,
                authorizedTypes = (d.get("authorizedTypes") as? List<*>)?.mapNotNull { runCatching { RuleType.valueOf(it as String) }.getOrNull() } ?: emptyList()
            )
        }
    }

    /** Helper to get the time windows collection for a user. */
    private fun windowsCol(protectedUid: String) = firestore.collection("users").document(protectedUid).collection("timeWindows")
    
    /** Adds a new protection time window. */
    suspend fun addTimeWindow(protectedUid: String, window: TimeWindow) = windowsCol(protectedUid).document(window.id).set(window).await()
    
    /** Deletes an existing protection time window. */
    suspend fun deleteTimeWindow(protectedUid: String, windowId: String) = windowsCol(protectedUid).document(windowId).delete().await()
    
    /** Lists all protection time windows for a user. */
    suspend fun listTimeWindows(protectedUid: String): List<TimeWindow> {
        val qs = windowsCol(protectedUid).get().await()
        return qs.documents.mapNotNull { d -> d.toObject(TimeWindow::class.java)?.copy(id = d.id) }
    }
    
    /**
     * Allows a Monitor to request a set of rules and parameters for a Protected user.
     * These must be authorized by the Protected user before they become active.
     */
    suspend fun requestRules(protectedUid: String, monitorUid: String, rules: List<MonitoringRule>) {
        val rulesMapList = rules.map {
            mapOf(
                "type" to it.type.name,
                "enabled" to it.enabled,
                "params" to mapOf(
                    "maxSpeed" to it.params.maxSpeed,
                    "inactivityDurationMin" to it.params.inactivityDurationMin,
                    "geofenceRadiusMeters" to it.params.geofenceRadiusMeters
                )
            )
        }
        // Use merge to avoid overwriting the 'authorizedTypes' field
        ruleDoc(protectedUid, monitorUid).set(mapOf("requested" to rulesMapList), SetOptions.merge()).await()
    }
}
