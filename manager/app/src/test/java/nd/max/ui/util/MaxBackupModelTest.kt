package nd.max.ui.util

import nd.max.ui.util.MaxBackupModel.Availability
import nd.max.ui.util.MaxBackupModel.ComponentKind
import nd.max.ui.util.MaxBackupModel.Handle
import nd.max.ui.util.MaxBackupModel.Integrity
import nd.max.ui.util.MaxBackupModel.Manifest
import nd.max.ui.util.MaxBackupModel.ManifestCodec
import nd.max.ui.util.MaxBackupModel.ManifestEntry
import nd.max.ui.util.MaxBackupModel.RestoreBlock
import nd.max.ui.util.MaxBackupModel.RestoreWarning
import nd.max.ui.util.MaxBackupModel.Scope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * قواعد `Max Backup` الخالصة.
 *
 * ولماذا هذه الاختبارات هي **جوهر** الميزة لا زينة عليها: كل قرار هنا هو قرار «هل نسمح
 * بأن تُكتب بيانات فوق تطبيق؟». ولو انحرف حكم واحد بلا اختبار لما ظهر العيب إلا بعد أن
 * يفقد مستخدم بياناته.
 */
class MaxBackupModelTest {

    private val shaA = "a".repeat(64)
    private val shaB = "b".repeat(64)

    private fun manifest(
        entries: List<ManifestEntry> = listOf(ManifestEntry("app_data.tar.gz", ComponentKind.APP_DATA, 1024, shaA)),
        complete: Boolean = true,
        versionCode: Long = 42,
        deviceModel: String? = "Pixel",
        soc: String? = "sm8650",
        encrypted: Boolean = false,
    ) = Manifest(
        pkg = "com.example.app",
        label = "Example",
        versionCode = versionCode,
        versionName = "1.2.3",
        createdAtMs = 1735689600000L,
        deviceModel = deviceModel,
        soc = soc,
        hadRoot = true,
        encrypted = encrypted,
        complete = complete,
        entries = entries,
    )

    // ── السلامة ─────────────────────────────────────────────────────────────

    @Test
    fun `حكم السلامة لا يرفّع المجهول إلى مقبول`() {
        assertEquals(Integrity.MISSING, MaxBackupModel.integrityOf(shaA, shaA, exists = false))
        // بصمة لم نُسجّلها: لا نعرف. وليست «سليمة» ولا «تالفة».
        assertEquals(Integrity.UNVERIFIABLE, MaxBackupModel.integrityOf(null, shaA, exists = true))
        // الفحص نفسه تعذّر: لا نعرف أيضًا.
        assertEquals(Integrity.UNVERIFIABLE, MaxBackupModel.integrityOf(shaA, null, exists = true))
        assertEquals(Integrity.UNVERIFIABLE, MaxBackupModel.integrityOf(shaA, "", exists = true))
    }

    @Test
    fun `المطابقة لا تفرّق بين حالتي الأحرف والمخالفة تبقى مخالفة`() {
        assertEquals(Integrity.VERIFIED, MaxBackupModel.integrityOf(shaA.uppercase(), shaA, exists = true))
        assertEquals(Integrity.CORRUPT, MaxBackupModel.integrityOf(shaA, shaB, exists = true))
    }

    // ── بوابة الاسترجاع ─────────────────────────────────────────────────────

    @Test
    fun `نسخة بلا مدخلات لا تُسترجع`() {
        val decision = MaxBackupModel.restoreDecision(
            manifest = manifest(entries = emptyList()),
            verdicts = emptyMap(),
            hasRoot = true,
            currentDeviceModel = "Pixel",
            currentSoc = "sm8650",
            currentVersionCode = 42,
            packageInstalled = true,
        )
        assertEquals(RestoreBlock.NO_ENTRIES, decision.block)
        assertFalse(decision.allowed)
    }

    @Test
    fun `النسخة الناقصة تُرفض ولو كانت بصماتها سليمة`() {
        val decision = MaxBackupModel.restoreDecision(
            manifest = manifest(complete = false),
            verdicts = mapOf("app_data.tar.gz" to Integrity.VERIFIED),
            hasRoot = true,
            currentDeviceModel = "Pixel",
            currentSoc = "sm8650",
            currentVersionCode = 42,
            packageInstalled = true,
        )
        assertEquals(RestoreBlock.INCOMPLETE, decision.block)
    }

    @Test
    fun `ملف واحد بلا بصمة يمنع الاسترجاع كاملًا`() {
        val entries = listOf(
            ManifestEntry("app_data.tar.gz", ComponentKind.APP_DATA, 10, shaA),
            ManifestEntry("external.tar.gz", ComponentKind.EXTERNAL_DATA, 10, null),
        )
        val decision = MaxBackupModel.restoreDecision(
            manifest = manifest(entries = entries),
            verdicts = mapOf(
                "app_data.tar.gz" to Integrity.VERIFIED,
                "external.tar.gz" to Integrity.UNVERIFIABLE,
            ),
            hasRoot = true,
            currentDeviceModel = "Pixel",
            currentSoc = "sm8650",
            currentVersionCode = 42,
            packageInstalled = true,
        )
        assertEquals(RestoreBlock.NOT_VERIFIED, decision.block)
    }

    @Test
    fun `بلا جذر يُمنع الاسترجاع بسبب معلَن لا بفشل صامت`() {
        val decision = MaxBackupModel.restoreDecision(
            manifest = manifest(),
            verdicts = mapOf("app_data.tar.gz" to Integrity.VERIFIED),
            hasRoot = false,
            currentDeviceModel = "Pixel",
            currentSoc = "sm8650",
            currentVersionCode = 42,
            packageInstalled = true,
        )
        assertEquals(RestoreBlock.ROOT_REQUIRED, decision.block)
    }

    @Test
    fun `التحذيرات لا تمنع والمانع لا يتنكّر في تحذير`() {
        val decision = MaxBackupModel.restoreDecision(
            manifest = manifest(deviceModel = "OtherPhone", soc = "mt6989", versionCode = 7),
            verdicts = mapOf("app_data.tar.gz" to Integrity.VERIFIED),
            hasRoot = true,
            currentDeviceModel = "Pixel",
            currentSoc = "sm8650",
            currentVersionCode = 42,
            packageInstalled = false,
        )
        assertTrue(decision.allowed)
        assertEquals(RestoreBlock.NONE, decision.block)
        assertTrue(RestoreWarning.DEVICE_MISMATCH in decision.warnings)
        assertTrue(RestoreWarning.SOC_MISMATCH in decision.warnings)
        assertTrue(RestoreWarning.APP_VERSION_DIFFERS in decision.warnings)
        assertTrue(RestoreWarning.PACKAGE_NOT_INSTALLED in decision.warnings)
    }

    @Test
    fun `كل تحذير له سبب يُنتجه ولو لم يكن هناك خلل مانع`() {
        val decision = MaxBackupModel.restoreDecision(
            manifest = manifest(encrypted = true),
            verdicts = mapOf("app_data.tar.gz" to Integrity.VERIFIED),
            hasRoot = true,
            currentDeviceModel = "Pixel",
            currentSoc = "sm8650",
            currentVersionCode = 42,
            packageInstalled = true,
        )
        assertTrue(decision.allowed)
        assertEquals(listOf(RestoreWarning.ENCRYPTED_ARCHIVE), decision.warnings)
    }

    @Test
    fun `الجهاز نفسه لا يُنتج تحذير اختلاف`() {
        val decision = MaxBackupModel.restoreDecision(
            manifest = manifest(deviceModel = "SM-8650", soc = "SM8650"),
            verdicts = mapOf("app_data.tar.gz" to Integrity.VERIFIED),
            hasRoot = true,
            currentDeviceModel = "sm-8650",
            currentSoc = "sm8650",
            currentVersionCode = 42,
            packageInstalled = true,
        )
        assertEquals(emptyList<RestoreWarning>(), decision.warnings)
    }

    @Test
    fun `اسم غير مقروء ليس دليل تطابق`() {
        assertFalse(MaxBackupModel.sameIdentifier(null, null))
        assertFalse(MaxBackupModel.sameIdentifier("", "  "))
        assertFalse(MaxBackupModel.sameIdentifier("Pixel", null))
        assertTrue(MaxBackupModel.sameIdentifier(" SM-8650 ", "sm-8650"))
        // والفواصل لا تُطبَّع: اختلافها اختلاف معلَن، ولا نُخفيه باسم «تطبيع».
        assertFalse(MaxBackupModel.sameIdentifier("SM-8650", "SM8650"))
    }

    // ── المستند ─────────────────────────────────────────────────────────────

    @Test
    fun `الترميز والفكّ يحفظان كل حقل`() {
        val original = manifest(
            entries = listOf(
                ManifestEntry("base_0.apk", ComponentKind.APK, 2048, shaB),
                ManifestEntry("app_data.tar.gz", ComponentKind.APP_DATA, 1024, null),
            ),
        )
        val decoded = ManifestCodec.decode(ManifestCodec.encode(original))!!
        assertEquals(original.pkg, decoded.pkg)
        assertEquals(original.versionCode, decoded.versionCode)
        assertEquals(original.createdAtMs, decoded.createdAtMs)
        assertEquals(original.soc, decoded.soc)
        assertEquals(2, decoded.entries.size)
        assertEquals(shaB, decoded.entries[0].sha256)
        // وبصمة غائبة تبقى غائبة عبر الترميز، فلا تصير سلسلة فارغة تُقرأ لاحقًا خطأً.
        assertNull(decoded.entries[1].sha256)
    }

    @Test
    fun `مستند بإصدار لا نفهمه يُرفض ولا يُفسَّر`() {
        val foreign = ManifestCodec.encode(manifest()).replace("\"schema\":1", "\"schema\":99")
        assertNull(ManifestCodec.decode(foreign))
    }

    @Test
    fun `المستند الناقص يُرفض ولا تُملأ فراغاته بقيم افتراضية`() {
        assertNull(ManifestCodec.decode("{}"))
        assertNull(ManifestCodec.decode("not json at all"))
        val missingEntries = ManifestCodec.encode(manifest()).replace("\"entries\":[", "\"entries_moved\":[")
        assertNull(ManifestCodec.decode(missingEntries))
    }

    @Test
    fun `مكوّن بمعرّف مجهول يُرفض بدل أن يُقرأ خطأً`() {
        val weird = ManifestCodec.encode(manifest()).replace("\"kind\":\"data\"", "\"kind\":\"telepathy\"")
        assertNull(ManifestCodec.decode(weird))
    }

    // ── الاحتفاظ ────────────────────────────────────────────────────────────

    @Test
    fun `الاحتفاظ لا يحذف آخر نسخة أبدًا`() {
        val one = listOf(handle(1_000))
        assertEquals(emptyList<Handle>(), MaxBackupModel.toPrune(one, 0))
        assertEquals(emptyList<Handle>(), MaxBackupModel.toPrune(one, -5))
    }

    @Test
    fun `الاحتفاظ يُبقي الأحدث ويُقلّم الباقي`() {
        val handles = listOf(handle(1_000), handle(5_000), handle(3_000), handle(2_000), handle(4_000))
        val pruned = MaxBackupModel.toPrune(handles, 3).map { it.createdAtMs }
        assertEquals(listOf(2_000L, 1_000L), pruned)
    }

    // ── النطاق والأهداف والتسمية ────────────────────────────────────────────

    @Test
    fun `النطاق الافتراضي بلا جذر لا يُشعل ما لا يعمل`() {
        val withoutRoot = Scope.forPrivilege(hasRoot = false)
        assertTrue(withoutRoot.apk)
        assertFalse(withoutRoot.appData)
        assertFalse(withoutRoot.externalData)
        assertFalse(withoutRoot.obb)

        assertEquals(Scope(), Scope.forPrivilege(hasRoot = true))
    }

    @Test
    fun `اختيار النطاق يطابق أنواع المكوّنات`() {
        val scope = Scope(apk = true, appData = false, externalData = true, obb = false)
        assertTrue(scope.selects(ComponentKind.APK))
        assertTrue(scope.selects(ComponentKind.SPLIT_APK))
        assertFalse(scope.selects(ComponentKind.APP_DATA))
        assertTrue(scope.selects(ComponentKind.EXTERNAL_DATA))
        assertFalse(scope.selects(ComponentKind.OBB))
        assertFalse(Scope(false, false, false, false).anySelected)
    }

    @Test
    fun `الـAPK لا يُنسخ كملف مباشر إلى مجلد التطبيق`() {
        assertNull(MaxBackupModel.destinationOf(ComponentKind.APK, "com.example.app"))
        assertNull(MaxBackupModel.destinationOf(ComponentKind.SPLIT_APK, "com.example.app"))
        assertEquals("/data/data/com.example.app", MaxBackupModel.destinationOf(ComponentKind.APP_DATA, "com.example.app"))
        assertEquals("/sdcard/Android/data/com.example.app", MaxBackupModel.destinationOf(ComponentKind.EXTERNAL_DATA, "com.example.app"))
        assertEquals("/sdcard/Android/obb/com.example.app", MaxBackupModel.destinationOf(ComponentKind.OBB, "com.example.app"))
    }

    @Test
    fun `أهداف الاسترجاع مشتقة من المدخلات لا من النية`() {
        val entries = listOf(
            ManifestEntry("base_0.apk", ComponentKind.APK, 1, shaA),
            ManifestEntry("app_data.tar.gz", ComponentKind.APP_DATA, 1, shaA),
        )
        val targets = MaxBackupModel.restoreTargets(manifest(entries = entries))
        assertTrue(MaxBackupModel.RestoreTarget.REINSTALL_PACKAGE in targets)
        assertTrue(MaxBackupModel.RestoreTarget.APP_DATA in targets)
        assertFalse(MaxBackupModel.RestoreTarget.OBB in targets)
    }

    @Test
    fun `اسم المجلد زمني بترتيب يطابق الترتيب النصّي`() {
        assertEquals("20250101-000000", MaxBackupModel.folderName(1735689600000L))
        assertTrue(MaxBackupModel.folderName(1_000) < MaxBackupModel.folderName(2_000))
    }

    @Test
    fun `القياس المقروء لا يدّعي دقّة أعلى من المصدر`() {
        assertEquals("512 B", MaxBackupModel.humanBytes(512))
        assertEquals("1.0 KB", MaxBackupModel.humanBytes(1024))
        assertEquals("1.0 MB", MaxBackupModel.humanBytes(1024L * 1024))
        assertEquals("1.5 GB", MaxBackupModel.humanBytes((1.5 * 1024 * 1024 * 1024).toLong()))
        assertEquals("-", MaxBackupModel.humanBytes(-1))
    }

    // ── الجرد ───────────────────────────────────────────────────────────────

    @Test
    fun `حجم لم يُقس لا يُحسب صفرًا ولا يُخفي نفسه`() {
        val plan = MaxBackupModel.Plan(
            pkg = "com.example.app",
            label = "Example",
            versionCode = 1,
            versionName = "1.0",
            isSystem = false,
            uid = 1000,
            hasRoot = false,
            components = listOf(
                MaxBackupModel.PlannedComponent(ComponentKind.APK, "/data/app/x/base.apk", 4096, Availability.AVAILABLE),
                MaxBackupModel.PlannedComponent(ComponentKind.APP_DATA, "/data/data/x", null, Availability.NEEDS_ROOT),
            ),
        )
        assertEquals(4096L, plan.knownBytes)
        assertTrue(plan.hasUnmeasured)
        // بيانات التطبيق وحدها محدَّدة ⇒ لا شيء متاح لهذه الطبقة، فيقولها بدل أن يَعِد.
        assertFalse(plan.canBackupData(Scope(apk = false, appData = true, externalData = false, obb = false)))
        assertTrue(plan.canBackupData(Scope(apk = true, appData = false, externalData = false, obb = false)))
        assertEquals(1, plan.selected(Scope(apk = true, appData = false, externalData = false, obb = false)).size)
    }

    @Test
    fun `مكوّن غير متاح بحجم مقيس صفر لا يُعدّ غير مقيس`() {
        val component = MaxBackupModel.PlannedComponent(
            kind = ComponentKind.OBB,
            source = "/sdcard/Android/obb/x",
            sizeBytes = 0L,
            availability = Availability.UNAVAILABLE,
        )
        val plan = MaxBackupModel.Plan(
            pkg = "com.example.app",
            label = null,
            versionCode = 1,
            versionName = null,
            isSystem = false,
            uid = null,
            hasRoot = true,
            components = listOf(component),
        )
        assertFalse(plan.hasUnmeasured)
        assertEquals(0L, plan.knownBytes)
    }

    private fun handle(createdAtMs: Long) = Handle(
        pkg = "com.example.app",
        folder = "/backup/$createdAtMs",
        createdAtMs = createdAtMs,
        bytes = 1,
        complete = true,
        entryCount = 1,
        encrypted = false,
    )
}
