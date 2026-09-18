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

import org.json.JSONObject

/**
 * `GAP-13` + `GAP-12` — **إعلان محتوى نسخة الإعداد قبل كتابتها**، وقسم المظهر معها.
 *
 * المشكلة التي يحلّها الملف ليست تقنية: نسخة إعداد **تُباع للمستخدم بأمل** ثم يُكتشف عند
 * الاستعادة أن نصف ما ظنّه محفوظًا لم يكن فيها. والسبب غيابُ **إعلان** يقول قبل البدء: هذا
 * سيُحفظ، وهذا لن يُحفظ، وهذا **قد يُحفظ وقد يغيب** — ولماذا.
 *
 * ولهذا كل شيء هنا **قبل** الكتابة لا بعدها:
 *
 * 1. **النطاق المُختار ⇒ قائمة أقسام معلَنة**، ولكل قسم مصدره ونوعه.
 * 2. **الغياب المتوقّع مُعلَن** بأسبابه ([AbsenceReason]) لا مُسكَت عنه. وأهمّها `NO_ROOT`:
 *    القيم تُقرأ عبر طبقة الخصائص، وعلى جهاز بلا جذر قد يعود القسم **غائبًا** — و«غائب» ليست
 *    «فارغ»، فالأولى تعني «لم أستطع»، والثانية تعني «قرأتُ فوجدت لا شيء».
 * 3. **المستثنى استثناءٌ صريح** بأسبابه ([ExclusionReason])، لا صمت.
 *
 * والقرار الأهمّ في هذا الملف **استثناء** لا إضافة: ملف `maxai_safety` **لا يُنسخ ولا
 * يُستورد**، لأن نسخة تُستورد من ملف يستطيع كاتبُها إضعاف حراسة `Max AI` ليست نسخة إعداد —
 * بل ناقل تغيير لسياسة سلامة بلا علم صاحب الجهاز. وحدود السلامة تُضبط في مكانها داخل التطبيق.
 *
 * وفصل هذا عن التنفيذ مقصود: كل ما هنا قابل للاختبار بلا جهاز وبلا أندرويد.
 */
object ConfigBackupInventory {

    /** إصدار صيغة حِزمة التفضيلات. مستند بإصدار لا نفهمه **يُرفض** ولا يُفسَّر خطأً. */
    const val PREFS_SCHEMA = 1

    /** مفتاح الحِزمة داخل خريطة النسخة. مُنطَّق لئلّا يصطدم بمفتاح خصائص حقيقي. */
    const val PREFS_KEY_PREFIX = "__MAXMANAGER_PREFS__"

    // ────────────────────────────────────────────────────────────────────────
    // أصناف المحتوى
    // ────────────────────────────────────────────────────────────────────────

    /**
     * `TWEAKS` و`APPLIST` كانا موجودين قبل هذا الملف؛ وأُضيفا هنا ليصير **القسم المُختار
     * والإعلان عن الغياب** شيئًا واحدًا لا شيئين منفصلين يتفرّقان مع الزمن.
     */
    enum class Section(val id: String) {
        TWEAKS("tweaks"),
        APPLIST("applist"),
        APPEARANCE("appearance"),
        APP_PREFS("app_prefs"),
    }

    /**
     * لماذا قد يغيب قسم **وإن اختاره المستخدم**. ووجود هذا التصنيف هو الفرق بين منتج يقول
     * «تمّت النسخة» ومنتج يقول «تمّت، إلا ثلاثة أقسام لسببين».
     */
    enum class AbsenceReason(val id: String) {
        /** القيم تُقرأ عبر طبقة الخصائص ولا جذر على الجهاز ⇒ لا قراءة موثوقة. */
        NO_ROOT("no_root"),

        /** ملف التفضيلات غير موجود أصلًا (تطبيق لم يُفتح قطّ، أو تنظيف بيانات). */
        FILE_MISSING("file_missing"),

        /** الملف موجود لكنه بلا مفتاح واحد — وهذا **مُعلَن** لا يُخلط بـ«غائب». */
        EMPTY("empty"),
    }

    /** لماذا لا يُنسخ شيء **أبدًا** — ولو اختاره المستخدم. */
    enum class ExclusionReason(val id: String) {
        /** سياسة سلامة: تُضبط داخل التطبيق لا في ملف يُستورد. */
        SAFETY_POLICY("safety_policy"),

        /** بيانات تطبيق آخر — مسؤولية نسخة التطبيقات لا نسخة الإعداد. */
        OTHER_APPS("other_apps"),

        /** سر (مفتاح/keystore): لا يُكتب في ملف نسخة يمكن مشاركته. */
        SECRET("secret"),
    }

    /** قسم سيُكتب فعلًا، ومصدره معلَن. */
    data class Included(val section: Section, val source: String, val needsRoot: Boolean)

    data class Absent(val section: Section, val reason: AbsenceReason)

    data class Excluded(val what: String, val reason: ExclusionReason)

    /** ما ستحتويه النسخة بالضبط — يُحسب **قبل** فتح منتقي الملفات. */
    data class Declaration(
        val included: List<Included>,
        val absent: List<Absent>,
        val exclusions: List<Excluded>,
    ) {
        /** فراغ حقيقي: لا قسم سيُكتب. الواجهة تُعطّل الزر بدل كتابة ملف فارغ. */
        val isEmpty: Boolean get() = included.isEmpty()
    }

    /** النطاق الذي اختاره المستخدم. */
    data class Scope(
        val tweaks: Boolean = true,
        val applist: Boolean = true,
        val appearance: Boolean = false,
        val appPrefs: Boolean = false,
    ) {
        val chosen: List<Section> = buildList {
            if (tweaks) add(Section.TWEAKS)
            if (applist) add(Section.APPLIST)
            if (appearance) add(Section.APPEARANCE)
            if (appPrefs) add(Section.APP_PREFS)
        }
    }

    /**
     * الحالة الفعلية للجهاز كما قيست قبل الكتابة — تُمرَّر ولا تُفترَض.
     *
     * @param hasRoot هل تحقّق وجود الجذر (وجود الوحدة ليس جذرًا).
     * @param prefFilesPresent أسماء ملفات التفضيلات التي وُجدت فعلًا.
     * @param prefFilesEmpty منها ما وُجد لكنه بلا مفتاح واحد. وهو **موجود** بالضرورة، فيُضمّ
     *        إلى الموجود عند الإعلان — وإلا صار ملفٌ فارغ يُقال عنه «غير موجود»، وهو خطأ في
     *        السبب لا في النتيجة.
     */
    data class DeviceState(
        val hasRoot: Boolean,
        val prefFilesPresent: Set<String>,
        val prefFilesEmpty: Set<String>,
    ) {
        /** الموجود فعلًا: ما أُعلن موجودًا وما أُعلن فارغًا معًا. */
        val existingPrefFiles: Set<String> get() = prefFilesPresent + prefFilesEmpty
    }

    /** ملفات التفضيلات التي يقرأها قسمٌ ما. وقائمة معلَنة هنا لا مكتشفة بالتخمين. */
    fun prefFilesOf(section: Section): List<String> = when (section) {
        Section.TWEAKS, Section.APPLIST -> emptyList()
        Section.APPEARANCE -> listOf(PREF_APPEARANCE)
        // `settings_prefs` تحمل اللغة وإعدادات الواجهة، و`app_prefs` أعلام الاستعمال الأولى.
        Section.APP_PREFS -> listOf(PREF_APP_PREFS, PREF_SETTINGS_PREFS)
    }

    const val PREF_APPEARANCE = "settings"
    const val PREF_APP_PREFS = "app_prefs"
    const val PREF_SETTINGS_PREFS = "settings_prefs"

    /**
     * ملفات **لا تُنسخ ولا تُستورد** أبدًا. و`maxai_safety` في مقدمتها — وهذا استثناء مقصود
     * وسُجّل في التوثيق، لا سهو.
     */
    val PROTECTED_PREF_FILES: List<String> = listOf("maxai_safety")

    /**
     * الإعلان الكامل: ما سيُكتب، وما قد يغيب ولماذا، وما لا يُكتب أبدًا ولماذا.
     *
     * والترتيب ثابت (بحسب [Scope.chosen]) ليكون الإعلان قابلًا للقراءة نفسها في كل مرة.
     */
    fun declare(scope: Scope, device: DeviceState): Declaration {
        val included = mutableListOf<Included>()
        val absent = mutableListOf<Absent>()

        scope.chosen.forEach { section ->
            when (section) {
                Section.TWEAKS -> {
                    // القيم تُقرأ عبر طبقة الخصائص: بلا جذر قد تعود كلها غير مقروءة.
                    if (device.hasRoot) {
                        included += Included(section, "خصائص النظام عبر طبقة الخصائص", needsRoot = true)
                    } else {
                        absent += Absent(section, AbsenceReason.NO_ROOT)
                    }
                }

                Section.APPLIST -> {
                    if (device.hasRoot) {
                        included += Included(section, "ملف قائمة التطبيقات", needsRoot = true)
                    } else {
                        absent += Absent(section, AbsenceReason.NO_ROOT)
                    }
                }

                Section.APPEARANCE, Section.APP_PREFS -> {
                    val files = prefFilesOf(section)
                    val present = files.filter { it in device.existingPrefFiles }
                    when {
                        present.isEmpty() -> absent += Absent(section, AbsenceReason.FILE_MISSING)
                        present.all { it in device.prefFilesEmpty } ->
                            absent += Absent(section, AbsenceReason.EMPTY)
                        else -> included += Included(
                            section,
                            present.joinToString(" + "),
                            needsRoot = false,
                        )
                    }
                }
            }
        }

        return Declaration(
            included = included,
            absent = absent,
            exclusions = exclusions(),
        )
    }

    /** الاستثناءات الثابتة — لا تتغيّر بتغيّر النطاق، وهذا مقصود. */
    fun exclusions(): List<Excluded> = listOf(
        Excluded("إعدادات حراسة Max AI", ExclusionReason.SAFETY_POLICY),
        Excluded("بيانات التطبيقات الأخرى", ExclusionReason.OTHER_APPS),
        Excluded("مفاتيح التوقيع والأسرار", ExclusionReason.SECRET),
    )

    /** ملفات التفضيلات المسموح بنسخها — أي ما ليس محميًّا. */
    fun copyablePrefFiles(): List<String> = listOf(PREF_APPEARANCE, PREF_APP_PREFS, PREF_SETTINGS_PREFS)

    /** هل يُسمح بنسخ هذا الملف؟ الحماية **بالقائمة البيضاء** لا بالأسود: المجهول يُرفض. */
    fun isCopyable(fileName: String): Boolean = fileName in copyablePrefFiles()

    // ────────────────────────────────────────────────────────────────────────
    // ترميز التفضيلات — بالأنواع لا بالنصوص
    // ────────────────────────────────────────────────────────────────────────

    /**
     * قيمة تفضيل بنوعها.
     *
     * وتمييز النوع ليس ترفًا: `SharedPreferences` تُعيد `Int` أو `Long` حسب ما كُتب، وكتابة
     * قيمة كانت `Int` كـ`Long` قد تكسر قارئًا يستعمل `getInt` فيرمي استثناءًا. فالنوع **يُحفظ
     * ويُعاد كما كان**، وإن كان غير معروف **يُرفض** بدل تخمينه نصًّا.
     */
    sealed interface PrefValue {
        data class Text(val value: String) : PrefValue

        /** `Int` و`Long` **نوعان مختلفان** ولا يُدمجان: `getInt` على قيمة كُتبت `Long` ترمي. */
        data class Int32(val value: Int) : PrefValue
        data class Whole(val value: Long) : PrefValue
        data class Decimal(val value: Double) : PrefValue
        data class Flag(val value: Boolean) : PrefValue
        data class TextSet(val value: List<String>) : PrefValue
    }

    object PrefCodec {

        private const val TYPE_TEXT = "s"
        private const val TYPE_INT = "i"
        private const val TYPE_LONG = "l"
        private const val TYPE_DECIMAL = "f"
        private const val TYPE_FLAG = "b"
        private const val TYPE_SET = "set"

        /** فكّ **صارم**: إصدار مجهول، أو نوع مجهول، أو قيمة مشوّهة ⇒ `null` للجميع. */
        fun encode(fileName: String, values: Map<String, PrefValue>): String {
            val root = JSONObject()
            root.put("schema", PREFS_SCHEMA)
            root.put("file", fileName)
            val entries = JSONObject()
            values.forEach { (key, value) ->
                val node = JSONObject()
                when (value) {
                    is PrefValue.Text -> { node.put("t", TYPE_TEXT); node.put("v", value.value) }
                    is PrefValue.Int32 -> { node.put("t", TYPE_INT); node.put("v", value.value) }
                    is PrefValue.Whole -> { node.put("t", TYPE_LONG); node.put("v", value.value) }
                    is PrefValue.Decimal -> { node.put("t", TYPE_DECIMAL); node.put("v", value.value) }
                    is PrefValue.Flag -> { node.put("t", TYPE_FLAG); node.put("v", value.value) }
                    is PrefValue.TextSet -> {
                        node.put("t", TYPE_SET)
                        node.put("v", org.json.JSONArray(value.value))
                    }
                }
                entries.put(key, node)
            }
            root.put("values", entries)
            return root.toString()
        }

        data class Decoded(val fileName: String, val values: Map<String, PrefValue>)

        fun decode(text: String): Decoded? {
            val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
            if (root.optInt("schema", -1) != PREFS_SCHEMA) return null
            val file = root.optString("file").takeIf { it.isNotBlank() } ?: return null
            // الحماية بالقائمة البيضاء **حتى في فكّ ملف واحد**: مسار ثانٍ لا يُفحص هو باب خلفي.
            if (!isCopyable(file)) return null
            val entries = root.optJSONObject("values") ?: return null
            val values = linkedMapOf<String, PrefValue>()
            entries.keys().forEach { key ->
                val node = entries.optJSONObject(key) ?: return null
                val type = node.optString("t")
                val value: PrefValue = when (type) {
                    TYPE_TEXT -> node.optString("v", null)?.let { PrefValue.Text(it) } ?: return null
                    TYPE_INT -> if (node.has("v")) PrefValue.Int32(node.optInt("v")) else return null
                    TYPE_LONG -> if (node.has("v")) PrefValue.Whole(node.optLong("v")) else return null
                    TYPE_DECIMAL -> if (node.has("v")) PrefValue.Decimal(node.optDouble("v")) else return null
                    TYPE_FLAG -> if (node.has("v")) PrefValue.Flag(node.optBoolean("v")) else return null
                    TYPE_SET -> {
                        val array = node.optJSONArray("v") ?: return null
                        PrefValue.TextSet(List(array.length()) { array.optString(it) })
                    }
                    else -> return null
                }
                values[key] = value
            }
            return Decoded(fileName = file, values = values)
        }

        /** ترميز كامل لعدّة ملفات في نصّ واحد — هذا ما يُوضع في خريطة النسخة. */
        fun encodeAll(
            files: Map<String, Map<String, PrefValue>>,
        ): String {
            val root = JSONObject()
            root.put("schema", PREFS_SCHEMA)
            val bundles = JSONObject()
            files.forEach { (name, values) -> bundles.put(name, JSONObject(encode(name, values))) }
            root.put("bundles", bundles)
            return root.toString()
        }

        /**
         * فكّ الحِزمة مع **رفض أي ملف غير مسموح**: الحماية بالقائمة البيضاء، فحِزمة تحمل
         * `maxai_safety` (أو أي اسم مجهول) **تُرفض كاملة** ولا تُطبَّق منها حزمة واحدة.
         */
        fun decodeAll(text: String): Map<String, Map<String, PrefValue>>? {
            val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
            if (root.optInt("schema", -1) != PREFS_SCHEMA) return null
            val bundles = root.optJSONObject("bundles") ?: return null
            val out = linkedMapOf<String, Map<String, PrefValue>>()
            bundles.keys().forEach { name ->
                if (!isCopyable(name)) return null
                val decoded = decode(bundles.optJSONObject(name)?.toString() ?: return null) ?: return null
                if (decoded.fileName != name) return null
                out[name] = decoded.values
            }
            return out
        }
    }

    /** معرّف السبب للتسجيل — والواجهة تُترجمه من مورد لا من هنا. */
    fun reasonId(reason: AbsenceReason): String = reason.id

    /** معرّف نوع القيمة للتسجيل — والمقارنة به لا باسم الصنف. */
    fun typeId(value: PrefValue): String = when (value) {
        is PrefValue.Text -> "text"
        is PrefValue.Int32 -> "int"
        is PrefValue.Whole -> "long"
        is PrefValue.Decimal -> "decimal"
        is PrefValue.Flag -> "flag"
        is PrefValue.TextSet -> "set"
    }
}
