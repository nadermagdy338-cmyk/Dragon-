/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.contract

import nd.max.core.daemon.CompanionCommands
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * عقد **الأمر الواحد**: أمر إقلاع الرفيق (في `mainfiles/service.sh`) وأمر شفائه (في
 * `CompanionCommands`) يجب أن يبقيا متطابقين — لأنّ انحرافهما عطبٌ صامت: يُشغَّل الرفيق بوسائط
 * مختلفة فيكتب في مسار غير الذي يقرؤه، أو باسم غير الذي يُقاس به حضوره.
 *
 * وكل تأكيد يسمّي **الملفّ الذي قرأه** في رسالة الفشل — وحكمٌ لا يقول أيّ ملفّ قرأه لا يُتّهم به أحد.
 */
class ModuleWatchmanContractTest {

    private val scriptPath: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "mainfiles/service.sh") }
            .firstOrNull { it.isFile }
            ?: File("mainfiles/service.sh")
    }

    private fun script(): String {
        assumeTrue("mainfiles/service.sh not reachable; guard not evaluated", scriptPath.isFile)
        return scriptPath.readText()
    }

    private val label: String get() = scriptPath.absolutePath

    /**
     * الاسم والفئة والوسائط الثلاث — بحروفها نفسها.
     *
     * والسكربت يضع الاسم في متغيّر (`COMPANION_NAME`) ثم يستعمله، فالتأكيد يقيس **القيمتين**:
     * تعريف المتغيّر، واستعماله في `--nice-name`. وقياس قيمة واحدة يُمرّر خطأً يوم يتغيّر التعريف.
     */
    @Test
    fun `the boot script launches the same companion the watchman launches`() {
        val text = script()

        assertTrue(
            "‏[$label] اسم الرفيق في السكربت ليس هو الذي يقيسه التطبيق: ${CompanionCommands.NICE_NAME}",
            text.contains("COMPANION_NAME=\"${CompanionCommands.NICE_NAME}\""),
        )
        assertTrue(
            "‏[$label] الاسم يُمرَّر إلى `--nice-name` لا يُخزَّن وحده",
            text.contains("--nice-name=\"\$COMPANION_NAME\""),
        )
        assertTrue(
            "‏[$label] فئة الرفيق تغيّرت — فأمر الشفاء يُشغّل شيئًا آخر",
            text.contains(CompanionCommands.MAIN_CLASS),
        )
        listOf("app_status", "background_apps", "java.lock").forEach { argument ->
            assertTrue("‏[$label] الوسيطة `$argument` غائبة عن أمر الإقلاع", text.contains("/$argument\""))
        }
        assertTrue(
            "‏[$label] الفئة تُشغَّل بـ`app_process` بمسار صفّ الفئات",
            text.contains("app_process -Djava.class.path="),
        )
    }

    /**
     * والمخرَج **يُضاف** في الموضعين: `>` يمحو دليل الفشل السابق، فتصير إعادة التشغيل تمحو ما
     * أُعيدت من أجله — وهو العطب نفسه الذي يمنعه `DaemonSupervisor` من مسار `--clearlogs`.
     */
    @Test
    fun `both launches append to sysmon log instead of truncating it`() {
        val text = script()
        assertTrue(
            "‏[$label] أمر إقلاع الرفيق يقتطع `sysmon.log` — فيُمحى دليل ما قبله",
            Regex("java\\.lock\"\\s*>>\"\\\$MODULE_CONFIG/sysmon\\.log\"").containsMatchIn(text),
        )
        assertTrue(
            "وأمر الشفاء يضيف كذلك: ${CompanionCommands.launch()}",
            CompanionCommands.launch().contains(">> '"),
        )
    }

    /**
     * وحدود الاستقبال: الأحداث الثلاثة المعلنة — و**لا `LOCKED_BOOT_COMPLETED`**.
     *
     * ولماذا يُقاس هذا نصًّا: ذاك البثّ يُسلَّم قبل فتح تخزين المستخدم، فقيام عملية التطبيق عنده
     * يُدخل مسارًا لم يُقَس على جهاز (وهو نوع العطب الذي كلف إقلاعًا كاملًا في ٢٠٢٦-١٠-٠١).
     */
    @Test
    fun `the receiver listens after unlock, not before it`() {
        val manifest = manifest()
        assertTrue("‏[${manifestLabel()}] حارس الوحدة ليس مستقبِلًا معلنًا", manifest.contains(".receiver.ModuleWatchReceiver"))
        assertTrue("USER_UNLOCKED هو وقت الشفاء", manifest.contains("android.intent.action.USER_UNLOCKED"))
        assertTrue("وقيام النظام بعده", manifest.contains("android.intent.action.BOOT_COMPLETED"))
        // والقياس على **الإعلان** لا على النصّ: الكلمة مذكورة في تعليقٍ يشرح سبب رفضها، فلو
        // قِيس النصّ كله لرسب الاختبار من شرحه هو. والإعلان الفعليّ يُعرف بـ`android:name=`.
        assertFalse(
            "LOCKED_BOOT_COMPLETED يُسلَّم قبل فتح تخزين المستخدم: لا يُعلن",
            manifest.contains("android:name=\"android.intent.action.LOCKED_BOOT_COMPLETED\""),
        )
    }

    /** المانيفست يُوجد بالصعود مثل السكربت — لا بمسار نسبيّ يفترض مجلد التنفيذ. */
    private val manifestPath: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "manager/app/src/main/AndroidManifest.xml") }
            .firstOrNull { it.isFile }
            ?: File("manager/app/src/main/AndroidManifest.xml")
    }

    private fun manifest(): String {
        assumeTrue("AndroidManifest.xml not reachable; guard not evaluated", manifestPath.isFile)
        return manifestPath.readText()
    }

    private fun manifestLabel(): String = manifestPath.absolutePath
}
