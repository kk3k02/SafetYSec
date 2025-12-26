package pt.a2025121082.isec.safetysec.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import pt.a2025121082.isec.safetysec.data.model.*
import javax.inject.Inject

class MonitoringRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun ruleDoc(protectedUid: String, monitorUid: String) =
        firestore.collection("users").document(protectedUid)
            .collection("rulesByMonitor").document(monitorUid)

    suspend fun saveAuthorizations(protectedUid: String, monitorUid: String, authorized: List<RuleType>, inactivityMin: Int?) {
        val data = mutableMapOf<String, Any>(
            "authorizedTypes" to authorized.map { it.name }
        )
        if (inactivityMin != null) {
            data["inactivityDuration"] = inactivityMin
        }
        ruleDoc(protectedUid, monitorUid).set(data, SetOptions.merge()).await()
    }

    suspend fun getRulesForProtected(protectedUid: String): List<MonitorRulesBundle> {
        val qs = firestore.collection("users").document(protectedUid)
            .collection("rulesByMonitor").get().await()

        return qs.documents.map { d ->
            val requested = (d.get("requested") as? List<*>)?.mapNotNull { it as? Map<*, *> }?.map {
                val typeStr = it["type"] as? String ?: "PANIC"
                val paramsMap = it["params"] as? Map<*, *>
                val params = RuleParams(
                    maxSpeed = (paramsMap?.get("maxSpeed") as? Number)?.toFloat(),
                    inactivityDurationMin = (paramsMap?.get("inactivityDurationMin") as? Number)?.toInt()
                )
                MonitoringRule(type = RuleType.valueOf(typeStr), params = params)
            } ?: emptyList()

            MonitorRulesBundle(
                monitorId = d.id,
                requested = requested,
                authorizedTypes = (d.get("authorizedTypes") as? List<*>)?.mapNotNull { runCatching { RuleType.valueOf(it as String) }.getOrNull() } ?: emptyList()
            )
        }
    }

    private fun windowsCol(protectedUid: String) = firestore.collection("users").document(protectedUid).collection("timeWindows")
    suspend fun addTimeWindow(protectedUid: String, window: TimeWindow) = windowsCol(protectedUid).document(window.id).set(window).await()
    suspend fun deleteTimeWindow(protectedUid: String, windowId: String) = windowsCol(protectedUid).document(windowId).delete().await()
    suspend fun listTimeWindows(protectedUid: String): List<TimeWindow> {
        val qs = windowsCol(protectedUid).get().await()
        return qs.documents.mapNotNull { d -> d.toObject(TimeWindow::class.java)?.copy(id = d.id) }
    }
    
    suspend fun requestRules(protectedUid: String, monitorUid: String, rules: List<MonitoringRule>) {
        val rulesMapList = rules.map { mapOf("type" to it.type.name, "enabled" to it.enabled, "params" to mapOf("maxSpeed" to it.params.maxSpeed, "inactivityDurationMin" to it.params.inactivityDurationMin)) }
        ruleDoc(protectedUid, monitorUid).set(mapOf("requested" to rulesMapList), SetOptions.merge()).await()
    }
}
