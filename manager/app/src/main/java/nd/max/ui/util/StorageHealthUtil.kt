/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.util

import nd.max.core.hardware.RootFileAccess

/**
 * `AR-11` — صحة وسائط التخزين: التآكل المُعلَن، لا الفراغ فقط.
 *
 * الفئة مأخوذة من `KuatoDev/Wear-Level-Insight` (قراءة مؤشرات عمر eMMC/UFS).
 * **قاعدة الصدق:** ما لا يُعلنه العتاد يبقى `null` ⇒ «غير معروف»، ولا يُفسَّر كـ«سليم».
 *
 * دلالات eMMC (JEDEC): `life_time` قيمة `0x01..0x0A` = ١٠٪..١٠٠٪ من العمر المستهلك،
 * و`0x0B` = تجاوز الحدّ. و`pre_eol_info`: `0x01` عادي · `0x02` تحذير · `0x03` عاجل.
 */
data class StorageMediaHealth(
    /** اسم الجهاز المقروء (مثل `mmcblk0`)، أو `null` إن لم نجد أيًّا. */
    val device: String?,
    /** التقدير A للعمر (قيمة JEDEC الخام). */
    val lifeTimeA: Int?,
    /** التقدير B للعمر (قيمة JEDEC الخام). */
    val lifeTimeB: Int?,
    /** `pre_eol_info` الخام (`1`/`2`/`3`). */
    val preEol: Int?,
) {
    val supported: Boolean get() = lifeTimeA != null || preEol != null

    /** العمر المستهلك بالنسبة المئوية، مشتقّ من قيمة JEDEC (⇒ يُوسَم تقديرًا). */
    val usedPercent: Int? get() = StorageHealthUtil.lifeTimePercent(lifeTimeA)
}

object StorageHealthUtil {

    /** جهات مرشّحة: eMMC ثم UFS/SD. نأخذ أول ما يُعلن مؤشرًا فعليًا. */
    private val DEVICE_CANDIDATES = listOf("mmcblk0", "sda", "mmcblk1")

    /**
     * يحلّل أول قيمة في نصّ JEDEC (مثل `0x01 0x02`) بصيغة سادسية، أو عشري إن لم تبدأ بـ`0x`.
     */
    fun parseHexToken(raw: String?): Int? {
        val token = raw?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.trim() ?: return null
        if (token.isEmpty()) return null
        return if (token.startsWith("0x", ignoreCase = true)) {
            token.drop(2).toIntOrNull(16)
        } else {
            token.toIntOrNull() ?: token.toIntOrNull(16)
        }
    }

    /** يفكّ سطرين `0x01 0x02` إلى `[1, 2]`، أو `null` إن تعذّر. */
    fun parseLifeTimePair(raw: String?): Pair<Int?, Int?>? {
        val tokens = raw?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: return null
        if (tokens.isEmpty()) return null
        fun v(t: String?) = t?.let {
            if (it.startsWith("0x", ignoreCase = true)) it.drop(2).toIntOrNull(16)
            else it.toIntOrNull() ?: it.toIntOrNull(16)
        }
        return v(tokens.getOrNull(0)) to v(tokens.getOrNull(1))
    }

    /** نسبة العمر المستهلك من قيمة JEDEC: `0` = غير معرّف ⇒ `null`، و`≥11` = تجاوز ⇒ ١٠٠٪. */
    fun lifeTimePercent(value: Int?): Int? = when {
        value == null -> null
        value in 1..10 -> value * 10
        value >= 11 -> 100
        else -> null
    }

    fun read(): StorageMediaHealth {
        for (device in DEVICE_CANDIDATES) {
            val base = "/sys/block/$device/device"
            val lifeRaw = RootFileAccess.read("$base/life_time")
            val eolRaw = RootFileAccess.read("$base/pre_eol_info")
            if (lifeRaw == null && eolRaw == null) continue
            val pair = parseLifeTimePair(lifeRaw)
            return StorageMediaHealth(
                device = device,
                lifeTimeA = pair?.first,
                lifeTimeB = pair?.second,
                preEol = parseHexToken(eolRaw),
            )
        }
        return StorageMediaHealth(device = null, lifeTimeA = null, lifeTimeB = null, preEol = null)
    }
}
