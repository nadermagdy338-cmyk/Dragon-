/*
 * ART/dex2oat compilation control - forces `cmd package compile` with a
 * chosen filter (speed-profile/speed/everything/quicker/verify), or resets
 * an app back to its installer-time compilation state.
 *
 * App listing is deliberately NOT reimplemented here: DebloatFreezeUtil
 * already exposes getInstalledApps()/DebloatAppInfo (label, package name,
 * isSystem, icon) for the Debloat & Freeze screen, and that's exactly what
 * this screen also needs - so Dex2oatViewModel reuses it directly instead of
 * this file duplicating a second app-enumeration path.
 *
 * Adapted from ZKM's Dex2oatUtils.kt (compile-mode logic only).
 *
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.util

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
