/*
 * Copyright (C) 2026-2027 Zexshia
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package nd.max.core.platform

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * لوحة الأداء — الطبقة البيانية. **لا واجهة في هذا الملف**: تُقرأ منه اللوحة العائمة داخل
 * الخدمة وشاشة الإعدادات معًا.
 *
 * ### القاعدة الحاكمة: الغائب `null` لا صفرًا
 *
 * قارئ الإطارات (`FpsMonitorUtil`) يُعيد `0f` حين **لا يجيب** المصدر — وقيمة صفر إطار/ثانية
 * ليست قراءة ممكنة على جهاز شاشته مضاءة. فالصفر هنا يُترجم **غيابًا** لا رقمًا، ويصل الواجهة
 * شرطةً (`MAX_VALUE_UNAVAILABLE`) لا صفرًا معقولًا. وهذه هي نفس القاعدة التي تحكم `status_unknown`
 * في طبقة البيانات وألوان التنبيه في طبقة التصميم: **الأداة لا تدّعي قياسًا لم تقسه.**
 *
 * ### قارئ واحد لا قارئان
 *
 * اللوحة العائمة وشاشة الإعدادات تحتاجان الأرقام نفسها في الوقت نفسه (معاينة حيّة + تراكب)،
 * وقراءتان متوازيتان تعنيان استدعاءَي shell كل ثانية لنفس القيمة — وهو ثمن على جهاز يُقاس
 * أداؤه أصلًا. فالمُشغِّل واحد ([HudSampler]) وللمالك واحد ([HudSampler.start] `owner`): من
 * يبدأ آخرًا يملكه، ومن ينتهي لا يوقف إلا ما يملكه.
 */
/**
 * شكل اللوحة العائمة. أربعة أشكال، ولكلّ واحد موضعه:
 *
 * - [Strip] شريط رقيق في سطر واحد — للعب الأفقيّ حيث العرض ثمين والطول مفقود.
 * - [Pane] لوح بصفوف: التسمية يسارًا والقيمة يمينًا — للقراءة المتأنّية والمقارنة.
 * - [Ring] حلقة إطارات: رقم كبير داخل قوس — للإطارات وحدها حين تكون هي السؤال.
 * - [Badge] رقاقة بمبدأ تراكبات الحاسوب: رقم واحد كبير ووحدته، **وزمن الإطار** تحته، وبقيّة
 *   الحقول سطرًا مضغوطًا. وهو الفرق بين «قياس يُقرأ» و«قياس يُلاحَظ»: من عاش على MangoHud
 *   أو Fraps لا يقرأ الرقم فقط بل زمن الإطار، لأن ٨٧ إطارًا و٥٨ إطارًا قد يبدوان متقاربين في
 *   العدّ وهما متباعدان في الإحساس — وزمن الإطار هو المقياس الذي يكشف تلك الفجوة.
 *
 * **والترتيب هنا هو ترتيب الاختيار في الشاشة** (`ordinal`)، فإضافة شكل جديد **تُلحَق بآخره**
 * ولا تُدسّ في وسطه: الشكل المحفوظ في تفضيلات المستخدم يُقرأ بالاسم لا بالرقم، لكن الترتيب
 * الظاهر في المنتقي يبقى ما رآه سابقًا فلا يتحرّك تحت إصبعه.
 *
 * وهو في طبقة البيانات لا في ملف الرسم، كما [FpsReadMode] بالضبط: يقرأه المخزن ويكتبه،
 * ويرسمه المُصيِّر، فلا هو تفصيل رسم ولا هو نصّ مُخزَّن.
 */
enum class HudForm { Strip, Pane, Ring, Badge }

/** ترتيب الحقول. و[Stack] هو أصل كل شيء: هو ما يقرأه المستخدم رأسيًّا في العربية. */
enum class HudArrangement { Line, Stack }

/** الحلقة **مفردة** فلا صفّ ولا عمود لها — يُجاب هنا مرة واحدة بدل تحكّم صامت في الشاشة. */
fun HudForm.acceptsArrangement(): Boolean = this != HudForm.Ring

enum class HudField {
    /** إطارات/ثانية من المصدر المُختار. */
    Frames,

    /** حِمل المعالج %. */
    Cpu,

    /** الذاكرة المستعملة. */
    Ram,

    /** قدرة البطارية الواطية. */
    Power,

    /** حرارة البطارية. */
    Heat,

    /** مُصيّر الرسوم للتطبيق الأمامي (GL/Vulkan). */
    Renderer
}

/**
 * قراءة واحدة. كل حقل قابل للغياب على حِدة: قد يجيب مصدر الإطارات ولا يجيب مصدر القدرة.
 */
data class HudReading(
    val frames: Float? = null,
    val cpu: Int? = null,
    val ramMb: Int? = null,
    val watt: Float? = null,
    val heat: Float? = null,
    val renderer: String? = null
) {
    /** هل أجاب مصدر الإطارات؟ هو المصدر الوحيد الذي يفشل بحسب الوضع المُختار. */
    val framesAnswered: Boolean get() = frames != null

    fun valueOf(field: HudField): Any? = when (field) {
        HudField.Frames -> frames
        HudField.Cpu -> cpu
        HudField.Ram -> ramMb
        HudField.Power -> watt
        HudField.Heat -> heat
        HudField.Renderer -> renderer
    }
}

/**
 * ما تعرفه الواجهة الآن: آخر قراءة، ومن قرأها، وهل الجلسة تُسجّل.
 *
 * @param reading `null` قبل أوّل قراءة — لا مصفوفة أصفار.
 * @param owner من يقرأ الآن (`shopfront` للخدمة و`screen` للشاشة) — يُعرض للمستخدم لأنّ
 *   «لماذا الأرقام تتوقّف حين أُغلق الشاشة؟» سؤال حقيقي، وجوابه أنّ القارئ كان الشاشة.
 */
data class HudSnapshot(
    val reading: HudReading? = null,
    val sampledAtMs: Long = 0L,
    val owner: String? = null,
    val recording: Boolean = false,
    val samples: Int = 0,
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
    /** آخر الإطارات بترتيب الزمن — منحنى اللوحة يُقصّ منها على المدى المُختار. */
    val framesHistory: List<Float> = emptyList()
) {
    companion object {
        const val DEFAULT_INTERVAL_MS = 1000L
        val Idle = HudSnapshot()
    }
}

/** الاسم الذي يُسجّله قارئ اللوحة العائمة، والمقابل في شاشة الإعدادات. */
const val HUD_OWNER_OVERLAY = "overlay"
const val HUD_OWNER_SCREEN = "screen"

/**
 * القناة بين القارئ والواجهتين. صغيرة عن قصد: قيمة واحدة تُستبدل كل دورة، لا سجلّ أحداث.
 */
object HudLive {

    /** أطول مدى يعرضه المُصيِّر ([HudSnapshot.framesHistory] يحمل هذا القدر والباقي يُسقَط). */
    const val HISTORY_CAPACITY = 40

    private val mutable = MutableStateFlow(HudSnapshot.Idle)
    private val history = ArrayDeque<Float>()
    val snapshot: StateFlow<HudSnapshot> = mutable.asStateFlow()

    internal fun publish(reading: HudReading, owner: String, intervalMs: Long) {
        reading.frames?.let { value ->
            history.addLast(value)
            while (history.size > HISTORY_CAPACITY) history.removeFirst()
        }
        val previous = mutable.value
        mutable.value = previous.copy(
            reading = reading,
            sampledAtMs = System.currentTimeMillis(),
            owner = owner,
            recording = HudRecorder.isLive,
            samples = previous.samples + 1,
            intervalMs = intervalMs,
            framesHistory = history.toList()
        )
    }

    internal fun markOwner(owner: String?) {
        mutable.value = mutable.value.copy(owner = owner)
    }

    internal fun markRecording(live: Boolean) {
        mutable.value = mutable.value.copy(recording = live)
    }

    internal fun reset() {
        history.clear()
        mutable.value = HudSnapshot.Idle
    }
}

/**
 * جلسة قياس: حلقة مقيّدة السعة من القراءات.
 *
 * والسقف مقصود — تسجيل ساعة بفاصل ثانية = ٣٦٠٠ عيّنة، وفي الذاكرة لا على القرص. وما يسقط
 * بالسقف هو **الأقدم**، ويُقال للمستخدم في الإحصاء أنّ ما يراه على المدى الأخير لا الجلسة كلها.
 */
class HudJournal(private val capacity: Int = 900) {

    data class Sample(val atMs: Long, val reading: HudReading)

    private val ring = ArrayDeque<Sample>()
    private var firstAtMs = 0L
    private var lastAtMs = 0L
    private var dropped = 0

    val size: Int get() = ring.size

    /** كم عيّنة سقطت بالسقف — يُعرض ولا يُسكَت عنه. */
    val droppedSamples: Int get() = dropped

    fun record(reading: HudReading, atMs: Long) {
        if (ring.isEmpty()) firstAtMs = atMs
        lastAtMs = atMs
        ring.addLast(Sample(atMs, reading))
        while (ring.size > capacity) {
            ring.removeFirst()
            dropped++
            firstAtMs = ring.first().atMs
        }
    }

    fun samples(): List<Sample> = ring.toList()

    fun clear() {
        ring.clear()
        dropped = 0
        firstAtMs = 0L
        lastAtMs = 0L
    }

    /**
     * الإحصاء. كل حقل يبقى `null` حتى تصل أوّل قراءة به — فمتوسط بلا عيّنات ليس صفرًا.
     *
     * والمدى يُحسب من **العيّنات الباقية** لا من زمن الجلسة، فما سقط لم يُحسب.
     */
    fun tally(): HudTally {
        val all = ring.toList()
        val frames = all.mapNotNull { it.reading.frames?.takeIf { v -> v > 0f } }
        val heats = all.mapNotNull { it.reading.heat?.takeIf { v -> v > 0f } }
        val watts = all.mapNotNull { it.reading.watt?.takeIf { v -> v > 0f } }
        return HudTally(
            samples = all.size,
            droppedSamples = dropped,
            spanMs = if (all.size > 1) lastAtMs - firstAtMs else 0L,
            framesAverage = frames.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            framesLow = frames.minOrNull(),
            framesHigh = frames.maxOrNull(),
            heatPeak = heats.maxOrNull(),
            powerAverage = watts.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        )
    }
}

/**
 * نتيجة الجلسة. القيم الغائبة `null` — لا أصفار تُقرأ أرقامًا.
 *
 * @param droppedSamples يوضّح أنّ المدى المعروض حدّه سقف الذاكرة لا بداية الجلسة.
 */
data class HudTally(
    val samples: Int = 0,
    val droppedSamples: Int = 0,
    val spanMs: Long = 0L,
    val framesAverage: Float? = null,
    val framesLow: Float? = null,
    val framesHigh: Float? = null,
    val heatPeak: Float? = null,
    val powerAverage: Float? = null
) {
    val isEmpty: Boolean get() = samples == 0
}

/** أعمدة CSV — ترتيب واحد يُكتب في الترويسة ويُقرأ آليًّا. */
private const val CSV_HEADER = "elapsed_ms,frames,cpu_percent,ram_mb,watt,battery_c,renderer"

/**
 * الجلسة نصًّا. والخلايا الغائبة **فارغة** لا `0`: جدول يُقرأ بجدول بيانات يجب أن يفرّق
 * بين «لم يُقَس» و«قِيس فكان صفرًا».
 */
fun hudSessionCsv(samples: List<HudJournal.Sample>): String {
    if (samples.isEmpty()) return CSV_HEADER + "\n"
    val origin = samples.first().atMs
    val body = samples.joinToString(separator = "\n") { (atMs, r) ->
        listOf(
            (atMs - origin).toString(),
            r.frames?.let { "%.1f".format(it) } ?: "",
            r.cpu?.toString() ?: "",
            r.ramMb?.toString() ?: "",
            r.watt?.let { "%.2f".format(it) } ?: "",
            r.heat?.let { "%.1f".format(it) } ?: "",
            r.renderer?.replace(',', ' ') ?: ""
        ).joinToString(",")
    }
    return CSV_HEADER + "\n" + body + "\n"
}

/**
 * حائز الجلسة: واحد للتطبيق كلّه، يكتب فيه المُشغِّل ويقرأ منه القارئان.
 *
 * و`stop()` **لا يُفرغ**: الجلسة تنتهي ونتيجتها تبقى حتى تُصدَّر أو تُحذف. وإفراغها عند
 * الإيقاف كان يُتلف ما سُجّل من أجل قراءته.
 */
object HudRecorder {
    private const val CAPACITY = 900

    private var journal: HudJournal? = null
    private var live = false

    val isLive: Boolean get() = live
    val hasSession: Boolean get() = journal != null

    fun start() {
        journal = HudJournal(CAPACITY)
        live = true
        HudLive.markRecording(true)
    }

    fun stop() {
        live = false
        HudLive.markRecording(false)
    }

    fun clear() {
        journal = null
        live = false
        HudLive.markRecording(false)
    }

    fun feed(reading: HudReading, atMs: Long) {
        if (live) journal?.record(reading, atMs)
    }

    fun tally(): HudTally? = journal?.takeIf { it.size > 0 }?.tally()

    fun csv(): String? = journal?.takeIf { it.size > 0 }?.let { hudSessionCsv(it.samples()) }
}

/**
 * المُشغِّل الواحد.
 *
 * **والفاصل زمنيّ لا وعدي:** يُقاس النوم بطرح زمن القراءة من الثانية، فلا تتراكم القراءات
 * حين تبطؤ قراءة واحدة (وهو ما كان يُطيل الدورة صامتًا في النسخة السابقة).
 */
object HudSampler {

    private var job: Job? = null
    private var owner: String? = null
    private var appContext: Context? = null

    val currentOwner: String? get() = owner

    fun start(
        context: Context,
        owner: String,
        scope: CoroutineScope,
        fields: () -> Set<HudField>,
        intervalMs: Long = HudSnapshot.DEFAULT_INTERVAL_MS
    ) {
        appContext = context.applicationContext
        FpsMonitorUtil.init(context)
        // قارئ واحد: من يبدأ آخرًا يملك، فلا حلقتان على جهاز واحد.
        job?.cancel()
        this.owner = owner
        HudLive.markOwner(owner)
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val began = System.currentTimeMillis()
                val reading = read(appContext!!, fields())
                if (isActive) {
                    HudLive.publish(reading, owner, intervalMs)
                    HudRecorder.feed(reading, System.currentTimeMillis())
                }
                delay((intervalMs - (System.currentTimeMillis() - began)).coerceAtLeast(150L))
            }
        }
    }

    /** لا يوقف إلا ما يملكه: شاشة تُغلق لا تُسقط تراكبًا يعمل. */
    fun stop(owner: String) {
        if (this.owner != owner) return
        job?.cancel()
        job = null
        this.owner = null
        HudLive.markOwner(null)
    }

    private fun read(context: Context, fields: Set<HudField>): HudReading {
        if (fields.isEmpty()) return HudReading()
        val wantsFrames = HudField.Frames in fields
        val renderer = if (HudField.Renderer in fields) {
            runCatching { FpsMonitorUtil.getCurrentRenderer(FpsMonitorUtil.getForegroundPackage()) }
                .getOrNull()?.takeIf { it.isNotBlank() && it != "FPS" }
        } else {
            null
        }
        return HudReading(
            // `0` من القارئ تعني «لم يُجِب» لا «صفر إطار» — فتُترجم غيابًا.
            frames = if (wantsFrames) runCatching { FpsMonitorUtil.getFps() }.getOrNull().positive() else null,
            cpu = if (HudField.Cpu in fields) {
                runCatching { FpsMonitorUtil.getCpuLoad() }.getOrNull()?.takeIf { it > 0 }
            } else {
                null
            },
            ramMb = if (HudField.Ram in fields) {
                runCatching { FpsMonitorUtil.getRamInfo(context).usedMb }.getOrNull()?.takeIf { it > 0 }
            } else {
                null
            },
            watt = if (HudField.Power in fields) {
                runCatching { FpsMonitorUtil.getPowerWatt() }.getOrNull().positive()
            } else {
                null
            },
            heat = if (HudField.Heat in fields) {
                runCatching { FpsMonitorUtil.getBatteryTemp(context) }.getOrNull().positive()
            } else {
                null
            },
            renderer = renderer
        )
    }
}

private fun Float?.positive(): Float? = this?.takeIf { it.isFinite() && it > 0f }
