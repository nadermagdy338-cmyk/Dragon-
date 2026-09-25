/*
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

import nd.max.core.hardware.RootFileAccess

object DebugUtils {
    private const val FULLMODE_DEBUG_PATH = "/data/adb/.config/MaxManager/debug/FullMode"

    /**
     * وضع المطوّر مُعلَن في الملف؟ والعقدة الغائبة أو غير المقروءة تعني «لا» — تُقرأ الملف
     * عبر الطبقة الموحّدة (قارئ أصلي ← IPC الجذر ← ملف ← صدفة) بدل صدفة لكل نداء.
     */
    fun isFullModeEnabled(): Boolean {
        return try {
            val content = RootFileAccess.read(FULLMODE_DEBUG_PATH) ?: return false
            content != "0"
        } catch (e: Exception) {
            false
        }
    }
}
