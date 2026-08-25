package ua.ukrainedrones

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext

/** Global toggle for press haptics — provided from the user setting at the app root. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

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
                pressTickVibrate(appContext)
            }
            waitForUpOrCancellation()
        }
    }
}

private fun pressTickVibrate(context: Context) {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    if (!vibrator.hasVibrator()) return
    val effect = if (Build.VERSION.SDK_INT >= 29) {
        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    } else {
        VibrationEffect.createOneShot(25L, VibrationEffect.DEFAULT_AMPLITUDE)
    }
    if (Build.VERSION.SDK_INT >= 30) {
        vibrator.vibrate(
            effect,
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
        )
    } else {
        vibrator.vibrate(effect)
    }
}