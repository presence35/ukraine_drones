package ua.ukrainedrones

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * One fired alert shown in the System-status popup. [endMillis] is null while the alert is
 * still ringing (or if the service was restarted before it ended). [tier] is null for official
 * oblast alerts; [threatType]/[locality]/[distanceKm] describe the triggering threat when known.
 */
data class AlertHistoryEntry(
    val atMillis: Long,
    val endMillis: Long?,
    val tier: ThreatZone?,
    val threatType: ThreatType?,
    val locality: String?,
    val distanceKm: Double?
)

/**
 * Ring buffer of the last [MAX_ENTRIES] fired alerts (siren/chime posts), persisted to DataStore
 * so the status popup survives restarts. Written by [AlertService] when an alert starts and when
 * it ends; read by the System-status popup. Open events are tracked by key so a re-post (e.g. a
 * tier change) closes the previous episode instead of stacking duplicate rows.
 */
object AlertHistory {

    internal const val MAX_ENTRIES = 20

    /** Entries older than this are pruned on load/append (6 hours — older alerts don't matter). */
    internal const val AUTO_CLEAR_AGE_MS = 6L * 60 * 60 * 1000

    private val _entries = MutableStateFlow<List<AlertHistoryEntry>>(emptyList())
    val entries: StateFlow<List<AlertHistoryEntry>> = _entries.asStateFlow()

    @Volatile private var attached = false
    private var appContext: Context? = null
    private val attachScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val openAtMillis = mutableMapOf<String, Long>()

    /** Restore persisted events from DataStore. Call once at startup (idempotent). */
    fun attach(context: Context) {
        if (attached) return
        attached = true
        appContext = context.applicationContext
        attachScope.launch { attachLoaded() }
    }

    /**
     * Record a fired alert as open. Any previously open alert for the same [key] is closed
     * first (a re-post with the same threat id is a new episode, not a stack).
     */
    fun openAlert(
        key: String,
        tier: ThreatZone?,
        threatType: ThreatType?,
        locality: String?,
        distanceKm: Double?,
        at: Long
    ) {
        openAtMillis.remove(key)?.let { closeEntry(it, at) }
        append(AlertHistoryEntry(at, null, tier, threatType, locality, distanceKm))
        openAtMillis[key] = at
        persist()
    }

    /** Mark the open alert with [key] as ended at [at]. No-op when it isn't open. */
    fun closeAlert(key: String, at: Long) {
        val openedAt = openAtMillis.remove(key) ?: return
        closeEntry(openedAt, at)
    }

    /** Close every open zone alert (used when the grace window clears all ringing alerts). */
    fun closeAllZoneAlerts(at: Long) {
        val zoneKeys = openAtMillis.keys.filter { it.startsWith("id:") }
        zoneKeys.forEach { key ->
            openAtMillis.remove(key)?.let { closeEntry(it, at) }
        }
    }

    /** Wipe the whole history (System-status popup "Clear" button). */
    fun clear() {
        openAtMillis.clear()
        _entries.value = emptyList()
        persist()
    }

    private fun append(entry: AlertHistoryEntry) {
        val now = System.currentTimeMillis()
        _entries.value =
            pruneExpiredEntries(_entries.value + entry, now, AUTO_CLEAR_AGE_MS).takeLast(MAX_ENTRIES)
    }

    private fun closeEntry(atMillis: Long, end: Long) {
        _entries.value = _entries.value.map {
            if (it.atMillis == atMillis && it.endMillis == null) it.copy(endMillis = end) else it
        }
    }

    private fun persist() {
        val context = appContext ?: return
        runBlocking { ZonePrefs(context).setAlertHistory(serializeAlertHistory(_entries.value)) }
    }

    private fun attachLoaded() {
        val context = appContext ?: return
        val now = System.currentTimeMillis()
        val loaded = runBlocking { parseAlertHistory(ZonePrefs(context).alertHistory().first()) }
        val pruned = pruneExpiredEntries(loaded, now, AUTO_CLEAR_AGE_MS)
        _entries.value = pruned
        if (pruned != loaded) persist()
    }
}

/** Drop entries older than [maxAgeMs] — the 6-hour auto-clear. Pure, unit-tested. */
internal fun pruneExpiredEntries(
    entries: List<AlertHistoryEntry>,
    now: Long,
    maxAgeMs: Long
): List<AlertHistoryEntry> = entries.filter { now - it.atMillis < maxAgeMs }

/**
 * Serialized form of the history — "at|end|tier|type|locality|distance" lines, one per event.
 * Blank fields mean null. Pure, so persistence is unit-testable without DataStore.
 */
internal fun serializeAlertHistory(entries: List<AlertHistoryEntry>): String =
    entries.joinToString("\n") { entry ->
        listOf(
            entry.atMillis,
            entry.endMillis ?: "",
            entry.tier?.name ?: "",
            entry.threatType?.name ?: "",
            entry.locality ?: "",
            entry.distanceKm?.toString() ?: ""
        ).joinToString("|")
    }

/** Reverse of [serializeAlertHistory]; skips malformed lines and caps the ring buffer. */
internal fun parseAlertHistory(raw: String, maxEntries: Int = AlertHistory.MAX_ENTRIES): List<AlertHistoryEntry> =
    raw.split('\n').mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size != 6) return@mapNotNull null
        val at = parts[0].toLongOrNull() ?: return@mapNotNull null
        val end = parts[1].toLongOrNull()
        val tier = ThreatZone.entries.firstOrNull { it.name == parts[2] }
        val type = ThreatType.entries.firstOrNull { it.name == parts[3] }
        val locality = parts[4].takeIf { it.isNotEmpty() }
        val dist = parts[5].toDoubleOrNull()
        AlertHistoryEntry(at, end, tier, type, locality, dist)
    }.takeLast(maxEntries)
