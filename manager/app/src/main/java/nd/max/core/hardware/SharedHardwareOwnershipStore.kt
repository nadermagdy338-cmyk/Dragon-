package nd.max.core.hardware

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/** OS-locked intent journal shared by the app and rooted AppMonitor processes. */
object SharedHardwareOwnershipStore {
    private const val DIRECTORY_NAME = "hardware-control-plane"
    private const val STATE_NAME = "owners.json"
    private const val LOCK_NAME = "owners.lock"
    private const val UNREADABLE_PROCESS_TTL_MS = 45_000L

    data class Intent(
        val key: String,
        val owner: ControlOwnership.Owner,
        val token: String,
        val desired: String,
        val requestId: String,
        val processId: Int,
        val processStartToken: String,
        val updatedAtMs: Long,
        val committed: Boolean = false,
    )

    class Journal internal constructor(internal val intents: MutableList<Intent>) {
        fun winner(key: String): Intent? = intents.asSequence()
            .filter { it.key == key }
            .maxWithOrNull(
                compareBy<Intent> { it.owner.priority }
                    .thenBy { it.requestId }
            )

        fun winners(): List<Intent> = intents
            .groupBy(Intent::key)
            .values
            .mapNotNull { contenders ->
                contenders.maxWithOrNull(
                    compareBy<Intent> { it.owner.priority }
                        .thenBy { it.requestId }
                )
            }

        fun replaceToken(intent: Intent) {
            intents.removeAll { it.key == intent.key && it.token == intent.token }
            intents += intent
        }

        fun removeToken(key: String, token: String) {
            intents.removeAll { it.key == key && it.token == token }
        }

        fun removeRequest(key: String, requestId: String) {
            intents.removeAll { it.key == key && it.requestId == requestId }
        }

        fun markCommitted(key: String, token: String, requestId: String) {
            val index = intents.indexOfFirst {
                it.key == key && it.token == token && it.requestId == requestId
            }
            if (index >= 0) intents[index] = intents[index].copy(committed = true)
        }
    }

    @Volatile private var directory: File? = null
    @Volatile private var applicationUid: Int? = null
    @Volatile private var localProcessId: Int = 1

    fun configure(appFilesDir: File, appUid: Int, processId: Int) {
        val root = File(appFilesDir, DIRECTORY_NAME)
        if (!root.exists() && !root.mkdirs()) error("cannot-create-shared-control-directory")
        applicationUid = appUid
        localProcessId = processId
        normalizeAccess(appFilesDir, directoryMode = true)
        normalizeAccess(root, directoryMode = true)
        directory = root
    }

    fun isConfigured(): Boolean = directory != null

    /** Arbitration, mutation, readback and publication all run under this lock. */
    fun <T> withExclusive(block: (Journal) -> T): T {
        val root = directory ?: error("shared-control-store-not-configured")
        val lockFile = File(root, LOCK_NAME)
        return RandomAccessFile(lockFile, "rw").channel.use { channel ->
            normalizeAccess(lockFile)
            channel.lock().use {
                val intents = readState(root)
                removeStaleIntents(intents)
                val journal = Journal(intents)
                val result = block(journal)
                writeState(root, intents)
                result
            }
        }
    }

    fun newIntent(
        key: String,
        owner: ControlOwnership.Owner,
        token: String,
        desired: String,
        requestId: String,
    ): Intent = Intent(
        key = key,
        owner = owner,
        token = token,
        desired = desired,
        requestId = requestId,
        processId = localProcessId,
        processStartToken = processStartToken(localProcessId).orEmpty(),
        updatedAtMs = System.currentTimeMillis(),
    )

    fun snapshot(): List<Intent> = withExclusive { it.intents.toList() }

    fun winnerSnapshot(): List<Intent> = withExclusive(Journal::winners)

    private fun readState(root: File): MutableList<Intent> = runCatching {
        val file = File(root, STATE_NAME)
        if (!file.exists()) return@runCatching mutableListOf()
        val rows = JSONArray(file.readText())
        buildList {
            repeat(rows.length()) { index ->
                val row = rows.getJSONObject(index)
                val owner = runCatching {
                    ControlOwnership.Owner.valueOf(row.getString("owner"))
                }.getOrNull() ?: return@repeat
                add(
                    Intent(
                        key = row.getString("key"),
                        owner = owner,
                        token = row.getString("token"),
                        desired = row.getString("desired"),
                        requestId = row.optString("request", "legacy-${row.optString("token")}"),
                        processId = row.optInt("pid", -1),
                        processStartToken = row.optString("start", ""),
                        updatedAtMs = row.optLong("updated", 0L),
                        committed = row.optBoolean("committed", true),
                    )
                )
            }
        }.toMutableList()
    }.getOrDefault(mutableListOf())

    private fun writeState(root: File, intents: List<Intent>) {
        val rows = JSONArray()
        intents.forEach { intent ->
            rows.put(
                JSONObject()
                    .put("key", intent.key)
                    .put("owner", intent.owner.name)
                    .put("token", intent.token)
                    .put("desired", intent.desired)
                    .put("request", intent.requestId)
                    .put("pid", intent.processId)
                    .put("start", intent.processStartToken)
                    .put("updated", intent.updatedAtMs)
                    .put("committed", intent.committed)
            )
        }
        val target = File(root, STATE_NAME)
        val temp = File(root, "$STATE_NAME.tmp.$localProcessId")
        temp.writeText(rows.toString())
        normalizeAccess(temp)
        if (!temp.renameTo(target)) {
            target.writeText(rows.toString())
            normalizeAccess(target)
            temp.delete()
        } else {
            normalizeAccess(target)
        }
    }

    private fun removeStaleIntents(intents: MutableList<Intent>) {
        val now = System.currentTimeMillis()
        intents.removeAll { intent ->
            when {
                intent.processId <= 0 -> true
                else -> {
                    val liveStart = processStartToken(intent.processId)
                    when {
                        liveStart != null -> intent.processStartToken.isBlank() || liveStart != intent.processStartToken
                        processDefinitelyMissing(intent.processId) -> true
                        else -> now - intent.updatedAtMs > UNREADABLE_PROCESS_TTL_MS
                    }
                }
            }
        }
    }

    /** `/proc/<pid>/stat` field 22, stable for one process lifetime. */
    private fun processStartToken(pid: Int): String? = runCatching {
        val stat = File("/proc/$pid/stat").readText()
        stat.substringAfterLast(") ").split(' ').getOrNull(19)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun processDefinitelyMissing(pid: Int): Boolean = runCatching {
        Os.kill(pid, 0)
        false
    }.getOrElse { error ->
        (error as? ErrnoException)?.errno == OsConstants.ESRCH
    }

    private fun normalizeAccess(file: File, directoryMode: Boolean = false) {
        val uid = applicationUid ?: return
        runCatching { Os.chown(file.absolutePath, uid, uid) }
        runCatching { Os.chmod(file.absolutePath, if (directoryMode) 0b111_101_000 else 0b110_110_000) }
    }
}
