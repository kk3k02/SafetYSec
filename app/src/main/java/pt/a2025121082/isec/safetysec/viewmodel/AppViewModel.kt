package pt.a2025121082.isec.safetysec.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.video.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
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

    private var alertsListenerJob: Job? = null
    private var inactivityJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var recording: Recording? = null
    private var isHardwareBusy = false
    
    // Publiczne videoCapture by MainActivity mogło go użyć do bindToLifecycle (rozwiązuje błąd kompilacji)
    var videoCapture: VideoCapture<Recorder>? = null
        private set

    init {
        // Inicjalizujemy videoCapture raz na początku
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.LOWEST))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        viewModelScope.launch {
            alertRepo.detectionEvents.collectLatest { type ->
                triggerAlertWithTimer(type)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startVideoRecording(alertId: String) {
        Log.d("AppViewModel", "startVideoRecording: alertId=$alertId, busy=$isHardwareBusy, hasRec=${recording != null}")

        if (isHardwareBusy || recording != null) {
            if (recording != null && !isHardwareBusy) {
                stopVideoRecording()
            }
            viewModelScope.launch {
                delay(1000)
                startVideoRecording(alertId)
            }
            return
        }
        
        isHardwareBusy = true 
        state = state.copy(recordingSecondsLeft = 30, isRecordingPopupOpen = true, isCancelWindowOpen = false)
        
        viewModelScope.launch {
            try {
                // Poczekaj chwilę by MainActivity zdążyło zbindować use-case (jeśli popup właśnie się otworzył)
                delay(500)

                val videoFile = File(context.filesDir, "alert_${alertId}.mp4")
                val outputOptions = FileOutputOptions.Builder(videoFile).build()

                recording = videoCapture!!.output.prepareRecording(context, outputOptions)
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
                
                recordingTimerJob?.cancel()
                recordingTimerJob = launch {
                    while (state.recordingSecondsLeft > 0) {
                        delay(1000)
                        state = state.copy(recordingSecondsLeft = state.recordingSecondsLeft - 1)
                    }
                    stopVideoRecording()
                    state = state.copy(isRecordingPopupOpen = false)
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Camera recording failed", e)
                state = state.copy(isRecordingPopupOpen = false)
                recording = null
                isHardwareBusy = false
            }
        }
    }

    private fun handleRecordingFinalized(alertId: String, uri: Uri?) {
        Log.d("AppViewModel", "Finalized alertId=$alertId, hasUri=${uri != null}")
        
        recording = null
        isHardwareBusy = false

        viewModelScope.launch {
            if (uri != null) {
                try {
                    alertRepo.updateAlertWithVideo(alertId, state.me!!, uri)
                } catch (e: Exception) {
                    Log.e("AppViewModel", "Video upload failed", e)
                }
            }
            state.me?.let { refreshProtectedData(it.uid) }
        }
    }

    private fun stopVideoRecording() {
        if (recording != null) {
            isHardwareBusy = true
            recording?.stop()
        }
        recordingTimerJob?.cancel()
    }

    private fun triggerAlertWithTimer(type: RuleType) = viewModelScope.launch {
        val me = state.me ?: return@launch
        if (state.isCancelWindowOpen) return@launch
        
        state = state.copy(isCancelWindowOpen = true, cancelSecondsLeft = 10, typedCancelCode = null, cancelPinError = null, isAlertSent = false)
        
        val tickerJob = viewModelScope.launch {
            while (state.cancelSecondsLeft > 0) { delay(1000); state = state.copy(cancelSecondsLeft = state.cancelSecondsLeft - 1) }
        }

        val alertId = alertRepo.triggerAlert(type, me, { state.typedCancelCode }, { GeoPoint(0.0, 0.0) })
        
        tickerJob.cancel()
        state = state.copy(isCancelWindowOpen = false, cancelSecondsLeft = 0)
        
        if (alertId != null) {
            state = state.copy(isAlertSent = true)
            startVideoRecording(alertId)
        } else {
            state = state.copy(error = "Alert cancelled.")
        }
        refreshProtectedData(me.uid)
    }

    fun triggerPanic() { triggerAlertWithTimer(RuleType.PANIC) }
    
    fun tryCancelAlert(typed: String) {
        val correctPin = state.me?.alertCancelCode ?: "0000"
        if (typed == correctPin) state = state.copy(typedCancelCode = typed, cancelPinError = null)
        else state = state.copy(cancelPinError = "Incorrect PIN code. Try again.")
    }

    fun dismissIncomingAlert() = viewModelScope.launch {
        val me = state.me ?: return@launch
        val alertToDismiss = state.pendingAlerts.firstOrNull() ?: return@launch
        alertRepo.deleteAlertFromMonitor(me.uid, alertToDismiss.id)
        state = state.copy(pendingAlerts = state.pendingAlerts.drop(1))
    }

    fun startMonitoringDashboard(monitorUid: String, context: Context?) {
        alertsListenerJob?.cancel()
        alertsListenerJob = viewModelScope.launch {
            db.collection("users").document(monitorUid).collection("alerts")
                .orderBy("timestamp", Query.Direction.DESCENDING).limit(20)
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener
                    state = state.copy(monitorAlerts = snapshot.toObjects(Alert::class.java))
                    val now = System.currentTimeMillis()
                    val newPending = state.pendingAlerts.toMutableList()
                    snapshot.documentChanges.forEach { diff ->
                        if (diff.type == DocumentChange.Type.ADDED) {
                            val alert = diff.document.toObject(Alert::class.java)
                            if ((now - alert.timestamp) < 300_000L) newPending.add(alert)
                        }
                    }
                    if (newPending.size != state.pendingAlerts.size) state = state.copy(pendingAlerts = newPending)
                }
            val me = authRepo.getUserProfile(monitorUid)
            state = state.copy(linkedProtectedUsers = me.protectedUsers.map { authRepo.getUserProfile(it) })
        }
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
        } catch (t: Throwable) { state = state.copy(isLoading = false, error = t.message) }
    }

    fun updateCancelPin(pin: String) = viewModelScope.launch {
        state = state.copy(isLoading = true, error = null, isSecurityUpdateSuccessful = false)
        try {
            authRepo.updateAlertCancelCode(pin)
            state = state.copy(isLoading = false, isSecurityUpdateSuccessful = true)
            loadMyProfile()
        } catch (t: Throwable) { state = state.copy(isLoading = false, error = t.message) }
    }

    fun consumeSecurityUpdateSuccess() { state = state.copy(isSecurityUpdateSuccessful = false) }

    private suspend fun refreshProtectedData(protectedUid: String) {
        try {
            val bundles = monitoringRepo.getRulesForProtected(protectedUid)
            val windows = monitoringRepo.listTimeWindows(protectedUid)
            val history = alertRepo.getProtectedAlertHistory(protectedUid)
            val me = authRepo.getUserProfile(protectedUid)
            state = state.copy(
                monitorRuleBundles = bundles, 
                timeWindows = windows, 
                myAlerts = history, 
                myLinkedMonitors = me.monitors.map { authRepo.getUserProfile(it) },
                inactivityAuthorized = bundles.any { it.authorizedTypes.contains(RuleType.INACTIVITY) },
                inactivityDurationMin = me.inactivityDurationMin
            )
        } catch (t: Throwable) { state = state.copy(error = t.message) }
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

    fun resetInactivityTimer() { state = state.copy(userInactivitySeconds = 0) }

    private fun triggerInactivityAlert() = viewModelScope.launch {
        val me = state.me ?: return@launch
        val alertId = alertRepo.triggerAlert(RuleType.INACTIVITY, me, { null }, { GeoPoint(0.0, 0.0) })
        if (alertId != null) {
            state = state.copy(showInactivityAlertPopup = state.inactivityDurationMin)
            loadMyProfile()
        }
    }

    fun dismissInactivityPopup() { state = state.copy(showInactivityAlertPopup = null) }
    fun clear() { state = AppUiState() }
    fun consumeLinkingSuccess() { state = state.copy(isLinkingSuccessful = false) }
    fun consumeAlertSentSuccess() { state = state.copy(isAlertSent = false) }
    fun consumeRemovalSuccess() { state = state.copy(isRemovalSuccessful = false) }
    fun consumeRequestSuccess() { state = state.copy(isRequestSuccessful = false) }
    fun consumeAdditionSuccess() { state = state.copy(isAdditionSuccessful = false) }

    fun generateOtp() = viewModelScope.launch { try { val c = authRepo.generateAssociationCode(); state = state.copy(myOtp = c) } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun linkWithOtp(code: String) = viewModelScope.launch { try { authRepo.linkWithAssociationCode(code); state = state.copy(isLinkingSuccessful = true); loadMyProfile() } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun removeMonitor(monitorId: String) = viewModelScope.launch { try { authRepo.removeAssociation(monitorId, state.me!!.uid); state = state.copy(isRemovalSuccessful = true, myLinkedMonitors = state.myLinkedMonitors.filter { it.uid != monitorId }) } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun removeProtectedUser(protectedId: String) = viewModelScope.launch { try { authRepo.removeAssociation(state.me!!.uid, protectedId); state = state.copy(isRemovalSuccessful = true, linkedProtectedUsers = state.linkedProtectedUsers.filter { it.uid != protectedId }) } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun requestRulesForProtected(pUid: String, types: List<RuleType>, params: RuleParams) = viewModelScope.launch { try { val rules = types.map { MonitoringRule(it, params, true) }; monitoringRepo.requestRules(pUid, state.me!!.uid, rules); state = state.copy(isRequestSuccessful = true); loadRulesForProtected(pUid) } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun loadRulesForProtected(pUid: String) = viewModelScope.launch { try { val bundles = monitoringRepo.getRulesForProtected(pUid); state = state.copy(rulesForSelectedProtected = bundles.find { it.monitorId == state.me!!.uid }) } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun saveAuthorizations(mUid: String, auth: List<RuleType>, inactivityMin: Int?) = viewModelScope.launch { try { monitoringRepo.saveAuthorizations(state.me!!.uid, mUid, auth, inactivityMin); refreshProtectedData(state.me!!.uid) } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun addTimeWindow(days: List<Int>, s: Int, e: Int) = viewModelScope.launch { val w = TimeWindow(daysOfWeek = days, startHour = s, endHour = e); if (!w.checkValid()) return@launch; try { monitoringRepo.addTimeWindow(state.me!!.uid, w); state = state.copy(timeWindows = state.timeWindows + w, isAdditionSuccessful = true) } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun removeTimeWindow(wId: String) = viewModelScope.launch { try { monitoringRepo.deleteTimeWindow(state.me!!.uid, wId); state = state.copy(isRemovalSuccessful = true, timeWindows = state.timeWindows.filter { it.id != wId }) } catch (t: Throwable) { state = state.copy(error = t.message) } }
    fun toggleFallDetection(ctx: Context) { val n = !state.isFallDetectionEnabled; val i = android.content.Intent(ctx, FallDetectionService::class.java); if (n) { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i) } else ctx.stopService(i); state = state.copy(isFallDetectionEnabled = n) }
    
    fun dismissRecordingPopup() {
        state = state.copy(isRecordingPopupOpen = false)
        stopVideoRecording()
    }
}
