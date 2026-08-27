package com.charles.cruiseapp.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF74F8E7),
    secondary = Color(0xFF4A635F),
    tertiary = Color(0xFF456179),
    background = Color(0xFFFAFDFC),
    surface = Color.White
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DD8C8),
    secondary = Color(0xFFB1CCC8),
    tertiary = Color(0xFFAEC9E5)
)

@Composable
fun CruiseTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable ()->Unit){
    MaterialTheme(colorScheme = if(darkTheme) DarkColors else LightColors, content = content)
}
