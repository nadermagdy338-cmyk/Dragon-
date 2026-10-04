/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.core.platform

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.topjohnwu.superuser.Shell
import java.text.Collator

/**
 * قراءة جدول العمليات من الجهاز — **مصدر واحد** للشاشة وللتراكب.
 *
 * ### لماذا طبقة مستقلّة
 *
 * كانت القراءة والحساب والعرض في ملفّ واحد داخل `ui/util`، ويستدعيها التراكب من `service/`:
 * فالشاشة والتراكب يقرآن بـ**مَعلمين** (`limit` · `sort`) لا يعرف أحدهما ما يفعله الآخر، ويُحسب
 * ما يُعرض مرّتين. وهي الآن هنا: تُقرأ الشجرة مرّة، ثم **يُصفّى ويُرتَّب في موضع واحد**، فما تراه
 * في الشاشة هو نفسه ما يُرسم فوق اللعبة — والفرق بينهما يصير مستحيلًا لا مُكتشَفًا.
 *
 * ### والفرق الذي كان يُخفى: **غياب الجواب ليس صفرًا**
 *
 * `top` قد لا يُخرج شيئًا (نسخة مخالفة · رفض من الـsu · جهاز يمنع القراءة) — وكان ذلك يُترجَم
 * `emptyList()` فتُرسم «لا توجد عمليات» وهي **كذبة**: العمليات موجودة، والقراءة هي التي لم تصل.
 * فصار [ProcessSample.failed] يميّز الحالتين، والعرض يقول «لا جواب» بدل «لا شيء».
 *
 * ### ولماذا عيّنتان
 *
 * `top` في عيّنة واحدة يعرض **متوسّط ما بعد الإقلاع** لكل عملية لا ما تستهلكه الآن، فيبدو خامل
 * الجهاز أثقل من مشغوله. فتطلب الأداة عيّنتين بفاصل ثانية، والحكم على الثانية وحدها. وحدّ العيّنة
 * **سطر ترويسة الأعمدة** لا «تكرار الـPID»: الثاني يعتمد على ترتيب الإخراج، فأوّل نسخة تُخرجه
 * بترتيب آخر تُعيد أرقام متوسّط الإقلاع بلا خطأ ظاهر.
 *
 * ### والأوامر
 *
 * كل أوامر الجذر عبر [Shell] وحدها (libsu) — لا `Runtime.exec("su -c …")` هنا ولا في غيره.
 */
enum class ProcessScope { All, Apps, System }

/** الترتيب: الرقمان القابلان للمقارنة في `top`، **والاسم**. */
enum class ProcessSort { Cpu, Memory, Name }

/** صفّ خام كما كتبه `top` — قبل أن يُسأل مدير الحزم عن صاحبه. */
data class ProcessRow(val pid: Int, val residentKb: Long, val cpuPercent: Float, val name: String)

/** صفّ واحد جاهز للعرض: رقم الجهاز + ما يعرفه أندرويد عن الحزمة. */
data class ProcessReading(
    val pid: Int,
    val cpuPercent: Float,
    val residentKb: Long,
    /** اسم الحزمة، أو مسار/اسم ثنائيّ نظام إن لم تكن حزمة مثبّتة. */
    val packageName: String,
    /** ما يُعرض للقارئ: تسمية الحزمة، أو اسم الثنائيّ. */
    val label: String,
    val isSystem: Boolean
) {
    /** ما يعرفه مدير الحزم عن حزمة: ما يُكتب للقارئ وصفة النظام. */
    internal data class Identity(val label: String, val isSystem: Boolean)

    /** حجم مقروء — الصيغة واحدة لكل من يعرض [residentKb]، فلا تختلف وحدة عن أخرى. */
    val residentText: String
        get() = if (residentKb >= 1024L) "%.1f MB".format(residentKb / 1024f) else "$residentKb KB"

    /** نسبة مقروءة — بمنزلة واحدة كأقصى، فلا يتغيّر عرض الرقم بعرض العمود. */
    val cpuText: String get() = "%.1f%%".format(cpuPercent)
}

/** تعداد **الشجرة كلها** لا القائمة المعروضة: رقم يصف ما قرأته الأداة لا ما قصّته. */
data class ProcessTally(val running: Int, val apps: Int, val system: Int)

/**
 * نتيجة دورة قراءة واحدة.
 *
 * @param readings ما سيُعرض (بعد التصفية والترتيب والقَصّ).
 * @param tally التعداد على الشجرة كلها.
 * @param failed لم تُقرأ الشجرة أصلًا — **المعنى مختلف عن قائمة فارغة**.
 */
data class ProcessSample(
    val readings: List<ProcessReading>,
    val tally: ProcessTally,
    val failed: Boolean
) {
    companion object {
        /** جواب غائب صريح — يُستعمل في العرض والتشغيل قبل أوّل قراءة وعند فشلها. */
        val noAnswer = ProcessSample(emptyList(), ProcessTally(0, 0, 0), failed = true)
    }
}

object ProcessFeed {

    private const val SAMPLE_COUNT = 2
    private const val SAMPLE_DELAY_SECONDS = 1
    private const val COLUMNS = "PID,RES,%CPU,NAME"

    /** أدنى عدد صفوف مقبول في عيّنة سليمة: `top` بلا صفوف يعني قراءة لم تنجح. */
    private const val MIN_ROWS = 1

    private val SEPARATOR = Regex("\\s+")

    /** `28M` · `1.2G` · `440K` · `512` (مجرّدًا = كيلوبايت). */
    private val SIZE = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*([KMGT])?", RegexOption.IGNORE_CASE)

    private val UNIT_KB = mapOf("K" to 1L, "M" to 1024L, "G" to 1_048_576L, "T" to 1_073_741_824L)

    /**
     * دورة قراءة واحدة: اقرأ ← ميّز ← صفِّ ← رتّب ← اقصر.
     *
     * @param query نصّ بحث في الاسم المعروض أو الحزمة؛ الفراغ يعني لا تصفية.
     */
    fun read(
        context: Context,
        limit: Int,
        sort: ProcessSort,
        scope: ProcessScope = ProcessScope.All,
        query: String = ""
    ): ProcessSample {
        val rows = sampleRows() ?: return ProcessSample.noAnswer
        val readings = rows.map { row -> resolve(context.packageManager, row) }
        return ProcessSample(
            readings = arrange(readings, scope, query, sort, limit),
            tally = tally(readings),
            failed = false
        )
    }

    /**
     * شجرة العمليات الخام، أو `null` إن لم تُقرأ.
     *
     * والفرق بين `null` وقائمة فارغة مقصود: الأولى «لم أستطع»، والثانية «قرأت ولم أجد» — وهي
     * حالة لا تُخفى أيضًا (جهاز بلا عمليات غير ممكن، فالفراغ يُعامل قراءةً فاشلة).
     */
    private fun sampleRows(): List<ProcessRow>? {
        val lines = runCatching {
            Shell.cmd("top -b -n $SAMPLE_COUNT -d $SAMPLE_DELAY_SECONDS -o $COLUMNS").exec().out
        }.getOrNull() ?: return null

        val rows = parseSample(lines)
        return rows.takeIf { it.size >= MIN_ROWS }
    }

    /** صفوف **آخر** عيّنة. والحدّ سطر ترويسة الأعمدة، وغيابه يُسقط كل شيء (لا يُخمَّن). */
    fun parseSample(lines: List<String>): List<ProcessRow> {
        val header = lines.indexOfLast { it.trim().startsWith("PID") }
        if (header < 0) return emptyList()
        return lines.drop(header + 1).mapNotNull(::parseRow)
    }

    private fun parseRow(raw: String): ProcessRow? {
        val line = raw.trim()
        if (line.isEmpty()) return null
        val columns = line.split(SEPARATOR)
        if (columns.size < 4) return null
        val pid = columns[0].toIntOrNull() ?: return null      // ترويسة عابرة أو سطر ملخّص يُرفض برقم
        return ProcessRow(
            pid = pid,
            residentKb = sizeInKb(columns[1]),
            cpuPercent = columns[2].removeSuffix("%").toFloatOrNull() ?: 0f,
            name = columns[3]
        )
    }

    /** ما لا يُقرأ حجمه يُحسب صفرًا فلا يتصدّر الترتيب بلا سبب. */
    private fun sizeInKb(res: String): Long {
        val match = SIZE.find(res.trim()) ?: return 0L
        val value = match.groupValues[1].toDoubleOrNull() ?: return 0L
        val unit = match.groupValues[2].uppercase().ifEmpty { "K" }
        return (value * (UNIT_KB[unit] ?: 1L)).toLong()
    }

    /**
     * ما يعرفه مدير الحزم عن حزمة، محفوظًا بين الدورات.
     *
     * **ولماذا كاش:** الدورة تقرأ الشجرة كلها (مئات الصفوف) وتُعاد كل ثانية أو اثنتين، وسؤال
     * `getApplicationInfo` لكل صفٍّ في كل دورة عمل يُدفع مرّات بلا معلومة جديدة — والنتيجة نفسها
     * إلّا إذا ثُبّتت حزمة أو أُزيلت. فالمحفوظ هو **الموجود** فقط: ما لم يُوجد لا يُحفظ، فيُعاد
     * سؤاله فيظهر تطبيق ثُبّت للتوّ بلا إعادة تشغيل. والقيد المعلن: تسمية تطبيق **حُدّثت** تبقى
     * كما قُرئت حتى إعادة تشغيل العملية — ولا يضرّ: هي الاسم نفسه.
     */
    private val identities = java.util.concurrent.ConcurrentHashMap<String, ProcessReading.Identity>()

    private fun resolve(pm: PackageManager, row: ProcessRow): ProcessReading {
        // ما لا نقطة فيه ليس اسم حزمة أصلًا (`surfaceflinger`) ⇒ ثنائيّ نظام بلا سؤال.
        if (!row.name.contains('.')) {
            return ProcessReading(
                pid = row.pid,
                cpuPercent = row.cpuPercent,
                residentKb = row.residentKb,
                packageName = row.name,
                label = row.name.substringAfterLast('/'),
                isSystem = true
            )
        }
        val known = identities[row.name] ?: lookup(pm, row.name)?.also { identities[row.name] = it }
        return ProcessReading(
            pid = row.pid,
            cpuPercent = row.cpuPercent,
            residentKb = row.residentKb,
            packageName = row.name,
            label = known?.label ?: row.name,
            // مجهول = نظاميّ: ما لا حزمة مثبّتة له لا يراه المستخدم في مُشغّله، والصواب ألّا
            // يُصنّف «تطبيقًا» فيُقصّ من قائمة التطبيقات بلا سبب.
            isSystem = known?.isSystem ?: true
        )
    }

    /** سؤال مدير الحزم مرّة: التسمية وصفة النظام. وغياب الحزمة يُعيد `null` فلا يُحفظ. */
    private fun lookup(pm: PackageManager, pkg: String): ProcessReading.Identity? {
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return null
        val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrNull()
        return ProcessReading.Identity(
            label = label ?: pkg,
            isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        )
    }

    /**
     * التصفية والترتيب والقَصّ — دالّة خالصة تقبل قائمة، فتُقاس بلا جهاز ولا `top`.
     *
     * والترتيب بالاسم يستعمل مُرتِّب اللغة الحالية ([Collator]): التطبيق بثمانٍ وثمانين لغة،
     * ومقارنة نقاط الترميز ترتّب العربية بعيدًا عن موضعها الأبجدي.
     */
    fun arrange(
        readings: List<ProcessReading>,
        scope: ProcessScope,
        query: String,
        sort: ProcessSort,
        limit: Int
    ): List<ProcessReading> {
        val needle = query.trim()
        val byName = Comparator<ProcessReading> { a, b -> collator.compare(a.label, b.label) }
        val ordered = when (sort) {
            ProcessSort.Cpu -> compareByDescending<ProcessReading> { it.cpuPercent }.then(byName)
            ProcessSort.Memory -> compareByDescending<ProcessReading> { it.residentKb }.then(byName)
            ProcessSort.Name -> byName
        }
        return readings
            .asSequence()
            .filter { reading ->
                when (scope) {
                    ProcessScope.All -> true
                    ProcessScope.Apps -> !reading.isSystem
                    ProcessScope.System -> reading.isSystem
                }
            }
            .filter { reading ->
                needle.isEmpty() ||
                    reading.label.contains(needle, ignoreCase = true) ||
                    reading.packageName.contains(needle, ignoreCase = true)
            }
            .sortedWith(ordered)
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    /** التعداد يُحسب على **ما قُرئ** لا على ما عُرض، فالشاشة تقول حجم الجهاز لا حجم قصّها. */
    fun tally(readings: List<ProcessReading>): ProcessTally {
        val apps = readings.count { !it.isSystem }
        return ProcessTally(running = readings.size, apps = apps, system = readings.size - apps)
    }

    private val collator: Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    /** إشارة قتل مباشرة للـPID. والتطبيق قد يُعيد تشغيل نفسه إن كان حيًّا. */
    fun kill(pid: Int): Boolean = runCatching {
        Shell.cmd("kill -9 $pid").exec().isSuccess
    }.getOrDefault(false)

    /** إنهاء كل عمليات حزمة — كما تفعل إعدادات النظام. */
    fun forceStop(packageName: String): Boolean = runCatching {
        Shell.cmd("am force-stop $packageName").exec().isSuccess
    }.getOrDefault(false)
}
