/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * صياغة سطور النشاط — **الخام يصير مفهومًا هنا، لا في الواجهة** (طلب المالك: لا قيم خام، ولا أسهم تنعكس).
 *
 * ما يُفكّ هنا:
 * - حدّ المعالج `min:max` بالكيلوهرتز ⇒ نطاق يُقرأ بوحداته (`StoryboardModel.readableRange`).
 * - الملف الشخصي `balanced` ⇒ مفتاح ثابت، ثم اسمه بلغة المستخدم في الواجهة.
 * - سياسة المعالج `policy4` ⇒ عنقودها (كفاءة · أداء · فائق)، فلا يتكرّر «حدود المعالج» لعنقودين مختلفين.
 * - السبب الداخلي `not-verified` ⇒ واحد من أسباب قليلة يفهمها المستخدم.
 * - المقبض ⇒ الشاشة التي تضبطه، أو لا شيء إن لم تكن له شاشة معروفة (لا باب وهمي).
 *
 * ولا يُخترع شيء: الخام غير المعروف يُعاد كما هو.
 */
package nd.max.ui.mainscreens

import nd.max.core.hardware.HardwareControlKey
import nd.max.ui.navigation.MaxDestination

/** الملف الشخصي للطاقة بمفتاحه الثابت، لا بنصّه المترجم. */
internal enum class ActivityProfile { DEFAULT, BALANCED, PERFORMANCE, POWERSAVE, GAMING }

/** عنقود المعالج بدوره على هذه المنصّة: كفاءة (`policy0`) · أداء (`policy4`) · فائق (`policy7`). */
internal enum class ActivityCluster { EFFICIENCY, PERFORMANCE, PRIME }

/** السبب الذي يُقال للمستخدم حين لا يُطبَّق سطر. */
internal enum class ActivityReason { UNVERIFIED, UNSUPPORTED, NOT_WRITABLE, HELD, FAILED, OTHER }

internal object ActivityFormat {
    private val KHZ_PAIR = Regex("""\s*(\d{4,})\s*:\s*(\d{4,})\s*""")

    /** الصيغة الخام لحدّ المعالج: `min:max` بالكيلوهرتز. وغيرها لا يُفكّ. */
    fun khzPair(raw: String?): Pair<Long, Long>? {
        val m = KHZ_PAIR.matchEntire(raw ?: return null) ?: return null
        return m.groupValues[1].toLong() to m.groupValues[2].toLong()
    }

    fun profileOf(raw: String?): ActivityProfile? = when (raw?.trim()?.lowercase()) {
        "default" -> ActivityProfile.DEFAULT
        "balanced" -> ActivityProfile.BALANCED
        "performance" -> ActivityProfile.PERFORMANCE
        "battery", "eco", "powersave" -> ActivityProfile.POWERSAVE
        "gaming" -> ActivityProfile.GAMING
        else -> null
    }

    fun clusterOf(policy: String): ActivityCluster? = when (policy.trim()) {
        "policy0" -> ActivityCluster.EFFICIENCY
        "policy4" -> ActivityCluster.PERFORMANCE
        "policy7" -> ActivityCluster.PRIME
        else -> null
    }

    /** السبب الخام يصير فئة واحدة. الفئة الأخيرة لما لا نعرفه، فلا يُخترع سبب. */
    fun reasonOf(raw: String): ActivityReason {
        val r = raw.lowercase()
        return when {
            "writable" in r || "permission" in r -> ActivityReason.NOT_WRITABLE
            "unsupported" in r || "missing" in r || "not-exposed" in r -> ActivityReason.UNSUPPORTED
            "not-verified" in r || "unverified" in r || "not_verified" in r -> ActivityReason.UNVERIFIED
            "held" in r || "blocked" in r || "owner" in r -> ActivityReason.HELD
            "failed" in r || "rollback" in r || "refused" in r -> ActivityReason.FAILED
            else -> ActivityReason.OTHER
        }
    }

    /** الشاشة التي تضبط المقبض، أو null حين لا شاشة معروفة له (فلا يُكتب باب وهمي). */
    fun routeFor(knob: String): String? = when {
        HardwareControlKey.isCpuLimits(knob) || knob == "cpu_boost" || knob == "cpu_governor" ->
            MaxDestination.CpuCoreControl.route
        knob.startsWith("gpu_") -> MaxDestination.GpuStudio.route
        knob == "thermal" -> MaxDestination.ThermalDetail.route
        knob == "kill_bg_apps" -> MaxDestination.MemoryHub.route
        knob.startsWith("max_ai") -> MaxDestination.MaxAi.route
        else -> null
    }
}
