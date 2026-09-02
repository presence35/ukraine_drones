package ua.okoneba.core.network.parser

import ua.okoneba.core.domain.model.Coordinates
import ua.okoneba.core.domain.model.NormalizedThreat
import ua.okoneba.core.domain.model.SourceTrajectory
import ua.okoneba.core.domain.model.ThreatType
import ua.okoneba.core.network.model.RawTelemetryPacketDto
import ua.okoneba.core.network.model.RawThreatDto

sealed interface ParseResult<out T> {
    data class Success<T>(val value: T) : ParseResult<T>
    data class Failure(val reason: String, val rawPayloadSnippet: String = "") : ParseResult<Nothing>
}

object TelemetryParser {

    fun parsePacket(dto: RawTelemetryPacketDto): ParseResult<List<NormalizedThreat>> {
        if (dto.sourceId.isBlank()) {
            return ParseResult.Failure("Invalid sourceId: cannot be blank")
        }

        val normalizedList = mutableListOf<NormalizedThreat>()

        for (raw in dto.threats) {
            val parsedThreat = parseIndividualThreat(dto.sourceId, raw, dto.stalenessMs)
            if (parsedThreat == null) {
                // Malformed threat within packet - reject entire packet for safety
                return ParseResult.Failure("Malformed threat payload with id: ${raw.id}")
            }
            normalizedList.add(parsedThreat)
        }

        return ParseResult.Success(normalizedList)
    }

    private fun parseIndividualThreat(
        sourceId: String,
        raw: RawThreatDto,
        sourceStalenessMs: Long?
    ): NormalizedThreat? {
        if (raw.id.isBlank()) return null
        if (raw.lat !in -90.0..90.0 || raw.lon !in -180.0..180.0) return null
        if (raw.timestamp <= 0) return null

        val threatType = when (raw.type.uppercase().trim()) {
            "MISSILE", "CRUISE_MISSILE", "CALIBER", "X101" -> ThreatType.MISSILE
            "DRONE", "SHAHEAD", "UAV", "GERAN" -> ThreatType.DRONE
            "AIRCRAFT", "TU95", "MIG31", "SU35" -> ThreatType.AIRCRAFT
            "BALLISTIC", "ISLANDER", "KN23", "S300" -> ThreatType.BALLISTIC
            "KAB", "GUIDED_BOMB", "FAB" -> ThreatType.GUIDED_BOMB
            else -> ThreatType.UNKNOWN
        }

        val trajectory = raw.trajectory?.let { rawTraj ->
            val validPath = rawTraj.path.filter { it.lat in -90.0..90.0 && it.lon in -180.0..180.0 }
                .map { Coordinates(it.lat, it.lon) }

            SourceTrajectory(
                headingDegrees = rawTraj.heading?.takeIf { it in 0.0..360.0 },
                speedKmh = rawTraj.speed?.takeIf { it >= 0.0 },
                altitudeMeters = rawTraj.altitude?.takeIf { it >= 0.0 },
                predictedPath = validPath
            )
        }

        return NormalizedThreat(
            sourceId = sourceId,
            threatId = raw.id,
            type = threatType,
            latitude = raw.lat,
            longitude = raw.lon,
            timestamp = raw.timestamp,
            sourceStalenessMs = sourceStalenessMs,
            trajectory = trajectory,
            rawMetadata = raw.metadata
        )
    }
}
