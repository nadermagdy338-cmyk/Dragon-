/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import nd.max.core.atlas.AtlasFileStoreIo
import nd.max.core.atlas.AtlasStoreIo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtlasRouteMemoryTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `a verified route becomes this device's preferred route`() {
        val memory = memory()
        memory.noteVerified("cpu_frequency", "cpu.frequency.arbiter")

        assertEquals("cpu.frequency.arbiter", memory.preferredRoute("cpu_frequency"))
    }

    @Test
    fun `a clean failure does not quarantine the route`() {
        val memory = memory()
        memory.noteFailed("cpu_frequency", "cpu.frequency.arbiter", rollbackVerified = true)

        assertTrue(memory.quarantinedRoutes("cpu_frequency").isEmpty())
        assertNull(memory.preferredRoute("cpu_frequency"))
    }

    @Test
    fun `an unverified rollback quarantines the route inside the same generation`() {
        val memory = memory()
        memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)

        assertEquals(setOf("gpu.frequency.arbiter"), memory.quarantinedRoutes("gpu_frequency"))
    }

    @Test
    fun `quarantine expires with the boot because the vendor state is rebuilt`() {
        var boot = 7L
        val io = InMemoryIo()
        val memory = AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { boot })
        memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
        assertEquals(1, memory.quarantinedRoutes("gpu_frequency").size)

        boot = 8L
        assertTrue(
            "a route stranded in an unknown state last boot must be retried, not banned forever",
            memory.quarantinedRoutes("gpu_frequency").isEmpty(),
        )
    }

    @Test
    fun `successful recovery after reboot does not inherit the old rollback failure`() {
        var boot = 7L
        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { boot })
        memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)

        boot = 8L
        memory.noteVerified("gpu_frequency", "gpu.frequency.arbiter")

        assertTrue(memory.quarantinedRoutes("gpu_frequency").isEmpty())
        assertEquals("gpu.frequency.arbiter", memory.preferredRoute("gpu_frequency"))
        val entry = memory.entries().single()
        assertEquals(8L, entry.bootGeneration)
        assertEquals(1, entry.successes)
        assertEquals(0, entry.failures)
        assertEquals(0, entry.rollbackFailures)
    }

    @Test
    fun `clean failure after reboot does not revive a quarantine from the previous boot`() {
        var boot = 7L
        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { boot })
        memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)

        boot = 8L
        memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = true)

        assertTrue(memory.quarantinedRoutes("gpu_frequency").isEmpty())
        assertEquals(1, memory.entries().single().failures)
        assertEquals(0, memory.entries().single().rollbackFailures)
    }

    @Test
    fun `restarting the process in the same boot preserves quarantine`() {
        val io = InMemoryIo()
        AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { 7L })
            .noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)

        val restarted = AtlasRouteMemory(io, clockMs = { 2_000L }, bootGeneration = { 7L })

        assertEquals(setOf("gpu.frequency.arbiter"), restarted.quarantinedRoutes("gpu_frequency"))
    }

    @Test
    fun `independent file stores retain quarantine then persist recovery in a new boot`() {
        val directory = temporary.newFolder("routes").toPath()
        AtlasRouteMemory(AtlasFileStoreIo(directory), clockMs = { 1_000L }, bootGeneration = { 7L })
            .noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)

        val sameBoot = AtlasRouteMemory(AtlasFileStoreIo(directory), clockMs = { 2_000L }, bootGeneration = { 7L })
        assertEquals(setOf("gpu.frequency.arbiter"), sameBoot.quarantinedRoutes("gpu_frequency"))

        val nextBoot = AtlasRouteMemory(AtlasFileStoreIo(directory), clockMs = { 1_000L }, bootGeneration = { 8L })
        assertTrue(nextBoot.quarantinedRoutes("gpu_frequency").isEmpty())
        nextBoot.noteVerified("gpu_frequency", "gpu.frequency.arbiter")

        val reopened = AtlasRouteMemory(AtlasFileStoreIo(directory), clockMs = { 2_000L }, bootGeneration = { 8L })
        assertTrue(reopened.quarantinedRoutes("gpu_frequency").isEmpty())
        assertEquals("gpu.frequency.arbiter", reopened.preferredRoute("gpu_frequency"))
    }

    @Test
    fun `unknown current boot does not release an existing quarantine`() {
        var boot = 7L
        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { boot })
        memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)

        boot = 0L

        assertEquals(setOf("gpu.frequency.arbiter"), memory.quarantinedRoutes("gpu_frequency"))
    }

    @Test
    fun `legacy unknown boot quarantine is not silently cleared by a known boot`() {
        val io = InMemoryIo()
        val legacy = AtlasRouteMemory(io, clockMs = { 1_000L })
        legacy.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
        val current = AtlasRouteMemory(io, clockMs = { 2_000L }, bootGeneration = { 8L })

        assertEquals(setOf("gpu.frequency.arbiter"), current.quarantinedRoutes("gpu_frequency"))
    }

    @Test
    fun `legacy quarantine anchors to a known boot and recovers after the following reboot`() {
        val io = InMemoryIo()
        AtlasRouteMemory(io, clockMs = { 1_000L })
            .noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
        val current = AtlasRouteMemory(io, clockMs = { 2_000L }, bootGeneration = { 8L })
        assertEquals(setOf("gpu.frequency.arbiter"), current.quarantinedRoutes("gpu_frequency"))
        assertEquals(8L, current.entries().single().bootGeneration)

        val rebooted = AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { 9L })

        assertTrue(rebooted.quarantinedRoutes("gpu_frequency").isEmpty())
        rebooted.noteVerified("gpu_frequency", "gpu.frequency.arbiter")
        assertTrue(rebooted.quarantinedRoutes("gpu_frequency").isEmpty())
        assertEquals("gpu.frequency.arbiter", rebooted.preferredRoute("gpu_frequency"))
    }

    @Test
    fun `anchoring a full store does not delete another targets quarantine`() {
        val io = InMemoryIo()
        val legacy = AtlasRouteMemory(io, clockMs = { 1_000L }, maxEntries = 2)
        legacy.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
        legacy.noteFailed("cpu_frequency", "cpu.frequency.arbiter", rollbackVerified = false)
        val current = AtlasRouteMemory(io, clockMs = { 2_000L }, bootGeneration = { 8L }, maxEntries = 2)

        assertEquals(setOf("gpu.frequency.arbiter"), current.quarantinedRoutes("gpu_frequency"))
        assertEquals(setOf("cpu.frequency.arbiter"), current.quarantinedRoutes("cpu_frequency"))
        assertEquals(2, current.entries().size)
    }

    @Test
    fun `failed legacy migration write keeps quarantine across subsequent boots`() {
        val io = InMemoryIo()
        AtlasRouteMemory(io, clockMs = { 1_000L })
            .noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
        io.writable = false

        for (boot in listOf(8L, 9L)) {
            val current = AtlasRouteMemory(io, clockMs = { 2_000L }, bootGeneration = { boot })
            assertEquals(setOf("gpu.frequency.arbiter"), current.quarantinedRoutes("gpu_frequency"))
            assertEquals(0L, current.entries().single().bootGeneration)
            assertNull(current.preferredRoute("gpu_frequency"))
        }
    }

    @Test
    fun `an unknown boot cannot advertise a remembered route as verified`() {
        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L })
        memory.noteVerified("gpu_frequency", "gpu.frequency.arbiter")

        assertNull(memory.preferredRoute("gpu_frequency"))
    }

    @Test
    fun `a later success in the same boot cannot erase an unsafe rollback`() {
        val memory = memory()
        memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)
        memory.noteVerified("gpu_frequency", "gpu.frequency.arbiter")

        assertEquals(setOf("gpu.frequency.arbiter"), memory.quarantinedRoutes("gpu_frequency"))
        assertNull(memory.preferredRoute("gpu_frequency"))
    }

    @Test
    fun `verified preference expires at reboot even when new uptime exceeds the old timestamp`() {
        var boot = 7L
        var now = 1_000L
        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { now }, bootGeneration = { boot })
        memory.noteVerified("gpu_frequency", "gpu.frequency.arbiter")

        boot = 8L
        now = 100_000L

        assertNull(memory.preferredRoute("gpu_frequency"))
    }

    @Test
    fun `missing malformed and nil boot identities stay unknown`() {
        listOf(null, "", "permission denied", "1-1-1-1-1", "00000000-0000-0000-0000-000000000000").forEach {
            assertEquals(0L, AtlasRouteMemory.generationForBootId(it))
        }
    }

    @Test
    fun `kernel boot identity is positive stable and normalized across readers`() {
        val id = "550e8400-e29b-41d4-a716-446655440000"
        val first = AtlasRouteMemory.generationForBootId(id)
        assertTrue(first > 0L)
        assertEquals(first, AtlasRouteMemory.generationForBootId("  550E8400-E29B-41D4-A716-446655440000\n"))
        assertTrue(first != AtlasRouteMemory.generationForBootId("550e8400-e29b-41d4-a716-446655440001"))
    }

    @Test
    fun `framework restart retains quarantine when the kernel boot identity is unchanged`() {
        val io = InMemoryIo()
        val bootId = "550e8400-e29b-41d4-a716-446655440000"
        AtlasRouteMemory(io, clockMs = { 1_000L }, bootGeneration = { AtlasRouteMemory.generationForBootId(bootId) })
            .noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)

        // A recreated Android framework/app has the same kernel UUID, regardless of BOOT_COUNT.
        val restarted = AtlasRouteMemory(io, clockMs = { 2_000L },
            bootGeneration = { AtlasRouteMemory.generationForBootId(bootId) })
        assertEquals(setOf("gpu.frequency.arbiter"), restarted.quarantinedRoutes("gpu_frequency"))

        val rebooted = AtlasRouteMemory(io, clockMs = { 1_000L },
            bootGeneration = { AtlasRouteMemory.generationForBootId("550e8400-e29b-41d4-a716-446655440001") })
        assertTrue(rebooted.quarantinedRoutes("gpu_frequency").isEmpty())
    }

    @Test
    fun `a failure in another privilege generation does not quarantine the current one`() {
        var privilege = 1L
        val memory = AtlasRouteMemory(
            InMemoryIo(),
            clockMs = { 1_000L },
            bootGeneration = { 1L },
            privilegeGeneration = { privilege },
        )
        memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)

        privilege = 2L
        assertTrue(memory.quarantinedRoutes("gpu_frequency").isEmpty())

        memory.noteVerified("gpu_frequency", "gpu.frequency.arbiter")
        assertTrue(memory.quarantinedRoutes("gpu_frequency").isEmpty())
        assertEquals(0, memory.entries().single().rollbackFailures)
    }

    @Test
    fun `the latest verified route wins`() {
        var now = 100L
        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { now }, bootGeneration = { 1L })
        memory.noteVerified("cpu_frequency", "cpu.frequency.arbiter")
        now = 500L
        memory.noteVerified("cpu_frequency", "cpu.frequency.vendor")

        assertEquals("cpu.frequency.vendor", memory.preferredRoute("cpu_frequency"))
    }

    @Test
    fun `a corrupt entry is discarded rather than half-trusted`() {
        val io = InMemoryIo()
        io.write("route.cpu_frequency.cpu.frequency.arbiter.json", "{\"schema\":1,\"target\":")
        val memory = AtlasRouteMemory(io, clockMs = { 0L })

        assertTrue(memory.entries().isEmpty())
        assertNull(memory.preferredRoute("cpu_frequency"))
    }

    @Test
    fun `an unknown schema is refused`() {
        val io = InMemoryIo()
        io.write(
            "route.gpu_frequency.gpu.frequency.arbiter.json",
            """
            {"schema":99,"target":"gpu_frequency","route":"gpu.frequency.arbiter","outcome":"VERIFIED",
             "verifiedAt":1,"boot":0,"privilege":0,"successes":1,"failures":0,"rollbackFailures":0}
            """.trimIndent(),
        )
        val memory = AtlasRouteMemory(io, clockMs = { 0L })

        assertTrue(memory.entries().isEmpty())
    }

    @Test
    fun `a target with unusual characters cannot name a file outside the store`() {
        val io = InMemoryIo()
        val memory = AtlasRouteMemory(io, clockMs = { 0L }, bootGeneration = { 1L })
        memory.noteVerified("../../etc/passwd", "cpu.frequency.arbiter")

        assertTrue(io.list().all { !it.contains("..") && !it.contains("/") })
        assertEquals("cpu.frequency.arbiter", memory.preferredRoute("../../etc/passwd"))
    }

    @Test
    fun `the store stays bounded`() {
        val io = InMemoryIo()
        val memory = AtlasRouteMemory(io, clockMs = { 0L }, maxEntries = 4)
        repeat(12) { index -> memory.noteVerified("cpu_frequency", "route.number.$index") }

        assertTrue("the store must not grow without bound: ${io.list().size}", io.list().size <= 4)
    }

    private fun memory(): AtlasRouteMemory =
        AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { 1L })

    private class InMemoryIo : AtlasStoreIo {
        private val entries = LinkedHashMap<String, String>()
        var writable: Boolean = true
        override fun read(name: String): String? = entries[name]
        override fun write(name: String, text: String): Boolean {
            if (!writable) return false
            entries[name] = text
            return true
        }

        override fun delete(name: String): Boolean = entries.remove(name) != null
        override fun list(): List<String> = entries.keys.sorted()
    }
}
