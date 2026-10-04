/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.contract

import nd.max.core.platform.AppStatusProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * عقد `app_status` من طرف الكاتب — النصف Kotlin من `fixtures/contracts/app_status.valid.txt`.
 *
 * لماذا هذا الاختبار قويّ رغم أنه مقارنة نصّين
 * -------------------------------------------
 * لأن الطرف الآخر يقيس الملف **نفسه**: `archdaemon/tests/file_protocols.c` يُمرّر
 * `app_status.valid.txt` إلى `read_app_status` الحقيقي ويؤكّد أنه يقرأ منه القيم المُعلَنة
 * (الحزمة · pid · المستوى · اسم التطبيق · معرّف التبديل). فالملف نقطة التحام بين لغتين:
 * إن انحرف الترميز هنا أو الانحلال هناك، سقط أحد الطرفين — ولا يبقى الشكل ضمنيًّا في دالّة
 * داخل `buildStatus()` لا يستطيع أحد استدعاؤها.
 *
 * ولا يُعدَّل الـfixture لتمرير هذا الاختبار: إن فشل فالسؤال «أيّ الطرفين تغيّر حقًّا؟»،
 * والجواب يُقاس لا يُقدَّر (راجع `fixtures/contracts/README.md`).
 */
class AppStatusProtocolContractTest {

    /**
     * قيم المرجع. وهي **المصدر المشترك**: `file_protocols.c` يؤكّد الأرقام نفسها من ملف
     * `app_status.valid.txt` نفسه، فالرقم مكتوب مرّة في البيانات ويُقاس في اللغتين.
     */
    private val focusedApp = "com.example.game 12288 10123"
    private val screenAwake = 1
    private val batterySaver = 0
    private val zenMode = 0
    private val batteryLevel = 87
    private val isCharging = 0
    private val appName = "Example Game"
    private val refreshRate = 120
    private val maxRefreshRate = 120
    private val switchId = "sw-1758888888000"
    private val perAppOverridesActive = true

    private fun encodeFixtureValues(): String = AppStatusProtocol.encode(
        focusedApp = focusedApp,
        screenAwake = screenAwake,
        batterySaver = batterySaver,
        zenMode = zenMode,
        batteryLevel = batteryLevel,
        isCharging = isCharging,
        appName = appName,
        currentRefreshRate = refreshRate,
        maxRefreshRate = maxRefreshRate,
        switchId = switchId,
        perAppOverridesActive = perAppOverridesActive,
    )

    @Test
    fun `the encoder reproduces the shared fixture byte for byte`() {
        val expected = ContractFixtures.text("app_status.valid.txt")
        assertEquals(
            "the writer and fixtures/contracts/app_status.valid.txt disagree; " +
                "the C reader asserts the values in that file, so one of the two moved",
            expected,
            encodeFixtureValues(),
        )
    }

    @Test
    fun `every declared field is present exactly once and the daemon-consumed set is a subset`() {
        val text = encodeFixtureValues()
        val keys = text.lineSequence()
            .filter { it.isNotBlank() }
            .map { it.substringBefore(' ') }
            .toList()

        val declared = listOf(
            AppStatusProtocol.FIELD_VERSION,
            AppStatusProtocol.FIELD_FOCUSED_APP,
            AppStatusProtocol.FIELD_SCREEN_AWAKE,
            AppStatusProtocol.FIELD_BATTERY_SAVER,
            AppStatusProtocol.FIELD_ZEN_MODE,
            AppStatusProtocol.FIELD_BATTERY_LEVEL,
            AppStatusProtocol.FIELD_IS_CHARGING,
            AppStatusProtocol.FIELD_APP_NAME,
            AppStatusProtocol.FIELD_REFRESH_RATE,
            AppStatusProtocol.FIELD_MAX_REFRESH_RATE,
            AppStatusProtocol.FIELD_SWITCH_ID,
            AppStatusProtocol.FIELD_PERAPP_ACTIVE,
        )
        assertEquals("a declared field was dropped or an undeclared one was added", declared, keys)
        assertEquals("no key may repeat: the reader would keep the last one silently",
            declared.size, keys.toSet().size)

        // كل حقل يقرؤه الخادم موجود فعلًا — وإلا لقرأ الافتراضي بلا أن يعلم أحد.
        val consumedMissing = AppStatusProtocol.DAEMON_CONSUMED_FIELDS.filterNot { it in keys }
        assertTrue("fields the daemon parses are missing from the writer: $consumedMissing", consumedMissing.isEmpty())
    }

    @Test
    fun `MODULE_VERSION is the single version string — and never the protocol revision`() {
        // **مُصحَّح ومُقاس (تكملة ١٤٢):** السطر **لا** يحمل سلسلة بناء مُشتقّة. الخادم يقارنه
        // بحرف واحد بـ`module.prop`: `check_module_version()` في `ModuleIntegrity.c:44` يُنفّذ
        // `grep -q '^version=%s$' module.prop` ويُخرج الخادم بـ`EXIT_FAILURE` عند أي اختلاف
        // (`EVENT=MODULE_INTEGRITY_FAILED reason=version_mismatch`). و`compile_zip.sh` لا يكتب
        // `version=` إطلاقًا (مصدر واحد)، فهي تبقى قيمة ملف `version`. ⇒ الرأس يُختم **بملف
        // `version` نفسه** في `verify.sh`، والثلاثة تتفق بايت ببايت (`version` ⇒ `module.prop`
        // ⇒ `MaxManager.h`) — وهي بوّابة `version_triangle` و`build.yml` معًا.
        //
        // **والحرس القديم كان يسقط على كل شجرة نظيفة وكل تشغيل CI** ولم يسقط في الجولة التي
        // كُتب فيها: كان يشترط إمّا `V<n>` وإمّا `<نسخة> (<عدّ>-<sha>-<نوع>)` — وبعد أن صارت
        // السلسلة `v1.0` (تكملة ١٤٠) لم يقبلهما شكل. وسبب صمته: CI كان يُسقط التشغيل **قبل**
        // الاختبارات في `version_triangle`. فلا يُعاد إلى شرط شكل: يُقاس **الاتفاق** الذي يقيسه
        // الخادم نفسه، وتُمنع إعادة الخلط بإصدار البروتوكول صراحةً.
        val root = ContractFixtures.repoRoot
        val header = File(root, "archdaemon/jni/include/MaxManager.h")
        assumeTrue("MaxManager.h not reachable; guard not evaluated", header.isFile)
        val value = Regex("""#define MODULE_VERSION "([^"]+)""").find(header.readText())?.groupValues?.get(1)
        assertNotNull("MaxManager.h must declare MODULE_VERSION", value)

        val declared = File(root, "version")
        assumeTrue("the `version` file is not reachable; guard not evaluated", declared.isFile)
        val expected = declared.readText().trim()
        assertTrue("the `version` file is empty — this guard would pass over any header", expected.isNotEmpty())
        assertEquals(
            "MODULE_VERSION must equal the `version` file byte-for-byte: the daemon greps module.prop " +
                "with it at boot and exits with EXIT_FAILURE on any difference",
            expected,
            value,
        )
        assertNotEquals(
            "MODULE_VERSION is a build version, not the protocol revision — a value shaped like the " +
                "protocol revision would re-create the confusion this guard exists to prevent",
            "V${AppStatusProtocol.VERSION}",
            value,
        )
    }

    @Test
    fun `the C reader parses exactly the fields Kotlin declares as daemon-consumed`() {
        // عقد الأسماء بين اللغتين كان **مُعلَنًا** في `DAEMON_CONSUMED_FIELDS` و**غير مقيس في C**:
        // إعادة تسمية حقل في Kotlin تعني أن الخادم يقرأ **الافتراضي بصمت** (لا خطأ ولا سطر عطل) —
        // وهو صنف العطب الصامت نفسه. فتُقرأ بادئات `strncmp(line, "<field> ", n)` من المصدر الحقيقي.
        val src = File(ContractFixtures.repoRoot, "archdaemon/jni/src/AppLoader/StatusMonitor.c")
        assumeTrue("StatusMonitor.c not reachable; guard not evaluated", src.isFile)
        val parsed = Regex("""strncmp\(line,\s*"([a-z_]+) """).findAll(src.readText())
            .map { it.groupValues[1] }
            .toSet()
        assertTrue("the C scan found no field prefix at all — it would pass over any tree", parsed.isNotEmpty())
        assertEquals(
            "the daemon's parsed fields and AppStatusProtocol.DAEMON_CONSUMED_FIELDS drifted apart",
            AppStatusProtocol.DAEMON_CONSUMED_FIELDS.toSet(),
            parsed,
        )
        assertTrue(
            "the version line must not be read by C as a data field — the handshake is informational, and " +
                "its absence from the parser is why adding a field stays an addition, not a break",
            AppStatusProtocol.FIELD_VERSION !in parsed,
        )
    }

    @Test
    fun `the handshake line is the first line so the reader can skip it before any field`() {
        val first = encodeFixtureValues().lineSequence().first()
        assertEquals(
            "the version handshake must lead the file; the daemon skips unknown keys, so a late " +
                "handshake would be indistinguishable from data",
            "${AppStatusProtocol.FIELD_VERSION} ${AppStatusProtocol.VERSION}",
            first,
        )
    }
}
