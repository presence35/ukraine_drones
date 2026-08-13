package ua.odesa.drones

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.osmdroid.util.GeoPoint

data class UiState(
    val connected: Boolean = false,
    val threatsInner: List<Threat> = emptyList(), // within the red zone radius
    val threatsOuter: List<Threat> = emptyList(), // in the yellow ring, beyond red
    val mapThreats: List<Threat> = emptyList(),   // all active threats across Europe
    val userLocation: LatLng? = null,
    val redZoneKm: Int = 3,
    val yellowZoneKm: Int = 8,
    val redArmed: Boolean = true,
    val yellowArmed: Boolean = true,
    val fastAlertsSooner: Boolean = true,
    val officialAlertsEnabled: Boolean = true,
    val disabledTypes: Set<ThreatType> = emptySet(),
    val activeZone: ThreatZone? = null,           // most specific zone with a threat
    val odesaOblastAlertActive: Boolean = false,
    val activeRegionTokens: Set<String> = emptySet(), // oblast stems under official alert
    val cityCounts: Map<String, Int> = emptyMap(),    // nameUa -> active threats in that city
    val language: AppLanguage = AppLanguage.EN,
    val selectedThreat: Threat? = null,
    val selectedThreatInfo: ThreatProximity? = null,
    val threatLevel: Double = 0.0,                 // experimental 0..10 gauge for the popup
    val disclaimerCollapsed: Boolean = false,
    val update: UpdateState = UpdateState.Idle,
    val needsInstallPermission: Boolean = false,
    val latestVersion: String? = null
)

/** Distance/ETA facts for the threat popup, computed from the predicted position. */
data class ThreatProximity(
    val predicted: LatLng,
    val distToUserKm: Double?,   // null when GPS unavailable
    val etaToUserMin: Double?,   // null when GPS unavailable or no speed
    val redKm: Int,
    val yellowKm: Int,
    val speedSource: SpeedSource,
    val speedKmh: Double?        // the displayed speed value (measured or nominal)
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val DAILY_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }

    private val prefs = ZonePrefs(app.applicationContext)
    private val speedTracker = ThreatSpeedTracker()
    private val updateManager = UpdateManager(app.applicationContext)

    // Seed from persisted prefs so the UI shows the previously chosen language
    // (and zone radii) on the very first frame — no UA flash when EN was selected.
    private val initialLanguage = runBlocking { prefs.language().first() }
    private val initialRedKm = runBlocking { prefs.redZoneKm().first() }
    private val initialYellowKm = runBlocking { prefs.yellowZoneKm().first() }

    private val selectedThreatFlow = MutableStateFlow<Threat?>(null)
    private val updateStateFlow = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private val installPermissionFlow = MutableStateFlow(false)
    private val latestVersionFlow = MutableStateFlow<String?>(null)
    private val nowFlow = MutableStateFlow(System.currentTimeMillis())
    private var isChecking = false

    private val zonesFlow = combine(prefs.redZoneKm(), prefs.yellowZoneKm()) { red, yellow ->
        red to yellow
    }

    init {
        // 1s clock so stale threats expire off the map/counts in real time, not just on the
        // next WebSocket frame.
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                nowFlow.value = System.currentTimeMillis()
            }
        }
        LocationTracker.start(getApplication())
        // Auto-check for updates at most once per day, silently.
        viewModelScope.launch {
            val lastCheck = prefs.lastUpdateCheck().first()
            if (System.currentTimeMillis() - lastCheck >= DAILY_CHECK_INTERVAL_MS) {
                checkForUpdates(notify = false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    /** Re-check location after the user grants the permission mid-session. */
    fun onLocationPermissionGranted() {
        LocationTracker.start(getApplication())
    }

    val uiState: StateFlow<UiState> = combine(
        NeptunClient.state,
        zonesFlow,
        threatEnabledFlow(prefs),
        prefs.language(),
        LocationTracker.location,
        selectedThreatFlow,
        updateStateFlow,
        installPermissionFlow,
        latestVersionFlow,
        nowFlow,
        prefs.disclaimerCollapsed(),
        prefs.redZoneArmed(),
        prefs.yellowZoneArmed(),
        prefs.fastAlertsSooner(),
        prefs.officialAlertsEnabled()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val radii = values[1] as Pair<Int, Int>
        buildUiState(
            neptun = values[0] as NeptunState,
            redKm = radii.first,
            yellowKm = radii.second,
            enabledTypes = values[2] as Set<ThreatType>,
            language = values[3] as AppLanguage,
            userLocation = values[4] as LatLng?,
            selected = values[5] as Threat?,
            now = values[9] as Long,
            fastAlertsSooner = values[13] as Boolean
        ).copy(
            update = values[6] as UpdateState,
            needsInstallPermission = values[7] as Boolean,
            latestVersion = values[8] as String?,
            disclaimerCollapsed = values[10] as Boolean,
            redArmed = values[11] as Boolean,
            yellowArmed = values[12] as Boolean,
            fastAlertsSooner = values[13] as Boolean,
            officialAlertsEnabled = values[14] as Boolean
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UiState(
            redZoneKm = initialRedKm,
            yellowZoneKm = initialYellowKm,
            language = initialLanguage
        )
    )

    private fun buildUiState(
        neptun: NeptunState,
        redKm: Int,
        yellowKm: Int,
        enabledTypes: Set<ThreatType>,
        language: AppLanguage,
        userLocation: LatLng?,
        selected: Threat?,
        now: Long,
        fastAlertsSooner: Boolean
    ): UiState {
        val zones = RadialZones(redKm, yellowKm)
        val odesaActive = neptun.oblastAlerts.any {
            it.oblast.contains("Одеськ", ignoreCase = true) ||
                it.name.contains("Одеськ", ignoreCase = true) ||
                it.key.contains("одеськ", ignoreCase = true)
        }
        // Oblasts with an official alert: a city label turns red when its oblast is listed.
        val activeRegionTokens = buildSet {
            for (citiesToken in Cities.cityOblast.values) {
                if (neptun.oblastAlerts.any {
                        it.oblast.contains(citiesToken, ignoreCase = true) ||
                            it.name.contains(citiesToken, ignoreCase = true)
                    }
                ) add(citiesToken)
            }
        }

        // Red zone tier: threats within redKm of the user (raw or predicted position).
        val inInner = mutableListOf<Threat>()
        // Yellow ring tier: threats beyond redKm but within yellowKm.
        val inOuter = mutableListOf<Threat>()
        // All active threats across the whole country, shown while any air-raid alert is
        // active — lets the user pan to other regions during alerts.
        val mapThreats = mutableListOf<Threat>()
        // Per-threat scores feeding the experimental overall threat-level gauge.
        val threatScores = mutableListOf<Double>()
        // Active threats whose locality names a curated city — shown next to the city label.
        val cityCounts = mutableMapOf<String, Int>()

        for (t in neptun.threats.values) {
            if (t.status == "resolved" || t.status == "stale" || isExpired(t, now)) continue
            if (t.type !in enabledTypes) continue
            t.locality?.takeIf { it in Cities.cityOblast }?.let {
                cityCounts[it] = (cityCounts[it] ?: 0) + 1
            }
            speedTracker.record(t.id, t.updatedAtMillis ?: now, t.lat, t.lon)
            val predicted = speedTracker.estimate(t.id, t)
                ?.let { predictPosition(t, it, now) } ?: GeoPoint(t.lat, t.lon)
            if (neptun.oblastAlerts.isNotEmpty()) mapThreats.add(t)
            if (userLocation == null) continue
            val distKm = distanceMeters(
                userLocation.lat, userLocation.lon, predicted.latitude, predicted.longitude
            ) / 1000.0
            if (distKm <= yellowKm) {
                val speed = speedTracker.estimate(t.id, t)
                val eta = if (speed != null && speed > 0.0) distKm / (speed * 3.6) * 60.0 else null
                threatScores.add(ThreatLevelModel.scoreOf(t, distKm, eta, redKm, yellowKm, now))
            }
            when (radialZone(distKm, zones)?.let { effectiveZone(t, it, fastAlertsSooner) }) {
                ThreatZone.INNER -> inInner.add(t)
                ThreatZone.OUTER -> inOuter.add(t)
                null -> {}
            }
        }

        // keep the selected threat pointer fresh (position/status may have updated)
        val refreshedSelected = selected?.let { s -> neptun.threats[s.id] }

        val activeZone = when {
            inInner.isNotEmpty() -> ThreatZone.INNER
            inOuter.isNotEmpty() -> ThreatZone.OUTER
            else -> null
        }

        val proximity = refreshedSelected?.let { t ->
            if (t.areaOnly) {
                null
            } else {
                speedTracker.record(t.id, t.updatedAtMillis ?: now, t.lat, t.lon)
                val speedPair = speedTracker.estimateWithSource(t.id, t)
                val speed = speedPair?.first
                val predicted = speed?.let { predictPosition(t, it, now) }
                    ?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(t.lat, t.lon)
                val distUser = userLocation?.let {
                    distanceMeters(it.lat, it.lon, predicted.lat, predicted.lon) / 1000.0
                }
                val etaUser = if (distUser != null && speed != null && speed > 0.0) {
                    distUser / (speed * 3.6) * 60.0
                } else null
                ThreatProximity(
                    predicted = predicted,
                    distToUserKm = distUser,
                    etaToUserMin = etaUser,
                    redKm = redKm,
                    yellowKm = yellowKm,
                    speedSource = speedPair?.second ?: SpeedSource.TYPICAL,
                    speedKmh = speedPair?.first?.let { it * 3.6 }
                )
            }
        }

        return UiState(
            connected = neptun.connected,
            threatsInner = inInner,
            threatsOuter = inOuter,
            mapThreats = mapThreats,
            userLocation = userLocation,
            redZoneKm = redKm,
            yellowZoneKm = yellowKm,
            disabledTypes = ThreatType.values().toSet() - enabledTypes,
            activeZone = activeZone,
            odesaOblastAlertActive = odesaActive,
            activeRegionTokens = activeRegionTokens,
            cityCounts = cityCounts,
            language = language,
            selectedThreat = refreshedSelected,
            selectedThreatInfo = proximity,
            threatLevel = ThreatLevelModel.overall(threatScores)
        )
    }

    fun setRedZoneKm(km: Int) {
        viewModelScope.launch { prefs.setRedZoneKm(km) }
    }

    fun setYellowZoneKm(km: Int) {
        viewModelScope.launch { prefs.setYellowZoneKm(km) }
    }

    fun setRedArmed(armed: Boolean) {
        viewModelScope.launch { prefs.setRedZoneArmed(armed) }
    }

    fun setYellowArmed(armed: Boolean) {
        viewModelScope.launch { prefs.setYellowZoneArmed(armed) }
    }

    /** Master alarm switch: arms or silences both zone tiers together. */
    fun setAlertsArmed(armed: Boolean) {
        viewModelScope.launch {
            prefs.setRedZoneArmed(armed)
            prefs.setYellowZoneArmed(armed)
        }
    }

    fun setFastAlertsSooner(sooner: Boolean) {
        viewModelScope.launch { prefs.setFastAlertsSooner(sooner) }
    }

    fun setOfficialAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setOfficialAlertsEnabled(enabled) }
    }

    fun setThreatEnabled(type: ThreatType, enabled: Boolean) {
        viewModelScope.launch { prefs.setThreatEnabled(type, enabled) }
    }

    fun setDisclaimerCollapsed(collapsed: Boolean) {
        viewModelScope.launch { prefs.setDisclaimerCollapsed(collapsed) }
    }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { prefs.setLanguage(lang) }
    }

    fun selectThreat(threat: Threat?) {
        selectedThreatFlow.value = threat
    }

    fun checkForUpdates(notify: Boolean = true, popupAvailable: Boolean = true) {
        if (isChecking) return
        val current = updateStateFlow.value
        if (current is UpdateState.Downloading || current is UpdateState.Downloaded) return
        isChecking = true
        viewModelScope.launch {
            updateStateFlow.value = UpdateState.Checking
            val result = updateManager.check()
            isChecking = false
            prefs.setLastUpdateCheck(System.currentTimeMillis())
            val s = Strings.get(prefs.language().first())
            when (result) {
                is UpdateState.Available -> {
                    latestVersionFlow.value = result.info.versionName
                    updateStateFlow.value = if (popupAvailable) result else UpdateState.Idle
                }
                is UpdateState.UpToDate -> {
                    latestVersionFlow.value = null
                    updateStateFlow.value = UpdateState.Idle
                    if (notify) {
                        Toast.makeText(getApplication(), s.updateUpToDate, Toast.LENGTH_SHORT).show()
                    }
                }
                is UpdateState.Failed -> {
                    updateStateFlow.value = UpdateState.Idle
                    if (notify) {
                        val message = result.message?.let { ": $it" }.orEmpty()
                        Toast.makeText(getApplication(), s.updateCheckFailed + message, Toast.LENGTH_SHORT).show()
                    }
                }
                else -> Unit
            }
        }
    }

    fun downloadUpdate() {
        val current = updateStateFlow.value as? UpdateState.Available ?: return
        viewModelScope.launch {
            try {
                val file = updateManager.download(current.info) { progress ->
                    updateStateFlow.value = UpdateState.Downloading(current.info, progress)
                }
                updateStateFlow.value = UpdateState.Downloaded(current.info, file)
            } catch (e: Exception) {
                updateStateFlow.value = UpdateState.Failed(e.message)
            }
        }
    }

    /** Builds the installer intent, or null when the "install unknown apps" permission is missing. */
    fun installIntent(): Intent? {
        val current = updateStateFlow.value as? UpdateState.Downloaded ?: return null
        val intent = updateManager.buildInstallIntent(current.file)
        installPermissionFlow.value = intent == null
        return intent
    }

    fun onInstallResult(canceled: Boolean) {
        if (canceled) {
            updateStateFlow.value = UpdateState.Idle
            installPermissionFlow.value = false
        }
    }

    fun openInstallPermissionSettings() {
        updateManager.openInstallPermissionSettings()
    }

    fun retryDownload() {
        viewModelScope.launch {
            updateStateFlow.value = UpdateState.Checking
            when (val result = updateManager.check()) {
                is UpdateState.Available -> {
                    updateStateFlow.value = result
                    downloadUpdate()
                }
                else -> updateStateFlow.value = result
            }
        }
    }

    fun dismissUpdate() {
        updateStateFlow.value = UpdateState.Idle
    }
}
