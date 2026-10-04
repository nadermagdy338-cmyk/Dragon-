/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.core.spoof

import nd.max.core.atlas.AtlasCapabilityState
import nd.max.ui.util.InstalledModule
import nd.max.ui.util.ModuleInventory

enum class SpoofModuleRole { ENGINE, FRAMEWORK }
enum class SpoofModuleStatus { DETECTED, DISABLED, REMOVAL_PENDING, UPDATE_PENDING, UNKNOWN }
enum class SpoofReadiness { SCAN_UNKNOWN, NO_ENGINE, ENGINE_UNAVAILABLE, NEEDS_ADAPTER }

data class SpoofModule(val module: InstalledModule, val role: SpoofModuleRole) {
    val status: SpoofModuleStatus get() = when {
        module.removalPending == true -> SpoofModuleStatus.REMOVAL_PENDING
        module.disabled == true -> SpoofModuleStatus.DISABLED
        module.updatePending == true -> SpoofModuleStatus.UPDATE_PENDING
        module.disabled == null || module.removalPending == null || module.updatePending == null -> SpoofModuleStatus.UNKNOWN
        else -> SpoofModuleStatus.DETECTED
    }
}

data class SpoofEngineSnapshot(
    val readiness: SpoofReadiness,
    val capability: AtlasCapabilityState,
    val modules: List<SpoofModule>,
)

/**
 * Exact module IDs only, not display-name substrings. Metadata is not authenticated;
 * detection never claims injection, activation, an eligible write route, or unlocked FPS.
 * APK-only Xposed modules, Magisk's built-in Zygisk and KPM runtime aren't inventoried here.
 */
object SpoofEngineRegistry {
    // COPG/JSON/build.sh declares id=COPG. No external code or configuration is copied.
    private val engines = setOf("COPG")
    private val frameworks = setOf("zygisk_lsposed", "riru_lsposed", "zygisknext")

    fun derive(inventory: ModuleInventory): SpoofEngineSnapshot {
        val modules = inventory.modules.mapNotNull { module ->
            val role = when (module.id) {
                in engines -> SpoofModuleRole.ENGINE
                in frameworks -> SpoofModuleRole.FRAMEWORK
                else -> return@mapNotNull null
            }
            SpoofModule(module, role)
        }.sortedBy { it.module.directory }
        val enginesFound = modules.filter { it.role == SpoofModuleRole.ENGINE }
        val readiness = when {
            !inventory.complete -> SpoofReadiness.SCAN_UNKNOWN
            enginesFound.isEmpty() && inventory.modules.any { it.id == null } -> SpoofReadiness.SCAN_UNKNOWN
            enginesFound.isEmpty() -> SpoofReadiness.NO_ENGINE
            enginesFound.any { it.status == SpoofModuleStatus.DETECTED } -> SpoofReadiness.NEEDS_ADAPTER
            enginesFound.any { it.status == SpoofModuleStatus.UNKNOWN } -> SpoofReadiness.SCAN_UNKNOWN
            else -> SpoofReadiness.ENGINE_UNAVAILABLE
        }
        val capability = when (readiness) {
            SpoofReadiness.SCAN_UNKNOWN -> AtlasCapabilityState.UNKNOWN
            SpoofReadiness.NO_ENGINE, SpoofReadiness.ENGINE_UNAVAILABLE -> AtlasCapabilityState.UNAVAILABLE
            SpoofReadiness.NEEDS_ADAPTER -> AtlasCapabilityState.NEEDS_ADAPTER
        }
        return SpoofEngineSnapshot(readiness, capability, modules)
    }
}
