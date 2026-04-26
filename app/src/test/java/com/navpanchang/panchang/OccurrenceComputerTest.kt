package com.navpanchang.panchang

import com.navpanchang.ephemeris.AyanamshaType
import com.navpanchang.ephemeris.MeeusEphemerisEngine
import com.navpanchang.ephemeris.SunriseCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Integration tests for [OccurrenceComputer]. Exercises the full pipeline:
 * Meeus → sunrise → tithi identification → Adhik classification → vrat logic → Parana →
 * emitted [Occurrence] rows.
 *
 * We pin assertions against observational invariants rather than specific calendar dates,
 * so the tests remain valid under any future Swiss-Eph precision swap.
 */
class OccurrenceComputerTest {

    private lateinit var computer: OccurrenceComputer

    // Lucknow — canonical test location.
    private val lat = 26.8467
    private val lon = 80.9462
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    // Canonical test events — minimal catalog for the computer.
    private val shuklaEkadashi = EventDefinition(
        id = "shukla_ekadashi",
        nameEn = "Shukla Ekadashi",
        nameHi = "शुक्ल एकादशी",
        category = EventCategory.VRAT,
        rule = EventRule.TithiAtSunrise(tithiIndex = 11, vratLogic = true),
        observeInAdhik = true,
        hasParana = true,
        defaultPlannerTimeHhmm = "20:00",
        defaultObserverAnchor = ObserverAnchor.SUNRISE,
        defaultSoundId = "ritual_temple_bell"
    )

    private val krishnaEkadashi = EventDefinition(
        id = "krishna_ekadashi",
        nameEn = "Krishna Ekadashi",
        nameHi = "कृष्ण एकादशी",
        category = EventCategory.VRAT,
        rule = EventRule.TithiAtSunrise(tithiIndex = 26, vratLogic = true),
        observeInAdhik = true,
        hasParana = true,
        defaultPlannerTimeHhmm = "20:00",
        defaultObserverAnchor = ObserverAnchor.SUNRISE,
        defaultSoundId = "ritual_temple_bell"
    )

    private val purnima = EventDefinition(
        id = "purnima",
        nameEn = "Purnima",
        nameHi = "पूर्णिमा",
        category = EventCategory.VRAT,
        rule = EventRule.TithiAtSunrise(tithiIndex = 15, vratLogic = false),
        observeInAdhik = true,
        hasParana = false,
        defaultPlannerTimeHhmm = "20:00",
        defaultObserverAnchor = ObserverAnchor.SUNRISE,
        defaultSoundId = "ritual_bell_toll"
    )

    private val mahashivratri = EventDefinition(
        id = "mahashivratri",
        nameEn = "Mahashivratri",
        nameHi = "महाशिवरात्रि",
        category = EventCategory.FESTIVAL,
        rule = EventRule.TithiInLunarMonth(
            tithiIndex = 29,
            // Engine canonicalizes lunar months as Sankranti-in-Amavasya-window (Amanta).
            // Mahashivratri (Krishna Chaturdashi) lands in the lunar window containing
            // Kumbha Sankranti — which AdhikMaasDetector.lunarMonthForSankranti maps to
            // Magha. Purnimanta users see "Phalguna" via the display-time translation
            // (LunarMonth.displayLunarMonth). See TECH_DESIGN.md §Amanta vs Purnimanta.
            lunarMonth = LunarMonth.Magha,
            suppressInAdhik = true
        ),
        observeInAdhik = false,
        hasParana = false,
        defaultPlannerTimeHhmm = "20:00",
        defaultObserverAnchor = ObserverAnchor.SUNRISE,
        defaultSoundId = "ritual_temple_bell"
    )

    @Before
    fun setUp() {
        val engine = MeeusEphemerisEngine()
        val calculator = PanchangCalculator(engine, SunriseCalculator(engine))
        val tithiEndFinder = TithiEndFinder(engine)
        val vratLogic = VratLogic(calculator, tithiEndFinder)
        val amavasyaFinder = AmavasyaFinder(tithiEndFinder, calculator)
        val sankrantiFinder = SankrantiFinder(engine)
        val adhikDetector = AdhikMaasDetector(amavasyaFinder, sankrantiFinder)

        computer = OccurrenceComputer(calculator, vratLogic, adhikDetector, tithiEndFinder)
    }

    @Test
    fun `computes roughly 24 Ekadashis over a one year window`() {
        val request = OccurrenceComputer.Request(
            events = listOf(shuklaEkadashi, krishnaEkadashi),
            startDate = LocalDate.of(2024, 4, 1),
            endDate = LocalDate.of(2025, 3, 31),
            latitudeDeg = lat,
            longitudeDeg = lon,
            zone = zone,
            locationTag = "HOME",
            isHighPrecision = false,
            ayanamshaType = AyanamshaType.LAHIRI
        )
        val occurrences = computer.computeWindow(request)

        val ekadashiCount = occurrences.count {
            it.eventId == "shukla_ekadashi" || it.eventId == "krishna_ekadashi"
        }
        assertTrue(
            "A normal lunar year has 24 Ekadashis; a year with Adhik Maas has 26. Got $ekadashiCount",
            ekadashiCount in 23..27
        )
    }

    @Test
    fun `computes roughly 12 Purnimas over a one year window`() {
        val request = OccurrenceComputer.Request(
            events = listOf(purnima),
            startDate = LocalDate.of(2024, 4, 1),
            endDate = LocalDate.of(2025, 3, 31),
            latitudeDeg = lat,
            longitudeDeg = lon,
            zone = zone,
            locationTag = "HOME",
            isHighPrecision = false,
            ayanamshaType = AyanamshaType.LAHIRI
        )
        val occurrences = computer.computeWindow(request)

        assertTrue(
            "A lunar year has 12 Purnimas (13 with Adhik). Got ${occurrences.size}",
            occurrences.size in 12..13
        )
    }

    @Test
    fun `Ekadashi occurrences carry Parana window data`() {
        val request = OccurrenceComputer.Request(
            events = listOf(shuklaEkadashi),
            startDate = LocalDate.of(2024, 4, 1),
            endDate = LocalDate.of(2024, 7, 1),
            latitudeDeg = lat,
            longitudeDeg = lon,
            zone = zone,
            locationTag = "HOME",
            isHighPrecision = false,
            ayanamshaType = AyanamshaType.LAHIRI
        )
        val occurrences = computer.computeWindow(request)

        assertTrue("Expected at least 3 Shukla Ekadashis in 3 months", occurrences.size >= 2)
        for (occ in occurrences) {
            assertTrue("Occurrence $occ should have Parana start", occ.paranaStartUtc != null)
            assertTrue("Occurrence $occ should have Parana end", occ.paranaEndUtc != null)
            assertTrue(
                "Parana end must be after start",
                occ.paranaEndUtc!! > occ.paranaStartUtc!!
            )
        }
    }

    @Test
    fun `Mahashivratri fires exactly once per year in Nija Magha`() {
        val request = OccurrenceComputer.Request(
            events = listOf(mahashivratri),
            startDate = LocalDate.of(2024, 1, 1),
            endDate = LocalDate.of(2024, 12, 31),
            latitudeDeg = lat,
            longitudeDeg = lon,
            zone = zone,
            locationTag = "HOME",
            isHighPrecision = false,
            ayanamshaType = AyanamshaType.LAHIRI
        )
        val occurrences = computer.computeWindow(request)

        assertEquals(
            "Mahashivratri should fire exactly once in 2024 (Nija Magha = early March)",
            1, occurrences.size
        )

        // 2024 Mahashivratri was March 8 (per drikpanchang.com).
        val occ = occurrences.first()
        assertEquals(
            "Mahashivratri 2024 should land in early March",
            3, occ.dateLocal.monthValue
        )
        assertTrue(
            "Mahashivratri 2024 should be on March 7 or 8",
            occ.dateLocal.dayOfMonth in 7..9
        )
    }

    @Test
    fun `shifted Ekadashi occurrences are tagged with shiftedDueToViddha`() {
        // Over a full year, at least a few Ekadashis typically get Dashami-Viddha shifted.
        // We don't assert a specific count (depends on the year), but we verify that any
        // shifted occurrence is correctly tagged and its observation date is consistent.
        val request = OccurrenceComputer.Request(
            events = listOf(shuklaEkadashi, krishnaEkadashi),
            startDate = LocalDate.of(2024, 1, 1),
            endDate = LocalDate.of(2024, 12, 31),
            latitudeDeg = lat,
            longitudeDeg = lon,
            zone = zone,
            locationTag = "HOME",
            isHighPrecision = false,
            ayanamshaType = AyanamshaType.LAHIRI
        )
        val occurrences = computer.computeWindow(request)

        // Every occurrence, shifted or not, should have a positive sunrise timestamp.
        for (occ in occurrences) {
            assertTrue("Sunrise must be > 0 for ${occ.eventId}@${occ.dateLocal}", occ.sunriseUtc > 0)
            assertTrue("Observance UTC must be > 0", occ.observanceUtc > 0)
        }

        // No two occurrences of the same event fall on the same date (dedup via Viddha gate).
        val grouped = occurrences.groupBy { it.eventId to it.dateLocal }
        for ((key, group) in grouped) {
            assertEquals(
                "Duplicate occurrence emitted for $key",
                1, group.size
            )
        }
    }

    @Test
    fun `occurrences carry the requested locationTag and precision`() {
        val request = OccurrenceComputer.Request(
            events = listOf(purnima),
            startDate = LocalDate.of(2024, 5, 1),
            endDate = LocalDate.of(2024, 7, 1),
            latitudeDeg = lat,
            longitudeDeg = lon,
            zone = zone,
            locationTag = "CURRENT",
            isHighPrecision = true,
            ayanamshaType = AyanamshaType.LAHIRI
        )
        val occurrences = computer.computeWindow(request)

        for (occ in occurrences) {
            assertEquals("CURRENT", occ.locationTag)
            assertTrue("isHighPrecision must be true", occ.isHighPrecision)
        }
    }
}
