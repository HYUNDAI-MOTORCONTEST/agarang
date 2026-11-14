package com.hmgchallenge.shared.forecast

/** Product heuristics, not pediatric thresholds. See docs/agarang/forecast.md for scope and provenance. */
data class ForecastPolicy(
    val version: String = "planning-policy-v1",
    val cautionMinutes: Int = 60,
    val imminentMinutes: Int = 15,
    val arrivalSoonMinutes: Int = 15,
    val maxRouteAgeMinutes: Int = 5,
    val historyLookbackMinutes: Int = 14 * 24 * 60,
    val maxAnchorAgeMinutes: Int = 24 * 60,
    val minimumHistoryIntervals: Int = 3,
    val maximumHistoryIntervals: Int = 6,
) {
    init {
        require(version.isNotBlank())
        require(imminentMinutes >= 0 && cautionMinutes >= imminentMinutes)
        require(arrivalSoonMinutes >= 0 && maxRouteAgeMinutes > 0)
        require(historyLookbackMinutes > 0 && maxAnchorAgeMinutes > 0)
        require(minimumHistoryIntervals > 0 && maximumHistoryIntervals >= minimumHistoryIntervals)
    }
}
