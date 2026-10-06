/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.platform

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * سطر الشريحة في الشاشة الرئيسية: **اسم المعالج ثم رمز قطعته بين قوسين** — طلب المالك
 * («أضف بين الأقواس رمز المعالج مثل MT6899 بجانب اسم المعالج»).
 *
 * ولماذا رمز القطعة أصلًا: اسم العرض قد يكون **مجموعة مرشّحين** حين يحمل رمز واحد أكثر من اسم
 * تجاري (عطب مقيس: `MT6899` يحمله في الكتالوج المشحون **أربعة** أسماء: Dimensity 8500 ·
 * 8500-Ultra · 8400 Ultimate · 8550 SUPER). فالرمز يقرأه المستخدم ويحكم بنفسه، والتطبيق لا
 * يرجّح مرشّحًا بلا مرجّح (ADR-07).
 *
 * والقياس هنا على التركيب وحده — **لا ادّعاء قراءة**: من أين يأتي الرمز على جهاز بعينه شأن
 * `declaredChipsetSources()` ويحتاج جهازًا، وهو مُعلن بهذه الحدود في ملفّ الهوية نفسه.
 */
class ChipsetPartCodeLineTest {

    @Test
    fun `the part code is appended in parentheses next to the name`() {
        assertEquals(
            "MediaTek Dimensity 8500 (MT6899)",
            chipsetLineWithPartCode("MediaTek Dimensity 8500", "MT6899"),
        )
    }

    @Test
    fun `a candidate list keeps its code so the reader can pick`() {
        assertEquals(
            "MediaTek Dimensity 8500 / 8500-Ultra / 8400 Ultimate (MT6899)",
            chipsetLineWithPartCode("MediaTek Dimensity 8500 / 8500-Ultra / 8400 Ultimate", "MT6899"),
        )
    }

    @Test
    fun `a missing code never leaves an empty pair of parentheses`() {
        assertEquals("MediaTek Dimensity 8500", chipsetLineWithPartCode("MediaTek Dimensity 8500", null))
        assertEquals("MediaTek Dimensity 8500", chipsetLineWithPartCode("MediaTek Dimensity 8500", "   "))
    }

    @Test
    fun `the unknown placeholder gives way to the announced code`() {
        // `ChipsetResolver.unknown` يبني «Unknown (SoC)» بكلمة نائبة لا برمز، والقوس هناك محجوز
        // للرمز — فإلحاق الرمز بقوس ثانٍ كان سيُقرأ «Unknown (SoC) (MT6899)».
        assertEquals(
            "Unknown (MT6899)",
            chipsetLineWithPartCode("Unknown (SoC)", "MT6899"),
        )
    }

    @Test
    fun `a name that already carries its code is not duplicated`() {
        // الشرط بلا حساسية لحالة الحروف: اسم فيه الرمز لا يُلحق به مرة ثانية — والقاعدة نفسها
        // تحمي من `Unknown (MT6899) (MT6899)` حين يأتي الرمز من العرض ومن المصادر معًا.
        assertEquals("MT6899", chipsetLineWithPartCode("MT6899", "MT6899"))
        assertEquals("MediaTek MT6899", chipsetLineWithPartCode("MediaTek MT6899", "MT6899"))
        assertEquals("mt6899", chipsetLineWithPartCode("mt6899", "MT6899"))
    }

    @Test
    fun `whitespace never produces a broken line`() {
        assertEquals("Dimensity 8500", chipsetLineWithPartCode("  Dimensity 8500  ", "  "))
        assertEquals("MT6899", chipsetLineWithPartCode("", " MT6899 "))
    }

    @Test
    fun `the identity for the owner's declared shape shows the code it announced`() {
        // الرمز معنون من المصادر المُعلنة لا مُخترع: هذا شكل الحالة المجهولة (بلا اسم في الكتالوج)
        // حيث `partCode` فارغ والرمز في المصادر — فلا يُعرض «Unknown» بلا رقم.
        val identity = ChipsetIdentity(
            display = "Unknown (SoC)",
            sources = listOf(
                ChipsetSource("soc0/machine", "MT6899", ChipsetEvidence.DEVICE_TREE),
            ),
        )
        assertEquals("MT6899", identity.displayPartCode)
        assertEquals("Unknown (MT6899)", identity.displayWithPartCode)
    }

    @Test
    fun `a known part code wins over the sources`() {
        val identity = ChipsetIdentity(
            display = "MediaTek Dimensity 8500",
            partCode = "MT6899",
            sources = listOf(ChipsetSource("ro.hardware", "mt6899", ChipsetEvidence.SYSTEM_PROPERTY)),
        )
        assertEquals("MT6899", identity.displayPartCode)
        assertEquals("MediaTek Dimensity 8500 (MT6899)", identity.displayWithPartCode)
    }

    @Test
    fun `a corroborating source is never read as a part code`() {
        // البائع واسم المصيّر يُقوّيان ولا يُسمّيان (عقد [ChipsetSource]) — ووضعهما في قوسي
        // الرمز كان سيُعرض «Adreno 750» رمزًا لمعالج.
        val identity = ChipsetIdentity(
            display = "Unknown (SoC)",
            sources = listOf(
                ChipsetSource("ro.soc.manufacturer", "Qualcomm", ChipsetEvidence.SYSTEM_PROPERTY, corroborates = true),
                ChipsetSource("gpu", "Adreno 750", ChipsetEvidence.GPU_INFO, corroborates = true),
                ChipsetSource("soc_id", "SM8650-AB", ChipsetEvidence.DEVICE_TREE),
            ),
        )
        assertEquals("SM8650-AB", identity.displayPartCode)
        assertEquals("Unknown (SM8650-AB)", identity.displayWithPartCode)
    }

    @Test
    fun `a named assertion is not reused as a code`() {
        // `SOC_MODEL` اسم تجاري كتبه المصنّع (`declaresName`)، فلا يُعرض مرتين: مرة اسمًا ومرة رمزًا.
        val identity = ChipsetIdentity(
            display = "MediaTek Dimensity 9300",
            sources = listOf(
                ChipsetSource("ro.soc.model", "Dimensity 9300", ChipsetEvidence.SYSTEM_PROPERTY, declaresName = true),
            ),
        )
        assertEquals(null, identity.displayPartCode)
        assertEquals("MediaTek Dimensity 9300", identity.displayWithPartCode)
    }
}
