/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
/*
 * عقد ملفّ إعداد المحرّك COPG — **نقيّ**: لا قراءة ولا كتابة، ولا مسار يُنفَّذ.
 *
 * **والمصدر الموثَّق الذي بُني عليه (مقيسٌ لا مُفترض، 2026-10-03):** واجهة الوحدة الرسميّة
 * (`webroot/js/copg-data.js` في `AlirezaParsi/COPG`) تصرّح في ترويستها أنّ شكل `COPG.json`
 * **«shared with zygisk/binaries»**، وتكتب الملفّ نفسه في المسار نفسه:
 *
 *   - المسار: `/data/adb/modules/COPG/COPG.json` (`module/service.sh` يقرؤه عند الإقلاع ثم ينفّذ
 *     `/data/adb/modules/COPG/controller`؛ و`module/COPG.json` هو الافتراضيّ المشحون).
 *   - مفتاح جهاز: `PACKAGES_<KEY>` ⇒ قائمة أسماء حزم (`"com.x"` بلا وسم = تطبيق بيانات الجهاز)،
 *     و`PACKAGES_<KEY>_DEVICE` ⇒ كائن `{ BRAND, DEVICE, MODEL, PRODUCT, FINGERPRINT?, SDK_INT? … }`.
 *   - الحفظ يحفظ **ترتيب المفاتيح** ويضيف الجديد في الذيل، ويضع `chmod 644` + `chcon system_file`.
 *
 * **وحدّ ما لم يُقرأ بعد:** مصدر المحرّك الأصليّ (`src/spoof_module.cpp`) **ليس في المستودع العام**
 * (الوحدة تشحن ثنائيات مبنيّة)، فـ«القارئ» موثَّقٌ بواجهة المحرّك الرسميّة لا بقراءة شفرته. ولذلك يبقى
 * كلُّ كتابةٍ مُتحقَّقًا منها **بالقراءة بعد الكتابة** فقط، ولا يُدَّعى أثرٌ على التطبيق (§0.1).
 *
 * **والمفاتيح التي نكتبها محصورةٌ ببادئتنا** (`PACKAGES_MAXMANAGER_*`): مفتاح COPG الذي يبدأ بغيره
 * **لا يُلمَس ولا يُعاد ترتيبه**، وحذفُ ما كتبناه هو الرجوع.
 */
package nd.max.core.spoof

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest

/** لماذا لم يُبنَ دمج. كلٌّ منها **فشلٌ مغلق**: لا ملفّ يُكتب على أساسه. */
enum class SpoofCopgRefusal {
    /** الملفّ الموجود لا يُحلَّل كائن JSON — لا نكتب فوق ما لا نقرؤه. */
    CONFIG_UNPARSEABLE,

    /** معرّفان لأنماطنا يُنتجان مفتاحًا واحدًا بعد التطبيع (مثل `a-b` و`a_b`) — لا مقبض بمفتاحين. */
    KEY_COLLISION,
    FOREIGN_PACKAGE_CONFLICT,
    UNSUPPORTED_POLICY,

    /** وسمٌ لا يسمح به نحو الإصدار المثبّت (أو إصدار مجهول): لا نكتب وسمًا قد يتجاهله المحرّك صامتًا. */
    UNSUPPORTED_TAG,
}

/** خطّة كتابة واحدة: نصّ الملفّ كاملًا + بصمته + المفاتيح التي نملكها. */
data class SpoofCopgPlan(
    val configPath: String,
    val json: String,
    val signature: String,
    /** مفاتيح الملفّ الخاصة بنا بعد الدمج (`PACKAGES_MAXMANAGER_*`) — هي ما يُحذَف عند الرجوع. */
    val ownedKeys: List<String>,
)

sealed interface SpoofCopgPlanResult {
    data class Ready(val plan: SpoofCopgPlan) : SpoofCopgPlanResult
    data class Refused(val reason: SpoofCopgRefusal) : SpoofCopgPlanResult
}

object SpoofCopgContract {
    /** معرّف الوحدة كما يقرؤه `module.prop` و`service.sh` — لا تخمين بالاسم المعروض. */
    const val MODULE_ID = "COPG"
    const val MODULE_DIR = "/data/adb/modules/$MODULE_ID"
    const val CONFIG_PATH = "$MODULE_DIR/COPG.json"

    /**
     * بادئتنا داخل ملفّ المحرّك. مفتاحٌ يبدأ بها **لنا وحدنا**، فلا يُتصادم مع أنماط COPG المشحونة
     * (كلّها تبدأ بـ`PACKAGES_` ثم اسم جهاز) ولا مع `cpu_spoof`.
     */
    const val PROFILE_PREFIX = "PACKAGES_MAXMANAGER_"
    const val DEVICE_SUFFIX = "_DEVICE"

    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    /** مفتاح جهازنا لمعرّف نمط — مفتاح COPG بأحرف كبيرة ومحارف `[A-Z0-9_]` فقط. */
    fun packageKey(profileId: String): String =
        PROFILE_PREFIX + profileId.uppercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")

    fun deviceKey(packageKey: String): String = packageKey + DEVICE_SUFFIX

    /**
     * كائن الجهاز بمفردات COPG **الموثَّقة** — والحقول التي لا يضبطها النمط **تُحذَف ولا تُفرَّغ**،
     * فالحقل الغائب أهدأ من قيمةٍ مُختلقة (ADR-07). و`name` اسمُ نمطنا الداخليّ لا يُكتب: مفردات
     * المحرّك لا تحمل حقلًا له.
     */
    fun deviceObject(profile: SpoofProfile): JsonObject = buildJsonObject {
        put("BRAND", profile.brand)
        put("DEVICE", profile.device)
        profile.manufacturer?.let { put("MANUFACTURER", it) }
        put("MODEL", profile.model)
        put("PRODUCT", profile.product)
        profile.fingerprint?.let { put("FINGERPRINT", it) }
        profile.sdkInt?.let { put("SDK_INT", it.toString()) }
    }

    /** حزم كل نمط مرتَّبةً — من روابط الفضاء (نيّة محفوظة)، لا من قائمة المحرّك الحيّة. */
    fun packagesByProfile(workspace: SpoofWorkspace): Map<String, List<String>> =
        workspace.bindings.entries
            .groupBy({ it.value }, { it.key })
            .mapValues { (_, packages) -> packages.sorted() }

    /**
     * يدمج فضائنا في ملفّ المحرّك: **يحفظ كل مفتاح غريب وترتيبه كما هو**، ويستبدل مفاتيحنا وحدها.
     *
     * - ملفٌ موجود لا يُحلَّل ⇒ [SpoofCopgRefusal.CONFIG_UNPARSEABLE] (لا نكتب فوق ما لا نقرؤه).
     * - نمطٌ بلا حزم مرتبطة **لا يُكتب له مفتاح** (لا نُلوّث الملفّ بمدخلات فارغة).
     * - مفاتيحنا القديمة تُحذَف وتُعاد كتابتها في الذيل — فالرابط المحذوف لا يبقى حيًّا في المحرّك.
     * - `null` = الملفّ غير موجود أصلًا؛ ويُبنى من الصفر (وحدة مثبَّتة بلا إعداد بعد).
     */
    fun plan(existing: String?, workspace: SpoofWorkspace, moduleVersion: String? = null): SpoofCopgPlanResult {
        val previous = if (existing.isNullOrBlank()) JsonObject(emptyMap())
        else runCatching { Json.parseToJsonElement(existing) as? JsonObject }.getOrNull()
            ?: return SpoofCopgPlanResult.Refused(SpoofCopgRefusal.CONFIG_UNPARSEABLE)

        // A global external hook cannot be undone by removing an app from COPG's package list.
        if (workspace.globalProfileId != null && workspace.appPolicies.values.any { policy ->
                policy.mode == SpoofInheritanceMode.DISABLED || policy.categories.values.any { it == SpoofCategoryMode.REAL }
            }) return SpoofCopgPlanResult.Refused(SpoofCopgRefusal.UNSUPPORTED_POLICY)
        if (workspace.appPolicies.values.any { policy -> policy.overrides.keys.any {
                it.category !in setOf(SpoofCategory.IDENTITY, SpoofCategory.BUILD) || it == SpoofField.SDK_INT
            } }) return SpoofCopgPlanResult.Refused(SpoofCopgRefusal.UNSUPPORTED_POLICY)
        val effectiveBindings = linkedMapOf<String, String>()
        val effectiveProfiles = mutableListOf<SpoofProfile>()
        val targets = (workspace.bindings.keys + workspace.appPolicies.keys).sorted()
        for ((index, pkg) in targets.withIndex()) {
            if (workspace.appPolicy(pkg).mode == SpoofInheritanceMode.DISABLED) continue
            val resolved = EffectiveSpoofProfileResolver.resolve(workspace, pkg, emptyMap()).fields
                .filter { it.source != SpoofSource.HOST_OBSERVATION }.associate { it.field to it.target }
            if (resolved.isEmpty()) continue
            if (resolved[SpoofField.SDK_INT] != null) return SpoofCopgPlanResult.Refused(SpoofCopgRefusal.UNSUPPORTED_POLICY)
            // COPG's existing identity adapter needs the four core fields; partial policies fail closed.
            val core = listOf(SpoofField.BRAND, SpoofField.MODEL, SpoofField.DEVICE, SpoofField.PRODUCT)
            if (core.any { resolved[it] == null }) return SpoofCopgPlanResult.Refused(SpoofCopgRefusal.UNSUPPORTED_POLICY)
            // GLOBAL may retain a dormant CUSTOM binding for later use. It must not name the
            // inherited global device after that dormant profile (false key collision/wrong group).
            val originalId = (if (workspace.appPolicy(pkg).mode == SpoofInheritanceMode.CUSTOM)
                workspace.bindings[pkg] else workspace.globalProfileId) ?: continue
            val hasPolicyOverrides = workspace.appPolicies[pkg]?.let { it.overrides.isNotEmpty() || it.categories.isNotEmpty() } == true
            val id = if (hasPolicyOverrides) "resolved_$index" else originalId
            // المُصنِّع يتبع الملف الأصل ما دامت الهوية لم تُعدَّل يدويًّا؛ تعديل BRAND يُسقطه كي لا يتناقض مع العلامة.
            val base = workspace.profiles.firstOrNull { it.id == originalId }
            val manufacturer = base?.manufacturer?.takeIf { base.brand == resolved[SpoofField.BRAND] }
            val profile = SpoofProfile(id, id, resolved.getValue(SpoofField.BRAND)!!,
                resolved.getValue(SpoofField.MODEL)!!, resolved.getValue(SpoofField.DEVICE)!!,
                resolved.getValue(SpoofField.PRODUCT)!!, resolved[SpoofField.FINGERPRINT],
                resolved[SpoofField.SDK_INT]?.toIntOrNull(), manufacturer)
            if (!SpoofProfileValidation.valid(profile)) return SpoofCopgPlanResult.Refused(SpoofCopgRefusal.UNSUPPORTED_POLICY)
            effectiveProfiles += profile
            effectiveBindings[pkg] = id
        }
        if (effectiveProfiles.groupBy { it.id }.values.any { versions -> versions.distinct().size > 1 }) {
            return SpoofCopgPlanResult.Refused(SpoofCopgRefusal.KEY_COLLISION)
        }
        // الوسوم: فقط لتطبيقٍ له جهاز فعّال، وبنحوٍ يسمح به الإصدار المثبّت — وإلا فشل مغلق.
        val tagsByPackage = effectiveBindings.keys.associateWith { pkg ->
            workspace.appPolicies[pkg]?.tags.orEmpty().sorted().map(CopgTag::parse)
        }.filterValues { it.isNotEmpty() }
        val (_, refusedTags) = CopgGrammarGate.partition(tagsByPackage.values.flatten(), moduleVersion)
        if (refusedTags.isNotEmpty()) return SpoofCopgPlanResult.Refused(SpoofCopgRefusal.UNSUPPORTED_TAG)
        val materialized = SpoofWorkspace(effectiveProfiles.distinctBy { it.id }, effectiveBindings)
        // COPG searches foreign package arrays in insertion order. A duplicate can defeat our override.
        val foreignPackages = previous.filterKeys { !it.startsWith(PROFILE_PREFIX) }
            .filterKeys { it.startsWith("PACKAGES_") && !it.endsWith(DEVICE_SUFFIX) }.values
            .filterIsInstance<JsonArray>().flatMap { array -> array.mapNotNull { (it as? JsonPrimitive)?.content?.substringBefore(':') } }
        val cpu = previous["cpu_spoof"] as? JsonObject
        val cpuPackages = cpu?.values?.filterIsInstance<JsonArray>().orEmpty()
            .flatMap { array -> array.mapNotNull { (it as? JsonPrimitive)?.content?.substringBefore(':') } }
        if ((foreignPackages + cpuPackages).any { it in effectiveBindings }) {
            return SpoofCopgPlanResult.Refused(SpoofCopgRefusal.FOREIGN_PACKAGE_CONFLICT)
        }
        val byProfile = packagesByProfile(materialized)
        val writeable = materialized.profiles.filter { byProfile[it.id].orEmpty().isNotEmpty() }
        val keys = writeable.map { packageKey(it.id) }
        if (keys.distinct().size != keys.size) {
            return SpoofCopgPlanResult.Refused(SpoofCopgRefusal.KEY_COLLISION)
        }

        // الترتيب: كل ما ليس لنا يُبقى في موضعه بالحرف؛ ثم مفاتيحنا في الذيل (حزم ← جهاز).
        val merged = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        previous.forEach { (key, value) -> if (!key.startsWith(PROFILE_PREFIX)) merged[key] = value }
        writeable.forEach { profile ->
            val pkgKey = packageKey(profile.id)
            merged[pkgKey] = buildJsonArray {
                byProfile.getValue(profile.id).forEach { pkg ->
                    add(JsonPrimitive(CopgPackageEntry(pkg, tagsByPackage[pkg].orEmpty()).render()))
                }
            }
            merged[deviceKey(pkgKey)] = deviceObject(profile)
        }

        val json = pretty.encodeToString(JsonObject.serializer(), JsonObject(merged)) + "\n"
        return SpoofCopgPlanResult.Ready(
            SpoofCopgPlan(CONFIG_PATH, json, signature(json), writeable.flatMap { listOf(packageKey(it.id), deviceKey(packageKey(it.id))) }),
        )
    }

    /**
     * مفاتيحنا القائمة في نصّ الملفّ — و`null` حين لا يُحلَّل (فالجهل ليس نفيًا، ADR-07).
     *
     * وتُستعمل للعرض **السلبيّ**: «ماذا كتبنا فيه الآن» يُقاس من الملفّ لا من نيّتنا المحفوظة،
     * فالنيّة المحفوظة قد تكون كُتبت أو لا، والملفّ هو الحقيقة.
     */
    fun ownedKeys(text: String): List<String>? =
        runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?.keys?.filter { it.startsWith(PROFILE_PREFIX) }?.sorted()

    /**
     * بصمة المحتوى — تُقارَن بعد الكتابة وهي حكم المحكِّم كما هي.
     *
     * **والطيّ جزءٌ من البصمة عن قصد:** `RootFileAccess.read` يعيد النصّ **مقصوصًا**، فبصمةٌ خامّة
     * كانت ستقول «لم يُطابق» لكلّ كتابةٍ نجحت (فرقُ سطرٍ أخير فقط) — وذلك أسوأ من بصمةٍ متسامحة:
     * المحتوى هو المقياس لا البياض.
     */
    /** `version=` من `module.prop` كما هو (`6.8.0`، `v6.8.0`…)، و`null` إن غاب — لا تخمين. */
    fun moduleVersion(moduleProp: String?): String? =
        moduleProp?.lineSequence()?.map { it.trim() }?.firstOrNull { it.startsWith("version=") }
            ?.removePrefix("version=")?.trim()?.takeIf { it.isNotEmpty() }

    fun signature(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.trim().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
