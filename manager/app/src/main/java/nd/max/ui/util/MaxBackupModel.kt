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

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

/**
 * Max Backup — النموذج **والقرار الخالص**، بلا أندرويد وبلا جذر.
 *
 * السبب في فصل هذا الملف عن محرّك التنفيذ: القرارات التي تُتّخذ هنا هي التي تحدّد إن كان
 * الاسترجاع آمنًا، وإن كان المستند صالحًا، وإن كان النسخ الاحتياطي مقروءًا. كتابتها داخل
 * كود يعتمد على `Shell` و`PackageManager` يجعلها **غير قابلة للاختبار** — فنُنفّذ آلاف
 * الأسطر ثم لا نستطيع إثبات حالة واحدة منها.
 *
 * ثلاث قواعد تحكم هذا الملف كلّه، وهي جوهر الفرق عن النسخ الاحتياطي «التقليدي»:
 *
 * 1. **الغائب ليس صفرًا.** حجم لم يُقس يقينًا `null`، لا `0`. و`0` تعني «قِسناه فكان فارغًا».
 * 2. **لا استرجاع بلا تحقّق.** إن لم يكن لدي `sha256` مسجَّلًا، فالملف `UNVERIFIABLE` —
 *    ولا يُسمح باسترجاعه. «لم أفحص» ليست «سليم».
 * 3. **أي خلل يبقى ظاهرًا.** `complete=false` تُسجَّل ولا تُخفي: النسخة الناقصة تُعرَض ناقصة.
 */
object MaxBackupModel {

    /** إصدار صيغة المستند. المستند بإصدار لا نفهمه **يُرفض** ولا يُفسَّر خطأً. */
    const val SCHEMA = 1

    // ────────────────────────────────────────────────────────────────────────
    // المكوّنات
    // ────────────────────────────────────────────────────────────────────────

    /** ما يمكن أن يدخل في نسخة تطبيق. */
    enum class ComponentKind(val id: String) {
        /** ملف الـAPK الأساسي. */
        APK("apk"),

        /** ملفات التقسيم (split APKs / App Bundle). */
        SPLIT_APK("split"),

        /** البيانات الداخلية: `/data/data/<pkg>`. */
        APP_DATA("data"),

        /** البيانات الخارجية الخاصة: `/sdcard/Android/data/<pkg>`. */
        EXTERNAL_DATA("external"),

        /** ملفات التوسعة: `/sdcard/Android/obb/<pkg>`. */
        OBB("obb"),

        /**
         * ملف واحد من **بيانات النظام** (واي‑فاي · بلوتوث · جهات · مكالمات · رسائل).
         *
         * وُضع هنا لا في تعداد ثانٍ لأن نتيجة ذلك أن **المستند والتحقّق والسجل والسجل
         * التاريخي** تُعاد كلها بلا مسار يوازيها: نسخة النظام مجلد في المستودع نفسه،
         * ببصمات `sha256` نفسها، وبوابة الاسترجاع نفسها التي تمنع الاسترجاع بلا فحص.
         * ونطاق بيانات النظام يُدار بـ[MaxBackupSystem.Scope] لا بـ[Scope] — ولذلك
         * [Scope.selects] تُعيد `false` له، وهو **مُعلَن** لا سهوًا.
         */
        SYSTEM("system"),
        ;

        companion object {
            fun fromId(raw: String?): ComponentKind? = entries.firstOrNull { it.id == raw }
        }
    }

    /**
     * توفّر المكوّن كما نستطيع إثباته الآن.
     *
     * `NEEDS_ROOT` ليست فشلًا: هي «موجود لكن هذه الطبقة لا تقرأه». و`UNKNOWN` هي «لم نتمكّن
     * من الجزم» — والفرق بينهما هو الفرق بين أن نطلب صلاحية وأن نصمت.
     */
    enum class Availability { AVAILABLE, NEEDS_ROOT, UNAVAILABLE, UNKNOWN }

    data class PlannedComponent(
        val kind: ComponentKind,
        /** المسار الحقيقي كما أعلنه النظام، أو `null` إن لم نقرأه. */
        val source: String?,
        /** الحجم المقيس بالبايت. `null` = لم يُقس — **لا** صفرًا. */
        val sizeBytes: Long?,
        val availability: Availability,
        val fileCount: Int? = null,
    )

    /** ما اختاره المستخدم ليدخل في النسخة. */
    data class Scope(
        val apk: Boolean = true,
        val appData: Boolean = true,
        val externalData: Boolean = true,
        val obb: Boolean = true,
    ) {
        fun selects(kind: ComponentKind): Boolean = when (kind) {
            ComponentKind.APK, ComponentKind.SPLIT_APK -> apk
            ComponentKind.APP_DATA -> appData
            ComponentKind.EXTERNAL_DATA -> externalData
            ComponentKind.OBB -> obb
            // بيانات النظام لها نطاقها الخاص (`MaxBackupSystem.Scope`)، فلا تُختار من هنا.
            ComponentKind.SYSTEM -> false
        }

        val anySelected: Boolean get() = apk || appData || externalData || obb

        companion object {
            /** الافتراضي بلا جذر: الـAPK وحده يُقرأ فعلًا. لا نُشعل ما لا يعمل. */
            fun forPrivilege(hasRoot: Boolean): Scope = if (hasRoot) {
                Scope()
            } else {
                Scope(apk = true, appData = false, externalData = false, obb = false)
            }
        }
    }

    /** جرد تطبيق واحد **قبل** أي كتابة. هذا ما يعرضه زر «ماذا سينسخ؟». */
    data class Plan(
        val pkg: String,
        val label: String?,
        val versionCode: Long,
        val versionName: String?,
        val isSystem: Boolean,
        val uid: Int?,
        val hasRoot: Boolean,
        val components: List<PlannedComponent>,
    ) {
        /** مجموع **المقيس فقط**. حجم لم يُقس لا يدخل في المجموع ولا يُقدَّر بدلًا منه. */
        val knownBytes: Long get() = components.sumOf { it.sizeBytes ?: 0L }

        /** صحيح حين يكون في الخطة مكوّن متاح لم يُقس حجمه ⇒ العرض يجب أن يقول «تقدير». */
        val hasUnmeasured: Boolean
            get() = components.any { it.availability != Availability.UNAVAILABLE && it.sizeBytes == null }

        fun selected(scope: Scope): List<PlannedComponent> = components.filter { scope.selects(it.kind) }

        fun component(kind: ComponentKind): PlannedComponent? = components.firstOrNull { it.kind == kind }

        /** هل تستطيع هذه الطبقة تنفيذ الخطة فعلًا؟ لا نعرض زرًّا لا يعمل. */
        fun canBackupData(scope: Scope): Boolean = components.any {
            scope.selects(it.kind) && it.availability == Availability.AVAILABLE
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // المستند
    // ────────────────────────────────────────────────────────────────────────

    data class ManifestEntry(
        /** مسار نسبي داخل مجلد النسخة. */
        val file: String,
        val kind: ComponentKind,
        val bytes: Long,
        /** `null` = لم نُحسب البصمة. ولا نُخترع واحدة: `null` ⇒ `UNVERIFIABLE` عند الفحص. */
        val sha256: String?,
    )

    data class Manifest(
        val pkg: String,
        val label: String?,
        val versionCode: Long,
        val versionName: String?,
        val createdAtMs: Long,
        val deviceModel: String?,
        val soc: String?,
        val hadRoot: Boolean,
        val encrypted: Boolean,
        val complete: Boolean,
        val entries: List<ManifestEntry>,
        /** ملاحظات صريحة عن أجزاء تعذّرت — تُحفَظ لتُقرأ لاحقًا لا لتُنسى. */
        val omissions: List<String> = emptyList(),
        /**
         * «تُحفَظ للأبد»: التقليم لا يمسّها.
         *
         * والحقل يُكتب في المستند فقط عندما تكون صحيحة، فمستند قديم يُقرأ كما هو (`false`).
         */
        val keptForever: Boolean = false,
        val schema: Int = SCHEMA,
    )

    /** نسخة موجودة على القرص، بملخّصها المعروض في السجل. */
    data class Handle(
        val pkg: String,
        val folder: String,
        val createdAtMs: Long,
        val bytes: Long,
        val complete: Boolean,
        val entryCount: Int,
        val encrypted: Boolean,
        /**
         * اسم التطبيق كما سُجّل في المستند وقت النسخ، أو `null` إن لم يُسجَّل.
         *
         * ولماذا يُقرأ من المستند لا من قائمة التطبيقات المثبّتة: نسخة تطبيق **أُزيل**
         * لا اسم له في القائمة، وكانت تُعرض بمعرّف حزمتها — وهو أسوأ ما يُعرض لنسخة
         * يعود إليها المستخدم لأنه فقد التطبيق أصلًا.
         */
        val label: String? = null,
        /** نسخة محفوظة لا يمسّها التقليم. */
        val keptForever: Boolean = false,
    )

    /**
     * ترميز/فكّ ترميز المستند.
     *
     * الفكّ **صارم عن قصد**: إصدار لا نفهمه، أو حقل جوهري مفقود، يعني `null` — أي «هذا ليس
     * مستند Max Backup». الفكّ المتسامح (قيم افتراضية عند الغياب) هو كيف يُسترجَع مستند
     * ناقص على أنه سليم.
     */
    object ManifestCodec {
        fun encode(manifest: Manifest): String {
            val root = JSONObject()
            root.put("schema", manifest.schema)
            root.put("pkg", manifest.pkg)
            manifest.label?.let { root.put("label", it) }
            root.put("versionCode", manifest.versionCode)
            manifest.versionName?.let { root.put("versionName", it) }
            root.put("createdAtMs", manifest.createdAtMs)
            manifest.deviceModel?.let { root.put("deviceModel", it) }
            manifest.soc?.let { root.put("soc", it) }
            root.put("hadRoot", manifest.hadRoot)
            root.put("encrypted", manifest.encrypted)
            root.put("complete", manifest.complete)
            // يُكتب فقط عند الصحة: غيابه هو الافتراضي، فالمستندات القديمة تبقى مقروءة بلا هجرة.
            if (manifest.keptForever) root.put("keptForever", true)

            val entries = JSONArray()
            manifest.entries.forEach { entry ->
                val node = JSONObject()
                node.put("file", entry.file)
                node.put("kind", entry.kind.id)
                node.put("bytes", entry.bytes)
                entry.sha256?.let { node.put("sha256", it) }
                entries.put(node)
            }
            root.put("entries", entries)

            val omissions = JSONArray()
            manifest.omissions.forEach { omissions.put(it) }
            root.put("omissions", omissions)

            return root.toString()
        }

        fun decode(text: String): Manifest? {
            val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
            if (root.optInt("schema", -1) != SCHEMA) return null

            val pkg = root.optString("pkg").takeIf { it.isNotBlank() } ?: return null
            if (!root.has("versionCode") || !root.has("createdAtMs")) return null
            val versionCode = root.optLong("versionCode", Long.MIN_VALUE)
            val createdAtMs = root.optLong("createdAtMs", Long.MIN_VALUE)
            if (versionCode == Long.MIN_VALUE || createdAtMs == Long.MIN_VALUE) return null

            val rawEntries = root.optJSONArray("entries") ?: return null
            val entries = buildList {
                for (index in 0 until rawEntries.length()) {
                    val node = rawEntries.optJSONObject(index) ?: return null
                    val file = node.optString("file").takeIf { it.isNotBlank() } ?: return null
                    val kind = ComponentKind.fromId(node.optString("kind")) ?: return null
                    if (!node.has("bytes")) return null
                    add(
                        ManifestEntry(
                            file = file,
                            kind = kind,
                            bytes = node.optLong("bytes", -1L).coerceAtLeast(0L),
                            sha256 = node.optString("sha256").takeIf { it.isNotBlank() },
                        )
                    )
                }
            }

            val omissions = root.optJSONArray("omissions")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            }.orEmpty()

            return Manifest(
                pkg = pkg,
                label = root.optString("label").takeIf { it.isNotBlank() },
                versionCode = versionCode,
                versionName = root.optString("versionName").takeIf { it.isNotBlank() },
                createdAtMs = createdAtMs,
                deviceModel = root.optString("deviceModel").takeIf { it.isNotBlank() },
                soc = root.optString("soc").takeIf { it.isNotBlank() },
                hadRoot = root.optBoolean("hadRoot", false),
                encrypted = root.optBoolean("encrypted", false),
                complete = root.optBoolean("complete", false),
                entries = entries,
                omissions = omissions,
                keptForever = root.optBoolean("keptForever", false),
            )
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // السلامة
    // ────────────────────────────────────────────────────────────────────────

    enum class Integrity { VERIFIED, MISSING, CORRUPT, UNVERIFIABLE }

    /**
     * حكم السلامة على ملف واحد، من ثلاث قيم **حقيقية** فقط: هل وُجد، وما البصمة المسجّلة،
     * وما البصمة المقروءة الآن.
     *
     * القاعدة التي تمنع الكذب: بصمة مسجّلة `null` ⇒ `UNVERIFIABLE`، وبصمة مقروءة `null`
     * ⇒ `UNVERIFIABLE`. لا واحدة منهما تصير `VERIFIED` بالصمت.
     */
    fun integrityOf(expectedSha: String?, actualSha: String?, exists: Boolean): Integrity = when {
        !exists -> Integrity.MISSING
        expectedSha.isNullOrBlank() -> Integrity.UNVERIFIABLE
        actualSha.isNullOrBlank() -> Integrity.UNVERIFIABLE
        expectedSha.equals(actualSha, ignoreCase = true) -> Integrity.VERIFIED
        else -> Integrity.CORRUPT
    }

    // ────────────────────────────────────────────────────────────────────────
    // بوابة الاسترجاع
    // ────────────────────────────────────────────────────────────────────────

    /** ما يمنع الاسترجاع. واحد فقط يُعرض، والأشدّ أولًا. */
    enum class RestoreBlock { NONE, NO_ENTRIES, INCOMPLETE, NOT_VERIFIED, ROOT_REQUIRED }

    /** ما لا يمنع لكن يجب أن يُقال قبل الكتابة. */
    enum class RestoreWarning {
        DEVICE_MISMATCH,
        SOC_MISMATCH,
        APP_VERSION_DIFFERS,
        PACKAGE_NOT_INSTALLED,
        ENCRYPTED_ARCHIVE,
    }

    data class RestoreDecision(
        val block: RestoreBlock,
        val warnings: List<RestoreWarning>,
    ) {
        val allowed: Boolean get() = block == RestoreBlock.NONE
    }

    /**
     * قرار الاسترجاع.
     *
     * **الترتيب مقصود**: نقيس الأشدّ فالأخف، لأن إظهار «تحذير اختلاف جهاز» على نسخة
     * تالفة يُشغل المستخدم بالثانوي ويخفي المانع.
     */
    fun restoreDecision(
        manifest: Manifest,
        verdicts: Map<String, Integrity>,
        hasRoot: Boolean,
        currentDeviceModel: String?,
        currentSoc: String?,
        currentVersionCode: Long?,
        packageInstalled: Boolean,
    ): RestoreDecision {
        val warnings = buildList {
            if (manifest.encrypted) add(RestoreWarning.ENCRYPTED_ARCHIVE)
            if (!packageInstalled) add(RestoreWarning.PACKAGE_NOT_INSTALLED)
            if (currentVersionCode != null && manifest.versionCode != currentVersionCode) {
                add(RestoreWarning.APP_VERSION_DIFFERS)
            }
            if (!sameIdentifier(manifest.deviceModel, currentDeviceModel)) {
                add(RestoreWarning.DEVICE_MISMATCH)
            }
            if (!sameIdentifier(manifest.soc, currentSoc)) {
                add(RestoreWarning.SOC_MISMATCH)
            }
        }

        val block = when {
            manifest.entries.isEmpty() -> RestoreBlock.NO_ENTRIES
            !manifest.complete -> RestoreBlock.INCOMPLETE
            !hasRoot -> RestoreBlock.ROOT_REQUIRED
            // الاسترجاع يحتاج تحقّقًا لكل مدخل. `UNVERIFIABLE` ليست اجتيازًا.
            manifest.entries.any { verdicts[it.file] != Integrity.VERIFIED } ->
                RestoreBlock.NOT_VERIFIED
            else -> RestoreBlock.NONE
        }
        return RestoreDecision(block, warnings)
    }

    /**
     * توحيد المعرّفات قبل المقارنة: `SM8650` و` sm8650 ` معرّف واحد (حالة أحرف وفراغات فقط).
     *
     * ولا نطبّع أكثر من ذلك **عن قصد**: حذف الفواصل والشرطات يوهم بتطابق `SM-8650` مع
     * `SM8650`، وهو تطبيع قد يجعل جهازين مختلفين يبدوان واحدًا فيسقط تحذير كان يجب أن يظهر.
     * والأسماء غير المقروءة (`null` أو فارغة) **غير متطابقة** — لأن «لا أعرف» ليست «نفس الجهاز».
     */
    fun sameIdentifier(a: String?, b: String?): Boolean {
        val left = a?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return false
        val right = b?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return false
        return left == right
    }

    // ────────────────────────────────────────────────────────────────────────
    // الاحتفاظ
    // ────────────────────────────────────────────────────────────────────────

    /**
     * أي نسخ تُقلَّم عند الاحتفاظ بأحدث `keep`.
     *
     * والقرار نفسه في [MaxBackupRetention] — دالة خالصة تُقاس في اختبار JVM بلا جهاز،
     * لأن هذه هي القاعدة الوحيدة هنا التي تحذف بيانات بلا سؤال. والدالة تلتصق بها
     * بترجمة `Handle` إلى عرض القرار، فلا يعيش القرار في مكانين يتفرّقان.
     */
    fun toPrune(handles: List<Handle>, keep: Int): List<Handle> {
        val removable = MaxBackupRetention.toRemove(
            copies = handles.map {
                MaxBackupRetention.Copy(
                    folder = it.folder,
                    createdAtMs = it.createdAtMs,
                    kept = it.keptForever,
                )
            },
            keep = keep,
        ).map { it.folder }.toSet()
        return handles.filter { it.folder in removable }
    }

    // ────────────────────────────────────────────────────────────────────────
    // أهداف الاسترجاع
    // ────────────────────────────────────────────────────────────────────────

    /** ما الذي سيُكتب فعلًا عند الاسترجاع — يُعرض للمستخدم قبل التأكيد. */
    enum class RestoreTarget { APP_DATA, EXTERNAL_DATA, OBB, REINSTALL_PACKAGE }

    /** الوجهة التي يُفكّ فيها المكوّن، أو `null` للمكوّنات التي لا تُنسخ كملفات. */
    fun destinationOf(kind: ComponentKind, pkg: String): String? = when (kind) {
        ComponentKind.APP_DATA -> "/data/data/$pkg"
        ComponentKind.EXTERNAL_DATA -> "/sdcard/Android/data/$pkg"
        ComponentKind.OBB -> "/sdcard/Android/obb/$pkg"
        ComponentKind.APK, ComponentKind.SPLIT_APK -> null
        // ملفات النظام لا تُفكّ إلى مجلد واحد: مساراتها المطلقة داخل الأرشيف تُتحقَّق مقابل
        // قائمة مُعلَنة قبل الفكّ (`MaxBackupSystemEngine.tarEntriesAllowed`).
        ComponentKind.SYSTEM -> null
    }

    fun restoreTargets(manifest: Manifest): List<RestoreTarget> = buildList {
        val kinds = manifest.entries.map { it.kind }.toSet()
        if (kinds.any { it == ComponentKind.APK || it == ComponentKind.SPLIT_APK }) {
            add(RestoreTarget.REINSTALL_PACKAGE)
        }
        if (kinds.contains(ComponentKind.APP_DATA)) add(RestoreTarget.APP_DATA)
        if (kinds.contains(ComponentKind.EXTERNAL_DATA)) add(RestoreTarget.EXTERNAL_DATA)
        if (kinds.contains(ComponentKind.OBB)) add(RestoreTarget.OBB)
    }

    // ────────────────────────────────────────────────────────────────────────
    // تسمية وقياس
    // ────────────────────────────────────────────────────────────────────────

    private const val FOLDER_PATTERN = "yyyyMMdd-HHmmss"

    /**
     * اسم مجلد النسخة: زمن **UTC** بصيغة تُرتَّب نصّيًّا = تُرتَّب زمنيًّا.
     * UTC مقصود: تغيير المنطقة الزمنية للجهاز لا يجب أن يجعل مجلدين يتصادمان.
     */
    fun folderName(createdAtMs: Long): String {
        val format = SimpleDateFormat(FOLDER_PATTERN, Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(java.util.Date(createdAtMs))
    }

    /** قياس مقروء للبشر. وحدة واحدة فقط، وبلا ادّعاء دقّة أعلى من المصدر. */
    fun humanBytes(bytes: Long): String {
        if (bytes < 0) return "-"
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        val decimals = if (value >= 100) 0 else 1
        return String.format(Locale.US, "%.${decimals}f %s", value, units[unitIndex])
    }
}
