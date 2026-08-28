package ua.ukrainedrones

import java.time.Instant

/** Live reconnect state for the current offline episode — transient, never persisted. */
data class ConnRetryState(
    val attempt: Int,
    val delayMs: Long,
    val nextAtMs: Long,
    val networkValidated: Boolean
)

/** Kind of a line in the current offline episode's transient reconnect log. */
enum class ConnEventKind {
    CONNECTION_LOST, RETRY_SCHEDULED, NO_NETWORK,
    MILESTONE_3, MILESTONE_5, MILESTONE_6, MILESTONE_10, MILESTONE_20,
    GAVE_UP, PAUSED
}

/** One line of the current offline episode's reconnect log (the in-between only). */
data class ConnEvent(
    val atMillis: Long,
    val kind: ConnEventKind,
    val attempt: Int? = null,
    val delayMs: Long? = null
) {
    fun label(s: Strings.StringSet): String = when (kind) {
        ConnEventKind.CONNECTION_LOST -> s.connEventLost
        ConnEventKind.RETRY_SCHEDULED -> String.format(s.connEventRetry, (delayMs ?: 0L) / 1000, attempt ?: 0)
        ConnEventKind.NO_NETWORK -> s.connEventNoNetwork
        ConnEventKind.MILESTONE_3 -> s.connEventMin3
        ConnEventKind.MILESTONE_5 -> s.connEventMin5
        ConnEventKind.MILESTONE_6 -> s.connEventMin6
        ConnEventKind.MILESTONE_10 -> s.connEventMin10
        ConnEventKind.MILESTONE_20 -> s.connEventMin20
        ConnEventKind.GAVE_UP -> s.connEventGaveUp
        ConnEventKind.PAUSED -> s.connEventPaused
    }
}

/** A threat just disappeared from the server feed (resolved or a remove frame) — drives the map death animation. */
data class ThreatRemoved(
    val id: String,
    val lat: Double,
    val lon: Double,
    val type: ThreatType,
    val courseDeg: Double = 0.0,
    val region: String? = null,
    val district: String? = null,
    val locality: String? = null
)

object NeptunClient {
    const val OFFLINE_GRACE_MS = 5_000L
    const val DEGRADED_STALE_MS = 30_000L
    const val USER_SHOT_GRACE_MS = 3_000L
    const val NEPTUN_DOMAIN = "neptun.in.ua"
    internal const val NEPTUN_SITE_URL = "https://$NEPTUN_DOMAIN/"
}

internal fun buildTestMig(id: String, now: Long, lat: Double, lon: Double): Threat {
    val nowIso = Instant.ofEpochMilli(now).toString()
    return Threat(
        id = id,
        type = ThreatType.AVIATION,
        title = "",
        region = null,
        district = null,
        locality = null,
        lat = lat,
        lon = lon,
        heading = null,
        bearingDeg = null,
        status = "active",
        advisory = false,
        areaOnly = false,
        confirmations = 2,
        reliability = Reliability.HIGH,
        count = 0,
        explanationShort = null,
        speedKmh = null,
        uncertaintyKm = null,
        positionQuality = "confirmed",
        confirmedAt = nowIso,
        confirmedAtMillis = now,
        updatedAt = nowIso,
        updatedAtMillis = now
    )
}
