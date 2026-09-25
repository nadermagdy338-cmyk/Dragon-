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
 *
 * Ported from ZKM's MtkRootService (com.zuan.kernelmanager.service.MtkRootService).
 *
 * libsu's RootService runs this class's process as root (uid 0), so file I/O
 * done here bypasses the SELinux/permission quirks that sometimes make
 * `su -c "echo ... > node"` shell fallbacks fail on stricter MTK sysfs nodes,
 * and it's much faster than spawning a shell per read/write since this is a
 * persistent bound connection.
 *
 * AndroidManifest.xml already declares this service (`.service.MtkRootService`);
 * this file provides the implementation that was previously missing, which is
 * why RootIpcManager.ipc was always null and every MTK feature that didn't
 * have its own shell fallback (GPU thermal reads, DEVFREQ freq/governor
 * writes, several Boost-tab toggle states, CPU misc availability) silently
 * did nothing.
 */
package nd.max.service

import android.content.Intent
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService
import nd.max.IMtkService
import nd.max.ui.util.EventLog
import java.io.File

class MtkRootService : RootService() {
    override fun onBind(intent: Intent): IBinder {
        return object : IMtkService.Stub() {

            override fun readNode(path: String): String {
                return try {
                    val file = File(path)
                    if (file.exists()) file.readText().trim() else ""
                } catch (e: Exception) {
                    ""
                }
            }

            override fun writeNode(path: String, value: String): Boolean {
                return try {
                    val file = File(path)
                    // Fast path first: plain root write (sufficient on most devices).
                    try {
                        file.writeText(value) // Executed purely as root (uid 0)
                        true
                    } catch (_: Exception) {
                        // HyperOS 3 (real-device log: rodin / Dimensity 8400 Ultra)
                        // denies even direct uid-0 writes to 0444 sysfs nodes with
                        // EACCES. Replicate the module binaries' proven dance
                        // (binutils setsgov / binprofiles write_unlock_core):
                        // save the original mode, chmod 0644, write, restore.
                        val originalMode = runCatching {
                            // 0o777 == 0b111_111_111 (Kotlin has no octal literals)
                            android.system.Os.stat(path).st_mode and 0b111_111_111
                        }.getOrDefault(0)
                        if (originalMode == 0) return false
                        android.system.Os.chmod(path, 0b110_100_100) // 0o644
                        try {
                            file.writeText(value)
                            true
                        } finally {
                            runCatching { android.system.Os.chmod(path, originalMode) }
                        }
                    }
                } catch (e: Exception) {
                    EventLog.error("MtkRootService", "write_node:$path", e)
                    false
                }
            }

            /**
             * عدة قراءات في معاملة binder واحدة. الحلقة هنا **داخل عملية الجذر**، فكل
             * عنصر يدفع ثمن قراءة ملف محلية فقط — لا معاملة IPC لكل عقدة.
             */
            override fun readNodes(paths: MutableList<String>): MutableList<String> {
                return paths.map { path -> readNode(path) }.toMutableList()
            }

            override fun nodeExists(path: String): Boolean {
                return File(path).exists()
            }

            override fun listDirectories(path: String): List<String> {
                return try {
                    val file = File(path)
                    if (file.isDirectory) {
                        file.listFiles()?.map { it.name } ?: emptyList()
                    } else emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
    }
}
