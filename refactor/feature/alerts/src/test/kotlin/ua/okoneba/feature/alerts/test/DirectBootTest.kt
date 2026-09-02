package ua.okoneba.feature.alerts.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.okoneba.core.domain.model.AlertDeduplicationPolicy
import ua.okoneba.core.domain.model.MonitoredTarget
import ua.okoneba.core.domain.model.ZoneConfiguration
import ua.okoneba.core.domain.repository.MonitoringSettings
import ua.okoneba.core.domain.repository.StoredLocation

class DirectBootTest {

    @Test
    fun `test direct boot minimal monitoring settings structure`() {
        // Minimal pre-unlock settings loaded from DE storage
        val deSettings = MonitoringSettings(
            isMonitoringEnabled = true,
            zoneConfig = ZoneConfiguration.safeCreate(5.0, 25.0),
            lastKnownLocation = StoredLocation(
                latitude = 50.4501,
                longitude = 30.5234,
                accuracyMeters = 50f,
                timestamp = 1000L
            ),
            alertPolicy = AlertDeduplicationPolicy.ONCE_PER_THREAT,
            pinnedTargets = listOf(
                MonitoredTarget.Pinned("kyiv", 50.4501, 30.5234, "Kyiv")
            )
        )

        // Verify Direct-Boot essential state can be constructed and evaluated
        assertTrue(deSettings.isMonitoringEnabled)
        assertEquals(5.0, deSettings.zoneConfig.redRadiusKm, 0.01)
        assertEquals(25.0, deSettings.zoneConfig.yellowRadiusKm, 0.01)
        assertEquals(1, deSettings.pinnedTargets.size)

        // Invariant 20: Explicitly stopped monitoring must remain stopped
        val stoppedSettings = deSettings.copy(isMonitoringEnabled = false)
        assertFalse(stoppedSettings.isMonitoringEnabled)
    }
}
