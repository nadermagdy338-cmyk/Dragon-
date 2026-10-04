/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

data class SpoofRecoverySummary(val engineId: String, val createdAtMs: Long, val phase: SpoofRecoveryPhase, val reason: String)

/** Private app storage, separate from portable user configuration. Corruption blocks writes; never replaces a broken journal. */
@Singleton
class SpoofRecoveryStore @Inject constructor(@ApplicationContext context: Context) {
    private val file = AtomicFile(File(context.filesDir, "identity_recovery.txt"))
    @Synchronized fun load(): List<SpoofRecoveryRecord> = try {
        file.openRead().use { input ->
            val bytes = input.readBytesLimited(SpoofRecoveryCodec.MAX_BYTES)
            SpoofRecoveryCodec.decode(bytes.toString(Charsets.UTF_8))
        }
    } catch (missing: FileNotFoundException) {
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw missing
        emptyList()
    }

    @Synchronized fun record(engineId: String): SpoofRecoveryRecord? = load().firstOrNull { it.engineId == engineId }
    @Synchronized fun put(record: SpoofRecoveryRecord): Boolean {
        var stream: java.io.FileOutputStream? = null
        return try {
            val records = load().filterNot { it.engineId == record.engineId } + record
            val bytes = SpoofRecoveryCodec.encode(records).toByteArray(Charsets.UTF_8)
            stream = file.startWrite()
            stream.write(bytes)
            file.finishWrite(stream)
            stream = null
            load() == records.sortedBy { it.engineId }
        } catch (_: Exception) {
            stream?.let { file.failWrite(it) }
            false
        }
    }
    fun summaries(): List<SpoofRecoverySummary> = load().map { SpoofRecoverySummary(it.engineId, it.createdAtMs, it.phase, it.reason) }
}

private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
