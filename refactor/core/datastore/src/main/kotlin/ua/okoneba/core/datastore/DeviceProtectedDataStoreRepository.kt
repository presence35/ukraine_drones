package ua.okoneba.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import ua.okoneba.core.domain.model.AlertDeduplicationPolicy
import ua.okoneba.core.domain.model.MonitoredTarget
import ua.okoneba.core.domain.model.ZoneConfiguration
import ua.okoneba.core.domain.repository.MonitoringPreferencesRepository
import ua.okoneba.core.domain.repository.MonitoringSettings
import ua.okoneba.core.domain.repository.StoredLocation

class DeviceProtectedDataStoreRepository(
    private val dataStore: DataStore<Preferences>
) : MonitoringPreferencesRepository {

    private object Keys {
        val IS_MONITORING_ENABLED = booleanPreferencesKey("is_monitoring_enabled")
        val RED_RADIUS_KM = doublePreferencesKey("red_radius_km")
        val YELLOW_RADIUS_KM = doublePreferencesKey("yellow_radius_km")
        val LAST_LOC_LAT = doublePreferencesKey("last_loc_lat")
        val LAST_LOC_LON = doublePreferencesKey("last_loc_lon")
        val LAST_LOC_ACCURACY = floatPreferencesKey("last_loc_acc")
        val LAST_LOC_TIME = longPreferencesKey("last_loc_time")
        val ALERT_POLICY = stringPreferencesKey("alert_policy")
        val PINNED_TARGETS_RAW = stringPreferencesKey("pinned_targets_raw")
    }

    override val monitoringSettings: Flow<MonitoringSettings> = dataStore.data.map { prefs ->
        mapPreferencesToSettings(prefs)
    }

    override suspend fun getSettings(): MonitoringSettings {
        val prefs = dataStore.data.first()
        return mapPreferencesToSettings(prefs)
    }

    override suspend fun setMonitoringEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.IS_MONITORING_ENABLED] = enabled
        }
    }

    override suspend fun updateZoneConfig(config: ZoneConfiguration) {
        dataStore.edit { prefs ->
            prefs[Keys.RED_RADIUS_KM] = config.redRadiusKm
            prefs[Keys.YELLOW_RADIUS_KM] = config.yellowRadiusKm
        }
    }

    override suspend fun updateLastKnownLocation(location: StoredLocation) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_LOC_LAT] = location.latitude
            prefs[Keys.LAST_LOC_LON] = location.longitude
            prefs[Keys.LAST_LOC_ACCURACY] = location.accuracyMeters
            prefs[Keys.LAST_LOC_TIME] = location.timestamp
        }
    }

    override suspend fun updateAlertPolicy(policy: AlertDeduplicationPolicy) {
        dataStore.edit { prefs ->
            prefs[Keys.ALERT_POLICY] = policy.name
        }
    }

    override suspend fun updatePinnedTargets(targets: List<MonitoredTarget.Pinned>) {
        // Simple and robust delimiter-based serialization for Direct-Boot safety without external reflection
        val serialized = targets.joinToString(";") { "${it.targetId}|${it.latitude}|${it.longitude}|${it.label}" }
        dataStore.edit { prefs ->
            prefs[Keys.PINNED_TARGETS_RAW] = serialized
        }
    }

    private fun mapPreferencesToSettings(prefs: Preferences): MonitoringSettings {
        val isEnabled = prefs[Keys.IS_MONITORING_ENABLED] ?: false
        val redRadius = prefs[Keys.RED_RADIUS_KM] ?: ZoneConfiguration.DEFAULT_RED_RADIUS_KM
        val yellowRadius = prefs[Keys.YELLOW_RADIUS_KM] ?: ZoneConfiguration.DEFAULT_YELLOW_RADIUS_KM
        val zoneConfig = ZoneConfiguration.safeCreate(redRadius, yellowRadius)

        val lat = prefs[Keys.LAST_LOC_LAT]
        val lon = prefs[Keys.LAST_LOC_LON]
        val acc = prefs[Keys.LAST_LOC_ACCURACY]
        val time = prefs[Keys.LAST_LOC_TIME]

        val storedLoc = if (lat != null && lon != null && acc != null && time != null) {
            StoredLocation(lat, lon, acc, time)
        } else {
            null
        }

        val alertPolicyStr = prefs[Keys.ALERT_POLICY]
        val alertPolicy = runCatching {
            alertPolicyStr?.let { AlertDeduplicationPolicy.valueOf(it) }
        }.getOrNull() ?: AlertDeduplicationPolicy.ONCE_PER_THREAT

        val rawPinned = prefs[Keys.PINNED_TARGETS_RAW] ?: ""
        val pinnedList = if (rawPinned.isNotBlank()) {
            rawPinned.split(";").mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size >= 4) {
                    val id = parts[0]
                    val pLat = parts[1].toDoubleOrNull()
                    val pLon = parts[2].toDoubleOrNull()
                    val label = parts[3]
                    if (pLat != null && pLon != null) {
                        MonitoredTarget.Pinned(targetId = id, latitude = pLat, longitude = pLon, label = label)
                    } else null
                } else null
            }
        } else {
            emptyList()
        }

        return MonitoringSettings(
            isMonitoringEnabled = isEnabled,
            zoneConfig = zoneConfig,
            lastKnownLocation = storedLoc,
            alertPolicy = alertPolicy,
            pinnedTargets = pinnedList
        )
    }

    companion object {
        private const val DATASTORE_FILE_NAME = "monitoring_de_preferences.preferences_pb"

        fun create(context: Context): DeviceProtectedDataStoreRepository {
            val deContext = if (context.isDeviceProtectedStorage) {
                context
            } else {
                context.createDeviceProtectedStorageContext()
            }

            val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                produceFile = { deContext.preferencesDataStoreFile(DATASTORE_FILE_NAME) }
            )

            return DeviceProtectedDataStoreRepository(dataStore)
        }
    }
}
