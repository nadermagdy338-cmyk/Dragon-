/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.daemon

import java.io.File
import nd.max.contract.ContractFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * قياس الخادم **بلا جهاز**: الأنماط الحقيقيّة لمخرَج `pidof` ولأسطر سجلّ الخادم، وهي مأخوذة من
 * الجهاز (تفريغ `_workspace/_bug/dump`) ومن المصدر لا من تصوّر.
 *
 * **والحدّ المُعلن:** هذا الملفّ لا يقول إن خادمًا يقوم على جهاز بعينه — ذاك «يحتاج جهازًا».
 * المقيس هنا هو **ما يُفعل بما يُقرأ**: أيّ مخرَج يُقرأ «حَيّ» وأيّه يُقرأ «ميّت» وأيّه يبقى
 * «مجهولًا» — والخطأ في هذا التصنيف هو الذي يُنتج إعادة تشغيل لخادم حيّ، أو صمتًا عن ميّت.
 */
class DaemonControlTest {

    // ── قياس الحياة: الأنماط الثلاثة الحقيقيّة لمخرَج `pidof` ────────────────────

    @Test
    fun `a pid is read out of the pidof output`() {
        assertEquals(4150, DaemonProbe.pid("4150")!!)
        assertEquals(4150, DaemonProbe.pid("4150 4151\n")!!)
        assertNull(DaemonProbe.pid(""))
        assertNull(DaemonProbe.pid("\n"))
    }

    @Test
    fun `pidof finding the process is alive, and its absence is dead — both measured codes`() {
        // وجدها: يطبع ورقم الخروج 0.
        assertEquals(DaemonLiveness.Alive, DaemonProbe.liveness(0, "4150\n", "", null))
        // لم يجدها: لا مخرَج ورمز 1 — وهذا هو نصّ pidof عند الغياب، لا خطأ.
        assertEquals(DaemonLiveness.Dead, DaemonProbe.liveness(1, "", "", null))
    }

    @Test
    fun `a tool that could not run is unknown, never dead`() {
        // أداة غائبة: الصدفة تقول not found وتخرج 127.
        assertEquals(
            DaemonLiveness.Unknown,
            DaemonProbe.liveness(127, "", "sh: pidof: not found", null),
        )
        // والصدفة نفسها لم تُشغَّل (استثناء ⇒ −1): لا حكم على الخادم من فشل أداتنا.
        assertEquals(DaemonLiveness.Unknown, DaemonProbe.liveness(-1, "", "boom", null))
        // وخرجٌ بلا مخرَج ولا سبب: مجهول أيضًا، لأن «لا شيء» ليس دليل غياب.
        assertEquals(DaemonLiveness.Unknown, DaemonProbe.liveness(0, "  ", "", null))
    }

    @Test
    fun `the independent confirmation rescues a truncated process name`() {
        // اسم sys.maxmanager-service أطول من 15 حرفًا فيُقتطع في stat، فلا يطابقه pidof دائمًا،
        // وps يرى سطر الأوامر كاملًا. فالعدّ ≥ 1 حكمٌ بالحياة وإن نفى pidof.
        assertEquals(DaemonLiveness.Alive, DaemonProbe.liveness(1, "", "", 1))
        assertEquals(DaemonLiveness.Alive, DaemonProbe.liveness(1, "", "", 3))
        // وعدّ صفر لا يُنقذ شيئًا: يبقى الحكم كما هو.
        assertEquals(DaemonLiveness.Dead, DaemonProbe.liveness(1, "", "", 0))
    }

    @Test
    fun `the confirmation command cannot match itself`() {
        // العطب الذي يمنعه القوس: بغيره يطابق grep سطر أوامر نفسه فيقول «حَيّ» عن ميّت.
        assertTrue(
            "the confirmation pattern must be bracketed so the grep process cannot match itself",
            DaemonCommands.PROBE_CONFIRM.contains("'[s]ys.maxmanager-service'"),
        )
        assertTrue(DaemonCommands.PROBE_CONFIRM.contains("ps -A"))
    }

    // ── الأمر النصّي: العقد الذي لا يُسمح أن ينحرف ──────────────────────────────

    @Test
    fun `the start command is a direct run and never the destructive pair`() {
        val command = DaemonCommands.start("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-service", "/tmp/log")

        assertTrue("the start must be the daemon's own --run, the same one service.sh execs", command.contains("--run"))
        assertTrue("it must be left in the background", command.trimEnd().endsWith("&"))
        assertTrue("its stderr belongs in the daemon's own log, not /dev/null", command.contains(">> '/tmp/log' 2>&1"))

        // وهذان هما الاستثناءان المقصودان: `--rerun` يقتل الرفيق (pkill appmonitoring)،
        // و`service.sh` يبدأ بـ`--clearlogs` فيمحو دليل المحاولة الفاشلة.
        DaemonCommands.DESTRUCTIVE.forEach { forbidden ->
            assertFalse("the start path must not use $forbidden", command.contains(forbidden))
        }
    }

    @Test
    fun `the version probe asks the daemon itself, before the availability gate`() {
        assertEquals(
            "/data/adb/modules/MaxManager/system/bin/sys.maxmanager-service --version 2>/dev/null",
            DaemonCommands.version("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-service"),
        )
        assertTrue(DaemonCommands.logTail("/tmp/log", 80).contains("tail -n 80"))
    }

    // ── قراءة السبب من أسطر الخادم ─────────────────────────────────────────────

    @Test
    fun `the exit reason of the shipped daemon is read out of its own lines`() {
        // الأسطر بأشكالها الحقيقيّة كما يكتبها log_zenith في SystemLogger.c.
        assertEquals(
            DaemonExit.ModuleIdentityMismatch,
            DaemonLog.lastExitOf("2026-09-28 20:59:16.060 F MaxManager: EVENT=MODULE_INTEGRITY_FAILED reason=modified_by_third_party"),
        )
        assertEquals(
            DaemonExit.ModuleVersionMismatch,
            DaemonLog.lastExitOf("F MaxManager: EVENT=MODULE_INTEGRITY_FAILED reason=version_mismatch expected=v1.0"),
        )
        assertEquals(
            DaemonExit.JavaCompanionTimeout,
            DaemonLog.lastExitOf("F MaxManager: EVENT=JAVA_COMPANION_TIMEOUT checks=120 action=exit"),
        )
        assertEquals(
            DaemonExit.JavaCompanionGone,
            DaemonLog.lastExitOf("I MaxManager: EVENT=DAEMON_STOPPED reason=java_companion_lock_released"),
        )
        assertEquals(
            DaemonExit.Signalled,
            DaemonLog.lastExitOf("I MaxManager: EVENT=DAEMON_EXIT signal=SIGTERM"),
        )
        assertEquals(
            DaemonExit.IntegrityGuard,
            DaemonLog.lastExitOf("F MaxManager: EVENT=INTEGRITY_CHECK_FAILED reason=dumpsys_tampered path=/system/bin/dumpsys"),
        )
    }

    @Test
    fun `the precursor line is not mistaken for the reason`() {
        // على الجهاز: تعديل module.prop يُنبّه inotify ثم يقرأ الحارس الهوية ثم يخرج.
        // فلو قُرأ السطر السابق لسُجّل «مُعدَّل» بدل السبب الحقيقي.
        val tail = listOf(
            "I MaxManager: EVENT=DAEMON_READY",
            "I MaxManager: EVENT=MODULE_PROP_MODIFIED",
            "F MaxManager: EVENT=MODULE_INTEGRITY_FAILED reason=modified_by_third_party",
        )
        assertEquals(DaemonExit.ModuleIdentityMismatch, DaemonLog.lastExitOf(tail))
        assertTrue(DaemonLog.propWasModified(tail))

        // والمرور الناجح ليس خروجًا، ولا الحدث العابر.
        assertNull(DaemonLog.lastExitOf("I MaxManager: EVENT=MODULE_INTEGRITY_PASSED"))
        assertNull(DaemonLog.lastExitOf("I MaxManager: EVENT=MODULE_PROP_MODIFIED"))
        assertNull(DaemonLog.lastExitOf("I MaxManager: EVENT=DAEMON_STARTED pid=4150"))
        assertNull(DaemonLog.lastExitOf(listOf("لا شيء حاسم هنا")))
    }

    @Test
    fun `the last terminal line wins, not the first`() {
        val tail = listOf(
            "F MaxManager: EVENT=JAVA_COMPANION_TIMEOUT checks=120 action=exit",
            "I MaxManager: EVENT=DAEMON_STARTED pid=9001",
            "I MaxManager: EVENT=DAEMON_STOPPED reason=java_companion_lock_released",
        )
        assertEquals(DaemonExit.JavaCompanionGone, DaemonLog.lastExitOf(tail))
    }

    // ── الأسطر الجديدة: نسبة السبب إلى المحاولة لا إلى تاريخ الملفّ ─────────────

    @Test
    fun `only the lines a start attempt actually wrote count as its reason`() {
        val before = "I MaxManager: EVENT=DAEMON_READY\n"
        val after = before + "F MaxManager: EVENT=MODULE_INTEGRITY_FAILED reason=modified_by_third_party\n"
        assertEquals(
            DaemonExit.ModuleIdentityMismatch,
            DaemonLog.lastExitOf(DaemonLog.newLines(before, after)),
        )

        // ولم تكتب المحاولة شيئًا: لا يُنسب إليها سبب قديم موجود في الذيل.
        assertTrue(DaemonLog.newLines(before, before).isEmpty())

        // ودُوِّر السجلّ أو مُسح (فالذيل الجديد لا يبدأ بالقديم): تفريغ، لا نسبة عمياء.
        assertTrue(DaemonLog.newLines(before, "I MaxManager: EVENT=DAEMON_READY\n").isEmpty())
        assertTrue(DaemonLog.newLines(before, "").isEmpty())

        // وأوّل قراءة (لا سابق): كل المخرَج جديد بمنطق «لا شيء قبله».
        assertEquals(1, DaemonLog.newLines("", before).size)
    }

    // ── الهوية: نفس قاعدة الحارس في الخادم ─────────────────────────────────────

    @Test
    fun `this copy of the identity is the daemon's own constants, and the shipped prop satisfies them`() {
        val integrity = File(ContractFixtures.repoRoot, "archdaemon/jni/src/MaxManagerUtility/ModuleIntegrity.c")
        assumeTrue("ModuleIntegrity.c not reachable; guard not evaluated", integrity.isFile)

        fun declared(name: String): String? =
            Regex("#define\\s+$name\\s+\"([^\"]*)\"").find(integrity.readText())?.groupValues?.get(1)

        // نسخة التطبيق تساوي ثوابت الحارس: لو تفرّعتا لتفرّع «ما يطلبه الخادم» عن «ما يقيسه المشرف».
        assertEquals(declared("MODULE_IDENTITY_NAME"), DaemonIdentity.EXPECTED_NAME)
        assertEquals(declared("MODULE_IDENTITY_AUTHOR"), DaemonIdentity.EXPECTED_AUTHOR)

        val shipped = File(ContractFixtures.repoRoot, "mainfiles/module.prop")
        assumeTrue("mainfiles/module.prop not reachable; guard not evaluated", shipped.isFile)
        assertEquals(
            "the shipped module.prop must satisfy the very rule the daemon enforces on boot",
            IdentityVerdict.Holds,
            DaemonIdentity.verdict(shipped.readText(), daemonVersion = null),
        )
    }

    @Test
    fun `the version the daemon prints is the version the shipped prop carries`() {
        val header = File(ContractFixtures.repoRoot, "archdaemon/jni/include/MaxManager.h")
        val shipped = File(ContractFixtures.repoRoot, "mainfiles/module.prop")
        assumeTrue("daemon header or module.prop not reachable; guard not evaluated", header.isFile && shipped.isFile)

        val daemonVersion = Regex("#define\\s+MODULE_VERSION\\s+\"([^\"]*)\"")
            .find(header.readText())?.groupValues?.get(1)
        assertTrue("MaxManager.h must declare MODULE_VERSION", !daemonVersion.isNullOrBlank())

        // وهذا هو ما يشتري معنى المقارنة: `--version` يقول نفس ما يقول الملفّ ⇒ فحكم الإصدار
        // في المشرف قابل للفصل بين «تطابق» و«تخالف» بدل أن يشكّ في الاثنين.
        assertEquals(
            IdentityVerdict.Holds,
            DaemonIdentity.verdict(shipped.readText(), daemonVersion),
        )
    }

    @Test
    fun `the historical rename bug is caught, exactly as the C guard catches it`() {
        // العطب المقيس في تاريخ المستودع: module.prop صار name=MaxManager والحارس يطلب Max Manager.
        val old = "id=MaxManager\nname=Max Manager\nauthor=MaxManager Project\nversion=v1.0\n"
        val verdict = DaemonIdentity.verdict(old, daemonVersion = "v1.0")
        assertTrue(verdict is IdentityVerdict.Violated)
        val violated = verdict as IdentityVerdict.Violated
        assertEquals("Max Manager", violated.identity.name)
        assertFalse(violated.versionMismatch)

        // وقراءة ممزّقة (ملفّ يُكتب في اللحظة نفسها): لا هويّة ⇒ مخالفة، فهي عابرة لا حتميّة.
        assertTrue(DaemonIdentity.verdict("", "v1.0") is IdentityVerdict.Violated)
    }

    @Test
    fun `the comparison does not trim, because the C guard does not trim`() {
        // prop_line_equals يقارن القيمة بحرفها: مسافة زائدة ⇒ ليست الهوية.
        assertTrue(
            DaemonIdentity.verdict(
                "name=MaxManager \nauthor=MaxManager Project\nversion=v1.0\n",
                daemonVersion = "v1.0",
            ) is IdentityVerdict.Violated,
        )
        // وقيمة بلا مفتاح (سطر لا يحمل =) لا تُقرأ هويّة.
        assertTrue(
            DaemonIdentity.verdict("MaxManager\nMaxManager Project\n", "v1.0") is IdentityVerdict.Violated,
        )
    }

    @Test
    fun `an unknown daemon version refuses to declare a mismatch`() {
        // الخادم لم يُقل إصداره (ثنائية غائبة أو أمر فشل): لا يُحكم بالبطلان لأن المجهول ليس دليلًا.
        assertEquals(
            IdentityVerdict.Holds,
            DaemonIdentity.verdict("name=MaxManager\nauthor=MaxManager Project\nversion=v9.9\n", daemonVersion = null),
        )
        assertEquals(
            IdentityVerdict.Holds,
            DaemonIdentity.verdict("name=MaxManager\nauthor=MaxManager Project\nversion=v9.9\n", daemonVersion = "  "),
        )
        // ولو قيل الإصدار فالمخالفة تُسمّى.
        val violated = DaemonIdentity.verdict(
            "name=MaxManager\nauthor=MaxManager Project\nversion=v9.9\n",
            daemonVersion = "v1.0",
        )
        assertTrue(violated is IdentityVerdict.Violated)
        assertTrue((violated as IdentityVerdict.Violated).versionMismatch)
    }

    @Test
    fun `carriage returns are tolerated, as the C guard tolerates them`() {
        val crlf = "id=MaxManager\r\nname=MaxManager\r\nauthor=MaxManager Project\r\nversion=v1.0\r\n"
        assertEquals(IdentityVerdict.Holds, DaemonIdentity.verdict(crlf, daemonVersion = "v1.0"))
    }
}
