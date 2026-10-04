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
 * دلالتا `null` و`""` في قناة RootNode — الفجوة الثانية في `ARCHITECTURE-AUDIT` §١٢.٦.
 *
 * لماذا كانت فجوة، وكيف تُغلق بلا جهاز
 * ----------------------------------
 * `RootNodeService` يعمل داخل عملية الجذر عبر ربط `RootService`، ولا يُشغّل في اختبار JVM — فلا
 * يمكن استدعاؤه لقياس دلالته. لكن الدلالة ليست في الخدمة وحدها: **أثرها في المستدعين**. والعقد
 * يقول شيئين، وكلاهما قابل للقياس على الكود نفسه:
 *
 * - `null` = «لا قناة الآن» **لا فشل** ⇒ فكل استعمال لها في المصادر يجب أن يكون **نداءً آمنًا**
 *   مع طريق بديل، ولا يجوز `!!` — لأن `!!` يحوّل انقطاع الربط (حالة عادية) إلى انهيار.
 * - `""` = «لم تُقرأ» ⇒ فقراءة الملفات يجب أن **تطرح الفراغ وتهبط** إلى الطريق البديل، لا أن
 *   تُعيده قيمةً صحيحة.
 *
 * وهذا يُغلق الفجوة في جزئها القابل للقياس، ويُبقي المُعلَن ما لا يُقاس: أن الربط يعمل فعلًا على
 * جهاز بجذر — وهذا يحتاج جهازًا.
 */
class RootNodeSemanticsContractTest {

    private fun serviceUsages(): List<Pair<String, Int>> {
        val hits = mutableListOf<Pair<String, Int>>()
        for (file in ContractFixtures.mainSourceFiles()) {
            file.readText().lines().forEachIndexed { index, line ->
                if (line.contains("RootNodeChannel.service")) hits += file.name to (index + 1)
            }
        }
        return hits
    }

    @Test
    fun `the channel is declared nullable with a private setter, so null is a legitimate state`() {
        val channel = File(
            ContractFixtures.repoRoot,
            "manager/app/src/main/java/nd/max/core/ipc/RootNodeChannel.kt",
        )
        assumeTrue("RootNodeChannel.kt not reachable; guard not evaluated", channel.isFile)
        val body = channel.readText()

        assertTrue(
            "the binder must be declared nullable: `null` means \"no channel yet\", not an error",
            body.contains("var service: IRootNodeService? = null"),
        )
        assertTrue(
            "only the channel may hand out or drop the binder; an outside writer could set it to a " +
                "stale interface",
            body.contains("private set"),
        )
        assertTrue(
            "a disconnect must clear the handle rather than leave a dead one",
            Regex("onServiceDisconnected[^}]*service = null", RegexOption.DOT_MATCHES_ALL).containsMatchIn(body),
        )
    }

    @Test
    fun `every consumer treats a missing channel as a fallback, never as a crash`() {
        val usages = serviceUsages()
        assertTrue("no consumer found — the guard is not evaluating anything", usages.isNotEmpty())

        val offenders = mutableListOf<String>()
        for (file in ContractFixtures.mainSourceFiles()) {
            val body = file.readText()
            if (body.contains("RootNodeChannel.service!!"))
                offenders += "${file.name}: uses !! on the binder, turning a normal disconnect into a crash"
        }
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())

        /*
         * وكل موضع يجب أن يُنادى بنداء آمن (`?.`). وهذا الشرط **يُسجّل ما يفرضه المُصرّف أصلًا**،
         * لا يضيف أمانًا جديدًا: النوع `IRootNodeService?` يجعل الوصول المباشر خطأ تصريف — وقد
         * جُرّب فعلًا فسقط البناء. فائدته الحقيقية أنه **يبقى حارسًا على النوعية (nullability) نفسها**:
         * لو صار الـمُعلَن غير قابل للفراغ يومًا (تُعديل «تنظيفي»)، يسقط هذا الاختبار بدل أن يتحول
         * انقطاع الربط إلى انهيار — وهو ما يجعل حارس `!!` أعلاه ذا معنى بدوره.
         */
        val unsafe = mutableListOf<String>()
        for (file in ContractFixtures.mainSourceFiles()) {
            file.readText().lines().forEachIndexed { index, line ->
                val marker = "RootNodeChannel.service"
                var from = line.indexOf(marker)
                while (from >= 0) {
                    val after = line.substring(from + marker.length).trimStart()
                    if (!after.startsWith("?.")) unsafe += "${file.name}:${index + 1}"
                    from = line.indexOf(marker, from + marker.length)
                }
            }
        }
        assertTrue(
            "RootNodeChannel.service must only ever be called with `?.` — the call sites assume a " +
                "nullable binder, and a nullable-to-non-null regression would break that assumption: $unsafe",
            unsafe.isEmpty(),
        )
    }

    @Test
    fun `an empty read is treated as unread and falls through to the local fallback`() {
        val access = File(
            ContractFixtures.repoRoot,
            "manager/app/src/main/java/nd/max/core/hardware/RootFileAccess.kt",
        )
        assumeTrue("RootFileAccess.kt not reachable; guard not evaluated", access.isFile)
        val body = access.readText()

        assertTrue(
            "read() must discard an empty IPC answer (`\"\"` = not read) and continue to the file path",
            body.contains("readText(path)?.trim()?.takeIf { it.isNotEmpty() }"),
        )
        assertTrue(
            "the IPC answer must be followed by a non-IPC fallback — the safety net that made this " +
                "channel optional in the first place",
            body.contains("File(path)") && (body.contains("Shell.cmd") || body.contains("shellTest")),
        )
    }
}
