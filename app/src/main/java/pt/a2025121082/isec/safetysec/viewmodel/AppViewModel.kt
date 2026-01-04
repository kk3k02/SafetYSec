package pt.a2025121082.isec.safetysec.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.tasks.await
import pt.a2025121082.isec.safetysec.data.model.*
import pt.a2025121082.isec.safetysec.data.repository.AlertRepository
import pt.a2025121082.isec.safetysec.data.repository.AuthRepository
import pt.a2025121082.isec.safetysec.data.repository.MonitorRulesBundle
import pt.a2025121082.isec.safetysec.data.repository.MonitoringRepository
import pt.a2025121082.isec.safetysec.util.FallDetectionService
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * UI State for the entire application, tracking user profiles, alerts, 
 * monitoring rules, and temporary UI flags (loading, errors, popups).
 */
data class AppUiState(
    val me: User? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val myAlerts: List<Alert> = emptyList(),
    val monitorAlerts: List<Alert> = emptyList(),
    val monitorAlertsClearedAt: Long = 0L,
    val monitorRuleBundles: List<MonitorRulesBundle> = emptyList(),
    val timeWindows: List<TimeWindow> = emptyList(),
    val myLinkedMonitors: List<User> = emptyList(),
    val linkedProtectedUsers: List<User> = emptyList(),
    val myOtp: String? = null,
    val isLinkingSuccessful: Boolean = false,
    val linkError: String? = null,
    val isAlertSent: Boolean = false,
    val isRemovalSuccessful: Boolean = false,
    val isRequestSuccessful: Boolean = false,
    val isAdditionSuccessful: Boolean = false,
    val rulesForSelectedProtected: MonitorRulesBundle? = null,
    val shownRuleRequestKeys: Set<String> = emptySet(),
    val isCancelWindowOpen: Boolean = false,
    val cancelAlertType: RuleType? = null,
    val cancelSecondsLeft: Int = 0,
    val typedCancelCode: String? = null,
    val cancelPinError: String? = null,
    val isFallDetectionEnabled: Boolean = false,
    val pendingAlerts: List<Alert> = emptyList(),
    val userInactivitySeconds: Int = 0,
    val inactivityAuthorized: Boolean = false,
    val inactivityDurationMin: Int = 0,
    val isSecurityUpdateSuccessful: Boolean = false,
    val isRecordingPopupOpen: Boolean = false,
    val recordingSecondsLeft: Int = 0,
    val activeMode: AppMode = AppMode.PROTECTED
)

/** Operating modes for the app: Protected (monitored) or Monitor (supervising). */
enum class AppMode { PROTECTED, MONITOR }

/**
 * Core ViewModel of the application. Orchestrates complex business logic, 
 * background monitoring services, real-time Firebase listeners, and emergency flows.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepo: AuthRepository,
    private val monitoringRepo: MonitoringRepository,
    private val alertRepo: AlertRepository,
    private val db: FirebaseFirestore
) : ViewModel() {

    /** Current UI state, observable by Compose components. */
    var state by mutableStateOf(AppUiState())
        private set

    // Coroutine Jobs for various background monitors
    private var inactivityJob: Job? = null
    private var geofenceJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var recording: Recording? = null
    private var protectedMonitoringDelayJob: Job? = null
    private var protectedMonitoringReady: Boolean = false
    private var accidentJob: Job? = null
    private var lastAccidentSampleSpeedKmh: Float? = null
    private var lastAccidentSampleAtMs: Long? = null
    private var lastAccidentAlertAt: Long = 0L

    // Real-time Firebase Firestore listeners
    private var profileListener: ListenerRegistration? = null
    private var myAlertsListener: ListenerRegistration? = null
    private var rulesByMonitorListener: ListenerRegistration? = null
    private var selectedProtectedRulesListener: ListenerRegistration? = null
    private var monitorPopupListener: ListenerRegistration? = null
    private val protectedAlertsListeners = mutableMapOf<String, ListenerRegistration>()
    private val alertsMap = mutableMapOf<String, List<Alert>>()

    // Functional interfaces for dynamic data injection (Location/Speed)
    private var locationProvider: (suspend () -> GeoPoint?)? = null
    private var speedProvider: (suspend () -> Float?)? = null

    /** Shared VideoCapture instance for CameraX recording. */
    val videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.LOWEST))
            .build()
    )

    private var currentAlertIdForRecording: String? = null
    private var lastGeofenceInside: Boolean? = null
    private var lastSpeedOverLimit: Boolean? = null
    private var speedJob: Job? = null
    private var pendingInitialSpeedAlert: Boolean = false
    private var lastLoggedSpeedCheckAt: Long = 0L
    private var lastSpeedAlertAt: Long = 0L

    init {
        // Collect detection events (e.g., fall detected) and trigger the alert UI
        viewModelScope.launch {
            alertRepo.detectionEvents.collectLatest { type ->
                triggerAlertWithTimer(type)
            }
        }
    }

    // --- Data Providers & Location ---

    fun setLocationProvider(provider: suspend () -> GeoPoint?) {
        this.locationProvider = provider
    }

    fun setSpeedProvider(provider: suspend () -> Float?) {
        this.speedProvider = provider
    }

    suspend fun getCurrentLocation(): GeoPoint? = locationProvider?.invoke()

    // --- Camera & Video Recording Logic ---

    /**
     * Starts the actual video recording process using CameraX.
     * Triggered automatically after a successful alert dispatch and hardware binding.
     */
    @SuppressLint("MissingPermission")
    fun startActualRecording() {
        if (state.activeMode != AppMode.PROTECTED) return
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

        // Manage the recording duration countdown
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

    /** Cleanup and upload tasks after a recording finishes. */
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

    // --- Emergency Alert Flow ---

    /**
     * Orchestrates the emergency alert countdown.
     * Shows a 10s cancellation window to the user before notifying monitors.
     */
    private fun triggerAlertWithTimer(type: RuleType) = viewModelScope.launch {
        val me = state.me ?: return@launch
        if (!me.roles.contains("Protected") || state.activeMode != AppMode.PROTECTED) return@launch
        if (!protectedMonitoringReady) return@launch
        if (state.isCancelWindowOpen || state.isRecordingPopupOpen) return@launch

        state = state.copy(
            isCancelWindowOpen = true,
            cancelAlertType = type,
            cancelSecondsLeft = 10,
            typedCancelCode = null,
            cancelPinError = null,
            isAlertSent = false
        )
        
        // Ticker for the UI countdown
        val tickerJob = viewModelScope.launch {
            while (state.cancelSecondsLeft > 0) { delay(1000); state = state.copy(cancelSecondsLeft = state.cancelSecondsLeft - 1) }
        }

        // Pass control to the repository to handle PIN check and server communication
        val alertId = alertRepo.triggerAlert(
            ruleType = type,
            user = me,
            cancelCodeProvider = { state.typedCancelCode },
            locationProvider = { locationProvider?.invoke() }
        )
        tickerJob.cancel()

        state = state.copy(isCancelWindowOpen = false, cancelAlertType = null, cancelSecondsLeft = 0)

        // If the alert was NOT cancelled, initiate recording popup
        if (alertId != null) {
            currentAlertIdForRecording = alertId
            state = state.copy(isAlertSent = true, recordingSecondsLeft = 30, isRecordingPopupOpen = true)
        } else if (type == RuleType.SPEED) {
            // Reset state to allow immediate re-trigger if speed remains high
            lastSpeedOverLimit = false
            pendingInitialSpeedAlert = false
        }
    }

    fun triggerPanic() { triggerAlertWithTimer(RuleType.PANIC) }

    // --- Monitor Dashboard & Real-time Alerts ---

    /** Sets up real-time monitoring of alerts for all users supervised by this Monitor. */
    fun startMonitoringDashboard(monitorUid: String) {
        // Global listener for new alerts (popups)
        if (monitorPopupListener == null) {
            monitorPopupListener = db.collection("users").document(monitorUid).collection("alerts")
                .addSnapshotListener { snapshot, _ ->
                    val now = System.currentTimeMillis()
                    val newPending = state.pendingAlerts.toMutableList()
                    snapshot?.documentChanges?.forEach { diff ->
                        if (diff.type == DocumentChange.Type.ADDED) {
                            val alert = diff.document.toObject(Alert::class.java).copy(id = diff.document.id)
                            // Show popup only for very recent alerts
                            if ((now - alert.timestamp) < 120_000L && !newPending.any { it.id == alert.id }) {
                                newPending.add(alert)
                            }
                        }
                    }
                    state = state.copy(pendingAlerts = newPending)
                }
        }

        val pIds = state.me?.protectedUsers ?: emptyList()

        // Sync listeners for protected users
        val currentKeys = protectedAlertsListeners.keys.toSet()
        val toRemove = currentKeys - pIds.toSet()
        toRemove.forEach { pUid ->
            protectedAlertsListeners[pUid]?.remove()
            protectedAlertsListeners.remove(pUid)
            alertsMap.remove(pUid)
        }

        pIds.forEach { pUid ->
            if (!protectedAlertsListeners.containsKey(pUid)) {
                protectedAlertsListeners[pUid] = db.collection("users").document(pUid).collection("my_alerts")
                    .orderBy("timestamp", Query.Direction.DESCENDING).limit(20)
                    .addSnapshotListener(MetadataChanges.INCLUDE) { snap, _ ->
                        if (snap == null) return@addSnapshotListener
                        if (snap.metadata.isFromCache && snap.isEmpty) return@addSnapshotListener
                        alertsMap[pUid] = snap.documents.mapNotNull { it.toObject(Alert::class.java)?.copy(id = it.id) }
                        state = state.copy(monitorAlerts = filteredMonitorAlerts(alertsMap.values.flatten()))
                    }
                viewModelScope.launch {
                    try {
                        val snap = db.collection("users").document(pUid).collection("my_alerts")
                            .orderBy("timestamp", Query.Direction.DESCENDING).limit(20).get().await()
                        alertsMap[pUid] = snap.documents.mapNotNull { it.toObject(Alert::class.java)?.copy(id = it.id) }
                        state = state.copy(monitorAlerts = filteredMonitorAlerts(alertsMap.values.flatten()))
                    } catch (e: Exception) { }
                }
            }
        }

        state = state.copy(monitorAlerts = filteredMonitorAlerts(alertsMap.values.flatten()))

        viewModelScope.launch {
            try {
                val me = authRepo.getUserProfile(monitorUid)
                state = state.copy(linkedProtectedUsers = me.protectedUsers.map { authRepo.getUserProfile(it) })
            } catch (e: Exception) { }
        }
    }

    /** Dismisses an incoming alert popup and removes the temporary record from the monitor's collection. */
    fun dismissIncomingAlert() = viewModelScope.launch {
        val me = state.me ?: return@launch
        val alert = state.pendingAlerts.firstOrNull() ?: return@launch
        alertRepo.deleteAlertFromMonitor(me.uid, alert.id)
        state = state.copy(pendingAlerts = state.pendingAlerts.drop(1))
    }

    fun clearMonitorAlertsHistory() = viewModelScope.launch {
        val clearedAt = System.currentTimeMillis()
        state = state.copy(
            monitorAlertsClearedAt = clearedAt,
            monitorAlerts = emptyList()
        )
        try {
            authRepo.updateMonitorAlertsClearedAt(clearedAt)
        } catch (e: Exception) { }
    }

    // --- Protected History & Profile ---

    fun refreshMyAlertsHistory() = viewModelScope.launch {
        val uid = state.me?.uid ?: authRepo.getCurrentUid() ?: return@launch
        startMyAlertsListener(uid)
        try {
            state = state.copy(myAlerts = alertRepo.getProtectedAlertHistory(uid))
        } catch (e: Exception) { }
    }

    fun clearMyAlertsHistory() = viewModelScope.launch {
        val uid = state.me?.uid ?: authRepo.getCurrentUid() ?: return@launch
        try {
            alertRepo.clearProtectedAlertHistory(uid)
            state = state.copy(myAlerts = emptyList())
        } catch (e: Exception) { }
    }

    /** 
     * Loads the current user's profile and sets up the app based on their roles.
     * Manages role-specific listeners (Protected vs Monitor).
     */
    fun loadMyProfile() = viewModelScope.launch {
        state = state.copy(isLoading = true)
        try {
            val uid = authRepo.getCurrentUid() ?: return@launch
            profileListener?.remove()
            profileListener = db.collection("users").document(uid).addSnapshotListener { snap, _ ->
                val me = snap?.toObject(User::class.java)
                if (me != null) {
                    val wasMonitor = state.me?.roles?.contains("Monitor") == true
                    val isMonitor = me.roles.contains("Monitor")

                    state = state.copy(
                        me = me,
                        isLoading = false,
                        monitorAlertsClearedAt = me.monitorAlertsClearedAt
                    )

                    if (me.roles.contains("Protected")) {
                        Log.d("AppViewModel", "Protected role detected, starting rules listener for ${me.uid}")
                        startMyAlertsListener(me.uid)
                        viewModelScope.launch {
                            try {
                                state = state.copy(myAlerts = alertRepo.getProtectedAlertHistory(me.uid))
                            } catch (e: Exception) { }
                        }
                        startRulesByMonitorListener(me.uid)
                        viewModelScope.launch { refreshProtectedMetadata(me.uid) }
                        scheduleProtectedMonitoringStart()
                    }

                    if (isMonitor) {
                        viewModelScope.launch {
                            state = state.copy(linkedProtectedUsers = me.protectedUsers.map { authRepo.getUserProfile(it) })
                        }
                        viewModelScope.launch {
                            try {
                                val alerts = me.protectedUsers.flatMap { uid ->
                                    alertRepo.getProtectedAlertHistory(uid)
                                }
                                state = state.copy(monitorAlerts = filteredMonitorAlerts(alerts))
                            } catch (e: Exception) { }
                        }
                        startMonitoringDashboard(me.uid)
                    } else if (wasMonitor) {
                        stopMonitoringDashboard()
                    }
                }
            }
        } catch (t: Throwable) { state = state.copy(isLoading = false, error = t.message) }
    }

    private fun stopMonitoringDashboard() {
        monitorPopupListener?.remove()
        monitorPopupListener = null
        protectedAlertsListeners.values.forEach { it.remove() }
        protectedAlertsListeners.clear()
        alertsMap.clear()
        state = state.copy(monitorAlerts = emptyList(), linkedProtectedUsers = emptyList(), pendingAlerts = emptyList())
    }

    private fun filteredMonitorAlerts(alerts: List<Alert>): List<Alert> {
        val clearedAt = state.monitorAlertsClearedAt
        return alerts.filter { it.timestamp > clearedAt }.sortedByDescending { it.timestamp }
    }

    private fun startMyAlertsListener(uid: String) {
        myAlertsListener?.remove()
        myAlertsListener = db.collection("users").document(uid).collection("my_alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, _ ->
                if (snap == null) return@addSnapshotListener
                if (snap.metadata.isFromCache && snap.isEmpty) return@addSnapshotListener
                state = state.copy(myAlerts = snap.documents.mapNotNull { it.toObject(Alert::class.java)?.copy(id = it.id) })
            }
        viewModelScope.launch {
            try {
                val snap = db.collection("users").document(uid).collection("my_alerts")
                    .orderBy("timestamp", Query.Direction.DESCENDING).limit(30).get().await()
                state = state.copy(myAlerts = snap.documents.mapNotNull { it.toObject(Alert::class.java)?.copy(id = it.id) })
            } catch (e: Exception) { }
        }
    }

    // --- Background Monitors (Geofence, Speed, Inactivity, Accident) ---

    private fun startGeofenceMonitor() {
        if (geofenceJob != null) return
        geofenceJob = viewModelScope.launch {
            checkGeofenceOnce()
            while (true) {
                delay(300_000) // Check every 5 minutes
                checkGeofenceOnce()
            }
        }
    }

    private fun startSpeedMonitor() {
        if (speedJob != null) return
        speedJob = viewModelScope.launch {
            checkSpeedOnce()
            while (true) {
                delay(15000) // Check every 15 seconds
                checkSpeedOnce()
            }
        }
    }

    private fun startAccidentMonitor() {
        if (accidentJob != null) return
        accidentJob = viewModelScope.launch {
            checkAccidentOnce()
            while (true) {
                delay(2000) // Check frequently for sharp deceleration
                checkAccidentOnce()
            }
        }
    }

    private fun stopSpeedMonitor(resetState: Boolean) {
        speedJob?.cancel()
        speedJob = null
        if (resetState) {
            lastSpeedOverLimit = null
            pendingInitialSpeedAlert = false
        }
    }

    private fun stopAccidentMonitor(resetState: Boolean) {
        accidentJob?.cancel()
        accidentJob = null
        if (resetState) {
            lastAccidentSampleSpeedKmh = null
            lastAccidentSampleAtMs = null
        }
    }

    private fun updateSpeedMonitorState() {
        val shouldRun = state.activeMode == AppMode.PROTECTED &&
            protectedMonitoringReady &&
            authorizedMaxSpeedKmh() != null
        Log.d("SpeedMonitor", "updateSpeedMonitorState: run=$shouldRun")
        if (shouldRun) startSpeedMonitor() else stopSpeedMonitor(resetState = true)
    }

    private fun updateAccidentMonitorState() {
        val shouldRun = state.activeMode == AppMode.PROTECTED &&
            protectedMonitoringReady &&
            isAccidentAuthorized()
        if (shouldRun) startAccidentMonitor() else stopAccidentMonitor(resetState = true)
    }

    /** 
     * Delays the start of background monitoring after a role/mode switch 
     * to prevent false alerts during initialization.
     */
    private fun scheduleProtectedMonitoringStart() {
        protectedMonitoringDelayJob?.cancel()
        protectedMonitoringReady = false
        protectedMonitoringDelayJob = viewModelScope.launch {
            delay(15_000)
            val me = state.me
            if (me != null && me.roles.contains("Protected") && state.activeMode == AppMode.PROTECTED) {
                protectedMonitoringReady = true
                startInactivityTimer()
                startGeofenceMonitor()
                updateSpeedMonitorState()
                updateAccidentMonitorState()
            }
        }
    }

    private fun stopProtectedMonitoring() {
        protectedMonitoringDelayJob?.cancel()
        protectedMonitoringDelayJob = null
        protectedMonitoringReady = false
        inactivityJob?.cancel()
        inactivityJob = null
        geofenceJob?.cancel()
        geofenceJob = null
        stopSpeedMonitor(resetState = true)
        stopAccidentMonitor(resetState = true)
    }

    // --- Safety Rule Heuristics ---

    private suspend fun checkGeofenceOnce() {
        if (state.activeMode != AppMode.PROTECTED) { lastGeofenceInside = null; return }
        
        val authorizedBundles = state.monitorRuleBundles.filter { it.authorizedTypes.contains(RuleType.GEOFENCE) }
        if (authorizedBundles.isEmpty() || !isWithinAnyWindow(state.timeWindows)) { lastGeofenceInside = null; return }

        val areas = authorizedBundles.flatMap { bundle ->
            bundle.requested.filter { it.type == RuleType.GEOFENCE }.flatMap { it.params.geofenceAreas ?: emptyList() }
        }
        if (areas.isEmpty()) { lastGeofenceInside = null; return }

        val loc = locationProvider?.invoke() ?: return
        val isInside = areas.any { area ->
            distanceMeters(loc.latitude, loc.longitude, area.latitude, area.longitude) <= area.radiusMeters
        }

        val wasInside = lastGeofenceInside
        lastGeofenceInside = isInside
        // Trigger alert only if exiting a safe zone
        if ((wasInside == null && !isInside) || (wasInside == true && !isInside)) {
            triggerAlertWithTimer(RuleType.GEOFENCE)
        }
    }

    // --- Rules Synchronization & Firestore Listeners ---

    private fun startRulesByMonitorListener(uid: String) {
        rulesByMonitorListener?.remove()
        rulesByMonitorListener = db.collection("users").document(uid).collection("rulesByMonitor")
            .addSnapshotListener { snap, _ ->
                val bundles = snap?.documents?.mapNotNull { d -> parseRulesBundle(d) } ?: emptyList()
                state = state.copy(
                    monitorRuleBundles = bundles,
                    inactivityAuthorized = bundles.any { it.authorizedTypes.contains(RuleType.PROLONGED_INACTIVITY) }
                )
                syncFallDetectionWithAuthorizations()
                updateSpeedMonitorState()
                updateAccidentMonitorState()
                viewModelScope.launch { checkGeofenceOnce(); checkSpeedOnce() }
            }
    }

    fun observeRulesForProtected(protectedUid: String) {
        val monitorUid = authRepo.getCurrentUid() ?: return
        selectedProtectedRulesListener?.remove()
        selectedProtectedRulesListener = db.collection("users").document(protectedUid)
            .collection("rulesByMonitor").document(monitorUid)
            .addSnapshotListener { snap, _ ->
                state = state.copy(rulesForSelectedProtected = snap?.let { if (it.exists()) parseRulesBundle(it) else null })
            }
    }

    fun clearSelectedProtectedRules() {
        selectedProtectedRulesListener?.remove()
        selectedProtectedRulesListener = null
        state = state.copy(rulesForSelectedProtected = null)
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
                inactivityAuthorized = bundles.any { it.authorizedTypes.contains(RuleType.PROLONGED_INACTIVITY) },
                inactivityDurationMin = me.inactivityDurationMin
            )
            syncFallDetectionWithAuthorizations()
            updateSpeedMonitorState()
            updateAccidentMonitorState()
        } catch (e: Exception) { }
    }

    // --- User Actions & Security Settings ---

    fun resetInactivityTimer() { state = state.copy(userInactivitySeconds = 0) }
    fun updateInactivityDuration(m: String) = viewModelScope.launch { try { authRepo.updateInactivityDuration(m.toIntOrNull() ?: 15); state = state.copy(isSecurityUpdateSuccessful = true) } catch (e: Exception) {} }
    fun updateCancelPin(p: String) = viewModelScope.launch { try { authRepo.updateAlertCancelCode(p); state = state.copy(isSecurityUpdateSuccessful = true) } catch (e: Exception) {} }
    
    /** Checks if the PIN entered by the user matches their cancellation code. */
    fun tryCancelAlert(typed: String) {
        val correct = state.me?.alertCancelCode ?: "0000"
        if (typed == correct) state = state.copy(typedCancelCode = typed, cancelPinError = null)
        else state = state.copy(cancelPinError = "Incorrect PIN.")
    }

    /** Full cleanup of ViewModel state and listeners. Called during logout or destruction. */
    fun clear() {
        profileListener?.remove()
        myAlertsListener?.remove()
        rulesByMonitorListener?.remove()
        selectedProtectedRulesListener?.remove()
        monitorPopupListener?.remove()
        stopProtectedMonitoring()
        protectedAlertsListeners.values.forEach { it.remove() }
        protectedAlertsListeners.clear()
        alertsMap.clear()
        rulesByMonitorListener = null
        selectedProtectedRulesListener = null
        geofenceJob = null
        lastGeofenceInside = null
        state = AppUiState()
    }

    fun markRuleRequestHandled(key: String) {
        state = state.copy(shownRuleRequestKeys = state.shownRuleRequestKeys + key)
    }

    // --- Success Consumers (for UI feedback resets) ---
    fun consumeSecurityUpdateSuccess() { state = state.copy(isSecurityUpdateSuccessful = false) }
    fun consumeLinkingSuccess() { state = state.copy(isLinkingSuccessful = false) }
    fun consumeLinkError() { state = state.copy(linkError = null) }
    fun consumeAlertSentSuccess() { state = state.copy(isAlertSent = false) }
    fun consumeRemovalSuccess() { state = state.copy(isRemovalSuccessful = false) }
    fun consumeRequestSuccess() { state = state.copy(isRequestSuccessful = false) }
    fun consumeAdditionSuccess() { state = state.copy(isAdditionSuccessful = false) }

    // --- Association (OTP) & Linking ---

    fun generateOtp() = viewModelScope.launch { try { state = state.copy(myOtp = authRepo.generateAssociationCode()) } catch (e: Exception) {} }
    
    /** Links a Monitor to a Protected user using the provided code. */
    fun linkWithOtp(code: String) = viewModelScope.launch {
        try {
            authRepo.linkWithAssociationCode(code)
            val uid = authRepo.getCurrentUid()
            if (uid != null) {
                val me = authRepo.getUserProfile(uid)
                state = state.copy(me = me, linkedProtectedUsers = me.protectedUsers.map { authRepo.getUserProfile(it) }, isLinkingSuccessful = true)
                startMonitoringDashboard(uid)
            } else { state = state.copy(isLinkingSuccessful = true) }
        } catch (e: Exception) {
            val msg = e.message ?: "Linking failed."
            state = when {
                msg.contains("Cannot monitor yourself") -> state.copy(linkError = "A user cannot be their own monitor and protected user.")
                msg.contains("Invalid code") -> state.copy(linkError = "Invalid code. Please check the 6-digit code and try again.")
                else -> state.copy(error = msg)
            }
        }
    }

    fun removeMonitor(id: String) = viewModelScope.launch { try { authRepo.removeAssociation(id, state.me!!.uid); state = state.copy(isRemovalSuccessful = true) } catch (e: Exception) {} }
    fun removeProtectedUser(id: String) = viewModelScope.launch { try { authRepo.removeAssociation(state.me!!.uid, id); state = state.copy(isRemovalSuccessful = true) } catch (e: Exception) {} }
    
    /** Allows a Monitor to request specific monitoring rules for a Protected user. */
    fun requestRulesForProtected(p: String, t: List<RuleType>, r: RuleParams) = viewModelScope.launch { try { monitoringRepo.requestRules(p, state.me!!.uid, t.map { MonitoringRule(it, r, true) }); state = state.copy(isRequestSuccessful = true) } catch (e: Exception) {} }
    
    fun loadRulesForProtected(p: String) = viewModelScope.launch { try { state = state.copy(rulesForSelectedProtected = monitoringRepo.getRulesForProtected(p).find { it.monitorId == state.me!!.uid }) } catch (e: Exception) {} }

    /** 
     * Saves authorizations granted by the Protected user to a specific Monitor.
     * Triggers immediate checks for newly authorized rules (like geofencing).
     */
    fun saveAuthorizations(m: String, a: List<RuleType>, i: Int?, geofenceAreas: List<GeofenceArea>?) = viewModelScope.launch {
        try {
            val inactivityMinutes = if (a.contains(RuleType.PROLONGED_INACTIVITY)) i else 0
            monitoringRepo.saveAuthorizations(state.me!!.uid, m, a, inactivityMinutes, geofenceAreas)
            if (inactivityMinutes != null) authRepo.updateInactivityDuration(inactivityMinutes)
            if (geofenceAreas != null) { lastGeofenceInside = true; checkGeofenceOnce() }
            refreshProtectedMetadata(state.me!!.uid)
        } catch (e: Exception) {}
    }

    // --- Time Window Management ---

    fun addTimeWindow(d: List<Int>, s: Int, e: Int) = viewModelScope.launch {
        try {
            monitoringRepo.addTimeWindow(state.me!!.uid, TimeWindow(daysOfWeek = d, startHour = s, endHour = e))
            state = state.copy(timeWindows = monitoringRepo.listTimeWindows(state.me!!.uid), isAdditionSuccessful = true)
        } catch (e: Exception) { }
    }

    fun removeTimeWindow(id: String) = viewModelScope.launch {
        try {
            monitoringRepo.deleteTimeWindow(state.me!!.uid, id)
            state = state.copy(timeWindows = monitoringRepo.listTimeWindows(state.me!!.uid), isRemovalSuccessful = true)
        } catch (e: Exception) { }
    }

    // --- Service Management ---

    /** Manages the lifecycle of the background FallDetectionService. */
    fun setFallDetectionEnabled(enabled: Boolean) {
        val me = state.me ?: return
        if (!me.roles.contains("Protected") || enabled == state.isFallDetectionEnabled) return
        val intent = Intent(context, FallDetectionService::class.java)
        if (enabled) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        } else { context.stopService(intent) }
        state = state.copy(isFallDetectionEnabled = enabled)
    }

    fun dismissRecordingPopup() {
        state = state.copy(isRecordingPopupOpen = false)
        stopVideoRecording()
    }

    // --- Core Monitoring Heuristics & Helpers ---

    private fun startInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (state.activeMode == AppMode.PROTECTED && state.inactivityAuthorized && state.inactivityDurationMin > 0) {
                    if (!isWithinAnyWindow(state.timeWindows)) { state = state.copy(userInactivitySeconds = 0); continue }
                    state = state.copy(userInactivitySeconds = state.userInactivitySeconds + 1)
                    if (state.userInactivitySeconds >= state.inactivityDurationMin * 60) {
                        triggerInactivityAlert()
                        resetInactivityTimer()
                    }
                } else { state = state.copy(userInactivitySeconds = 0) }
            }
        }
    }

    fun setActiveMode(mode: AppMode) {
        if (state.activeMode != mode) {
            if (mode == AppMode.MONITOR) {
                stopProtectedMonitoring()
                setFallDetectionEnabled(false)
                stopVideoRecording()
                state = state.copy(activeMode = mode, isCancelWindowOpen = false, cancelSecondsLeft = 0, isRecordingPopupOpen = false, recordingSecondsLeft = 0, userInactivitySeconds = 0)
                lastGeofenceInside = null
            } else {
                state = state.copy(activeMode = mode)
                scheduleProtectedMonitoringStart()
                syncFallDetectionWithAuthorizations()
            }
        }
    }

    private fun isWithinAnyWindow(windows: List<TimeWindow>): Boolean {
        if (windows.isEmpty()) return true
        val cal = Calendar.getInstance()
        val day = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return windows.any { window -> day in window.daysOfWeek && hour in window.startHour until window.endHour }
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /** Parses a Firestore document into a ustructured MonitorRulesBundle. */
    private fun parseRulesBundle(d: DocumentSnapshot): MonitorRulesBundle? {
        val storedAreas = (d.get("geofenceAreas") as? List<*>)?.mapNotNull { it as? Map<*, *> }?.mapNotNull {
            val lat = (it["latitude"] as? Number)?.toDouble() ?: return@mapNotNull null
            val lon = (it["longitude"] as? Number)?.toDouble() ?: return@mapNotNull null
            val radius = (it["radiusMeters"] as? Number)?.toDouble() ?: return@mapNotNull null
            GeofenceArea(latitude = lat, longitude = lon, radiusMeters = radius)
        }

        val requested = (d.get("requested") as? List<*>)?.mapNotNull { it as? Map<*, *> }?.mapNotNull {
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

        return MonitorRulesBundle(
            monitorId = d.id,
            requested = requested,
            authorizedTypes = (d.get("authorizedTypes") as? List<*>)?.mapNotNull { runCatching { RuleType.valueOf(it as String) }.getOrNull() } ?: emptyList()
        )
    }

    private fun triggerInactivityAlert() = viewModelScope.launch { triggerAlertWithTimer(RuleType.PROLONGED_INACTIVITY) }

    private fun isAccidentAuthorized(): Boolean = state.monitorRuleBundles.any { it.authorizedTypes.contains(RuleType.ACCIDENT) }

    private fun syncFallDetectionWithAuthorizations() {
        val shouldEnable = state.activeMode == AppMode.PROTECTED && state.monitorRuleBundles.any { it.authorizedTypes.contains(RuleType.FALL) }
        setFallDetectionEnabled(shouldEnable)
    }

    private fun authorizedMaxSpeedKmh(): Float? {
        return state.monitorRuleBundles.filter { it.authorizedTypes.contains(RuleType.SPEED) }.mapNotNull { bundle -> bundle.requested.firstOrNull { it.type == RuleType.SPEED }?.params?.maxSpeed }.minOrNull()
    }

    private suspend fun checkAccidentOnce() {
        if (!protectedMonitoringReady || state.activeMode != AppMode.PROTECTED || !isAccidentAuthorized() || !isWithinAnyWindow(state.timeWindows)) {
            lastAccidentSampleSpeedKmh = null; lastAccidentSampleAtMs = null; return
        }
        val speedKmh = speedProvider?.invoke() ?: return
        val now = System.currentTimeMillis()
        val prevSpeed = lastAccidentSampleSpeedKmh
        val prevAt = lastAccidentSampleAtMs
        lastAccidentSampleSpeedKmh = speedKmh
        lastAccidentSampleAtMs = now
        if (prevSpeed == null || prevAt == null) return
        val deltaMs = now - prevAt
        if (deltaMs <= 0L || deltaMs > 6000L) return
        // Sharp deceleration detection logic
        if (prevSpeed >= 15f && speedKmh <= prevSpeed * 0.3f && (now - lastAccidentAlertAt >= 60_000L)) {
            lastAccidentAlertAt = now
            alertRepo.emitDetectionEvent(RuleType.ACCIDENT)
        }
    }

    private suspend fun checkSpeedOnce() {
        if (!protectedMonitoringReady || state.activeMode != AppMode.PROTECTED) { lastSpeedOverLimit = null; return }
        val maxSpeed = authorizedMaxSpeedKmh()
        if (maxSpeed == null || maxSpeed <= 0f || !isWithinAnyWindow(state.timeWindows)) { lastSpeedOverLimit = null; return }
        val speedKmh = speedProvider?.invoke() ?: return
        val overLimit = speedKmh > maxSpeed
        lastSpeedOverLimit = overLimit
        if (overLimit && (System.currentTimeMillis() - lastSpeedAlertAt >= 60_000L)) {
            lastSpeedAlertAt = System.currentTimeMillis()
            triggerAlertWithTimer(RuleType.SPEED)
        }
    }

    override fun onCleared() {
        super.onCleared()
        clear()
    }
}
