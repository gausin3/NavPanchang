package com.navpanchang.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * NavPanchang typography — manuscript scale per `DESIGN.md` §3.
 *
 * **Today** every slot uses `FontFamily.Default`, which on Android 8+ resolves to
 * Roboto for English and Noto Sans Devanagari as a fallback for Hindi. That's good
 * enough for v1 ship and keeps APK weight down.
 *
 * **Target pairing** (separate follow-up PR — adds ~150 KB / loading complexity):
 *  * Source Serif 4 — English headings (transitional serif, pairs with Devanagari).
 *  * Inter — English body / UI / numerals (with `tnum` for time alignment).
 *  * Tiro Devanagari Hindi — all Hindi text (manuscript shirorekha discipline).
 *
 * The scale below is tightened from Material 3 defaults — manuscript reading wants
 * less display drama and more body comfort. Body floor is **16sp** (`bodyLarge`)
 * because the primary user is older. Devanagari needs ~1.15× line-height of equivalent
 * English, so `bodyLarge` is set at 26sp / 16sp ≈ 1.625 — comfortable for matras,
 * slightly airy for English (correct trade).
 *
 * **Conventions:**
 *  * Bilingual labels (e.g., "Ekadashi · एकादशी") use the same `FontWeight` for both
 *    scripts; never bold one and normal the other.
 *  * Time displays should pin `tnum` once Inter ships — until then, monospaced
 *    fallback is acceptable for sunrise/sunset alignment.
 */
val NavPanchangTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 56.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        // Tightened slightly from M3's 24sp to 26sp — gives Devanagari matras room
        // without making English feel double-spaced.
        lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)
