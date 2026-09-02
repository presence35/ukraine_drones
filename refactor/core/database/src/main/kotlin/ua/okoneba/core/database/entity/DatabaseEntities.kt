package ua.okoneba.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import ua.okoneba.core.domain.model.AlertTier
import ua.okoneba.core.domain.repository.AuditLogEntry
import ua.okoneba.core.domain.repository.AuditLogLevel
import ua.okoneba.core.domain.repository.EpisodeRecord

@Entity(
    tableName = "episode_ledger",
    primaryKeys = ["sourceId", "threatId", "targetId"],
    indices = [
        Index(value = ["active"]),
        Index(value = ["lastSeenAt"])
    ]
)
data class EpisodeLedgerEntity(
    val sourceId: String,
    val threatId: String,
    val targetId: String,
    val highestAlertTier: String,
    val firstAlertAt: Long,
    val lastSeenAt: Long,
    val active: Boolean
) {
    fun toDomain(): EpisodeRecord = EpisodeRecord(
        sourceId = sourceId,
        threatId = threatId,
        targetId = targetId,
        highestAlertTier = runCatching { AlertTier.valueOf(highestAlertTier) }.getOrDefault(AlertTier.YELLOW),
        firstAlertAt = firstAlertAt,
        lastSeenAt = lastSeenAt,
        active = active
    )

    companion object {
        fun fromDomain(domain: EpisodeRecord): EpisodeLedgerEntity = EpisodeLedgerEntity(
            sourceId = domain.sourceId,
            threatId = domain.threatId,
            targetId = domain.targetId,
            highestAlertTier = domain.highestAlertTier.name,
            firstAlertAt = domain.firstAlertAt,
            lastSeenAt = domain.lastSeenAt,
            active = domain.active
        )
    }
}

@Entity(
    tableName = "audit_log",
    indices = [
        Index(value = ["timestamp"])
    ]
)
data class AuditLogEntity(
    @androidx.room.PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val eventType: String,
    val details: String,
    val level: String
) {
    fun toDomain(): AuditLogEntry = AuditLogEntry(
        id = id,
        timestamp = timestamp,
        eventType = eventType,
        details = details,
        level = runCatching { AuditLogLevel.valueOf(level) }.getOrDefault(AuditLogLevel.INFO)
    )

    companion object {
        fun fromDomain(domain: AuditLogEntry): AuditLogEntity = AuditLogEntity(
            id = domain.id,
            timestamp = domain.timestamp,
            eventType = domain.eventType,
            details = domain.details,
            level = domain.level.name
        )
    }
}
