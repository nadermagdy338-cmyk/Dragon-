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

package nd.max

import android.app.Application
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import nd.max.core.diagnostics.DiagnosticCenter
import nd.max.core.hardware.ManualControlLocks
import nd.max.core.daemon.ModuleWatchman
import nd.max.core.hardware.SharedControlStorage
import nd.max.core.hardware.SharedHardwareOwnershipStore
import nd.max.core.maxai.MaxAiEngine
import nd.max.service.GamePanelService
import nd.max.ui.settings.AppLanguage

/**
 * نقطة إقلاع التطبيق: تُنشئ مكون Hilt وتُقلع بمحرك MAX AI.
 *
 * المحرك يبدأ مع العملية (مرة واحدة) حتى وهو مطفأ — فمحرك الأمان
 * يعمل دائمًا بصرف النظر عن حالة AI، ودورة الإيقاف لا تكتب شيئًا على
 * العتاد. تفعيل Max AI نفسه يظل قرار المستخدم وحده (افتراضيًا مطفأ).
 */
@HiltAndroidApp
class MaxManagerApplication : Application() {

    @Inject lateinit var maxAiEngine: MaxAiEngine

    override fun onCreate() {
        super.onCreate()
        // لغة التطبيق أولًا: AppCompat يطبّق اللغة ويجدّد النشاط عند الحاجة، وهذا الوقت هو الوحيد
        // الخالي من أي نشاط. بدون هذا السطر كان اختيار المستخدم يُنسى بعد كل قتل للعملية (settings_prefs).
        AppLanguage.applySaved(this)
        // والمستودع المشترك في **التخزين المحميّ بالجهاز** — النوع نفسه الذي يختاره الرفيق الجذريّ،
        // فلو اختلفا لصار **مستودعان لا يرى أحدهما الآخر**. وهذا هو عطب «تعمل مرّة ولا تعمل أخرى»
        // بعينه: كان الرفيق يبني مستودعه في تخزينٍ لا يُفتح قبل قفل الشاشة فيموت عند الإقلاع.
        // التفصيل والسجلّ المقيس في [`SharedControlStorage`].
        val controlFilesDir = SharedControlStorage.filesDir(this)
        // وفشل التهيئة **لا يُسقط التطبيق عند الإقلاع**: يُعلن باسمه في تشخيص التطبيق (فيراه
        // المستخدم في شاشة التشخيص) ويمضي محدودًا — والسقوط كان يعطي «التطبيق يتوقّف» بدل سبب.
        runCatching {
            SharedHardwareOwnershipStore.configure(controlFilesDir, applicationInfo.uid, android.os.Process.myPid())
            // Same directory as the journal: the UI writes locks here, the companion
            // process reads them, and both must agree on one durable lock set.
            ManualControlLocks.configure(controlFilesDir)
        }.onFailure {
            DiagnosticCenter.record(
                component = "control-plane",
                message = "shared control directory unavailable: ${it.message}",
                throwable = it,
            )
        }
        // **وصدفة الجذر تُسخَّن هنا لا في فتح شاشة.**
        //
        // libsu تُنشئ صدفتها **عند أوّل أمر** لا عند الإقلاع: فأوّل `Shell.cmd(...).exec()`
        // يدفع ثمن ولادة `su` وانتظار جاهزيتها (إلى مهلة المكتبة) — وإن وقع ذلك في مسار فتح
        // شاشة، **انتظر المستخدم شاشةً لا تُرسم**. والتهيئة المسبقة هنا **غير حاجبة**: الدالّة
        // بالاستدعاء الراجع تبني الصدفة على خيط داخليّ للمكتبة، والإقلاع لا ينتظرها — فتصادف
        // أوّلُ شاشةٍ تُفتح صدفةً جاهزةً بدل أن تدفع ثمنها.
        //
        // ولا يُغيَّر سلوك فاشل: إن رُفض الجذر أو غاب، يسقط الاستدعاء الراجع صامتًا ويبقى كل
        // مسار كما هو (كل نداء جذر بعدها يسأل الصدفة نفسها ويُعلن فشله في موضعه).
        Shell.getShell { }

        // **وحارس الوحدة يُشغَّل هنا أيضًا — لا في مستقبِل البثّ وحده.**
        //
        // والمستقبِل يُغطّي ما يملكه النظام (قيام، فتح، تحديث). وهذا يُغطّي ما يملكه الإنسان:
        // «فتحُ التطبيق». وهو مهمّ لأنّ المستخدم غالبًا **يفتح التطبيق** حين يلاحظ أن الوحدة لا
        // تعمل — ولو لم يكن هنا بابٌ للشفاء، لما وجد شيئًا يُصلح ما رآه.
        //
        // والثمن معلن ومقبول: قياسٌ واحد بصدفة **بلا جذر** (`pidof`) في كل قيام عملية، ولا نداء
        // جذر إلا إذا نفى القياس حياة أحدهما حقًّا ([`ModuleWatchman`] يشرح الطريقين). والخيط
        // منفصل ومنتظر لا يشارك الإقلاع، فلا يُبطئ أوّل شاشة.
        if (isUserUnlocked()) {
            Thread { runCatching { ModuleWatchman().watch() } }
                .apply { isDaemon = true }
                .start()
        }
        // **ولوحة مساحة الألعاب تُشغَّل هنا إن كان لها سبب.**
        //
        // سبب وجودها ليس لحظةَ فتح اللعبة من التطبيق بل **أيّ** لحظة تُفتح فيها: من اللوبي، أو
        // من مشغّل خارجي، أو بعد إعادة تشغيل الجهاز. والخيط لا يعرف تلك اللحظة إن لم تكن الخدمة
        // حيّةً أصلًا — فتُشغَّل عند قيام التطبيق متى كانت هناك لعبة مُفعَّلة واحدة على الأقل.
        // وإن لم تكن، لا يُشغَّل شيء: لا خدمة أمامية بلا سبب ولا إشعار دائم لم يُطلب
        // (`GamePanelService.ensureRunning` تشرح الحدّ والانتظار).
        if (isUserUnlocked()) {
            runCatching { GamePanelService.ensureRunning(this) }
        }
        maxAiEngine.start()
    }

    /**
     * هل تخزين المستخدم مفتوح؟ — وهذا شرط عمل الحارس لا شرط عمله وحده: كل مساراته تقرأ إعدادات
     * التطبيق، فقبل الفتح لا يُبنى قرار على قراءة ناقصة. وتخزين المستودع نفسه صار محميًّا بالجهاز
     * ([`SharedControlStorage`]) فلا يعتمد على هذا الشرط — وهذا هو الفرق بين إصلاح الجذر وحيطة المسار.
     */
    private fun isUserUnlocked(): Boolean = runCatching {
        getSystemService(android.os.UserManager::class.java)?.isUserUnlocked != false
    }.getOrDefault(false)
}
