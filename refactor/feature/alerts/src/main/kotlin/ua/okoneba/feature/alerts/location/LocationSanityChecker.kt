package ua.okoneba.feature.alerts.location

import ua.okoneba.core.domain.engine.GeoUtils
import ua.okoneba.core.domain.model.Coordinates
import ua.okoneba.core.domain.model.UserLocationState

class LocationSanityChecker(
    private val maxSpeedKmh: Double = MAX_REALISTIC_SPEED_KMH,
    private val minMovementThresholdMeters: Double = MIN_MOVEMENT_THRESHOLD_METERS
) {
    companion object {
        const val MAX_REALISTIC_SPEED_KMH = 1200.0 // Supersonic/absurd terrestrial speed threshold
        const val MIN_MOVEMENT_THRESHOLD_METERS = 25.0 // Sub-threshold GPS noise filter
        const val BASELINE_ACCURACY_METERS = 100.0f
        const val MAX_TRUSTED_ACCURACY_METERS = 2000.0f
    }

    private var lastValidLocation: UserLocationState.Valid? = null

    @Synchronized
    fun processLocationUpdate(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        timestamp: Long = System.currentTimeMillis()
    ): UserLocationState {
        // Basic boundary validation
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return UserLocationState.Unlocated("Coordinates out of physical bounds: ($latitude, $longitude)")
        }

        if (accuracyMeters > MAX_TRUSTED_ACCURACY_METERS) {
            return UserLocationState.Unlocated("Location accuracy is too low: ${accuracyMeters}m > ${MAX_TRUSTED_ACCURACY_METERS}m")
        }

        val previous = lastValidLocation

        if (previous == null) {
            val valid = UserLocationState.Valid(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                timestamp = timestamp
            )
            lastValidLocation = valid
            return valid
        }

        val distanceKm = GeoUtils.haversineDistanceKm(
            lat1 = previous.latitude,
            lon1 = previous.longitude,
            lat2 = latitude,
            lon2 = longitude
        )
        val distanceMeters = distanceKm * 1000.0
        val timeDiffSeconds = ((timestamp - previous.timestamp) / 1000.0).coerceAtLeast(0.1)
        val speedKmh = (distanceKm / timeDiffSeconds) * 3600.0

        // Absurd jump check (e.g. 1000 km in a few seconds)
        if (speedKmh > maxSpeedKmh && distanceKm > 50.0) {
            return UserLocationState.Suspect(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                timestamp = timestamp,
                reason = "Impossible location jump: ${distanceKm.toInt()} km in ${timeDiffSeconds.toInt()}s (${speedKmh.toInt()} km/h)",
                previousValid = previous
            )
        }

        // Location is valid
        val valid = UserLocationState.Valid(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            timestamp = timestamp
        )
        lastValidLocation = valid
        return valid
    }

    /**
     * Determines whether a location update should trigger a full geographic recalculation
     * or be conflated as tiny sub-threshold GPS jitter.
     */
    fun isSignificantMovement(
        current: UserLocationState.Valid,
        previous: UserLocationState.Valid?
    ): Boolean {
        if (previous == null) return true
        val distanceKm = GeoUtils.haversineDistanceKm(
            lat1 = previous.latitude,
            lon1 = previous.longitude,
            lat2 = current.latitude,
            lon2 = current.longitude
        )
        return (distanceKm * 1000.0) >= minMovementThresholdMeters
    }

    fun setInitialKnownLocation(valid: UserLocationState.Valid) {
        lastValidLocation = valid
    }
}
