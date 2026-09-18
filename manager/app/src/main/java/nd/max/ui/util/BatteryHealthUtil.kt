/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.util

import nd.max.core.hardware.RootFileAccess

/**
 * `AR-08` — صحة البطارية: السعة الحقيقية مقابل التصميمية، وعدد الدورات إن أعلنه النظام.
 *
 * **قاعدة الصدق (ADR-07 / XR-19):** ثلاث حالات فقط، ولا رقم مُخترَع:
 * - `MEASURED` — كل الأرقام من ملفات النظام فعلًا.
 * - `ESTIMATED` — الرقم **مشتقّ** (مثل النسبة من سعة التصميم) ونُعلن أنه كذلك.
 * - `UNSUPPORTED` — النظام لا يُعلنها؛ نعرض `—` بدل أن نخترع.
 *
 * مرجع الفئة: `abanana84/abattery` — «أداة تشخيص لا قياس مخبري»، ويُظهر `N/A` بدل رقم.
 */
enum class BatteryHealthSource { MEASURED, ESTIMATED, UNSUPPORTED }

data class BatteryHealth(
    /** سعة التصميم بوحدة µAh (الملف `charge_full_design`). */
    val designUah: Long?,
    /** السعة القصوى الحالية بوحدة µAh (الملف `charge_full`). */
    val currentFullUah: Long?,
    /** عدد دورات الشحن إن أعلنه النظام (`cycle_count`). */
    val cycleCount: Int?,
    /** نسبة السعة الحالية إلى التصميمية (مشتقّة ⇒ `ESTIMATED`). */
    val stateOfHealthPercent: Int?,
) {
    val source: BatteryHealthSource
        get() = when {
            designUah != null && currentFullUah != null -> BatteryHealthSource.MEASURED
            cycleCount != null || designUah != null || currentFullUah != null ->
                BatteryHealthSource.ESTIMATED
            else -> BatteryHealthSource.UNSUPPORTED
        }
}

object BatteryHealthUtil {

    // المصنّعون يوزّعون هذه العُقد بين `battery` و`bms`؛ نجرّب المجموعتين ونأخذ أول مقروء.
    private val DESIGN_PATHS = listOf(
        "/sys/class/power_supply/battery/charge_full_design",
        "/sys/class/power_supply/bms/charge_full_design",
        "/sys/class/power_supply/battery/energy_full_design",
    )
    private val FULL_PATHS = listOf(
        "/sys/class/power_supply/battery/charge_full",
        "/sys/class/power_supply/bms/charge_full",
        "/sys/class/power_supply/battery/energy_full",
    )
    private val CYCLE_PATHS = listOf(
        "/sys/class/power_supply/battery/cycle_count",
        "/sys/class/power_supply/bms/cycle_count",
    )

    /** يحلّل قيمة سعة صحيحة موجبة، أو `null` — ولا يحوّل النصّ الفاسد إلى صفر. */
    fun parseMicroAh(raw: String?): Long? {
        val value = raw?.trim()?.toLongOrNull() ?: return null
        return value.takeIf { it > 0 }
    }

    /**
     * عدد الدورات: النظام يستخدم `-1` (أو أقل) معنى «غير معروف» على كثير من الأجهزة،
     * فنُعيد `null` ولا نعرض رقمًا كاذبًا.
     */
    fun parseCycleCount(raw: String?): Int? {
        val value = raw?.trim()?.toIntOrNull() ?: return null
        return value.takeIf { it >= 0 }
    }

    /**
     * النسبة المئوية لسعة الحالية إلى التصميمية. تُشترط سلامة المدخلين: كلاهما > 0،
     * والنسبة في نطاق معقول (≤ 150٪) وإلا فهي قراءة فاسدة لا تُنشَر.
     */
    fun stateOfHealthPercent(designUah: Long?, currentFullUah: Long?): Int? {
        if (designUah == null || currentFullUah == null) return null
        if (designUah <= 0 || currentFullUah <= 0) return null
        val percent = ((currentFullUah * 100.0) / designUah).toInt()
        return percent.takeIf { it in 1..150 }
    }

    fun read(): BatteryHealth {
        val design = firstValue(DESIGN_PATHS)?.let(::parseMicroAh)
        val full = firstValue(FULL_PATHS)?.let(::parseMicroAh)
        val cycles = firstValue(CYCLE_PATHS)?.let(::parseCycleCount)
        return BatteryHealth(
            designUah = design,
            currentFullUah = full,
            cycleCount = cycles,
            stateOfHealthPercent = stateOfHealthPercent(design, full),
        )
    }

    private fun firstValue(paths: List<String>): String? =
        paths.firstNotNullOfOrNull { RootFileAccess.read(it) }
}
