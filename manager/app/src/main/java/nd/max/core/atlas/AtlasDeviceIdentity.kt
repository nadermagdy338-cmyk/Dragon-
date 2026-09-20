package nd.max.core.atlas

/**
 * Device identity from public platform surfaces (plan `P10`).
 *
 * Every field here is something the platform *declares about itself* and an ordinary app may read
 * without any privilege transport: the SoC manufacturer/model, the board and hardware names, the
 * supported ABIs, the API level, the kernel release from `uname`, and the memory class. This file
 * imports **no Android class**: an adapter fills this type from `Build`/`Os`/`ActivityManager` in a
 * later plan, exactly as the transport adapter of `P2` is filled. Keeping the model pure is what
 * makes it testable on the JVM and what keeps identity resolution out of the UI.
 *
 * Three rules, and they are the reason this file exists at all:
 *
 * 1. **Identity is declared, never inferred from a file.** The phase rule is "no capability claim
 *    from the existence of a node"; the mirror is that capability hints come from what the platform
 *    says, and a node is at best a corroborating observation.
 * 2. **A hint is not authority.** [AtlasVendorTags.hints] selects *which vendor-tagged catalog
 *    entries participate*. It cannot exclude a generic entry, cannot promote a claim, and cannot
 *    make anything readable: an inapplicable vendor entry simply produces real read failures, and a
 *    failure is recorded as evidence rather than swallowed.
 * 3. **The identity key is private.** [privateCacheKey] exists so a cached scan can be invalidated
 *    when the device or the kernel changes. It is explicitly **not** report content: adding a
 *    device-specific fingerprint to a sharing artifact is a privacy regression, and `P6`'s
 *    exclusion list owns what may be exported.
 */
data class AtlasDeviceIdentity(
    /** `Build.SOC_MANUFACTURER` (API 31). Null on older platforms, which is not an error. */
    val socManufacturer: String? = null,
    /** `Build.SOC_MODEL` (API 31). */
    val socModel: String? = null,
    /** `Build.HARDWARE`: the platform name, often the SoC family token on Android devices. */
    val hardware: String? = null,
    /** `Build.BOARD`, which some vendors use for the platform name instead. */
    val board: String? = null,
    /** `Build.SUPPORTED_ABIS`, in preference order. Never empty on a running device. */
    val supportedAbis: List<String>,
    /** `Build.VERSION.SDK_INT`. */
    val apiLevel: Int,
    /** `android.system.Os.uname().release`. A public surface; `/proc/version` is denied to apps. */
    val kernelRelease: String? = null,
    /** `ActivityManager.isLowRamDevice()`, when the caller can ask. */
    val isLowRamDevice: Boolean? = null,
    /** `ActivityManager.getMemoryClass()`, when the caller can ask. */
    val memoryClassMb: Int? = null,
) {
    init {
        require(apiLevel >= 1) { "api level must be a real platform level: $apiLevel" }
        require(supportedAbis.isNotEmpty()) { "a running device always declares at least one ABI" }
        require(supportedAbis.all { it.isNotBlank() }) { "ABI names are never blank" }
        require(memoryClassMb == null || memoryClassMb > 0) { "memory class is positive when present" }
        // Declared names are printable text, and enforcing that is what makes [privateCacheKey]
        // unambiguous: the key joins fields with a control character, so a field carrying one could
        // otherwise forge a field boundary and let two different devices share a cache entry.
        require(declaredTexts().none { it.hasControlCharacter() }) {
            "declared names may not contain control characters"
        }
    }

    private fun declaredTexts(): List<String?> =
        listOf(socManufacturer, socModel, hardware, board, kernelRelease) + supportedAbis

    /**
     * Vendor hints for catalog selection, resolved from the declared texts.
     *
     * Unknown text contributes nothing, so an unrecognized device keeps the generic knowledge it
     * would have had anyway — losing vendor entries, never generic ones.
     */
    fun vendorHints(): Set<String> = AtlasVendorTags.hints(this)

    /**
     * A stable key for cache invalidation only.
     *
     * It is deterministic (so two scans of one device agree) and it separates identities that differ
     * in any declared field (so an OTA, a kernel change or a different device invalidates a cached
     * scan). It must never be written into a user-facing or shared artifact.
     */
    fun privateCacheKey(): String = listOf(
        CACHE_KEY_VERSION,
        socManufacturer.orNullPlaceholder(),
        socModel.orNullPlaceholder(),
        hardware.orNullPlaceholder(),
        board.orNullPlaceholder(),
        supportedAbis.joinToString(","),
        apiLevel.toString(),
        kernelRelease.orNullPlaceholder(),
        isLowRamDevice?.toString() ?: NULL_PLACEHOLDER,
        memoryClassMb?.toString() ?: NULL_PLACEHOLDER,
    ).joinToString(KEY_SEPARATOR)

    private fun String?.orNullPlaceholder(): String = this?.trim()?.takeIf { it.isNotEmpty() } ?: NULL_PLACEHOLDER

    companion object {
        const val CACHE_KEY_VERSION: String = "atlas-identity-1"

        /** A control character, so a field value cannot forge a field boundary. */
        private const val KEY_SEPARATOR: String = "\u0001"

        private const val NULL_PLACEHOLDER: String = "\u0000"
    }
}

/**
 * Reviewed vendor tag resolution (`P10`).
 *
 * Two rules produce a hint, and both are deliberately narrow:
 *
 * - **Aliases** map a declaration the platform actually uses (`Google`, `Spreadtrum`) to the tag a
 *   catalog entry is tagged with.
 * - **Model prefixes** map the prefix families vendors use in their own model strings (`mt6983`,
 *   `sm8650`, `gs201`). This one is a heuristic and is labelled as such: it only ever *adds* a
 *   vendor-tagged candidate, whose paths then have to read successfully to produce anything. A wrong
 *   hint costs one failed attempt and records a real failure; it cannot create a claim.
 *
 * A prefix rule additionally requires a token of at least [MIN_PREFIX_TOKEN_LENGTH] characters, so a
 * short accidental token is not read as a vendor.
 */
object AtlasVendorTags {

    /** The tag vocabulary. A tag exists here because a reviewed catalog entry may be tagged with it. */
    val CANONICAL: Set<String> = setOf(
        "mediatek",
        "qualcomm",
        "exynos",
        "tensor",
        "kirin",
        "unisoc",
        "rockchip",
        "amlogic",
        "nvidia",
        "broadcom",
    )

    /** Reviewed aliases: a declaration the platform makes, mapped to the catalog's tag. */
    private val ALIASES: Map<String, String> = mapOf(
        "mediatek" to "mediatek",
        "mtk" to "mediatek",
        "dimensity" to "mediatek",
        "helio" to "mediatek",
        "qualcomm" to "qualcomm",
        "qti" to "qualcomm",
        "snapdragon" to "qualcomm",
        "samsung" to "exynos",
        "exynos" to "exynos",
        "google" to "tensor",
        "tensor" to "tensor",
        "hisilicon" to "kirin",
        "huawei" to "kirin",
        "kirin" to "kirin",
        "spreadtrum" to "unisoc",
        "unisoc" to "unisoc",
        "rockchip" to "rockchip",
        "amlogic" to "amlogic",
        "nvidia" to "nvidia",
        "broadcom" to "broadcom",
    )

    /** Reviewed model-prefix families. A heuristic, and it may only add candidates. */
    private val MODEL_PREFIX_FAMILIES: List<Pair<String, String>> = listOf(
        "msm" to "qualcomm",
        "sm" to "qualcomm",
        "qsd" to "qualcomm",
        "mt" to "mediatek",
        "exynos" to "exynos",
        "s5e" to "exynos",
        "gs" to "tensor",
        "ums" to "unisoc",
        "rk" to "rockchip",
    )

    /** Below this length a token is not read as a model string. */
    const val MIN_PREFIX_TOKEN_LENGTH: Int = 5

    /**
     * Tags for one identity. Deterministic and order-independent: the result is sorted, so two runs
     * and two devices with the same declaration produce byte-identical selection input.
     */
    fun hints(identity: AtlasDeviceIdentity): Set<String> {
        val texts = listOf(identity.socManufacturer, identity.socModel, identity.hardware, identity.board)
        return texts.filterNotNull().flatMap(::tokensOf).mapNotNull(::tagOf).toSortedSet()
    }

    /** Lowercased alphanumeric tokens. Punctuation and case carry no meaning across vendors. */
    fun tokensOf(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }

    private fun tagOf(token: String): String? {
        ALIASES[token]?.let { return it }
        if (token in CANONICAL) return token
        if (token.length < MIN_PREFIX_TOKEN_LENGTH) return null
        return MODEL_PREFIX_FAMILIES.firstOrNull { (prefix, _) -> token.startsWith(prefix) }?.second
    }
}

/**
 * Kernel release parsing (`P10`), kept honest about what a kernel string does **not** say.
 *
 * `uname -r` on Android typically looks like `6.1.75-android14-11-g3f9a1b2c`. The numeric part is a
 * kernel version; the suffix is a build marker. The suffix is **not** an Android version, is not
 * normative, and varies per vendor and per GKI branch, so it is only recorded as "something follows
 * the numbers". Nothing here derives a platform level from a kernel release: that inference is a
 * classic source of confidently wrong device reports.
 */
object AtlasKernelRelease {

    /** A parsed kernel release: what is known, and nothing more. */
    data class Parsed(
        /** The leading `major.minor[.patch]`, or `null` when the text does not start with one. */
        val numericRelease: String?,
        /** True when anything follows the numeric release (an Android or vendor build marker). */
        val hasLocalSuffix: Boolean,
    ) {
        init {
            require(numericRelease == null || KERNEL_RELEASE_NUMERIC.matches(numericRelease)) {
                "a numeric release is digits and dots: $numericRelease"
            }
        }
    }

    fun parse(raw: String?): Parsed {
        val text = raw?.trim().orEmpty()
        val match = KERNEL_RELEASE_LEADING.find(text)
            ?: return Parsed(numericRelease = null, hasLocalSuffix = false)
        return Parsed(numericRelease = match.groupValues[1], hasLocalSuffix = match.groupValues[3].isNotEmpty())
    }
}

// File-level so that constructing [AtlasKernelRelease.Parsed] directly cannot observe an
// uninitialized field: a nested class does not trigger its enclosing object's initialization.
private fun String?.hasControlCharacter(): Boolean = this != null && any { it.code < 0x20 || it.code == 0x7F }

private val KERNEL_RELEASE_NUMERIC = Regex("^\\d+(\\.\\d+){1,2}$")
private val KERNEL_RELEASE_LEADING = Regex("^(\\d+(\\.\\d+){1,2})(.*)$")
