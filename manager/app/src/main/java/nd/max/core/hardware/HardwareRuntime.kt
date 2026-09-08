/*
 * Runtime inspection layer for MaxManager.
 * It reports what the kernel currently exposes and separates that from what
 * MaxManager would like to control. No vendor-specific command is required here.
 */
package nd.max.core.hardware

import android.content.Context
import java.util.Locale

object HardwareRuntime {
    enum class Health { READY, READ_ONLY, UNAVAILABLE, DEGRADED }

    data class SubsystemHealth(
        val name: String,
        val health: Health,
        val detail: String,
    )

    data class Snapshot(
        val generatedAtMs: Long,
        val cpuPolicies: List<CpuHardwareBackend.Policy>,
        val gpuDevices: List<GpuHardwareBackend.Device>,
        val zram: ZramHardwareBackend.State,
        val capabilities: HardwareCapabilitySnapshot,
        val health: List<SubsystemHealth>,
    ) {
        val supportedCount: Int get() = capabilities.features.values.count { it.access != AccessLevel.NONE }
        val writableCount: Int get() = capabilities.features.values.count { it.access == AccessLevel.READ_WRITE }
        val unsupportedCount: Int get() = capabilities.features.values.count { it.access == AccessLevel.NONE }
    }

    fun snapshot(context: Context? = null): Snapshot {
        val capabilities = HardwareCapabilityResolver.resolve(context)
        val cpu = CpuHardwareBackend.policies()
        val gpu = GpuHardwareBackend.devices()
        val zram = ZramHardwareBackend.readState()
        return Snapshot(
            generatedAtMs = System.currentTimeMillis(),
            cpuPolicies = cpu,
            gpuDevices = gpu,
            zram = zram,
            capabilities = capabilities,
            health = buildHealth(capabilities, cpu, gpu, zram),
        )
    }

    fun statusLine(context: Context? = null): String {
        val s = snapshot(context)
        val ready = s.health.count { it.health == Health.READY }
        val degraded = s.health.count { it.health == Health.DEGRADED || it.health == Health.READ_ONLY }
        return "${s.capabilities.vendor}/${s.capabilities.platform} • $ready ready • $degraded restricted • ${s.unsupportedCount} undetected"
    }

    fun compactReport(context: Context? = null): String {
        val s = snapshot(context)
        return buildString {
            appendLine("MaxManager Hardware Report")
            appendLine("generated=${s.generatedAtMs}")
            appendLine("vendor=${s.capabilities.vendor}")
            appendLine("platform=${s.capabilities.platform}")
            appendLine("capabilities=supported:${s.supportedCount},writable:${s.writableCount},undetected:${s.unsupportedCount}")
            appendLine("cpu_policies=${s.cpuPolicies.size}")
            s.cpuPolicies.forEach { p ->
                appendLine("cpu ${p.name} governor=${p.governor ?: "?"} min=${p.minKHz ?: "?"} max=${p.maxKHz ?: "?"}")
            }
            appendLine("gpu_devices=${s.gpuDevices.size}")
            s.gpuDevices.forEach { g ->
                appendLine("gpu ${g.name} governor=${g.governor ?: "?"} min=${g.minFreq ?: "?"} max=${g.maxFreq ?: "?"} cur=${g.currentFreq ?: "?"}")
            }
            appendLine("zram exists=${s.zram.exists} size=${s.zram.sizeBytes ?: 0} algorithm=${s.zram.algorithm ?: "?"}")
            s.health.forEach { h -> appendLine("health ${h.name.lowercase(Locale.US)}=${h.health} ${h.detail}") }
            val owners = ControlOwnership.snapshot()
            appendLine("ownership_count=${owners.size}")
            owners.forEach { lease ->
                appendLine("owner key=${lease.key} owner=${lease.owner} token=${lease.ownerToken} desired=${lease.desired}")
            }
        }
    }

    private fun buildHealth(
        capabilities: HardwareCapabilitySnapshot,
        cpu: List<CpuHardwareBackend.Policy>,
        gpu: List<GpuHardwareBackend.Device>,
        zram: ZramHardwareBackend.State,
    ): List<SubsystemHealth> = listOf(
        subsystem("cpu", capabilities, HardwareFeature.CPU_FREQUENCY, cpu.isNotEmpty(), "policies=${cpu.size}"),
        subsystem("gpu", capabilities, HardwareFeature.GPU_FREQUENCY, gpu.isNotEmpty(), "devices=${gpu.size}"),
        subsystem("thermal", capabilities, HardwareFeature.THERMAL_ZONES, capabilities.supports(HardwareFeature.THERMAL_ZONES), "sysfs=${capabilities.supports(HardwareFeature.THERMAL_ZONES)}"),
        subsystem("zram", capabilities, HardwareFeature.ZRAM, zram.exists, "size=${zram.sizeBytes ?: 0}"),
        subsystem("display", capabilities, HardwareFeature.DISPLAY_REFRESH, capabilities.supports(HardwareFeature.DISPLAY_REFRESH), "refresh=${capabilities.capability(HardwareFeature.DISPLAY_REFRESH)?.access}"),
    )

    private fun subsystem(
        name: String,
        caps: HardwareCapabilitySnapshot,
        feature: HardwareFeature,
        exists: Boolean,
        detail: String,
    ): SubsystemHealth {
        val access = caps.capability(feature)?.access ?: AccessLevel.NONE
        val health = when {
            !exists || access == AccessLevel.NONE -> Health.UNAVAILABLE
            access == AccessLevel.READ_WRITE -> Health.READY
            else -> Health.READ_ONLY
        }
        return SubsystemHealth(name, health, detail)
    }
}
