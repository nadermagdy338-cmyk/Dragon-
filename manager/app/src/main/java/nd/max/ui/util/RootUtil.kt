/*
 * Copyright (C) 2026-2027 Zexshia
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

import nd.max.MaxManagerPaths
import nd.max.MaxManagerProps


import android.os.FileObserver
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import nd.max.R
import nd.max.core.hardware.RootFileAccess


object RootUtils {
    private const val MODULE_DIR = "/data/adb/modules/MaxManager"
    private const val API_DIR_PATH = "/data/data/nd.max/API"
    private const val PROFILE_FILE_NAME = "current_profile"
    private const val PROFILE_PATH = "$API_DIR_PATH/$PROFILE_FILE_NAME"
    private const val DAEMON_PROFILE_PATH = "/data/adb/.config/MaxManager/API/current_profile"

    private fun readRootFile(path: String): String? {
        return RootFileAccess.read(path)
    }

    /**
     * يكتب ملفًا تملكه الخدمة (root) داخل مجلّد التطبيق: محاولة مباشرة، ثم طريق الجذر.
     *
     * وكان الخلل أن **طريق الجذر لم يكن يُجرَّب أبدًا** في الحالة التي وُجد من أجلها:
     * كان الحكم على قابلية الكتابة مبنيًّا على **المجلّد** (`parent.canWrite()`)،
     * بينما الملف الذي أنشأته الخدمة يخصّ root داخل مجلّد يملكه التطبيق — أي أن
     * `parent.canWrite()` تُعيد `true` بينما `target.writeText()` ترمي `EACCES`،
     * فيقفز الاستثناء ويكتب السجل خطأً يُفهَم كأن الجذر لم يستطع الكتابة.
     *
     * والقياس من حزمة سجلّات جهاز حقيقي (2026-09-20، مرّتين في جلستين مختلفتين):
     * `UI_ERROR screen=RootUtils operation=write_root_file:/data/data/nd.max/API/current_profile
     * detail=… open failed: EACCES (Permission denied)` — على مسار تكتبه الخدمة أصلًا
     * (`PROFILE_MODE_APP` في `AZenith.h`)، فلا يبقى تخمين: مرآة الملف يجب أن تُكتب
     * بالجذر، والسجل يجب أن يقول الحقيقة عند الفشل الحقيقي وحده.
     */
    private fun writeRootFile(path: String, content: String) {
        val written = runCatching {
            val target = File(path)
            val parent = target.parentFile
            if (parent == null) return@runCatching false
            if (!parent.exists() && !parent.mkdirs()) return@runCatching false
            if (!parent.canWrite()) return@runCatching false
            // الحكم يُبنى على **الملف** لا على المجلّد: ملفٌ يملكه root داخل مجلّد
            // التطبيق غير قابل للكتابة منه وإن كان المجلّد قابلًا للكتابة.
            if (target.exists() && !target.canWrite()) return@runCatching false
            target.writeText(content)
            true
        }.getOrDefault(false)
        if (written) return
        if (runCatching { RootFileAccess.write(path, content) }.getOrDefault(false)) return
        EventLog.error(
            "RootUtils",
            "write_root_file:$path",
            IOException("لم تنجح الكتابة مباشرةً ولا عبر الجذر (المسار يملكه root داخل مجلّد التطبيق)")
        )
    }

    private fun syncProfileState() {
        try {
            val apiDir = File(API_DIR_PATH)
            if (!apiDir.exists()) {
                apiDir.mkdirs()
            }
            val content = RootFileAccess.read(DAEMON_PROFILE_PATH) ?: return
            writeRootFile(PROFILE_PATH, content)
        } catch (e: Exception) {
            EventLog.error("RootUtils", "sync_profile_state", e)
        }
    }

    /**
     * `AR-04` — يفوّض إلى **القارئ الواحد** (`ModuleHealthUtil`) بدل أمر shell على مسار مكتوب
     * يدويًّا. السبب: كان في المستودع قارئان لرقم الإصدار نفسه (هذا وذاك)، ومسار مُكرّر يمكن
     * أن ينحرف عن الحقيقة. الآن قراءة واحدة والحكم في `VersionIdentity`. والعقد لم يتغيّر.
     */
    fun getModuleVersionCode(): Int = ModuleHealthUtil.read().versionCode

    data class GameInfo(val pkg: String?, val pid: Int?, val startTime: String?)

    fun observeGameInfo(): Flow<GameInfo> = flow {
        var lastInfo: GameInfo? = null
        while (true) {
            val raw = readRootFile("/data/data/nd.max/API/gameinfo")
            var currentInfo = GameInfo(null, null, null)

            if (!raw.isNullOrBlank()) {
                val lines = raw.lines()
                val firstLine = lines[0].split(" ")

                val pkg = firstLine.getOrNull(0)?.takeIf { it != "NULL" && it.isNotBlank() }
                val pid = firstLine.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
                val time = lines.find { it.startsWith("Time:") }?.substringAfter("Time:")?.trim()

                currentInfo = GameInfo(pkg, pid, time)
            }

            if (currentInfo != lastInfo) {
                emit(currentInfo)
                lastInfo = currentInfo
            }
            delay(2000)
        }
    }.flowOn(Dispatchers.IO)

    fun observeProfileRes(): Flow<Int> = callbackFlow {
        syncProfileState()

        trySend(getCurrentProfileRes())

        val apiDir = File(API_DIR_PATH)
        if (!apiDir.exists()) {
            apiDir.mkdirs()
        }

        val observer = object : FileObserver(apiDir, MODIFY or CREATE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == PROFILE_FILE_NAME) {
                    trySend(getCurrentProfileRes())
                }
            }
        }

        observer.startWatching()
        awaitClose { observer.stopWatching() }
    }.flowOn(Dispatchers.IO)

    fun getCurrentProfileRes(): Int {
        val content = readRootFile(PROFILE_PATH)

        return when (content) {
            "0" -> R.string.status_initializing
            "1" -> {
                val isLite = PropertyUtils.get(MaxManagerProps.Conf.LITE_MODE, "0") == "1"
                if (isLite) R.string.profile_perflite else R.string.Profile_Performance
            }
            "2" -> R.string.Profile_Balanced
            "3" -> R.string.Profile_ECO_mode
            else -> R.string.status_unknown
        }
    }

    fun requestRootAccess(): Boolean {
        val currentShell = Shell.getCachedShell()
        if (currentShell != null && !currentShell.isRoot) {
            currentShell.close()
        }
        return Shell.getShell().isRoot
    }

    fun observeServiceStatusRes(): Flow<Pair<Int, String>> = flow {
        var lastStatus: Pair<Int, String>? = null
        while (true) {
            val currentStatus = getServiceStatusRes()
            if (currentStatus != lastStatus) {
                emit(currentStatus)
                lastStatus = currentStatus
            }
            delay(2000)
        }
    }.flowOn(Dispatchers.IO)

    fun isRootGranted(): Boolean {
        val currentShell = Shell.getCachedShell()
        if (currentShell != null && !currentShell.isRoot) {
            currentShell.close()
        }
        return Shell.getShell().isRoot
    }

    fun isModuleInstalled(): Boolean {
        return SuFile(MODULE_DIR).exists()
    }

    fun getServiceStatusRes(): Pair<Int, String> {
        val result = Shell.cmd("pidof sys.maxmanager-service").exec()
        return if (result.isSuccess) {
            val pid = result.out.firstOrNull() ?: ""
            R.string.status_alive to pid
        } else {
            R.string.status_suspended to ""
        }
    }

    fun isUpdateApkAvailable(): Boolean {
        return SuFile(MaxManagerPaths.MODULE_APK).exists()
    }

    fun isModuleUpdatePendingReboot(): Boolean {
        return SuFile("/data/adb/modules/MaxManager/update").exists()
    }
}
