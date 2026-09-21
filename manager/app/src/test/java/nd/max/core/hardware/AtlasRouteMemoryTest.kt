package nd.max.core.hardware

import nd.max.core.atlas.AtlasStoreIo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AtlasRouteMemoryTest {

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
    fun `a failure in another privilege generation does not quarantine the current one`() {
        var privilege = 1L
        val memory = AtlasRouteMemory(
            InMemoryIo(),
            clockMs = { 1_000L },
            bootGeneration = { 0L },
            privilegeGeneration = { privilege },
        )
        memory.noteFailed("gpu_frequency", "gpu.frequency.arbiter", rollbackVerified = false)

        privilege = 2L
        assertTrue(memory.quarantinedRoutes("gpu_frequency").isEmpty())
    }

    @Test
    fun `the latest verified route wins`() {
        var now = 100L
        val memory = AtlasRouteMemory(InMemoryIo(), clockMs = { now }, bootGeneration = { 0L })
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
        val memory = AtlasRouteMemory(io, clockMs = { 0L })
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
        AtlasRouteMemory(InMemoryIo(), clockMs = { 1_000L }, bootGeneration = { 0L })

    private class InMemoryIo : AtlasStoreIo {
        private val entries = LinkedHashMap<String, String>()
        override fun read(name: String): String? = entries[name]
        override fun write(name: String, text: String): Boolean {
            entries[name] = text
            return true
        }

        override fun delete(name: String): Boolean = entries.remove(name) != null
        override fun list(): List<String> = entries.keys.sorted()
    }
}
