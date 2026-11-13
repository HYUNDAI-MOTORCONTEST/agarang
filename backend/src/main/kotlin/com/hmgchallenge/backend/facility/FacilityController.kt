package com.hmgchallenge.backend.facility

import com.hmgchallenge.shared.data.DataOrigin
import com.hmgchallenge.shared.data.DataSnapshot
import com.hmgchallenge.shared.data.DataStatus
import com.hmgchallenge.shared.facility.DemoStopScenario
import com.hmgchallenge.shared.facility.FacilityCatalog
import com.hmgchallenge.shared.facility.FacilityCatalogCodec
import com.hmgchallenge.shared.facility.FacilityPolicy
import com.hmgchallenge.shared.facility.RouteAccessCatalog
import com.hmgchallenge.shared.facility.RouteAccessCatalogCodec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.DefaultResourceLoader
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

class RouteAccessCatalogService(
    private val load: () -> DataSnapshot<RouteAccessCatalog>?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val initial: DataSnapshot<RouteAccessCatalog> = try {
        val data = load()
        require(data == null || (data.origin == DataOrigin.NAVI_SDK && data.value?.origin == DataOrigin.NAVI_SDK))
        data ?: DataSnapshot(DataOrigin.NAVI_SDK, DataStatus.UNSUPPORTED)
    } catch (_: Exception) { DataSnapshot(DataOrigin.NAVI_SDK, DataStatus.FAILED) }

    fun catalog(): DataSnapshot<RouteAccessCatalog> {
        val catalog = initial.readyValue ?: return initial
        val now = clock()
        val fresh = FacilityPolicy.fresh(catalog.fetchedAtEpochMillis, now, FacilityPolicy().restAreaDataAgeMillis)
        return initial.copy(
            status = if (fresh) DataStatus.READY else DataStatus.STALE,
            observedAtEpochMillis = now,
        )
    }
}

@Service
class VerifiedRouteAccessCatalog(
    @Value("\${app.facilities.route-access:}") location: String,
    resourceLoader: ResourceLoader,
) {
    val service = RouteAccessCatalogService({
        if (location.isBlank()) null else {
            val resource = resourceLoader.getResource(location)
            if (!resource.exists()) null else resource.inputStream.use { input ->
                val bytes = input.readNBytes(RouteAccessCatalog.MAX_JSON_BYTES + 1)
                require(bytes.size <= RouteAccessCatalog.MAX_JSON_BYTES)
                RouteAccessCatalogCodec.decode(bytes.toString(Charsets.UTF_8))
            }
        }
    })
}

@RestController
class FacilityController @Autowired constructor(
    private val publicCatalog: PublicFacilityCatalog,
    private val verifiedRouteAccess: VerifiedRouteAccessCatalog,
) {
    constructor(publicCatalog: PublicFacilityCatalog) : this(
        publicCatalog,
        VerifiedRouteAccessCatalog("classpath:facilities/route-access.json", DefaultResourceLoader()),
    )

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

    @GetMapping(RouteAccessCatalog.PATH, produces = [MediaType.APPLICATION_JSON_VALUE])
    fun routeAccess(@RequestParam(defaultValue = "public") mode: String): ResponseEntity<String> {
        val snapshot = when (mode) {
            "public" -> verifiedRouteAccess.service.catalog()
            "demo" -> DemoStopScenario.routeAccessCatalog(System.currentTimeMillis())
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown route access mode")
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
            .body(RouteAccessCatalogCodec.encode(snapshot))
    }
}
