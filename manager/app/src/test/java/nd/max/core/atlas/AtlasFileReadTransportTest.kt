package nd.max.core.atlas

import nd.max.core.hardware.AtlasTransportRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText

/**
 * Real-execution tests for the shipped transport (`P2`/`T2.5`, rootless half).
 *
 * Nothing is faked: real files, real syscalls, real `errno`, real symbolic links, real permission
 * bits. The transport is merely rooted at a temporary directory so the device's absolute paths can be
 * recreated — the class under test is the one that ships. If the errno-to-cause mapping is wrong,
 * these fail, and every absence claim the boundary makes depends on that mapping.
 */
class AtlasFileReadTransportTest {

    @Test
    fun `a real file is read verbatim`() {
        val dir = temp()
        dir.resolve("temp").writeText("42000\n")

        val result = AtlasFileReadTransport(dir).readText("/temp", 64)

        assertEquals(AtlasTransportRead.Text("42000\n", truncated = false), result)
    }

    @Test
    fun `a value larger than the allowance is flagged, never trimmed into a value`() {
        val dir = temp()
        dir.resolve("big").writeText("abcdefghij")

        val result = AtlasFileReadTransport(dir).readText("/big", 4)

        assertTrue("a partial value must be flagged", (result as AtlasTransportRead.Text).truncated)
        assertEquals("abcd", result.text)
    }

    @Test
    fun `an empty file is a read that returned nothing, not a denial`() {
        val dir = temp()
        dir.resolve("empty").writeText("")

        val result = AtlasFileReadTransport(dir).readText("/empty", 64)

        assertEquals(AtlasTransportRead.Text("", truncated = false), result)
    }

    @Test
    fun `a missing file is absent, from a real errno`() {
        val dir = temp()

        val result = AtlasFileReadTransport(dir).readText("/nothing-here", 64)

        assertEquals(AtlasFailure.ABSENT, (result as AtlasTransportRead.Failed).cause)
        assertTrue(result.reason.contains("ENOENT"))
    }

    @Test
    fun `a real permission denial is reported as a denial`() {
        val dir = temp()
        val secret = dir.resolve("secret").also { it.writeText("classified\n") }
        Files.setPosixFilePermissions(secret, PosixFilePermissions.fromString("---------"))
        // A root process ignores the mode bits; claiming this evidence while running as root would be
        // claiming something the environment never demonstrated.
        assumeTrue("needs an unprivileged process", !Files.isReadable(secret))

        val result = AtlasFileReadTransport(dir).readText("/secret", 64)

        assertEquals(AtlasFailure.PERMISSION_DENIED, (result as AtlasTransportRead.Failed).cause)
        assertTrue(result.reason.contains("EACCES"))
    }

    @Test
    fun `a denied directory cannot be enumerated`() {
        val dir = temp()
        val locked = dir.resolve("locked").createDirectories()
        locked.resolve("child").createDirectories()
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"))
        assumeTrue("needs an unprivileged process", !Files.isReadable(locked))

        val result = AtlasFileReadTransport(dir).listNames("/locked", 8)

        assertEquals(AtlasFailure.PERMISSION_DENIED, result.cause)
        assertTrue(result.names.isEmpty())
    }

    @Test
    fun `a directory is not a readable interface`() {
        val dir = temp()
        dir.resolve("sub").createDirectories()

        val result = AtlasFileReadTransport(dir).readText("/sub", 64)

        assertEquals(AtlasFailure.UNKNOWN_CAUSE, (result as AtlasTransportRead.Failed).cause)
        assertTrue(result.reason.contains("directory"))
    }

    @Test
    fun `a listing is cut at the allowance and says so`() {
        val dir = temp()
        val listed = dir.resolve("devfreq").createDirectories()
        (1..6).forEach { listed.resolve("node$it").createDirectories() }

        val cut = AtlasFileReadTransport(dir).listNames("/devfreq", 3)
        val whole = AtlasFileReadTransport(dir).listNames("/devfreq", 32)

        assertEquals(AtlasFailure.NONE, cut.cause)
        assertTrue("a cut listing must never look complete", cut.truncated)
        assertEquals(3, cut.names.size)
        assertFalse(whole.truncated)
        assertEquals(6, whole.names.size)
    }

    @Test
    fun `a listing of a missing directory is absent and returns no names`() {
        val dir = temp()

        val result = AtlasFileReadTransport(dir).listNames("/nope", 8)

        assertEquals(AtlasFailure.ABSENT, result.cause)
        assertTrue(result.names.isEmpty())
        assertFalse("a failed listing is never described as truncated", result.truncated)
    }

    @Test
    fun `real symbolic links are followed and the hop budget is honoured`() {
        val dir = temp()
        dir.resolve("real").createDirectories().resolve("temp").writeText("39000\n")
        Files.createSymbolicLink(dir.resolve("link1"), Path.of("real"))
        Files.createSymbolicLink(dir.resolve("link2"), Path.of("link1"))
        val transport = AtlasFileReadTransport(dir)

        assertEquals(
            "the answer stays in the coordinates it was asked in",
            "/real/temp",
            transport.canonicalPath("/link2/temp", maxHops = 8),
        )
        assertNull("a bound that cannot be met returns nothing", transport.canonicalPath("/link2/temp", maxHops = 1))
        assertEquals(
            AtlasTransportRead.Text("39000\n", truncated = false),
            transport.readText("/link2/temp", 64),
        )
    }

    @Test
    fun `a symbolic link cycle terminates instead of spinning`() {
        val dir = temp()
        Files.createSymbolicLink(dir.resolve("a"), Path.of("b"))
        Files.createSymbolicLink(dir.resolve("b"), Path.of("a"))

        assertNull(AtlasFileReadTransport(dir).canonicalPath("/a", maxHops = 16))
    }

    @Test
    fun `a file used as a directory is absent rather than unknown`() {
        val dir = temp()
        dir.resolve("file").createFile()

        val result = AtlasFileReadTransport(dir).readText("/file/child", 64)

        assertEquals(AtlasFailure.ABSENT, (result as AtlasTransportRead.Failed).cause)
    }

    @Test
    fun `the transport refuses a non positive allowance or a relative path`() {
        val transport = AtlasFileReadTransport(temp())

        assertTrue(runCatching { transport.readText("/x", 0) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { transport.listNames("/x", 0) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { transport.readText("relative", 8) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `an already rooted path is not rooted twice`() {
        val dir = temp()
        dir.resolve("real").createDirectories().resolve("temp").writeText("39000\n")
        val transport = AtlasFileReadTransport(dir)
        val canonical = transport.canonicalPath("/real/temp", maxHops = 8)!!

        // The caller hands the canonical answer straight back to a read; rooting it again would look
        // for it under a duplicated directory and report a real ENOENT as absence.
        assertEquals(AtlasTransportRead.Text("39000\n", truncated = false), transport.readText(canonical, 64))
        assertEquals(
            "and a host path that is already under the root is understood too",
            AtlasTransportRead.Text("39000\n", truncated = false),
            transport.readText(dir.resolve("real/temp").toString(), 64),
        )
    }

    @Test
    fun `the device root is the real filesystem and no other root is allowed to be relative`() {
        assertTrue(runCatching { AtlasFileReadTransport(Path.of("relative/root")) }.exceptionOrNull() is IllegalArgumentException)
        // The production root is the real one; this is the only difference between the device and a test.
        assertEquals("/", AtlasFileReadTransport.ROOT_DEVICE)
    }

    private fun temp(): Path = Files.createTempDirectory("atlas-transport-").also { it.toFile().deleteOnExit() }
}
