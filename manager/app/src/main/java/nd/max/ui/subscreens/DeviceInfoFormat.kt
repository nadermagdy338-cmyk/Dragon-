/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * Device Info — **الصياغات والتحليلات الصافية** (JVM بلا جهاز).
 *
 * انتقل إلى هنا حرفيًّا من `DeviceInfoModel.kt` (سقف ألف سطر للملف) — وزيادة عليه
 * صياغات الجولة الجديدة: عمر التشغيل، ونسبة الأبعاد، وفئة الكثافة، وفكّ إصدار Vulkan،
 * وتحليل سطر `GLES` من SurfaceFlinger و`/proc/meminfo`، وترميز صحة البطارية ومصدر
 * الطاقة إلى موارد نصّية. **ولا شيء هنا يقرأ ملفًا ولا يعرف Android** — كل دالّة
 * قيمةً في قيمةً خارجًا، فيُقاس على JVM على نصوص حقيقية (`DeviceInfoFormatTest`).
 *
 * والوحدات الرمزية (`GHz` · `dpi` · `f/1.5`) لا تُترجَم — كما سبق وأُعلن في الملفّ
 * الأصليّ.
 */
package nd.max.ui.subscreens

import androidx.annotation.StringRes
import java.util.Locale
import nd.max.R

/**
 * المتاح = الكل − المستخدم، **ولا يُحسب إن نقص أحد الطرفين**: حسابٌ من طرفٍ واحد يُنتج
 * رقمًا يبدو قياسًا وهو طرحٌ على مجهول.
 */
fun availableMb(usedMb: Int?, totalMb: Int?): Int? {
    val used = usedMb?.takeIf { it >= 0 } ?: return null
    val total = totalMb?.takeIf { it > 0 } ?: return null
    return (total - used).coerceAtLeast(0)
}

/** نظيره للتخزين. */
fun freeGb(usedGb: Float?, totalGb: Float?): Float? {
    val used = usedGb?.takeIf { it >= 0f } ?: return null
    val total = totalGb?.takeIf { it > 0f } ?: return null
    return (total - used).coerceAtLeast(0f)
}

/**
 * أسرة الرسوم من اسم الشريحة — **وقاعدة البائع واحدة**: `Adreno` لكوالكوم (`kgsl` عقدتها)،
 * و`Mali` لميدياتك. ولا تُعمَّم `Mali` على Unisoc وRockchip: اسم البائع لا يكفي للحكم على
 * أسرة الرسوم فيهما، فيُقال «غير مدعوم» بدل تخمين (`null`).
 */
fun gpuFamilyOf(chipset: String?): String? {
    val value = chipset?.lowercase() ?: return null
    return when {
        listOf("adreno", "qualcomm", "snapdragon").any(value::contains) -> "Adreno"
        listOf("mali", "mediatek", "dimensity").any(value::contains) -> "Mali"
        else -> null
    }
}

/** سرعة الشبكة: كيلوبت ← ميغابت عند الحدّ نفسه، وبلا كسر بلا معنى (١٫٠٤ MB/s كذبٌ صغير). */
fun formatKbps(kbps: Long): String =
    if (kbps >= KBPS_PER_MBPS) "${formatDouble(kbps / KBPS_PER_MBPS.toDouble())} Mbps" else "$kbps Kbps"

/**
 * تنسيق عشريّ **بمقام ثابت** (`Locale.US`).
 *
 * و`Locale` ليست تفصيلًا: `"%.1f".format(x)` بمقام الجهاز يكتب الفاصلة العربية في بعض
 * الإعدادات، فيقرأ المستخدم `٤٫٢` في حقل إنجليزيّ وتنكسر مقارنة الاختبار على جهاز آخر.
 * والثابت هنا يجعل المخرَج واحدًا في كل مكان — وهو ما يجعل هذه الدوالّ قابلة للقياس.
 */
internal fun formatDouble(value: Double, decimals: Int = 1): String =
    String.format(Locale.US, "%.${decimals}f", value)

/**
 * قراءة نواة واحدة — **أنواع بسيطة فقط** فتُقاس على JVM بلا جهاز.
 * وكل حقل اختياريّ على حدة: نواة قد تكون متصلة وتردّدها غير مقروء، ولا يُخترع لها قيمة.
 */
data class CpuCoreReading(
    val id: Int,
    val online: Boolean,
    val currentMhz: Int? = null,
    val maxMhz: Int? = null,
    val cluster: String? = null,
    val governor: String? = null,
)

/**
 * سطر نواة واحدة — **صيغته هي قاعدته**: `#3 · متوقفة` لنواة مطفأة، و`#0 · 2.40/3.35 GHz ·
 * policy0 · schedutil` لنواة تعمل.
 *
 * وثلاثة قرارات تقيسها هذه الدالة:
 *
 * 1. **النواة المطفأة لا تُعرض بتردّدها.** النواة المُطفأة تُقرأ `0` على كثير من الأنوية،
 *    وطبعُ `0.00 GHz` يقول «توقّفت عند الصفر» بدل «لا تعمل الآن» — والحقيقة هي الثانية.
 * 2. **تردّد بلا قراءة يُحذف من السطر ولا يُكمل بصفر.** نواة متصلة لا تُعلن نواة التردّد
 *    تظهر بمعرّفها وعنقودها وحاكمها وبلا تردّد — لا `0.00 GHz`.
 * 3. **العنقود والحاكم يُذكران حيث يُعرفان** ولا يُفرَض لهما قيمة عامة.
 *
 * @param offlineLabel كلمة «مطفأة» بلغة الواجهة (النموذج لا يعرف لغة).
 */
fun formatCoreLine(reading: CpuCoreReading, offlineLabel: String): String {
    val head = "#${reading.id}"
    if (!reading.online) return "$head · $offlineLabel"

    val parts = mutableListOf(head)
    val current = reading.currentMhz?.takeIf { it > 0 }?.let { formatDouble(it / 1000.0, 2) }
    val top = reading.maxMhz?.takeIf { it > 0 }?.let { formatDouble(it / 1000.0, 2) }
    when {
        current != null && top != null -> parts += "$current/$top GHz"
        // والحالي وحده يُقال، والأقصى وحده **لا يُقال على أنه حال** — يسقط من السطر.
        current != null -> parts += "$current GHz"
        else -> Unit
    }
    reading.cluster?.takeIf { it.isNotBlank() }?.let(parts::add)
    reading.governor?.takeIf { it.isNotBlank() }?.let(parts::add)
    return parts.joinToString(" · ")
}

internal val HEALTH_PERCENT_RANGE = 1..150
internal const val MB_PER_GB = 1024
internal const val KBPS_PER_MBPS = 1024L

// ── قراءة نصّية خالصة: تُقاس على JVM بلا جهاز ────────────────────────────────

/** سطر مخبأ واحد كما تُعلنه `cpu0/cache`: المستوى والنوع والحجم، نصًّا كما هو من النواة. */
data class CacheLevel(val level: String?, val type: String?, val size: String?)

/**
 * ما يُقرأ من `/proc/cpuinfo` ولا يقرؤه أحد غيرنا: الخصائص ومخبأ x86، وثلاثة تعريفاتٍ عتادية
 * تُعلَن على ARM وحدها (`CPU implementer` · `CPU part` · `CPU revision`) — وهي **رموز كما
 * أعلنتها النواة** (`0x41`) لا أسماء مُستنتجة، فلا يُtranslated ولا يُفكّك هنا.
 */
data class CpuInfoText(
    val features: String?,
    val cacheSize: String?,
    val implementer: String? = null,
    val part: String? = null,
    val revision: String? = null,
)

/**
 * محلّل `/proc/cpuinfo` — **دالّة خالصة** لأن الملفّ يختلف باختلاف المعمار: على ARM
 * النصّ `Features:` وعلى x86 `flags:`، والمخبأ `cache size:` على x86 وغائب على ARM.
 * فالقراءة نفسها تُقاس هنا على نصّين حقيقيين، ولا تُترك للتجربة على جهاز واحد.
 */
fun parseCpuInfo(text: String): CpuInfoText {
    var features: String? = null
    var cacheSize: String? = null
    var implementer: String? = null
    var part: String? = null
    var revision: String? = null
    text.lineSequence().forEach { line ->
        val separator = line.indexOf(':')
        if (separator <= 0) return@forEach
        val key = line.substring(0, separator).trim().lowercase(Locale.US)
        val value = line.substring(separator + 1).trim()
        if (value.isEmpty()) return@forEach
        when (key) {
            "features", "flags" -> if (features == null) features = value
            "cache size" -> if (cacheSize == null) cacheSize = value
            // والثلاثة **أول سطر يُعلنه**: ملفّ النواة يكرّرها لكل نواة، وهي رمزٌ واحد للنواة كلها.
            "cpu implementer" -> if (implementer == null) implementer = value
            "cpu part" -> if (part == null) part = value
            "cpu revision" -> if (revision == null) revision = value
        }
    }
    return CpuInfoText(
        features = features,
        cacheSize = cacheSize,
        implementer = implementer,
        part = part,
        revision = revision,
    )
}

/**
 * ملخّص المخبأ سطرًا واحدًا: `L1d 64K · L1i 64K · L2 512K`. والمستويات تُرتَّب بترتيبها
 * المقروء (المستوى ثم النوع) فلا يتغيّر السطر بترتيب مجلدات النواة، ويُرجع `null` حين لا
 * مستويات — ولا يُخترع «L1» لجهاز لم يُعلن مخبأً.
 */
fun formatCache(levels: List<CacheLevel>, fallbackSize: String? = null): String? {
    val rendered = levels.mapNotNull { level ->
        val size = level.size?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val number = level.level?.trim().orEmpty()
        if (number.isEmpty()) return@mapNotNull null
        val letter = when (level.type?.trim()?.lowercase(Locale.US)) {
            "data" -> "d"
            "instruction" -> "i"
            else -> ""
        }
        "L$number$letter $size"
    }.distinct()
    if (rendered.isNotEmpty()) return rendered.joinToString(" · ")
    return fallbackSize?.takeIf { it.isNotBlank() }
}

/** الأنماط المدعومة سطرًا: مرتّبة تصاعديًّا وبلا تكرار — و`null` حين لم يُعلَن نمط. */
fun formatHz(rates: List<Int>): String? {
    val clean = rates.filter { it > 0 }.distinct().sorted()
    if (clean.isEmpty()) return null
    return clean.joinToString(" · ")
}

// مصادر القراءة — نصًّا واحدًا فلا يُكتب مرّتين ولا يختلف وصف مصدرين لفكرة واحدة.
internal const val BUILD_FIELD = "Build"
internal const val UNAME = "uname"
internal const val CPU_INFO = "/proc/cpuinfo"
internal const val CPU_CACHE = "cpu0/cache"
internal const val GLES = "ActivityManager"
internal const val BUILD_PROP = "getprop"
internal const val PROC_STAT = "/proc/stat"
internal const val PROC_MEMINFO = "/proc/meminfo"
internal const val PROC_SWAPS = "/proc/swaps"
internal const val PROC_UPTIME = "/proc/uptime"
internal const val CPU_POLICIES = "cpufreq policies"
internal const val CPU_TOP_CORE = "cpu*_cur_freq"
internal const val CPUINFO_MAX_FREQ = "cpuinfo_max_freq"
internal const val CPUINFO_MIN_FREQ = "cpuinfo_min_freq"
internal const val CPU_ONLINE = "cpu*_online"
internal const val GPU_SYSFS = "gpu sysfs"
internal const val GPU_OPP_TABLE = "gpu opp table"
/** وحدة الإضاءة: رمز عالميّ لا يُترجَم (كما `GHz` و`°C` في هذا الملفّ). */
internal const val LUX_UNIT = "lux"

internal const val THERMAL_ZONE = "thermal_zone"
internal const val BATTERY_SYSFS = "power_supply"
internal const val WINDOW_MANAGER = "DisplayManager"
internal const val SENSOR_SERVICE = "SensorManager"
internal const val STAT_FS = "StatFs"
internal const val TRAFFIC_STATS = "TrafficStats"
// مصادر الجولة الجديدة — نفس القاعدة: اسمٌ واحد يُطبع مع الحقل فلا يُدّعى قياسٌ بلا مصدر.
internal const val MEDIA_DRM = "MediaDrm"
internal const val PACKAGE_MANAGER = "PackageManager"
internal const val SETTINGS_GLOBAL = "Settings.Global"
internal const val SETTINGS_SYSTEM = "Settings.System"
internal const val CAMERA2 = "Camera2"
internal const val SURFACE_FLINGER = "SurfaceFlinger"
internal const val RUNTIME_HEAP = "Runtime"
internal const val TIME_ZONE = "TimeZone"
internal const val CONFIGURATION = "Configuration"
internal const val BATTERY_MANAGER = "BatteryManager"

// ══ صياغات وتحليلات الجولة الجديدة (تكملة ٢٢٥) ═════════════════════════
// كلها صافية: قيمةً في قيمةً خارجًا — والوحدات الرمزية (`d` · `mm` · `f/1.5`) لا تُترجَم.

/**
 * عمر التشغيل: `3d 5h` · `4h 12m` · `12m` — والصفر والسالب ليسا عمرًا فلا يُمرَّران.
 */
fun formatUptime(totalSeconds: Long): String? {
    val seconds = totalSeconds.takeIf { it > 0 } ?: return null
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        // وأقلّ من دقيقة **تُقال بالثواني**: «0m» تُقرأ صفرًا والعمر ليس صفرًا.
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

/**
 * لحظة زمنية بصيغة ثابتة (`yyyy-MM-dd HH:mm` · `Locale.US`) وبمنطقة **مُمرَّرة** —
 * فالصيغة لا تتبدّل بمقام الجهاز، والاختبار يقيسها بمنطقةٍ معلومة لا بمنطقة الآلة.
 */
fun formatEpochMillis(millis: Long, timeZoneId: String?): String? {
    if (millis <= 0L) return null
    val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    format.timeZone = timeZoneId?.let { java.util.TimeZone.getTimeZone(it) } ?: java.util.TimeZone.getDefault()
    return format.format(java.util.Date(millis))
}

/**
 * نسبة الأبعاد العشرية (`2.22 : 1`) — الأطول على الأقصر، فلا تختلف الصياغة بين
 * جهازٍ مُعلَن بالطول وآخر بالعرض. **وهي حسابٌ من البكسلين لا قياس مسطرة** (مُعلن).
 */
fun aspectRatioLabel(widthPx: Int?, heightPx: Int?): String? {
    val w = widthPx?.takeIf { it > 0 }?.toDouble() ?: return null
    val h = heightPx?.takeIf { it > 0 }?.toDouble() ?: return null
    val ratio = maxOf(w, h) / minOf(w, h)
    return "${formatDouble(ratio, 2)} : 1"
}

/**
 * قطر الشاشة بالبوصة: `sqrt(w² + h²) / dpi` — حسابٌ مُعلن من قراءتين لا مَسطرة.
 * ولذلك لا يُقال بعدها «PPI»: الرقمان **متعادلان رياضيًّا**، وعرضهما معًا يوهم بقياسين.
 */
fun diagonalInchesLabel(widthPx: Int?, heightPx: Int?, densityDpi: Int?): String? {
    val w = widthPx?.takeIf { it > 0 } ?: return null
    val h = heightPx?.takeIf { it > 0 } ?: return null
    val dpi = densityDpi?.takeIf { it > 0 } ?: return null
    val diagonalPx = kotlin.math.sqrt(w.toDouble() * w + h.toDouble() * h)
    return formatDouble(diagonalPx / dpi, 2)
}

/**
 * فئة الكثافة: **أقرب قيمة قياسية** (120/160/240/320/480/640). والجهاز ذو 520dpi
 * يكون `XXHDPI` — وهو ما تفعله المنصّة نفسها حين تختار مورد الكثافة، لا تقريبٌ من عندنا.
 */
fun densityBucketLabel(dpi: Int?): String? {
    val value = dpi?.takeIf { it > 0 } ?: return null
    return listOf(
        120 to "LDPI",
        160 to "MDPI",
        240 to "HDPI",
        320 to "XHDPI",
        480 to "XXHDPI",
        640 to "XXXHDPI",
    ).minByOrNull { kotlin.math.abs(it.first - value) }?.second
}

/**
 * فكّ إصدار Vulkan المُرمَّز (‏`android.hardware.vulkan.version`): 10 بتات باتش،
 * 10 صغري، 10 رئيسي. وصفر أو غياب = لا إصدار مُعلن، لا `0.0.0`.
 */
fun decodeVulkanVersion(encoded: Int?): String? {
    val value = encoded?.takeIf { it > 0 } ?: return null
    return "${value shr 22}.${(value shr 12) and 0x3FF}.${value and 0xFFF}"
}

/** مستوى Vulkan (`android.hardware.vulkan.level`) — وصفره ليس مستوى. */
fun vulkanLevelLabel(level: Int?): String? = level?.takeIf { it > 0 }?.toString()

/**
 * سطر `GLES` كما يطبعه `SurfaceFlinger`:
 * `GLES: ARM, Mali-G720 MC7, OpenGL ES 3.2 v1.r44p1-…`.
 *
 * والتحليل **مقيس على الصيغة لا على جهاز**: البائع ثم العارض ثم واجهة الرسم ثم المشغّل،
 * والحقول المفقودة `null` — فلا يُخترع عارضٌ من اسم الشريحة حيث لا يُعلن العتاد اسمه.
 * ونقطة الالفصل الأولى والثانية فقط (`limit = 3`) لأن بعض العارضين يحمل فاصلة في اسمه.
 */
data class GlesLine(
    val vendor: String?,
    val renderer: String?,
    val api: String?,
    val driver: String?,
)

fun parseGlesLine(text: String?): GlesLine? {
    val raw = text ?: return null
    val line = raw.lineSequence()
        .map(String::trim)
        .firstOrNull { it.contains("GLES:") }
        ?.substringAfter("GLES:")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    val parts = line.split(", ", limit = 3)
    val vendor = parts.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() }
    val renderer = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    val tail = parts.getOrNull(2)?.trim().orEmpty()
    val api = Regex("OpenGL ES [0-9.]+").find(tail)?.value
    val driver = api?.let { tail.removePrefix(it).trim() }?.takeIf { it.isNotEmpty() }
    return GlesLine(vendor = vendor, renderer = renderer, api = api, driver = driver)
}

/**
 * `/proc/meminfo` (كيلوبايت) — والوحدات نصّ الملفّ نفسه فلا تحويلٌ يخفي مصدر الرقم.
 * وكل حقل اختياريّ عدا `MemTotal` الذي بدونه لا تلقطة أصلًا.
 */
data class MemInfoKb(
    val totalKb: Long,
    val freeKb: Long?,
    val availableKb: Long?,
    val cachedKb: Long?,
    val buffersKb: Long?,
)

fun parseMemInfo(text: String?): MemInfoKb? {
    val values = mutableMapOf<String, Long>()
    text?.lineSequence()?.forEach { line ->
        val separator = line.indexOf(':')
        if (separator <= 0) return@forEach
        val key = line.substring(0, separator).trim()
        val number = line.substring(separator + 1).trim()
            .split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: return@forEach
        values[key] = number
    }
    val total = values["MemTotal"] ?: return null
    return MemInfoKb(
        totalKb = total,
        freeKb = values["MemFree"],
        availableKb = values["MemAvailable"],
        cachedKb = values["Cached"],
        buffersKb = values["Buffers"],
    )
}

/**
 * صحة البطارية كما تُعلنها `BatteryManager` ← مورد النصّ. **ورقمٌ لا يعرفه هذا الجدول
 * يُرجع `null`** فيُقال «غير مقروء» — لا يُكتب له تفسيرٌ من عندنا.
 */
@StringRes
fun batteryHealthLabelRes(code: Int?): Int? = when (code) {
    1 -> R.string.devinfo_battery_health_unknown
    2 -> R.string.devinfo_battery_health_good
    3 -> R.string.devinfo_battery_health_dead
    4 -> R.string.devinfo_battery_health_over_voltage
    5 -> R.string.devinfo_battery_health_overheat
    6 -> R.string.devinfo_battery_health_cold
    7 -> R.string.devinfo_battery_health_watchdog
    8 -> R.string.devinfo_battery_health_over_current
    9 -> R.string.devinfo_battery_health_failure
    else -> null
}

/** مصدر التغذية (`BATTERY_PLUGGED_*`) ← مورد النصّ. */
@StringRes
fun powerSourceLabelRes(plugged: Int?): Int? = when (plugged) {
    0 -> R.string.devinfo_power_battery
    1 -> R.string.devinfo_power_ac
    2 -> R.string.devinfo_power_usb
    4 -> R.string.devinfo_power_wireless
    8 -> R.string.devinfo_power_dock
    else -> null
}

/** اتجاه الشاشة (`Configuration.ORIENTATION_*`) ← مورد النصّ. */
@StringRes
fun orientationLabelRes(orientation: Int?): Int? = when (orientation) {
    1 -> R.string.devinfo_orientation_portrait
    2 -> R.string.devinfo_orientation_landscape
    3 -> R.string.devinfo_orientation_square
    else -> null
}

/**
 * ميكروأمبير/ساعة ← ميليأمبير/ساعة. **وصفرها قراءة صحيحة** (بطارية مُفرَّغة) فتمرّ —
 * والفرق نفسه المعلن في مستوى البطارية ودورات الشحن، لا قاعدة واحدة تُطبَّق على كل الأرقام.
 */
fun milliAmpHoursFromMicro(microAmpHours: Long?): Int? =
    microAmpHours?.takeIf { it >= 0 }?.let { (it / 1_000).toInt() }

/**
 * أنواع HDR المدعومة كما تُعلنها `Display.HdrCapabilities` — **أسماء منتجات لا تُترجَم**
 * (كما `Mali` و`schedutil`). والرقم الذي لا يُعرف نوعه يُحذف لا يُخترع له اسم.
 */
fun hdrTypeNames(types: List<Int>?): String? {
    val names = types.orEmpty().mapNotNull { type ->
        when (type) {
            1 -> "Dolby Vision"
            2 -> "HDR10"
            3 -> "HLG"
            4 -> "HDR10+"
            else -> null
        }
    }.distinct()
    return names.joinToString(" · ").takeIf { it.isNotEmpty() }
}

/** أقصى HDCP كما يُعلنه `MediaDrm` — ومعنى `-1` (لا مخرج رقميّ) حكمٌ خاصّ يُقال في موضعه. */
fun formatHdcpLevel(level: Int?): String? = when (level) {
    1 -> "HDCP 1.4"
    2 -> "HDCP 2.0"
    3 -> "HDCP 2.1"
    4 -> "HDCP 2.2"
    5 -> "HDCP 2.3"
    else -> null
}

/** `-1` في HDCP ليس مستوىً ولا غيابًا: «لا مخرج رقميّ» — حالة معلنة تُقال لا تُحذف. */
fun isHdcpNoDigitalOutput(level: Int?): Boolean = level == -1

/** فتحة العدسة: `f/1.5` — رمز عالميّ لا يُترجَم، وصفرها ليس فتحة. */
fun formatAperture(value: Float): String? =
    value.takeIf { it > 0f }?.let { "f/" + formatDouble(it.toDouble(), 1) }

/** البُعد البؤري بالملّيمتر (`4.94 mm`). */
fun formatFocalLength(mm: Float): String? =
    mm.takeIf { it > 0f }?.let { formatDouble(it.toDouble(), 2) + " mm" }

/** حجم المستشعر بالملّيمتر: `6.55 × 4.92 mm`. */
fun formatSensorSize(widthMm: Float?, heightMm: Float?): String? {
    val w = widthMm?.takeIf { it > 0f } ?: return null
    val h = heightMm?.takeIf { it > 0f } ?: return null
    return "${formatDouble(w.toDouble(), 2)} × ${formatDouble(h.toDouble(), 2)} mm"
}

/**
 * ميغابكسل من أبعاد مصفوفة البكسل — **حسابٌ مُعلن** لا رقمٌ تسويقيّ: الشركات تُقرّب
 * (‏«48 MP» لمستشعر 48.1)، وهذا العدد ما سمعته العتادة لا ما يقوله الإعلان.
 */
fun formatMegaPixels(widthPx: Int?, heightPx: Int?): String? {
    val w = widthPx?.takeIf { it > 0 } ?: return null
    val h = heightPx?.takeIf { it > 0 } ?: return null
    return formatDouble(w.toDouble() * h / 1_000_000.0, 1) + " MP"
}

/** ترتيب مرشّح الألوان كما يُعلنه `CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT`. */
fun colorFilterName(value: Int?): String? = when (value) {
    0 -> "RGGB"
    1 -> "GRBG"
    2 -> "GBRG"
    3 -> "BGGR"
    4 -> "RGB"
    5 -> "MONO"
    else -> null
}

/**
 * سطر قسم تخزين: `data 41.1/118.5 GB` — المسار والوحدة رمزان لا يُترجَمان،
 * والمستخدم **مقروء** أم `?` حين قُرأ الحجم دون الكمية — لا صفراً مُختلقًا.
 */
fun formatPartitionLine(path: String, usedGb: Float?, totalGb: Float?): String? {
    val total = totalGb?.takeIf { it > 0f } ?: return null
    val used = usedGb?.takeIf { it >= 0f }?.let { formatDouble(it.toDouble()) } ?: "?"
    return "$path $used/${formatDouble(total.toDouble())} GB"
}
