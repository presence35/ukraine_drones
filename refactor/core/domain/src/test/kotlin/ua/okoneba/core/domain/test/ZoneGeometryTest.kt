package ua.okoneba.core.domain.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import ua.okoneba.core.domain.engine.ZoneEvaluationEngine
import ua.okoneba.core.domain.model.AlertTier
import ua.okoneba.core.domain.model.Coordinates
import ua.okoneba.core.domain.model.NormalizedThreat
import ua.okoneba.core.domain.model.ThreatType
import ua.okoneba.core.domain.model.ZoneConfiguration

class ZoneGeometryTest {

    private val engine = ZoneEvaluationEngine()

    @Test
    fun `test zone configuration bounds validation`() {
        // Valid configs
        val valid = ZoneConfiguration(redRadiusKm = 2.0, yellowRadiusKm = 4.0)
        assertEquals(2.0, valid.redRadiusKm, 0.001)
        assertEquals(4.0, valid.yellowRadiusKm, 0.001)

        val maxValid = ZoneConfiguration(redRadiusKm = 20.0, yellowRadiusKm = 50.0)
        assertEquals(20.0, maxValid.redRadiusKm, 0.001)
        assertEquals(50.0, maxValid.yellowRadiusKm, 0.001)

        // Invalid red radius (< 2 km)
        assertThrows(IllegalArgumentException::class.java) {
            ZoneConfiguration(redRadiusKm = 1.9, yellowRadiusKm = 10.0)
        }

        // Invalid red radius (> 20 km)
        assertThrows(IllegalArgumentException::class.java) {
            ZoneConfiguration(redRadiusKm = 20.1, yellowRadiusKm = 30.0)
        }

        // Invalid yellow radius (yellow <= red)
        assertThrows(IllegalArgumentException::class.java) {
            ZoneConfiguration(redRadiusKm = 10.0, yellowRadiusKm = 10.0)
        }

        // Invalid yellow radius (yellow < red + 2 km)
        assertThrows(IllegalArgumentException::class.java) {
            ZoneConfiguration(redRadiusKm = 10.0, yellowRadiusKm = 11.5)
        }

        // Invalid yellow radius (> 50 km)
        assertThrows(IllegalArgumentException::class.java) {
            ZoneConfiguration(redRadiusKm = 10.0, yellowRadiusKm = 50.5)
        }
    }

    @Test
    fun `test safeCreate clamps and normalizes invalid parameters`() {
        val clampedUnder = ZoneConfiguration.safeCreate(redKm = 0.5, yellowKm = 1.0)
        assertEquals(ZoneConfiguration.MIN_RED_RADIUS_KM, clampedUnder.redRadiusKm, 0.001)
        assertEquals(ZoneConfiguration.MIN_RED_RADIUS_KM + ZoneConfiguration.MIN_YELLOW_EXTRA_KM, clampedUnder.yellowRadiusKm, 0.001)

        val clampedOver = ZoneConfiguration.safeCreate(redKm = 25.0, yellowKm = 60.0)
        assertEquals(ZoneConfiguration.MAX_RED_RADIUS_KM, clampedOver.redRadiusKm, 0.001)
        assertEquals(ZoneConfiguration.MAX_YELLOW_RADIUS_KM, clampedOver.yellowRadiusKm, 0.001)
    }

    @Test
    fun `test exact zone boundaries inclusion`() {
        val config = ZoneConfiguration(redRadiusKm = 10.0, yellowRadiusKm = 25.0)
        val targetCoords = Coordinates(latitude = 50.4501, longitude = 30.5234) // Kyiv center

        // 1. Distance = 0 km (same coordinate) -> RED
        val threatCenter = createThreat("T1", 50.4501, 30.5234)
        val (distCenter, tierCenter) = engine.evaluateDistanceAndTier(threatCenter, targetCoords, config)
        assertEquals(0.0, distCenter, 0.01)
        assertEquals(AlertTier.RED, tierCenter)

        // 2. Distance ~5 km (well inside red) -> RED
        val threat5km = createThreat("T2", 50.4951, 30.5234) // ~5.0 km North
        val (dist5km, tier5km) = engine.evaluateDistanceAndTier(threat5km, targetCoords, config)
        assert(dist5km <= 10.0)
        assertEquals(AlertTier.RED, tier5km)

        // 3. Distance ~15 km (between red and yellow) -> YELLOW
        val threat15km = createThreat("T3", 50.5851, 30.5234) // ~15.0 km North
        val (dist15km, tier15km) = engine.evaluateDistanceAndTier(threat15km, targetCoords, config)
        assert(dist15km > 10.0 && dist15km <= 25.0)
        assertEquals(AlertTier.YELLOW, tier15km)

        // 4. Distance ~40 km (outside yellow) -> OUTSIDE
        val threat40km = createThreat("T4", 50.8101, 30.5234) // ~40.0 km North
        val (dist40km, tier40km) = engine.evaluateDistanceAndTier(threat40km, targetCoords, config)
        assert(dist40km > 25.0)
        assertEquals(AlertTier.OUTSIDE, tier40km)
    }

    private fun createThreat(id: String, lat: Double, lon: Double): NormalizedThreat {
        return NormalizedThreat(
            sourceId = "NEPTUN",
            threatId = id,
            type = ThreatType.MISSILE,
            latitude = lat,
            longitude = lon,
            timestamp = System.currentTimeMillis()
        )
    }
}
