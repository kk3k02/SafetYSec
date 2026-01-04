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
    val linkError: String? = null,
    val isAlertSent: Boolean = false,
    val isRemovalSuccessful: Boolean = false,
    val isRequestSuccessful: Boolean = false,
    val isAdditionSuccessful: Boolean = false,
    val rulesForSelectedProtected: MonitorRulesBundle? = null,
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

enum class AppMode { PROTECTED, MONITOR }

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
    private var geofenceJob: Job? = null
    private var recordingTimerJob: Job? = null
    private var recording: Recording? = null
    private var protectedMonitoringDelayJob: Job? = null
    private var protectedMonitoringReady: Boolean = false
    private var accidentJob: Job? = null
    private var lastAccidentSampleSpeedKmh: Float? = null
    private var lastAccidentSampleAtMs: Long? = null
    private var lastAccidentAlertAt: Long = 0L

    private var profileListener: ListenerRegistration? = null
    private var myAlertsListener: ListenerRegistration? = null
    private var rulesByMonitorListener: ListenerRegistration? = null
    private var monitorPopupListener: ListenerRegistration? = null
    private val protectedAlertsListeners = mutableMapOf<String, ListenerRegistration>()
    private val alertsMap = mutableMapOf<String, List<Alert>>()

    // Location provider injected from UI (MainActivity)
    private var locationProvider: (suspend () -> GeoPoint?)? = null
    private var speedProvider: (suspend () -> Float?)? = null

    // SINGLE INSTANCE VideoCapture
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
        viewModelScope.launch {
            alertRepo.detectionEvents.collectLatest { type ->
                triggerAlertWithTimer(type)
            }
        }
    }

    fun setLocationProvider(provider: suspend () -> GeoPoint?) {
        this.locationProvider = provider
    }

    fun setSpeedProvider(provider: suspend () -> Float?) {
        this.speedProvider = provider
    }

    suspend fun getCurrentLocation(): GeoPoint? = locationProvider?.invoke()

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
        val tickerJob = viewModelScope.launch {
            while (state.cancelSecondsLeft > 0) { delay(1000); state = state.copy(cancelSecondsLeft = state.cancelSecondsLeft - 1) }
        }

        val alertId = alertRepo.triggerAlert(
            ruleType = type,
            user = me,
            cancelCodeProvider = { state.typedCancelCode },
            locationProvider = { locationProvider?.invoke() }
        )
        tickerJob.cancel()

        state = state.copy(isCancelWindowOpen = false, cancelAlertType = null, cancelSecondsLeft = 0)

        if (alertId != null) {
            currentAlertIdForRecording = alertId
            state = state.copy(isAlertSent = true, recordingSecondsLeft = 30, isRecordingPopupOpen = true)
        } else if (type == RuleType.SPEED) {
            // Allow retriggering if user cancels while still over limit
            lastSpeedOverLimit = false
            pendingInitialSpeedAlert = false
        }
    }

    fun triggerPanic() { triggerAlertWithTimer(RuleType.PANIC) }

    fun startMonitoringDashboard(monitorUid: String) {
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

        // Get protected users for this monitor
        val pIds = state.me?.protectedUsers ?: emptyList()

        // Remove listeners for users that are no longer linked
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
                        state = state.copy(monitorAlerts = alertsMap.values.flatten().sortedByDescending { it.timestamp })
                    }
                viewModelScope.launch {
                    try {
                        val snap = db.collection("users").document(pUid).collection("my_alerts")
                            .orderBy("timestamp", Query.Direction.DESCENDING).limit(20).get().await()
                        alertsMap[pUid] = snap.documents.mapNotNull { it.toObject(Alert::class.java)?.copy(id = it.id) }
                        state = state.copy(monitorAlerts = alertsMap.values.flatten().sortedByDescending { it.timestamp })
                    } catch (e: Exception) { }
                }
            }
        }

        // Update monitorAlerts in case pIds changed
        state = state.copy(monitorAlerts = alertsMap.values.flatten().sortedByDescending { it.timestamp })

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

    fun refreshMyAlertsHistory() = viewModelScope.launch {
        val uid = state.me?.uid ?: authRepo.getCurrentUid() ?: return@launch
        startMyAlertsListener(uid)
        try {
            state = state.copy(myAlerts = alertRepo.getProtectedAlertHistory(uid))
        } catch (e: Exception) { }
    }

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

                    state = state.copy(me = me, isLoading = false)

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
                                state = state.copy(monitorAlerts = alerts.sortedByDescending { it.timestamp })
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

    private fun startGeofenceMonitor() {
        if (geofenceJob != null) return
        geofenceJob = viewModelScope.launch {
            checkGeofenceOnce()
            while (true) {
                delay(300_000)
                checkGeofenceOnce()
            }
        }
    }

    private fun startSpeedMonitor() {
        if (speedJob != null) return
        speedJob = viewModelScope.launch {
            checkSpeedOnce()
            while (true) {
                delay(15000)
                checkSpeedOnce()
            }
        }
    }

    private fun startAccidentMonitor() {
        if (accidentJob != null) return
        accidentJob = viewModelScope.launch {
            checkAccidentOnce()
            while (true) {
                delay(2000)
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
        Log.d(
            "SpeedMonitor",
            "updateSpeedMonitorState: mode=${state.activeMode} ready=$protectedMonitoringReady max=${authorizedMaxSpeedKmh()} run=$shouldRun"
        )
        if (shouldRun) {
            startSpeedMonitor()
        } else if (speedJob != null) {
            stopSpeedMonitor(resetState = true)
        }
    }

    private fun updateAccidentMonitorState() {
        val shouldRun = state.activeMode == AppMode.PROTECTED &&
            protectedMonitoringReady &&
            isAccidentAuthorized()
        if (shouldRun) {
            startAccidentMonitor()
        } else if (accidentJob != null) {
            stopAccidentMonitor(resetState = true)
        }
    }

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

    private suspend fun checkGeofenceOnce() {
        if (state.activeMode != AppMode.PROTECTED) {
            lastGeofenceInside = null
            return
        }

        val authorizedBundles = state.monitorRuleBundles.filter {
            it.authorizedTypes.contains(RuleType.GEOFENCE)
        }
        if (authorizedBundles.isEmpty()) {
            lastGeofenceInside = null
            return
        }

        if (!isWithinAnyWindow(state.timeWindows)) {
            lastGeofenceInside = null
            return
        }

        val areas = authorizedBundles.flatMap { bundle ->
            bundle.requested.filter { it.type == RuleType.GEOFENCE }
                .flatMap { it.params.geofenceAreas ?: emptyList() }
        }
        if (areas.isEmpty()) {
            lastGeofenceInside = null
            return
        }

        val loc = locationProvider?.invoke() ?: return
        val isInside = areas.any { area ->
            val distance = distanceMeters(
                loc.latitude,
                loc.longitude,
                area.latitude,
                area.longitude
            )
            distance <= area.radiusMeters
        }

        val wasInside = lastGeofenceInside
        lastGeofenceInside = isInside
        if ((wasInside == null && !isInside) || (wasInside == true && !isInside)) {
            triggerAlertWithTimer(RuleType.GEOFENCE)
        }
    }

    private fun startRulesByMonitorListener(uid: String) {
        rulesByMonitorListener?.remove()
        rulesByMonitorListener = db.collection("users").document(uid).collection("rulesByMonitor")
            .addSnapshotListener { snap, _ ->
                Log.d("AppViewModel", "rulesByMonitor snapshot for $uid: ${snap?.size() ?: 0} docs")
                val bundles = snap?.documents?.map { d ->
                    val storedAreas = (d.get("geofenceAreas") as? List<*>)
                        ?.mapNotNull { it as? Map<*, *> }
                        ?.mapNotNull {
                            val lat = (it["latitude"] as? Number)?.toDouble() ?: return@mapNotNull null
                            val lon = (it["longitude"] as? Number)?.toDouble() ?: return@mapNotNull null
                            val radius = (it["radiusMeters"] as? Number)?.toDouble() ?: return@mapNotNull null
                            GeofenceArea(latitude = lat, longitude = lon, radiusMeters = radius)
                        }
                    val requested = (d.get("requested") as? List<*>)
                        ?.mapNotNull { it as? Map<*, *> }
                        ?.mapNotNull {
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

                    MonitorRulesBundle(
                        monitorId = d.id,
                        requested = requested,
                        authorizedTypes = (d.get("authorizedTypes") as? List<*>)
                            ?.mapNotNull { runCatching { RuleType.valueOf(it as String) }.getOrNull() }
                            ?: emptyList()
                    )
                } ?: emptyList()

                state = state.copy(
                    monitorRuleBundles = bundles,
                    inactivityAuthorized = bundles.any { it.authorizedTypes.contains(RuleType.PROLONGED_INACTIVITY) }
                )
                syncFallDetectionWithAuthorizations()
                updateSpeedMonitorState()
                updateAccidentMonitorState()
                viewModelScope.launch {
                    checkGeofenceOnce()
                    checkSpeedOnce()
                }
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
                inactivityAuthorized = bundles.any { it.authorizedTypes.contains(RuleType.PROLONGED_INACTIVITY) },
                inactivityDurationMin = me.inactivityDurationMin
            )
            syncFallDetectionWithAuthorizations()
            updateSpeedMonitorState()
            updateAccidentMonitorState()
            checkSpeedOnce()
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
        rulesByMonitorListener?.remove()
        monitorPopupListener?.remove()
        stopProtectedMonitoring()
        protectedAlertsListeners.values.forEach { it.remove() }
        protectedAlertsListeners.clear()
        alertsMap.clear()
        rulesByMonitorListener = null
        geofenceJob = null
        lastGeofenceInside = null
        state = AppUiState()
    }

    fun consumeSecurityUpdateSuccess() { state = state.copy(isSecurityUpdateSuccessful = false) }
    fun consumeLinkingSuccess() { state = state.copy(isLinkingSuccessful = false) }
    fun consumeLinkError() { state = state.copy(linkError = null) }
    fun consumeAlertSentSuccess() { state = state.copy(isAlertSent = false) }
    fun consumeRemovalSuccess() { state = state.copy(isRemovalSuccessful = false) }
    fun consumeRequestSuccess() { state = state.copy(isRequestSuccessful = false) }
    fun consumeAdditionSuccess() { state = state.copy(isAdditionSuccessful = false) }

    fun generateOtp() = viewModelScope.launch { try { state = state.copy(myOtp = authRepo.generateAssociationCode()) } catch (e: Exception) {} }
    fun linkWithOtp(code: String) = viewModelScope.launch {
        try {
            authRepo.linkWithAssociationCode(code)
            val uid = authRepo.getCurrentUid()
            if (uid != null) {
                val me = authRepo.getUserProfile(uid)
                state = state.copy(
                    me = me,
                    linkedProtectedUsers = me.protectedUsers.map { authRepo.getUserProfile(it) },
                    isLinkingSuccessful = true
                )
                startMonitoringDashboard(uid)
            } else {
                state = state.copy(isLinkingSuccessful = true)
            }
        } catch (e: Exception) {
            val msg = e.message ?: "Linking failed."
            state = if (msg.contains("Cannot monitor yourself")) {
                state.copy(linkError = "A user cannot be their own monitor and protected user.")
            } else {
                state.copy(error = msg)
            }
        }
    }
    fun removeMonitor(id: String) = viewModelScope.launch { try { authRepo.removeAssociation(id, state.me!!.uid); state = state.copy(isRemovalSuccessful = true) } catch (e: Exception) {} }
    fun removeProtectedUser(id: String) = viewModelScope.launch { try { authRepo.removeAssociation(state.me!!.uid, id); state = state.copy(isRemovalSuccessful = true) } catch (e: Exception) {} }
    fun requestRulesForProtected(p: String, t: List<RuleType>, r: RuleParams) = viewModelScope.launch { try { monitoringRepo.requestRules(p, state.me!!.uid, t.map { MonitoringRule(it, r, true) }); state = state.copy(isRequestSuccessful = true) } catch (e: Exception) {} }
    fun loadRulesForProtected(p: String) = viewModelScope.launch { try { state = state.copy(rulesForSelectedProtected = monitoringRepo.getRulesForProtected(p).find { it.monitorId == state.me!!.uid }) } catch (e: Exception) {} }
    fun saveAuthorizations(
        m: String,
        a: List<RuleType>,
        i: Int?,
        geofenceAreas: List<GeofenceArea>?
    ) = viewModelScope.launch {
        try {
            monitoringRepo.saveAuthorizations(state.me!!.uid, m, a, i, geofenceAreas)
            if (i != null) {
                authRepo.updateInactivityDuration(i)
            }
            if (geofenceAreas != null) {
                lastGeofenceInside = true
                checkGeofenceOnce()
            }
            refreshProtectedMetadata(state.me!!.uid)
        } catch (e: Exception) {}
    }
    fun addTimeWindow(d: List<Int>, s: Int, e: Int) = viewModelScope.launch { try { monitoringRepo.addTimeWindow(state.me!!.uid, TimeWindow(daysOfWeek = d, startHour = s, endHour = e)); state = state.copy(isAdditionSuccessful = true) } catch (e: Exception) {} }
    fun removeTimeWindow(id: String) = viewModelScope.launch { try { monitoringRepo.deleteTimeWindow(state.me!!.uid, id); state = state.copy(isRemovalSuccessful = true) } catch (e: Exception) {} }
    fun setFallDetectionEnabled(enabled: Boolean) {
        val me = state.me ?: return
        if (!me.roles.contains("Protected")) return
        if (enabled == state.isFallDetectionEnabled) return
        val intent = Intent(context, FallDetectionService::class.java)
        if (enabled) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
        state = state.copy(isFallDetectionEnabled = enabled)
    }

    fun dismissRecordingPopup() {
        state = state.copy(isRecordingPopupOpen = false)
        stopVideoRecording()
    }

    private fun startInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (state.activeMode == AppMode.PROTECTED && state.inactivityAuthorized && state.inactivityDurationMin > 0) {
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
                setFallDetectionEnabled(false)
                stopVideoRecording()
                state = state.copy(
                    activeMode = mode,
                    isCancelWindowOpen = false,
                    cancelSecondsLeft = 0,
                    isRecordingPopupOpen = false,
                    recordingSecondsLeft = 0,
                    userInactivitySeconds = 0
                )
                lastGeofenceInside = null
                stopProtectedMonitoring()
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
        return windows.any { window ->
            day in window.daysOfWeek && hour in window.startHour until window.endHour
        }
    }

    private fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun triggerInactivityAlert() = viewModelScope.launch {
        triggerAlertWithTimer(RuleType.PROLONGED_INACTIVITY)
    }

    private fun isAccidentAuthorized(): Boolean =
        state.monitorRuleBundles.any { it.authorizedTypes.contains(RuleType.ACCIDENT) }

    private fun syncFallDetectionWithAuthorizations() {
        val shouldEnable = state.activeMode == AppMode.PROTECTED &&
            state.monitorRuleBundles.any { it.authorizedTypes.contains(RuleType.FALL) }
        setFallDetectionEnabled(shouldEnable)
    }

    private fun authorizedMaxSpeedKmh(): Float? {
        val limits = state.monitorRuleBundles
            .filter { it.authorizedTypes.contains(RuleType.SPEED) }
            .mapNotNull { bundle ->
                bundle.requested.firstOrNull { it.type == RuleType.SPEED }?.params?.maxSpeed
            }
        return limits.minOrNull()
    }

    private suspend fun checkAccidentOnce() {
        if (!protectedMonitoringReady) return
        if (state.activeMode != AppMode.PROTECTED) {
            lastAccidentSampleSpeedKmh = null
            lastAccidentSampleAtMs = null
            return
        }
        if (!isAccidentAuthorized()) {
            lastAccidentSampleSpeedKmh = null
            lastAccidentSampleAtMs = null
            return
        }
        if (!isWithinAnyWindow(state.timeWindows)) {
            Log.d("AccidentMonitor", "skip: outside window")
            return
        }

        val speedKmh = speedProvider?.invoke() ?: return
        val now = System.currentTimeMillis()
        val prevSpeed = lastAccidentSampleSpeedKmh
        val prevAt = lastAccidentSampleAtMs
        lastAccidentSampleSpeedKmh = speedKmh
        lastAccidentSampleAtMs = now

        if (prevSpeed == null || prevAt == null) {
            Log.d("AccidentMonitor", "init: speed=$speedKmh")
            return
        }
        val deltaMs = now - prevAt
        if (deltaMs <= 0L || deltaMs > 6000L) {
            Log.d("AccidentMonitor", "skip: deltaMs=$deltaMs")
            return
        }

        // Fixed internal heuristic: sharp drop over short interval (non-configurable).
        val suddenDrop = prevSpeed >= 15f && speedKmh <= prevSpeed * 0.3f
        if (!suddenDrop) {
            Log.d("AccidentMonitor", "no-drop: prev=$prevSpeed now=$speedKmh dt=$deltaMs")
            return
        }

        val nowTs = System.currentTimeMillis()
        if (nowTs - lastAccidentAlertAt >= 60_000L) {
            lastAccidentAlertAt = nowTs
            Log.d("AccidentMonitor", "fire: prev=$prevSpeed now=$speedKmh dt=$deltaMs")
            alertRepo.emitDetectionEvent(RuleType.ACCIDENT)
        }
    }

    private suspend fun checkSpeedOnce() {
        if (!protectedMonitoringReady) return
        val now = System.currentTimeMillis()
        if (now - lastLoggedSpeedCheckAt > 10_000L) {
            lastLoggedSpeedCheckAt = now
            Log.d("SpeedMonitor", "checkSpeedOnce: mode=${state.activeMode} windows=${state.timeWindows.size}")
        }
        if (state.activeMode != AppMode.PROTECTED) {
            lastSpeedOverLimit = null
            pendingInitialSpeedAlert = false
            return
        }

        val maxSpeed = authorizedMaxSpeedKmh()
        if (maxSpeed == null || maxSpeed <= 0f) {
            lastSpeedOverLimit = null
            pendingInitialSpeedAlert = false
            Log.d("SpeedMonitor", "skip: no maxSpeed")
            return
        }

        val speedKmh = speedProvider?.invoke()
        if (speedKmh == null || speedKmh < 0f) {
            Log.d("SpeedMonitor", "skip: speed unavailable")
            return
        }

        val overLimit = speedKmh > maxSpeed
        lastSpeedOverLimit = overLimit
        pendingInitialSpeedAlert = false
        if (!overLimit) return

        val nowTs = System.currentTimeMillis()
        if (nowTs - lastSpeedAlertAt >= 60_000L) {
            lastSpeedAlertAt = nowTs
            Log.d("SpeedMonitor", "fire: overlimit speed=$speedKmh max=$maxSpeed")
            triggerAlertWithTimer(RuleType.SPEED)
        }
    }

    override fun onCleared() {
        super.onCleared()
        clear()
    }
}
