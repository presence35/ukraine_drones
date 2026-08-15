package ua.ukrainedrones

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "zone_prefs")

enum class AppLanguage { UA, EN }

/** Density of the threat detail popup. */
enum class ThreatCardSize { SMALL, MEDIUM, LARGE }

class ZonePrefs(private val context: Context) {

    private val languageKey = stringPreferencesKey("app_language")
    private val languageChosenKey = booleanPreferencesKey("language_chosen")
    private val redZoneKmKey = intPreferencesKey("red_zone_km")
    private val yellowZoneKmKey = intPreferencesKey("yellow_zone_km")
    private val redArmedKey = booleanPreferencesKey("red_zone_armed")
    private val yellowArmedKey = booleanPreferencesKey("yellow_zone_armed")
    private val fastAlertsSoonerKey = booleanPreferencesKey("fast_alerts_sooner")
    private val officialAlertsKey = booleanPreferencesKey("official_alerts_enabled")
    private val sirenOverrideKey = booleanPreferencesKey("siren_override")
    private val disclaimerCollapsedKey = booleanPreferencesKey("disclaimer_collapsed")
    private val lastUpdateCheckKey = longPreferencesKey("last_update_check")
    private val followMeKey = booleanPreferencesKey("follow_me")
    private val pinnedCityKey = stringPreferencesKey("pinned_city")
    private val forceOfflineKey = booleanPreferencesKey("temp_force_offline")
    private val settingsHintRemainingKey = intPreferencesKey("settings_hint_remaining")
    private val threatToggleHintRemainingKey = intPreferencesKey("threat_toggle_hint_remaining")
    private val threatCardSizeKey = stringPreferencesKey("threat_card_size")
    private val showMapScaleKey = booleanPreferencesKey("show_map_scale")
    private val legacyCacheCleanedKey = booleanPreferencesKey("legacy_osmdroid_cleaned")

    /** Red (inner) zone radius in km — slider range 1–20, default 10. */
    fun redZoneKm(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[redZoneKmKey] ?: 10 }

    suspend fun setRedZoneKm(km: Int) {
        context.dataStore.edit { it[redZoneKmKey] = km.coerceIn(1, 20) }
    }

    /** Yellow (outer) zone radius in km — slider range 21–50, default min (21). */
    fun yellowZoneKm(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[yellowZoneKmKey] ?: 21 }

    suspend fun setYellowZoneKm(km: Int) {
        context.dataStore.edit { it[yellowZoneKmKey] = km.coerceIn(21, 50) }
    }

    /** Whether the red zone can fire urgent siren alerts. */
    fun redZoneArmed(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[redArmedKey] ?: true }

    suspend fun setRedZoneArmed(armed: Boolean) {
        context.dataStore.edit { it[redArmedKey] = armed }
    }

    /** Whether the yellow zone can fire warning alerts. */
    fun yellowZoneArmed(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[yellowArmedKey] ?: true }

    suspend fun setYellowZoneArmed(armed: Boolean) {
        context.dataStore.edit { it[yellowArmedKey] = armed }
    }

    /** Fast objects (missiles, KAB, MiG-31K) sound the siren at any zone entry. */
    fun fastAlertsSooner(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[fastAlertsSoonerKey] ?: true }

    suspend fun setFastAlertsSooner(sooner: Boolean) {
        context.dataStore.edit { it[fastAlertsSoonerKey] = sooner }
    }

    /** Whether the app notifies on the official oblast air-raid alert. Zone alerts are unaffected. */
    fun officialAlertsEnabled(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[officialAlertsKey] ?: true }

    suspend fun setOfficialAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[officialAlertsKey] = enabled }
    }

    /** Whether siren alerts ring even when the phone is on vibrate/silent. Default off. */
    fun sirenOverride(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[sirenOverrideKey] ?: false }

    suspend fun setSirenOverride(override: Boolean) {
        context.dataStore.edit { it[sirenOverrideKey] = override }
    }

    /** Whether a threat type is shown on the map — default on. */
    fun threatMapVisible(type: ThreatType): Flow<Boolean> {
        val key = booleanPreferencesKey("threat_map_${type.name}")
        return context.dataStore.data.map { prefs -> prefs[key] ?: true }
    }

    suspend fun setThreatMapVisible(type: ThreatType, visible: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("threat_map_${type.name}")] = visible }
    }

    /** Whether a threat type fires alerts — default on. */
    fun threatAlertsEnabled(type: ThreatType): Flow<Boolean> {
        val key = booleanPreferencesKey("threat_alert_${type.name}")
        return context.dataStore.data.map { prefs -> prefs[key] ?: true }
    }

    suspend fun setThreatAlertsEnabled(type: ThreatType, enabled: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("threat_alert_${type.name}")] = enabled }
    }

    /** Whether the "follow official guidelines" disclaimer card is collapsed. */
    fun disclaimerCollapsed(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[disclaimerCollapsedKey] ?: false }

    suspend fun setDisclaimerCollapsed(collapsed: Boolean) {
        context.dataStore.edit { it[disclaimerCollapsedKey] = collapsed }
    }

    /** Epoch millis of the last completed update check (auto or manual). */
    fun lastUpdateCheck(): Flow<Long> =
        context.dataStore.data.map { prefs -> prefs[lastUpdateCheckKey] ?: 0L }

    suspend fun setLastUpdateCheck(ts: Long) {
        context.dataStore.edit { it[lastUpdateCheckKey] = ts }
    }

    /** Whether the map/zones follow the GPS position (false = pinned to a city). */
    fun followMe(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[followMeKey] ?: true }

    suspend fun setFollowMe(follow: Boolean) {
        context.dataStore.edit { it[followMeKey] = follow }
    }

    /** Pinned city by nameUa, or null when not pinned. */
    fun pinnedCity(): Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[pinnedCityKey] }

    suspend fun setPinnedCity(nameUa: String?) {
        context.dataStore.edit {
            if (nameUa == null) it.remove(pinnedCityKey) else it[pinnedCityKey] = nameUa
        }
    }

    /** TEMP testing toggle: force the app to behave as if NEPTUN is offline (exercises the backup path). */
    fun forceOffline(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[forceOfflineKey] ?: false }

    suspend fun setForceOffline(force: Boolean) {
        context.dataStore.edit { it[forceOfflineKey] = force }
    }

    fun language(): Flow<AppLanguage> =
        context.dataStore.data.map { prefs ->
            when (prefs[languageKey]) {
                "EN" -> AppLanguage.EN
                else -> AppLanguage.UA
            }
        }

    suspend fun setLanguage(lang: AppLanguage) {
        context.dataStore.edit {
            it[languageKey] = lang.name
        }
    }

    /** Whether the user has ever picked a language (via the first-run popup or Settings). */
    fun languageChosen(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[languageChosenKey] ?: false }

    suspend fun setLanguageChosen(chosen: Boolean) {
        context.dataStore.edit { it[languageChosenKey] = chosen }
    }

    /** How many more launches should draw the pulsing ring around the Settings heart. */
    fun settingsHintRemaining(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[settingsHintRemainingKey] ?: 10 }

    suspend fun setSettingsHintRemaining(remaining: Int) {
        context.dataStore.edit { it[settingsHintRemainingKey] = remaining.coerceAtLeast(0) }
    }

    /** How many more Map/Alerts toggles should show the one-time "how it works" hint toast. */
    fun threatToggleHintRemaining(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[threatToggleHintRemainingKey] ?: 3 }

    suspend fun setThreatToggleHintRemaining(remaining: Int) {
        context.dataStore.edit { it[threatToggleHintRemainingKey] = remaining.coerceAtLeast(0) }
    }

    /** Density of the threat detail popup. */
    fun threatCardSize(): Flow<ThreatCardSize> =
        context.dataStore.data.map { prefs ->
            prefs[threatCardSizeKey]?.let { stored ->
                ThreatCardSize.values().firstOrNull { it.name == stored }
            } ?: ThreatCardSize.LARGE
        }

    suspend fun setThreatCardSize(size: ThreatCardSize) {
        context.dataStore.edit { it[threatCardSizeKey] = size.name }
    }

    /** Whether the map's bottom-left scale bar is shown — default on. */
    fun showMapScale(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[showMapScaleKey] ?: true }

    suspend fun setShowMapScale(show: Boolean) {
        context.dataStore.edit { it[showMapScaleKey] = show }
    }

    /** Whether the pre-migration osmdroid tile caches have already been deleted. */
    fun legacyCacheCleaned(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[legacyCacheCleanedKey] ?: false }

    suspend fun setLegacyCacheCleaned(cleaned: Boolean) {
        context.dataStore.edit { it[legacyCacheCleanedKey] = cleaned }
    }
}

/** The set of threat types shown on the map — flows once per any map-visibility change. */
fun threatMapFlow(prefs: ZonePrefs): Flow<Set<ThreatType>> {
    return combine(ThreatType.values().map { prefs.threatMapVisible(it) }) { visible ->
        ThreatType.values().filterIndexed { i, _ -> visible[i] }.toSet()
    }
}

/** The set of threat types that fire alerts — flows once per any alert-toggle change. */
fun threatAlertFlow(prefs: ZonePrefs): Flow<Set<ThreatType>> {
    return combine(ThreatType.values().map { prefs.threatAlertsEnabled(it) }) { enabled ->
        ThreatType.values().filterIndexed { i, _ -> enabled[i] }.toSet()
    }
}
