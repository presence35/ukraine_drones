package ua.ukrainedrones

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import ua.ukrainedrones.connection.ConnectionHolder
import ua.ukrainedrones.connection.ConnectionState
import ua.ukrainedrones.connection.ConnEventKind
import ua.ukrainedrones.connection.NeptunConnectionClient
import ua.ukrainedrones.connection.isConnected
import ua.ukrainedrones.connection.isDegraded
import ua.ukrainedrones.connection.isOffline
import ua.ukrainedrones.connection.isPaused
import ua.ukrainedrones.connection.offlineSinceOrNull
import ua.ukrainedrones.connection.reconnectStartMillisOrZero
import ua.ukrainedrones.domain.ODESA_LAT
import ua.ukrainedrones.domain.ODESA_LON
import ua.ukrainedrones.data.ApiMonitor
import ua.ukrainedrones.data.ManifestResult
import ua.ukrainedrones.data.SystemEntry
import ua.ukrainedrones.data.SystemEntryKind
import ua.ukrainedrones.service.ServiceState
import java.util.Calendar

/**
 * Foreground, always-on monitoring. Owns the shared NeptunClient connection and
 * posts notifications when the oblast alarm turns on or when a tracked threat
 * enters the INNER tier (urgent siren) or the OUTER tier (warning chime).
 */
private data class Quint<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

class AlertService : Service() {

    companion object {
        const val ACTION_RETRY = "ua.ukrainedrones.RETRY"
        const val ACTION_IGNORE_RETRY = "ua.ukrainedrones.IGNORE_RETRY"
        const val EXTRA_REVEAL_ID = "reveal_threat_id"
        const val EXTRA_REVEAL_LAT = "reveal_threat_lat"
        const val EXTRA_REVEAL_LON = "reveal_threat_lon"
        const val EXTRA_SHOW_UPDATE = "show_update"
        private const val CHANNEL_MONITOR = "monitor"
        private const val CHANNEL_ALERTS = "alerts_siren2"
        private const val CHANNEL_ALERTS_OUTER = "alerts_siren_outer2"
        private const val CHANNEL_ALLCLEAR = "alerts_all_clear2"
        private const val CHANNEL_ALERTS_ALARM = "alerts_siren_alarm"
        private const val CHANNEL_ALERTS_OUTER_ALARM = "alerts_siren_outer_alarm"
        private const val CHANNEL_OFFLINE = "offline"
        private const val CHANNEL_OFFLINE_CRITICAL = "offline_critical"
        private const val CHANNEL_UPDATE = "updates"
        private const val NOTIF_MONITOR = 1
        private const val NOTIF_ALERT = 2
        private const val NOTIF_ALLCLEAR = 3
        private const val NOTIF_MILESTONE = 5
        private const val NOTIF_OFFLINE_CRITICAL = 6
        private const val NOTIF_UPDATE = 7
        /** Minutes offline before the (audible) critical offline notification rings. */
        const val CRITICAL_OFFLINE_MIN = 5
        private const val ALL_CLEAR_GRACE_MS = 20_000L
        /** How often the monitoring loop re-evaluates state (fast tick, decoupled from the
         *  all-clear grace so a threat that leaves a zone clears promptly). */
        private const val MONITOR_TICK_MS = 1_000L
        /** Tick interval when idle: screen off, no active threats, no outage. */
        private const val MONITOR_TICK_IDLE_MS = 30_000L
        /** Throttle for DebugLog.sweep() — runs at most this often. */
        private const val SWEEP_THROTTLE_MS = 10_000L
        /** Vibration level used for official alerts with no known reason threat (fixed, urgent). */
        private const val VIBRATION_STRONG = 4
        /** Fixed vibration level used for all zone alerts (strong, was configurable). */
        private const val VIBRATION_ZONE = 3

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AlertService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlertService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitoringJob: Job? = null

    private var wasFocusAlertActive = false
    private var officialRegionToken: String? = null
    private var currentReasonThreatId: String? = null
    private var officialAnnouncedToken: String? = null
    private var officialAnnouncedSince: String? = null
    private var officialAnnouncedReasonId: String? = null
    private var knownZones: Map<String, ThreatZone> = emptyMap()
    private var debugOfficialActive = false
    private var lastChannelLang: AppLanguage? = null
    private var emptySince: Long? = null
    private var lastMonitorTitle: String? = null
    private var lastMonitorText: String? = null
    private var lastMonitorRetry: String? = null
    private var lastMonitorProgressMax: Int? = null
    private var lastMonitorProgressNow: Int? = null
    private var lastMonitorIgnore: String? = null
    private var hasShownGpsFallbackToast = false
    @Volatile private var wasConnected = true
    private var offlineAlertJob: Job? = null
    private var offlineRestorePending = false
    private val screenOnFlow = MutableStateFlow(true)
    private var screenReceiver: BroadcastReceiver? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var hasActiveThreats = false
    private var isOutage = false
    private var lastSweepAtMs = 0L
private var notif3minShown = false
    private var notif6minShown = false
    private var notif10minShown = false
    private var notif20minShown = false
    private var notifCriticalShown = false
    private val tally by lazy { NeutralizedTally(applicationContext, scope) }
    @Volatile private var currentToken: String? = null

    private data class MonitorState(
        val focusOblastAlertActive: Boolean,
        val focusOblastAlertRaw: Boolean,
        val focusToken: String?,
        val focusOblastAlertSince: String?,
        val focusBannerCity: String,
        val focusCityUa: String?,
        val focusRegion: String,
        val focusPinned: Boolean,
        val officialReason: String?,
        val officialReasonThreatId: String?,
        val zoneThreats: Map<String, ThreatZone>,
        val params: ZoneParams,
        val lang: AppLanguage,
        val slowRedArmed: Boolean,
        val slowYellowArmed: Boolean,
        val fastRedArmed: Boolean,
        val fastYellowArmed: Boolean,
        val officialAlertsEnabled: Boolean,
        val zoneSirenOverride: Boolean,
        val officialSirenOverride: Boolean,
        val connectionState: ConnectionState,
        val threats: Map<String, Threat>,
        val alerts: List<OblastAlert>,
        val criticalOfflineOverride: Boolean,
        val fastVibrationLevel: Int,
        val slowVibrationLevel: Int,
        val focusLocation: LatLng?,
        val nightActive: Boolean,
        val enabled: Set<ThreatType>
    )

    /** Toggle + follow state used to gate zone/official alert tiering. */
    private data class AlertConfig(
        val slowRedArmed: Boolean,
        val slowYellowArmed: Boolean,
        val fastRedArmed: Boolean,
        val fastYellowArmed: Boolean,
        val officialAlertsEnabled: Boolean,
        val officialAlertCityScope: Boolean,
        val sirenOverride: Boolean,
        val followMe: Boolean,
        val criticalOfflineOverride: Boolean
    )

    /** Per-tick inputs merged with the threat-alert toggles: language, pin. */
    private data class TailPrefs(
        val enabled: Set<ThreatType>,
        val lang: AppLanguage,
        val pinned: String?
    )

    /** Night-mode window prefs (raw, day values untouched). */
    private data class NightWindow(
        val enabled: Boolean,
        val startMin: Int,
        val endMin: Int,
        val useCustomZones: Boolean
    )

    /** All night prefs combined, so the effective (night vs day) config resolves once per tick. */
    private data class NightSettings(
        val window: NightWindow,
        val zones: NightZones,
        val zoneSirenOverride: Boolean,
        val officialSirenOverride: Boolean
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        // Await the persisted log restore before the client starts its watchdog
        // (ConnectionLog.observe) — an async restore finishing after a fresh write would clobber it.
        scope.launch {
            ConnectionLog.attach(applicationContext)
            DebugLog.attach(applicationContext)
            ApiMonitor.attach(applicationContext)
            ConnectionLog.awaitAttached()
            DebugLog.awaitAttached()
            ApiMonitor.awaitAttached()
            val client = ConnectionHolder.getClient(applicationContext)
            val sup = ConnectionHolder.getSupervisor(applicationContext)
            val recStart = ServiceState(applicationContext).reconnectStartMillis().first()
            val ignoreUntil = ServiceState(applicationContext).ignoreRetryUntil().first()
            client.start(savedReconnectStartMs = recStart, savedIgnoreUntilMs = ignoreUntil)
            sup.start()
            ConnectionHolder.getClient(applicationContext)
                .testHarness.setForceOffline(ServiceState(applicationContext).forceOffline().first())
            // Persist reconnect timestamp across process kills
            launch {
                client.connectionState.collect { cs ->
                    when (cs) {
                        is ConnectionState.Offline -> ServiceState(applicationContext).setReconnectStartMillis(cs.reconnectStartMillis)
                        is ConnectionState.Connected -> ServiceState(applicationContext).setReconnectStartMillis(0L)
                        else -> {}
                    }
                }
            }
        }
        LocationTracker.start(this)
        WidgetUpdater.start(this, scope)
        startForegroundCompat()
        if (wakeLock == null || wakeLock?.isHeld == false) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UkraineDrones:AlertService").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        scope.launch { dailyUpdateCheckLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_IGNORE_RETRY) {
            // The user told the app to give up reconnecting for 30 minutes: no auto-retry until
            // the pause expires or they tap the Offline pill / Retry. The next tick re-renders
            // the monitor notification as "paused".
            ConnectionHolder.getClient(applicationContext).pauseFor(30)
        }
        if (intent?.action == ACTION_RETRY) {
            // The TEMP force-offline test toggle would keep NEPTUN "down" even after a
            // successful reconnect — turn it off so Retry truly restores the stream.
            scope.launch { ServiceState(applicationContext).setForceOffline(false) }
            val client = ConnectionHolder.getClient(applicationContext)
            client.testHarness.setForceOffline(false)
            client.retryNow()
        }
        if (intent?.action == NeutralizedTally.ACTION_NEUTRALIZED_DISMISS) {
            // The tally notification was swiped away (or its replay consumed in the app) —
            // reset the count and memory so any later neutralizations start a fresh tally
            // instead of resurrecting the dismissed one, and drop the notification itself.
            tally.reset()
            // The dismiss receiver can spin up a fresh service after "Stop Monitoring & Exit"
            // (the swipe races the shutdown) — that swipe must not resurrect monitoring.
            if (monitoringJob == null) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        startMonitoring()
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val notif = monitorNotification(
            title = Strings.get(AppLanguage.UA).notifOngoingTitle,
            text = "",
            retryLabel = null
        )
        // specialUse on 34+: Android 15 enforces a 6h/24h background cap on dataSync FGS,
        // which would stop a 24/7 monitor (onTimeout() then RemoteServiceException).
        ServiceCompat.startForeground(
            this,
            NOTIF_MONITOR,
            notif,
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                else -> 0
            }
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startMonitoring() {
        if (monitoringJob != null) return
        if (wakeLock == null || wakeLock?.isHeld == false) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UkraineDrones:AlertService").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        val prefs = UserPrefs(applicationContext)
        val svcState = ServiceState(applicationContext)
        // Register screen on/off receiver for adaptive tick.
        if (screenReceiver == null) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val nowOn = intent.action == Intent.ACTION_SCREEN_ON
                    if (nowOn && !screenOnFlow.value) {
                        // Screen just turned on — force an immediate re-evaluation.
                        // (The tick loop picks up the new interval on its next iteration.)
                    }
                    screenOnFlow.value = nowOn
                }
            }
            try {
                ContextCompat.registerReceiver(this, screenReceiver!!, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            } catch (_: Exception) {
                screenReceiver = null
                screenOnFlow.value = true
            }
        }
        // Server-driven resolutions/removals only — the map long-press never touches this
        // flow, so the tally excludes manual test triggers. Subscribed only while the tally is
        // enabled: off means no collector at all (not even a per-event pref read).
        scope.launch {
            prefs.neutralizedTallyEnabled()
                .distinctUntilChanged()
                .flatMapLatest { enabled ->
                    if (!enabled) emptyFlow() else ConnectionHolder.getClient(applicationContext).removedThreats
                }
                .collect { removed ->
                    // Default: only count resolutions that could matter to the user — those in the
                    // focus oblast (GPS-follow or pinned). The "All of Ukraine" sub-setting lifts
                    // that to any resolution country-wide.
                    if (!prefs.neutralizedTallyAllUkraine().first()) {
                        val token = currentToken ?: return@collect
                        if (!ThreatEvaluator.inOblast(removed.region, removed.district, removed.locality, token)) return@collect
                    }
                    tally.onResolved(removed, prefs.language().first())
                }
        }
        monitoringJob = scope.launch {
            // Restore the last announced official-alert episode BEFORE any tick can evaluate the
            // announce branch. Doing it here (not onCreate) removes the startup race where the
            // first handleState tick could re-ring an alert that a service kill already announced.
            // handleState reconciles these against the live episode; the prefs stay until the
            // episode truly ends, so repeated kills mid-episode still won't re-ring.
            officialAnnouncedToken = svcState.officialAnnouncedToken().first().ifBlank { null }
            officialAnnouncedSince = svcState.officialAnnouncedSince().first().ifBlank { null }
            officialAnnouncedReasonId = svcState.officialAnnouncedReasonId().first().ifBlank { null }
            // Restore offline-restore state before the first tick so handleState doesn't miss
            // a pre-kill outage (wasConnected starts true; this sets it false when needed).
            if (svcState.offlinePendingSince().first() > 0) {
                wasConnected = false
                offlineRestorePending = true
            }
            val nowFlow = MutableStateFlow(System.currentTimeMillis())
            launch {
                while (true) {
                    val fast = screenOnFlow.value || hasActiveThreats || isOutage
                    delay(if (fast) MONITOR_TICK_MS else MONITOR_TICK_IDLE_MS)
                    nowFlow.value = System.currentTimeMillis()
                }
            }
            combine(
                combine(
                    ConnectionHolder.getClient(applicationContext).connectionState,
                    ConnectionHolder.getClient(applicationContext).threats,
                    ConnectionHolder.getClient(applicationContext).alerts,
                    LocationTracker.location,
                    nowFlow
                ) { cs, threats, alerts, gps, now -> Quint(cs, threats, alerts, gps, now) },
                combine(
                    prefs.slowRedKm(), prefs.slowYellowKm(), prefs.fastRedMin(), prefs.fastYellowMin()
                ) { slowRed, slowYellow, fastRed, fastYellow ->
                    ZoneParams(slowRed, slowYellow, fastRed, fastYellow)
                },
                combine(
                    prefs.slowRedZoneArmed(),
                    prefs.slowYellowZoneArmed(),
                    prefs.fastRedZoneArmed(),
                    prefs.fastYellowZoneArmed(),
                    prefs.officialAlertsEnabled(),
                    prefs.officialAlertCityScope(),
                    prefs.sirenOverride(),
                    prefs.followMe(),
                    prefs.criticalOfflineOverride()
                ) { flags: Array<Boolean> ->
                    AlertConfig(
                        flags[0], flags[1], flags[2], flags[3], flags[4], flags[5], flags[6], flags[7], flags[8]
                    )
                },
                combine(
                    threatAlertFlow(prefs),
                    prefs.language(),
                    prefs.pinnedCity()
                ) { enabled, lang, pinned ->
                    TailPrefs(enabled, lang, pinned)
                },
                combine(
                    combine(
                        prefs.nightEnabled(), prefs.nightStartMin(), prefs.nightEndMin(),
                        prefs.nightUseCustomZones()
                    ) { enabled, start, end, use ->
                        NightWindow(enabled, start, end, use)
                    },
                    combine(
                        combine(
                            prefs.nightSlowRedKm(), prefs.nightSlowYellowKm(), prefs.nightFastRedMin(),
                            prefs.nightFastYellowMin()
                        ) { sr, sy, fr, fy ->
                            NightZones(sr, sy, fr, fy, slowRedArmed = true, slowYellowArmed = true, fastRedArmed = true, fastYellowArmed = true)
                        },
                        combine(
                            prefs.nightSlowRedZoneArmed(), prefs.nightSlowYellowZoneArmed(),
                            prefs.nightFastRedZoneArmed(), prefs.nightFastYellowZoneArmed()
                        ) { flags: Array<Boolean> ->
                            flags
                        }
                    ) { zones, flags ->
                        zones.copy(
                            slowRedArmed = flags[0],
                            slowYellowArmed = flags[1],
                            fastRedArmed = flags[2],
                            fastYellowArmed = flags[3]
                        )
                    },
                    combine(
                        prefs.nightZoneSirenOverride(), prefs.nightOfficialSirenOverride()
                    ) { zoneOv, officialOv -> zoneOv to officialOv }
                ) { window, zones, ov ->
                    NightSettings(window, zones, ov.first, ov.second)
                }
            ) { core, params, config, tail, night ->
                val cs = core.a as ConnectionState
                val threats = core.b as Map<String, Threat>
                val alerts = core.c as List<OblastAlert>
                val gps = core.d as LatLng?
                val now = core.e as Long
                val followMe = config.followMe
                val lang = tail.lang
                val pinned = tail.pinned?.let { name -> Cities.byUa[name] }
                val odesaFallback = LatLng(ODESA_LAT, ODESA_LON)
                val focus = if (followMe) gps ?: odesaFallback
                    else pinned?.let { LatLng(it.lat, it.lon) } ?: gps ?: odesaFallback
                if (gps == null && !hasShownGpsFallbackToast) {
                    hasShownGpsFallbackToast = true
                    val s = Strings.get(lang)
                    runCatching {
                        android.widget.Toast.makeText(
                            this@AlertService, s.gpsFallbackOdesa, android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                val nightActive = isNightActive(
                    NightConfig(night.window.enabled, night.window.startMin, night.window.endMin),
                    now
                )
                val effectiveParams = effectiveZoneParams(
                    params, night.zones, night.window.useCustomZones, nightActive
                )
                val armed = effectiveArmed(
                    ZoneArmed(
                        config.slowRedArmed, config.slowYellowArmed,
                        config.fastRedArmed, config.fastYellowArmed
                    ),
                    night.zones, night.window.useCustomZones, nightActive
                )
                val zoneSirenOverride = if (nightActive) night.zoneSirenOverride else config.sirenOverride
                val officialSirenOverride = if (nightActive) night.officialSirenOverride else config.sirenOverride
                val attribution = focusAttribution(followMe, gps, pinned)
                val cityUa = attribution.bannerCityUa.takeIf { it.isNotBlank() }
                val officialActive = officialAlertActiveFor(
                    alerts,
                    attribution.token,
                    cityUa,
                    config.officialAlertCityScope
                )
                val officialRaw = officialAlertActiveFor(
                    alerts,
                    attribution.token,
                    cityUa,
                    false
                )
                val officialSince: String? = attribution.token?.let { token ->
                    alerts
                        .firstOrNull { alert ->
                            alert.inOblast(token) &&
                                (!config.officialAlertCityScope || cityUa.isNullOrBlank() || alert.coversCity(cityUa))
                        }
                        ?.since
                }
                val (officialReason, officialReasonThreatId) = if (officialActive) {
                    ThreatEvaluator.buildOfficialReason(
                        threats, attribution.token, lang, focus,
                        effectiveParams, now,
                        tail.enabled,
                        focusRegionText(lang, followMe, pinned)
                    )
                } else null to null
                MonitorState(
                    focusOblastAlertActive = officialActive,
                    focusOblastAlertRaw = officialRaw,
                    focusToken = attribution.token,
                    focusOblastAlertSince = officialSince,
                    focusBannerCity = (
                        if (lang == AppLanguage.UA) attribution.bannerCityUa else attribution.bannerCityEn
                    ).ifBlank { Strings.get(lang).unknownLocation },
                    focusCityUa = attribution.bannerCityUa.takeIf { it.isNotBlank() },
                    focusRegion = focusRegionText(lang, followMe, pinned),
                    focusPinned = !followMe && pinned != null,
                    officialReason = officialReason,
                    officialReasonThreatId = officialReasonThreatId,
                    zoneThreats = ThreatEvaluator.zoneThreats(threats, effectiveParams, focus, tail.enabled, now),
                    params = effectiveParams,
                    lang = lang,
                    slowRedArmed = armed.slowRed,
                    slowYellowArmed = armed.slowYellow,
                    fastRedArmed = armed.fastRed,
                    fastYellowArmed = armed.fastYellow,
                    officialAlertsEnabled = config.officialAlertsEnabled,
                    zoneSirenOverride = zoneSirenOverride,
                    officialSirenOverride = officialSirenOverride,
                    connectionState = cs,
                    threats = threats,
                    alerts = alerts,
                    criticalOfflineOverride = config.criticalOfflineOverride,
                    fastVibrationLevel = VIBRATION_ZONE,
                    slowVibrationLevel = VIBRATION_ZONE,
                    focusLocation = focus,
                    nightActive = nightActive,
                    enabled = tail.enabled
                )
            }.collect { handleState(it) }
        }
    }

    private fun focusRegionText(lang: AppLanguage, followMe: Boolean, pinned: City?): String {
        val s = Strings.get(lang)
        if (!followMe && pinned != null) {
            val token = Cities.cityOblast[pinned.nameUa]
            return if (lang == AppLanguage.UA) {
                // "Київськ" -> "Київська область"
                String.format(s.notifBodyRegionFormat, "${token ?: ""}а")
            } else {
                String.format(s.notifBodyRegionFormat, pinned.nameEn)
            }
        }
        return s.notifBodyRegion
    }

    private suspend fun handleState(state: MonitorState) {
        val s = Strings.get(state.lang)
        currentToken = state.focusToken

        if (lastChannelLang != state.lang) {
            updateMonitorChannel(s)
            lastChannelLang = state.lang
        }

        // Offline tracking: the always-visible ongoing monitor notification switches to the offline
        // wording with a Retry action once the drop outlasts the shared grace (short blips that
        // recover inside it never reach the offline wording). No separate one-shot alert — a
        // second notification with its own Retry button was just duplicate noise in the shade.
        if (state.connectionState.isConnected) {
            offlineAlertJob?.cancel()
            offlineAlertJob = null
            offlineRestorePending = false
            if (!wasConnected) persistOfflineSince(0L)
        } else if (wasConnected) {
            // Just dropped: remember the outage start so a service kill mid-outage still
            // re-flags it (see the offlineRestorePending branch on the next start).
            offlineAlertJob?.cancel()
            persistOfflineSince(System.currentTimeMillis())
            offlineAlertJob = scope.launch {
                // Once the grace elapses (and an official alert didn't already surface), the
                // monitor notification's Retry action carries the recovery path anyway.
                delay(NeptunConnectionClient.OFFLINE_GRACE_MS)
                if (ConnectionHolder.getClient(applicationContext).connectionState.value.isConnected) persistOfflineSince(0L)
            }
        } else if (offlineRestorePending) {
            // An outage was already in progress when the service died and restarted; the
            // pre-kill drop was never flagged. The ongoing monitor notification re-renders the
            // offline state on the first tick, so just clear the restore flag.
            offlineRestorePending = false
            offlineAlertJob?.cancel()
            offlineAlertJob = scope.launch {
                delay(NeptunConnectionClient.OFFLINE_GRACE_MS)
                if (ConnectionHolder.getClient(applicationContext).connectionState.value.isConnected) persistOfflineSince(0L)
            }
        }
        val wasConnectedBefore = wasConnected
        wasConnected = state.connectionState.isConnected
        if (state.connectionState.isConnected && !wasConnectedBefore) {
            // Just reconnected: reset the offline-milestone ladder and drop any lingering
            // milestone notification so it can't stay in the shade after the outage ends.
            notif3minShown = false
            notif6minShown = false
            notif10minShown = false
            notif20minShown = false
            notifCriticalShown = false
            try {
                NotificationManagerCompat.from(this).cancel(NOTIF_MILESTONE)
                NotificationManagerCompat.from(this).cancel(NOTIF_OFFLINE_CRITICAL)
            } catch (_: SecurityException) {
            }
        }

        // Reconnection milestone notifications (3min, 6min, 10min, 20min)
        val cs = state.connectionState
        val reconnectStart = cs.reconnectStartMillisOrZero
        val now = System.currentTimeMillis()
        val elapsedSinceReconnect = if (reconnectStart > 0 && now > reconnectStart) now - reconnectStart else 0L
        val threeMinMs = 3 * 60 * 1000L
        val sixMinMs = 6 * 60 * 1000L
        val tenMinMs = 10 * 60 * 1000L
        val twentyMinMs = 20 * 60 * 1000L

        if (!state.connectionState.isConnected && elapsedSinceReconnect > 0) {
            // Show notification at 3 minutes
            if (elapsedSinceReconnect >= threeMinMs && !notif3minShown) {
                notif3minShown = true
                ConnectionHolder.getSupervisor(applicationContext).recordEvent(ConnEventKind.MILESTONE_3)
                val notif = NotificationCompat.Builder(this, CHANNEL_OFFLINE)
                    .setSmallIcon(R.drawable.ic_wifi_off)
                    .setContentTitle(s.offlineStatusTitle)
                    .setContentText(s.offlineMilestone3Min)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(openAppIntent())
                    .build()
                safeNotify(NOTIF_MILESTONE, notif)
            }
            // Show notification at 6 minutes
            if (elapsedSinceReconnect >= sixMinMs && !notif6minShown) {
                notif6minShown = true
                ConnectionHolder.getSupervisor(applicationContext).recordEvent(ConnEventKind.MILESTONE_6)
                val notif = NotificationCompat.Builder(this, CHANNEL_OFFLINE)
                    .setSmallIcon(R.drawable.ic_wifi_off)
                    .setContentTitle(s.offlineStatusTitle)
                    .setContentText(s.offlineMilestone6Min)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(openAppIntent())
                    .build()
                safeNotify(NOTIF_MILESTONE, notif)
            }
            // Show notification at 10 minutes
            if (elapsedSinceReconnect >= tenMinMs && !notif10minShown) {
                notif10minShown = true
                ConnectionHolder.getSupervisor(applicationContext).recordEvent(ConnEventKind.MILESTONE_10)
                val notif = NotificationCompat.Builder(this, CHANNEL_OFFLINE)
                    .setSmallIcon(R.drawable.ic_wifi_off)
                    .setContentTitle(s.offlineStatusTitle)
                    .setContentText(s.offlineMilestone10Min)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(openAppIntent())
                    .build()
                safeNotify(NOTIF_MILESTONE, notif)
            }
            // Show notification at 20 minutes and stop reconnection attempts
            if (elapsedSinceReconnect >= twentyMinMs && !notif20minShown) {
                notif20minShown = true
                ConnectionHolder.getSupervisor(applicationContext).recordEvent(ConnEventKind.MILESTONE_20)
                val notif = NotificationCompat.Builder(this, CHANNEL_OFFLINE)
                    .setSmallIcon(R.drawable.ic_wifi_off)
                    .setContentTitle(s.offlineStatusTitle)
                    .setContentText(s.offlineMilestone20Min)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(openAppIntent())
                    .build()
                safeNotify(NOTIF_MILESTONE, notif)
                ConnectionHolder.getClient(applicationContext).pauseFor(30)
            }
            // Critical offline override: ring an audible notification once the drop outlasts
            // CRITICAL_OFFLINE_MIN minutes (on its own channel, so it can sound while the
            // silent CHANNEL_OFFLINE milestones stay quiet).
            if (state.criticalOfflineOverride && elapsedSinceReconnect >= CRITICAL_OFFLINE_MIN * 60_000L && !notifCriticalShown) {
                notifCriticalShown = true
                ConnectionHolder.getSupervisor(applicationContext).recordEvent(ConnEventKind.MILESTONE_5)
                val notif = NotificationCompat.Builder(this, CHANNEL_OFFLINE_CRITICAL)
                    .setSmallIcon(R.drawable.ic_wifi_off)
                    .setContentTitle(s.offlineStatusTitle)
                    .setContentText(s.offlineCritical5Min)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(openAppIntent())
                    .addAction(0, s.offlineRetryAction, retryPendingIntent())
                    .build()
                safeNotify(NOTIF_OFFLINE_CRITICAL, notif)
            }
        }

        val offlineMinutes = (elapsedSinceReconnect / 60_000L).toInt().coerceIn(0, 20)
        val isOfflineNow = state.connectionState.isOffline
        notifyMonitor(
            title = when {
                isOfflineNow -> s.offlineStatusTitle
                state.focusPinned -> String.format(s.notifMonitoringCityFormat, state.focusBannerCity)
                else -> s.notifOngoingTitle
            },
            text = when {
                isOfflineNow -> offlineLiveBody(s, offlineMinutes)
                state.connectionState.isDegraded -> s.connDegradedBody
                else -> ""
            },
            retryLabel = if (isOfflineNow) s.offlineRetryAction else null,
            progressMax = if (isOfflineNow) 20 else null,
            progressNow = if (isOfflineNow) offlineMinutes else null,
            ignoreLabel = if (isOfflineNow && elapsedSinceReconnect >= twentyMinMs) s.offlineIgnoreAction else null
        )

        val all = state.threats

        /** Channel tier after arming toggles are applied; null = no sound for this object. */
        fun alertTier(id: String, spatial: ThreatZone): ThreatZone? {
            val fast = FastThreatTypes.contains(all[id]?.type)
            val red = if (fast) state.fastRedArmed else state.slowRedArmed
            val yellow = if (fast) state.fastYellowArmed else state.slowYellowArmed
            return when (spatial) {
                ThreatZone.INNER -> if (red) ThreatZone.INNER
                else if (yellow) ThreatZone.OUTER else null
                ThreatZone.OUTER -> if (yellow) ThreatZone.OUTER else null
            }
        }

        // Fire when a threat's alert tier changes (closest/urgent tier wins).
        // Coalesce every trigger in this update into one post: re-notifying the same alert id
        // restarts the siren, so a zone entry + official edge together would double-play it.
        val alertable = state.zoneThreats.entries
            .mapNotNull { (id, spatial) -> alertTier(id, spatial)?.let { id to it } }
            .toMap()
        var posted = false
        var postedId: String? = null
        val newEntries = alertable.entries
            .filter { (id, zone) -> knownZones[id] != zone }
            .sortedBy { it.value.ordinal }
        if (newEntries.isNotEmpty()) {
            // Post one notification for the most urgent newly-changed threat, but only mark
            // THAT threat as known — every other newly-changed threat stays "unknown" so it
            // gets its own alert on the very next tick instead of being silently absorbed.
            val (id, zone) = newEntries.first()
            postedId = id
            val t = all[id]
            val body = t?.let { ThreatEvaluator.threatBody(it, state.lang) } ?: s.notifBodyRegion
            postAlert(
                zone, bannerFor(zone, s), body, state.zoneSirenOverride, revealThreat = t,
                vibrationLevel = if (t?.type in FastThreatTypes) state.fastVibrationLevel else state.slowVibrationLevel
            )
            posted = true
            knownZones = knownZones + (id to zone)
        }
        // Drop ids that left zoneThreats entirely (resolved/expired/out of range) so a future
        // re-entry is treated as new — except ids the user shot down within the grace window:
        // their quick same-id respawn is the same drone coming back and must not re-alert.
        // Everything else keeps its value so the not-yet-fired new entries are re-evaluated
        // on the next tick.
        val client = ConnectionHolder.getClient(applicationContext)
        val droppedZoneIds = knownZones.keys.filterNot { id ->
            id in state.zoneThreats.keys || client.wasUserShotRecently(id)
        }
        knownZones = knownZones.filterKeys { it !in droppedZoneIds }

        // Restart reconciliation: an episode we already announced before a service kill must
        // not re-ring. The persisted identity (focus token + NEPTUN `since`) is restored into
        // the instance flags once, then the in-memory copy is dropped — the prefs stay until
        // the episode truly ends, so a second kill mid-episode still won't re-ring.
        // Re-enabling the official-alerts toggle is NOT a re-announce trigger: an already-rung
        // alert stays rung (wasFocusAlertActive is still true), and an alert that genuinely
        // started while muted falls through to the announce branch below on its own.
        if (!wasFocusAlertActive && officialAnnouncedToken != null && officialAnnouncedSince != null &&
            state.focusToken == officialAnnouncedToken &&
            state.focusOblastAlertSince != null &&
            state.focusOblastAlertSince == officialAnnouncedSince
        ) {
            wasFocusAlertActive = true
            currentReasonThreatId = officialAnnouncedReasonId
            officialRegionToken = state.focusToken
        }
        officialAnnouncedToken = null
        officialAnnouncedSince = null
        officialAnnouncedReasonId = null

        // Official oblast-level alert (independent of zone membership). Gated by the
        // Settings toggle — turning it off stops only official-alert notifications,
        // never the Red/Yellow zone alerts.
        val officialActive = state.officialAlertsEnabled && state.focusOblastAlertActive
        val officialBody = state.officialReason ?: state.focusRegion
        // Debug log: track the RAW official-alert lifecycle (independent of the notification
        // toggle) so an alert that was never announced still shows up, with the why.
        if (!debugOfficialActive && state.focusOblastAlertActive) {
            debugOfficialActive = true
            val reasonThreat = state.officialReasonThreatId?.let { all[it] }
            DebugLog.recordOfficial(
                DebugLogKind.OFFICIAL_ON,
                night = state.nightActive,
                sirenOverride = state.officialSirenOverride,
                vibrationLevel = reasonThreat?.let {
                    if (it.type in FastThreatTypes) state.fastVibrationLevel else state.slowVibrationLevel
                } ?: VIBRATION_STRONG,
                notified = state.officialAlertsEnabled && !posted,
                reason = when {
                    !state.officialAlertsEnabled -> DebugLogReason.TOGGLE_OFF
                    posted -> DebugLogReason.COALESCED
                    else -> DebugLogReason.FIRED
                },
                threatId = reasonThreat?.id,
                threatType = reasonThreat?.type,
                locality = reasonThreat?.let { it.locality ?: it.district ?: it.region } ?: state.focusCityUa,
                distanceKm = distanceFromFocusKm(reasonThreat, state),
                now = System.currentTimeMillis()
            )
        }
        if (officialActive && !wasFocusAlertActive && !posted) {
            val reasonThreat = state.officialReasonThreatId?.let { all[it] }
            postAlert(
                null,
                String.format(s.alertBannerFormat, state.focusBannerCity),
                officialBody,
                state.officialSirenOverride,
                revealThreat = reasonThreat,
                vibrationLevel = reasonThreat?.let {
                    if (it.type in FastThreatTypes) state.fastVibrationLevel else state.slowVibrationLevel
                } ?: VIBRATION_STRONG
            )
            currentReasonThreatId = state.officialReasonThreatId
            officialRegionToken = state.focusToken
            wasFocusAlertActive = true
            persistOfficialAnnounced(state)
        } else if (officialActive && wasFocusAlertActive && !posted && alertable.isEmpty() &&
            state.officialReasonThreatId != currentReasonThreatId && alertNotificationShowing()
        ) {
            // Wait-for-reason: the official alert fired with only a region-level body; keep
            // updating the SAME NOTIF_ALERT silently as a specific threat reason arrives.
            // Mirrors the knownZones change-tracking above — a same-id re-post is guarded on
            // the threat behind the reason (not its text, which NEPTUN refreshes as
            // confirmations tick up) so the siren isn't re-triggered — and never clobbers a
            // ringing zone alert (alertable is non-empty then). It only refreshes while the
            // alert notification is STILL showing: if the user tapped/swiped it away, a re-post
            // would be a brand-new notification that rings again (setOnlyAlertOnce only
            // suppresses updates to an existing one) — a same-episode reason refresh must never
            // resurrect a dismissed "official alert" as a new siren.
            val reasonThreat = state.officialReasonThreatId?.let { all[it] }
            postAlert(
                null,
                String.format(s.alertBannerFormat, state.focusBannerCity),
                officialBody,
                state.officialSirenOverride,
                revealThreat = reasonThreat,
                vibrationLevel = reasonThreat?.let {
                    if (it.type in FastThreatTypes) state.fastVibrationLevel else state.slowVibrationLevel
                } ?: VIBRATION_STRONG,
                silent = true
            )
            currentReasonThreatId = state.officialReasonThreatId
            persistOfficialAnnounced(state)
        }
        // Scope suppression: an announced alert hidden by the city-scope filter (City level
        // switched on mid-episode, or coverage dropped while it is on) must NOT run through the
        // all-clear machinery — the NEPTUN episode is still live and a settings flip is not a
        // lifecycle event. Drop the notification silently instead; when coverage returns (or the
        // scope goes back to oblast-wide), the announce branch above rings fresh.
        if (state.officialAlertsEnabled && wasFocusAlertActive && state.focusOblastAlertRaw &&
            !state.focusOblastAlertActive
        ) {
            if (alertable.isEmpty()) {
                cancelAlert()
            }
            currentReasonThreatId = null
            debugOfficialActive = false
            wasFocusAlertActive = false
            clearOfficialAnnounced()
            DebugLog.recordOfficial(
                DebugLogKind.OFFICIAL_OFF,
                night = state.nightActive,
                sirenOverride = state.officialSirenOverride,
                vibrationLevel = null,
                notified = false,
                reason = DebugLogReason.TOGGLE_OFF,
                threatId = null,
                threatType = null,
                locality = state.focusCityUa,
                distanceKm = null,
                now = System.currentTimeMillis()
            )
        }
        // All clear: the official alert that was ringing has just ended. The cheerful chime
        // fires only for the official oblast alert — zone-threat clears stay silent — and
        // never when the official-alert notifications are turned off. It fires only while we
        // are still focused on the region whose alert was ringing: switching the focus away
        // to a non-alerting region must NOT announce an all-clear for the old region (it's no
        // longer relevant) — that's handled silently below. When no zone alert is active,
        // cancel the lingering siren notification immediately instead of waiting for the 60s
        // grace path; if a zone alert is still ringing, leave it up.
        if (state.officialAlertsEnabled && wasFocusAlertActive && !state.focusOblastAlertRaw &&
            state.focusToken == officialRegionToken
        ) {
            if (alertable.isEmpty()) {
                cancelAlert()
            }
            postAllClear(s, state.focusBannerCity)
            currentReasonThreatId = null
            officialRegionToken = null
            debugOfficialActive = false
            clearOfficialAnnounced()
            DebugLog.recordOfficial(
                DebugLogKind.OFFICIAL_OFF,
                night = state.nightActive,
                sirenOverride = state.officialSirenOverride,
                vibrationLevel = null,
                notified = true,
                reason = DebugLogReason.FIRED,
                threatId = null,
                threatType = null,
                locality = state.focusCityUa,
                distanceKm = null,
                now = System.currentTimeMillis()
            )
        }
        // Focus switched away from the region whose official alert was ringing, to a
        // non-alerting region. Drop the active-alert tracking silently — no all-clear chime
        // for the old region, no lingering siren — so returning to that still-alerting region
        // re-announces (fresh siren) on the next tick.
        if (wasFocusAlertActive && !state.focusOblastAlertActive &&
            officialRegionToken != null && state.focusToken != officialRegionToken
        ) {
            if (alertable.isEmpty()) {
                cancelAlert()
            }
            currentReasonThreatId = null
            debugOfficialActive = false
            officialRegionToken = null
            wasFocusAlertActive = false
            clearOfficialAnnounced()
        }
        // An alert that ends (or was never notified) while notifications are off must not leave
        // the "already notified" flag stuck on for a future alert.
        if (!state.focusOblastAlertActive) {
            // Ended while official-alert notifications were off: no all-clear fired, log why.
            if (debugOfficialActive) {
                debugOfficialActive = false
                DebugLog.recordOfficial(
                    DebugLogKind.OFFICIAL_OFF,
                    night = state.nightActive,
                    sirenOverride = state.officialSirenOverride,
                    vibrationLevel = null,
                    notified = false,
                    reason = DebugLogReason.TOGGLE_OFF,
                    threatId = null,
                    threatType = null,
                    locality = state.focusCityUa,
                    distanceKm = null,
                    now = System.currentTimeMillis()
                )
            }
            wasFocusAlertActive = false
            officialRegionToken = null
            clearOfficialAnnounced()
        }

        // Debug verdict sweep: log every threat in the active region and why it did or
        // didn't fire, using the service's own computed maps. Read-only for the decision path.
        val nowForSweep = System.currentTimeMillis()
        if (nowForSweep - lastSweepAtMs >= SWEEP_THROTTLE_MS) {
            lastSweepAtMs = nowForSweep
            DebugLog.sweep(
                DebugLogContext(
                    threats = all,
                    focus = state.focusLocation,
                    token = state.focusToken,
                    enabledTypes = state.enabled,
                    zoneThreats = state.zoneThreats,
                    alertable = alertable,
                    knownZones = knownZones,
                    postedId = postedId,
                    night = state.nightActive,
                    sirenOverride = state.zoneSirenOverride,
                    fastVibrationLevel = state.fastVibrationLevel,
                    slowVibrationLevel = state.slowVibrationLevel,
                    now = now
                )
            )
        }

        // Start the grace window once nothing is active; clear only after it expires.
        // The periodic nowFlow tick re-runs this every grace period, so a quiet stream still
        // clears stale alerts (threats leave the zone map on the next tick, not on demand).
        if (state.zoneThreats.isEmpty() && !state.focusOblastAlertActive) {
            val since = emptySince
            if (since == null) {
                emptySince = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - since >= ALL_CLEAR_GRACE_MS) {
                emptySince = null
                cancelAlert()
                knownZones = emptyMap()
            }
        } else {
            emptySince = null
        }
        hasActiveThreats = state.zoneThreats.isNotEmpty() || state.focusOblastAlertActive
        isOutage = !state.connectionState.isConnected
    }

    private fun bannerFor(zone: ThreatZone, s: Strings.StringSet): String = when (zone) {
        ThreatZone.INNER -> s.redZoneAlert
        ThreatZone.OUTER -> s.yellowZoneAlert
    }

    /** Approx. distance from the focus point (GPS/pin) to a threat, km. */
    private fun distanceFromFocusKm(t: Threat?, state: MonitorState): Double? {
        val focus = state.focusLocation ?: return null
        if (t == null) return null
        return distanceMeters(focus.lat, focus.lon, t.lat, t.lon) / 1000.0
    }

    private fun monitorNotification(
        title: String,
        text: String,
        retryLabel: String?,
        progressMax: Int? = null,
        progressNow: Int? = null,
        ignoreLabel: String? = null
    ) =
        NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_trident)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .apply {
                if (retryLabel != null) {
                    addAction(0, retryLabel, retryPendingIntent())
                }
                if (ignoreLabel != null) {
                    addAction(0, ignoreLabel, ignoreRetryPendingIntent())
                }
                if (progressMax != null && progressNow != null) {
                    setProgress(progressMax, progressNow, false)
                }
            }
            .build()

    private fun notifyMonitor(
        title: String,
        text: String,
        retryLabel: String?,
        progressMax: Int? = null,
        progressNow: Int? = null,
        ignoreLabel: String? = null
    ) {
        if (title == lastMonitorTitle && text == lastMonitorText && retryLabel == lastMonitorRetry &&
            progressMax == lastMonitorProgressMax && progressNow == lastMonitorProgressNow && ignoreLabel == lastMonitorIgnore
        ) return
        lastMonitorTitle = title
        lastMonitorText = text
        lastMonitorRetry = retryLabel
        lastMonitorProgressMax = progressMax
        lastMonitorProgressNow = progressNow
        lastMonitorIgnore = ignoreLabel
        safeNotify(NOTIF_MONITOR, monitorNotification(title, text, retryLabel, progressMax, progressNow, ignoreLabel))
    }

    /** Live offline body: "X/20 min · attempt N" (or the paused wording). */
    private fun offlineLiveBody(s: Strings.StringSet, minutes: Int): String {
        val client = ConnectionHolder.getClient(applicationContext)
        val cs = client.connectionState.value
        if (cs is ConnectionState.Paused) return s.offlinePausedBody
        val attempt = when (cs) {
            is ConnectionState.Offline -> cs.attempt
            is ConnectionState.Connecting -> cs.attempt
            else -> 0
        }
        return String.format(s.offlineLiveFormat, minutes, 20, attempt + 1)
    }

    private fun buildAlertNotification(
        zone: ThreatZone?,
        title: String,
        body: String,
        sirenOverride: Boolean,
        revealThreat: Threat?,
        silent: Boolean,
        vibrationLevel: Int
    ): NotificationCompat.Builder {
        // Without the override, sirens follow the phone's ringer/vibrate mode via the
        // notification stream; with it, they ring on the alarm stream even in vibrate/silent.
        // All-clear never overrides — it's not an emergency.
        val channel = when {
            sirenOverride && zone == ThreatZone.OUTER -> CHANNEL_ALERTS_OUTER_ALARM
            sirenOverride -> CHANNEL_ALERTS_ALARM
            zone == ThreatZone.OUTER -> CHANNEL_ALERTS_OUTER
            else -> CHANNEL_ALERTS
        }
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_trident)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(revealThreat))
            .setVibrate(vibrationPattern(vibrationLevel))
            .apply {
                // Silent updates refresh the alert body without re-ringing the siren on the
                // same notification (e.g. a reason text arriving after the initial alert).
                if (silent) setOnlyAlertOnce(true)
                // Override alerts ring on the alarm stream and launch the app full-screen over
                // DND too, so a genuinely missed siren still lands in front of the user.
                if (sirenOverride) setFullScreenIntent(openAppIntent(revealThreat), true)
            }
    }

    private fun postAlert(
        zone: ThreatZone?,
        title: String,
        body: String,
        sirenOverride: Boolean,
        revealThreat: Threat? = null,
        silent: Boolean = false,
        vibrationLevel: Int = 3
    ) {
        safeNotify(
            NOTIF_ALERT,
            buildAlertNotification(zone, title, body, sirenOverride, revealThreat, silent, vibrationLevel).build()
        )
    }

    private fun cancelAlert() {
        try {
            NotificationManagerCompat.from(this).cancel(NOTIF_ALERT)
        } catch (_: SecurityException) {
        }
    }

    /** Whether the alert notification is currently showing. A silent reason refresh must not
     *  re-post once it's gone: a dismissed notification re-posted is a brand-new notification
     *  that rings again (setOnlyAlertOnce only suppresses updates to a live one). */
    private fun alertNotificationShowing(): Boolean =
        runCatching { NotificationManagerCompat.from(this).activeNotifications.any { it.id == NOTIF_ALERT } }
            .getOrDefault(false)

    /** Persist the current outage start across service restarts (0 clears it). */
    private suspend fun persistOfflineSince(ts: Long) {
        ServiceState(applicationContext).setOfflinePendingSince(ts)
    }

    /** Persist the just-announced official alert episode so a service restart mid-episode
     *  doesn't re-ring it (see the restart reconciliation in handleState). */
    private fun persistOfficialAnnounced(state: MonitorState) {
        scope.launch {
            ServiceState(applicationContext).setOfficialAnnounced(
                state.focusToken, state.focusOblastAlertSince, state.officialReasonThreatId
            )
        }
    }

    /** Clear the persisted official-alert episode when it genuinely ends (all-clear, focus
     *  switch, alert gone) so a future alert announces fresh. */
    private fun clearOfficialAnnounced() {
        scope.launch {
            ServiceState(applicationContext).setOfficialAnnounced(null, null, null)
        }
    }

    private fun postAllClear(s: Strings.StringSet, city: String) {
        val notif = NotificationCompat.Builder(this, CHANNEL_ALLCLEAR)
            .setSmallIcon(R.drawable.ic_trident)
            .setContentTitle(String.format(s.allClearTitle, city))
            .setContentText(s.allClearText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        safeNotify(NOTIF_ALLCLEAR, notif)
    }

    private fun sirenUri(resId: Int): Uri =
        Uri.parse("android.resource://$packageName/$resId")

    private fun safeNotify(id: Int, notif: android.app.Notification) {
        try {
            NotificationManagerCompat.from(this).notify(id, notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied — silently skip.
        }
    }

    private fun openAppIntent(revealThreat: Threat? = null): PendingIntent {
        // singleTask + these flags make notification taps bring the existing activity forward
        // instead of stacking a second MainActivity on the back stack (which made Exit/back
        // appear to need multiple presses).
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (revealThreat != null) {
                putExtra(EXTRA_REVEAL_ID, revealThreat.id)
                putExtra(EXTRA_REVEAL_LAT, revealThreat.lat)
                putExtra(EXTRA_REVEAL_LON, revealThreat.lon)
            }
        }
        return PendingIntent.getActivity(
            this, if (revealThreat != null) 1 else 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** "Retry" action on the offline notifications: forces an immediate reconnect attempt. */
    private fun retryPendingIntent(): PendingIntent {
        val intent = Intent(this, AlertService::class.java).setAction(ACTION_RETRY)
        return PendingIntent.getForegroundService(
            this, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** "Ignore 30 min" action on the 20-min give-up notification: pause all auto-retries. */
    private fun ignoreRetryPendingIntent(): PendingIntent {
        val intent = Intent(this, AlertService::class.java).setAction(ACTION_IGNORE_RETRY)
        return PendingIntent.getForegroundService(
            this, 2, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Epoch millis of the next 16:20 after [from]. */
    private fun nextUpdateCheckMillis(from: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 20)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= from) add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /** Self-rescheduling daily 16:20 version check: notify once per new server versionCode. */
    private suspend fun dailyUpdateCheckLoop() {
        while (true) {
            val now = System.currentTimeMillis()
            val next = nextUpdateCheckMillis(now)
            delay(next - now)
            runDailyUpdateCheck()
        }
    }

    private fun runDailyUpdateCheck() {
        scope.launch {
            val result = UpdateManager(applicationContext).check()
            if (result is UpdateState.Available) {
                val svcState = ServiceState(applicationContext)
                val userPrefs = UserPrefs(applicationContext)
                val lastNotified = svcState.lastNotifiedUpdateCode().first()
                if (result.info.versionCode > lastNotified) {
                    svcState.setLastNotifiedUpdateCode(result.info.versionCode.toLong())
                    postUpdateNotification(result.info, userPrefs.language().first())
                }
            }
            val manifestResult = ApiMonitor.checkManifest(applicationContext)
            if (manifestResult is ManifestResult.Changed) {
                ApiMonitor.record(
                    SystemEntry(System.currentTimeMillis(), SystemEntryKind.SDK_CHANGED,
                        "SHA256: ${manifestResult.oldHash} -> ${manifestResult.newHash}")
                )
            } else if (manifestResult is ManifestResult.Failed) {
                ApiMonitor.record(
                    SystemEntry(System.currentTimeMillis(), SystemEntryKind.SDK_CHECK_FAILED,
                        manifestResult.message)
                )
            }
        }
    }

    /** Silent, dismissible "new version available" heads-up; tap opens the app and pops the update dialog. */
    private fun postUpdateNotification(info: UpdateInfo, lang: AppLanguage) {
        val s = Strings.get(lang)
        val notif = NotificationCompat.Builder(this, CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_trident)
            .setContentTitle(s.notifUpdateTitle)
            .setContentText(String.format(s.notifUpdateText, info.versionName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(updatePendingIntent())
            .build()
        safeNotify(NOTIF_UPDATE, notif)
    }

    private fun updatePendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SHOW_UPDATE, true)
        }
        return PendingIntent.getActivity(
            this, 4, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Default (EN) names; refreshed per-language by updateChannels() on start and on
        // every language change (re-creating with the same id updates name/description only —
        // importance/sound persist, so users keep their per-channel settings).
        val en = Strings.get(AppLanguage.EN)
        defineChannels(nm, en)
        val keep = setOf(
            CHANNEL_MONITOR,
            CHANNEL_ALERTS,
            CHANNEL_ALERTS_OUTER,
            CHANNEL_ALLCLEAR,
            CHANNEL_ALERTS_ALARM,
            CHANNEL_ALERTS_OUTER_ALARM,
            CHANNEL_OFFLINE,
            CHANNEL_OFFLINE_CRITICAL,
            NeutralizedTally.CHANNEL_NEUTRALIZED,
            CHANNEL_UPDATE
        )
        nm.notificationChannels
            .filter { it.id !in keep }
            .forEach { nm.deleteNotificationChannel(it.id) }
    }

    private fun defineChannels(nm: NotificationManager, s: Strings.StringSet) {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, s.notifChannelName, NotificationManager.IMPORTANCE_LOW).apply {
                description = s.notifChannelDesc
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, s.alertChannelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = s.alertChannelDesc
                enableVibration(true)
                setSound(
                    sirenUri(R.raw.air_raid_siren),
                    notificationAttributes()
                )
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS_OUTER, s.outerAlertChannelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = s.outerAlertChannelDesc
                enableVibration(true)
                setSound(
                    sirenUri(R.raw.zone_outer),
                    notificationAttributes()
                )
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALLCLEAR, s.allClearChannelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = s.allClearChannelDesc
                enableVibration(true)
                setSound(
                    sirenUri(R.raw.all_clear),
                    notificationAttributes()
                )
            }
        )
        // "Always sound" variants used only when the siren-override setting is on: they ring
        // on the alarm stream so they sound even with the phone on vibrate/silent.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS_ALARM, s.alarmAlertChannelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = s.alarmAlertChannelDesc
                enableVibration(true)
                setSound(
                    sirenUri(R.raw.air_raid_siren),
                    alarmAttributes()
                )
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS_OUTER_ALARM, s.outerAlarmAlertChannelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = s.outerAlarmAlertChannelDesc
                enableVibration(true)
                setSound(
                    sirenUri(R.raw.zone_outer),
                    alarmAttributes()
                )
            }
        )
        // Offline: high importance so it grabs attention, but silent — it's not an alert,
        // just a "we lost the live feed" heads-up (the official sirens are still the authority).
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_OFFLINE, s.offlineChannelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = s.offlineChannelDesc
                enableVibration(true)
                setSound(sirenUri(R.raw.critical_offline), notificationAttributes())
            }
        )
        // Critical offline: audible, but a friendly chime (not a siren) — the "we've been
        // dark for CRITICAL_OFFLINE_MIN minutes" reminder behind the critical-offline override.
        // When bypassSilent is enabled, use alarm stream to ring through silent mode.
        val bypassSilent = kotlinx.coroutines.runBlocking(Dispatchers.IO) { UserPrefs(applicationContext).criticalOfflineBypassSilent().first() }
        val criticalAttrs = if (bypassSilent) alarmAttributes() else notificationAttributes()
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_OFFLINE_CRITICAL, s.offlineCriticalChannelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = s.offlineCriticalChannelDesc
                enableVibration(true)
                setSound(
                    sirenUri(R.raw.critical_offline),
                    criticalAttrs
                )
            }
        )
        // Neutralized tally: informational, always silent (LOW importance) — never rings.
        nm.createNotificationChannel(
            NotificationChannel(NeutralizedTally.CHANNEL_NEUTRALIZED, s.neutralizedNotifChannelName, NotificationManager.IMPORTANCE_LOW).apply {
                description = s.neutralizedChannelDesc
            }
        )
        // Update: informative, silent (DEFAULT importance with no sound) — never rings.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATE, s.notifUpdateChannelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = s.notifUpdateChannelDesc
                setSound(null, null)
            }
        )
    }

    private fun notificationAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private fun alarmAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    /** Re-creating the channels with the same ids updates their names/descriptions (not
     *  importance/sound), so switching the app language re-localizes every channel name. */
    private fun updateMonitorChannel(s: Strings.StringSet) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        defineChannels(nm, s)
    }

    override fun onDestroy() {
        screenReceiver?.let { unregisterReceiver(it) }
        screenReceiver = null
        monitoringJob?.cancel()
        wakeLock?.let { wl -> if (wl.isHeld) wl.release() }
        wakeLock = null
        ConnectionHolder.clear()
        LocationTracker.stop()
        // The tally is promised to live only "while monitoring runs" — dropping it here also
        // prevents a stale tap/swipe from relaunching the app (and restarting sockets).
        tally.reset()
        scope.cancel()
        super.onDestroy()
    }
}

/**
 * Vibration pattern for a 0–4 strength level, applied per notification (overrides the channel
 * default). Level 0 disables vibration; higher levels mean longer and more frequent pulses.
 * Android notifications express "strength" only through the pattern — there is no amplitude.
 */
internal fun vibrationPattern(level: Int): LongArray = when (level) {
    0 -> longArrayOf(0)
    1 -> longArrayOf(0, 120, 60, 120)
    2 -> longArrayOf(0, 200, 100, 200)
    4 -> longArrayOf(0, 600, 100, 600, 100, 600)
    else -> longArrayOf(0, 400, 120, 400)
}
