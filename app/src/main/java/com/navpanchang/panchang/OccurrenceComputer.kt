package com.navpanchang.panchang

import com.navpanchang.ephemeris.AyanamshaType
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Walks a date range day by day, computing the panchang at each sunrise and matching it
 * against every subscribed [EventDefinition]. Emits one [Occurrence] per event/day match,
 * with all correctness flags (Dashami-Viddha shift, Adhik suppression, Kshaya context)
 * baked in.
 *
 * **This is the hot loop** of the app's background work. A 24-month lookahead for a
 * 10-event subscription list calls [computeWindow] with ~730 days. Performance budget:
 * ~5 ms per day on a mid-range device = ~4 seconds total. The loop pre-classifies lunar
 * months once (via [AdhikMaasDetector]) and reuses the result across all events for the
 * same day, so the Sankranti scan isn't repeated per event.
 *
 * See TECH_DESIGN.md §Occurrence computation.
 */
class OccurrenceComputer @Inject constructor(
    private val panchangCalculator: PanchangCalculator,
    private val vratLogic: VratLogic,
    private val adhikMaasDetector: AdhikMaasDetector,
    private val tithiEndFinder: TithiEndFinder
) {

    /**
     * Inputs needed for a bulk computation.
     *
     * @property events the parsed [EventDefinition]s to match against each day.
     * @property startDate the first Gregorian date to evaluate (inclusive).
     * @property endDate the last Gregorian date to evaluate (inclusive).
     * @property latitudeDeg location latitude in degrees.
     * @property longitudeDeg location longitude in degrees.
     * @property zone the local timezone — used to interpret each date's sunrise.
     * @property locationTag stored on each emitted [Occurrence]. "HOME" or "CURRENT".
     * @property isHighPrecision stored on each emitted [Occurrence]. Tier 1 vs Tier 2.
     */
    data class Request(
        val events: List<EventDefinition>,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val latitudeDeg: Double,
        val longitudeDeg: Double,
        val zone: ZoneId,
        val locationTag: String,
        val isHighPrecision: Boolean,
        val ayanamshaType: AyanamshaType
    )

    /** Main entry point. */
    fun computeWindow(request: Request): List<Occurrence> {
        // Pre-classify lunar months for the entire window (plus ~45-day padding on each
        // side so edge-case Adhik/Kshaya boundaries are correctly bounded).
        val paddedStartUtc = request.startDate
            .minusDays(LUNAR_MONTH_PADDING_DAYS)
            .atStartOfDay(request.zone)
            .toInstant()
            .toEpochMilli()
        val paddedEndUtc = request.endDate
            .plusDays(LUNAR_MONTH_PADDING_DAYS)
            .atStartOfDay(request.zone)
            .toInstant()
            .toEpochMilli()
        val lunarMonths = adhikMaasDetector.classifyLunarMonthsInWindow(
            paddedStartUtc, paddedEndUtc, request.ayanamshaType
        )

        // Snapshot of which days already produced a shifted-Ekadashi occurrence — lets us
        // avoid double-emitting when the D+1 day would naturally also match tithi 11/26.
        val alreadyEmittedVrat = mutableSetOf<Pair<String, LocalDate>>() // (eventId, date)

        val results = mutableListOf<Occurrence>()

        var day = request.startDate
        while (!day.isAfter(request.endDate)) {
            val panchangAtSunrise = panchangCalculator.computeAtSunrise(
                day, request.latitudeDeg, request.longitudeDeg, request.zone, request.ayanamshaType
            )
            if (panchangAtSunrise == null) {
                day = day.plusDays(1); continue
            }
            val sunriseUtc = panchangAtSunrise.epochMillisUtc
            val tithiIndex = panchangAtSunrise.tithi.index

            // Classify this day's lunar month (if known).
            val lunarMonthWindow = lunarMonths.firstOrNull { window ->
                sunriseUtc in window.startEpochMillisUtc until window.endEpochMillisUtc
            }
            val lunarMonth = lunarMonthWindow?.month
            val isAdhik = lunarMonthWindow?.type == LunarMonthType.Adhik

            for (event in request.events) {
                val emitted = matchEvent(
                    event = event,
                    day = day,
                    sunriseUtc = sunriseUtc,
                    tithiAtSunrise = tithiIndex,
                    lunarMonth = lunarMonth,
                    isAdhik = isAdhik,
                    request = request,
                    alreadyEmittedVrat = alreadyEmittedVrat
                )
                if (emitted != null) {
                    results.add(emitted)
                    alreadyEmittedVrat.add(event.id to emitted.dateLocal)
                }
            }

            // Kshaya fallback pass: check for any tithi that began and ended between
            // today's sunrise and tomorrow's sunrise (so no sunrise owns it). If such a
            // tithi matches a subscribed vrat target, emit a safe-fallback occurrence
            // tagged with isKshayaContext = true. The UI surfaces this with a [!] icon.
            val kshayaOccurrences = detectKshayaForDay(
                day, sunriseUtc, lunarMonth, isAdhik, request, alreadyEmittedVrat
            )
            for (occ in kshayaOccurrences) {
                results.add(occ)
                alreadyEmittedVrat.add(occ.eventId to occ.dateLocal)
            }

            day = day.plusDays(1)
        }

        return results
    }

    /**
     * Detect tithis that are *Kshaya* on [day] — tithis that begin after [sunriseUtc] and
     * end before tomorrow's sunrise, so neither day "owns" them via the standard
     * tithi-at-sunrise rule.
     *
     * For each detected Kshaya tithi, iterate the subscribed events. If the target tithi
     * of any vrat rule matches the Kshaya tithi, emit a safe-fallback [Occurrence] for
     * [day] (the day the tithi *started*, which is typically when it was longest-prevailing
     * during daylight hours). Tagged `isKshayaContext = true` so the UI can explain.
     */
    private fun detectKshayaForDay(
        day: LocalDate,
        sunriseUtc: Long,
        lunarMonth: LunarMonth?,
        isAdhik: Boolean,
        request: Request,
        alreadyEmittedVrat: MutableSet<Pair<String, LocalDate>>
    ): List<Occurrence> {
        val nextSunriseUtc = panchangCalculator.sunriseUtc(
            day.plusDays(1), request.latitudeDeg, request.longitudeDeg, request.zone
        ) ?: return emptyList()

        // A Kshaya-free day has at most one tithi boundary between today's sunrise and
        // tomorrow's sunrise. Two boundaries means some tithi is entirely contained in
        // that window — that's the Kshaya tithi.
        val boundaries = tithiEndFinder.findAllTithiEndsInWindow(
            sunriseUtc, nextSunriseUtc, request.ayanamshaType
        )
        if (boundaries.size < 2) return emptyList()

        // Identify the Kshaya tithi: it's the one present between the two boundaries.
        val midpoint = (boundaries[0] + boundaries[1]) / 2
        val kshayaTithiIndex = panchangCalculator.computeAtInstant(midpoint, request.ayanamshaType).tithi.index

        val out = mutableListOf<Occurrence>()
        for (event in request.events) {
            // Only TithiAtSunrise rules with vrat logic qualify — the traditional Kshaya
            // fallback is specifically for vrats (Ekadashi especially). Other rules
            // don't use the fallback.
            val rule = event.rule
            if (rule !is EventRule.TithiAtSunrise) continue
            if (!rule.vratLogic) continue
            if (rule.tithiIndex != kshayaTithiIndex) continue

            if (alreadyEmittedVrat.contains(event.id to day)) continue
            if (isAdhik && !event.observeInAdhik) continue

            out.add(
                Occurrence(
                    eventId = event.id,
                    dateLocal = day,
                    sunriseUtc = sunriseUtc,
                    observanceUtc = sunriseUtc,
                    paranaStartUtc = null, // Parana computation deferred — Kshaya is rare
                    paranaEndUtc = null,
                    shiftedDueToViddha = false,
                    isKshayaContext = true,
                    lunarMonth = lunarMonth,
                    isAdhik = isAdhik,
                    locationTag = request.locationTag,
                    isHighPrecision = request.isHighPrecision
                )
            )
        }
        return out
    }

    /**
     * Try to match a single event against a single day. Returns the emitted [Occurrence]
     * or `null` if the event doesn't fire on this day.
     */
    private fun matchEvent(
        event: EventDefinition,
        day: LocalDate,
        sunriseUtc: Long,
        tithiAtSunrise: Int,
        lunarMonth: LunarMonth?,
        isAdhik: Boolean,
        request: Request,
        alreadyEmittedVrat: MutableSet<Pair<String, LocalDate>>
    ): Occurrence? {
        // Adhik Maas suppression for events that don't observe in Adhik months.
        if (isAdhik && !event.observeInAdhik) return null

        return when (val rule = event.rule) {
            is EventRule.TithiAtSunrise -> matchTithiAtSunrise(
                rule, event, day, sunriseUtc, tithiAtSunrise,
                lunarMonth, isAdhik, request, alreadyEmittedVrat
            )

            is EventRule.EveningTithi ->
                if (tithiAtSunrise == rule.tithiIndex) {
                    // Pradosh and similar events fire at *sunset* on the day whose
                    // sunrise falls in the target tithi. Resolve sunset for this day
                    // and use it as the observer anchor.
                    val sunsetUtc = panchangCalculator.sunsetUtc(
                        day, request.latitudeDeg, request.longitudeDeg, request.zone
                    ) ?: sunriseUtc
                    buildOccurrence(
                        event, day, sunriseUtc, sunsetUtc,
                        lunarMonth, isAdhik, request,
                        shifted = false, paranaStart = null, paranaEnd = null
                    )
                } else null

            is EventRule.TithiInLunarMonth -> {
                val monthMatches = lunarMonth == rule.lunarMonth
                if (!monthMatches) return null
                if (isAdhik && rule.suppressInAdhik) return null
                if (tithiAtSunrise != rule.tithiIndex) return null
                buildOccurrence(
                    event, day, sunriseUtc, sunriseUtc,
                    lunarMonth, isAdhik, request,
                    shifted = false, paranaStart = null, paranaEnd = null
                )
            }
        }
    }

    private fun matchTithiAtSunrise(
        rule: EventRule.TithiAtSunrise,
        event: EventDefinition,
        day: LocalDate,
        sunriseUtc: Long,
        tithiAtSunrise: Int,
        lunarMonth: LunarMonth?,
        isAdhik: Boolean,
        request: Request,
        alreadyEmittedVrat: MutableSet<Pair<String, LocalDate>>
    ): Occurrence? {
        // If this event was already emitted for today (e.g. via a previous-day Dashami-Viddha
        // shift), skip it so we don't double-count.
        if (alreadyEmittedVrat.contains(event.id to day)) return null

        // Non-vrat rules: straight tithi-at-sunrise match. But also skip if the same
        // tithi is present at tomorrow's sunrise (two-sunrise case) — the rule says
        // "observe on the day where the tithi *ends* in the next tithi", which is the
        // SECOND sunrise. The next loop iteration will emit it.
        if (!rule.vratLogic) {
            if (tithiAtSunrise != rule.tithiIndex) return null
            if (nextSunriseHasSameTithi(rule.tithiIndex, day, request)) return null
            return buildOccurrence(
                event, day, sunriseUtc, sunriseUtc,
                lunarMonth, isAdhik, request,
                shifted = false, paranaStart = null, paranaEnd = null
            )
        }

        // Vrat rules (Ekadashi): apply Dashami-Viddha and compute Parana.
        if (tithiAtSunrise != rule.tithiIndex) return null

        val viddha = vratLogic.applyDashamiViddha(
            ekadashiTithiIndex = rule.tithiIndex,
            candidateDate = day,
            latitudeDeg = request.latitudeDeg,
            longitudeDeg = request.longitudeDeg,
            zone = request.zone,
            ayanamshaType = request.ayanamshaType
        )

        // If no viddha shift, still guard against the double-sunrise case where an
        // unusually long Ekadashi (~26h) spans two sunrises without triggering viddha.
        // In that case, traditional rule says observe on the SECOND day — defer to the
        // next iteration rather than emitting now.
        if (!viddha.shifted && nextSunriseHasSameTithi(rule.tithiIndex, day, request)) {
            return null
        }

        // If the shift pushes the event to D+1, we emit with the shifted date. The sunrise
        // used for the occurrence should match the shifted date, not the original.
        val observationDate = viddha.observationDate
        val observationSunriseUtc = panchangCalculator.sunriseUtc(
            observationDate, request.latitudeDeg, request.longitudeDeg, request.zone
        ) ?: return null

        val parana = if (event.hasParana) {
            vratLogic.computeParanaWindow(
                ekadashiObservationDate = observationDate,
                ekadashiTithiIndex = rule.tithiIndex,
                latitudeDeg = request.latitudeDeg,
                longitudeDeg = request.longitudeDeg,
                zone = request.zone,
                ayanamshaType = request.ayanamshaType
            )
        } else null

        return buildOccurrence(
            event = event,
            day = observationDate,
            sunriseUtc = observationSunriseUtc,
            observanceUtc = observationSunriseUtc,
            lunarMonth = lunarMonth,
            isAdhik = isAdhik,
            request = request,
            shifted = viddha.shifted,
            paranaStart = parana?.startUtc,
            paranaEnd = parana?.endUtc
        )
    }

    /**
     * True iff tomorrow's sunrise at the same location falls inside the same [tithiIndex]
     * as today. Used to defer emission to the second day when a single tithi spans two
     * consecutive sunrises (traditional "observe on the second day" rule).
     */
    private fun nextSunriseHasSameTithi(
        tithiIndex: Int,
        day: LocalDate,
        request: Request
    ): Boolean {
        val next = panchangCalculator.computeAtSunrise(
            day.plusDays(1), request.latitudeDeg, request.longitudeDeg, request.zone, request.ayanamshaType
        ) ?: return false
        return next.tithi.index == tithiIndex
    }

    private fun buildOccurrence(
        event: EventDefinition,
        day: LocalDate,
        sunriseUtc: Long,
        observanceUtc: Long,
        lunarMonth: LunarMonth?,
        isAdhik: Boolean,
        request: Request,
        shifted: Boolean,
        paranaStart: Long?,
        paranaEnd: Long?
    ): Occurrence = Occurrence(
        eventId = event.id,
        dateLocal = day,
        sunriseUtc = sunriseUtc,
        observanceUtc = observanceUtc,
        paranaStartUtc = paranaStart,
        paranaEndUtc = paranaEnd,
        shiftedDueToViddha = shifted,
        isKshayaContext = false, // Phase 2b: wire Kshaya detection from TithiEndFinder
        lunarMonth = lunarMonth,
        isAdhik = isAdhik,
        locationTag = request.locationTag,
        isHighPrecision = request.isHighPrecision
    )

    private companion object {
        /** Pad the Adhik Maas classification window by ~45 days on each side so the
         *  first and last months of the lookahead are fully bounded by Amavasyas. */
        private const val LUNAR_MONTH_PADDING_DAYS = 45L
    }
}
