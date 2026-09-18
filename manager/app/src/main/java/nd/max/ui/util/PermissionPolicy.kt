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

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * `GAP-07` — **سياسة الصلاحيات و`AppOps`**: النموذج والقرار الخالص.
 *
 * ولإيضاح العلاقة بين الاثنين — وهي التي تجعل هذه الشاشة ذات معنى لا قائمة أرقام:
 * **صلاحية البيان** تقول «هذا التطبيق طلب الوصول إلى الكاميرا»، و**`AppOps`** تقول ما صنع
 * به فعلًا: هل يسمح له الآن، أم يُتجاهَل، أم يُقيَّد بالاستعمال الأمامي. ومع كل إصدار أندرويد
 * صار `AppOps` يحكم ما لا تملكه صلاحيات البيان أصلًا (الحافظة · الاهتزاز · التنفيذ في
 * الخلفية). أي أن نصف الحقيقة في البيان، ونصفها في `AppOps` — وعرض نصف واحد هو «مدير
 * صلاحيات» يكذب على مستخدمه بلا أن يقصد.
 *
 * والسبب في فصل هذا الملف عن التنفيذ: كل قرار فيه يُتّخذ **قبل** كتابة إعداد نظام، وقرار
 * خاطئ واحد يعني تطبيقًا كُتمت صلاحياته بلا أن يعرف صاحبه. فهو قابل للاختبار كلّه بلا جهاز.
 *
 * ثلاثة مبادئ تحكمه:
 *
 * 1. **القراءة لا تُخترَع.** مخرج `cmd appops get` يُحلَّل بمطابقة **صارمة على العلامة**، وسطر
 *    لا نفهمه **يُسقَط** ولا يُخمَّن — و«وضع مجهول» ليس «افتراضي».
 * 2. **الكتابة تُقرأ بعدها.** بعد `appops set` نُعيد القراءة ونحكم على **القيمة** لا على نجاح
 *    الأمر — و«الجهاز تجاهل ما كتبناه» نتيجة معلَنة لا فشل صامت.
 * 3. **المرجع يُدوَّن ويُقارَن.** «أعدت التثبيت أو غيّرت الروم فعادت كل الصلاحيات» يُعالَج
 *    بحفظ **مرجع صريح** والمقارنة به لاحقًا — لا بنيّة إعادة الضبط من الذاكرة.
 */
object PermissionPolicy {

    /** إصدار صيغة مستند المرجع. مستند بإصدار لا نفهمه **يُرفض**. */
    const val SCHEMA = 1

    // ────────────────────────────────────────────────────────────────────────
    // أوضاع AppOps
    // ────────────────────────────────────────────────────────────────────────

    /**
     * أوضاع `AppOps` كما تُعلنها المنصّة.
     *
     * و`DEFAULT` ليست «مسموح» ولا «ممنوع»: هي **رفع التجاوز** فيعود الحكم إلى البيان وسياسة
     * المنصّة. وخلطها بأي منهما هو أوضح صورة لمدير صلاحيات يضلّل.
     */
    enum class OpMode(val id: String) {
        ALLOW("allow"),
        IGNORE("ignore"),
        DENY("deny"),
        DEFAULT("default"),
        FOREGROUND("foreground"),
        ASK("ask"),
        ;

        companion object {
            /** تُقبل صيغة المنصّة كما هي؛ وأي شيء آخر ليس وضعًا نعرفه. */
            fun fromId(raw: String?): OpMode? {
                val needle = raw?.trim()?.lowercase(Locale.ROOT) ?: return null
                return entries.firstOrNull { it.id == needle }
            }
        }
    }

    /** الأوضاع التي يجوز كتابتها، بترتيب العرض: من الأوسع إلى الأضيق إلى رفع التجاوز. */
    val WRITABLE_MODES: List<OpMode> = listOf(
        OpMode.ALLOW, OpMode.FOREGROUND, OpMode.IGNORE, OpMode.DENY, OpMode.DEFAULT,
    )

    data class OpState(val op: String, val mode: OpMode)

    /**
     * تحليل مخرج `cmd appops get <pkg>`.
     *
     * الصيغة الحقيقية (أندرويد ١٠+):
     * ```
     * Uid: 10123
     * Package: com.example
     *   CAMERA: allow
     *   READ_CONTACTS: deny; rejectTime=+1d ago
     * ```
     * والحرس على **شكل اسم العملية** لا على الإزاحة: اسم بأحرف كبيرة وشرطات سفلية فقط. فسطرا
     * `Uid:` و`Package:` يسقطان لأن فيهما أحرفًا صغيرة — فلا نحتاج قائمة استثناءات لأسماء
     * تتغيّر مع إصدارات المنصّة. والإزاحة **اختيارية** عن قصد: قراءة عملية واحدة تُعيد السطر
     * بلا إزاحة (`CAMERA: allow`)، وهي نفس الصيغة التي تحتاجها قراءة الجدول كله.
     *
     * والقيمة تُقتطع عند أول `;` لأن ما بعدها **بيانات تتبّع** (`rejectTime`, `time`,
     * `duration`) وليست وضعًا.
     */
    fun parseAppOps(stdout: List<String>): List<OpState> = stdout.mapNotNull { rawLine ->
        val match = OP_LINE.find(rawLine) ?: return@mapNotNull null
        val mode = OpMode.fromId(match.groupValues[2].substringBefore(';').trim()) ?: return@mapNotNull null
        OpState(op = match.groupValues[1], mode = mode)
    }.distinctBy { it.op }

    /** وضع عملية واحدة كما يُقرأ بعد الكتابة. `null` = لم يُعلَن — ولا نُخمّن بديلًا. */
    fun parseSingleOp(stdout: List<String>?): OpMode? =
        stdout?.let(::parseAppOps)?.firstOrNull()?.mode

    private val OP_LINE = Regex("""^\s*([A-Z][A-Z0-9_]*)\s*:\s*(.+)$""")

    // ────────────────────────────────────────────────────────────────────────
    // حكم الكتابة
    // ────────────────────────────────────────────────────────────────────────

    /**
     * حكم على **القيمة** بعد الكتابة، لا على نجاح الأمر.
     *
     * و[APPLIED_AS_DEFAULT] موجودة لأن المنصّة **لا تُعلن** وضعًا مُساويًا للافتراضي: بعد كتابة
     * `default` تُزال الصفّ من القائمة (رفع التجاوز). فغياب الصف بعد `default` **دليل نجاح**،
     * وغيابه بعد أي وضع آخر **ليس كذلك**. وخلطهما يجعل «نجح» و«لم أعرف» كلمة واحدة.
     */
    enum class WriteVerdict { APPLIED, APPLIED_AS_DEFAULT, IGNORED_BY_DEVICE, FAILED, UNVERIFIABLE }

    fun verdict(wrote: OpMode, readBack: OpMode?, commandSucceeded: Boolean): WriteVerdict = when {
        !commandSucceeded -> WriteVerdict.FAILED
        readBack == wrote -> WriteVerdict.APPLIED
        readBack == null && wrote == OpMode.DEFAULT -> WriteVerdict.APPLIED_AS_DEFAULT
        readBack == null -> WriteVerdict.UNVERIFIABLE
        else -> WriteVerdict.IGNORED_BY_DEVICE
    }

    // ────────────────────────────────────────────────────────────────────────
    // المرجع المدوَّن
    // ────────────────────────────────────────────────────────────────────────

    /**
     * مرجع صريح لحالة تطبيق: ما كانت عليه الأوضاع في لحظة اختارها المستخدم.
     * و`ops` **مُعلَنة كلها** لا مختارة: المرجع الذي يحفظ بعض الأوضاع يَسهُل أن يُظنّ شاملًا.
     */
    data class Reference(
        val pkg: String,
        val savedAtMs: Long,
        val ops: Map<String, OpMode>,
        val schema: Int = SCHEMA,
    )

    /** انحراف واحد: وضع في المرجع، وما صار إليه الآن. */
    data class Drift(val op: String, val reference: OpMode, val current: OpMode?)

    /** كل ما انحرف عن المرجع، مرتَّبًا بالاسم ليكون قارئه قادرًا على مسحه بعينه. */
    fun drift(reference: Reference, current: List<OpState>): List<Drift> {
        val now = current.associate { it.op to it.mode }
        return reference.ops.entries
            .mapNotNull { (op, refMode) ->
                val currentMode = now[op]
                // `null` تعني «الصف غير مُعلَن»، وهي انحراف أيضًا: المرجع أراد وضعًا مُعلَنًا.
                // والمقارنة بـ`refMode` وحدها تكفي: `null` لا تساوي وضعًا معلَنًا أبدًا.
                if (currentMode == refMode) null else Drift(op, refMode, currentMode)
            }
            .sortedBy { it.op }
    }

    /**
     * ما سيُكتب فعلًا عند الاستعادة: الأوضاع المُشتقّة من المرجع.
     * ولا يُكتب شيء إن لم يكن هناك انحراف — فلا نُرسل أوامر بلا سبب.
     */
    fun restorePlan(reference: Reference, drift: List<Drift>): Map<String, OpMode> =
        drift.associate { it.op to reference.ops.getValue(it.op) }

    // ────────────────────────────────────────────────────────────────────────
    // الترميز
    // ────────────────────────────────────────────────────────────────────────

    /**
     * فكّ **صارم** كبقية مستنداتنا: إصدار لا نفهمه، أو حزمة مفقودة، أو وضع مجهول ⇒ `null`.
     * والوضع المجهول يُرفض لأن قبوله يعني استعادة قيمة لا نعرف معناها إلى نظام الجهاز.
     */
    object Codec {
        fun encode(reference: Reference): String {
            val root = JSONObject()
            root.put("schema", reference.schema)
            root.put("pkg", reference.pkg)
            root.put("savedAtMs", reference.savedAtMs)
            val ops = JSONObject()
            reference.ops.forEach { (op, mode) -> ops.put(op, mode.id) }
            root.put("ops", ops)
            return root.toString()
        }

        fun decode(text: String): Reference? {
            val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
            if (root.optInt("schema", -1) != SCHEMA) return null
            val pkg = root.optString("pkg").takeIf { it.isNotBlank() } ?: return null
            if (!root.has("savedAtMs")) return null
            val opsNode = root.optJSONObject("ops") ?: return null
            val ops = linkedMapOf<String, OpMode>()
            opsNode.keys().forEach { op ->
                val mode = OpMode.fromId(opsNode.optString(op)) ?: return null
                ops[op] = mode
            }
            return Reference(
                pkg = pkg,
                savedAtMs = root.optLong("savedAtMs", 0L),
                ops = ops,
            )
        }

        /** قائمة الأوضاع كـ`JSONArray` — تُستعمل في لقطة التصدير لا في المرجع وحده. */
        fun encodeOps(ops: List<OpState>): JSONArray {
            val array = JSONArray()
            ops.forEach { state ->
                val node = JSONObject()
                node.put("op", state.op)
                node.put("mode", state.mode.id)
                array.put(node)
            }
            return array
        }
    }
}
