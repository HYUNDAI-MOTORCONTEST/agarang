package com.hmgchallenge.app.departure

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import com.hmgchallenge.app.care.AtomicCareRecordStore
import com.hmgchallenge.shared.care.CareEventType
import com.hmgchallenge.shared.care.CareRepository
import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.departure.DeparturePlanner
import com.hmgchallenge.shared.departure.DepartureRepository
import com.hmgchallenge.shared.departure.DepartureStoreStatus
import com.hmgchallenge.shared.departure.PackingSettings
import com.hmgchallenge.shared.journey.JourneySnapshot
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Actual AtomicFile/persistence round trip in an isolated directory. No SDK or personal-record writes. */
class DepartureSmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        val originalCare = File(targetContext.filesDir, "care-record.json")
        fun originalHash() = originalCare.takeIf { it.exists() }?.readBytes()?.let { MessageDigest.getInstance("SHA-256").digest(it).toList() }
        val before = originalHash()
        val directory = File(targetContext.filesDir, "issue7-qa-${System.nanoTime()}")
        var code = Activity.RESULT_OK
        try {
            check(directory.mkdir())
            runBlocking {
                var now = System.currentTimeMillis()
                val care = CareRepository(AtomicCareRecordStore(File(directory, "care.json")), Dispatchers.Unconfined, { now })
                care.load();care.saveProfile(2, 120);care.saveTimingPreferences(120, null, null);care.record(CareEventType.FEED)
                val careRecord = checkNotNull(care.state.value.record)
                val file = File(directory, "departure.json")
                var edit = true
                val repo = DepartureRepository(AtomicDepartureStore(file), { edit }, clock = { now }, io = Dispatchers.Unconfined)
                repo.load();repo.saveSettings(PackingSettings(manualTravelMinutes = 90, feedIntervalMinutes = 120, diaperIntervalMinutes = 120))
                repo.addItem("검증용 손수건")
                val first = checkNotNull(repo.state.value.record)
                val absent = DataSnapshot<JourneySnapshot>(DataOrigin.NAVI_SDK, DataStatus.EMPTY)
                val evaluation = DeparturePlanner().evaluate(careRecord, first, absent, now)
                repo.toggleItem(first.items.first().id, evaluation.packing)
                val restarted = DepartureRepository(AtomicDepartureStore(file), clock = { now }, io = Dispatchers.Unconfined)
                restarted.load()
                check(restarted.state.value.record == repo.state.value.record)
                check(evaluation.packing.isChecked(checkNotNull(restarted.state.value.record).items.first(), now))
                result.putString("persistedChecklist", "passed")
                edit = false
                val confirmed = repo.state.value.record
                repo.addItem("주행 중 저장 금지")
                check(repo.state.value.record == confirmed)
                result.putString("drivingGuard", "passed")
                file.writeText("invalid fixture")
                restarted.load()
                check(restarted.state.value.status == DepartureStoreStatus.NEEDS_RELOAD)
                result.putString("corruptCache", "passed")
                now += 1_000
                check(originalHash() == before)
            }
            result.putString("result", "passed")
        } catch (e: Exception) {
            code = Activity.RESULT_CANCELED
            result.putString("result", "failed")
            result.putString("problem", "${e.javaClass.simpleName}:${e.message}")
        } finally {
            result.putBoolean("fixtureCleaned", !directory.exists() || directory.deleteRecursively())
            result.putBoolean("personalCarePreserved", originalHash() == before)
        }
        finish(code, result)
    }
}
