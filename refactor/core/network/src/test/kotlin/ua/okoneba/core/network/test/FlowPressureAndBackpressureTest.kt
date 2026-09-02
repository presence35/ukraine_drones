package ua.okoneba.core.network.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ua.okoneba.core.network.feed.NeptunFeedProvider
import ua.okoneba.core.network.model.RawTelemetryPacketDto
import ua.okoneba.core.network.model.RawThreatDto

class FlowPressureAndBackpressureTest {

    @Test
    fun `test telemetry burst conflation and latest state semantics under heavy load`() = runTest {
        val feed = NeptunFeedProvider()
        val scope = CoroutineScope(Dispatchers.Default)
        feed.start(scope)

        // Emit a rapid burst of 100 telemetry packets
        for (i in 1..100) {
            val packet = RawTelemetryPacketDto(
                sourceId = "NEPTUN",
                sequenceNumber = i.toLong(),
                serverTime = 1000L + i,
                threats = listOf(
                    RawThreatDto(
                        id = "T_$i",
                        type = "MISSILE",
                        lat = 50.0 + (i * 0.001),
                        lon = 30.0,
                        timestamp = 1000L + i
                    )
                )
            )
            feed.emitRawPacket(packet)
        }

        // Collect latest snapshot from conflated stream
        val latestSnapshot = feed.telemetrySnapshots.first()

        // Verify buffer did not crash and latest valid state is preserved
        assertEquals("NEPTUN", latestSnapshot.sourceId)
        assertEquals(1, latestSnapshot.threats.size)
        // Highest sequence threat from the burst is present
        assertEquals("T_100", latestSnapshot.threats.first().threatId)

        scope.cancel()
    }
}
