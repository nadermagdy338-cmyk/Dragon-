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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AR-04` — الحكم على تطابق الإصدارين.
 *
 * القاعدة التي تحرسها هذه الاختبارات: **لا `MATCH` بلا دليل**. الوحدة غير المركَّبة، أو رقم
 * غير مقروء، أو تطبيق تعذّرت قراءته ⇒ `UNKNOWN` — لا «مطابق» بالصمت.
 */
class VersionIdentityTest {

    @Test
    fun `same readable codes match`() {
        assertEquals(
            VersionIdentity.Agreement.MATCH,
            VersionIdentity.compare(appVersionCode = 7L, moduleInstalled = true, moduleVersionCode = 7),
        )
    }

    @Test
    fun `different codes are a mismatch`() {
        assertEquals(
            VersionIdentity.Agreement.MISMATCH,
            VersionIdentity.compare(appVersionCode = 1L, moduleInstalled = true, moduleVersionCode = 10_000),
        )
    }

    @Test
    fun `module not installed is never a verdict`() {
        assertEquals(
            VersionIdentity.Agreement.UNKNOWN,
            VersionIdentity.compare(appVersionCode = 7L, moduleInstalled = false, moduleVersionCode = 7),
        )
    }

    @Test
    fun `unreadable module code is never a verdict`() {
        assertEquals(
            VersionIdentity.Agreement.UNKNOWN,
            VersionIdentity.compare(appVersionCode = 7L, moduleInstalled = true, moduleVersionCode = -1),
        )
        // ولا حتى لو صادف أن رقم التطبيق سالب أيضًا: سالبان ليسا «تطابقًا».
        assertEquals(
            VersionIdentity.Agreement.UNKNOWN,
            VersionIdentity.compare(appVersionCode = -1L, moduleInstalled = true, moduleVersionCode = -1),
        )
    }

    @Test
    fun `app that failed to read yields no report verdict`() {
        val report = VersionIdentity.Report(
            app = VersionIdentity.AppIdentity("UNKNOWN", -1L, readable = false),
            module = VersionIdentity.ModuleIdentity(true, "MaxManager", "V1", 1),
            agreement = VersionIdentity.Agreement.UNKNOWN,
        )
        assertNull("لا فرق إصدار بلا حكم", report.versionGap)
        assertEquals(VersionIdentity.UNKNOWN, report.app.display)
    }

    @Test
    fun `version gap is signed so direction is visible`() {
        val moduleNewer = VersionIdentity.Report(
            app = VersionIdentity.AppIdentity("1.0", 1L, readable = true),
            module = VersionIdentity.ModuleIdentity(true, "MaxManager", "V2", 2),
            agreement = VersionIdentity.Agreement.MISMATCH,
        )
        assertEquals(1L, moduleNewer.versionGap)

        val appNewer = VersionIdentity.Report(
            app = VersionIdentity.AppIdentity("3.0", 3L, readable = true),
            module = VersionIdentity.ModuleIdentity(true, "MaxManager", "V2", 2),
            agreement = VersionIdentity.Agreement.MISMATCH,
        )
        assertEquals(-1L, appNewer.versionGap)
    }

    @Test
    fun `unreadable module display never shows a number`() {
        val unknown = VersionIdentity.ModuleIdentity(false, null, null, -1)
        assertEquals(VersionIdentity.UNKNOWN, unknown.display)
        assertFalse(unknown.codeKnown)
        assertFalse("-1" == unknown.display)

        val known = VersionIdentity.ModuleIdentity(true, "MaxManager", "V1", 1)
        assertEquals("1", known.display)
        assertTrue(known.codeKnown)
    }

    @Test
    fun `reads the module fields it declares`() {
        val prop = """
            id=MaxManager
            name=Max Manager
            version=V1
            versionCode=1
            author=MaxManager Project
            description=Max Manager - adaptive, chip-aware performance tuning.
        """.trimIndent()

        assertEquals("MaxManager", ModuleHealthUtil.parseField(prop, "id"))
        assertEquals("V1", ModuleHealthUtil.parseField(prop, "version"))
        assertEquals(1, ModuleHealthUtil.parseVersionCode(prop))
        // ‏`description` يحوي شرطات ومسافات: لا يُخلط برقم الإصدار ولا يكسره.
        assertEquals(1, ModuleHealthUtil.parseVersionCode(prop))
    }

    @Test
    fun `reads the kernelsu flavoured prop too`() {
        val prop = """
            # KernelSU Next module for MaxManager
            id=nees_maxmanager
            version=v1.0.0-rc
            versionCode=10000
        """.trimIndent()

        assertEquals("nees_maxmanager", ModuleHealthUtil.parseField(prop, "id"))
        assertEquals("v1.0.0-rc", ModuleHealthUtil.parseField(prop, "version"))
        assertEquals(10_000, ModuleHealthUtil.parseVersionCode(prop))
    }

    @Test
    fun `missing or malformed fields stay unknown`() {
        assertNull(ModuleHealthUtil.parseField(null, "id"))
        assertNull(ModuleHealthUtil.parseField("", "id"))
        assertNull(ModuleHealthUtil.parseField("id=", "id"))
        assertNull(ModuleHealthUtil.parseField("name=Max Manager", "id"))
        // ولا يُقرأ `version` من داخل `versionCode`.
        assertEquals("V1", ModuleHealthUtil.parseField("version=V1\nversionCode=9", "version"))
        assertEquals(-1, ModuleHealthUtil.parseVersionCode("version=V1"))
    }
}
