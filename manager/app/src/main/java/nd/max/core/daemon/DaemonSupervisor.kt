/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */

package nd.max.core.daemon

import nd.max.MaxManagerPaths

/**
 * **وظيفة خلفية تُعيد الخادم الميّت.** ليست شاشة، وليست زرًّا يُضغط: خيط داخل الرفيق الـJava
 * (`nd.max.AppMonitor`) يفحص الخادم كل دقيقة ويعيد تشغيله — فيعود الملفّ العام والحاكم لكل تطبيق
 * و`--checkbypasschg` بلا إقلاع ولا تدخّل.
 *
 * **ولماذا هذا هو الموضع الصحيح (مقيسًا):** الخادم لا يُشغَّل إلا مرّة واحدة في الإقلاع —
 * `mainfiles/service.sh` ينتهي بـ`sleep 1 && exec "$BIN_SVC" --run` — **ولا مُعيد تشغيل له في
 * الشجرة كلها**. والرفيق هو الوحيد الذي يبقى حيّا بعده، والعلاقة بينهما أحاديّة الاتجاه اليوم:
 * الخادم **يراقب** قفل الرفيق (`java_lock_watcher_thread` في `StartupInit/DaemonStartup.c`) ويموت
 * إن مات؛ أمّا موت الخادم فلا يراه أحد. فهذه الوظيفة تُغلق الاتجاه الثاني.
 *
 * **وما لا تفعله بقصد (كل بند نهجٌ مقيس لا اختيار):**
 *
 * 1. **لا تقتل الرفيق:** `--rerun` ينتهي إلى
 *    `pkill -9 -f sys.maxmanager-appmonitoring` (`binutils/src/utils/mod.rs:255`) — أي أن الزرّ
 *    القديم كان يقتل الجهة التي تُصلحه. التشغيل هنا مباشر بـ`--run` وحده.
 * 2. **لا تمحو السجلّ:** `service.sh` يبدأ بـ`"$BIN_SVC" --clearlogs`، فكل إعادة تشغيل عبره تمحو
 *    دليل المحاولة السابقة. ومسارنا لا يمرّ به (ومُثبَّت في اختبار على نصّ الأمر).
 * 3. **لا تُخمّن:** «مجهول» لا يُعالج بإعادة تشغيل، ومخالفة هويّة حتميّة لا تُعالج بإعادة تشغيل،
 *    وعلامة `update`/`remove`/`disable`/`rom-native-mode` توقف النظر أصلًا.
 * 4. **لا تُلاحق بلا سقف:** ثلاث محاولات في الجلسة، **الأولى فور اكتشاف الموت** (لا معنى لتأجيلها)
 *    ثم تراجع متزايد (٣٠ ث · ٦٠ ث · ١٢٠ ث) — لأن كل تشغيل يُنشئ إشعارًا للمستخدم
 *    (`notify("Initializing…")`)، ثم يتوقّف ويسمّي السبب مرّة واحدة.
 */

/** قرار المشرف في دورة واحدة — خالص، فيُقاس بلا جهاز. */
sealed interface Decision {
    /** الخادم حيّ (ويُصفَّر عدّاد المحاولات). */
    data object Idle : Decision

    /** لا وقت للفعل الآن: مؤجَّل، أو الحكم مجهول. */
    data object Wait : Decision

    /** حان دور المحاولة رقم [attempt]. */
    data class Start(val attempt: Int) : Decision

    /** لا مزيد من المحاولات، والسبب [reason] مُسمًّى. */
    data class GiveUp(val reason: String) : Decision
}

/** ما يمنع إعادة التشغيل أصلًا — مُقاسًا من الجهاز، ويُقال نصًّا لا يُختصر إلى «فشل». */
sealed interface BlockReason {
    val reason: String

    /** الوحدة غير مثبّتة (أو الثنائية غير مقروءة): لا شيء يُشغَّل. */
    data class BinaryMissing(val path: String) : BlockReason {
        override val reason = "binary_missing path=$path"
    }

    /** الوحدة قيد تغيير أو معطَّلة أو في نمط ROM الأصلي: لا يُشغَّل خادم بالعناد. */
    data class ModuleChanging(val markers: List<String>) : BlockReason {
        override val reason = "module_marker=${markers.joinToString(",")}"
    }

    /** `module.prop` غير مقروء — وهو بنفسه سبب موت الخادم (`fopen == NULL` عند الحارس). */
    data class PropUnreadable(val path: String) : BlockReason {
        override val reason = "module_prop_unreadable path=$path"
    }

    /** هويّة مخالفة: `module.prop` ليس ما يطلبه الخادم — إعادة التشغيل لا تُصلحه. */
    data class IdentityViolated(
        val identity: ModulePropIdentity,
        val daemonVersion: String?,
        val versionMismatch: Boolean,
    ) : BlockReason {
        override val reason = buildString {
            append("module_identity_conflict")
            append(" name=").append(identity.name ?: "missing")
            append(" author=").append(identity.author ?: "missing")
            append(" requires=name=")
            append(DaemonIdentity.EXPECTED_NAME)
            append(",author=")
            append(DaemonIdentity.EXPECTED_AUTHOR)
            if (versionMismatch) {
                append(" version=").append(identity.version ?: "missing")
                append(" daemon=").append(daemonVersion ?: "unknown")
            }
        }
    }
}

/**
 * القواعد الثابتة للمشرف، منفصلة عن التنفيذ — فتُقرأ وتُقاس في موضع واحد.
 */
object DaemonSupervisorPolicy {

    /** دورة الفحص: أسرع من هذا = قذف أوامر صدفة بلا مقابل؛ أبطأ = دقيقة فأكثر بلا خادم. */
    const val CHECK_INTERVAL_MS = 60_000L

    /** مهلة تأكيد الإقلاع — أوسع من نافذة العشر ثوانٍ في الواجهة، لأن الإقلاع يقرأ ملفّات ويمسح سجلًّا. */
    const val CONFIRM_TIMEOUT_MS = 25_000L
    const val CONFIRM_POLL_MS = 500L

    /** سقف المحاولات في الجلسة الواحدة — كل تشغيل يُنشئ إشعارًا للمستخدم. */
    const val MAX_ATTEMPTS = 3

    /**
     * التراجع **بعد** كل محاولة: الأولى فورية (فالموت مكتشَف للتوّ، والتأجيل بلا مقابل)، ثم تتباعد
     * المحاولات. ولذلك قيمة عمليّة: العطب العابر (قراءة ممزّقة لـ`module.prop` لحظة كتابته) يُحلّ
     * بأسرع ممّا تستغرقه المحاولة الأولى نفسها — فيُصلحه التدوير عادةً في المحاولة التالية.
     */
    private val BACKOFF_MS = longArrayOf(30_000L, 60_000L, 120_000L)

    fun backoffAfter(attempt: Int): Long = BACKOFF_MS[(attempt - 1).coerceIn(0, BACKOFF_MS.lastIndex)]

    fun decide(
        attempts: Int,
        nextAttemptAtMs: Long,
        nowMs: Long,
        liveness: DaemonLiveness,
        blocked: BlockReason?,
    ): Decision = when {
        liveness == DaemonLiveness.Alive -> Decision.Idle
        liveness == DaemonLiveness.Unknown -> Decision.Wait
        blocked != null -> Decision.GiveUp(blocked.reason)
        attempts >= MAX_ATTEMPTS -> Decision.GiveUp("attempts_exhausted attempts=$attempts")
        nowMs < nextAttemptAtMs -> Decision.Wait
        else -> Decision.Start(attempts + 1)
    }
}

/** نتيجة محاولة تشغيل واحدة. */
data class StartOutcome(
    /** قبلت الصدفة الأمر — **وليست** إثباتًا أن الخادم قام. */
    val accepted: Boolean,
    /** أقيم فعلًا (تأكيد بقياس مستقلّ داخل المهلة). */
    val alive: Boolean,
    /** سبب الخروج **الذي كتبه هذه المحاولة** (لا واحد قديم من ذيل السجلّ). */
    val exit: DaemonExit?,
    val waitedMs: Long,
)

/**
 * تشغيل واحد مُثبَّت: أمر مفصول، ثم سؤال متكرّر حتى [DaemonSupervisorPolicy.CONFIRM_TIMEOUT_MS]،
 * ثم قراءة **الأسطر الجديدة** من السجلّ لسبب الخروج.
 *
 * وقراءة الأسطر الجديدة (لا الذيل كاملًا) تُغلق عطبًا حقيقيًّا: ذيل السجلّ يحمل سبب الموت السابق،
 * فنسبته إلى محاولةٍ لم تكتب حرفًا تُنتج «سببًا» لا وجود له. و[DaemonLog.newLines] تعيد تفريغًا
 * إن مُسح السجلّ أو دُوِّر (فلا يُنسب نصّ قديم إلى محاولة جديدة).
 */
class DaemonStarter(
    private val io: DaemonIo = RootDaemonIo,
    private val serviceBin: String = MaxManagerPaths.SERVICE_BIN,
    private val logPath: String = MaxManagerPaths.MAXMANAGER_LOG,
) {

    fun startAndConfirm(): StartOutcome {
        val probe = DaemonLivenessProbe(io)
        val before = io.shell(DaemonCommands.logTail(logPath, LOG_TAIL_LINES)).stdout

        val started = io.shell(DaemonCommands.start(serviceBin, logPath))
        val accepted = started.exit == 0

        var waited = 0L
        var alive = false
        while (waited < DaemonSupervisorPolicy.CONFIRM_TIMEOUT_MS) {
            io.sleep(DaemonSupervisorPolicy.CONFIRM_POLL_MS)
            waited += DaemonSupervisorPolicy.CONFIRM_POLL_MS
            if (probe.read() == DaemonLiveness.Alive) {
                alive = true
                break
            }
        }

        val exit = if (alive) {
            null
        } else {
            val after = io.shell(DaemonCommands.logTail(logPath, LOG_TAIL_LINES)).stdout
            DaemonLog.lastExitOf(DaemonLog.newLines(before, after))
        }

        return StartOutcome(accepted, alive, exit, waited)
    }

    private companion object {
        const val LOG_TAIL_LINES = 80
    }
}

/**
 * الوظيفة الخلفية نفسها: دورة فحص دائمة على خيطها الخاص.
 *
 * ولا يُلمس شيء من طبقة الواجهة (`ui/…`) من هنا — لا أمر ولا كتابة — فالنقطة العامّة الوحيدة
 * هي [tick]، وهي التي تُقاس في اختبارات الوحدات بمُزيف.
 */
class DaemonSupervisor(
    private val io: DaemonIo = RootDaemonIo,
    private val serviceBin: String = MaxManagerPaths.SERVICE_BIN,
    private val logPath: String = MaxManagerPaths.MAXMANAGER_LOG,
    private val intervalMs: Long = DaemonSupervisorPolicy.CHECK_INTERVAL_MS,
    /** سطر خَبَر (نجاح/إقلاع). */
    private val info: (String) -> Unit = {},
    /** سطر يحتاج انتباهًا (فشل/تخلٍّ عن المحاولة) — بنصّ السبب الذي سمّاه الخادم أو الجهاز. */
    private val problem: (String) -> Unit = {},
) {

    private val probe = DaemonLivenessProbe(io)
    private var attempts = 0
    private var nextAttemptAtMs = 0L
    private var reportedGiveUp: String? = null

    /** الحلقة الدائمة. تُستدعى على خيط مخصّص (‏`Thread(...).apply { isDaemon = true }`). */
    fun run() {
        while (!Thread.currentThread().isInterrupted) {
            runCatching { tick() }
                .onFailure { problem("EVENT=DAEMON_SUPERVISOR_TICK_FAILED error=${it.javaClass.simpleName}") }
            val slept = runCatching { io.sleep(intervalMs) }.isSuccess
            if (!slept) return
        }
    }

    /** دورة واحدة كاملة — وهي **نقطة القياس** (الحدّ بين ما يُقاس هنا وما يحتاج جهازًا). */
    fun tick(): Decision {
        val now = io.uptimeMs()
        val liveness = probe.read()

        if (liveness == DaemonLiveness.Alive) {
            if (attempts > 0) {
                info("EVENT=DAEMON_SUPERVISOR_RECOVERED attempts=$attempts")
            }
            attempts = 0
            nextAttemptAtMs = 0L
            reportedGiveUp = null
            return Decision.Idle
        }

        val decision = DaemonSupervisorPolicy.decide(attempts, nextAttemptAtMs, now, liveness, blockReason())

        when (decision) {
            Decision.Idle, Decision.Wait -> Unit

            is Decision.Start -> {
                attempts = decision.attempt
                val outcome = DaemonStarter(io, serviceBin, logPath).startAndConfirm()
                nextAttemptAtMs = now + DaemonSupervisorPolicy.backoffAfter(decision.attempt)
                if (outcome.alive) {
                    info(
                        "EVENT=DAEMON_SUPERVISOR_STARTED attempt=${decision.attempt} " +
                            "waited_ms=${outcome.waitedMs}"
                    )
                } else {
                    problem(
                        "EVENT=DAEMON_SUPERVISOR_START_FAILED attempt=${decision.attempt} " +
                            "accepted=${outcome.accepted} exit=${outcome.exit?.code ?: "no_new_line"} " +
                            "waited_ms=${outcome.waitedMs}"
                    )
                }
            }

            is Decision.GiveUp -> {
                // مرّة واحدة لكل سبب: الدورة تعمل كل دقيقة، وتكرار السطر نفسه ضجيج يُخفي غيره.
                if (reportedGiveUp != decision.reason) {
                    reportedGiveUp = decision.reason
                    problem("EVENT=DAEMON_SUPERVISOR_GAVE_UP reason=${decision.reason}")
                }
            }
        }

        return decision
    }

    /**
     * لماذا لا يُعاد التشغيل: الوحدة قيد تغيير، أو الهوية مخالفة، أو الثنائية غائبة.
     *
     * وهذا هو الفرق بين «أعِد المحاولة» و«لا تُعِدها» — والفرق كله في **قراءة الملفّ الآن** لا في
     * تخمين ما جرى: هويّة سليمة + خروجٌ بهويّة في السجلّ = قراءة ممزّقة عابرة (فالمحاولة تفيد)،
     * وهويّة مخالفة = الملفّ نفسه هو العطب (فالمحاولة لا تفيد، ويُسمّى الفرق نصًّا).
     */
    fun blockReason(): BlockReason? {
        if (!io.exists(serviceBin)) return BlockReason.BinaryMissing(serviceBin)

        val markers = MODULE_MARKERS.filter { io.exists("${MaxManagerPaths.MODULE_DIR}/$it") }
        if (markers.isNotEmpty()) return BlockReason.ModuleChanging(markers)

        val propPath = MaxManagerPaths.MODULE_PROP
        val propText = io.readText(propPath) ?: return BlockReason.PropUnreadable(propPath)

        // إصدار الخادم من فمه لا من نسخة ثانية في Kotlin: `--version` يطبع `MODULE_VERSION` من
        // `archdaemon/jni/include/MaxManager.h`، وهو **قبل** بوّابة التوفّر فيعمل والخادم متوقّف.
        val daemonVersion = io.shell(DaemonCommands.version(serviceBin))
            .stdout.lineSequence().firstOrNull { it.isNotBlank() }?.trim()

        val verdict = DaemonIdentity.verdict(propText, daemonVersion)
        return if (verdict is IdentityVerdict.Violated) {
            BlockReason.IdentityViolated(
                identity = verdict.identity,
                daemonVersion = daemonVersion,
                versionMismatch = verdict.versionMismatch,
            )
        } else {
            null
        }
    }

    private companion object {
        /**
         * علامات تُوقف التشغيل. والأربعة **ليست رأيًا واحدًا**:
         * `update` و`remove` يراقبهما الخادم نفسه (`InotifyWatcher.c`) ويخرج عندهما عمدًا،
         * و`disable` تعني وحدة معطَّلة (فلا خادم لها)،
         * و`rom-native-mode` يعني أن الوحدة تعمل بنمط ROM أصلي — و`mainfiles/service.sh` **لا يشغّل**
         * الخادم حين يوجد (`if [ ! -f "$MODDIR/rom-native-mode" ]`)، فإعادة تشغيله هنا عنادٌ لا إصلاح.
         */
        val MODULE_MARKERS = listOf("update", "remove", "disable", "rom-native-mode")
    }
}
