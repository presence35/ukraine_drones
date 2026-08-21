package ua.ukrainedrones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSnapshotTest {

    private val params = ZoneParams(slowRedKm = 20, slowYellowKm = 50, fastRedMin = 5, fastYellowMin = 20)
    private val allTypes = ThreatTypeCatalog.INFO.keys
    private val now = 1_700_000_000_000L
    private val focus = LatLng(46.48, 30.73) // Odesa

    private fun state(vararg threats: Threat) =
        NeptunState(threats = threats.associateBy { it.id })

    @Test
    fun `empty state yields zero threats and no zone`() {
        val snap = computeWidgetSnapshot(state(), focus, "odesa", params, allTypes, now)
        assertEquals(0, snap.threatCount)
        assertNull(snap.activeZone)
        assertNull(snap.nearestKm)
        assertFalse(snap.officialAlert)
    }

    @Test
    fun `counts map-enabled non-stale non-resolved threats`() {
        val s = state(
            threat(id = "a", updatedAtMillis = now),                              // active, counts
            threat(id = "b", status = "resolved", updatedAtMillis = now),         // resolved, skipped
            threat(id = "c", updatedAtMillis = now - 301_000L),                   // stale, skipped
            threat(id = "d", areaOnly = true, updatedAtMillis = now),             // areaOnly, not in mapThreats
            threat(id = "e", type = ThreatType.RECON, updatedAtMillis = now)      // counts
        )
        val snap = computeWidgetSnapshot(s, focus, "odesa", params, allTypes, now)
        assertEquals(2, snap.threatCount)
    }

    @Test
    fun `nearest distance is the closest live threat to the focus`() {
        val s = state(
            threat(id = "near", lat = 46.48, lon = 30.80, updatedAtMillis = now),    // ~5.6 km
            threat(id = "far", lat = 47.0, lon = 31.0, updatedAtMillis = now)         // ~70 km
        )
        val snap = computeWidgetSnapshot(s, focus, "odesa", params, allTypes, now)
        // nearest is capped+rounded to an int km
        assertTrue(snap.nearestKm!! < 10.0)
        assertTrue(snap.nearestKm!! > 0.0)
    }

    @Test
    fun `inner threat yields inner zone`() {
        val s = state(threat(id = "a", lat = 46.49, lon = 30.74, updatedAtMillis = now))
        val snap = computeWidgetSnapshot(s, focus, "odesa", params, allTypes, now)
        assertEquals(ThreatZone.INNER, snap.activeZone)
    }

    @Test
    fun `official alert matches the focus oblast token`() {
        val alert = OblastAlert(key = "k", name = "Одеська область", oblast = "Одеська", since = "x")
        val s = NeptunState(neptunAlerts = listOf(alert))
        assertTrue(computeWidgetSnapshot(s, focus, "odesa", params, allTypes, now).officialAlert)
        assertFalse(computeWidgetSnapshot(s, focus, "kyivska", params, allTypes, now).officialAlert)
    }

    @Test
    fun `source flags reflect connectivity`() {
        val online = computeWidgetSnapshot(NeptunState(connected = true), focus, null, params, allTypes, now)
        assertTrue(online.sourceOnline)
        assertFalse(online.sourceBackup)

        val backup = computeWidgetSnapshot(
            NeptunState(connected = false, backupUp = true),
            focus, null, params, allTypes, now
        )
        assertFalse(backup.sourceOnline)
        assertTrue(backup.sourceBackup)
    }
}
