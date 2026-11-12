package com.hmgchallenge.app.care

import android.util.AtomicFile
import com.hmgchallenge.shared.care.CareRecord
import com.hmgchallenge.shared.care.CareRecordCodec
import com.hmgchallenge.shared.care.CareRecordStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Private app storage. AtomicFile recovers the last committed record after an interrupted write. */
class AtomicCareRecordStore(private val file: File) : CareRecordStore {
    private val atomic = AtomicFile(file)

    override fun read(): CareRecord? {
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        return atomic.openRead().bufferedReader(Charsets.UTF_8).use { CareRecordCodec.decode(it.readText()) }
    }

    override fun write(record: CareRecord) {
        val encoded = CareRecordCodec.encode(record)
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(encoded.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            atomic.finishWrite(stream)
            stream = null
            // AtomicFile may log a failed rename/sync rather than throw. Confirm the persisted bytes too.
            val saved = atomic.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (saved != encoded) throw IOException("Care write confirmation failed")
        } catch (e: Exception) {
            stream?.let { atomic.failWrite(it) }
            throw e
        }
    }
}
