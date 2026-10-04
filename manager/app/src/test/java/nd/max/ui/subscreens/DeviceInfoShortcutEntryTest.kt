/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.subscreens

import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.deviceInfoRouteOf
import nd.max.ui.navigation.launchRouteNeedsArgument
import nd.max.ui.navigation.launchRouteOf
import nd.max.ui.navigation.maxHubRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * حرّاس **الاتجاه العكسي**: من الشاشة التي تعمل على الموضوع إلى القسم الذي يشرحه في
 * «معلومات الجهاز» (الحرارة ← قسم الحرارة · التشخيص ← المستشعرات · الذاكرة ← قسم الذاكرة).
 *
 * **ولماذا اختبار لا مراجعة بالعين:** الزرّ يقترن **باسم وجهته**، ووجهةٌ في زرٍّ خطأ تفتح قسمًا
 * آخر — عطب صامت لا يظهر في صورة ولا في مراجعة سطر. بدلًا من ذلك يقيس الاختبار ثلاثة أشياء
 * على **السجلّ نفسه**: (١) كل شاشة في الخريطة تُرجع قسمًا موجودًا، (٢) القسم المُعلَن في الزرّ
 * يعود إلى **الشاشة نفسها** (`maxDeviceInfoSource`) إلا حيث يتفرّع المعنى صراحةً — والتشخيص
 * هي الوحيدة، لأنها تملك ثلاثة أقسام، (٣) الشاشة تحمل `MaxDeviceInfoShortcut(` فعلًا
 * **باسمها** فلا زرّ يفتح قسمًا غيرها.
 *
 * وكل نصوص الملفات تُقرأ **بلا تعليقاتها** (`read`): تُقاس البنية لا صياغة الشرح.
 */
class DeviceInfoShortcutEntryTest {
    private lateinit var sourceRoot: File

    @Before
    fun locateSourceRoot() {
        val found = listOf(
            File("src/main/java/nd/max"),
            File("app/src/main/java/nd/max"),
            File("../app/src/main/java/nd/max"),
            File("manager/app/src/main/java/nd/max"),
        ).firstOrNull { it.isDirectory }
        assumeTrue("Cannot locate nd.max sources; guard not evaluated", found != null)
        sourceRoot = found!!
    }

    /** الكود دون تعليقاته: تُقاس البنية، ولا تُقاس صياغة التعليق. */
    private fun read(path: String): String = File(sourceRoot, path).readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//[^\\n]*"), "")

    /**
     * الوجهات التي تحمل ضغطة إلى «معلومات الجهاز» — وهي الخريطة التي يقول الاختبار إنها
     * **كاملة**: شاشةٌ تعمل على موضوع قسمٍ ولم تُدرج هنا تعني قارئًا لا يجد القسم.
     */
    private val shortcutOwners = listOf(
        "ui/subscreens/CpuCoreControlScreen.kt" to MaxDestination.CpuCoreControl,
        "ui/subscreens/GpuStudioScreen.kt" to MaxDestination.GpuStudio,
        "ui/subscreens/ZramManagerScreen.kt" to MaxDestination.ZramManager,
        "ui/subscreens/StorageDetailScreen.kt" to MaxDestination.StorageDetail,
        "ui/subscreens/ChargingScreen.kt" to MaxDestination.Charging,
        "ui/subscreens/DisplayStudioScreen.kt" to MaxDestination.DisplayStudio,
        "ui/subscreens/ThermalDetailScreen.kt" to MaxDestination.ThermalDetail,
        "ui/subscreens/NetworkDetailScreen.kt" to MaxDestination.NetworkDetail,
        "ui/mainscreens/DiagnosticsScreen.kt" to MaxDestination.Diagnostics,
        // وسطح التحكّم الصوتيّ (`AS-02`): يعمل على الصوت، فبابه هنا لا في شاشةٍ لا تحمل موضوعه.
        "ui/subscreens/audio/AudioStudioScreen.kt" to MaxDestination.AudioStudio,
    )

    private val hubSource = "ui/subscreens/hubs/MaxDomainHubScreen.kt"

    // ── (١) الخريطة: كل وجهة تحمل قسمًا موجودًا فعلًا ──────────────────────────

    @Test
    fun `every shortcut owner maps to a section that exists`() {
        (shortcutOwners.map { it.second } + hubDestinations()).forEach { destination ->
            val section = deviceInfoShortcutSection(destination)
            assertNotNull(
                "الشاشة ${destination::class.simpleName} تعمل على موضوع قسمٍ ولا ضغطة لها",
                section,
            )
            assertTrue(section!! in DeviceInfoSection.entries)
        }
    }

    private fun hubDestinations(): List<MaxDestination> = listOf(
        MaxDestination.CpuHub,
        MaxDestination.GpuHub,
        MaxDestination.MemoryHub,
        MaxDestination.DisplayHub,
        MaxDestination.ThermalHub,
        MaxDestination.PowerHub,
        MaxDestination.StorageHub,
        MaxDestination.NetworkHub,
        // والمجال العاشر (`AU-01`): كان مُعلَنًا بلا قسمٍ حتى `AS-01`، وقد صار له قسمٌ فعليّ.
        MaxDestination.AudioHub,
    )

    // ── (٢) الدائرية: الزرّ يعود إلى الشاشة نفسها ────────────────────────────

    @Test
    fun `the shortcut of a screen returns to that same screen through the door map`() {
        // كل شاشة تملك قسمًا **واحدًا** يفتحها هي: فمن ضغط زرّ القسم في شاشة الحرارة يصل
        // إلى قسم الحرارة، ومن ضغط باب قسم الحرارة هناك يعود إلى شاشة الحرارة. وهذا هو
        // ما يجعل الاتجاهين خريطةً واحدة لا خريطتين تختلفان يومًا.
        val owners = shortcutOwners.filterNot { it.second == MaxDestination.Diagnostics }
        owners.forEach { (_, destination) ->
            val section = deviceInfoShortcutSection(destination)!!
            assertEquals(
                "القسم ${section.wireKey} في الزرّ لا يعود إلى ${destination::class.simpleName}",
                destination,
                maxDeviceInfoSource(section),
            )
        }
        // وصفحة المجال تختلف عن الشاشة المُفردة: القسم الواحد للمجال يفتح **شاشة تملكه داخل
        // صفوف المجال** (لا المجال نفسه)، فالمقيس هنا أن المالك من صفوف حوزه — وهو ما يمنع
        // أن يفتح زرّ مجالٍ شاشةً في مجال آخر.
        hubDestinations().forEach { hub ->
            val section = deviceInfoShortcutSection(hub)!!
            val owner = maxDeviceInfoSource(section)
            assertTrue(
                "قسم حوزه ${section.wireKey} يفتح $owner وهي ليست ضمن صفوف الحوز",
                maxHubRows(hub).contains(owner),
            )
        }
    }

    @Test
    fun `diagnostics opens the sensors section, not the system one`() {
        // **والاستثناء مُعلَن بالاسم:** `Diagnostics` تملك أربعة أقسام (النظرة العامة والنظام
        // والمستشعرات والكاميرا) فلا يمكن أن يكون المعكوس عليها إلا واحدًا. والمقصود المستشعرات — وهي
        // بطاقتها فعلًا (`SensorInventoryCard`) — وطلب المالك صريحًا.
        assertEquals(DeviceInfoSection.Sensors, deviceInfoShortcutSection(MaxDestination.Diagnostics))
        // **والتشخيص هي الوجهة الوحيدة التي تملك أكثر من قسم** — فلو صارت شاشة أخرى كذلك
        // لَما كان معكوسها واحدًا بلا اختيار، وهو ما يجب أن يُقال في هذا الاختبار لا أن يُسكت
        // عنه (وإلا صار معكوسًا مُخمَّنًا ينحرف بصمت حين ينتقل قسم من شاشة إلى أخرى).
        assertEquals(
            "الاستثناء الوحيد في الخريطة يجب أن يكون التشخيص",
            listOf("Diagnostics"),
            MaxDestination.All
                .filter { owner -> DeviceInfoSection.entries.count { maxDeviceInfoSource(it) == owner } > 1 }
                .map { it::class.simpleName },
        )
    }

    @Test
    fun `a screen with no matching section gets no button at all`() {
        // و«لا قسم» = «لا زرّ»: `ResponsivenessHub` مجال التحكّم الوحيد بلا قسم مقابل
        // (الأقسام الأحد عشر لا تحمل قسمًا للاستجابة)، فلا يُرسم له زرّ يفتح نظرة عامة عامّة.
        assertNull(deviceInfoShortcutSection(MaxDestination.ResponsivenessHub))
        // **وصفحات المحور لا ترسم بابًا أصلًا (اختيار المالك، الجولة ٢٠١):** هي فهرس أبواب،
        // فالخريطة تبقى صحيحة للمجالات لكن الصفحة لا تستدعي المكوّن ولا تُبقي استيراده ميتًا.
        assertFalse(
            "صفحة محور ترسم بابًا لمعلومات الجهاز — والصفحة نفسها فهرس أبواب",
            read(hubSource).contains("MaxDeviceInfoShortcut"),
        )
        assertTrue("الدور الذي لا قسم له لا باب", deviceInfoShortcutSection(MaxDestination.Settings) == null)
    }

    // ── (٣) مفتاح المسار: يُقرأ ويُبنى ولا يُهمَل ────────────────────────────

    @Test
    fun `every section key round-trips through its wire key`() {
        DeviceInfoSection.entries.forEach { section ->
            assertEquals(section, deviceInfoSectionOf(section.wireKey))
        }
    }

    @Test
    fun `an unknown or missing key falls back to overview instead of an empty screen`() {
        // والدخول المجرّد (من الرئيسية) يصل بلا `section` أصلًا، فيجب أن يجد شاشة لا تبويبًا
        // لا وجود له: «نظرة عامة» هي السقوط، لا شاشة فارغة ولا انتظار معامل لا يأتي.
        assertEquals(DeviceInfoSection.Overview, deviceInfoSectionOf(null))
        assertEquals(DeviceInfoSection.Overview, deviceInfoSectionOf(""))
        assertEquals(DeviceInfoSection.Overview, deviceInfoSectionOf("bogus"))
        assertEquals(DeviceInfoSection.Overview, deviceInfoSectionOf("MEMORY"))
    }

    @Test
    fun `the route carries the section, and the bare launch still lands on overview`() {
        assertEquals(
            "device_info?section=memory",
            deviceInfoRouteOf(DeviceInfoSection.Memory.wireKey),
        )
        assertFalse(deviceInfoRouteOf(DeviceInfoSection.Memory.wireKey).contains('{'))
        assertEquals("device_info", launchRouteOf(MaxDestination.DeviceInfo.route))
        assertFalse(
            "المعامل في الاستعلام لا في المسار: الوجهة تبقى قابلة للإطلاق من قائمة",
            launchRouteNeedsArgument(MaxDestination.DeviceInfo.route),
        )
    }

    @Test
    fun `the graph declares the section argument, and the screen reads it`() {
        val graph = read("ui/navigation/MaxNavGraph.kt")
        assertTrue(
            "مسار بمعامل غير مُعلَن يُهمله الـNavigator صامتًا، فيُفتح دائمًا على النظرة العامة",
            graph.contains("navArgument(\"section\")"),
        )
        assertTrue(graph.contains("MaxDestination.DeviceInfo.route"))
        // **وصُحّح المُرسى في تكملة ٢٢٤:** التقليب (`DI-01`) جعل الشاشة تبذر **رقم الصفحة** من
        // المفتاح (`deviceInfoPageOf`) لا القسم — **والحكم لم يُخفَّف:** `deviceInfoPageOf`
        // تُفوّض إلى `deviceInfoSectionOf` نفسها وترجع ترتيبها، فالمرسى صار على الاسم الجديد
        // **ومعه** حرس التفويض في النموذج (فلا يُشتقّ رقم الصفحة بيد في الشاشة).
        val screen = read("ui/subscreens/DeviceInfoScreen.kt")
        assertTrue(
            "الشاشة تبذر تبويبها من المفتاح عبر الدالّة الصافية لا بحارس موازٍ",
            screen.contains("deviceInfoPageOf(sectionKey)"),
        )
        val model = read("ui/subscreens/DeviceInfoModel.kt")
        assertTrue(
            "ورقم الصفحة مشتقٌّ من القسم في النموذج لا مكتوب بيد",
            model.contains("fun deviceInfoPageOf(") && model.contains("deviceInfoSectionOf(wireKey)"),
        )
    }

    // ── (٤) كل شاشة تحمل الزرّ باسمها ────────────────────────────────────────

    @Test
    fun `every shortcut owner calls the shortcut with its own destination`() {
        val offenders = shortcutOwners.filterNot { (source, destination) ->
            val code = read(source)
            code.contains("MaxDeviceInfoShortcut(") &&
                code.contains("MaxDestination.${destination::class.simpleName}")
        }.map { it.first }
        assertTrue(
            "شاشات لا تحمل ضغطتها، أو تحملها باسم وجهة أخرى (زرّ يفتح قسمًا لا يخصّها): $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the shortcut renders nothing when a screen has no section`() {
        // والحكم في المكوّن نفسه لا في الشاشات: من استُدعي بوجهة بلا قسم **لا يرسم شيئًا**،
        // فلا يحتاج كل نداءٍ حارسًا يعيد كتابة الخريطة.
        val component = read("ui/component/MaxDeviceInfoShortcut.kt")
        assertTrue(
            "المكوّن يجب أن يُسقط نفسه حين لا قسم للوجهة",
            component.contains("deviceInfoShortcutSection(from) ?: return"),
        )
        assertTrue(
            "والمسار يُبنى من السجلّ لا بيد (ADR-02)",
            component.contains("deviceInfoRouteOf(section.wireKey)"),
        )
    }
}
