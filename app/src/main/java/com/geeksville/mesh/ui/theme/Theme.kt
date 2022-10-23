package com.geeksville.mesh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Purple200 = Color(0xFFBB86FC)
private val Purple500 = Color(0xFF6200EE)
private val Purple700 = Color(0xFF3700B3)
private val Teal200 = Color(0xFF03DAC5)
private val LightGray = Color(0xFFFAFAFA)
private val LightSkyBlue = Color(0x99A6D1E6)
private val LightBlue = Color(0xFFA6D1E6)
private val SkyBlue = Color(0xFF57AEFF)
private val LightPink = Color(0xFFFFE6E6)
private val LightGreen = Color(0xFFCFE8A9)
private val LightRed = Color(0xFFFFB3B3)

private val darkColors = darkColors(
    primary = Purple200,
    primaryVariant = Purple700,
    secondary = Teal200
)

private val lightColors = lightColors(
    primary = SkyBlue,
    primaryVariant = LightSkyBlue,
    secondary = Teal200

    /* Other default colors to override
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    */
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        darkColors
    } else {
        lightColors
    }

    MaterialTheme(
        colors = colors
    ) {
        content()
    }
}