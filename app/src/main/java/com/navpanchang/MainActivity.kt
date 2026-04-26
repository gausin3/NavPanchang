package com.navpanchang

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.navpanchang.panchang.AppLanguage
import com.navpanchang.ui.NavPanchangNavGraph
import com.navpanchang.ui.components.NavPanchangBottomBar
import com.navpanchang.ui.theme.NavPanchangTheme
import com.navpanchang.util.LanguageSwitchInterceptor
import com.navpanchang.util.TRANSLATED_LANGUAGES
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

/**
 * Hosts the three-tab Compose shell (Home / Calendar / Settings).
 *
 * See TECH_DESIGN.md §UI screens.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Applies the user's chosen companion language to the activity's resource context
     * BEFORE Compose starts resolving `R.string.*`. Reads from SharedPreferences
     * synchronously — Hilt isn't ready at this point, so we can't go through
     * `MetadataRepository`. This mirrors the same pref key the repository writes to;
     * see `MetadataRepository.appLanguage()`.
     *
     * After a Settings → Language toggle, Settings calls `recreate()` and this method
     * fires again with the new value, so Compose remounts with the right locale and
     * every `R.string.*` resolves from `values-<tag>/strings.xml`.
     *
     * For companion languages without their own resource folder yet (Marathi, Tamil,
     * Gujarati pending translation), Android's resource resolver falls back to
     * `values/strings.xml` (English) — that's correct: those users see English UI
     * chrome plus the regional companion in *data* labels via the bilingual helpers.
     */
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("navpanchang_prefs", Context.MODE_PRIVATE)
        val storedTag = prefs.getString("app_language", null)
        val storedNumerals = prefs.getString("numeral_system", null)

        if (storedTag == null && storedNumerals == null) {
            // First launch — no explicit user pick. Let Android's default resource
            // resolution apply the device locale (so a UK install sees English chrome,
            // an `hi-IN` install sees Hindi chrome). Data labels still render bilingual
            // via the display helpers (English + Hindi until the user picks otherwise).
            // Numerals fall back to the active locale's default (Devanagari for hi-IN,
            // ASCII for en — both fine.)
            super.attachBaseContext(newBase)
            return
        }

        // Resolve the picked language → effective resource-resolution tag.
        val pickedLanguageTag = storedTag
            ?.let { runCatching { AppLanguage.valueOf(it).tag }.getOrNull() }
            ?: AppLanguage.HINDI.tag
        // Marathi/Tamil/Gujarati without translations fall back to Hindi (closer script
        // than English). When a translation lands, add the tag to TRANSLATED_LANGUAGES.
        val effectiveLanguageTag =
            if (pickedLanguageTag in TRANSLATED_LANGUAGES) pickedLanguageTag else "hi"

        // Compose the final locale tag, appending the BCP-47 unicode numbering-system
        // extension `-u-nu-<system>`. The JVM's default for non-Latin-script locales
        // (Locale.forLanguageTag("hi"), etc.) is *Latin* digits — so to get native
        // numerals we MUST be explicit. Without the extension, both LATIN and NATIVE
        // would silently produce identical Latin output. See NumeralSystem kdoc.
        val numerals = storedNumerals
            ?.let { runCatching { com.navpanchang.panchang.NumeralSystem.valueOf(it) }.getOrNull() }
            ?: com.navpanchang.panchang.NumeralSystem.LATIN
        val numberingSystem = when (numerals) {
            com.navpanchang.panchang.NumeralSystem.LATIN -> "latn"
            com.navpanchang.panchang.NumeralSystem.NATIVE -> when (effectiveLanguageTag) {
                "hi", "mr" -> "deva"   // Devanagari (११)
                "ta" -> "tamldec"      // Tamil (௧௧)
                "gu" -> "gujr"         // Gujarati (૧૧)
                else -> "latn"          // English / unknown — Latin is correct
            }
        }
        val finalTag = "$effectiveLanguageTag-u-nu-$numberingSystem"
        val locale = Locale.forLanguageTag(finalTag)
        super.attachBaseContext(LanguageSwitchInterceptor.wrap(newBase, locale))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NavPanchangTheme {
                NavPanchangScaffold()
            }
        }
    }
}

@Composable
private fun NavPanchangScaffold() {
    val navController = rememberNavController()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { NavPanchangBottomBar(navController = navController) }
    ) { innerPadding ->
        NavPanchangNavGraph(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
