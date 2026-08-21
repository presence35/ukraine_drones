package ua.ukrainedrones

import kotlin.math.roundToInt

/**
 * A lightweight, serializable snapshot of the threat state for the home-screen widget.
 *
 * Pure and deterministic — computed from shared domain functions only, never by the widget
 * itself. The widget simply renders whatever it is handed, so the launcher process can never
 * re-derive or drift from the app's zone/alert logic (mirror rule: see ARCHITECTURE.md).
 */
data class WidgetSnapshot(
    val threatCount: Int = 0,
    val activeZone: ThreatZone? = null,
    val nearestKm: Double? = null,
    val officialAlert: Boolean = false,
    val sourceOnline: Boolean = false,
    val sourceBackup: Boolean = false,
    val updatedAtMs: Long = 0L
) {
    companion object {
        /** Max distance (km) at which a threat is still shown as a rounded nearest value. */
        const val NEAREST_CAP_KM = 500.0
    }
}

/**
 * Computes the widget snapshot from the shared domain state. Mirrors the footer-strip
 * semantics of the main UI: counts non-stale, non-resolved, map-enabled threats; the nearest
 * distance and zone derive from the focus point via [ThreatEvaluator]; the official-alert flag
 * matches the focus oblast via [focusAttribution] (majors-only, same as the app). No decision
 * logic lives in the widget layer.
 */
fun computeWidgetSnapshot(
    neptun: NeptunState,
    focus: LatLng?,
    token: String?,
    params: ZoneParams,
    mapEnabled: Set<ThreatType>,
    now: Long = System.currentTimeMillis()
): WidgetSnapshot {
    val eval = ThreatEvaluator.evaluate(
        neptun = neptun,
        params = params,
        focusLocation = focus,
        mapEnabledTypes = mapEnabled,
        now = now
    )

    var count = 0
    var nearestKm: Double? = null
    for (t in eval.mapThreats) {
        if (t.isStale(now)) continue
        count++
        if (focus != null) {
            val d = distanceMeters(focus.lat, focus.lon, t.lat, t.lon) / 1000.0
            if (nearestKm == null || d < nearestKm) nearestKm = d
        }
    }
    nearestKm = nearestKm?.let { it.coerceAtMost(WidgetSnapshot.NEAREST_CAP_KM).roundToInt().toDouble() }

    val officialAlert = token != null && neptun.oblastAlerts.any { it.inOblast(token) }

    return WidgetSnapshot(
        threatCount = count,
        activeZone = eval.activeZone,
        nearestKm = nearestKm,
        officialAlert = officialAlert,
        sourceOnline = neptun.connected,
        sourceBackup = neptun.backupUp && neptun.backupActive,
        updatedAtMs = now
    )
}
