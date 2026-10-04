/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.util
import nd.max.core.platform.PropertyUtils

import nd.max.core.hardware.RootFileAccess

/**
 * `AR-10` — ZRAM **كمسار معلن لا كمقبض أعمى**.
 *
 * الفكرة (AOSP — `mmd`): المنصّة صارت تدير ضغط الذاكرة بمراحل معلنة (`lz4` ثم وسم خمول
 * ثم إعادة ضغط `zstd` ثم writeback)، وتُضبط بخصائص `mmd.zram.*`. فالقيمة أولًا في **العرض**:
 * هل المنصّة تديره؟ بأي خوارزمية؟ وهل writeback مُفعَّل؟ ثم **احترام المنصّة** عندما تديره
 * (نفس مبدأ `XR-R4`: لا نُلغي ما وضعه النظام لأننا لا نحبّه).
 *
 * **قراءة فقط:** لا كتابة ولا `swapoff` ولا تغيير سياسة. وثلاث حالات: قيمة · `false` · `null`.
 */
data class ZramPlatformState(
    /** هل تديره المنصّة (`mmd.zram.*` معلنة)؟ `null` = لا نستطيع الجزم. */
    val platformManaged: Boolean?,
    /** خوارزمية الضغط كما تُعلنها المنصّة. */
    val algorithm: String?,
    /** الحجم المطلوب كما هو معلَن (نسبة أو بايت — نعرضه نصًّا لا نُفسّره). */
    val sizeRaw: String?,
    /** هل writeback إلى الوميض مُفعَّل؟ */
    val writebackEnabled: Boolean?,
    /** هل يُعلن النظام دعم إعادة الضغط (`/sys/block/zram0/recompress`)؟ */
    val recompressSupported: Boolean?,
    /** هل يُعلن النظام تتبّع الخمول (`/sys/block/zram0/idle`)؟ */
    val idleTrackingPresent: Boolean?,
    /** حجم منطقة الضغط الحالية بالبايت (`disksize`). */
    val disksizeBytes: Long?,
) {
    /** هل عندنا ما نقوله فعلًا؟ */
    val supported: Boolean get() = platformManaged == true || disksizeBytes != null
}

object ZramPlatformUtil {

    const val ZRAM_SYSFS = "/sys/block/zram0"

    // مفاتيح AOSP الرسمية لسياسة mmd (docs: memory management daemon).
    private const val PROP_ALGORITHM = "mmd.zram.comp_algorithm"
    private const val PROP_SIZE = "mmd.zram.size"
    private const val PROP_WRITEBACK = "mmd.zram.writeback.enabled"

    /** يفهم الأعلام الشائعة (`1/0`, `true/false`, `on/off`) ويعود بـ`null` لأي غيره. */
    fun parseBoolFlag(raw: String?): Boolean? = when (raw?.trim()?.lowercase()) {
        "1", "true", "yes", "on", "enabled" -> true
        "0", "false", "no", "off", "disabled" -> false
        else -> null
    }

    /** يحلّل بايتات صحيحة موجبة، أو `null` — ولا يُحوّل نصًّا فاسدًا إلى صفر. */
    fun parseBytes(raw: String?): Long? {
        val value = raw?.trim()?.toLongOrNull() ?: return null
        return value.takeIf { it > 0 }
    }

    fun read(): ZramPlatformState {
        val algorithm = PropertyUtils.get(PROP_ALGORITHM).takeIf { it.isNotBlank() }
        val sizeRaw = PropertyUtils.get(PROP_SIZE).takeIf { it.isNotBlank() }
        val writeback = parseBoolFlag(PropertyUtils.get(PROP_WRITEBACK).takeIf { it.isNotBlank() })

        val zramPresent = RootFileAccess.exists(ZRAM_SYSFS)
        val disksize = RootFileAccess.read("$ZRAM_SYSFS/disksize")?.let(::parseBytes)
        val recompress = if (!zramPresent) null else RootFileAccess.exists("$ZRAM_SYSFS/recompress")
        val idle = if (!zramPresent) null else RootFileAccess.exists("$ZRAM_SYSFS/idle")

        val managed: Boolean? = when {
            algorithm != null || sizeRaw != null || writeback != null -> true
            zramPresent -> false
            else -> null
        }

        return ZramPlatformState(
            platformManaged = managed,
            algorithm = algorithm,
            sizeRaw = sizeRaw,
            writebackEnabled = writeback,
            recompressSupported = recompress,
            idleTrackingPresent = idle,
            disksizeBytes = disksize,
        )
    }
}
