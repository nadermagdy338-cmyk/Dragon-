package nd.max.core.gamespace

import nd.max.core.hardware.RootFileAccess
import nd.max.ui.util.CpuTopologyUtil
import nd.max.ui.util.MtkUtils

/** ترددات حيّة للوحة اللعبة: نفس عقد sysfs التي تقرؤها شاشتا CPU وGPU، بالجذر. */
data class PanelClocks(
    val cpuMhz: Int? = null,
    val cpuCeilingMhz: Int? = null,
    val gpuMhz: Int? = null,
    val gpuCeilingMhz: Int? = null
)

object PanelClockReader {
    private var policies: List<String>? = null
    private var cpuCeiling: Int? = null
    private var gpuCeiling: Int? = null
    private var gpuCeilingResolved = false
    private val LEADING = Regex("\\d+")

    /** يُستدعى من خيط IO. أي حقل لا يجيبه العتاد يبقى null — لا قيمة مختلقة. */
    fun read(): PanelClocks {
        val paths = policies ?: runCatching { CpuTopologyUtil.detectClusters().map { it.policyPath } }
            .getOrDefault(emptyList()).also {
                policies = it
                cpuCeiling = it.maxOfOrNull { p -> CpuTopologyUtil.clusterMaxFreqMhz(p) }?.takeIf { v -> v > 0 }
            }
        val cpu = paths.mapNotNull { RootFileAccess.read("$it/scaling_cur_freq")?.trim()?.toLongOrNull() }
            .maxOrNull()?.div(1000)?.toInt()?.takeIf { it > 0 }
        if (!gpuCeilingResolved) {
            gpuCeilingResolved = true
            gpuCeiling = runCatching {
                MtkUtils.getGpuDevfreqNode()?.let { RootFileAccess.read("$it/max_freq")?.trim()?.toLongOrNull() }
                    ?.div(1_000L)?.toInt()?.takeIf { it > 0 }
                    ?: MtkUtils.oppFrequenciesHz().maxOrNull()?.div(1_000_000L)?.toInt()?.takeIf { it > 0 }
            }.getOrNull()
        }
        val gpu = runCatching { LEADING.find(MtkUtils.getCurrentGpuFreq())?.value?.toIntOrNull() }
            .getOrNull()?.takeIf { it > 0 }
        return PanelClocks(cpu, cpuCeiling, gpu, gpuCeiling)
    }
}
