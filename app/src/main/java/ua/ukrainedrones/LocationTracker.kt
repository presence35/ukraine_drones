package ua.ukrainedrones

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared, battery-first device location. One listener owned by the foreground service so the
 * UI and the alert logic read the same fix. The red/yellow zones are km-scale, so a coarse
 * fix is plenty: ~2-minute updates, only when the device actually moves >250 m. Falls back to
 * the last known fix so zone circles keep drawing while indoors.
 */
object LocationTracker {

    private const val UPDATE_INTERVAL_MS = 120_000L
    private const val MIN_DISTANCE_METERS = 250f

    private val _location = MutableStateFlow<LatLng?>(null)
    val location: StateFlow<LatLng?> = _location.asStateFlow()

    private val _lastFixAtMs = MutableStateFlow<Long?>(null)
    val lastFixAtMs: StateFlow<Long?> = _lastFixAtMs.asStateFlow()

    @Volatile
    private var started = false
    private var appContext: Context? = null
    private var listener: LocationListener? = null

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        appContext = app
        if (started) return
        if (!hasPermission(app)) return
        val l = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                recordFix(loc)
            }
        }
        listener = l
        try {
            pickLastKnown(app)?.let { recordFix(it) }
            val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val looper = Looper.getMainLooper()
            // Network provider only: the alert zones are km-scale, so a coarse fix is
            // plenty, and skipping GPS keeps the radio off (battery-cheapest).
            val requested = runCatching {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, UPDATE_INTERVAL_MS, MIN_DISTANCE_METERS, l, looper)
            }
            if (requested.isSuccess) {
                // Only mark started once the update request actually registered; otherwise a
                // failed start (e.g. SecurityException) would make every future start() a no-op.
                started = true
            }
        } catch (_: SecurityException) {
            _location.value = null
        }
    }

    /** Requests a precise one-shot fix (GPS, falling back to network) for the shelter list. */
    fun forceRefresh() {
        val ctx = appContext ?: return
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine) return
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val onFix: (Location) -> Unit = { loc -> recordFix(loc) }
        val requested = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.getCurrentLocation(
                    LocationManager.GPS_PROVIDER,
                    CancellationSignal(),
                    ContextCompat.getMainExecutor(ctx)
                ) { loc -> if (loc != null) onFix(loc) }
            } else {
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, object : LocationListener {
                    override fun onLocationChanged(loc: Location) { onFix(loc) }
                }, Looper.getMainLooper())
            }
        }
        if (requested.isFailure) {
            runCatching {
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, object : LocationListener {
                    override fun onLocationChanged(loc: Location) { onFix(loc) }
                }, Looper.getMainLooper())
            }
        }
    }

    fun stop() {
        val ctx = appContext ?: return
        listener?.let {
            runCatching {
                val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                lm.removeUpdates(it)
            }
        }
        listener = null
        started = false
    }

    private fun recordFix(loc: Location) {
        _location.value = LatLng(loc.latitude, loc.longitude)
        _lastFixAtMs.value = System.currentTimeMillis()
    }

    private fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pickLastKnown(ctx: Context): Location? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
    }
}
