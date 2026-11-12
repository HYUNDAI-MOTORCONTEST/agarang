package com.hmgchallenge.shared.forecast

import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.journey.JourneySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Records are confirmed local writes. A new ETA, route loss or timer tick always re-evaluates the
 * same engine, without making navigation availability a prerequisite for care-based forecasts.
 */
fun journeyForecasts(
    records: Flow<CareRecord?>,
    journeys: Flow<DataSnapshot<JourneySnapshot>>,
    refreshTicks: Flow<Long>,
    engine: ForecastEngine = ForecastEngine(),
    clock: () -> Long = System::currentTimeMillis,
): Flow<ForecastResult?> = combine(records, journeys, refreshTicks) { care, journey, _ ->
    // Read the clock on every input change, rather than reusing a previous timer tick. Otherwise a
    // newly observed SDK callback would incorrectly look as though it came from the future.
    care?.let { engine.evaluate(ForecastInput(it, clock(), journey)) }
}
