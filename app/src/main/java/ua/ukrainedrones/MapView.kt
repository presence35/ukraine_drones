package ua.ukrainedrones

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
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
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

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

private fun iconFor(type: ThreatType): Int = when (type) {
    ThreatType.SHAHED -> R.drawable.shahed
    ThreatType.FPV_LOITERING -> R.drawable.ic_threat_fpv
    ThreatType.CRUISE_MISSILE -> R.drawable.ic_threat_cruise
    ThreatType.BALLISTIC -> R.drawable.ic_threat_ballistic
    ThreatType.KAB -> R.drawable.ic_threat_kab
    ThreatType.AVIATION -> R.drawable.ic_threat_aviation
    ThreatType.RECON -> R.drawable.ic_threat_recon
    ThreatType.UNKNOWN -> R.drawable.ic_threat_unknown
}

private fun StringBuilder.appendThreatKey(t: Threat, now: Long) {
    append(t.id).append('@').append(t.status).append('@')
    append(t.lat).append(',').append(t.lon).append('@')
    append(t.courseDeg).append('@').append(if (t.isStale(now)) 'S' else 'L').append(';')
}

/**
 * osmdroid draws markers at the drawable's intrinsic size; the vector icons are used as-is,
 * but the shahed.webp photo is larger, so scale it down to a marker-sized bitmap.
 */
private fun threatIcon(context: Context, type: ThreatType): Drawable {
    val res = context.resources
    if (type != ThreatType.SHAHED) return ContextCompat.getDrawable(context, iconFor(type))!!
    val src = ContextCompat.getDrawable(context, R.drawable.shahed)!!
    val targetW = (32 * res.displayMetrics.density).toInt()
    val iw = src.intrinsicWidth.coerceAtLeast(1)
    val ih = src.intrinsicHeight.coerceAtLeast(1)
    val w = targetW
    val h = (ih.toFloat() * targetW / iw).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    src.setBounds(0, 0, w, h)
    src.draw(canvas)
    return BitmapDrawable(res, bmp)
}

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

@Composable
fun NeptunMapView(
    uiState: UiState,
    lang: AppLanguage,
    onScaleChange: (Double) -> Unit,
    onThreatTapped: (Threat) -> Unit,
    onMapTapped: () -> Unit,
    fitUkraineTick: Int = 0,
    zoomZone: ThreatZone? = null,
    zoomTick: Int = 0,
    fitZonesTick: Int = 0,
    paused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = Strings.get(lang)

    // Only rebuild overlays when the threat data actually changes. Pan/zoom and
    // unrelated recompositions (language, popup selection) must not clear + redraw
    // the map, which is what made the banner above it flicker.
    val overlayKey = buildString {
        append(lang).append('A').append(uiState.activeZone)
        append('R').append(uiState.slowRedKm).append('Y').append(uiState.slowYellowKm)
        append('F').append(uiState.followMe).append('P').append(uiState.pinnedCity?.nameUa)
        append('G').append(uiState.focusLocation?.lat).append(',').append(uiState.focusLocation?.lon)
        append('O').append(uiState.focusOblastAlertActive)
        for (token in uiState.activeRegionTokens) append('R').append(token).append(';')
        for (t in uiState.mapThreats) appendThreatKey(t, System.currentTimeMillis())
    }
    val lastOverlayKey = remember { mutableStateOf<String?>(null) }
    val lastFitUkraineTick = remember { mutableStateOf(-1) }
    val lastFollow = remember { mutableStateOf<LatLng?>(null) }
    val lastZoomTick = remember { mutableStateOf(-1) }
    val lastFitZonesTick = remember { mutableStateOf(-1) }
    val didDefaultFit = remember { mutableStateOf(false) }
    val lastPinnedCity = remember { mutableStateOf<String?>(null) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val markerRefs = remember { mutableStateOf<MutableMap<String, Marker>>(mutableMapOf()) }
    val speedTracker = remember { ThreatSpeedTracker() }
    val pausedState by rememberUpdatedState(paused)

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

                // Feed ground meters-per-pixel to the Compose scale bar while panning/zooming.
                addMapListener(object : MapListener {
                    private fun mpp(): Double =
                        TileSystem.GroundResolution(this@apply.mapCenter.latitude, this@apply.zoomLevelDouble)
                    override fun onScroll(event: ScrollEvent?): Boolean { onScaleChange(mpp()); return false }
                    override fun onZoom(event: ZoomEvent?): Boolean { onScaleChange(mpp()); return false }
                })
            }
        },
        update = { mapView ->
            mapViewRef.value = mapView
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
                    zoneBoundingBox(GeoPoint(focus.lat, focus.lon), uiState.slowYellowKm.toDouble()),
                    true
                )
            }

            // Pin change: jump to the city and refit to its yellow zone.
            val pinned = uiState.pinnedCity
            if (!uiState.followMe && pinned != null && lastPinnedCity.value != pinned.nameUa) {
                lastPinnedCity.value = pinned.nameUa
                mapView.zoomToBoundingBox(
                    zoneBoundingBox(GeoPoint(pinned.lat, pinned.lon), uiState.slowYellowKm.toDouble()),
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
                    ThreatZone.INNER -> uiState.slowRedKm.toDouble()
                    else -> uiState.slowYellowKm.toDouble()
                }
                mapView.zoomToBoundingBox(zoneBoundingBox(center, radiusKm), true)
            }

            // Alert-zones panel opened: centre + zoom so the whole yellow zone sits in
            // the visible area ABOVE the panel. The bbox is extended downward so the
            // zone occupies the top 60% of the viewport (the sheet covers ~40% below).
            if (fitZonesTick != lastFitZonesTick.value) {
                lastFitZonesTick.value = fitZonesTick
                val center = focus?.let { GeoPoint(it.lat, it.lon) } ?: mapView.mapCenter
                val zone = zoneBoundingBox(center, uiState.slowYellowKm.toDouble())
                val visibleFrac = 0.6f
                val dLat = zone.latNorth - center.latitude
                val southPad = dLat * 2 * ((1f / visibleFrac) - 1f)
                mapView.zoomToBoundingBox(
                    BoundingBox(zone.latNorth, zone.lonEast, zone.latSouth - southPad, zone.lonWest),
                    true
                )
            }

            if (overlayKey == lastOverlayKey.value) {
                // No change — skip clearing + redrawing the map (avoids banner flicker).
            } else {
                lastOverlayKey.value = overlayKey

                mapView.overlays.clear()
                markerRefs.value.clear()

                // Bottom-most overlay: single-tap on empty map closes the popup, while
                // markers added after it keep tap priority.
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
                        points = circlePoints(zoneCenter, uiState.slowYellowKm * 1000.0)
                        fillColor = Color.TRANSPARENT
                        strokeColor = if (yellowAlert) Color.argb(235, 255, 213, 0)
                        else Color.argb(150, 255, 213, 0)
                        strokeWidth = if (yellowAlert) 4f else 2.5f
                        title = strings.yellowZoneLabel
                        setInfoWindow(null)
                    })
                    mapView.overlays.add(Polygon(mapView).apply {
                        points = circlePoints(zoneCenter, uiState.slowRedKm * 1000.0)
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
                    speedTracker.record(t.id, t.updatedAtMillis ?: System.currentTimeMillis(), t.lat, t.lon)
                    val typeInfo = ThreatTypeCatalog.INFO.getValue(t.type)
                    val typeLabel = if (lang == AppLanguage.UA) typeInfo.labelUa else typeInfo.labelEn
                    val rawRegion = t.region ?: t.district ?: t.locality ?: strings.noRegion
                    val regionLabel = if (lang == AppLanguage.EN) Cities.uaToEn[rawRegion] ?: rawRegion else rawRegion
                    // Place markers at their dead-reckoned position straight away (matching the
                    // animation loop) so a rebuild never snaps a moving marker back to its raw fix
                    // and returning to the app doesn't flash stale fixes before the loop corrects.
                    val predicted = speedTracker.estimate(t.id, t)?.let {
                        predictPosition(t, it, System.currentTimeMillis())
                    }
                    val pos = predicted ?: GeoPoint(t.lat, t.lon)
                    val stale = t.isStale(System.currentTimeMillis())
                    val marker = Marker(mapView).apply {
                        position = pos
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = threatIcon(context, t.type)
                        alpha = if (stale) 0.25f else 1.0f
                        title = typeLabel
                        snippet = regionLabel
                        // Rotate to show course, mirroring NEPTUN's predict().heading: velocity
                        // bearing while live, else reported heading, else their A(id) pseudo-course.
                        rotation = if (t.areaOnly) 0f else t.courseDeg.toFloat()
                        setOnMarkerClickListener { _, _ ->
                            onThreatTapped(t)
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                    markerRefs.value[t.id] = marker
                }

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

                mapView.invalidate()
            }
        },
        onRelease = { _ ->
            mapViewRef.value = null
        }
    )

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
                val speed = speedTracker.estimate(t.id, t) ?: continue
                val predicted = predictPosition(t, speed, now) ?: continue
                val cur = marker.position
                if (cur != null &&
                    distanceMeters(cur.latitude, cur.longitude, predicted.latitude, predicted.longitude) > 1.0
                ) {
                    marker.position = predicted
                    dirty = true
                }
            }
            if (dirty) mapView.invalidate()
        }
    }
}
