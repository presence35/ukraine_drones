package ua.ukrainedrones

import android.content.ComponentName
import android.content.Context
import android.appwidget.AppWidgetManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Background bridge between the live app state and the home-screen widget. Runs inside
 * [AlertService] (the already-always-on monitor), so it costs no extra battery. Recomputes a
 * [WidgetSnapshot] whenever NEPTUN, GPS, prefs or a 30s clock tick changes, persists it to the
 * widget snapshot store, then triggers a Glance re-render only when a widget is actually placed.
 *
 * The widget itself is passive — it only reads the snapshot. All decision logic stays here via
 * [computeWidgetSnapshot], which calls the shared domain functions (mirror rule: see
 * ARCHITECTURE.md).
 */
object WidgetUpdater {

    private const val UPDATE_INTERVAL_MS = 30_000L

    private val Context.widgetSnapshotStore by preferencesDataStore(name = "widget_snapshot")

    object Keys {
        val threatCount = intPreferencesKey("threat_count")
        val activeZone = stringPreferencesKey("active_zone")
        val nearestKm = intPreferencesKey("nearest_km")
        val officialAlert = booleanPreferencesKey("official_alert")
        val sourceOnline = booleanPreferencesKey("source_online")
        val sourceBackup = booleanPreferencesKey("source_backup")
        val updatedAtMs = longPreferencesKey("updated_at_ms")
        val lang = stringPreferencesKey("lang")
    }

    fun start(context: Context, scope: CoroutineScope) {
        scope.launch {
            val prefs = ZonePrefs(context)
            val clock = MutableStateFlow(System.currentTimeMillis())
            launch {
                while (true) {
                    delay(UPDATE_INTERVAL_MS)
                    clock.value = System.currentTimeMillis()
                }
            }
            combine(
                combine(
                    NeptunClient.state,
                    LocationTracker.location,
                    clock
                ) { neptun, gps, now -> Triple(neptun, gps, now) },
                combine(
                    prefs.slowRedKm(), prefs.slowYellowKm(),
                    prefs.fastRedMin(), prefs.fastYellowMin()
                ) { sr, sy, fr, fy -> ZoneParams(sr, sy, fr, fy) },
                combine(
                    prefs.followMe(), prefs.pinnedCity(), prefs.language(), threatMapFlow(prefs)
                ) { follow, pinned, lang, mapEnabled ->
                    Tail(follow, pinned, lang, mapEnabled)
                }
            ) { core, params, tail ->
                val pinnedCity = tail.pinned?.let { name -> Cities.ALL.firstOrNull { it.nameUa == name } }
                val focus = if (tail.followMe) core.second
                    else pinnedCity?.let { LatLng(it.lat, it.lon) } ?: core.second
                val attribution = focusAttribution(tail.followMe, core.second, pinnedCity)
                computeWidgetSnapshot(
                    neptun = core.first,
                    focus = focus,
                    token = attribution.token,
                    params = params,
                    mapEnabled = tail.mapEnabled,
                    now = core.third
                ) to tail.lang
            }.collect { (snapshot, lang) ->
                persist(context, snapshot, lang)
                if (hasPlacedWidgets(context)) {
                    ThreatWidget().updateAll(context)
                }
            }
        }
    }

    private fun hasPlacedWidgets(context: Context): Boolean {
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, ThreatWidgetReceiver::class.java))
        return ids.isNotEmpty()
    }

    private suspend fun persist(context: Context, snapshot: WidgetSnapshot, lang: AppLanguage) {
        context.widgetSnapshotStore.edit { prefs ->
            prefs[Keys.threatCount] = snapshot.threatCount
            prefs[Keys.activeZone] = snapshot.activeZone?.name
            prefs[Keys.nearestKm] = snapshot.nearestKm?.toInt() ?: -1
            prefs[Keys.officialAlert] = snapshot.officialAlert
            prefs[Keys.sourceOnline] = snapshot.sourceOnline
            prefs[Keys.sourceBackup] = snapshot.sourceBackup
            prefs[Keys.updatedAtMs] = snapshot.updatedAtMs
            prefs[Keys.lang] = lang.name
        }
    }

    private data class Tail(
        val followMe: Boolean,
        val pinned: String?,
        val lang: AppLanguage,
        val mapEnabled: Set<ThreatType>
    )

    suspend fun readSnapshot(context: Context): WidgetSnapshot {
        val prefs = context.widgetSnapshotStore.data.first()
        return WidgetSnapshot(
            threatCount = prefs[Keys.threatCount] ?: 0,
            activeZone = prefs[Keys.activeZone]?.let { zone ->
                ThreatZone.values().firstOrNull { it.name == zone }
            },
            nearestKm = prefs[Keys.nearestKm]?.takeIf { it >= 0 }?.toDouble(),
            officialAlert = prefs[Keys.officialAlert] ?: false,
            sourceOnline = prefs[Keys.sourceOnline] ?: false,
            sourceBackup = prefs[Keys.sourceBackup] ?: false,
            updatedAtMs = prefs[Keys.updatedAtMs] ?: 0L
        )
    }

    suspend fun readLang(context: Context): AppLanguage {
        val prefs = context.widgetSnapshotStore.data.first()
        return prefs[Keys.lang]?.let { lang -> AppLanguage.values().firstOrNull { it.name == lang } }
            ?: AppLanguage.EN
    }
}
