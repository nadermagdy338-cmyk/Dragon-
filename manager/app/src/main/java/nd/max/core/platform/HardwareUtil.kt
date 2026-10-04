/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.core.platform

import android.content.Context
import android.os.Build
import org.json.JSONObject



private fun readSysFile(path: String): String {
    return try {
        java.io.File(path).readText().trim()
    } catch (e: Exception) {
        ""
    }
}

internal fun extractChipVariants(code: String): List<String> {
    val variants = mutableListOf(code)

    val beforeSlash = code.substringBefore("/").trim()
    if (beforeSlash != code && beforeSlash.isNotEmpty()) variants.add(beforeSlash)

    val beforeUnderscore = beforeSlash.substringBefore("_").trim()
    if (beforeUnderscore != beforeSlash && beforeUnderscore.isNotEmpty()) variants.add(beforeUnderscore)

    val basePattern = Regex("^([A-Za-z]+\\d+)")
    basePattern.find(beforeUnderscore)?.groupValues?.get(1)?.let { base ->
        if (base !in variants && base.isNotEmpty()) variants.add(base)
    }

    return variants
}

/*
 * وحدّ الاستنتاج بالبادئة (`MIN_INFERRED_CODE_LEN`) صار في `ChipsetIdentity.kt` مع بقية
 * منطق الحلّ: قاعدة واحدة في موضع واحد، فلا تفترق نسختان منها.
 */

/*
 * أقلّ طول لرقم قطعة يُقبل داخل قيمة **مركّبة**، ومعه فصل الأجزاء.
 *
 * والشرطان معًا (حرف + رقم، وطول ≥ 4) هما ما يمنع الأجزاء الرقمية المحضة من المطابقة
 * المصادفة: `soc_id` مثلًا `519`، و`SM 8550` المكتوب بفاصل يعطي جزءًا `8550` وحده — وكلاهما
 * لا يُعلن شريحة، فتزول قيمتهما ولا يبقى إلا ما يُشبه رقم قطعة (`SM8550` · `MT6789` · `gs201`).
 */
private const val MIN_PART_LEN = 4

/*
 * و**الشرطة والشرطة المائلة والسفلى لا تفصل** عن قصد: `SM8350-AC` و`MT6833V/ZA` و
 * `MT8390AV/A` أرقام قطعة كاملة، وفصلها على `-` يُبقي `SM8350` فيسبق المفتاح **الأدقّ** إلى
 * المفتاح الأساسي، فيُعرض «Snapdragon 888» عن جهاز كتالوجه يقول «888+».
 *
 * والفرق **مقيس** على الكتالوج الحقيقي (١٠٧٣ مفتاحًا، وكل مفتاح يُوضع في قالب
 * `Qualcomm Technologies, Inc <CODE>`): بالفصل الواسع كان ٧٠٥ من ٨٨٢ مفتاحًا تعود إلى اسمها
 * نفسه (٧٩٫٩٪)، وبعدم الفصل تعود ٨٧٦ من ٨٨٦ (٩٨٫٩٪) — وترتفع المعروفة من ٨٧٧ إلى ٨٨٤.
 * والتفصيل في `ChipsetIdentityTest` و`HANDOFF` تكملة ١٨١.
 */
private val PART_SEPARATOR = Regex("[^A-Za-z0-9/_-]+")

/**
 * سطر `Hardware` من `/proc/cpuinfo`: مصدر إضافي يُعلن الشريحة على أجهزة لا تصل فيها
 * `ro.board.platform` ولا يُقرأ لها `soc0/machine` (كثير من أجهزة MediaTek القديمة).
 */
private fun cpuInfoHardware(): String {
    val text = readSysFile("/proc/cpuinfo")
    if (text.isEmpty()) return ""
    return text.lineSequence()
        .map { line -> line.substringBefore(':') to line.substringAfter(':', "") }
        .firstOrNull { (label, _) -> label.trim().equals("Hardware", ignoreCase = true) }
        ?.second?.trim().orEmpty()
}

/**
 * `compatible` في شجرة الأجهزة: قائمة NUL-مفصولة، وعناصرها تحمل رقم الشريحة
 * (`qcom,sm8150` · `google,gs201`) — فيصل الرقم من قصّ الأجزاء لا من السلسلة كاملة.
 */
private fun deviceTreeCompatible(): String =
    readSysFile("/sys/firmware/devicetree/base/compatible").replace('\u0000', ' ').trim()

/** المسار البديل نفسه: `/sys/firmware/…` تُحجب على بعض الأنظمة وهذا يُقرأ. */
private fun deviceTreeCompatibleFallback(): String =
    readSysFile("/proc/device-tree/compatible").replace('\u0000', ' ').trim()

/**
 * مطابقة هوية الشريحة — **منطق نقيّ** بلا Android ولا JSON، فيُقاس على الكتالوج الحقيقي في
 * اختبار JVM (`ChipsetIdentityTest`) لا على هاتف المالك.
 */
internal object ChipsetMatcher {

    /**
     * أرقام القطع المخبّأة داخل قيمة مركّبة واحدة.
     *
     * **والعطب الذي وُجدت من أجله مقيس:** `/sys/devices/soc0/machine` يُعلن على أجهزة كوالكوم
     * `Qualcomm Technologies, Inc SM8150`، وكانت المطابقة تجري على السلسلة **كاملة** فلا تُصيب
     * شيئًا، فلا يبقى إلا `Unknown (msmnile)` — وهو ما كانت الرئيسية تعرضه فعلًا. والقصّ إلى
     * أجزاء يُصيب `SM8150` في الكتالوج بلا أن يُغيّر ترتيب المطابقة ولا سلوك أي مصدر آخر.
     *
     * **وأثره مقيس لا مُدَّعى** (نفس قياس `PART_SEPARATOR` أعلاه): من ١٠٧٣ مفتاحًا في الكتالوج
     * لم يُعرَف منها قبل هذا التغيير في قالب آلة كوالكوم إلا **٢**، ويُعرَف بعده **٨٨٤**، ولم
     * يُكسر مفتاح واحد (كان يُعرَف فصار مجهولًا = ٠).
     */
    fun partsOf(value: String): List<String> =
        value.split(PART_SEPARATOR)
            .filter { part ->
                part.length >= MIN_PART_LEN &&
                    part.any { it.isLetter() } && part.any { it.isDigit() }
            }
            .distinct()

    /**
     * **والمطابقة نفسها لم تبقَ هنا** — انتقلت إلى `ChipsetResolver` في `ChipsetIdentity.kt`،
     * لأن القاعدة صارت أوسع من جدول: طبقات دليل، وقول المصنّع مقدَّمًا، ومرشّحون يُعرضون معًا
     * حين تحمل الشريحة أكثر من اسم. ومصدر واحد للقاعدة خير من نسختين تفترقان.
     */
}

/**
 * ما يُعلنه الجهاز عن شريحته، **ومعه من أين جاء**.
 *
 * والترتيب هنا ترتيب **قراءة** لا ترتيب **حكم**: الحكم في `ChipsetResolver` يحكم بطبقات الدليل
 * (رقم القطعة قبل كود المشروع) وبالمطابقة التامّة قبل الاستنتاج — فلا يسبق `ro.board.platform`
 * الرقمَ الذي في `soc0/machine` لمجرد أنه سبقه في هذه القائمة.
 *
 * **وما زاد على القائمة السابقة** (كلّه رخيص: خصيصة أو ملف صغير):
 * `ro.vendor.mediatek.platform` — الخصيصة التي تحمل الرقم فعلًا على أجهزة MediaTek الحديثة
 * (`ro.mediatek.platform` تُقرأ فارغة عليها)، و`ro.vendor.soc.model` — نظير `ro.soc.model`
 * عند بائعين يكتبون فيه الاسم التجاري، و`/proc/device-tree/compatible` — المسار البديل لشجرة
 * الأجهزة حين يُحجب `/sys/firmware/devicetree/base/…` عن التطبيق، وسطر `Hardware` من
 * `/proc/cpuinfo` كما كان.
 *
 * **وحدّها المُعلن:** هذه القيم تُقرأ من الجهاز ولا تُقاس هنا؛ المُقاس في اختبار JVM هو **ما
 * يُفعل بها** على الكتالوج الحقيقي. وأي حكم على أجهزة حقيقية يبقى «يحتاج جهازًا». ولهذا أيضًا
 * يُطبع كل مصدر بقيمته في تقرير الجهاز (`DeviceBlueprint`) فيُسمّى السبب في أول تقرير قادم.
 */
private fun declaredChipsetSources(): List<ChipsetSource> {
    val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.trim() else ""

    return listOf(
        // ── ما قد يكون **اسمًا تجاريًا** كتبه المصنّع: يُقدَّم ولا يُتصيَّد من الكتالوج ──
        ChipsetSource("Build.SOC_MODEL", socModel, ChipsetEvidence.SYSTEM_PROPERTY, declaresName = true),
        ChipsetSource("ro.soc.model", PropertyUtils.get("ro.soc.model").trim(), ChipsetEvidence.SYSTEM_PROPERTY, declaresName = true),
        ChipsetSource("ro.vendor.soc.model", PropertyUtils.get("ro.vendor.soc.model").trim(), ChipsetEvidence.SYSTEM_PROPERTY, declaresName = true),

        // ── الخصائص ──
        ChipsetSource("ro.board.platform", PropertyUtils.get("ro.board.platform").trim(), ChipsetEvidence.SYSTEM_PROPERTY),
        ChipsetSource("ro.hardware.chipname", PropertyUtils.get("ro.hardware.chipname").trim(), ChipsetEvidence.SYSTEM_PROPERTY),
        ChipsetSource("ro.chipname", PropertyUtils.get("ro.chipname").trim(), ChipsetEvidence.SYSTEM_PROPERTY),
        ChipsetSource("ro.mediatek.platform", PropertyUtils.get("ro.mediatek.platform").trim(), ChipsetEvidence.SYSTEM_PROPERTY),
        ChipsetSource("ro.vendor.mediatek.platform", PropertyUtils.get("ro.vendor.mediatek.platform").trim(), ChipsetEvidence.SYSTEM_PROPERTY),
        ChipsetSource("ro.product.board", PropertyUtils.get("ro.product.board").trim(), ChipsetEvidence.SYSTEM_PROPERTY),

        // ── النواة: ما أعلنته عن نفسها ──
        ChipsetSource("/sys/devices/soc0/machine", readSysFile("/sys/devices/soc0/machine"), ChipsetEvidence.KERNEL_SYSFS),
        ChipsetSource("/sys/devices/soc0/family", readSysFile("/sys/devices/soc0/family"), ChipsetEvidence.KERNEL_SYSFS),
        ChipsetSource("/sys/devices/soc0/soc_id", readSysFile("/sys/devices/soc0/soc_id"), ChipsetEvidence.KERNEL_SYSFS),
        ChipsetSource("/proc/cpuinfo Hardware", cpuInfoHardware(), ChipsetEvidence.CPU_INFO),
        ChipsetSource("/sys/firmware/devicetree/base/compatible", deviceTreeCompatible(), ChipsetEvidence.DEVICE_TREE),
        ChipsetSource("/proc/device-tree/compatible", deviceTreeCompatibleFallback(), ChipsetEvidence.DEVICE_TREE),

        // ── حقول البناء: آخر ما يُسأل، أوسعها وأقلّها دقّة ──
        ChipsetSource("Build.HARDWARE", Build.HARDWARE.trim(), ChipsetEvidence.BUILD_FIELD),
        ChipsetSource("Build.BOARD", Build.BOARD.trim(), ChipsetEvidence.BUILD_FIELD),

        // ── تقوية لا تسمية (انظر `corroborates`) ──
        // ولا واحد منها يسمّي شريحة: معالج الرسوم **مشترك** بين شرائح (`Adreno 740` في 8 Gen 2
        // وأخواتها)، والبائع اسم شركة لا شريحة. فتُقرأ للتقرير ولتأكيد **البائع** وحدهما، ولا تُنتج
        // اسمًا — وهو نصّ طلب المالك: «اجمع الأدلة… وvendor وCPU/GPU عند توفرها» جمعًا بلا اختراع.
        ChipsetSource("ro.soc.manufacturer", PropertyUtils.get("ro.soc.manufacturer").trim(), ChipsetEvidence.SYSTEM_PROPERTY, corroborates = true),
        ChipsetSource("ro.hardware.egl", PropertyUtils.get("ro.hardware.egl").trim(), ChipsetEvidence.SYSTEM_PROPERTY, corroborates = true),
        ChipsetSource("/sys/class/kgsl/kgsl-3d0/gpu_model", readSysFile("/sys/class/kgsl/kgsl-3d0/gpu_model"), ChipsetEvidence.GPU_INFO, corroborates = true),
    )
}

/**
 * GPU كوالكوم يُعلن نفسه: `kgsl` (مسار النواة) أو `Adreno`.
 *
 * **ولا تُعمَّم على `Mali`**: تستعملها MediaTek وUnisoc وRockchip وغيرها، فاستدلال البائع منها
 * **خاطئ** لا ناقص — وهذا فرق يُقاس: تقوية تُخطئ أسوأ من تقوية لا تُقال.
 */
private fun isQualcommGpu(value: String): Boolean {
    val gpu = value.lowercase()
    return "adreno" in gpu || "kgsl" in gpu
}

/** كتالوج `socs.json` بعد قراءته، و`null` تعني «لم تُقرأ بعد». */
@Volatile private var socCatalogCache: Map<String, SocEntry>? = null

/**
 * الكتالوج: مفتاح ← صفّ (`VENDOR` و`NAME`).
 *
 * **ويُقرأ مرة واحدة:** كانت الدالة تفتح الأصل وتُحلّل ١٠٧٣ مفتاحًا في **كل** نداء، ويُنادى
 * بها من بطاقة الرئيسية وبصمة الجهاز والسجل وشاشة الأنوية. ولا يُخزَّن الفشل: أصل تعذّرت
 * قراءته اليوم قد يُقرأ غدًا، وتخزين الفراغ يجعل «مجهول» أبديًّا بلا دليل.
 */
private fun socCatalog(context: Context): Map<String, SocEntry> {
    socCatalogCache?.let { return it }
    val parsed = try {
        readSocCatalog(context.assets.open("socs.json").bufferedReader().use { it.readText() })
    } catch (e: Exception) {
        e.printStackTrace()
        emptyMap()
    }
    if (parsed.isNotEmpty()) socCatalogCache = parsed
    return parsed
}

internal fun readSocCatalog(jsonText: String): Map<String, SocEntry> {
    val json = JSONObject(jsonText)
    val map = LinkedHashMap<String, SocEntry>(json.length())
    val iterator = json.keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        val row = json.optJSONObject(key)
        map[key] = SocEntry(
            vendor = row?.optString("VENDOR", "").orEmpty().trim(),
            name = row?.optString("NAME", "").orEmpty().trim(),
        )
    }
    return map
}

/** علامات تجارية لا معنى لها في سطر هوية على شاشة: تُزال وتُطوى المسافات. */
internal fun cleanSocName(raw: String): String =
    raw.replace("®", "").replace("™", "").replace("©", "").replace(Regex("\\s+"), " ").trim()

/**
 * حلّ هوية الشريحة: **ما يُعرض** ومعه دليله.
 *
 * والمنطق كلّه في `ChipsetResolver` (وحدة نقيّة تُقاس بلا جهاز)، وهذا الملفّ يقرأ ما أعلنه
 * الجهاز ويُسمّي مصدره — فلا تُكتب القاعدة في موضعين.
 *
 * **و`internal` هنا شرط ترجمة لا ذوق:** نوع الإرجاع `ChipsetIdentity` داخليّ، وإعلانٌ عامّ
 * يُعرّضه يرفضه مُصرّف Kotlin — فالدالّة داخليّة مثل نوعها. ومستدعيها اليوم `DeviceBlueprint`
 * في الوحدة نفسها (‏`:app` وحدة واحدة، فلا حاجة إلى تعريضه للخارج).
 */
internal fun getChipsetIdentity(context: Context): ChipsetIdentity =
    ChipsetResolver.resolve(socCatalog(context), declaredChipsetSources())

fun getChipsetName(context: Context): String = getChipsetIdentity(context).display

/**
 * بائع الشريحة: من **صفوف الكتالوج** أولًا، ثم الخصائص كما كانت.
 *
 * والعطب الذي أُصلح هنا: كان البائع يُستنتج من **سطر العرض**، فحين صار السطر مجموع مرشّحين
 * (`"MediaTek Dimensity 8300 / 8350"`) لم يبقَ فيه ما يُقرأ منه… فالاستنتاج صار من بيانات
 * الصفّ، والفراغ يعني «لا أقول» فيُسأل المصدر التالي.
 */
fun getChipsetVendor(context: Context): String {
    val identity = getChipsetIdentity(context)
    identity.vendors.map(::vendorKeyOf).firstOrNull { it.isNotEmpty() }?.let { return it }

    if (!identity.unknown) {
        val name = identity.display.lowercase()
        return when {
            "mediatek" in name -> "mediatek"
            "qualcomm" in name || "snapdragon" in name -> "qualcomm"
            else -> "unknown"
        }
    }

    // وتقوية البائع من الأدلة التي لا تُسمّي شريحة: `ro.soc.manufacturer` ثمّ GPU من نوع Adreno/kgsl.
    // ولا تُستعمل `mali`: تستعملها MediaTek وUnisoc وغيرها، فاستدلال البائع منها **خاطئ** لا ناقص.
    identity.sources.firstOrNull { it.corroborates && vendorKeyOf(it.value).isNotEmpty() }
        ?.let { source -> return vendorKeyOf(source.value) }
    if (identity.sources.any { it.corroborates && isQualcommGpu(it.value) }) return "qualcomm"

    val socModel      = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.lowercase() else ""
    val boardPlatform = PropertyUtils.get("ro.board.platform").lowercase()
    val chipname      = PropertyUtils.get("ro.hardware.chipname").lowercase()
    val mtPlatform    = PropertyUtils.get("ro.mediatek.platform").lowercase()

    return when {
        socModel.startsWith("mt")      ||
        boardPlatform.startsWith("mt") ||
        chipname.startsWith("mt")      ||
        mtPlatform.isNotEmpty()            -> "mediatek"

        socModel.startsWith("sm")      ||
        socModel.startsWith("msm")     ||
        boardPlatform.startsWith("sm") ||
        boardPlatform.startsWith("msm")    -> "qualcomm"

        else -> "unknown"
    }
}

/**
 * **لقياس التوافق وحده:** نفس الحلّ بقيم بلا تسمية مصدر — وهو الشكل الذي كانت تُنادى به
 * الدالّة قبل هذا التغيير، فتبقى أشكال الأجهزة في الاختبار حرفيّة كما كانت.
 */
internal fun resolveChipsetName(catalog: Map<String, SocEntry>, declared: List<String>): String =
    resolveChipset(
        catalog,
        declared.map { ChipsetSource("declared", it, ChipsetEvidence.SYSTEM_PROPERTY) },
    ).display
/** **القياس**: نفس الحلّ بمصادر مُسمّاة — فلا يُمحى الفرق بين «قول المصنّع» و«رقم قطعة». */
internal fun resolveChipset(catalog: Map<String, SocEntry>, sources: List<ChipsetSource>): ChipsetIdentity =
    ChipsetResolver.resolve(catalog, sources)
