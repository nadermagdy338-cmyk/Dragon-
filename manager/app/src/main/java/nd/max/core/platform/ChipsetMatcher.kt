/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.platform

/*
 * قصّ قيمة مركّبة إلى أرقام قطع — **منطق نقيّ بلا Android ولا JSON**.
 *
 * ولماذا فُصل في ملفّه بعد أن كان داخل `HardwareUtil.kt`: كان معرّفه هناك **لا يُترجَم ولا يُقاس**
 * بلا Android، فبقيت قاعدته (وهي حكم على نصّ لا على جهاز) خارج أي قياس في بيئة بلا SDK. ونقله
 * إلى ملفّ بلا استيراد يجعل `ChipsetPartCodeLineTest` و«مطابقة الهوية» قابلين للتشغيل بُمصرِّف
 * Kotlin وحده. **ولا سلوك تغيّر بالحرف:** الكائن في الحزمة نفسها والاسم نفسه، فكل مستدعٍ يبقى كما هو.
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
 * متغيّرات الرمز نفسه: `MT6897Z_A/ZA` → نفسه ثم `MT6897Z_A` ثم `MT6897Z` ثم `MT6897` —
 * فالرمز يُطابَق كما أُعلن، ثم بجذوره الشائعة، بلا اختراع رمز جديد.
 */
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

/** علامات تجارية لا معنى لها في سطر هوية على شاشة: تُزال وتُطوى المسافات. */
internal fun cleanSocName(raw: String): String =
    raw.replace("®", "").replace("™", "").replace("©", "").replace(Regex("\\s+"), " ").trim()
