package ua.okoneba.feature.alerts.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.okoneba.core.domain.model.UserLocationState
import ua.okoneba.feature.alerts.location.LocationSanityChecker

class LocationSanityTest {

    @Test
    fun `test location sanity validation and impossible jump filtering`() {
        val checker = LocationSanityChecker()
        val t0 = 1_000_000L

        // 1. Initial valid location in Odesa
        val loc1 = checker.processLocationUpdate(
            latitude = 46.4825,
            longitude = 30.7233,
            accuracyMeters = 50f,
            timestamp = t0
        )
        assertTrue(loc1 is UserLocationState.Valid)
        assertEquals(46.4825, (loc1 as UserLocationState.Valid).latitude, 0.0001)

        // 2. Normal terrestrial movement 5 seconds later (100 meters away)
        val loc2 = checker.processLocationUpdate(
            latitude = 46.4834,
            longitude = 30.7233,
            accuracyMeters = 40f,
            timestamp = t0 + 5000L
        )
        assertTrue(loc2 is UserLocationState.Valid)
        assertTrue(checker.isSignificantMovement(loc2 as UserLocationState.Valid, loc1))

        // 3. Tiny GPS jitter (< 5 meters) 1 second later -> isSignificantMovement is false
        val locJitter = checker.processLocationUpdate(
            latitude = 46.48342,
            longitude = 30.72331,
            accuracyMeters = 30f,
            timestamp = t0 + 6000L
        )
        assertTrue(locJitter is UserLocationState.Valid)
        assertFalse(checker.isSignificantMovement(locJitter as UserLocationState.Valid, loc2))

        // 4. Absurd impossible jump: 1000 km to Kharkiv 3 seconds later
        val locJump = checker.processLocationUpdate(
            latitude = 49.9935,
            longitude = 36.2304,
            accuracyMeters = 20f,
            timestamp = t0 + 9000L
        )
        assertTrue(locJump is UserLocationState.Suspect)
        val suspect = locJump as UserLocationState.Suspect
        assertNotNull(suspect.previousValid)
        assertEquals(46.48342, suspect.previousValid!!.latitude, 0.0001)

        // Invariant 15: getUsableCoordinates retains the previous valid location for evaluation
        val usableCoords = suspect.getUsableCoordinates()
        assertNotNull(usableCoords)
        assertEquals(46.48342, usableCoords!!.latitude, 0.0001)

        // 5. Out of bounds coordinates rejected cleanly
        val locInvalid = checker.processLocationUpdate(
            latitude = 95.0,
            longitude = 200.0,
            accuracyMeters = 10f,
            timestamp = t0 + 12000L
        )
        assertTrue(locInvalid is UserLocationState.Unlocated)
    }
}
