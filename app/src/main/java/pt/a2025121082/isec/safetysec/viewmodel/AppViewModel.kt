package pt.a2025121082.isec.safetysec.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.video.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pt.a2025121082.isec.safetysec.data.model.*
import pt.a2025121082.isec.safetysec.data.repository.AlertRepository
import pt.a2025121082.isec.safetysec.data.repository.AuthRepository
import pt.a2025121082.isec.safetysec.data.repository.MonitorRulesBundle
import pt.a2025121082.isec.safetysec.data.repository.MonitoringRepository
import pt.a2025121082.isec.safetysec.util.FallDetectionService
import java.io.File
import javax.inject.Inject

data class AppUiState(
    val me: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val myAlerts: List<Alert> = emptyList(),
    val monitorAlerts: List<Alert> = emptyList(),
    val monitorRuleBundles: List<MonitorRulesBundle> = emptyList(),
    val timeWindows: List<TimeWindow> = emptyList(),
    val myLinkedMonitors: List<User> = emptyList(),
    val linkedProtectedUsers: List<User> = emptyList(),
    val myOtp: String? = null,
    val isLinkingSuccessful: Boolean = false,
    val isAlertSent: Boolean = false,
    val isRemovalSuccessful: Boolean = false,
    val isRequestSuccessful: Boolean = false,
    val isAdditionSuccessful: Boolean = false,
    val rulesForSelectedProtected: MonitorRulesBundle? = null,
    val isCancelWindowOpen: Boolean = false,
    val cancelSecondsLeft: Int = 0,
    val typedCancelCode: String? = null,
    val cancelPinError: String? = null,
    val isFallDetectionEnabled: Boolean = false,
    val pendingAlerts: List<Alert> = emptyList(),
    val userInactivitySeconds: Int = 0,
    val inactivityAuthorized: Boolean = false,
    val inactivityDurationMin: Int = 0,
    val showInactivityAlertPopup: Int? = null,
    val isSecurityUpdateSuccessful: Boolean = false,
    val isRecordingPopupOpen: Boolean = false,
    val recordingSecondsLeft: Int = 0
)

@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepo: AuthRepository,
    private val monitoringRepo: MonitoringRepository,
    private val alertRepo: AlertRepository,
    private val db: FirebaseFirestore
) : ViewModel() {

    var state by mutableStateOf(AppUiState())
        private set

    private var inactivityJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var recording: Recording? = null
    
    private var profileListener: ListenerRegistration? = null
    private var myAlertsListener: ListenerRegistration? = null
    private var monitorPopupListener: ListenerRegistration? = null
    private val protectedAlertsListeners = mutableMapOf<String, ListenerRegistration>()
    private val alertsMap = mutableMapOf<String, List<Alert>>()

    // SINGLE INSTANCE VideoCapture - UI to zbindowuje raz
    val videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.LOWEST))
            .build()
    )

    private var currentAlertIdForRecording: String? = null

    init {
        viewModelScope.launch {
            alertRepo.detectionEvents.collectLatest { type ->
                triggerAlertWithTimer(type)
            }
        }
    }

    /**
     * Called by MainActivity when it's sure that videoCapture is bound to lifecycle.
     */
    @SuppressLint("MissingPermission")
    fun startActualRecording() {
        val alertId = currentAlertIdForRecording ?: return
        if (recording != null) return

        Log.d("AppViewModel", "Starting recording for alert: $alertId")
        val videoFile = File(context.filesDir, "alert_${alertId}.mp4")
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        recording = videoCapture.output.prepareRecording(context, outputOptions)
            .apply {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    val uri = if (!event.hasError()) Uri.fromFile(videoFile) else null
                    handleRecordingFinalized(alertId, uri)
                }
            }
        
        // Start countdown
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (state.recordingSecondsLeft > 0) {
                delay(1000)
                state = state.copy(recordingSecondsLeft = state.recordingSecondsLeft - 1)
            }
            stopVideoRecording()
            state = state.copy(isRecordingPopupOpen = false)
        }
    }

    private fun handleRecordingFinalized(alertId: String, uri: Uri?) {
        recording = null
        currentAlertIdForRecording = null
        viewModelScope.launch {
            if (uri != null) {
                try {
                    alertRepo.updateAlertWithVideo(alertId, state.me!!, uri)
                } catch (e: Exception) { Log.e("AppViewModel", "Upload failed", e) }
            }
        }
    }

    fun stopVideoRecording() {
        recording?.stop()
        recordingTimerJob?.cancel()
    }

    private fun triggerAlertWithTimer(type: RuleType) = viewModelScope.launch {
        val me = state.me ?: return@launch
        if (state.isCancelWindowOpen || state.isRecordingPopupOpen) return@launch
        
        state = state.copy(isCancelWindowOpen = true, cancelSecondsLeft = 10, typedCancelCode = null, cancelPinError = null, isAlertSent = false)
        val tickerJob = viewModelScope.launch {
            while (state.cancelSecondsLeft > 0) { delay(1000); state = state.copy(cancelSecondsLeft = state.cancelSecondsLeft - 1) }
        }
        val alertId = alertRepo.triggerAlert(type, me, { state.typedCancelCode }, { GeoPoint(0.0, 0.0) })
        tickerJob.cancel()
        
        state = state.copy(isCancelWindowOpen = false, cancelSecondsLeft = 0)
        
        if (alertId != null) {
            currentAlertIdForRecording = alertId
            state = state.copy(isAlertSent = true, recordingSecondsLeft = 30, isRecordingPopupOpen = true)
        }
    }

    fun triggerPanic() { triggerAlertWithTimer(RuleType.PANIC) }

    fun startMonitoringDashboard(monitorUid: String, context: Context?) {
        if (monitorPopupListener == null) {
            monitorPopupListener = db.collection("users").document(monitorUid).collection("alerts")
                .addSnapshotListener { snapshot, _ ->
                    val now = System.currentTimeMillis()
                    val newPending = state.pendingAlerts.toMutableList()
                    snapshot?.documentChanges?.forEach { diff ->
                        if (diff.type == DocumentChange.Type.ADDED) {
                            val alert = diff.document.toObject(Alert::class.java).copy(id = diff.document.id)
                            if ((now - alert.timestamp) < 120_000L && !newPending.any { it.id == alert.id }) {
                                newPending.add(alert)
                            }
                        }
                    }
                    state = state.copy(pendingAlerts = newPending)
                }
        }

        val pIds = state.me?.protectedUsers ?: emptyList()
        pIds.forEach { pUid ->
            if (!protectedAlertsListeners.containsKey(pUid)) {
                protectedAlertsListeners[pUid] = db.collection("users").document(pUid).collection("my_alerts")
                    .orderBy("timestamp", Query.Direction.DESCENDING).limit(20)
                    .addSnapshotListener { snap, _ ->
                        alertsMap[pUid] = snap?.documents?.mapNotNull { it.toObject(Alert::class.java)?.copy(id = it.id) } ?: emptyList()
                        state = state.copy(monitorAlerts = alertsMap.values.flatten().sortedByDescending { it.timestamp })
                    }
            }
        }
        viewModelScope.launch {
            try {
                val me = authRepo.getUserProfile(monitorUid)
                state = state.copy(linkedProtectedUsers = me.protectedUsers.map { authRepo.getUserProfile(it) })
            } catch (e: Exception) { }
        }
    }

    fun dismissIncomingAlert() = viewModelScope.launch {
        val me = state.me ?: return@launch
        val alert = state.pendingAlerts.firstOrNull() ?: return@launch
        alertRepo.deleteAlertFromMonitor(me.uid, alert.id)
        state = state.copy(pendingAlerts = state.pendingAlerts.drop(1))
    }

    fun loadMyProfile() = viewModelScope.launch {
        state = state.copy(isLoading = true)
        try {
            val uid = authRepo.getCurrentUid() ?: return@launch
            profileListener?.remove()
            profileListener = db.collection("users").document(uid).addSnapshotListener { snap, _ ->
                val me = snap?.toObject(User::class.java)
                if (me != null) {
                    state = state.copy(me = me, isLoading = false)
                    if (me.roles.contains("Protected")) {
                        startMyAlertsListener(me.uid)
                        viewModelScope.launch { refreshProtectedMetadata(me.uid) }
                        startInactivityTimer()
                    }
                    if (me.roles.contains("Monitor")) startMonitoringDashboard(me.uid, null)
                }
            }
        } catch (t: Throwable) { state = state.copy(isLoading = false, error = t.message) }
    }

    private fun startMyAlertsListener(uid: String) {
        myAlertsListener?.remove()
        myAlertsListener = db.collection("users").document(uid).collection("my_alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener { snap, _ ->
                state = state.copy(myAlerts = snap?.documents?.mapNotNull { it.toObject(Alert::class.java)?.copy(id = it.id) } ?: emptyList())
            }
    }

    private suspend fun refreshProtectedMetadata(uid: String) {
        try {
            val bundles = monitoringRepo.getRulesForProtected(uid)
            val windows = monitoringRepo.listTimeWindows(uid)
            val me = authRepo.getUserProfile(uid)
            state = state.copy(
                monitorRuleBundles = bundles, 
                timeWindows = windows, 
                myLinkedMonitors = me.monitors.map { authRepo.getUserProfile(it) },
                inactivityAuthorized = bundles.any { it.authorizedTypes.contains(RuleType.INACTIVITY) },
                inactivityDurationMin = me.inactivityDurationMin
            )
        } catch (e: Exception) { }
    }

    fun resetInactivityTimer() { state = state.copy(userInactivitySeconds = 0) }
    fun updateInactivityDuration(m: String) = viewModelScope.launch { try { authRepo.updateInactivityDuration(m.toIntOrNull() ?: 15); state = state.copy(isSecurityUpdateSuccessful = true) } catch (e: Exception) {} }
    fun updateCancelPin(p: String) = viewModelScope.launch { try { authRepo.updateAlertCancelCode(p); state = state.copy(isSecurityUpdateSuccessful = true) } catch (e: Exception) {} }
    fun tryCancelAlert(typed: String) {
        val correct = state.me?.alertCancelCode ?: "0000"
        if (typed == correct) state = state.copy(typedCancelCode = typed, cancelPinError = null)
        else state = state.copy(cancelPinError = "Incorrect PIN.")
    }

    fun clear() {
        profileListener?.remove()
        myAlertsListener?.remove()
        monitorPopupListener?.remove()
        protectedAlertsListeners.values.forEach { it.remove() }
        state = AppUiState()
    }

    fun consumeSecurityUpdateSuccess() { state = state.copy(isSecurityUpdateSuccessful = false) }
    fun consumeLinkingSuccess() { state = state.copy(isLinkingSuccessful = false) }
    fun consumeAlertSentSuccess() { state = state.copy(isAlertSent = false) }
    fun consumeRemovalSuccess() { state = state.copy(isRemovalSuccessful = false) }
    fun consumeRequestSuccess() { state = state.copy(isRequestSuccessful = false) }
    fun consumeAdditionSuccess() { state = state.copy(isAdditionSuccessful = false) }

    fun generateOtp() = viewModelScope.launch { try { state = state.copy(myOtp = authRepo.generateAssociationCode()) } catch (e: Exception) {} }
    fun linkWithOtp(code: String) = viewModelScope.launch { try { authRepo.linkWithAssociationCode(code); state = state.copy(isLinkingSuccessful = true) } catch (e: Exception) {} }
    fun removeMonitor(id: String) = viewModelScope.launch { try { authRepo.removeAssociation(id, state.me!!.uid); state = state.copy(isRemovalSuccessful = true) } catch (e: Exception) {} }
    fun removeProtectedUser(id: String) = viewModelScope.launch { try { authRepo.removeAssociation(state.me!!.uid, id); state = state.copy(isRemovalSuccessful = true) } catch (e: Exception) {} }
    fun requestRulesForProtected(p: String, t: List<RuleType>, r: RuleParams) = viewModelScope.launch { try { monitoringRepo.requestRules(p, state.me!!.uid, t.map { MonitoringRule(it, r, true) }); state = state.copy(isRequestSuccessful = true) } catch (e: Exception) {} }
    fun loadRulesForProtected(p: String) = viewModelScope.launch { try { state = state.copy(rulesForSelectedProtected = monitoringRepo.getRulesForProtected(p).find { it.monitorId == state.me!!.uid }) } catch (e: Exception) {} }
    fun saveAuthorizations(m: String, a: List<RuleType>, i: Int?) = viewModelScope.launch { try { monitoringRepo.saveAuthorizations(state.me!!.uid, m, a, i) } catch (e: Exception) {} }
    fun addTimeWindow(d: List<Int>, s: Int, e: Int) = viewModelScope.launch { try { monitoringRepo.addTimeWindow(state.me!!.uid, TimeWindow(daysOfWeek = d, startHour = s, endHour = e)); state = state.copy(isAdditionSuccessful = true) } catch (e: Exception) {} }
    fun removeTimeWindow(id: String) = viewModelScope.launch { try { monitoringRepo.deleteTimeWindow(state.me!!.uid, id); state = state.copy(isRemovalSuccessful = true) } catch (e: Exception) {} }
    fun toggleFallDetection(ctx: Context) { val n = !state.isFallDetectionEnabled; val i = android.content.Intent(ctx, FallDetectionService::class.java); if (n) { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i) } else ctx.stopService(i); state = state.copy(isFallDetectionEnabled = n) }
    
    fun dismissRecordingPopup() {
        state = state.copy(isRecordingPopupOpen = false)
        stopVideoRecording()
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
                } else { state = state.copy(userInactivitySeconds = 0) }
            }
        }
    }

    private fun triggerInactivityAlert() = viewModelScope.launch {
        val me = state.me ?: return@launch
        if (alertRepo.triggerAlert(RuleType.INACTIVITY, me, { null }, { GeoPoint(0.0, 0.0) }) != null) {
            state = state.copy(showInactivityAlertPopup = state.inactivityDurationMin)
        }
    }

    fun dismissInactivityPopup() { state = state.copy(showInactivityAlertPopup = null) }

    override fun onCleared() {
        super.onCleared()
        clear()
    }
}
