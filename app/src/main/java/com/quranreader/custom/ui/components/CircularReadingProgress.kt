package com.quranreader.custom.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Full circular progress indicator for reading progress
 * Displays current page, total pages, surah name, and ayah number
 * When currentAyah is 0, it displays session name instead of surah/ayah
 */
@Composable
fun CircularReadingProgress(
    progress: Float,          // 0f..1f
    currentPage: Int,
    totalPages: Int,
    currentSurah: String,     // "Al-Imran" or session name
    currentAyah: Int,         // 33, or 0 for session mode
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "circleProgress"
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val strokeWidthDp = 14.dp
    
    val isSessionMode = currentAyah == 0

    Box(
        modifier = modifier.size(210.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(
                width = strokeWidthDp.toPx(),
                cap = StrokeCap.Round
            )
            // Track (full circle background)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            // Progress fill
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = stroke
            )
        }

        // Text inside circle
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$currentPage",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "of $totalPages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Divider mini
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            )
            Spacer(modifier = Modifier.height(6.dp))
            
            if (isSessionMode) {
                // Session mode: show session name only
                Text(
                    text = currentSurah,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Reading Session",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Normal mode: show surah and ayah
                Text(
                    text = currentSurah,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Ayah $currentAyah",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
