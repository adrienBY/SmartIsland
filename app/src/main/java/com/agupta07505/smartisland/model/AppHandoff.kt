/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.model

/**
 * The hand-off between an app and its live card, like the native Dynamic Island: leaving Spotify (home gesture, app
 * switch) makes its music card come *out of the camera* — the shell grows from a camera-sized core to the full pill
 * while the album art and bars are revealed from the centre — instead of the content fading in on a full-width pill
 * with the generic arrival pulse. Opening the app again does the reverse: the card shrinks back into the camera.
 *
 * Pure logic (no Android types) so it can be unit tested.
 */
enum class HandoffDirection {
    /** The card's app left the foreground: its card comes out of the camera. */
    OutOfApp,

    /** The card's app came to the foreground: its card goes back into the camera. */
    IntoApp
}

data class HandoffEvent(val key: String, val direction: HandoffDirection, val atMs: Long)

/** What [AppHandoff.detect] needs to know about one card. */
data class HandoffCard(val key: String, val packageName: String, val live: Boolean)

object AppHandoff {

    /**
     * Grow out of the camera. Roughly the length of the launcher's app-close animation (~420 ms to settle at 1x) with
     * the small overshoot the native island has, so the pill lands while the app icon lands.
     */
    const val GROW_DAMPING = 0.72f
    const val GROW_STIFFNESS = 170f

    /** Shrink into the camera: quicker and without bounce, it has to be gone before the app's window covers it. */
    const val SHRINK_DAMPING = 1f
    const val SHRINK_STIFFNESS = 560f

    /** An event is only matched to a card's arrival (to skip the generic pulse) within this time. */
    const val MATCH_WINDOW_MS = 1_500L

    /** The content starts at this scale in the camera and grows to 1. */
    const val CONTENT_START_SCALE = 0.62f

    /** Share of the morph before the content starts to show, and the share over which it fades in. */
    const val CONTENT_DELAY = 0.18f
    const val CONTENT_FADE_SPAN = 0.5f

    /**
     * Cards whose visibility changed *because the foreground app changed*. A live card of the app that just left
     * becomes visible ([HandoffDirection.OutOfApp]); a live card of the app that just opened stops being visible
     * ([HandoffDirection.IntoApp]). Cards that appear or go for any other reason are not hand-offs.
     */
    fun detect(
        previousForeground: String?,
        foreground: String?,
        previouslyShown: List<HandoffCard>,
        shown: List<HandoffCard>,
        nowMs: Long
    ): List<HandoffEvent> {
        if (previousForeground == foreground) return emptyList()
        val before = previouslyShown.mapTo(HashSet()) { it.key }
        val after = shown.mapTo(HashSet()) { it.key }
        val out = shown.filter { c ->
            c.live && c.key !in before && !previousForeground.isNullOrEmpty() && c.packageName == previousForeground
        }.map { HandoffEvent(it.key, HandoffDirection.OutOfApp, nowMs) }
        val into = previouslyShown.filter { c ->
            c.live && c.key !in after && !foreground.isNullOrEmpty() && c.packageName == foreground
        }.map { HandoffEvent(it.key, HandoffDirection.IntoApp, nowMs) }
        return out + into
    }

    /** True when [event] is the hand-off that brought [key] onto the island (so its arrival pulse is skipped). */
    fun explainsArrival(event: HandoffEvent?, key: String?, nowMs: Long): Boolean =
        event != null && key != null && event.direction == HandoffDirection.OutOfApp && event.key == key &&
            nowMs - event.atMs in 0..MATCH_WINDOW_MS

    /**
     * Shell width for morph progress [p] (0 = camera core, 1 = full pill; the grow spring overshoots a little past 1).
     * Never narrower than the core, so the shrink ends as a circle on the camera.
     */
    fun width(coreDp: Float, fullDp: Float, p: Float): Float {
        if (p >= 1f) return fullDp + (fullDp - coreDp) * (p - 1f) * OVERSHOOT_GAIN
        return coreDp + (fullDp - coreDp) * p.coerceAtLeast(0f)
    }

    /** Opacity of the pill's content at morph progress [p]: it waits until the core has opened up a little. */
    fun contentAlpha(p: Float): Float = ((p - CONTENT_DELAY) / CONTENT_FADE_SPAN).coerceIn(0f, 1f)

    /** Scale of the pill's content at morph progress [p] (follows the overshoot, capped). */
    fun contentScale(p: Float): Float =
        (CONTENT_START_SCALE + (1f - CONTENT_START_SCALE) * p).coerceIn(CONTENT_START_SCALE, 1.03f)

    /** The overshoot past the full width is damped: the native island stretches only a few dp. */
    private const val OVERSHOOT_GAIN = 0.6f
}
