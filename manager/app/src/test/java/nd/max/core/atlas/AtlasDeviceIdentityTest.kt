package nd.max.core.atlas

import nd.max.core.atlas.support.AtlasSourceGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `P10` tests. Pure JVM: identity is a declared model, so nothing here touches a device, a file, a
 * privilege transport or the control plane.
 */
class AtlasDeviceIdentityTest {

    // ---- the type's own rules -----------------------------------------------------------------------

    @Test
    fun `a running device identity requires an api level and at least one abi`() {
        assertTrue(
            runCatching { identity(apiLevel = 0) }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { identity(abis = emptyList()) }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { identity(abis = listOf("arm64-v8a", " ")) }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `a missing memory class is unknown rather than zero and a present one is positive`() {
        assertNull(identity(memoryClassMb = null).memoryClassMb)
        assertTrue(
            runCatching { identity(memoryClassMb = 0) }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `declared names may not contain control characters`() {
        // The cache key joins fields with a control character, so a field that carries one could forge
        // a field boundary and make two different devices share a key.
        assertTrue(
            runCatching { identity(hardware = "a\u0001b") }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { identity(socModel = "gs\u0000201") }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    // ---- the private cache key ----------------------------------------------------------------------

    @Test
    fun `the private cache key is deterministic for one device and different for another`() {
        assertEquals(identity().privateCacheKey(), identity().privateCacheKey())
        assertNotEquals(identity().privateCacheKey(), identity(hardware = "mt6983").privateCacheKey())
        assertNotEquals(identity().privateCacheKey(), identity(kernelRelease = "6.1.75-android14").privateCacheKey())
        assertNotEquals(identity().privateCacheKey(), identity(apiLevel = 34).privateCacheKey())
        assertNotEquals(identity().privateCacheKey(), identity(abis = listOf("armeabi-v7a")).privateCacheKey())
        assertNotEquals(identity().privateCacheKey(), identity(isLowRamDevice = true).privateCacheKey())
        assertNotEquals(identity().privateCacheKey(), identity(memoryClassMb = 512).privateCacheKey())
    }

    @Test
    fun `the private cache key separates identities that would collide without a version prefix`() {
        assertTrue(identity().privateCacheKey().startsWith(AtlasDeviceIdentity.CACHE_KEY_VERSION))
    }

    // ---- vendor hints -------------------------------------------------------------------------------

    @Test
    fun `a declared soc manufacturer and model resolve to a canonical tag`() {
        val hints = identity(socManufacturer = "Google", socModel = "GS201").vendorHints()

        assertEquals(setOf("tensor"), hints)
    }

    @Test
    fun `model prefix families resolve a long enough token and the tag is canonical`() {
        assertEquals(setOf("mediatek"), undeclared(hardware = "mt6983").vendorHints())
        assertEquals(setOf("qualcomm"), undeclared(board = "sm8650").vendorHints())
        assertEquals(setOf("exynos"), undeclared(socModel = "exynos2400").vendorHints())
        assertEquals(setOf("unisoc"), undeclared(hardware = "ums9230").vendorHints())
        assertTrue(undeclared(hardware = "mt6983").vendorHints().all { it in AtlasVendorTags.CANONICAL })
    }

    @Test
    fun `vendor hints are case insensitive deduplicated and sorted`() {
        val hints = identity(socManufacturer = "MediaTek", socModel = "Dimensity 9200", hardware = "MT6983").vendorHints()

        assertEquals(setOf("mediatek"), hints)
        assertEquals(hints.toList(), hints.toList().sorted())
        assertEquals(setOf("qualcomm", "tensor"), identity(socModel = "sm8650", board = "gs201").vendorHints())
    }

    @Test
    fun `an unrecognized declaration yields no hints so generic knowledge survives`() {
        assertEquals(emptySet<String>(), identity(socManufacturer = "Acme", socModel = "X1000").vendorHints())
        assertEquals(emptySet<String>(), undeclared().vendorHints())
    }

    @Test
    fun `a short token is not read as a vendor model string`() {
        // "sm1" and "gs2" start with a reviewed prefix but are too short to be model strings, and a
        // wrong hint would cost budget for nothing.
        assertEquals(emptySet<String>(), undeclared(hardware = "sm1").vendorHints())
        assertEquals(emptySet<String>(), undeclared(board = "gs2").vendorHints())
        assertTrue(AtlasVendorTags.MIN_PREFIX_TOKEN_LENGTH >= 5)
    }

    @Test
    fun `a device brand is not a soc vendor and never produces a hint`() {
        // Xiaomi, OnePlus, OPPO build phones; they do not design SoCs. Only SoC-side declarations may
        // produce a tag, which is why the brand field is not part of the identity at all.
        assertEquals(emptySet<String>(), undeclared(socManufacturer = "Xiaomi", socModel = "Redmi").vendorHints())
        assertEquals(emptySet<String>(), undeclared(hardware = "OnePlus", board = "OPPO").vendorHints())
    }

    @Test
    fun `tokens are split on punctuation so a decorated declaration still resolves`() {
        // Tokens are raw alphanumeric runs, digits included; a digits-only token simply resolves to no
        // vendor, which is why the hint set stays small.
        assertEquals(listOf("mt6983", "dimensity", "9200"), AtlasVendorTags.tokensOf("MT6983/Dimensity-9200"))
        assertEquals(setOf("mediatek"), undeclared(hardware = "MT6983::Dimensity").vendorHints())
    }

    // ---- kernel release -----------------------------------------------------------------------------

    @Test
    fun `kernel release parsing keeps the numeric part and records a build marker`() {
        val vendor = AtlasKernelRelease.parse("6.1.75-android14-11-g3f9a1b2c")

        assertEquals("6.1.75", vendor.numericRelease)
        assertTrue(vendor.hasLocalSuffix)
        assertEquals(AtlasKernelRelease.Parsed("5.15.0", false), AtlasKernelRelease.parse("5.15.0"))
        assertEquals(AtlasKernelRelease.Parsed("6.1", false), AtlasKernelRelease.parse(" 6.1 "))
    }

    @Test
    fun `parsing an unusable release is not an error and yields no number`() {
        listOf<String?>(null, "", "   ", "linux", "-android14").forEach { raw ->
            val parsed = AtlasKernelRelease.parse(raw)
            assertNull("$raw must not produce a number", parsed.numericRelease)
            assertFalse(parsed.hasLocalSuffix)
        }
    }

    @Test
    fun `the parsed release cannot carry a platform level because it has nowhere to put one`() {
        // The suffix of an Android kernel release is a build marker, not a version. A type with only
        // these two fields is how that mistake is made impossible rather than merely discouraged.
        val names = AtlasKernelRelease.Parsed::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .map { it.name }
            .toSet()

        assertTrue(names.containsAll(setOf("numericRelease", "hasLocalSuffix")))
        assertTrue(
            "no field may carry a platform level: $names",
            names.none { name ->
                listOf("android", "api", "level", "version").any { it in name.lowercase() }
            },
        )
    }

    // ---- structural guard ---------------------------------------------------------------------------

    @Test
    fun `the identity source names no authority transport or android import`() {
        val code = AtlasSourceGuard.code(IDENTITY_SOURCE)
        val hits = AtlasSourceGuard.forbiddenHits(code)

        assertEquals("$IDENTITY_SOURCE must not name: $hits", emptyList<String>(), hits)
        assertFalse("the identity model is pure Kotlin", code.contains("import android"))
        assertTrue("the guard must actually have read the file", code.length > 3_000)
    }

    // ---- helpers ------------------------------------------------------------------------------------

    /** An identity that declares nothing, for the tests about what an unknown device keeps. */
    private fun undeclared(
        socManufacturer: String? = null,
        socModel: String? = null,
        hardware: String? = null,
        board: String? = null,
    ): AtlasDeviceIdentity = identity(
        socManufacturer = socManufacturer,
        socModel = socModel,
        hardware = hardware,
        board = board,
    )

    private fun identity(
        socManufacturer: String? = "Qualcomm",
        socModel: String? = "SM8650",
        hardware: String? = "kalama",
        board: String? = "kalama",
        abis: List<String> = listOf("arm64-v8a", "armeabi-v7a"),
        apiLevel: Int = 35,
        kernelRelease: String? = "6.1.75-android14-11-g3f9a1b2c",
        isLowRamDevice: Boolean? = false,
        memoryClassMb: Int? = 256,
    ): AtlasDeviceIdentity = AtlasDeviceIdentity(
        socManufacturer = socManufacturer,
        socModel = socModel,
        hardware = hardware,
        board = board,
        supportedAbis = abis,
        apiLevel = apiLevel,
        kernelRelease = kernelRelease,
        isLowRamDevice = isLowRamDevice,
        memoryClassMb = memoryClassMb,
    )

    private companion object {
        const val IDENTITY_SOURCE = "src/main/java/nd/max/core/atlas/AtlasDeviceIdentity.kt"
    }
}
