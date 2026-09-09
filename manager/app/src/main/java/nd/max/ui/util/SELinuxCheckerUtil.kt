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


import android.content.Context
import androidx.compose.runtime.Composable
import nd.max.R
import nd.max.core.hardware.RootFileAccess


fun getSELinuxStatus(context: Context): String {
    val enforcePath = "/sys/fs/selinux/enforce"
    return when {
        !RootFileAccess.exists(enforcePath) -> context.getString(R.string.selinux_disabled)
        else -> when (RootFileAccess.read(enforcePath)) {
            "1" -> context.getString(R.string.selinux_enforcing)
            "0" -> context.getString(R.string.selinux_permissive)
            else -> context.getString(R.string.status_unknown)
        }
    }
}
