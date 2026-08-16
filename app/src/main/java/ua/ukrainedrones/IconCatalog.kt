package ua.ukrainedrones

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/**
 * Single source of truth for which drawable represents a threat type in which icon set.
 * The classic set is the monochrome vector set; the photo set is the bundled direction-
 * oriented photos (see `drawable-nodpi/threat_photo_*.png`).
 */
object IconCatalog {

    /** Classic vector icon for [type]. */
    fun classicRes(type: ThreatType): Int = when (type) {
        ThreatType.SHAHED -> R.drawable.shahed
        ThreatType.FPV_LOITERING -> R.drawable.ic_threat_fpv
        ThreatType.CRUISE_MISSILE -> R.drawable.ic_threat_cruise
        ThreatType.BALLISTIC -> R.drawable.ic_threat_ballistic
        ThreatType.KAB -> R.drawable.ic_threat_kab
        ThreatType.AVIATION -> R.drawable.ic_threat_aviation
        ThreatType.RECON -> R.drawable.ic_threat_recon
        ThreatType.UNKNOWN -> R.drawable.ic_threat_unknown
    }

    /** Photo icon for [type], or the classic vector when no photo is bundled (UNKNOWN). */
    fun res(type: ThreatType, set: ThreatIconSet): Int = when (set) {
        ThreatIconSet.CLASSIC -> classicRes(type)
        ThreatIconSet.PHOTO -> photoRes(type) ?: classicRes(type)
    }

    internal fun photoRes(type: ThreatType): Int? = when (type) {
        ThreatType.SHAHED -> R.drawable.threat_photo_shahed
        ThreatType.FPV_LOITERING -> R.drawable.threat_photo_fpv
        ThreatType.CRUISE_MISSILE -> R.drawable.threat_photo_cruise
        ThreatType.BALLISTIC -> R.drawable.threat_photo_ballistic
        ThreatType.KAB -> R.drawable.threat_photo_kab
        ThreatType.AVIATION -> R.drawable.threat_photo_aviation
        ThreatType.RECON -> R.drawable.threat_photo_recon
        ThreatType.UNKNOWN -> null
    }

    /**
     * Direction each photo's subject faces as baked into the image, in degrees clockwise
     * from north/up (the filename suffix, e.g. "top_right" = 45°). Map markers rotate the
     * icon by `courseDeg - base` so the subject points along its true course.
     */
    fun photoBaseDeg(type: ThreatType): Float = when (type) {
        ThreatType.BALLISTIC -> 0f      // _top
        ThreatType.CRUISE_MISSILE -> 45f // _top_right
        ThreatType.FPV_LOITERING -> 180f // _bottom_middle
        ThreatType.KAB -> 225f           // _bottom_left
        ThreatType.AVIATION -> 270f      // _left
        ThreatType.RECON -> 225f         // _bottom_left
        ThreatType.SHAHED -> 0f          // _top_middle
        ThreatType.UNKNOWN -> 0f
    }
}

/**
 * Threat icon that letterboxes the photo set inside its square slot (the vector set draws
 * as-is). The photo assets are wide/short or narrow/tall, so they must keep their aspect
 * ratio instead of stretching into the box like `Icon` would.
 */
@Composable
fun ThreatIcon(
    type: ThreatType,
    set: ThreatIconSet,
    size: Dp,
    tint: Color = Color.Unspecified,
    contentDescription: String? = null
) {
    if (set == ThreatIconSet.CLASSIC || IconCatalog.photoRes(type) == null) {
        Icon(
            painter = painterResource(id = IconCatalog.res(type, set)),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size)
        )
    } else {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = IconCatalog.res(type, set)),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}