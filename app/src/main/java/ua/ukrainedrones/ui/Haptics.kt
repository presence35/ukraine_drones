package ua.ukrainedrones

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext

/** Global toggle for press haptics — provided from the user setting at the app root. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * True when the device renders no animations ("Remove animations" accessibility toggle or a
 * zero Developer-options animator scale — both zero the global duration scale). Callers skip
 * motion work entirely instead of running transition machinery that completes instantly.
 */
@Composable
fun animationsOff(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Plays a small haptic tick when a tap goes down. A passive listener (does not consume the
 * gesture), so it layers cleanly on top of any clickable/combinedClickable/toggleable.
 * Add it in the modifier chain right before the clickable it should accompany.
 *
 * Vibrates via the raw Vibrator service (same as the shot-down flourish) rather than Compose's
 * haptic feedback: USAGE_ALARM keeps the tick working even when system touch feedback is off.
 */
@Composable
fun Modifier.pressTick(): Modifier {
    val enabled = LocalHapticsEnabled.current
    val appContext = LocalContext.current.applicationContext
    if (!enabled) return this
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (down.pressed) {
                hapticTick(appContext)
            }
            waitForUpOrCancellation()
        }
    }
}

/**
 * One short haptic tick (30 ms, full amplitude) via the raw Vibrator service — the same
 * mechanism as [pressTick] and the shoot-down flourish: USAGE_ALARM keeps it working even
 * when system touch feedback is off. Callers gate on [LocalHapticsEnabled].
 */
internal fun hapticTick(context: Context) {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    if (!vibrator.hasVibrator()) return
    // One-shot with full amplitude (same mechanism as the shoot-down flourish) — the
    // predefined EFFECT_TICK is a device-tuned "keyboard tap" that many OEMs render as a no-op.
    val effect = VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE)
    if (Build.VERSION.SDK_INT >= 30) {
        vibrator.vibrate(
            effect,
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
        )
    } else {
        vibrator.vibrate(effect)
    }
}