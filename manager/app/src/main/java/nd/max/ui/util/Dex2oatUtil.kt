/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
ضبط تصريف ART عبر `cmd package compile`: مرشّح مضبوط لكل تطبيق، أو إعادة الحالة إلى ما
 * بعد التثبيت. وكل عملية تُقاس بزمنها وتُسجَّل نتيجتها (AR-13) — "تحسين" بلا أثر مُثبَت
 * ليس تحسينًا. */

package nd.max.ui.util
import nd.max.core.platform.EventLog

import android.os.SystemClock
import com.topjohnwu.superuser.Shell

/**
 * One ART compiler filter option, in the order shown to the user.
 *
 * `AR-13`: كل عملية هنا تُقاس وتُسجَّل نتيجتها **بزمن حقيقي** (لا تقدير)، لأن تصريفًا
 * بلا أثر مُسجَّل يبقى «تحسينًا» لا يمكن الدفاع عنه. والتسجيل يمرّ بـ[EventLog] نفسه
 * المبوَّب خلف سجل النشاط المفصَّل (مطفأ افتراضيًا كما بقية الأحداث).
 */
object Dex2oatUtil {

    private const val SCREEN = "Dex2oat"

    /** The exact tokens `cmd package compile -m <filter>` expects, in display order. */
    val COMPILE_MODES = listOf("speed-profile", "speed", "everything", "quicker", "verify")

    /** ينفّذ عملية ويقيسها ويسجّل نتيجتها — نقطة واحدة تكفي كل الدوال. */
    private inline fun measured(action: String, target: String?, block: () -> Boolean): Boolean {
        val start = SystemClock.elapsedRealtime()
        val success = runCatching(block).getOrDefault(false)
        val elapsed = SystemClock.elapsedRealtime() - start
        EventLog.result(
            screen = SCREEN,
            action = action,
            target = target,
            success = success,
            durationMs = elapsed,
        )
        return success
    }

    /** Force-recompiles a single app with the given filter (`-f` = force recompile). */
    fun compileApp(packageName: String, filter: String): Boolean =
        measured("compile:$filter", packageName) {
            Shell.cmd("cmd package compile -m $filter -f $packageName").exec().isSuccess
        }

    /** Clears compiled artifacts, reverting the app to its installer-time state. */
    fun resetApp(packageName: String): Boolean =
        measured("reset", packageName) {
            Shell.cmd("cmd package compile --reset $packageName").exec().isSuccess
        }

    /** Force-recompiles every installed package (`-a` = all packages). Slow; run off the main thread. */
    fun compileAll(filter: String): Boolean =
        measured("compile_all:$filter", null) {
            Shell.cmd("cmd package compile -m $filter -f -a").exec().isSuccess
        }

    /** Resets every installed package's compiled artifacts. Slow; run off the main thread. */
    fun resetAll(): Boolean =
        measured("reset_all", null) {
            Shell.cmd("cmd package compile --reset -a").exec().isSuccess
        }
}
