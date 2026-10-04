/*
 * Copyright (C) 2026 Zexshia
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

package nd.max

/**
 * Shared on-device path constants.
 *
 * Before this file existed, "/data/adb/.config/MaxManager/gamelist/maxmanagerApplist.json"
 * was hardcoded independently in TweakViewmodel.kt, ApplistViewmodel.kt,
 * AppSettingsViewmodel.kt, and (as of the Xiaomi-extras change) AppMonitor.kt.
 * Centralising it here means a future path change only needs one edit
 * instead of four, and new code has one obvious place to reuse instead of
 * copy-pasting the string again — see PROJECT_NOTES.md rule #3
 * ("متكررش منطق موجود").
 *
 * SERVICE_BIN was likewise hardcoded on its own in AppMonitor.kt's
 * restoreGlobalMaxManagerProfile() ("/data/adb/modules/MaxManager/system/bin/
 * sys.maxmanager-service"). Centralised here for the same reason, and now
 * also reused by AppMonitorLogger to forward log lines into the daemon's
 * shared log via its `--log` CLI hook. Module id confirmed from
 * mainfiles/module.prop (id=MaxManager).
 */
object MaxManagerPaths {
    const val MODULE_CONFIG = "/data/adb/.config/MaxManager"
    const val MODULE_DIR = "/data/adb/modules/MaxManager"
    const val APPLIST_JSON = "$MODULE_CONFIG/gamelist/maxmanagerApplist.json"
    const val SERVICE_BIN = "$MODULE_DIR/system/bin/sys.maxmanager-service"

    /**
     * `module.prop` المشحون — يقرؤه حارس هويّة الخادم (`is_kanged` في `ModuleIntegrity.c`)
     * ويقرؤه المشرف من الطرف نفسه ليقول أيّهما العطب: الملفّ أم لحظة كتابته.
     */
    const val MODULE_PROP = "$MODULE_DIR/module.prop"
    const val MODULE_APK = "$MODULE_DIR/system/product/priv-app/MaxManager/MaxManager.apk"
    const val MAXMANAGER_LOG = "$MODULE_CONFIG/debug/MaxManager.log"
    const val PER_APP_CPU_STATUS = "$MODULE_CONFIG/runtime/per_app_cpu_status"

    /**
     * نتيجة **كل مقبض** per-app (CPU · GPU · الحكام · الحرارة) مع رمز سببه —
     * يُكتب من مراقب الخلفية وتقرؤه الواجهة والتشخيص. التفصيل في
     * [nd.max.core.hardware.PerAppHardwareStatus].
     */
    const val PER_APP_HW_STATUS = "$MODULE_CONFIG/runtime/per_app_hw_status"
}
