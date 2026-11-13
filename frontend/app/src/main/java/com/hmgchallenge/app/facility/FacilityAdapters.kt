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
