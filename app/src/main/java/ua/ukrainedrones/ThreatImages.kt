package ua.ukrainedrones

import androidx.annotation.DrawableRes

/**
 * Reference photos for the expanded threat card in Settings. Types we have photos for are
 * bundled in the app as `drawable-nodpi/threat_*.webp` (so they load offline, instantly);
 * the rest are hotlinked from Wikimedia Commons, so we never host them. Map markers and
 * card icons use the vector drawables.
 */
object ThreatImages {

    private const val COMMONS_BASE = "https://commons.wikimedia.org/wiki/Special:FilePath/"
    private const val THUMB_WIDTH = 560

    /** Bundled photos, referenced from `app/src/main/res/drawable-nodpi/`. */
    private val BUNDLED: Map<ThreatType, Int> = mapOf(
        ThreatType.SHAHED to R.drawable.threat_shahed,
        ThreatType.UNKNOWN to R.drawable.threat_unknown,
        ThreatType.BALLISTIC to R.drawable.threat_ballistic
    )

    private val FILE_NAMES: Map<ThreatType, String> = mapOf(
        ThreatType.FPV_LOITERING to "ZALA_Lancet_1.jpg",
        ThreatType.CRUISE_MISSILE to "3M-14E_submarine_launched_land_attack_cruise_missile_from_Kalibr-PLE-Club-S_system_02.jpg",
        ThreatType.BALLISTIC to "9K720_Iskander_(SS-26_Stone)_(41253217174).jpg",
        ThreatType.KAB to "KAB-250LG-E_guided_bomb_at_MAKS-2015_01.jpg",
        ThreatType.AVIATION to "2018_Moscow_Victory_Day_Parade_66.jpg",
        ThreatType.RECON to "Orlan-10_UAV_Army-2022_2022-08-20_2287.jpg"
    )

    /** Bundled drawable for types with a local photo, else null. */
    fun drawableRes(type: ThreatType): Int? = BUNDLED[type]

    fun url(type: ThreatType): String? =
        FILE_NAMES[type]?.let { "$COMMONS_BASE$it?width=$THUMB_WIDTH" }
}