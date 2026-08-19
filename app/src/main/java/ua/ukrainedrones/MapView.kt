package ua.ukrainedrones

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.api.IGeoPoint
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.TileSystem
import org.osmdroid.util.TileSystemWebMercator
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal val DARK_TILE_SOURCE = XYTileSource(
    "CartoDB_DarkNoLabels", 0, 17, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_nolabels/",
        "https://b.basemaps.cartocdn.com/dark_nolabels/",
        "https://c.basemaps.cartocdn.com/dark_nolabels/",
        "https://d.basemaps.cartocdn.com/dark_nolabels/"
    )
)

/** Odesa city centre — fallback camera target before the first GPS fix. */
private val DEFAULT_CENTER = GeoPoint(46.4832, 30.7346)

/** Ukraine (incl. Crimea) plus a ~0.5° margin — the map can't pan past this. */
private val UA_VIEW_LIMITS = BoundingBox(52.7, 40.6, 43.9, 21.7)

private val tileSystem = TileSystemWebMercator()

/** Bounding box that fits a zone circle centred on `center`, with a 5% margin. */
private fun zoneBoundingBox(center: IGeoPoint, radiusKm: Double): BoundingBox {
    val marginKm = radiusKm * 1.05
    val dLat = marginKm * 1000.0 / 110_574.0
    val dLon = marginKm * 1000.0 /
        (111_320.0 * cos(Math.toRadians(center.latitude)).coerceAtLeast(0.01))
    return BoundingBox(
        center.latitude + dLat,
        center.longitude + dLon,
        center.latitude - dLat,
        center.longitude - dLon
    )
}

private fun StringBuilder.appendThreatKey(t: Threat, now: Long) {
    // Identity + lifecycle only. Continuously-changing fields (lat/lon/courseDeg) are
    // deliberately excluded: they churn on nearly every WebSocket frame during an alert,
    // defeating the key's whole purpose (avoid clears + full rebuilds). Position smoothing
    // and course/staleness rendering happen in-place in the 1s marker loop instead.
    append(t.id).append('@').append(t.status).append('@')
    append(if (t.isStale(now)) 'S' else 'L').append(';')
}

/**
 * Marker drawable for a threat in the given icon set. The vector set is used at its intrinsic
 * size; the shahed.webp photo and the whole photo set are larger, so they're scaled down to a
 * marker-sized bitmap.
 */
private fun threatIcon(context: Context, type: ThreatType, iconSet: ThreatIconSet): Drawable {
    val res = IconCatalog.res(type, iconSet)
    if (iconSet == ThreatIconSet.CLASSIC && type != ThreatType.SHAHED) {
        return ContextCompat.getDrawable(context, res)!!
    }
    val src = ContextCompat.getDrawable(context, res)!!
    val targetW = (32 * context.resources.displayMetrics.density).toInt()
    val iw = src.intrinsicWidth.coerceAtLeast(1)
    val ih = src.intrinsicHeight.coerceAtLeast(1)
    val w = targetW
    val h = (ih.toFloat() * targetW / iw).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    src.setBounds(0, 0, w, h)
    src.draw(canvas)
    return BitmapDrawable(context.resources, bmp)
}

/** Same marker icon, rendered `scale`× larger — used to grow the selected threat's icon. */
private fun scaledThreatIcon(
    context: Context,
    type: ThreatType,
    iconSet: ThreatIconSet,
    scale: Float
): Drawable {
    val src = ContextCompat.getDrawable(context, IconCatalog.res(type, iconSet))!!
    val density = context.resources.displayMetrics.density
    val targetW = (32 * density * scale).toInt().coerceAtLeast(2)
    val iw = src.intrinsicWidth.coerceAtLeast(1)
    val ih = src.intrinsicHeight.coerceAtLeast(1)
    val w = targetW
    val h = (ih.toFloat() * targetW / iw).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    src.setBounds(0, 0, w, h)
    src.draw(canvas)
    return BitmapDrawable(context.resources, bmp)
}

/** Threat marker icon at the current zoom-derived scale ([scale] 1.0 = plain [threatIcon]). */
private fun threatIconFor(
    context: Context,
    type: ThreatType,
    iconSet: ThreatIconSet,
    scale: Float
): Drawable =
    if (scale <= 1.001f) threatIcon(context, type, iconSet)
    else scaledThreatIcon(context, type, iconSet, scale)

/** Icon growth from map zoom: icons keep pace with the map's magnification as you zoom in
 *  (the threat visibly grows), clamped so it never balloons. Flat at 1.0× up to zoom 10,
 *  2.0× at zoom 12, capped at 3.0× (reached around zoom 13.2) and flat beyond. */
private fun zoomIconScale(zoom: Double): Float =
    minOf(3.0, 2.0.pow(((zoom - 10.0) * 0.5).coerceAtLeast(0.0))).toFloat()

private fun zoneColor(zone: ThreatZone?): Int = when (zone) {
    ThreatZone.INNER -> Color.rgb(255, 82, 82)
    ThreatZone.OUTER -> Color.rgb(255, 215, 64)
    null -> Color.rgb(158, 158, 158)
}

/** Classic "blue glowing dot" used as the GPS location icon. */
private fun gpsDotBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val coreR = 7f * density
    val glowR = coreR * 2.8f
    val size = (glowR * 2).toInt().coerceAtLeast(2)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f
    val glow = Paint().apply {
        shader = RadialGradient(
            cx, cy, glowR,
            intArrayOf(Color.argb(120, 33, 150, 243), Color.argb(0, 33, 150, 243)),
            floatArrayOf(0.45f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(cx, cy, glowR, glow)
    canvas.drawCircle(cx, cy, coreR, Paint().apply {
        isAntiAlias = true
        color = Color.rgb(33, 150, 243)
    })
    canvas.drawCircle(cx, cy, coreR * 0.55f, Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeWidth = 1.5f * density
        color = Color.WHITE
    })
    return bmp
}

/** Map pin with the tip at the bottom centre — anchors the pinned city precisely. */
private fun pinBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val w = (30 * density).toInt()
    val h = (42 * density).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val path = Path().apply {
        moveTo(w / 2f, h.toFloat())
        cubicTo(w * 0.24f, h * 0.62f, 0f, h * 0.38f, 0f, h * 0.30f)
        cubicTo(0f, h * 0.08f, w * 0.22f, 0f, w / 2f, 0f)
        cubicTo(w * 0.78f, 0f, w.toFloat(), h * 0.08f, w.toFloat(), h * 0.30f)
        cubicTo(w.toFloat(), h * 0.38f, w * 0.76f, h * 0.62f, w / 2f, h.toFloat())
        close()
    }
    canvas.drawPath(path, Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.rgb(0, 91, 187)
    })
    canvas.drawPath(path, Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.WHITE
    })
    val innerR = (4.6f * density)
    canvas.drawCircle(w / 2f, h * 0.28f, innerR, Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.WHITE
    })
    canvas.drawCircle(w / 2f, h * 0.28f, innerR * 0.55f, Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.rgb(255, 213, 0)
    })
    return bmp
}

/** Polygon approximation of a circle around [center] — osmdroid has no native circle overlay. */
private fun circlePoints(center: GeoPoint, radiusMeters: Double, segments: Int = 64): List<GeoPoint> {
    val dLat = radiusMeters / 110_574.0
    val dLon = radiusMeters / (111_320.0 * cos(Math.toRadians(center.latitude)).coerceAtLeast(0.01))
    return List(segments) { i ->
        val a = 2.0 * Math.PI * i / segments
        GeoPoint(center.latitude + dLat * sin(a), center.longitude + dLon * cos(a))
    }
}

/** Destination point [distMeters] along [bearingDeg] from [start] (great-circle, small-step). */
private fun destinationPoint(start: GeoPoint, bearingDeg: Double, distMeters: Double): GeoPoint {
    val earthR = 6_371_000.0
    val d = distMeters / earthR
    val theta = Math.toRadians(bearingDeg)
    val phi1 = Math.toRadians(start.latitude)
    val lam1 = Math.toRadians(start.longitude)
    val phi2 = Math.asin(
        sin(phi1) * cos(d) + cos(phi1) * sin(d) * cos(theta)
    )
    val lam2 = lam1 + Math.atan2(
        sin(theta) * sin(d) * cos(phi1),
        cos(d) - sin(phi1) * sin(phi2)
    )
    return GeoPoint(Math.toDegrees(phi2), Math.toDegrees(lam2))
}

/** A time-to-arrival course segment (red to the red threshold, yellow onward). */
private fun ttaLine(color: Int, density: Float): Polyline = Polyline().apply {
    outlinePaint.color = color
    outlinePaint.style = Paint.Style.STROKE
    outlinePaint.strokeWidth = 2.5f * density
    setInfoWindow(null)
}

/** Green ring highlighting the threat a notification just revealed (transparent centre). */
private fun newRingBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val r = 20f * density
    val glowR = r * 1.4f
    val size = (glowR * 2).toInt().coerceAtLeast(2)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f
    canvas.drawCircle(cx, cy, glowR, Paint().apply {
        shader = RadialGradient(
            cx, cy, glowR,
            intArrayOf(Color.argb(80, 76, 175, 80), Color.argb(0, 76, 175, 80)),
            floatArrayOf(0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    })
    canvas.drawCircle(cx, cy, r, Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Color.rgb(76, 175, 80)
    })
    return bmp
}

/** A revealed threat that is still highlighted with the green ring. */
private data class NewRingState(val id: String?, val position: LatLng, val activeUntilMs: Long)

private const val NEW_RING_MS = 8_000L
/** Floor for the reveal frame's lat/lon span — stops over-zoom on a very close threat. */
private const val REVEAL_MIN_SPAN_LAT = 0.10
private const val REVEAL_MIN_SPAN_LON = 0.16

/** Framing box for a notification reveal: focus near the top, threat near the bottom, with a
 *  clamped span so a huge gap (or a zero gap) still yields a valid, zoomable box. */
private fun buildRevealBoundingBox(threat: LatLng, focus: LatLng?): BoundingBox {
    if (focus == null) {
        val span = 0.5
        return BoundingBox(
            threat.lat + span, threat.lon + span,
            threat.lat - span, threat.lon - span
        )
    }
    val ft = 0.28f  // focus vertical fraction from the top
    val fb = 0.72f  // threat vertical fraction from the top
    val g = fb - ft
    val gapLat = Math.abs(focus.lat - threat.lat)
    val spanLat = Math.max(gapLat / g, REVEAL_MIN_SPAN_LAT).coerceAtMost(40.0)
    val north = Math.max(focus.lat, threat.lat) + ft * spanLat
    val south = Math.min(focus.lat, threat.lat) - (1 - fb) * spanLat
    val lonMid = (focus.lon + threat.lon) / 2
    val gapLon = Math.abs(focus.lon - threat.lon)
    val spanLon = Math.max(gapLon / g, REVEAL_MIN_SPAN_LON).coerceAtMost(80.0)
    return BoundingBox(
        north.coerceAtMost(85.0), lonMid + spanLon / 2,
        south.coerceAtLeast(-85.0), lonMid - spanLon / 2
    )
}

@Composable
fun NeptunMapView(
    uiState: UiState,
    lang: AppLanguage,
    iconSet: ThreatIconSet = ThreatIconSet.PHOTO,
    onScaleChange: (Double) -> Unit,
    onThreatTapped: (Threat) -> Unit,
    onMapTapped: () -> Unit,
    fitUkraineTick: Int = 0,
    zoomZone: ThreatZone? = null,
    zoomTick: Int = 0,
    fitZonesTick: Int = 0,
    revealRequest: RevealRequest? = null,
    paused: Boolean = false,
    onNeutralize: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = Strings.get(lang)

    // Only rebuild overlays when the threat data actually changes. Pan/zoom and
    // unrelated recompositions (language, popup selection) must not clear + redraw
    // the map, which is what made the banner above it flicker.
    val overlayKey = buildString {
        append(lang).append('A').append(uiState.activeZone)
        append('I').append(iconSet)
        append('R').append(uiState.activeZoneParams.slowRedKm).append('Y').append(uiState.activeZoneParams.slowYellowKm)
        append('F').append(uiState.followMe).append('P').append(uiState.pinnedCity?.nameUa)
        append('G').append(uiState.focusLocation?.lat).append(',').append(uiState.focusLocation?.lon)
        append('O').append(uiState.focusOblastAlertActive)
        append('T').append(uiState.showTtaLines)
        for (token in uiState.activeRegionTokens) append('R').append(token).append(';')
        for (t in uiState.mapThreats) appendThreatKey(t, System.currentTimeMillis())
    }
    val lastOverlayKey = remember { mutableStateOf<String?>(null) }
    val lastFitUkraineTick = remember { mutableStateOf(-1) }
    val lastFollow = remember { mutableStateOf<LatLng?>(null) }
    val lastZoomTick = remember { mutableStateOf(-1) }
    val lastFitZonesTick = remember { mutableStateOf(-1) }
    val lastRevealTick = remember { mutableStateOf(-1) }
    val newRingState = remember { mutableStateOf<NewRingState?>(null) }
    val newRingMarker = remember { mutableStateOf<Marker?>(null) }
    val didDefaultFit = remember { mutableStateOf(false) }
    val lastPinnedCity = remember { mutableStateOf<String?>(null) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val markerRefs = remember { mutableStateOf<MutableMap<String, Marker>>(mutableMapOf()) }
    val ttaRefs = remember { mutableStateOf<MutableMap<String, Pair<Polyline, Polyline>>>(mutableMapOf()) }
    val pausedState by rememberUpdatedState(paused)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val hiddenTypesState by rememberUpdatedState(uiState.hiddenTypes)
    val iconSetState by rememberUpdatedState(uiState.iconSet)
    val selectedThreatIdState by rememberUpdatedState(uiState.selectedThreat?.id)
    val focusLocationState by rememberUpdatedState(uiState.focusLocation)
    val deathAnimationEnabledState by rememberUpdatedState(uiState.deathAnimationEnabled)
    val followBulletState by rememberUpdatedState(uiState.followBullet)
    val mapScope = rememberCoroutineScope()
    val deathFx = remember { ThreatDeathOverlay() }
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }

    // Follow-the-bullet: with the setting on, the camera glides onto the strike; it never
    // scrolls to the launching city, and it never returns anywhere after the explosion. Off:
    // the camera stays still while the animation plays.
    val followStrike: (MapView, GeoPoint, GeoPoint?) -> Unit = { mapView, geo, _ ->
        if (mapView.width > 0 && mapView.height > 0 && followBulletState) {
            mapScope.launch { mapView.controller.animateTo(geo) }
        }
    }

    // Where the death-bullet takes off from: the nearest major city to the target, else the
    // focus point (GPS or pinned city) when no city is close enough.
    val strikeOrigin: (GeoPoint) -> GeoPoint? = { target ->
        Cities.nearestCity(target.latitude, target.longitude)?.let { GeoPoint(it.lat, it.lon) }
            ?: (focusLocationState ?: LocationTracker.location.value)?.let { GeoPoint(it.lat, it.lon) }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            Configuration.getInstance().userAgentValue = ctx.packageName
            // Keep the tile cache in the OS cache dir so Android treats it as "Cache"
            // (evictable, not counted as user data) and cap its size.
            Configuration.getInstance().osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            Configuration.getInstance().osmdroidTileCache = File(ctx.cacheDir, "osmdroid")
            Configuration.getInstance().tileFileSystemCacheMaxBytes = 64L * 1024 * 1024
            Configuration.getInstance().tileFileSystemCacheTrimBytes = 48L * 1024 * 1024
            var lastIconScale = -1f
            MapView(ctx).apply {
                setTileProvider(UkraineTileProvider(ctx))
                setBackgroundColor(Color.BLACK)
                overlayManager.tilesOverlay.setLoadingBackgroundColor(Color.BLACK)
                overlayManager.tilesOverlay.setLoadingLineColor(Color.BLACK)
                setMultiTouchControls(true)
                // No +/– buttons — everyone uses pinch. Contours stay clean on the map.
                setBuiltInZoomControls(false)
                maxZoomLevel = 17.0
                // Clamp the viewport to Ukraine (incl. Crimea) plus a small margin so
                // the map can't pan out into foreign territory.
                setScrollableAreaLimitDouble(UA_VIEW_LIMITS)
                controller.setCenter(DEFAULT_CENTER)
                // Start at a city-level zoom instead of osmdroid's default whole-globe view;
                // once the first GPS fix lands, didDefaultFit re-zooms to the yellow zone.
                controller.setZoom(12.0)

                // Feed ground meters-per-pixel to the Compose scale bar while panning/zooming, and
                    // grow/shrink the live threat icons as the zoom crosses the scaling band.
                    addMapListener(object : MapListener {
                        private fun mpp(): Double =
                            TileSystem.GroundResolution(this@apply.mapCenter.latitude, this@apply.zoomLevelDouble)
                        override fun onScroll(event: ScrollEvent?): Boolean { onScaleChange(mpp()); return false }
                        override fun onZoom(event: ZoomEvent?): Boolean {
                            onScaleChange(mpp())
                            val scale = zoomIconScale(this@apply.zoomLevelDouble)
                            if (scale != lastIconScale) {
                                lastIconScale = scale
                                for (t in uiState.mapThreats) {
                                    markerRefs.value[t.id]?.icon =
                                        threatIconFor(context, t.type, iconSetState, scale)
                                }
                                this@apply.invalidate()
                            }
                            return false
                        }
                    })
            }
        },
        update = { mapView ->
            mapViewRef.value = mapView

            // Green ring marking the threat a notification just revealed — follows the live
            // marker (or holds the raw fix), and disappears once its 8s window is up.
            fun placeRing() {
                val ring = newRingState.value ?: return
                val now = System.currentTimeMillis()
                if (now >= ring.activeUntilMs) {
                    newRingMarker.value?.let { mapView.overlays.remove(it) }
                    newRingMarker.value = null
                    return
                }
                val target = ring.id?.let { id ->
                    uiState.mapThreats.firstOrNull { it.id == id }
                }?.let { t ->
                    ThreatSpeedTracker.estimate(t.id, t)?.let { predictPosition(t, it, now) }
                } ?: GeoPoint(ring.position.lat, ring.position.lon)
                val existing = newRingMarker.value
                if (existing == null || existing !in mapView.overlays) {
                    val m = Marker(mapView).apply {
                        position = target
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = BitmapDrawable(context.resources, newRingBitmap(context))
                        setInfoWindow(null)
                    }
                    mapView.overlays.add(m)
                    newRingMarker.value = m
                } else {
                    existing.position = target
                }
            }
            // Floor the zoom-out so you can't zoom past "Ukraine fills the screen".
            if (mapView.width > 0 && mapView.height > 0) {
                val floorZoom =
                    tileSystem.getBoundingBoxZoom(UA_VIEW_LIMITS, mapView.width, mapView.height)
                if (mapView.minZoomLevel != floorZoom) mapView.setMinZoomLevel(floorZoom)
            }
            onScaleChange(
                TileSystem.GroundResolution(mapView.mapCenter.latitude, mapView.zoomLevelDouble)
            )

            // Camera follows the focus point (GPS while following, pinned city otherwise).
            val focus = uiState.focusLocation
            if (focus != null && lastFollow.value != focus) {
                lastFollow.value = focus
                mapView.controller.animateTo(GeoPoint(focus.lat, focus.lon))
            } else if (focus == null && lastFollow.value != null) {
                lastFollow.value = null
            }

            // Default view: once we have a focus point, open zoomed to fit the whole
            // yellow zone (camera then just follows it without re-zooming).
            if (!didDefaultFit.value && focus != null) {
                didDefaultFit.value = true
                mapView.zoomToBoundingBox(
                    zoneBoundingBox(GeoPoint(focus.lat, focus.lon), uiState.activeZoneParams.slowYellowKm.toDouble()),
                    true
                )
            }

            // Pin change: jump to the city and refit to its yellow zone.
            val pinned = uiState.pinnedCity
            if (!uiState.followMe && pinned != null && lastPinnedCity.value != pinned.nameUa) {
                lastPinnedCity.value = pinned.nameUa
                mapView.zoomToBoundingBox(
                    zoneBoundingBox(GeoPoint(pinned.lat, pinned.lon), uiState.activeZoneParams.slowYellowKm.toDouble()),
                    true
                )
            } else if (uiState.followMe) {
                lastPinnedCity.value = null
            }

            // Header tap: zoom out so the whole of Ukraine fills the screen.
            if (fitUkraineTick != lastFitUkraineTick.value) {
                lastFitUkraineTick.value = fitUkraineTick
                mapView.zoomToBoundingBox(UA_VIEW_LIMITS, true)
            }

            // Zone-button tap: zoom the camera to fit that zone circle with a 5% margin.
            if (zoomZone != null && zoomTick != lastZoomTick.value) {
                lastZoomTick.value = zoomTick
                val center = focus?.let { GeoPoint(it.lat, it.lon) } ?: mapView.mapCenter
                val radiusKm = when (zoomZone) {
                    ThreatZone.INNER -> uiState.activeZoneParams.slowRedKm.toDouble()
                    else -> uiState.activeZoneParams.slowYellowKm.toDouble()
                }
                mapView.zoomToBoundingBox(zoneBoundingBox(center, radiusKm), true)
            }

            // Alert-zones panel opened: centre + zoom so the whole yellow zone sits in
            // the visible area ABOVE the panel. The bbox is extended downward so the
            // zone occupies the top 60% of the viewport (the sheet covers ~40% below).
            if (fitZonesTick != lastFitZonesTick.value) {
                lastFitZonesTick.value = fitZonesTick
                val center = focus?.let { GeoPoint(it.lat, it.lon) } ?: mapView.mapCenter
                val zone = zoneBoundingBox(center, uiState.activeZoneParams.slowYellowKm.toDouble())
                val visibleFrac = 0.6f
                val dLat = zone.latNorth - center.latitude
                val southPad = dLat * 2 * ((1f / visibleFrac) - 1f)
                mapView.zoomToBoundingBox(
                    BoundingBox(zone.latNorth, zone.lonEast, zone.latSouth - southPad, zone.lonWest),
                    true
                )
            }

            // Notification tap: pan + zoom so the focus point (GPS/city) sits near the top
            // and the revealed threat near the bottom, with space between. The span scales
            // with the gap, so the zoom reflects how far the threat is. Also mark it with
            // the green ring.
            val reveal = revealRequest
            if (reveal != null && reveal.tick != lastRevealTick.value) {
                lastRevealTick.value = reveal.tick
                val threat = LatLng(reveal.lat, reveal.lon)
                newRingState.value = NewRingState(
                    reveal.id, threat, System.currentTimeMillis() + NEW_RING_MS
                )
                if (mapView.width > 0 && mapView.height > 0) {
                    // Harden: a bad framing box (or a not-yet-laid-out map) must never crash
                    // the composition thread — fall back to a plain centre-on-threat pan.
                    try {
                        mapView.zoomToBoundingBox(
                            buildRevealBoundingBox(threat, uiState.focusLocation), true
                        )
                    } catch (_: Exception) {
                        mapView.controller.animateTo(GeoPoint(threat.lat, threat.lon))
                    }
                }
                placeRing()
            }

            if (overlayKey == lastOverlayKey.value) {
                // No change — skip clearing + redrawing the map (avoids banner flicker).
            } else if (deathFx.isActive) {
                // A death animation is mid-flight: defer the clear+rebuild until it finishes.
                // clearing mapView.overlays (of which deathFx is a member) while the 16ms
                // invalidate loop is drawing can race the overlay list. The 1s UI-state tick
                // recomposes this update block, so the deferred rebuild fires right after.
            } else {
                lastOverlayKey.value = overlayKey

                mapView.overlays.clear()
                markerRefs.value.clear()
                ttaRefs.value.clear()

                // Bottom-most overlay: single-tap on empty map closes the popup, while
                // markers added after it keep tap priority. Long-press is handled by the
                // top-most overlay (markers swallow their own long-presses).
                mapView.overlays.add(
                    0,
                    MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            onMapTapped()
                            return true
                        }
                        override fun longPressHelper(p: GeoPoint): Boolean = true
                    })
                )

                // City labels (English names on top of label-free tiles)
                mapView.overlays.add(
                    CityLabelOverlay(context, lang, uiState.activeRegionTokens)
                )

                // Focus-centered alert zones: yellow ring (outer) and red circle (inner) for
                // the SLOW distance thresholds — outlines only, no fill so the map stays clean.
                if (focus != null) {
                    val zoneCenter = GeoPoint(focus.lat, focus.lon)
                    val yellowAlert = uiState.activeZone == ThreatZone.OUTER
                    val redAlert = uiState.activeZone == ThreatZone.INNER
                    mapView.overlays.add(Polygon(mapView).apply {
                        points = circlePoints(zoneCenter, uiState.activeZoneParams.slowYellowKm * 1000.0)
                        fillColor = Color.TRANSPARENT
                        strokeColor = if (yellowAlert) Color.argb(235, 255, 213, 0)
                        else Color.argb(150, 255, 213, 0)
                        strokeWidth = if (yellowAlert) 4f else 2.5f
                        title = strings.yellowZoneLabel
                        setInfoWindow(null)
                    })
                    mapView.overlays.add(Polygon(mapView).apply {
                        points = circlePoints(zoneCenter, uiState.activeZoneParams.slowRedKm * 1000.0)
                        fillColor = Color.TRANSPARENT
                        strokeColor = if (redAlert) Color.argb(235, 255, 60, 60)
                        else Color.argb(160, 255, 82, 82)
                        strokeWidth = if (redAlert) 4f else 3f
                        title = strings.redZoneLabel
                        setInfoWindow(null)
                    })
                }

                // Threats anywhere in the country — tappable, type icon; stale/expired ones
                // render dimmed (still tappable) until they pass the hard ghost cap.
                for (t in uiState.mapThreats) {
                    // A user-shot drone stays hidden while its death animation plays; the
                    // next redraw after the animation brings it back in place.
                    if (deathFx.isActiveFor(t.id)) continue
                    ThreatSpeedTracker.record(t.id, t.updatedAtMillis ?: System.currentTimeMillis(), t.lat, t.lon)
                    val typeInfo = ThreatTypeCatalog.INFO.getValue(t.type)
                    val typeLabel = if (lang == AppLanguage.UA) typeInfo.labelUa else typeInfo.labelEn
                    val rawRegion = t.region ?: t.district ?: t.locality ?: strings.noRegion
                    val regionLabel = if (lang == AppLanguage.EN) Cities.uaToEn[rawRegion] ?: rawRegion else rawRegion
                    // Place markers at their dead-reckoned position straight away (matching the
                    // animation loop) so a rebuild never snaps a moving marker back to its raw fix
                    // and returning to the app doesn't flash stale fixes before the loop corrects.
                    val predicted = ThreatSpeedTracker.estimate(t.id, t)?.let {
                        predictPosition(t, it, System.currentTimeMillis())
                    }
                    val pos = predicted ?: GeoPoint(t.lat, t.lon)
                    val stale = t.isStale(System.currentTimeMillis())
                    val marker = Marker(mapView).apply {
                        position = pos
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = threatIconFor(context, t.type, iconSet, zoomIconScale(mapView.zoomLevelDouble))
                        alpha = if (stale) 0.25f else 1.0f
                        title = typeLabel
                        snippet = regionLabel
                        // Rotate to show course, mirroring NEPTUN's predict().heading: velocity
                        // bearing while live, else reported heading, else their A(id) pseudo-course.
                        // The classic icons face up at 0°; the photo/army sets have a baked-in
                        // facing angle, so their rotation is the course minus that base (0..360).
                        rotation = if (t.areaOnly) 0f else {
                            val course = t.courseDeg.toFloat()
                            val base = IconCatalog.baseDeg(t.type, iconSet)
                            (course - base + 360f) % 360f
                        }
                        setOnMarkerClickListener { _, _ ->
                            onThreatTapped(t)
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                    markerRefs.value[t.id] = marker
                    if (uiState.showTtaLines && !t.areaOnly && t.type in FastThreatTypes && predicted != null) {
                        val speedMps = ThreatSpeedTracker.estimate(t.id, t) ?: 0.0
                        val bearing = t.bearingDeg ?: t.heading
                        if (speedMps > 0.0 && bearing != null) {
                            val p = uiState.activeZoneParams
                            val density = context.resources.displayMetrics.density
                            val redEnd = destinationPoint(predicted, bearing, speedMps * p.fastRedMin * 60)
                            val yellowEnd = destinationPoint(
                                redEnd, bearing, speedMps * (p.fastYellowMin - p.fastRedMin) * 60
                            )
                            val redLine = ttaLine(Color.argb(210, 255, 60, 60), density).apply {
                                setPoints(listOf(predicted, redEnd))
                            }
                            val yellowLine = ttaLine(Color.argb(200, 255, 213, 0), density).apply {
                                setPoints(listOf(redEnd, yellowEnd))
                            }
                            mapView.overlays.add(redLine)
                            mapView.overlays.add(yellowLine)
                            ttaRefs.value[t.id] = redLine to yellowLine
                        }
                    }
                }

                // Reveal ring above the threat icons (added only while its 8s window is live).
                placeRing()

                // GPS dot — a plain marker driven by LocationTracker's coarse fix. No separate
                // location provider here (that was the battery-heavy blue accuracy circle).
                // Only shown while following; when pinned to a city your real position (possibly
                // far away) would just confuse the view.
                if (uiState.followMe) {
                    uiState.userLocation?.let {
                        mapView.overlays.add(Marker(mapView).apply {
                            position = GeoPoint(it.lat, it.lon)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = BitmapDrawable(context.resources, gpsDotBitmap(context))
                            setInfoWindow(null)
                        })
                    }
                }

                // Pinned-city pin — tip of the marker sits exactly on the city.
                if (!uiState.followMe) {
                    uiState.pinnedCity?.let { city ->
                        mapView.overlays.add(Marker(mapView).apply {
                            position = GeoPoint(city.lat, city.lon)
                            setAnchor(Marker.ANCHOR_CENTER, 1.0f)
                            icon = BitmapDrawable(context.resources, pinBitmap(context))
                            setInfoWindow(null)
                        })
                    }
                }

                // Top-most touch overlay: markers swallow their own long-presses, so a
                // separate overlay gets them first. Taps fall through to the map/markers.
                // Long-pressing a threat marker fires the death animation on demand — the
                // same flourish a real resolution plays. Empty-ground long-presses are ignored.
                mapView.overlays.add(
                    MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean = false
                        override fun longPressHelper(p: GeoPoint): Boolean {
                            val pressPx = Point()
                            mapView.projection.toPixels(p, pressPx)
                            val density = mapView.context.resources.displayMetrics.density
                            val threshold = 48 * density
                            var nearest: Marker? = null
                            var nearestD = threshold
                            for (t in uiState.mapThreats) {
                                val m = markerRefs.value[t.id] ?: continue
                                val mp = m.position ?: continue
                                val tp = Point()
                                mapView.projection.toPixels(mp, tp)
                                val dx = tp.x - pressPx.x
                                val dy = tp.y - pressPx.y
                                val d = sqrt((dx * dx + dy * dy).toFloat())
                                if (d <= nearestD) {
                                    nearest = m
                                    nearestD = d
                                }
                            }
                            if (nearest != null) {
                                // If the pressed threat is the selected one, self-destruct
                                // its card too (reuses the real neutralized-card flow).
                                val pressedId = markerRefs.value.entries
                                    .firstOrNull { it.value === nearest }?.key
                                // Unhook the real marker so the normal pipeline stops drawing
                                // it; the overlay now renders its icon through the flight and
                                // drops it when the explosion starts.
                                mapView.overlays.remove(nearest)
                                markerRefs.value.entries.removeAll { it.value === nearest }
                                mapView.invalidate()
                                if (pressedId != null && pressedId == selectedThreatIdState) {
                                    onNeutralize(pressedId)
                                }
                                // Remember the shot so a same-id respawn within the grace window
                                // doesn't re-alert (the object itself is never removed).
                                if (pressedId != null) NeptunClient.markUserShot(pressedId)
                                if (deathAnimationEnabledState) {
                                    val target = nearest.position ?: GeoPoint(p.latitude, p.longitude)
                                    val origin = strikeOrigin(target)
                                    if (deathFx.isActiveFor(pressedId)) {
                                        // Already being struck — a follow-up projectile just
                                        // flies off-screen instead of exploding twice.
                                        deathFx.spawnDud(pressedId, target, origin)
                                    } else {
                                        followStrike(mapView, target, origin)
                                        deathFx.spawn(
                                            id = pressedId,
                                            geo = target,
                                            origin = origin,
                                            icon = nearest.icon,
                                            rotationDeg = nearest.rotation,
                                            alpha = nearest.alpha,
                                            hideAtBoom = true
                                        )
                                    }
                                }
                                return true
                            }
                            return false
                        }
                    })
                )

                // Death flourish on top of everything else.
                mapView.overlays.add(deathFx)

                mapView.invalidate()
            }
        },
        onRelease = { _ ->
            mapViewRef.value = null
        }
    )

        // Death animations: real resolved/remove frames. The threat's own marker icon keeps
        // rendering in the overlay through the full flight and fades out across the explosion;
        // without a live marker, fall back to the raw fix + a fresh icon.
        LaunchedEffect(Unit) {
            NeptunClient.removedThreats.collect { r ->
                // Skip resolutions that arrived while the map wasn't visible (Settings open or
                // app backgrounded): no stale half-consumed animations on return, and no
                // "bullet to nowhere" duds from threats that appeared and resolved unseen.
                if (pausedState || lifecycle.currentState < Lifecycle.State.STARTED) return@collect
                if (r.type in hiddenTypesState || !deathAnimationEnabledState) return@collect
                val marker = markerRefs.value[r.id]
                val anchor0 = GeoPoint(r.lat, r.lon)
                val origin = strikeOrigin(anchor0)
                if (marker == null || deathFx.isActiveFor(r.id)) {
                    // Already destroyed — a prior bullet landed (the server re-sent the
                    // resolution), so don't explode where the threat used to be: a follow-up
                    // projectile just streaks across and off-screen, then is dropped.
                    deathFx.spawnDud(r.id, anchor0, origin)
                } else {
                    // Unhook the real marker right away — the overlay draws its own copy of the
                    // icon, so keeping the shared marker would render the same drawable twice
                    // (its bounds/alpha are mutated per frame, and the marker's own draw fights
                    // back, making the icon flip or change direction mid-flight).
                    mapViewRef.value?.overlays?.remove(marker)
                    markerRefs.value.entries.removeAll { it.value === marker }
                    val anchor = marker.position ?: anchor0
                    val base = IconCatalog.baseDeg(r.type, iconSetState)
                    val rotation = marker.rotation ?: (r.courseDeg.toFloat() - base + 360f) % 360f
                    // A fresh copy, not the marker's shared drawable.
                    val icon = threatIconFor(
                        context, r.type, iconSetState,
                        zoomIconScale(mapViewRef.value?.zoomLevelDouble ?: 0.0)
                    )
                    // With follow-the-bullet on, the camera glides onto the target so the
                    // strike is actually seen.
                    mapViewRef.value?.let { followStrike(it, anchor, origin) }
                    mapViewRef.value?.invalidate()
                    deathFx.spawn(
                        id = r.id,
                        geo = anchor,
                        origin = origin,
                        icon = icon,
                        rotationDeg = rotation,
                        alpha = marker.alpha ?: 1f
                    )
                    // A short pulse as the projectile detonates on the map. USAGE_ALARM keeps it
                    // audible as a vibration even when the system "touch feedback" haptics are off.
                    if (vibrator != null) mapScope.launch {
                        delay(DEATH_EXPLOSION_START_MS)
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE),
                            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
                        )
                    }
                }
            }
        }

        // Redraw the map at ~60fps while a death animation is playing and the map is visible,
        // so the overlay animates; otherwise idle at a slow tick (no battery cost).
        LaunchedEffect(Unit) {
            while (true) {
                if (deathFx.isActive && !pausedState && lifecycle.currentState >= Lifecycle.State.STARTED) {
                    mapViewRef.value?.invalidate()
                    delay(16)
                } else {
                    delay(1000)
                }
            }
        }

    // Smoothly advance markers between the (sparse) server fixes: predict each threat's
    // current position from its heading + estimated speed and move the marker in-place,
    // without clearing the whole map. Only invalidates when something actually moved.
    LaunchedEffect(overlayKey) {
        while (true) {
            delay(1000)
            // The map is fully hidden behind Settings — skip the marker smoothing to save battery.
            if (pausedState) continue
            val mapView = mapViewRef.value ?: continue
            val now = System.currentTimeMillis()
            var dirty = false
            for (t in uiState.mapThreats) {
                val marker = markerRefs.value[t.id] ?: continue
                // Staleness dimming + course rotation update in-place too (they're excluded
                // from overlayKey, so a full rebuild no longer runs for them).
                val targetAlpha = if (t.isStale(now)) 0.25f else 1.0f
                if (marker.alpha != targetAlpha) {
                    marker.alpha = targetAlpha
                    dirty = true
                }
                val targetRot = if (t.areaOnly) 0f else {
                    val course = t.courseDeg.toFloat()
                    val base = IconCatalog.baseDeg(t.type, iconSetState)
                    (course - base + 360f) % 360f
                }
                if (marker.rotation != targetRot) {
                    marker.rotation = targetRot
                    dirty = true
                }
                val speed = ThreatSpeedTracker.estimate(t.id, t) ?: continue
                val predicted = predictPosition(t, speed, now) ?: continue
                val cur = marker.position
                if (cur != null &&
                    distanceMeters(cur.latitude, cur.longitude, predicted.latitude, predicted.longitude) > 1.0
                ) {
                    marker.position = predicted
                    dirty = true
                }
                val tta = ttaRefs.value[t.id]
                if (tta != null && !t.areaOnly && t.type in FastThreatTypes) {
                    val bearing = t.bearingDeg ?: t.heading
                    if (bearing != null) {
                        val p = uiState.activeZoneParams
                        val redEnd = destinationPoint(predicted, bearing, speed * p.fastRedMin * 60)
                        val yellowEnd = destinationPoint(
                            redEnd, bearing, speed * (p.fastYellowMin - p.fastRedMin) * 60
                        )
                        tta.first.setPoints(listOf(predicted, redEnd))
                        tta.second.setPoints(listOf(redEnd, yellowEnd))
                        dirty = true
                    }
                }
            }
            if (dirty) mapView.invalidate()
            // Reveal ring: glide onto its threat marker, or fade out once the 8s window ends.
            newRingMarker.value?.let { rm ->
                val ring = newRingState.value ?: return@let
                if (now >= ring.activeUntilMs) {
                    mapView.overlays.remove(rm)
                    newRingMarker.value = null
                    dirty = true
                } else {
                    val t = ring.id?.let { id -> uiState.mapThreats.firstOrNull { it.id == id } }
                    val target = t?.let { tt ->
                        ThreatSpeedTracker.estimate(tt.id, tt)?.let { predictPosition(tt, it, now) }
                    } ?: GeoPoint(ring.position.lat, ring.position.lon)
                    val cur = rm.position
                    if (cur == null ||
                        distanceMeters(cur.latitude, cur.longitude, target.latitude, target.longitude) > 1.0
                    ) {
                        rm.position = target
                        dirty = true
                    }
                }
            }
            if (dirty) mapView.invalidate()
        }
    }
}
