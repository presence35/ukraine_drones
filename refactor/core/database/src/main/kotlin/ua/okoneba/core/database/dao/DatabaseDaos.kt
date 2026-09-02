package ua.okoneba.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ua.okoneba.core.database.entity.AuditLogEntity
import ua.okoneba.core.database.entity.EpisodeLedgerEntity

@Dao
interface EpisodeLedgerDao {

    @Query("SELECT * FROM episode_ledger WHERE sourceId = :sourceId AND threatId = :threatId AND targetId = :targetId LIMIT 1")
    suspend fun getEpisode(sourceId: String, threatId: String, targetId: String): EpisodeLedgerEntity?

    @Query("SELECT * FROM episode_ledger WHERE active = 1")
    suspend fun getAllActiveEpisodes(): List<EpisodeLedgerEntity>

    @Query("SELECT COUNT(*) FROM episode_ledger WHERE active = 1")
    fun observeActiveEpisodesCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: EpisodeLedgerEntity)

    @Query("UPDATE episode_ledger SET lastSeenAt = :timestamp WHERE sourceId = :sourceId AND threatId = :threatId")
    suspend fun updateLastSeen(sourceId: String, threatId: String, timestamp: Long)

    @Query("UPDATE episode_ledger SET active = 0 WHERE active = 1 AND (sourceId || ':' || threatId || ':' || targetId) NOT IN (:activeKeys)")
    suspend fun markInactiveNotIn(activeKeys: List<String>)

    @Query("DELETE FROM episode_ledger WHERE active = 0 AND lastSeenAt < :olderThanMs")
    suspend fun deleteExpiredInactive(olderThanMs: Long)

    @Transaction
    suspend fun recordAlertAtomic(
        sourceId: String,
        threatId: String,
        targetId: String,
        newTier: String,
        timestamp: Long
    ) {
        val existing = getEpisode(sourceId, threatId, targetId)
        if (existing == null) {
            insertOrUpdate(
                EpisodeLedgerEntity(
                    sourceId = sourceId,
                    threatId = threatId,
                    targetId = targetId,
                    highestAlertTier = newTier,
                    firstAlertAt = timestamp,
                    lastSeenAt = timestamp,
                    active = true
                )
            )
        } else {
            // Keep the highest alert tier between existing and new
            val currentTierRank = if (existing.highestAlertTier == "RED") 2 else 1
            val newTierRank = if (newTier == "RED") 2 else 1
            val updatedTier = if (newTierRank > currentTierRank) newTier else existing.highestAlertTier

            insertOrUpdate(
                existing.copy(
                    highestAlertTier = updatedTier,
                    lastSeenAt = timestamp,
                    active = true
                )
            )
        }
    }
}

@Dao
interface AuditLogDao {

    @Insert
    suspend fun insert(entity: AuditLogEntity)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int): List<AuditLogEntity>

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int): Flow<List<AuditLogEntity>>

    @Query("SELECT COUNT(*) FROM audit_log")
    suspend fun countLogs(): Int

    @Query("DELETE FROM audit_log WHERE id IN (SELECT id FROM audit_log ORDER BY timestamp ASC LIMIT :toDeleteCount)")
    suspend fun trimOldestLogs(toDeleteCount: Int)

    @Transaction
    suspend fun insertBounded(entity: AuditLogEntity, maxCapacity: Int = 1000) {
        insert(entity)
        val count = countLogs()
        if (count > maxCapacity) {
            val excess = count - maxCapacity
            trimOldestLogs(excess)
        }
    }
}
