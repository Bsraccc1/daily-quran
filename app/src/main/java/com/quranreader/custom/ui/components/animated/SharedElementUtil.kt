package com.quranreader.custom.ui.components.animated

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.quranreader.custom.ui.theme.Motion

/**
 * Shared-element-style transition wrapper for Compose 1.6.x (BOM 2024.05.00).
 *
 * **Note**: True `SharedTransitionLayout` ships with Compose Animation 1.7.0+
 * (BOM 2024.09.00+). On the current BOM (2024.05.00), this util provides a
 * visually-similar effect using [AnimatedContent] with cross-fade + scale,
 * scoped via [SharedElementContainer].
 *
 * When the BOM is upgraded, replace [SharedElementContainer] with the real
 * `SharedTransitionLayout` and `Modifier.sharedElement(...)` API.
 *
 * Usage in nav graph (e.g. Juz card → Reading screen):
 * ```kotlin
 * SharedElementContainer(targetState = currentScreen) { screen ->
 *     when (screen) {
 *         is Screen.Juz -> JuzScreen(onSurahClick = { /* navigate */ })
 *         is Screen.Reading -> MushafReaderScreen()
 *     }
 * }
 * ```
 */

/** CompositionLocal exposing the current shared-element scope, if any. */
val LocalSharedElementScope = staticCompositionLocalOf<SharedElementScope?> { null }

/** Marker scope passed through CompositionLocal to mark elements participating in transitions. */
class SharedElementScope internal constructor(
    /** The current AnimatedVisibilityScope, used by `Modifier.sharedKey` to detect transitions. */
    val animatedVisibilityScope: AnimatedVisibilityScope
)

/**
 * Container providing a shared-element-style scope for child screens/composables.
 * Use [targetState] to drive transitions: when [targetState] changes,
 * the previous content fades+scales out while the new content fades+scales in.
 *
 * Inside [content], use `LocalSharedElementScope.current` and `Modifier.sharedKey(...)`
 * to participate in the transition.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun <T> SharedElementContainer(
    targetState: T,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<T>.() -> ContentTransform = { defaultSharedTransform<T>() },
    contentKey: (targetState: T) -> Any? = { it },
    content: @Composable AnimatedVisibilityScope.(state: T) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = transitionSpec,
        contentKey = contentKey,
        label = "SharedElementContainer"
    ) { state ->
        // Provide AnimatedVisibilityScope via CompositionLocal for child composables
        // that may want to sync animations with the container.
        CompositionLocalProvider(
            LocalSharedElementScope provides SharedElementScope(this)
        ) {
            content(state)
        }
    }
}

/**
 * Default transform: cross-fade + slight scale-in / scale-out, mimicking shared element feel.
 */
@OptIn(ExperimentalAnimationApi::class)
private fun <T> AnimatedContentTransitionScope<T>.defaultSharedTransform(): ContentTransform {
    val enter = fadeIn(animationSpec = Motion.emphasizedDecelerate()) +
        scaleIn(
            initialScale = 0.92f,
            animationSpec = Motion.emphasizedDecelerate()
        )
    val exit = fadeOut(animationSpec = Motion.emphasizedAccelerate()) +
        scaleOut(
            targetScale = 1.04f,
            animationSpec = Motion.emphasizedAccelerate()
        )
    return enter togetherWith exit
}

/**
 * Stub `Modifier.sharedKey` extension: today this is a no-op marker that
 * composables can attach to declare their intent to share across transitions.
 *
 * When BOM upgrades to 2024.09.00, swap the implementation to call the real
 * `Modifier.sharedElement(state = rememberSharedContentState(key), ...)`.
 *
 * Until then the marker enables call-sites to be ready without compile errors.
 */
fun Modifier.sharedKey(
    @Suppress("UNUSED_PARAMETER") key: String
): Modifier = this
