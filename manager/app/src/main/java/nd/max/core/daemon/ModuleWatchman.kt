/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */

package nd.max.core.daemon

import nd.max.MaxManagerPaths

/**
 * **حارسٌ لا يموت مع الذي يحرسه.**
 *
 * **والعطب الذي وُلد هذا الملفّ له (مقيس من سجلّ جهاز حقيقي، ٢٠٢٦-١٠-٠١):** الوحدة كلها معلَّقة على
 * عمليّة واحدة — الرفيق الـJava (`nd.max.AppMonitor`) — لأنّ قفل `java.lock` الذي يحمله هو الإشارة
 * الوحيدة لحياة الوحدة:
 *
 * ```
 * 17:36:33.595  AndroidRuntime  IllegalStateException: cannot-create-shared-control-directory
 * 17:36:33.698  MaxManager      EVENT=JAVA_COMPANION_WAIT_START max_checks=120
 * 17:38:35.291  MaxManager      EVENT=JAVA_COMPANION_TIMEOUT checks=120 action=exit
 * ```
 *
 * وموته لا يُصلحه أحد: [`DaemonSupervisor`] — وهو المُصلِح الوحيد في الشجرة — يعيش **داخله**
 * (`AppMonitor.kt`)، و`mainfiles/service.sh` يعمل مرّة واحدة في الإقلاع، والتطبيق لا يشغّل مشرفًا ولا
 * يبدأ الرفيق. فبقيت الوحدة ميتة حتى الإقلاع التالي، وأمام المستخدم إشعار «Java companion daemon
 * crashed or failed to start» **بلا سبب يُقرأ في أيّ ملفّ**.
 *
 * **وما يفعله:** عند قيام النظام (`BOOT_COMPLETED` · `USER_UNLOCKED` · `MY_PACKAGE_REPLACED`) وعند
 * فتح التطبيق، يقيس الحالتين **قياسًا مباشرًا** (لا من حالة محفوظة)، ثم يُوفِّق: الرفيق أوّلًا ثم
 * الخادم — لأنّ الخادم ينتظر قفل الرفيق، فتشغيله وحده انتظار ١٢٠ ثانية مضمون.
 *
 * ## وطريقان لا طريق: رخيص ثم مُثبِت
 *
 * * **الرخيص (بلا جذر):** `pidof`/`ps` لا يحتاجان جذرًا، وهذا ما يقيسه التطبيق أوّلًا في كل قيام.
 *   فإن كان **الاثنان حيّين** انتهى النداء بلا أيّ نداء `su`.
 * * **المُثبِت (بالجذر):** إن نفى الرخيص حياة أحدهما، يُعاد القياس **بجذر**، ثم الفعل. ولا يُبنى
 *   فعل على نفيه لأنّ صدفة بلا جذر قد لا ترى عمليّة تعمل بجذر (‏`/proc` مقيّد) — فيكون «لم أجده»
 *   **مجهولًا** حتى يُسأل من يرى (ADR-07: لا فعل على «مجهول»). وهذا هو سبب وجود الطريقين: الرخص
 *   في الحالة السليمة (وهي الغالبة)، والصواب في حالة العطب.
 *
 * ## محاذير مقيَّدة بقصد (كلّها من الشجرة لا من الرأي)
 *
 * 1. **لا `--rerun` ولا `--clearlogs`** — الأول يمرّ بـ`restartservice` فيقتل الرفيق
 *    (`binutils/src/utils/plan.rs:49`) ثم `service.sh` الذي يمحو سجلّ الخادم في كل محاولة.
 * 2. **لا قتل لشيء:** إن كان الرفيق حيًّا لا يُشغَّل ثانٍ (الثاني يفقد القفل ويخرج بحكم `tryLock`).
 * 3. **وحدود الوحدة تُحترم:** غياب الثنائيّة أو علامة `update`/`remove`/`disable`/`rom-native-mode`
 *    توقف النظر أصلًا ([`DaemonSupervisor.blockReason`] — نفس المصدر، فلا نسخة ثانية تتفرّق).
 * 4. **وسقفٌ لكل نداء:** محاولة واحدة — لا حلقة ولا انتظار يجمّد خيطًا.
 */
enum class CompanionLiveness { Alive, Dead, Unknown }

/** قرار الحارس في نداء واحد — خالص، فيُقاس بمُزيف بلا جهاز. */
sealed interface WatchDecision {
    /** الوحدة سليمة (أو لا وحدة مُثبَّتة): لا فعل. */
    data object Idle : WatchDecision

    /** الخادم ميّت والرفيق حيّ: يُشغَّل الخادم وحده. */
    data object StartDaemon : WatchDecision

    /** الرفيق غائب والخادم حيّ: يُشغَّل الرفيق وحده، ويُعاد القياس بعده. */
    data object StartCompanion : WatchDecision

    /** الاثنان غائبان: الرفيق أوّلًا (بحدّ انتظار معلن)، ثم الخادم. */
    data object StartCompanionThenDaemon : WatchDecision

    /** قياس غير حاسم: لا فعل، والقرار يعود في النداء التالي. */
    data object Wait : WatchDecision

    /** لا يُعالَج، والسبب مُسمًّى نصًّا. */
    data class GiveUp(val reason: String) : WatchDecision
}

/** قواعد الحارس الثابتة — منفصلة عن التنفيذ، فتُقرأ وتُقاس في موضع واحد. */
object ModuleWatchmanPolicy {

    /**
     * انتظار ظهور الرفيق بعد تشغيله.
     *
     * ولماذا ١٥ ثانية: قياس الجهاز الحقيقي يقول إنّ JVM يستغرق ≈`0.5` ثانية حتى `callMain`
     * (`17:36:33.045 START` ← `17:36:33.536 callMain: return`)، والمهلة هنا **أوسع من اللازم بقصد**
     * لأنها ليست مسار نجاح: الثقيل (تهيئة Dex ورسم الخرائط) قد يبلغ ثوانيَ على جهاز بطيء.
     */
    const val WAIT_MS = 15_000L
    const val POLL_MS = 1_000L

    fun decide(
        daemon: DaemonLiveness,
        companion: CompanionLiveness,
        blocked: BlockReason?,
    ): WatchDecision = when {
        blocked != null -> WatchDecision.GiveUp(blocked.reason)
        companion == CompanionLiveness.Unknown || daemon == DaemonLiveness.Unknown -> WatchDecision.Wait
        companion == CompanionLiveness.Dead && daemon == DaemonLiveness.Alive -> WatchDecision.StartCompanion
        companion == CompanionLiveness.Dead -> WatchDecision.StartCompanionThenDaemon
        daemon == DaemonLiveness.Dead -> WatchDecision.StartDaemon
        else -> WatchDecision.Idle
    }
}

/**
 * نصوص أوامر الرفيق في موضع واحد — فتُقاس في اختبار، **ويُقاس عليها `mainfiles/service.sh`** (‏حارس
 * عقده في `ModuleWatchmanContractTest`) فلا يتفرّق الأمران: أمر الإقلاع وأمر الشفاء.
 */
object CompanionCommands {

    /** الاسم الذي يضعه `--nice-name` في `service.sh` نفسه — وهو ما يُقاس به الحضور. */
    const val NICE_NAME = "sys.maxmanager-appmonitoring"

    const val MAIN_CLASS = "nd.max.AppMonitor"

    /** القياس الأوّل — نفس ما يقيسه `service.sh` (`toybox pidof` بالاسم الكامل). */
    const val PROBE = "pidof $NICE_NAME"

    /**
     * القياس الثاني — لأنّ اسم العمليّة يتجاوز `TASK_COMM_LEN` فيُقتطع في `/proc/<pid>/stat`،
     * فلا يُترك الحكم على أداة واحدة (القوسان حول `s` يمنعان مطابقة سطر `grep` نفسه).
     */
    const val PROBE_CONFIRM = "ps -A 2>/dev/null | grep -c '[s]ys.maxmanager-appmonitoring'"

    /**
     * التشغيل: نفس وسائط `service.sh` بالترتيب نفسه (الفئة، ثم ثلاث مسارات: الحالة، التطبيقات
     * الخلفيّة، قفل الرفيق)، ومخرَجه **يُضاف** إلى `sysmon.log` لا يُقتطع (فلا يُمحى دليل فشل سابق
     * بإعادة تشغيل — وهو العطب نفسه الذي يمنعه `DaemonSupervisor`).
     */
    fun launch(apkPath: String = MaxManagerPaths.MODULE_APK): String {
        val config = MaxManagerPaths.MODULE_CONFIG
        return "nohup app_process -Djava.class.path='$apkPath' / --nice-name=$NICE_NAME $MAIN_CLASS " +
            "'$config/app_status' '$config/background_apps' '$config/java.lock' " +
            ">> '$config/sysmon.log' 2>&1 </dev/null &"
    }

    /** وجود الرفيق: `pidof` ثم تأكيد `ps` — والحكم في [`CompanionProbe`] (خالص). */
    fun liveness(
        primaryExit: Int,
        primaryStdout: String,
        primaryStderr: String,
        confirmCount: Int?,
    ): CompanionLiveness = CompanionProbe.liveness(primaryExit, primaryStdout, primaryStderr, confirmCount)
}

/** حكم خالص على مخرَج القياسين — بنفس دلالات `pidof` الحقيقية لا بما يُتوقَّع منها. */
object CompanionProbe {

    fun pid(output: String): Int? = Regex("\\d+").find(output)?.value?.toIntOrNull()

    fun liveness(
        primaryExit: Int,
        primaryStdout: String,
        primaryStderr: String,
        confirmCount: Int?,
    ): CompanionLiveness = when {
        pid(primaryStdout) != null -> CompanionLiveness.Alive
        (confirmCount ?: 0) >= 1 -> CompanionLiveness.Alive
        primaryExit < 0 -> CompanionLiveness.Unknown
        primaryExit == 127 || primaryStderr.contains("not found", ignoreCase = true) -> CompanionLiveness.Unknown
        primaryExit == 1 && primaryStdout.isBlank() -> CompanionLiveness.Dead
        else -> CompanionLiveness.Unknown
    }
}

/** ما يُنفَّذ فعلًا — خلف الواجهتين القائمتين، فلا مسار ثانٍ للصدفة ولا للخادم. */
class ModuleWatchman(
    /** الطريق الرخيص: بلا جذر (‏`pidof`/`ps` لا يحتاجانه). */
    private val cheapIo: DaemonIo = RootDaemonIo,
    /** الطريق المُثبِت: بالجذر — قياسًا وفعلًا. */
    private val rootIo: DaemonIo = RootShellDaemonIo,
    private val serviceBin: String = MaxManagerPaths.SERVICE_BIN,
    private val logPath: String = MaxManagerPaths.MAXMANAGER_LOG,
    private val info: (String) -> Unit = {},
    private val problem: (String) -> Unit = {},
) {

    private val cheapDaemonProbe = DaemonLivenessProbe(cheapIo)
    private val rootDaemonProbe = DaemonLivenessProbe(rootIo)

    /** نداء واحد كامل: قياس، ثم قرار، ثم فعل واحد — بلا حلقة ولا انتظار يجمّد الخيط. */
    fun watch(): WatchDecision {
        // ١) الرخيص: إن كان الاثنان حيّين فلا نداء جذر واحد في هذا القيام.
        if (cheapDaemonProbe.read() == DaemonLiveness.Alive &&
            companionLiveness(cheapIo) == CompanionLiveness.Alive
        ) {
            return WatchDecision.Idle
        }

        // ٢) المُثبِت: القياس نفسه بجذر، لأنّ «لم أجده» من صدفة لا ترى كل عمليّة **مجهول** لا «ميّت».
        val daemon = rootDaemonProbe.read()
        val companion = companionLiveness(rootIo)
        val blocked = blockReason()
        val decision = ModuleWatchmanPolicy.decide(daemon, companion, blocked)
        when (decision) {
            WatchDecision.Idle, WatchDecision.Wait -> Unit

            WatchDecision.StartDaemon -> {
                val outcome = DaemonStarter(io = rootIo, serviceBin = serviceBin, logPath = logPath).startAndConfirm()
                report(outcome.alive, "daemon", outcome.accepted, outcome.exit?.code)
            }

            WatchDecision.StartCompanion -> {
                if (startCompanion()) info("EVENT=WATCHMAN_COMPANION_STARTED stage=alone")
            }

            WatchDecision.StartCompanionThenDaemon -> {
                if (startCompanion()) {
                    info("EVENT=WATCHMAN_COMPANION_STARTED stage=before_daemon")
                    val outcome = DaemonStarter(io = rootIo, serviceBin = serviceBin, logPath = logPath).startAndConfirm()
                    report(outcome.alive, "daemon_after_companion", outcome.accepted, outcome.exit?.code)
                }
            }

            is WatchDecision.GiveUp -> problem("EVENT=WATCHMAN_GAVE_UP reason=${decision.reason}")
        }
        return decision
    }

    /** حضوره يُقاس بالاسم — لا بقفل ولا بحالة محفوظة. */
    fun companionLiveness(io: DaemonIo = cheapIo): CompanionLiveness {
        val primary = io.shell(CompanionCommands.PROBE)
        when (val verdict = CompanionCommands.liveness(primary.exit, primary.stdout, primary.stderr, null)) {
            CompanionLiveness.Alive -> return verdict
            CompanionLiveness.Unknown -> return verdict
            CompanionLiveness.Dead -> Unit
        }
        // والتأكيد يُجرَّب مرّة واحدة: `ps` ليس متاحا في كل ROM (وقد يكون `toybox ps` بسِماات
        // لا تعرف `-A`) — وغيابه لا يُقلب الجواب، بل يبقى حكم `pidof` وحده (وهو مسار الإقلاع
        // نفسه في `service.sh`).
        val confirmShell = io.shell(CompanionCommands.PROBE_CONFIRM)
        val confirm = confirmShell.stdout.trim().toIntOrNull()
        return CompanionCommands.liveness(primary.exit, primary.stdout, primary.stderr, confirm)
    }

    /**
     * تشغيله ثم انتظار ظهوره (بحدّ معلن). وغيابُه بعد الحدّ **يُقال** ولا يُعاد في النداء نفسه:
     * النداء التالي يقيس من جديد، فلا حلقة تشغيل متكرّرة على وحدة لا تقوم.
     */
    private fun startCompanion(): Boolean {
        val launched = rootIo.shell(CompanionCommands.launch()).exit == 0
        var waited = 0L
        while (waited < ModuleWatchmanPolicy.WAIT_MS) {
            rootIo.sleep(ModuleWatchmanPolicy.POLL_MS)
            waited += ModuleWatchmanPolicy.POLL_MS
            if (companionLiveness(rootIo) == CompanionLiveness.Alive) return true
        }
        problem("EVENT=WATCHMAN_COMPANION_FAILED launched=$launched waited_ms=$waited")
        return false
    }

    /** حدود الوحدة — من [`DaemonSupervisor`] نفسه، فلا نسخة ثانية تتفرّق عن الأصل. */
    fun blockReason(): BlockReason? = DaemonSupervisor(
        io = rootIo,
        serviceBin = serviceBin,
        logPath = logPath,
    ).blockReason()

    private fun report(alive: Boolean, stage: String, accepted: Boolean, exitCode: String?) {
        if (alive) {
            info("EVENT=WATCHMAN_STARTED stage=$stage")
        } else {
            problem("EVENT=WATCHMAN_START_FAILED stage=$stage accepted=$accepted exit=${exitCode ?: "no_new_line"}")
        }
    }
}
