package ua.ukrainedrones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val st = NeptunState(connected = true, lastAlertAt = System.currentTimeMillis())
        assertFalse(st.backupActive)
        assertEquals(st.neptunAlerts, st.oblastAlerts)
    }

    @Test
    fun `backup active and sourced when disconnected`() {
        val st = NeptunState(
            connected = false,
            neptunAlerts = emptyList(),
            backupAlerts = listOf(OblastAlert("14", "Одеська область", "Одеська область", null))
        )
        assertTrue(st.backupActive)
        assertTrue(st.oblastAlerts.any { it.inOblast("Одеськ") })
        assertEquals(AlertSource.BACKUP, st.alertSourceFor("Одеськ"))
    }

    @Test
    fun `source is BOTH when both report the oblast`() {
        val st = NeptunState(
            connected = false,
            neptunAlerts = listOf(OblastAlert("14", "Одеська область", "Одеська область", null)),
            backupAlerts = listOf(OblastAlert("14", "Одеська область", "Одеська область", null))
        )
        assertEquals(AlertSource.BOTH, st.alertSourceFor("Одеськ"))
    }

    @Test
    fun `source is NEPTUN and backup inactive while connected`() {
        val st = NeptunState(
            connected = true,
            lastAlertAt = System.currentTimeMillis(),
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
    fun `forceOffline activates backup even while NEPTUN connected`() {
        val st = NeptunState(
            connected = true,
            lastAlertAt = System.currentTimeMillis(),
            forceOffline = true,
            backupAlerts = listOf(OblastAlert("14", "Одеська область", "Одеська область", null))
        )
        assertTrue(st.backupActive)
        assertTrue(st.oblastAlerts.any { it.inOblast("Одеськ") })
        assertEquals(AlertSource.BACKUP, st.alertSourceFor("Одеськ"))
    }

    @Test
    fun `forceOffline false keeps backup inactive while connected`() {
        val st = NeptunState(connected = true, lastAlertAt = System.currentTimeMillis(), forceOffline = false)
        assertFalse(st.backupActive)
    }
}
