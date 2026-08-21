package ua.ukrainedrones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertsUaTest {

    @Test
    fun `parseStates keeps only active oblasts`() {
        val body = """
            {"states":[
              {"id":1,"name":"Вінницька область","alert":false,"changed":"2025-11-02T22:07:39Z"},
              {"id":11,"name":"Луганська область","alert":true,"changed":"2023-10-29T18:22:37Z"},
              {"id":14,"name":"Одеська область","alert":true,"changed":"2025-11-04T07:58:05Z"}
            ]}
        """.trimIndent()

        val alerts = AlertsUaClient.parseStates(body)
        assertEquals(2, alerts?.size)
        assertEquals("Луганська область", alerts?.get(0)?.oblast)
        assertEquals("11", alerts?.get(0)?.key)
        assertEquals("Одеська область", alerts?.get(1)?.oblast)
    }

    @Test
    fun `parseStates handles blank name and empty array`() {
        assertEquals(0, AlertsUaClient.parseStates("""{"states":[{"id":1,"name":"","alert":true}]}""")?.size)
        assertEquals(0, AlertsUaClient.parseStates("""{"states":[]}""")?.size)
        assertNull(AlertsUaClient.parseStates("not json"))
    }

    @Test
    fun `mergeAlerts dedupes by oblast with primary winning`() {
        val primary = listOf(OblastAlert("1", "Одеська область", "Одеська область", null))
        val backup = listOf(
            OblastAlert("14", "Одеська область", "Одеська область", null),
            OblastAlert("19", "Харківська область", "Харківська область", null)
        )
        val merged = mergeAlerts(primary, backup)
        assertEquals(2, merged.size)
        assertEquals("1", merged[0].key) // primary kept
        assertEquals("Харківська область", merged[1].oblast)
    }

    @Test
    fun `backup not active while NEPTUN connected`() {
        val st = NeptunState(connected = true, lastFrameAt = System.currentTimeMillis())
        assertFalse(st.backupActive)
        assertEquals(st.neptunAlerts, st.oblastAlerts)
    }

    @Test
    fun `backup active and sourced when disconnected`() {
        val st = NeptunState(
            connected = false,
            backupUp = true,
            neptunAlerts = emptyList(),
            backupAlerts = listOf(OblastAlert("14", "Одеська область", "Одеська область", null))
        )
        assertTrue(st.backupActive)
        assertTrue(st.oblastAlerts.any { it.inOblast("Одеськ") })
        assertEquals(AlertSource.BACKUP, st.alertSourceFor("Одеськ"))
    }

    @Test
    fun `backup tags the alert while the socket is down even if NEPTUN reported it`() {
        // NEPTUN's frozen snapshot still lists the oblast, but only the live backup confirms
        // it — the source tag credits the live source, never a held NEPTUN alert.
        val st = NeptunState(
            connected = false,
            backupUp = true,
            neptunAlerts = listOf(OblastAlert("14", "Одеська область", "Одеська область", null)),
            backupAlerts = listOf(OblastAlert("14", "Одеська область", "Одеська область", null))
        )
        assertEquals(AlertSource.BACKUP, st.alertSourceFor("Одеськ"))
    }

    @Test
    fun `last known NEPTUN alerts are held while both sources are down`() {
        // Backup polled OK earlier, then died: its stale payload must not surface while NEPTUN
        // is down too, but NEPTUN's last-known list is HELD — an outage must never fabricate
        // "alert ended". Held alerts are never source-tagged (no live source confirms them).
        val st = NeptunState(
            connected = false,
            backupUp = false,
            backupLastOkAt = System.currentTimeMillis() - 120_000,
            neptunAlerts = listOf(OblastAlert("14", "Одеська область", "Одеська область", null)),
            backupAlerts = listOf(OblastAlert("99", "Харківська область", "Харківська область", null))
        )
        assertTrue(st.oblastAlerts.any { it.inOblast("Одеськ") })
        assertTrue(st.oblastAlerts.none { it.inOblast("Харківськ") })
        assertNull(st.alertSourceFor("Одеськ"))
        assertNull(st.alertSourceFor("Харківськ"))
    }

    @Test
    fun `stale backup does not merge while NEPTUN is merely silent`() {
        // NEPTUN alive but silent (backupActive) with the backup itself down: only NEPTUN's
        // own alerts count — the stale backup payload never sneaks in.
        val st = NeptunState(
            connected = true,
            backupUp = false,
            lastFrameAt = System.currentTimeMillis() - 70_000,
            neptunAlerts = listOf(OblastAlert("1", "Одеська область", "Одеська область", null)),
            backupAlerts = listOf(OblastAlert("14", "Харківська область", "Харківська область", null))
        )
        assertTrue(st.backupActive)
        assertEquals(listOf("Одеська область"), st.oblastAlerts.map { it.oblast })
        assertEquals(AlertSource.NEPTUN, st.alertSourceFor("Одеськ"))
        assertNull(st.alertSourceFor("Харківськ"))
    }

    @Test
    fun `forceOffline activates backup even while NEPTUN connected`() {
        val st = NeptunState(
            connected = true,
            lastFrameAt = System.currentTimeMillis(),
            forceOffline = true,
            backupUp = true,
            backupAlerts = listOf(OblastAlert("14", "Одеська область", "Одеська область", null))
        )
        assertTrue(st.backupActive)
        assertTrue(st.oblastAlerts.any { it.inOblast("Одеськ") })
        assertEquals(AlertSource.BACKUP, st.alertSourceFor("Одеськ"))
    }

    @Test
    fun `forceOffline with backup down holds last known NEPTUN alerts`() {
        val st = NeptunState(
            connected = true,
            lastFrameAt = System.currentTimeMillis(),
            forceOffline = true,
            backupUp = false,
            neptunAlerts = listOf(OblastAlert("14", "Одеська область", "Одеська область", null)),
            backupAlerts = listOf(OblastAlert("14", "Одеська область", "Одеська область", null))
        )
        assertTrue(st.neptunDown)
        assertTrue(st.oblastAlerts.any { it.inOblast("Одеськ") })
    }

    @Test
    fun `source is NEPTUN and backup inactive while connected`() {
        val st = NeptunState(
            connected = true,
            lastFrameAt = System.currentTimeMillis(),
            neptunAlerts = listOf(OblastAlert("14", "Одеська область", "Одеська область", null)),
            backupAlerts = listOf(OblastAlert("14", "Одеська область", "Одеська область", null))
        )
        assertEquals(AlertSource.NEPTUN, st.alertSourceFor("Одеськ"))
        assertNull(st.alertSourceFor("Харківськ"))
    }

    @Test
    fun `backup offline elapsed is null while healthy`() {
        val st = NeptunState(backupUp = true, backupLastOkAt = System.currentTimeMillis())
        assertNull(st.backupOfflineElapsedSec)
    }

    @Test
    fun `backup offline elapsed counts seconds when down`() {
        val st = NeptunState(backupUp = false, backupLastOkAt = System.currentTimeMillis() - 65_000)
        val sec = st.backupOfflineElapsedSec
        assertTrue(sec != null && sec >= 60)
    }

    @Test
    fun `backup offline elapsed null before first success`() {
        val st = NeptunState(backupUp = false, backupLastOkAt = 0L)
        assertNull(st.backupOfflineElapsedSec)
    }

    @Test
    fun `forceOffline false keeps backup inactive while connected`() {
        val st = NeptunState(connected = true, lastFrameAt = System.currentTimeMillis(), forceOffline = false)
        assertFalse(st.backupActive)
    }

    @Test
    fun `forceOffline marks NEPTUN down and starts its offline timer`() {
        val st = NeptunState(
            connected = true,
            offlineSince = System.currentTimeMillis() - 120_000L,
            lastFrameAt = System.currentTimeMillis(),
            forceOffline = true
        )
        assertTrue(st.neptunDown)
        assertNotNull(st.offlineElapsedSec)
        assertTrue(st.offlineElapsedSec!! >= 120L)
        assertTrue(st.backupActive)
    }

    @Test
    fun `neptunDown is true when disconnected`() {
        val st = NeptunState(connected = false, offlineSince = System.currentTimeMillis() - 60_000L)
        assertTrue(st.neptunDown)
        assertTrue(st.backupActive)
    }

    @Test
    fun `connected without force marks NEPTUN up`() {
        val st = NeptunState(connected = true, lastFrameAt = System.currentTimeMillis())
        assertFalse(st.neptunDown)
    }

    @Test
    fun `backup active when connected but the stream has been silent`() {
        val st = NeptunState(connected = true, lastFrameAt = System.currentTimeMillis() - 70_000)
        assertTrue(st.backupActive)
    }

    @Test
    fun `backup inactive again once a fresh frame arrives`() {
        val st = NeptunState(connected = true, lastFrameAt = System.currentTimeMillis())
        assertFalse(st.backupActive)
    }

    @Test
    fun `backup alerts merge even while NEPTUN is healthy and streaming`() {
        // The core "prize" behavior: an official siren that alerts.com.ua reports before NEPTUN
        // forwards it must ring immediately — both sources are always-on peers.
        val st = NeptunState(
            connected = true,
            lastFrameAt = System.currentTimeMillis(),
            backupUp = true,
            neptunAlerts = listOf(OblastAlert("1", "Одеська область", "Одеська область", null)),
            backupAlerts = listOf(OblastAlert("14", "Харківська область", "Харківська область", null))
        )
        assertFalse(st.backupActive)
        assertTrue(st.oblastAlerts.any { it.inOblast("Одеськ") })
        assertTrue(st.oblastAlerts.any { it.inOblast("Харківськ") })
        assertEquals(AlertSource.NEPTUN, st.alertSourceFor("Одеськ"))
        assertEquals(AlertSource.BACKUP, st.alertSourceFor("Харківськ"))
    }

    @Test
    fun `both sources reporting the same oblast while connected tag BOTH`() {
        val st = NeptunState(
            connected = true,
            lastFrameAt = System.currentTimeMillis(),
            backupUp = true,
            neptunAlerts = listOf(OblastAlert("1", "Одеська область", "Одеська область", null)),
            backupAlerts = listOf(OblastAlert("14", "Одеська область", "Одеська область", null))
        )
        assertEquals(AlertSource.BOTH, st.alertSourceFor("Одеськ"))
    }

    @Test
    fun `dead backup never merges while NEPTUN healthy and streaming`() {
        // Stale-backup gating holds under the always-on merge: a backup that stopped polling
        // must not resurrect its last payload while NEPTUN is fine.
        val st = NeptunState(
            connected = true,
            lastFrameAt = System.currentTimeMillis(),
            backupUp = false,
            neptunAlerts = listOf(OblastAlert("1", "Одеська область", "Одеська область", null)),
            backupAlerts = listOf(OblastAlert("14", "Харківська область", "Харківська область", null))
        )
        assertEquals(listOf("Одеська область"), st.oblastAlerts.map { it.oblast })
        assertEquals(AlertSource.NEPTUN, st.alertSourceFor("Одеськ"))
        assertNull(st.alertSourceFor("Харківськ"))
    }
}
