/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.util

import nd.max.ui.util.ConfigBackupInventory.AbsenceReason
import nd.max.ui.util.ConfigBackupInventory.DeviceState
import nd.max.ui.util.ConfigBackupInventory.ExclusionReason
import nd.max.ui.util.ConfigBackupInventory.PrefValue
import nd.max.ui.util.ConfigBackupInventory.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات `GAP-13` + `GAP-12`.
 *
 * والقاعدة التي تحكمها: **الإعلان صادق قبل الكتابة** — الغياب يُقال، والمستثنى يُسمّى، والنوع
 * يُحفظ كما كان.
 */
class ConfigBackupInventoryTest {

    private val rooted = DeviceState(hasRoot = true, prefFilesPresent = emptySet(), prefFilesEmpty = emptySet())
    private val noRoot = DeviceState(hasRoot = false, prefFilesPresent = emptySet(), prefFilesEmpty = emptySet())

    /**
     * نطاق صريح: كل الأعلام تُمرَّر دائمًا. و`Scope()` الافتراضي يفتح `tweaks` و`applist`،
     * فالاعتماد عليه هنا يجعل الاختبار يقيس شيئًا آخر غير الذي يقصده.
     */
    private fun scopeOf(
        tweaks: Boolean = false,
        applist: Boolean = false,
        appearance: Boolean = false,
        appPrefs: Boolean = false,
    ) = ConfigBackupInventory.Scope(tweaks, applist, appearance, appPrefs)

    private fun fullScope() = scopeOf(tweaks = true, applist = true, appearance = true, appPrefs = true)

    // ────────────────────────────────────────────────────────────────────────
    // الإعلان
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `both root-backed sections are declared absent when there is no root`() {
        val declaration = ConfigBackupInventory.declare(scopeOf(tweaks = true, applist = true), noRoot)
        assertEquals(
            listOf(Section.TWEAKS, Section.APPLIST),
            declaration.absent.map { it.section },
        )
        assertTrue(declaration.absent.all { it.reason == AbsenceReason.NO_ROOT })
    }

    @Test
    fun `with root both root-backed sections are declared included`() {
        val declaration = ConfigBackupInventory.declare(scopeOf(tweaks = true, applist = true), rooted)
        assertEquals(
            listOf(Section.TWEAKS, Section.APPLIST),
            declaration.included.map { it.section },
        )
        assertTrue(declaration.included.all { it.needsRoot })
    }

    @Test
    fun `a missing preference file is declared, not silently skipped`() {
        val declaration = ConfigBackupInventory.declare(fullScope(), rooted)
        assertTrue(
            declaration.absent.any {
                it.section == Section.APPEARANCE && it.reason == AbsenceReason.FILE_MISSING
            }
        )
    }

    @Test
    fun `an existing but empty file is empty, not missing`() {
        // «غائب» و«فارغ» نتيجتان مختلفتان: الأولى «لم أستطع»، والثانية «قرأتُ فوجدت لا شيء».
        val device = rooted.copy(prefFilesEmpty = setOf(ConfigBackupInventory.PREF_APPEARANCE))
        val declaration = ConfigBackupInventory.declare(scopeOf(appearance = true), device)
        assertEquals(listOf(AbsenceReason.EMPTY), declaration.absent.map { it.reason })
    }

    @Test
    fun `a present non-empty file is included without root`() {
        val device = rooted.copy(
            prefFilesPresent = setOf(ConfigBackupInventory.PREF_APPEARANCE),
        )
        val declaration = ConfigBackupInventory.declare(scopeOf(appearance = true), device)
        assertEquals(listOf(Section.APPEARANCE), declaration.included.map { it.section })
        assertFalse(declaration.included.single().needsRoot)
        assertTrue(declaration.absent.isEmpty())
    }

    @Test
    fun `a section with one present file and one empty file is still included`() {
        val device = rooted.copy(
            prefFilesPresent = setOf(ConfigBackupInventory.PREF_APP_PREFS),
            prefFilesEmpty = setOf(ConfigBackupInventory.PREF_SETTINGS_PREFS),
        )
        val declaration = ConfigBackupInventory.declare(scopeOf(appPrefs = true), device)
        assertEquals(listOf(Section.APP_PREFS), declaration.included.map { it.section })
    }

    @Test
    fun `nothing chosen means an empty declaration and no backup button`() {
        val declaration = ConfigBackupInventory.declare(scopeOf(), rooted)
        assertTrue(declaration.isEmpty)
        assertTrue(declaration.included.isEmpty())
    }

    @Test
    fun `chosen sections keep a stable order`() {
        assertEquals(
            listOf(Section.TWEAKS, Section.APPLIST, Section.APPEARANCE, Section.APP_PREFS),
            fullScope().chosen,
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    // الاستثناءات
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the safety file is excluded by policy, never by silence`() {
        val exclusions = ConfigBackupInventory.exclusions()
        assertTrue(
            exclusions.any { it.reason == ExclusionReason.SAFETY_POLICY }
        )
        // ولا يتغيّر الاستثناء بتغيّر النطاق.
        assertEquals(exclusions, ConfigBackupInventory.declare(fullScope(), rooted).exclusions)
        assertEquals(exclusions, ConfigBackupInventory.declare(scopeOf(), rooted).exclusions)
    }

    @Test
    fun `maxai_safety is not a copyable file`() {
        ConfigBackupInventory.PROTECTED_PREF_FILES.forEach { name ->
            assertFalse("$name must never be copyable", ConfigBackupInventory.isCopyable(name))
        }
        assertFalse(ConfigBackupInventory.isCopyable("something_unknown"))
        assertFalse(ConfigBackupInventory.isCopyable(""))
        ConfigBackupInventory.copyablePrefFiles().forEach {
            assertTrue(ConfigBackupInventory.isCopyable(it))
        }
    }

    @Test
    fun `no declared section reads a protected file`() {
        Section.entries.flatMap { ConfigBackupInventory.prefFilesOf(it) }.forEach { name ->
            assertTrue(
                "section reads a non-copyable file: $name",
                ConfigBackupInventory.isCopyable(name),
            )
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // الترميز
    // ────────────────────────────────────────────────────────────────────────

    /**
     * مصنع محلي: `PrefValue.Text(...)` بنصّ صلب يُحسب في `code_health` كنصّ واجهة صلب
     * (لأنه يطابق `Text("...")`) — وهو إنذار كاذب نُبعده عن الفاحص بدالّة، لا بتعديل الفاحص.
     */
    private fun txt(value: String): PrefValue = PrefValue.Text(value)

    private val every = linkedMapOf<String, PrefValue>(
        "text" to txt("dark"),
        "int" to PrefValue.Int32(3),
        "long" to PrefValue.Whole(9_000_000_000L),
        "decimal" to PrefValue.Decimal(1.5),
        "flag" to PrefValue.Flag(true),
        "set" to PrefValue.TextSet(listOf("a", "b")),
    )

    @Test
    fun `codec round-trips every value type without widening it`() {
        val decoded = ConfigBackupInventory.PrefCodec.decode(
            ConfigBackupInventory.PrefCodec.encode(ConfigBackupInventory.PREF_APPEARANCE, every)
        )
        assertEquals(ConfigBackupInventory.PREF_APPEARANCE, decoded?.fileName)
        assertEquals(every, decoded?.values)
        // والتحديد الأهمّ: `Int` لا تصير `Long` — وإلا رمى قارئها `getInt` استثناءً.
        assertTrue(decoded?.values?.get("int") is PrefValue.Int32)
    }

    @Test
    fun `codec rejects a schema it does not understand`() {
        val json = ConfigBackupInventory.PrefCodec.encode(ConfigBackupInventory.PREF_APPEARANCE, every)
            .replace("\"schema\":1", "\"schema\":7")
        assertNull(ConfigBackupInventory.PrefCodec.decode(json))
    }

    @Test
    fun `codec rejects an unknown value type instead of guessing`() {
        val json = ConfigBackupInventory.PrefCodec.encode(
            ConfigBackupInventory.PREF_APPEARANCE,
            linkedMapOf("k" to txt("v")),
        ).replace("\"t\":\"s\"", "\"t\":\"blob\"")
        assertNull(ConfigBackupInventory.PrefCodec.decode(json))
    }

    @Test
    fun `codec rejects a bundle whose file name does not match`() {
        val json = ConfigBackupInventory.PrefCodec.encode(
            ConfigBackupInventory.PREF_APPEARANCE,
            every,
        ).replace("\"file\":\"settings\"", "\"file\":\"other\"")
        assertNull(ConfigBackupInventory.PrefCodec.decode(json))
    }

    @Test
    fun `bundle round-trips all copyable files`() {
        val files = mapOf(
            ConfigBackupInventory.PREF_APPEARANCE to every,
            ConfigBackupInventory.PREF_APP_PREFS to linkedMapOf("first_run" to PrefValue.Flag(false)),
        )
        assertEquals(
            files,
            ConfigBackupInventory.PrefCodec.decodeAll(ConfigBackupInventory.PrefCodec.encodeAll(files)),
        )
    }

    @Test
    fun `bundle containing a protected file is rejected whole`() {
        // لا نطبّق ما فهمناه ونتجاهل الباقي: نصف استعادة لا يفهم المستخدم مصدرها أسوأ من عدمها.
        val json = ConfigBackupInventory.PrefCodec.encodeAll(
            mapOf("maxai_safety" to linkedMapOf("guard" to PrefValue.Flag(false)))
        )
        assertNull(ConfigBackupInventory.PrefCodec.decodeAll(json))
    }

    @Test
    fun `bundle containing an unknown file is rejected whole`() {
        val json = ConfigBackupInventory.PrefCodec.encodeAll(
            mapOf("whatever" to linkedMapOf("k" to PrefValue.Flag(true)))
        )
        assertNull(ConfigBackupInventory.PrefCodec.decodeAll(json))
    }

    @Test
    fun `bundle decode rejects malformed input`() {
        assertNull(ConfigBackupInventory.PrefCodec.decodeAll("not json"))
        assertNull(ConfigBackupInventory.PrefCodec.decodeAll("{}"))
    }
}
