/*
 * Copyright (C) 2026-2027 MaxManager contributors
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

package nd.max.core.hardware

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import nd.max.MaxManagerPaths
import nd.max.core.diagnostics.DiagnosticCenter

/**
 * تطبيق ملفات الأداء العامة (performance / balanced / eco) عبر خدمة
 * الوحدة الحقيقية sys.maxmanager-service — نفس المسار الذي يستخدمه
 * HomeViewmodel.applyProfile() وزر التشغيل السريع.
 *
 * هذه هي الطريقة الوحيدة الصحيحة لتبديل الملف العام: الوحدة الأصلية
 * تدير المحافظ والحدود و I/O وغيرها كوحدة واحدة، فأي كتابة مباشرة
 * منفصلة كانت ستتصارع معها.
 */
object ProfileApplier {

    const val PROFILE_PERFORMANCE = "1"
    const val PROFILE_BALANCED = "2"
    const val PROFILE_ECO = "3"

    // نفس مسار ملف الملف الحالي الذي يراقبه RootUtils (observeProfileRes)
    // عبر FileObserver — الكتابة هنا تُحدِّث شارة الملف في الواجهة تلقائيًا.
    private const val DAEMON_PROFILE_PATH = "/data/adb/.config/MaxManager/API/current_profile"

    /**
     * يطبّق ملفًا عامًا. يعيد true فقط عندما تنجح الخدمة فعليًا.
     *
     * @param profileId "1" أداء / "2" متوازن / "3" توفير طاقة
     */
    fun apply(profileId: String): Boolean {
        if (profileId !in listOf(PROFILE_PERFORMANCE, PROFILE_BALANCED, PROFILE_ECO)) {
            return false
        }
        return runService(profileId, fromAi = false)
    }

    /**
     * مسار محرك MAX AI ومحرك الأمان: نفس الخدمة، بعلم --from-ai الذي
     * يتيح لها التطبيق أثناء تفعيل AI — بوابة الوحدة ترفض المسار
     * اليدوي فقط (الاختيار اليدوي أثناء AI يُحفظ معلقًا في التطبيق
     * بدل التنفيذ، فلا يتصارع مالكان على الملف).
     */
    fun applyFromAi(profileId: String): Boolean {
        if (profileId !in listOf(PROFILE_PERFORMANCE, PROFILE_BALANCED, PROFILE_ECO)) {
            return false
        }
        return runService(profileId, fromAi = true)
    }

    private fun runService(profileId: String, fromAi: Boolean): Boolean {
        val serviceBin = SuFile(MaxManagerPaths.SERVICE_BIN)
        if (!serviceBin.exists()) {
            // الوحدة مثبتة لكن خدمتها غير موجودة — أثر بالغ الأهمية في
            // التشخيص: كل تبديل ملف من الواجهة يفشل بصمت خلافًا لذلك.
            DiagnosticCenter.record(
                "profile",
                "service binary missing at ${MaxManagerPaths.SERVICE_BIN}",
                level = DiagnosticCenter.Level.ERROR
            )
            return false
        }
        val cmd = if (fromAi) {
            "${MaxManagerPaths.SERVICE_BIN} -p $profileId --from-ai"
        } else {
            "${MaxManagerPaths.SERVICE_BIN} -p $profileId"
        }
        val result = Shell.cmd(cmd).exec()
        if (!result.isSuccess) {
            DiagnosticCenter.record(
                "profile",
                "apply profileId=$profileId fromAi=$fromAi failed via module service" +
                    (result.err.take(3).joinToString(" ").take(160)).let { if (it.isBlank()) "" else " :: $it" }
            )
        }
        return result.isSuccess
    }

    /** الملف العام الحالي من ملف الوحدة، أو null عند تعذّر القراءة. */
    fun currentProfile(): String? = RootFileAccess.read(DAEMON_PROFILE_PATH)
}
