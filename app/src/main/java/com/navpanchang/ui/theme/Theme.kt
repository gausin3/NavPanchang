package com.navpanchang.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * NavPanchang Compose theme.
 *
 * **Dynamic color is OFF by default — intentionally.** Material 3's wallpaper-sampled
 * dynamic color (Android 12+) would let the device wallpaper recolor the entire app,
 * defeating the carefully-chosen devotional palette. A religious surface is not the
 * place for "match my wallpaper." See `DESIGN.md` §7 (Don'ts).
 *
 * Light and dark schemes both anchor to the manuscript-devotional palette. Slot mapping
 * matches `DESIGN.md` §2:
 *
 *  * `primary`         = saffron (vrat-active accent)
 *  * `secondary`       = indigo-eve (Planner / dusk surfaces)
 *  * `tertiary`        = gold-leaf (ritual emphasis, manuscript rules)
 *  * `error`           = sindoor (festival emphasis — repurposed; this app has no
 *                       destructive flows). Form/reliability warnings use the extended
 *                       `StateWarning` token directly.
 *  * `background/surface` = sandal-paper (light) / temple-night (dark)
 *  * `surfaceVariant`  = sandalwood (light) / temple-night (dark)
 */
private val LightColors = lightColorScheme(
    primary = BrandSaffron,
    onPrimary = BrandSaffronOn,
    primaryContainer = BrandSandalwood,
    onPrimaryContainer = BrandInk,

    secondary = BrandIndigoEve,
    onSecondary = BrandIndigoEveOn,
    secondaryContainer = BrandSandalwood,
    onSecondaryContainer = BrandInk,

    tertiary = BrandGoldLeaf,
    onTertiary = BrandSaffronOn,
    tertiaryContainer = BrandSandalwood,
    onTertiaryContainer = BrandInk,

    background = BrandSandalPaper,
    onBackground = BrandInk,

    surface = BrandSandalPaper,
    onSurface = BrandInk,
    surfaceVariant = BrandSandalwood,
    onSurfaceVariant = BrandAsh,

    outline = BrandAsh,
    outlineVariant = BrandSandalwood,

    // Repurposed — sindoor for festival emphasis. See class kdoc.
    error = BrandSindoor,
    onError = BrandSindoorOn,
    errorContainer = BrandSandalwood,
    onErrorContainer = BrandSindoor
)

private val DarkColors = darkColorScheme(
    primary = DarkBrass,
    onPrimary = DarkOnBrass,
    primaryContainer = DarkTempleNight,
    onPrimaryContainer = DarkParchment,

    secondary = DarkSaffronEmber,
    onSecondary = DarkMidnight,
    secondaryContainer = DarkTempleNight,
    onSecondaryContainer = DarkParchment,

    tertiary = DarkBrass,
    onTertiary = DarkMidnight,
    tertiaryContainer = DarkTempleNight,
    onTertiaryContainer = DarkParchment,

    background = DarkMidnight,
    onBackground = DarkParchment,

    surface = DarkTempleNight,
    onSurface = DarkParchment,
    surfaceVariant = DarkTempleNight,
    onSurfaceVariant = DarkAsh,

    outline = DarkAsh,
    outlineVariant = DarkTempleNight,

    error = DarkSindoorEmber,
    onError = DarkParchment,
    errorContainer = DarkTempleNight,
    onErrorContainer = DarkSindoorEmber
)

@Composable
fun NavPanchangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * Wallpaper-sampled dynamic color. Defaults to **false** — see class kdoc and
     * `DESIGN.md` §7. Can be flipped on for a debug build / experiment, but should
     * never default to true in production.
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) {
                androidx.compose.material3.dynamicDarkColorScheme(ctx)
            } else {
                androidx.compose.material3.dynamicLightColorScheme(ctx)
            }
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
