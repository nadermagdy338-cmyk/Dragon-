package nd.max.ui.util

import nd.max.ui.util.MaxBackupSystem.Availability
import nd.max.ui.util.MaxBackupSystem.Kind
import nd.max.ui.util.MaxBackupSystem.RestoreBlock
import nd.max.ui.util.MaxBackupSystem.RestoreWarning
import nd.max.ui.util.MaxBackupSystem.Semantics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * قواعد **بيانات النظام** الخالصة.
 *
 * وكل اختبار هنا يثبّت قرارًا يُتّخذ **قبل** الكتابة: هل نُدرج هذا الصف أم نتخطّاه؟ وهل نفكّ
 * هذا الأرشيف أصلًا؟ وهل نعرض زر استرجاع لفئة تحجب المنصّة كتابتها؟ — وقرار خاطئ واحد في هذه
 * القائمة يعني إمّا تكرار بيانات المستخدم أو الكتابة في مسار لم نُعلنه.
 */
class MaxBackupSystemTest {

    // ── تطبيع الأرقام ───────────────────────────────────────────────────────

    @Test
    fun `الرقم يُطبَّع بآخر تسع خانات وبادئة الدولة لا تفرّق`() {
        // نفس الرقم بصيغتين: بصفرين دوليين، وبمسافات، وبشرطة.
        assertEquals("771234567", MaxBackupSystem.normalizeNumber("+967 771 234 567"))
        assertEquals("771234567", MaxBackupSystem.normalizeNumber("00967771234567"))
        assertEquals("771234567", MaxBackupSystem.normalizeNumber("771-234-567"))
    }

    @Test
    fun `ما لا يميّز رقمًا لا يُعتبر مفتاحًا`() {
        assertNull(MaxBackupSystem.normalizeNumber(null))
        assertNull(MaxBackupSystem.normalizeNumber(""))
        // خدمة قصيرة: «٤ أرقام» لا تميّز رقمًا عن آخر، والبناء عليها تطبيع كاذب.
        assertNull(MaxBackupSystem.normalizeNumber("1234"))
        assertEquals("123456", MaxBackupSystem.normalizeNumber("123456"))
    }

    // ── المفاتيح الطبيعية ───────────────────────────────────────────────────

    @Test
    fun `مفتاح المكالمة يجمع الرقم والتاريخ والنوع والمدّة`() {
        val a = MaxBackupSystem.naturalKey(
            Kind.CALL_LOG,
            mapOf("number" to "+967771234567", "date" to "1700000000000", "type" to "1", "duration" to "42"),
        )
        val b = MaxBackupSystem.naturalKey(
            Kind.CALL_LOG,
            mapOf("number" to "00967771234567", "date" to "1700000000000", "type" to "1", "duration" to "42"),
        )
        assertEquals(a, b)
        // ومدّة مختلفة مكالمة أخرى — لا الصف نفسه.
        val c = MaxBackupSystem.naturalKey(
            Kind.CALL_LOG,
            mapOf("number" to "00967771234567", "date" to "1700000000000", "type" to "1", "duration" to "43"),
        )
        assertFalse(a == c)
    }

    @Test
    fun `صف بلا رقم أو بلا تاريخ لا مفتاح له`() {
        assertNull(MaxBackupSystem.naturalKey(Kind.CALL_LOG, mapOf("number" to null, "date" to "1")))
        assertNull(MaxBackupSystem.naturalKey(Kind.CALL_LOG, mapOf("number" to "771234567", "date" to null)))
        // جهة بلا اسم: الاسم هو ما يميّزها عندنا، وبلا اسم لا نَدّعي تطبيعًا.
        assertNull(MaxBackupSystem.naturalKey(Kind.CONTACTS, mapOf("display_name" to "", "number" to "771234567")))
    }

    @Test
    fun `مفتاح الجهة يوازن حالة الأحرف ويشمل الرقم`() {
        val a = MaxBackupSystem.naturalKey(
            Kind.CONTACTS,
            mapOf("display_name" to "Ahmed", "number" to "+967771234567", "email" to "A@B.com"),
        )
        val b = MaxBackupSystem.naturalKey(
            Kind.CONTACTS,
            mapOf("display_name" to "  ahmed ", "number" to "00967771234567", "email" to "a@b.com"),
        )
        assertEquals(a, b)
    }

    @Test
    fun `كلمة القاموس تُطبَّع بالحالة واللغة`() {
        val a = MaxBackupSystem.naturalKey(Kind.USER_DICTIONARY, mapOf("word" to "MaxManager", "locale" to "en_US"))
        val b = MaxBackupSystem.naturalKey(Kind.USER_DICTIONARY, mapOf("word" to " maxmanager ", "locale" to "en_US"))
        assertEquals(a, b)
        assertNull(MaxBackupSystem.naturalKey(Kind.USER_DICTIONARY, mapOf("word" to "   ")))
    }

    // ── الدمج ───────────────────────────────────────────────────────────────

    @Test
    fun `الدمج يتخطّى الموجود ويُدرج الجديد`() {
        val existing = setOf("771234567|1|1|10")
        val incoming = listOf(
            mapOf("number" to "771234567", "date" to "1", "type" to "1", "duration" to "10"),
            mapOf("number" to "771999999", "date" to "1", "type" to "1", "duration" to "10"),
        )
        val plan = MaxBackupSystem.mergePlan(Kind.CALL_LOG, existing, incoming)
        assertEquals(1, plan.insert.size)
        assertEquals(1, plan.skippedExisting)
        assertEquals(0, plan.undedupable)
    }

    @Test
    fun `الدمج لا يُدرج الصف نفسه مرتين من نفس النسخة`() {
        val incoming = listOf(
            mapOf("number" to "771234567", "date" to "1", "type" to "1", "duration" to "10"),
            mapOf("number" to "00967771234567", "date" to "1", "type" to "1", "duration" to "10"),
        )
        val plan = MaxBackupSystem.mergePlan(Kind.CALL_LOG, emptySet(), incoming)
        assertEquals(1, plan.insert.size)
        assertEquals(1, plan.skippedExisting)
    }

    @Test
    fun `الصف بلا مفتاح يُدرَج لكن يُعَدّ ولا يُدّعى أنه لن يتكرّر`() {
        val incoming = listOf(mapOf("number" to "12", "date" to "1"))
        val plan = MaxBackupSystem.mergePlan(Kind.CALL_LOG, emptySet(), incoming)
        // البيانات أهم من نظافة العدّ ⇒ تُدرَج.
        assertEquals(1, plan.insert.size)
        // لكنها تُعَدّ باسمها، فلا نقول «لا تكرار» بلا سند.
        assertEquals(1, plan.undedupable)
        assertEquals(0, plan.skippedExisting)
    }

    // ── بوابة الاسترجاع ─────────────────────────────────────────────────────

    @Test
    fun `الرسائل تُقرأ وتُؤرشف لكن استرجاعها محجوب بسياسة المنصّة`() {
        val decision = MaxBackupSystem.restoreDecision(
            kind = Kind.SMS,
            entryCount = 1,
            availability = Availability.AVAILABLE,
            hasRoot = true,
            permissionsGranted = true,
        )
        assertEquals(RestoreBlock.READ_ONLY_BY_POLICY, decision.block)
        assertFalse(decision.allowed)
        assertTrue(RestoreWarning.BLOCKED_BY_PLATFORM in decision.warnings)
    }

    @Test
    fun `ملفات النظام بلا جذر تُمنع بسبب معلَن`() {
        val decision = MaxBackupSystem.restoreDecision(
            kind = Kind.WIFI,
            entryCount = 1,
            availability = Availability.AVAILABLE,
            hasRoot = false,
            permissionsGranted = true,
        )
        assertEquals(RestoreBlock.NEEDS_ROOT, decision.block)
    }

    @Test
    fun `فئة المزوّد بلا إذن تُمنع بسبب مختلف عن الحجب السياسي`() {
        val decision = MaxBackupSystem.restoreDecision(
            kind = Kind.CONTACTS,
            entryCount = 1,
            availability = Availability.AVAILABLE,
            hasRoot = true,
            permissionsGranted = false,
        )
        assertEquals(RestoreBlock.NEEDS_PERMISSION, decision.block)
    }

    @Test
    fun `فئة المزوّد بإذن تُسمح وتُعلن أن الاسترجاع دمج`() {
        val decision = MaxBackupSystem.restoreDecision(
            kind = Kind.CALL_LOG,
            entryCount = 1,
            availability = Availability.AVAILABLE,
            hasRoot = true,
            permissionsGranted = true,
        )
        assertEquals(RestoreBlock.NONE, decision.block)
        assertTrue(decision.allowed)
        assertTrue(RestoreWarning.MERGE_ADDS_ROWS in decision.warnings)
    }

    @Test
    fun `وثيقة بلا هذه الفئة لا تُسترجع`() {
        val decision = MaxBackupSystem.restoreDecision(
            kind = Kind.WIFI,
            entryCount = 0,
            availability = Availability.AVAILABLE,
            hasRoot = true,
            permissionsGranted = true,
        )
        assertEquals(RestoreBlock.EMPTY, decision.block)
    }

    // ── حرس مسارات الأرشيف ──────────────────────────────────────────────────

    @Test
    fun `المسارات المُعلَنة وحدها تُقبل في الأرشيف`() {
        assertTrue(
            MaxBackupSystemEngine.tarEntriesAllowed(
                Kind.WIFI,
                listOf("data/misc/apexdata/com.android.wifi/WifiConfigStore.xml"),
            )
        )
        // نفس المدخل بادئة `./` — تطبيع لا تجاوز.
        assertTrue(
            MaxBackupSystemEngine.tarEntriesAllowed(
                Kind.WIFI,
                listOf("./data/misc/wifi/WifiConfigStore.xml"),
            )
        )
    }

    @Test
    fun `مسار غير مُعلَن يرفض الأرشيف كاملًا`() {
        // هذا هو الفرق بين «استرجاع» و«كتابة في مكان يختاره أحدهم».
        assertFalse(
            MaxBackupSystemEngine.tarEntriesAllowed(
                Kind.WIFI,
                listOf(
                    "data/misc/wifi/WifiConfigStore.xml",
                    "data/adb/modules/MaxManager/system/etc/hosts",
                ),
            )
        )
        assertFalse(MaxBackupSystemEngine.tarEntriesAllowed(Kind.WIFI, emptyList()))
        // ومسارات البلوتوث لا تُقبل لأرشيف واي‑فاي: الحرس لكل فئة على حدة.
        assertFalse(
            MaxBackupSystemEngine.tarEntriesAllowed(
                Kind.WIFI,
                listOf("data/misc/bluedroid/bt_config.conf"),
            )
        )
    }

    // ── صيغة الأرشيف ────────────────────────────────────────────────────────

    @Test
    fun `أرشيف الصفوف يرمّز ويفكّ بلا فقدان`() {
        val rows = listOf(
            mapOf("number" to "771234567", "date" to "1", "type" to "1", "duration" to "10"),
            mapOf("number" to null, "date" to "2", "type" to "2", "duration" to null),
        )
        val file = kotlin.io.path.createTempFile("maxbackup", ".json").toFile()
        try {
            file.writeText(MaxBackupSystemEngine.encodeRows(Kind.CALL_LOG, rows))
            val decoded = MaxBackupSystemEngine.readProviderArchive(file, Kind.CALL_LOG)
            assertEquals(2, decoded?.size)
            assertEquals("771234567", decoded?.get(0)?.get("number"))
            // والقيمة الغائبة تبقى غائبة: لا تتحوّل إلى سلسلة فارغة تُقرأ لاحقًا رقمًا.
            assertNull(decoded?.get(1)?.get("number"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `أرشيف بنوع مختلف أو بإصدار لا نفهمه يُرفض`() {
        val file = kotlin.io.path.createTempFile("maxbackup", ".json").toFile()
        try {
            file.writeText(MaxBackupSystemEngine.encodeRows(Kind.CALL_LOG, emptyList()))
            // نفس الملف بنوع آخر: لا يُفكّ إلى النوع الخطأ.
            assertNull(MaxBackupSystemEngine.readProviderArchive(file, Kind.SMS))
            file.writeText("""{"schema":99,"kind":"call_log","rows":[]}""")
            assertNull(MaxBackupSystemEngine.readProviderArchive(file, Kind.CALL_LOG))
            file.writeText("not json")
            assertNull(MaxBackupSystemEngine.readProviderArchive(file, Kind.CALL_LOG))
        } finally {
            file.delete()
        }
    }

    // ── النطاق والجدول ──────────────────────────────────────────────────────

    @Test
    fun `النطاق الافتراضي هو ما يمكن أرشفته فعلًا`() {
        val plan = MaxBackupSystem.Plan(
            hasRoot = false,
            components = listOf(
                MaxBackupSystem.Component(Kind.WIFI, Availability.NEEDS_ROOT, null),
                MaxBackupSystem.Component(Kind.CONTACTS, Availability.AVAILABLE, 100L, rowCount = 3),
                MaxBackupSystem.Component(Kind.SMS, Availability.NEEDS_PERMISSION, null),
            ),
        )
        val scope = MaxBackupSystem.Scope.from(plan)
        assertTrue(scope.selects(Kind.CONTACTS))
        assertFalse(scope.selects(Kind.WIFI))
        assertFalse(scope.selects(Kind.SMS))
        assertTrue(scope.toggle(Kind.WIFI, true).selects(Kind.WIFI))
        assertFalse(scope.anySelected.not())
    }

    @Test
    fun `كل فئة في الجدول لها مصدر معلَن وملف فريد`() {
        Kind.entries.forEach { kind ->
            val source = MaxBackupSystem.source(kind)
            assertTrue("لا مصدر للفئة ${kind.id}", source != null)
            if (source?.method == MaxBackupSystem.Method.ROOT_FILES) {
                assertTrue("فئة ملفّية بلا مسار: ${kind.id}", source.paths.isNotEmpty())
            } else {
                assertTrue("فئة مزوّد بلا سلطة: ${kind.id}", !source?.authority.isNullOrBlank())
            }
            assertEquals(kind, MaxBackupSystem.kindOfEntry(kind.entryFile))
        }
        val files = Kind.entries.map { it.entryFile }
        assertEquals(files.size, files.distinct().size)
    }

    @Test
    fun `الفئة الوحيدة المحجوبة كتابةً هي الرسائل`() {
        val readOnly = Kind.entries.filter {
            MaxBackupSystem.source(it)?.semantics == Semantics.READ_ONLY_BY_POLICY
        }
        assertEquals(listOf(Kind.SMS), readOnly)
    }
}
