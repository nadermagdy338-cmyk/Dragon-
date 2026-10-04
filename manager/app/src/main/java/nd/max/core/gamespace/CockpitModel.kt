/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.core.gamespace

/**
 * منطق نقيّ للوحة القيادة (يُقاس بلا جهاز): تقطيع المنزلقات، ونص سرعة الشبكة، وعتبة الشاشة
 * الضيّقة، وقاعدة الطيّ عند الخمول. الرسم نفسه في `GamePanelCockpit` ولا يعرف هذه الأرقام.
 */
object CockpitModel {
    /** أقلّ عرض (dp) تُعرض معه اللوحة بدرج ظاهر وعمودَي بلاطات. */
    const val COMPACT_BELOW_DP = 640f

    /** بلا لمس بهذه المدّة تُطوى اللوحة ملء الشاشة حتى لا تحجب اللعبة إن نُسيت. */
    const val IDLE_COLLAPSE_MS = 30_000L

    fun isCompact(widthDp: Float): Boolean = widthDp < COMPACT_BELOW_DP

    /** رقم المقطع المضاء (0..count) لقيمة 0..1؛ القيم خارج المدى تُقصّ لا تُسقط. */
    fun segmentOf(value: Float, count: Int): Int =
        (value.coerceIn(0f, 1f) * count).toInt().coerceIn(0, count)

    /** «1.20 MB/s» فوق 1 MB/s وإلا «28.5 KB/s»، والسالب (عدّاد غير مدعوم) شرطة. */
    fun speedText(bytesPerSecond: Long): String = when {
        bytesPerSecond < 0 -> "--"
        bytesPerSecond >= 1_000_000L -> "%.2f MB/s".format(java.util.Locale.US, bytesPerSecond / 1_000_000f)
        else -> "%.1f KB/s".format(java.util.Locale.US, bytesPerSecond / 1_000f)
    }

    fun shouldCollapseIdle(nowMs: Long, lastTouchMs: Long): Boolean = nowMs - lastTouchMs > IDLE_COLLAPSE_MS
}
