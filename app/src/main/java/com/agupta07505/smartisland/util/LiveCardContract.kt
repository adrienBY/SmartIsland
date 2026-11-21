/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.util

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.agupta07505.smartisland.model.IslandMode
import com.agupta07505.smartisland.model.IslandNotification

/**
 * "Live card": an app opts its ongoing notification into the island (first user: Phosphore, which drives the Glyph
 * Matrix). The notification then lives in the island only (taken out of the status bar and the shade), the pill shows
 * its dot-matrix picture, and the card offers its actions: "choice" actions as a strip of dot-matrix thumbnails (one
 * marked selected) and "control" actions as buttons.
 *
 * Notification extras:
 * - [EXTRA_LIVE_CARD] (boolean): opts in.
 * - [EXTRA_SHORT_TEXT] (string): a word or two for the pill's right side.
 * - [EXTRA_MATRIX] (float[] of n×n levels 0..1) + [EXTRA_MATRIX_SIZE] (n): the picture.
 * - [EXTRA_ENDED] (boolean): the card is over. Apps re-post their notification once with it before taking it down,
 *   because the island already cancelled it from the shade and would not hear about the removal.
 *
 * Action extras: [ACTION_ROLE] ([ROLE_CONTROL] / [ROLE_CHOICE]), [ACTION_SELECTED], and [EXTRA_MATRIX] /
 * [EXTRA_MATRIX_SIZE] for a choice's thumbnail.
 *
 * [SYNC]: after the island restarts (its cards are gone, the notifications were cancelled), every app with a receiver
 * for it is asked to post its live cards again; [EXTRA_SYNC_IDS] lists the notification ids the island still shows
 * for that app, so the app can also end cards it no longer runs.
 */
object LiveCardContract {
    const val EXTRA_LIVE_CARD = "smartisland.live_card"
    const val EXTRA_SHORT_TEXT = "smartisland.short_text"
    const val EXTRA_MATRIX = "smartisland.matrix"
    const val EXTRA_MATRIX_SIZE = "smartisland.matrix_size"
    const val EXTRA_ENDED = "smartisland.ended"
    const val ACTION_ROLE = "smartisland.role"
    const val ACTION_SELECTED = "smartisland.selected"
    const val ROLE_CONTROL = "control"
    const val ROLE_CHOICE = "choice"
    const val SYNC = "com.agupta07505.smartisland.action.LIVE_CARD_SYNC"
    const val EXTRA_SYNC_IDS = "ids"

    /** Largest matrix accepted (the Glyph Matrix is 13×13 or 25×25). */
    private const val MAX_MATRIX = 64

    fun isLiveCard(notification: Notification): Boolean =
        runCatching { notification.extras?.getBoolean(EXTRA_LIVE_CARD, false) == true }.getOrDefault(false)

    fun isEnded(notification: Notification): Boolean =
        runCatching { notification.extras?.getBoolean(EXTRA_ENDED, false) == true }.getOrDefault(false)

    fun shortText(notification: Notification): String =
        runCatching { notification.extras?.getCharSequence(EXTRA_SHORT_TEXT)?.toString() }.getOrNull().orEmpty()

    /** The picture in a bundle (notification or action extras), or null when absent or malformed. */
    fun matrix(extras: android.os.Bundle?): Pair<FloatArray, Int>? = runCatching {
        val levels = extras?.getFloatArray(EXTRA_MATRIX) ?: return null
        val n = extras.getInt(EXTRA_MATRIX_SIZE, 0)
        if (n !in 1..MAX_MATRIX || levels.size != n * n) null else levels to n
    }.getOrNull()

    /** The notification id inside a StatusBarNotification key ("user|package|id|tag|uid"). */
    fun idFromKey(key: String): Int? = key.split('|').getOrNull(2)?.toIntOrNull()

    /** Asks every app that takes part to post its live cards again (and to end the ones it no longer runs). */
    fun requestSync(context: Context, cards: List<IslandNotification>) {
        runCatchingLogged("LiveCardContract", "Live card sync failed") {
            @Suppress("DEPRECATION")
            val receivers = context.packageManager.queryBroadcastReceivers(Intent(SYNC), 0)
            for (info in receivers) {
                val ai = info.activityInfo ?: continue
                val ids = cards.filter { it.mode == IslandMode.LiveCard && it.packageName == ai.packageName }
                    .mapNotNull { idFromKey(it.key) }
                    .toIntArray()
                context.sendBroadcast(
                    Intent(SYNC).setComponent(ComponentName(ai.packageName, ai.name)).putExtra(EXTRA_SYNC_IDS, ids)
                )
            }
        }
    }
}
