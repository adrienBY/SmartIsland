/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppHandoffTest {
    private val spotify = "com.spotify.music"
    private val launcher = "com.nothing.launcher"
    private val music = HandoffCard("music", spotify, live = true)
    private val chat = HandoffCard("chat", "com.whatsapp", live = false)

    @Test
    fun leavingTheAppBringsItsLiveCardOutOfTheCamera() {
        val events = AppHandoff.detect(spotify, launcher, emptyList(), listOf(music), 10L)
        assertEquals(listOf(HandoffEvent("music", HandoffDirection.OutOfApp, 10L)), events)
    }

    @Test
    fun openingTheAppSendsItsLiveCardBackIntoTheCamera() {
        val events = AppHandoff.detect(launcher, spotify, listOf(music), emptyList(), 20L)
        assertEquals(listOf(HandoffEvent("music", HandoffDirection.IntoApp, 20L)), events)
    }

    @Test
    fun cardsThatComeOrGoWithoutAnAppSwitchAreNotHandOffs() {
        // Music starts while the user is on the home screen: an ordinary arrival.
        assertTrue(AppHandoff.detect(launcher, launcher, emptyList(), listOf(music), 0L).isEmpty())
        // Another app's card appears during the switch: not this app's hand-off.
        assertTrue(AppHandoff.detect(spotify, launcher, emptyList(), listOf(chat), 0L).isEmpty())
        // A plain notification is never a live card.
        val alert = HandoffCard("a", spotify, live = false)
        assertTrue(AppHandoff.detect(spotify, launcher, emptyList(), listOf(alert), 0L).isEmpty())
    }

    @Test
    fun lockingAndUnlockingWithMusicOnIsNeverAHandOff() {
        // Locking inside Spotify: the foreground app is cleared and the music card shows on the lock screen.
        assertTrue(AppHandoff.detect(spotify, null, emptyList(), listOf(music), 0L).isEmpty())
        // Unlocking straight back into Spotify: its card hides again, without a morph.
        assertTrue(AppHandoff.detect(null, spotify, listOf(music), emptyList(), 0L).isEmpty())
        // Unlocking to the home screen: nothing to play either.
        assertTrue(AppHandoff.detect(null, launcher, listOf(music), listOf(music), 0L).isEmpty())
    }

    @Test
    fun handOffsStayQuietWhileLockedAndRightAfterAnUnlock() {
        assertTrue(AppHandoff.quiet(locked = true, nowMs = 5_000L, unlockedAtMs = 0L))
        assertTrue(AppHandoff.quiet(locked = false, nowMs = 1_000L, unlockedAtMs = 500L))
        assertFalse(AppHandoff.quiet(locked = false, nowMs = 500L + AppHandoff.UNLOCK_QUIET_MS, unlockedAtMs = 500L))
        assertFalse(AppHandoff.quiet(locked = false, nowMs = 10_000L, unlockedAtMs = Long.MIN_VALUE / 2))
    }

    @Test
    fun theArrivalPulseIsSkippedOnlyForTheMatchingRecentHandOff() {
        val e = HandoffEvent("music", HandoffDirection.OutOfApp, 1_000L)
        assertTrue(AppHandoff.explainsArrival(e, "music", 1_200L))
        assertFalse(AppHandoff.explainsArrival(e, "chat", 1_200L))
        assertFalse(AppHandoff.explainsArrival(e, "music", 1_000L + AppHandoff.MATCH_WINDOW_MS + 1))
        assertFalse(AppHandoff.explainsArrival(e.copy(direction = HandoffDirection.IntoApp), "music", 1_200L))
    }

    @Test
    fun theShellRunsFromTheCameraCoreToThePillWithADampedOvershoot() {
        assertEquals(34f, AppHandoff.width(34f, 112f, 0f), 0.001f)
        assertEquals(112f, AppHandoff.width(34f, 112f, 1f), 0.001f)
        assertEquals(73f, AppHandoff.width(34f, 112f, 0.5f), 0.001f)
        // Never narrower than the camera core.
        assertEquals(34f, AppHandoff.width(34f, 112f, -0.2f), 0.001f)
        val over = AppHandoff.width(34f, 112f, 1.05f)
        assertTrue(over > 112f && over < 112f + 78f * 0.05f)
    }

    @Test
    fun theContentWaitsForTheCoreToOpenThenGrowsIn() {
        assertEquals(0f, AppHandoff.contentAlpha(0.1f), 0.001f)
        assertEquals(1f, AppHandoff.contentAlpha(1f), 0.001f)
        assertEquals(AppHandoff.CONTENT_START_SCALE, AppHandoff.contentScale(0f), 0.001f)
        assertEquals(1f, AppHandoff.contentScale(1f), 0.001f)
        assertTrue(AppHandoff.contentScale(1.2f) <= 1.06f)
    }
}
