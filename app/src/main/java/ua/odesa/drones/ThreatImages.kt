package ua.odesa.drones

/**
 * Wikimedia Commons photo shown in a threat popup's expandable "more info" section.
 * Images are hotlinked from Commons (Special:FilePath thumbs) so we never host them.
 * Filenames were verified to exist on Commons; a type without an entry shows text only.
 */
object ThreatImages {

    private const val COMMONS_BASE = "https://commons.wikimedia.org/wiki/Special:FilePath/"
    private const val THUMB_WIDTH = 560

    private val FILE_NAMES: Map<ThreatType, String> = mapOf(
        ThreatType.SHAHED to "Shahed-136_mockup_in_Tehran_2022_demonstration.jpg",
        ThreatType.FPV_LOITERING to "ZALA_Lancet_1.jpg",
        ThreatType.CRUISE_MISSILE to "3M-14E_submarine_launched_land_attack_cruise_missile_from_Kalibr-PLE-Club-S_system_02.jpg",
        ThreatType.BALLISTIC to "9K720_Iskander_(SS-26_Stone)_(41253217174).jpg",
        ThreatType.KAB to "KAB-250LG-E_guided_bomb_at_MAKS-2015_01.jpg",
        ThreatType.AVIATION to "2018_Moscow_Victory_Day_Parade_66.jpg",
        ThreatType.RECON to "Orlan-10_UAV_Army-2022_2022-08-20_2287.jpg"
    )

    fun url(type: ThreatType): String? =
        FILE_NAMES[type]?.let { "$COMMONS_BASE$it?width=$THUMB_WIDTH" }
}
