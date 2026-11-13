package com.hmgchallenge.app.facility

import android.util.AtomicFile
import com.hmgchallenge.shared.facility.FacilityCatalog
import com.hmgchallenge.shared.facility.FacilityCatalogClient
import com.hmgchallenge.shared.facility.FacilityCatalogCodec
import com.hmgchallenge.shared.facility.FacilityCatalogStore
import com.hmgchallenge.shared.facility.RouteAccessCatalog
import com.hmgchallenge.shared.facility.RouteAccessCatalogClient
import com.hmgchallenge.shared.facility.RouteAccessCatalogCodec
import com.hmgchallenge.shared.facility.RouteAccessCatalogStore
import com.hmgchallenge.shared.data.DataSnapshot
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Invoked on IO by FacilityRepository. Public catalog only; no journey/location is sent. */
class HttpFacilityCatalogClient(private val baseUrl: String) : FacilityCatalogClient {
    override fun fetch(): DataSnapshot<FacilityCatalog> {
        val connection = URL(baseUrl.trimEnd('/') + FacilityCatalog.PATH).openConnection() as HttpURLConnection
        connection.connectTimeout = 3_000
        connection.readTimeout = 3_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK)
            val bytes = connection.inputStream.use { input ->
                // readNBytes is not available on every supported Android release.
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= FacilityCatalog.MAX_JSON_BYTES)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return FacilityCatalogCodec.decode(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}

/**
 * 백엔드 없이 동작하는 제출(release) 빌드용. 백엔드가 서빙하는 것과 같은 공개 시설 JSON을
 * APK assets로 넣어 두고(빌드 시 backend/src/main/resources/facilities에서 복사) 그대로 읽는다.
 * 신선도 판정은 HTTP 응답과 똑같이 FacilityRepository가 한다.
 */
class AssetFacilityCatalogClient(
    private val open: (String) -> java.io.InputStream,
) : FacilityCatalogClient {
    override fun fetch(): DataSnapshot<FacilityCatalog> =
        FacilityCatalogCodec.decode(readBounded(open(ASSET), FacilityCatalog.MAX_JSON_BYTES))

    companion object { const val ASSET = "public-catalog.json" }
}

/** 검증된 경로 접근성 회랑 JSON을 assets에서 읽는다. [AssetFacilityCatalogClient]와 같은 방식. */
class AssetRouteAccessCatalogClient(
    private val open: (String) -> java.io.InputStream,
) : RouteAccessCatalogClient {
    override fun fetch(): DataSnapshot<RouteAccessCatalog> =
        RouteAccessCatalogCodec.decode(readBounded(open(ASSET), RouteAccessCatalog.MAX_JSON_BYTES))

    companion object { const val ASSET = "route-access.json" }
}

private fun readBounded(stream: java.io.InputStream, maxBytes: Int): String = stream.use { input ->
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size() + count <= maxBytes)
        output.write(buffer, 0, count)
    }
    output.toByteArray().toString(Charsets.UTF_8)
}

class AtomicFacilityCatalogStore(private val file: File) : FacilityCatalogStore {
    private val atomic = AtomicFile(file)
    override fun read(): DataSnapshot<FacilityCatalog>? {
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        require(file.length() <= FacilityCatalog.MAX_JSON_BYTES)
        return atomic.openRead().bufferedReader(Charsets.UTF_8).use { FacilityCatalogCodec.decode(it.readText()) }
    }

    override fun write(snapshot: DataSnapshot<FacilityCatalog>) {
        val text = FacilityCatalogCodec.encode(snapshot)
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            atomic.finishWrite(stream)
            stream = null
            val persisted = atomic.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (persisted != text) throw IOException("Facility cache confirmation failed")
        } catch (e: Exception) {
            stream?.let { atomic.failWrite(it) }
            throw e
        }
    }
}

/** Fetches verified corridor route-access metadata without sending baby/location/destination data. */
class HttpRouteAccessCatalogClient(private val baseUrl: String) : RouteAccessCatalogClient {
    override fun fetch(): DataSnapshot<RouteAccessCatalog> {
        val connection = URL(baseUrl.trimEnd('/') + RouteAccessCatalog.PATH).openConnection() as HttpURLConnection
        connection.connectTimeout = 3_000
        connection.readTimeout = 3_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK)
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= RouteAccessCatalog.MAX_JSON_BYTES)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return RouteAccessCatalogCodec.decode(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}

class AtomicRouteAccessCatalogStore(private val file: File) : RouteAccessCatalogStore {
    private val atomic = AtomicFile(file)

    override fun read(): DataSnapshot<RouteAccessCatalog>? {
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        require(file.length() <= RouteAccessCatalog.MAX_JSON_BYTES)
        return atomic.openRead().bufferedReader(Charsets.UTF_8).use { RouteAccessCatalogCodec.decode(it.readText()) }
    }

    override fun write(snapshot: DataSnapshot<RouteAccessCatalog>) {
        val text = RouteAccessCatalogCodec.encode(snapshot)
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            atomic.finishWrite(stream)
            stream = null
            val persisted = atomic.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (persisted != text) throw IOException("Route access cache confirmation failed")
        } catch (e: Exception) {
            stream?.let { atomic.failWrite(it) }
            throw e
        }
    }
}
