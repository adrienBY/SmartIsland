/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.model

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.tanh

/**
 * The collapsed pill stretches under the finger like an iOS Liquid Glass control: dragging pulls it out towards the
 * finger (rubber-banded, so it gives less the further you go), the other axis thins a little as if the volume were
 * kept, and on release it snaps back with a jelly wobble that overshoots through the middle before settling.
 *
 * Pure maths (no Android types) so it can be unit tested; the drag sets the stretch, the shell's layer draws it.
 */
object LiquidStretch {
    /** How far (dp) the pill can be pulled out at most, sideways and up / down. */
    const val MAX_X_DP = 26f
    const val MAX_Y_DP = 9f

    /** Share of the finger's travel the stretch follows at first (before the rubber band takes over). */
    const val FOLLOW = 0.55f

    /** How much the other axis thins as one is pulled out (0 = not at all, 1 = keeps the area). */
    const val SQUASH = 0.45f

    /** How much of the extra length grows towards the finger (1 = the far edge stays put, 0.5 = both edges move). */
    const val ANCHOR = 0.8f

    /** The release wobble: very little damping, quick, so it rings two or three times in about half a second. */
    const val RELEASE_DAMPING = 0.32f
    const val RELEASE_STIFFNESS = 520f

    /** Stretch (px, signed) for a finger [dragPx] away from where it touched down, never past [maxPx]. */
    fun pull(dragPx: Float, maxPx: Float): Float =
        if (maxPx <= 0f) 0f else maxPx * tanh(dragPx * FOLLOW / maxPx)

    /** Horizontal and vertical scale of a [widthPx] x [heightPx] pill stretched by [stretchXPx] / [stretchYPx]. */
    fun scale(stretchXPx: Float, stretchYPx: Float, widthPx: Float, heightPx: Float): Pair<Float, Float> {
        if (widthPx <= 0f || heightPx <= 0f) return 1f to 1f
        val longX = 1f + abs(stretchXPx) / widthPx
        val longY = 1f + abs(stretchYPx) / heightPx
        return (longX / longY.pow(SQUASH)) to (longY / longX.pow(SQUASH))
    }

    /**
     * How far (px) to move the pill so the extra length grows towards the finger: [scale] is the scale on that axis,
     * [sizePx] the pill's size on it, [stretchPx] the signed stretch (its sign says which side the finger is on).
     */
    fun anchorShiftPx(scale: Float, sizePx: Float, stretchPx: Float): Float =
        if (stretchPx == 0f) 0f else sign(stretchPx) * (scale - 1f).coerceAtLeast(0f) * sizePx / 2f * (2f * ANCHOR - 1f)
}
