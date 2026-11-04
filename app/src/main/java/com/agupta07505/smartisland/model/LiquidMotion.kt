/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.model

/**
 * The portrait island's "liquid" feel in one place: softer, bouncier springs, and a height that trails the width on
 * the way open, so the shell stretches wide first and then pours down instead of scaling as a rigid box.
 * Landscape keeps its own calmer springs (it moves much further).
 */
object LiquidMotion {
    /** Expand / size spring (was 0.8 / 400): about 6 % overshoot. */
    const val PORTRAIT_DAMPING = 0.66f
    const val PORTRAIT_STIFFNESS = 340f

    /** The height opens on a slower, still bouncy spring than the width, so it trails it (the stretch). */
    const val HEIGHT_STIFFNESS_SHARE = 0.68f
    const val HEIGHT_DAMPING = 0.7f

    /** Collapsed pill stretching to its with-content width (was 0.72 / 420). */
    const val PILL_STRETCH_DAMPING = 0.56f
    const val PILL_STRETCH_STIFFNESS = 360f

    /** Arrival pulse: a wider, wobblier swell (was +12.5 %, damping 0.6). */
    const val PULSE_WIDTH_FRACTION = 0.16f
    const val PULSE_OUT_DAMPING = 0.45f
}
