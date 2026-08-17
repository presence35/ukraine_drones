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
    val threatsInner: List<Threat> = emptyList(), // reaching within the red time tier
    val threatsOuter: List<Threat> = emptyList(), // in the yellow time tier, beyond red
    val mapThreats: List<Threat> = emptyList(),   // all active threats across Europe
    val userLocation: LatLng? = null,
    val slowRedKm: Int = 20,      // slow threats: distance to the red (inner) zone, km
    val slowYellowKm: Int = 50,  // slow threats: distance to the yellow (outer) zone, km
    val fastRedMin: Int = 5,     // fast threats: ETA to the red (inner) zone, minutes
    val fastYellowMin: Int = 20,  // fast threats: ETA to the yellow (outer) zone, minutes
    val slowRedArmed: Boolean = true,
    val slowYellowArmed: Boolean = true,
    val fastRedArmed: Boolean = true,
    val fastYellowArmed: Boolean = true,
    val activeZoneParams: ZoneParams = ZoneParams(20, 50, 5, 20), // effective (night-aware) thresholds
    val activeSlowRedArmed: Boolean = true,
    val activeSlowYellowArmed: Boolean = true,
    val activeFastRedArmed: Boolean = true,
    val activeFastYellowArmed: Boolean = true,
    val nightActive: Boolean = false,                    // night window currently in effect
    val nightWindowText: String = "",                    // localized "22:00–07:00" when configured
    val nightEnabled: Boolean = true,
    val nightStartMin: Int = 22 * 60,
    val nightEndMin: Int = 7 * 60,
    val nightUseCustomZones: Boolean = false,
    val nightSlowRedKm: Int = 20,
    val nightSlowYellowKm: Int = 50,
    val nightFastRedMin: Int = 5,
    val nightFastYellowMin: Int = 20,
    val nightSlowRedArmed: Boolean = true,
    val nightSlowYellowArmed: Boolean = true,
    val nightFastRedArmed: Boolean = true,
    val nightFastYellowArmed: Boolean = true,
    val nightZoneSirenOverride: Boolean = false,
    val nightOfficialSirenOverride: Boolean = false,
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
    val revealRequest: RevealRequest? = null,      // notification tap: pan the camera onto a threat
    val disclaimerCollapsed: Boolean = false,
    val disclaimerReadCount: Int = 0,
    val update: UpdateState = UpdateState.Idle,
    val needsInstallPermission: Boolean = false,
    val latestVersion: String? = null,
    val languageChosen: Boolean = false,
    val batteryOnboardShown: Boolean = false,
    val threatCardSize: ThreatCardSize = ThreatCardSize.LARGE,
    val iconSet: ThreatIconSet = ThreatIconSet.PHOTO,
    val showMapScale: Boolean = true,
    val deathAnimationEnabled: Boolean = true,
    val fastGroupCollapsed: Boolean = false,
    val slowGroupCollapsed: Boolean = false,
    val fastVibrationLevel: Int = 3,
    val slowVibrationLevel: Int = 3,
    val alertActive: Boolean = false        // any threat or official alert live right now
)

/** One-shot request from a notification tap to bring the camera onto a threat. */
data class RevealRequest(
    val tick: Int,
    val id: String?,
    val lat: Double,
    val lon: Double
)

/** Distance/ETA facts for the threat popup, computed from the predicted position. */
data class ThreatProximity(
    val predicted: LatLng,
    val distToUserKm: Double?,   // null when GPS unavailable
    val etaToUserMin: Double?,   // null when GPS unavailable or no speed
    val params: ZoneParams,
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
    // TEMP: a threat id long-pressed on the map is treated as neutralized so the card
    // self-destructs like a real resolution. Cleared on every selection change.
    private val tempNeutralizedFlow = MutableStateFlow<String?>(null)
    private val revealFlow = MutableStateFlow<RevealRequest?>(null)
    private var revealTick = 0
    private val updateStateFlow = MutableStateFlow<UpdateState>(UpdateState.Idle)
    private val installPermissionFlow = MutableStateFlow(false)
    private val latestVersionFlow = MutableStateFlow<String?>(null)
    private val nowFlow = MutableStateFlow(System.currentTimeMillis())
    private var isChecking = false

    private val zonesFlow = combine(
        prefs.slowRedKm(), prefs.slowYellowKm(), prefs.fastRedMin(), prefs.fastYellowMin()
    ) { slowRed, slowYellow, fastRed, fastYellow ->
        ZoneParams(slowRed, slowYellow, fastRed, fastYellow)
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
        val disclaimer: Boolean,
        val disclaimerReadCount: Int
    )

    private data class PrefsQuad(
        val pinnedCity: String?,
        val languageChosen: Boolean,
        val batteryOnboardShown: Boolean,
        val cardSize: ThreatCardSize,
        val iconSet: ThreatIconSet
    )

    /** Night-mode window prefs (raw, day values untouched). */
    private data class NightWindowPrefs(
        val enabled: Boolean,
        val startMin: Int,
        val endMin: Int,
        val useCustomZones: Boolean
    )

    /** Night-mode zone prefs (raw). */
    private data class NightZonesPrefs(
        val slowRedKm: Int,
        val slowYellowKm: Int,
        val fastRedMin: Int,
        val fastYellowMin: Int,
        val slowRedArmed: Boolean,
        val slowYellowArmed: Boolean,
        val fastRedArmed: Boolean,
        val fastYellowArmed: Boolean,
        val zoneSirenOverride: Boolean,
        val officialSirenOverride: Boolean
    )

    private data class NightPrefs(
        val window: NightWindowPrefs,
        val zones: NightZonesPrefs
    )

    private data class PrefsSnapshot(
        val mapEnabled: Set<ThreatType>,
        val alertEnabled: Set<ThreatType>,
        val language: AppLanguage,
        val disclaimerCollapsed: Boolean,
        val disclaimerReadCount: Int,
        val slowRedArmed: Boolean,
        val slowYellowArmed: Boolean,
        val fastRedArmed: Boolean,
        val fastYellowArmed: Boolean,
        val officialAlertsEnabled: Boolean,
        val sirenOverride: Boolean,
        val followMe: Boolean,
        val pinnedCity: String?,
        val languageChosen: Boolean,
        val batteryOnboardShown: Boolean,
        val cardSize: ThreatCardSize,
        val iconSet: ThreatIconSet,
        val showMapScale: Boolean,
        val deathAnimationEnabled: Boolean,
        val fastGroupCollapsed: Boolean,
        val slowGroupCollapsed: Boolean,
        val fastVibrationLevel: Int,
        val slowVibrationLevel: Int,
        val night: NightPrefs
    )

    /** Live inputs that change every frame/second: stream, GPS, selection, time. */
    private data class LiveSnapshot(
        val neptun: NeptunState,
        val slowRedKm: Int,
        val slowYellowKm: Int,
        val fastRedMin: Int,
        val fastYellowMin: Int,
        val userLocation: LatLng?,
        val selected: Threat?,
        val now: Long,
        val reveal: RevealRequest?,
        val tempNeutralizedId: String?
    )

    private data class UpdateUi(
        val update: UpdateState,
        val needsInstallPermission: Boolean,
        val latestVersion: String?
    )

    private data class AlertConfig(
        val slowRedArmed: Boolean,
        val slowYellowArmed: Boolean,
        val fastRedArmed: Boolean,
        val fastYellowArmed: Boolean,
        val officialAlertsEnabled: Boolean,
        val sirenOverride: Boolean,
        val followMe: Boolean,
        val showMapScale: Boolean,
        val deathAnimationEnabled: Boolean,
        val fastGroupCollapsed: Boolean,
        val slowGroupCollapsed: Boolean
    )

    private val liveSnapshot = combine(
        NeptunClient.state,
        zonesFlow,
        LocationTracker.location,
        selectedThreatFlow,
        nowFlow,
        revealFlow,
        tempNeutralizedFlow
    ) { values: Array<Any?> ->
        val neptun = values[0] as NeptunState
        val radii = values[1] as ZoneParams
        val location = values[2] as LatLng?
        val selected = values[3] as Threat?
        val now = values[4] as Long
        val reveal = values[5] as RevealRequest?
        val tempNeutralizedId = values[6] as String?
        LiveSnapshot(
            neptun,
            radii.slowRedKm, radii.slowYellowKm, radii.fastRedMin, radii.fastYellowMin,
            location, selected, now, reveal, tempNeutralizedId
        )
    }

    private val prefsSnapshot = combine(
        combine(
            threatMapFlow(prefs),
            threatAlertFlow(prefs),
            prefs.language(),
            prefs.disclaimerCollapsed(),
            prefs.disclaimerReadCount()
        ) { map, alert, lang, disclaimer, readCount ->
            ThreatPrefs(map, alert, lang, disclaimer, readCount)
        },
        combine(
            prefs.slowRedZoneArmed(),
            prefs.slowYellowZoneArmed(),
            prefs.fastRedZoneArmed(),
            prefs.fastYellowZoneArmed(),
            prefs.officialAlertsEnabled(),
            prefs.sirenOverride(),
            prefs.followMe(),
            prefs.showMapScale(),
            prefs.deathAnimationEnabled(),
            prefs.fastGroupCollapsed(),
            prefs.slowGroupCollapsed()
        ) { flags: Array<Boolean> ->
            AlertConfig(
                flags[0], flags[1], flags[2], flags[3], flags[4],
                flags[5], flags[6], flags[7], flags[8], flags[9], flags[10]
            )
        },
        combine(
            prefs.pinnedCity(),
            prefs.languageChosen(),
            prefs.batteryOnboardShown(),
            prefs.threatCardSize(),
            prefs.threatIconSet()
        ) { pinned, chosen, batteryShown, card, iconSet ->
            PrefsQuad(pinned, chosen, batteryShown, card, iconSet)
        },
        combine(
            prefs.fastVibrationLevel(),
            prefs.slowVibrationLevel()
        ) { fast, slow -> fast to slow },
        combine(
            combine(
                prefs.nightEnabled(), prefs.nightStartMin(), prefs.nightEndMin(),
                prefs.nightUseCustomZones()
            ) { enabled, start, end, use ->
                NightWindowPrefs(enabled, start, end, use)
            },
combine(
                    combine(
                        prefs.nightSlowRedKm(), prefs.nightSlowYellowKm(), prefs.nightFastRedMin(),
                        prefs.nightFastYellowMin()
                    ) { sr, sy, fr, fy ->
                        NightZonesPrefs(sr, sy, fr, fy, true, true, true, true, false, false)
                    },
                    combine(
                        prefs.nightSlowRedZoneArmed(), prefs.nightSlowYellowZoneArmed(),
                        prefs.nightFastRedZoneArmed(), prefs.nightFastYellowZoneArmed(),
                        prefs.nightZoneSirenOverride(), prefs.nightOfficialSirenOverride()
                    ) { flags: Array<Boolean> ->
                        flags
                    }
                ) { zones, flags ->
                    zones.copy(
                        slowRedArmed = flags[0],
                        slowYellowArmed = flags[1],
                        fastRedArmed = flags[2],
                        fastYellowArmed = flags[3],
                        zoneSirenOverride = flags[4],
                        officialSirenOverride = flags[5]
                    )
                }
        ) { window, zones -> NightPrefs(window, zones) }
    ) { a, b, c, vib, night ->
        PrefsSnapshot(
            mapEnabled = a.map,
            alertEnabled = a.alert,
            language = a.lang,
            disclaimerCollapsed = a.disclaimer,
            disclaimerReadCount = a.disclaimerReadCount,
            slowRedArmed = b.slowRedArmed,
            slowYellowArmed = b.slowYellowArmed,
            fastRedArmed = b.fastRedArmed,
            fastYellowArmed = b.fastYellowArmed,
            officialAlertsEnabled = b.officialAlertsEnabled,
            sirenOverride = b.sirenOverride,
            followMe = b.followMe,
            pinnedCity = c.pinnedCity,
            languageChosen = c.languageChosen,
            batteryOnboardShown = c.batteryOnboardShown,
            cardSize = c.cardSize,
            iconSet = c.iconSet,
            showMapScale = b.showMapScale,
            deathAnimationEnabled = b.deathAnimationEnabled,
            fastGroupCollapsed = b.fastGroupCollapsed,
            slowGroupCollapsed = b.slowGroupCollapsed,
            fastVibrationLevel = vib.first,
            slowVibrationLevel = vib.second,
            night = night
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
        prefs.slowRedKm().first()
        prefs.slowYellowKm().first()
        prefs.fastRedMin().first()
        prefs.fastYellowMin().first()
        prefs.slowRedZoneArmed().first()
        prefs.slowYellowZoneArmed().first()
        prefs.fastRedZoneArmed().first()
        prefs.fastYellowZoneArmed().first()
        prefs.followMe().first()
        prefs.pinnedCity().first()
        prefs.languageChosen().first()
        prefs.batteryOnboardShown().first()
        prefs.fastVibrationLevel().first()
        prefs.slowVibrationLevel().first()
        prefs.nightEnabled().first()
        prefs.nightStartMin().first()
        prefs.nightEndMin().first()
        prefs.nightUseCustomZones().first()
        prefs.nightSlowRedKm().first()
        prefs.nightSlowYellowKm().first()
        prefs.nightFastRedMin().first()
        prefs.nightFastYellowMin().first()
        prefs.nightSlowRedZoneArmed().first()
        prefs.nightSlowYellowZoneArmed().first()
        prefs.nightFastRedZoneArmed().first()
        prefs.nightFastYellowZoneArmed().first()
        prefs.nightZoneSirenOverride().first()
        prefs.nightOfficialSirenOverride().first()
        emit(Unit)
    }.flowOn(Dispatchers.IO)

val uiState: StateFlow<UiState> = combine(
        seedFlow,
        liveSnapshot,
        prefsSnapshot,
        updateUiFlow
    ) { _, live, prefs, updateUi ->
        val nightActive = isNightActive(
            NightConfig(prefs.night.window.enabled, prefs.night.window.startMin, prefs.night.window.endMin),
            live.now
        )
        val nightZones = NightZones(
            prefs.night.zones.slowRedKm, prefs.night.zones.slowYellowKm,
            prefs.night.zones.fastRedMin, prefs.night.zones.fastYellowMin,
            prefs.night.zones.slowRedArmed, prefs.night.zones.slowYellowArmed,
            prefs.night.zones.fastRedArmed, prefs.night.zones.fastYellowArmed
        )
        val effectiveParams = effectiveZoneParams(
            ZoneParams(live.slowRedKm, live.slowYellowKm, live.fastRedMin, live.fastYellowMin),
            nightZones, prefs.night.window.useCustomZones, nightActive
        )
        val activeArmed = effectiveArmed(
            ZoneArmed(
                prefs.slowRedArmed, prefs.slowYellowArmed,
                prefs.fastRedArmed, prefs.fastYellowArmed
            ),
            nightZones, prefs.night.window.useCustomZones, nightActive
        )
        buildUiState(
            neptun = live.neptun,
            slowRedKm = live.slowRedKm,
            slowYellowKm = live.slowYellowKm,
            fastRedMin = live.fastRedMin,
            fastYellowMin = live.fastYellowMin,
            effectiveParams = effectiveParams,
            mapEnabledTypes = prefs.mapEnabled,
            alertedTypes = prefs.alertEnabled,
            language = prefs.language,
            userLocation = live.userLocation,
            followMe = prefs.followMe,
            pinnedCity = prefs.pinnedCity?.let { name ->
                Cities.ALL.firstOrNull { it.nameUa == name }
            },
            selected = live.selected,
            now = live.now,
            reveal = live.reveal,
            tempNeutralizedId = live.tempNeutralizedId,
            deathAnimationEnabled = prefs.deathAnimationEnabled
        ).copy(
            update = updateUi.update,
            needsInstallPermission = updateUi.needsInstallPermission,
            latestVersion = updateUi.latestVersion,
            disclaimerCollapsed = prefs.disclaimerCollapsed,
            disclaimerReadCount = prefs.disclaimerReadCount,
            slowRedArmed = prefs.slowRedArmed,
            slowYellowArmed = prefs.slowYellowArmed,
            fastRedArmed = prefs.fastRedArmed,
            fastYellowArmed = prefs.fastYellowArmed,
            officialAlertsEnabled = prefs.officialAlertsEnabled,
            sirenOverride = prefs.sirenOverride,
            activeZoneParams = effectiveParams,
            activeSlowRedArmed = activeArmed.slowRed,
            activeSlowYellowArmed = activeArmed.slowYellow,
            activeFastRedArmed = activeArmed.fastRed,
            activeFastYellowArmed = activeArmed.fastYellow,
            nightActive = nightActive,
            nightWindowText = if (prefs.night.window.enabled) {
                nightWindowText(prefs.night.window.startMin, prefs.night.window.endMin)
            } else "",
            nightEnabled = prefs.night.window.enabled,
            nightStartMin = prefs.night.window.startMin,
            nightEndMin = prefs.night.window.endMin,
            nightUseCustomZones = prefs.night.window.useCustomZones,
            nightSlowRedKm = prefs.night.zones.slowRedKm,
            nightSlowYellowKm = prefs.night.zones.slowYellowKm,
            nightFastRedMin = prefs.night.zones.fastRedMin,
            nightFastYellowMin = prefs.night.zones.fastYellowMin,
            nightSlowRedArmed = prefs.night.zones.slowRedArmed,
            nightSlowYellowArmed = prefs.night.zones.slowYellowArmed,
            nightFastRedArmed = prefs.night.zones.fastRedArmed,
            nightFastYellowArmed = prefs.night.zones.fastYellowArmed,
            nightZoneSirenOverride = prefs.night.zones.zoneSirenOverride,
            nightOfficialSirenOverride = prefs.night.zones.officialSirenOverride,
            languageChosen = prefs.languageChosen,
            batteryOnboardShown = prefs.batteryOnboardShown,
            threatCardSize = prefs.cardSize,
            iconSet = prefs.iconSet,
            showMapScale = prefs.showMapScale,
            deathAnimationEnabled = prefs.deathAnimationEnabled,
            fastGroupCollapsed = prefs.fastGroupCollapsed,
            slowGroupCollapsed = prefs.slowGroupCollapsed,
            fastVibrationLevel = prefs.fastVibrationLevel,
            slowVibrationLevel = prefs.slowVibrationLevel
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UiState()
    )

    private fun buildUiState(
        neptun: NeptunState,
        slowRedKm: Int,
        slowYellowKm: Int,
        fastRedMin: Int,
        fastYellowMin: Int,
        effectiveParams: ZoneParams,
        mapEnabledTypes: Set<ThreatType>,
        alertedTypes: Set<ThreatType>,
        language: AppLanguage,
        userLocation: LatLng?,
        followMe: Boolean,
        pinnedCity: City?,
        selected: Threat?,
        now: Long,
        reveal: RevealRequest?,
        tempNeutralizedId: String?,
        deathAnimationEnabled: Boolean
    ): UiState {
        val animOn = deathAnimationEnabled
        val params = effectiveParams
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
        val focusBannerCity = (
            if (language == AppLanguage.UA) attribution.bannerCityUa else attribution.bannerCityEn
        ).ifBlank { Strings.get(language).unknownLocation }
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

        // Red zone tier: threats inside the red band (slow: within slowRedKm, fast: ETA ≤ fastRedMin).
        val inInner = mutableListOf<Threat>()
        // Yellow ring tier: threats inside the yellow band (slow: within slowYellowKm, fast: ETA ≤ fastYellowMin).
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
            val tier = zoneTier(t, distKm, speedKmh, params)
            if (tier != null) {
                val eta = etaMinutes(distKm, speedKmh)
                val (redVal, yellowVal) =
                    if (t.type in FastThreatTypes) params.fastRedMin to params.fastYellowMin
                    else params.slowRedKm to params.slowYellowKm
                threatScores.add(
                    ThreatLevelModel.scoreOf(t, distKm, eta, redVal, yellowVal, now)
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
        // The selected threat is gone (removed by the server, marked resolved/area-only, a ghost
        // past the hard cap, or TEMP long-pressed) — show a brief neutralized card, then drop
        // the selection.
        val selectedGone = selected != null && (
            (refreshedSelected?.let { t ->
                t.status == "resolved" || t.areaOnly || t.isGhost(now)
            } ?: true) || selected.id == tempNeutralizedId
            )
        // With the death animation disabled the card never flips to the "Neutralized" compact
        // form nor auto-dismisses: it stays open on the last-known snapshot until the user
        // closes it, so nothing animates anywhere.
        val neutralizedThreat = if (selectedGone && animOn) selected else null

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
                    params = params,
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
            threatsInner = inInner,
            threatsOuter = inOuter,
            mapThreats = mapThreats,
            userLocation = userLocation,
            slowRedKm = slowRedKm,
            slowYellowKm = slowYellowKm,
            fastRedMin = fastRedMin,
            fastYellowMin = fastYellowMin,
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
            selectedThreat = if (selectedGone && animOn) null else refreshedSelected,
            selectedThreatInfo = proximity,
            neutralizedThreat = neutralizedThreat,
            threatLevel = ThreatLevelModel.overall(threatScores),
            revealRequest = reveal,
            alertActive = mapThreats.isNotEmpty() || redCities.isNotEmpty()
        )
    }

    /** Localized "22:00–07:00" label for the configured night window. */
    private fun nightWindowText(startMin: Int, endMin: Int): String =
        "${timeText(startMin)}–${timeText(endMin)}"

    private fun timeText(min: Int): String =
        String.format(java.util.Locale.US, "%02d:%02d", min / 60, min % 60)

    fun setSlowRedKm(km: Int) {
        viewModelScope.launch { prefs.setSlowRedKm(km) }
    }

    fun setSlowYellowKm(km: Int) {
        viewModelScope.launch { prefs.setSlowYellowKm(km) }
    }

    fun setFastRedMin(min: Int) {
        viewModelScope.launch { prefs.setFastRedMin(min) }
    }

    fun setFastYellowMin(min: Int) {
        viewModelScope.launch { prefs.setFastYellowMin(min) }
    }

    fun setSlowRedArmed(armed: Boolean) {
        viewModelScope.launch { prefs.setSlowRedZoneArmed(armed) }
    }

    fun setSlowYellowArmed(armed: Boolean) {
        viewModelScope.launch { prefs.setSlowYellowZoneArmed(armed) }
    }

    fun setFastRedArmed(armed: Boolean) {
        viewModelScope.launch { prefs.setFastRedZoneArmed(armed) }
    }

    fun setFastYellowArmed(armed: Boolean) {
        viewModelScope.launch { prefs.setFastYellowZoneArmed(armed) }
    }

    /** Master alarm switch: arms or silences all four zone bells together. */
    fun setAlertsArmed(armed: Boolean) {
        viewModelScope.launch {
            prefs.setSlowRedZoneArmed(armed)
            prefs.setSlowYellowZoneArmed(armed)
            prefs.setFastRedZoneArmed(armed)
            prefs.setFastYellowZoneArmed(armed)
        }
    }

    fun setOfficialAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setOfficialAlertsEnabled(enabled) }
    }

    fun setSirenOverride(override: Boolean) {
        viewModelScope.launch { prefs.setSirenOverride(override) }
    }

    fun setFastVibrationLevel(level: Int) {
        viewModelScope.launch { prefs.setFastVibrationLevel(level) }
    }

    fun setSlowVibrationLevel(level: Int) {
        viewModelScope.launch { prefs.setSlowVibrationLevel(level) }
    }

    fun setBatteryOnboardShown(shown: Boolean) {
        viewModelScope.launch { prefs.setBatteryOnboardShown(shown) }
    }

    fun setNightEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setNightEnabled(enabled) }
    }

    fun setNightStartMin(min: Int) {
        viewModelScope.launch { prefs.setNightStartMin(min) }
    }

    fun setNightEndMin(min: Int) {
        viewModelScope.launch { prefs.setNightEndMin(min) }
    }

    fun setNightUseCustomZones(use: Boolean) {
        viewModelScope.launch { prefs.setNightUseCustomZones(use) }
    }

    fun setNightSlowRedKm(km: Int) {
        viewModelScope.launch { prefs.setNightSlowRedKm(km) }
    }

    fun setNightSlowYellowKm(km: Int) {
        viewModelScope.launch { prefs.setNightSlowYellowKm(km) }
    }

    fun setNightFastRedMin(min: Int) {
        viewModelScope.launch { prefs.setNightFastRedMin(min) }
    }

    fun setNightFastYellowMin(min: Int) {
        viewModelScope.launch { prefs.setNightFastYellowMin(min) }
    }

    fun setNightSlowRedArmed(armed: Boolean) {
        viewModelScope.launch { prefs.setNightSlowRedZoneArmed(armed) }
    }

    fun setNightSlowYellowArmed(armed: Boolean) {
        viewModelScope.launch { prefs.setNightSlowYellowZoneArmed(armed) }
    }

    fun setNightFastRedArmed(armed: Boolean) {
        viewModelScope.launch { prefs.setNightFastRedZoneArmed(armed) }
    }

    fun setNightFastYellowArmed(armed: Boolean) {
        viewModelScope.launch { prefs.setNightFastYellowZoneArmed(armed) }
    }

    fun setNightZoneSirenOverride(override: Boolean) {
        viewModelScope.launch { prefs.setNightZoneSirenOverride(override) }
    }

    fun setNightOfficialSirenOverride(override: Boolean) {
        viewModelScope.launch { prefs.setNightOfficialSirenOverride(override) }
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

    /** Wizard grid: one tap enables/disables a threat type for the map AND alerts together. */
    fun setThreatEnabled(type: ThreatType, enabled: Boolean) {
        viewModelScope.launch {
            prefs.setThreatMapVisible(type, enabled)
            prefs.setThreatAlertsEnabled(type, enabled)
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

    fun onDisclaimerShown() {
        viewModelScope.launch {
            prefs.disclaimerReadCount().first().let { count ->
                if (count < 3) prefs.setDisclaimerReadCount(count + 1)
            }
        }
    }

    fun setThreatCardSize(size: ThreatCardSize) {
        viewModelScope.launch { prefs.setThreatCardSize(size) }
    }

    fun setThreatIconSet(set: ThreatIconSet) {
        viewModelScope.launch { prefs.setThreatIconSet(set) }
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

    fun setDeathAnimationEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setDeathAnimationEnabled(enabled) }
    }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { prefs.setLanguage(lang) }
    }

    /** Dismiss the first-run language picker without changing the language. */
    fun skipLanguageChoose() {
        viewModelScope.launch { prefs.setLanguageChosen(true) }
    }

    /** Re-open the first-run setup (language, icon pack, alert groups, feature tour + battery
     *  prompt). Only flips the onboarding-completed flags — no setting is reset. */
    fun relaunchSetup() {
        viewModelScope.launch {
            prefs.setLanguageChosen(false)
            prefs.setBatteryOnboardShown(false)
        }
    }

    fun selectThreat(threat: Threat?) {
        tempNeutralizedFlow.value = null
        selectedThreatFlow.value = threat
    }

    /** TEMP: treat [id] as neutralized so its card self-destructs (map long-press test trigger). */
    fun tempNeutralize(id: String) {
        tempNeutralizedFlow.value = id
    }

    /**
     * A notification tap carrying the triggering threat's id/position: select it so the
     * popup opens, then ask the map to pan the camera onto it. Best-effort selection — on a
     * cold start the stream may not have the threat yet, and the pan still works from the
     * coordinates carried in the intent. With [select] false the camera pans without opening
     * the popup (footer strip taps).
     */
    fun revealThreat(id: String?, lat: Double, lon: Double, select: Boolean = true) {
        revealTick++
        tempNeutralizedFlow.value = null
        if (select && id != null) {
            selectedThreatFlow.value = NeptunClient.state.value.threats[id]
        }
        revealFlow.value = RevealRequest(revealTick, id, lat, lon)
    }

    /**
     * Footer strip tap: pan the camera onto [t]'s dead-reckoned position (where its marker
     * actually sits), without selecting it or opening the popup.
     */
    fun panToThreat(t: Threat) {
        val now = System.currentTimeMillis()
        val predicted = speedTracker.estimate(t.id, t)?.let { predictPosition(t, it, now) }
            ?: GeoPoint(t.lat, t.lon)
        revealThreat(t.id, predicted.latitude, predicted.longitude, select = false)
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
    private fun hasActiveAlert(): Boolean = uiState.value.alertActive

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
