/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidStretchTest {
    @Test
    fun thePullFollowsTheFingerThenRubberBandsToItsLimit() {
        assertEquals(0f, LiquidStretch.pull(0f, 60f), 0f)
        assertEquals(LiquidStretch.FOLLOW * 4f, LiquidStretch.pull(4f, 60f), 0.05f)
        assertTrue(LiquidStretch.pull(10_000f, 60f) <= 60f)
        assertEquals(-LiquidStretch.pull(40f, 60f), LiquidStretch.pull(-40f, 60f), 0.0001f)
    }

    @Test
    fun pullingSidewaysMakesThePillLongerAndThinner() {
        val (sx, sy) = LiquidStretch.scale(30f, 0f, 300f, 90f)
        assertEquals(1.1f, sx, 0.001f)
        assertTrue(sy < 1f && sy > 0.9f)
        // The direction of the pull does not change the shape, only where it grows.
        assertEquals(LiquidStretch.scale(30f, 0f, 300f, 90f), LiquidStretch.scale(-30f, 0f, 300f, 90f))
        assertEquals(1f to 1f, LiquidStretch.scale(0f, 0f, 300f, 90f))
    }

    @Test
    fun theExtraLengthGrowsTowardsTheFinger() {
        val right = LiquidStretch.anchorShiftPx(1.1f, 300f, 30f)
        assertTrue(right > 0f)
        assertEquals(-right, LiquidStretch.anchorShiftPx(1.1f, 300f, -30f), 0.0001f)
        // Far edge mostly in place: of the 30 px of extra width, the near edge moves most of it.
        assertEquals(15f * (2f * LiquidStretch.ANCHOR - 1f), right, 0.001f)
        assertEquals(0f, LiquidStretch.anchorShiftPx(1.1f, 300f, 0f), 0f)
    }
}
