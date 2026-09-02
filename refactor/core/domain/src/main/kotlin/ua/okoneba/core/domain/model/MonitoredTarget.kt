package ua.okoneba.core.domain.model

sealed interface UserLocationState {
    data class Valid(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val timestamp: Long
    ) : UserLocationState

    data class Suspect(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val timestamp: Long,
        val reason: String,
        val previousValid: Valid?
    ) : UserLocationState

    data class Stale(
        val lastKnown: Valid,
        val stalenessDurationMs: Long
    ) : UserLocationState

    data class Unlocated(
        val reason: String
    ) : UserLocationState

    fun getUsableCoordinates(): Coordinates? = when (this) {
        is Valid -> Coordinates(latitude, longitude)
        is Suspect -> previousValid?.let { Coordinates(it.latitude, it.longitude) }
        is Stale -> Coordinates(lastKnown.latitude, lastKnown.longitude)
        is Unlocated -> null
    }
}

sealed interface MonitoredTarget {
    val targetId: String

    data class FollowMe(
        override val targetId: String = TARGET_ID_FOLLOW_ME,
        val locationState: UserLocationState
    ) : MonitoredTarget {
        companion object {
            const val TARGET_ID_FOLLOW_ME = "follow_me_primary"
        }
    }

    data class Pinned(
        override val targetId: String,
        val latitude: Double,
        val longitude: Double,
        val label: String
    ) : MonitoredTarget
}
