package ua.ukrainedrones

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Night-mode schedule window. Times are minutes since midnight (0–1439). */
data class NightConfig(
    val enabled: Boolean,
    val startMin: Int,
    val endMin: Int
)

/** Night-mode zone values: custom thresholds + armed bells, applied while the window is active. */
data class NightZones(
    val slowRedKm: Int,
    val slowYellowKm: Int,
    val fastRedMin: Int,
    val fastYellowMin: Int,
    val redArmed: Boolean,
    val yellowArmed: Boolean
)

/**
 * True when [nowMillis] falls inside the night window. Overnight windows (start > end,
 * e.g. 22:00→07:00) wrap past midnight. start == end (or disabled) means never active.
 * Shared by MainViewModel (UI) and AlertService (notifications) — the mirror rule.
 */
fun isNightActive(config: NightConfig, nowMillis: Long): Boolean {
    if (!config.enabled || config.startMin == config.endMin) return false
    val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        .toLocalTime()
    val minute = now.hour * 60 + now.minute
    return if (config.startMin < config.endMin) {
        minute in config.startMin until config.endMin
    } else {
        minute >= config.startMin || minute < config.endMin
    }
}

/** The active zone thresholds: night custom values while the window is active, else the day ones. */
fun effectiveZoneParams(
    day: ZoneParams,
    night: NightZones,
    useNightZones: Boolean,
    nightActive: Boolean
): ZoneParams = if (nightActive && useNightZones) {
    ZoneParams(night.slowRedKm, night.slowYellowKm, night.fastRedMin, night.fastYellowMin)
} else {
    day
}

/** The active armed bells: night custom values while the window is active, else the day ones. */
fun effectiveArmed(
    dayRed: Boolean,
    dayYellow: Boolean,
    night: NightZones,
    useNightZones: Boolean,
    nightActive: Boolean
): Pair<Boolean, Boolean> = if (nightActive && useNightZones) {
    night.redArmed to night.yellowArmed
} else {
    dayRed to dayYellow
}