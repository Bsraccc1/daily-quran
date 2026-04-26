package com.quranreader.custom.ui.components.animated

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.quranreader.custom.ui.theme.Motion

/**
 * Animated Material 3 card with scale-on-press + ripple.
 *
 * Default: scales to 0.97 when pressed, animates back over [Motion.short] (100ms).
 * Card's built-in clickable wires up the ripple feedback automatically.
 *
 * Usage:
 * ```kotlin
 * AnimatedCard(onClick = { /* ... */ }) {
 *     Text("Surah Al-Fatihah", modifier = Modifier.padding(16.dp))
 * }
 * ```
 *
 * @param onClick callback when the card is tapped. If `null`, the card is non-interactive.
 * @param shape outer card shape — defaults to Material 3 medium rounded corners.
 * @param colors card surface + content colors — defaults to elevated card colors.
 * @param contentPadding padding around the content slot.
 * @param pressedScale scale factor when the card is pressed; default 0.97.
 */
@Composable
fun AnimatedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    colors: CardColors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    pressedScale: Float = 0.97f,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = Motion.short(),
        label = "AnimatedCard.scale"
    )

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.scale(scale),
            shape = shape,
            colors = colors,
            interactionSource = interactionSource
        ) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    } else {
        Card(
            modifier = modifier.scale(scale),
            shape = shape,
            colors = colors
        ) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}
