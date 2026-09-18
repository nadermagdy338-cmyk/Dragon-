/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.util

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import com.topjohnwu.superuser.Shell
import org.json.JSONArray
import org.json.JSONObject

/**
 * `AR-24` — **دفتر ذاكرة لكل تطبيق: PSS ومقارنة لقطات**.
 *
 * الفجوة التي يسدّها: المستودع كلّه لم يكن فيه **أي قارئ PSS** (مُتحقَّق: لا `getProcessMemoryInfo`
 * ولا `meminfo` إلا سطرَي `SwapTotal/SwapFree` من `/proc/meminfo`). فكان «التطبيق يستهلك ذاكرة
 * كثيرة» حكمًا **لا يمكن فحصه**، ومقارنة اللقطات («هل زادت عن آخر مرّة؟») غير ممكنة أصلًا.
 *
 * **طريقتان، ومصداقيتهما مختلفة عن قصد — ولذلك تُوسَم:**
 * - [Method.OWN_PROCESS]: قراءة مباشرة من `ActivityManager` لتطبيقنا — **بلا امتياز**، وهذا
 *   مسار موثوق قابل للتحقّق.
 * - [Method.DUMPSYS]: `dumpsys meminfo <pkg>` — **يحتاج امتيازًا (جذر/Shizuku)**، وتحليل المخرجات
 *   يعتمد على صيغة لا يضمنها أحد بين الإصدارات. لذلك يُوسَم كذلك، ولا يُخلط بمقياس موثوق.
 *
 * **ولا نُطلق حكمًا على فرق بين لقطتين بطريقتين مختلفتين** — تُعلَن
 * [MemoryDelta.Verdict.INSUFFICIENT] بدل مقارنة تفاح ببرتقال.
 */
object MemoryLedger {

    private const val PREFS = "memory_ledger"
    private const val KEY_SNAPSHOTS = "snapshots"

    /** أقصى عدد لقطات محفوظة لكل مفتاح — نافذة متحرّكة، لا ملف ينمو بلا حدّ. */
    const val MAX_SNAPSHOTS_PER_KEY = 8

    /** أقلّ فرق نسبي يُعتبر تغيّرًا (١٠٪). */
    private const val SIGNIFICANT_RATIO = 0.10

    /** من أين جاء الرقم — بلا هذا الوسم يصير «PSS» كلمة بلا معنى. */
    enum class Method {
        /** `ActivityManager.getProcessMemoryInfo` — بلا امتياز. */
        OWN_PROCESS,

        /** `dumpsys meminfo` — يحتاج امتيازًا، وتحليله غير مضمون بين الإصدارات. */
        DUMPSYS,
    }

    data class MemorySnapshot(
        val key: String,
        val totalPssKb: Long,
        val atMs: Long,
        val method: Method,
    )

    /** نتيجة المقارنة — ثلاث حالات صريحة، ولا «تغيّر» بلا أساس. */
    sealed interface MemoryDelta {
        /** لا لقطة سابقة، أو **طريقة قياس مختلفة** ⇒ لا مقارنة. */
        data object Insufficient : MemoryDelta

        /** الفرق داخل الحدّ ⇒ لا تغيّر يستحق الإعلان. */
        data class Stable(val previousKb: Long, val currentKb: Long) : MemoryDelta

        /** تغيّر معلَن: موجب = زادت، سالب = نقصت. */
        data class Changed(
            val previousKb: Long,
            val currentKb: Long,
            val deltaKb: Long,
            val percent: Int,
        ) : MemoryDelta
    }

    // ---- التحليل (خالص) ---------------------------------------------------------------

    /**
     * `TOTAL PSS: 123456` من `dumpsys meminfo` — **مطابقة صارمة على الوسم** ثم أول قيمة.
     * وليس «أول رقم في الصفحة»: البحث عن رقم عشوائي هو كيف يُنتَج رقم يبدو مقيسًا وهو ليس كذلك.
     *
     * **ولا نشترط نهاية السطر**: المخرج الحقيقي يضع على السطر نفسه
     * `TOTAL PSS: 98765   TOTAL RSS: 120000   TOTAL SWAP PSS: 0` — اشتراط `$` كان يُسقط كل
     * قراءة حقيقية (كشفه اختبار قبل التسليم)، ومطابقة الوسم نفسها تكفي لمنع التقاط رقم آخر.
     */
    fun parseTotalPssKb(dumpsysRaw: String?): Long? {
        if (dumpsysRaw.isNullOrBlank()) return null
        val match = Regex("(?im)^\\s*TOTAL PSS\\s*:\\s*(\\d+)").find(dumpsysRaw) ?: return null
        return match.groupValues.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }
    }

    /**
     * المقارنة — **خالصة**. تُعيد [MemoryDelta.Insufficient] عند غياب سابقة أو اختلاف الطريقة.
     */
    fun delta(previous: MemorySnapshot?, current: MemorySnapshot): MemoryDelta {
        if (previous == null) return MemoryDelta.Insufficient
        if (previous.method != current.method) return MemoryDelta.Insufficient
        if (previous.totalPssKb <= 0L || current.totalPssKb <= 0L) return MemoryDelta.Insufficient

        val delta = current.totalPssKb - previous.totalPssKb
        val ratio = kotlin.math.abs(delta).toDouble() / previous.totalPssKb.toDouble()
        if (ratio < SIGNIFICANT_RATIO) {
            return MemoryDelta.Stable(previous.totalPssKb, current.totalPssKb)
        }
        val percent = (ratio * 100.0).toInt()
        return MemoryDelta.Changed(previous.totalPssKb, current.totalPssKb, delta, percent)
    }

    // ---- التخزين -------------------------------------------------------------------

    fun encode(snapshots: List<MemorySnapshot>): String {
        val array = JSONArray()
        snapshots.forEach { snapshot ->
            array.put(
                JSONObject().apply {
                    put("key", snapshot.key)
                    put("kb", snapshot.totalPssKb)
                    put("at", snapshot.atMs)
                    put("method", if (snapshot.method == Method.OWN_PROCESS) "own" else "dumpsys")
                }
            )
        }
        return array.toString()
    }

    /** يتجاهل المدخل المشوّه بدل إسقاط الدفتر كله. */
    fun decode(raw: String?): List<MemorySnapshot> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val key = item.optString("key").trim()
                    val kb = item.optLong("kb", -1L)
                    if (key.isEmpty() || kb <= 0L) continue
                    val method = if (item.optString("method") == "own") Method.OWN_PROCESS else Method.DUMPSYS
                    add(MemorySnapshot(key, kb, item.optLong("at", 0L), method))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** يحتفظ بآخر [MAX_SNAPSHOTS_PER_KEY] لكل مفتاح — لا نافذة واحدة تُخرج بقية التطبيقات. */
    fun trim(snapshots: List<MemorySnapshot>): List<MemorySnapshot> =
        snapshots.groupBy { it.key }.flatMap { (_, group) -> group.takeLast(MAX_SNAPSHOTS_PER_KEY) }

    data class Report(
        val snapshots: List<MemorySnapshot>,
        val current: MemorySnapshot?,
        val delta: MemoryDelta,
    )

    /** أحدث لقطة لمفتاح، بلا أي قراءة نظام. */
    fun read(context: Context, key: String): Report {
        val all = decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SNAPSHOTS, null))
        val forKey = all.filter { it.key == key }.sortedBy { it.atMs }
        val current = forKey.lastOrNull()
        if (current == null) return Report(all, null, MemoryDelta.Insufficient)
        return Report(all, current, delta(forKey.dropLast(1).lastOrNull(), current))
    }

    /**
     * يسجّل لقطة لتطبيقنا **بلا امتياز** — وهذا المسار الموثوق.
     * @return التقرير بعد التسجيل، أو `null` إن تعذّرت القراءة.
     */
    fun observeOwn(context: Context, atMs: Long = System.currentTimeMillis()): Report? {
        val pss = readOwnPssKb(context) ?: return null
        val key = context.packageName
        return record(context, MemorySnapshot(key, pss, atMs, Method.OWN_PROCESS))
    }

    /**
     * يسجّل لقطة لتطبيق آخر عبر `dumpsys meminfo` — **يحتاج امتيازًا**، وتحليله غير مضمون،
     * ولذلك تبقى لقطته موسومة [Method.DUMPSYS] ولا تُقارن بلقطة [Method.OWN_PROCESS].
     */
    fun observeViaDumpsys(
        context: Context,
        packageName: String,
        atMs: Long = System.currentTimeMillis(),
    ): Report? {
        val raw = runCatching {
            Shell.cmd("dumpsys meminfo $packageName 2>/dev/null").exec().out.joinToString("\n")
        }.getOrNull()
        val pss = parseTotalPssKb(raw) ?: return null
        return record(context, MemorySnapshot(packageName, pss, atMs, Method.DUMPSYS))
    }

    private fun record(context: Context, snapshot: MemorySnapshot): Report {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = decode(prefs.getString(KEY_SNAPSHOTS, null))
        val forKey = all.filter { it.key == snapshot.key }.sortedBy { it.atMs }
        val previous = forKey.lastOrNull()

        val updated = trim(all + snapshot)
        prefs.edit().putString(KEY_SNAPSHOTS, encode(updated)).apply()

        return Report(
            snapshots = updated,
            current = snapshot,
            delta = delta(previous, snapshot),
        )
    }

    /** PSS لتطبيقنا من `ActivityManager` — بلا جذر. */
    private fun readOwnPssKb(context: Context): Long? = runCatching {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val info: Debug.MemoryInfo = manager.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
            .firstOrNull() ?: return null
        info.totalPss.toLong().takeIf { it > 0L }
    }.getOrNull()
}
