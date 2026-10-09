/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.gamespace

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nd.max.ui.util.AppConfig
import nd.max.ui.util.PerAppHardwareRuntimeStatus
import nd.max.ui.util.PerAppKernelUtil
import nd.max.ui.util.ProfilePresetStore
import nd.max.ui.util.applyGpuCeilingChoice
import nd.max.ui.util.readPerAppHardwareRuntimeStatus

/** مراحل تبديل البروفايل الحراري كما تراها اللوحة: فوريّ ثم مؤكَّد من المحرّك، لا انتظار أعمى. */
enum class ThermalPhase { Idle, Applying, Applied, Pending, Failed }

/**
 * حالة بروفايل الحرارة للّعبة الأمامية.
 *
 * [selected] هو الاختيار المعروض (يُحدَّث **فورًا** عند اللمس)، و[phase] تقول أين وصل التنفيذ فعلًا،
 * و[reason] رمز السبب القادم من المحرّك كما هو (تترجمه الواجهة)، و[targetsMhz] تردد كل بروفايل
 * المتوقّع من قدرة هذا الجهاز تحديدًا — فتُرى قيمة كل اختيار قبل لمسه.
 */
data class ThermalPanelState(
    val selected: String = GameThermalProfiles.DEFAULT,
    val phase: ThermalPhase = ThermalPhase.Idle,
    val reason: String = "",
    val liveMhz: Int? = null,
    val targetsMhz: Map<String, Int> = emptyMap(),
    val requestedAt: Long = 0L,
)

/** حكم المحرّك على الطلب: [phase] نهائية (Applied/Failed) أو Pending حين لم يردّ في المهلة. */
data class ThermalVerdict(val phase: ThermalPhase, val reason: String = "", val liveMhz: Int? = null)

/** نتيجة الحفظ: `Unchanged` تعني أن الاختيار هو القائم أصلًا فلا كتابة ولا انتظار. */
enum class ThermalSave { Changed, Unchanged, Failed }

/**
 * البروفايلات الستة لسقف الـGPU في اللعبة — نفس قائمة شاشة إعدادات التطبيق وبنفس مفتاحها
 * (`gpu_profile`): افتراضي · موفّر · متوازن · ألعاب · أداء · مخصّص.
 *
 * مبدأ هذا الملف: **كاتب واحد**. اللوحة لا تكتب في عقدة GPU؛ تحفظ الاختيار في إعداد التطبيق
 * (عبر [GameProfileRepository] نفسه الذي تكتب به الشاشة) و`AppMonitor` هو من ينفّذ ويقيس —
 * وهو ما يمنعه ADR-11 من أن يتكرّر في موضع ثانٍ. وما تضيفه اللوحة هو **التأكيد**: تقرأ سجل نتيجة
 * المقبض ([readPerAppHardwareRuntimeStatus]) حتى يردّ المحرّك، فيرى المستخدم «طُبِّق · ٦٥٠ MHz» أو
 * سبب الرفض بدل الصمت.
 */
object GameThermalProfiles {

    const val DEFAULT = "default"

    /** اسم المقبض في سجل النتائج — يطابق ما يكتبه `AppMonitor` حرفيًّا. */
    const val KNOB = "gpu_profile"

    const val CONFIRM_TIMEOUT_MS = 4_500L
    private const val POLL_MS = 100L

    /** الترتيب هو ترتيب شاشة الإعدادات: افتراضي أولًا ثم من الأبرد إلى الأعلى ثم المخصّص. */
    val ids: List<String> = listOf(DEFAULT, "power", "balanced", "gaming", "performance", "custom")

    /** قيمة مخزّنة ⟶ معرّف معروف؛ والاسم القديم `powersave` يُرقّى إلى `power`، والمجهول إلى الافتراضي. */
    fun normalize(raw: String?): String {
        val value = raw?.trim()?.lowercase().orEmpty()
        return when {
            value == "powersave" -> "power"
            value in ids -> value
            else -> DEFAULT
        }
    }

    /**
     * الاختيار القائم لإعداد لعبة. تردد صريح محفوظ يعني سقفًا مخصّصًا (هو ما يُنفَّذ فعلًا، لأن
     * المحرّك يقدّمه على البروفايل)، والمفتاح القديم `thermal_profile` يُقرأ حين يكون `gpu_profile`
     * افتراضيًا — كما تفعل الشاشة تمامًا فلا يختلف المعروض هنا عن المعروض هناك.
     */
    fun selectionOf(config: AppConfig?): String {
        if (config == null) return DEFAULT
        val explicit = config.gpu_max_freq.toLongOrNull()
        if (explicit != null && explicit > 0L) return "custom"
        val direct = normalize(config.gpu_profile)
        if (direct != DEFAULT) return direct
        return normalize(config.thermal_profile)
    }

    /**
     * تردد كل بروفايل (MHz) من قدرة الجهاز. النسبة والاختيار من سلّم الترددات يأتيان من
     * [pick] (المنتقي نفسه الذي يستعمله المحرّك) فلا رقمان لسلوك واحد، ويُحقن للاختبار.
     */
    fun targetsMhz(
        frequencies: List<Long>,
        percentOf: (String) -> Int,
        pick: (List<Long>, String, Int) -> Long?,
    ): Map<String, Int> {
        if (frequencies.isEmpty()) return emptyMap()
        return ids.filter { it != DEFAULT }.mapNotNull { id ->
            pick(frequencies, id, percentOf(id))?.let { hz -> id to (hz / 1_000_000L).toInt() }
        }.toMap()
    }

    /** ترددات البروفايلات على هذا الجهاز الآن — قراءة sysfs، فتجري خارج الخيط الرئيسي. */
    suspend fun loadTargets(context: Context): Map<String, Int> = withContext(Dispatchers.IO) {
        runCatching {
            val frequencies = PerAppKernelUtil.readGpuCapabilities().frequencies
            targetsMhz(
                frequencies = frequencies,
                percentOf = { id -> ProfilePresetStore.percentFor(context, id) },
                pick = { list, id, percent -> PerAppKernelUtil.pickProfileFrequency(list, id, percent, null) },
            )
        }.getOrDefault(emptyMap())
    }

    /** الاختيار المحفوظ للّعبة الآن (من المستودع نفسه)، مع تحميله إن لم يكن قد حُمِّل. */
    suspend fun savedSelection(packageName: String): String {
        if (GameProfileRepository.state.value.loading) GameProfileRepository.load()
        return selectionOf(GameProfileRepository.state.value.profiles[packageName])
    }

    /**
     * يحفظ الاختيار في إعداد اللعبة بنفس دلالة الشاشة ([applyGpuCeilingChoice]: اختيار بروفايل
     * يُفرغ التردد الصريح). وما لا إدخال له ويُختار له «افتراضي» لا يُنشأ له إدخال: لا شيء يُنفَّذ،
     * وإنشاء إدخال يجعل التطبيق «مُدارًا» فيتنحّى محرّك MAX AI عنه بلا سبب.
     */
    suspend fun persist(packageName: String, id: String): ThermalSave {
        val target = normalize(id)
        if (GameProfileRepository.state.value.loading) GameProfileRepository.load()
        if (selectionOf(GameProfileRepository.state.value.profiles[packageName]) == target) return ThermalSave.Unchanged
        val saved = GameProfileRepository.update(packageName) { live ->
            applyGpuCeilingChoice(live ?: AppConfig(), KNOB, target)
        }
        return if (saved) ThermalSave.Changed else ThermalSave.Failed
    }

    /**
     * يحكم على سجل نتائج المحرّك: `null` = لم يردّ بعد على **هذا** الطلب.
     *
     * شرطان لقبول السجل: أن يكون أحدث من لحظة الطلب (سجل قديم لا يشهد لطلب جديد)، وأن يحمل
     * سطر المقبض. والتردد الحيّ يُقرأ كما كتبه المحرّك (هرتز) فيُحوَّل إلى MHz.
     */
    fun judge(status: PerAppHardwareRuntimeStatus, sinceMs: Long): ThermalVerdict? {
        if (status.atMs < sinceMs) return null
        val record = status.outcomes.firstOrNull { it.knob == KNOB } ?: return null
        val mhz = record.live.toLongOrNull()?.takeIf { it > 0L }?.let { (it / 1_000_000L).toInt() }
        return if (record.isFailure) {
            ThermalVerdict(ThermalPhase.Failed, record.reason, mhz)
        } else {
            ThermalVerdict(ThermalPhase.Applied, record.reason, mhz)
        }
    }

    /**
     * ينتظر ردّ المحرّك على الطلب. المهلة تنتهي بـ`Pending` لا بفشل: المحرّك قد يكون متأخرًا لا
     * معطّلًا، والاختيار محفوظ على أي حال وسيُنفَّذ عند ردّه.
     */
    suspend fun awaitApplied(
        packageName: String,
        sinceMs: Long,
        timeoutMs: Long = CONFIRM_TIMEOUT_MS,
        read: (String) -> PerAppHardwareRuntimeStatus = ::readPerAppHardwareRuntimeStatus,
        now: () -> Long = System::currentTimeMillis,
    ): ThermalVerdict {
        val deadline = now() + timeoutMs
        while (true) {
            val verdict = withContext(Dispatchers.IO) { runCatching { read(packageName) }.getOrNull() }
                ?.let { judge(it, sinceMs) }
            if (verdict != null) return verdict
            if (now() >= deadline) return ThermalVerdict(ThermalPhase.Pending, "monitor-silent")
            delay(POLL_MS)
        }
    }
}
