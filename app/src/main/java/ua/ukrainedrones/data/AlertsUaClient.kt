package ua.ukrainedrones

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Latest state from the independent alerts.com.ua backup source. */
data class AlertsUaState(
    val alerts: List<OblastAlert> = emptyList(),
    val active: Boolean = false,   // has the backup returned data at least once
    val lastOkAt: Long = 0L,       // epoch millis of the last successful poll
    val lastError: String? = null
)

/**
 * Independent oblast-alert backup source: alerts.com.ua/api/states. Keyless/public — the same
 * official air-raid alert data other aggregators reference. Polled on a fixed cadence and fed
 * into [NeptunClient] as a fallback so oblast alerts survive a NEPTUN outage. This is the
 * "less reliable backup" path, never the primary.
 */
object AlertsUaClient {

    private const val URL = "https://alerts.com.ua/api/states"
    private const val POLL_MS = 10_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private val inFlight = AtomicBoolean(false)
    private var manuallyStopped = false

    private val _state = MutableStateFlow(AlertsUaState())
    val state: StateFlow<AlertsUaState> = _state.asStateFlow()

    fun start() {
        if (!scope.isActive) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
        manuallyStopped = false
        pollNow()
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && !manuallyStopped) {
                delay(POLL_MS)
                if (manuallyStopped) return@launch
                pollNow()
            }
        }
    }

    fun stop() {
        manuallyStopped = true
        pollJob?.cancel()
        pollJob = null
        scope.cancel()
    }

    /** Immediate pull, used when the app returns to the foreground. */
    fun refreshNow() {
        if (manuallyStopped) return
        pollNow()
    }

    private fun pollNow() {
        if (!inFlight.compareAndSet(false, true)) return
        // No Cache-Control: no-store on purpose: the origin republishes the payload every
        // ~5-7s behind Cloudflare's edge cache, so serving polls from that cache keeps the
        // origin load near zero at identical freshness.
        val request = Request.Builder()
            .url(URL)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                inFlight.set(false)
                if (!manuallyStopped) {
                    _state.value = _state.value.copy(lastError = e.message)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                inFlight.set(false)
                response.use {
                    if (!it.isSuccessful) {
                        if (!manuallyStopped) _state.value = _state.value.copy(lastError = "HTTP ${it.code}")
                        return
                    }
                    val body = it.body?.string() ?: return
                    try {
                        val parsed = parseStates(body)
                        // Keep last-good on transient failures; only replace on a valid payload.
                        if (parsed != null) {
                            _state.value = AlertsUaState(
                                alerts = parsed,
                                active = true,
                                lastOkAt = System.currentTimeMillis(),
                                lastError = null
                            )
                        }
                    } catch (_: Exception) {
                        // Malformed payload — keep current state.
                    }
                }
            }
        })
    }

    /** Parse the alerts.com.ua `/api/states` payload into the active oblast alerts. */
    fun parseStates(body: String): List<OblastAlert>? {
        return try {
            val env = JSONObject(body)
            val arr = env.optJSONArray("states") ?: return null
            val out = mutableListOf<OblastAlert>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (!o.optBoolean("alert", false)) continue
                val name = o.optString("name")
                if (name.isBlank()) continue
                // key = region id; name = the oblast, which our focus attribution matches by stem.
                out.add(
                    OblastAlert(
                        key = o.optString("id"),
                        name = name,
                        oblast = name,
                        since = o.optString("changed", null)
                    )
                )
            }
            out
        } catch (_: Exception) {
            null
        }
    }
}
