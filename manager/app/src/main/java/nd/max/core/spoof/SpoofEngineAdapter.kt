/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.core.spoof

/**
 * Why a translation is not usable. `DOCUMENTED` means the engine's own WebUI publishes the file
 * shape (see [SpoofCopgContract]) — it still is **not** a readiness claim: this type holds no path,
 * no command and no writer, so producing a plan is never applying it.
 */
enum class SpoofAdapterStatus { UNSUPPORTED_ENGINE, DOCUMENTED }

/**
 * A translation of our vocabulary into the engine's **documented** vocabulary.
 *
 * It carries no file path, no shell command and no writer: producing a plan is not applying it. The
 * write itself lives in [SpoofCopgBackend] and passes through `HardwareControlArbiter`; this type is
 * the pure, testable half — the same one the contract merges into the file.
 */
data class SpoofAdapterPlan(
    val engineId: String?,
    val status: SpoofAdapterStatus,
    /** `key=value` pairs in the engine's documented spelling; empty when the engine is unknown. */
    val records: List<String>,
) {
    val empty: Boolean get() = records.isEmpty()
}

object SpoofEngineAdapter {
    /** Engine ids whose public UI documents a per-app configuration file. */
    private val documented = setOf(SpoofCopgContract.MODULE_ID)

    /**
     * Unknown engine ⇒ **fail closed** with no records at all (never a partial translation written
     * into a file a foreign module owns). A known engine translates the fields the profile sets;
     * an unset optional field is skipped, never emptied.
     */
    fun plan(engineId: String?, profile: SpoofProfile): SpoofAdapterPlan {
        if (engineId == null || engineId !in documented) {
            return SpoofAdapterPlan(engineId, SpoofAdapterStatus.UNSUPPORTED_ENGINE, emptyList())
        }
        val records = SpoofCopgContract.deviceObject(profile).map { (key, value) ->
            "$key=${value.toString().removeSurrounding("\"")}"
        }
        return SpoofAdapterPlan(engineId, SpoofAdapterStatus.DOCUMENTED, records)
    }
}
