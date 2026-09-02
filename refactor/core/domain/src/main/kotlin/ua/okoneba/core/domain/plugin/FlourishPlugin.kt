package ua.okoneba.core.domain.plugin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class FlourishType {
    THREAT_ENTERED_YELLOW,
    THREAT_ENTERED_RED,
    THREAT_ESCALATED,
    THREAT_EXITED_ACTIVE,
    THREAT_EXPIRED
}

data class FlourishToken(
    val type: FlourishType,
    val threatId: String,
    val targetId: String,
    val timestamp: Long
)

interface FlourishPlugin {
    val pluginName: String
    fun onFlourishEvent(token: FlourishToken)
}

/**
 * Isolated Flourish dispatcher ensuring plugin execution never blocks or crashes core threat evaluation.
 */
class FlourishPluginManager(
    private val scope: CoroutineScope
) {
    private val plugins = mutableListOf<FlourishPlugin>()

    fun registerPlugin(plugin: FlourishPlugin) {
        synchronized(plugins) {
            plugins.add(plugin)
        }
    }

    fun unregisterPlugin(plugin: FlourishPlugin) {
        synchronized(plugins) {
            plugins.remove(plugin)
        }
    }

    fun dispatchFlourishToken(token: FlourishToken, onPluginError: ((pluginName: String, error: Throwable) -> Unit)? = null) {
        val currentPlugins = synchronized(plugins) { plugins.toList() }
        if (currentPlugins.isEmpty()) return

        // Dispatch asynchronously on default dispatcher to isolate latency and failures
        scope.launch(Dispatchers.Default) {
            for (plugin in currentPlugins) {
                try {
                    plugin.onFlourishEvent(token)
                } catch (t: Throwable) {
                    onPluginError?.invoke(plugin.pluginName, t)
                }
            }
        }
    }
}
