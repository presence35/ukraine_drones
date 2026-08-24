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
enum class ThreatCardSize { SMALL, LARGE }

/** Which visual style is used for threat icons everywhere (map, strip, popups, toggles). */
enum class ThreatIconSet { PHOTO, ARMY, COMIC, RUSSIAN }

class ZonePrefs(private val context: Context) {

    private val languageKey = stringPreferencesKey("app_language")
    private val languageChosenKey = booleanPreferencesKey("language_chosen")
    private val slowRedKmKey = intPreferencesKey("slow_red_km")
    private val slowYellowKmKey = intPreferencesKey("slow_yellow_km")
    private val fastRedMinKey = intPreferencesKey("fast_red_min")
    private val fastYellowMinKey = intPreferencesKey("fast_yellow_min")
    private val slowRedArmedKey = booleanPreferencesKey("slow_red_armed")
    private val slowYellowArmedKey = booleanPreferencesKey("slow_yellow_armed")
    private val fastRedArmedKey = booleanPreferencesKey("fast_red_armed")
    private val fastYellowArmedKey = booleanPreferencesKey("fast_yellow_armed")
    private val officialAlertsKey = booleanPreferencesKey("official_alerts_enabled")
    private val sirenOverrideKey = booleanPreferencesKey("siren_override")
    private val disclaimerCollapsedKey = booleanPreferencesKey("disclaimer_collapsed")
    private val disclaimerReadCountKey = intPreferencesKey("disclaimer_read_count")
    private val lastUpdateCheckKey = longPreferencesKey("last_update_check")
    private val lastNotifiedUpdateCodeKey = longPreferencesKey("last_notified_update_code")
    private val followMeKey = booleanPreferencesKey("follow_me")
    private val pinnedCityKey = stringPreferencesKey("pinned_city")
    private val forceOfflineKey = booleanPreferencesKey("temp_force_offline")
    private val settingsHintRemainingKey = intPreferencesKey("settings_hint_remaining")
    private val threatToggleHintRemainingKey = intPreferencesKey("threat_toggle_hint_remaining")
    private val shelterTipRemainingKey = intPreferencesKey("shelter_tip_remaining")
    private val threatCardSizeKey = stringPreferencesKey("threat_card_size")
    private val threatIconSetKey = stringPreferencesKey("threat_icon_set")
    private val showMapScaleKey = booleanPreferencesKey("show_map_scale")
    private val deathAnimationEnabledKey = booleanPreferencesKey("death_animation_enabled")
    private val followBulletKey = booleanPreferencesKey("follow_bullet")
    private val neutralizedTallyEnabledKey = booleanPreferencesKey("neutralized_tally_enabled")
    private val neutralizedTallyAllUkraineKey = booleanPreferencesKey("neutralized_tally_all_ukraine")
    private val legacyCacheCleanedKey = booleanPreferencesKey("legacy_osmdroid_cleaned")
    private val fastGroupCollapsedKey = booleanPreferencesKey("fast_group_collapsed")
    private val slowGroupCollapsedKey = booleanPreferencesKey("slow_group_collapsed")
    private val connLogKey = stringPreferencesKey("conn_log")
    private val connLogPendingSinceKey = longPreferencesKey("conn_log_pending_since")
    private val connLogPendingStatusKey = stringPreferencesKey("conn_log_pending_status")
    private val offlinePendingSinceKey = longPreferencesKey("offline_pending_since")
    private val batteryOnboardShownKey = booleanPreferencesKey("battery_onboard_shown")
    private val permissionPromptDeferredKey = booleanPreferencesKey("permission_prompt_deferred")
    private val debugLogKey = stringPreferencesKey("debug_log")
    private val nightEnabledKey = booleanPreferencesKey("night_enabled")
    private val nightStartMinKey = intPreferencesKey("night_start_min")
    private val nightEndMinKey = intPreferencesKey("night_end_min")
    private val nightUseCustomZonesKey = booleanPreferencesKey("night_use_custom_zones")
    private val nightSlowRedKmKey = intPreferencesKey("night_slow_red_km")
    private val nightSlowYellowKmKey = intPreferencesKey("night_slow_yellow_km")
    private val nightFastRedMinKey = intPreferencesKey("night_fast_red_min")
    private val nightFastYellowMinKey = intPreferencesKey("night_fast_yellow_min")
    private val nightSlowRedArmedKey = booleanPreferencesKey("night_slow_red_armed")
    private val nightSlowYellowArmedKey = booleanPreferencesKey("night_slow_yellow_armed")
    private val nightFastRedArmedKey = booleanPreferencesKey("night_fast_red_armed")
    private val nightFastYellowArmedKey = booleanPreferencesKey("night_fast_yellow_armed")
    private val nightZoneSirenOverrideKey = booleanPreferencesKey("night_zone_siren_override")
    private val nightOfficialSirenOverrideKey = booleanPreferencesKey("night_official_siren_override")
    private val sheltersEnabledKey = booleanPreferencesKey("shelters_enabled")
    private val sheltersWithKidsEnabledKey = booleanPreferencesKey("shelters_with_kids_enabled")
    private val periodicGpsKey = booleanPreferencesKey("periodic_gps_enabled")
    private val calmMessagesEnabledKey = booleanPreferencesKey("calm_messages_enabled")
    private val officialAlertCityScopeKey = booleanPreferencesKey("official_alert_city_scope")

    /** Red (inner) slow-threat distance threshold in km — slider range 1–20, default 20. */
    fun slowRedKm(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[slowRedKmKey] ?: 20 }

    suspend fun setSlowRedKm(km: Int) {
        context.dataStore.edit { prefs ->
            prefs[slowRedKmKey] = km.coerceIn(1, 20)
            // Yellow tracks red: it can never dip below red+2, so a red raise pushes it up.
            val red = prefs[slowRedKmKey] ?: 20
            val yellow = prefs[slowYellowKmKey] ?: 50
            prefs[slowYellowKmKey] = yellow.coerceIn(red + 2, 50)
        }
    }

    /** Yellow (outer) slow-threat distance threshold in km — range red+2–50, default 50. */
    fun slowYellowKm(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[slowYellowKmKey] ?: 50 }

    suspend fun setSlowYellowKm(km: Int) {
        context.dataStore.edit { prefs ->
            val red = prefs[slowRedKmKey] ?: 20
            prefs[slowYellowKmKey] = km.coerceIn(red + 2, 50)
        }
    }

    /** Red (inner) fast-threat time-to-arrival threshold in minutes — range 1–5, default 5. */
    fun fastRedMin(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[fastRedMinKey] ?: 5 }

    suspend fun setFastRedMin(min: Int) {
        context.dataStore.edit { prefs ->
            prefs[fastRedMinKey] = min.coerceIn(1, 5)
            val red = prefs[fastRedMinKey] ?: 5
            val yellow = prefs[fastYellowMinKey] ?: 20
            prefs[fastYellowMinKey] = yellow.coerceIn(red + 2, 20)
        }
    }

    /** Yellow (outer) fast-threat time-to-arrival threshold in minutes — range red+2–20, default 20. */
    fun fastYellowMin(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[fastYellowMinKey] ?: 20 }

    suspend fun setFastYellowMin(min: Int) {
        context.dataStore.edit { prefs ->
            val red = prefs[fastRedMinKey] ?: 5
            prefs[fastYellowMinKey] = min.coerceIn(red + 2, 20)
        }
    }

    /** Whether the slow red zone can fire urgent siren alerts. */
    fun slowRedZoneArmed(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[slowRedArmedKey] ?: true }

    suspend fun setSlowRedZoneArmed(armed: Boolean) {
        context.dataStore.edit { it[slowRedArmedKey] = armed }
    }

    /** Whether the slow yellow zone can fire warning alerts. */
    fun slowYellowZoneArmed(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[slowYellowArmedKey] ?: true }

    suspend fun setSlowYellowZoneArmed(armed: Boolean) {
        context.dataStore.edit { it[slowYellowArmedKey] = armed }
    }

    /** Whether the fast red zone can fire urgent siren alerts. */
    fun fastRedZoneArmed(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[fastRedArmedKey] ?: true }

    suspend fun setFastRedZoneArmed(armed: Boolean) {
        context.dataStore.edit { it[fastRedArmedKey] = armed }
    }

    /** Whether the fast yellow zone can fire warning alerts. */
    fun fastYellowZoneArmed(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[fastYellowArmedKey] ?: true }

    suspend fun setFastYellowZoneArmed(armed: Boolean) {
        context.dataStore.edit { it[fastYellowArmedKey] = armed }
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

    /** Whether the advanced-feature explainer for [id] has been shown once. */
    fun explainerSeen(id: String): Flow<Boolean> {
        val key = booleanPreferencesKey("explainer_seen_$id")
        return context.dataStore.data.map { prefs -> prefs[key] ?: false }
    }

    suspend fun setExplainerSeen(id: String, seen: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey("explainer_seen_$id")] = seen }
    }

    /** Re-arm every first-use hint: reset the toast counters and re-show the explainers. */
    suspend fun resetAllTips() {
        context.dataStore.edit { prefs ->
            prefs[settingsHintRemainingKey] = 10
            prefs[threatToggleHintRemainingKey] = 3
            prefs[shelterTipRemainingKey] = 0
            listOf("followMe", "nightMode", "officialAlerts", "sirenOverride", "threatToggles", "cardSize")
                .forEach { id -> prefs.remove(booleanPreferencesKey("explainer_seen_$id")) }
        }
    }

    /** Whether the "follow official guidelines" disclaimer card is collapsed. */
    fun disclaimerCollapsed(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[disclaimerCollapsedKey] ?: false }

    suspend fun setDisclaimerCollapsed(collapsed: Boolean) {
        context.dataStore.edit { it[disclaimerCollapsedKey] = collapsed }
    }

    /** How many times the disclaimers card has been shown on Settings open (auto-expands until 3). */
    fun disclaimerReadCount(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[disclaimerReadCountKey] ?: 0 }

    suspend fun setDisclaimerReadCount(count: Int) {
        context.dataStore.edit { it[disclaimerReadCountKey] = count }
    }

    /** Epoch millis of the last completed update check (auto or manual). */
    fun lastUpdateCheck(): Flow<Long> =
        context.dataStore.data.map { prefs -> prefs[lastUpdateCheckKey] ?: 0L }

    suspend fun setLastUpdateCheck(ts: Long) {
        context.dataStore.edit { it[lastUpdateCheckKey] = ts }
    }

    /** Server versionCode last advertised by the daily update notification (0 = never). */
    fun lastNotifiedUpdateCode(): Flow<Long> =
        context.dataStore.data.map { prefs -> prefs[lastNotifiedUpdateCodeKey] ?: 0L }

    suspend fun setLastNotifiedUpdateCode(code: Long) {
        context.dataStore.edit { it[lastNotifiedUpdateCodeKey] = code }
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

    /** TEMP testing toggle: force the app to behave as if NEPTUN is offline. */
    fun forceOffline(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[forceOfflineKey] ?: false }

    suspend fun setForceOffline(force: Boolean) {
        context.dataStore.edit { it[forceOfflineKey] = force }
    }

    fun language(): Flow<AppLanguage> =
        context.dataStore.data.map { prefs ->
            when (prefs[languageKey]) {
                "EN" -> AppLanguage.EN
                "UA" -> AppLanguage.UA
                else -> if (java.util.Locale.getDefault().language == "uk") AppLanguage.UA else AppLanguage.EN
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

    /**
     * Shelter-button onboarding stage (0–6): 0–1 show the "tap" tip, 2–3 show nothing (a
     * break), 4–5 show the "long press" tip, 6+ show nothing ever. Stored as a count that only
     * advances (capped at 6) so the tip sequence plays once.
     */
    fun shelterTipStage(): Flow<Int> =
        context.dataStore.data.map { prefs -> (prefs[shelterTipRemainingKey] ?: 0).coerceIn(0, 6) }

    suspend fun setShelterTipStage(stage: Int) {
        context.dataStore.edit { it[shelterTipRemainingKey] = stage.coerceIn(0, 6) }
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

    /** Icon style used for threats everywhere — classic vector set or photo set. */
    fun threatIconSet(): Flow<ThreatIconSet> =
        context.dataStore.data.map { prefs ->
            prefs[threatIconSetKey]?.let { stored ->
                ThreatIconSet.values().firstOrNull { it.name == stored }
            } ?: ThreatIconSet.PHOTO
        }

    suspend fun setThreatIconSet(set: ThreatIconSet) {
        context.dataStore.edit { it[threatIconSetKey] = set.name }
    }

    /** Whether the map's bottom-right scale bar is shown — default on. */
    fun showMapScale(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[showMapScaleKey] ?: true }

    suspend fun setShowMapScale(show: Boolean) {
        context.dataStore.edit { it[showMapScaleKey] = show }
    }

    /** Whether the "Go to shelter" button shows on the map — default on. */
    fun sheltersEnabled(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[sheltersEnabledKey] ?: true }

    suspend fun setSheltersEnabled(enabled: Boolean) {
        context.dataStore.edit { it[sheltersEnabledKey] = enabled }
    }

    /** Whether walk times account for kids (slower pace) — default on. */
    fun sheltersWithKidsEnabled(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[sheltersWithKidsEnabledKey] ?: true }

    suspend fun setSheltersWithKidsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[sheltersWithKidsEnabledKey] = enabled }
    }

    /** Whether the app periodically snaps a one-shot GPS fix (every 15 min) to prevent cell drift. Default true. */
    fun periodicGps(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[periodicGpsKey] ?: true }

    suspend fun setPeriodicGps(enabled: Boolean) {
        context.dataStore.edit { it[periodicGpsKey] = enabled }
    }

    /** Whether the footer shows rotating calm messages when no threats are around — default on. */
    fun calmMessagesEnabled(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[calmMessagesEnabledKey] ?: true }

    suspend fun setCalmMessagesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[calmMessagesEnabledKey] = enabled }
    }

    /** Official-alert scope: false = whole oblast, true = only when the focus city is covered. */
    fun officialAlertCityScope(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[officialAlertCityScopeKey] ?: false }

    suspend fun setOfficialAlertCityScope(enabled: Boolean) {
        context.dataStore.edit { it[officialAlertCityScopeKey] = enabled }
    }

    /** Whether the projectile-and-explosion "neutralized" flourish plays — default on. */
    fun deathAnimationEnabled(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[deathAnimationEnabledKey] ?: true }

    suspend fun setDeathAnimationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[deathAnimationEnabledKey] = enabled }
    }

    /** Whether the camera follows the death bullet (then returns to where the user was) — default on. */
    fun followBullet(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[followBulletKey] ?: true }

    suspend fun setFollowBullet(enabled: Boolean) {
        context.dataStore.edit { it[followBulletKey] = enabled }
    }

    /** Whether the "resolved threats" tally notification counts/show — default on. */
    fun neutralizedTallyEnabled(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[neutralizedTallyEnabledKey] ?: true }

    suspend fun setNeutralizedTallyEnabled(enabled: Boolean) {
        context.dataStore.edit { it[neutralizedTallyEnabledKey] = enabled }
    }

    /** Whether the resolved-threats tally also counts threats anywhere in Ukraine (default: focus oblast only). */
    fun neutralizedTallyAllUkraine(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[neutralizedTallyAllUkraineKey] ?: false }

    suspend fun setNeutralizedTallyAllUkraine(enabled: Boolean) {
        context.dataStore.edit { it[neutralizedTallyAllUkraineKey] = enabled }
    }

    /** Whether the pre-migration osmdroid tile caches have already been deleted. */
    fun legacyCacheCleaned(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[legacyCacheCleanedKey] ?: false }

    suspend fun setLegacyCacheCleaned(cleaned: Boolean) {
        context.dataStore.edit { it[legacyCacheCleanedKey] = cleaned }
    }

    /** Whether the Fast threat group in Settings is collapsed — default expanded. */
    fun fastGroupCollapsed(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[fastGroupCollapsedKey] ?: false }

    suspend fun setFastGroupCollapsed(collapsed: Boolean) {
        context.dataStore.edit { it[fastGroupCollapsedKey] = collapsed }
    }

    /** Whether the Slow threat group in Settings is collapsed — default expanded. */
    fun slowGroupCollapsed(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[slowGroupCollapsedKey] ?: false }

    suspend fun setSlowGroupCollapsed(collapsed: Boolean) {
        context.dataStore.edit { it[slowGroupCollapsedKey] = collapsed }
    }

    /** Serialized connection-status log ("at|status|dur" lines), for the System-status popup. */
    fun connLog(): Flow<String> =
        context.dataStore.data.map { prefs -> prefs[connLogKey] ?: "" }

    suspend fun setConnLog(serialized: String) {
        context.dataStore.edit { it[connLogKey] = serialized }
    }

    /** Epoch millis when the current offline episode started (0 = none), across restarts. */
    fun connLogPendingSince(): Flow<Long> =
        context.dataStore.data.map { prefs -> prefs[connLogPendingSinceKey] ?: 0L }

    suspend fun setConnLogPendingSince(ts: Long) {
        context.dataStore.edit { it[connLogPendingSinceKey] = ts }
    }

    /** Name of the status currently in progress ("OFFLINE"), or empty when none. */
    fun connLogPendingStatus(): Flow<String> =
        context.dataStore.data.map { prefs -> prefs[connLogPendingStatusKey] ?: "" }

    suspend fun setConnLogPendingStatus(status: String) {
        context.dataStore.edit { it[connLogPendingStatusKey] = status }
    }

    /** Epoch millis when the current NEPTUN outage started (0 = none), across service restarts. */
    fun offlinePendingSince(): Flow<Long> =
        context.dataStore.data.map { prefs -> prefs[offlinePendingSinceKey] ?: 0L }

    suspend fun setOfflinePendingSince(ts: Long) {
        context.dataStore.edit { it[offlinePendingSinceKey] = ts }
    }

    /** Whether the first-run battery-exemption prompt has been shown once (dismissed or allowed). */
    fun batteryOnboardShown(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[batteryOnboardShownKey] ?: false }

    suspend fun setBatteryOnboardShown(shown: Boolean) {
        context.dataStore.edit { it[batteryOnboardShownKey] = shown }
    }

    /** Whether the current launch skipped the location/notification prompts (tapped "Later").
     *  Reset on each cold start so the request re-arms for the next session. */
    fun permissionPromptDeferred(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[permissionPromptDeferredKey] ?: false }

    suspend fun setPermissionPromptDeferred(deferred: Boolean) {
        context.dataStore.edit { it[permissionPromptDeferredKey] = deferred }
    }

    /** Serialized debug decision log ("at|kind|reason|..." lines), for the Debug log screen. */
    fun debugLog(): Flow<String> =
        context.dataStore.data.map { prefs -> prefs[debugLogKey] ?: "" }

    suspend fun setDebugLog(serialized: String) {
        context.dataStore.edit { it[debugLogKey] = serialized }
    }

    /** Whether the night-mode window is enabled. */
    fun nightEnabled(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[nightEnabledKey] ?: true }

    suspend fun setNightEnabled(enabled: Boolean) {
        context.dataStore.edit { it[nightEnabledKey] = enabled }
    }

    /** Night window start, minute since midnight (default 22:00 = 1320). */
    fun nightStartMin(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[nightStartMinKey] ?: 22 * 60 }

    suspend fun setNightStartMin(min: Int) {
        context.dataStore.edit { it[nightStartMinKey] = min.coerceIn(0, 1439) }
    }

    /** Night window end, minute since midnight (default 07:00 = 420). */
    fun nightEndMin(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[nightEndMinKey] ?: 7 * 60 }

    suspend fun setNightEndMin(min: Int) {
        context.dataStore.edit { it[nightEndMinKey] = min.coerceIn(0, 1439) }
    }

    /** Whether the night window uses its own zone thresholds/armed bells (false = day values). */
    fun nightUseCustomZones(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[nightUseCustomZonesKey] ?: false }

    suspend fun setNightUseCustomZones(use: Boolean) {
        context.dataStore.edit { it[nightUseCustomZonesKey] = use }
    }

    /** Night red (inner) slow-threat distance threshold in km — range 1–20, default 20. */
    fun nightSlowRedKm(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[nightSlowRedKmKey] ?: 20 }

    suspend fun setNightSlowRedKm(km: Int) {
        context.dataStore.edit { prefs ->
            prefs[nightSlowRedKmKey] = km.coerceIn(1, 20)
            val red = prefs[nightSlowRedKmKey] ?: 20
            val yellow = prefs[nightSlowYellowKmKey] ?: 50
            prefs[nightSlowYellowKmKey] = yellow.coerceIn(red + 2, 50)
        }
    }

    /** Night yellow (outer) slow-threat distance threshold in km — range red+2–50, default 50. */
    fun nightSlowYellowKm(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[nightSlowYellowKmKey] ?: 50 }

    suspend fun setNightSlowYellowKm(km: Int) {
        context.dataStore.edit { prefs ->
            val red = prefs[nightSlowRedKmKey] ?: 20
            prefs[nightSlowYellowKmKey] = km.coerceIn(red + 2, 50)
        }
    }

    /** Night red (inner) fast-threat ETA threshold in minutes — range 1–5, default 5. */
    fun nightFastRedMin(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[nightFastRedMinKey] ?: 5 }

    suspend fun setNightFastRedMin(min: Int) {
        context.dataStore.edit { prefs ->
            prefs[nightFastRedMinKey] = min.coerceIn(1, 5)
            val red = prefs[nightFastRedMinKey] ?: 5
            val yellow = prefs[nightFastYellowMinKey] ?: 20
            prefs[nightFastYellowMinKey] = yellow.coerceIn(red + 2, 20)
        }
    }

    /** Night yellow (outer) fast-threat ETA threshold in minutes — range red+2–20, default 20. */
    fun nightFastYellowMin(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[nightFastYellowMinKey] ?: 20 }

    suspend fun setNightFastYellowMin(min: Int) {
        context.dataStore.edit { prefs ->
            val red = prefs[nightFastRedMinKey] ?: 5
            prefs[nightFastYellowMinKey] = min.coerceIn(red + 2, 20)
        }
    }

    /** Whether the slow red zone can fire urgent siren alerts during the night window. */
    fun nightSlowRedZoneArmed(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[nightSlowRedArmedKey] ?: true }

    suspend fun setNightSlowRedZoneArmed(armed: Boolean) {
        context.dataStore.edit { it[nightSlowRedArmedKey] = armed }
    }

    /** Whether the slow yellow zone can fire warning alerts during the night window. */
    fun nightSlowYellowZoneArmed(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[nightSlowYellowArmedKey] ?: true }

    suspend fun setNightSlowYellowZoneArmed(armed: Boolean) {
        context.dataStore.edit { it[nightSlowYellowArmedKey] = armed }
    }

    /** Whether the fast red zone can fire urgent siren alerts during the night window. */
    fun nightFastRedZoneArmed(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[nightFastRedArmedKey] ?: true }

    suspend fun setNightFastRedZoneArmed(armed: Boolean) {
        context.dataStore.edit { it[nightFastRedArmedKey] = armed }
    }

    /** Whether the fast yellow zone can fire warning alerts during the night window. */
    fun nightFastYellowZoneArmed(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[nightFastYellowArmedKey] ?: true }

    suspend fun setNightFastYellowZoneArmed(armed: Boolean) {
        context.dataStore.edit { it[nightFastYellowArmedKey] = armed }
    }

    /** Whether zone sirens ring on the alarm stream (even on vibrate/silent) at night. Default off. */
    fun nightZoneSirenOverride(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[nightZoneSirenOverrideKey] ?: false }

    suspend fun setNightZoneSirenOverride(override: Boolean) {
        context.dataStore.edit { it[nightZoneSirenOverrideKey] = override }
    }

    /** Whether official oblast alerts ring on the alarm stream (even on vibrate/silent) at night. */
    fun nightOfficialSirenOverride(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[nightOfficialSirenOverrideKey] ?: false }

    suspend fun setNightOfficialSirenOverride(override: Boolean) {
        context.dataStore.edit { it[nightOfficialSirenOverrideKey] = override }
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
