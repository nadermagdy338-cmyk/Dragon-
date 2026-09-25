/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

import org.json.JSONObject

/**
 * Where cached evidence lives (`P5`, slice `E`).
 *
 * Injected rather than hardcoded to `Context` so the store's *rules* — atomic replace, bounded read,
 * corruption, key mismatch — can be tested for real. The Android binding lives in `DataModule` and
 * points at an app-private directory that is never backed up.
 */
interface AtlasStoreIo {

    /** Reads one entry, or `null` when it does not exist or cannot be read. */
    fun read(name: String): String?

    /**
     * Replaces one entry completely. A partially written file must never be observable: either the
     * new text lands or the previous content survives, because a half-written cache entry is
     * indistinguishable from a plausible one.
     */
    fun write(name: String, text: String): Boolean

    fun delete(name: String): Boolean

    /** Entry names currently present. */
    fun list(): List<String>
}

/** Why a lookup did not produce evidence. Each one is a different fact, and none of them is "none". */
enum class AtlasCacheMiss {
    /** Nothing is stored under this id. */
    ABSENT,

    /** Stored, but not what this store writes. Discarded. */
    CORRUPT,

    /** Stored, but larger than the entry bound. Discarded rather than truncated into plausibility. */
    OVERSIZE,

    /** Stored and readable, but for a different device/build/privilege context. */
    KEY_MISMATCH,

    /** Stored by a schema this build does not know. Discarded. */
    UNSUPPORTED_SCHEMA,
}

sealed interface AtlasCacheLookup {
    data class Hit(val evidence: AtlasCachedEvidence) : AtlasCacheLookup
    data class Miss(val reason: AtlasCacheMiss) : AtlasCacheLookup
}

/** A cached observation with the private fingerprint it was stored under. */
data class AtlasCachedEvidence(
    val observation: AtlasObservation,
    val volatility: AtlasVolatility,
    val fingerprint: String,
) {
    /** The freshness rules attached to the observation's own vintage. */
    fun freshnessAt(): AtlasFreshness = AtlasFreshness(
        volatility = volatility,
        observedAtElapsedMs = observation.observedAtElapsedMs ?: 0L,
        bootGeneration = observation.bootGeneration,
        privilegeGeneration = observation.privilegeGeneration,
    )
}

/**
 * Bounded, versioned, self-validating evidence cache.
 *
 * Four properties are the whole point of this file:
 *
 * 1. **Explicit fields and a schema number.** The shape is written by hand (the same style the
 *    profile-sharing document uses) — no reflection and no "unknown key is fine", because a cache
 *    format that silently accepts what it does not understand is a cache that can invent evidence.
 * 2. **Corruption is discarded, not repaired.** An unreadable, unknown-schema, oversize or malformed
 *    entry is **deleted** and reported as a miss, so the caller rescans. Nothing is half-trusted.
 * 3. **The fingerprint is private.** It contains the device's private cache key; it never leaves this
 *    class into a report (`P6` asserts that).
 * 4. **No authority can be stored.** The only writable payload is an [AtlasObservation], a type with
 *    no control field, so a cached observation can never be read back as permission to change
 *    something.
 */
class AtlasEvidenceStore(
    private val io: AtlasStoreIo,
    private val maxEntryBytes: Int = MAX_ENTRY_BYTES,
) {

    init {
        require(maxEntryBytes > 0) { "the entry bound must be positive" }
    }

    /**
     * A private fingerprint for the whole context a cached observation depends on (`T5.3`).
     *
     * Device identity, catalog version, schema, boot and privilege generation: any one of them
     * changing makes every cached observation a statement about a different situation, so they are
     * folded into one key instead of being checked one by one at read time.
     */
    fun fingerprint(
        catalogVersion: String,
        identity: AtlasDeviceIdentity,
        bootGeneration: Long,
        privilegeGeneration: Long,
    ): String {
        require(catalogVersion.isNotBlank()) { "the fingerprint needs a catalog version" }
        require(bootGeneration >= 0L && privilegeGeneration >= 0L) { "generations are non-negative" }
        return listOf(
            SCHEMA.toString(),
            catalogVersion,
            identity.privateCacheKey(),
            bootGeneration.toString(),
            privilegeGeneration.toString(),
        ).joinToString(SEPARATOR)
    }

    fun load(observationId: String, fingerprint: String): AtlasCacheLookup {
        require(AtlasIds.isValidObservationId(observationId)) { "cache id is not canonical: $observationId" }
        val name = nameOf(observationId)
        val text = io.read(name) ?: return AtlasCacheLookup.Miss(AtlasCacheMiss.ABSENT)
        if (text.length > maxEntryBytes) {
            io.delete(name)
            return AtlasCacheLookup.Miss(AtlasCacheMiss.OVERSIZE)
        }
        val decoded = decode(text)
        if (decoded == null) {
            io.delete(name)
            return AtlasCacheLookup.Miss(AtlasCacheMiss.CORRUPT)
        }
        if (decoded.schemaUnsupported) {
            io.delete(name)
            return AtlasCacheLookup.Miss(AtlasCacheMiss.UNSUPPORTED_SCHEMA)
        }
        val evidence = decoded.evidence
        if (evidence == null) {
            io.delete(name)
            return AtlasCacheLookup.Miss(AtlasCacheMiss.CORRUPT)
        }
        if (evidence.fingerprint != fingerprint) {
            // Not deleted: the entry is valid, it is simply about another context, and the next scan
            // will overwrite it. Deleting it here would also throw away evidence a concurrent reader
            // may still be holding.
            return AtlasCacheLookup.Miss(AtlasCacheMiss.KEY_MISMATCH)
        }
        return AtlasCacheLookup.Hit(evidence)
    }

    /** Stores one observation. Returns false when the entry cannot be represented or written. */
    fun save(observation: AtlasObservation, volatility: AtlasVolatility, fingerprint: String): Boolean {
        val text = encode(observation, volatility, fingerprint)
        if (text.length > maxEntryBytes) return false
        return io.write(nameOf(observation.id), text)
    }

    /** Removes every entry this store owns. Returns how many were removed. */
    fun clear(): Int = io.list().count { it.startsWith(NAME_PREFIX) && io.delete(it) }

    fun storedIds(): List<String> = io.list()
        .filter { it.startsWith(NAME_PREFIX) && it.endsWith(NAME_SUFFIX) }
        .map { it.removePrefix(NAME_PREFIX).removeSuffix(NAME_SUFFIX) }
        .sorted()

    /**
     * Drops an entry for one observation, used when the interface answered so a stale failure note
     * cannot outlive it (`T5.4`: failed reads never extend a success's freshness).
     */
    fun forget(observationId: String): Boolean = io.delete(nameOf(observationId))

    // ---- explicit-shape serialization ---------------------------------------------------------------

    private class Decoded(
        val schemaUnsupported: Boolean,
        val evidence: AtlasCachedEvidence?,
    )

    private fun encode(observation: AtlasObservation, volatility: AtlasVolatility, fingerprint: String): String {
        val root = JSONObject()
        root.put(KEY_SCHEMA, SCHEMA)
        root.put(KEY_FINGERPRINT, fingerprint)
        root.put(KEY_VOLATILITY, volatility.name)
        root.put(KEY_ID, observation.id)
        root.put(KEY_DOMAIN, observation.domain.name)
        root.put(KEY_PROVIDER, observation.providerId)
        root.put(KEY_CATALOG, observation.catalogVersion)
        root.put(KEY_SOURCE, observation.sourceId)
        root.put(KEY_PATH, observation.path)
        root.put(KEY_ACCESS, observation.access.name)
        root.put(KEY_SEMANTIC, observation.semanticStatus.name)
        root.put(KEY_UNIT, observation.unit.name)
        root.put(KEY_VALUE, observation.value ?: JSONObject.NULL)
        root.put(KEY_TEXT, observation.textValue ?: JSONObject.NULL)
        root.put(KEY_FAILURE, observation.failure.name)
        root.put(KEY_REASON, observation.reason)
        root.put(KEY_OBSERVED_AT, observation.observedAtElapsedMs ?: JSONObject.NULL)
        root.put(KEY_BOOT, observation.bootGeneration)
        root.put(KEY_PRIVILEGE, observation.privilegeGeneration)
        root.put(KEY_TRUNCATED, observation.truncated)
        return root.toString()
    }

    private fun decode(text: String): Decoded? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val schema = root.optInt(KEY_SCHEMA, -1)
        if (schema != SCHEMA) return Decoded(schemaUnsupported = true, evidence = null)
        // Every field is required. A missing field is corruption, not a default: defaulting `unit`
        // would fabricate a meaning and defaulting `path` would fabricate a source.
        val fields = REQUIRED_KEYS.all { root.has(it) }
        if (!fields) return null
        return runCatching {
            val value = if (root.isNull(KEY_VALUE)) null else root.getDouble(KEY_VALUE)
            val textValue = if (root.isNull(KEY_TEXT)) null else root.getString(KEY_TEXT)
            val observedAt = if (root.isNull(KEY_OBSERVED_AT)) null else root.getLong(KEY_OBSERVED_AT)
            val observation = AtlasObservation(
                id = root.getString(KEY_ID),
                domain = AtlasDomain.valueOf(root.getString(KEY_DOMAIN)),
                providerId = root.getString(KEY_PROVIDER),
                catalogVersion = root.getString(KEY_CATALOG),
                sourceId = root.getString(KEY_SOURCE),
                path = root.getString(KEY_PATH),
                access = AtlasAccess.valueOf(root.getString(KEY_ACCESS)),
                semanticStatus = AtlasSemanticStatus.valueOf(root.getString(KEY_SEMANTIC)),
                unit = AtlasUnit.valueOf(root.getString(KEY_UNIT)),
                value = value,
                textValue = textValue,
                rawRepresentation = null,
                failure = AtlasFailure.valueOf(root.getString(KEY_FAILURE)),
                reason = root.getString(KEY_REASON),
                observedAtElapsedMs = observedAt,
                bootGeneration = root.getLong(KEY_BOOT),
                privilegeGeneration = root.getLong(KEY_PRIVILEGE),
                truncated = root.getBoolean(KEY_TRUNCATED),
            )
            Decoded(
                schemaUnsupported = false,
                evidence = AtlasCachedEvidence(
                    observation = observation,
                    volatility = AtlasVolatility.valueOf(root.getString(KEY_VOLATILITY)),
                    fingerprint = root.getString(KEY_FINGERPRINT),
                ),
            )
        }.getOrNull()
    }

    private fun nameOf(observationId: String): String = "$NAME_PREFIX$observationId$NAME_SUFFIX"

    companion object {
        const val SCHEMA: Int = 1

        /** One cached evidence entry. Small: it is a single observation, never a dump. */
        const val MAX_ENTRY_BYTES: Int = 8 * 1024

        const val NAME_PREFIX: String = "evidence."
        const val NAME_SUFFIX: String = ".json"

        private const val SEPARATOR = "\u001F"

        private const val KEY_SCHEMA = "schema"
        private const val KEY_FINGERPRINT = "fingerprint"
        private const val KEY_VOLATILITY = "volatility"
        private const val KEY_ID = "id"
        private const val KEY_DOMAIN = "domain"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_CATALOG = "catalog"
        private const val KEY_SOURCE = "source"
        private const val KEY_PATH = "path"
        private const val KEY_ACCESS = "access"
        private const val KEY_SEMANTIC = "semantic"
        private const val KEY_UNIT = "unit"
        private const val KEY_VALUE = "value"
        private const val KEY_TEXT = "text"
        private const val KEY_FAILURE = "failure"
        private const val KEY_REASON = "reason"
        private const val KEY_OBSERVED_AT = "observedAt"
        private const val KEY_BOOT = "boot"
        private const val KEY_PRIVILEGE = "privilege"
        private const val KEY_TRUNCATED = "truncated"

        private val REQUIRED_KEYS: List<String> = listOf(
            KEY_SCHEMA,
            KEY_FINGERPRINT,
            KEY_VOLATILITY,
            KEY_ID,
            KEY_DOMAIN,
            KEY_PROVIDER,
            KEY_CATALOG,
            KEY_SOURCE,
            KEY_PATH,
            KEY_ACCESS,
            KEY_SEMANTIC,
            KEY_UNIT,
            KEY_VALUE,
            KEY_TEXT,
            KEY_FAILURE,
            KEY_REASON,
            KEY_OBSERVED_AT,
            KEY_BOOT,
            KEY_PRIVILEGE,
            KEY_TRUNCATED,
        )
    }
}
