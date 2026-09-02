package com.charles.cruiseapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Tropical / ocean palette — sunny beach-day light mode, deep-ocean-at-night dark mode.

private val LightColors = lightColorScheme(
    primary = Color(0xFF00897B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFAEF3E6),
    onPrimaryContainer = Color(0xFF00382F),
    secondary = Color(0xFFFF6F59),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDACF),
    onSecondaryContainer = Color(0xFF5C1F0E),
    tertiary = Color(0xFFFFB300),
    onTertiary = Color(0xFF3D2E00),
    tertiaryContainer = Color(0xFFFFEAB0),
    onTertiaryContainer = Color(0xFF4A3800),
    background = Color(0xFFFFFBF3),
    onBackground = Color(0xFF1B1C1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1C1A),
    surfaceVariant = Color(0xFFE3F3EF),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF7A938F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF52DDC9),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005045),
    onPrimaryContainer = Color(0xFF8CF9E5),
    secondary = Color(0xFFFF9A7D),
    onSecondary = Color(0xFF5C1A00),
    secondaryContainer = Color(0xFF7A2E14),
    onSecondaryContainer = Color(0xFFFFDBCC),
    tertiary = Color(0xFFFFCE6B),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4200),
    onTertiaryContainer = Color(0xFFFFDFA0),
    background = Color(0xFF0A1C2A),
    onBackground = Color(0xFFE0E8EA),
    surface = Color(0xFF102839),
    onSurface = Color(0xFFE0E8EA),
    surfaceVariant = Color(0xFF1C3B4E),
    onSurfaceVariant = Color(0xFFB9CCD3),
    outline = Color(0xFF6C8894),
)

// Sunset/ocean accent gradients used by hero banners across screens.
object CruiseGradients {
    val oceanSunset = listOf(Color(0xFF00897B), Color(0xFF00B4A6), Color(0xFFFF9457))
    val deepOcean = listOf(Color(0xFF0A1C2A), Color(0xFF0D4F52), Color(0xFF14746B))
}

private val CruiseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val CruiseTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.15.sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
fun CruiseTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = CruiseShapes,
        typography = CruiseTypography,
        content = content,
    )
}
