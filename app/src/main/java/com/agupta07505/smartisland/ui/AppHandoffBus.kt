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

    /** Called with every new visible list; finds the hand-offs the foreground change caused. */
    fun onVisible(foreground: String?, shown: List<IslandNotification>) {
        val cards = shown.map { HandoffCard(it.key, it.packageName, ForegroundApp.isLiveMode(it.mode)) }
        val found = AppHandoff.detect(lastForeground, foreground, lastShown, cards, android.os.SystemClock.elapsedRealtime())
        lastForeground = foreground
        lastShown = cards
        // One shell, one morph: the first hand-off of the pass drives it (an app normally has one live card).
        found.firstOrNull()?.let { event = it }
    }
}
