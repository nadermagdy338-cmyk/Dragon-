/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpoofImpersonationTest {
    private fun bundled(): SpoofCpuCatalog =
        SpoofCpuCatalogParser.parse(File("src/main/assets/spoof/cpu_catalog.json").readText())!!

    @Test fun bundledCpuCatalogCarriesCopgsElevenModelsAndItsLicense() {
        val catalog = bundled()
        assertEquals(11, catalog.models.size)
        assertEquals("Apache-2.0", catalog.license)
        assertTrue(catalog.models.any { it.key == "dimensity9400plus" })
        catalog.models.forEach { assertTrue(it.key, CopgTagRules.valid("cpu=${it.key}")) }
    }

    @Test fun parserRejectsWrongSchemaAndDropsOnlyTheBrokenModel() {
        assertNull(SpoofCpuCatalogParser.parse("not json"))
        assertNull(SpoofCpuCatalogParser.parse("""{"schema":"2","models":[]}"""))
        val mixed = """{"schema":"1","models":[{"key":"bad!key","name":"X"},{"key":"ok_1","name":"Ok"}]}"""
        assertEquals(listOf("ok_1"), SpoofCpuCatalogParser.parse(mixed)!!.models.map { it.key })
    }

    @Test fun impersonationGroupUsesOnlyFreeCopgTags() {
        // مجموعة «الانتحال» في COPG: انتحال المعالج وحظر المعالج مجانيان؛ COW وAndroid ID من PRO.
        assertEquals(CopgTier.FREE, CopgTag.Cpu("sd8elite").tier)
        assertEquals(CopgTier.FREE, CopgTag.BlockCpu.tier)
        assertEquals(CopgTier.PRO, CopgTag.PropCow.tier)
        assertEquals(CopgTier.PRO, CopgTag.AndroidId.tier)
    }

    @Test fun cpuSpoofAndBlockAreMutuallyExclusiveAndKeepOtherTags() {
        val spoofed = SpoofImpersonation.withCpu(setOf("nolog"), "sd8elite")
        assertEquals(setOf("nolog", "cpu=sd8elite"), spoofed)
        assertEquals("sd8elite", SpoofImpersonation.cpuKey(spoofed))
        val blocked = SpoofImpersonation.withBlock(spoofed, true)
        assertEquals(setOf("nolog", "blocked"), blocked)
        assertTrue(SpoofImpersonation.blocksCpu(blocked))
        assertNull(SpoofImpersonation.cpuKey(blocked))
        assertEquals(setOf("nolog"), SpoofImpersonation.withBlock(blocked, false))
        // كل مجموعة تنتجها الواجهة يجب أن تمرّ على المدقّق الحقيقي للسياسة، وإلا تُرفض عند الحفظ.
        AppSpoofProfile(tags = spoofed)
        AppSpoofProfile(tags = blocked)
    }

    @Test fun clearingTheCpuKeepsBlockAndOtherTags() {
        assertEquals(setOf("dab", "blocked"), SpoofImpersonation.withCpu(setOf("dab", "blocked"), null))
        assertNull(SpoofImpersonation.cpuKey(SpoofImpersonation.withCpu(setOf("cpu=sd8elite", "kso"), null)))
    }

    @Test fun unsafeModelIsRefusedBeforeItReachesThePolicy() {
        assertTrue(runCatching { SpoofImpersonation.withCpu(emptySet(), "bad model!") }.isFailure)
    }
}
