package com.navpanchang.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = BrandSaffron,
    onPrimary = BrandCream,
    primaryContainer = BrandGold,
    onPrimaryContainer = BrandTextOnLight,
    secondary = BrandDeepIndigo,
    onSecondary = BrandCream,
    background = BrandCream,
    onBackground = BrandTextOnLight,
    surface = BrandCream,
    onSurface = BrandTextOnLight
)

private val DarkColors = darkColorScheme(
    primary = BrandGold,
    onPrimary = DarkBackground,
    primaryContainer = BrandDeepIndigo,
    onPrimaryContainer = BrandCream,
    secondary = BrandSaffron,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface
)

@Composable
fun NavPanchangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NavPanchangTypography,
        content = content
    )
}
