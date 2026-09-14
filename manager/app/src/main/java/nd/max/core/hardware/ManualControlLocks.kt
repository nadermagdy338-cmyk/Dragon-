package nd.max.core.hardware

import android.os.Process

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Durable per-knob manual locks (spec decisions #3, #5, #24; INV-3).
 *
 * A lock records that the user personally set this physical knob. It is not an
 * ownership priority — it is an exclusion that survives process death, so it
 * cannot ride the intent journal (whose entries expire with their owning
 * process). Automated owners (`MAX_AI`, `GLOBAL_PROFILE`) may never write a
 * locked knob; `SAFETY` and `RECOVERY` still may, because safety supremacy
 * (INV-2) outranks every user preference.
 *
 * The lock's own token is exempt so the manual path itself keeps writing
 * through the arbiter.
 */
object ManualControlLocks {
    private const val DIRECTORY_NAME = "hardware-control-plane"
    private const val FILE_NAME = "manual-locks.json"

    data class Lock(
        val key: String,
        val token: String,
        val desired: String,
        val baseline: String?,
        val lockedAtMs: Long,
    )

    @Volatile private var directory: File? = null
    private val processLock = ReentrantLock()

    /**
     * Every accessor is @Synchronized, so this cache is monitor-protected. It
     * keeps the per-submit lock check off the disk — the safety loop asks about
     * every knob roughly once a second.
     */
    private var cache: List<Lock>? = null

    /** Shares the journaled control directory so both processes see one lock set. */
    @Synchronized
    fun configure(appFilesDir: File) {
        val root = File(appFilesDir, DIRECTORY_NAME)
        if (!root.exists()) root.mkdirs()
        directory = root
        // Another directory means another lock set: never serve stale entries.
        cache = null
    }

    fun isConfigured(): Boolean = directory != null

    @Synchronized
    fun lock(key: String, token: String, desired: String, baseline: String?): Lock {
        val locks = read()
        val existing = locks.firstOrNull { it.key == key }
        val entry = Lock(
            key = key,
            token = token,
            desired = desired,
            // The first captured baseline is the user's pre-manual state and
            // must not be overwritten by later manual adjustments.
            baseline = existing?.baseline ?: baseline,
            lockedAtMs = existing?.lockedAtMs ?: System.currentTimeMillis(),
        )
        write(locks.filterNot { it.key == key } + entry)
        return entry
    }

    @Synchronized
    fun unlock(key: String): Lock? {
        val locks = read()
        val removed = locks.firstOrNull { it.key == key } ?: return null
        write(locks.filterNot { it.key == key })
        return removed
    }

    @Synchronized
    fun updateDesired(key: String, desired: String) {
        val locks = read()
        val index = locks.indexOfFirst { it.key == key }
        if (index < 0) return
        val updated = locks.toMutableList()
        updated[index] = updated[index].copy(desired = desired)
        write(updated)
    }

    @Synchronized
    fun find(key: String): Lock? = read().firstOrNull { it.key == key }

    @Synchronized
    fun lockedKeys(): Set<String> = read().map(Lock::key).toSet()

    @Synchronized
    fun snapshot(): List<Lock> = read()

    /** Drops every lock. Used by tests and by an explicit "give all knobs back". */
    @Synchronized
    fun clearAll() {
        write(emptyList())
    }

    /**
     * True when [owner] must not write [key] because the user locked it.
     * Safety and recovery always pass; the lock holder's own token always passes.
     */
    fun blocks(owner: ControlOwnership.Owner, key: String, token: String): Boolean {
        if (owner.priority >= ControlOwnership.Owner.SAFETY.priority) return false
        val lock = find(key) ?: return false
        return lock.token != token
    }

    private fun read(): List<Lock> {
        cache?.let { return it }
        val file = file() ?: return emptyList()
        val loaded = if (!file.exists()) {
            emptyList()
        } else {
            runCatching {
                val rows = JSONArray(file.readText())
                buildList {
                    repeat(rows.length()) { index ->
                        val row = rows.getJSONObject(index)
                        val key = row.optString("key")
                        if (key.isBlank()) return@repeat
                        add(
                            Lock(
                                key = key,
                                token = row.optString("token"),
                                desired = row.optString("desired"),
                                baseline = row.optString("baseline").takeIf(String::isNotBlank),
                                lockedAtMs = row.optLong("lockedAt", 0L),
                            )
                        )
                    }
                }
            }.getOrElse { emptyList() }
        }
        cache = loaded
        return loaded
    }

    private fun write(locks: List<Lock>) {
        val file = file() ?: return
        val rows = JSONArray()
        locks.forEach { lock ->
            rows.put(
                JSONObject()
                    .put("key", lock.key)
                    .put("token", lock.token)
                    .put("desired", lock.desired)
                    .put("baseline", lock.baseline.orEmpty())
                    .put("lockedAt", lock.lockedAtMs)
            )
        }
        val temp = File(file.parentFile, "$FILE_NAME.tmp.${Process.myPid()}")
        val payload = rows.toString()
        FileOutputStream(temp).use { output ->
            output.write(payload.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        if (!temp.renameTo(file)) {
            FileOutputStream(file).use { output ->
                output.write(payload.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            temp.delete()
        }
        // Publish only after durable bytes are visible.
        cache = locks.toList()
    }

    private fun file(): File? = directory?.let { File(it, FILE_NAME) }
}
