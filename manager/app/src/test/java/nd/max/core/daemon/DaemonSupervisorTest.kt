/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.daemon

import nd.max.MaxManagerPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * محتوى `module.prop` بأشكاله المشحونة (باقي الحقول اختصارًا — المقيس هو السطور الثلاثة التي
 * يقارنها الحارس في الخادم).
 */
private fun shippedProp(
    name: String = "MaxManager",
    author: String = "MaxManager Project",
    version: String = "v1.0",
): String = "id=MaxManager\nname=$name\nauthor=$author\nversion=$version\n"

private const val LOG_PATH = "/data/adb/.config/MaxManager/debug/MaxManager.log"

/**
 * المشرف — مُقاسًا كاملًا بجهاز مُزيف: القرار، والسقف، والتراجع، والأسباب التي تُوقف النظر.
 *
 * **والحدّ المُعلن:** لا شيء هنا يقول إن خادمًا يقيم على جهاز — ذاك يحتاج جهازًا، وحكمه في
 * `HANDOFF`. المقيس هو **ما يفعله المشرف بما يقرؤه**: أيّ قراءة تُنتج تشغيلًا، وأيّها تُنتج
 * انتظارًا، وأيّها تُوقف المحاولات ويُسمّى سببها — وهو الفرق بين نهاية مسدودة ووظيفة تعمل.
 */
class DaemonSupervisorTest {

    /** جهاز مُزيف: كل قراءة تُعطى نصًّا، ولا شيء يلمس الملفّات ولا الصدفة. */
    private class FakeIo : DaemonIo {
        val commands = mutableListOf<String>()
        val paths = mutableSetOf(MaxManagerPaths.SERVICE_BIN)
        val texts = mutableMapOf(MaxManagerPaths.MODULE_PROP to shippedProp())
        val tailQueue = ArrayDeque<String>()

        var daemonVersion = "v1.0"
        var alivePid: Int? = null
        /** إن مُرِّر: يصير الخادم حيًّا بعد هذا العدد من نداءات `pidof`. */
        var aliveAfterPolls: Int? = null
        var pidofToolMissing = false
        var clock = 0L

        private var pidofPolls = 0

        override fun shell(command: String): ShellOutcome {
            commands += command
            return when {
                command.startsWith("pidof") -> {
                    pidofPolls += 1
                    val alive = alivePid != null ||
                        (aliveAfterPolls?.let { pidofPolls >= it } == true)
                    when {
                        pidofToolMissing -> ShellOutcome(127, "", "sh: pidof: not found")
                        alive -> ShellOutcome(0, "${alivePid ?: 4150}\n", "")
                        else -> ShellOutcome(1, "", "")
                    }
                }

                command.contains("ps -A") -> ShellOutcome(0, "${if (alivePid != null) 1 else 0}\n", "")
                command.contains("--version") -> ShellOutcome(0, "$daemonVersion\n", "")
                command.startsWith("tail") -> ShellOutcome(0, tailQueue.removeFirstOrNull().orEmpty(), "")
                else -> ShellOutcome(0, "", "")
            }
        }

        override fun readText(path: String): String? = texts[path]
        override fun exists(path: String): Boolean = path in paths
        override fun sleep(ms: Long) {
            clock += ms
        }

        override fun uptimeMs(): Long = clock

        fun startedTheDaemon(): Boolean = commands.any { it.contains(" --run ") }
        fun startCount(): Int = commands.count { it.contains(" --run ") }
    }

    private class Recorder {
        val info = mutableListOf<String>()
        val problem = mutableListOf<String>()
    }

    private fun supervisor(io: FakeIo, recorder: Recorder = Recorder()): Pair<DaemonSupervisor, Recorder> =
        DaemonSupervisor(
            io = io,
            serviceBin = MaxManagerPaths.SERVICE_BIN,
            logPath = LOG_PATH,
            info = { recorder.info += it },
            problem = { recorder.problem += it },
        ) to recorder

    // ── السياسة الخالصة ────────────────────────────────────────────────────────

    @Test
    fun `a live daemon is left alone and an unknown verdict is never acted on`() {
        assertEquals(Decision.Idle, DaemonSupervisorPolicy.decide(0, 0, 0, DaemonLiveness.Alive, null))
        // مجهول: لا يُعاد تشغيل خادم لم يُثبت موته.
        assertEquals(Decision.Wait, DaemonSupervisorPolicy.decide(0, 0, 1_000_000, DaemonLiveness.Unknown, null))
    }

    @Test
    fun `a dead daemon starts at once, then waits for its backoff`() {
        assertEquals(Decision.Start(1), DaemonSupervisorPolicy.decide(0, 0, 0, DaemonLiveness.Dead, null))
        assertEquals(Decision.Wait, DaemonSupervisorPolicy.decide(1, 90_000, 60_000, DaemonLiveness.Dead, null))
        assertEquals(Decision.Start(2), DaemonSupervisorPolicy.decide(1, 90_000, 90_000, DaemonLiveness.Dead, null))
    }

    @Test
    fun `attempts are capped so a hopeless daemon cannot loop forever`() {
        val capped = DaemonSupervisorPolicy.decide(
            attempts = DaemonSupervisorPolicy.MAX_ATTEMPTS,
            nextAttemptAtMs = 0,
            nowMs = 10_000_000,
            liveness = DaemonLiveness.Dead,
            blocked = null,
        )
        assertTrue(capped is Decision.GiveUp)
        assertTrue((capped as Decision.GiveUp).reason.startsWith("attempts_exhausted"))
    }

    @Test
    fun `the backoff is bounded and grows with the attempt number`() {
        // الأولى فوريّة (يُقرأ `nextAttemptAtMs = 0`)، و**بعدها** يُؤجَّل: تراجع لا تكرار كل دورة.
        assertTrue(DaemonSupervisorPolicy.backoffAfter(1) > 0L)
        assertTrue(DaemonSupervisorPolicy.backoffAfter(2) > DaemonSupervisorPolicy.backoffAfter(1))
        assertTrue(DaemonSupervisorPolicy.backoffAfter(3) > DaemonSupervisorPolicy.backoffAfter(2))
        // والحدّ: لا ينمو بلا نهاية مهما بلغ العدّاد.
        assertEquals(DaemonSupervisorPolicy.backoffAfter(3), DaemonSupervisorPolicy.backoffAfter(99))
    }

    @Test
    fun `a blocked device is reported with its name instead of being retried`() {
        val blocked = DaemonSupervisorPolicy.decide(
            attempts = 0,
            nextAttemptAtMs = 0,
            nowMs = 0,
            liveness = DaemonLiveness.Dead,
            blocked = BlockReason.ModuleChanging(listOf("update")),
        )
        assertTrue(blocked is Decision.GiveUp)
        assertEquals("module_marker=update", (blocked as Decision.GiveUp).reason)
    }

    // ── التشغيل: مؤكَّد لا مُفترض ──────────────────────────────────────────────

    @Test
    fun `a start is confirmed by the probe, and its window is wide enough for a real boot`() {
        val io = FakeIo()
        io.aliveAfterPolls = 3 // العدة الأولى والثانية: ميّت · الثالثة: حيّ
        val outcome = DaemonStarter(io, MaxManagerPaths.SERVICE_BIN, LOG_PATH).startAndConfirm()

        assertTrue(outcome.accepted)
        assertTrue("the probe must be the one that declares it up", outcome.alive)
        assertEquals(3L * DaemonSupervisorPolicy.CONFIRM_POLL_MS, outcome.waitedMs)
        assertNull("a live daemon has no exit reason", outcome.exit)
        assertEquals(1, io.startCount())

        assertTrue(DaemonSupervisorPolicy.CONFIRM_TIMEOUT_MS > 10_000L)
        assertEquals(0L, DaemonSupervisorPolicy.CONFIRM_TIMEOUT_MS % DaemonSupervisorPolicy.CONFIRM_POLL_MS)
    }

    @Test
    fun `a start that dies names the reason its own lines added`() {
        val io = FakeIo()
        io.tailQueue += "I MaxManager: EVENT=DAEMON_READY\n"
        io.tailQueue += "I MaxManager: EVENT=DAEMON_READY\n" +
            "F MaxManager: EVENT=MODULE_INTEGRITY_FAILED reason=modified_by_third_party\n"

        val outcome = DaemonStarter(io, MaxManagerPaths.SERVICE_BIN, LOG_PATH).startAndConfirm()

        assertFalse(outcome.alive)
        assertTrue(io.startedTheDaemon())
        assertEquals(DaemonSupervisorPolicy.CONFIRM_TIMEOUT_MS, outcome.waitedMs)
        assertEquals(DaemonExit.ModuleIdentityMismatch, outcome.exit)
    }

    @Test
    fun `a start that wrote nothing reports no reason rather than the previous death's`() {
        val io = FakeIo()
        // الذيل نفسه قبل المحاولة وبعدها: الملفّ يحمل سببًا قديمًا، ولا حرف جديد في هذه المحاولة.
        val stale = "F MaxManager: EVENT=JAVA_COMPANION_TIMEOUT checks=120 action=exit\n"
        io.tailQueue += stale
        io.tailQueue += stale

        val outcome = DaemonStarter(io, MaxManagerPaths.SERVICE_BIN, LOG_PATH).startAndConfirm()

        assertFalse(outcome.alive)
        assertNull("a stale line must not be presented as this attempt's reason", outcome.exit)
    }

    // ── الدورة الكاملة ────────────────────────────────────────────────────────

    @Test
    fun `a dead daemon is started, and its recovery is recorded once`() {
        val io = FakeIo()
        val (supervisor, recorder) = supervisor(io)

        assertTrue(supervisor.tick() is Decision.Start)
        assertTrue("the start must go through the direct run", io.startedTheDaemon())
        assertTrue(recorder.problem.any { it.contains("EVENT=DAEMON_SUPERVISOR_START_FAILED") })

        io.alivePid = 4150
        io.clock += 200_000
        assertEquals(Decision.Idle, supervisor.tick())
        assertEquals(Decision.Idle, supervisor.tick())
        assertEquals(1, recorder.info.count { it.contains("RECOVERED") })
    }

    @Test
    fun `a retry is deferred by the backoff, not repeated every tick`() {
        val io = FakeIo()
        val (supervisor, _) = supervisor(io)

        supervisor.tick() // محاولة أولى تفشل
        val afterFirst = io.startCount()

        // دورة تالية بعد ثانية واحدة: التراجع (٣٠ ث) يمنع تكرار المحاولة.
        io.clock += 1_000
        assertEquals(Decision.Wait, supervisor.tick())
        assertEquals(afterFirst, io.startCount())

        // وبعد انقضاء التراجع تُسمح محاولة ثانية.
        io.clock += 60_000
        assertTrue(supervisor.tick() is Decision.Start)
        assertEquals(afterFirst + 1, io.startCount())
    }

    @Test
    fun `an unknown verdict never starts anything`() {
        val io = FakeIo()
        io.pidofToolMissing = true
        val (supervisor, recorder) = supervisor(io)

        assertEquals(Decision.Wait, supervisor.tick())
        assertFalse(io.startedTheDaemon())
        assertTrue(recorder.problem.isEmpty())
    }

    @Test
    fun `a mismatched module prop stops the attempts and names both sides`() {
        val io = FakeIo()
        io.texts[MaxManagerPaths.MODULE_PROP] = shippedProp(name = "Max Manager")
        val (supervisor, recorder) = supervisor(io)

        val decision = supervisor.tick()
        assertTrue(decision is Decision.GiveUp)
        val reason = (decision as Decision.GiveUp).reason
        assertTrue("the actual name must be in the report, not a generic failure", reason.contains("name=Max Manager"))
        assertTrue(reason.contains("requires=name=MaxManager,author=MaxManager Project"))
        assertFalse("a permanent identity conflict must not be retried", io.startedTheDaemon())

        // ويُقال مرّة واحدة: الدورة كل دقيقة، وتكرار السطر نفسه ضجيج يُخفي غيره.
        io.clock += 60_000
        supervisor.tick()
        assertEquals(1, recorder.problem.count { it.contains("GAVE_UP") })
    }

    @Test
    fun `a version mismatch is named with both versions`() {
        val io = FakeIo()
        io.texts[MaxManagerPaths.MODULE_PROP] = shippedProp(version = "v1.0.0-rc")
        val (supervisor, _) = supervisor(io)

        val reason = (supervisor.tick() as Decision.GiveUp).reason
        assertTrue(reason.contains("version=v1.0.0-rc"))
        assertTrue(reason.contains("daemon=v1.0"))
    }

    @Test
    fun `module markers and a missing binary stop the supervisor before it fights the module manager`() {
        val updating = FakeIo()
        updating.paths += "${MaxManagerPaths.MODULE_DIR}/update"
        val (updatingSupervisor, _) = supervisor(updating)
        assertEquals("module_marker=update", (updatingSupervisor.tick() as Decision.GiveUp).reason)
        assertFalse(updating.startedTheDaemon())

        val native = FakeIo()
        native.paths += "${MaxManagerPaths.MODULE_DIR}/rom-native-mode"
        val (nativeSupervisor, _) = supervisor(native)
        assertEquals("module_marker=rom-native-mode", (nativeSupervisor.tick() as Decision.GiveUp).reason)

        val removed = FakeIo()
        removed.paths += "${MaxManagerPaths.MODULE_DIR}/remove"
        val (removedSupervisor, _) = supervisor(removed)
        assertEquals("module_marker=remove", (removedSupervisor.tick() as Decision.GiveUp).reason)

        val missing = FakeIo()
        missing.paths.clear()
        val (missingSupervisor, _) = supervisor(missing)
        assertEquals(
            "binary_missing path=${MaxManagerPaths.SERVICE_BIN}",
            (missingSupervisor.tick() as Decision.GiveUp).reason,
        )
    }

    @Test
    fun `an unreadable module prop is reported as the cause, because that is what kills the daemon`() {
        val io = FakeIo()
        io.texts.clear()
        val (supervisor, _) = supervisor(io)

        assertEquals(
            "module_prop_unreadable path=${MaxManagerPaths.MODULE_PROP}",
            (supervisor.tick() as Decision.GiveUp).reason,
        )
    }

    @Test
    fun `the supervisor exhausts its attempts and then stays quiet`() {
        val io = FakeIo()
        val (supervisor, recorder) = supervisor(io)

        repeat(DaemonSupervisorPolicy.MAX_ATTEMPTS) {
            assertTrue(supervisor.tick() is Decision.Start)
            io.clock += 10 * 60_000L // يكفي لتجاوز أي تراجع
        }

        val exhausted = supervisor.tick()
        assertTrue(exhausted is Decision.GiveUp)
        assertTrue((exhausted as Decision.GiveUp).reason.startsWith("attempts_exhausted"))
        assertEquals(1, recorder.problem.count { it.contains("GAVE_UP") })
        assertEquals(DaemonSupervisorPolicy.MAX_ATTEMPTS, io.startCount())
    }
}
