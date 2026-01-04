package pt.a2025121082.isec.safetysec.data.repository

import pt.a2025121082.isec.safetysec.data.model.MonitoringRule
import pt.a2025121082.isec.safetysec.data.model.RuleType

/**
 * Represents a collection of monitoring rules and their authorization status 
 * for a specific Monitor-Protected user relationship.
 * 
 * This bundle tracks what a Monitor has requested to watch and what the 
 * Protected user has actually permitted.
 */
data class MonitorRulesBundle(
    /** The unique identifier of the Monitor user. */
    val monitorId: String,
    
    /** 
     * The list of rules (with parameters) that the Monitor has requested 
     * to be active for the Protected user. 
     */
    val requested: List<MonitoringRule>,
    
    /** 
     * The list of rule types that the Protected user has explicitly 
     * authorized for this specific Monitor. 
     */
    val authorizedTypes: List<RuleType>
)
