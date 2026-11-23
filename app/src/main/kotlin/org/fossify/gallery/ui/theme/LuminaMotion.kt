package org.fossify.gallery.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * Central motion tokens for Lumina.
 *
 * The spring profiles and restrained fade timing are adapted from the MonoMail
 * design-system reference, then named locally so Lumina can tune them independently.
 */
object LuminaMotion {
    fun <T> bouncy() = spring<T>(
        dampingRatio = 0.65f,
        stiffness = 800f
    )

    fun <T> gentle() = spring<T>(
        dampingRatio = 0.8f,
        stiffness = 400f
    )

    fun <T> snappy() = spring<T>(
        dampingRatio = 0.75f,
        stiffness = 1200f
    )

    val contentEnter = tween<Float>(
        durationMillis = 250,
        easing = LinearOutSlowInEasing
    )

    val contentExit = tween<Float>(
        durationMillis = 200,
        easing = FastOutSlowInEasing
    )

    fun screenSlide() = tween<IntOffset>(
        durationMillis = 350,
        easing = FastOutSlowInEasing
    )

    const val listStaggerDelayMillis = 30
    const val maxListStaggerDelayMillis = 300
}
