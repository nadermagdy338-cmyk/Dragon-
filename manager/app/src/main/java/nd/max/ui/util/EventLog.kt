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

import com.topjohnwu.superuser.Shell
import nd.max.MaxManagerPaths
import nd.max.MaxManagerProps

/**
 * Records "what the user chose, on which screen, and when" -- as opposed to
 * everything else in the logging system (AppMonitorLogger.kt on the Kotlin
 * companion side, log_zenith()/log_preload() on the native daemon side),
 * which only records the *engine's* reaction (apply/revert/profile-switch).
 * Before this file existed there was no record of the decision that caused
 * an engine action, only the action itself -- so a bad outcome could be
 * traced to "the daemon tried to apply X" but never to "the user turned on
 * X for this app, in this screen, at this time."
 *
 * This runs in the main app (UI) process, not the rooted AppMonitor
 * companion daemon, so unlike AppMonitorLogger.kt it can't just
 * Runtime.exec() directly -- it goes through libsu's Shell (the same
 * mechanism every other root operation in this app already uses, e.g.
 * LogUtil.kt's dumpDiagnosticLogs()) to reach the same
 * `sys.maxmanager-service --log <TAG> <LEVEL> <MSG>` CLI hook that
 * AppMonitorLogger forwards through, so every source -- UI, companion
 * daemon, native daemon -- ends up in the same timestamped MaxManager.log.
 *
 * Calls are fire-and-forget (Shell.cmd(...).submit()): logging a settings
 * change must never add latency or a failure path to the UI interaction
 * that triggered it.
 *
 * Gated behind [MaxManagerProps.Conf.DETAILED_LOG], off by default (see the
 * Settings screen's "Detailed activity log" toggle / SettingsViewModel).
 * Checked via [PropertyUtils] -- a plain reflective getprop read, not a
 * shell spawn -- so the disabled (default) state costs nothing extra per
 * interaction beyond that one cheap check.
 */
object EventLog {
    private const val TAG = "ui"
    private const val LEVEL_INFO = 1

    private fun isEnabled(): Boolean = PropertyUtils.get(MaxManagerProps.Conf.DETAILED_LOG) == "1"

    /**
     * Records a single setting change.
     *
     * @param screen Short screen/section identifier, e.g. "AppSettings",
     *   "GlobalTweaks", "DebloatFreeze". Keep these stable -- they're what
     *   LogsViewerScreen's future source filter will group by.
     * @param field The setting's key, e.g. "gpu_profile", "cpu_governor".
     *   Use the same key the config JSON / prop uses where one exists, so
     *   this line and the resulting APPLY_FAILED/PROFILE_APPLY lines are
     *   trivially greppable together.
     * @param old Value before the change. Use "default"/"off"/etc. rather
     *   than an empty string so the line stays self-explanatory.
     * @param new Value after the change.
     * @param pkg Optional package name, when the action is scoped to one
     *   app (per-app config screens). Omitted for global settings.
     */
    fun userAction(screen: String, field: String, old: String, new: String, pkg: String? = null) {
        val pkgPart = if (pkg != null) " pkg=${shellSafe(pkg)}" else ""
        val message = "EVENT=USER_ACTION screen=${shellSafe(screen)} field=${shellSafe(field)} " +
            "old=${shellSafe(old)} new=${shellSafe(new)}$pkgPart"
        forward(message)
    }

    /**
     * Records a discrete user-triggered action that isn't a value change --
     * a button tap like "kill process", "freeze app", "flash kernel module".
     *
     * @param screen Short screen identifier, same convention as userAction().
     * @param action What was triggered, e.g. "kill_process", "freeze",
     *   "flash_module".
     * @param target Optional subject of the action (package name, process
     *   name, module id, etc.).
     */
    fun userTriggered(screen: String, action: String, target: String? = null) {
        val targetPart = if (target != null) " target=${shellSafe(target)}" else ""
        val message = "EVENT=USER_TRIGGERED screen=${shellSafe(screen)} action=${shellSafe(action)}$targetPart"
        forward(message)
    }

    /**
     * Records a handled application error. This is intentionally separate from
     * [userTriggered] so diagnostic logs can distinguish a failed operation
     * from a normal user action. The throwable message is sanitized and capped
     * because this log is persistent on the device.
     */
    fun error(screen: String, operation: String, throwable: Throwable? = null) {
        val detail = throwable?.message
            ?.replace("\n", " ")
            ?.replace("\r", " ")
            ?.take(240)
            ?.let { " detail=${shellSafe(it)}" }
            ?: ""
        forward(
            "EVENT=UI_ERROR screen=${shellSafe(screen)} " +
                "operation=${shellSafe(operation)}$detail"
        )
    }

    /**
     * `AR-13` — يسجّل **نتيجة عملية** بزمنها المقيس فعلًا، لا نيّتها.
     *
     * هذا هو الفرق الذي يمنع «تحسينات بلا أثر معلَن»: قبل هذا كان `cmd package compile`
     * يُنادى ويُعاد `Boolean` ولا يبقى منه سطر واحد — فيصير نجاحه وفشله ومدّته مجهولة.
     *
     * @param success نتيجة العملية كما أعادها المنفّذ (لا كالمتوقَّع).
     * @param durationMs زمن مقيس بـ[android.os.SystemClock.elapsedRealtime] — **لا تقدير**.
     */
    fun result(
        screen: String,
        action: String,
        target: String?,
        success: Boolean,
        durationMs: Long,
    ) {
        forward(resultMessage(screen, action, target, success, durationMs))
    }

    /**
     * بناء سطر النتيجة بمعزل عن الإرسال — قابل للاختبار بلا أي أثر جانبي.
     * `internal` لأن العقد يخصّ الوحدة واختباراتها فقط.
     */
    internal fun resultMessage(
        screen: String,
        action: String,
        target: String?,
        success: Boolean,
        durationMs: Long,
    ): String {
        val targetPart = if (target != null) " target=${shellSafe(target)}" else ""
        return "EVENT=OP_RESULT screen=${shellSafe(screen)} action=${shellSafe(action)}" +
            "$targetPart ok=$success duration_ms=$durationMs"
    }

    /**
     * `AR-32` — يسجّل **عرضًا مقيسًا** (لا إجراء مستخدم ولا خطأ مُعالَج).
     *
     * الفرق مقصود: `userTriggered` = «المستخدم فعل»، و`error` = «التقطنا استثناءً»،
     * وهذا = «الجهاز/التطبيق أساء التصرّف أمامنا وقسناه». خلطه بأي منهما يجعل السجل
     * يخبرك أن المستخدم ضغط زرًّا لم يضغطه.
     *
     * @param valueMs القيمة المقيسة (مثلًا مدّة انسداد).
     * @param worstMs أسوأ قيمة مقيسة حتى الآن، إن وُجدت.
     * @param count كم مرّة وقع العرض حتى الآن، إن عُرف.
     */
    fun symptom(
        screen: String,
        symptom: String,
        valueMs: Long,
        worstMs: Long? = null,
        count: Long? = null,
    ) {
        forward(symptomMessage(screen, symptom, valueMs, worstMs, count))
    }

    /** بناء سطر العرض بمعزل عن الإرسال — قابل للاختبار بلا أي أثر جانبي. */
    internal fun symptomMessage(
        screen: String,
        symptom: String,
        valueMs: Long,
        worstMs: Long? = null,
        count: Long? = null,
    ): String {
        val worstPart = if (worstMs != null) " worst_ms=$worstMs" else ""
        val countPart = if (count != null) " count=$count" else ""
        return "EVENT=SYMPTOM screen=${shellSafe(screen)} symptom=${shellSafe(symptom)}" +
            " value_ms=$valueMs$worstPart$countPart"
    }

    /**
     * `PEER-8` + `AR-31` — نتيجة **كتابة في عقدة عتاد، مقروءة بعد الكتابة**.
     *
     * ولماذا حدث منفصل: `OP_RESULT` يحمل نجاحًا/فشلًا وزمنًا، ولا يحمل **القيمة التي وجدناها
     * فعلًا**. والفرق بين «أمر الكتابة نجح» و«القيمة استقرّت» هو ما يكشف أن الجهاز يقيّد قيمة
     * (تدوير/حدّ/حاكم) — أو أن المسار غير قابل للكتابة أصلًا.
     *
     * @param verdict واحدة من كلمات [nd.max.core.hardware.WriteVerification.verdictWord].
     */
    fun writeCheck(path: String, wrote: String, readBack: String?, verdict: String) {
        forward(writeCheckMessage(path, wrote, readBack, verdict))
    }

    /** بناء سطر التحقّق بمعزل عن الإرسال — قابل للاختبار بلا أي أثر جانبي. */
    internal fun writeCheckMessage(
        path: String,
        wrote: String,
        readBack: String?,
        verdict: String,
    ): String {
        val readPart = if (readBack != null) " read=${shellSafe(singleLine(readBack))}" else " read=?"
        return "EVENT=WRITE_CHECK path=${shellSafe(path)} wrote=${shellSafe(singleLine(wrote))}" +
            "$readPart verdict=${shellSafe(verdict)}"
    }

    /**
     * يوحّد الفراغات ويُزيل الأطراف: عقدة sysfs تُعاد بسطر جديد، وسطر السجل يجب أن يبقى
     * **حقلًا واحدًا قابلًا للبحث** لا فراغين متتاليين. ولا يُغيّر الدلالة: القيمة تبقى كما هي.
     */
    internal fun singleLine(value: String): String =
        value.replace(Regex("\\s+"), " ").trim()

    /**
     * يكتب **أسطرًا جاهزة** إلى السجل كما هي — ولا يبنيها هنا.
     *
     * والفرق بينها وبين بقية الدوال مقصود ومحدود: هذه لا تُنتج حدثًا عن المستخدم ولا عن خطأ،
     * بل تُودع نصًّا بُني في موضع آخر **خالصًا ومُختبَرًا** ([nd.max.core.diagnostics.LogHeader]):
     * ترويسة الجلسة، ودليل القراءة، وقاموس الرموز. ولماذا هنا لا في الشاشة: الشاشة تُفرغ السجل
     * فيلزم إعادة كتابة الترويسة بعده، وإلا صار الملف بلا جهاز ولا إعداد ولا معنى للرموز — وهو
     * بالضبط ما يجعل تشخيصًا من الملف وحده مستحيلًا.
     *
     * ولا تُقبل هنا جملة حرّة: كل سطر يجب أن يكون حدثًا (`EVENT=`) لأن الملف يُقرأ آليًّا وفكّه
     * يعتمد على هذا الشكل.
     */
    fun header(lines: List<String>) {
        lines.filter { it.startsWith("EVENT=") }.forEach(::forward)
    }

    private fun forward(message: String) {
        if (!isEnabled()) return
        try {
            Shell.cmd("'${MaxManagerPaths.SERVICE_BIN}' --log '$TAG' '$LEVEL_INFO' '$message'").submit()
        } catch (_: Exception) {
            // Never let logging failure surface to the UI interaction that
            // triggered it -- worst case this one line is missing from
            // MaxManager.log, which is still strictly better than crashing
            // or blocking a settings toggle over a log write.
        }
    }

    /**
     * Values here are generally safe (enum-like settings values, package
     * names), but this is defense in depth against the shell command built
     * above ever seeing a stray single quote.
     */
    internal fun shellSafe(value: String): String =
        value.replace("\n", " ").replace("\r", "").replace("'", "'\\''")
}
