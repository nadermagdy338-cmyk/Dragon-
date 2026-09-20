package nd.max.core.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The second line of defense (`P7`/`P13`): the accumulated interface vocabulary.
 *
 * The bank is a body of knowledge that may read a device, so the tests are about what it may **claim**
 * rather than about how many entries it holds. Three properties carry the whole design:
 *
 * 1. It passes the same validator as the reviewed bank. A bank that needs its own rules cannot be
 *    reviewed by the same eyes.
 * 2. Nothing in it is marked `SOURCE_VERIFIED`: no implementation was inspected in this run, so every
 *    entry is either a documented claim or a fetched inventory, and saying otherwise would be the first
 *    lie a maintainer acts on.
 * 3. Its readings are `INFERRED`, never `REVIEWED_MATCH` — the tier marker is what keeps a candidate
 *    name from being read as a reviewed fact.
 */
class AtlasCommunityBankTest {

    @Test
    fun `the bank validates under the same rules as the reviewed catalog`() {
        val result = AtlasCommunityBank.validation()

        assertTrue(
            "the community bank must pass the product's own validator: " +
                (result as? AtlasCatalogValidation.Invalid)?.problems.orEmpty(),
            result is AtlasCatalogValidation.Valid,
        )
        assertEquals("atlas-community-1", AtlasCommunityBank.catalog().version)
    }

    @Test
    fun `the bank is wide enough to matter and bounded enough to review`() {
        val size = AtlasCommunityBank.entries().size

        // A second line of defense with a handful of entries would be decoration. Forty is the floor
        // this bank actually reaches, and the ceiling is there so nobody can turn it into an imported
        // path dump without deleting this assertion.
        assertTrue("the bank carries $size interfaces", size >= 40)
        assertTrue("the bank stays reviewable: $size interfaces", size <= 128)
    }

    @Test
    fun `no entry claims to have been verified against an implementation`() {
        val overstated = AtlasCommunityBank.entries()
            .filter { it.provenance.confidence == AtlasSourceConfidence.SOURCE_VERIFIED }

        assertTrue(
            "no community entry may claim SOURCE_VERIFIED in this run: " + overstated.map { it.id },
            overstated.isEmpty(),
        )
    }

    @Test
    fun `every entry is read-only and needs no privilege`() {
        assertTrue(AtlasCommunityBank.entries().all { it.safety == AtlasSafetyClass.READ_ONLY })
        assertTrue(AtlasCommunityBank.entries().none { it.provenance.reference.isBlank() })
        assertTrue(AtlasCommunityBank.entries().none { it.provenance.licenseNote.isBlank() })
    }

    @Test
    fun `no community id collides with a reviewed id`() {
        val reviewed = AtlasReviewedSeeds.entries().map { it.id }.toSet()
        val collisions = AtlasCommunityBank.entries().map { it.id }.filter { it in reviewed }

        assertTrue("an id must name one interface: $collisions", collisions.isEmpty())
    }

    @Test
    fun `no attribute is a path and no root is a wildcard`() {
        AtlasCommunityBank.entries().forEach { entry ->
            assertTrue(
                "an attribute is one safe basename: ${entry.id} -> ${entry.attribute}",
                AtlasIds.isSafeBasename(entry.attribute),
            )
            assertTrue(
                "a root is a concrete approved path: ${entry.id} -> ${entry.parentRoot}",
                AtlasIds.isSafeAbsolutePath(entry.parentRoot),
            )
            assertFalse("a root is never a pattern: ${entry.parentRoot}", entry.parentRoot.contains("*"))
        }
    }

    @Test
    fun `a vendor entry names its vendor and a generic entry does not`() {
        AtlasCommunityBank.entries().forEach { entry ->
            when (entry.provider) {
                AtlasProviderKind.VENDOR -> assertTrue(
                    "a vendor entry names its vendor: ${entry.id}",
                    entry.vendorTags.isNotEmpty() && entry.vendorTags.all { it in AtlasVendorTags.CANONICAL },
                )

                else -> assertTrue(
                    "a generic entry carries no vendor tag: ${entry.id}",
                    entry.vendorTags.isEmpty(),
                )
            }
        }
    }

    @Test
    fun `the bank's readings are inferred and never reviewed matches`() {
        assertEquals(AtlasSemanticStatus.INFERRED, AtlasDiscovery.COMMUNITY.semantic)
        assertEquals("community-bank", AtlasDiscovery.COMMUNITY.providerId)
        assertEquals(AtlasSemanticStatus.REVIEWED_MATCH, AtlasDiscovery.REVIEWED.semantic)
        assertEquals(listOf(AtlasDiscovery.REVIEWED, AtlasDiscovery.COMMUNITY), AtlasDiscovery.defaultSources())
    }

    @Test
    fun `a vendor interface is tagged so it cannot be attempted on the wrong silicon`() {
        val kgsl = AtlasCommunityBank.entries().filter { it.parentRoot == "/sys/class/kgsl" }
        val mtk = AtlasCommunityBank.entries().filter { it.parentRoot == "/proc/gpufreq" }

        assertTrue(kgsl.isNotEmpty())
        assertTrue(kgsl.all { it.vendorTags == setOf("qualcomm") })
        assertTrue(mtk.isNotEmpty())
        assertTrue(mtk.all { it.vendorTags == setOf("mediatek") })
    }
}
