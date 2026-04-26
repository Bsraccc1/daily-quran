package com.quranreader.custom.ui.components.animated

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.quranreader.custom.ui.theme.Motion
import com.quranreader.custom.ui.theme.MotionDuration
import com.quranreader.custom.ui.theme.MotionEasing

/**
 * A header that toggles a content section with smooth height + fade animation.
 *
 * Animation specs:
 *  - Expand: [MotionDuration.standard] (300ms) with [MotionEasing.standard]
 *  - Chevron rotation: [Motion.standard] tween
 *  - Content fade: standard easing
 *
 * Usage:
 * ```kotlin
 * ExpandableSection(title = "Audio settings", icon = Icons.Default.PlayArrow) {
 *     // section content here
 * }
 * ```
 *
 * @param title header label
 * @param icon optional leading icon
 * @param initiallyExpanded initial expansion state — persisted across recompositions via rememberSaveable
 * @param content section body shown when expanded
 */
@Composable
fun ExpandableSection(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.standard(),
        label = "ExpandableSection.chevronRotation"
    )

    val headerColor by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = Motion.standard(),
        label = "ExpandableSection.headerColor"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = headerColor
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = headerColor,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(rotation),
                tint = headerColor
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = Motion.standard()) + fadeIn(animationSpec = Motion.standard()),
            exit = shrinkVertically(animationSpec = Motion.standard()) + fadeOut(animationSpec = Motion.standard())
        ) {
            content()
        }
    }
}
