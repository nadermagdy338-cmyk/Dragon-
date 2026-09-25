/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.diagnostics

import nd.max.MaxManagerProps

/**
 * قائمة الإعدادات التي تُدرَج في ترويسة السجل وفي التقرير — **في موضع واحد**.
 *
 * لماذا وُجد هذا الملف
 * --------------------
 * الترويسة تُكتب من موضعين مختلفين: عملية المراقب (`AppMonitor`) وعملية التطبيق (`LogsViewer`
 * عند التفريغ وعند التصدير). ولو كتب كل موضع قائمته لصار في المستودع قائمتان تتباعدان ببطء —
 * وحين تتباعدان يصير الملف المُرسَل من مسار أقل ثراءً من الملف المُرسَل من المسار الآخر، وهذا
 * أسوأ من غياب المعلومة: يُقرأ الاثنان على أنهما صورة واحدة عن الجهاز.
 *
 * والمفتاح هنا هو **الاسم الذي يُقرأ** (`detailed_log`)، لأن الترويسة تُقرأ بعين إنسان وبريد
 * تلقائي معًا؛ واسم الخاصية الحقيقي يُحمل في [Entry.property].
 */
object LogSettingsDigest {

    data class Entry(val label: String, val property: String)

    /**
     * وليست القائمة اختيارًا اعتباطيًا: كل مدخل فيها **يغيّر سلوك المحرّك أو حجم السجل**، فلا
     * يمكن تفسير سلوك من ملف بلا معرفته. و`bypasscharge`/`fpsged` مثلًا تُسأل أولًا في كل تقرير
     * عن تحكم لا يعمل: هل هو معطّل أصلًا أم مفعّل وفشل؟
     */
    val entries: List<Entry> = listOf(
        Entry("detailed_log", MaxManagerProps.Conf.DETAILED_LOG),
        Entry("soc_type", MaxManagerProps.General.SOC_TYPE),
        Entry("disable_tweak", MaxManagerProps.General.DISABLE_TWEAK),
        Entry("ai_enabled", MaxManagerProps.Conf.AI_ENABLED),
        Entry("dynamic_thermal", MaxManagerProps.Conf.DYNAMIC_THERMAL),
        Entry("thermal_core", MaxManagerProps.Conf.THERMAL_CORE),
        Entry("fps_ged", MaxManagerProps.Conf.FPS_GED),
        Entry("use_fpsgo", MaxManagerProps.Conf.USE_FPSGO),
        Entry("bypass_charge", MaxManagerProps.Conf.BYPASS_CHARGE),
        Entry("cpu_limit", MaxManagerProps.Conf.CPU_LIMIT),
        Entry("disable_trace", MaxManagerProps.Conf.DISABLE_TRACE),
        Entry("debug_mode", MaxManagerProps.General.DEBUG_MODE),
        Entry("log_max_kb", MaxManagerProps.Conf.LOG_MAX_KB),
        Entry("log_min_level", MaxManagerProps.Conf.LOG_MIN_LEVEL),
    )

    /**
     * يقرأ القائمة بـ[read] — والدالة تُمرَّر لا تُستورَد، لأن قارئ الخصائص يختلف بحسب العملية
     * (انعكاس `SystemProperties` في التطبيق، وصندوق أوامر في المراقب)، والقراءة نفسها ليست
     * شأن هذا الملف.
     *
     * والقيمة الفارغة تُعاد كما هي (`""`): الترويسة تكتبها `unset`، وهذه حقيقة أنفع من قيمة
     * مُخمَّنة أو من إسقاط المدخل.
     */
    fun of(read: (String) -> String): List<Pair<String, String>> =
        entries.map { entry -> entry.label to read(entry.property) }
}
