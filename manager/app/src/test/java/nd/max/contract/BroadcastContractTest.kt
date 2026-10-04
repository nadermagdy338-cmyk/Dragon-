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
 * عقد البثّ `nd.max.ACTION_MANAGE` — السطح الوحيد في §٤ الذي كان يُعلَن **«❌ بلا عقد»**:
 * الخادم C يبني سطر `am broadcast … --es/--ez`، والمستقبِل Kotlin يقرأ تلك الـextras بالاسم،
 * **ولا شيء كان يقارن التهجئتين**. ومفتاح يُعاد تسميته في طرف ⇒ الحقل يصل فراغًا: لا خطأ ولا
 * أثر — نفس صنف عطب `gpu_studio` الصامت.
 *
 * **والحرس هنا مقابِل للحرس C في `archdaemon/tests/broadcast.c`:** ذاك يقيس **السطر المُصدَر
 * فعلًا** من `notify`/`toast`/`clearlogs`/`hidenotifications`؛ وهذا يقيس أن **الجدول يطابق
 * المصدرين** (المُصدِر والمستقبِل) ومعه إعلان المانيفست. والحرسان يقرآن `broadcast_extras.tsv`
 * نفسه — فملف واحد يقيس لغتين.
 *
 * **وأربعة حرّاس:**
 *   ١. **الصدق:** كل صفّ مُصدِر يحوي مفتاحه فعلًا بصيغة `--es`/`--ez`، وكل صفّ مستقبِل يقرؤه
 *      بالنداء المُعلَن. فانحراف طرف عن الجدول يُسقطه.
 *   ٢. **اكتمال المُصدِر:** أي `--es`/`--ez` في ملفّي المصدر بلا صفّ ⇒ سقوط (مفتاح يُضاف بصمت).
 *   ٣. **اكتمال المستقبِل:** أي `get*Extra("…")` في المستقبِل بلا صفّ ⇒ سقوط.
 *   ٤. **المانيفست:** المستقبِل مُعلَن `exported="true"` بإذن `nd.max.permission.MANAGE` وفعل
 *      `nd.max.ACTION_MANAGE` — وبدونها لا يصل البثّ أصلًا.
 *
 * **والحدّ المُعلن:** هذا يقيس **اتفاق المصادر** لا **وصول Intent على جهاز** — `am broadcast`
 * وقدرة `su` على تجاوز فحص الإذن تبقى سلوك جهاز.
 */
class BroadcastContractTest {

    private data class Row(
        val key: String,
        val wireType: String,
        val producerFn: String,
        val readerCall: String,
    )

    /** الملفات التي تُصدِر البثّ — النطاق مكتوب هنا لا مُفترَض. */
    private val producerFiles = mapOf(
        "notify" to "archdaemon/jni/src/MaxManagerUtility/DaemonUtility.c",
        "toast" to "archdaemon/jni/src/MaxManagerUtility/DaemonUtility.c",
        "clearlogs" to "archdaemon/jni/src/BinaryCLI/CLIUtility.c",
        "hidenotifications" to "archdaemon/jni/src/BinaryCLI/CLIUtility.c",
    )

    private val receiverPath = "manager/app/src/main/java/nd/max/receiver/MaxManagerReceiver.kt"
    private val manifestPath = "manager/app/src/main/AndroidManifest.xml"

    /** العدد المقيس — مفروض بالمساواة الدقيقة: جدول ينقص صفًّا بصمت يتوقّف عن القياس. */
    private val expectedRows = 8

    private fun root(): File {
        val r = ContractFixtures.repoRoot
        assumeTrue("repository root not reachable; guard not evaluated", File(r, "archdaemon").isDirectory)
        return r
    }

    private fun rows(): List<Row> = ContractFixtures.text("broadcast_extras.tsv")
        .lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split('\t') }
        .filter { it.size == 5 }
        .map { Row(it[0], it[1], it[2], it[3]) }
        .toList()

    // ملاحظة مقيسة: النمط **لا يُلزم قوسًا بعد المفتاح**. أوّل نسخة كانت `getBooleanExtra\("([^"]+)"\)`
    // فلم تجد شيئًا — لأن النداء الحقيقي `getBooleanExtra("clearall", false)` يحمل قيمة افتراضية.
    // فصار الاكتمال أعمى عن كل الـextras المنطقية وهو «يمرّ». الحدّ الأدنى الصحيح هو اسم المفتاح لا القوس.
    private val readerString = Regex("""getStringExtra\("([^"]+)""")
    private val readerBool = Regex("""getBooleanExtra\("([^"]+)""")
    private val emittedString = Regex("""--es\s+([A-Za-z0-9_]+)""")
    private val emittedBool = Regex("""--ez\s+([A-Za-z0-9_]+)""")

    private fun flagOf(row: Row) = if (row.wireType == "bool") "--ez" else "--es"

    @Test
    fun `the table carries the measured rows and names a real producer and reader`() {
        val rows = rows()
        assertTrue("the table is empty — a fixture that asserts nothing", rows.isNotEmpty())
        assertTrue(
            "the measured table must stay $expectedRows rows (saw ${rows.size}); a shrinking table stops measuring",
            rows.size == expectedRows,
        )
        val unknown = rows.filter { it.producerFn != "-" && it.producerFn !in producerFiles.keys }
        assertTrue("rows naming a producer that the guard cannot locate: ${unknown.map { it.key }}", unknown.isEmpty())
        val badCall = rows.filter {
            it.readerCall !in setOf("getStringExtra", "getBooleanExtra", "getBooleanExtra+getStringExtra")
        }
        assertTrue("rows with an unknown reader_call: ${badCall.map { it.key }}", badCall.isEmpty())
        assertTrue(
            "the declared key set changed — update the guard deliberately, do not let it drift",
            rows.map { it.key }.toSet() == setOf(
                "notifytitle", "notifytext", "chrono_bool", "timeout", "toasttext", "clearall", "chrono",
            ),
        )
    }

    @Test
    fun `every declared producer really emits its extra and every reader really reads it`() {
        val r = root()
        val receiver = File(r, receiverPath)
        assertTrue("$receiverPath is missing; the guard cannot be evaluated", receiver.isFile)
        val receiverText = receiver.readText()
        val broken = mutableListOf<String>()

        for (row in rows()) {
            if (row.producerFn != "-") {
                val file = File(r, producerFiles.getValue(row.producerFn))
                if (!file.isFile) {
                    broken += "`${row.key}`: producer file ${producerFiles.getValue(row.producerFn)} is missing"
                } else if (!file.readText().contains("${flagOf(row)} ${row.key}")) {
                    broken += "`${row.key}`: `${row.producerFn}` no longer emits `${flagOf(row)} ${row.key}`"
                }
            }
            // الوجود يُقاس بحرف المفتاح لا بالقوس المغلق: `getBooleanExtra` يحمل قيمة افتراضية.
            if (row.readerCall.contains("getStringExtra") &&
                !receiverText.contains("""getStringExtra("${row.key}""")
            ) {
                broken += "`${row.key}`: the receiver no longer reads getStringExtra(\"${row.key}\")"
            }
            if (row.readerCall.contains("getBooleanExtra") &&
                !receiverText.contains("""getBooleanExtra("${row.key}""")
            ) {
                broken += "`${row.key}`: the receiver no longer reads getBooleanExtra(\"${row.key}\")"
            }
        }
        assertTrue(
            "a broadcast extra and one of its declared sides drifted apart — decide which side is wrong, " +
                "do not edit the table:\n" + broken.joinToString("\n"),
            broken.isEmpty(),
        )
    }

    @Test
    fun `no emitted extra exists in the daemon sources that the table does not list`() {
        val r = root()
        val declared = rows().filter { it.producerFn != "-" }.map { it.key }.toSet()
        val found = linkedMapOf<String, MutableSet<String>>()
        for (file in producerFiles.values.toSet()) {
            val f = File(r, file)
            val text = f.readText()
            for (re in listOf(emittedString, emittedBool)) {
                for (m in re.findAll(text)) found.getOrPut(m.groupValues[1]) { linkedSetOf() }.add(file)
            }
        }
        assertTrue("the scan found no emitted extra at all — it would pass over any tree", found.isNotEmpty())
        val unlisted = found.keys - declared
        assertTrue(
            "these extras are emitted by the daemon but have no row in fixtures/contracts/broadcast_extras.tsv: " +
                "${unlisted.sorted()} — add them deliberately, do not let them appear silently",
            unlisted.isEmpty(),
        )
    }

    @Test
    fun `no extra read by the receiver is missing from the table`() {
        val r = root()
        val declared = rows().map { it.key }.toSet()
        val text = File(r, receiverPath).readText()
        val read = (readerString.findAll(text) + readerBool.findAll(text))
            .map { it.groupValues[1] }
            .toSet()
        assertTrue("the receiver reads no extra at all — the scan is broken", read.isNotEmpty())
        val unlisted = read - declared
        assertTrue(
            "the receiver reads these extras but the table does not declare them: ${unlisted.sorted()} — " +
                "a key the daemon never sends, or a new one added silently",
            unlisted.isEmpty(),
        )
    }

    @Test
    fun `the manifest declares the receiver the daemon broadcasts to`() {
        val r = root()
        val manifest = File(r, manifestPath)
        assertTrue("$manifestPath is missing; the guard cannot be evaluated", manifest.isFile)
        val text = manifest.readText()
        assertTrue("the receiver entry must exist", text.contains(""".receiver.MaxManagerReceiver"""))
        assertTrue(
            "the action nd.max.ACTION_MANAGE must be declared in the receiver's intent-filter",
            text.contains("""android:name="nd.max.ACTION_MANAGE""""),
        )
        assertTrue(
            "the receiver must stay exported — `am broadcast` from the daemon cannot reach it otherwise",
            Regex("""<receiver[^>]*\.receiver\.MaxManagerReceiver[^>]*android:exported="true"""").containsMatchIn(text),
        )
        assertTrue(
            "the receiver must keep the nd.max.permission.MANAGE guard with exported=true",
            Regex("""<receiver[^>]*\.receiver\.MaxManagerReceiver[^>]*android:permission="nd\.max\.permission\.MANAGE"""").containsMatchIn(text),
        )
    }

    /**
     * **التكذيب:** لو كانت المقارنة لا تميّز لمرّت على أي مفتاح. فيُقلب كل مفتاح ويُشترَط ألّا يوجد
     * في أي مصدر — أي أن الحرس يفصل فعلًا بين الصحيح والمنحرف.
     */
    @Test
    fun `the guard is discriminating — a mutated key appears nowhere in the sources`() {
        val r = root()
        val sources = (producerFiles.values.toSet() + receiverPath)
            .joinToString("\n") { File(r, it).readText() }
        val wronglyFound = rows().map { it.key }.filter { sources.contains("${it}_MUTATED") }
        assertTrue("a deliberately mutated key was 'found', so the guard cannot fail: $wronglyFound", wronglyFound.isEmpty())
    }
}
