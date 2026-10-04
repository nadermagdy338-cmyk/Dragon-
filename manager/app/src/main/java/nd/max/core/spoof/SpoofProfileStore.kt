/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.core.spoof

import android.content.Context
import android.util.AtomicFile
import java.io.File

/** Uses the existing filesDir profile-store convention; callers run on Dispatchers.IO. */
class SpoofProfileStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, "spoof_workspace.txt"))

    fun load(): SpoofWorkspace = synchronized(lock) {
        // AtomicFile recovers a backup from an interrupted write in openRead().
        try {
            file.openRead().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= SpoofWorkspaceCodec.MAX_BYTES)
                    output.write(buffer, 0, count)
                }
                SpoofWorkspaceCodec.decode(output.toString("UTF-8"))
            }
        } catch (missing: java.io.FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw missing
            SpoofWorkspace()
        }
    }

    fun save(workspace: SpoofWorkspace, expected: SpoofWorkspace): Boolean = synchronized(lock) {
        var stream: java.io.FileOutputStream? = null
        try {
            if (load() != expected) return@synchronized false
            val text = SpoofWorkspaceCodec.encode(workspace)
            stream = file.startWrite()
            stream.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
            stream = null
            load() == workspace
        } catch (_: Exception) {
            stream?.let { file.failWrite(it) }
            false
        }
    }

    companion object { private val lock = Any() }
}
