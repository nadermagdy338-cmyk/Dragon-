/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas

/** A user goal, not a kernel command. Atlas chooses the route that can satisfy it. */
enum class AtlasControlGoal {
    PERFORMANCE,
    SUSTAINED_PERFORMANCE,
    EFFICIENCY,
    THERMAL_HEADROOM,
    BATTERY_SAVING,
}

/** Control cohorts are independent; proving one does not authorize another. */
enum class AtlasControlTarget {
    CPU_FREQUENCY,
    CPU_GOVERNOR,
    GPU_FREQUENCY,
    GPU_GOVERNOR,
    THERMAL_PROFILE,
    DISPLAY_REFRESH,
    CHARGING,
    MEMORY,
    STORAGE,
}

/**
 * A typed intent. It carries a desired value only after the UI has validated its shape; it never
 * carries a path or shell command. Package name is optional for global intents and is only context
 * for ownership, never part of a hardware key.
 */
data class AtlasControlIntent(
    val target: AtlasControlTarget,
    val goal: AtlasControlGoal,
    val desired: String? = null,
    val packageName: String? = null,
) {
    init {
        require(packageName == null || PACKAGE_NAME.matches(packageName)) {
            "packageName must be an Android package identifier"
        }
        require(desired == null || desired.length <= MAX_DESIRED_LENGTH) {
            "desired value exceeds the bounded intent size"
        }
    }

    val isPerApp: Boolean get() = packageName != null

    companion object {
        private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
        private const val MAX_DESIRED_LENGTH = 128
    }
}

/** A goal that is measurable by the controller, when the device exposes the signal. */
sealed interface AtlasMeasuredGoal {
    data class TargetFps(val fps: Int) : AtlasMeasuredGoal {
        init { require(fps in 1..240) { "FPS target is outside the supported range" } }
    }

    data class MaxTemperatureCelsius(val celsius: Int) : AtlasMeasuredGoal {
        init { require(celsius in 35..105) { "thermal target is outside the safe planning range" } }
    }

    data class MaxPowerWatts(val watts: Double) : AtlasMeasuredGoal {
        init { require(watts > 0.0 && watts <= 100.0) { "power target is outside the bounded range" } }
    }
}
