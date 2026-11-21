/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.ui.expanded

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agupta07505.smartisland.data.SmartIslandSettings
import com.agupta07505.smartisland.model.IslandNotification
import com.agupta07505.smartisland.model.IslandNotificationAction
import com.agupta07505.smartisland.ui.bounceClick
import com.agupta07505.smartisland.util.LiveCardContract
import kotlin.math.pow
import com.agupta07505.smartisland.ui.IslandText as Text

/** Choices per row in the live card's picker. */
private const val CHOICES_PER_ROW = 5

/**
 * A dot-matrix picture drawn the way the LEDs look: a black disc, lit dots in white by level, unlit LEDs faintly
 * visible. Only LEDs inside the round matrix are drawn.
 */
@Composable
internal fun DotMatrix(levels: FloatArray?, n: Int, modifier: Modifier = Modifier, unlitAlpha: Float = 0.13f) {
    Canvas(modifier = modifier.clip(CircleShape).background(Color.Black)) {
        if (levels == null || n <= 0) return@Canvas
        val side = size.minDimension
        val inset = side * 0.07f
        val pitch = (side - 2 * inset) / n
        val r = pitch * 0.38f
        val centre = (n - 1) / 2f
        val reach = (n / 2f) * (n / 2f)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = x - centre
            val dy = y - centre
            if (dx * dx + dy * dy > reach) continue
            val v = levels.getOrElse(y * n + x) { 0f }.coerceIn(0f, 1f).pow(1f / 2.2f)
            val alpha = unlitAlpha + (1f - unlitAlpha) * v
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = r,
                center = Offset(inset + (x + 0.5f) * pitch, inset + (y + 0.5f) * pitch)
            )
        }
    }
}

/** The pill's left slot: the app's picture, or its icon when it sent none. */
@Composable
internal fun LiveCardCollapsedGlyph(notification: IslandNotification?, settings: SmartIslandSettings) {
    val levels = notification?.liveMatrix
    if (levels != null && notification.liveMatrixSize > 0) {
        DotMatrix(levels, notification.liveMatrixSize, Modifier.size(22.dp), unlitAlpha = 0.1f)
    } else {
        val icon = notification?.largeIcon ?: notification?.icon
        if (icon != null) {
            Image(
                bitmap = remember(icon) { icon.asImageBitmap() },
                contentDescription = null,
                modifier = Modifier.size(22.dp).clip(CircleShape)
            )
        } else {
            com.agupta07505.smartisland.ui.NotificationGlyph(notification = notification, settings = settings)
        }
    }
}

/** The pill's right slot: the app's word or two (what is on now). */
@Composable
internal fun LiveCardCollapsedRight(notification: IslandNotification?) {
    val text = notification?.liveShortText?.takeIf { it.isNotBlank() } ?: return
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * An app's live card opened: its picture and status, the choices it offers as dot-matrix thumbnails (tap one to put it
 * on), and its control buttons. A tap on the header opens the app.
 */
@Composable
fun LiveCardExpanded(
    notification: IslandNotification,
    bottomPadding: Dp,
    onOpenNotification: () -> Unit,
    onCollapse: () -> Unit,
    settings: SmartIslandSettings = SmartIslandSettings.Default
) {
    val context = LocalContext.current
    val palette = LocalIslandPalette.current
    val choices = remember(notification.actionIntents) {
        notification.actionIntents.filter { it.role == LiveCardContract.ROLE_CHOICE && it.pendingIntent != null }
    }
    val controls = remember(notification.actionIntents) {
        notification.actionIntents.filter { it.role != LiveCardContract.ROLE_CHOICE && it.pendingIntent != null }
    }
    // The tapped choice shows as picked at once; the app's next post confirms it (or puts the old one back).
    var picked by remember(notification.key) { mutableStateOf<String?>(null) }
    val confirmed = choices.firstOrNull { it.selected }?.title
    LaunchedEffect(confirmed) { picked = null }
    val selectedTitle = picked ?: confirmed
    // The header shows the picked choice's picture until the app's own picture arrives.
    val pickedChoice = picked?.let { title -> choices.firstOrNull { it.title == title } }
    val headerLevels = pickedChoice?.matrix ?: notification.liveMatrix
    val headerSize = if (pickedChoice?.matrix != null) pickedChoice.matrixSize else notification.liveMatrixSize

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                    onOpenNotification()
                    onCollapse()
                },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (headerLevels != null && headerSize > 0) {
                DotMatrix(headerLevels, headerSize, Modifier.size(52.dp))
            } else {
                LiveCardCollapsedGlyph(notification, settings)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title.ifBlank { notification.appName },
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (notification.text.isNotBlank()) {
                    Text(
                        text = notification.text,
                        color = palette.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        if (choices.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                choices.chunked(CHOICES_PER_ROW).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { choice ->
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                                LiveChoice(
                                    choice = choice,
                                    selected = choice.title == selectedTitle,
                                    onPick = {
                                        if (choice.title != selectedTitle && notification.fireAction(context, choice)) {
                                            picked = choice.title
                                        }
                                    }
                                )
                            }
                        }
                        repeat(CHOICES_PER_ROW - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }

        if (controls.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                controls.forEach { action ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(palette.tint(0.16f))
                            .bounceClick { notification.fireAction(context, action) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = action.title,
                            color = palette.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveChoice(choice: IslandNotificationAction, selected: Boolean, onPick: () -> Unit) {
    val palette = LocalIslandPalette.current
    val ring by animateColorAsState(
        targetValue = if (selected) palette.text else Color.Transparent,
        animationSpec = spring(stiffness = 700f),
        label = "liveChoiceRing"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "liveChoiceScale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.bounceClick(onPick)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .border(2.dp, ring, CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (choice.matrix != null && choice.matrixSize > 0) {
                DotMatrix(choice.matrix, choice.matrixSize, Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(palette.tint(0.16f)))
            }
        }
        Text(
            text = choice.title,
            color = if (selected) palette.text else palette.textTertiary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
