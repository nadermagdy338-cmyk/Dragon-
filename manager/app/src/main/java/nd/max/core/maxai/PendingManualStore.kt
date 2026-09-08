package nd.max.core.maxai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مخزن التغييرات اليدوية المعلقة: تعديلات طلبها المستخدم أثناء إدارة
 * Max AI. لا تُهدر ولا تُنفَّذ خلسة — تُحفظ وتُعرض وتُطبق لحظة إيقاف
 * Max AI، فالسيادة للمستخدم عند عودة التحكم اليدوي.
 *
 * التخزين: JSON في filesDir الخاص بالتطبيق (لا يحتاج صلاحيات)، كتابة
 *ذرية (tmp + rename) كي لا يفسد ملف بانقطاع.
 */
@Singleton
class PendingManualStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file = File(context.filesDir, FILE_NAME)
    private val mutex = Mutex()

    suspend fun add(change: PendingManualChange) = mutex.withLock {
        val all = readAll().filterNot { it.key == change.key } + change
        writeAll(all)
    }

    suspend fun all(): List<PendingManualChange> = mutex.withLock { readAll() }

    suspend fun clear() = mutex.withLock { writeAll(emptyList()) }

    /** أحدث تعديل ملف معلق ("1"/"2"/"3") إن وُجد. */
    suspend fun pendingProfile(): PendingManualChange? =
        all().firstOrNull { it.key == KEY_PROFILE }

    private fun readAll(): List<PendingManualChange> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            PendingManualChange(
                key = o.optString("key"),
                value = o.optString("value"),
                label = o.optString("label"),
                timestampMs = o.optLong("ts", 0L),
            )
        }
    }.getOrDefault(emptyList())

    private fun writeAll(changes: List<PendingManualChange>) {
        runCatching {
            file.parentFile?.mkdirs()
            val arr = JSONArray()
            changes.forEach { c ->
                arr.put(
                    JSONObject()
                        .put("key", c.key)
                        .put("value", c.value)
                        .put("label", c.label)
                        .put("ts", c.timestampMs)
                )
            }
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) {
                // rename قد يفشل عبر أنظمة ملفات معينة — سقوط إلى نسخ.
                file.writeText(arr.toString())
                tmp.delete()
            }
        }
    }

    companion object {
        private const val FILE_NAME = "maxai_pending_changes.json"
        const val KEY_PROFILE = "profile"
    }
}
