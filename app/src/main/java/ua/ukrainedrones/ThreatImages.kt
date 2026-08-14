package ua.ukrainedrones

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Photos shown in the app. Types we host ourselves are served from our update server's
 * /images/ folder (files staged in the repo under server/images/ and uploaded manually).
 * Everything else is hotlinked from Wikimedia Commons, so we never host it.
 *
 * Fetched photos live in the OS cache dir (context.cacheDir) — the "proper" cache folder:
 * Android can evict it under storage pressure, and it isn't counted as app user data.
 * A small in-memory bitmap keeps the map markers and icons fast between recompositions.
 */
object ThreatImages {

    private const val COMMONS_BASE = "https://commons.wikimedia.org/wiki/Special:FilePath/"
    private const val SELF_HOSTED_BASE = "https://odesaplay.com.ua/other_apps/ukrainedrones/images/"
    private const val THUMB_WIDTH = 560
    private const val DISK_CACHE_MAX_BYTES = 3L * 1024 * 1024

    /** Files on our own server, under <SELF_HOSTED_BASE>. Add a mapping as you drop files in. */
    private val SELF_HOSTED: Map<ThreatType, String> = mapOf(
        ThreatType.SHAHED to "shahed.webp",
        ThreatType.UNKNOWN to "unknown.webp"
    )

    private val FILE_NAMES: Map<ThreatType, String> = mapOf(
        ThreatType.FPV_LOITERING to "ZALA_Lancet_1.jpg",
        ThreatType.CRUISE_MISSILE to "3M-14E_submarine_launched_land_attack_cruise_missile_from_Kalibr-PLE-Club-S_system_02.jpg",
        ThreatType.BALLISTIC to "9K720_Iskander_(SS-26_Stone)_(41253217174).jpg",
        ThreatType.KAB to "KAB-250LG-E_guided_bomb_at_MAKS-2015_01.jpg",
        ThreatType.AVIATION to "2018_Moscow_Victory_Day_Parade_66.jpg",
        ThreatType.RECON to "Orlan-10_UAV_Army-2022_2022-08-20_2287.jpg"
    )

    private val cache = ConcurrentHashMap<ThreatType, Bitmap>()

    /** Bumped whenever a self-hosted photo finishes loading, so UI reading it recomposes. */
    val revision = mutableIntStateOf(0)

    /** Lazily built Coil loader with an explicit disk cache under the OS cache dir. */
    private var loader: ImageLoader? = null

    private fun loader(context: Context): ImageLoader =
        loader ?: ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.1).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "threat_photos"))
                    .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                    .build()
            }
            .build()
            .also {
                loader = it
                Coil.setImageLoader(it)
            }

    fun url(type: ThreatType): String? =
        SELF_HOSTED[type]?.let { SELF_HOSTED_BASE + it }
            ?: FILE_NAMES[type]?.let { "$COMMONS_BASE$it?width=$THUMB_WIDTH" }

    fun cachedBitmap(type: ThreatType): Bitmap? = cache[type]

    /** Fetches the self-hosted photo into the disk cache once; UI falls back to an icon until it's in. */
    fun ensureLoaded(context: Context, type: ThreatType) {
        val u = SELF_HOSTED[type]?.let { SELF_HOSTED_BASE + it } ?: return
        if (cache.containsKey(type)) return
        val request = ImageRequest.Builder(context)
            .data(u)
            .allowHardware(false)
            .target(
                onSuccess = { drawable ->
                    val bmp = (drawable as? BitmapDrawable)?.bitmap
                    if (bmp != null) {
                        cache[type] = bmp
                        revision.intValue++
                    }
                }
            )
            .build()
        loader(context).enqueue(request)
    }
}

/** The self-hosted photo for [type] once cached, triggering recomposition when it arrives. */
@Composable
fun rememberThreatPhoto(type: ThreatType): Bitmap? {
    val context = LocalContext.current
    val rev = ThreatImages.revision.intValue
    LaunchedEffect(type) { ThreatImages.ensureLoaded(context, type) }
    return ThreatImages.cachedBitmap(type)
}

/**
 * Threat icon that shows the self-hosted photo when it's cached, falling back to the
 * [vectorRes] vector icon (same look as the other threat types) until then — and offline.
 */
@Composable
fun ThreatTypeIcon(
    type: ThreatType,
    vectorRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val bmp = rememberThreatPhoto(type)
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = contentDescription,
            colorFilter = if (tint != Color.Unspecified) ColorFilter.tint(tint) else null,
            modifier = modifier
        )
    } else {
        Icon(
            painter = painterResource(vectorRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier
        )
    }
}
