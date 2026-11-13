package com.hmgchallenge.shared.departure

import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.forecast.ForecastEngine
import com.hmgchallenge.shared.forecast.ForecastInput
import com.hmgchallenge.shared.forecast.ForecastResult
import com.hmgchallenge.shared.journey.JourneySnapshot

data class DepartureEvaluation(
    val evaluatedAtEpochMillis: Long,
    val packing: PackingPlan,
    val forecast: ForecastResult?,
    val validDecision: DepartureDecision?,
    val checkedItems: Int,
    val totalItems: Int,
    val isDemo: Boolean,
)

class DeparturePlanner(private val forecastEngine: ForecastEngine = ForecastEngine()) {
    fun evaluate(care: CareRecord?, preparation: DepartureRecord, journey: DataSnapshot<JourneySnapshot>, now: Long): DepartureEvaluation {
        require(now >= 0)
        val packing = packing(preparation, care?.profile?.id, journey, now)
        val session = preparation.driveSession?.takeIf { it.babyId == care?.profile?.id }
        val forecast = care?.let { forecastEngine.evaluate(ForecastInput(it, now, journey, session)) }
        val decision = preparation.decision?.takeIf { it.tripKey == packing.tripKey &&
            now >= it.chosenAtEpochMillis && now - it.chosenAtEpochMillis < PackingPlan.CHECK_VALIDITY_MILLIS }
        return DepartureEvaluation(now, packing, forecast, decision, preparation.items.count { packing.isChecked(it, now) },
            preparation.items.size, preparation.origin == com.hmgchallenge.shared.care.CareEntrySource.DEMO || forecast?.isDemo == true)
    }

    fun packing(record: DepartureRecord, babyId: String?, snapshot: DataSnapshot<JourneySnapshot>, now: Long): PackingPlan {
        require(now >= 0)
        val settings = record.settings
        val route = snapshot.readyValue
        val observed = snapshot.observedAtEpochMillis
        val eta = route?.estimatedArrivalAtEpochMillis
        val validRoute = (snapshot.origin == DataOrigin.NAVI_SDK || snapshot.origin == DataOrigin.DEMO) &&
            snapshot.status == DataStatus.READY && observed != null && now >= observed && now - observed < ROUTE_FRESHNESS_MILLIS &&
            eta != null && eta >= now
        val durationMillis = if (validRoute) eta!! - now else settings.manualTravelMinutes?.toLong()?.times(60_000)
        val source = when {
            validRoute && (snapshot.origin == DataOrigin.DEMO || route!!.isSimulation) -> PackingDurationSource.DEMO
            validRoute -> PackingDurationSource.NAVIGATION
            durationMillis != null -> PackingDurationSource.MANUAL
            else -> PackingDurationSource.UNKNOWN
        }
        val destinationKey = if (validRoute) route!!.destination?.let {
            "${snapshot.origin}:${route.destinationLabel}:${it.latitudeDegrees}:${it.longitudeDegrees}"
        } ?: "${snapshot.origin}:${route.id}" else "manual"
        val key = "${record.outingId}:${babyId.orEmpty()}:$destinationKey:${settings.scope}"
        fun unknown(problem: PackingProblem) = PackingPlan(key, settings.scope, source, null, null, null, problem)
        if (durationMillis == null) return unknown(PackingProblem.DURATION_UNKNOWN)
        if (settings.scope == PackingScope.WHOLE_OUTING && settings.extraOutingMinutes == 0) {
            return unknown(PackingProblem.OUTING_RANGE_UNCONFIRMED)
        }
        val extraMillis = if (settings.scope == PackingScope.WHOLE_OUTING) settings.extraOutingMinutes * 60_000L else 0
        val totalMillis = if (durationMillis <= MAX_DURATION_MILLIS) durationMillis + extraMillis else return unknown(PackingProblem.TRIP_TOO_LONG)
        if (totalMillis > MAX_DURATION_MILLIS) return unknown(PackingProblem.TRIP_TOO_LONG)
        fun required(interval: Int?, spare: Int): Int? = interval?.let {
            val intervalMillis = it * 60_000L
            // Do not round the journey to minutes before ceiling the quantity at interval boundaries.
            ((totalMillis / intervalMillis) + if (totalMillis % intervalMillis > 0) 1 else 0).toInt() + spare
        }
        return PackingPlan(key, settings.scope, source, totalMillis / 60_000 + if (totalMillis % 60_000 > 0) 1 else 0,
            required(settings.feedIntervalMinutes, settings.spareFeedServings), required(settings.diaperIntervalMinutes, settings.spareDiapers))
    }

    companion object {
        const val ROUTE_FRESHNESS_MILLIS = 60_000L
        private const val MAX_DURATION_MILLIS = PackingSettings.MAX_MINUTES * 60_000L
    }
}
