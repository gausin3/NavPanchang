package com.navpanchang.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.navpanchang.data.db.NavPanchangDb
import com.navpanchang.data.db.entities.CalcMetadataEntity
import com.navpanchang.ephemeris.AyanamshaType
import com.navpanchang.panchang.AppLanguage
import com.navpanchang.panchang.LunarConvention
import com.navpanchang.panchang.NumeralSystem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room-integration tests for [MetadataRepository]. Focused on the canonical-resolution
 * helpers (`ayanamshaType()`, `lunarConvention()`) and their forward-compatible defaults
 * — the bits hot loops depend on for thread-safe, single-fetch resolution.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class MetadataRepositoryTest {

    private lateinit var db: NavPanchangDb
    private lateinit var repository: MetadataRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // Wipe SharedPreferences between tests so app-language fallback assertions are
        // deterministic — Robolectric persists prefs across @Test methods otherwise.
        context.getSharedPreferences("navpanchang_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
        db = Room.inMemoryDatabaseBuilder(context, NavPanchangDb::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MetadataRepository(db.metadataDao(), context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `lunarConvention defaults to PURNIMANTA on a fresh install`() = runTest {
        assertEquals(LunarConvention.PURNIMANTA, repository.lunarConvention())
    }

    @Test
    fun `lunarConvention round-trips AMANTA after setLunarConvention`() = runTest {
        repository.setLunarConvention(LunarConvention.AMANTA)
        assertEquals(LunarConvention.AMANTA, repository.lunarConvention())
    }

    @Test
    fun `lunarConvention falls back to PURNIMANTA on stored junk value`() = runTest {
        // Simulate a forward-compat downgrade scenario: a future build wrote some unknown
        // value, then the user downgraded. We must not crash — fall back to the safe default.
        db.metadataDao().upsert(CalcMetadataEntity(lunarConvention = "AMANTA_PLUS_UNICORN"))
        assertEquals(LunarConvention.PURNIMANTA, repository.lunarConvention())
    }

    @Test
    fun `ayanamshaType defaults to LAHIRI on a fresh install`() = runTest {
        assertEquals(AyanamshaType.LAHIRI, repository.ayanamshaType())
    }

    @Test
    fun `ayanamshaType falls back to LAHIRI on stored junk value`() = runTest {
        db.metadataDao().upsert(CalcMetadataEntity(ayanamshaType = "TROPICAL_MAYBE"))
        assertEquals(AyanamshaType.LAHIRI, repository.ayanamshaType())
    }

    @Test
    fun `appLanguage defaults to HINDI on a fresh install`() {
        assertEquals(AppLanguage.HINDI, repository.appLanguage())
    }

    @Test
    fun `appLanguage round-trips after setAppLanguage`() {
        repository.setAppLanguage(AppLanguage.MARATHI)
        assertEquals(AppLanguage.MARATHI, repository.appLanguage())
    }

    @Test
    fun `numeralSystem defaults to LATIN on a fresh install`() {
        assertEquals(NumeralSystem.LATIN, repository.numeralSystem())
    }

    @Test
    fun `numeralSystem round-trips after setNumeralSystem`() {
        repository.setNumeralSystem(NumeralSystem.DEVANAGARI)
        assertEquals(NumeralSystem.DEVANAGARI, repository.numeralSystem())
    }

    @Test
    fun `numeralSystem falls back to LATIN on stored junk value`() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("navpanchang_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("numeral_system", "ROMAN_NUMERALS_VII")
            .apply()
        assertEquals(NumeralSystem.LATIN, repository.numeralSystem())
    }

    @Test
    fun `appLanguage falls back to HINDI on stored junk value`() {
        // Simulate a future build that wrote an unknown language enum value, then the
        // user downgraded. The repository must not crash — fall back to the safe default.
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("navpanchang_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("app_language", "PUNJABI_FROM_THE_FUTURE")
            .apply()
        assertEquals(AppLanguage.HINDI, repository.appLanguage())
    }
}
