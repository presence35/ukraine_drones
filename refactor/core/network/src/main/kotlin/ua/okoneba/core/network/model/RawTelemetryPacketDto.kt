package ua.okoneba.core.network.model

data class RawCoordinatesDto(
    val lat: Double,
    val lon: Double
)

data class RawTrajectoryDto(
    val heading: Double? = null,
    val speed: Double? = null,
    val altitude: Double? = null,
    val path: List<RawCoordinatesDto> = emptyList()
)

data class RawThreatDto(
    val id: String,
    val type: String,
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
    val trajectory: RawTrajectoryDto? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class RawTelemetryPacketDto(
    val sourceId: String,
    val sequenceNumber: Long,
    val serverTime: Long,
    val stalenessMs: Long? = null,
    val threats: List<RawThreatDto> = emptyList()
)
