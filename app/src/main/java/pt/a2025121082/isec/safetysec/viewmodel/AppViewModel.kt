package pt.a2025121082.isec.safetysec.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pt.a2025121082.isec.safetysec.data.model.*
import pt.a2025121082.isec.safetysec.data.repository.AlertRepository
import pt.a2025121082.isec.safetysec.data.repository.AuthRepository
import pt.a2025121082.isec.safetysec.data.repository.MonitorRulesBundle
import pt.a2025121082.isec.safetysec.data.repository.MonitoringRepository
import pt.a2025121082.isec.safetysec.util.FallDetectionService
import javax.inject.Inject

data class AppUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val me: User? = null,
    val myOtp: String? = null,
    val monitorRuleBundles: List<MonitorRulesBundle> = emptyList(),
    val timeWindows: List<TimeWindow> = emptyList(),
    val myAlerts: List<Alert> = emptyList(),
    val myLinkedMonitors: List<User> = emptyList(),
    val monitorAlerts: List<Alert> = emptyList(),
    val linkedProtectedUsers: List<User> = emptyList(),
    val rulesForSelectedProtected: MonitorRulesBundle? = null,
    val isLinkingSuccessful: Boolean = false,
    val isRequestSuccessful: Boolean = false,
    val isRemovalSuccessful: Boolean = false,
    val isAdditionSuccessful: Boolean = false,
    val isCancelWindowOpen: Boolean = false,
    val cancelSecondsLeft: Int = 0,
    val typedCancelCode: String? = null,
    val isFallDetectionEnabled: Boolean = false
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

    init {
        // Listen for sensor events from background services
        viewModelScope.launch {
            alertRepo.detectionEvents.collectLatest { type ->
                triggerAlertWithTimer(type)
            }
        }
    }

    /**
     * Triggers an alert flow with a 10-second countdown on the UI.
     */
    private fun triggerAlertWithTimer(type: RuleType) = viewModelScope.launch {
        val me = state.me ?: return@launch
        if (state.isCancelWindowOpen) return@launch

        // UI: Initialize countdown state
        state = state.copy(isCancelWindowOpen = true, cancelSecondsLeft = 10, typedCancelCode = null)
        
        // Background ticker job to update the UI clock every second
        val tickerJob = viewModelScope.launch {
            while (state.cancelSecondsLeft > 0) {
                delay(1000)
                state = state.copy(cancelSecondsLeft = state.cancelSecondsLeft - 1)
            }
        }

        // Wait for user input or timeout via Repository logic
        val sent = alertRepo.triggerAlert(
            ruleType = type,
            user = me,
            cancelCodeProvider = { state.typedCancelCode },
            locationProvider = { GeoPoint(0.0, 0.0) },
            videoUriProvider = { null }
        )
        
        // Cleanup after dialog closes
        tickerJob.cancel()
        state = state.copy(isCancelWindowOpen = false, cancelSecondsLeft = 0, typedCancelCode = null)
        
        if (!sent) {
            state = state.copy(error = "Alert cancelled by user.")
        } else {
            loadMyProfile() // Refresh history to see new alert
        }
    }

    fun triggerPanic() {
        triggerAlertWithTimer(RuleType.PANIC)
    }

    fun tryCancelAlert(typed: String) {
        state = state.copy(typedCancelCode = typed)
    }

    fun clear() {
        alertsListenerJob?.cancel()
        state = AppUiState()
    }

    fun loadMyProfile() = viewModelScope.launch {
        state = state.copy(isLoading = true, error = null)
        try {
            val me = authRepo.getUserProfile()
            state = state.copy(me = me, isLoading = false)
            if (me.roles.contains("Protected")) refreshProtectedData(me.uid)
            if (me.roles.contains("Monitor")) startMonitoringDashboard(me.uid)
        } catch (t: Throwable) {
            state = state.copy(isLoading = false, error = t.message)
        }
    }

    private suspend fun refreshProtectedData(protectedUid: String) {
        try {
            val bundles = monitoringRepo.getRulesForProtected(protectedUid)
            val windows = monitoringRepo.listTimeWindows(protectedUid)
            val me = authRepo.getUserProfile(protectedUid)
            val monitors = me.monitors.map { authRepo.getUserProfile(it) }
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
            db.collection("users").document(monitorUid).collection("alerts")
                .orderBy("timestamp", Query.Direction.DESCENDING).limit(20)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    state = state.copy(monitorAlerts = snapshot?.toObjects(Alert::class.java) ?: emptyList())
                }
            val me = authRepo.getUserProfile(monitorUid)
            val users = me.protectedUsers.map { authRepo.getUserProfile(it) }
            state = state.copy(linkedProtectedUsers = users)
        }
    }

    fun loadRulesForProtected(protectedUid: String) = viewModelScope.launch {
        val me = state.me ?: return@launch
        try {
            val bundles = monitoringRepo.getRulesForProtected(protectedUid)
            state = state.copy(rulesForSelectedProtected = bundles.find { it.monitorId == me.uid })
        } catch (t: Throwable) {
            state = state.copy(error = t.message)
        }
    }

    fun generateOtp() = viewModelScope.launch {
        state = state.copy(isLoading = true, error = null)
        try {
            state = state.copy(isLoading = false, myOtp = authRepo.generateAssociationCode())
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

    fun consumeLinkingSuccess() { state = state.copy(isLinkingSuccessful = false) }

    fun removeMonitor(monitorId: String) = viewModelScope.launch {
        val me = state.me ?: return@launch
        state = state.copy(isLoading = true, error = null, isRemovalSuccessful = false)
        try {
            authRepo.removeAssociation(monitorId, me.uid)
            state = state.copy(
                isLoading = false, 
                isRemovalSuccessful = true,
                myLinkedMonitors = state.myLinkedMonitors.filter { it.uid != monitorId }
            )
        } catch (t: Throwable) {
            state = state.copy(isLoading = false, error = t.message)
        }
    }

    fun removeProtectedUser(protectedId: String) = viewModelScope.launch {
        val me = state.me ?: return@launch
        state = state.copy(isLoading = true, error = null, isRemovalSuccessful = false)
        try {
            authRepo.removeAssociation(me.uid, protectedId)
            state = state.copy(
                isLoading = false, 
                isRemovalSuccessful = true,
                linkedProtectedUsers = state.linkedProtectedUsers.filter { it.uid != protectedId }
            )
        } catch (t: Throwable) {
            state = state.copy(isLoading = false, error = t.message)
        }
    }

    fun consumeRemovalSuccess() { state = state.copy(isRemovalSuccessful = false) }

    fun requestRulesForProtected(protectedUid: String, enabledTypes: List<RuleType>, params: RuleParams) = viewModelScope.launch {
        val me = state.me ?: return@launch
        state = state.copy(isLoading = true, error = null, isRequestSuccessful = false)
        try {
            val rules = enabledTypes.map { MonitoringRule(it, params, true) }
            monitoringRepo.requestRules(protectedUid, me.uid, rules)
            state = state.copy(isLoading = false, isRequestSuccessful = true)
            loadRulesForProtected(protectedUid)
        } catch (t: Throwable) {
            state = state.copy(isLoading = false, error = t.message)
        }
    }

    fun consumeRequestSuccess() { state = state.copy(isRequestSuccessful = false) }

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

    fun addTimeWindow(days: List<Int>, startHour: Int, endHour: Int) = viewModelScope.launch {
        val me = state.me ?: return@launch
        val window = TimeWindow(daysOfWeek = days, startHour = startHour, endHour = endHour)
        if (!window.checkValid()) {
            state = state.copy(error = "Invalid time window")
            return@launch
        }
        state = state.copy(isLoading = true, error = null, isAdditionSuccessful = false)
        try {
            monitoringRepo.addTimeWindow(me.uid, window)
            val updated = state.timeWindows + window
            state = state.copy(
                isLoading = false, 
                timeWindows = updated,
                isAdditionSuccessful = true
            )
        } catch (t: Throwable) {
            state = state.copy(isLoading = false, error = t.message)
        }
    }

    fun consumeAdditionSuccess() { state = state.copy(isAdditionSuccessful = false) }

    fun removeTimeWindow(windowId: String) = viewModelScope.launch {
        val me = state.me ?: return@launch
        state = state.copy(isLoading = true, error = null, isRemovalSuccessful = false)
        try {
            monitoringRepo.deleteTimeWindow(me.uid, windowId)
            val updatedWindows = state.timeWindows.filter { it.id != windowId }
            state = state.copy(
                isLoading = false, 
                isRemovalSuccessful = true,
                timeWindows = updatedWindows
            )
        } catch (t: Throwable) {
            state = state.copy(isLoading = false, error = t.message)
        }
    }

    fun toggleFallDetection(context: Context) {
        val newState = !state.isFallDetectionEnabled
        val intent = Intent(context, FallDetectionService::class.java)
        
        if (newState) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
        state = state.copy(isFallDetectionEnabled = newState)
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

    fun consumeAdditionSucess() {
        state = state.copy(isAdditionSuccessful = false)
    }
}
