/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
هوية الشريحة: **ما أعلنه الجهاز** ← **اسم مدعوم بدليل**.

العطب الذي وُلدت هذه الوحدة من أجله (مقيس على الكتالوج المشحون، لا محكيّ):
١. **اسم كود المشروع كان يُسمّي شريحة واحدة.** `lahaina` عائلة تشمل `SM8350`
   (‏Snapdragon 888) و`SM7325` (‏778G)، والكتالوج يسجّل لها اسمًا واحدًا — وكان
   `ro.board.platform` (وهو كود) يسأل **قبل** `soc0/machine` (وهو رقم القطعة)، فيُعرض
   778G على جهاز 888. ومثله `KONA` و`LITO`.
٢. **المطابقة بالبادئة كانت تُؤلّف اسمًا.** الكتالوج لا يحمل مفتاح `MT6897`، بل يحمل
   `MT6897Z/ZA → Dimensity 8300` و`MT6897Z_A/ZA → Dimensity 8350` … فجهاز يعلن `MT6897`
   كان يفوز بمفتاح **بحساب الطول** — لا بدليل — فيُعرض اسم واحد من بين اثنين.
٣. **الشكّ لم يكن يُعلن.** المُخرَج كان `String` واحدًا، فحين تحمل الشريحة أسماء تجارية
   عدة كان الباقي يُخفى.
٤. **رقم قطعة واحد تحمله شريحتان تجاريتان.** وهذه شكوى المالك المنقولة في هذه الجولة:
   «بعض الأحيان يكتب اسم معالج خاطئ لقربهم من نفس العائلة مثل ميدياتك 8300 و 8350». والقيس:
   `MT6897Z_A/ZA` هو `ro.soc.model` في شجرة جهاز حقيقية (`device_xiaomi_duchamp` ⟵ **Redmi
   K70E / POCO X6 Pro**، وسوقه **Dimensity 8300-Ultra** — و`ro.soc.manufacturer=Mediatek`
   فيها)، وتُعلنه أجهزة **Dimensity 8350** أيضًا؛ والكتالوج المشحون يسجّله باسم واحد (‏8350)
   فيُعرض **8350 على جهاز 8300-Ultra**. والرمز المشترك **لا يُسمّى باسم واحد** بعد اليوم.

فالقاعدة هنا واحدة: **ما دعمه الدليل يُقال، وما لم يدعمه يُقال إنّه غير معروف أو يُعرض
مرشّحًا مع أخيه** — ولا يُخترع اسم، ولا يُرجَّح مرشّح بلا مرجّح.
 */

package nd.max.core.platform

/**
 * لاحقة «صفّ قديم» في الكتالوج: مفتاح يشير إلى **القطعة نفسها** بمفتاح أقدم
 * (`MT6897Z_A/ZA_OLD` ⟵ `MT6897Z_A/ZA`)، فتُقرأ توأمًا لا شريحة ثانية.
 */
private const val OLD_ROW_SUFFIX = "_OLD"

/** بادئة «المجهول» — مرجع واحد فلا تُنسخ في موضعين. */
internal const val UNKNOWN_LABEL_PREFIX = "Unknown ("

/** الكلمة التي لا تُعلن شريحة: خاصية غير مضبوطة تُقرأ `"unknown"` على أجهزة كثيرة. */
private const val UNKNOWN_WORD = "unknown"

/**
 * أدنى طول لرمز يُقبل في **الاستنتاج بالبادئة**.
 *
 * والرمز القصير جدًّا تطابقه مصادفةً (`qsd8` تطابق عائلة كاملة) — فلا يُستنتج منه.
 */
internal const val MIN_INFERRED_CODE_LEN = 5

/** طبقة الدليل التي جاءت منها القيمة — تُطبع في تقرير الجهاز فيُسمّى الدليل لا يُستنتج. */
internal enum class ChipsetEvidence(val label: String) {
    DEVICE_TREE("device-tree"),
    KERNEL_SYSFS("kernel-sysfs"),
    CPU_INFO("proc-cpuinfo"),
    GPU_INFO("gpu-sysfs"),
    SYSTEM_PROPERTY("system-property"),
    BUILD_FIELD("build-field"),
}

/**
 * حالة الحلّ — ثلاث لا رابعة، ويُعلنها تقرير الجهاز كما هي.
 *
 * والفرق بينها هو **قوّة الدليل لا جمال الاسم**، وهذا نصّ طلب المالك: «الدقة أهم من إعطاء
 * اسم؛ Unknown أفضل من اسم خاطئ»:
 *
 * * [EXACT] — تأكيد واحد لا ينازعه تأكيد: مفتاح كتالوج تامّ باسم واحد، أو قول مصنّع واحد.
 * * [AMBIGUOUS] — تأكيدان لا يتقاطعان، أو رمز أساسي تحمله أسماء عدة، **أو رقم قطعة تحمله
 *   شريحتان تجاريتان** (`MT6897Z_A/ZA`) ⇒ تُعرض الأسماء معًا ولا يُختار أحدها.
 * * [UNKNOWN] — لا تأكيد، أو رمز لا يعرفه الكتالوج ⇒ يُقال الرمز المعلَن ولا يُخترع اسم.
 */
internal enum class ChipsetMatch { EXACT, AMBIGUOUS, UNKNOWN }

/**
 * صفّ كتالوج: **البائع والاسم مفصولين**.
 *
 * والفصل ليس تجميلًا: العرض صار قد يجمع مرشّحين فيسأل عن بائعهم ومشتركهم، وقيمة واحدة
 * مدموجة (`"MediaTek Dimensity 8350"`) لا تُجيب.
 */
internal data class SocEntry(val vendor: String, val name: String) {

    /** النصّ المعروض: البائع ثم الاسم، بلا علامات تجارية. */
    val label: String
        get() = cleanSocName(
            listOf(vendor, name).filter { it.isNotEmpty() }.joinToString(" ")
        )
}

/** قيمة أعلنها الجهاز ومصدرها. */
internal data class ChipsetSource(
    val label: String,
    val value: String,
    val evidence: ChipsetEvidence,
    /**
     * `SOC_MODEL` وحده: قيمة **اسم تجاري كتبه المصنّع** لا رقم قطعة.
     * ولهذا حكم مختلف: قول المصنّع يُقدَّم ولا يُتصيَّد من الكتالوج.
     */
    val declaresName: Boolean = false,
    /**
     * **يُقوّي ولا يُسمّي.** قيمة لا تصلح للتسمية أصلًا (بائع، أو معالج رسوم مشترك بين شرائح)،
     * فمصيرها: تُطبع في تقرير الجهاز، ويُستأنس بها في **اسم البائع** — ولا تُنتج اسم شريحة.
     * وهذا نصّ طلب المالك: «اجمع الأدلة… وvendor وCPU/GPU عند توفرها» — جمعًا بلا اختراع.
     */
    val corroborates: Boolean = false,
)

/** نتيجة الحلّ: ما يُعرض، وما يبرّره، وما بقي مجهولًا. */
internal data class ChipsetIdentity(
    val display: String,
    val match: ChipsetMatch = ChipsetMatch.UNKNOWN,
    val partCode: String? = null,
    val declaredName: String? = null,
    val candidates: List<String> = emptyList(),
    val matchedKey: String? = null,
    val vendors: List<String> = emptyList(),
    val evidence: ChipsetEvidence? = null,
    /**
     * الاسم جاء من **استنتاج بالبادئة** لا من مفتاح تامّ — وهو أضعف الدليلين فيُقال.
     * (والحكم يبقى [ChipsetMatch.EXACT] إن لم ينازعه تأكيد: اسم واحد لا خلاف عليه.)
     */
    val inferred: Boolean = false,
    val sources: List<ChipsetSource> = emptyList(),
) {
    /** أكثر من اسم تجاري لشريحة واحدة ⇒ العرض يجمعها ولا يرجّح. */
    val ambiguous: Boolean get() = match == ChipsetMatch.AMBIGUOUS

    val unknown: Boolean get() = match == ChipsetMatch.UNKNOWN
}

/**
 * حلّ هوية الشريحة — **منطق نقيّ**: لا Android ولا JSON ولا ملف، فيُقاس على الكتالوج
 * الحقيقي في اختبار JVM (`ChipsetIdentityTest`).
 */
internal object ChipsetResolver {

    fun resolve(catalog: Map<String, SocEntry>, sources: List<ChipsetSource>): ChipsetIdentity {
        val declared = sources
            .map { it.copy(value = cleanSocName(it.value)) }
            .filter { it.value.isNotEmpty() && !it.value.equals(UNKNOWN_WORD, ignoreCase = true) }
        if (declared.isEmpty()) return unknown("SoC", declared)

        // المراجع (`corroborates`) لا تُسمّي: GPU وبائع لا يسمّيان شريحة، فلا تُسأل عن اسم أصلًا.
        val assertions = declared.filterNot { it.corroborates }
        if (assertions.isEmpty()) return unknown("SoC", declared)

        // ── (٠) طبقة التأكيدات: كل مصدر يقول **ما يسمّيه**، ثم تُطابَق التأكيدات بعضها ببعض ──
        // وهذه هي القاعدة التي طلبها المالك: «طابق الأدلة مع الكتالوج بدل اختيار أول نتيجة».
        // والتماثل **بالاسم المعروض** لا بمفتاح الصفّ: مفاتيح عدة تحمل الاسم نفسه
        // (`SM8650` وأخواته)، فالمُهمّ أن تتفق المصادر على الاسم لا على الصفّ الذي بلغته.
        val claims = LinkedHashMap<String, Claim>()
        assertions.forEach { source ->
            claimOf(catalog, source)?.let { claim -> claims.putIfAbsent(claimClassOf(claim), claim) }
        }
        if (claims.isNotEmpty()) {
            val rows = claims.values.toList()
            // وكل تأكيد **مجموعة أسماء يجيزها** (ورقم القطعة المشترك يجيز اسمين)، والحلّ ما أجازته
            // كلّها: تقاطعها غير الفارغ هو الجواب — وهو ما يجعل «8350» المُعلَن في `SOC_MODEL`
            // يُضيّق رمزًا مشتركًا إلى اسمه — وفراغه تعارضٌ حقيقي يُعرض اتحادُه ولا يُنتقى منه.
            val agreed = rows.map { it.display.toSet() }.reduce { acc, names -> acc intersect names }
            val names = (if (agreed.isEmpty()) rows.flatMap { it.display } else agreed.toList())
                .distinct()
                .sortedWith(BY_LAST_NUMBER_THEN_TEXT)
            return ChipsetIdentity(
                display = joinCandidates(names),
                match = if (names.size == 1) ChipsetMatch.EXACT else ChipsetMatch.AMBIGUOUS,
                partCode = partCodeOf(declared),
                declaredName = rows.firstOrNull { it.source.declaresName }?.source?.value,
                candidates = if (names.size == 1) emptyList() else names,
                matchedKey = rows.firstNotNullOfOrNull { it.key },
                vendors = rows.flatMap { it.vendor }.filter { it.isNotEmpty() }.distinct(),
                evidence = rows.first().source.evidence,
                sources = declared,
            )
        }

        // ── (١) و(٢) ولا تأكيد؟ فالاستنتاج بالبادئة — **آخر** الاحتمالات لا أوّلها ──
        // والترتيب هو الإصلاح الأول بعينه: قيمة **تحمل رقم قطعة** تُسأل قبل أي كود مشروع،
        // فلا يسبق `lahaina` الرمزَ `SM8350` لأن الأول سبقه في القائمة.
        for (tier in listOf(assertions.filter { carriesPartCode(it.value) }, assertions.filterNot { carriesPartCode(it.value) })) {
            if (tier.isEmpty()) continue
            val codes = searchCodesOf(tier.map { it.value })

            // وكل ما تطابقه البادئة يُجمع، ثم يُعرض جماعةً إن اختلفت أسماؤه — لا فائز بالطول.
            val inferred = inferByPrefix(catalog, codes)
            if (inferred.isNotEmpty()) {
                val names = inferred.map { baseName(it.second.label) }
                    .distinct()
                    .sortedWith(BY_LAST_NUMBER_THEN_TEXT)
                return ChipsetIdentity(
                    display = joinCandidates(names),
                    match = if (names.size == 1) ChipsetMatch.EXACT else ChipsetMatch.AMBIGUOUS,
                    partCode = partCodeIn(codes),
                    candidates = if (names.size == 1) emptyList() else names,
                    matchedKey = inferred.first().first,
                    vendors = inferred.map { it.second.vendor }.distinct(),
                    evidence = tier.first().evidence,
                    inferred = true,
                    sources = declared,
                )
            }
        }

        // ── (٣) المجهول يُسمّى برمز القطعة الذي أعلنه الجهاز — والرمز حقيقة والاسم المخترع كذبة ──
        val fallback = assertions.map { it.value }
            .flatMap { listOf(it) + ChipsetMatcher.partsOf(it) }
            .firstOrNull(::carriesPartCode)
            ?: assertions.first().value
        return unknown(ChipsetMatcher.partsOf(fallback).firstOrNull() ?: fallback, declared)
    }

    /**
     * تأكيد مصدر واحد: ما يسمّيه، ومفتاح الصفّ الذي بلغه إن بلغه، ومن أيّ مصدر.
     *
     * و«ما يسمّيه» **قائمة لا نصًّا**، لأن رقم القطعة الواحد قد تحمله شريحتان تجاريتان
     * (`MT6897Z_A/ZA` ⟵ 8300-Ultra و8350) — فالتأكيد مجموعة أسماء عندما يكون الرمز مشتركًا.
     */
    private data class Claim(
        val display: List<String>,
        val vendor: List<String>,
        val key: String?,
        val source: ChipsetSource,
    )

    /**
     * صنف التأكيد: ما يجعل تأكيدين **الشريحة نفسها**، وهو الأساس الذي يجعل التعارض يُعلن.
     *
     * والقاعدة: الاسم بلا «أيضًا يُسمّى» — فـ`Kirin 970/975` صفٌّ واحد يسمّي شريحة واحدة باسمين،
     * فيُشطر إلى `Kirin 970` ويوافق تأكيدًا آخر يقول `Kirin 970`. وأمّا `Kirin 960` و`970`،
     * و`8300` و`8350`، و`8 Gen 3` و`8 Gen 4` فأسماء **شرائح مختلفة** — لا تتّفق، فيُعلن التعارض
     * بدل أن يُرجَّح أحدهما كما كان (وهو العطب الذي جاء منه تقرير «8350 على جهاز 8300»).
     */
    private fun claimClassOf(claim: Claim): String =
        claim.display.map { normalizeSocCode(it.substringBefore('/')) }.sorted().joinToString("/")

    /**
     * ما تؤكّده قيمةٌ واحدة — **والترتيب داخل القيمة لا يتغيّر**:
     * قول المصنّع، ثم المفتاح التامّ، ثم صورة الرمز المخفّفة (`searchCodesOf`).
     *
     * "ولا يتغيّر" مقصود: القيمة الواحدة تحمل **صور شريحة واحدة** (`SM8350-AC` و`SM8350`),
     * فاختيار أوّلها هو الأدقّ لا الأسوأ (ولو جُمعت لصارت كل شريحة `-AC` مرشّحين اثنين).
     * والتعارض المقصود إعلانه هو **بين المصادر** — حيث تكون الأدلة مستقلة فعلًا.
     */
    private fun claimOf(catalog: Map<String, SocEntry>, source: ChipsetSource): Claim? {
        if (source.declaresName && !carriesPartCode(source.value) && source.value.any(Char::isDigit)) {
            val hit = keyMatches(catalog, source.value) ?: nameMatch(catalog, source.value)?.let { listOf(it) }
            return if (hit != null) {
                claimFor(hit, source)
            } else {
                // اسم لا يعرفه الكتالوج يُؤكَّد كما هو — لا يُدخل إلى الكتالوج ولا يُخترع له صفّ.
                Claim(listOf(source.value), emptyList(), null, source)
            }
        }
        if (!carriesPartCode(source.value)) {
            // ولا رقم قطعة؟ فالمفتاح **التامّ** وحده يُسأل — لا بالبادئة (انظر `inferByPrefix`).
            //
            // وهذه ليست توسعة نطاق بل **إعادة** دليل كان مقبولًا قبل طبقة التأكيدات: الكتالوج
            // يحمل صيغ آلة كاملة كمفاتيح (`Qualcomm Technologies, Inc 450`)، ويحمل `soc_id`
            // مجرّدًا (`8626` ← Snapdragon 400) — والجهاز يعلن أحدها حرفيًّا. فالمفتاح التامّ
            // **دليل** لا تخمين؛ وما ليس مفتاحًا (`qualcomm` · `shiba`) لا يُقبل بنصّه.
            //
            // و`keyMatch` يستثني الأكواد (`lahaina` · `kona`) على كل حال، فلا يعود اسم مشروع
            // يسمّي شريحة من عائلة — وهو العطب الأول الذي وُلدت هذه الوحدة من أجله.
            val exact = keyMatches(catalog, source.value) ?: return null
            return claimFor(exact, source)
        }
        val hit = keyMatchesOf(catalog, searchCodesOf(listOf(source.value))) ?: return null
        return claimFor(hit, source)
    }

    /** تأكيد من صفوف بلغها رمزٌ: أسماء الصفوف وبائعوها ومفتاح أوّلها. */
    private fun claimFor(rows: List<Pair<String, SocEntry>>, source: ChipsetSource): Claim =
        Claim(
            display = rows.map { it.second.label }.distinct(),
            vendor = rows.map { it.second.vendor }.filter { it.isNotEmpty() }.distinct(),
            key = rows.firstOrNull()?.first,
            source = source,
        )

    /**
     * أول رمز تُصيبه القيم بترتيبها — تامًّا ثم مُطبَّعًا — **مع توائمه**.
     * و`null` تعني «لم يُصب شيء» لا «صفّ واحد».
     */
    private fun keyMatchesOf(catalog: Map<String, SocEntry>, codes: List<String>): List<Pair<String, SocEntry>>? {
        codes.forEach { code -> keyMatches(catalog, code)?.let { return it } }
        return null
    }

    /**
     * **كل** صفوف الكتالوج التي تسمّي القطعة التي يسمّيها هذا الرمز — لا أوّلها فقط.
     *
     * والعطب المقيس الذي وُلدت من أجله: `MT6897Z_A/ZA` يُعلنه `ro.soc.model` على
     * **Redmi K70E / POCO X6 Pro** (‏Dimensity 8300-Ultra)، وتُعلنه أجهزة **Dimensity 8350**
     * كذلك — والكتالوج يسجّله باسم واحد، فكان صاحب K70E يرى «8350». وإنّ وجود
     * `MT6897Z_A/ZA_OLD` ⟵ Dimensity 8300-Ultra في الكتالوج هو أثر الحقيقة نفسها: الرمز
     * الواحد باسمين. فالرمز المشترك يُعرض باسمَيه ولا يُنتقى منه اسم.
     *
     * وحدّه المُعلن: الكتالوج هو من يقول أيّ رمز مشترك (بصفٍّ توأم كما في `_OLD`)، فلا يُدّعى
     * أن كل اشتراك في العالم صار معلومًا — وإنما ألّا **يُدَّعى** اسم واحد لرمز يحمله صفّان.
     */
    private fun keyMatches(catalog: Map<String, SocEntry>, code: String): List<Pair<String, SocEntry>>? {
        val primary = keyMatch(catalog, code) ?: return null
        val twins = catalog.entries
            .filter { entry ->
                entry.key != primary.first &&
                    entry.value.label.isNotEmpty() &&
                    !isCodenameKey(entry.key) &&
                    samePart(primary.first, entry.key)
            }
            .map { it.key to it.value }
        return (listOf(primary) + twins).distinctBy { it.second.label }
    }

    /**
     * صفّان يسمّيان **القطعة نفسها**: تطابق حرفيّ، أو لاحقة «صفّ قديم» (`_OLD`) على أحدهما
     * تشير إلى الآخر — وهي رصدٌ للحقيقة لا صناعةٌ لها.
     */
    private fun samePart(first: String, second: String): Boolean =
        first.equals(second, ignoreCase = true) ||
            aliasBaseOf(first)?.equals(second, ignoreCase = true) == true ||
            aliasBaseOf(second)?.equals(first, ignoreCase = true) == true

    /** المفتاح بلا لاحقة الصفّ القديم، أو `null` إن لم يحملها. */
    private fun aliasBaseOf(key: String): String? =
        if (key.length > OLD_ROW_SUFFIX.length && key.endsWith(OLD_ROW_SUFFIX, ignoreCase = true)) {
            key.dropLast(OLD_ROW_SUFFIX.length)
        } else {
            null
        }

    /**
     * مفتاح الكتالوج المطابق لرمز: تطابق حرفيّ، ثم تطابق بعد إسقاط الفواصل وحالة الأحرف.
     *
     * و**مفاتيح الأكواد مستثناة**: مفتاح بلا رقم (`lahaina` · `kona` · `msmnile`) اسم مشروع لا
     * شريحة، ويسمّي في الكتالوج شريحة واحدة من عائلة تشمل عدة — فالمطابقة عليه هي العطب الأول
     * بعينه (`lahaina → 778G` على جهاز `SM8350`). فإن لم يبق للجهاز إلا كود مشروع قيل «مجهول»
     * بالكود المعلن، ولم يُنطق اسم مخترع.
     *
     * وهذا **صفّ واحد** لا مجموعة: الجمع في [keyMatches] وحده، فلا يصير هذا التابع مُجمِّعًا.
     */
    private fun keyMatch(catalog: Map<String, SocEntry>, code: String): Pair<String, SocEntry>? {
        catalog.entries.firstOrNull { it.key.equals(code, ignoreCase = true) && !isCodenameKey(it.key) }
            ?.takeIf { it.value.label.isNotEmpty() }
            ?.let { return it.key to it.value }
        val norm = normalizeSocCode(code)
        if (norm.isEmpty()) return null
        return catalog.entries.firstOrNull { normalizeSocCode(it.key) == norm && !isCodenameKey(it.key) }
            ?.takeIf { it.value.label.isNotEmpty() }
            ?.let { it.key to it.value }
    }

    /** صفّ الكتالوج الذي يحمل هذا **الاسم** (لا الرمز) — لقول المصنّع حين يطابق الكتالوج. */
    private fun nameMatch(catalog: Map<String, SocEntry>, text: String): Pair<String, SocEntry>? {
        val norm = normalizeSocCode(text)
        if (norm.isEmpty()) return null
        return catalog.entries.firstOrNull { normalizeSocCode(it.value.name) == norm }
            ?.takeIf { it.value.label.isNotEmpty() }
            ?.let { it.key to it.value }
    }

    /**
     * ما تطابقه البادئة من مفاتيح — **مجموعة لا فائز**.
     *
     * وهنا يُغلق العطب الثالث: القاعدة القديمة كانت تختار أقصر مفتاح بحساب طول، فيُعرض اسم
     * واحد لشريحة تحمل اسمين. الآن تُجمع كل المطابقات ويُعرض أعلاها صفًّا واحدًا.
     */
    private fun inferByPrefix(catalog: Map<String, SocEntry>, codes: List<String>): List<Pair<String, SocEntry>> {
        val needles = codes.map(::normalizeSocCode).filter(::isInferableCode)
        if (needles.isEmpty()) return emptyList()
        return catalog.entries
            .filter { entry ->
                entry.value.label.isNotEmpty() &&
                    !isCodenameKey(entry.key) &&
                    needles.any { normalizeSocCode(entry.key).startsWith(it) }
            }
            .map { it.key to it.value }
    }

    /**
     * كل ما يُسأل عنه الكتالوج، بترتيبه: القيم، ثم أرقام القطع داخلها، ثم صيغها المخفّفة.
     *
     * والصيغ المخفّفة (`MT6789V/CZA` ← `MT6789`) هي **آخر** ما يُسأل، فلا تتقدّم على الأدقّ.
     */
    private fun searchCodesOf(values: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        values.forEach { out += it }
        values.forEach { value -> out += ChipsetMatcher.partsOf(value) }
        values.forEach { value ->
            out += extractChipVariants(value)
            ChipsetMatcher.partsOf(value).forEach { part -> out += extractChipVariants(part) }
        }
        return out.toList()
    }

    /**
     * رقم القطعة كما سيُعرض في التقرير: **مستخرجًا** لا جملةً كاملة.
     *
     * و`soc0/machine` يُعلن `Qualcomm Technologies, Inc SM8350`، فتركُه جملةً في التقرير كان
     * يجعل «رقم القطعة» اسم شركة وماركة معًا — وهو الذي يُقارَن، فلا يُقارَن بجملة.
     */
    private fun partCodeIn(codes: List<String>): String? = codes.firstNotNullOfOrNull { code ->
        ChipsetMatcher.partsOf(code).firstOrNull() ?: code.takeIf(::carriesPartCode)
    }

    private fun partCodeOf(declared: List<ChipsetSource>): String? =
        partCodeIn(declared.filterNot { it.declaresName }.map { it.value })

    private fun unknown(code: String, sources: List<ChipsetSource>) =
        ChipsetIdentity(display = "$UNKNOWN_LABEL_PREFIX$code)", sources = sources)
}

/**
 * هل يصلح الرمز **للاستنتاج بالبادئة**؟ — وثلاثة شروط، كلٌّ منها من عطب مقيس لا من ذوق:
 *
 * * **رقم** — الكتالوج يسمّي بأرقام قطع، ومفتاح بلا رقم (‏`Tensor` · `lahaina` · `kona`) كود
 *   مشروع يُستثنى في [isCodenameKey] أصلًا، فلا يُطابَق.
 * * **حرف معه** — رقم مجرّد (`soc_id` = `519`) ليس رقم قطعة، ومطابقته بالبادئة **مصادفة** لا دليل.
 *   وما ليس مفتاحًا تامًّا لا يُسمّى (وهذا فرق يُقاس: `8626` مفتاح فتُقبل بالتماثل، و`519` ليس
 *   مفتاحًا فلا يُقبل).
 * * **الطول** ([MIN_INFERRED_CODE_LEN]) — `qsd8` تطابق عائلة كاملة بحساب الحروف.
 *
 * **والعطب الذي أُغلق بهذا الشرط:** جهاز أعلن `qualcomm` وحدها (كلمة بائع في `SOC_MODEL`) كان
 * يُعرض له «Qualcomm Snapdragon 450» — لأن الكتالوج يحمل المفتاح `Qualcomm Technologies, Inc 450`
 * والبادئة تُطابقه. والبادئة الآن لا تُسأل إلا عن **رقم قطعة**، والبائع يُقوّي ولا يسمّي.
 */
private fun isInferableCode(needle: String): Boolean =
    needle.length >= MIN_INFERRED_CODE_LEN &&
        needle.any(Char::isDigit) &&
        needle.any(Char::isLetter)

/**
 * هل تحمل القيمة **رقم قطعة**؟
 *
 * والشرط الأخير هو الفرق بين رقم واسم مكتوب بكلمتين: `Tensor G3` و`Dimensity 8350` أسماء
 * أعلنها المصنّع (فيها فراغ)، و`SM8350` و`MT6833V/ZA` أرقام قطع.
 */
internal fun carriesPartCode(value: String): Boolean {
    if (ChipsetMatcher.partsOf(value).isNotEmpty()) return true
    val token = value.trim()
    return token.isNotEmpty() &&
        token.none { it.isWhitespace() } &&
        token.any { it.isLetter() } && token.any { it.isDigit() }
}

/** توحيد الرمز للمقارنة: بلا فواصل ولا حالة أحرف. */
internal fun normalizeSocCode(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

/**
 * مفتاح بلا رقم = **كود مشروع** (اسم تطويري) لا اسم شريحة: `lahaina` · `kona` · `msmnile`.
 *
 * والكتالوج يسجّل له اسمًا واحدًا، والحقيقة أنّ الكود يغطي عائلة: `lahaina` تشمل `SM8350`
 * (Snapdragon 888) و`SM7325` (778G). فالاشتقاق منه **تخمين**، وهذا ما تمنعه هذه البوابة.
 */
internal fun isCodenameKey(key: String): Boolean = key.none { it.isDigit() }

/**
 * الاسم التجاري بلا لاحقته: `Dimensity 8350-Ultimate` ← `Dimensity 8350`.
 *
 * والغاية **العرض الجماعي** لا الطمس: حين تحمل شريحة واحدة `8300` و`8350-Ultimate` و`8350 Apex`
 * فالمفيد قول «8300 / 8350» بدل طابور لاحقات. واللاحقة لا تُنزل إلّا إذا بدأت بفاصل، فلا
 * يُمحى `+` من `Snapdragon 888+` (وتلك عائلة أخرى لها مفاتيحها).
 */
internal fun baseName(label: String): String {
    val lastNumber = Regex("\\d+").findAll(label).lastOrNull() ?: return label
    val tail = label.substring(lastNumber.range.last + 1)
    return if (tail.isNotEmpty() && tail.first() in " -/_") {
        label.substring(0, lastNumber.range.last + 1)
    } else {
        label
    }
}

/**
 * جمع المرشّحين في سطر واحد بعامل مشترك: `["… Snapdragon 8 Gen 3", "… Snapdragon 8 Gen 4"]`
 * ← `"… Snapdragon 8 Gen 3 / 4"`. والقاسم يأتي من الكلمات المشتركة، فلا يُعاد الاسم مرتين.
 */
internal fun joinCandidates(candidates: List<String>): String {
    if (candidates.size <= 1) return candidates.firstOrNull().orEmpty()
    val words = candidates.map { it.split(' ').filter { word -> word.isNotEmpty() } }
    val shortest = words.minOf { it.size }
    var common = 0
    while (common < shortest - 1 && words.all { it[common] == words.first()[common] }) common++
    val head = if (common == 0) "" else words.first().take(common).joinToString(" ") + " "
    return head + candidates.joinToString(" / ") { it.split(' ').filter { w -> w.isNotEmpty() }.drop(common).joinToString(" ") }
}

/**
 * بائع الشريحة من صفّ الكتالوج — مفتاح موحَّد لا صيغة عرض.
 *
 * و`getChipsetVendor` كان يستنتج البائع من **سطر العرض**، وهو ما ينهار حين يصير السطر مجموع
 * مرشّحين. فالاستنتاج صار من بيانات الصفّ، والفراغ يعني «لا أقول».
 */
internal fun vendorKeyOf(vendor: String): String {
    val value = vendor.lowercase()
    return when {
        "mediatek" in value -> "mediatek"
        "qualcomm" in value -> "qualcomm"
        else -> ""
    }
}

/** ترتيب ثابت للمرشّحين: برقم آخر الاسم، ثم بالنصّ — فلا يتغيّر العرض بين تشغيلين. */
private val BY_LAST_NUMBER_THEN_TEXT = Comparator<String> { first, second ->
    val a = lastNumberOf(first)
    val b = lastNumberOf(second)
    when {
        a != null && b != null && a != b -> a.compareTo(b)
        else -> first.compareTo(second)
    }
}

private fun lastNumberOf(text: String): Long? =
    Regex("\\d+").findAll(text).lastOrNull()?.value?.toLongOrNull()
