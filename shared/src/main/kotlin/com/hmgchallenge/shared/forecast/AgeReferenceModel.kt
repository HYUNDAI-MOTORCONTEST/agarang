package com.hmgchallenge.shared.forecast

/**
 * General context only. A completed month cannot distinguish the first days from later weeks,
 * nor identify milk feeding method. Feeding ranges therefore never produce a personal due time.
 * CDC sources and the explicit age-band assumptions are recorded in docs/agarang/forecast.md.
 */
object AgeReferenceModel {
    const val VERSION = "public-context-2026-11-v1"
    const val CDC_BREASTFEEDING = "cdc-breastfeeding-2026"
    const val CDC_FORMULA = "cdc-formula-2026"
    const val AAP_TRAVEL = "aap-car-seat-travel"

    fun feeding(monthAge: Int): ReferenceSummary {
        require(monthAge in 0..24)
        return when (monthAge) {
            0 -> ReferenceSummary(ReferenceGuidance.EARLY_MILK_FEEDING, IntervalRangeMinutes(60, 240), listOf(CDC_BREASTFEEDING, CDC_FORMULA))
            in 1..5 -> ReferenceSummary(ReferenceGuidance.EARLY_MILK_FEEDING, IntervalRangeMinutes(120, 240), listOf(CDC_BREASTFEEDING, CDC_FORMULA))
            in 6..11 -> ReferenceSummary(ReferenceGuidance.RESPONSIVE_FEEDING_WITH_SOLIDS, sourceIds = listOf(CDC_BREASTFEEDING, CDC_FORMULA))
            else -> ReferenceSummary(ReferenceGuidance.INDIVIDUAL_ROUTINE, sourceIds = listOf(CDC_BREASTFEEDING, CDC_FORMULA))
        }
    }

    // A conservative daytime travel-planning reminder, not a safe car-seat duration or sleep limit.
    // AAP's nighttime range is deliberately not selected from an epoch timestamp without a time zone.
    fun travelBreak(monthAge: Int): ReferenceSummary? {
        require(monthAge in 0..24)
        return if (monthAge < 12) ReferenceSummary(
            ReferenceGuidance.TRAVEL_BREAK, IntervalRangeMinutes(120, 180), listOf(AAP_TRAVEL),
        ) else null
    }
}
