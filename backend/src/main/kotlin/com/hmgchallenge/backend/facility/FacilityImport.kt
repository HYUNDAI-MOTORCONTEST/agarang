package com.hmgchallenge.backend.facility

import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.facility.AmenityStatus
import com.hmgchallenge.shared.facility.CareFacility
import com.hmgchallenge.shared.facility.FacilityCatalog
import com.hmgchallenge.shared.facility.FacilityCatalogCodec
import com.hmgchallenge.shared.facility.FacilityDataSource
import com.hmgchallenge.shared.facility.FacilityListing
import com.hmgchallenge.shared.facility.FacilityType
import com.hmgchallenge.shared.journey.GeoPoint
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.LocalDate
import java.time.DateTimeException
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

data class FacilityImportResult(val catalog: FacilityCatalog, val rejectedRows: Int)

/** Strictly allowlisted fields. Provider contact/person fields in the export are discarded. */
class StandardFacilityNormalizer {
    fun normalize(restJson: String, shelterJson: String, fetchedAtEpochMillis: Long): FacilityImportResult {
        var rejected = 0
        val entries = listOf(restJson to FacilityType.REST_AREA, shelterJson to FacilityType.DROWSINESS_SHELTER).flatMap { (text, type) ->
            val root = Json.parseToJsonElement(text.removePrefix("\uFEFF")).jsonObject
            val records = root["records"] as? JsonArray ?: error("Expected official standard-data records")
            require(records.size <= FacilityCatalog.MAX_ENTRIES)
            records.mapNotNull { element ->
                try { normalizeRow(element.jsonObject, type, fetchedAtEpochMillis) }
                catch (_: IllegalArgumentException) { rejected++; null }
                catch (_: DateTimeException) { rejected++; null }
            }
        }
        // A duplicate ID is ambiguous data, not a last-write-wins merge of two directions.
        require(entries.map { it.facility.id }.distinct().size == entries.size) { "Duplicate facilities" }
        return FacilityImportResult(FacilityCatalog(DataOrigin.PUBLIC_DATA, fetchedAtEpochMillis, sources, entries), rejected)
    }

    private fun normalizeRow(row: JsonObject, type: FacilityType, fetchedAt: Long): FacilityListing {
        fun field(korean: String): String? = (row[korean] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
        val name = requireNotNull(field(if (type == FacilityType.REST_AREA) "휴게소명" else "졸음쉼터명"))
        require(name.length <= 100)
        val lat = requireNotNull(field("위도")?.toDoubleOrNull())
        val lon = requireNotNull(field("경도")?.toDoubleOrNull())
        require(lat in 33.0..39.0 && lon in 124.0..132.0) { "Missing or invalid Korean facility coordinates" }
        val asOf = LocalDate.parse(requireNotNull(field("데이터기준일자"))).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
        require(asOf in 0..fetchedAt)
        val routeNumber = field("도로노선번호")
        val direction = field("도로노선방향")
        val key = listOf(type.name, name, routeNumber.orEmpty(), direction.orEmpty(), lat.toString(), lon.toString()).joinToString("|")
        val id = "public-" + MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        val nursing = if (type == FacilityType.REST_AREA) when (field("수유실유무")?.uppercase()) {
            "Y" -> AmenityStatus.AVAILABLE
            "N" -> AmenityStatus.UNAVAILABLE
            else -> AmenityStatus.UNKNOWN
        } else AmenityStatus.UNKNOWN
        val facility = CareFacility(id, name, GeoPoint(lat, lon), type, nursing, AmenityStatus.UNKNOWN, asOf)
        return FacilityListing(facility, if (type == FacilityType.REST_AREA) REST_SOURCE_ID else SHELTER_SOURCE_ID,
            routeNumber, field("도로노선명"), direction)
    }

    companion object {
        const val REST_SOURCE_ID = "data-go-kr-15025446"
        const val SHELTER_SOURCE_ID = "data-go-kr-15028203"
        val sources = listOf(
            FacilityDataSource(REST_SOURCE_ID, "한국도로공사", "전국휴게소정보표준데이터",
                "https://www.data.go.kr/data/15025446/standard.do", "이용허락범위 제한 없음 (기관별 상세)", "반기"),
            FacilityDataSource(SHELTER_SOURCE_ID, "한국도로공사", "전국졸음쉼터표준데이터",
                "https://www.data.go.kr/data/15028203/standard.do", "이용허락범위 제한 없음 (기관별 상세)", "연간"),
        )
    }
}

object FacilityImport {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size in 3..4) { "Expected rest JSON, shelter JSON, output path, optional retrieval epoch millis" }
        require(args.take(3).all { it.isNotBlank() }) { "Specify facilityRestFile, facilityShelterFile and facilityOutput" }
        val clock = System.currentTimeMillis()
        val now = args.getOrNull(3)?.toLong() ?: clock
        require(now in 0..clock)
        val result = StandardFacilityNormalizer().normalize(Files.readString(Path.of(args[0])), Files.readString(Path.of(args[1])), now)
        require(result.catalog.entries.isNotEmpty() && result.rejectedRows == 0) { "Rejected rows; inspect the input before publishing" }
        val output = Path.of(args[2]).toAbsolutePath()
        Files.createDirectories(output.parent)
        val temp = Files.createTempFile(output.parent, ".facility-import-", ".json")
        try {
            Files.writeString(temp, FacilityCatalogCodec.encode(DataSnapshot(DataOrigin.PUBLIC_DATA, DataStatus.READY, result.catalog, now)))
            Files.move(temp, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
        println("Normalized facilities: ${result.catalog.entries.size}; rejected rows: ${result.rejectedRows}")
    }
}
