package ua.ukrainedrones

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

/**
 * Foreground, always-on monitoring. Owns the shared NeptunClient connection and
 * posts notifications when the Odesa oblast alarm turns on or when a tracked threat
 * enters the INNER tier (urgent siren) or the OUTER tier (warning chime).
 */
class AlertService : Service() {

    companion object {
        const val ACTION_STOP = "ua.ukrainedrones.STOP"
        const val ACTION_RETRY = "ua.ukrainedrones.RETRY"
        const val EXTRA_REVEAL_ID = "reveal_threat_id"
        const val EXTRA_REVEAL_LAT = "reveal_threat_lat"
        const val EXTRA_REVEAL_LON = "reveal_threat_lon"
        private const val CHANNEL_MONITOR = "monitor"
        private const val CHANNEL_ALERTS = "alerts_siren2"
        private const val CHANNEL_ALERTS_OUTER = "alerts_siren_outer2"
        private const val CHANNEL_ALLCLEAR = "alerts_all_clear2"
        private const val CHANNEL_ALERTS_ALARM = "alerts_siren_alarm"
        private const val CHANNEL_ALERTS_OUTER_ALARM = "alerts_siren_outer_alarm"
        private const val CHANNEL_OFFLINE = "offline"
        private const val NOTIF_MONITOR = 1
        private const val NOTIF_ALERT = 2
        private const val NOTIF_ALLCLEAR = 3
        private const val NOTIF_OFFLINE = 4
        private const val CENTRE_ALERT_GRACE_MS = 60_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AlertService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AlertService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitoringJob: Job? = null

    private var wasFocusAlertActive = false
    private var currentReasonThreatId: String? = null
    private var knownZones: Map<String, ThreatZone> = emptyMap()
    private var lastChannelLang: AppLanguage? = null
    private var emptySince: Long? = null
    private var lastMonitorTitle: String? = null
    private var lastMonitorText: String? = null
    private var lastMonitorRetry: String? = null
    private var wasConnected = true
    private var offlineNotifShown = false
    private var offlineAlertJob: Job? = null
    private val speedTracker = ThreatSpeedTracker()
    private var alertEpoch = 0

    private sealed class MonitorEvent {
        data class State(
            val focusOblastAlertActive: Boolean,
            val focusAlertSource: AlertSource?,
            val focusBannerCity: String,
            val focusRegion: String,
            val focusPinned: Boolean,
            val officialReason: String?,
            val officialReasonThreatId: String?,
            val zoneThreats: Map<String, ThreatZone>,
            val params: ZoneParams,
            val lang: AppLanguage,
            val redArmed: Boolean,
            val yellowArmed: Boolean,
            val officialAlertsEnabled: Boolean,
            val sirenOverride: Boolean,
            val connected: Boolean,
            val offlineElapsedSec: Long?
        ) : MonitorEvent()
    }

    /** Toggle + follow state used to gate zone/official alert tiering. */
    private data class AlertConfig(
        val redArmed: Boolean,
        val yellowArmed: Boolean,
        val officialAlertsEnabled: Boolean,
        val sirenOverride: Boolean,
        val followMe: Boolean
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        ConnectionLog.attach(applicationContext)
        NeptunClient.start()
        scope.launch { NeptunClient.setForceOffline(ZonePrefs(applicationContext).forceOffline().first()) }
        LocationTracker.start(this)
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RETRY) {
            NeptunClient.retryNow()
        }
        startMonitoring()
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val notif = monitorNotification(
            title = Strings.get(AppLanguage.UA).notifOngoingTitle,
            text = Strings.get(AppLanguage.UA).notifStatusZones,
            retryLabel = null
        )
        ServiceCompat.startForeground(
            this,
            NOTIF_MONITOR,
            notif,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else 0
        )
    }

    private fun startMonitoring() {
        if (monitoringJob != null) return
        val prefs = ZonePrefs(applicationContext)
        monitoringJob = scope.launch {
            val nowFlow = MutableStateFlow(System.currentTimeMillis())
            launch {
                while (true) {
                    delay(CENTRE_ALERT_GRACE_MS)
                    nowFlow.value = System.currentTimeMillis()
                }
            }
            combine(
                combine(
                    NeptunClient.state,
                    LocationTracker.location,
                    nowFlow
                ) { neptun, gps, now -> Triple(neptun, gps, now) },
                combine(
                    prefs.slowRedKm(), prefs.slowYellowKm(), prefs.fastRedMin(), prefs.fastYellowMin()
                ) { slowRed, slowYellow, fastRed, fastYellow ->
                    ZoneParams(slowRed, slowYellow, fastRed, fastYellow)
                },
                combine(
                    prefs.redZoneArmed(),
                    prefs.yellowZoneArmed(),
                    prefs.officialAlertsEnabled(),
                    prefs.sirenOverride(),
                    prefs.followMe()
                ) { flags: Array<Boolean> ->
                    AlertConfig(flags[0], flags[1], flags[2], flags[3], flags[4])
                },
                combine(
                    threatAlertFlow(prefs),
                    prefs.language(),
                    prefs.pinnedCity()
                ) { enabled, lang, pinned -> Triple(enabled, lang, pinned) }
            ) { core, params, config, tail ->
                val neptun = core.first
                val gps = core.second
                val now = core.third
                val followMe = config.followMe
                val lang = tail.second
                val pinned = tail.third?.let { name -> Cities.ALL.firstOrNull { it.nameUa == name } }
                val focus = if (followMe) gps else pinned?.let { LatLng(it.lat, it.lon) } ?: gps
                val attribution = focusAttribution(followMe, gps, pinned)
                val officialActive = attribution.token?.let { token ->
                    neptun.oblastAlerts.any { it.inOblast(token) }
                } == true
                val (officialReason, officialReasonThreatId) = if (officialActive) {
                    buildReason(
                        neptun, attribution.token, lang, focus,
                        params, now,
                        tail.first,
                        focusRegionText(lang, followMe, pinned)
                    )
                } else null to null
                MonitorEvent.State(
                    focusOblastAlertActive = officialActive,
                    focusAlertSource = attribution.token?.let { token -> neptun.alertSourceFor(token) },
                    focusBannerCity = if (lang == AppLanguage.UA) attribution.bannerCityUa else attribution.bannerCityEn,
                    focusRegion = focusRegionText(lang, followMe, pinned),
                    focusPinned = !followMe && pinned != null,
                    officialReason = officialReason,
                    officialReasonThreatId = officialReasonThreatId,
                    zoneThreats = zoneThreats(neptun, params, focus, tail.first, now),
                    params = params,
                    lang = lang,
                    redArmed = config.redArmed,
                    yellowArmed = config.yellowArmed,
                    officialAlertsEnabled = config.officialAlertsEnabled,
                    sirenOverride = config.sirenOverride,
                    connected = neptun.connected,
                    offlineElapsedSec = neptun.offlineElapsedSec
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

    /**
     * Active threats inside either tier (INNER > OUTER), keyed by id. Tiers follow
     * [zoneTier]: slow threats by distance, fast threats by time-to-arrival, both around the
     * focus point (GPS or pinned city). Advisory (NEPTUN observation) threats, disabled types
     * and out-of-reach types are skipped.
     */
    private fun zoneThreats(
        st: NeptunState,
        params: ZoneParams,
        focus: LatLng?,
        enabled: Set<ThreatType>,
        now: Long
    ): Map<String, ThreatZone> {
        if (focus == null) return emptyMap()
        val map = LinkedHashMap<String, ThreatZone>()
        for (t in st.threats.values) {
            if (t.status == "resolved" || t.status == "stale" || isExpired(t, now) || t.areaOnly) continue
            if (t.type !in enabled) continue
            if (t.advisory) continue
            speedTracker.record(t.id, t.updatedAtMillis ?: now, t.lat, t.lon)
            val estimate = speedTracker.estimate(t.id, t)
            val p = estimate?.let { predictPosition(t, it, now) }
            val lat = p?.latitude ?: t.lat
            val lon = p?.longitude ?: t.lon
            val distKm = distanceMeters(focus.lat, focus.lon, lat, lon) / 1000.0
            val speedKmh = estimate?.times(3.6)
            val zone = zoneTier(t, distKm, speedKmh, params) ?: continue
            map[t.id] = zone
        }
        return map
    }

    private fun handleState(state: MonitorEvent.State) {
        val s = Strings.get(state.lang)

        if (lastChannelLang != state.lang) {
            updateMonitorChannel(s)
            lastChannelLang = state.lang
        }

        // Offline tracking: fire a one-shot alert on drop (immediately when an official
        // alert is active at drop or during the 30s grace, else after the grace), and switch
        // the ongoing status notification to the offline wording with a Retry action.
        val offline = state.offlineElapsedSec
        if (state.connected) {
            offlineNotifShown = false
            offlineAlertJob?.cancel()
            offlineAlertJob = null
        } else if (wasConnected) {
            // Just dropped: decide whether to alert now or after the grace.
            offlineAlertJob?.cancel()
            val alertNow = state.focusOblastAlertActive
            offlineAlertJob = scope.launch {
                if (!alertNow) {
                    delay(NeptunClient.OFFLINE_GRACE_MS)
                    // During the grace an official alert may have fired — alert immediately then.
                }
                if (!offlineNotifShown && !NeptunClient.state.value.connected) {
                    postOfflineAlert(state.lang)
                    offlineNotifShown = true
                }
            }
        } else if (state.focusOblastAlertActive && !offlineNotifShown) {
            // An official alert fired while already offline — surface it immediately.
            offlineAlertJob?.cancel()
            if (!NeptunClient.state.value.connected) {
                postOfflineAlert(state.lang)
                offlineNotifShown = true
            }
        }
        wasConnected = state.connected

        notifyMonitor(
            title = if (offline != null) s.offlineStatusTitle else s.notifOngoingTitle,
            text = if (offline != null) {
                s.offlineBodyFormat
            } else if (state.focusPinned) String.format(s.notifStatusPinned, state.focusBannerCity) else s.notifStatusZones,
            retryLabel = if (offline != null) s.offlineRetryAction else null
        )

        val all = NeptunClient.state.value.threats

        /** Channel tier after arming toggles are applied; null = no sound for this object. */
        fun alertTier(id: String, spatial: ThreatZone): ThreatZone? = when (spatial) {
            ThreatZone.INNER -> if (state.redArmed) ThreatZone.INNER
            else if (state.yellowArmed) ThreatZone.OUTER else null
            ThreatZone.OUTER -> if (state.yellowArmed) ThreatZone.OUTER else null
        }

        // Fire when a threat's alert tier changes (closest/urgent tier wins).
        // Coalesce every trigger in this update into one post: re-notifying the same alert id
        // restarts the siren, so a zone entry + official edge together would double-play it.
        val alertable = state.zoneThreats.entries
            .mapNotNull { (id, spatial) -> alertTier(id, spatial)?.let { id to it } }
            .toMap()
        var posted = false
        val newEntries = alertable.entries
            .filter { (id, zone) -> knownZones[id] != zone }
            .sortedBy { it.value.ordinal }
        if (newEntries.isNotEmpty()) {
            // Post one notification for the most urgent newly-changed threat, but only mark
            // THAT threat as known — every other newly-changed threat stays "unknown" so it
            // gets its own alert on the very next tick instead of being silently absorbed.
            val (id, zone) = newEntries.first()
            val t = all[id]
            val body = t?.let { threatBody(it, state.lang) } ?: s.notifBodyRegion
            postAlert(zone, bannerFor(zone, s), body, state.sirenOverride, revealThreat = t)
            posted = true
            knownZones = knownZones + (id to zone)
        }
        // Drop ids that left zoneThreats entirely (resolved/expired/out of range) so a future
        // re-entry is treated as new; everything else keeps its value so the not-yet-fired
        // new entries are re-evaluated on the next tick.
        knownZones = knownZones.filterKeys { it in state.zoneThreats.keys }

        // Official oblast-level alert (independent of zone membership). Gated by the
        // Settings toggle — turning it off stops only official-alert notifications,
        // never the Red/Yellow zone alerts.
        val officialActive = state.officialAlertsEnabled && state.focusOblastAlertActive
        val officialBody = state.officialReason?.let { it + sourceTag(state.focusAlertSource, s) }
            ?: state.focusRegion + sourceTag(state.focusAlertSource, s)
        if (officialActive && !wasFocusAlertActive && !posted) {
            postAlert(
                null,
                String.format(s.alertBannerFormat, state.focusBannerCity),
                officialBody,
                state.sirenOverride,
                revealThreat = state.officialReasonThreatId?.let { all[it] }
            )
            currentReasonThreatId = state.officialReasonThreatId
        } else if (officialActive && wasFocusAlertActive && !posted && alertable.isEmpty() &&
            state.officialReasonThreatId != currentReasonThreatId
        ) {
            // Wait-for-reason: the official alert fired with only a region-level body; keep
            // updating the SAME NOTIF_ALERT silently as a specific threat reason arrives.
            // Mirrors the knownZones change-tracking above — a same-id re-post is guarded on
            // the threat behind the reason (not its text, which NEPTUN refreshes as
            // confirmations tick up) so the siren isn't re-triggered — and never clobbers a
            // ringing zone alert (alertable is non-empty then).
            postAlert(
                null,
                String.format(s.alertBannerFormat, state.focusBannerCity),
                officialBody,
                state.sirenOverride,
                revealThreat = state.officialReasonThreatId?.let { all[it] }
            )
            currentReasonThreatId = state.officialReasonThreatId
        }
        // All clear: the official alert that was ringing has just ended. The cheerful chime
        // fires only for the official oblast alert — zone-threat clears stay silent — and
        // never when the official-alert notifications are turned off. When no zone alert is
        // active, cancel the lingering siren notification immediately instead of waiting for
        // the 60s grace path below; if a zone alert is still ringing, leave it up.
        if (state.officialAlertsEnabled && wasFocusAlertActive && !state.focusOblastAlertActive) {
            if (alertable.isEmpty()) {
                cancelAlert()
            }
            postAllClear(s, state.focusBannerCity)
            currentReasonThreatId = null
        }
        wasFocusAlertActive = state.focusOblastAlertActive

        // Start the grace window once nothing is active; clear only after it expires.
        // The periodic nowFlow tick re-runs this every grace period, so a quiet stream still
        // clears stale alerts (threats leave the zone map on the next tick, not on demand).
        if (state.zoneThreats.isEmpty() && !state.focusOblastAlertActive) {
            val since = emptySince
            if (since == null) {
                emptySince = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - since >= CENTRE_ALERT_GRACE_MS) {
                emptySince = null
                cancelAlert()
                knownZones = emptyMap()
            }
        } else {
            emptySince = null
        }
    }

    private fun bannerFor(zone: ThreatZone, s: Strings.StringSet): String = when (zone) {
        ThreatZone.INNER -> s.redZoneAlert
        ThreatZone.OUTER -> s.yellowZoneAlert
    }

    private fun threatBody(t: Threat, lang: AppLanguage): String {
        val info = ThreatTypeCatalog.INFO.getValue(t.type)
        val label = if (lang == AppLanguage.UA) info.labelUa else info.labelEn
        val where = t.locality ?: t.district ?: t.region
        return if (where != null) "$label — $where" else label
    }

    /**
     * Reason line for the official oblast alert: the highest-priority active, non-advisory,
     * non-area-only threat whose region/district/locality sits in the focus oblast, ordered
     * by ThreatLevelModel.scoreOf. Returns the localized reason text and the threat id that
     * produced it (id null when no qualifying threat exists — then a region-level fallback
     * template is returned).
     */
    private fun buildReason(
        st: NeptunState,
        token: String?,
        lang: AppLanguage,
        focus: LatLng?,
        params: ZoneParams,
        now: Long,
        enabled: Set<ThreatType>,
        regionFallback: String
    ): Pair<String?, String?> {
        var best: Threat? = null
        var bestScore = -1.0
        for (t in st.threats.values) {
            if (t.status != "active" || t.advisory || t.areaOnly || t.type !in enabled ||
                isExpired(t, now) || !inFocusOblast(t, token)
            ) continue
            speedTracker.record(t.id, t.updatedAtMillis ?: now, t.lat, t.lon)
            val predicted = speedTracker.estimate(t.id, t)?.let { predictPosition(t, it, now) }
            val lat = predicted?.latitude ?: t.lat
            val lon = predicted?.longitude ?: t.lon
            val distKm = if (focus != null) distanceMeters(focus.lat, focus.lon, lat, lon) / 1000.0 else null
            val speed = speedTracker.estimate(t.id, t)
            val eta = if (speed != null && speed > 0.0 && distKm != null) distKm / (speed * 3.6) * 60.0 else null
            val score = if (distKm != null) {
                val (redVal, yellowVal) =
                    if (t.type in FastThreatTypes) params.fastRedMin to params.fastYellowMin
                    else params.slowRedKm to params.slowYellowKm
                ThreatLevelModel.scoreOf(t, distKm, eta, redVal, yellowVal, now)
            } else 0.0
            if (score > bestScore) {
                bestScore = score
                best = t
            }
        }
        return if (best != null) {
            val reason = translateCourseAssessment(best.explanationShort, lang) ?: threatBody(best, lang)
            reason to best.id
        } else {
            String.format(Strings.get(lang).notifReasonFormat, regionFallback) to null
        }
    }

    /** True when any of the threat's region/district/locality names sits in the focus oblast. */
    private fun inFocusOblast(t: Threat, token: String?): Boolean {
        if (token == null) return false
        return (t.region != null && inOblastText(t.region, token)) ||
            (t.district != null && inOblastText(t.district, token)) ||
            (t.locality != null && inOblastText(t.locality, token))
    }

    private fun inOblastText(text: String, token: String): Boolean =
        text.startsWith(token, ignoreCase = true) || Cities.cityOblast[text] == token

    /** Small source tag appended to the official-alert body when it came from the backup. */
    private fun sourceTag(source: AlertSource?, s: Strings.StringSet): String = when (source) {
        AlertSource.BACKUP -> s.alertSourceBackup
        AlertSource.BOTH -> s.alertSourceBoth
        else -> ""
    }

    private fun monitorNotification(title: String, text: String, retryLabel: String?) =
        NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_launcher_drone)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .apply {
                if (retryLabel != null) {
                    addAction(0, retryLabel, retryPendingIntent())
                }
            }
            .build()

    private fun notifyMonitor(title: String, text: String, retryLabel: String?) {
        if (title == lastMonitorTitle && text == lastMonitorText && retryLabel == lastMonitorRetry) return
        lastMonitorTitle = title
        lastMonitorText = text
        lastMonitorRetry = retryLabel
        safeNotify(NOTIF_MONITOR, monitorNotification(title, text, retryLabel))
    }

    private fun buildAlertNotification(
        zone: ThreatZone?,
        title: String,
        body: String,
        sirenOverride: Boolean,
        revealThreat: Threat?
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
            .setSmallIcon(R.drawable.ic_launcher_drone)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(revealThreat))
    }

    private fun postAlert(
        zone: ThreatZone?,
        title: String,
        body: String,
        sirenOverride: Boolean,
        revealThreat: Threat? = null
    ) {
        alertEpoch++
        safeNotify(
            NOTIF_ALERT,
            buildAlertNotification(zone, title, body, sirenOverride, revealThreat).build()
        )
    }

    private fun cancelAlert() {
        alertEpoch++
        try {
            NotificationManagerCompat.from(this).cancel(NOTIF_ALERT)
        } catch (_: SecurityException) {
        }
    }

    /** One-shot "connection dropped" alert on the silent offline channel. */
    private fun postOfflineAlert(lang: AppLanguage) {
        val s = Strings.get(lang)
        val body = s.offlineBodyFormat
        val notif = NotificationCompat.Builder(this, CHANNEL_OFFLINE)
            .setSmallIcon(R.drawable.ic_launcher_drone)
            .setContentTitle(s.offlineStatusTitle)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$body\n\n${s.offlineOfficialSirensLine}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .addAction(0, s.offlineRetryAction, retryPendingIntent())
            .build()
        safeNotify(NOTIF_OFFLINE, notif)
    }

    private fun postAllClear(s: Strings.StringSet, city: String) {
        alertEpoch++
        val notif = NotificationCompat.Builder(this, CHANNEL_ALLCLEAR)
            .setSmallIcon(R.drawable.ic_launcher_drone)
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
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** "Retry" action on the offline notifications: forces an immediate reconnect attempt. */
    private fun retryPendingIntent(): PendingIntent {
        val intent = Intent(this, AlertService::class.java).setAction(ACTION_RETRY)
        return PendingIntent.getService(
            this, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Default (EN) names; refreshed per-language by updateMonitorChannel() on start.
        val en = Strings.get(AppLanguage.EN)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, en.notifChannelName, NotificationManager.IMPORTANCE_LOW).apply {
                description = en.notifChannelDesc
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Air alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Air-raid sirens and urgent zone alerts"
                enableVibration(true)
                setSound(
                    sirenUri(R.raw.air_raid_siren),
                    notificationAttributes()
                )
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS_OUTER, "Region alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "OUTER zone (Регіон) warning alerts"
                enableVibration(true)
                setSound(
                    sirenUri(R.raw.zone_outer),
                    notificationAttributes()
                )
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALLCLEAR, "All clear", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Cheerful chime when the official air-raid alert ends"
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
            NotificationChannel(CHANNEL_ALERTS_ALARM, "Air alerts — always sound", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Air-raid sirens and urgent zone alerts, even on vibrate/silent"
                enableVibration(true)
                setSound(
                    sirenUri(R.raw.air_raid_siren),
                    alarmAttributes()
                )
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS_OUTER_ALARM, "Region alerts — always sound", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "OUTER zone (Регіон) warning alerts, even on vibrate/silent"
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
            NotificationChannel(CHANNEL_OFFLINE, en.offlineChannelName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = en.offlineChannelDesc
                enableVibration(true)
            }
        )
        val keep = setOf(
            CHANNEL_MONITOR,
            CHANNEL_ALERTS,
            CHANNEL_ALERTS_OUTER,
            CHANNEL_ALLCLEAR,
            CHANNEL_ALERTS_ALARM,
            CHANNEL_ALERTS_OUTER_ALARM,
            CHANNEL_OFFLINE
        )
        nm.notificationChannels
            .filter { it.id !in keep }
            .forEach { nm.deleteNotificationChannel(it.id) }
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

    /** Re-creating the channel with the same id updates its name/description (not importance). */
    private fun updateMonitorChannel(s: Strings.StringSet) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, s.notifChannelName, NotificationManager.IMPORTANCE_LOW).apply {
                description = s.notifChannelDesc
            }
        )
    }

    override fun onDestroy() {
        monitoringJob?.cancel()
        NeptunClient.stop()
        LocationTracker.stop()
        scope.cancel()
        super.onDestroy()
    }
}
