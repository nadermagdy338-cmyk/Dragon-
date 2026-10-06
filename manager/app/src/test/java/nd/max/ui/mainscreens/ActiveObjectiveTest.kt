package nd.max.ui.mainscreens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * «ما يوازنه الآن» يُقاس من الأوزان المنشورة لا من التفضيل المحفوظ.
 *
 * وهذه هي نقطة الشك المعلنة: المستخدم يختار «بطارية» فيُحفظ تفضيله، بينما المحرك قد يكون
 * على هدف آخر (شاشة مطفأة، أو تعلّم تقدّم على التفضيل) — فيقرأ المستخدم اختياره على أنه
 * الواقع. والوسم هنا يُشتق من `Objective.labelFor(weights)` (نفس دالة المحرك)، والاختبار
 * الثاني **يفرض التقاطع مع مصدر `Objective.kt`** فلا ينحرف الرمز في جهة عن الأخرى.
 */
class ActiveObjectiveTest {

    private fun repoRoot(): File? = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "manager/app/src/main").isDirectory }
        ?: generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }

    @Test
    fun absentLabelMeansNoActiveObjective() {
        assertNull(activeObjective(null))
    }

    @Test
    fun engineLabelsMapToTheirOwnTone() {
        assertEquals(ObjectiveTone.Performance, activeObjective(ObjectiveLabels.PERFORMANCE))
        assertEquals(ObjectiveTone.Balanced, activeObjective(ObjectiveLabels.BALANCED))
        assertEquals(ObjectiveTone.Battery, activeObjective(ObjectiveLabels.BATTERY))
    }

    /** رمز غير معروف يبقى «متوازنًا»: لا هدف رابع، ولا وسم فارغ يُقرأ كخطأ عرض. */
    @Test
    fun unknownLabelFallsBackToBalanced() {
        assertEquals(ObjectiveTone.Balanced, activeObjective(""))
        assertEquals(ObjectiveTone.Balanced, activeObjective("screaming"))
    }

    /**
     * عقد التقاطع مع مصدر المحرك: الرموز التي يعرضها المستعمل هي **بعينها** التي تُرجعها
     * `Objective.labelFor` — فلا يُضاف هدف في المحرك ويُقرأ في الواجهة كشيء آخر.
     */
    @Test
    fun presentedLabelsAreTheOnesTheEngineEmits() {
        val source = repoRoot()?.let { File(it, "manager/app/src/main/java/nd/max/core/maxai/Objective.kt") }
            ?: File("src/main/java/nd/max/core/maxai/Objective.kt")
        assumeTrue(
            "مصدر Objective.kt غير مرئي من مجلد التشغيل؛ لم تُقيَّم الدعوى",
            source.isFile,
        )
        val body = source.readText().substringAfter("fun labelFor").substringBefore("fun ")
        val emitted = Regex("\"([a-z_]+)\"").findAll(body).map { it.groupValues[1] }.toSet()

        assertEquals(
            "رموز labelFor في المحرك تغيّرت — الواجهة تعرض غيرها",
            setOf(ObjectiveLabels.PERFORMANCE, ObjectiveLabels.BALANCED, ObjectiveLabels.BATTERY),
            emitted,
        )
        assertTrue("الوسط ليس ضمن الرموز الصادرة", ObjectiveLabels.BALANCED in emitted)
    }
}
