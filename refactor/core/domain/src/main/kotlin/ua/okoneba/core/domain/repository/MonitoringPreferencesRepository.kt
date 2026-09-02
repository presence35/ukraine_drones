package ua.okoneba.core.domain.repository

import kotlinx.coroutines.flow.Flow
import ua.okoneba.core.domain.model.AlertDeduplicationPolicy
import ua.okoneba.core.domain.model.MonitoredTarget
import ua.okoneba.core.domain.model.ZoneConfiguration

data class StoredLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestamp: Long
)

data class MonitoringSettings(
    val isMonitoringEnabled: Boolean,
    val zoneConfig: ZoneConfiguration,
    val lastKnownLocation: StoredLocation?,
    val alertPolicy: AlertDeduplicationPolicy,
    val pinnedTargets: List<MonitoredTarget.Pinned>
)

interface MonitoringPreferencesRepository {
    val monitoringSettings: Flow<MonitoringSettings>
    suspend fun getSettings(): MonitoringSettings
    suspend fun setMonitoringEnabled(enabled: Boolean)
    suspend fun updateZoneConfig(config: ZoneConfiguration)
    suspend fun updateLastKnownLocation(location: StoredLocation)
    suspend fun updateAlertPolicy(policy: AlertDeduplicationPolicy)
    suspend fun updatePinnedTargets(targets: List<MonitoredTarget.Pinned>)
}
