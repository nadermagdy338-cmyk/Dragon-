/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.topjohnwu.superuser.Shell
import java.text.Collator

/** صفّ واحد من `top`: العملية كما يعرضها الجهاز، ومعها ما يعرفه مدير الحزم عنها. */
data class ProcessInfo(
    val pid: String,
    /** الحجم كما يُعرض (`1.2 MB`). */
    val res: String,
    /** الحجم نفسه بالكيلوبايت — للمقارنة والرسم، فتحليل النصّ المعروض كان يعطي صفرًا. */
    val resKb: Long,
    val cpu: String,
    /** النسبة العددية نفسها (`cpu` نصًّا للعرض، وهذه للحساب). */
    val cpuPercent: Float,
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
    val icon: Drawable? = null
)

/** ترتيب القائمة: بالمعالج أو بالذاكرة — وهما الرقمان الوحيدان القابلان للمقارنة هنا. */
enum class ProcessSortType { CPU, RAM }

/**
 * قراءة العمليات من `top` وتنسيقها للعرض.
 *
 * **لماذا عيّنتان:** `top` في عيّنة واحدة يعرض **متوسّط ما بعد الإقلاع** لكل عملية، لا ما
 * تستهلكه الآن؛ فيبدو خامل الجهاز أثقل من مشغوله. ولذلك تُطلب عيّنتان بفاصل ثانية، ويُبنى
 * الحكم على الثانية وحدها. والعيّنتان تُفصلان في هذا الملف **بحدّ صريح** (سطر ترويسة الأعمدة
 * يُصفّر المتراكم) بدل الاعتماد على «نفس الـPID يُكتب مرتين فيُستبدل» — فالأخير يعتمد على
 * ترتيب الإخراج، وأوّل نسخة من `top` تخرجه مجموعةً أخرى يعطي أرقامًا من متوسّط الإقلاع بلا أن
 * يظهر خطأ.
 *
 * **والأوامر عبر [Shell] وحدها** (libsu): كل أوامر الجذر في MaxManager لها طريق واحد، فلا
 * `Runtime.exec("su -c")` في هذا الملف ولا في غيره.
 */
object ProcessMonitorUtil {

    private const val SAMPLE_COUNT = 2
    private const val SAMPLE_DELAY_SECONDS = 1
    private const val COLUMNS = "PID,RES,%CPU,NAME"

    /** تُقطع أسماء الحزم الطويلة عند أول مسافة، والأعمدة تُفصل بمسافات متعددة. */
    private val COLUMN_SEPARATOR = Regex("\\s+")

    /** حجم مقروء وسuffix اختياري: `28M` · `1.2G` · `440K` · `512`. */
    private val SIZE = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*([KMGT])?", RegexOption.IGNORE_CASE)

    private val UNIT_KB: Map<String, Long> = mapOf("K" to 1L, "M" to 1_024L, "G" to 1_048_576L, "T" to 1_073_741_824L)

    /**
     * أعلى [limit] عملية بحسب [sortType]، تنازليًّا.
     *
     * والعيّنة الثانية (الحيّة) هي المرجع؛ وإن فشل الأمر أو خرج بلا صفوف صحيحة تُعاد قائمة
     * فارغة — والشاشة تُظهر «لا شيء» بدل أرقام من متوسّط الإقلاع.
     */
    fun getTopProcesses(context: Context, limit: Int, sortType: ProcessSortType): List<ProcessInfo> {
        val pm = context.packageManager
        val samples = Shell.cmd(
            "top -b -n $SAMPLE_COUNT -d $SAMPLE_DELAY_SECONDS -o $COLUMNS"
        ).exec().out

        val latest = latestSample(samples)
        return latest
            .mapNotNull { row -> parseRow(pm, row) }
            .sortedWith(comparatorFor(sortType))
            .take(limit)
    }

    /**
     * صفوف **آخر** عيّنة فقط.
     *
     * والحدّ هو سطر الترويسة: كل عيّنة تُصدَّر بترويسة أعمدة، فما بعد آخر ترويسة هو العيّنة
     * الحيّة. وإن لم تظهر ترويسة (نسخة `top` تخالف المتوقّع) تُقرأ كل الصفوف — وهو ما كان
     * يحدث قبل هذا الحرس أيضًا.
     */
    private fun latestSample(lines: List<String>): List<String> {
        val headerIndex = lines.indexOfLast { it.trim().startsWith("PID") }
        return if (headerIndex >= 0) lines.drop(headerIndex + 1) else lines
    }

    private fun parseRow(pm: PackageManager, rawLine: String): ProcessInfo? {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("Tasks") || line.startsWith("Mem")) return null

        val columns = line.split(COLUMN_SEPARATOR)
        if (columns.size < 4) return null
        val pid = columns[0].toIntOrNull()?.toString() ?: return null   // رقم فقط: سطر ترويسة عابر يُرفض

        val name = columns[3]
        val identity = identify(pm, name)
        val resKb = sizeInKb(columns[1])
        val cpuText = columns[2].removeSuffix("%")
        return ProcessInfo(
            pid = pid,
            res = formatSize(resKb),
            resKb = resKb,
            cpu = "$cpuText%",
            cpuPercent = cpuText.toFloatOrNull() ?: 0f,
            packageName = name,
            appName = identity.label,
            isSystem = identity.isSystem,
            icon = identity.icon,
        )
    }

    /** اسم العرض والأيقونة وصفة النظام — وكلها «لا شيء» إن لم تكن العملية حزمة مثبّتة. */
    private data class Identity(val label: String, val isSystem: Boolean, val icon: Drawable?)

    private fun identify(pm: PackageManager, rawName: String): Identity {
        if (!rawName.contains('.')) {
            // ثنائي نظام: `/system/bin/surfaceflinger` ⇒ `surfaceflinger` (والاسم كما هو إن لا مسار).
            return Identity(rawName.substringAfterLast('/'), isSystem = true, icon = null)
        }
        val info = runCatching { pm.getApplicationInfo(rawName, 0) }.getOrNull()
            ?: return Identity(rawName, isSystem = true, icon = null)
        return Identity(
            label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(rawName),
            isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            icon = runCatching { pm.getApplicationIcon(info) }.getOrNull(),
        )
    }

    /**
     * الترتيب: الرقم تنازليًّا، ثم الاسم أبجديًّا عند التساوي.
     *
     * والاسم يُقارن بمُرتِّب اللغة الحالية ([Collator]) لا بـ`String.compareTo`: التطبيق بثمانٍ
     * وثمانين لغة، ومقارنة نقاط الترميز ترتّب العربية بعيدًا عن موضعها الأبجدي.
     */
    private fun comparatorFor(sortType: ProcessSortType): Comparator<ProcessInfo> {
        val byName = Comparator<ProcessInfo> { a, b -> labelCollator.compare(a.appName, b.appName) }
        return when (sortType) {
            ProcessSortType.CPU -> compareByDescending<ProcessInfo> { it.cpuPercent }.then(byName)
            ProcessSortType.RAM -> compareByDescending<ProcessInfo> { it.resKb }
                .then(byName)
        }
    }

    private val labelCollator: Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    /**
     * صيغة العرض الواحدة للحجم (`1.2 MB`).
     *
     * **وقرار الرقم المجرّد مُعلَن:** `top` على هذه الأجهزة يطبع RES بالكيلوبايت عند غياب
     * الـsuffix، وهو ما تعتمده [sizeInKb] أدناه. فالصيغة المعروضة والمقارَنة واحدة لا تتفرّقان،
     * والمستدعي الذي يحتاج رقمًا يستعمل `resKb` ولا يحلّل النصّ المعروض.
     */
    private fun formatSize(kb: Long): String =
        if (kb >= 1_024L) String.format("%.1f MB", kb / 1_024f) else "$kb KB"

    /** الحجم بالكيلوبايت؛ وما لا يُقرأ يُحسب صفرًا فلا يتصدّر الترتيب بلا سبب. */
    private fun sizeInKb(res: String): Long {
        val match = SIZE.find(res.trim()) ?: return 0L
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
        val unit = match.groupValues[2].uppercase().ifEmpty { "K" }   // مجرّد رقم = كيلوبايت
        return (value * (UNIT_KB[unit] ?: 1L)).toLong()
    }

    /** إشارة قتل مباشرة للـPID. والعملية قد تُعيد تشغيل نفسها إن كانت جزءًا من تطبيق حيّ. */
    fun killProcess(pid: String): Boolean = Shell.cmd("kill -9 $pid").exec().isSuccess

    /** إنهاء كل عمليات حزمة، كما تفعل إعدادات النظام. */
    fun forceStopApp(packageName: String): Boolean = Shell.cmd("am force-stop $packageName").exec().isSuccess
}
