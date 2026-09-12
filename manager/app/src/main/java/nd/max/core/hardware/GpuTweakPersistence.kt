package nd.max.core.hardware

import nd.max.MaxManagerProps
import nd.max.ui.util.PropertyUtils

/**
 * Bounded persisted Global Tweaks consumer for verified GPU Studio state.
 * Saved smart modes re-resolve against the *current* driver's advertised
 * values, so restored state stays capability-driven across reboots and even
 * across different hardware (a saved "adaptive" becomes a full dynamic range
 * on generic devfreq and a lock release on MediaTek fixed-index GPUs).
 */
object GpuTweakPersistence {
    fun loadValidated(device: GpuHardwareBackend.Device): GpuHardwareBackend.Request? {
        when (PropertyUtils.get(MaxManagerProps.GpuStudio.MODE)) {
            "efficiency" -> return requestForMode(device, GpuHardwareBackend.IntentMode.EFFICIENCY)
            "adaptive" -> return requestForMode(device, GpuHardwareBackend.IntentMode.ADAPTIVE)
            "sustained" -> return requestForMode(device, GpuHardwareBackend.IntentMode.SUSTAINED)
        }
        val min = PropertyUtils.get(MaxManagerProps.GpuStudio.MIN_FREQ).toLongOrNull()
        val max = PropertyUtils.get(MaxManagerProps.GpuStudio.MAX_FREQ).toLongOrNull()
        val governor = PropertyUtils.get(MaxManagerProps.GpuStudio.GOVERNOR).takeIf(String::isNotBlank)
        val request = when {
            min != null && max != null -> GpuHardwareBackend.Request(min, max, governor)
            governor != null -> GpuHardwareBackend.Request(governor = governor)
            else -> return null
        }
        return request.takeIf { GpuHardwareBackend.validate(device, it) == null }
    }

    private fun requestForMode(
        device: GpuHardwareBackend.Device,
        mode: GpuHardwareBackend.IntentMode,
    ): GpuHardwareBackend.Request? =
        GpuHardwareBackend.requestForMode(device, mode)?.takeIf { GpuHardwareBackend.validate(device, it) == null }

    fun applySaved(): GpuHardwareBackend.TransactionResult? {
        val device = GpuHardwareBackend.selection().device ?: return null
        val request = loadValidated(device) ?: return null
        return GpuHardwareBackend.apply(device, request)
    }
}
