package nd.max.core.hardware

import nd.max.core.atlas.AtlasFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtlasAdaptiveReadTransportTest {
    @Test
    fun `authorized reads prefer the privileged transport`() {
        val ordinary = FakeTransport(AtlasTransportRead.Text("ordinary", false))
        val privileged = FakeTransport(AtlasTransportRead.Text("root", false))
        val transport = AtlasAdaptiveReadTransport(ordinary, privileged) { true }

        val result = transport.readText("/sys/test/value", 64)

        assertEquals("root", (result as AtlasTransportRead.Text).text)
        assertEquals(0, ordinary.reads)
        assertEquals(1, privileged.reads)
    }

    @Test
    fun `permission failure falls back without turning it into absence`() {
        val ordinary = FakeTransport(AtlasTransportRead.Text("ordinary", false))
        val privileged = FakeTransport(
            AtlasTransportRead.Failed(AtlasFailure.PERMISSION_DENIED, "denied"),
        )
        val transport = AtlasAdaptiveReadTransport(ordinary, privileged) { true }

        val result = transport.readText("/sys/test/value", 64)

        assertTrue(result is AtlasTransportRead.Text)
        assertEquals(1, ordinary.reads)
        assertEquals(1, privileged.reads)
    }

    private class FakeTransport(private val response: AtlasTransportRead) : AtlasReadTransport {
        var reads = 0
        override fun readText(path: String, maxBytes: Int): AtlasTransportRead {
            reads++
            return response
        }

        override fun listNames(path: String, limit: Int): AtlasTransportList =
            AtlasTransportList(emptyList())

        override fun canonicalPath(path: String, maxHops: Int): String = path
    }
}
