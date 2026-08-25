package ua.ukrainedrones

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

/** Global toggle for press haptics — provided from the user setting at the app root. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Plays a small haptic tick when a tap goes down. A passive listener (does not consume the
 * gesture), so it layers cleanly on top of any clickable/combinedClickable/toggleable.
 * Add it in the modifier chain right before the clickable it should accompany.
 */
@Composable
fun Modifier.pressTick(): Modifier {
    val haptic = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    if (!enabled) return this
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (down.pressed) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            waitForUpOrCancellation()
        }
    }
}