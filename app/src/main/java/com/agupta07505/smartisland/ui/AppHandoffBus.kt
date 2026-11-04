/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.agupta07505.smartisland.model.AppHandoff
import com.agupta07505.smartisland.model.HandoffCard
import com.agupta07505.smartisland.model.HandoffEvent
import com.agupta07505.smartisland.model.IslandNotification
import com.agupta07505.smartisland.util.ForegroundApp

/**
 * The latest app <-> card hand-off (see [AppHandoff]). Written by [IslandViewModel] in the same pass that decides the
 * visible cards, so it is already set when the overlay composes the card's arrival; read by the island layout.
 */
internal object AppHandoffBus {
    var event by mutableStateOf<HandoffEvent?>(null)
        private set

    private var lastForeground: String? = null
    private var lastShown: List<HandoffCard> = emptyList()
    private var lastLocked = false
    private var unlockedAtMs = Long.MIN_VALUE / 2

    /** No hand-off now: locked, or the unlock is still settling (also read by the service's go-home fast path). */
    fun quietNow(): Boolean = AppHandoff.quiet(lastLocked, android.os.SystemClock.elapsedRealtime(), unlockedAtMs)

    /** The overlay window is about to show again after the lock screen hid it: the island grows out of the camera. */
    fun reveal() {
        event = HandoffEvent("", com.agupta07505.smartisland.model.HandoffDirection.Reveal, android.os.SystemClock.elapsedRealtime())
    }

    /** Called with every new visible list; finds the hand-offs the foreground change caused. */
    fun onVisible(foreground: String?, shown: List<IslandNotification>, locked: Boolean) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastLocked && !locked) unlockedAtMs = now
        val lockChanged = lastLocked != locked
        lastLocked = locked
        val cards = shown.map { HandoffCard(it.key, it.packageName, ForegroundApp.isLiveMode(it.mode)) }
        // The lock state flipping changes which cards show on its own (lock-screen rules): never a hand-off.
        val found = if (lockChanged || AppHandoff.quiet(locked, now, unlockedAtMs)) emptyList()
        else AppHandoff.detect(lastForeground, foreground, lastShown, cards, now)
        lastForeground = foreground
        lastShown = cards
        // One shell, one morph: the first hand-off of the pass drives it (an app normally has one live card).
        found.firstOrNull()?.let { event = it }
    }
}
