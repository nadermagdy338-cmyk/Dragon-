/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.daemon

import nd.max.MaxManagerPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `module.prop` المشحون — نفس مقارنة `ModuleIntegrity.c` (‏`name`/`author`/`version`). */
private fun shippedProp(
    name: String = "MaxManager",
    author: String = "MaxManager Project",
    version: String = "v1.0",
): String = "id=MaxManager\nname=$name\nauthor=$author\nversion=$version\n"

private const val LOG_PATH = "/data/adb/.config/MaxManager/debug/MaxManager.log"

/**
 * حارس الوحدة — مُقاسًا كاملًا بجهاز مُزيف.
 *
 * **وما يُقاس هنا بالضبط:** أيّ قراءة تُنتج فعلًا، وأيّها تُنتج انتظارًا، وأيّها تُوقف النظر — وأن
 * الفعل يقع على **الرفيق أوّلًا** ثم الخادم، وأن الطريق الرخيص (بلا جذر) لا يُستدعى فيه `su` أبدًا
 * ما دام الاثنان حيّين. وما يحتاج جهازًا (‏أن يقيم خادم فعلًا) لا يُدَّعى هنا.
 */
class ModuleWatchmanTest {

    /** جهاز مُزيف: كل قراءة تُعطى نصًّا، ولا شيء يلمس الملفّات ولا الصدفة. */
    private class FakeIo(
        var daemonAlive: Boolean = true,
        var companionAlive: Boolean = true,
    ) : DaemonIo {
        val commands = mutableListOf<String>()
        val paths = mutableSetOf(MaxManagerPaths.SERVICE_BIN)
        val texts = mutableMapOf(MaxManagerPaths.MODULE_PROP to shippedProp())

        /** هل يظهر بعد تشغيله؟ — لقياس مسار «تشغيل فاشل» أيضًا. */
        var companionAppearsOnLaunch = true
        var daemonAppearsOnStart = true
        var launchAccepted = true
        var pidofToolMissing = false
        private var clock = 0L

        override fun shell(command: String): ShellOutcome {
            commands += command
            return when {
                command == DaemonCommands.PROBE -> aliveVerdict(daemonAlive, 4150)
                command == DaemonCommands.PROBE_CONFIRM ->
                    ShellOutcome(0, if (daemonAlive) "1\n" else "0\n", "")

                command == CompanionCommands.PROBE -> aliveVerdict(companionAlive, 4151)
                command == CompanionCommands.PROBE_CONFIRM ->
                    ShellOutcome(0, if (companionAlive) "1\n" else "0\n", "")

                command.startsWith("nohup app_process") -> {
                    if (launchAccepted && companionAppearsOnLaunch) companionAlive = true
                    ShellOutcome(if (launchAccepted) 0 else 1, "", "")
                }

                command.contains(DaemonCommands.start(MaxManagerPaths.SERVICE_BIN, LOG_PATH)) -> {
                    if (daemonAppearsOnStart) daemonAlive = true
                    ShellOutcome(0, "", "")
                }

                command.contains("--version") -> ShellOutcome(0, "v1.0\n", "")
                command.startsWith("tail") -> ShellOutcome(0, "", "")
                else -> ShellOutcome(0, "", "")
            }
        }

        private fun aliveVerdict(alive: Boolean, pid: Int): ShellOutcome = when {
            pidofToolMissing -> ShellOutcome(127, "", "sh: pidof: not found")
            alive -> ShellOutcome(0, "$pid\n", "")
            else -> ShellOutcome(1, "", "")
        }

        override fun readText(path: String): String? = texts[path]
        override fun exists(path: String): Boolean = path in paths
        override fun sleep(ms: Long) {
            clock += ms
        }

        override fun uptimeMs(): Long = clock

        fun startedCompanion(): Boolean = commands.any { it.startsWith("nohup app_process") }
        fun startedDaemon(): Boolean = commands.any { it.contains(" --run ") }
    }

    private class Recorder {
        val info = mutableListOf<String>()
        val problem = mutableListOf<String>()
    }

    private fun watchman(
        cheap: FakeIo,
        root: FakeIo = cheap,
        recorder: Recorder = Recorder(),
    ): Pair<ModuleWatchman, Recorder> = ModuleWatchman(
        cheapIo = cheap,
        rootIo = root,
        serviceBin = MaxManagerPaths.SERVICE_BIN,
        logPath = LOG_PATH,
        info = { recorder.info += it },
        problem = { recorder.problem += it },
    ) to recorder

    // ── السياسة الخالصة ────────────────────────────────────────────────────────

    @Test
    fun `a healthy module is left alone`() {
        assertEquals(
            WatchDecision.Idle,
            ModuleWatchmanPolicy.decide(DaemonLiveness.Alive, CompanionLiveness.Alive, null),
        )
    }

    @Test
    fun `a dead companion is started before the daemon, and alone when the daemon lives`() {
        // الاثنان غائبان: الرفيق أوّلًا ثم الخادم — لأن الخادم ينتظر قفله.
        assertEquals(
            WatchDecision.StartCompanionThenDaemon,
            ModuleWatchmanPolicy.decide(DaemonLiveness.Dead, CompanionLiveness.Dead, null),
        )
        // والخادم حيّ: الرفيق وحده — وإلا أُمر خادم قائم بالقيام فثانيًا.
        assertEquals(
            WatchDecision.StartCompanion,
            ModuleWatchmanPolicy.decide(DaemonLiveness.Alive, CompanionLiveness.Dead, null),
        )
        // والرفيق حيّ والخادم ميّت: الخادم وحده.
        assertEquals(
            WatchDecision.StartDaemon,
            ModuleWatchmanPolicy.decide(DaemonLiveness.Dead, CompanionLiveness.Alive, null),
        )
    }

    /** **«مجهول» لا يُعالج** (ADR-07): صدفة لم تُشغَّل أو أداة غائبة لا تُقرأ «مات». */
    @Test
    fun `an unknown verdict never triggers an action`() {
        assertEquals(
            WatchDecision.Wait,
            ModuleWatchmanPolicy.decide(DaemonLiveness.Unknown, CompanionLiveness.Alive, null),
        )
        assertEquals(
            WatchDecision.Wait,
            ModuleWatchmanPolicy.decide(DaemonLiveness.Alive, CompanionLiveness.Unknown, null),
        )
        assertEquals(
            CompanionLiveness.Unknown,
            CompanionProbe.liveness(127, "", "sh: pidof: not found", null),
        )
        assertEquals(
            CompanionLiveness.Dead,
            CompanionProbe.liveness(1, "", "", 0),
        )
        assertEquals(
            CompanionLiveness.Alive,
            CompanionProbe.liveness(1, "", "", 1),
        )
    }

    /** وحدود الوحدة تسبق الفعل: وحدة قيد التحديث أو ثنائيّة غائبة ⇒ لا تشغيل بالعناد. */
    @Test
    fun `module limits stop the watchman before any action`() {
        val blocked = ModuleWatchmanPolicy.decide(
            DaemonLiveness.Dead,
            CompanionLiveness.Dead,
            BlockReason.BinaryMissing(MaxManagerPaths.SERVICE_BIN),
        )
        assertTrue(blocked is WatchDecision.GiveUp)
        assertEquals("binary_missing path=${MaxManagerPaths.SERVICE_BIN}", (blocked as WatchDecision.GiveUp).reason)
    }

    // ── النصوص: أمر واحد لا أمران ─────────────────────────────────────────────

    /**
     * أمر الشفاء هو **أمر الإقلاع نفسه**: الفئة، ثم `--nice-name`، ثم ثلاث وسائط بالترتيب
     * (الحالة، التطبيقات الخلفيّة، القفل). واختبار العقد (`ModuleWatchmanContractTest`) يقيس
     * الأمرين على بعضهما في `service.sh`، فلا ينحرف أحدهما.
     */
    @Test
    fun `the heal command mirrors the boot command`() {
        val command = CompanionCommands.launch(apkPath = "/product/priv-app/MaxManager/MaxManager.apk")

        assertTrue(command.startsWith("nohup app_process -Djava.class.path='/product/priv-app/MaxManager/MaxManager.apk' /"))
        assertTrue(command.contains("--nice-name=${CompanionCommands.NICE_NAME}"))
        assertTrue(command.contains(CompanionCommands.MAIN_CLASS))
        assertTrue(
            "الوسائط بترتيب `service.sh`: الحالة ثم الخلفيّة ثم القفل",
            command.indexOf("'${MaxManagerPaths.MODULE_CONFIG}/app_status'") <
                command.indexOf("'${MaxManagerPaths.MODULE_CONFIG}/background_apps'"),
        )
        assertTrue(
            "والقفل آخرها — وهو ما يقيسه الخادم",
            command.indexOf("'${MaxManagerPaths.MODULE_CONFIG}/background_apps'") <
                command.indexOf("'${MaxManagerPaths.MODULE_CONFIG}/java.lock'"),
        )
        assertTrue("والمخرَج يُضاف لا يُقتطع (فلا يُمحى دليل فشل سابق)", command.contains(">> '${MaxManagerPaths.MODULE_CONFIG}/sysmon.log'"))
    }

    /** وما لا يُستعمل قطّ: `--rerun` يقتل الرفيق، و`--clearlogs` يمحو السجلّ في كل محاولة. */
    @Test
    fun `the watchman never uses the destructive paths`() {
        assertFalse(CompanionCommands.launch().contains("--rerun"))
        assertFalse(CompanionCommands.launch().contains("--clearlogs"))
        assertFalse(CompanionCommands.PROBE.contains("--clearlogs"))
        assertFalse(CompanionCommands.PROBE_CONFIRM.contains("--rerun"))
    }

    // ── السلوك على جهاز مُزيف ──────────────────────────────────────────────────

    /** **الطريق الرخيص:** الاثنان حيّان ⇒ لا أمر جذريّ واحد في هذا القيام. */
    @Test
    fun `a healthy module costs no root command at all`() {
        val cheap = FakeIo(daemonAlive = true, companionAlive = true)
        val root = FakeIo(daemonAlive = true, companionAlive = true)
        val (watchman, _) = watchman(cheap, root)

        assertEquals(WatchDecision.Idle, watchman.watch())
        assertTrue("الطريق الرخيص لا يلمس الجذر: ${root.commands}", root.commands.isEmpty())
        assertFalse(root.startedCompanion())
        assertFalse(root.startedDaemon())
    }

    /**
     * **والعطب المقيس:** الرفيق غائب والخادم غائب ⇒ يُشغَّل الرفيق أوّلًا، ثم الخادم — والترتيب
     * مقيس لا مُفترض، لأنّ الخادم ينتظر قفل الرفيق فينهي الوحدة إن لم يجده.
     */
    @Test
    fun `a dead companion is started before the daemon`() {
        val cheap = FakeIo(daemonAlive = false, companionAlive = false)
        val root = FakeIo(daemonAlive = false, companionAlive = false)
        val (watchman, recorder) = watchman(cheap, root)

        assertEquals(WatchDecision.StartCompanionThenDaemon, watchman.watch())
        assertTrue(root.startedCompanion())
        assertTrue(root.startedDaemon())
        assertTrue(
            "الرفيق قبل الخادم: ${root.commands}",
            root.commands.indexOfFirst { it.startsWith("nohup app_process") } <
                root.commands.indexOfFirst { it.contains(" --run ") },
        )
        assertTrue(recorder.info.any { it.contains("WATCHMAN_COMPANION_STARTED") })
        assertTrue(recorder.info.any { it.contains("WATCHMAN_STARTED stage=daemon_after_companion") })
    }

    /** وتشغيله ثم عدم ظهوره **يُقال** ولا يُعاد في النداء نفسه (النداء التالي يقيس من جديد). */
    @Test
    fun `a companion that does not appear is reported once, without the daemon start`() {
        val cheap = FakeIo(daemonAlive = false, companionAlive = false)
        val root = FakeIo(daemonAlive = false, companionAlive = false).apply {
            companionAppearsOnLaunch = false
        }
        val (watchman, recorder) = watchman(cheap, root)

        watchman.watch()

        assertTrue(root.startedCompanion())
        assertFalse("لا خادم ينتظر قفلًا لن يُحمَل", root.startedDaemon())
        assertTrue(recorder.problem.any { it.contains("WATCHMAN_COMPANION_FAILED") })
    }

    /** والخادم وحده يُشغَّل حين يكون الرفيق حيًّا — ولا يُقتل شيء ولا يُشغَّل رفيق ثانٍ. */
    @Test
    fun `a live companion only lacks the daemon`() {
        val cheap = FakeIo(daemonAlive = false, companionAlive = true)
        val root = FakeIo(daemonAlive = false, companionAlive = true)
        val (watchman, recorder) = watchman(cheap, root)

        assertEquals(WatchDecision.StartDaemon, watchman.watch())

        assertTrue(root.startedDaemon())
        assertFalse("رفيق حيّ لا يُشغَّل ثانٍ", root.startedCompanion())
        assertTrue(recorder.info.any { it.contains("WATCHMAN_STARTED stage=daemon") })
    }

    /** وأداة القياس الغائبة تُبقي القرار «مجهول» ⇒ لا فعل جذريّ أعمى. */
    @Test
    fun `a missing pidof tool yields no action and no root command`() {
        val cheap = FakeIo().apply { pidofToolMissing = true }
        val root = FakeIo().apply { pidofToolMissing = true }
        val (watchman, _) = watchman(cheap, root)

        assertEquals(WatchDecision.Wait, watchman.watch())
        assertFalse(root.startedCompanion())
        assertFalse(root.startedDaemon())
    }
}
