package ua.ukrainedrones

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.TileSystem
import org.osmdroid.views.MapView

/**
 * Map-side flourish facade: owns the death-animation overlay plus everything that drives it —
 * the strike camera glide, the shot/kill haptics, the bullet take-off origin and the tally-tap
 * replay orchestration. The map view keeps only the thin policy hooks (visibility gating, input
 * handling) and delegates every flourish mechanic here, so the critical marker loop stays clean.
 */
class DeathFxController(
    private val context: Context,
    private val mapView: () -> MapView?,
    private val focusLocation: () -> LatLng?,
    private val iconFor: (ThreatType) -> Drawable,
    private val scope: CoroutineScope
) {
    /** The overlay itself — added to the map's overlay list and driven per frame. */
    val overlay = ThreatDeathOverlay()

    private val vibrator = context.getSystemService(Vibrator::class.java)
    // A pending "return the camera to where the user was" job — replaced by each new strike.
    private var cameraReturnJob: Job? = null

    val active: StateFlow<Boolean> get() = overlay.active
    val isActive: Boolean get() = overlay.isActive

    fun isActiveFor(id: String?): Boolean = overlay.isActiveFor(id)

    /** Drop every active death + cancel a pending camera return instantly — a red alert ejects
     *  the flourish (safety outranks the playful replay). */
    fun clear() {
        cameraReturnJob?.cancel()
        overlay.clear()
    }

    /** User-initiated or server-driven strike: spawn the projectile + explosion. */
    fun strike(
        id: String? = null,
        geo: GeoPoint,
        origin: GeoPoint? = null,
        icon: Drawable? = null,
        rotationDeg: Float = 0f,
        alpha: Float = 1f
    ) = overlay.spawn(id, geo, origin, icon, rotationDeg, alpha)

    /** Follow-up projectile for an already-destroyed threat: no icon, never explodes. */
    fun strikeDud(id: String?, geo: GeoPoint, origin: GeoPoint?) = overlay.spawnDud(id, geo, origin)

    /** Where the death-bullet takes off from: the nearest major city to the target, else the
     *  focus point (GPS or pinned city) when no city is close enough. */
    fun strikeOrigin(target: GeoPoint): GeoPoint? {
        return Cities.nearestCity(target.latitude, target.longitude)?.let { GeoPoint(it.lat, it.lon) }
            ?: focusLocation()?.let { GeoPoint(it.lat, it.lon) }
            ?: LocationTracker.location.value?.let { GeoPoint(it.lat, it.lon) }
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
     * red alert ejects it (see [clear]). Runs on the caller's scope; the caller already gated
     * on visibility/alert/lifecycle before invoking.
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
        var index = 0
        groups.forEachIndexed { gi, group ->
            // Centre + zoom onto this group only (with margin).
            val box = flourishesBoundingBox(group, null)
            mapView.zoomToBoundingBox(box, true)
            delay(if (gi == 0) 300L else 400L)
            group.forEach { rec ->
                delay(if (index == 0) 0L else FLOURISH_STAGGER_MS)
                index++
                val anchor = GeoPoint(rec.lat, rec.lon)
                val origin = strikeOrigin(anchor)
                val icon = iconFor(rec.type)
                overlay.spawn(
                    id = "flourish:$index",
                    geo = anchor,
                    origin = origin,
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
        // Back home, at peace.
        mapView()?.controller?.animateTo(preCenter)
    }
}