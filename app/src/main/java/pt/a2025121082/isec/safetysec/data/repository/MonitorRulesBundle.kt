package pt.a2025121082.isec.safetysec.data.repository

import pt.a2025121082.isec.safetysec.data.model.MonitoringRule
import pt.a2025121082.isec.safetysec.data.model.RuleType

/**
 * Represents a rules bundle created per Monitor for a specific Protected user.
 */
data class MonitorRulesBundle(
    val monitorId: String,
    val requested: List<MonitoringRule>,
    val authorizedTypes: List<RuleType>
)
