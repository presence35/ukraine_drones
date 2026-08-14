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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Foreground, always-on monitoring. Owns the shared NeptunClient connection and
 * posts notifications when the Odesa oblast alarm turns on or when a tracked threat
 * enters the INNER tier (urgent siren) or the OUTER tier (warning chime).
 */
class AlertService : Service() {

    companion object {
        const val ACTION_STOP = "ua.ukrainedrones.STOP"
        private const val CHANNEL_MONITOR = "monitor"
        private const val CHANNEL_ALERTS = "alerts_siren"
        private const val CHANNEL_ALERTS_OUTER = "alerts_siren_outer"
        private const val NOTIF_MONITOR = 1
        private const val NOTIF_ALERT = 2
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
    private var knownZones: Map<String, ThreatZone> = emptyMap()
    private var lastChannelLang: AppLanguage? = null
    private var emptySince: Long? = null
    private var lastMonitorTitle: String? = null
    private var lastMonitorText: String? = null
    private val speedTracker = ThreatSpeedTracker()

    private sealed class MonitorEvent {
        data class State(
            val focusOblastAlertActive: Boolean,
            val focusBannerCity: String,
            val focusRegion: String,
            val focusPinned: Boolean,
            val zoneThreats: Map<String, ThreatZone>,
            val lang: AppLanguage,
            val redArmed: Boolean,
            val yellowArmed: Boolean,
            val fastAlertsSooner: Boolean,
            val officialAlertsEnabled: Boolean
        ) : MonitorEvent()

        object Tick : MonitorEvent()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        NeptunClient.start()
        LocationTracker.start(this)
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startMonitoring()
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val notif = monitorNotification(
            title = Strings.get(AppLanguage.UA).notifOngoingTitle,
            text = Strings.get(AppLanguage.UA).notifStatusZones
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
            val stateEvents: Flow<MonitorEvent> = combine(
                NeptunClient.state,
                prefs.redZoneKm(),
                prefs.yellowZoneKm(),
                LocationTracker.location,
                threatEnabledFlow(prefs),
                prefs.language(),
                prefs.redZoneArmed(),
                prefs.yellowZoneArmed(),
                prefs.fastAlertsSooner(),
                prefs.officialAlertsEnabled(),
                prefs.followMe(),
                prefs.pinnedCity()
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val neptun = values[0] as NeptunState
                val gps = values[3] as LatLng?
                val followMe = values[10] as Boolean
                val pinnedName = values[11] as String?
                val pinned = pinnedName?.let { name ->
                    Cities.ALL.firstOrNull { it.nameUa == name }
                }
                val lang = values[5] as AppLanguage
                // Zones + official alert centre on the focus point: GPS while following,
                // the pinned city otherwise (mirrors the foreground UI).
                val focus = if (followMe) gps
                else pinned?.let { LatLng(it.lat, it.lon) } ?: gps
                MonitorEvent.State(
                    focusOblastAlertActive = focusOblastAlertActive(neptun, followMe, pinned),
                    focusBannerCity = focusBannerCity(lang, followMe, pinned),
                    focusRegion = focusRegionText(lang, followMe, pinned),
                    focusPinned = !followMe && pinned != null,
                    zoneThreats = zoneThreats(
                        neptun,
                        values[1] as Int,
                        values[2] as Int,
                        focus,
                        values[4] as Set<ThreatType>
                    ),
                    lang = lang,
                    redArmed = values[6] as Boolean,
                    yellowArmed = values[7] as Boolean,
                    fastAlertsSooner = values[8] as Boolean,
                    officialAlertsEnabled = values[9] as Boolean
                )
            }.distinctUntilChanged()
            // Periodic tick so the alert's grace window can expire even when the
            // telemetry stream goes quiet — no 5s polling, one cheap wakeup per grace.
            val ticks: Flow<MonitorEvent> = flow {
                while (true) {
                    delay(CENTRE_ALERT_GRACE_MS)
                    emit(MonitorEvent.Tick)
                }
            }
            merge(stateEvents, ticks).collect { event ->
                when (event) {
                    is MonitorEvent.State -> handleState(event)
                    is MonitorEvent.Tick -> handleGraceTick()
                }
            }
        }
    }

    private fun focusOblastAlertActive(st: NeptunState, followMe: Boolean, pinned: City?): Boolean {
        val token = if (followMe) "Одеськ"
        else pinned?.let { Cities.cityOblast[it.nameUa] } ?: "Одеськ"
        return st.oblastAlerts.any {
            it.oblast.contains(token, ignoreCase = true) ||
                it.name.contains(token, ignoreCase = true)
        }
    }

    private fun focusBannerCity(lang: AppLanguage, followMe: Boolean, pinned: City?): String = when {
        !followMe && pinned != null ->
            if (lang == AppLanguage.UA) pinned.nameUa else pinned.nameEn
        else -> if (lang == AppLanguage.UA) "Одеса" else "Odesa"
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
     * Active threats inside either tier (INNER > OUTER), keyed by id. Zones are radii around
     * the focus point (GPS or pinned city); disabled threat types are skipped.
     */
    private fun zoneThreats(
        st: NeptunState,
        redKm: Int,
        yellowKm: Int,
        focus: LatLng?,
        enabled: Set<ThreatType>
    ): Map<String, ThreatZone> {
        if (focus == null) return emptyMap()
        val now = System.currentTimeMillis()
        val zones = RadialZones(redKm, yellowKm)
        val map = LinkedHashMap<String, ThreatZone>()
        for (t in st.threats.values) {
            if (t.status == "resolved" || t.status == "stale" || isExpired(t, now) || t.areaOnly) continue
            if (t.type !in enabled) continue
            speedTracker.record(t.id, t.updatedAtMillis ?: now, t.lat, t.lon)
            val p = speedTracker.estimate(t.id, t)?.let { predictPosition(t, it, now) }
            val lat = p?.latitude ?: t.lat
            val lon = p?.longitude ?: t.lon
            val zone = radialZone(
                distanceMeters(focus.lat, focus.lon, lat, lon) / 1000.0,
                zones
            ) ?: continue
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
        notifyMonitor(
            title = s.notifOngoingTitle,
            text = if (state.focusPinned) s.notifStatusPinned else s.notifStatusZones
        )

        val all = NeptunClient.state.value.threats

        /** Channel tier after arming toggles are applied; null = no sound for this object. */
        fun alertTier(id: String, spatial: ThreatZone): ThreatZone? = when (
            all[id]?.let { effectiveZone(it, spatial, state.fastAlertsSooner) } ?: spatial
        ) {
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
        val newZone = alertable.entries
            .filter { (id, zone) -> knownZones[id] != zone }
            .minWithOrNull(compareBy { it.value.ordinal })
        if (newZone != null) {
            val (id, zone) = newZone
            val t = all[id]
            val body = t?.let { threatBody(it, state.lang) } ?: s.notifBodyRegion
            postAlert(zone, bannerFor(zone, s), body)
            posted = true
        }
        knownZones = alertable

        // Official oblast-level alert (independent of zone membership). Gated by the
        // Settings toggle — turning it off stops only official-alert notifications,
        // never the Red/Yellow zone alerts.
        if (state.officialAlertsEnabled && state.focusOblastAlertActive && !wasFocusAlertActive && !posted) {
            postAlert(
                null,
                String.format(s.alertBannerFormat, state.focusBannerCity),
                state.focusRegion
            )
        }
        wasFocusAlertActive = state.focusOblastAlertActive

        // Start the grace window once nothing is active; clear only after it expires.
        if (state.zoneThreats.isEmpty() && !state.focusOblastAlertActive) {
            if (emptySince == null) emptySince = System.currentTimeMillis()
        } else {
            emptySince = null
        }
    }

    private fun bannerFor(zone: ThreatZone, s: Strings.StringSet): String = when (zone) {
        ThreatZone.INNER -> s.redZoneAlert
        ThreatZone.OUTER -> s.yellowZoneAlert
    }

    /** After the grace window with nothing active, clear the alert. */
    private fun handleGraceTick() {
        val since = emptySince ?: return
        if (System.currentTimeMillis() - since >= CENTRE_ALERT_GRACE_MS) {
            emptySince = null
            cancelAlert()
            knownZones = emptyMap()
        }
    }

    private fun threatBody(t: Threat, lang: AppLanguage): String {
        val info = ThreatTypeCatalog.INFO.getValue(t.type)
        val label = if (lang == AppLanguage.UA) info.labelUa else info.labelEn
        val where = t.locality ?: t.district ?: t.region
        return if (where != null) "$label — $where" else label
    }

    private fun monitorNotification(title: String, text: String) =
        NotificationCompat.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_launcher_drone)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun notifyMonitor(title: String, text: String) {
        if (title == lastMonitorTitle && text == lastMonitorText) return
        lastMonitorTitle = title
        lastMonitorText = text
        safeNotify(NOTIF_MONITOR, monitorNotification(title, text))
    }

    private fun postAlert(zone: ThreatZone?, title: String, body: String) {
        // OUTER uses its own channel so the two tiers ring differently; everything else
        // (INNER, official oblast alert) gets the urgent siren channel.
        val channel = if (zone == ThreatZone.OUTER) CHANNEL_ALERTS_OUTER else CHANNEL_ALERTS
        val notif = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_launcher_drone)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        safeNotify(NOTIF_ALERT, notif)
    }

    private fun cancelAlert() {
        try {
            NotificationManagerCompat.from(this).cancel(NOTIF_ALERT)
        } catch (_: SecurityException) {
        }
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

    private fun openAppIntent(): PendingIntent {
        // singleTask + these flags make notification taps bring the existing activity forward
        // instead of stacking a second MainActivity on the back stack (which made Exit/back
        // appear to need multiple presses).
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
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
                description = "Odesa air raid and INNER zone threat alerts"
                enableVibration(true)
                setSound(
                    sirenUri(R.raw.air_raid_siren),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS_OUTER, "Region alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "OUTER zone (Регіон) warning alerts"
                enableVibration(true)
                setSound(
                    sirenUri(R.raw.zone_outer),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        )
        val keep = setOf(CHANNEL_MONITOR, CHANNEL_ALERTS, CHANNEL_ALERTS_OUTER)
        nm.notificationChannels
            .filter { it.id !in keep }
            .forEach { nm.deleteNotificationChannel(it.id) }
    }

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
