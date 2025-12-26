package pt.a2025121082.isec.safetysec.viewmodel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentChange
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
    val isAlertSent: Boolean = false,
    val isCancelWindowOpen: Boolean = false,
    val cancelSecondsLeft: Int = 0,
    val typedCancelCode: String? = null,
    val isFallDetectionEnabled: Boolean = false,
    val pendingAlerts: List<Alert> = emptyList(),
    val userInactivitySeconds: Int = 0,
    val inactivityAuthorized: Boolean = false,
    val inactivityDurationMin: Int = 0,
    val showInactivityAlertPopup: Int? = null,
    val isSecurityUpdateSuccessful: Boolean = false 
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
    private var inactivityJob: Job? = null
    private var isFirstLoad = true
    private val processedAlertIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            alertRepo.detectionEvents.collectLatest { type ->
                triggerAlertWithTimer(type)
            }
        }
    }

    fun triggerPanic() {
        triggerAlertWithTimer(RuleType.PANIC)
    }

    private fun triggerAlertWithTimer(type: RuleType) = viewModelScope.launch {
        val me = state.me ?: return@launch
        if (state.isCancelWindowOpen) return@launch

        state = state.copy(isCancelWindowOpen = true, cancelSecondsLeft = 10, typedCancelCode = null, isAlertSent = false)
        
        val tickerJob = viewModelScope.launch {
            while (state.cancelSecondsLeft > 0) {
                delay(1000)
                state = state.copy(cancelSecondsLeft = state.cancelSecondsLeft - 1)
            }
        }

        val sent = alertRepo.triggerAlert(
            ruleType = type,
            user = me,
            cancelCodeProvider = { state.typedCancelCode },
            locationProvider = { GeoPoint(0.0, 0.0) },
            videoUriProvider = { null }
        )
        
        tickerJob.cancel()
        state = state.copy(isCancelWindowOpen = false, cancelSecondsLeft = 0, typedCancelCode = null)
        
        if (!sent) {
            state = state.copy(error = "Alert cancelled.")
        } else {
            state = state.copy(isAlertSent = true)
        }
        refreshProtectedData(me.uid)
    }

    fun startMonitoringDashboard(monitorUid: String, context: Context?) {
        alertsListenerJob?.cancel()
        isFirstLoad = true
        processedAlertIds.clear()
        
        alertsListenerJob = viewModelScope.launch {
            db.collection("users").document(monitorUid).collection("alerts")
                .orderBy("timestamp", Query.Direction.DESCENDING).limit(20)
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    
                    val allAlerts = snapshot.toObjects(Alert::class.java)
                    state = state.copy(monitorAlerts = allAlerts)

                    val now = System.currentTimeMillis()
                    val newPending = state.pendingAlerts.toMutableList()

                    snapshot.documentChanges.forEach { diff ->
                        if (diff.type == DocumentChange.Type.ADDED) {
                            val alert = diff.document.toObject(Alert::class.java)
                            val isVeryRecent = (now - alert.timestamp) < 300_000L
                            
                            if ((!isFirstLoad || isVeryRecent) && !processedAlertIds.contains(alert.id)) {
                                newPending.add(alert)
                                processedAlertIds.add(alert.id)
                                if (context != null) showSystemNotification(context, alert)
                            }
                        }
                    }
                    
                    if (newPending.size != state.pendingAlerts.size) {
                        state = state.copy(pendingAlerts = newPending)
                    }
                    isFirstLoad = false
                }
            
            val me = authRepo.getUserProfile(monitorUid)
            state = state.copy(linkedProtectedUsers = me.protectedUsers.map { authRepo.getUserProfile(it) })
        }
    }

    fun dismissIncomingAlert() = viewModelScope.launch {
        val me = state.me ?: return@launch
        val alertToDismiss = state.pendingAlerts.firstOrNull() ?: return@launch
        alertRepo.deleteAlertFromMonitor(me.uid, alertToDismiss.id)
        state = state.copy(pendingAlerts = state.pendingAlerts.drop(1))
    }

    private fun showSystemNotification(context: Context, alert: Alert) {
        val channelId = "alerts_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(NotificationChannel(channelId, "Emergency Alerts", NotificationManager.IMPORTANCE_HIGH))
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("EMERGENCY: ${alert.type.displayName()}")
            .setContentText("${alert.protectedName} needs help!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(alert.id.hashCode(), notification)
    }

    fun loadMyProfile() = viewModelScope.launch {
        state = state.copy(isLoading = true, error = null)
        try {
            val me = authRepo.getUserProfile()
            state = state.copy(me = me, isLoading = false)
            if (me.roles.contains("Protected")) {
                refreshProtectedData(me.uid)
                startInactivityTimer()
            }
            if (me.roles.contains("Monitor")) startMonitoringDashboard(me.uid, null)
        } catch (t: Throwable) { state = state.copy(isLoading = false, error = t.message) }
    }

    fun updateInactivityDuration(minutesStr: String) = viewModelScope.launch {
        val minutes = minutesStr.toIntOrNull() ?: 15
        state = state.copy(isLoading = true, error = null, isSecurityUpdateSuccessful = false)
        try {
            authRepo.updateInactivityDuration(minutes)
            state = state.copy(isLoading = false, inactivityDurationMin = minutes, isSecurityUpdateSuccessful = true)
            loadMyProfile()
        } catch (t: Throwable) {
            state = state.copy(isLoading = false, error = t.message)
        }
    }

    fun updateCancelPin(pin: String) = viewModelScope.launch {
        state = state.copy(isLoading = true, error = null, isSecurityUpdateSuccessful = false)
        try {
            authRepo.updateAlertCancelCode(pin)
            state = state.copy(isLoading = false, isSecurityUpdateSuccessful = true)
            loadMyProfile()
        } catch (t: Throwable) {
            state = state.copy(isLoading = false, error = t.message)
        }
    }

    fun consumeSecurityUpdateSuccess() { 
        state = state.copy(isSecurityUpdateSuccessful = false) 
    }

    private suspend fun refreshProtectedData(protectedUid: String) {
        try {
            val bundles = monitoringRepo.getRulesForProtected(protectedUid)
            val windows = monitoringRepo.listTimeWindows(protectedUid)
            val history = alertRepo.getProtectedAlertHistory(protectedUid)
            val me = authRepo.getUserProfile(protectedUid)
            
            val inactivityBundle = bundles.find { it.authorizedTypes.contains(RuleType.INACTIVITY) }
            val duration = me.inactivityDurationMin

            state = state.copy(
                monitorRuleBundles = bundles, 
                timeWindows = windows, 
                myAlerts = history, 
                myLinkedMonitors = me.monitors.map { authRepo.getUserProfile(it) },
                inactivityAuthorized = inactivityBundle != null,
                inactivityDurationMin = duration
            )
        } catch (t: Throwable) {
            state = state.copy(error = t.message)
        }
    }

    private fun startInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (state.inactivityAuthorized) {
                    state = state.copy(userInactivitySeconds = state.userInactivitySeconds + 1)
                    if (state.userInactivitySeconds >= 60) {
                        triggerInactivityAlert()
                        resetInactivityTimer()
                    }
                } else {
                    state = state.copy(userInactivitySeconds = 0)
                }
            }
        }
    }

    fun resetInactivityTimer() {
        state = state.copy(userInactivitySeconds = 0)
    }

    private fun triggerInactivityAlert() = viewModelScope.launch {
        val me = state.me ?: return@launch
        val sent = alertRepo.triggerAlert(
            ruleType = RuleType.INACTIVITY,
            user = me,
            cancelCodeProvider = { null },
            locationProvider = { GeoPoint(0.0, 0.0) },
            videoUriProvider = { null }
        )
        if (sent) {
            state = state.copy(showInactivityAlertPopup = state.inactivityDurationMin)
            loadMyProfile()
        }
    }

    fun dismissInactivityPopup() {
        state = state.copy(showInactivityAlertPopup = null)
    }

    fun clear() { 
        alertsListenerJob?.cancel()
        inactivityJob?.cancel()
        state = AppUiState() 
    }
    fun consumeLinkingSuccess() { state = state.copy(isLinkingSuccessful = false) }
    fun consumeAlertSentSuccess() { state = state.copy(isAlertSent = false) }
    fun consumeRemovalSuccess() { state = state.copy(isRemovalSuccessful = false) }
    fun consumeRequestSuccess() { state = state.copy(isRequestSuccessful = false) }
    fun consumeAdditionSuccess() { state = state.copy(isAdditionSuccessful = false) }
    fun tryCancelAlert(typed: String) { state = state.copy(typedCancelCode = typed) }
    
    fun generateOtp() = viewModelScope.launch { try { val c = authRepo.generateAssociationCode(); state = state.copy(myOtp = c) } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun linkWithOtp(code: String) = viewModelScope.launch { try { authRepo.linkWithAssociationCode(code); state = state.copy(isLinkingSuccessful = true); loadMyProfile() } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun removeMonitor(monitorId: String) = viewModelScope.launch { try { authRepo.removeAssociation(monitorId, state.me!!.uid); state = state.copy(isRemovalSuccessful = true, myLinkedMonitors = state.myLinkedMonitors.filter { it.uid != monitorId }) } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun removeProtectedUser(protectedId: String) = viewModelScope.launch { try { authRepo.removeAssociation(state.me!!.uid, protectedId); state = state.copy(isRemovalSuccessful = true, linkedProtectedUsers = state.linkedProtectedUsers.filter { it.uid != protectedId }) } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun requestRulesForProtected(pUid: String, types: List<RuleType>, params: RuleParams) = viewModelScope.launch { try { val rules = types.map { MonitoringRule(it, params, true) }; monitoringRepo.requestRules(pUid, state.me!!.uid, rules); state = state.copy(isRequestSuccessful = true); loadRulesForProtected(pUid) } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun loadRulesForProtected(pUid: String) = viewModelScope.launch { try { val bundles = monitoringRepo.getRulesForProtected(pUid); state = state.copy(rulesForSelectedProtected = bundles.find { it.monitorId == state.me!!.uid }) } catch (t: Throwable) { state = state.copy(error = t.message) } }
    
    fun saveAuthorizations(mUid: String, auth: List<RuleType>, inactivityMin: Int?) = viewModelScope.launch { try { monitoringRepo.saveAuthorizations(state.me!!.uid, mUid, auth, inactivityMin); refreshProtectedData(state.me!!.uid) } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun addTimeWindow(days: List<Int>, s: Int, e: Int) = viewModelScope.launch { val w = TimeWindow(daysOfWeek = days, startHour = s, endHour = e); if (!w.checkValid()) return@launch; try { monitoringRepo.addTimeWindow(state.me!!.uid, w); state = state.copy(timeWindows = state.timeWindows + w, isAdditionSuccessful = true) } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun removeTimeWindow(wId: String) = viewModelScope.launch { try { monitoringRepo.deleteTimeWindow(state.me!!.uid, wId); state = state.copy(isRemovalSuccessful = true, timeWindows = state.timeWindows.filter { it.id != wId }) } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun toggleFallDetection(ctx: Context) { val n = !state.isFallDetectionEnabled; val i = Intent(ctx, FallDetectionService::class.java); if (n) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i) } else ctx.stopService(i); state = state.copy(isFallDetectionEnabled = n) }
}
