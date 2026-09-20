package nd.max.core.diagnostics

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes a report to a file the user can hand to one app (`P6`/`T6.6`).
 *
 * The export is deliberately dull: a file in the app's own cache, an app-generated name, an atomic
 * replace, and an age-based cleanup. There is **no upload path anywhere in this class** — no client, no
 * endpoint, no recipient — and the file never leaves the device unless the user picks a destination in
 * the system share sheet. It also never overwrites a report that a share may still be reading
 * ([cleanup] takes the protected file as an argument), because deleting a file out from under a
 * receiving app is a failure the user sees as "the share did nothing".
 */
class AtlasReportExporter(
    private val directory: File,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {

    /** Names are generated here, never taken from content, so a report cannot name its own file. */
    private fun nameFor(stamp: Long): String = "atlas-report-$stamp.json"

    /**
     * Writes one report and returns the file, or `null` when it could not be written.
     *
     * The bound is checked before writing: an artifact larger than the schema's limit is refused rather
     * than landing as a file the maintainer will read as complete.
     */
    fun write(text: String): File? {
        if (text.toByteArray(StandardCharsets.UTF_8).size > AtlasSupportReport.MAX_BYTES) return null
        return try {
            if (!directory.exists() && !directory.mkdirs()) return null
            val temporary = File(directory, "atlas-report.tmp")
            temporary.writeText(text, StandardCharsets.UTF_8)
            val target = File(directory, nameFor(clockMs()))
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            target
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /** Reports currently on disk, newest first. */
    fun reports(): List<File> = directory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.startsWith(PREFIX) && it.name.endsWith(SUFFIX) }
        .sortedByDescending { it.name }

    /**
     * Removes reports older than [maxAgeMs], except [protected] — the one a share may still be reading.
     *
     * Returns how many were removed. A report that cannot be aged (a clock that moved backwards) is
     * kept: deleting evidence because time is unmeasurable is the wrong direction.
     */
    fun cleanup(maxAgeMs: Long = DEFAULT_MAX_AGE_MS, protected: File? = null): Int {
        if (maxAgeMs <= 0L) return 0
        val now = clockMs()
        return reports().count { report ->
            if (report == protected) return@count false
            val age = now - report.lastModified()
            age > maxAgeMs && report.delete()
        }
    }

    /** The temporary file must never survive a write, so a reader can never see a half artifact. */
    fun temporaryFiles(): List<File> = directory.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }

    companion object {
        /** The directory name under a private/cache root. Declared once so a test cannot guess it. */
        const val DIRECTORY_NAME: String = "atlas-reports"

        /** One day: long enough for a user to come back to the share sheet, short enough to not pile up. */
        const val DEFAULT_MAX_AGE_MS: Long = 24L * 60L * 60L * 1000L

        private const val PREFIX = "atlas-report-"
        private const val SUFFIX = ".json"

        /** The directory under a cache root. Cache, not files: a report is reconstructible output. */
        fun directoryFor(cacheRoot: File): File = File(cacheRoot, DIRECTORY_NAME)
    }
}
