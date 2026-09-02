package ua.okoneba.core.domain.model

enum class ThreatType {
    MISSILE,
    DRONE,
    AIRCRAFT,
    BALLISTIC,
    GUIDED_BOMB,
    UNKNOWN
}

data class Coordinates(
    val latitude: Double,
    val longitude: Double
)

data class SourceTrajectory(
    val headingDegrees: Double? = null,
    val speedKmh: Double? = null,
    val altitudeMeters: Double? = null,
    val predictedPath: List<Coordinates> = emptyList()
)

data class NormalizedThreat(
    val sourceId: String,
    val threatId: String,
    val type: ThreatType,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val sourceStalenessMs: Long? = null,
    val trajectory: SourceTrajectory? = null,
    val rawMetadata: Map<String, String> = emptyMap()
) {
    val identityKey: String
        get() = "$sourceId:$threatId"
}
