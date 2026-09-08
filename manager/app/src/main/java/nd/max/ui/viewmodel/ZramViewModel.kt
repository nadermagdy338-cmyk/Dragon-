/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.viewmodel

import nd.max.MaxManagerProps

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nd.max.ui.util.PropertyUtils
import nd.max.core.hardware.ZramHardwareBackend

data class ZramSizePreset(
    val id: String,
    val labelRes: String,
    val mb: Int
)

class ZramViewModel : ViewModel() {

    companion object {
        private const val ZRAM_DEV = "/sys/block/zram0"
        private const val PROP_SIZE_MB = MaxManagerProps.Storage.ZRAM_SIZE_MB
        private const val PROP_SWAPPINESS = MaxManagerProps.Storage.SWAPPINESS
        private const val PROP_COMP_ALGO = MaxManagerProps.Storage.ZRAM_COMP_ALGORITHM
        const val MAX_ZRAM_MB = 25600 // 25.0 GB ceiling

        val SIZE_PRESETS = listOf(
            ZramSizePreset("disabled", "zram_preset_disabled", 0),
            ZramSizePreset("light", "zram_preset_light", 4096),
            ZramSizePreset("stock", "zram_preset_stock", 8192),
            ZramSizePreset("power", "zram_preset_power", 12288)
        )
    }

    var isAvailable by mutableStateOf<Boolean?>(null)
        private set
    var compAlgorithm by mutableStateOf("lz4")
        @JvmName("setCompAlgorithmState") private set
    var currentDiskSizeMb by mutableStateOf(0)
        private set
    var usedSwapMb by mutableStateOf(0)
        private set
    var totalSwapMb by mutableStateOf(0)
        private set
    var origDataMb by mutableStateOf(0)
        private set
    var compDataMb by mutableStateOf(0)
        private set
    var swappiness by mutableStateOf(60)
        private set
    var selectedPresetId by mutableStateOf("stock")
        private set

    var availableCompAlgorithms by mutableStateOf<List<String>>(emptyList())
        private set

    // Hardware Architecture panel — static capability facts detected once,
    // reusing the same /proc/swaps parse refreshStats() already does instead
    // of a second parallel detector.
    var blockDeviceNodeExists by mutableStateOf(false)
        private set
    var swapPriority by mutableStateOf<Int?>(null)
        private set
    var kernelCompactionSupported by mutableStateOf(false)
        private set
    var multiStreamCount by mutableStateOf<Int?>(null)
        private set

    val efficiencyRatio: Float
        get() = if (compDataMb > 0) origDataMb.toFloat() / compDataMb.toFloat() else 1f

    val ramSavedMb: Int
        get() = (origDataMb - compDataMb).coerceAtLeast(0)

    private var pollJob: kotlinx.coroutines.Job? = null

    fun loadState() {
        viewModelScope.launch(Dispatchers.IO) {
            val exists = Shell.cmd("test -e $ZRAM_DEV/disksize && echo 1 || echo 0")
                .exec().out.joinToString("").trim() == "1"

            if (!exists) {
                isAvailable = false
                return@launch
            }
            isAvailable = true

            val compAlgoRaw = Shell.cmd("cat $ZRAM_DEV/comp_algorithm 2>/dev/null")
                .exec().out.joinToString("")
            compAlgorithm = Regex("\\[(.*?)]").find(compAlgoRaw)?.groupValues?.get(1)
                ?.ifEmpty { null } ?: compAlgoRaw.trim().substringBefore(' ').ifEmpty { "lz4" }
            availableCompAlgorithms = compAlgoRaw.replace("[", "").replace("]", "")
                .trim().split(Regex("\\s+")).filter { it.isNotBlank() }

            swappiness = Shell.cmd("cat /proc/sys/vm/swappiness 2>/dev/null")
                .exec().out.joinToString("").trim().toIntOrNull() ?: 60

            val savedPreset = PropertyUtils.get(MaxManagerProps.Storage.ZRAM_PRESET)
            if (savedPreset.isNotEmpty()) selectedPresetId = savedPreset

            blockDeviceNodeExists = Shell.cmd("test -e /dev/block/zram0 && echo 1 || echo 0")
                .exec().out.joinToString("").trim() == "1"
            kernelCompactionSupported = Shell.cmd("test -e /proc/sys/vm/compact_memory && echo 1 || echo 0")
                .exec().out.joinToString("").trim() == "1"
            multiStreamCount = Shell.cmd("cat $ZRAM_DEV/max_comp_streams 2>/dev/null")
                .exec().out.joinToString("").trim().toIntOrNull()

            refreshStats()
            startPolling()
        }
    }

    private fun refreshStats() {
        val disksizeBytes = Shell.cmd("cat $ZRAM_DEV/disksize 2>/dev/null")
            .exec().out.joinToString("").trim().toLongOrNull() ?: 0L
        currentDiskSizeMb = (disksizeBytes / (1024 * 1024)).toInt()

        // mm_stat: orig_data_size compr_data_size mem_used_total ...
        val mmStat = Shell.cmd("cat $ZRAM_DEV/mm_stat 2>/dev/null")
            .exec().out.joinToString("").trim().split(Regex("\\s+"))
        origDataMb = (mmStat.getOrNull(0)?.toLongOrNull() ?: 0L).let { it / (1024 * 1024) }.toInt()
        compDataMb = (mmStat.getOrNull(1)?.toLongOrNull() ?: 0L).let { it / (1024 * 1024) }.toInt()

        // /proc/swaps: Filename Type Size Used Priority (Size/Used are in KB)
        val swapsOut = Shell.cmd("cat /proc/swaps 2>/dev/null").exec().out
        val zramSwapLine = swapsOut.drop(1).firstOrNull { it.contains("zram") }
        val fields = zramSwapLine?.trim()?.split(Regex("\\s+"))
        totalSwapMb = ((fields?.getOrNull(2)?.toLongOrNull() ?: 0L) / 1024).toInt()
        usedSwapMb = ((fields?.getOrNull(3)?.toLongOrNull() ?: 0L) / 1024).toInt()
        swapPriority = fields?.getOrNull(4)?.toIntOrNull()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshStats()
                delay(3000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }

    fun applyPreset(preset: ZramSizePreset) {
        selectedPresetId = preset.id
        applySizeMb(preset.mb)
        PropertyUtils.set(MaxManagerProps.Storage.ZRAM_PRESET, preset.id)
    }

    fun applyCustomSizeMb(mb: Int) {
        selectedPresetId = "custom"
        applySizeMb(mb.coerceIn(0, MAX_ZRAM_MB))
        PropertyUtils.set(MaxManagerProps.Storage.ZRAM_PRESET, "custom")
    }

    /**
     * Resizing a live zram device requires it to be swapped off and reset first
     * (the kernel refuses to change disksize on an active device), then swapped
     * back on at the new size. This briefly frees the compressed swap pages back
     * to RAM, same as what any zram-config tool does.
     */
    private fun applySizeMb(mb: Int) {
        currentDiskSizeMb = mb
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = mb.toLong() * 1024 * 1024
            if (mb == 0) {
                Shell.cmd(
                    "swapoff $ZRAM_DEV 2>/dev/null",
                    "echo 1 > $ZRAM_DEV/reset 2>/dev/null"
                ).exec()
            } else {
                Shell.cmd(
                    "swapoff $ZRAM_DEV 2>/dev/null",
                    "echo 1 > $ZRAM_DEV/reset 2>/dev/null",
                    "echo $compAlgorithm > $ZRAM_DEV/comp_algorithm 2>/dev/null",
                    "echo $bytes > $ZRAM_DEV/disksize 2>/dev/null",
                    "mkswap $ZRAM_DEV 2>/dev/null",
                    "swapon $ZRAM_DEV 2>/dev/null"
                ).exec()
            }
            refreshStats()
            val liveMb = currentDiskSizeMb
            if (liveMb == mb) {
                PropertyUtils.set(PROP_SIZE_MB, mb.toString())
            }

        }
    }

    fun applySwappiness(value: Int) {
        val clamped = value.coerceIn(0, 200)
        swappiness = clamped
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("echo $clamped > /proc/sys/vm/swappiness 2>/dev/null").exec()
            PropertyUtils.set(PROP_SWAPPINESS, clamped.toString())
        }
    }

    /**
     * Changing the compression codec hits the same kernel constraint as
     * resizing (the device must be swapped off + reset first), so this mirrors
     * applySizeMb()'s sequence rather than a separate, easy-to-drift copy.
     */
    fun setCompAlgorithm(algo: String) {
        if (algo == compAlgorithm) return
        val previous = compAlgorithm
        compAlgorithm = algo
        viewModelScope.launch(Dispatchers.IO) {
            val bytes = currentDiskSizeMb.toLong() * 1024 * 1024
            if (currentDiskSizeMb > 0) {
                Shell.cmd(
                    "swapoff $ZRAM_DEV 2>/dev/null",
                    "echo 1 > $ZRAM_DEV/reset 2>/dev/null",
                    "echo $algo > $ZRAM_DEV/comp_algorithm 2>/dev/null",
                    "echo $bytes > $ZRAM_DEV/disksize 2>/dev/null",
                    "mkswap $ZRAM_DEV 2>/dev/null",
                    "swapon $ZRAM_DEV 2>/dev/null"
                ).exec()
            } else {
                Shell.cmd("echo $algo > $ZRAM_DEV/comp_algorithm 2>/dev/null").exec()
            }
            val live = ZramHardwareBackend.readState()
            if (live.exists && live.algorithm == algo) {
                compAlgorithm = algo
                PropertyUtils.set(PROP_COMP_ALGO, algo)
            } else {
                compAlgorithm = live.algorithm ?: previous
            }
            refreshStats()
        }
    }

    /** Writes to the kernel's real memory-compaction trigger — same node "Free up RAM" style tools use. */
    fun compactZram() {
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("echo 1 > /proc/sys/vm/compact_memory 2>/dev/null").exec()
            refreshStats()
        }
    }

    /** Restores the stock 8GB preset — reuses applyPreset() instead of a second size-setting path. */
    fun resetToDefault() {
        applyPreset(SIZE_PRESETS.first { it.id == "stock" })
    }
}
