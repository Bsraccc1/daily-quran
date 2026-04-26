package com.quranreader.custom.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

/**
 * Shared motion specs for Quran Reader v3.0.
 *
 * Material 3 expressive motion system:
 *  - **Emphasized** — most prominent transitions (e.g. screen-level navigation, hifz overlay open)
 *  - **Standard** — basic transitions (e.g. card expansion, chip selection)
 *  - **Short** — quick micro-interactions (e.g. ripple, scale-on-press, ayah highlight fade)
 *
 * Use [MotionDuration] together with [MotionEasing] when constructing animation specs:
 * ```kotlin
 * animateColorAsState(
 *     targetValue = color,
 *     animationSpec = tween(MotionDuration.short, easing = MotionEasing.standard)
 * )
 * ```
 *
 * Or use the convenience [Motion.short], [Motion.standard], [Motion.emphasized] tween factories
 * for common cases:
 * ```kotlin
 * animateColorAsState(targetValue = color, animationSpec = Motion.short())
 * ```
 *
 * Reading screen circular UI must NOT change structurally — only use [Motion.short] for
 * micro-interactions on existing elements.
 */
object MotionDuration {
    /** 500ms — emphasized transitions (screen-level, modal open). */
    const val emphasized: Int = 500

    /** 300ms — standard transitions (card expand, list updates). */
    const val standard: Int = 300

    /** 100ms — short transitions (ripple, scale-on-press, ayah highlight fade). */
    const val short: Int = 100

    /** 200ms — emphasized accelerate (exit transitions). */
    const val emphasizedAccelerate: Int = 200

    /** 400ms — emphasized decelerate (enter transitions). */
    const val emphasizedDecelerate: Int = 400
}

/**
 * Material 3 easing curves matching [Material 3 motion guidelines](https://m3.material.io/styles/motion/easing-and-duration/tokens-specs).
 */
object MotionEasing {
    /** Emphasized — most expressive transitions. */
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Emphasized accelerate — exits with acceleration. */
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Emphasized decelerate — enters with deceleration. */
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Standard — basic transitions. */
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Standard accelerate — basic exits. */
    val standardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** Standard decelerate — basic enters. */
    val standardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
}

/**
 * Convenience tween factories combining [MotionDuration] + [MotionEasing].
 *
 * Pattern: `animationSpec = Motion.standard()` returns `tween<T>(300, easing = standard)`.
 */
object Motion {
    /** Short tween — 100ms, standard easing. For micro-interactions. */
    fun <T> short(easing: Easing = MotionEasing.standard) =
        tween<T>(durationMillis = MotionDuration.short, easing = easing)

    /** Standard tween — 300ms, standard easing. For basic transitions. */
    fun <T> standard(easing: Easing = MotionEasing.standard) =
        tween<T>(durationMillis = MotionDuration.standard, easing = easing)

    /** Emphasized tween — 500ms, emphasized easing. For prominent transitions. */
    fun <T> emphasized(easing: Easing = MotionEasing.emphasized) =
        tween<T>(durationMillis = MotionDuration.emphasized, easing = easing)

    /** Emphasized accelerate — 200ms. For exit animations. */
    fun <T> emphasizedAccelerate() =
        tween<T>(
            durationMillis = MotionDuration.emphasizedAccelerate,
            easing = MotionEasing.emphasizedAccelerate
        )

    /** Emphasized decelerate — 400ms. For enter animations. */
    fun <T> emphasizedDecelerate() =
        tween<T>(
            durationMillis = MotionDuration.emphasizedDecelerate,
            easing = MotionEasing.emphasizedDecelerate
        )
}
