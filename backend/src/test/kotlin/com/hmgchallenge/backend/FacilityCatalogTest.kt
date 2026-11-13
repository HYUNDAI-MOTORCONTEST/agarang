package com.hmgchallenge.backend

import com.hmgchallenge.backend.facility.FacilityCatalogService
import com.hmgchallenge.backend.facility.FacilityController
import com.hmgchallenge.backend.facility.PublicFacilityCatalog
import com.hmgchallenge.backend.facility.StandardFacilityNormalizer
import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.facility.AmenityStatus
import com.hmgchallenge.shared.facility.FacilityCatalogCodec
import com.hmgchallenge.shared.facility.FacilityType
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.web.server.ResponseStatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FacilityCatalogTest {
    private val now = 1_800_000_000_000L
    private val normalizer = StandardFacilityNormalizer()
    private val empty = """{"records":[]}"""
    private fun row(nursing: String = "Y", direction: String = "상행", latitude: String = "37.45", date: String = "2026-09-17") =
        """{"휴게소명":"공개 시설","도로노선번호":"50","도로노선명":"공개 도로","도로노선방향":"$direction","위도":"$latitude","경도":"127.05","수유실유무":"$nursing","데이터기준일자":"$date","휴게소전화번호":"DISCARD-ME","담당자":"DISCARD-ME"}"""
    private fun normalize(row: String) = normalizer.normalize("""{"records":[$row]}""", empty, now)

    @Test
    fun normalizerPreservesDirectionAndSourceDateWithoutInferringDiaperFacilities() {
        val result = normalize(row())
        val item = result.catalog.entries.single()
        assertEquals(AmenityStatus.AVAILABLE, item.facility.nursingRoom)
        assertEquals(AmenityStatus.UNKNOWN, item.facility.diaperChanging)
        assertEquals("상행", item.directionLabel)
        assertEquals(0, result.rejectedRows)
        assertTrue(item.facility.verifiedAtEpochMillis!! < result.catalog.fetchedAtEpochMillis)
        assertFalse(FacilityCatalogCodec.encode(DataSnapshot(DataOrigin.PUBLIC_DATA, DataStatus.READY, result.catalog, now)).contains("DISCARD-ME"))
    }

    @Test
    fun missingBlankAndUnexpectedNursingValuesRemainUnknownAndNRemainsUnavailable() {
        for (value in listOf("", "?", "unknown")) assertEquals(AmenityStatus.UNKNOWN, normalize(row(value)).catalog.entries.single().facility.nursingRoom)
        assertEquals(AmenityStatus.UNAVAILABLE, normalize(row("N")).catalog.entries.single().facility.nursingRoom)
        assertEquals(null, normalize(row(direction = "")).catalog.entries.single().directionLabel)
    }

    @Test
    fun malformedCoordinatesAndFutureOrMissingSourceDatesAreRejected() {
        for (bad in listOf(row(latitude = ""), row(latitude = "127.0"), row(latitude = "0"), row(date = ""), row(date = "bad-date"), row(date = "2099-01-01"))) {
            val result = normalize(bad)
            assertTrue(result.catalog.entries.isEmpty())
            assertEquals(1, result.rejectedRows)
        }
    }

    @Test
    fun directionsHaveDistinctStableIdsButExactDuplicatesFail() {
        assertEquals(normalize(row()).catalog.entries.single().facility.id, normalize(row()).catalog.entries.single().facility.id)
        assertTrue(normalize(row()).catalog.entries.single().facility.id != normalize(row(direction = "하행")).catalog.entries.single().facility.id)
        assertFailsWith<IllegalArgumentException> { normalizer.normalize("""{"records":[${row()},${row()}]}""", empty, now) }
    }

    @Test
    fun shelterDoesNotAcquireNursingRoomFromUnspecifiedFields() {
        val json = """{"records":[{"졸음쉼터명":"공개 쉼터","위도":"35.6635","경도":"126.9322","도로노선방향":"순천기점 + 천안종점","데이터기준일자":"2026-02-25","수유실유무":"Y"}]}"""
        val item = normalizer.normalize(empty, json, now).catalog.entries.single()
        assertEquals(FacilityType.DROWSINESS_SHELTER, item.facility.type)
        assertEquals(AmenityStatus.UNKNOWN, item.facility.nursingRoom)
        assertEquals("순천기점 + 천안종점", item.directionLabel)
    }

    @Test
    fun bundledPublicSnapshotHas468FacilitiesAndRequiredAttribution() {
        val public = PublicFacilityCatalog("classpath:facilities/public-catalog.json", DefaultResourceLoader())
        val snapshot = public.service.catalog()
        val catalog = snapshot.value!!
        assertEquals(DataOrigin.PUBLIC_DATA, catalog.origin)
        assertEquals(468, catalog.entries.size)
        assertEquals(215, catalog.entries.count { it.facility.type == FacilityType.REST_AREA })
        assertEquals(212, catalog.entries.count { it.facility.nursingRoom == AmenityStatus.AVAILABLE })
        assertTrue(catalog.entries.all { it.facility.diaperChanging == AmenityStatus.UNKNOWN })
        assertTrue(catalog.sources.all { it.provider == "한국도로공사" && it.license.contains("제한 없음") })
    }

    @Test
    fun publicAndExplicitDemoApiModesDoNotMix() {
        val controller = FacilityController(PublicFacilityCatalog("classpath:facilities/public-catalog.json", DefaultResourceLoader()))
        val public = FacilityCatalogCodec.decode(controller.facilities("public").body!!)
        val demo = FacilityCatalogCodec.decode(controller.facilities("demo").body!!)
        assertEquals(DataOrigin.PUBLIC_DATA, public.origin)
        assertEquals(DataOrigin.DEMO, demo.origin)
        assertTrue(demo.readyValue!!.entries.all { it.facility.id.startsWith("demo-") })
        assertFailsWith<ResponseStatusException> { controller.facilities("secret") }
    }

    @Test
    fun absentBrokenAndExpiredCatalogsKeepDistinctFailureStates() {
        assertEquals(DataStatus.UNSUPPORTED, FacilityCatalogService({ null }, { now }).catalog().status)
        assertEquals(DataStatus.FAILED, FacilityCatalogService({ error("private path") }, { now }).catalog().status)
        val catalog = normalize(row(date = "2020-01-01")).catalog.copy(fetchedAtEpochMillis = now - 86_400_000)
        val stale = FacilityCatalogService({ DataSnapshot(DataOrigin.PUBLIC_DATA, DataStatus.READY, catalog, now) }, { now }).catalog()
        assertEquals(DataStatus.STALE, stale.status)
        assertEquals(catalog.fetchedAtEpochMillis, stale.value!!.fetchedAtEpochMillis)
    }
}
