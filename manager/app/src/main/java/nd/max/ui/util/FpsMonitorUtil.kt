/*
 * Adapted from ZKM (Zuan Kernel Manager) FpsReader.kt and MonitorReader.kt.
 * Original FpsReader base: helloklf (vtools). ZKM integration: Copyright (c) 2025 ZKM, GPL-3.0.
 * Adaptation: Copyright (C) 2026-2027 Zexshia
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

package nd.max.ui.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import com.topjohnwu.superuser.Shell
import nd.max.core.hardware.RootFileAccess
import kotlin.math.abs
import java.util.regex.Pattern

enum class FpsReadMode { SURFACEFLINGER, KERNEL_NODE, DUMPSYS_TIMESTATS }

/**
 * Reads live FPS + system load metrics for the in-game floating overlay
 * ([nd.max.service.FpsOverlayService]). All shell reads go through libsu's
 * [Shell.cmd], same as the rest of MaxManager's manager app, instead of ZKM's
 * separate `ShellExecutor` wrapper.
 */
object FpsMonitorUtil {

    private const val PREFS_NAME = "maxmanager_fps_overlay"
    private const val KEY_MODE = "fps_read_mode"

    var currentMode: FpsReadMode = FpsReadMode.SURFACEFLINGER
        private set

    private var kernelFpsPath: String? = null
    private var lastSfFrames = -1L
    private var lastSfTime = -1L

    // عدادات دلتا /proc/stat — volatile لأن عدة حلقات (لوحة النظام،
    // حلقة التعلم، محرك التوصيات، حاكم الطاقة) تقرأها من خيوط IO
    // متزامنة، وlong غير ذري على ART 32-bit (قراءة ممزقة محتملة).
    @Volatile private var lastCpuTotal = 0L
    @Volatile private var lastCpuIdle = 0L

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val idx = prefs.getInt(KEY_MODE, FpsReadMode.SURFACEFLINGER.ordinal)
        currentMode = FpsReadMode.entries.getOrElse(idx) { FpsReadMode.SURFACEFLINGER }
        if (currentMode == FpsReadMode.KERNEL_NODE) findKernelFpsNode()
    }

    fun setMode(context: Context, mode: FpsReadMode) {
        currentMode = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MODE, mode.ordinal).apply()
        lastSfFrames = -1L
        lastSfTime = -1L
        if (mode == FpsReadMode.KERNEL_NODE) findKernelFpsNode()
    }

    private fun shellOut(cmd: String): String =
        Shell.cmd(cmd).exec().out.joinToString("\n").trim()

    fun getFps(): Float {
        val primary = when (currentMode) {
            FpsReadMode.SURFACEFLINGER -> readSurfaceFlingerFps()
            FpsReadMode.KERNEL_NODE -> readKernelFps()
            FpsReadMode.DUMPSYS_TIMESTATS -> readDumpsysFps()
        }
        if (primary > 0f) return primary
        // Android/HyperOS vendor builds frequently remove or change the
        // legacy SurfaceFlinger transaction used above. Falling back keeps
        // the HUD functional instead of permanently showing --.
        return when (currentMode) {
            FpsReadMode.SURFACEFLINGER -> readDumpsysFps().takeIf { it > 0f } ?: readKernelFps()
            FpsReadMode.KERNEL_NODE -> readDumpsysFps().takeIf { it > 0f } ?: readSurfaceFlingerFps()
            FpsReadMode.DUMPSYS_TIMESTATS -> readSurfaceFlingerFps().takeIf { it > 0f } ?: readKernelFps()
        }
    }

    /** `service call SurfaceFlinger 1013` returns a running frame counter; FPS is its delta over time. */
    private fun readSurfaceFlingerFps(): Float {
        return try {
            val result = shellOut("service call SurfaceFlinger 1013")
            if (!result.contains("Parcel")) return 0f
            val matcher = Pattern.compile("([0-9a-fA-F]{8})\\s+([0-9a-fA-F]{8})").matcher(result)
            if (!matcher.find()) return 0f
            val lo = (matcher.group(1) ?: return 0f).toLong(16)
            val hi = (matcher.group(2) ?: return 0f).toLong(16)
            val frames = (hi shl 32) or lo
            val now = System.currentTimeMillis()
            var fps = 0f
            if (lastSfTime > 0 && lastSfFrames >= 0) {
                val dt = now - lastSfTime
                if (dt > 0) fps = (frames - lastSfFrames) * 1000f / dt
            }
            lastSfFrames = frames
            lastSfTime = now
            if (fps in 0f..240f) fps else 0f
        } catch (e: Exception) { 0f }
    }

    private val KERNEL_FPS_CANDIDATES = listOf(
        "/sys/class/drm/sde-crtc-0/measured_fps",
        "/sys/class/graphics/fb0/measured_fps",
        "/sys/class/video/fps_info"
    )

    private fun findKernelFpsNode() {
        // `[ -f … ]` في صدفة لكل مرشَّح ⇒ فحص وجود مباشر (وبدونه صدفتان لكل قراءة FPS).
        kernelFpsPath = KERNEL_FPS_CANDIDATES.firstOrNull { RootFileAccess.exists(it) } ?: ""
    }

    private fun readKernelFps(): Float {
        if (kernelFpsPath.isNullOrEmpty()) {
            findKernelFpsNode()
            if (kernelFpsPath.isNullOrEmpty()) return 0f
        }
        val line = runCatching { RootFileAccess.read(kernelFpsPath!!) }.getOrNull() ?: return 0f
        return secondField(line)?.toFloatOrNull() ?: 0f
    }

    /** الحقل الثاني من سطر — بديل `awk '{print $2}'` بلا صدفة، ومُختبَر مباشرة. */
    internal fun secondField(line: String): String? =
        line.trim().split("\\s+".toRegex()).getOrNull(1)

    private fun readDumpsysFps(): Float {
        return try {
            val out = shellOut(
                "(dumpsys SurfaceFlinger --timestats -dump && " +
                    "dumpsys SurfaceFlinger --timestats -clear -enable) | grep averageFPS"
            )
            val value = Regex("averageFPS\\s*[=:]\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
                .find(out)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: return 0f
            value.takeIf { it in 1f..240f } ?: 0f
        } catch (e: Exception) { 0f }
    }

    // ── System load metrics shown alongside FPS in the overlay ──────────

    fun getForegroundPackage(): String {
        return try {
            listOf(
                "dumpsys activity activities | grep mResumedActivity",
                "dumpsys window | grep mCurrentFocus",
                "cmd activity get-top-activity"
            ).firstNotNullOfOrNull { cmd ->
                parsePackageFromDump(shellOut(cmd)).ifEmpty { null }
            } ?: ""
        } catch (e: Exception) { "" }
    }

    private fun parsePackageFromDump(raw: String): String {
        if (raw.isEmpty()) return ""
        val matcher = Pattern.compile("([a-zA-Z0-9_]+\\.[a-zA-Z0-9_.]+)/").matcher(raw)
        if (matcher.find()) {
            val found = matcher.group(1) ?: return ""
            if (found != "com.android.systemui" && found != "android") return found
        }
        return ""
    }

    fun getCurrentRenderer(pkg: String): String {
        if (pkg.isEmpty()) return "FPS"
        return try {
            // `pidof` تحتاج صدفة (لا مقابل ملفي لها)، وأمّا خريطتَي الذاكرة فكانتا صدفتين
            // إضافيتين — صارتا قراءة واحدة: الملف يُقرأ، والبحث `contains` في الذاكرة.
            val pid = shellOut("pidof $pkg").split(" ").firstOrNull() ?: return "FPS"
            val maps = runCatching { RootFileAccess.read("/proc/$pid/maps") }.getOrNull()
                ?: return "FPS"
            if (maps.contains("libvulkan.so")) return "VULKAN"
            if (maps.contains("libGLES")) return "OPENGL"
            "FPS"
        } catch (e: Exception) { "FPS" }
    }

    /**
     * حمل المعالج كنسبة مئوية من دلتا /proc/stat بين استدعاءين.
     *
     * @Synchronized لأن عدة حللات مراقبة (لوحة النظام كل 2 ث، التعلم
     * المعزز كل 3 ث، محرك التوصيات كل 5 ث، حاكم الطاقة كل 2 ث) تتقاسم
     * نفس العدادات الثابتة — بدون القفل تتنافس قراءة-تعديل-كتابة
     * فتُفقد فترات أو تُقرأ قيم ممزقة.
     *
     * صيغة الإجمالي تشمل كل حقول النواة (حتى steal) وiowait يُحسب
     * ضمن الخمول — انتظار IO ليس عملًا للمعالج. الصيغة السابقة
     * (user+nice+system+idle فقط) كانت تحسب iowait/irq/softirq/steal
     * كأنها عمل فتبالغ في الحمل ممنهجًا على الأنظمة كثيفة IO.
     */
    /** عيّنة من السطر الأول لـ`/proc/stat`: المجموع والخمول — الحقول كلها. */
    internal data class CpuSample(val total: Long, val idle: Long)

    /**
     * تحليل سطر `cpu …` — نقيّ ومُختبَر (`FpsMonitorParseTest`)، و`null` للسطر المشوّه:
     *فشلُ قراءة لا يُعرض صفرًا يُوهم بأن المعالج ساكن.
     */
    internal fun parseStatSample(line: String): CpuSample? {
        val p = line.trim().split("\\s+".toRegex())
        if (p.size < 5) return null
        val user = p[1].toLongOrNull() ?: return null
        val nice = p[2].toLongOrNull() ?: return null
        val system = p[3].toLongOrNull() ?: return null
        val idle = p[4].toLongOrNull() ?: return null
        val iowait = p.getOrNull(5)?.toLongOrNull() ?: 0L
        val irq = p.getOrNull(6)?.toLongOrNull() ?: 0L
        val softirq = p.getOrNull(7)?.toLongOrNull() ?: 0L
        val steal = p.getOrNull(8)?.toLongOrNull() ?: 0L
        val idleAll = idle + iowait
        return CpuSample(
            total = user + nice + system + idleAll + irq + softirq + steal,
            idle = idleAll,
        )
    }

    /** نسبة الحمل بين عيّنتين — نفس الصيغة السابقة حرفيًا، نقيّة ومُختبَرة. */
    internal fun loadPercent(previous: CpuSample, current: CpuSample): Int {
        val dTotal = current.total - previous.total
        val dIdle = current.idle - previous.idle
        if (dTotal <= 0L) return 0
        return (((dTotal - dIdle) * 100 / dTotal).toInt()).coerceIn(0, 100)
    }

    @Synchronized
    fun getCpuLoad(): Int {
        // **قراءة مباشرة لا صدفة:** `cat /proc/stat` كانت تولّد صدفة جذر لكل نداء، والدالة
        // تُنادى من أربع حلقات (لوحة كل ٢ث · تعلّم كل ٣ث · توصيات كل ٥ث · حاكم طاقة كل ٢ث).
        // وطبقة `RootFileAccess` تقرأ الملف مباشرة (أو عبر IPC) وتُبقي الصدفة احتياطًا أخيرًا
        // فيها — فالسلوك على جهاز لا يُقرأ فيه الملف محفوظ حرفيًّا.
        val line = runCatching { RootFileAccess.read("/proc/stat") }.getOrNull()
            ?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: return 0
        val sample = parseStatSample(line) ?: return 0
        val previous = if (lastCpuTotal == 0L) null else CpuSample(lastCpuTotal, lastCpuIdle)
        lastCpuTotal = sample.total
        lastCpuIdle = sample.idle
        return previous?.let { loadPercent(it, sample) } ?: 0
    }

    fun getPowerWatt(): Float {
        return try {
            // كانتا صدفتين منفصلتين ⇒ قراءتان في معاملة واحدة (والصدفة تبقى داخل الطبقة).
            val values = RootFileAccess.readMany(
                listOf(
                    "/sys/class/power_supply/battery/voltage_now",
                    "/sys/class/power_supply/battery/current_now",
                )
            )
            val v = values.getOrNull(0)?.toLongOrNull() ?: 0L
            val c = values.getOrNull(1)?.toLongOrNull() ?: 0L
            if (v > 0 && c != 0L) (abs(v * c).toDouble() / 1_000_000_000_000.0).toFloat() else 0f
        } catch (e: Exception) { 0f }
    }

    fun getBatteryTemp(context: Context): Float {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        } catch (e: Exception) { 0f }
    }

    data class RamInfo(val usedMb: Int, val totalMb: Int)

    fun getRamInfo(context: Context): RamInfo {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val totalMb = (info.totalMem / 1048576L).toInt()
            val usedMb = ((info.totalMem - info.availMem) / 1048576L).toInt()
            RamInfo(usedMb, totalMb)
        } catch (e: Exception) { RamInfo(0, 0) }
    }

    fun getAppIcon(context: Context, packageName: String): Drawable? {
        if (packageName.isEmpty()) return null
        return try { context.packageManager.getApplicationIcon(packageName) } catch (e: Exception) { null }
    }
}
