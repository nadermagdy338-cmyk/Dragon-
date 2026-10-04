/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * عقد `IRootNodeService` — **أقوى سطح اليوم**، وقيل عنه في `ARCHITECTURE-AUDIT` §١٢.٣ إن المطلوب
 * تثبيت دلالاته لا إعادة تصميمه. فهذا الحرس يثبّت **السلك**: العمليات الخمس بأسمائها وتواقيعها
 * بالضبط، فلا تُضاف عملية سادسة ولا يُغيّر نوع وسيط بلا أن يسقط شيء.
 *
 * ولماذا هو حرس بنيوي لا اختبار سلوك
 * ----------------------------------
 * `RootNodeService` يعمل داخل عملية الجذر عبر `RootService`/`HiddenApiBypass`، ووحدات اختبار
 * JVM لا تستطيع تشغيله. والدلالات التي يُدّعى أنها مُثبَّتة (`null` = «لا قناة» لا فشل، و`\"\"` =
 * «لم تُقرأ») موثَّقة في الـAIDL وفي `RootNodeChannel` معًا — والقابل للقياس بلا جهاز هو **توافق
 * السلك**: أي انحراف في التوقيع يكسر كل مستدعٍ، ويكشفه هذا الحرس في ثانية.
 */
class RootNodeContractTest {

    private val aidl: File
        get() = File(
            ContractFixtures.repoRoot,
            "manager/app/src/main/aidl/nd/max/core/ipc/IRootNodeService.aidl",
        )

    private fun declarations(): List<String> {
        assumeTrue("IRootNodeService.aidl not reachable; guard not evaluated", aidl.isFile)
        // التعليقات تُطرح أولًا: السطر الذي يشرح عملية ليس عملية، وقراءته دعوى كاذبة.
        val body = aidl.readText()
            .replace(Regex("(?s)/\\*.*?\\*/"), " ")
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")
        return body.lineSequence()
            .map { it.trim() }
            .filter { it.endsWith(";") && it.contains("(") && it.contains(")") }
            .toList()
    }

    @Test
    fun `the interface still declares exactly the five operations, with their exact signatures`() {
        val expected = listOf(
            "String readText(String path);",
            "List<String> readTexts(in List<String> paths);",
            "boolean exists(String path);",
            "boolean writeText(String path, String value);",
            "List<String> listNames(String path, boolean directoriesOnly);",
        )
        assertEquals(
            "the RootNode wire contract changed; every caller was written against these five",
            expected,
            declarations(),
        )
    }

    @Test
    fun `the batch read stays a single transaction over an in-parameter list`() {
        val declarations = declarations()
        assertTrue(
            "readTexts must keep `in List<String>` — the batching exists because every other call " +
                "is a full binder transaction and thermal scanning issues hundreds per cycle",
            declarations.contains("List<String> readTexts(in List<String> paths);"),
        )
        assertEquals(
            "there must be exactly one batch operation; a second one would be the duplicated " +
                "capability this contract exists to prevent",
            1,
            declarations.count { it.contains("readTexts") },
        )
    }

    @Test
    fun `the interface name is the one the service and the channel both bind to`() {
        assumeTrue("IRootNodeService.aidl not reachable; guard not evaluated", aidl.isFile)
        assertTrue(
            "the AIDL must declare IRootNodeService, which is what RootNodeService implements " +
                "and RootNodeChannel binds",
            aidl.readText().contains("interface IRootNodeService"),
        )
        val service = File(ContractFixtures.repoRoot, "manager/app/src/main/java/nd/max/core/ipc/RootNodeService.kt")
        assumeTrue("RootNodeService.kt not reachable; cross-file guard not evaluated", service.isFile)
        assertTrue(
            "RootNodeService must still implement the AIDL interface it is bound through",
            service.readText().contains("IRootNodeService.Stub"),
        )
    }
}
