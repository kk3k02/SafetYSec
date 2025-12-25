package pt.a2025121082.isec.safetysec.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import pt.a2025121082.isec.safetysec.data.model.*
import javax.inject.Inject

/**
 * Repository responsible for managing monitoring rules and time windows.
 */
class MonitoringRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun ruleDoc(protectedUid: String, monitorUid: String) =
        firestore.collection("users").document(protectedUid)
            .collection("rulesByMonitor").document(monitorUid)

    /**
     * Stores a list of monitoring rules requested by a Monitor.
     */
    suspend fun requestRules(protectedUid: String, monitorUid: String, rules: List<MonitoringRule>) {
        val rulesMapList = rules.map { rule ->
            mapOf(
                "type" to rule.type.name,
                "enabled" to rule.enabled,
                "params" to mapOf(
                    "maxSpeed" to rule.params.maxSpeed,
                    "inactivityDurationMin" to rule.params.inactivityDurationMin,
                    "geofenceAreas" to rule.params.geofenceAreas?.map { area ->
                        mapOf(
                            "latitude" to area.latitude,
                            "longitude" to area.longitude,
                            "radiusMeters" to area.radiusMeters
                        )
                    }
                )
            )
        }

        ruleDoc(protectedUid, monitorUid).set(
            mapOf("requested" to rulesMapList),
            SetOptions.merge()
        ).await()
    }

    suspend fun getRulesForProtected(protectedUid: String): List<MonitorRulesBundle> {
        val qs = firestore.collection("users").document(protectedUid)
            .collection("rulesByMonitor").get().await()

        return qs.documents.map { d ->
            val requested = (d.get("requested") as? List<*>)?.mapNotNull { it as? Map<*, *> }?.map {
                val typeStr = it["type"] as? String ?: "PANIC"
                val enabled = it["enabled"] as? Boolean ?: true
                val paramsMap = it["params"] as? Map<*, *>

                val geofenceList = (paramsMap?.get("geofenceAreas") as? List<*>)?.mapNotNull { item ->
                    val areaMap = item as? Map<*, *>
                    if (areaMap != null) {
                        GeofenceArea(
                            latitude = (areaMap["latitude"] as? Number)?.toDouble() ?: 0.0,
                            longitude = (areaMap["longitude"] as? Number)?.toDouble() ?: 0.0,
                            radiusMeters = (areaMap["radiusMeters"] as? Number)?.toDouble() ?: 0.0
                        )
                    } else null
                }

                val params = RuleParams(
                    maxSpeed = (paramsMap?.get("maxSpeed") as? Number)?.toFloat(),
                    inactivityDurationMin = (paramsMap?.get("inactivityDurationMin") as? Number)?.toInt(),
                    geofenceAreas = geofenceList
                )

                MonitoringRule(type = RuleType.valueOf(typeStr), params = params, enabled = enabled)
            } ?: emptyList()

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

    suspend fun saveAuthorizations(protectedUid: String, monitorUid: String, authorized: List<RuleType>) {
        ruleDoc(protectedUid, monitorUid).set(
            mapOf("authorizedTypes" to authorized.map { it.name }),
            SetOptions.merge()
        ).await()
    }

    private fun windowsCol(protectedUid: String) =
        firestore.collection("users").document(protectedUid).collection("timeWindows")

    suspend fun addTimeWindow(protectedUid: String, window: TimeWindow) {
        // Use window.id as the document ID for explicit control
        windowsCol(protectedUid).document(window.id).set(window).await()
    }

    suspend fun deleteTimeWindow(protectedUid: String, windowId: String) {
        // Delete the document by its explicit ID in Firestore
        windowsCol(protectedUid).document(windowId).delete().await()
    }

    suspend fun listTimeWindows(protectedUid: String): List<TimeWindow> {
        val qs = windowsCol(protectedUid).get().await()
        return qs.documents.mapNotNull { d ->
            // Fix: Explicitly map the Firestore Document ID to the object's id property
            d.toObject(TimeWindow::class.java)?.copy(id = d.id)
        }
    }
}
