/*
 * Copyright (C) 2026-2027 KowX
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nd.max.ui.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import nd.max.IMtkService
import nd.max.service.MtkRootService

/**
 * Fast root-IPC channel that [MtkUtils] and [MtkViewModel] use to read/write MTK sysfs nodes
 * without spawning a shell for every call.
 *
 * Backed by [MtkRootService], an AIDL-based root service (ported from the upstream project this
 * screen was adapted from) that runs as uid 0 and services `readNode`/`writeNode`/`nodeExists`/
 * `listDirectories` calls directly against the filesystem.
 *
 * [bind] is called once from `MainActivity.onCreate` and [unbind] from `onDestroy`. Binding is
 * asynchronous - `ipc` stays null until [connection] fires - so every call site should keep
 * treating `ipc == null` as "not connected yet" and fall back to `Shell`/`File` access, the way
 * [MtkUtils] already does. That fallback path is what kept the app working before this service
 * existed, and it remains the safety net for devices/timings where the bind hasn't completed.
 */
object RootIpcManager {
    private const val TAG = "RootIPC"

    var ipc: IMtkService? = null
        private set

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            ipc = IMtkService.Stub.asInterface(service)
            Log.d(TAG, "MTK root IPC service connected")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            ipc = null
            Log.d(TAG, "MTK root IPC service disconnected")
        }
    }

    fun bind(context: Context) {
        if (ipc == null) {
            val intent = Intent(context.applicationContext, MtkRootService::class.java)
            RootService.bind(intent, connection)
        }
    }

    fun unbind() {
        if (ipc != null) {
            RootService.unbind(connection)
            ipc = null
        }
    }
}
