/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.core.hardware

/** Features that a device may expose to MaxManager. */
enum class HardwareFeature {
    CPU_FREQUENCY,
    CPU_GOVERNOR,
    CPU_CORE_CONTROL,
    CPU_BOOST,
    GPU_FREQUENCY,
    GPU_GOVERNOR,
    GPU_BOOST,
    THERMAL_ZONES,
    THERMAL_COOLING,
    BATTERY_TELEMETRY,
    ZRAM,
    DISPLAY_REFRESH,
    DISPLAY_RESOLUTION,
    TOUCH_CONTROL,
}

enum class AccessLevel {
    NONE,
    READ_ONLY,
    READ_WRITE,
}

data class FeatureCapability(
    val feature: HardwareFeature,
    val access: AccessLevel,
    val backend: String,
    val evidence: List<String> = emptyList(),
) {
    val readable: Boolean get() = access != AccessLevel.NONE
    val writable: Boolean get() = access == AccessLevel.READ_WRITE
}

data class HardwareCapabilitySnapshot(
    val vendor: String,
    val platform: String,
    val features: Map<HardwareFeature, FeatureCapability>,
    val generatedAtMs: Long = System.currentTimeMillis(),
) {
    fun capability(feature: HardwareFeature): FeatureCapability? = features[feature]
    fun supports(feature: HardwareFeature): Boolean = features[feature]?.readable == true
    fun canWrite(feature: HardwareFeature): Boolean = features[feature]?.writable == true
}
