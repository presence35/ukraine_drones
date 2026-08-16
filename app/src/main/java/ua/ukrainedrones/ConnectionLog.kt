package ua.ukrainedrones

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Connection states shown in the status log — mirrors the header pill's three states. */
enum class ConnStatus { ONLINE, OFFLINE, BACKUP }

/** One logged status change. [durationSec] is the episode length for OFF/BACKUP, null for ONLINE. */
data class ConnLogEntry(
    val atMillis: Long,
    val status: ConnStatus,
    val durationSec: Long?
)

/**
 * Ring buffer of the last [MAX_ENTRIES] connection statuses, persisted to DataStore so the log
 * survives app/service restarts. Fed by [NeptunClient]'s watchdog tick. The currently
 * in-progress off/backup episode is kept separately (see [currentEpisode]) so the popup can show
 * a live running duration, and is only committed to the log once it has lasted
 * [NeptunClient.OFFLINE_GRACE_MS] — transient blips are ignored.
 */
object ConnectionLog {

    private const val MAX_ENTRIES = 10
    private const val LINE_SEP = '\n'

    private val _entries = MutableStateFlow<List<ConnLogEntry>>(emptyList())
    val entries: StateFlow<List<ConnLogEntry>> = _entries.asStateFlow()

    @Volatile private var pending: ConnLogEntry? = null
    @Volatile private var lastStatus: ConnStatus? = null
    @Volatile private var attached = false
    private var appContext: Context? = null

    /**
     * Restore persisted entries + any in-progress episode from DataStore. Call once (from
     * AlertService/MainActivity) before NeptunClient starts its watchdog. Idempotent.
     */
    fun attach(context: Context) {
        if (attached) return
        attached = true
        appContext = context.applicationContext
        val prefs = ZonePrefs(context.applicationContext)
        val (restoredPending, restoredEntries) = runBlocking {
            val loaded = parse(prefs.connLog().first())
            val since = prefs.connLogPendingSince().first()
            val name = prefs.connLogPendingStatus().first()
            val restored = if (since > 0) {
                ConnLogEntry(since, ConnStatus.entries.firstOrNull { it.name == name } ?: ConnStatus.OFFLINE, null)
            } else null
            restored to loaded
        }
        pending = restoredPending
        _entries.value = restoredEntries
    }

    /**
     * Called every watchdog tick with the current status. Only commits a completed episode once
     * it outlasted the grace window, and brackets it with a recovery row when it recovers.
     */
    fun observe(status: ConnStatus, now: Long) {
        val prev = lastStatus
        lastStatus = status
        if (prev == null) return // first sighting after (re)start — never fabricate an episode
        if (status == prev) return

        val current = pending
        var dirty = false
        if (current != null && current.status != ConnStatus.ONLINE) {
            val durSec = (now - current.atMillis) / 1000
            if (durSec * 1000 >= NeptunClient.OFFLINE_GRACE_MS) {
                _entries.value = (_entries.value + current.copy(durationSec = durSec)).takeLast(MAX_ENTRIES)
                dirty = true
            }
            pending = null
            persistPending(0L, "")
        }
        if (status == ConnStatus.ONLINE) {
            if (dirty) {
                _entries.value = (_entries.value + ConnLogEntry(now, ConnStatus.ONLINE, null)).takeLast(MAX_ENTRIES)
            }
            if (dirty) persist()
        } else {
            pending = ConnLogEntry(now, status, null)
            persistPending(now, status.name)
            if (dirty) persist()
        }
    }

    /** The in-progress off/backup episode with its running duration, or null when online. */
    fun currentEpisode(now: Long): ConnLogEntry? =
        pending?.let { ConnLogEntry(it.atMillis, it.status, (now - it.atMillis) / 1000) }

    private fun persist() {
        val context = appContext ?: return
        runBlocking { ZonePrefs(context).setConnLog(serialize(_entries.value)) }
    }

    private fun persistPending(since: Long, status: String) {
        val context = appContext ?: return
        runBlocking {
            ZonePrefs(context).setConnLogPendingSince(since)
            ZonePrefs(context).setConnLogPendingStatus(status)
        }
    }

    private fun serialize(entries: List<ConnLogEntry>): String =
        entries.joinToString(LINE_SEP.toString()) { "${it.atMillis}|${it.status.name}|${it.durationSec ?: ""}" }

    private fun parse(raw: String): List<ConnLogEntry> =
        raw.split(LINE_SEP).mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size != 3) return@mapNotNull null
            val at = parts[0].toLongOrNull() ?: return@mapNotNull null
            val status = ConnStatus.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
            val dur = parts[2].toLongOrNull()
            ConnLogEntry(at, status, dur)
        }.takeLast(MAX_ENTRIES)
}