/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * Device Info — **رأس القسم**: بناء `DeviceInfoHero` من اللقطة (المقياس وبلاطات
 * الإحصاء وحالات الثقة) — انتقلت هذه الكتلة حرفيًّا من `DeviceInfoFacts` في تكملة ٢٢٥
 * لتخطّي سقف الأسطر، فلا سطرٌ هنا مُعاد كتابته (ADR-18)، والقياس كما كان على JVM
 * (`DeviceInfoFactsTest`).
 */
package nd.max.ui.subscreens

import androidx.annotation.StringRes
import nd.max.R
import nd.max.core.platform.SensorInventory

// ── رأس القسم: المقياس والبلاطات ─────────────────────────────────────────

/**
 * رأس كل قسم — **والقاعدة المطبَّقة في كل بلاطة**: قيمةٌ تُطبع إن كانت قراءة، و`—`
 * مع حالة ثقة إن كانت غيابًا (كما يفعل `MaxMetric` نفسه). ولا جزءٌ يُبنى من رقمٍ لا وجود له.
 */
internal fun heroOf(section: DeviceInfoSection, s: DeviceInfoSnapshot): DeviceInfoHero =
    when (section) {
        DeviceInfoSection.Overview -> overviewHero(s)
        DeviceInfoSection.Cpu -> hero(
            gauge = cpuGauge(s),
            tiles = listOf(
                tile(R.string.devinfo_cpu_cores, s.cpuCores?.toString(), trust = liveCount(s.cpuCores)),
                tile(R.string.devinfo_cpu_cores_online, s.cpuCoresOnline?.toString(), trust = liveCount(s.cpuCoresOnline)),
                tile(R.string.devinfo_cpu_load, s.cpuLoadPercent?.toString(), "%", trust = liveCount(s.cpuLoadPercent)),
                tile(R.string.devinfo_arch, s.cpuArch, trust = textTrust(s.cpuArch)),
            ),
        )
        DeviceInfoSection.Gpu -> hero(
            gauge = gpuGauge(s),
            tiles = listOf(
                tile(R.string.devinfo_gpu_load, s.gpuLoadPercent?.toString(), "%", trust = liveCount(s.gpuLoadPercent)),
                tile(R.string.devinfo_gpu_family, s.chipset?.let(::gpuFamilyOf), trust = textTrust(s.chipset?.let(::gpuFamilyOf))),
                tile(R.string.devinfo_vulkan_version, s.vulkanVersion, trust = textTrust(s.vulkanVersion)),
                tile(R.string.devinfo_gpu_driver, s.gpuDriver, trust = textTrust(s.gpuDriver)),
            ),
        )
        DeviceInfoSection.Memory -> hero(
            gauge = memoryGauge(s),
            tiles = listOf(
                tile(R.string.devinfo_ram_total, s.ramTotalMb?.let(::formatMb), trust = liveCount(s.ramTotalMb)),
                tile(R.string.devinfo_ram_used, s.ramUsedMb?.let(::formatMb), trust = liveCount(s.ramUsedMb)),
                tile(
                    R.string.devinfo_ram_available,
                    availableMb(s.ramUsedMb, s.ramTotalMb)?.let(::formatMb),
                    trust = memoryTrust(s),
                ),
                tile(R.string.devinfo_java_heap, s.javaHeapMaxMb?.toString(), "MB", trust = liveCount(s.javaHeapMaxMb)),
            ),
        )
        DeviceInfoSection.Storage -> hero(
            gauge = storageGauge(s),
            tiles = listOf(
                tile(R.string.devinfo_storage_total, s.storageTotalGb?.let { formatDouble(it.toDouble()) }, "GB", trust = storageTrust(s.storageTotalGb)),
                tile(R.string.devinfo_storage_used, s.storageUsedGb?.let { formatDouble(it.toDouble()) }, "GB", trust = storageTrust(s.storageUsedGb)),
                tile(
                    R.string.devinfo_storage_free,
                    freeGb(s.storageUsedGb, s.storageTotalGb)?.let { formatDouble(it.toDouble()) },
                    "GB",
                    trust = storageTrust(freeGb(s.storageUsedGb, s.storageTotalGb)),
                ),
            ),
        )
        DeviceInfoSection.Battery -> hero(
            gauge = batteryGauge(s),
            tiles = listOf(
                tile(R.string.devinfo_battery_temp, s.batteryTempC?.takeIf { it > 0f }?.let { formatDouble(it.toDouble()) }, "°C", trust = countableTrust(s.batteryTempC)),
                tile(R.string.devinfo_battery_voltage, s.batteryVoltageV?.takeIf { it > 0f }?.let { formatDouble(it.toDouble()) }, "V", trust = countableTrust(s.batteryVoltageV)),
                tile(R.string.devinfo_power, s.powerWatt?.takeIf { it > 0f }?.let { formatDouble(it.toDouble()) }, "W", trust = countableTrust(s.powerWatt)),
                tile(R.string.devinfo_battery_current, s.batteryCurrentMa?.toString(), "mA", trust = currentTrust(s.batteryCurrentMa)),
            ),
        )
        DeviceInfoSection.Display -> hero(
            tiles = listOf(
                tile(R.string.devinfo_resolution, resolutionText(s.displayWidth, s.displayHeight), trust = textTrust(resolutionText(s.displayWidth, s.displayHeight))),
                tile(R.string.devinfo_density, s.displayDensityDpi?.toString(), "dpi", trust = liveCount(s.displayDensityDpi)),
                tile(R.string.devinfo_refresh, s.displayRefreshHz?.takeIf { it > 0 }?.toString(), "Hz", trust = refreshTrust(s.displayRefreshHz)),
                tile(R.string.devinfo_screen_size, diagonalInchesLabel(s.displayWidth, s.displayHeight, s.displayDensityDpi), "in", trust = textTrust(diagonalInchesLabel(s.displayWidth, s.displayHeight, s.displayDensityDpi))),
            ),
        )
        DeviceInfoSection.Thermal -> hero(
            tiles = listOf(
                tile(R.string.devinfo_temp_cpu, s.cpuTempC?.takeIf { it > 0 }?.toString(), "°C", trust = temperatureTrust(s.cpuTempC)),
                tile(R.string.devinfo_temp_gpu, s.gpuTempC?.takeIf { it > 0 }?.toString(), "°C", trust = temperatureTrust(s.gpuTempC)),
                tile(R.string.devinfo_temp_skin, s.skinTempC?.takeIf { it > 0 }?.toString(), "°C", trust = temperatureTrust(s.skinTempC)),
                tile(R.string.devinfo_thermal_zones, s.thermalZoneCount?.toString(), trust = liveCount(s.thermalZoneCount)),
            ),
        )
        DeviceInfoSection.Sensors -> hero(
            tiles = listOf(
                tile(R.string.devinfo_sensor_count, s.sensorCount?.toString(), trust = liveCount(s.sensorCount)),
                tile(R.string.devinfo_sensor_kinds, s.sensorKindCount?.toString(), trust = liveCount(s.sensorKindCount)),
                tile(R.string.devinfo_sensor_wakeup, s.sensorWakeUpCount?.toString(), trust = liveCount(s.sensorWakeUpCount)),
                tile(
                    R.string.max_sensor_light,
                    s.sensorLight?.lux?.let { formatDouble(it.toDouble()) },
                    LUX_UNIT,
                    trust = lightTrust(s.sensorLight),
                ),
            ),
        )
        DeviceInfoSection.System -> hero(
            tiles = listOf(
                tile(R.string.android_version, s.android, trust = textTrust(s.android)),
                tile(R.string.devinfo_api_level, s.sdk?.toString(), trust = sdkTrust(s.sdk)),
                tile(R.string.devinfo_security_patch, s.securityPatch, trust = textTrust(s.securityPatch)),
                tile(R.string.devinfo_uptime, formatUptime(s.uptimeSeconds ?: 0L), trust = textTrust(formatUptime(s.uptimeSeconds ?: 0L))),
            ),
        )
        DeviceInfoSection.Network -> hero(
            tiles = listOf(
                tile(R.string.devinfo_download, s.downloadKbps?.let(::formatKbps), trust = netTrust(s.downloadKbps)),
                tile(R.string.devinfo_upload, s.uploadKbps?.let(::formatKbps), trust = netTrust(s.uploadKbps)),
                tile(R.string.devinfo_adb, null, trust = boolTrust(s.adbEnabled), valueRes = onOffRes(s.adbEnabled)),
                tile(
                    R.string.devinfo_dev_options,
                    null,
                    trust = boolTrust(s.developerOptions),
                    valueRes = onOffRes(s.developerOptions),
                ),
            ),
        )
        DeviceInfoSection.Camera -> hero(
            tiles = listOf(
                tile(R.string.devinfo_camera_count, s.cameras.size.takeIf { it > 0 }?.toString(), trust = cameraCountTrust(s.cameras.size)),
                tile(
                    R.string.devinfo_camera_megapixels,
                    s.cameras.mapNotNull { camera ->
                        formatMegaPixels(camera.pixelArray?.first, camera.pixelArray?.second)?.removeSuffix(" MP")
                    }.maxByOrNull { it.toDoubleOrNull() ?: 0.0 },
                    "MP",
                    trust = if (s.cameras.isEmpty()) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot,
                ),
                tile(
                    R.string.devinfo_camera_zoom,
                    s.cameras.mapNotNull { it.maxDigitalZoom }?.maxOrNull()?.let { formatDouble(it.toDouble()) },
                    "×",
                    trust = zoomTrust(s.cameras),
                ),
                tile(
                    R.string.devinfo_camera_level,
                    s.cameras.firstOrNull { it.facing == CameraFacing.Back }?.hardwareLevel,
                    trust = textTrust(s.cameras.firstOrNull { it.facing == CameraFacing.Back }?.hardwareLevel),
                ),
            ),
        )

        // والصوت (`AS-01`): أربع قراءات — والمعدّل وحده بوحدة، والباقي عدّ. **والصفر قراءة**: قائمة
        // مؤثرات فارغة تعني «لا مؤثرات مُعلَنة» وهي بلاطة صحيحة؛ وإنما «غير مقروء» غياب اللقطة.
        DeviceInfoSection.Audio -> hero(
            tiles = listOf(
                tile(
                    R.string.max_audio_sample_rate_caption,
                    s.audio?.output?.sampleRateHz?.toString(),
                    "Hz",
                    trust = audioTrust(s.audio?.output?.sampleRateHz),
                ),
                tile(
                    R.string.max_audio_frames_caption,
                    s.audio?.output?.framesPerBuffer?.toString(),
                    trust = audioTrust(s.audio?.output?.framesPerBuffer),
                ),
                tile(
                    R.string.max_audio_devices_title,
                    s.audio?.devices?.size?.toString(),
                    trust = audioCountTrust(s.audio?.devices != null),
                ),
                tile(
                    R.string.max_audio_effects_title,
                    s.audio?.effects?.size?.toString(),
                    trust = audioCountTrust(s.audio?.effects != null),
                ),
            ),
        )
    }

/** رقمٌ لم يُقرأ ⇒ «غير مقروء»؛ ورقمٌ قُرئ ⇒ «حيّ» — والصفر قراءة لا غياب. */
private fun audioTrust(value: Int?): DeviceInfoTrust =
    if (value == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live

/** العدّ من قائمة مُعلَنة: قراءةٌ أن أُعلنت، وغيابُ قراءة أن غابت اللقطة (فلا يُقال «صفر» لمجهول). */
private fun audioCountTrust(announced: Boolean): DeviceInfoTrust =
    if (announced) DeviceInfoTrust.Live else DeviceInfoTrust.Unreadable

/** النظرة العامة وحدها تحمل هويّة: الاسم هو العنوان، والسطر تحته `الطراز · الشريحة`. */
private fun overviewHero(s: DeviceInfoSnapshot): DeviceInfoHero = DeviceInfoHero(
    title = s.deviceName,
    subtitle = listOfNotNull(s.model, s.chipset)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .takeIf { it.isNotEmpty() },
    gauges = listOfNotNull(batteryGauge(s)),
    tiles = listOf(
        tile(R.string.android_version, s.android, trust = textTrust(s.android)),
        tile(R.string.ram, s.ramTotalMb?.let(::formatMb), trust = liveCount(s.ramTotalMb)),
        tile(R.string.devinfo_storage_total, s.storageTotalGb?.let { formatDouble(it.toDouble()) }, "GB", trust = storageTrust(s.storageTotalGb)),
        tile(R.string.devinfo_cpu_cores, s.cpuCores?.toString(), trust = liveCount(s.cpuCores)),
        tile(R.string.devinfo_resolution, resolutionText(s.displayWidth, s.displayHeight), trust = textTrust(resolutionText(s.displayWidth, s.displayHeight))),
        tile(R.string.devinfo_refresh, s.displayRefreshHz?.takeIf { it > 0 }?.toString(), "Hz", trust = refreshTrust(s.displayRefreshHz)),
    ),
)

private fun hero(gauge: DeviceInfoGauge? = null, tiles: List<DeviceInfoTile>): DeviceInfoHero =
    DeviceInfoHero(gauges = listOfNotNull(gauge), tiles = tiles)

/**
 * بلاطة — **و`value = null` يمرّ** فتحمل الثقة وحدها (زرّ بلا قيمة يُقرأ `—` لا صفرًا)،
 * وما تُترجَم قيمته يمرّ من [valueRes] فلا يُطبع لفظٌ لاتينيّ في جملة عربية.
 */
private fun tile(
    @StringRes captionRes: Int,
    value: String?,
    unit: String? = null,
    trust: DeviceInfoTrust,
    @StringRes valueRes: Int? = null,
): DeviceInfoTile = DeviceInfoTile(
    captionRes = captionRes,
    value = value?.takeIf { it.isNotBlank() },
    unit = unit,
    trust = trust,
    valueRes = valueRes,
)

// ── مقاييس الرأس: الكسر يُبنى من قراءتين لا من رقم واحد ───────────────────

/** البطارية: النسبة من مئة — وصفرها **قراءة** (بطارية فارغة) فالمقياس يُرسم حتى الصفر. */
private fun batteryGauge(s: DeviceInfoSnapshot): DeviceInfoGauge? {
    val percent = s.batteryPercent?.takeIf { it in 0..100 } ?: return null
    return DeviceInfoGauge(
        titleRes = R.string.devinfo_battery_level,
        valueText = percent.toString(),
        unitText = "%",
        fraction = percent / 100f,
        live = true,
    )
}

/** المعالج: التردّد الحيّ من سقفه — ولا مقياس بلا سقفٍ مُعلن (كسرٌ بلا مقام تخمين). */
private fun cpuGauge(s: DeviceInfoSnapshot): DeviceInfoGauge? {
    val freq = s.cpuFreqMhz?.takeIf { it > 0 } ?: return null
    val ceiling = s.cpuCeilingMhz?.takeIf { it > 0 } ?: return null
    return DeviceInfoGauge(
        titleRes = R.string.devinfo_cpu_freq,
        valueText = formatDouble(freq / 1000.0, 2),
        unitText = "GHz",
        fraction = (freq.toFloat() / ceiling).coerceIn(0f, 1f),
        subtitle = "${formatDouble(freq / 1000.0, 2)} / ${formatDouble(ceiling / 1000.0, 2)} GHz",
        live = true,
    )
}

/** الرسوم: التردّد من أقصى ما تدعمه OPP أولًا، وإلا من سقف العقدة. */
private fun gpuGauge(s: DeviceInfoSnapshot): DeviceInfoGauge? {
    val freq = s.gpuFreqMhz?.takeIf { it > 0 } ?: return null
    val ceiling = s.gpuMaxSupportedMhz?.takeIf { it > 0 }
        ?: s.gpuCeilingMhz?.takeIf { it > 0 }
        ?: return null
    return DeviceInfoGauge(
        titleRes = R.string.devinfo_gpu_freq,
        valueText = formatDouble(freq / 1000.0, 2),
        unitText = "GHz",
        fraction = (freq.toFloat() / ceiling).coerceIn(0f, 1f),
        subtitle = "${formatDouble(freq / 1000.0, 2)} / ${formatDouble(ceiling / 1000.0, 2)} GHz",
        live = true,
    )
}

/** الذاكرة: المستخدم من الكل — والطرفان معاً أو لا مقياس. */
private fun memoryGauge(s: DeviceInfoSnapshot): DeviceInfoGauge? {
    val used = s.ramUsedMb?.takeIf { it >= 0 } ?: return null
    val total = s.ramTotalMb?.takeIf { it > 0 } ?: return null
    return DeviceInfoGauge(
        titleRes = R.string.devinfo_ram_used,
        valueText = formatDouble(used / 1024.0),
        unitText = "GB",
        fraction = (used.toFloat() / total).coerceIn(0f, 1f),
        subtitle = "${formatMb(used)} / ${formatMb(total)}",
        live = true,
    )
}

/** التخزين: المستخدم من الكل — نفس قاعدة الذاكرة بلا صيغة ثانية. */
private fun storageGauge(s: DeviceInfoSnapshot): DeviceInfoGauge? {
    val used = s.storageUsedGb?.takeIf { it >= 0f } ?: return null
    val total = s.storageTotalGb?.takeIf { it > 0f } ?: return null
    return DeviceInfoGauge(
        titleRes = R.string.devinfo_storage_used,
        valueText = formatDouble(used.toDouble()),
        unitText = "GB",
        fraction = (used / total).coerceIn(0f, 1f),
        subtitle = "${formatDouble(used.toDouble())} / ${formatDouble(total.toDouble())} GB",
        live = false,
    )
}

/** نصّ الدقة للبلاطة (`2712 × 1220`) — وصفرٌ في أحد الطرفين ليس دقة. */
private fun resolutionText(width: Int?, height: Int?): String? {
    val w = width?.takeIf { it > 0 } ?: return null
    val h = height?.takeIf { it > 0 } ?: return null
    return "$w × $h"
}

// ── حالات الثقة للبلاطات — كل حالة لها حكمها لا قاعدة واحدة تُعمَّل الجميع ──

private fun liveCount(value: Int?): DeviceInfoTrust =
    if (value == null || value <= 0) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live

private fun textTrust(value: String?): DeviceInfoTrust =
    if (value.isNullOrBlank()) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot

private fun sdkTrust(sdk: Int?): DeviceInfoTrust =
    if (sdk == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot

private fun refreshTrust(hz: Int?): DeviceInfoTrust =
    if (hz == null || hz <= 0) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot

private fun storageTrust(gb: Float?): DeviceInfoTrust =
    if (gb == null || gb <= 0f) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot

private fun netTrust(kbps: Long?): DeviceInfoTrust =
    if (kbps == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live

/** الحرارة: وصفرها ليس قراءة — نفس قاعدة `temperature` في الحقول. */
private fun temperatureTrust(celsius: Int?): DeviceInfoTrust =
    if (celsius == null || celsius <= 0) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live

/** تيار البطارية **قد يكون سالبًا** (تفريغ) فهو قراءة عند أي إشارة — لا عند الموجب وحده. */
private fun currentTrust(ma: Int?): DeviceInfoTrust =
    if (ma == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live

private fun countableTrust(value: Float?): DeviceInfoTrust =
    if (value == null || value <= 0f) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live

private fun memoryTrust(s: DeviceInfoSnapshot): DeviceInfoTrust =
    if (availableMb(s.ramUsedMb, s.ramTotalMb) == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Live

private fun lightTrust(reading: SensorInventory.LightReading?): DeviceInfoTrust = when (reading?.state) {
    null -> DeviceInfoTrust.Unreadable
    SensorInventory.ReadingState.REPORTED -> DeviceInfoTrust.Live
    SensorInventory.ReadingState.UNREADABLE -> DeviceInfoTrust.Unreadable
    SensorInventory.ReadingState.ABSENT -> DeviceInfoTrust.Unsupported
}

private fun cameraCountTrust(size: Int): DeviceInfoTrust =
    if (size <= 0) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot

private fun zoomTrust(cameras: List<DeviceCameraInfo>): DeviceInfoTrust =
    if (cameras.none { it.maxDigitalZoom != null }) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot

/** ثلاث حالات `Boolean?`: مُفعّل، مُعطّل، لم يُقرأ — والصفر والنفي لا يُجمعان في واحد. */
private fun boolTrust(value: Boolean?): DeviceInfoTrust =
    if (value == null) DeviceInfoTrust.Unreadable else DeviceInfoTrust.Snapshot

@StringRes
private fun onOffRes(value: Boolean?): Int? = when (value) {
    true -> R.string.devinfo_state_on
    false -> R.string.devinfo_state_off
    null -> null
}
