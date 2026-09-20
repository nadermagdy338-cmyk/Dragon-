package nd.max.core.hardware

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Frequency controls for per-app profiles. Requests are capability-driven and
 * are never silently substituted with an unsupported value.
 */
@Singleton
class PerAppFrequencyController @Inject constructor(
    private val arbiter: HardwareControlArbiter,
) {
    data class Result(
        val requested: String,
        val applied: Boolean,
        val verified: Boolean,
        val actual: String? = null,
        val error: String? = null,
    )

    fun applyCpuLimits(policyPath: String, minKHz: Long?, maxKHz: Long?): Result {
        if (minKHz == null && maxKHz == null) return Result("default", true, true, "default")
        val key = HardwareControlKey.cpuLimits(policyPath)
        val token = "per-app-cpu-${System.currentTimeMillis()}"
        val baselinePolicy = CpuHardwareBackend.policies().firstOrNull { it.path == policyPath }
            ?: return Result("${minKHz ?: ""}:${maxKHz ?: ""}", false, false, error = "unsupported-policy")
        val baseline = "${baselinePolicy.minKHz ?: ""}:${baselinePolicy.maxKHz ?: ""}"
        // القيمة المطلوبة تُصاغ من جدول OPP المُعلَن: قيمة بين الحدّين وليست في الجدول
        // لا يرفضها السائق بل يُبدّلها، فيصير التحقق فشلًا لتغيير ناجح ثم يُسترجع خط
        // الأساس (`CpuHardwareBackend.snapToAvailableAtOrBelow` يحمل القياس).
        val minSnapped = minKHz?.let { CpuHardwareBackend.snapToAvailableAtOrBelow(baselinePolicy, it) }
        val maxSnapped = maxKHz?.let { CpuHardwareBackend.snapToAvailableAtOrBelow(baselinePolicy, it) }
        val desired = "${minSnapped ?: ""}:${maxSnapped ?: ""}"

        val r = arbiter.submit(
            key = key,
            owner = ControlOwnership.Owner.PER_APP,
            token = token,
            desired = desired,
            apply = { value ->
                val parts = value.split(":", limit = 2)
                CpuHardwareBackend.setPolicyLimits(
                    policyPath,
                    parts.getOrNull(0)?.takeIf { it.isNotBlank() }?.toLongOrNull(),
                    parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull(),
                ).verified
            },
            read = {
                CpuHardwareBackend.policies().firstOrNull { it.path == policyPath }?.let {
                    "${it.minKHz ?: ""}:${it.maxKHz ?: ""}"
                }
            },
            baseline = baseline,
            restore = { value ->
                val parts = value.split(":", limit = 2)
                CpuHardwareBackend.setPolicyLimits(
                    policyPath,
                    parts.getOrNull(0)?.takeIf { it.isNotBlank() }?.toLongOrNull(),
                    parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull(),
                ).verified
            },
        )
        return Result(desired, r.applied, r.verified, r.actual, r.error)
    }

    fun applyGpuCeiling(maxHz: Long): Result {
        val selection = GpuHardwareBackend.selection()
        val device = selection.device
            ?: return Result(maxHz.toString(), false, false, error = selection.reason)
        if (device.frequencies.isEmpty()) {
            return Result(maxHz.toString(), false, false, error = "no-advertised-frequency-range")
        }
        val low = device.frequencies.first()
        val target = device.frequencies.lastOrNull { it <= maxHz }
            ?: return Result(maxHz.toString(), false, false, error = "unsupported-frequency")

        val key = HardwareControlKey.gpuFrequency(device.name)
        val token = "per-app-gpu-${System.currentTimeMillis()}"
        val baseline = GpuHardwareBackend.captureBaseline(device)
        val request = if (device.rangeWritable) {
            GpuHardwareBackend.Request(minFreq = low, maxFreq = target)
        } else {
            GpuHardwareBackend.Request(minFreq = target, maxFreq = target)
        }
        val desired = "${request.minFreq}:${request.maxFreq}"

        val r = arbiter.submit(
            key = key,
            owner = ControlOwnership.Owner.PER_APP,
            token = token,
            desired = desired,
            apply = { value ->
                val split = value.split(":", limit = 2)
                val min = split.getOrNull(0)?.takeIf { it.isNotBlank() }?.toLongOrNull()
                val max = split.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull()
                GpuHardwareBackend.apply(
                    device,
                    GpuHardwareBackend.Request(minFreq = min, maxFreq = max),
                ).verified
            },
            read = {
                GpuHardwareBackend.refresh(device.path)?.let { live ->
                    "${live.minFreq ?: ""}:${live.maxFreq ?: ""}"
                }
            },
            baseline = "${baseline.minFreq ?: ""}:${baseline.maxFreq ?: ""}",
            restore = { GpuHardwareBackend.restoreBaseline(baseline) },
        )
        return Result(desired, r.applied, r.verified, r.actual, r.error)
    }
}
