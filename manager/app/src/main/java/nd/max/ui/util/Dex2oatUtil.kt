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

import com.topjohnwu.superuser.Shell

/** One ART compiler filter option, in the order shown to the user. */
object Dex2oatUtil {

    /** The exact tokens `cmd package compile -m <filter>` expects, in display order. */
    val COMPILE_MODES = listOf("speed-profile", "speed", "everything", "quicker", "verify")

    /** Force-recompiles a single app with the given filter (`-f` = force recompile). */
    fun compileApp(packageName: String, filter: String): Boolean {
        return Shell.cmd("cmd package compile -m $filter -f $packageName").exec().isSuccess
    }

    /** Clears compiled artifacts, reverting the app to its installer-time state. */
    fun resetApp(packageName: String): Boolean {
        return Shell.cmd("cmd package compile --reset $packageName").exec().isSuccess
    }

    /** Force-recompiles every installed package (`-a` = all packages). Slow; run off the main thread. */
    fun compileAll(filter: String): Boolean {
        return Shell.cmd("cmd package compile -m $filter -f -a").exec().isSuccess
    }

    /** Resets every installed package's compiled artifacts. Slow; run off the main thread. */
    fun resetAll(): Boolean {
        return Shell.cmd("cmd package compile --reset -a").exec().isSuccess
    }
}
