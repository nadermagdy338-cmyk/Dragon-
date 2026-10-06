/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerAppDeviceModelTest {
    private val global = SpoofProfile("global", "Shared", "google", "Pixel", "pixel", "pixel")
    private val custom = SpoofProfile("custom", "Custom", "samsung", "Galaxy", "galaxy", "galaxy")
    private val sample = SpoofProfile("sample_redmagic", "REDMAGIC 9 Pro", "nubia", "REDMAGIC 9 Pro",
        "NX769J", "NX769J", fingerprint = "nubia/NX769J/NX769J:15/ID/1:user/release-keys")
    private fun workspace() = SpoofWorkspace(listOf(global, custom, sample), globalProfileId = global.id)
    private val observed = mapOf(
        SpoofField.BRAND to "nubia", SpoofField.MODEL to "Host", SpoofField.DEVICE to "host",
        SpoofField.PRODUCT to "host", SpoofField.SDK_INT to "35",
    )
    private fun device(key: String, name: String) = SampleDevice(
        key = key, name = name, brand = "b", manufacturer = "m", model = "M", device = "d", product = "p",
        fingerprint = "b/M/d:15/ID/1:user/release-keys",
    )

    @Test fun effectiveFollowsTheModeAndNeverInventsAProfile() {
        // وارث بلا ربط ⇒ القالب العام.
        assertEquals(global, PerAppDeviceModel.effective(workspace(), "com.example.app"))
        // مخصّص ⇒ ملفّه هو.
        val bound = workspace().bind("com.example.app", custom.id)
        assertEquals(custom, PerAppDeviceModel.effective(bound, "com.example.app"))
        // متوقف ⇒ لا شيء: يرى جهازه الحقيقي.
        val off = workspace().setAppPolicy("com.example.app", AppSpoofProfile(SpoofInheritanceMode.DISABLED))
        assertNull(PerAppDeviceModel.effective(off, "com.example.app"))
        // ولا يمكن أصلًا بناء مرجع معلّق: الثابت في `SpoofWorkspace` يرفضه، ففرع `null` في `effective`
        // حزام أمان لا مسارًا اعتياديًّا — وهذا سبب وجوده لا أكثر.
        assertTrue(runCatching {
            SpoofWorkspace(listOf(global), mapOf("com.example.app" to custom.id))
        }.isFailure)
        assertTrue(runCatching { SpoofWorkspace(listOf(global), emptyMap(), custom.id) }.isFailure)
    }

    @Test fun sampleKeyOnlyMapsCatalogProfiles() {
        assertEquals("redmagic", PerAppDeviceModel.sampleKey(sample.id))
        assertNull(PerAppDeviceModel.sampleKey("copy_1234"))
        assertNull(PerAppDeviceModel.sampleKey("custom"))
        assertNull(PerAppDeviceModel.sampleKey(null))
        assertNull(PerAppDeviceModel.sampleKey("sample_"))
    }

    @Test fun rowOrderPutsThisAppsDeviceFirstWithoutLosingOrDuplicating() {
        val devices = listOf(device("a", "A"), device("redmagic", "REDMAGIC 9 Pro"), device("b", "B"))
        val ordered = PerAppDeviceModel.rowOrder(devices, sample.id)
        assertEquals(listOf("redmagic", "a", "b"), ordered.map { it.key })
        assertEquals(devices.size, ordered.size)
        assertEquals(devices.toSet(), ordered.toSet())
        // بلا اختيار، أو بمعرّف لا بطاقة له (ملفّ حُرّ/منسوخ)، أو بمفتاح غير موجود في الكتالوج: الترتيب كما هو.
        assertEquals(devices, PerAppDeviceModel.rowOrder(devices, null))
        assertEquals(devices, PerAppDeviceModel.rowOrder(devices, "copy_99"))
        assertEquals(devices, PerAppDeviceModel.rowOrder(devices, "sample_missing"))
    }

    @Test fun changedFieldCountCountsOnlyObservedDifferences() {
        assertEquals(0, PerAppDeviceModel.changedFieldCount(null, observed))
        // مطابق للمرصود في كل ما رُصد ⇒ صفر.
        val same = global.copy(brand = "nubia", model = "Host", device = "host", product = "host", fingerprint = null,
            sdkInt = null)
        assertEquals(0, PerAppDeviceModel.changedFieldCount(same, observed))
        // MODEL وDEVICE وPRODUCT تختلف، وBRAND مطابق (لا يُحتسب)، والبصمة لم تُرصد (لا تُحتسب) ⇒ ثلاثة.
        assertEquals(3, PerAppDeviceModel.changedFieldCount(sample, observed))
        // قيمة لم تُرصد لا تُحتسب تغييرًا (وإلا ادّعى العدّ ما لا دليل عليه).
        assertEquals(0, PerAppDeviceModel.changedFieldCount(sample, emptyMap()))
        assertEquals(1, PerAppDeviceModel.changedFieldCount(sample, mapOf(SpoofField.MODEL to "Host")))
    }
}
