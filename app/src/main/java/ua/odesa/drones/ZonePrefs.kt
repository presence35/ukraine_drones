package ua.odesa.drones

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

class ZonePrefs(private val context: Context) {

    private val languageKey = stringPreferencesKey("app_language")
    private val redZoneKmKey = intPreferencesKey("red_zone_km")
    private val yellowZoneKmKey = intPreferencesKey("yellow_zone_km")
    private val redArmedKey = booleanPreferencesKey("red_zone_armed")
    private val yellowArmedKey = booleanPreferencesKey("yellow_zone_armed")
    private val fastAlertsSoonerKey = booleanPreferencesKey("fast_alerts_sooner")
    private val officialAlertsKey = booleanPreferencesKey("official_alerts_enabled")
    private val disclaimerCollapsedKey = booleanPreferencesKey("disclaimer_collapsed")
    private val lastUpdateCheckKey = longPreferencesKey("last_update_check")

    /** Red (inner) zone radius in km — slider range 1–5. */
    fun redZoneKm(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[redZoneKmKey] ?: 3 }

    suspend fun setRedZoneKm(km: Int) {
        context.dataStore.edit { it[redZoneKmKey] = km.coerceIn(1, 5) }
    }

    /** Yellow (outer) zone radius in km — slider range 6–20. */
    fun yellowZoneKm(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[yellowZoneKmKey] ?: 8 }

    suspend fun setYellowZoneKm(km: Int) {
        context.dataStore.edit { it[yellowZoneKmKey] = km.coerceIn(6, 20) }
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

    /** Whether a threat type participates in alerts/map — default on. */
    fun threatEnabled(type: ThreatType): Flow<Boolean> {
        val key = booleanPreferencesKey("threat_enabled_${type.name}")
        return context.dataStore.data.map { prefs -> prefs[key] ?: true }
    }

    suspend fun setThreatEnabled(type: ThreatType, enabled: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("threat_enabled_${type.name}")] = enabled }
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

    fun language(): Flow<AppLanguage> =
        context.dataStore.data.map { prefs ->
            when (prefs[languageKey]) {
                "UA" -> AppLanguage.UA
                else -> AppLanguage.EN
            }
        }

    suspend fun setLanguage(lang: AppLanguage) {
        context.dataStore.edit { it[languageKey] = lang.name }
    }
}

/** The set of threat types the user has enabled — flows once per any toggle change. */
fun threatEnabledFlow(prefs: ZonePrefs): Flow<Set<ThreatType>> {
    return combine(ThreatType.values().map { prefs.threatEnabled(it) }) { enabled ->
        ThreatType.values().filterIndexed { i, _ -> enabled[i] }.toSet()
    }
}
