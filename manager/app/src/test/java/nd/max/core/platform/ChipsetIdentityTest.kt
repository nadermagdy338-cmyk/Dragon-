/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.platform

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * هوية الشريحة — مُختبَرة **على الكتالوج الحقيقي** لا على جهاز.
 *
 * **الحدّ المُعلن أولًا:** ما لا يُقاس هنا هو **القراءة**: `ro.board.platform` و`ro.soc.model`
 * و`/proc/cpuinfo` و`/sys/devices/soc0/machine` تُقرأ من هاتف حقيقي، فلا يُدّعى هنا أن أيّ جهاز
 * يُعلن هذه القيم. والمُقاس هو **ما يُفعل بما أعلنه الجهاز**: هل يُصيب الكتالوج أم يبقى مجهولًا
 * أم يُعلن شكّه؟ فالمدخلات هنا هي الأشكال الحقيقية لما تُخرجه تلك المصادر على أجهزة معروفة.
 *
 * **والعطوب المُغلَقة هنا، مقيسة لا محكيّة:**
 *
 * ١. **مفتاح بلا رقم اسم مشروع لا شريحة.** `lahaina` عائلة تشمل `SM8350` (‏888) و`SM7325`
 * (‏778G)، والكتالوج يسجّل لها اسمًا واحدًا — و`ro.board.platform` (وهو كود) كان يُسأل **قبل**
 * `soc0/machine` (وهو رقم القطعة)، فيُعرض **778G على جهاز 888**. ومثله `KONA` و`LITO`.
 * ٢. **المطابقة بالبادئة كانت تُؤلّف اسمًا.** الكتالوج لا يحمل مفتاح `MT6897`، بل
 * `MT6897Z/ZA → Dimensity 8300` و`MT6897Z_A/ZA → Dimensity 8350` … فجهاز يعلن `MT6897` كان
 * يفوز بمفتاح **بحساب الطول** فيُعرض له اسم واحد من اثنين: **١٠٨ من ٤١٦** عائلة رموز في هذا
 * الكتالوج تحمل أكثر من اسم تجاري، و**٢٧** منها رمزها الأساسي ليس مفتاحًا فلا يبقى له إلا
 * البادئة. والبوابة الأخيرة في هذا الملفّ تمشي على الـ**٢٧** كلها: **٢٤** منها تُعلن مرشّحيها
 * و**٣** رموزها أقصر من حدّ الاستنتاج فيُقال لها `Unknown (<الرمز>)` — فلا يعود أحدها صامتًا.
 * ٣. **رقم قطعة واحد باسمين تجاريين** — وهو عطب هذه الجولة (شكوى المالك: «بعض الأحيان يكتب
 * اسم معالج خاطئ لقربهم من نفس العائلة مثل ميدياتك 8300 و 8350»). والدليل **مقيس من شجرة
 * جهاز حقيقية لا مرويّ**: `device_xiaomi_duchamp` (‏**Redmi K70E / POCO X6 Pro**، وسوقه
 * **Dimensity 8300-Ultra**) يسجّل `ro.soc.manufacturer=Mediatek` و`ro.soc.model=MT6897Z_A/ZA`
 * — والكتالوج المشحون يسمّي هذا الرمز بعينه **Dimensity 8350** (وتُعلنه أجهزة 8350 كذلك)،
 * فيُعرض «8350» على جهاز 8300-Ultra. والقاعدة الآن: **رمز يحمله صفّان لا يُسمّى باسم واحد** —
 * يُعرض الاثنان، وفي الكتالوج أثرُ الحقيقة نفسها: صفّ `MT6897Z_A/ZA_OLD` ⟵ 8300-Ultra.
 *
 * **وما زال غير مُدّعى:** أنّ جهازًا بعينه يُعلن قيمة بعينها. ذاك «يحتاج جهازًا»، ولذلك صار كل
 * مصدر يُطبع بقيمته في تقرير الجهاز (`DeviceBlueprint`) فيُسمّى السبب في أول تقرير قادم.
 */
class ChipsetIdentityTest {

    // ── القصّ إلى أجزاء ──────────────────────────────────────────────

    @Test
    fun `a part number is read out of a composite value`() {
        // قالب آلة كوالكوم: الشركة والكلمات تُقصى ويبقى رقم القطعة.
        assertEquals(listOf("SM8150"), ChipsetMatcher.partsOf("Qualcomm Technologies, Inc SM8150"))
        // والشرطة والمائلة والسفلى **لا تفصل**: رقم القطعة الكامل هو الأدقّ.
        assertEquals(listOf("SM8350-AC"), ChipsetMatcher.partsOf("Qualcomm Technologies, Inc SM8350-AC"))
        assertEquals(listOf("MT6833V/ZA"), ChipsetMatcher.partsOf("Hardware : MT6833V/ZA"))
        assertEquals(listOf("MT6789V_CZA"), ChipsetMatcher.partsOf("MT6789V_CZA"))
        // شجرة الأجهزة: NUL مفصولة، فالمدخل الواحد يحمل أكثر من عنصر.
        assertEquals(listOf("gs201"), ChipsetMatcher.partsOf("google,gs201 google,gs201"))
    }

    @Test
    fun `a value with no part number yields nothing`() {
        // ولا واحد منها يُعلن شريحة: كلمتان بلا رقم، وأرقام محضة، وفراغ.
        listOf("qcom", "msmnile", "shiba", "kalama", "519", "", "Qualcomm Technologies, Inc").forEach { value ->
            assertEquals("«$value» ليست رقم قطعة", emptyList<String>(), ChipsetMatcher.partsOf(value))
        }
    }

    @Test
    fun `a codename is a key without a number, and a marketing name is not`() {
        // الفرق الذي يمنع عرض «778G» على جهاز «888»: مفتاح بلا رقم كودُ مشروع لا اسمُ شريحة.
        assertTrue(isCodenameKey("lahaina"))
        assertTrue(isCodenameKey("KONA"))
        assertTrue(isCodenameKey("msmnile"))
        assertFalse(isCodenameKey("SM8350"))
        assertFalse(isCodenameKey("Tensor G3"))
        assertFalse(isCodenameKey("8350"))
    }

    // ── أولوية المطابقة ───────────────────────────────────────────────

    @Test
    fun `an exact key wins over its own reduced variant`() {
        // هذا هو أثر «الشرطة لا تفصل» مقيسًا في أصغر صورة: `SM9999-AC` مفتاح قائم باسمه الخاص،
        // فلا يجوز أن يُختصر إلى `SM9999` ويُعرض اسم الأساس.
        val catalog = mapOf(
            "SM9999-AC" to SocEntry("Qualcomm", "Snapdragon 999 for Galaxy"),
            "SM9999" to SocEntry("Qualcomm", "Snapdragon 999"),
        )
        assertEquals("Qualcomm Snapdragon 999 for Galaxy", resolveChipsetName(catalog, listOf("SM9999-AC")))
        assertEquals("Qualcomm Snapdragon 999", resolveChipsetName(catalog, listOf("SM9999")))
    }

    @Test
    fun `a code with no exact key still resolves through its variants`() {
        // `MT1234V/CZA` ليس مفتاحًا، و`MT1234V` كذلك، فيبقى `MT1234` — وهو نفس سلوك
        // `extractChipVariants` قبل هذا التغيير، فلا يُدَّعى أنه جديد.
        val catalog = mapOf("MT1234" to SocEntry("MediaTek", "Dimensity 1234"))
        assertEquals("MediaTek Dimensity 1234", resolveChipsetName(catalog, listOf("MT1234V/CZA")))
    }

    // ── الصدق في المجهول (ADR-07) ─────────────────────────────────────

    @Test
    fun `what is not known stays unknown, with the code that was declared`() {
        val catalog = mapOf("MT1234" to SocEntry("MediaTek", "Dimensity 1234"))

        // لا رقم قطعة معلنًا: النصّ كما كان قبل التغيير حرفيًّا.
        assertEquals("Unknown (SoC)", resolveChipsetName(catalog, listOf("", "", "")))
        assertEquals("Unknown (qcom)", resolveChipsetName(catalog, listOf("qcom", "msmnile")))

        // والمجهول يُسمّى **برقم القطعة الذي أعلنه الجهاز** لا بجملة البرنامج التي تحمله،
        // ومعه لا يُخترع اسم: الرقم حقيقة، والاسم المُخترع كذبة.
        val unknown = resolveChipsetName(catalog, listOf("Qualcomm Technologies, Inc ZZZZ9999"))
        assertEquals("Unknown (ZZZZ9999)", unknown)
        assertTrue("لا يُخترع اسم من رقم غير معروف: $unknown", unknown.startsWith("Unknown ("))

        // ورقم `soc_id` محضٌ لا يُعلن شريحة: لا يُقرأ باسم.
        assertEquals("Unknown (519)", resolveChipsetName(catalog, listOf("", "", "519", "qcom")))
    }

    @Test
    fun `trademark marks never reach the screen`() {
        // الكتالوج يكتب `Qualcomm®` و`Snapdragon™`: علامات لا معنى لها في سطر هوية.
        val catalog = mapOf("SM9999" to SocEntry("Qualcomm®", "Snapdragon™ 999"))
        val name = resolveChipsetName(catalog, listOf("sm9999"))
        assertEquals("Qualcomm Snapdragon 999", name)
        assertFalse("علامة تجارية في «$name»", name.contains("®") || name.contains("™"))
    }

    // ── العطب الأول: كود المشروع لا يسبق رقم القطعة ───────────────────

    @Test
    fun `a platform codename never overrides the part number of the same device`() {
        val catalog = catalog()
        // ترتيب `declaredChipsetSources` الحقيقي: `ro.board.platform` (كود) قبل `soc0/machine` (رقم).
        val identity = resolveChipset(
            catalog,
            listOf(
                ChipsetSource("ro.board.platform", "lahaina", ChipsetEvidence.SYSTEM_PROPERTY),
                ChipsetSource("/sys/devices/soc0/machine", "Qualcomm Technologies, Inc SM8350", ChipsetEvidence.KERNEL_SYSFS),
            ),
        )
        assertEquals("Qualcomm Snapdragon 888", identity.display)
        assertEquals("SM8350", identity.partCode)
        assertFalse("عُرض 778G على جهاز 888: ${identity.display}", identity.display.contains("778"))
    }

    @Test
    fun `a codename alone names no chip`() {
        val catalog = catalog()
        // ولا رقم قطعة مع الكود: لا يُنطق اسم شريحة من اسم مشروع — بل الكود المعلن نفسه.
        val identity = resolveChipset(
            catalog,
            listOf(ChipsetSource("ro.board.platform", "lahaina", ChipsetEvidence.SYSTEM_PROPERTY)),
        )
        assertEquals("Unknown (lahaina)", identity.display)
        assertFalse("اسم مُخترع من كود مشروع: ${identity.display}", identity.display.contains("778"))
    }

    // ── العطب الثاني والثالث: الشكّ يُعلن ولا يُرجَّح ───────────────────

    @Test
    fun `a part number that carries two names is shown as both, and its exact variant as one`() {
        val catalog = catalog()

        // الرمز الأساسي `MT6897` ليس مفتاحًا: يحمل `Dimensity 8300` و`8350` معًا فيُعرضان معًا.
        val base = resolveChipset(
            catalog,
            listOf(ChipsetSource("ro.vendor.mediatek.platform", "MT6897", ChipsetEvidence.SYSTEM_PROPERTY)),
        )
        assertTrue("لم يُعلن الشكّ: ${base.display}", base.ambiguous)
        assertTrue("الشريحة تحمل 8300 و8350: ${base.display}", base.display.contains("8300") && base.display.contains("8350"))
        assertEquals(listOf("MediaTek Dimensity 8300", "MediaTek Dimensity 8350"), base.candidates)

        // وأما الرمز **اللاحقي** الذي يحمله صفّ واحد باسم واحد فيُقال بلا شكّ — وهو فرق
        // «الدليل» عن «الاستنتاج».
        assertEquals("MediaTek Dimensity 8300", resolveChipsetName(catalog, listOf("MT6897Z/ZA")))
        // و`MT6897Z_A/ZA` **مشترك** فلا يُنتقى منه اسم — وهذا عطب هذه الجولة، وبوّابته التالية.
    }

    /**
     * **عطب هذه الجولة، مقيسًا على ما أعلنه جهاز حقيقي:** `MT6897Z_A/ZA` هو `ro.soc.model` في
     * شجرة `device_xiaomi_duchamp` (‏Redmi K70E / POCO X6 Pro — **Dimensity 8300-Ultra**)، وتُعلنه
     * أجهزة **Dimensity 8350** كذلك؛ والكتالوج يسجّله باسم واحد (8350) فيُعرض 8350 على جهاز
     * 8300-Ultra. والقاعدة المُصلحة: **الرمز المشترك يُعرض باسمَيه ولا يُنتقى منه اسم**.
     */
    @Test
    fun `a part code two marketing names share is never resolved to one of them`() {
        val catalog = catalog()

        // (١) الشكل الحقيقي للجهاز: الاسم في `SOC_MODEL` ورقم القطعة نفسه معلنًا من الخصائص.
        val shared = resolveChipset(
            catalog,
            listOf(
                ChipsetSource("Build.SOC_MODEL", "MT6897Z_A/ZA", ChipsetEvidence.SYSTEM_PROPERTY, declaresName = true),
                ChipsetSource("ro.board.platform", "mt6897", ChipsetEvidence.SYSTEM_PROPERTY),
            ),
        )
        assertEquals("رمز مشترك عُرض بحالة اسم واحد", ChipsetMatch.AMBIGUOUS, shared.match)
        assertTrue("الرمز المشترك عُرض باسم واحد: ${shared.display}", shared.display.contains("8300") && shared.display.contains("8350"))
        assertTrue("لا مرشّحين مُعلَنين: ${shared.candidates}", shared.candidates.size >= 2)

        // (٢) وبوّابة على الكتالوج كلّه: كل رمز يحمله صفّان يحملان **اسمين** يُعلن مرشّحيه،
        // وصاحبُه يجد اسمه بينهم — فلا يُنطق اسم واحد لرمز بيعه مصنّعان باسمين.
        val leaks = mutableListOf<String>()
        var sharedCodes = 0
        catalog.forEach { (key, row) ->
            if (ChipsetMatcher.partsOf(key).isEmpty() || row.label.isEmpty()) return@forEach
            val identity = resolveChipset(
                catalog,
                listOf(ChipsetSource("/sys/devices/soc0/machine", key, ChipsetEvidence.KERNEL_SYSFS)),
            )
            if (identity.candidates.size < 2) return@forEach
            sharedCodes++
            if (identity.match != ChipsetMatch.AMBIGUOUS) leaks += "$key → ${identity.display} (حالته ${identity.match})"
            if (row.label !in identity.candidates) leaks += "$key: صفّه «${row.label}» ليس بين المرشّحين (${identity.display})"
        }
        assertTrue("رموز مشتركة عُرضت باسم واحد: $leaks", leaks.isEmpty())
        assertTrue("لا رمز مشترك في هذا الكتالوج؟ فالبوّابة لا تقيس شيئًا", sharedCodes >= 1)
    }

    /**
     * وبوّابة بيانات: **لا اسم لشريحة لا وجود لها**. وهي التي أمسكت العطب الفعلي: الصفّ
     * `MT8792Z/CA` كان اسمه `Dimensity 83000` — خمس خانات، ولا شريحة بهذا الاسم (الصواب
     * `Dimensity 8300`، وهو ما تسجّله قوائم MediaTek لِـ`MT8792Z/CA`).
     */
    @Test
    fun `no catalog row carries a name with a five digit marketing number`() {
        val catalog = catalog()
        val typos = catalog.filterValues { row -> Regex("\\d{5,}").containsMatchIn(row.name) }
            .map { (key, row) -> "$key → ${row.name}" }
        assertTrue("أسماء بأرقام من خمس خانات: $typos", typos.isEmpty())
    }

    @Test
    fun `every ambiguous base code in the catalog discloses its candidates`() {
        val catalog = catalog()
        val keys = catalog.keys.toList()
        val keySet = keys.map(::normalizeSocCode).toSet()

        // عائلات الرموز: الرمز الأساسي ← الأسماء التجارية التي تحملها مفاتيحه.
        val families = mutableMapOf<String, MutableSet<String>>()
        keys.forEach { key ->
            val base = baseCodeOf(key) ?: return@forEach
            families.getOrPut(normalizeSocCode(base)) { mutableSetOf() } += baseName(catalog.getValue(key).label)
        }

        var ambiguous = 0
        var disclosed = 0
        val failures = mutableListOf<String>()
        families.forEach { (base, names) ->
            // إذا كان الرمز الأساسي نفسه مفتاحًا فله صفّه الخاص: يُقال اسمه بلا استنتاج.
            if (names.size < 2 || base in keySet) return@forEach
            ambiguous++
            val identity = resolveChipset(
                catalog,
                listOf(ChipsetSource("ro.board.platform", base, ChipsetEvidence.SYSTEM_PROPERTY)),
            )
            // والعقد: **إمّا مرشّحون معلَنون وإمّا مجهول** — ولا اسم واحد يُنطق بلا دليل.
            // والمجهول هنا صادق لا هروبًا: رمز أقصر من حدّ الاستنتاج لا يُستنتج منه أصلًا
            // (تطابقه المصادفة أثقل من فائدته)، فيُقال `Unknown (<الرمز>)`.
            if (identity.ambiguous) disclosed++
            if (!identity.ambiguous && !identity.unknown) {
                failures += "$base → ${identity.display} (يحمل: ${names.joinToString()})"
            }
        }

        // المقيس اليوم على الكتالوج المُسلَّم: ٢٧ رمزًا أساسيًّا غامضًا، منها **٢٤** يُعلن
        // مرشّحيه و**٣** رموزها أقصر من حدّ الاستنتاج (`qsd8` · `s5` · `sun8`) فيُقال لها
        // `Unknown (<الرمز>)` — وهو الصدق نفسه لا هروب. والعتبة الدنيا تمنع أن تصير البوابة
        // فارغة بلا أن تنكسر بأول تحرير للكتالوج.
        assertTrue("تقلّص عدد الرموز الغامضة المقيسة: $ambiguous", ambiguous >= 20)
        assertTrue("تقلّص عدد المرشّحين المعلَنين: $disclosed", disclosed >= 20)
        assertTrue("رموز غامضة عُرضت باسم واحد: $failures", failures.isEmpty())
    }

    // ── قول المصنّع ──────────────────────────────────────────────────

    @Test
    fun `the name the system declares is preferred over what the catalog would infer`() {
        val catalog = catalog()

        // `SOC_MODEL` يحمل اسمًا كتبه المصنّع ولا يحمل رقم قطعة ⇒ قوله هو، والشكّ يزول به.
        val declared = resolveChipset(
            catalog,
            listOf(
                ChipsetSource("Build.SOC_MODEL", "Dimensity 8350", ChipsetEvidence.SYSTEM_PROPERTY, declaresName = true),
                ChipsetSource("ro.board.platform", "mt6897", ChipsetEvidence.SYSTEM_PROPERTY),
            ),
        )
        assertEquals("MediaTek Dimensity 8350", declared.display)
        assertEquals("Dimensity 8350", declared.declaredName)
        assertEquals("mt6897", declared.partCode)
        assertFalse("الشكّ زال بقول المصنّع", declared.ambiguous)

        // واسم لا يعرفه الكتالوج يُقال كما هو: لا «مجهول»، ولا تصيُّد من الرمز.
        val unknownName = resolveChipset(
            catalog,
            listOf(ChipsetSource("Build.SOC_MODEL", "Dimensity 9999", ChipsetEvidence.SYSTEM_PROPERTY, declaresName = true)),
        )
        assertEquals("Dimensity 9999", unknownName.display)
    }

    @Test
    fun `a declared name without a number is not a name`() {
        // و«وحدة بلا رقم» ليست اسم شريحة: `qualcomm` في `SOC_MODEL` لا تصير سطر هوية.
        val catalog = catalog()
        val identity = resolveChipset(
            catalog,
            listOf(ChipsetSource("Build.SOC_MODEL", "qualcomm", ChipsetEvidence.SYSTEM_PROPERTY, declaresName = true)),
        )
        assertEquals("Unknown (qualcomm)", identity.display)
    }

    // ── تعارض المصادر: يُعلَن ولا يُرجَّح (طلب المالك: «مطابق الأدلة… بدل اختيار أول نتيجة») ──

    @Test
    fun `sources that agree are exact, and the state says so`() {
        val catalog = catalog()
        // قول المصنّع ورقم القطعة يشيران إلى الشريحة نفسها ⇒ حالة واحدة بلا شكّ.
        val identity = resolveChipset(
            catalog,
            listOf(
                ChipsetSource("Build.SOC_MODEL", "Dimensity 8350", ChipsetEvidence.SYSTEM_PROPERTY, declaresName = true),
                ChipsetSource("/sys/devices/soc0/machine", "MediaTek MT6897Z_A/ZA", ChipsetEvidence.KERNEL_SYSFS),
            ),
        )
        assertEquals(ChipsetMatch.EXACT, identity.match)
        assertEquals("MediaTek Dimensity 8350", identity.display)
        assertEquals("Dimensity 8350", identity.declaredName)
        assertFalse("تأكيدان متّفقان لا يصنعان شكًّا: ${identity.display}", identity.ambiguous)
        assertTrue("التأكيد ليس استنتاجًا بالبادئة", !identity.inferred)
    }

    @Test
    fun `two sources naming different chips are both disclosed, never one of them`() {
        val catalog = catalog()
        // `8300` في `SOC_MODEL` و`MT6897Z_A/ZA` في النواة: دليلان مستقلّان لا يتقاطعان
        // (الرمز **مشترك**: 8300-Ultra و8350). وهذا هو الفرض الذي يُنتج «8350 على جهاز 8300»
        // إن رُجّح أحدهما — فلا يُرجَّح أحدهما.
        val identity = resolveChipset(
            catalog,
            listOf(
                ChipsetSource("Build.SOC_MODEL", "Dimensity 8300", ChipsetEvidence.SYSTEM_PROPERTY, declaresName = true),
                ChipsetSource("/sys/devices/soc0/machine", "MediaTek MT6897Z_A/ZA", ChipsetEvidence.KERNEL_SYSFS),
            ),
        )
        assertEquals(ChipsetMatch.AMBIGUOUS, identity.match)
        assertTrue("لم يُعلن التعارض: ${identity.display}", identity.display.contains("8300"))
        assertTrue("لم يُعلن التعارض: ${identity.display}", identity.display.contains("8350"))
        // والاتّحاد أوسع من الاسمين: الدليل الثاني أعلن الرمز المشترك بمرشّحيه، فما أجازه الدليلان
        // معًا = ثلالثة أسماء — يُعرض كلّها ولا يُنتقى منها اسم واحد.
        assertEquals(
            listOf("MediaTek Dimensity 8300", "MediaTek Dimensity 8300-Ultra", "MediaTek Dimensity 8350"),
            identity.candidates,
        )
    }

    /**
     * والوجه الآخر للقاعدة نفسها: **الاسم الذي أعلنه المصنّع يُضيّق الرمز المشترك**، لأن التقاطع
     * (لا الاتّحاد) هو الجواب حين تتفق التأكيدات — والرمز المشترك يجيز الاسمين، فأحدهما فقط يُصدّقه.
     */
    @Test
    fun `a declared name narrows a shared part code to itself`() {
        val catalog = catalog()
        val identity = resolveChipset(
            catalog,
            listOf(
                ChipsetSource("Build.SOC_MODEL", "Dimensity 8350", ChipsetEvidence.SYSTEM_PROPERTY, declaresName = true),
                ChipsetSource("/sys/devices/soc0/machine", "MediaTek MT6897Z_A/ZA", ChipsetEvidence.KERNEL_SYSFS),
            ),
        )
        assertEquals(ChipsetMatch.EXACT, identity.match)
        assertEquals("MediaTek Dimensity 8350", identity.display)
        assertTrue("الشكّ بقيت على رمز مشترك ضيّقه الاسم المُعلَن: ${identity.display}", !identity.ambiguous)
    }

    @Test
    fun `a corroborating source never names a chip`() {
        val catalog = catalog()
        // GPU وبائع: تقوية لا تسمية — وحدهما لا يُنتجان اسمًا أصلًا.
        val identity = resolveChipset(
            catalog,
            listOf(
                ChipsetSource("ro.hardware.egl", "adreno", ChipsetEvidence.SYSTEM_PROPERTY, corroborates = true),
                ChipsetSource("/sys/class/kgsl/kgsl-3d0/gpu_model", "Adreno (TM) 740", ChipsetEvidence.GPU_INFO, corroborates = true),
            ),
        )
        assertEquals(ChipsetMatch.UNKNOWN, identity.match)
        assertEquals("Unknown (SoC)", identity.display)
    }

    @Test
    fun `an unknown part number is not overridden by a project codename`() {
        val catalog = catalog()
        // الرمز الأوّل لا يعرفه الكتالوج، والثاني كود مشروع لا يسمّي شريحة: يبقى الرمز.
        val identity = resolveChipset(
            catalog,
            listOf(
                ChipsetSource("/sys/devices/soc0/machine", "Qualcomm Technologies, Inc ZZZZ9999", ChipsetEvidence.KERNEL_SYSFS),
                ChipsetSource("ro.board.platform", "lahaina", ChipsetEvidence.SYSTEM_PROPERTY),
            ),
        )
        assertEquals(ChipsetMatch.UNKNOWN, identity.match)
        assertEquals("Unknown (ZZZZ9999)", identity.display)
    }

    @Test
    fun `the three states never lie about what the catalog carries`() {
        val catalog = catalog()
        catalog.keys.forEach { code ->
            if (ChipsetMatcher.partsOf(code).isEmpty()) return@forEach
            val identity = resolveChipset(catalog, listOf(ChipsetSource("soc", code, ChipsetEvidence.SYSTEM_PROPERTY)))
            when (identity.match) {
                // EXACT = اسم واحد ولا مرشّحين. فإن ظهر مرشّح فهو شكّ لم يُعلَن في حالته.
                ChipsetMatch.EXACT -> assertTrue(
                    "EXACT بمرشّحين ($code): ${identity.display}",
                    !identity.ambiguous && identity.candidates.isEmpty(),
                )
                ChipsetMatch.AMBIGUOUS -> assertTrue(
                    "AMBIGUOUS بلا مرشّحين ($code): ${identity.display}",
                    identity.candidates.size > 1,
                )
                ChipsetMatch.UNKNOWN -> assertTrue(
                    "UNKNOWN بلا رمز ($code): ${identity.display}",
                    identity.display.startsWith(UNKNOWN_LABEL_PREFIX),
                )
            }
        }
    }

    @Test
    fun `the vendor is read from the catalog row, not from the display line`() {
        assertEquals("mediatek", vendorKeyOf("MediaTek"))
        assertEquals("qualcomm", vendorKeyOf("Qualcomm®"))
        assertTrue(vendorKeyOf("Unisoc").isEmpty())
        // وحتى حين يصير العرض مجموع مرشّحين (`MediaTek Dimensity 8300 / 8350`) يبقى البائع معروفًا.
        val identity = resolveChipset(
            catalog(),
            listOf(ChipsetSource("ro.board.platform", "mt6897", ChipsetEvidence.SYSTEM_PROPERTY)),
        )
        assertEquals(listOf("MediaTek"), identity.vendors)
    }

    // ── العرض الجماعي ────────────────────────────────────────────────

    @Test
    fun `candidates are joined without repeating their common words`() {
        assertEquals("", joinCandidates(emptyList()))
        assertEquals("Qualcomm Snapdragon 888", joinCandidates(listOf("Qualcomm Snapdragon 888")))
        assertEquals(
            "Qualcomm Snapdragon 8 Gen 3 / 4",
            joinCandidates(listOf("Qualcomm Snapdragon 8 Gen 3", "Qualcomm Snapdragon 8 Gen 4")),
        )
        // وبلا قاسم: يُكتب كل اسم كاملًا ولا يُقصّ.
        assertEquals("MT8168A / MT8168B", joinCandidates(listOf("MT8168A", "MT8168B")))
    }

    @Test
    fun `a variant suffix folds into its base name, and a plus mark does not`() {
        // اللاحقة تُطوى لجمع المرشّحين: `8350-Ultimate` و`8350 Apex` اسم واحد للعرض.
        assertEquals("MediaTek Dimensity 8350", baseName("MediaTek Dimensity 8350-Ultimate"))
        assertEquals("MediaTek Dimensity 8350", baseName("MediaTek Dimensity 8350 Apex"))
        assertEquals("MediaTek Dimensity 8300", baseName("MediaTek Dimensity 8300-Ultra"))
        // وأما `+` فليست لاحقة تُطوى: `888+` شريحة أخرى لها مفاتيحها.
        assertEquals("Qualcomm Snapdragon 888+", baseName("Qualcomm Snapdragon 888+"))
        assertEquals("Qualcomm Snapdragon 8 Gen 3", baseName("Qualcomm Snapdragon 8 Gen 3"))
    }

    // ── مفتاح تامّ = دليل · بادئة = استنتاج (والعطب المقيس: `qualcomm`) ──

    /**
     * العطب كما وقع: جهاز أعلن `qualcomm` وحدها (كلمة بائع في `SOC_MODEL`) عُرض له
     * **«Qualcomm Snapdragon 450»** — لأن الكتالوج يحمل المفتاح `Qualcomm Technologies, Inc 450`،
     * والبندقة `qualcomm` تُطابقه بالبادئة. والمقيس الآن: البادئة لا تُسأل إلّا عن **رقم قطعة**
     * (حرف ورقم)، والمفتاح التامّ يُقبل — فالمعنى الذي يحمل الأدقّ.
     */
    @Test
    fun `a bare vendor word never names a chip, while the exact key it prefixes does`() {
        val catalog = catalog()

        // (١) صيغة الآلة نفسها — **مفتاح مشحون** في الكتالوج: دليل تامّ يُقبل بلا تخمين.
        val shipped = resolveChipset(
            catalog,
            listOf(ChipsetSource("/sys/devices/soc0/machine", "Qualcomm Technologies, Inc 450", ChipsetEvidence.KERNEL_SYSFS)),
        )
        assertEquals(ChipsetMatch.EXACT, shipped.match)
        assertEquals(catalog.getValue("Qualcomm Technologies, Inc 450").label, shipped.display)
        assertFalse("المفتاح التامّ ليس استنتاجًا", shipped.inferred)

        // (٢) وبادئتها وحدها (`qualcomm`) لا تُنتج اسمًا — ولو كانت بادئة ذلك المفتاح بعينه.
        val word = resolveChipset(
            catalog,
            listOf(ChipsetSource("Build.SOC_MODEL", "qualcomm", ChipsetEvidence.SYSTEM_PROPERTY, declaresName = true)),
        )
        assertEquals(ChipsetMatch.UNKNOWN, word.match)
        assertEquals("Unknown (qualcomm)", word.display)
    }

    @Test
    fun `a numeric id names only when the catalog carries it as a key`() {
        val catalog = catalog()

        // أرقام `soc_id` المجرّدة **مفاتيح** في هذا الكتالوج (`8626` ← Snapdragon 400)، والجهاز
        // يعلنها حرفيًّا — فتُقبل بالتماثل.
        val numericKey = catalog.entries.first { (key, row) -> key.all(Char::isDigit) && row.label.isNotEmpty() }
        val named = resolveChipset(
            catalog,
            listOf(ChipsetSource("/sys/devices/soc0/soc_id", numericKey.key, ChipsetEvidence.KERNEL_SYSFS)),
        )
        assertEquals(numericKey.value.label, named.display)

        // ورقم **ليس** مفتاحًا لا يُطابق بالبادئة: رقم مُجرّد ليس رقم قطعة، فتطابقه مصادفة.
        val absent = generateSequence(numericKey.key.toLong() + 1) { it + 1 }
            .map(Long::toString)
            .first { it !in catalog }
        val missing = resolveChipset(
            catalog,
            listOf(ChipsetSource("/sys/devices/soc0/soc_id", absent, ChipsetEvidence.KERNEL_SYSFS)),
        )
        assertEquals(ChipsetMatch.UNKNOWN, missing.match)
        assertEquals("Unknown ($absent)", missing.display)
    }

    /**
     * بوابة على **الكتالوج كلّه**: كل صيغة آلة يحملها مفتاحًا (فيها فراغ ورقم) تُحلّ إلى صفّها
     * نفسه. والمقيس على المشحون: **٨٤ من ٨٤**. والمستثنى صيغ بلا رقم (‏`… KONA` · `SAMSUNG
     * SERRANO`) — وهي أكواد مشاريع تشمل عائلة، فلا يُطالب لها باسم واحد.
     */
    @Test
    fun `every machine string the catalog ships as a key resolves to its own row`() {
        val catalog = catalog()
        var total = 0
        val misses = mutableListOf<String>()
        catalog.forEach { (key, row) ->
            if (' ' !in key || key.none { it.isDigit() } || row.label.isEmpty()) return@forEach
            total++
            val resolved = resolveChipset(
                catalog,
                listOf(ChipsetSource("/sys/devices/soc0/machine", key, ChipsetEvidence.KERNEL_SYSFS)),
            )
            if (resolved.display != row.label) misses += "«$key» → ${resolved.display} (الكتالوج: ${row.label})"
        }
        assertTrue("صيغ آلة لم تُحلّ إلى صفّها: $misses", misses.isEmpty())
        assertTrue("لا صيغ آلة في الكتالوج؟ فالبوابة لا تقيس شيئًا", total >= 50)
    }

    /**
     * و**البوابة التي تُغلق العطب المسرود عند المالك**: لا قيمة بلا رقم قطعة تُسمّى بالبادئة.
     *
     * والمقيس على المشحون: **٣٧** قيمة بلا رقم (وهي أطول من حدّ الاستنتاج، فهي مادّة مطابقة
     * فعلية)، و**صفر** منها أنتج اسمًا. وقبل الإصلاح كان `qualcomm` وحده كافيًا لإنتاج
     * «Qualcomm Snapdragon 450».
     */
    @Test
    fun `no value without a part code is ever prefix-matched into a name`() {
        val catalog = catalog()
        val candidates = catalog.keys.filter { it.none(Char::isDigit) && it.length >= MIN_INFERRED_CODE_LEN }.distinct()
        val leaks = mutableListOf<String>()
        candidates.forEach { word ->
            val resolved = resolveChipset(
                catalog,
                listOf(ChipsetSource("ro.board.platform", word, ChipsetEvidence.SYSTEM_PROPERTY)),
            )
            if (!resolved.unknown) leaks += "«$word» → ${resolved.display}"
        }
        assertTrue("قيم بلا رقم قطعة أنتجت أسماء: $leaks", leaks.isEmpty())
        assertTrue("لا قيم مجرّبة؟ فالبوابة لا تقيس شيئًا", candidates.size >= 20)
    }

    // ── العقد على الكتالوج الحقيقي ───────────────────────────────────

    @Test
    fun `real device shapes resolve against the real catalog`() {
        val catalog = catalog()
        DEVICES.forEach { (device, declared, expected) ->
            val resolved = resolveChipsetName(catalog, declared)
            if (expected != null) {
                assertEquals(device, expected, resolved)
            } else {
                assertFalse("$device لم يُعرَف: $resolved", resolved.startsWith("Unknown"))
            }
        }
    }

    @Test
    fun `no resolved name carries a trademark mark`() {
        val catalog = catalog()
        DEVICES.forEach { (device, declared, _) ->
            val resolved = resolveChipsetName(catalog, declared)
            assertFalse(
                "$device: «$resolved» يحمل علامة تجارية",
                resolved.contains("®") || resolved.contains("™") || resolved.contains("©"),
            )
        }
    }

    @Test
    fun `a code inside a machine string resolves back to its own name`() {
        val catalog = catalog()
        var total = 0
        var ownName = 0
        val misses = mutableListOf<String>()
        // والرمز **المشترك** يُستثنى بقصد: اسمه الواحد في الكتالوج هو الشكّ نفسه، فمطالبته باسم
        // واحد تناقض القاعدة الحيّة — وبوّابة الاشتراك أعلاه هي التي تحكمه لا هذه.
        val shared = catalog.keys.filter { code ->
            ChipsetMatcher.partsOf(code).isNotEmpty() &&
                resolveChipset(
                    catalog,
                    listOf(ChipsetSource("soc", code, ChipsetEvidence.SYSTEM_PROPERTY)),
                ).candidates.size > 1
        }.toSet()
        catalog.keys.forEach { code ->
            // أرقام القطع وحدها هي المقصودة: مفتاح بلا رقم (`Tensor` · `lahaina` · `KONA`) لا
            // يُعلنه جهاز في جملة، فلا يُحاسب عليه هذا المقياس.
            if (ChipsetMatcher.partsOf(code).isEmpty() || code in shared) return@forEach
            total++
            val resolved = resolveChipsetName(catalog, machineDeclaring(code))
            if (resolved == catalog.getValue(code).label) {
                ownName++
            } else if (misses.size < 8) {
                misses += "$code → $resolved (الكتالوج: ${catalog.getValue(code).label})"
            }
        }
        // المقيس على الكتالوج المُسلَّم **بعد استثناء الرموز المشتركة**: ٨٧٤ من ٨٨٤ (٩٨٫٩٪).
        // والعتبة ٩٥٪ تُبقي البوابة حقيقية بلا أن تنكسر بأول تحرير للكتالوج، وتُسقط العودة إلى
        // القصّ الواسع (٧٩٫٩٪).
        assertTrue(
            "عيّنات فاشلة: $misses",
            ownName.toLong() * 100 >= total.toLong() * 95,
        )
    }

    // ── المصادر ──────────────────────────────────────────────────────

    /** قالب آلة كوالكوم على API < 31 — الشكل الذي كان يُنتج `Unknown (msmnile)`. */
    private fun machineDeclaring(code: String) = listOf(
        "", "", "codename", "Qualcomm Technologies, Inc $code", "qcom", "", "qcom", "codename", "",
    )

    /** الرمز الأساسي لمفتاح: `MT6897Z_A/ZA` ← `MT6897`. */
    private fun baseCodeOf(key: String): String? =
        Regex("^([A-Za-z]+\\d+)").find(key)?.groupValues?.get(1)

    private fun catalog(): Map<String, SocEntry> = readSocCatalog(catalogFile().readText())

    /**
     * ملف الكتالوج المُسلَّم نفسه — لا نسخة منه. القراءة بالملف لأن `assets/` ليست على مسار
     * الصفوف (classpath)، والصعود في الشجرة يجعل الاختبار يعمل من أي مجلد تشغيل.
     */
    private fun catalogFile(): File {
        val relatives = listOf("app/src/main/assets/socs.json", "src/main/assets/socs.json")
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            relatives.forEach { relative ->
                val candidate = File(directory, relative)
                if (candidate.isFile) return candidate
            }
            directory = directory?.parentFile
        }
        throw AssertionError("لم يُوجد socs.json من مجلد التشغيل ${File("").absolutePath}")
    }

    private companion object {

        /**
         * أشكال حقيقية لما تُعلنه الأجهزة، بترتيب `declaredChipsetSources` نفسه على وجه التقريب:
         * `SOC_MODEL` · `chipname` · `board.platform` · `soc0/machine` · `family` · `soc_id` ·
         * `HARDWARE` · `BOARD` · `mediatek.platform` · سطر `cpuinfo` · `compatible`.
         *
         * والخانة الثالثة هي الاسم المتوقّع، أو `null` إن كان المطلوب **فقط** ألّا يبقى مجهولًا
         * (لئلا يصير الاختبار عقدًا على اسم لم أراجعه بنفسي).
         */
        val DEVICES = listOf(
            // صنف كوالكوم على API ≥ 31: `SOC_MODEL` هو رقم القطعة.
            Triple(
                "Galaxy S23 (SM8550)",
                listOf("SM8550", "", "kalama", "", "", "", "kalama", "kalama", ""),
                "Qualcomm Snapdragon 8 Gen 2",
            ),
            // وفارق اللاحقة محفوظ: `-AC` مفتاح قائم باسمه.
            Triple(
                "Galaxy-variant (SM8650-AC)",
                listOf("SM8650-AC", "", "pineapple", "Qualcomm Technologies, Inc SM8650-AC", "qcom", "", "qcom", "pineapple", ""),
                "Qualcomm Snapdragon 8 Gen 3 for Galaxy",
            ),
            // وصنف كوالكوم على API < 31: لا `SOC_MODEL`، والرقم داخل جملة `soc0/machine`.
            Triple(
                "Qualcomm API<31 (machine declares SM8150)",
                listOf("", "", "msmnile", "Qualcomm Technologies, Inc SM8150", "qcom", "", "qcom", "msmnile", ""),
                "Qualcomm Snapdragon 855",
            ),
            Triple(
                "Qualcomm (machine declares SDM845)",
                listOf("", "", "sdm845", "Qualcomm Technologies, Inc SDM845", "qcom", "", "qcom", "sdm845", ""),
                "Qualcomm Snapdragon 845",
            ),
            // MediaTek: `board.platform` رقم قطعة لا اسم مشروع، والـSOC_MODEL مركّب.
            Triple(
                "Redmi Note 12 (MT6789)",
                listOf("MT6789", "", "mt6789", "mt6789", "", "", "mt6789", "mt6789", ""),
                "MediaTek Helio G99",
            ),
            Triple(
                "MediaTek composite SOC_MODEL",
                listOf("MT6789V/ZA", "", "mt6789", "MT6789V/CZA", "", "", "mt6789", "mt6789", ""),
                "MediaTek Helio G99",
            ),
            Triple(
                "MediaTek legacy (MT6768)",
                listOf("", "", "mt6768", "MT6768", "mt6768", "", "mt6768", "mt6768", ""),
                "MediaTek Helio P65",
            ),
            // ومصادر لا تُقرأ اليوم أصلًا: سطر `cpuinfo` وحده، ثم `compatible` وحده.
            Triple(
                "Snapdragon 680 (cpuinfo only)",
                listOf("", "", "", "", "", "", "", "", "", "", "", "Hardware : Qualcomm Technologies, Inc SM6225", ""),
                "Qualcomm Snapdragon 680",
            ),
            Triple(
                "Dimensity 700 (cpuinfo only)",
                listOf("", "", "", "", "", "", "", "", "", "", "", "Hardware : MT6833V/ZA", ""),
                "MediaTek Dimensity 700",
            ),
            Triple(
                "Tensor G2 (device tree only)",
                listOf("", "", "", "", "", "", "", "", "", "", "", "", "google,gs201 google,gs201"),
                "Google Tensor G2",
            ),
            // وبقية العائلات: كلها بلا خانة متوقّعة إلا ألّا تبقى مجهولة.
            Triple("Pixel 8 (Tensor G3)", listOf("Tensor G3", "", "gs301", "gs301", "", "", "gs301", "shiba", ""), null),
            Triple("Pixel 7 (Tensor G2)", listOf("Tensor G2", "", "gs201", "gs201", "", "", "gs201", "panther", ""), null),
            Triple("Exynos 2100", listOf("Exynos 2100", "", "exynos2100", "exynos2100", "exynos2100", "", "exynos2100", "exynos2100", ""), null),
            // و`hi3660` صُحّح إلى `hi3670`: الأولى رقم قطعة **Kirin 960** في الكتالوج نفسه،
            // فكان الصفّ يُعلن شريحتين معًا؛ وطبقة التأكيدات كشفت ذلك فأعلنته تعارضًا لا تُرجّحه.
            Triple("Kirin 970", listOf("", "", "", "Kirin970", "", "", "hi3670", "hi3670", ""), "HiSilicon Kirin 970"),
            // ورقم قطعة **مشترك** — وهذا الشكل مقيس من شجرة `device_xiaomi_duchamp`
            // (‏Redmi K70E / POCO X6 Pro) بحرفه: `ro.soc.model=MT6897Z_A/ZA`. والمتوقّع هنا
            // **اسمان** لا اسم، لأن جهاز 8300-Ultra وجهاز 8350 يعلنان الرمز نفسه وليس في
            // الجهاز ما يفرّق بينهما.
            Triple(
                "Redmi K70E / POCO X6 Pro (8300-Ultra declares MT6897Z_A/ZA)",
                listOf("MT6897Z_A/ZA", "", "mt6897", "mt6897", "", "", "mt6897", "mt6897", ""),
                "MediaTek Dimensity 8300-Ultra / 8350",
            ),
        )
    }
}
