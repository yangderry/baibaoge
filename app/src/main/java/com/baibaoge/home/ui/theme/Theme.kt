package com.baibaoge.home.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = TeaGreen,
    onPrimary = Color.White,
    primaryContainer = TeaGreenContainer,
    onPrimaryContainer = TeaGreenDark,
    secondary = WarmBeige,
    onSecondary = Ink,
    secondaryContainer = WarmSurfaceVariant,
    onSecondaryContainer = Ink,
    tertiary = TeaGreen,
    background = RiceWhite,
    onBackground = Ink,
    surface = WarmSurface,
    onSurface = Ink,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = InkSoft,
    surfaceContainer = WarmSurface,
    outline = WarmOutline,
    error = ExpireRed
)

private val DarkColors = darkColorScheme(
    primary = TeaGreenLight,
    onPrimary = Color(0xFF142016),
    primaryContainer = TeaGreenContainerDark,
    onPrimaryContainer = TeaGreenLight,
    secondary = WarmBeige,
    onSecondary = InkDark,
    secondaryContainer = WarmSurfaceVariantDark,
    onSecondaryContainer = InkDark,
    tertiary = TeaGreenLight,
    background = RiceWhiteDark,
    onBackground = InkDark,
    surface = WarmSurfaceDark,
    onSurface = InkDark,
    surfaceVariant = WarmSurfaceVariantDark,
    onSurfaceVariant = InkSoftDark,
    surfaceContainer = WarmSurfaceDark,
    outline = WarmOutlineDark,
    error = Color(0xFFE88A8A)
)

@Composable
fun BaibaogeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    // 状态栏图标随明暗模式调整
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        window.statusBarColor = colorScheme.background.toArgb()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
