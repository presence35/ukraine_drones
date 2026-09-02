package ua.okoneba.core.domain.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.okoneba.core.domain.plugin.FlourishPlugin
import ua.okoneba.core.domain.plugin.FlourishPluginManager
import ua.okoneba.core.domain.plugin.FlourishToken
import ua.okoneba.core.domain.plugin.FlourishType
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class FlourishBoundaryTest {

    @Test
    fun `test failing flourish plugin is isolated and does not crash core dispatch`() = runTest {
        val manager = FlourishPluginManager(CoroutineScope(Dispatchers.Default))
        val errorReported = AtomicBoolean(false)
        val successfulPluginExecutionCount = AtomicInteger(0)

        val failingPlugin = object : FlourishPlugin {
            override val pluginName: String = "CrashingAnimationPlugin"
            override fun onFlourishEvent(token: FlourishToken) {
                throw RuntimeException("Intentional animation plugin crash")
            }
        }

        val healthyPlugin = object : FlourishPlugin {
            override val pluginName: String = "HealthyTelemetryRecorder"
            override fun onFlourishEvent(token: FlourishToken) {
                successfulPluginExecutionCount.incrementAndGet()
            }
        }

        manager.registerPlugin(failingPlugin)
        manager.registerPlugin(healthyPlugin)

        val token = FlourishToken(
            type = FlourishType.THREAT_ENTERED_RED,
            threatId = "THREAT_99",
            targetId = "target_1",
            timestamp = System.currentTimeMillis()
        )

        // Invariant 17: Plugin failure must never break alert generation or crash core
        manager.dispatchFlourishToken(token) { pluginName, error ->
            if (pluginName == "CrashingAnimationPlugin") {
                errorReported.set(true)
            }
        }

        delay(100) // Allow asynchronous dispatch to complete

        assertTrue(errorReported.get())
        assertEquals(1, successfulPluginExecutionCount.get())
    }
}
