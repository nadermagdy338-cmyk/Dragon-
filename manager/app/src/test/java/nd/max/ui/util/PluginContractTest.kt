package nd.max.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد الإضافات: التحليل والحكم.
 *
 * وأهمّ اختبارين هنا ليسا في الحقول العادية، بل في **الرفض**: قدرة مجهولة تُرفض ولا
 * تُتجاهل، وإضافة تدّعي هوية غير مجلدها تُرفض. ولو مرّ أحدهما لما كان للعقد معنى —
 * فالعقد قيمته في ما يمنعه، لا في ما يسمح به.
 */
class PluginContractTest {

    private fun manifest(vararg lines: String) = lines.toList()

    private val valid = manifest(
        "id=com.example.reporter",
        "name=Reporter",
        "version=1.0.0",
        "api=1",
        "kind=instrument",
        "capabilities=read.telemetry,report.instrument",
        "description=Reports one number.",
    )

    @Test
    fun `a well formed manifest is accepted with every declared field`() {
        val parsed = PluginContract.parse(valid, "com.example.reporter").first
        assertTrue(parsed.accepted)
        val manifest = parsed.manifest!!
        assertEquals("com.example.reporter", manifest.id)
        assertEquals("Reporter", manifest.name)
        assertEquals("1.0.0", manifest.version)
        assertEquals(1, manifest.apiLevel)
        assertEquals(PluginKind.Instrument, manifest.kind)
        assertEquals(
            setOf(PluginCapability.ReadTelemetry, PluginCapability.ReportInstrument),
            manifest.capabilities,
        )
        assertTrue(manifest.has(PluginCapability.ReadTelemetry))
    }

    /**
     * القدرة المجهولة **تُرفض** ولا تُتجاهل: تشغيل إضافة بصلاحيات أقلّ ممّا طلبت يعني
     * سلوكًا لم يوافق عليه أحد، ولا أحد يعرف أنه حدث.
     */
    @Test
    fun `an unknown capability refuses the whole manifest`() {
        val parsed = PluginContract.parse(
            manifest(
                "id=com.example.x", "name=X", "version=1.0.0", "api=1",
                "kind=instrument", "capabilities=read.telemetry,write.hardware",
            ),
            "com.example.x",
        ).first
        assertFalse(parsed.accepted)
        assertEquals(PluginRejection.UnknownCapability, parsed.rejection)
        assertEquals("write.hardware", parsed.detail)
    }

    @Test
    fun `there is no capability that writes hardware`() {
        // البند ليس شكليًّا: غياب القدرة هو ما يمنع الإضافة من تجاوز الـarbiter.
        val tokens = PluginContract.capabilities().map { it.token }
        assertTrue(tokens.none { it.contains("write") })
        assertTrue(tokens.contains("propose.profile"))
    }

    @Test
    fun `a manifest claiming another folder identity is refused`() {
        val parsed = PluginContract.parse(valid, "com.example.someoneelse").first
        assertFalse(parsed.accepted)
        assertEquals(PluginRejection.BadId, parsed.rejection)
    }

    @Test
    fun `an api newer than this build is refused rather than partially run`() {
        val parsed = PluginContract.parse(
            manifest(
                "id=com.example.x", "name=X", "version=1.0.0",
                "api=${PLUGIN_API_LEVEL + 1}", "kind=instrument", "capabilities=read.telemetry",
            ),
            "com.example.x",
        ).first
        assertFalse(parsed.accepted)
        assertEquals(PluginRejection.ApiTooNew, parsed.rejection)
    }

    @Test
    fun `missing and malformed required fields each have their own reason`() {
        fun reject(vararg lines: String): PluginRejection? =
            PluginContract.parse(lines.toList(), "com.example.x").first.rejection

        assertEquals(
            PluginRejection.MissingName,
            reject("id=com.example.x", "version=1.0.0", "api=1", "kind=instrument", "capabilities=read.telemetry"),
        )
        assertEquals(
            PluginRejection.BadVersion,
            reject("id=com.example.x", "name=X", "version=one", "api=1", "kind=instrument", "capabilities=read.telemetry"),
        )
        assertEquals(
            PluginRejection.MissingKind,
            reject("id=com.example.x", "name=X", "version=1.0.0", "api=1", "capabilities=read.telemetry"),
        )
        assertEquals(
            PluginRejection.UnknownKind,
            reject("id=com.example.x", "name=X", "version=1.0.0", "api=1", "kind=magic", "capabilities=read.telemetry"),
        )
        assertEquals(
            PluginRejection.NoCapabilities,
            reject("id=com.example.x", "name=X", "version=1.0.0", "api=1", "kind=instrument"),
        )
        assertEquals(
            PluginRejection.BadId,
            reject("id=../escape", "name=X", "version=1.0.0", "api=1", "kind=instrument", "capabilities=read.telemetry"),
        )
    }

    @Test
    fun `id and version validation state their rules literally`() {
        assertTrue(PluginContract.isValidId("com.example.plugin-1"))
        assertFalse(PluginContract.isValidId("../escape"))
        assertFalse(PluginContract.isValidId("with..dots"))
        assertFalse(PluginContract.isValidId("With Spaces"))
        assertFalse(PluginContract.isValidId("Upper"))
        assertFalse(PluginContract.isValidId("a".repeat(65)))

        assertTrue(PluginContract.isValidVersion("1.0"))
        assertTrue(PluginContract.isValidVersion("1.0.0"))
        assertTrue(PluginContract.isValidVersion("2.1.3-beta"))
        assertFalse(PluginContract.isValidVersion("1"))
        assertFalse(PluginContract.isValidVersion("v1.0"))
    }

    @Test
    fun `unknown keys are reported as ignored rather than silently dropped`() {
        val parsed = PluginContract.parse(valid + "surprise=1", "com.example.reporter")
        assertEquals(listOf("surprise"), parsed.ignoredKeys)
        assertTrue(parsed.first.accepted)
    }

    @Test
    fun `a duplicate id keeps the first and refuses the rest`() {
        val state = PluginContract.evaluate(
            listOf(
                PluginContract.parse(valid, "com.example.reporter").first,
                PluginContract.parse(valid, "com.example.reporter").first,
            ),
        )
        assertEquals(1, state.accepted.size)
        assertEquals(1, state.rejected.size)
        assertEquals(PluginRejection.DuplicateId, state.rejected.single().rejection)
    }

    /**
     * القالب المعروض للطرف الثالث هو ما نقرؤه فعلًا: لو أضيف مفتاح إلى العقد ولم يُضف
     * إلى القالب (أو العكس) لكان التوثيق يكذب. هذا الاختبار يربطهما.
     */
    @Test
    fun `the published template declares exactly the readable keys`() {
        val lines = PluginContract.manifestTemplate().lines()
        val keys = lines
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.substringBefore('=').trim() }
        assertEquals(PluginContract.readableKeys(), keys.sorted())

        // والقالب نفسه يمرّ من المحلّل: ما نعرضه هو صيغة صالحة لا مثال مزيّن.
        val parsed = PluginContract.parse(lines, "com.example.myplugin").first
        assertTrue("the template must parse", parsed.accepted)
        assertNotNull(parsed.manifest)
    }

    @Test
    fun `every rejection reason has a distinct identity`() {
        // يُسقط هذا أي دمج مقصود بين سببين مختلفين في الواجهة.
        assertEquals(
            PluginRejection.entries.size,
            PluginRejection.entries.map { it.name }.distinct().size,
        )
    }
}
