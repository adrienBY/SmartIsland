/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager

/**
 * Runs the island's animations at the panel's top refresh rate (120 / 144 Hz) while the island moves.
 *
 * An overlay window gets whatever refresh rate the system picks for the app below it, often 60 Hz, and an adaptive
 * (LTPO) panel drops much lower when nothing is touched, so a spring that plays with no finger on the screen (a card
 * arriving, the island collapsing by itself) stepped visibly. While the island moves, its window asks for the
 * highest-refresh display mode at the current resolution; [HOLD_MS] after the last moving frame it lets go again,
 * so an island at rest costs no battery.
 *
 * The overlay draws on the main thread: [poke] is called from it (the island's motion flow and its touch handler).
 */
object MotionBoost {
    /** How long the boost outlives the last moving frame (also covers the first frames of the next movement). */
    const val HOLD_MS = 700L

    /** Set by the overlay service while its window exists. */
    @Volatile
    var sink: (() -> Unit)? = null

    fun poke() {
        sink?.invoke()
    }

    /** The highest-refresh mode at the display's current resolution, or null when there is nothing faster to ask for. */
    fun fastestMode(context: Context): Display.Mode? {
        val display = runCatching {
            (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.getDisplay(Display.DEFAULT_DISPLAY)
        }.getOrNull() ?: return null
        val current = display.mode
        return display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate }
    }

    /** Writes (or clears) the refresh-rate request on a window's params. */
    fun applyTo(params: WindowManager.LayoutParams, mode: Display.Mode?) {
        params.preferredDisplayModeId = mode?.modeId ?: 0
        params.preferredRefreshRate = mode?.refreshRate ?: 0f
    }
}
