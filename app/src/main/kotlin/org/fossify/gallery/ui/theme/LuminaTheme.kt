package org.fossify.gallery.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LuminaLightColors = lightColorScheme(
    primary = Color(0xFF405A78),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E4F8),
    onPrimaryContainer = Color(0xFF14243A),
    secondary = Color(0xFF536176),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE2EC),
    onSecondaryContainer = Color(0xFF181C22),
    tertiary = Color(0xFF655B78),
    onTertiary = Color.White,
    background = Color(0xFFFAF9FF),
    surface = Color(0xFFFAF9FF),
    surfaceContainer = Color(0xFFF0F0F6),
    surfaceContainerHigh = Color(0xFFEAEAF0),
    surfaceContainerHighest = Color(0xFFE4E4EB)
)

private val LuminaDarkColors = darkColorScheme(
    primary = Color(0xFFB7C9E6),
    onPrimary = Color(0xFF1E314B),
    primaryContainer = Color(0xFF344A66),
    onPrimaryContainer = Color(0xFFD7E4F8),
    secondary = Color(0xFFBEC8D9),
    onSecondary = Color(0xFF283447),
    secondaryContainer = Color(0xFF3C4755),
    onSecondaryContainer = Color(0xFFDCE2EC),
    tertiary = Color(0xFFD0C1E5),
    onTertiary = Color(0xFF362D48),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A)
)

private val LuminaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LuminaTheme(
    themeMode: String = "SYSTEM",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> systemDark
    }
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) LuminaDarkColors else LuminaLightColors
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        shapes = LuminaShapes,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
