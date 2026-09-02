package ua.okoneba.core.domain.repository

import kotlinx.coroutines.flow.Flow
import ua.okoneba.core.domain.model.AlertTier

data class EpisodeRecord(
    val sourceId: String,
    val threatId: String,
    val targetId: String,
    val highestAlertTier: AlertTier,
    val firstAlertAt: Long,
    val lastSeenAt: Long,
    val active: Boolean
) {
    val compositeKey: String
        get() = "$sourceId:$threatId:$targetId"
}

interface EpisodeLedgerRepository {
    suspend fun getEpisode(sourceId: String, threatId: String, targetId: String): EpisodeRecord?
    suspend fun getAllActiveEpisodes(): List<EpisodeRecord>
    fun observeActiveEpisodesCount(): Flow<Int>
    suspend fun recordAlert(
        sourceId: String,
        threatId: String,
        targetId: String,
        tier: AlertTier,
        timestamp: Long
    )
    suspend fun updateLastSeen(
        sourceId: String,
        threatId: String,
        timestamp: Long
    )
    suspend fun markEpisodesInactiveNotIn(activeKeys: Set<String>, timestamp: Long)
    suspend fun cleanExpiredEpisodes(olderThanMs: Long)
}

enum class AuditLogLevel {
    INFO,
    WARN,
    ERROR
}

data class AuditLogEntry(
    val id: Long = 0,
    val timestamp: Long,
    val eventType: String,
    val details: String,
    val level: AuditLogLevel = AuditLogLevel.INFO
)

interface AuditLogRepository {
    suspend fun record(eventType: String, details: String, level: AuditLogLevel = AuditLogLevel.INFO)
    suspend fun getRecentLogs(limit: Int = 100): List<AuditLogEntry>
    fun observeRecentLogs(limit: Int = 100): Flow<List<AuditLogEntry>>
}
