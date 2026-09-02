package ua.okoneba.core.network.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.okoneba.core.domain.engine.FeedHealthInfo
import ua.okoneba.core.domain.engine.FeedSnapshot
import ua.okoneba.core.domain.engine.MasterThreatEvaluator
import ua.okoneba.core.domain.model.SystemHealthState
import ua.okoneba.core.network.model.RawTelemetryPacketDto
import ua.okoneba.core.network.model.RawThreatDto
import ua.okoneba.core.network.parser.ParseResult
import ua.okoneba.core.network.parser.TelemetryParser

class MalformedTelemetryTest {

    @Test
    fun `test parser cleanly rejects invalid coordinates and blank ids without throwing`() {
        // Invalid latitude (> 90)
        val badLatPacket = RawTelemetryPacketDto(
            sourceId = "NEPTUN",
            sequenceNumber = 1,
            serverTime = 1000L,
            threats = listOf(
                RawThreatDto(id = "T1", type = "MISSILE", lat = 120.0, lon = 30.0, timestamp = 1000L)
            )
        )
        val result1 = TelemetryParser.parsePacket(badLatPacket)
        assertTrue(result1 is ParseResult.Failure)

        // Blank Threat ID
        val blankIdPacket = RawTelemetryPacketDto(
            sourceId = "NEPTUN",
            sequenceNumber = 2,
            serverTime = 1000L,
            threats = listOf(
                RawThreatDto(id = "", type = "DRONE", lat = 50.0, lon = 30.0, timestamp = 1000L)
            )
        )
        val result2 = TelemetryParser.parsePacket(blankIdPacket)
        assertTrue(result2 is ParseResult.Failure)

        // Valid packet
        val validPacket = RawTelemetryPacketDto(
            sourceId = "NEPTUN",
            sequenceNumber = 3,
            serverTime = 1000L,
            threats = listOf(
                RawThreatDto(id = "VALID_1", type = "DRONE", lat = 50.4501, lon = 30.5234, timestamp = 1000L)
            )
        )
        val result3 = TelemetryParser.parsePacket(validPacket)
        assertTrue(result3 is ParseResult.Success)
        assertEquals(1, (result3 as ParseResult.Success).value.size)
    }

    @Test
    fun `test evaluator handles malformed packet stream without clearing retained state or crashing`() {
        val evaluator = MasterThreatEvaluator()
        val t0 = 1_000_000L

        val neptunHealth = FeedHealthInfo(
            sourceId = "NEPTUN",
            priority = 0,
            isConnected = true,
            lastSuccessfulPacketTime = t0
        )
        evaluator.updateFeedHealth(neptunHealth, t0)

        // 1. Valid snapshot arrives
        val validPacket = RawTelemetryPacketDto(
            sourceId = "NEPTUN",
            sequenceNumber = 1,
            serverTime = t0,
            threats = listOf(
                RawThreatDto(id = "T_ALPHA", type = "MISSILE", lat = 50.0, lon = 30.0, timestamp = t0)
            )
        )
        val parseSuccess = TelemetryParser.parsePacket(validPacket) as ParseResult.Success
        evaluator.ingestFeedSnapshot(FeedSnapshot("NEPTUN", parseSuccess.value, t0), t0)

        assertEquals(1, evaluator.authoritativeState.value.threats.size)
        assertEquals("T_ALPHA", evaluator.authoritativeState.value.threats.first().threatId)

        // 2. Corrupted / Malformed packet arrives 2 seconds later
        val malformedSnapshot = FeedSnapshot("NEPTUN", emptyList(), t0 + 2000L, isMalformed = true)
        evaluator.ingestFeedSnapshot(malformedSnapshot, t0 + 2000L)

        // Invariant 16: Malformed telemetry must not crash the engine or clear valid state
        val stateAfterMalformed = evaluator.authoritativeState.value
        assertEquals(SystemHealthState.HEALTHY, stateAfterMalformed.systemHealth)
        assertEquals(1, stateAfterMalformed.threats.size)
        assertEquals("T_ALPHA", stateAfterMalformed.threats.first().threatId)

        // 3. Next valid packet arrives and replaces snapshot cleanly
        val nextValidPacket = RawTelemetryPacketDto(
            sourceId = "NEPTUN",
            sequenceNumber = 3,
            serverTime = t0 + 4000L,
            threats = listOf(
                RawThreatDto(id = "T_BETA", type = "MISSILE", lat = 50.1, lon = 30.1, timestamp = t0 + 4000L)
            )
        )
        val parseSuccess2 = TelemetryParser.parsePacket(nextValidPacket) as ParseResult.Success
        evaluator.ingestFeedSnapshot(FeedSnapshot("NEPTUN", parseSuccess2.value, t0 + 4000L), t0 + 4000L)

        val stateRecovered = evaluator.authoritativeState.value
        assertEquals(1, stateRecovered.threats.size)
        assertEquals("T_BETA", stateRecovered.threats.first().threatId)
    }
}
