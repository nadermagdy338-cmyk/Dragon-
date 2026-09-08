package nd.max.core.maxai

import nd.max.core.hardware.DeviceStateCollector

/**
 * حاسوب المكافآت من القياسات الفعلية فقط.
 *
 * قاعدة المالك: موجبة = تحسّن أداء + حرارة مستقرة + بطارية مقبولة؛
 * سالبة = رأس حرارة أو تراجع أو لا تحسن. المدخلان قياسان حقيقيان
 * قبل تنفيذ الإجراء وبعده (DeviceStateCollector)، والإجراء نفسه
 * نُفِّذ فعلًا على العتاد — لا مكافأة على فعل لم يحدث.
 */
object RewardCalculator {

    /**
     * درجة الحالة: أعلى = أفضل. عقوبات موزونة: رأس الحرارة فوق 35°C
     * (تتصاعد حتى 45+)، الضغط الزائد فوق 85%، ومستوى البطارية.
     * نفس الأوزان التي كانت في حلقة التعلم القديمة — محفوظة عمدًا
     * كي لا تتغير دلالة المكافأة عند إعادة الإعمار.
     */
    fun stateScore(s: DeviceStateCollector.DeviceSnapshot): Float {
        val thermalPenalty = ((s.thermal * 100f - 35f) / 10f).coerceIn(0f, 1f) * 0.5f
        val overloadPenalty = ((s.cpuLoad * 100f - 85f) / 15f).coerceIn(0f, 1f) * 0.3f
        val batteryTerm = (1f - s.battery) * 0.2f
        return (1f - thermalPenalty - overloadPenalty - batteryTerm).coerceIn(0f, 1f)
    }

    /**
     * المكافأة لانتقال واحد.
     *
     * @param executed هل نُفِّذ الإجراء فعليًا على العتاد؟ (بلا تنفيذ
     * لا يستحق الوكيل مكافأة ولا عقوبة — الانتقال يُنسى بصدق).
     */
    fun compute(
        before: DeviceStateCollector.DeviceSnapshot,
        after: DeviceStateCollector.DeviceSnapshot,
        executed: Boolean,
    ): Float {
        if (!executed) return 0f

        val tempDeltaC = (after.thermal - before.thermal) * 100f
        val scoreDelta = stateScore(after) - stateScore(before)

        return when {
            // رأس حرارة واضح: عقوبة قاطعة مهما تحسن غير ذلك.
            tempDeltaC > 2f -> -1f
            // ارتفاع حراري مقلق بلا مكسب: عقوبة.
            tempDeltaC > 1f && scoreDelta <= 0f -> -0.5f
            // تحسن حقيقي بحرارة مستقرة: مكافأة موجبة بمقدار التحسن.
            scoreDelta > 0.05f && tempDeltaC < 1.5f -> scoreDelta.coerceIn(0f, 1f)
            // تراجع واضح: عقوبة بمقدار التراجع.
            scoreDelta < -0.05f -> scoreDelta.coerceIn(-1f, 0f)
            // محايد: لا شيء.
            else -> 0f
        }
    }
}
