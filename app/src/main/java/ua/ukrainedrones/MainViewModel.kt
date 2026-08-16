package ua.ukrainedrones

import android.app.Application
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.osmdroid.util.GeoPoint

data class UiState(
    val connected: Boolean = false,
    val neptunDown: Boolean = false,                 // NEPTUN offline (real or simulated via test toggle)
    val forceOffline: Boolean = false,             // TEMP test toggle — simulate NEPTUN offline
    val backupActive: Boolean = false,           // oblast alerts fall back to the backup source
    val backupUp: Boolean = false,               // backup source polled successfully recently
    val backupSeen: Boolean = false,             // backup source has polled successfully at least once
    val backupOfflineElapsedSec: Long? = null,   // seconds since backup last succeeded, null while up
    val backupError: String? = null,             // last backup error message, if any
    val offlineElapsedSec: Long? = null,          // seconds since the stream dropped, null while connected
    val threatsInner: List<Threat> = emptyList(), // reaching within the red time tier
    val threatsOuter: List<Threat> = emptyList(), // in the yellow time tier, beyond red
    val mapThreats: List<Threat> = emptyList(),   // all active threats across Europe
    val userLocation: LatLng? = null,
    val redZoneMin: Int = 20,
    val yellowZoneMin: Int = 60,
    val redCircleKm: Double = 60.0,   // drawn red circle = redZoneMin at Shahed reference speed
    val yellowCircleKm: Double = 180.0, // drawn yellow circle = yellowZoneMin at Shahed reference speed
    val redArmed: Boolean = true,
    val yellowArmed: Boolean = true,
    val officialAlertsEnabled: Boolean = true,
    val sirenOverride: Boolean = false,
    val hiddenTypes: Set<ThreatType> = emptySet(),      // hidden from the map
    val silencedTypes: Set<ThreatType> = emptySet(),    // alerts off (still on the map, dimmed)
    val activeZone: ThreatZone? = null,           // most specific zone with a threat
    val focusOblastAlertActive: Boolean = false,  // official alert on the focus point's oblast
    val focusBannerCity: String = "",             // localized city name for the alert banner
    val activeRegionTokens: Set<String> = emptySet(), // oblast stems under official alert
    val language: AppLanguage = AppLanguage.EN,
    val followMe: Boolean = true,
    val pinnedCity: City? = null,
    val focusLocation: LatLng? = null,            // camera + zone center: GPS (follow) or pinned city
    val redCities: Set<String> = emptySet(),      // nameUa of cities whose oblast has an official alert
    val selectedThreat: Threat? = null,
    val selectedThreatInfo: ThreatProximity? = null,
    val neutralizedThreat: Threat? = null,   // selected threat just resolved — fades out
    val threatLevel: Double = 0.0,                 // experimental 0..10 gauge for the popup
    val disclaimerCollapsed: Boolean = false,
    val update: UpdateState = UpdateState.Idle,
    val needsInstallPermission: Boolean = false,
    val latestVersion: String? = null,
    val languageChosen: Boolean = false,
    val threatCardSize: ThreatCardSize = ThreatCardSize.LARGE,
    val showMapScale: Boolean = true,
    val fastGroupCollapsed: Boolean = false,
    val slowGroupCollapsed: Boolean = false
)

/** Distance/ETA facts for the threat popup, computed from the predicted position. */
data class ThreatProximity(
    val predicted: LatLng,
    val distToUserKm: Double?,   // null when GPS unavailable
    val etaToUserMin: Double?,   // null when GPS unavailable or no speed
    val redMin: Int,
    val yellowMin: Int,
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

    private val selectedThreatFlow = MutableStateFlow<Threat?>(null)
    private val updateStateFlow = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private val installPermissionFlow = MutableStateFlow(false)
    private val latestVersionFlow = MutableStateFlow<String?>(null)
    private val nowFlow = MutableStateFlow(System.currentTimeMillis())
    private var isChecking = false

    private val zonesFlow = combine(prefs.redZoneMin(), prefs.yellowZoneMin()) { red, yellow ->
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
        // Auto-check for updates at most once per day; pops only when no alert is active.
        autoCheckForUpdates(allowPopup = true)
    }

    override fun onCleared() {
        super.onCleared()
    }

    /** Everything read from prefs whenever any of them changes. */
    private data class ThreatPrefs(
        val map: Set<ThreatType>,
        val alert: Set<ThreatType>,
        val lang: AppLanguage,
        val disclaimer: Boolean
    )

    private data class PrefsSnapshot(
        val mapEnabled: Set<ThreatType>,
        val alertEnabled: Set<ThreatType>,
        val language: AppLanguage,
        val disclaimerCollapsed: Boolean,
        val redArmed: Boolean,
        val yellowArmed: Boolean,
        val officialAlertsEnabled: Boolean,
        val sirenOverride: Boolean,
        val followMe: Boolean,
        val pinnedCity: String?,
        val languageChosen: Boolean,
        val cardSize: ThreatCardSize,
        val showMapScale: Boolean,
        val fastGroupCollapsed: Boolean,
        val slowGroupCollapsed: Boolean
    )

    /** Live inputs that change every frame/second: stream, GPS, selection, time. */
    private data class LiveSnapshot(
        val neptun: NeptunState,
        val redMin: Int,
        val yellowMin: Int,
        val userLocation: LatLng?,
        val selected: Threat?,
        val now: Long
    )

    private data class UpdateUi(
        val update: UpdateState,
        val needsInstallPermission: Boolean,
        val latestVersion: String?
    )

    private data class AlertConfig(
        val redArmed: Boolean,
        val yellowArmed: Boolean,
        val officialAlertsEnabled: Boolean,
        val sirenOverride: Boolean,
        val followMe: Boolean,
        val showMapScale: Boolean,
        val fastGroupCollapsed: Boolean,
        val slowGroupCollapsed: Boolean
    )

    private val liveSnapshot = combine(
        NeptunClient.state,
        zonesFlow,
        LocationTracker.location,
        selectedThreatFlow,
        nowFlow
    ) { neptun, radii, location, selected, now ->
        LiveSnapshot(neptun, radii.first, radii.second, location, selected, now)
    }

    private val prefsSnapshot = combine(
        combine(
            threatMapFlow(prefs),
            threatAlertFlow(prefs),
            prefs.language(),
            prefs.disclaimerCollapsed()
        ) { map, alert, lang, disclaimer ->
            ThreatPrefs(map, alert, lang, disclaimer)
        },
        combine(
            prefs.redZoneArmed(),
            prefs.yellowZoneArmed(),
            prefs.officialAlertsEnabled(),
            prefs.sirenOverride(),
            prefs.followMe(),
            prefs.showMapScale(),
            prefs.fastGroupCollapsed(),
            prefs.slowGroupCollapsed()
        ) { flags: Array<Boolean> ->
            AlertConfig(flags[0], flags[1], flags[2], flags[3], flags[4], flags[5], flags[6], flags[7])
        },
        combine(
            prefs.pinnedCity(),
            prefs.languageChosen(),
            prefs.threatCardSize()
        ) { pinned, chosen, card -> Triple(pinned, chosen, card) }
    ) { a, b, c ->
        PrefsSnapshot(
            mapEnabled = a.map,
            alertEnabled = a.alert,
            language = a.lang,
            disclaimerCollapsed = a.disclaimer,
            redArmed = b.redArmed,
            yellowArmed = b.yellowArmed,
            officialAlertsEnabled = b.officialAlertsEnabled,
            sirenOverride = b.sirenOverride,
            followMe = b.followMe,
            pinnedCity = c.first,
            languageChosen = c.second,
            cardSize = c.third,
            showMapScale = b.showMapScale,
            fastGroupCollapsed = b.fastGroupCollapsed,
            slowGroupCollapsed = b.slowGroupCollapsed
        )
    }

    private val updateUiFlow = combine(
        updateStateFlow,
        installPermissionFlow,
        latestVersionFlow
    ) { update, install, latest ->
        UpdateUi(update, install, latest)
    }

    /**
     * One-time background read that primes the DataStore cache off the main thread, so the
     * first uiState emission already carries the persisted language/radii — no runBlocking on
     * the main thread and no first-frame flash.
     */
    private val seedFlow: Flow<Unit> = flow {
        prefs.language().first()
        prefs.redZoneMin().first()
        prefs.yellowZoneMin().first()
        prefs.followMe().first()
        prefs.pinnedCity().first()
        prefs.languageChosen().first()
        emit(Unit)
    }.flowOn(Dispatchers.IO)

    val uiState: StateFlow<UiState> = combine(
        seedFlow,
        liveSnapshot,
        prefsSnapshot,
        updateUiFlow
    ) { _, live, prefs, updateUi ->
        buildUiState(
            neptun = live.neptun,
            redMin = live.redMin,
            yellowMin = live.yellowMin,
            mapEnabledTypes = prefs.mapEnabled,
            alertedTypes = prefs.alertEnabled,
            language = prefs.language,
            userLocation = live.userLocation,
            followMe = prefs.followMe,
            pinnedCity = prefs.pinnedCity?.let { name ->
                Cities.ALL.firstOrNull { it.nameUa == name }
            },
            selected = live.selected,
            now = live.now
        ).copy(
            update = updateUi.update,
            needsInstallPermission = updateUi.needsInstallPermission,
            latestVersion = updateUi.latestVersion,
            disclaimerCollapsed = prefs.disclaimerCollapsed,
            redArmed = prefs.redArmed,
            yellowArmed = prefs.yellowArmed,
            officialAlertsEnabled = prefs.officialAlertsEnabled,
            sirenOverride = prefs.sirenOverride,
            languageChosen = prefs.languageChosen,
            threatCardSize = prefs.cardSize,
            showMapScale = prefs.showMapScale,
            fastGroupCollapsed = prefs.fastGroupCollapsed,
            slowGroupCollapsed = prefs.slowGroupCollapsed
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UiState()
    )

    private fun buildUiState(
        neptun: NeptunState,
        redMin: Int,
        yellowMin: Int,
        mapEnabledTypes: Set<ThreatType>,
        alertedTypes: Set<ThreatType>,
        language: AppLanguage,
        userLocation: LatLng?,
        followMe: Boolean,
        pinnedCity: City?,
        selected: Threat?,
        now: Long
    ): UiState {
        val zones = TimeZones(redMin, yellowMin)
        val redCircleKm = zoneCircleKm(redMin)
        val yellowCircleKm = zoneCircleKm(yellowMin)
        // Camera + zone center: GPS while following, else the pinned city (else GPS as fallback).
        val focusLocation = if (followMe) userLocation
        else pinnedCity?.let { LatLng(it.lat, it.lon) } ?: userLocation
        // Official alert state for the FOCUS point: the pinned city's oblast, else the
        // oblast of the nearest listed city to the GPS fix while following.
        val attribution = focusAttribution(followMe, userLocation, pinnedCity)
        val focusToken = attribution.token
        val focusOblastAlertActive = focusToken?.let { token ->
            neptun.oblastAlerts.any { it.inOblast(token) }
        } == true
        val focusBannerCity =
            if (language == AppLanguage.UA) attribution.bannerCityUa else attribution.bannerCityEn
        // Oblasts with an official alert: a city label turns red when its oblast is listed.
        val activeRegionTokens = buildSet {
            for (citiesToken in Cities.cityOblast.values) {
                if (neptun.oblastAlerts.any { it.inOblast(citiesToken) }) add(citiesToken)
            }
        }
        // Cities whose oblast is under an official alert — red dot in the picker.
        val redCities = buildSet {
            for (city in Cities.ALL) {
                val token = Cities.cityOblast[city.nameUa] ?: continue
                if (token in activeRegionTokens) add(city.nameUa)
            }
        }

        // Red zone tier: threats that could reach the focus within redMin minutes.
        val inInner = mutableListOf<Threat>()
        // Yellow ring tier: threats that could reach the focus within yellowMin minutes.
        val inOuter = mutableListOf<Threat>()
        // All active threats across the whole country, shown while any air-raid alert is
        // active — lets the user pan to other regions during alerts.
        val mapThreats = mutableListOf<Threat>()
        // Per-threat scores feeding the experimental overall threat-level gauge.
        val threatScores = mutableListOf<Double>()

        for (t in neptun.threats.values) {
            // Truly gone: resolved by the server (or a remove frame), area-only, or a ghost
            // past the hard cap. Everything else — including stale/expired threats — stays on
            // the map, just dimmed.
            if (t.status == "resolved" || t.areaOnly || t.isGhost(now)) continue
            if (t.type !in mapEnabledTypes) continue
            val stale = t.isStale(now)
            if (!stale) {
                speedTracker.record(t.id, t.updatedAtMillis ?: now, t.lat, t.lon)
            }
            val predicted = speedTracker.estimate(t.id, t)
                ?.let { predictPosition(t, it, now) } ?: GeoPoint(t.lat, t.lon)
            if (neptun.oblastAlerts.isNotEmpty()) mapThreats.add(t)
            // Stale threats stay on the map but never feed counts, tiers, or the gauge.
            if (stale || focusLocation == null) continue
            // Advisory = NEPTUN observation, never an alert — shown on the map only.
            if (t.advisory) continue
            val distKm = distanceMeters(
                focusLocation.lat, focusLocation.lon, predicted.latitude, predicted.longitude
            ) / 1000.0
            val speedKmh = speedTracker.estimate(t.id, t)?.times(3.6)
            val tier = timeTier(t, distKm, speedKmh, zones)
            if (tier != null) {
                val eta = etaMinutes(distKm, speedKmh)
                threatScores.add(
                    ThreatLevelModel.scoreOf(t, distKm, eta, redCircleKm.toInt(), yellowCircleKm.toInt(), now)
                )
            }
            when (tier) {
                ThreatZone.INNER -> inInner.add(t)
                ThreatZone.OUTER -> inOuter.add(t)
                null -> {}
            }
        }

        // keep the selected threat pointer fresh (position/status may have updated)
        val refreshedSelected = selected?.let { s -> neptun.threats[s.id] }
        // The selected threat just resolved/gone — show a brief neutralized card, then drop it.
        val neutralizedThreat = if (selected != null && refreshedSelected == null) selected else null

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
                val distUser = focusLocation?.let {
                    distanceMeters(it.lat, it.lon, predicted.lat, predicted.lon) / 1000.0
                }
                val etaUser = if (distUser != null && speed != null && speed > 0.0) {
                    distUser / (speed * 3.6) * 60.0
                } else null
                ThreatProximity(
                    predicted = predicted,
                    distToUserKm = distUser,
                    etaToUserMin = etaUser,
                    redMin = redMin,
                    yellowMin = yellowMin,
                    speedSource = speedPair?.second ?: SpeedSource.TYPICAL,
                    speedKmh = speedPair?.first?.let { it * 3.6 }
                )
            }
        }

        return UiState(
            connected = neptun.connected,
            neptunDown = neptun.neptunDown,
            forceOffline = neptun.forceOffline,
            backupActive = neptun.backupActive,
            backupUp = neptun.backupUp,
            backupSeen = neptun.backupLastOkAt > 0,
            backupOfflineElapsedSec = neptun.backupOfflineElapsedSec,
            backupError = neptun.backupError,
            offlineElapsedSec = neptun.offlineElapsedSec,
            threatsInner = inInner,
            threatsOuter = inOuter,
            mapThreats = mapThreats,
            userLocation = userLocation,
            redZoneMin = redMin,
            yellowZoneMin = yellowMin,
            redCircleKm = redCircleKm,
            yellowCircleKm = yellowCircleKm,
            hiddenTypes = ThreatType.values().toSet() - mapEnabledTypes,
            silencedTypes = ThreatType.values().toSet() - alertedTypes,
            activeZone = activeZone,
            focusOblastAlertActive = focusOblastAlertActive,
            focusBannerCity = focusBannerCity,
            activeRegionTokens = activeRegionTokens,
            language = language,
            followMe = followMe,
            pinnedCity = pinnedCity,
            focusLocation = focusLocation,
            redCities = redCities,
            selectedThreat = refreshedSelected,
            selectedThreatInfo = proximity,
            neutralizedThreat = neutralizedThreat,
            threatLevel = ThreatLevelModel.overall(threatScores)
        )
    }

    fun setRedZoneMin(min: Int) {
        viewModelScope.launch { prefs.setRedZoneMin(min) }
    }

    fun setYellowZoneMin(min: Int) {
        viewModelScope.launch { prefs.setYellowZoneMin(min) }
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

    fun setOfficialAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setOfficialAlertsEnabled(enabled) }
    }

    fun setSirenOverride(override: Boolean) {
        viewModelScope.launch { prefs.setSirenOverride(override) }
    }

    /** Follow-me toggle: switching it back on resumes GPS-centered zones/camera. */
    fun setFollowMe(follow: Boolean) {
        viewModelScope.launch { prefs.setFollowMe(follow) }
    }

    /** TEMP test toggle: force the app to simulate NEPTUN being offline. */
    fun setForceOffline(force: Boolean) {
        viewModelScope.launch {
            prefs.setForceOffline(force)
            NeptunClient.setForceOffline(force)
        }
    }

    /** Pin the map to a city. Pinning auto-disables follow-me so the pin takes effect. */
    fun setPinnedCity(city: City?) {
        viewModelScope.launch {
            prefs.setPinnedCity(city?.nameUa)
            if (city != null) prefs.setFollowMe(false)
        }
    }

    fun setThreatMapVisible(type: ThreatType, visible: Boolean) {
        viewModelScope.launch {
            prefs.setThreatMapVisible(type, visible)
            maybeShowToggleHint(mapToast = true)
        }
    }

    fun setThreatAlertsEnabled(type: ThreatType, enabled: Boolean) {
        viewModelScope.launch {
            prefs.setThreatAlertsEnabled(type, enabled)
            if (enabled) prefs.setThreatMapVisible(type, true)
            maybeShowToggleHint(mapToast = false)
        }
    }

    fun setGroupThreatMapVisible(types: Set<ThreatType>, visible: Boolean) {
        viewModelScope.launch {
            types.forEach { prefs.setThreatMapVisible(it, visible) }
            maybeShowToggleHint(mapToast = true)
        }
    }

    fun setGroupThreatAlertsEnabled(types: Set<ThreatType>, enabled: Boolean) {
        viewModelScope.launch {
            types.forEach {
                prefs.setThreatAlertsEnabled(it, enabled)
                if (enabled) prefs.setThreatMapVisible(it, true)
            }
            maybeShowToggleHint(mapToast = false)
        }
    }

    /** One-time hint (first 3 Map/Alerts toggles ever): a brief toast explaining how they work. */
    private suspend fun maybeShowToggleHint(mapToast: Boolean) {
        val remaining = prefs.threatToggleHintRemaining().first()
        if (remaining <= 0) return
        prefs.setThreatToggleHintRemaining(remaining - 1)
        val s = Strings.get(prefs.language().first())
        val prefix = if (mapToast) s.mapToggleHintPrefix else s.alertToggleHintPrefix
        val rest = if (mapToast) s.mapToggleHintRest else s.alertToggleHintRest
        val message = SpannableString(prefix + rest)
        message.setSpan(StyleSpan(Typeface.BOLD), 0, prefix.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }

    fun setDisclaimerCollapsed(collapsed: Boolean) {
        viewModelScope.launch { prefs.setDisclaimerCollapsed(collapsed) }
    }

    fun setThreatCardSize(size: ThreatCardSize) {
        viewModelScope.launch { prefs.setThreatCardSize(size) }
    }

    fun setFastGroupCollapsed(collapsed: Boolean) {
        viewModelScope.launch { prefs.setFastGroupCollapsed(collapsed) }
    }

    fun setSlowGroupCollapsed(collapsed: Boolean) {
        viewModelScope.launch { prefs.setSlowGroupCollapsed(collapsed) }
    }

    fun setShowMapScale(show: Boolean) {
        viewModelScope.launch { prefs.setShowMapScale(show) }
    }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { prefs.setLanguage(lang) }
    }

    /** Dismiss the first-run language picker without changing the language. */
    fun skipLanguageChoose() {
        viewModelScope.launch { prefs.setLanguageChosen(true) }
    }

    fun selectThreat(threat: Threat?) {
        selectedThreatFlow.value = threat
    }

    /** Auto-check at most once per day. [allowPopup] pops the dialog on start when no alert is active. */
    fun autoCheckForUpdates(allowPopup: Boolean) {
        viewModelScope.launch {
            val lastCheck = prefs.lastUpdateCheck().first()
            if (System.currentTimeMillis() - lastCheck >= DAILY_CHECK_INTERVAL_MS) {
                checkForUpdates(notify = false, popupAvailable = allowPopup, popupOnlyWithoutAlert = allowPopup)
            }
        }
    }

    /** True when any threat or official alert is currently active — the update dialog stays hidden then. */
    private fun hasActiveAlert(): Boolean =
        uiState.value.mapThreats.isNotEmpty() || uiState.value.redCities.isNotEmpty()

    fun checkForUpdates(notify: Boolean = true, popupAvailable: Boolean = true, popupOnlyWithoutAlert: Boolean = false) {
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
                    val showDialog = popupAvailable && (!popupOnlyWithoutAlert || !hasActiveAlert())
                    updateStateFlow.value = if (showDialog) result else UpdateState.Idle
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
