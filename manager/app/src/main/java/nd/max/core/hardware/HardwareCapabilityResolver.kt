/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.core.hardware

import android.content.Context
import android.os.Build
import nd.max.ui.util.PropertyUtils

/**
 * Read-only capability discovery. Unsupported controls remain visible to the
 * user; the access level explains whether they are readable or writable.
 */
object HardwareCapabilityResolver {
    private const val THERMAL_ROOT = "/sys/class/thermal"
    private const val CPU_ROOT = "/sys/devices/system/cpu/cpufreq"
    private const val ZRAM_ROOT = "/sys/block/zram0"

    fun resolve(context: Context? = null): HardwareCapabilitySnapshot {
        val platform = firstNonBlank(
            PropertyUtils.get("ro.soc.model"),
            PropertyUtils.get("ro.board.platform"),
            PropertyUtils.get("ro.hardware"),
            Build.HARDWARE,
        ) ?: "unknown"
        val vendor = when {
            platform.contains("mt", true) || PropertyUtils.get("ro.mediatek.platform").isNotBlank() -> "mediatek"
            platform.contains("sm", true) || platform.contains("msm", true) || platform.contains("qcom", true) -> "qualcomm"
            PropertyUtils.get("ro.product.manufacturer").isNotBlank() -> PropertyUtils.get("ro.product.manufacturer").lowercase()
            else -> "unknown"
        }

        val features = linkedMapOf<HardwareFeature, FeatureCapability>()
        allFeatures().forEach { features[it] = FeatureCapability(it, AccessLevel.NONE, "not-detected") }
        cpuCapabilities().forEach { features[it.feature] = it }
        gpuCapabilities().forEach { features[it.feature] = it }
        thermalCapabilities().forEach { features[it.feature] = it }
        batteryCapabilities().forEach { features[it.feature] = it }
        zramCapabilities().forEach { features[it.feature] = it }
        displayCapabilities(context).forEach { features[it.feature] = it }
        return HardwareCapabilitySnapshot(vendor, platform, features)
    }

    private fun allFeatures() = HardwareFeature.entries

    private fun cpuCapabilities(): List<FeatureCapability> {
        val policies = RootFileAccess.listDirectories(CPU_ROOT).filter { it.startsWith("policy") }
        if (policies.isEmpty()) return emptyList()
        val governor = policies.map { "$CPU_ROOT/$it/scaling_governor" }
        val mins = policies.map { "$CPU_ROOT/$it/scaling_min_freq" }
        val maxs = policies.map { "$CPU_ROOT/$it/scaling_max_freq" }
        val govAccess = when {
            governor.any(RootFileAccess::writable) -> AccessLevel.READ_WRITE
            governor.any(RootFileAccess::exists) -> AccessLevel.READ_ONLY
            else -> AccessLevel.NONE
        }
        val freqPaths = mins + maxs
        val freqAccess = when {
            freqPaths.any(RootFileAccess::writable) -> AccessLevel.READ_WRITE
            freqPaths.any(RootFileAccess::exists) -> AccessLevel.READ_ONLY
            else -> AccessLevel.NONE
        }
        val onlinePaths = RootFileAccess.listDirectories("/sys/devices/system/cpu")
            .filter { it.matches(Regex("cpu\\d+")) }
            .sortedBy { it.removePrefix("cpu").toIntOrNull() ?: Int.MAX_VALUE }
            .map { "/sys/devices/system/cpu/$it/online" }
            .filter(RootFileAccess::exists)
        val coreAccess = when {
            onlinePaths.any(RootFileAccess::writable) -> AccessLevel.READ_WRITE
            onlinePaths.isNotEmpty() -> AccessLevel.READ_ONLY
            else -> AccessLevel.NONE
        }
        val boostPaths = listOf(
            "$CPU_ROOT/boost",
            "/sys/devices/system/cpu/cpufreq/boost",
        ).distinct().filter(RootFileAccess::exists)
        val boostAccess = when { boostPaths.any(RootFileAccess::writable) -> AccessLevel.READ_WRITE; boostPaths.isNotEmpty() -> AccessLevel.READ_ONLY; else -> AccessLevel.NONE }
        return listOf(
            FeatureCapability(HardwareFeature.CPU_GOVERNOR, govAccess, "cpufreq", governor.filter(RootFileAccess::exists)),
            FeatureCapability(HardwareFeature.CPU_FREQUENCY, freqAccess, "cpufreq", freqPaths.filter(RootFileAccess::exists)),
            FeatureCapability(HardwareFeature.CPU_CORE_CONTROL, coreAccess, "cpu-online", onlinePaths),
            FeatureCapability(HardwareFeature.CPU_BOOST, boostAccess, "cpufreq-boost", boostPaths)
        )
    }

    private fun gpuCapabilities(): List<FeatureCapability> {
        val selection = GpuHardwareBackend.selection()
        val device = selection.device
        if (device == null) return emptyList()

        val governorPaths = listOf("${device.path}/governor").filter(RootFileAccess::exists)
        val frequencyPaths = listOf("${device.path}/min_freq", "${device.path}/max_freq")
            .filter(RootFileAccess::exists)
        val governorAccess = when {
            device.governorWritable -> AccessLevel.READ_WRITE
            device.governor != null || device.governors.isNotEmpty() -> AccessLevel.READ_ONLY
            else -> AccessLevel.NONE
        }
        val frequencyAccess = when {
            device.rangeWritable || device.exactLockWritable -> AccessLevel.READ_WRITE
            device.currentFreq != null || device.minFreq != null || device.maxFreq != null -> AccessLevel.READ_ONLY
            else -> AccessLevel.NONE
        }
        return listOf(
            FeatureCapability(HardwareFeature.GPU_GOVERNOR, governorAccess, "gpu-backend:${device.family.name.lowercase()}", governorPaths),
            FeatureCapability(HardwareFeature.GPU_FREQUENCY, frequencyAccess, "gpu-backend:${device.family.name.lowercase()}", frequencyPaths),
            // Boost semantics are deliberately unavailable until a verified vendor adapter owns them.
            FeatureCapability(HardwareFeature.GPU_BOOST, AccessLevel.NONE, "not-proven", emptyList()),
        )
    }

    private fun thermalCapabilities(): List<FeatureCapability> {
        val dirs = RootFileAccess.listDirectories(THERMAL_ROOT)
        val zones = dirs.filter { it.startsWith("thermal_zone") }.map { "$THERMAL_ROOT/$it" }
        val cooling = dirs.filter { it.startsWith("cooling_device") }.map { "$THERMAL_ROOT/$it" }
        return listOf(
            FeatureCapability(HardwareFeature.THERMAL_ZONES, if (zones.any { RootFileAccess.exists("$it/temp") }) AccessLevel.READ_ONLY else AccessLevel.NONE, "thermal-sysfs", zones),
            FeatureCapability(HardwareFeature.THERMAL_COOLING, when { cooling.any { RootFileAccess.writable("$it/cur_state") } -> AccessLevel.READ_WRITE; cooling.any { RootFileAccess.exists("$it/cur_state") } -> AccessLevel.READ_ONLY; else -> AccessLevel.NONE }, "thermal-cooling", cooling)
        )
    }

    private fun batteryCapabilities(): List<FeatureCapability> = listOf(
        FeatureCapability(HardwareFeature.BATTERY_TELEMETRY, if (RootFileAccess.exists("/sys/class/power_supply/battery")) AccessLevel.READ_ONLY else AccessLevel.NONE, "power-supply", listOf("/sys/class/power_supply/battery"))
    )

    private fun zramCapabilities(): List<FeatureCapability> = listOf(
        FeatureCapability(HardwareFeature.ZRAM, when { RootFileAccess.writable("$ZRAM_ROOT/disksize") -> AccessLevel.READ_WRITE; RootFileAccess.exists("$ZRAM_ROOT/disksize") -> AccessLevel.READ_ONLY; else -> AccessLevel.NONE }, "zram", listOfNotNull("$ZRAM_ROOT/disksize".takeIf(RootFileAccess::exists), "$ZRAM_ROOT/reset".takeIf(RootFileAccess::exists)))
    )

    private fun displayCapabilities(context: Context?): List<FeatureCapability> {
        val wm = context?.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
        val display = wm?.defaultDisplay
        val refresh = display?.refreshRate ?: 0f
        return listOf(
            FeatureCapability(HardwareFeature.DISPLAY_REFRESH, if (refresh > 0f) AccessLevel.READ_ONLY else AccessLevel.NONE, "android-display", if (refresh > 0f) listOf("Display.refreshRate") else emptyList()),
            FeatureCapability(HardwareFeature.DISPLAY_RESOLUTION, if (display != null) AccessLevel.READ_ONLY else AccessLevel.NONE, "android-display", if (display != null) listOf("Display.metrics") else emptyList())
        )
    }

    private fun firstNonBlank(vararg values: String): String? = values.firstOrNull { it.isNotBlank() }
}
