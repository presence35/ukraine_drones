package ua.ukrainedrones

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.TileSystem
import org.osmdroid.views.MapView
import kotlin.random.Random

// Ukraine (incl. Crimea) plus a ~0.5° margin — mirrors the map's own pan limit, so a projectile
// can never take off from outside the country.
private const val UA_MIN_LAT = 43.9
private const val UA_MAX_LAT = 52.7
private const val UA_MIN_LON = 21.7
private const val UA_MAX_LON = 40.6

/**
 * Map-side flourish facade: owns the death-animation overlay plus everything that drives it —
 * the strike camera glide, the shot/kill haptics, the bullet take-off origin (a random point on
 * the viewport edge, clamped to Ukraine) and the tally-tap replay orchestration. The map view
 * keeps only the thin policy hooks (visibility gating, input handling) and delegates every
 * flourish mechanic here, so the critical marker loop stays clean.
 */
class DeathFxController(
    private val context: Context,
    private val mapView: () -> MapView?,
    private val iconFor: (ThreatType) -> Drawable,
    private val scope: CoroutineScope
) {
    /** The overlay itself — added to the map's overlay list and driven per frame. */
    val overlay = ThreatDeathOverlay()

    private val vibrator = context.getSystemService(Vibrator::class.java)
    // A pending "return the camera to where the user was" job — replaced by each new strike.
    private var cameraReturnJob: Job? = null
    // The running tally-tap replay, so a red alert can cancel it mid-show (clear()).
    private var replayJob: Job? = null

    private val _replayProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    /** During the tally-tap replay: the current bullet (1-based) within the CURRENT group and
     *  that group's size, so the footer can read "Resolving threat X of N" per group. */
    val replayProgress: StateFlow<Pair<Int, Int>?> = _replayProgress.asStateFlow()

    val active: StateFlow<Boolean> get() = overlay.active
    val isActive: Boolean get() = overlay.isActive

    fun isActiveFor(id: String?): Boolean = overlay.isActiveFor(id)

    /** Drop every active death + cancel a pending camera return or running replay instantly —
     *  a red alert ejects the flourish (safety outranks the playful replay). */
    fun clear() {
        cameraReturnJob?.cancel()
        replayJob?.cancel()
        replayJob = null
        _replayProgress.value = null
        overlay.clear()
    }

    /** Launch the tally-tap replay on the controller's scope, replacing any show in flight. */
    fun startReplay(records: List<FlourishRecord>) {
        replayJob?.cancel()
        replayJob = scope.launch { replay(records) }
    }

    /** User-initiated or server-driven strike: spawn the projectile + explosion. The bullet
     *  takes off from a random point on the viewport edge (clamped to Ukraine). */
    fun strike(
        id: String? = null,
        geo: GeoPoint,
        icon: Drawable? = null,
        rotationDeg: Float = 0f,
        alpha: Float = 1f
    ) = overlay.spawn(id, geo, randomEdgeOrigin(), icon, rotationDeg, alpha)

    /** Follow-up projectile for an already-destroyed threat: no icon, never explodes. */
    fun strikeDud(id: String?, geo: GeoPoint) {
        randomEdgeOrigin()?.let { overlay.spawnDud(id, geo, it) }
    }

    /** A random point exactly on the viewport edge (0px), converted to geo and clamped to
     *  Ukraine — the bullet always glides in from the screen edge, and can never originate in
     *  another country even when the whole country fills the screen. */
    private fun randomEdgeOrigin(): GeoPoint? {
        val mapView = mapView() ?: return null
        if (mapView.width <= 0 || mapView.height <= 0) return null
        val w = mapView.width.toFloat()
        val h = mapView.height.toFloat()
        val t = Random.nextFloat()
        val (px, py) = when (Random.nextInt(4)) {
            0 -> 0f to t * h      // left edge
            1 -> w to t * h       // right edge
            2 -> t * w to 0f      // top edge
            else -> t * w to h    // bottom edge
        }
        val geo = mapView.projection.fromPixels(px.toInt(), py.toInt())
        return GeoPoint(
            geo.latitude.coerceIn(UA_MIN_LAT, UA_MAX_LAT),
            geo.longitude.coerceIn(UA_MIN_LON, UA_MAX_LON)
        )
    }

    /** Follow-the-bullet: with the setting on, the camera glides onto the strike, then pans
     *  back to where the user was once the explosion has finished. It never scrolls to the
     *  launching city. Off: the camera stays still while the animation plays. A fresh strike
     *  replaces any pending return so rapid successive shots don't fight over the camera. */
    fun followStrike(target: GeoPoint, followBullet: Boolean) {
        val mapView = mapView() ?: return
        if (mapView.width <= 0 || mapView.height <= 0 || !followBullet) return
        val preCenter = mapView.mapCenter
        cameraReturnJob?.cancel()
        cameraReturnJob = scope.launch {
            mapView.controller.animateTo(target)
            delay(DEATH_EXPLOSION_START_MS + DEATH_EXPLOSION_LEN_MS + 300L)
            this@DeathFxController.mapView()?.controller?.animateTo(preCenter)
        }
    }

    /** Fun haptics: a short crisp "shot" as the bullet fires, then a longer pulse when it
     *  detonates. USAGE_ALARM keeps both audible as vibration even when the system "touch
     *  feedback" haptics are off. */
    fun strikeHaptics() {
        val vibrator = vibrator ?: return
        scope.launch {
            vibrator.vibrate(
                VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
            )
            delay(DEATH_EXPLOSION_START_MS)
            vibrator.vibrate(
                VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
            )
        }
    }

    /**
     * Tally-tap replay flourish: group the remembered resolutions by how close they sit in the
     * current viewport (screen-size × zoom adaptive), then for each group zoom onto just it,
     * fire its bullets [FLOURISH_STAGGER_MS] apart, and move to the next group — the camera
     * finally returns to where the user was after the LAST group explodes. Pure flourish — a
     * red alert ejects it (see [clear], which also cancels this show mid-flight). Launched via
     * [startReplay]; the caller gates on visibility/alert/lifecycle before invoking.
     */
    suspend fun replay(records: List<FlourishRecord>) {
        val mapView = mapView() ?: return
        if (records.isEmpty()) return
        val preCenter = mapView.mapCenter
        cameraReturnJob?.cancel()
        // A group spans about a third of the current viewport width — zoomed in, groups are
        // tight; zoomed out, everything clusters.
        val mpp = TileSystem.GroundResolution(mapView.mapCenter.latitude, mapView.zoomLevelDouble)
        val groupDist = mpp * mapView.width * 0.33f
        val groups = clusterFlourish(records, groupDist.toDouble())
        DebugLog.recordFlourish(DebugLogReason.FIRED, System.currentTimeMillis())
        var index = 0
        groups.forEachIndexed { gi, group ->
            // Centre + zoom onto this group only (with margin).
            val box = flourishesBoundingBox(group, null)
            mapView.zoomToBoundingBox(box, true)
            delay(if (gi == 0) 300L else 400L)
            group.forEachIndexed { groupIndex, rec ->
                delay(if (index == 0) 0L else FLOURISH_STAGGER_MS)
                index++
                val anchor = GeoPoint(rec.lat, rec.lon)
                val icon = iconFor(rec.type)
                // Per-group progress (1-based within the current group) so the footer reads
                // "Resolving threat X of N" — a fresh 1..N for each cluster, never a global total.
                _replayProgress.value = (groupIndex + 1) to group.size
                overlay.spawn(
                    id = "flourish:$index",
                    geo = anchor,
                    origin = randomEdgeOrigin(),
                    icon = icon,
                    rotationDeg = 0f,
                    alpha = 1f
                )
                mapView.invalidate()
                strikeHaptics()
            }
            // Wait for this group's last bullet to finish before panning on.
            delay(FLOURISH_STAGGER_MS + DEATH_EXPLOSION_START_MS + DEATH_EXPLOSION_LEN_MS)
        }
        _replayProgress.value = null
        // Back home, at peace.
        mapView()?.controller?.animateTo(preCenter)
    }
}