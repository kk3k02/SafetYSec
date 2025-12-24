package pt.a2025121082.isec.safetysec.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pt.a2025121082.isec.safetysec.data.model.Alert
import pt.a2025121082.isec.safetysec.data.model.MonitoringRule
import pt.a2025121082.isec.safetysec.data.model.RuleParams
import pt.a2025121082.isec.safetysec.data.model.RuleType
import pt.a2025121082.isec.safetysec.data.model.TimeWindow
import pt.a2025121082.isec.safetysec.data.model.User
import pt.a2025121082.isec.safetysec.data.repository.AlertRepository
import pt.a2025121082.isec.safetysec.data.repository.AuthRepository
import pt.a2025121082.isec.safetysec.data.repository.MonitorRulesBundle
import pt.a2025121082.isec.safetysec.data.repository.MonitoringRepository
import javax.inject.Inject

/**
 * Global UI state for the main app flows.
 */
data class AppUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val me: User? = null,
    val myOtp: String? = null,

    // Protected data
    val monitorRuleBundles: List<MonitorRulesBundle> = emptyList(),
    val timeWindows: List<TimeWindow> = emptyList(),
    val myAlerts: List<Alert> = emptyList(),
    val myLinkedMonitors: List<User> = emptyList(),

    // Monitor data
    val monitorAlerts: List<Alert> = emptyList(),
    val linkedProtectedUsers: List<User> = emptyList(),
    val rulesForSelectedProtected: MonitorRulesBundle? = null,
    val isLinkingSuccessful: Boolean = false,

    // Alert cancel window state
    val isCancelWindowOpen: Boolean = false,
    val cancelSecondsLeft: Int = 0,
    val typedCancelCode: String? = null
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val monitoringRepo: MonitoringRepository,
    private val alertRepo: AlertRepository,
    private val db: FirebaseFirestore
) : ViewModel() {

    var state by mutableStateOf(AppUiState())
        private set

    private var alertsListenerJob: Job? = null

    fun clear() {
        alertsListenerJob?.cancel()
        state = AppUiState()
    }

    fun loadMyProfile() = viewModelScope.launch {
        state = state.copy(isLoading = true, error = null)
        try {
            val me = authRepo.getUserProfile()
            state = state.copy(me = me, isLoading = false)

            if (me.roles.contains("Protected")) {
                refreshProtectedData(me.uid)
            }
            if (me.roles.contains("Monitor")) {
                startMonitoringDashboard(me.uid)
            }
        } catch (t: Throwable) {
            state = state.copy(isLoading = false, error = t.message)
        }
    }

    private fun refreshProtectedData(protectedUid: String) = viewModelScope.launch {
        try {
            val bundles = monitoringRepo.getRulesForProtected(protectedUid)
            val windows = monitoringRepo.listTimeWindows(protectedUid)
            
            // Load full profiles of linked monitors
            val me = authRepo.getUserProfile(protectedUid)
            val monitorIds = me.monitors
            val monitors = if (monitorIds.isNotEmpty()) {
                monitorIds.map { id -> authRepo.getUserProfile(id) }
            } else {
                emptyList()
            }
            
            state = state.copy(
                monitorRuleBundles = bundles, 
                timeWindows = windows,
                myLinkedMonitors = monitors
            )
        } catch (t: Throwable) {
            state = state.copy(error = t.message)
        }
    }

    private fun startMonitoringDashboard(monitorUid: String) {
        alertsListenerJob?.cancel()
        alertsListenerJob = viewModelScope.launch {
            db.collection("users").document(monitorUid)
                .collection("alerts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    val alerts = snapshot?.toObjects(Alert::class.java) ?: emptyList()
                    state = state.copy(monitorAlerts = alerts)
                }

            val me = authRepo.getUserProfile(monitorUid)
            val linkedIds = me.protectedUsers
            if (linkedIds.isNotEmpty()) {
                val users = linkedIds.map { id -> authRepo.getUserProfile(id) }
                state = state.copy(linkedProtectedUsers = users)
            }
        }
    }

    /**
     * Loads the rules configuration and authorizations for a specific Protected user.
     * This allows the Monitor to see what the Protected user has agreed to.
     */
    fun loadRulesForProtected(protectedUid: String) = viewModelScope.launch {
        val me = state.me ?: return@launch
        try {
            val bundles = monitoringRepo.getRulesForProtected(protectedUid)
            val myBundle = bundles.find { it.monitorId == me.uid }
            state = state.copy(rulesForSelectedProtected = myBundle)
        } catch (t: Throwable) {
            state = state.copy(error = t.message)
        }
    }

    // Association
    fun generateOtp() = viewModelScope.launch {
        state = state.copy(isLoading = true, error = null)
        try {
            val code = authRepo.generateAssociationCode()
            state = state.copy(isLoading = false, myOtp = code)
        } catch (t: Throwable) {
            state = state.copy(isLoading = false, error = t.message)
        }
    }

    fun linkWithOtp(code: String) = viewModelScope.launch {
        state = state.copy(isLoading = true, error = null, isLinkingSuccessful = false)
        try {
            authRepo.linkWithAssociationCode(code)
            state = state.copy(isLoading = false, isLinkingSuccessful = true)
            loadMyProfile()
        } catch (t: Throwable) {
            state = state.copy(isLoading = false, error = t.message)
        }
    }

    fun consumeLinkingSuccess() {
        state = state.copy(isLinkingSuccessful = false)
    }

    // Rules
    fun requestRulesForProtected(
        protectedUid: String,
        enabledTypes: List<RuleType>,
        params: RuleParams
    ) = viewModelScope.launch {
        val me = state.me ?: return@launch
        state = state.copy(isLoading = true, error = null)
        try {
            val rules = enabledTypes.map { type ->
                MonitoringRule(type = type, params = params, enabled = true)
            }
            monitoringRepo.requestRules(
                protectedUid = protectedUid,
                monitorUid = me.uid,
                rules = rules
            )
            state = state.copy(isLoading = false)
            loadRulesForProtected(protectedUid) // Refresh after update
        } catch (t: Throwable) {
            state = state.copy(isLoading = false, error = t.message)
        }
    }

    fun saveAuthorizations(monitorUid: String, authorized: List<RuleType>) = viewModelScope.launch {
        val me = state.me ?: return@launch
        state = state.copy(isLoading = true, error = null)
        try {
            monitoringRepo.saveAuthorizations(me.uid, monitorUid, authorized)
            state = state.copy(isLoading = false)
            refreshProtectedData(me.uid)
        } catch (t: Throwable) {
            state = state.copy(isLoading = false, error = t.message)
        }
    }

    // Time windows
    fun addTimeWindow(days: List<Int>, startHour: Int, endHour: Int) = viewModelScope.launch {
        val me = state.me ?: return@launch
        val window = TimeWindow(daysOfWeek = days, startHour = startHour, endHour = endHour)
        if (!window.isValid()) {
            state = state.copy(error = "Invalid time window")
            return@launch
        }
        state = state.copy(isLoading = true, error = null)
        try {
            monitoringRepo.addTimeWindow(me.uid, window)
            state = state.copy(isLoading = false)
            refreshProtectedData(me.uid)
        } catch (t: Throwable) {
            state = state.copy(isLoading = false, error = t.message)
        }
    }

    fun updateCancelPin(pin: String) = viewModelScope.launch {
        state = state.copy(isLoading = true, error = null)
        try {
            authRepo.updateAlertCancelCode(pin)
            state = state.copy(isLoading = false)
            loadMyProfile()
        } catch (t: Throwable) {
            state = state.copy(isLoading = false, error = t.message)
        }
    }

    // Panic alert
    fun triggerPanic() = viewModelScope.launch {
        val me = state.me ?: return@launch
        if (!me.roles.contains("Protected")) {
            state = state.copy(error = "Only Protected can trigger panic.")
            return@launch
        }
        state = state.copy(isCancelWindowOpen = true, cancelSecondsLeft = 10)
        val sent = alertRepo.triggerAlert(
            ruleType = RuleType.PANIC,
            user = me,
            cancelCodeProvider = { state.typedCancelCode },
            locationProvider = { GeoPoint(0.0, 0.0) },
            videoUriProvider = { null }
        )
        state = state.copy(isCancelWindowOpen = false, cancelSecondsLeft = 0, typedCancelCode = null)
        if (!sent) {
            state = state.copy(error = "Alert cancelled.")
        }
    }

    fun tryCancelAlert(typed: String) {
        state = state.copy(typedCancelCode = typed)
    }
}
