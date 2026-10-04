/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.core.spoof

/** What backs a row. Nothing in this build is measured on a device, so no row may claim to be. */
enum class SpoofTargetEvidence { MEASURED_ON_DEVICE, DOCUMENTED_REFERENCE, UNVERIFIED }

/**
 * Row state. There is deliberately **no success value**: no reviewed write route exists today, so
 * every row is an intent with a reason and none of them can read as "opened" or "available".
 */
enum class SpoofTargetState {
    /** No detectable engine, so no tag could be consumed at all. */
    NO_ENGINE,

    /** The engine's reader/version contract is not verified — the refusal SP-05 also returns. */
    CONTRACT_UNVERIFIED,

    /** The display's own measured modes cannot reach this rate. */
    ABOVE_DEVICE_REFRESH,

    /** The device refresh rate was never measured, so this rate cannot be ranked against it. */
    DEVICE_REFRESH_UNKNOWN,
}

data class SpoofFpsTarget(
    val frames: Int,
    val evidence: SpoofTargetEvidence,
    val source: String,
    val state: SpoofTargetState,
)

/**
 * Games named by the plan's own reading of `maxmanagerApplist.json`, with the document that named
 * them. A package outside this table gets an **empty list**, never a guessed target set.
 */
private val DOCUMENTED_GAMES = mapOf(
    "com.mobile.legends" to "PER-APP-SPOOF-PLAN §2.2 (maxmanagerApplist.json)",
    "com.netease.yysls" to "PER-APP-SPOOF-PLAN §2.2 (maxmanagerApplist.json)",
)

/** The four rates the plan lists. Kept as data so the surface never invents a fifth. */
val SPOOF_FPS_FRAMES = listOf(60, 90, 120, 144)

/**
 * The engine's per-app refresh override is described by COPG's public WebUI (Apache-2.0
 * vocabulary, per the plan §3.1). Its tag spelling was **not** measured in this environment, so
 * the attribution says exactly that instead of quoting a tag that was never read.
 */
internal const val FPS_SOURCE =
    "refresh override documented by the COPG WebUI (Apache-2.0); tag spelling not measured here"

/**
 * Pure: rows for one game. Order of judgement is fixed — no engine, then an unmeasured display,
 * then a rate the display cannot reach, and only then the unverified contract. So an unverified
 * engine never hides a device limit, and a device never claims a rate it cannot reach.
 */
fun spoofFpsTargets(
    packageName: String,
    readiness: SpoofReadiness,
    deviceMaxRefreshHz: Int?,
): List<SpoofFpsTarget> {
    val attribution = DOCUMENTED_GAMES[packageName] ?: return emptyList()
    return SPOOF_FPS_FRAMES.map { frames ->
        val state = when {
            readiness != SpoofReadiness.NEEDS_ADAPTER -> SpoofTargetState.NO_ENGINE
            deviceMaxRefreshHz == null -> SpoofTargetState.DEVICE_REFRESH_UNKNOWN
            frames > deviceMaxRefreshHz -> SpoofTargetState.ABOVE_DEVICE_REFRESH
            else -> SpoofTargetState.CONTRACT_UNVERIFIED
        }
        SpoofFpsTarget(frames, SpoofTargetEvidence.DOCUMENTED_REFERENCE, "$attribution · $FPS_SOURCE", state)
    }
}
