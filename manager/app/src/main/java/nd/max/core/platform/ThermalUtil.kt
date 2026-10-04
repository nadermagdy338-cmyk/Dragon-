/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
قراءة الحراريات من `/sys/class/thermal`: المناطق وأجهزة التبريد ونقاط الحماية، مع تصنيف
 * المناطق بحسب نوعها وربطها بالمعنى (CPU · GPU · البطارية · الشاشة). */

package nd.max.core.platform

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlin.math.abs
import nd.max.core.hardware.VerifiedControl
import nd.max.core.hardware.HardwareCapabilityResolver
import nd.max.core.hardware.HardwareCapabilitySnapshot
import nd.max.core.hardware.PerAppRecoveryStore
import nd.max.core.hardware.RootFileAccess
import nd.max.core.ipc.RootNodeChannel

/** One kernel thermal zone under /sys/class/thermal/thermal_zoneN. */
data class ThermalZoneInfo(
    val id: Int,
    val label: String,
    val category: String,
    val temperatureC: Int,
    val sysfsPath: String,
    val isEnabled: Boolean
)

/** One kernel cooling device under /sys/class/thermal/cooling_deviceN. */
data class CoolingDeviceInfo(
    val id: Int,
    val label: String,
    val category: String,
    val currentState: Int,
    val maxState: Int,
    val sysfsPath: String
)

/** A single trip point (shutdown/passive/hot/critical threshold) for a zone. */
data class ThermalTripPoint(
    val index: Int,
    val temperatureC: Int,
    val kind: String
)

object ThermalUtil {

    private const val THERMAL_ROOT = "/sys/class/thermal"
    private const val COOLING_ROOT = "/sys/class/thermal/cooling_device"
    private const val POLICY_FILE = "/sys/class/thermal/thermal_policy"

    /**
     * Some sensors report garbage (e.g. -273000, or plain 0 on a dead node)
     * instead of a real reading. Normalize millidegree readings down to
     * whole degrees, then zero out anything clearly outside a plausible
     * device operating range rather than showing nonsense to the user.
     */
    private fun sanitizeTemperature(raw: Int): Int {
        val normalized = when {
            abs(raw) >= 10_000 -> raw / 1000   // millidegrees Celsius
            abs(raw) > 200 -> raw / 10          // some vendor nodes use deci-degrees
            else -> raw                         // already Celsius
        }
        return if (normalized < -40 || normalized > 200) 0 else normalized
    }

    /**
     * Android ThermalService fallback. Some vendor kernels expose thermal
     * sensors through dumpsys thermalservice even when /sys/class/thermal is
     * hidden, unreadable, or uses vendor-specific names.
     */
    /**
     * Reads battery temperature from Android's sticky battery broadcast, then
     * falls back to battery-specific kernel/ThermalService sensors. CPU/GPU
     * zones are deliberately excluded: their much higher junction temperature
     * is not a battery reading and must not drive battery safety policy.
     */
    fun readBatteryTemperatureC(context: Context): Float {
        val broadcast = runCatching {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val raw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?: Int.MIN_VALUE
            if (raw == Int.MIN_VALUE) null else (raw / 10f).takeIf(::isValidBatteryTemperature)
        }.getOrNull()
        if (broadcast != null) return broadcast

        val powerSupply = readAbsoluteNode("/sys/class/power_supply/battery/temp")
            ?.toIntOrNull()
            ?.let(::sanitizeTemperature)
            ?.toFloat()
            ?.takeIf(::isValidBatteryTemperature)
        if (powerSupply != null) return powerSupply

        val batteryZone = readThermalZones().asSequence()
            .filter { it.category == "Battery" }
            .map { it.temperatureC.toFloat() }
            .firstOrNull(::isValidBatteryTemperature)
        if (batteryZone != null) return batteryZone

        return readThermalServiceTemperatures()[3].toFloat()
            .takeIf(::isValidBatteryTemperature) ?: 0f
    }

    private fun isValidBatteryTemperature(value: Float): Boolean =
        value.isFinite() && value in -20f..100f

    private fun listDirectoryNames(path: String): List<String> =
        nd.max.core.hardware.RootFileAccess.listDirectories(path)

    /** Returns the generic device capability map used by diagnostics and UI. */
    fun capabilitySnapshot(): HardwareCapabilitySnapshot =
        HardwareCapabilityResolver.resolve()

    /**
     * ما كسبته هذه الدالة في تكملة ٢٠٥ — والعطب كان **مقيسًا بالأسطر لا مُتخيَّلًا**:
     *
     * `HomeDashboardViewModel` ينادي هذه الدالة **في كل نبضة قياس (كل ٢ ثانية)**، وهي تنفّذ
     * `dumpsys thermalservice` عبر صدفة الجذر ثم تُحلّل خرجه بـ٤ تعبيرات نمطيّة. و`dumpsys`
     * وحده من أبطأ أوامر المنصّة — ثوانٍ على كثير من الأجهزة — **ثم يُطرح ناتجه في الحال**:
     * المستدعي لا يستعمله إلا لملء فئةٍ غابت من `/sys/class/thermal`، وهي الحالة النادرة.
     * فما يجري في الحالة الشائعة: رحلة جذر ثقيلة كل ثانيتين يُهمل ناتجها، وتُؤخّر معها كامل
     * نبضة القياس (وهي سلسلة متتابعة) — وظهر ذلك عند المالك «الرئيسية تستغرق دقيقة».
     *
     * **والحلّ لا يُنقص قدرة:** الناتج يُخزّن بنافذة صلاحية، والنداء **واحد متزامن** (‏`@Synchronized`)
     * فلو توازت نبضتان لا تنطلق رحلتان — وهذا هو نفس عرف المستودع: ما لا يتغيّر كل ثانيتين
     * لا يُقاس كل ثانيتين (كما في سقف الرسوم وسقف الأنوية).
     */
    private const val SERVICE_TTL_MS = 20_000L
    private val serviceLock = Any()
    private var serviceCache: IntArray? = null
    private var serviceCacheAtMs = 0L

    /**
     * حرارة خدمة المنصّة كاحتياط **وحده**، بنافذة صلاحية [SERVICE_TTL_MS].
     *
     * **والقفل خاصّ بهذا الكاش لا قفل الكائن:** نبضة القياس صارت متوازية (تكملة ٢٠٥)، فلو
     * انتظرت قراءة المناطق على هذا القفل لوقفت خلف `dumpsys` باردة. و`@Synchronized` على
     * الدالة كان سيقفل الكائن كلّه — فالقفل هنا ضيّق بقدر ما يُحرَس، وهذا هو المقصود.
     *
     * وناتج فاشل (`أصفار`) يُخزَّن أيضًا: إعادة السؤال كل ثانيتين عن خدمة لا تُجيب هي بعينها
     * الكلفة التي أُزيلت، ولحظة انتهاء الصلاحية تُعيد المحاولة فلا يبقى جهاز بلا قراءة أبدًا.
     */
    fun readThermalServiceTemperatures(): IntArray = synchronized(serviceLock) {
        val now = SystemClock.elapsedRealtime()
        serviceCache?.takeIf { now - serviceCacheAtMs in 0 until SERVICE_TTL_MS }?.let {
            return@synchronized it
        }
        val fresh = readThermalServiceTemperaturesUncached()
        serviceCache = fresh
        serviceCacheAtMs = now
        fresh
    }

    private fun readThermalServiceTemperaturesUncached(): IntArray {
        return try {
            val result = Shell.cmd("dumpsys thermalservice 2>/dev/null").exec()
            if (!result.isSuccess || result.out.isEmpty()) return intArrayOf(0, 0, 0, 0)

            var cpu = 0
            var gpu = 0
            var skin = 0
            var battery = 0

            // Do not depend on the order of mValue/mType/mName inside
            // Temperature{...}; vendor Android builds vary slightly.
            val objectPattern = Regex("Temperature\\{([^}]*)}", RegexOption.IGNORE_CASE)
            val valuePattern = Regex("\\bmValue=([-+]?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
            val typePattern = Regex("\\bmType=(\\d+)", RegexOption.IGNORE_CASE)
            val namePattern = Regex("\\bmName=([^,}]+)", RegexOption.IGNORE_CASE)

            for (match in objectPattern.findAll(result.out.joinToString("\n"))) {
                val body = match.groupValues[1]
                val value = valuePattern.find(body)?.groupValues?.getOrNull(1)
                    ?.toFloatOrNull()?.let { if (it in -40f..200f) it.toInt() else 0 } ?: 0
                if (value <= 0) continue

                val type = typePattern.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
                val name = namePattern.find(body)?.groupValues?.getOrNull(1)?.trim().orEmpty()

                when {
                    type == 0 || name.contains("cpu", true) || name.contains("cluster", true) ||
                        name.contains("soc", true) || name.equals("mtktsap", true) ->
                        cpu = maxOf(cpu, value)
                    type == 1 || name.contains("gpu", true) || name.contains("graphics", true) ->
                        gpu = maxOf(gpu, value)
                    type == 2 || name.contains("batt", true) ->
                        battery = maxOf(battery, value)
                    type == 3 || name.contains("skin", true) || name.contains("surface", true) ->
                        skin = maxOf(skin, value)
                }
            }
            intArrayOf(cpu, gpu, skin, battery)
        } catch (_: Exception) {
            intArrayOf(0, 0, 0, 0)
        }
    }

    /**
     * بيانات منطقة لا تتغيّر إلا مع الإقلاع: الاسم المعروض، التصنيف، والمسار.
     *
     * **ولماذا كاش بجيل الإقلاع:** النوع والتصنيف خاصية إقلاع (الأنوية تُعلنها مرة) لا قياس
     * لحظي، فإعادة قراءتها كل دورتين هدر محض — وهو نفس عرف التعلّم في أطلس (`bootId` من
     * `/proc/sys/kernel/random/boot_id`). والقياس اللحظي يبقى للحرارة والتمكين وحدهما.
     */
    internal data class ZoneMeta(
        val id: Int,
        val label: String,
        val category: String,
        val dirPath: String,
    )

    private var zoneMetaCache: Pair<String, List<ZoneMeta>>? = null

    private fun zoneMetas(): List<ZoneMeta> {
        val boot = PerAppRecoveryStore.bootId()
        zoneMetaCache?.let { (cachedBoot, metas) -> if (cachedBoot == boot) return metas }
        val metas = listDirectoryNames(THERMAL_ROOT)
            .filter { it.startsWith("thermal_zone") }
            .sortedBy { it.removePrefix("thermal_zone").toIntOrNull() ?: 0 }
            .mapNotNull { name ->
                runCatching {
                    val id = name.removePrefix("thermal_zone").toIntOrNull()
                        ?: return@mapNotNull null
                    val dir = File(THERMAL_ROOT, name)
                    val rawName = readNode(dir, "type") ?: "unknown"
                    ZoneMeta(
                        id = id,
                        label = prettifyName(rawName),
                        category = classifyZone(rawName),
                        dirPath = dir.absolutePath,
                    )
                }.getOrNull()
            }
        zoneMetaCache = boot to metas
        return metas
    }

    /**
     * التجميع النقي: قيم `temp` و`mode` لكل منطقة بترتيبها ⇒ المناطق. مُختبَر مباشرةً
     * (`ThermalZoneBatchTest`)، ودلالته **مطابقة للسابق حرفيًا**: حرارة غائبة ⇒ صفر،
     * و`mode` غائب ⇒ مُتمكَّن، و`disabled` وحده ⇒ معطَّل.
     */
    internal fun assembleZones(metas: List<ZoneMeta>, values: List<String?>): List<ThermalZoneInfo> =
        metas.mapIndexed { index, meta ->
            val rawTemp = values.getOrNull(index * 2)?.toIntOrNull() ?: 0
            val enabled = values.getOrNull(index * 2 + 1)?.trim() != "disabled"
            ThermalZoneInfo(
                id = meta.id,
                label = meta.label,
                category = meta.category,
                temperatureC = sanitizeTemperature(rawTemp),
                sysfsPath = meta.dirPath,
                isEnabled = enabled,
            )
        }

    /**
     * مسارات القياس لكل منطقة، بترتيب يطابق [assembleZones] بالضبط: الحرارة أولًا ثم التمكين.
     *
     * ودالة نقيّة صغيرة عمدًا حتى يُثبَّت التوافق بين البانية والجمّاع باختبار مباشر: خطأُ ترتيب
     * هنا يعني «حرارة تُقرأ من `mode`» — عطب صامت لا يظهر إلا كأرقام غريبة على الشاشة.
     */
    internal fun zonePaths(metas: List<ZoneMeta>): List<String> =
        metas.flatMap { meta ->
            listOf(File(meta.dirPath, "temp").absolutePath, File(meta.dirPath, "mode").absolutePath)
        }

    fun readThermalZones(): List<ThermalZoneInfo> {
        val metas = zoneMetas()
        if (metas.isEmpty()) return emptyList()
        // قراءتان لكل منطقة (حرارة + تمكين) **في معاملة IPC واحدة** بدل ٢×عدد المناطق،
        // وخريطة الأنواع لا تُقرأ أصلًا بعد أول مرة في الإقلاع.
        return assembleZones(metas, RootFileAccess.readMany(zonePaths(metas)))
    }

    fun readCoolingDevices(): List<CoolingDeviceInfo> {
        val deviceNames = listDirectoryNames(THERMAL_ROOT)
            .filter { it.startsWith("cooling_device") }
            .sortedBy { it.removePrefix("cooling_device").toIntOrNull() ?: 0 }

        return deviceNames.mapNotNull { name ->
            try {
                val id = name.removePrefix("cooling_device").toIntOrNull() ?: return@mapNotNull null
                val dir = File(THERMAL_ROOT, name)
                val rawName = readNode(dir, "type") ?: "unknown"

                CoolingDeviceInfo(
                    id = id,
                    label = rawName,
                    category = classifyCoolingDevice(rawName),
                    currentState = readNode(dir, "cur_state")?.toIntOrNull() ?: 0,
                    maxState = readNode(dir, "max_state")?.toIntOrNull() ?: 0,
                    sysfsPath = dir.absolutePath
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    fun readTripPoints(zoneSysfsPath: String): List<ThermalTripPoint> {
        val dir = File(zoneSysfsPath)
        val trips = mutableListOf<ThermalTripPoint>()
        for (i in 0..9) {
            val rawTemp = readNode(dir, "trip_point_${i}_temp")?.toIntOrNull() ?: break
            val kind = readNode(dir, "trip_point_${i}_type") ?: "unknown"
            trips.add(ThermalTripPoint(i, sanitizeTemperature(rawTemp), kind))
        }
        return trips
    }

    fun setZoneEnabled(zoneId: Int, enabled: Boolean): Boolean {
        val mode = if (enabled) "enabled" else "disabled"
        val path = "$THERMAL_ROOT/thermal_zone$zoneId/mode"
        val result = VerifiedControl.apply(
            requested = mode,
            write = { value -> writeNode(path, value) },
            read = { readNode(File(path), "") },
        )
        return result.successful
    }

    fun setCoolingState(deviceId: Int, state: Int): Boolean {
        val safeState = state.coerceAtLeast(0)
        val path = "$THERMAL_ROOT/cooling_device$deviceId/cur_state"
        val result = VerifiedControl.apply(
            requested = safeState.toString(),
            write = { value -> writeNode(path, value) },
            read = { readAbsoluteNode(path) },
            equals = { expected, actual -> actual?.toIntOrNull() == expected.toInt() },
        )
        return result.successful
    }

    fun isThermalPolicySupported(): Boolean = Shell.cmd("[ -f $POLICY_FILE ]").exec().isSuccess

    /**
     * السياسة الحرارية الحالية. والصدفة كانت `cat … || echo default`، والقيمة المطلوبة هي
     * فحواها **أو** `default` عند الغياب/الفشل — وهي نفسها دلالة `read() ?: "default"`،
     * بلا رحلة صدفة لكل نداء.
     */
    fun readThermalPolicy(): String =
        RootFileAccess.read(POLICY_FILE) ?: "default"

    fun writeThermalPolicy(policy: String): Boolean {
        return Shell.cmd("echo $policy > $POLICY_FILE 2>/dev/null").exec().isSuccess
    }

    /** Reads a sysfs node through IPC, direct I/O, then a root shell. */
    private fun readNode(dir: File, node: String): String? {
        val path = if (node.isBlank()) dir.absolutePath else File(dir, node).absolutePath
        return readAbsoluteNode(path)
    }

    /**
     * قراءة عقدة مطلقة بترتيب `RootFileAccess` الواحد — قارئ أصلي ← IPC الجذر ← ملف مباشر
     * ← صدفة. وهذه الدالة كانت تُعيد كتابة الترتيب نفسه بثلاث طبقات منها (IPC ثم ملف ثم
     * `cat`)، فصار المصدر واحدًا: ترتيب يتباعد بين موضعين آخر هو عطب يظهر على أول جهاز
     * يخالف مفترضات أحدهما.
     */
    private fun readAbsoluteNode(path: String): String? =
        runCatching { RootFileAccess.read(path) }.getOrNull()

    private fun writeNode(path: String, value: String): Boolean {
        RootNodeChannel.service?.let { service ->
            runCatching { if (service.writeText(path, value)) return true }
        }
        // HyperOS 3: direct root writes to 0444 sysfs nodes fail with EACCES;
        // only the chmod dance (proven by binutils/binprofiles on the real
        // device) succeeds, so the shell fallback replicates it here too.
        return runCatching {
            Shell.cmd(
                "m=\$(stat -c %a '$path' 2>/dev/null)",
                "chmod 644 '$path' 2>/dev/null",
                "printf '%s' '$value' > '$path'",
                "chmod \$m '$path' 2>/dev/null"
            ).exec().isSuccess
        }.getOrDefault(false)
    }

    private fun classifyZone(rawName: String): String = when {
        rawName.contains("cpu", true) || rawName.contains("cluster", true) ||
            rawName.contains("mtkts", true) || rawName.contains("soc", true) ||
            rawName.contains("tsens", true) || rawName.contains("ap", true) -> "CPU"
        rawName.contains("gpu", true) || rawName.contains("graphics", true) -> "GPU"
        rawName.contains("batt", true) -> "Battery"
        rawName.contains("charg", true) || rawName.contains("usb", true) -> "Charger"
        rawName.contains("skin", true) || rawName.contains("surface", true) -> "Skin"
        rawName.contains("modem", true) || rawName.contains("radio", true) -> "Modem"
        rawName.contains("wifi", true) -> "WiFi"
        rawName.contains("cam", true) -> "Camera"
        rawName.contains("flash", true) || rawName.contains("led", true) -> "Flash"
        rawName.contains("pa", true) || rawName.contains("amplifier", true) -> "PA"
        else -> "System"
    }

    private fun classifyCoolingDevice(rawName: String): String = when {
        rawName.contains("cpu", true) -> "CPU"
        rawName.contains("gpu", true) -> "GPU"
        rawName.contains("fan", true) || rawName.contains("cooler", true) -> "Fan"
        rawName.contains("backlight", true) || rawName.contains("brightness", true) -> "Display"
        rawName.contains("thermal", true) -> "Thermal"
        else -> "Other"
    }

    private fun prettifyName(rawName: String): String {
        return rawName.replace("_", " ")
            .replace("tsens", "Temperature Sensor")
            .replace("tz", "Zone")
            .split(" ")
            .joinToString(" ") { w -> w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
    }
}
