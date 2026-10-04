/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.contract

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * ملكية الملف العام — الفجوة الثالثة في `ARCHITECTURE-AUDIT` §١٢.٦.
 *
 * العقد (§١٢.١) يقول: «**الخادم وحده** يطبّق الملف العام، مثبَّت بـ`profilesettings` الذي يرفض غير
 * الخادم». وتحقّقه يحتاج تشغيل ثنائية Rust على جهاز — وهما غائبان هنا. لكن **ما يقرّره الرفض
 * مقيس من المصدر**، والمصدر كشف شيئًا يستحق التثبيت أكثر من الرفض نفسه:
 *
 * **الشرط الثاني يبدو زائدًا وليس كذلك.** `verify_caller()` يقرأ سطر أوامر **الأب**، والأب ليس
 * الخادم دائمًا: `systemv()` في الخادم يعمل بـ`execle("/system/bin/sh", "sh", "-c", command, …)`،
 * فإن لم تُنفّذ الصدفة آخر أمر في نفسها فالعمليّة التي تُشغّل الثنائية هي `sh`، وسطر أوامرها يحمل
 * `sys.maxmanager-profilesettings 2` — **لا تحوي `-service`، وتحوي `maxmanager`**. فالشرطان
 * يغطيان الحالتين (أب = الخادم · أب = `sh`)، وحذف «الزائد» يُسقط كل طلبات الملف على الجهاز.
 *
 * فالحرس هنا يثبّت: الشرطين معًا، ومصدر الأب، وأن الرفض **يخرج بغير صفر** (لا ابتلاع صمت)، وأن اسم
 * الخادم المُتحقَّق منه هو الاسم الذي يُثبّته المنصّب فعلًا — فلا يتباعد الاثنان بعد إعادة تسمية.
 */
class ExecOwnershipContractTest {

    private val rust = File(ContractFixtures.repoRoot, "binprofiles/src/main.rs")

    /**
     * والقرار انتقل إلى `binprofiles/src/plan.rs` (تكملة ١٢٦) ليُقاس بـ`cargo test` على سطري
     * الأوامر الحقيقيين — فصار حرس هذه الدعوى **أقوى** لا أضعف: الشرطين يُقيَّمان فعلًا هناك،
     * وهنا نُثبّت أنهما باقيان بنصّهما وأنّ الأب يُقرأ من `/proc` لا يُفترض.
     */
    private val planFile = File(ContractFixtures.repoRoot, "binprofiles/src/plan.rs")

    private fun source(): String {
        assumeTrue("binprofiles/src/main.rs not reachable; guard not evaluated", rust.isFile)
        return rust.readText()
    }

    private fun planSource(): String {
        assumeTrue("binprofiles/src/plan.rs not reachable; guard not evaluated", planFile.isFile)
        return planFile.readText()
    }

    @Test
    fun `the caller is identified from the parent process, not from trust`() {
        val body = source()
        assertTrue(
            "the parent pid must be read from /proc/self/stat (field 4)",
            body.contains("\"/proc/self/stat\""),
        )
        assertTrue(
            "the parent's cmdline must be read, not assumed",
            body.contains("\"/proc/{}/cmdline\""),
        )
    }

    @Test
    fun `both acceptance clauses stay, because the parent may be the shell that systemv spawns`() {
        val body = planSource()
        assertTrue(
            "the direct-daemon clause must stay",
            body.contains("cmdline.contains(\"sys.maxmanager-service\")"),
        )
        assertTrue(
            "the shell-wrapper clause must stay: systemv() runs the binary through `sh -c`, and that " +
                "shell's cmdline carries `sys.maxmanager-profilesettings`, which the -service clause " +
                "can never match. Removing this as \"redundant\" breaks every profile apply on device.",
            body.contains("cmdline.contains(\"sys.maxmanager\")"),
        )

        // والدليل على أن الصدف وسيط حقيقي مأخوذ من الخادم لا من التصور.
        val systemv = File(ContractFixtures.repoRoot, "archdaemon/jni/src/ShellUtility/SystemvUtility.c")
        assumeTrue("SystemvUtility.c not reachable; guard not evaluated", systemv.isFile)
        assertTrue(
            "systemv() must still go through /system/bin/sh -c; if it ever execs directly, the parent " +
                "becomes the daemon and this guard's reasoning must be re-measured, not assumed",
            systemv.readText().contains("\"/system/bin/sh\""),
        )
    }

    @Test
    fun `a refused caller exits non-zero rather than continuing quietly`() {
        val body = source()
        val refusal = Regex("if !verify_caller\\(\\) \\{(.*?)\\n    \\}", RegexOption.DOT_MATCHES_ALL)
            .find(body)?.groupValues?.get(1)
        assumeTrue("verify_caller() gate not found in the expected shape; guard not evaluated", refusal != null)
        assertTrue(
            "a non-daemon caller must fail loudly (exit non-zero); a silent continue would let any " +
                "process rewrite the shared profile",
            refusal!!.contains("std::process::exit(1)"),
        )
    }

    @Test
    fun `the daemon name this contract checks is the name the installer actually ships`() {
        val installer = File(ContractFixtures.repoRoot, ".github/scripts/compile_zip.sh")
        assumeTrue("compile_zip.sh not reachable; cross-layer guard not evaluated", installer.isFile)
        assertTrue(
            "compile_zip.sh must stage the daemon as `sys.maxmanager-service` (the name the ownership " +
                "check and the AOSP/KernelSU templates all use); a rename on one side alone would make " +
                "every profile apply fail on device with exit 1",
            installer.readText().contains("sys.maxmanager-service"),
        )
        assertTrue(
            "the Rust side must check the same name",
            planSource().contains("sys.maxmanager-service"),
        )
    }
}
