/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.core.spoof

import java.util.Base64

/**
 * Device identity only: no personal identifiers and no runtime activation flag.
 *
 * `fingerprint` and `sdkInt` are **optional on purpose** — an unset field is quieter than a guessed
 * one, and a profile that changes fewer fields has a smaller scope. `null` means "not set here",
 * never a spoofed empty value. Nothing in this class writes anywhere.
 */
data class SpoofProfile(
    val id: String,
    val name: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val fingerprint: String? = null,
    val sdkInt: Int? = null,
    /** `Build.MANUFACTURER` للجهاز المُحاكى (كتالوج COPG يحمله). اختياري: الغائب لا يُكتب ولا يُختلق (ADR-07). */
    val manufacturer: String? = null,
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        require(manufacturer == null || manufacturer.isSpoofValue())
        require(listOf(name, brand, model, device, product).all { it.isSpoofValue() })
        require(fingerprint == null || fingerprint.isSpoofValue())
        require(sdkInt == null || sdkInt in 1..100)
    }
}

/** Rejects blanks, padded values, control characters and over-long input — the same rule everywhere. */
internal fun String.isSpoofValue(): Boolean =
    isNotBlank() && this == trim() && length <= 120 && none(Char::isISOControl)

data class SpoofWorkspace(
    val profiles: List<SpoofProfile> = emptyList(),
    /** Saved intentions, not engine scope or evidence of spoofing. Never carries a consent record. */
    val bindings: Map<String, String> = emptyMap(),
    val globalProfileId: String? = null,
    val appPolicies: Map<String, AppSpoofProfile> = emptyMap(),
) {
    init {
        require(profiles.size <= 100 && bindings.size <= 1000)
        require(profiles.map { it.id }.distinct().size == profiles.size)
        require(bindings.all { (pkg, id) -> validPackage(pkg) && profiles.any { it.id == id } })
        require(globalProfileId == null || profiles.any { it.id == globalProfileId })
        require(appPolicies.size <= 1000 && appPolicies.keys.all(::validPackage))
        require(appPolicies.all { (pkg, policy) -> policy.mode != SpoofInheritanceMode.CUSTOM || pkg in bindings })
    }

    fun upsert(profile: SpoofProfile): SpoofWorkspace =
        copy(profiles = profiles.filterNot { it.id == profile.id } + profile)

    fun remove(id: String): SpoofWorkspace = copy(
        profiles = profiles.filterNot { it.id == id },
        bindings = bindings.filterValues { it != id },
        globalProfileId = globalProfileId?.takeUnless { it == id },
        // Deleting a referenced profile must not unexpectedly enable global inheritance.
        appPolicies = appPolicies.mapValues { (pkg, policy) ->
            if (bindings[pkg] == id) AppSpoofProfile(SpoofInheritanceMode.DISABLED) else policy
        } + bindings.filterValues { it == id }.keys.associateWith { AppSpoofProfile(SpoofInheritanceMode.DISABLED) },
    )

    fun bind(pkg: String, profileId: String?): SpoofWorkspace {
        require(validPackage(pkg))
        require(profileId == null || profiles.any { it.id == profileId })
        return copy(
            bindings = if (profileId == null) bindings - pkg else bindings + (pkg to profileId),
            appPolicies = if (profileId == null) appPolicies - pkg
                else appPolicies + (pkg to (appPolicies[pkg] ?: AppSpoofProfile()).copy(mode = SpoofInheritanceMode.CUSTOM)),
        )
    }

    /** SP-08: drops every tag of one app and leaves every other app untouched. */
    fun clearApp(pkg: String): SpoofWorkspace {
        require(validPackage(pkg))
        return copy(bindings = bindings - pkg, appPolicies = appPolicies - pkg)
    }

    /**
     * P1 — نسخة هذا التطبيق وحده كملف مشترك جديد: القالب الجديد نسخة مستقلة
     * (لا مرجع)، والأصل المشترك لا يتأثر. المعرّف الجديد إلزامي لمنع الالتباس.
     */
    fun copyProfileForApp(
        pkg: String,
        newId: String,
        newName: String,
    ): SpoofWorkspace {
        require(validPackage(pkg))
        val sourceId = bindings[pkg] ?: globalProfileId
            ?: error("no-source-profile")
        val source = profiles.firstOrNull { it.id == sourceId }
            ?: error("missing-source-profile")
        require(profiles.none { it.id == newId })
        require(newName.isSpoofValue())
        return upsert(source.copy(id = newId, name = newName)).bind(pkg, newId)
    }

    fun appPolicy(pkg: String): AppSpoofProfile = appPolicies[pkg]
        ?: AppSpoofProfile(if (pkg in bindings) SpoofInheritanceMode.CUSTOM else SpoofInheritanceMode.GLOBAL)

    fun setAppPolicy(pkg: String, policy: AppSpoofProfile): SpoofWorkspace {
        require(validPackage(pkg))
        return copy(appPolicies = appPolicies + (pkg to policy))
    }

    companion object {
        fun validPackage(pkg: String): Boolean = pkg.length <= 255 &&
            pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))
    }
}

/**
 * One preview row. A `null` side means that field is not set in that profile, and the surface must
 * say "not set" instead of printing an empty or invented value (ADR-07).
 */
data class SpoofFieldPreview(val field: String, val current: String?, val proposed: String?) {
    val changed: Boolean get() = current != proposed
}

fun spoofPreview(current: SpoofProfile, proposed: SpoofProfile): List<SpoofFieldPreview> = listOf(
    SpoofFieldPreview("BRAND", current.brand, proposed.brand),
    SpoofFieldPreview("MODEL", current.model, proposed.model),
    SpoofFieldPreview("DEVICE", current.device, proposed.device),
    SpoofFieldPreview("PRODUCT", current.product, proposed.product),
    SpoofFieldPreview("FINGERPRINT", current.fingerprint, proposed.fingerprint),
    SpoofFieldPreview("SDK_INT", current.sdkInt?.toString(), proposed.sdkInt?.toString()),
)

data class SpoofImportPlan(
    val workspace: SpoofWorkspace,
    val addedProfiles: Int,
    val addedBindings: Int,
    val conflicts: Int,
)

/** Add-only import: existing IDs and app assignments always win. Colliding foreign IDs are skipped. */
fun planSpoofImport(current: SpoofWorkspace, incoming: SpoofWorkspace): SpoofImportPlan {
    val known = current.profiles.associateBy { it.id }
    val additions = incoming.profiles.filter { it.id !in known }
    val rejectedIds = incoming.profiles.filter { it.id in known && known[it.id] != it }.map { it.id }.toSet()
    // A local GLOBAL/DISABLED policy is an assignment too, even without a profile binding.
    // Adding an imported binding there changes future CUSTOM selection and can collide with policy.
    val newBindings = incoming.bindings.filter { (pkg, id) ->
        pkg !in current.bindings && pkg !in current.appPolicies && id !in rejectedIds
    }
    val bindingConflicts = incoming.bindings.count { (pkg, id) ->
        id in rejectedIds || (pkg in current.bindings && current.bindings[pkg] != id) ||
            (pkg !in current.bindings && pkg in current.appPolicies)
    }
    val merged = current.copy(
        profiles = current.profiles + additions,
        bindings = current.bindings + newBindings,
        // Imported policy never overrides local policy, and a conflicting profile cannot activate an app.
        appPolicies = current.appPolicies + incoming.appPolicies.filter { (pkg, policy) ->
            pkg !in current.appPolicies && pkg !in current.bindings &&
                incoming.bindings[pkg] !in rejectedIds &&
                (policy.mode != SpoofInheritanceMode.CUSTOM || pkg in newBindings)
        },
        // Imports do not silently activate a global identity.
    )
    SpoofWorkspaceCodec.encode(merged) // enforce total payload size before any confirmation/write
    return SpoofImportPlan(merged, additions.size, newBindings.size, rejectedIds.size + bindingConflicts)
}

/**
 * Strict, versioned text envelope. Base64 escapes fields; foreign/partial input is never merged.
 *
 * Schema 4 adds per-app COPG tag records (`T`). Schema 3 adds global selection and app/category/field policy records. Schema 2 adds
 * `FINGERPRINT` and `SDK_INT` to the profile record. Schema 1 still decodes —
 * see [decodeProfile] — and its two new fields come back **unset**, never invented. The envelope
 * carries configuration records only: a per-app honesty acknowledgment is
 * deliberately *not* part of any exported file, because consent is not a portable artifact.
 */
object SpoofWorkspaceCodec {
    const val MAX_BYTES = 256 * 1024
    const val SCHEMA = 4
    private const val PREFIX = "MAXMANAGER_SPOOF\t"
    private const val FIELDS_V1 = 6
    private const val FIELDS_V2 = 8
    private const val FIELDS_V4 = 9
    private fun encodeField(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decodeField(value: String): String {
        val bytes = Base64.getDecoder().decode(value)
        val decoded = bytes.toString(Charsets.UTF_8)
        require(encodeField(decoded) == value) // rejects invalid UTF-8/noncanonical encodings
        return decoded
    }

    /**
     * Pure schema migration. Every value is carried over as-is; under schema 1 the two newer
     * fields come back **unset**, because a schema-1 file never held them and inventing a value
     * would be a fabricated spoof setting.
     */
    private fun decodeProfile(schema: Int, fields: List<String>): SpoofProfile {
        require(fields.size == (if (schema >= 4) FIELDS_V4 else if (schema >= 2) FIELDS_V2 else FIELDS_V1))
        val values = fields.map(::decodeField)
        val fingerprint = if (schema >= 2 && values[FIELDS_V1].isNotEmpty()) values[FIELDS_V1] else null
        val sdkToken = if (schema >= 2) values[FIELDS_V1 + 1] else ""
        val sdkInt = if (sdkToken.isEmpty()) null else sdkToken.toIntOrNull() ?: error("invalid-sdk-int")
        val manufacturer = if (schema >= 4 && values[FIELDS_V2].isNotEmpty()) values[FIELDS_V2] else null
        return SpoofProfile(values[0], values[1], values[2], values[3], values[4], values[5], fingerprint, sdkInt, manufacturer)
    }

    fun encode(workspace: SpoofWorkspace): String = buildString {
        append(PREFIX).append(SCHEMA).append('\n')
        workspace.profiles.forEach { profile ->
            val fields = listOf(profile.id, profile.name, profile.brand, profile.model,
                profile.device, profile.product, profile.fingerprint ?: "", profile.sdkInt?.toString() ?: "",
                profile.manufacturer ?: "")
            append("P\t").append(fields.joinToString("\t", transform = ::encodeField)).append('\n')
        }
        workspace.globalProfileId?.let { append("G\t").append(encodeField(it)).append('\n') }
        workspace.appPolicies.toSortedMap().forEach { (pkg, policy) ->
            append("A\t").append(encodeField(pkg)).append('\t').append(policy.mode.name).append('\n')
            policy.categories.toSortedMap(compareBy { it.name }).forEach { (category, mode) ->
                append("C\t").append(encodeField(pkg)).append('\t').append(category.name).append('\t').append(mode.name).append('\n')
            }
            policy.overrides.toSortedMap(compareBy { it.name }).forEach { (field, value) ->
                append("O\t").append(encodeField(pkg)).append('\t').append(field.name).append('\t').append(encodeField(value)).append('\n')
            }
            policy.tags.toSortedSet().forEach { tag ->
                append("T\t").append(encodeField(pkg)).append('\t').append(encodeField(tag)).append('\n')
            }
        }
        workspace.bindings.toSortedMap().forEach { (pkg, id) ->
            append("B\t").append(encodeField(pkg)).append('\t').append(encodeField(id)).append('\n')
        }
    }.also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) }

    fun decode(raw: String): SpoofWorkspace {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val lines = raw.removeSuffix("\n").split('\n')
        require(lines.firstOrNull()?.startsWith(PREFIX) == true)
        val header = lines.first().removePrefix(PREFIX)
        require(header in listOf("1", "2", "3", "4")) { "unsupported-schema" }
        val schema = header.toInt()
        val profiles = mutableListOf<SpoofProfile>()
        val bindings = linkedMapOf<String, String>()
        var globalId: String? = null
        val policies = linkedMapOf<String, AppSpoofProfile>()
        for (line in lines.drop(1)) {
            val fields = line.split('\t')
            when (fields.firstOrNull()) {
                "P" -> {
                    require(profiles.size < 100)
                    profiles += decodeProfile(schema, fields.drop(1))
                }
                "B" -> {
                    require(fields.size == 3 && bindings.size < 1000)
                    val pkg = decodeField(fields[1])
                    require(pkg !in bindings)
                    bindings[pkg] = decodeField(fields[2])
                }
                "G" -> {
                    require(schema >= 3 && fields.size == 2 && globalId == null)
                    globalId = decodeField(fields[1])
                }
                "A" -> {
                    require(schema >= 3 && fields.size == 3 && policies.size < 1000)
                    val pkg = decodeField(fields[1])
                    require(pkg !in policies)
                    policies[pkg] = AppSpoofProfile(SpoofInheritanceMode.valueOf(fields[2]))
                }
                "C" -> {
                    require(schema >= 3 && fields.size == 4)
                    val pkg = decodeField(fields[1])
                    val policy = policies[pkg] ?: error("policy-before-category")
                    val category = SpoofCategory.valueOf(fields[2])
                    require(category !in policy.categories)
                    policies[pkg] = policy.copy(categories = policy.categories + (category to SpoofCategoryMode.valueOf(fields[3])))
                }
                "O" -> {
                    require(schema >= 3 && fields.size == 4)
                    val pkg = decodeField(fields[1])
                    val policy = policies[pkg] ?: error("policy-before-override")
                    val field = SpoofField.valueOf(fields[2])
                    require(field !in policy.overrides)
                    policies[pkg] = policy.copy(overrides = policy.overrides + (field to decodeField(fields[3])))
                }
                "T" -> {
                    require(schema >= 4 && fields.size == 3)
                    val pkg = decodeField(fields[1])
                    val policy = policies[pkg] ?: error("policy-before-tag")
                    val tag = decodeField(fields[2])
                    require(tag !in policy.tags)
                    policies[pkg] = policy.copy(tags = policy.tags + tag)
                }
                else -> error("invalid-record")
            }
        }
        return SpoofWorkspace(profiles, bindings, globalId, policies)
    }
}
