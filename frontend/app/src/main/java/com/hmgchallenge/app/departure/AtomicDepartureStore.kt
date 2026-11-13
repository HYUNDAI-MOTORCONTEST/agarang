package com.hmgchallenge.app.departure

import android.util.AtomicFile
import com.hmgchallenge.shared.departure.DepartureRecord
import com.hmgchallenge.shared.departure.DepartureRecordCodec
import com.hmgchallenge.shared.departure.DepartureStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Checklist/range inputs stay in private app storage and are independent of the care journal. */
class AtomicDepartureStore(private val file: File) : DepartureStore {
    private val atomic = AtomicFile(file)
    override fun read(): DepartureRecord? {
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        return atomic.openRead().use { input ->
            val bytes = ByteArray(DepartureRecordCodec.MAX_BYTES + 1)
            var total = 0
            while (total < bytes.size) {
                val count = input.read(bytes, total, bytes.size - total)
                if (count < 0) break
                total += count
            }
            require(total <= DepartureRecordCodec.MAX_BYTES)
            DepartureRecordCodec.decode(bytes.copyOf(total).toString(Charsets.UTF_8))
        }
    }

    override fun write(record: DepartureRecord) {
        val text = DepartureRecordCodec.encode(record)
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            atomic.finishWrite(stream)
            stream = null
            if (read() != record) throw IOException("Preparation write confirmation failed")
        } catch (e: Exception) {
            stream?.let { atomic.failWrite(it) }
            throw e
        }
    }
}
