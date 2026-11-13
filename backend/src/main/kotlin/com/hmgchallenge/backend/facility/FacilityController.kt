package com.hmgchallenge.backend.facility

import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.facility.DemoStopScenario
import com.hmgchallenge.shared.facility.FacilityCatalog
import com.hmgchallenge.shared.facility.FacilityCatalogCodec
import com.hmgchallenge.shared.facility.FacilityPolicy
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

class FacilityCatalogService(
    private val load: () -> DataSnapshot<FacilityCatalog>?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val initial: DataSnapshot<FacilityCatalog> = try {
        val data = load()
        require(data == null || (data.origin == DataOrigin.PUBLIC_DATA && data.value?.origin == DataOrigin.PUBLIC_DATA))
        data ?: DataSnapshot(DataOrigin.PUBLIC_DATA, DataStatus.UNSUPPORTED)
    } catch (_: Exception) { DataSnapshot(DataOrigin.PUBLIC_DATA, DataStatus.FAILED) }

    fun catalog(): DataSnapshot<FacilityCatalog> {
        val catalog = initial.readyValue ?: return initial
        val now = clock()
        return initial.copy(status = if (FacilityPolicy().catalogFresh(catalog, now)) DataStatus.READY else DataStatus.STALE,
            observedAtEpochMillis = now)
    }
}

@Service
class PublicFacilityCatalog(
    @Value("\${app.facilities.catalog:}") location: String,
    resourceLoader: ResourceLoader,
) {
    val service = FacilityCatalogService({
        if (location.isBlank()) null else {
            val resource = resourceLoader.getResource(location)
            if (!resource.exists()) null else resource.inputStream.use { input ->
                val bytes = input.readNBytes(FacilityCatalog.MAX_JSON_BYTES + 1)
                require(bytes.size <= FacilityCatalog.MAX_JSON_BYTES)
                FacilityCatalogCodec.decode(bytes.toString(Charsets.UTF_8))
            }
        }
    })
}

@RestController
class FacilityController(private val publicCatalog: PublicFacilityCatalog) {
    @GetMapping(FacilityCatalog.PATH, produces = [MediaType.APPLICATION_JSON_VALUE])
    fun facilities(@RequestParam(defaultValue = "public") mode: String): ResponseEntity<String> {
        val snapshot = when (mode) {
            "public" -> publicCatalog.service.catalog()
            "demo" -> DemoStopScenario.create(System.currentTimeMillis()).facilities
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown facility mode")
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
            .body(FacilityCatalogCodec.encode(snapshot))
    }
}
