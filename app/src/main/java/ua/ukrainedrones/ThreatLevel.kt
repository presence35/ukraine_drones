package ua.ukrainedrones

import kotlin.math.min

/**
 * Experimental "one number" threat level for the user's current position.
 * Combines per-threat type severity with distance, reliability, source count,
 * raid size, position quality and staleness into a single 0–10 score. This is
 * a rough estimate for the UI only — not an official rating, and never a
 * substitute for official air-raid signals.
 */
object ThreatLevelModel {

    private val BASE_SEVERITY: Map<ThreatType, Double> = mapOf(
        ThreatType.BALLISTIC to 10.0,
        ThreatType.CRUISE_MISSILE to 8.0,
        ThreatType.AVIATION to 7.0,
        ThreatType.SHAHED to 5.0,
        ThreatType.KAB to 4.0,
        ThreatType.UNKNOWN to 4.0,
        ThreatType.FPV_LOITERING to 3.0,
        ThreatType.RECON to 2.0
    )

    private fun reliabilityFactor(r: Reliability): Double = when (r) {
        Reliability.HIGH -> 1.0
        Reliability.MEDIUM -> 0.8
        Reliability.UNKNOWN -> 0.7
        Reliability.LOW -> 0.5
    }

    /** More independent sources → more confidence; single source is baseline. */
    private fun confirmFactor(n: Int): Double = 1.0 + 0.15 * min((n - 1).coerceAtLeast(0), 6)

    /** A raid group is more dangerous than a single object. */
    private fun countFactor(c: Int): Double = 1.0 + 0.1 * min((c - 1).coerceAtLeast(0), 8)

    private fun qualityFactor(t: Threat): Double {
        val base = when (t.positionQuality) {
            "approx" -> 0.85
            "confirmed" -> 1.0
            else -> 0.9
        }
        val u = t.uncertaintyKm ?: return base
        val uncert = when {
            u >= 8.0 -> 0.85
            u <= 1.0 -> 1.0
            else -> 1.0 - 0.15 * ((u - 1.0) / 7.0)
        }
        return base * uncert
    }

    /** Decays as the fix ages toward its per-type staleness window. */
    private fun staleFactor(t: Threat, now: Long): Double {
        val updated = t.updatedAtMillis ?: return 1.0
        val remaining = (1.0 - (now - updated).coerceAtLeast(0) / staleAfterMs(t.type).toDouble())
            .coerceIn(0.0, 1.0)
        return (0.4 + 0.6 * remaining).coerceIn(0.4, 1.0)
    }

    /** Less time to react → higher imminence factor. */
    private fun etaFactor(etaMin: Double?): Double = when {
        etaMin == null -> 0.9
        etaMin <= 1.0 -> 1.0
        etaMin <= 3.0 -> 0.95
        etaMin <= 8.0 -> 0.85
        etaMin <= 15.0 -> 0.75
        else -> 0.7
    }

    /**
     * Score for a single threat relative to the user's position, 0..10.
     * [distKm] is the distance to the user's GPS fix; threats beyond the yellow
     * zone don't contribute at all.
     */
    fun scoreOf(
        t: Threat,
        distKm: Double,
        etaMin: Double?,
        redKm: Int,
        yellowKm: Int,
        now: Long
    ): Double {
        val distanceFactor = when {
            distKm <= redKm -> 1.0
            distKm <= yellowKm -> 0.65
            else -> 0.0
        }
        if (distanceFactor == 0.0) return 0.0
        return (BASE_SEVERITY.getValue(t.type)
            * distanceFactor
            * reliabilityFactor(t.reliability)
            * confirmFactor(t.confirmations)
            * countFactor(t.count)
            * qualityFactor(t)
            * staleFactor(t, now)
            * etaFactor(etaMin))
            .coerceIn(0.0, 10.0)
    }

    /**
     * Aggregate the strongest nearby threats into one 0..10 level with
     * diminishing returns: the top threat dominates, then 2nd and 3rd add less.
     */
    fun overall(scores: List<Double>): Double {
        val sorted = scores.sortedDescending()
        var total = 0.0
        val weights = listOf(1.0, 0.5, 0.25)
        for (i in 0 until minOf(sorted.size, weights.size)) total += sorted[i] * weights[i]
        return total.coerceIn(0.0, 10.0)
    }
}
