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

/**
 * `GAP-14` — **عقد الطرف الثالث**: كيف يوسّعنا غيرنا بلا أن يلمس نواتنا.
 *
 * ثلاثة نظائر في مجالنا تفعل هذا (`fas-rs` بقالب إضافة · `acc` بقسم للتطبيقات الرفيقة ·
 * `uperf` بواجهة ملف النمط)، ولم نكن نفعله. والفائدة ليست «ميزة إضافات»، بل أن يصبح
 * **حدّنا معلنًا**: من يريد أن يوسّعنا يعرف بالضبط ما يُسمح به، ونحن نعرف بالضبط ما
 * نرفضه ولماذا — بدل أن يصل توصيل مبنيّ على تخمين شكل ملفاتنا الداخلية.
 *
 * ## القاعدتان الحاكمتان — وهما أهمّ من العقد نفسه
 *
 * 1. **الإضافة إعلان لا سلطة.** كل قدرة في [PluginCapability] إمّا **قراءة** أو
 *    **اقتراح**. ولا توجد قدرة واحدة اسمها «اكتب في العتاد»، وغيابها **مقصود**: كل كتابة
 *    في هذا المستودع تمرّ بالـarbiter (ADR-11)، وإضافة تستطيع الكتابة مباشرةً تُلغي
 *    الحارس الذي بُني ليمنع أن يكتب مكوّنان في المقبض نفسه.
 *
 * 2. **الرفض يُسمّى ولا يُسكَت عنه.** إضافة غير صالحة لا تُهمَل بصمت ولا تُشغَّل
 *    «جزئيًّا»: تُرفض بسبب بعينه ([PluginRejection])، ويُعرض السبب للمستخدم. الصمت هنا
 *    يجعل المستخدم يظنّ أن إضافته عملت ثم يبحث عن عطب في مكان آخر.
 *
 * وهذا الملف **خالص**: لا Compose ولا أندرويد ولا shell، فيُختبر كاملًا في JVM عادي.
 */
package nd.max.ui.util

/** نسخة الـAPI التي نفهمها اليوم. تُعلَن في كل بيان، والاختلاف يُرفض بسببه. */
const val PLUGIN_API_LEVEL = 1

/**
 * ما **يُسمح** لإضافة أن تفعله. مجموعة مغلقة: أي رمز آخر يُرفض ولا يُتجاهل، لأن
 * تجاهل قدرة مجهولة يعني تشغيل إضافة بقدرات أقلّ ممّا طلبت — أي سلوك لم يوافق عليه أحد.
 */
enum class PluginCapability(val token: String) {
    /** قراءة قياساتنا المعلنة (لا الكتابة في عقدة). */
    ReadTelemetry("read.telemetry"),

    /** فهرسة مسارات العتاد المتاحة للقراءة. */
    ReadHardware("read.hardware"),

    /**
     * اقتراح نمط أداء. الاقتراح **يُمرَّر إلى الـarbiter** كأي طلب داخلي، ولا يُنفَّذ
     * بنفسه. ولذلك اسمه `Propose` لا `Apply` — والاسم الضابط هنا ليس تجميلًا.
     */
    ProposeProfile("propose.profile"),

    /** الانتفاع بأحداثنا المعلنة (بدء جلسة، تغيّر تطبيق مقدّمة…). */
    SubscribeEvent("subscribe.event"),

    /** تقديم قياس خاصّ بها (يُعرض موسومًا بمصدره لا كرقمنا). */
    ReportInstrument("report.instrument"),
    ;

    companion object {
        fun fromToken(token: String): PluginCapability? =
            entries.firstOrNull { it.token == token.trim().lowercase() }
    }
}

/** شكل الإضافة. مفتوح للقراءة في الواجهة، وليس مجرّد حقل زينة. */
enum class PluginKind(val token: String) {
    ProfilePack("profile-pack"),
    Instrument("instrument"),
    EventBridge("event-bridge"),
    ActionShortcut("action-shortcut"),
    ;

    companion object {
        fun fromToken(token: String): PluginKind? =
            entries.firstOrNull { it.token == token.trim().lowercase() }
    }
}

/** بيان إضافة كما أعلنته هي. */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val apiLevel: Int,
    val kind: PluginKind,
    val capabilities: Set<PluginCapability>,
    val description: String? = null,
) {
    fun has(capability: PluginCapability): Boolean = capability in capabilities
}

/** لماذا رُفض بيان. كل سبب قابل للعرض باسمه، وكل سبب يمنع التشغيل تمامًا. */
enum class PluginRejection {
    BadId,
    MissingName,
    BadVersion,
    ApiTooOld,
    ApiTooNew,
    MissingKind,
    UnknownKind,
    UnknownCapability,
    NoCapabilities,
    DuplicateId,
}

/**
 * نتيجة الحكم على بيان واحد.
 *
 * `detail` يحمل **الشيء الملموس** الذي رُفض (الرمز المجهول، الرقم المعلن) لا وصفًا
 * عامًّا — وهذا ما يجعل الرفض قابلاً للتصحيح من كاتب الإضافة بدل أن يكون لغزًا.
 */
data class PluginEvaluation(
    val manifest: PluginManifest?,
    val id: String,
    val rejection: PluginRejection?,
    val detail: String? = null,
) {
    val accepted: Boolean get() = rejection == null && manifest != null

    companion object {
        fun rejected(id: String, reason: PluginRejection, detail: String? = null) =
            PluginEvaluation(manifest = null, id = id, rejection = reason, detail = detail)
    }
}

/** كل ما وجدناه في مجلد الإضافات بعد الحكم. */
data class PluginRegistryState(
    val accepted: List<PluginManifest> = emptyList(),
    val rejected: List<PluginEvaluation> = emptyList(),
) {
    val isEmpty: Boolean get() = accepted.isEmpty() && rejected.isEmpty()
    val hasRejections: Boolean get() = rejected.isNotEmpty()
}

/**
 * العقد نفسه: التحليل والحكم والتوثيق.
 *
 * تنسيق البيان **سطر لكل زوج `key=value`**، وهو قصدًا أبسط ما يمكن كتابته بلا مكتبة:
 * ملف يُكتب بمحرّر نصوص على الهاتف. لكن البساطة في الشكل لا تعني التساهل في الحكم —
 * كل حقل مطلوب يُفحص، وكل رمز مجهول يُرفض.
 */
object PluginContract {

    /** المجلد الوحيد الذي نقرأ منه، تحت مسار إعداداتنا القائم. */
    const val DIRECTORY = "/data/adb/.config/MaxManager/plugins"

    const val MANIFEST_FILE = "plugin.manifest"

    /** مسار بيان إضافة بعينها — يبنيه العقد، فلا يُكتب في الواجهة. */
    fun manifestPath(id: String): String = "$DIRECTORY/$id/$MANIFEST_FILE"

    /** المفاتيح المقروءة. ما عداها **يُتجاهل ويُعلَن** أنه تُجوهل. */
    private val KEYS = setOf("id", "name", "version", "api", "kind", "capabilities", "description")

    data class ParsedManifest(val first: PluginEvaluation, val ignoredKeys: List<String>)

    /**
     * يحلّل مخرج ملف البيان. `expectedId` هو **اسم المجلد**، ويُقارَن بما أعلنته
     * الإضافة: اختلافهما يعني أن الإضافة تدّعي هوية غير مكانها، وهو أول ما يُرفض.
     */
    fun parse(lines: List<String>, expectedId: String): ParsedManifest {
        val fields = LinkedHashMap<String, String>()
        val ignored = mutableListOf<String>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val separator = line.indexOf('=')
            if (separator <= 0) continue
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            if (key !in KEYS) {
                ignored += key
                continue
            }
            fields[key] = value
        }

        val declaredId = fields["id"].orEmpty()
        val id = declaredId.ifEmpty { expectedId }
        if (!isValidId(id)) {
            return ParsedManifest(PluginEvaluation.rejected(id, PluginRejection.BadId, id), ignored)
        }
        if (declaredId.isNotEmpty() && declaredId != expectedId) {
            return ParsedManifest(PluginEvaluation.rejected(id, PluginRejection.BadId, "$expectedId≠$declaredId"), ignored)
        }

        val name = fields["name"].orEmpty()
        if (name.isBlank()) {
            return ParsedManifest(PluginEvaluation.rejected(id, PluginRejection.MissingName), ignored)
        }

        val version = fields["version"].orEmpty()
        if (!isValidVersion(version)) {
            return ParsedManifest(PluginEvaluation.rejected(id, PluginRejection.BadVersion, version), ignored)
        }

        val apiLevel = fields["api"]?.toIntOrNull()
            ?: return ParsedManifest(PluginEvaluation.rejected(id, PluginRejection.ApiTooOld, fields["api"]), ignored)
        if (apiLevel > PLUGIN_API_LEVEL) {
            return ParsedManifest(PluginEvaluation.rejected(id, PluginRejection.ApiTooNew, "$apiLevel>$PLUGIN_API_LEVEL"), ignored)
        }
        if (apiLevel < 1) {
            return ParsedManifest(PluginEvaluation.rejected(id, PluginRejection.ApiTooOld, "$apiLevel"), ignored)
        }

        val kindToken = fields["kind"].orEmpty()
        val kind = PluginKind.fromToken(kindToken)
            ?: return ParsedManifest(
                PluginEvaluation.rejected(
                    id,
                    if (kindToken.isBlank()) PluginRejection.MissingKind else PluginRejection.UnknownKind,
                    kindToken.ifBlank { null },
                ),
                ignored,
            )

        val tokens = fields["capabilities"].orEmpty()
            .split(',', ';', ' ')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        if (tokens.isEmpty()) {
            return ParsedManifest(PluginEvaluation.rejected(id, PluginRejection.NoCapabilities), ignored)
        }
        val capabilities = LinkedHashSet<PluginCapability>()
        for (token in tokens) {
            val capability = PluginCapability.fromToken(token)
                ?: return ParsedManifest(PluginEvaluation.rejected(id, PluginRejection.UnknownCapability, token), ignored)
            capabilities += capability
        }

        val manifest = PluginManifest(
            id = id,
            name = name,
            version = version,
            apiLevel = apiLevel,
            kind = kind,
            capabilities = capabilities,
            description = fields["description"]?.takeIf { it.isNotBlank() },
        )
        return ParsedManifest(PluginEvaluation(manifest, id, rejection = null), ignored)
    }

    /**
     * حكم جماعي: يرتّب المقبول، ويُبعد المتكرّر (الأول يفوز)، ويحفظ **كل** الرفض بأسبابه.
     */
    fun evaluate(evaluations: List<PluginEvaluation>): PluginRegistryState {
        val accepted = LinkedHashMap<String, PluginManifest>()
        val rejected = mutableListOf<PluginEvaluation>()
        for (evaluation in evaluations) {
            if (!evaluation.accepted) {
                rejected += evaluation
                continue
            }
            val manifest = evaluation.manifest!!
            if (manifest.id in accepted) {
                rejected += PluginEvaluation.rejected(manifest.id, PluginRejection.DuplicateId, manifest.id)
                continue
            }
            accepted[manifest.id] = manifest
        }
        return PluginRegistryState(accepted.values.toList(), rejected)
    }

    /**
     * معرّف الإضافة: أحرف لاتينية صغيرة وأرقام ونقطة وشرطة، بلا مسافات.
     *
     * والصرامة ليست شكلية: المعرّف يُستعمل **اسمًا لمجلد**، فمعرّف فيه `..` أو فاصل
     * مسار يجعل قراءة بيان إضافة قراءةً لملف آخر على الجهاز.
     */
    fun isValidId(id: String): Boolean =
        id.isNotBlank() && id.length <= 64 &&
            id.all { it.isDigit() || it in 'a'..'z' || it == '.' || it == '-' || it == '_' } &&
            !id.contains("..")

    /** نسخة من `x.y` أو `x.y.z` — البادئة الرقمية إلزامية، واللاحقة حرّة. */
    fun isValidVersion(version: String): Boolean {
        val head = version.substringBefore('-').substringBefore('+')
        val parts = head.split('.')
        if (parts.size < 2) return false
        return parts.take(3).all { it.isNotEmpty() && it.all(Char::isDigit) }
    }

    /**
     * قالب البيان الذي يُعرض للطرف الثالث حرفيًّا — لأنه **هو العقد**.
     *
     * ولماذا داخل الكود لا في التوثيق وحده: نصّ يعرض ما نقرأه فعلًا يستحيل أن يتقادم
     * بصمت، بخلاف صفحة توثيق تصف سلوكًا تغيّر.
     */
    fun manifestTemplate(): String = listOf(
        "# MaxManager plugin manifest — API $PLUGIN_API_LEVEL",
        "id=com.example.myplugin",
        "name=My Plugin",
        "version=1.0.0",
        "api=$PLUGIN_API_LEVEL",
        "kind=${PluginKind.Instrument.token}",
        "capabilities=${PluginCapability.ReadTelemetry.token},${PluginCapability.ReportInstrument.token}",
        "description=One line about what this plugin reports.",
    ).joinToString("\n")

    /** أسماء المفاتيح المقروءة، للعرض في الواجهة (فلا تُخترع قائمة ثانية في الشاشة). */
    fun readableKeys(): List<String> = KEYS.sorted()

    fun kinds(): List<PluginKind> = PluginKind.entries.toList()

    fun capabilities(): List<PluginCapability> = PluginCapability.entries.toList()
}
