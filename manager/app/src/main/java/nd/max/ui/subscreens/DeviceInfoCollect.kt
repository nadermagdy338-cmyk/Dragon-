/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * Device Info — **طبقة الجمع**: كل قراءة الجهاز لهذه الشاشة، ولا رسمَ هنا.
 *
 * **ولماذا فُصلت عن الشاشة:** الشاشة كانت تحمل القراءة والرسم معًا، ف𝑮ّضى سقف الملفّ
 * (`code_health` = 1000 سطر) بزيادة حقول الجولة الجديدة. والنقل حرفيّ (كل دالّة انتقلت
 * بنصّها)، والزيادة هنا قراءاتٌ جديدة لا قواعدَ عرض — والقاعدة كلها في `DeviceInfoFacts`
 * والصياغات في `DeviceInfoFormat`، فلا يجمع ملفٌّ قاعدةً وقراءةً ورسمًا.
 *
 * **ولا كتابة sysfs ولا root من هنا:** كل ما يُقرأ يُقرأ فقط (‏`readNode` و`PropertyUtils`
 * و`RootFileAccess.readCommand` لقراءة سطر `GLES` وحده) — والكتابة كلها على الـarbiter
 * (ADR-11) وهذا الملف لا يعرفه.
 *
 * **وحُراس القراءة:** كل قراءةٍ مُحاطة بـ`runCatching` وتُرجع `null` عند العجز — فالغياب
 * يُقال «غير مقروء» في النموذج، ولا يُملأ بقيمةٍ افتراضية (ADR-07). وما يُقرأ من
 * `CameraCharacteristics` يُحوَّل **هنا** إلى `DeviceCameraInfo` البسيط، فيبقى النموذج
 * قابلًا للقياس على JVM بلا كاميرا.
 */
package nd.max.ui.subscreens

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaDrm
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.system.Os
import android.view.WindowManager
import java.util.UUID
import nd.max.R
import nd.max.core.audio.AudioInventory
import nd.max.core.audio.AudioInventorySnapshot
import nd.max.core.hardware.CpuHardwareBackend
import nd.max.core.hardware.RootFileAccess
import nd.max.core.platform.CoolingDeviceInfo
import nd.max.core.platform.PropertyUtils
import nd.max.core.platform.SensorInventory
import nd.max.core.platform.ThermalTripPoint
import nd.max.core.platform.ThermalZoneInfo
import nd.max.core.platform.getChipsetIdentity
import nd.max.core.platform.getChipsetVendor
import nd.max.ui.util.BatteryHealthUtil
import nd.max.ui.util.CpuTopologyUtil
import nd.max.ui.util.getAppVersion
import nd.max.ui.util.getDeclaredRefreshRates
import nd.max.ui.util.getRealDeviceName
import nd.max.ui.util.getSELinuxStatus
import nd.max.ui.viewmodel.DashboardState

/** علامة اتجاه لحقل لاتيني داخل جملة عربية — العُرف نفسه في `CpuCoreControlScreen`. */
// و`private` لا `internal`: الحزمة كلها تُترجم معًا، ولكلٍّ من `ChargingScreen` و
// `CpuCoreControlScreen` علامة LTR خاصة به — والجمع بين خاصٍّ وعامٍّ في اسمٍ واحد
// «تعارض إعلانات» أمسكه CI في تكملة ٢٢٥، فالخاص هو الحال السائد في هذا الحزمة.
private const val LTR_MARK = "\u200E"

/** ما يُقرأ من DRM مرة واحدة: عقدة `MediaDrm` وحدها، وكلها اختيارية. */
internal class DrmRead(
    val securityLevel: String?,
    val vendor: String?,
    val version: String?,
    val algorithms: String?,
    val hdcp: String?,
    /** «لا مخرج رقميّ» (‏`-1`) — حالة معلنة لا مستوى ولا غياب. */
    val noDigitalOutput: Boolean,
)

/** ما يُقرأ مرّة واحدة في عمر الشاشة: هويّة الجهاز والبناء وكل ما لا يتغيّر. */
internal class DeviceInfoStatics(
    val deviceName: String,
    val manufacturer: String,
    val chipset: String,
    val chipsetVendor: String?,
    val android: String,
    val sdk: Int,
    val kernel: String,
    val machine: String,
    val abis: String,
    val fingerprint: String,
    val securityPatch: String,
    val selinux: String,
    val appVersion: String,
    val cpuCache: String?,
    val cpuFeatures: String?,
    val gpuGles: String?,
    val batteryHealthPercent: Int?,
    val batteryCycleCount: Int?,
    val batteryTechnology: String?,
    val supportedHz: List<Int>,
    // ══ حقول الجولة الجديدة ═══════════════════════════════════════════════
    val brand: String,
    val model: String,
    val deviceCodename: String,
    val board: String,
    val hardware: String,
    val bootloader: String,
    val baseband: String?,
    val buildId: String,
    val buildIncremental: String,
    val buildType: String,
    val buildMillis: Long,
    val androidCodename: String,
    val uptimeSeconds: Long?,
    val bootMillis: Long?,
    val timeZoneId: String,
    val localeName: String?,
    val vmLabel: String?,
    val webViewVersion: String?,
    val playServicesVersion: String?,
    val treble: Boolean?,
    val seamlessUpdates: Boolean?,
    val dynamicPartitions: Boolean?,
    val drm: DrmRead?,
    val cpuImplementer: String?,
    val cpuPart: String?,
    val cpuRevision: String?,
    val gpuRenderer: String?,
    val gpuRendererVendor: String?,
    val gpuDriver: String?,
    val vulkanVersion: String?,
    val vulkanLevel: String?,
    val memCachedMb: Int?,
    val memBuffersMb: Int?,
    val javaHeapMaxMb: Int?,
    val storagePartitions: List<DeviceInfoPartitionRow>,
    val batteryHealthCode: Int?,
    val batteryPluggedCode: Int?,
    val batteryDesignCapacityMah: Int?,
    val batteryChargeCounterMah: Int?,
    val cutoutTopPx: Int?,
    val hdrTypes: List<Int>,
    val wideGamut: Boolean?,
    val brightnessPercent: Int?,
    val autoBrightness: Boolean?,
    val timeoutMinutes: Float?,
    val fontScale: Float?,
    val orientationCode: Int?,
    val adbEnabled: Boolean?,
    val developerOptions: Boolean?,
    val capabilities: List<DeviceInfoChipRow>,
    val cameras: List<DeviceCameraInfo>,
    /**
     * وجرد الصوت (`AS-01`): يُقرأ **مرّة واحدة** مع بقية الثوابت، و`null` غياب قراءة لا «لا أجهزة».
     *
     * **ولماذا هنا لا في `deviceInfoSnapshotOf`:** تلك الدالّة **نقيّة بلا `Context`** (تُبنى من
     * `statics` واللقطات)، فنداؤها بقارئ يلمس المنصّة كان يسأل عن `context` غير موجود — وهو ما
     * أسقط `compileReleaseKotlin` فعلًا في أول تشغيل لهذا التغيير. والقراءة في موضعها الواحد.
     */
    val audio: AudioInventorySnapshot?,
)

internal fun deviceInfoStatics(context: Context): DeviceInfoStatics {
    val identity = getChipsetIdentity(context)
    // ما يُقرأ من النواة والملفات مرّة واحدة: `uname` ونصّ `/proc/cpuinfo` وصحة البطارية.
    val uname = Os.uname()
    val cpuInfo = readNode("/proc/cpuinfo").orEmpty()
    val battery = runCatching { BatteryHealthUtil.read() }.getOrNull()
    val cpuText = parseCpuInfo(cpuInfo)
    val memInfo = parseMemInfo(readNode("/proc/meminfo"))
    val batteryExtras = readBatteryExtras(context)
    // وسطر `GLES` يُقرأ **مرّة واحدة** لا ثلاث: العارض والبائع والمشغّل ثلاثة أجزاء لسطرٍ واحد.
    val gles = parseGlesLine(readGlesLine())
    return DeviceInfoStatics(
        // واسم الجهاز من `getRealDeviceName`: هي التي تقرأ جدول الأسماء المخزون، فالسطر
        // الذي يراه المستخدم هنا هو السطر نفسه الذي يراه في الرئيسية (لا اسمان لجهاز واحد).
        deviceName = getRealDeviceName(context),
        manufacturer = Build.MANUFACTURER.orEmpty(),
        chipset = identity.display,
        // وبائع الشريحة من صفّ الكتالوج لا من نصّ العرض (وهو ما ينهار على رمز مشترك).
        chipsetVendor = identity.vendors.firstOrNull() ?: getChipsetVendor(context).takeIf { it != "unknown" },
        android = Build.VERSION.RELEASE.orEmpty(),
        sdk = Build.VERSION.SDK_INT,
        kernel = uname.release,
        machine = uname.machine,
        abis = Build.SUPPORTED_ABIS.joinToString(", "),
        fingerprint = Build.FINGERPRINT.orEmpty(),
        // وبصمة الأمان في `Build.VERSION` لا في `Build` (خطأ مُصرَّف أمسكه `compileReleaseKotlin`).
        securityPatch = Build.VERSION.SECURITY_PATCH.orEmpty(),
        selinux = getSELinuxStatus(context),
        appVersion = getAppVersion(context),
        // و`/proc/cpuinfo` يُقرأ **مرّة واحدة** ومنه نصّان لا رقم: الخصائص، ومخبأ x86 حين
        // لا يُعلن نظام الملفات مستويات (`cpu0/cache`) — ونصّ النواة يُحلّل بدالّة خالصة
        // مقيسة على JVM (`parseCpuInfo`)، فلا تُترك صيغة الملفّ للتجربة على جهاز واحد.
        cpuCache = parseAndFormatCache(cpuInfo),
        cpuFeatures = cpuText.features,
        gpuGles = declaredGles(context),
        batteryHealthPercent = battery?.stateOfHealthPercent,
        batteryCycleCount = battery?.cycleCount,
        batteryTechnology = readTechnology(),
        supportedHz = declaredRefreshRates(context),
        // ══ الهوية والبناء ════════════════════════════════════════════════
        brand = Build.BRAND.orEmpty(),
        model = Build.MODEL.orEmpty(),
        deviceCodename = Build.DEVICE.orEmpty(),
        board = Build.BOARD.orEmpty(),
        hardware = Build.HARDWARE.orEmpty(),
        bootloader = Build.BOOTLOADER.orEmpty(),
        baseband = runCatching { Build.getRadioVersion() }.getOrNull()?.takeIf { it.isNotBlank() },
        buildId = Build.ID.orEmpty(),
        buildIncremental = Build.VERSION.INCREMENTAL.orEmpty(),
        buildType = Build.TYPE.orEmpty(),
        buildMillis = Build.TIME,
        androidCodename = Build.VERSION.CODENAME.orEmpty(),
        uptimeSeconds = readUptimeSeconds(),
        // لحظة الإقلاع = الساعة الآن − مهلة التشغيل — حسابٌ من قراءتين (يُعلن في النموذج كذلك).
        bootMillis = readUptimeSeconds()?.let { System.currentTimeMillis() - it * 1_000L },
        timeZoneId = java.util.TimeZone.getDefault().id,
        localeName = readLocale(context),
        vmLabel = readVmLabel(),
        webViewVersion = readPackageVersion(context, "com.google.android.webview")
            ?: readPackageVersion(context, "com.android.webview"),
        playServicesVersion = readPackageVersion(context, "com.google.android.gms"),
        treble = propBool("ro.treble.enabled"),
        seamlessUpdates = propBool("ro.virtual_ab.enabled") ?: propBool("ro.build.ab_update"),
        dynamicPartitions = propBool("ro.boot.dynamic_partitions"),
        drm = readDrm(),
        cpuImplementer = cpuText.implementer,
        cpuPart = cpuText.part,
        cpuRevision = cpuText.revision,
        // والاسم الحقيقيّ للرسوم من `SurfaceFlinger` (سطر `GLES`) — قراءةٌ واحدة عبر الجذر،
        // والتحليل في `DeviceInfoFormat.parseGlesLine` (صافٍ ومقيس).
        gpuRenderer = gles?.renderer,
        gpuRendererVendor = gles?.vendor,
        gpuDriver = gles?.driver,
        // وVulkan **مُعلَنان من المنصّة** لا مُستنتجان من وجود مكتبة.
        vulkanVersion = readVulkanVersion(context),
        vulkanLevel = readVulkanLevel(context),
        memCachedMb = memInfo?.cachedKb?.let { (it / 1024).toInt() },
        memBuffersMb = memInfo?.buffersKb?.let { (it / 1024).toInt() },
        javaHeapMaxMb = readJavaHeapMaxMb(context),
        storagePartitions = readStoragePartitions(),
        batteryHealthCode = batteryExtras?.first,
        batteryPluggedCode = batteryExtras?.second,
        batteryDesignCapacityMah = milliAmpHoursFromMicro(
            readNode("/sys/class/power_supply/battery/charge_full_design")?.trim()?.toLongOrNull(),
        ),
        batteryChargeCounterMah = milliAmpHoursFromMicro(readChargeCounter(context)),
        cutoutTopPx = readCutoutTopPx(context),
        hdrTypes = readHdrTypes(context),
        wideGamut = readWideColorGamut(context),
        brightnessPercent = readBrightnessPercent(context),
        autoBrightness = readAutoBrightness(context),
        timeoutMinutes = readTimeoutMinutes(context),
        fontScale = runCatching { context.resources.configuration.fontScale }.getOrNull()?.takeIf { it > 0f },
        orientationCode = runCatching { context.resources.configuration.orientation }.getOrNull(),
        adbEnabled = readGlobalBool(context, Settings.Global.ADB_ENABLED),
        developerOptions = readGlobalBool(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
        capabilities = readCapabilities(context),
        cameras = collectCameras(context),
        // والصوت (`AS-01`): **من القارئ القائم نفسه** (`AudioInventory`) لا من قراءة ثانية — ولقطةٌ
        // لم تُقرأ تبقى `null` فيُقال «غير مقروء» ولا يُكتب صفر مكانها (ADR-07).
        audio = runCatching { AudioInventory.read(context) }.getOrNull(),
    )
}

// ── قراءات صغيرة: كل واحدة runCatching وتُرجع null عند العجز ────────────────

/**
 * قارئ عقدة sysfs **يُرجع `null` حين يعجز**، ولا يُعيد نصًّا فارغًا: القراءة التي فشلت
 * ليست صفرًا ولا نصًّا فارغًا — هي غياب يُقال عنه «غير مقروء» في الطبقة التي فوقنا.
 */
internal fun readNode(path: String): String? = runCatching {
    java.io.File(path).takeIf { it.canRead() }?.readText()
}.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

/** عمر التشغيل بالثواني من `/proc/uptime` — وصفرها ليس عمرًا. */
private fun readUptimeSeconds(): Long? =
    readNode("/proc/uptime")?.substringBefore(' ')?.trim()?.toDoubleOrNull()?.toLong()?.takeIf { it > 0 }

/** لغة الواجهة كما تُعلنها الإعدادات (`ar_EG`) — سلسلةٌ واحدة لا قائمة. */
private fun readLocale(context: Context): String? = runCatching {
    context.resources.configuration.locales[0].toLanguageTag()
}.getOrNull()?.takeIf { it.isNotBlank() }

/** الآلة الافتراضية كما تُعلنها الخصائص (`2.1.0 · Dalvik`) — نصٌّ واحد لا حقلان. */
private fun readVmLabel(): String? {
    val version = System.getProperty("java.vm.version")?.takeIf { it.isNotBlank() } ?: return null
    val name = System.getProperty("java.vm.name")?.takeIf { it.isNotBlank() }
    return listOfNotNull(version, name).joinToString(" · ")
}

private fun readPackageVersion(context: Context, packageName: String): String? = runCatching {
    context.packageManager.getPackageInfo(packageName, 0).versionName
}.getOrNull()?.takeIf { it.isNotBlank() }

/** قراءة خاصية نظام كبوليان ثلاثي: `true`/`false`/غياب — والغياب ليس `false` (ADR-07). */
private fun propBool(key: String): Boolean? = when (PropertyUtils.get(key).trim().lowercase()) {
    "1", "true", "yes" -> true
    "0", "false", "no" -> false
    else -> null
}

/** سطر `GLES` من `SurfaceFlinger` — قراءةٌ واحدة عبر الجذر، وتحليله صافٍ ومقيس. */
private fun readGlesLine(): String? = runCatching {
    RootFileAccess.readCommand("dumpsys SurfaceFlinger 2>/dev/null | grep -m1 GLES:")
}.getOrNull()

/**
 * إصدار Vulkan المُرمَّز ومستواه — **من سجلّ المنصّة لا من التخمين**:
 * `getSystemAvailableFeatures` يُعلن `android.hardware.vulkan.version` و`…level`.
 */
private fun readVulkanVersion(context: Context): String? = runCatching {
    context.packageManager.getSystemAvailableFeatures()
        .firstOrNull { it.name == "android.hardware.vulkan.version" }
        ?.version
        ?.let(::decodeVulkanVersion)
}.getOrNull()

private fun readVulkanLevel(context: Context): String? = runCatching {
    context.packageManager.getSystemAvailableFeatures()
        .firstOrNull { it.name == "android.hardware.vulkan.level" }
        ?.version
        ?.let(::vulkanLevelLabel)
}.getOrNull()

/**
 * سقف ذاكرة الآلة الافتراضية بالميغابايت — **`memoryClass` لا قراءة الـRuntime**: الحارس
 * `theUiLayerNeverExecutesProcessesDirectly` يمنع استدعاء العمليات (‏`Runtime` و
 * `ProcessBuilder`) داخل طبقة ui حرفيًّا (ومقصوده أعمّ: ما يمسّ العملية والقراءة
 * الحسّاسة يمرّ بالطبقة المالكة)، والقراءة نفسها
 * هي التي يقرأها تقرير الأطلس (‏`core/di/DataModule.kt` ← `memoryClassMb`) — فلا مصدران
 * لرقمٍ واحد يختلفان يومًا، وهي القاعدة نفسها المكتوبة على `cpuClusterInfo`.
 */
private fun readJavaHeapMaxMb(context: Context): Int? = runCatching {
    (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.memoryClass?.takeIf { it > 0 }
}.getOrNull()

/** قسم تخزين واحد — ومن لا وجود له على هذا الجهاز لا يُكتب له صفّ. */
private fun readStoragePartitions(): List<DeviceInfoPartitionRow> = listOf(
    "/data", "/system", "/vendor", "/cache",
).mapNotNull { path ->
    runCatching {
        val stats = StatFs(path)
        val total = stats.totalBytes
        val available = stats.availableBytes
        if (total <= 0L) return@runCatching null
        DeviceInfoPartitionRow(
            path = path,
            usedGb = (total - available) / 1073741824f,
            totalGb = total / 1073741824f,
        )
    }.getOrNull()
}

/**
 * صحة البطارية ومصدر التغذية من النية اللاصقة — بلا إذن ولا استعلام. والزوج **مُعطَّل
 * الطرفين** حين يغيب أحدهما: فالرقم غير المُعلن `null` لا `Int.MIN_VALUE` المُقنَّع.
 */
private fun readBatteryExtras(context: Context): Pair<Int?, Int?>? = runCatching {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, Int.MIN_VALUE)
        .takeIf { it != Int.MIN_VALUE }
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, Int.MIN_VALUE)
        .takeIf { it != Int.MIN_VALUE }
    if (health == null && plugged == null) return null
    health to plugged
}.getOrNull()

/** عدّاد الشحن بالـµAh — وصفره قراءة (بطارية مُفرَّغة) فتمرّ إلى الصياغة كما هي. */
private fun readChargeCounter(context: Context): Long? = runCatching {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
    manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        .takeIf { it != Int.MIN_VALUE }
        ?.toLong()
}.getOrNull()

/** الشقّ العلويّ بالبكسل — وصفره **قراءة** (لا شقّ) والغياب وحده عدم قراءة. */
private fun readCutoutTopPx(context: Context): Int? = runCatching {
    displayOf(context)?.cutout?.safeInsetTop
}.getOrNull()

private fun readHdrTypes(context: Context): List<Int> = runCatching {
    displayOf(context)?.hdrCapabilities?.supportedHdrTypes?.toList()
}.getOrNull().orEmpty()

private fun readWideColorGamut(context: Context): Boolean? = runCatching {
    displayOf(context)?.isWideColorGamut
}.getOrNull()

/**
 * الشاشة — عبر `WindowManager` لا `Context.getDisplay()` لأن后者 API 30 و`minSdk` = 29،
 * والفرق هنا يظهر على جهازٍ واحد بالضبط: الذي يعمل أندرويد 10.
 */
private fun displayOf(context: Context) = runCatching {
    (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
}.getOrNull()

/** السطوع الحاليّ كنسبة من ٢٥٥ — **وصفره قراءة** (سطوع مطفأ) لا غياب. */
private fun readBrightnessPercent(context: Context): Int? = runCatching {
    val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    Math.round(raw * 100f / 255f)
}.getOrNull()

private fun readAutoBrightness(context: Context): Boolean? = runCatching {
    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
}.getOrNull()

private fun readTimeoutMinutes(context: Context): Float? = runCatching {
    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) / 60_000f
}.getOrNull()?.takeIf { it > 0f }

private fun readGlobalBool(context: Context, key: String): Boolean? = runCatching {
    Settings.Global.getInt(context.contentResolver, key) == 1
}.getOrNull()

/** عدّاد الشحن والجهة من عقدة DRM واحدة — وكل حقلٍ منها يُعلن أو يغيب. */
private fun readDrm(): DrmRead? = runCatching {
    val uuid = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
    val drm = MediaDrm(uuid)
    try {
        val hdcpLevel = runCatching { drm.maxHdcpLevel }.getOrNull()
        DrmRead(
            securityLevel = runCatching { drm.getPropertyString("securityLevel") }.getOrNull()?.takeIf { it.isNotBlank() },
            vendor = runCatching { drm.getPropertyString("vendor") }.getOrNull()?.takeIf { it.isNotBlank() },
            version = runCatching { drm.getPropertyString("version") }.getOrNull()?.takeIf { it.isNotBlank() },
            algorithms = runCatching { drm.getPropertyString("algorithms") }.getOrNull()?.takeIf { it.isNotBlank() },
            hdcp = hdcpLevel?.let(::formatHdcpLevel),
            noDigitalOutput = isHdcpNoDigitalOutput(hdcpLevel),
        )
    } finally {
        runCatching { drm.release() }
    }
}.getOrNull()

// ── القدرات المُعلَنة: `hasSystemFeature` لا يحتاج إذنًا، وهو تصريحُ المنصّة لا انطباع ──

private fun readCapabilities(context: Context): List<DeviceInfoChipRow> = runCatching {
    val pm = context.packageManager
    listOf(
        R.string.devinfo_cap_wifi to "android.hardware.wifi",
        R.string.devinfo_cap_wifi_direct to "android.hardware.wifi.direct",
        R.string.devinfo_cap_wifi_aware to "android.hardware.wifi.aware",
        R.string.devinfo_cap_wifi_rtt to "android.hardware.wifi.rtt",
        // والـPasspoint مسجَّل في سجلّ المنصّة تحت هذا الاسم؛ ومن لا يُعلنه يُقال «غير مُعلَن».
        R.string.devinfo_cap_wifi_passpoint to "android.hardware.wifi.passpoint",
        R.string.devinfo_cap_nfc to "android.hardware.nfc",
        R.string.devinfo_cap_nfc_hce to "android.hardware.nfc.hce",
        R.string.devinfo_cap_bluetooth to "android.hardware.bluetooth",
        R.string.devinfo_cap_ble to "android.hardware.bluetooth_le",
        R.string.devinfo_cap_usb_host to "android.hardware.usb.host",
        R.string.devinfo_cap_usb_accessory to "android.hardware.usb.accessory",
        R.string.devinfo_cap_uwb to "android.hardware.uwb",
        R.string.devinfo_cap_gps to "android.hardware.location.gps",
        R.string.devinfo_cap_telephony to "android.hardware.telephony",
        R.string.devinfo_cap_ir to "android.hardware.consumerir",
        R.string.devinfo_cap_fingerprint to "android.hardware.fingerprint",
        R.string.devinfo_cap_camera to "android.hardware.camera.any",
        R.string.devinfo_cap_camera_flash to "android.hardware.camera.flash",
    ).map { (labelRes, feature) ->
        DeviceInfoChipRow(
            labelRes = labelRes,
            // وثلاث حالات لا اثنتان: `hasSystemFeature` لا يرمي، فالمجهول هنا غير وارد —
            // والغياب تصريحٌ من المنصّة («غير مُعلَن») لا عطبُ قراءة.
            state = if (pm.hasSystemFeature(feature)) CapabilityState.Supported else CapabilityState.NotDeclared,
        )
    }
}.getOrDefault(emptyList())

// ── الكاميرا: من `CameraCharacteristics` إلى `DeviceCameraInfo` البسيط ────────

/**
 * جرد العدسات — **بلا إذن كاميرا**: `getCameraIdList` و`getCameraCharacteristics`
 * قراءتان مفتوحتان لكل تطبيق، والفتح وحده يحتاج الإذن.
 */
private fun collectCameras(context: Context): List<DeviceCameraInfo> = runCatching {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return emptyList()
    manager.cameraIdList.mapNotNull { id ->
        runCatching {
            val chars = manager.getCameraCharacteristics(id)
            val jpegSizes = runCatching {
                chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(android.graphics.ImageFormat.JPEG)
                    .orEmpty()
            }.getOrDefault(emptyArray())
            DeviceCameraInfo(
                facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> CameraFacing.Front
                    CameraCharacteristics.LENS_FACING_BACK -> CameraFacing.Back
                    else -> CameraFacing.External
                },
                hardwareLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    ?.let(::hardwareLevelName),
                apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList().orEmpty(),
                focalLengthsMm = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty(),
                sensorSizeMm = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    ?.let { it.width to it.height },
                pixelArray = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                    ?.let { it.width to it.height },
                sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION),
                maxDigitalZoom = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM),
                flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE),
                jpegMax = jpegSizes.maxByOrNull { it.width.toLong() * it.height }
                    ?.let { it.width to it.height },
                jpegCount = jpegSizes.size.takeIf { it > 0 },
                // و`toList()` إلزامية: `IntArray` لا تحمل `mapNotNull` (أمسكه CI في تكملة ٢٢٥) —
                // والتحويل إلى قائمة هو الطريق الوحيد الذي تُقاس عليه القائمة بالمُرشِّح نفسه.
                capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?.toList()
                    ?.mapNotNull(::capabilityName)
                    .orEmpty(),
                colorFilterArrangement = chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT),
            )
        }.getOrNull()
    }
}.getOrDefault(emptyList())

/** المستوى العتادي كما تسمّيه المنصّة — رموزٌ لاتينية كما أعلنت لا أسماء مُترجمة. */
private fun hardwareLevelName(level: Int): String = when (level) {
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
    else -> "LEVEL_$level"
}

/** قدرة مُعلنة — والرقم الذي لا يُعرف اسمه يُحذف لا يُخترع له اسم (`hdrTypeNames` كذلك). */
private fun capabilityName(value: Int): String? = when (value) {
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING -> "PRIVATE_REPROCESSING"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS -> "READ_SENSOR_SETTINGS"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "BURST_CAPTURE"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING -> "YUV_REPROCESSING"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> "DEPTH_OUTPUT"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO -> "CONSTRAINED_HIGH_SPEED_VIDEO"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING -> "MOTION_TRACKING"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "LOGICAL_MULTI_CAMERA"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME -> "MONOCHROME"
    // والاثنان الأخيران حقيقيان موثَّقان (API 30 و31)؛ واسمٌ لم يُوثَّق لا يُكتب — كانت
    // `SECURE_IMAGE_REPROCESSING` هنا باختلاقٍ منّي فأمسكها CI في تكملة ٢٢٥، وهي نصّ
    // القاعدة في تعليق هذه الدالة: «الرقم الذي لا يُعرف اسمه يُحذف لا يُخترع له اسم».
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA -> "SYSTEM_CAMERA"
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR -> "ULTRA_HIGH_RESOLUTION_SENSOR"
    else -> null
}

// ── ما انتقل حرفيًّا من الشاشة: القراءات الثابتة وعُقد التردد والحرارة ────────

/**
 * واجهة الرسم المعلَنة (`OpenGL ES 3.2`). `deviceConfigurationInfo` مُهمَلة في API ٣٣+ ولا
 * بديل مباشر لها، فتُقرأ بلفّ `runCatching` وتُقال «غير مقروء» لو رمت. **ولا يُخترع إصدار**
 * من وجود مكتبة في خرائط العملية (`libvulkan.so`): وجودها لا يعني دعمًا معلنًا، وسؤالٌ لم
 * يُقَس لا يُجاب بتخمين — ولذلك لا يُعرض صفّ Vulkan أصلًا.
 */
internal fun declaredGles(context: Context): String? = runCatching {
    (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
        ?.deviceConfigurationInfo
        ?.glEsVersion
}.getOrNull()

/** أنماط الشاشة التي تُعلنها المنصّة، **بلا قائمة افتراضية**. */
internal fun declaredRefreshRates(context: Context): List<Int> =
    runCatching { getDeclaredRefreshRates(context) }.getOrDefault(emptyList())

/**
 * المخبأ: مستويات النواة في `cpu0/cache/index0…3` (`level` و`type` و`size`) أولًا، معها احتياطٌ من
 * `/proc/cpuinfo` — والاحتياط لا يعمل إلّا إذا لم تكن مستويات (الفرع في `formatCache`).
 */
internal fun parseAndFormatCache(cpuInfo: String): String? {
    val levels = (0..3).mapNotNull { index ->
        val dir = "/sys/devices/system/cpu/cpu0/cache/index$index"
        val size = readNode("$dir/size") ?: return@mapNotNull null
        CacheLevel(level = readNode("$dir/level"), type = readNode("$dir/type"), size = size)
    }
    return formatCache(levels, fallbackSize = parseCpuInfo(cpuInfo).cacheSize)
}

/**
 * تقنية البطارية كما تُعلنها عُقدة `technology` — وتغيب على كثير من الأجهزة، وفيها
 * يُرجع `null` ولا يُكتب نصّ عامّ («Li-ion») يبدو قراءة وهو ثابت من عندنا.
 */
internal fun readTechnology(): String? = listOf(
    "/sys/class/power_supply/battery/technology",
    "/sys/class/power_supply/bms/technology",
).firstNotNullOfOrNull { readNode(it) }

/**
 * عناقيد التردّد: أسطر العرض **ومعها خرائط النواة ← العنقود والحاكم**.
 *
 * و`CpuHardwareBackend` و`CpuTopologyUtil` هما **عينهما** ما يقرأ منه تقرير الجهاز وشاشة
 * الأنوية، فالمدى المعروض هنا لا يخالف المدى المعروض هناك — ومصدران لمدى واحد يختلفان يومًا.
 * والخرائط تُبنى من **نفس الدورة** التي تُبنى فيها الأسطر، فلا تُقرأ العناقيد مرّتين ولا
 * يجوز أن يقول سطر العنقود شيئًا وسطر النواة تحته غيره.
 */
internal class CpuClusterInfo(
    val lines: List<String>,
    val clusterOfCore: Map<Int, String>,
    val governorOfCore: Map<Int, String>,
)

internal fun cpuClusterInfo(): CpuClusterInfo = runCatching {
    val policies = CpuHardwareBackend.policies()
    val clusterOfCore = mutableMapOf<Int, String>()
    val governorOfCore = mutableMapOf<Int, String>()
    val lines = CpuTopologyUtil.detectClusters().map { cluster ->
        // اسم العنقود هو **اسم سياسته** (`policy0`) كما في شاشة الأنوية: وسم الترجمة
        // (`GOLD`/`PRIME`) وسم ماشيني داخلي لا يُطبع في واجهة عربية ولا إنجليزية.
        val name = cluster.policyPath.substringAfterLast('/').ifEmpty { cluster.shortTag }
        val policy = policies.firstOrNull { it.path == cluster.policyPath }
        cluster.cores.forEach { core ->
            clusterOfCore[core] = name
            policy?.governor?.takeIf { it.isNotBlank() }?.let { governorOfCore[core] = it }
        }
        buildString {
            append(name)
            append(": ")
            append(cluster.cores.joinToString(","))
            append(" · ")
            append(policy?.hwMinKHz ?: "?")
            append('–')
            append(policy?.hwMaxKHz ?: "?")
            append(" kHz")
            policy?.governor?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
    }
    CpuClusterInfo(lines, clusterOfCore, governorOfCore)
}.getOrDefault(CpuClusterInfo(emptyList(), emptyMap(), emptyMap()))

/**
 * سطر لكل نواة من **الدورة الحيّة** نفسها التي تُغذّي بطاقات الرئيسية: المعرّف والحالة
 * والتردّد والعنقود والحاكم. والصياغة في النموذج (`formatCoreLine`) فلا تُكتب هنا قاعدة،
 * و`LTR_MARK` يُطوّق السطر لأنه بيانات لاتينية داخل واجهة قد تكون عربية (كحال شاشة الأنوية).
 */
internal fun cpuCoreLinesOf(
    dashboard: DashboardState,
    clusters: CpuClusterInfo,
    offlineLabel: String,
): List<String> = dashboard.cores
    .sortedBy { it.cpu }
    .map { core ->
        val line = formatCoreLine(
            CpuCoreReading(
                id = core.cpu,
                online = core.online,
                currentMhz = core.freqMhz,
                maxMhz = core.maxFreqMhz,
                cluster = clusters.clusterOfCore[core.cpu],
                governor = clusters.governorOfCore[core.cpu],
            ),
            offlineLabel = offlineLabel,
        )
        "$LTR_MARK$line$LTR_MARK"
    }

/**
 * ما قُرئ من `/sys/class/thermal` **مرّة واحدة عند الفتح**: المناطق وأجهزة التبريد ونقاط
 * التخفيف مرتّبة بمسار منطقتها.
 *
 * **ولماذا تُجمع في نوع واحد وتُقرأ معًا:** اللقطة تُبنى بعدها، فوجودها كاملًا يعني أن البيانات
 * حاضرة؛ وتفتيتها على ثلاث حالات كان يجعل النموذج يُبنى وبعضها وصل وبعضها لم يصل بلا سبب.
 */
internal data class ThermalRead(
    val zones: List<ThermalZoneInfo>,
    val cooling: List<CoolingDeviceInfo>,
    val trips: Map<String, List<ThermalTripPoint>>,
)

/**
 * اللقطة من الدورات القائمة — **والتحويل الوحيد المسموح هنا هو الصفر إلى غياب**
 * (`countable`)، والاستثناءان معلنان: صفر البطارية وصفر الحمل قراءتان.
 */
internal fun deviceInfoSnapshotOf(
    dashboard: DashboardState,
    statics: DeviceInfoStatics,
    cpuClusters: CpuClusterInfo,
    offlineCoreLabel: String,
    thermal: ThermalRead?,
    sensors: SensorInventory.Report?,
    sensorKindLabels: Map<SensorInventory.Kind, String>,
    sensorWakeUpLabel: String,
    thermalOffLabel: String,
): DeviceInfoSnapshot = DeviceInfoSnapshot(
    deviceName = statics.deviceName,
    manufacturer = statics.manufacturer,
    chipset = statics.chipset,
    chipsetVendor = statics.chipsetVendor,
    android = statics.android,
    sdk = statics.sdk,
    kernel = statics.kernel,
    abis = statics.abis,
    fingerprint = statics.fingerprint,
    securityPatch = statics.securityPatch,
    selinux = statics.selinux,
    appVersion = statics.appVersion,
    cpuCores = countable(dashboard.cores.size),
    // والمتصل منها **قراءة ثانية غير الكلية**: نواة مُطفأة تُقرأ في القائمة ولا تعمل.
    cpuCoresOnline = countable(dashboard.cores.count { it.online }),
    cpuCoreLines = cpuCoreLinesOf(dashboard, cpuClusters, offlineCoreLabel),
    cpuClusters = cpuClusters.lines,
    cpuLoadPercent = dashboard.cpuLoadPercent,
    // وأعلى تردّد **حيّ** بين الأنوية المتصلة، لا `cpu0` وحده: على big.LITTLE يبقى
    // الصغير في أدنى درجاته والعمل على العنقود الرئيسي، فيُقرأ الجهاز هادئًا وهو يعمل.
    cpuFreqMhz = countable(dashboard.cpuTopCoreMhz),
    cpuCeilingMhz = countable(dashboard.cpuCeilingMhz),
    // وأرضية التردّد من الدورة نفسها (`cpuMinMhz`) — فالمدى (أدنى/أعلى) من مصدر واحد.
    cpuMinMhz = dashboard.cpuMinMhz,
    cpuArch = statics.machine,
    cpuCache = statics.cpuCache,
    cpuFeatures = statics.cpuFeatures,
    gpuLoadPercent = dashboard.gpuLoadPercent,
    gpuFreqMhz = dashboard.gpuFreqMhz,
    gpuCeilingMhz = dashboard.gpuCeilingMhz,
    gpuMaxSupportedMhz = dashboard.gpuMaxSupportedMhz,
    gpuGles = statics.gpuGles,
    ramUsedMb = countable(dashboard.ramUsedMb),
    ramTotalMb = countable(dashboard.ramTotalMb),
    swapUsedMb = dashboard.swapUsedMb,
    swapTotalMb = dashboard.swapTotalMb,
    storageUsedGb = countable(dashboard.storageUsedGb),
    storageTotalGb = countable(dashboard.storageTotalGb),
    batteryPercent = dashboard.batteryPercent,
    batteryStatus = dashboard.batteryStatus,
    batteryHealthPercent = statics.batteryHealthPercent,
    batteryCycleCount = statics.batteryCycleCount,
    batteryTechnology = statics.batteryTechnology,
    batteryTempC = countable(dashboard.batteryTempC),
    batteryVoltageV = countable(dashboard.batteryVoltageV),
    batteryCurrentMa = dashboard.batteryCurrentMa,
    powerWatt = countable(dashboard.powerWatt),
    displayWidth = countable(dashboard.displayWidth),
    displayHeight = countable(dashboard.displayHeight),
    displayDensityDpi = countable(dashboard.displayDensityDpi),
    displayRefreshHz = countable(dashboard.displayRefreshHz),
    displaySupportedHz = statics.supportedHz,
    cpuTempC = countable(dashboard.cpuTempC),
    gpuTempC = countable(dashboard.gpuTempC),
    skinTempC = countable(dashboard.skinTempC),
    thermalZoneCount = countable(thermal?.zones?.size ?: 0),
    thermalZones = thermal?.zones.orEmpty().map { zone ->
        ThermalZoneRow(
            label = zone.label,
            category = zone.category,
            celsius = zone.temperatureC,
            enabled = zone.isEnabled,
            // ونقاط التخفيف تُنسب إلى منطقتها بـ`sysfsPath` — وهو مفتاح الربط الوحيد الصحيح:
            // التسمية قد تتكرّر، والرقم قد يُعاد استخدامه.
            trips = thermal?.trips?.get(zone.sysfsPath).orEmpty()
                .map { trip -> ThermalTripRow(celsius = trip.temperatureC, kind = trip.kind) },
        )
    },
    thermalOffLabel = thermalOffLabel,
    // والتصفية (`-1` = لا حال · `0` = سقف لم يُعلَن) في النموذج المقيس ([coolingRow]) لا هنا.
    coolingDevices = thermal?.cooling.orEmpty().map { device ->
        coolingRow(device.label, device.currentState, device.maxState)
    },
    sensorCount = countable(sensors?.count ?: 0),
    sensorWakeUpCount = countable(sensors?.wakeUpCount ?: 0),
    sensorKindCount = countable(sensors?.kindsReported?.size ?: 0),
    // و**الجرد كله** يُمرَّر بعد أن كان يُختصر إلى ثلاث أعداد: الأسماء والأصناف والمدى
    // والدقّة والاستهلاك والتأخير والإيقاظ كانت تُقرأ ثم تُهمل (`DI-03`).
    sensorItems = sensors?.items.orEmpty(),
    sensorKinds = sensors?.kindsReported.orEmpty(),
    sensorKindLabels = sensorKindLabels,
    sensorWakeUpLabel = sensorWakeUpLabel,
    sensorLight = sensors?.light,
    downloadKbps = dashboard.downloadSpeedKbps.takeIf { it > 0 },
    uploadKbps = dashboard.uploadSpeedKbps.takeIf { it > 0 },
    // ══ حقول الجولة الجديدة — من `DeviceInfoStatics` وحدها فلا قراءة ثانية ══════
    brand = statics.brand,
    model = statics.model,
    deviceCodename = statics.deviceCodename,
    board = statics.board,
    hardware = statics.hardware,
    bootloader = statics.bootloader,
    baseband = statics.baseband,
    buildId = statics.buildId,
    buildIncremental = statics.buildIncremental,
    buildType = statics.buildType,
    buildMillis = statics.buildMillis,
    androidCodename = statics.androidCodename,
    uptimeSeconds = statics.uptimeSeconds,
    bootMillis = statics.bootMillis,
    timeZoneId = statics.timeZoneId,
    localeName = statics.localeName,
    vmLabel = statics.vmLabel,
    webViewVersion = statics.webViewVersion,
    playServicesVersion = statics.playServicesVersion,
    treble = statics.treble,
    seamlessUpdates = statics.seamlessUpdates,
    dynamicPartitions = statics.dynamicPartitions,
    drmSecurityLevel = statics.drm?.securityLevel,
    drmVendor = statics.drm?.vendor,
    drmVersion = statics.drm?.version,
    drmAlgorithms = statics.drm?.algorithms,
    drmHdcp = statics.drm?.hdcp,
    drmNoDigitalOutput = statics.drm?.noDigitalOutput == true,
    cpuImplementer = statics.cpuImplementer,
    cpuPart = statics.cpuPart,
    cpuRevision = statics.cpuRevision,
    gpuRenderer = statics.gpuRenderer,
    gpuRendererVendor = statics.gpuRendererVendor,
    gpuDriver = statics.gpuDriver,
    vulkanVersion = statics.vulkanVersion,
    vulkanLevel = statics.vulkanLevel,
    ramCachedMb = statics.memCachedMb,
    ramBuffersMb = statics.memBuffersMb,
    javaHeapMaxMb = statics.javaHeapMaxMb,
    storagePartitions = statics.storagePartitions,
    batteryHealthCode = statics.batteryHealthCode,
    batteryPluggedCode = statics.batteryPluggedCode,
    batteryDesignCapacityMah = statics.batteryDesignCapacityMah,
    batteryChargeCounterMah = statics.batteryChargeCounterMah,
    displayCutoutTopPx = statics.cutoutTopPx,
    displayHdrTypes = statics.hdrTypes,
    displayWideGamut = statics.wideGamut,
    displayBrightnessPercent = statics.brightnessPercent,
    displayAutoBrightness = statics.autoBrightness,
    displayTimeoutMinutes = statics.timeoutMinutes,
    fontScale = statics.fontScale,
    orientationCode = statics.orientationCode,
    adbEnabled = statics.adbEnabled,
    developerOptions = statics.developerOptions,
    capabilities = statics.capabilities,
    cameras = statics.cameras,
    // والصوت (`AS-01`): من اللقطة المقروءة في الثوابت — **قراءة واحدة لا قراءتان** تأتيان برقمين.
    audio = statics.audio,
)
