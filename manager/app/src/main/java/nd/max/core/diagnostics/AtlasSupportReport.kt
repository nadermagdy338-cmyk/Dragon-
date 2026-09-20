package nd.max.core.diagnostics

import nd.max.core.atlas.AtlasFeatureOutcome
import nd.max.core.atlas.AtlasObservation
import nd.max.core.atlas.AtlasScanState
import nd.max.core.atlas.AtlasScanStatus
import nd.max.core.atlas.AtlasSemanticStatus
import nd.max.core.atlas.AtlasStage
import nd.max.core.atlas.AtlasUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * The minimized support report (`P6`, slice `F`).
 *
 * This is the last-resort artifact a user may choose to send after the safe stages have finished, and
 * it is designed by **subtraction**: the type has no field for a secret, an identifier, a file path
 * outside the reviewed catalog, a package list, a command output, a log line or a dump. A field that
 * does not exist cannot leak, and a test cannot be fooled into passing by a value that happens not to
 * be present today.
 *
 * Four rules are enforced here rather than trusted:
 *
 * 1. **The schema is explicit and versioned.** Unknown fields on the way in are rejected with an
 *    explicit cause, because a report format that silently accepts what it does not understand cannot
 *    be re-read a year later.
 * 2. **An incomplete run cannot produce a complete report.** A running or cancelled scan has no
 *    report at all ([NotReportable]): saying "here is what your device answered" while the scan was
 *    interrupted misrepresents it.
 * 3. **The size bound is checked before the artifact exists.** Oversize input is rejected rather than
 *    truncated into something plausible.
 * 4. **Preview and share are the same bytes.** The serialization is a pure function of the report, so
 *    what the user previewed is bit-for-bit what leaves the device — and serializing performs no read.
 */
data class AtlasSupportReport(
    val appVersion: String,
    val catalogVersion: String,
    val device: ReportedDevice,
    val summary: ReportedSummary,
    val features: List<ReportedFeature>,
    val diagnostics: ReportedDiagnostics? = null,
    val schema: Int = SCHEMA,
) {

    /** A coarse device description: model family, API level and ABI — never an identifier. */
    data class ReportedDevice(
        val socModel: String?,
        val socManufacturer: String?,
        val apiLevel: Int,
        val abi: String?,
        val kernelMajorMinor: String?,
        val lowRamDevice: Boolean?,
    )

    data class ReportedSummary(
        val status: String,
        /** `null` when the scan did not know how many features it had. */
        val percent: Int?,
        val totalFeatures: Int,
        val observed: Int,
        val unresolved: Int,
        val cacheHits: Int,
        val suppressed: Int,
        val limitsReached: Boolean,
    )

    /** One feature's outcome, in catalog vocabulary. Nothing device-specific is added. */
    data class ReportedFeature(
        val id: String,
        val stage: String,
        val outcome: String,
        val failure: String?,
        val unit: String?,
        /** Age of the evidence when the report was built, or `null` when it is not ageable. */
        val ageMs: Long?,
        /** A short, reviewed reason code — never a raw message or a file path beyond the catalog. */
        val reason: String,
    )

    data class ReportedDiagnostics(
        val warnCount: Int,
        val errorCount: Int,
        val distinctComponents: Int,
        /** Allowlisted component names only; messages are not representable here. */
        val components: List<ComponentSummary>,
    ) {
        data class ComponentSummary(val component: String, val level: String, val count: Int)
    }

    // ---- construction ------------------------------------------------------------------------------

    companion object {

        const val SCHEMA: Int = 1

        /** The serialized bound, checked before the artifact exists (`T6.4`). */
        const val MAX_BYTES: Int = 256 * 1024

        /** Features are bounded too: a report is a summary, not a transcript. */
        const val MAX_FEATURES: Int = 512

        /** Components are bounded for the same reason. */
        const val MAX_COMPONENTS: Int = 64

        /**
         * Builds a report, or refuses with the reason.
         *
         * The refusal cases are the interesting part: they are exactly the situations in which sending
         * a report would say something untrue about the device.
         */
        fun from(
            state: AtlasScanState,
            appVersion: String,
            catalogVersion: String,
            device: ReportedDevice,
            diagnostics: ReportedDiagnostics? = null,
            nowMs: Long? = null,
            /** Comes from the discovery job, not from the state: only the job knows its own limits. */
            limitsReached: Boolean = false,
        ): Result {
            if (state.status == AtlasScanStatus.IDLE || state.isRunning) {
                return Result.NotReportable("the scan has not finished")
            }
            if (state.status == AtlasScanStatus.CANCELLED) {
                return Result.NotReportable("the scan was cancelled, so its contents describe a partial pass")
            }
            if (state.outcomes.size > MAX_FEATURES) {
                return Result.NotReportable("more than $MAX_FEATURES features; the report is a summary")
            }
            val report = AtlasSupportReport(
                appVersion = appVersion,
                catalogVersion = catalogVersion,
                device = device,
                summary = ReportedSummary(
                    status = state.status.name,
                    percent = state.percent,
                    totalFeatures = state.totalFeatures,
                    observed = state.observed.size,
                    unresolved = state.unresolved.size,
                    cacheHits = state.cacheHits,
                    suppressed = state.suppressed,
                    limitsReached = limitsReached,
                ),
                features = state.outcomes.map { outcome -> feature(outcome, nowMs) },
                diagnostics = diagnostics,
            )
            return Result.Ready(report)
        }

        private fun feature(outcome: AtlasFeatureOutcome, nowMs: Long?): ReportedFeature = when (outcome) {
            is AtlasFeatureOutcome.Observed -> ReportedFeature(
                id = outcome.id,
                stage = outcome.stage.name,
                outcome = OUTCOME_OBSERVED,
                failure = null,
                unit = outcome.observation.unit.name,
                ageMs = ageOf(outcome.observation, nowMs, outcome.fromCache),
                reason = "from-${if (outcome.fromCache) "held-evidence" else "read"}" +
                    (outcome.cacheMiss?.let { ":after-${it.name.lowercase()}" } ?: ""),
            )

            is AtlasFeatureOutcome.Unresolved -> ReportedFeature(
                id = outcome.id,
                stage = outcome.stage.name,
                outcome = OUTCOME_UNRESOLVED,
                failure = outcome.failure.name,
                unit = null,
                ageMs = null,
                reason = "cause-${outcome.failure.name.lowercase()}",
            )

            is AtlasFeatureOutcome.Suppressed -> ReportedFeature(
                id = outcome.id,
                stage = AtlasStage.REVIEWED_KNOWLEDGE.name,
                outcome = OUTCOME_SUPPRESSED,
                failure = outcome.cause.name,
                unit = null,
                ageMs = null,
                reason = "suppressed-by-recent-attempt",
            )

            is AtlasFeatureOutcome.Cancelled -> ReportedFeature(
                id = outcome.id,
                stage = AtlasStage.BOUNDED_DISCOVERY.name,
                outcome = OUTCOME_CANCELLED,
                failure = null,
                unit = null,
                ageMs = null,
                reason = "cancelled",
            )
        }

        /** An age, or `null`: evidence that cannot be aged is reported as un-ageable, not as zero. */
        private fun ageOf(observation: AtlasObservation, nowMs: Long?, fromCache: Boolean): Long? {
            if (!fromCache) return 0L
            val observedAt = observation.observedAtElapsedMs ?: return null
            val now = nowMs ?: return null
            return (now - observedAt).takeIf { it >= 0L }
        }

        /** How the reviewer should read the observation's meaning. */
        fun semanticLabel(status: AtlasSemanticStatus): String = status.name.lowercase()

        /** True when a unit can be shown as a number; free text is not a measurement. */
        fun isMeasured(unit: AtlasUnit): Boolean = unit != AtlasUnit.UNKNOWN

        /**
         * Re-reads a report. Used by the maintainer path that turns one into a fixture (`P9`).
         *
         * A report this build does not understand is refused as `UNSUPPORTED_SCHEMA` rather than read
         * optimistically: half-understood evidence is how a support decision gets made on the wrong
         * basis, and a format that silently accepts what it cannot explain cannot be re-read later.
         */
        fun decode(text: String): Decoded {
            if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return Decoded(null, DecodeFailure.OVERSIZE)
            val root = runCatching { JSONObject(text) }.getOrNull() ?: return Decoded(null, DecodeFailure.MALFORMED)
            if (root.optInt(KEY_SCHEMA, -1) != SCHEMA) return Decoded(null, DecodeFailure.UNSUPPORTED_SCHEMA)
            return runCatching {
                val device = root.getJSONObject(KEY_DEVICE)
                val summary = root.getJSONObject(KEY_SUMMARY)
                val features = root.getJSONArray(KEY_FEATURES)
                Decoded(
                    AtlasSupportReport(
                        appVersion = root.getString(KEY_APP),
                        catalogVersion = root.getString(KEY_CATALOG),
                        device = ReportedDevice(
                            socModel = device.optStringOrNull("soc_model"),
                            socManufacturer = device.optStringOrNull("soc_manufacturer"),
                            apiLevel = device.getInt("api_level"),
                            abi = device.optStringOrNull("abi"),
                            kernelMajorMinor = device.optStringOrNull("kernel"),
                            lowRamDevice = if (device.isNull("low_ram")) null else device.getBoolean("low_ram"),
                        ),
                        summary = ReportedSummary(
                            status = summary.getString("status"),
                            percent = if (summary.isNull("percent")) null else summary.getInt("percent"),
                            totalFeatures = summary.getInt("total"),
                            observed = summary.getInt("observed"),
                            unresolved = summary.getInt("unresolved"),
                            cacheHits = summary.getInt("cache_hits"),
                            suppressed = summary.getInt("suppressed"),
                            limitsReached = summary.getBoolean("limits_reached"),
                        ),
                        features = (0 until features.length()).map { index ->
                            val item = features.getJSONObject(index)
                            ReportedFeature(
                                id = item.getString("id"),
                                stage = item.getString("stage"),
                                outcome = item.getString("outcome"),
                                failure = item.optStringOrNull("failure"),
                                unit = item.optStringOrNull("unit"),
                                ageMs = if (item.isNull("age_ms")) null else item.getLong("age_ms"),
                                reason = item.getString("reason"),
                            )
                        },
                    ),
                    null,
                )
            }.getOrNull() ?: Decoded(null, DecodeFailure.MALFORMED)
        }

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

        private const val KEY_SCHEMA = "schema"
        private const val KEY_APP = "app"
        private const val KEY_CATALOG = "catalog"
        private const val KEY_DEVICE = "device"
        private const val KEY_SUMMARY = "summary"
        private const val KEY_FEATURES = "features"
        private const val KEY_DIAGNOSTICS = "diagnostics"

        /**
         * The outcome vocabulary, published (`P13`).
         *
         * The Atlas doctor compares a stored report with a replayed run, so the two sides must agree
         * on the words. A second copy of these four strings in the doctor would be a copy that can
         * drift, and a drifted comparison reports differences that are spellings.
         */
        const val OUTCOME_OBSERVED = "observed"
        const val OUTCOME_UNRESOLVED = "unresolved"
        const val OUTCOME_SUPPRESSED = "suppressed"
        const val OUTCOME_CANCELLED = "cancelled"
    }

    sealed interface Result {
        data class Ready(val report: AtlasSupportReport) : Result
        data class NotReportable(val reason: String) : Result
    }

    /** Why a stored report could not be read back. Each one is a different fact. */
    enum class DecodeFailure { UNSUPPORTED_SCHEMA, MALFORMED, OVERSIZE }

    /** What `decode` produced: a report and no failure, or the other way round. */
    data class Decoded(val report: AtlasSupportReport?, val failure: DecodeFailure?) {
        val isReadable: Boolean get() = report != null && failure == null
    }

    // ---- serialization -----------------------------------------------------------------------------

    /** The artifact. Pure: it reads nothing and touches no file. */
    fun encode(): String {
        val root = JSONObject()
        root.put(KEY_SCHEMA, schema)
        root.put(KEY_APP, appVersion)
        root.put(KEY_CATALOG, catalogVersion)
        root.put(
            KEY_DEVICE,
            JSONObject().apply {
                put("soc_model", device.socModel ?: JSONObject.NULL)
                put("soc_manufacturer", device.socManufacturer ?: JSONObject.NULL)
                put("api_level", device.apiLevel)
                put("abi", device.abi ?: JSONObject.NULL)
                put("kernel", device.kernelMajorMinor ?: JSONObject.NULL)
                put("low_ram", device.lowRamDevice ?: JSONObject.NULL)
            },
        )
        root.put(
            KEY_SUMMARY,
            JSONObject().apply {
                put("status", summary.status)
                put("percent", summary.percent ?: JSONObject.NULL)
                put("total", summary.totalFeatures)
                put("observed", summary.observed)
                put("unresolved", summary.unresolved)
                put("cache_hits", summary.cacheHits)
                put("suppressed", summary.suppressed)
                put("limits_reached", summary.limitsReached)
            },
        )
        root.put(
            KEY_FEATURES,
            JSONArray().apply {
                features.forEach { feature ->
                    put(
                        JSONObject().apply {
                            put("id", feature.id)
                            put("stage", feature.stage)
                            put("outcome", feature.outcome)
                            put("failure", feature.failure ?: JSONObject.NULL)
                            put("unit", feature.unit ?: JSONObject.NULL)
                            put("age_ms", feature.ageMs ?: JSONObject.NULL)
                            put("reason", feature.reason)
                        },
                    )
                }
            },
        )
        diagnostics?.let { diagnostics ->
            root.put(
                KEY_DIAGNOSTICS,
                JSONObject().apply {
                    put("warn", diagnostics.warnCount)
                    put("error", diagnostics.errorCount)
                    put("components", diagnostics.distinctComponents)
                    put(
                        "entries",
                        JSONArray().apply {
                            diagnostics.components.forEach { component ->
                                put(
                                    JSONObject().apply {
                                        put("component", component.component)
                                        put("level", component.level)
                                        put("count", component.count)
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
        return root.toString()
    }

}
