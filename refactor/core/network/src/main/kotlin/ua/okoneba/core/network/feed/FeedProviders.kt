package ua.okoneba.core.network.feed

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ua.okoneba.core.domain.engine.FeedHealthInfo
import ua.okoneba.core.domain.engine.FeedSnapshot
import ua.okoneba.core.network.model.RawTelemetryPacketDto
import ua.okoneba.core.network.parser.ParseResult
import ua.okoneba.core.network.parser.TelemetryParser

interface FeedProvider {
    val sourceId: String
    val priority: Int
    val health: StateFlow<FeedHealthInfo>
    val telemetrySnapshots: SharedFlow<FeedSnapshot>

    fun start(scope: CoroutineScope)
    fun stop()
    fun emitRawPacket(packet: RawTelemetryPacketDto)
    fun simulateConnectionChange(connected: Boolean)
}

abstract class BaseFeedProvider(
    override val sourceId: String,
    override val priority: Int
) : FeedProvider {

    protected val _health = MutableStateFlow(
        FeedHealthInfo(
            sourceId = sourceId,
            priority = priority,
            isConnected = false,
            lastSuccessfulPacketTime = 0L,
            sourceStalenessMs = null,
            consecutiveFailureCount = 0
        )
    )
    override val health: StateFlow<FeedHealthInfo> = _health.asStateFlow()

    // Extra buffer capacity 1 with DROP_OLDEST enforces latest-state semantics
    protected val _telemetrySnapshots = MutableSharedFlow<FeedSnapshot>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    override val telemetrySnapshots: SharedFlow<FeedSnapshot> = _telemetrySnapshots.asSharedFlow()

    protected var providerScope: CoroutineScope? = null
    private var job: Job? = null

    override fun start(scope: CoroutineScope) {
        providerScope = scope
        _health.value = _health.value.copy(isConnected = true)
    }

    override fun stop() {
        job?.cancel()
        _health.value = _health.value.copy(isConnected = false)
        providerScope = null
    }

    override fun simulateConnectionChange(connected: Boolean) {
        _health.value = _health.value.copy(isConnected = connected)
    }

    override fun emitRawPacket(packet: RawTelemetryPacketDto) {
        val now = System.currentTimeMillis()
        when (val parseResult = TelemetryParser.parsePacket(packet)) {
            is ParseResult.Success -> {
                _health.value = _health.value.copy(
                    isConnected = true,
                    lastSuccessfulPacketTime = now,
                    sourceStalenessMs = packet.stalenessMs,
                    consecutiveFailureCount = 0
                )
                val snapshot = FeedSnapshot(
                    sourceId = sourceId,
                    threats = parseResult.value,
                    receivedAt = now,
                    isMalformed = false
                )
                _telemetrySnapshots.tryEmit(snapshot)
            }
            is ParseResult.Failure -> {
                val currentFailures = _health.value.consecutiveFailureCount + 1
                _health.value = _health.value.copy(
                    consecutiveFailureCount = currentFailures
                )
                val malformedSnapshot = FeedSnapshot(
                    sourceId = sourceId,
                    threats = emptyList(),
                    receivedAt = now,
                    isMalformed = true
                )
                _telemetrySnapshots.tryEmit(malformedSnapshot)
            }
        }
    }
}

class NeptunFeedProvider(
    sourceId: String = SOURCE_NEPTUN,
    priority: Int = PRIORITY_PRIMARY
) : BaseFeedProvider(sourceId, priority) {
    companion object {
        const val SOURCE_NEPTUN = "NEPTUN"
        const val PRIORITY_PRIMARY = 0
    }
}

class BackupFeedProvider(
    sourceId: String = SOURCE_BACKUP_A,
    priority: Int = PRIORITY_BACKUP
) : BaseFeedProvider(sourceId, priority) {
    companion object {
        const val SOURCE_BACKUP_A = "BACKUP_FEED_A"
        const val SOURCE_BACKUP_B = "BACKUP_FEED_B"
        const val PRIORITY_BACKUP = 10
    }
}
