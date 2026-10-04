/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

enum class SpoofCategory { IDENTITY, BUILD, CPU, GPU, IDENTIFIERS, DISPLAY, NETWORK, LOCALE }
enum class SpoofField(val category: SpoofCategory) {
    BRAND(SpoofCategory.IDENTITY), MODEL(SpoofCategory.IDENTITY), DEVICE(SpoofCategory.IDENTITY),
    PRODUCT(SpoofCategory.IDENTITY), FINGERPRINT(SpoofCategory.BUILD), SDK_INT(SpoofCategory.BUILD),
    CPU_MODEL(SpoofCategory.CPU), GPU_MODEL(SpoofCategory.GPU), ANDROID_ID(SpoofCategory.IDENTIFIERS),
    SERIAL(SpoofCategory.IDENTIFIERS), DPI(SpoofCategory.DISPLAY), CARRIER(SpoofCategory.NETWORK),
    LOCALE(SpoofCategory.LOCALE),
}
enum class SpoofInheritanceMode { GLOBAL, CUSTOM, DISABLED }
enum class SpoofCategoryMode { INHERIT, GLOBAL, REAL }
enum class SpoofSource { HOST_OBSERVATION, GLOBAL_PROFILE, APP_PROFILE, APP_OVERRIDE }
enum class SpoofCapabilityState { SUPPORTED, WRITABLE, READ_ONLY, NEEDS_ADAPTER, UNAVAILABLE, NEVER_TOUCH, UNKNOWN }

/** Assignment lives in workspace.bindings only; policy never duplicates a profile ID. */
data class AppSpoofProfile(
    val mode: SpoofInheritanceMode = SpoofInheritanceMode.GLOBAL,
    val categories: Map<SpoofCategory, SpoofCategoryMode> = emptyMap(),
    val overrides: Map<SpoofField, String> = emptyMap(),
    /**
     * وسوم COPG المعروضة نصًّا (`dnd`، `cpu=x`) — **نيّة محفوظة** لا أثر مُتحقَّق. تُكتب في مصفوفة حزمة التطبيق
     * فقط حين يكون له جهاز فعّال، ويرفضها [SpoofCopgContract.plan] ما لم يسمح بها نحو الإصدار المثبّت.
     */
    val tags: Set<String> = emptySet(),
) {
    init {
        require(tags.size <= CopgTagRules.MAX_TAGS && tags.all(CopgTagRules::valid) && CopgTagRules.conflicts(tags).isEmpty())
        require(overrides.values.all { it.isSpoofValue() })
        overrides[SpoofField.SDK_INT]?.let { require(it.toIntOrNull() in 1..100) }
    }
}

data class EffectiveSpoofField(
    val field: SpoofField,
    val observed: String?,
    val target: String?,
    val source: SpoofSource,
    /** No process probe exists yet. Null must render Unknown, never the desired value. */
    val verifiedEffective: String? = null,
)
data class EffectiveSpoofProfile(val packageName: String?, val fields: List<EffectiveSpoofField>)

fun SpoofProfile.identityValues(): Map<SpoofField, String> = buildMap {
    put(SpoofField.BRAND, brand); put(SpoofField.MODEL, model)
    put(SpoofField.DEVICE, device); put(SpoofField.PRODUCT, product)
    fingerprint?.let { put(SpoofField.FINGERPRINT, it) }
    sdkInt?.let { put(SpoofField.SDK_INT, it.toString()) }
}

/** Resolves intentions only. Host observations may themselves be globally hooked. */
object EffectiveSpoofProfileResolver {
    fun resolve(workspace: SpoofWorkspace, packageName: String?, observed: Map<SpoofField, String>): EffectiveSpoofProfile {
        require(packageName == null || SpoofWorkspace.validPackage(packageName))
        val global = workspace.profiles.firstOrNull { it.id == workspace.globalProfileId }?.identityValues().orEmpty()
        val policy = packageName?.let { workspace.appPolicy(it) }
        val custom = packageName?.let { pkg -> workspace.profiles.firstOrNull { it.id == workspace.bindings[pkg] } }
            ?.identityValues().orEmpty()
        return EffectiveSpoofProfile(packageName, SpoofField.entries.map { field ->
            val categoryMode = policy?.categories?.get(field.category) ?: SpoofCategoryMode.INHERIT
            val realOnly = policy?.mode == SpoofInheritanceMode.DISABLED || categoryMode == SpoofCategoryMode.REAL
            val appValues = policy?.mode == SpoofInheritanceMode.CUSTOM && categoryMode == SpoofCategoryMode.INHERIT
            val override = if (appValues) policy.overrides[field] else null
            val selected = if (appValues) custom[field] else null
            val source = when {
                realOnly -> SpoofSource.HOST_OBSERVATION
                override != null -> SpoofSource.APP_OVERRIDE
                selected != null -> SpoofSource.APP_PROFILE
                global[field] != null -> SpoofSource.GLOBAL_PROFILE
                else -> SpoofSource.HOST_OBSERVATION
            }
            val target = when (source) {
                SpoofSource.HOST_OBSERVATION -> observed[field]
                SpoofSource.APP_OVERRIDE -> override
                SpoofSource.APP_PROFILE -> selected
                SpoofSource.GLOBAL_PROFILE -> global[field]
            }
            EffectiveSpoofField(field, observed[field], target, source)
        })
    }
}

/** Configuration eligibility is not injection capability. External file parsing alone proves neither. */
object SpoofCapabilityResolver {
    fun state(field: SpoofField, configReadable: Boolean?, global: Boolean = false): SpoofCapabilityState = when {
        field == SpoofField.SERIAL || field == SpoofField.ANDROID_ID || field == SpoofField.CARRIER -> SpoofCapabilityState.NEVER_TOUCH
        field == SpoofField.SDK_INT -> SpoofCapabilityState.READ_ONLY // framework API level is not an identity knob
        field.category !in setOf(SpoofCategory.IDENTITY, SpoofCategory.BUILD) -> SpoofCapabilityState.NEEDS_ADAPTER
        global -> SpoofCapabilityState.NEEDS_ADAPTER
        configReadable == null -> SpoofCapabilityState.UNKNOWN
        !configReadable -> SpoofCapabilityState.UNAVAILABLE
        else -> SpoofCapabilityState.WRITABLE // documented COPG config only; process verification still missing
    }
}

/** Reject contradictory fingerprint identities instead of manufacturing missing Build values. */
object SpoofProfileValidation {
    fun valid(profile: SpoofProfile): Boolean {
        val fp = profile.fingerprint ?: return true
        val match = Regex("([^/:\\s]+)/([^/:\\s]+)/([^/:\\s]+):([^/:\\s]+)/([^/:\\s]+)/([^/:\\s]+):([^/:\\s]+)/([^/:\\s]+)")
            .matchEntire(fp) ?: return false
        return match.groupValues[1] == profile.brand && match.groupValues[2] == profile.product &&
            match.groupValues[3] == profile.device
    }
}
