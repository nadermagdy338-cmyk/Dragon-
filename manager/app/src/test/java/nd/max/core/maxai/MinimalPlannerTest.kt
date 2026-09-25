/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد المخطِّط: معادلة الجدوى التي تقرر أي مقبض يتحرك.
 *
 * هذه هي أخطر نقطة في العقل — لو رجّحت المقبض الخشن المكلف أو الذي
 * يُتوقع أن يسخّن، عاد Max AI إلى "اقفز إلى السقف الكامل" وهو بالضبط
 * ما فشل في سجل الجهاز الحقيقي.
 */
class MinimalPlannerTest {

    private fun utility(
        impact: Float,
        cost: Float,
        credibility: Float = 1f,
        thermal: Float = 0f,
    ) = MinimalPlanner.utilityOf(impact, cost, credibility, thermal)

    @Test
    fun `cheaper control wins when impact is equal`() {
        val cheap = utility(impact = 0.1f, cost = 0.1f)
        val costly = utility(impact = 0.1f, cost = 0.5f)
        assertTrue("المقبض الأرخص يجب أن يتقدم", cheap > costly)
    }

    @Test
    fun `higher credible control wins when cost and impact are equal`() {
        val trusted = utility(impact = 0.1f, cost = 0.2f, credibility = 0.9f)
        val contested = utility(impact = 0.1f, cost = 0.2f, credibility = 0.1f)
        assertTrue("المقبض الموثوق يجب أن يتقدم", trusted > contested)
    }

    @Test
    fun `predicted heat penalizes utility even for a big impact`() {
        // هذا هو الحارس: مقبض يرفع الأداء كثيرًا لكنه يسخّن بقوة يجب
        // أن يسقط تحت بديل أبرد — لا أن يفوز بمجرد كبر أثره.
        val hot = utility(impact = 0.5f, cost = 0.2f, credibility = 1f, thermal = 5f)
        val cool = utility(impact = 0.2f, cost = 0.2f, credibility = 1f, thermal = 0f)
        assertTrue("البديل البارد يجب أن يتفوق على الساخن رغم أثر أقل", cool > hot)
    }

    @Test
    fun `negative predicted heat is treated as no penalty`() {
        // التبريد المتوقع لا يمنح مكافأة زائدة عن الحد: النموذج لا
        // يجب أن "يكافئ" مقبضًا لمجرد أنه قد يبرّد.
        val cooling = utility(impact = 0.2f, cost = 0.2f, thermal = -3f)
        val neutral = utility(impact = 0.2f, cost = 0.2f, thermal = 0f)
        assertEquals(cooling, neutral, 0.0001f)
    }

    @Test
    fun `zero cost is floored instead of dividing by zero`() {
        val u = utility(impact = 0.1f, cost = 0f)
        assertTrue("يجب أن يبقى منتهيًا لا لا نهائي", u.isFinite())
        assertTrue(u > 0f)
    }

    @Test
    fun `satisfaction threshold marks a healthy device`() {
        // مبدأ "لا تدخل بلا فجوة": العتبة المعلنة هي الحد الفاصل.
        assertEquals(0.75f, MinimalPlanner.SATISFIED_SCORE, 0.0001f)
    }

    @Test
    fun `ranking is a total order on distinct utilities`() {
        val a = utility(impact = 0.3f, cost = 0.1f, credibility = 1f, thermal = 0f)
        val b = utility(impact = 0.3f, cost = 0.3f, credibility = 1f, thermal = 0f)
        val c = utility(impact = 0.1f, cost = 0.1f, credibility = 1f, thermal = 3f)
        val ranked = listOf(a, b, c).sortedDescending()
        assertTrue("الترتيب يجب أن يفصل الثلاثة", ranked[0] > ranked[1] && ranked[1] > ranked[2])
    }
}
