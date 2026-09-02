package ua.okoneba.core.database.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.okoneba.core.database.dao.AuditLogDao
import ua.okoneba.core.database.dao.EpisodeLedgerDao
import ua.okoneba.core.database.entity.AuditLogEntity
import ua.okoneba.core.domain.model.AlertTier
import ua.okoneba.core.domain.repository.AuditLogEntry
import ua.okoneba.core.domain.repository.AuditLogLevel
import ua.okoneba.core.domain.repository.AuditLogRepository
import ua.okoneba.core.domain.repository.EpisodeLedgerRepository
import ua.okoneba.core.domain.repository.EpisodeRecord

class RoomEpisodeLedgerRepository(
    private val dao: EpisodeLedgerDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : EpisodeLedgerRepository {

    override suspend fun getEpisode(
        sourceId: String,
        threatId: String,
        targetId: String
    ): EpisodeRecord? = withContext(ioDispatcher) {
        dao.getEpisode(sourceId, threatId, targetId)?.toDomain()
    }

    override suspend fun getAllActiveEpisodes(): List<EpisodeRecord> = withContext(ioDispatcher) {
        dao.getAllActiveEpisodes().map { it.toDomain() }
    }

    override fun observeActiveEpisodesCount(): Flow<Int> {
        return dao.observeActiveEpisodesCount()
    }

    override suspend fun recordAlert(
        sourceId: String,
        threatId: String,
        targetId: String,
        tier: AlertTier,
        timestamp: Long
    ) = withContext(ioDispatcher) {
        dao.recordAlertAtomic(
            sourceId = sourceId,
            threatId = threatId,
            targetId = targetId,
            newTier = tier.name,
            timestamp = timestamp
        )
    }

    override suspend fun updateLastSeen(
        sourceId: String,
        threatId: String,
        timestamp: Long
    ) = withContext(ioDispatcher) {
        dao.updateLastSeen(sourceId, threatId, timestamp)
    }

    override suspend fun markEpisodesInactiveNotIn(
        activeKeys: Set<String>,
        timestamp: Long
    ) = withContext(ioDispatcher) {
        dao.markInactiveNotIn(activeKeys.toList())
    }

    override suspend fun cleanExpiredEpisodes(olderThanMs: Long) = withContext(ioDispatcher) {
        dao.deleteExpiredInactive(olderThanMs)
    }
}

class RoomAuditLogRepository(
    private val dao: AuditLogDao,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxCapacity: Int = 1000
) : AuditLogRepository {

    override suspend fun record(
        eventType: String,
        details: String,
        level: AuditLogLevel
    ) {
        val now = System.currentTimeMillis()
        val entity = AuditLogEntity(
            timestamp = now,
            eventType = eventType,
            details = details.take(500), // Bound entry payload size
            level = level.name
        )

        // Best effort non-blocking write to avoid stalling hot telemetry pipelines
        scope.launch(ioDispatcher) {
            runCatching {
                dao.insertBounded(entity, maxCapacity)
            }
        }
    }

    override suspend fun getRecentLogs(limit: Int): List<AuditLogEntry> = withContext(ioDispatcher) {
        dao.getRecentLogs(limit).map { it.toDomain() }
    }

    override fun observeRecentLogs(limit: Int): Flow<List<AuditLogEntry>> {
        return dao.observeRecentLogs(limit).map { list -> list.map { it.toDomain() } }
    }
}
