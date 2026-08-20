package ua.ukrainedrones

import android.content.Context
import android.view.Gravity
import android.widget.Toast
import kotlin.math.roundToInt

/**
 * Toast helper: one function decides placement so callers never hardcode gravity.
 * Top = just below the header banner (status bar + header). When a card/popup is visible
 * at the top (threat/shelter popup, zones sheet), the toast drops to the bottom instead,
 * above the floating zone/shelter buttons.
 */
fun showToast(context: Context, text: CharSequence, cardVisible: Boolean) {
    val res = context.resources
    fun systemDimen(idName: String): Int {
        val id = res.getIdentifier(idName, "dimen", "android")
        return if (id == 0) 0 else runCatching { res.getDimensionPixelSize(id) }.getOrDefault(0)
    }
    val density = res.displayMetrics.density
    fun dp(value: Float) = (value * density).roundToInt()
    val (gravity, yOffset) = if (cardVisible) {
        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL to
            (systemDimen("navigation_bar_height") + dp(64f))
    } else {
        Gravity.TOP or Gravity.CENTER_HORIZONTAL to
            (systemDimen("status_bar_height") + dp(56f))
    }
    Toast.makeText(context, text, Toast.LENGTH_SHORT)
        .apply { setGravity(gravity, 0, yOffset) }
        .show()
}