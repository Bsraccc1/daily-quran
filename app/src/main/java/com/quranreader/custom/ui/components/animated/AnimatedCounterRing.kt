package com.quranreader.custom.ui.components.animated

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quranreader.custom.ui.theme.MotionDuration
import com.quranreader.custom.ui.theme.MotionEasing
import androidx.compose.animation.core.tween

/**
 * Circular progress ring that animates as completed count grows toward target.
 *
 * Used in memorization (hifz) mode — visual counter for ayah repeats.
 *
 * - When [completed] increases, the ring fills clockwise.
 * - On reaching [target] the ring color animates to [completedColor] with bouncy spring.
 * - Center label is a slot — typically `"$completed / $target"`.
 *
 * Usage:
 * ```kotlin
 * AnimatedCounterRing(
 *     completed = 2,
 *     target = 3,
 *     size = 96.dp,
 *     label = { count, max -> Text("$count / $max") }
 * )
 * ```
 *
 * @param completed completed count (0..target)
 * @param target target count
 * @param size diameter of the ring
 * @param strokeWidth ring stroke width
 * @param activeColor color while progress is in-flight; defaults to theme primary
 * @param trackColor color of the un-filled track; defaults to theme surfaceVariant
 * @param completedColor color when [completed] >= [target]; defaults to theme tertiary
 * @param label center content composable receiving (completed, target)
 */
@Composable
fun AnimatedCounterRing(
    completed: Int,
    target: Int,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    strokeWidth: Dp = 8.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    completedColor: Color = MaterialTheme.colorScheme.tertiary,
    label: @Composable (completed: Int, target: Int) -> Unit = { c, t -> DefaultCounterLabel(c, t) }
) {
    val safeTarget = target.coerceAtLeast(1)
    val safeCompleted = completed.coerceIn(0, safeTarget)
    val isComplete = safeCompleted >= safeTarget

    val progress by animateFloatAsState(
        targetValue = safeCompleted.toFloat() / safeTarget.toFloat(),
        animationSpec = if (isComplete) {
            spring(stiffness = 80f, dampingRatio = 0.6f)
        } else {
            tween(MotionDuration.standard, easing = MotionEasing.standard)
        },
        label = "AnimatedCounterRing.progress"
    )

    val ringColor by animateColorAsState(
        targetValue = if (isComplete) completedColor else activeColor,
        animationSpec = tween(MotionDuration.standard, easing = MotionEasing.standard),
        label = "AnimatedCounterRing.ringColor"
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            // Track
            drawCircle(
                color = trackColor,
                radius = (this.size.minDimension - strokeWidth.toPx()) / 2f,
                style = stroke
            )
            // Progress arc — start at top (rotate -90deg) and sweep clockwise
            rotate(degrees = -90f) {
                drawArc(
                    color = ringColor,
                    startAngle = 0f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = stroke
                )
            }
        }
        label(safeCompleted, safeTarget)
    }
}

@Composable
private fun DefaultCounterLabel(completed: Int, target: Int) {
    Text(
        text = "$completed / $target",
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center
    )
}
