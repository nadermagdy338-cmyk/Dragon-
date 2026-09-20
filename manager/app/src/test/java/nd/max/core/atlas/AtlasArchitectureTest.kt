package nd.max.core.atlas

import java.io.File
import nd.max.core.atlas.support.AtlasSourceGuard
import nd.max.core.hardware.AtlasReadBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `P9.2`: the structural guard for the whole Atlas read path.
 *
 * Some Atlas invariants are not behavior at all — they are the absence of a capability. No test can
 * observe "this module cannot write a node" by calling it, because the interesting call does not exist.
 * So it is asserted over the sources, and the guard **fails closed**: if the source tree cannot be
 * found or read, the test throws instead of passing over an empty file list.
 *
 * The guard is deliberately narrow. It does not claim the whole application reads only; it claims that
 * the Atlas package, the bounded read boundary, the rootless transport and the Atlas screen contain no
 * writer, no shell, no privileged transport and no upward dependency — the four things that would turn
 * a measurement feature into a control surface.
 */
class AtlasArchitectureTest {

    /** Everything that participates in reading a device for Atlas. */
    private val readPath = listOf(
        "src/main/java/nd/max/core/atlas/AtlasCatalog.kt",
        "src/main/java/nd/max/core/atlas/AtlasCommunityBank.kt",
        "src/main/java/nd/max/core/atlas/AtlasDiscovery.kt",
        "src/main/java/nd/max/core/atlas/AtlasResolver.kt",
        "src/main/java/nd/max/core/atlas/AtlasRepository.kt",
        "src/main/java/nd/max/core/atlas/AtlasEvidenceStore.kt",
        "src/main/java/nd/max/core/atlas/AtlasFileReadTransport.kt",
        "src/main/java/nd/max/core/atlas/AtlasFixture.kt",
        "src/main/java/nd/max/core/atlas/AtlasFixtureRecorder.kt",
        "src/main/java/nd/max/core/atlas/AtlasPlatformProvider.kt",
        "src/main/java/nd/max/core/hardware/ReadOnlyProbeAccess.kt",
    )

    @Test
    fun `the guard reads a non-trivial number of files instead of silently scanning nothing`() {
        // A guard that passes because it found no files is worse than no guard: it certifies a tree it
        // never read. The count is asserted, and every file below is read through a call that throws.
        val packageDirectory = AtlasSourceGuard.projectFile("src/main/java/nd/max/core/atlas/AtlasCatalog.kt").parentFile
        val files = packageDirectory?.listFiles().orEmpty().filter { it.isFile && it.extension == "kt" }

        assertTrue("the Atlas package must be readable: ${packageDirectory?.absolutePath}", files.isNotEmpty())
        assertEquals("and the guard sees every file in it: ${files.map { it.name }}", 17, files.size)
        readPath.forEach { relative ->
            assertTrue("$relative must exist", AtlasSourceGuard.read(relative).isNotBlank())
        }
    }

    @Test
    fun `the read path names no writer and no privileged transport`() {
        readPath.forEach { relative ->
            val hits = AtlasSourceGuard.forbiddenHits(AtlasSourceGuard.code(relative))
            assertEquals("$relative must not name: $hits", emptyList<String>(), hits)
        }
    }

    @Test
    fun `the rootless transport exposes no write operation`() {
        val transport = AtlasSourceGuard.code("src/main/java/nd/max/core/atlas/AtlasFileReadTransport.kt")

        listOf("writeText", "createFile", "createDirectories", "newOutputStream", "delete").forEach { token ->
            assertTrue(
                "a read transport must not carry `$token`: it is the one seam a control plane could ride in on",
                !transport.contains(token),
            )
        }
        assertTrue("and it reads", transport.contains("readText"))
    }

    @Test
    fun `the boundary keeps its single write-free entry point`() {
        val boundary = AtlasSourceGuard.code("src/main/java/nd/max/core/hardware/ReadOnlyProbeAccess.kt")

        assertTrue("the surface is read and list", boundary.contains("fun read(") && boundary.contains("fun list("))
        // Its transport interface is the authorization: three read-shaped calls and nothing else.
        assertTrue(boundary.contains("interface AtlasReadTransport"))
        assertTrue(!boundary.contains("fun write"))
    }

    @Test
    fun `the atlas core does not depend on the user interface or on the policy engine`() {
        val forbiddenImports = listOf("import nd.max.ui.", "import nd.max.core.maxai.", "import nd.max.core.work.")

        AtlasSourceGuard.projectFile("src/main/java/nd/max/core/atlas/AtlasCatalog.kt")
            .parentFile
            ?.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val code = AtlasSourceGuard.stripComments(file.readText())
                forbiddenImports.forEach { prefix ->
                    assertTrue(
                        "core/atlas must not depend upward: ${file.name} has `$prefix`",
                        !code.contains(prefix),
                    )
                }
            }
    }

    @Test
    fun `the replay transport cannot widen what the app is willing to address`() {
        // A fixture is data that arrives from the outside, so the rule that decides what may be read has
        // to be enforced where the data is parsed — not only where a live path is built.
        val fixture = AtlasSourceGuard.code("src/main/java/nd/max/core/atlas/AtlasFixture.kt")
        val recorder = AtlasSourceGuard.code("src/main/java/nd/max/core/atlas/AtlasFixtureRecorder.kt")

        assertTrue("the fixture format checks the approved anchors", fixture.contains("AtlasAnchors.isApproved"))
        assertTrue("and refuses unsafe paths", fixture.contains("isSafeAbsolutePath"))
        assertTrue("the recorder checks them too", recorder.contains("AtlasAnchors.isApproved"))
        assertTrue(
            "neither one may reach for a writer: a fixture is read, recorded and replayed",
            !fixture.contains("newOutputStream") && !recorder.contains("newOutputStream"),
        )
    }

    @Test
    fun `the fixture ceilings agree with the read budget they exist to cover`() {
        // Two numbers that must match: a fixture that could hold more than one job may address would
        // invite recording something other than a scan, and a smaller one would silently cut a scan.
        assertEquals(
            AtlasReadBudget.DEFAULT.maxEntriesTotal,
            AtlasFixture.MAX_ENTRIES_PER_JOB,
        )
        assertEquals(AtlasFixture.MAX_ENTRIES, AtlasFixture.MAX_ENTRIES_PER_JOB)
    }

    @Test
    fun `both banks are read-only and neither can be consulted for a write`() {
        listOf(AtlasReviewedSeeds.entries(), AtlasCommunityBank.entries()).forEach { entries ->
            assertTrue(entries.isNotEmpty())
            assertTrue(
                "every entry in both banks is a read",
                entries.all { it.safety == AtlasSafetyClass.READ_ONLY },
            )
        }
    }
}
