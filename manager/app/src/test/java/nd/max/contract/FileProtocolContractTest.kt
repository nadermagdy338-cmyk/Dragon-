/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * عقد `API/current_modes` — ملف من قيمة واحدة، ودلالته كلها في مقارنة حرفية واحدة.
 *
 * الكاتب هو **MAX AI** (`MaxAiEngine.setAiEnabled`)، وهو مكوّن أصلي ممنوع التعديل بأمر المالك،
 * فالقناة تُثبَّت من طرفيها لا بتغيير الكاتب: (١) النطاق المُعلَن في الـfixtures، و(٢) الحرف
 * الذي يقارن به الخادم. والحرف الثاني هو الموضع الوحيد الذي تُترجم فيه القيمة إلى سلوك، وقيمته
 * الحقيقية أنه **كل ما لا يساوي `1` يُقرأ مطفأً** — فقيمة مشوّهة تُقرأ «إيقافًا» لا خطأً، وهذا
 * يُعلَن ولا يُخفى.
 */
class FileProtocolContractTest {

    @Test
    fun `current_modes declares exactly the two literals the daemon understands`() {
        val on = ContractFixtures.text("current_modes.valid.txt").trim()
        val torn = ContractFixtures.text("current_modes.torn.txt").trim()

        assertEquals("the canonical enable value is the single literal 1", "1", on)
        assertNotEquals(
            "the torn fixture exists to prove that anything but a literal 1 reads as off",
            "1",
            torn,
        )
    }

    @Test
    fun `the daemon decides on the literal 1 alone, in the file that watches the change`() {
        val watcher = File(
            ContractFixtures.repoRoot,
            "archdaemon/jni/src/InotifyHandler/InotifyWatcher.c",
        )
        assumeTrue(
            "InotifyWatcher.c not reachable; cross-language guard not evaluated",
            watcher.isFile,
        )
        val body = watcher.readText()

        assertTrue(
            "the watcher must still react to the current_modes file name",
            body.contains("\"current_modes\""),
        )
        assertTrue(
            "the on/off decision must remain strcmp against the literal \"1\"; a looser test " +
                "(atoi, contains, case-folded) would silently accept values the fixtures mark as off",
            body.contains("strcmp(ai_state, \"1\")"),
        )
    }

    @Test
    fun `every declared fixture is present so a silent deletion cannot look like a pass`() {
        val declared = listOf(
            "app_status.valid.txt",
            "app_status.torn.txt",
            "app_status.missing.txt",
            "current_modes.valid.txt",
            "current_modes.torn.txt",
            "per_app_hw_status.valid.txt",
            "per_app_hw_status.torn.txt",
            "cli_profile.tsv",
        )
        val missing = declared.filterNot { File(ContractFixtures.directory, it).isFile }
        assertTrue("declared contract fixtures are missing: $missing", missing.isEmpty())
    }
}
