/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.viewmodel

import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import nd.max.core.diagnostics.DiagnosticCenter
import nd.max.core.platform.EventLog
import nd.max.core.spoof.AppSpoofProfile
import nd.max.core.spoof.CopgTag
import nd.max.core.spoof.CopgTagRules
import nd.max.core.spoof.SampleDevice
import nd.max.core.spoof.SpoofApplyEngine
import nd.max.core.spoof.SpoofConfigurationRepository
import nd.max.core.spoof.SpoofField
import nd.max.core.spoof.PerAppDeviceModel
import nd.max.core.spoof.SpoofImpersonation
import nd.max.core.spoof.SpoofInheritanceMode
import nd.max.core.spoof.SpoofProfile
import nd.max.core.spoof.SpoofProfileValidation
import nd.max.core.spoof.SpoofWorkspace
import nd.max.core.spoof.planSpoofImport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
// الاسم تاريخي: هذا هو ViewModel تبويب «تزييف» لكل تطبيق (لا استوديو عام بعده).
class SpoofStudioViewModel @Inject constructor(
    private val repository: SpoofConfigurationRepository,
    private val engine: SpoofApplyEngine,
) : ViewModel() {
    val configuration = repository.state
    val acknowledgments = repository.acknowledgments
    val busy = engine.busy
    val engineConfig = engine.engineConfig
    val globalConfig = engine.globalConfig
    val lastWrite = engine.lastWrite
    val lastGlobalWrite = engine.lastGlobalWrite

    /**
     * إصدار الإعدادات الذي نجحت عنده آخر كتابة — فحين يُعدَّل شيء بعده يبطل ادّعاء «مُتحقَّق»
     * (ADR-07: لا حالة معروضة بلا مصدر يقيسها. كان اللواء يقول «مُتحقَّق» عن جهاز اختير بعد الكتابة).
     */
    private val mutableVerifiedRevision = MutableStateFlow<Long?>(null)
    val verifiedRevision = mutableVerifiedRevision.asStateFlow()
    val recovery = engine.recovery
    val recoveryFailed = engine.recoveryFailed
    fun restore(engineId: String) { viewModelScope.launch { engine.restore(engineId) } }
    private val mutableSaved = MutableStateFlow<Boolean?>(null)
    val saved = mutableSaved.asStateFlow()

    /** Observation of this manager process, never certified unspoofed hardware. */
    val observed: Map<SpoofField, String> = mapOf(
        SpoofField.BRAND to Build.BRAND, SpoofField.MODEL to Build.MODEL,
        SpoofField.DEVICE to Build.DEVICE, SpoofField.PRODUCT to Build.PRODUCT,
        SpoofField.FINGERPRINT to Build.FINGERPRINT, SpoofField.SDK_INT to Build.VERSION.SDK_INT.toString(),
    )

    // لا قراءة للجذر هنا: هذا الـViewModel يُنشأ مع كل صفحة إعدادات تطبيق (لتعديلات COPG)، فالقراءة
    // تُطلَب عند فتح تبويب التزييف نفسه (AppSpoofSection) بدل كل فتح لصفحة أي تطبيق.
    init { viewModelScope.launch { repository.load() } }
    fun acknowledge(pkg: String, accepted: Boolean) {
        EventLog.userTriggered(SCREEN, if (accepted) "acknowledge" else "revoke", pkg)
        viewModelScope.launch { repository.acknowledge(pkg, accepted) }
    }
    fun change(transform: (SpoofWorkspace) -> SpoofWorkspace) {
        viewModelScope.launch { mutableSaved.value = repository.update(transform) }
    }
    fun saveProfile(profile: SpoofProfile) {
        if (!SpoofProfileValidation.valid(profile)) { mutableSaved.value = false; return }
        change { it.upsert(profile) }
    }
    fun setGlobal(id: String?) = change { it.copy(globalProfileId = id) }
    fun setMode(pkg: String, mode: SpoofInheritanceMode) = change {
        it.setAppPolicy(pkg, it.appPolicy(pkg).copy(mode = mode))
    }
    fun assign(pkg: String, id: String) = change { it.bind(pkg, id) }

    /**
     * جهاز عيّنة ⇒ ملف كامل الحقول دفعة واحدة، ثم ربطه بتطبيق (`pkg`) أو جعله العالمي (`pkg == null`).
     * المعرّف ثابت لكل جهاز فاختيارُه ثانيةً يحدّث الملف نفسه لا يُكرّره، والحدّ (100) يُحترم قبل الإضافة.
     */
    fun applySample(sample: SampleDevice, pkg: String?) {
        EventLog.userTriggered(SCREEN, "pick_device", pkg?.let { "$it:${sample.key}" } ?: sample.key)
        change { latest ->
            val exists = latest.profiles.any { it.id == sample.profileId }
            if (!exists && latest.profiles.size >= 100) return@change latest
            val withProfile = latest.upsert(sample.toProfile())
            if (pkg != null) withProfile.bind(pkg, sample.profileId) else withProfile.copy(globalProfileId = sample.profileId)
        }
    }

    /**
     * «نسخة مستقلة» لهذا التطبيق: ينسخ ملفّه الحاليّ (المربوط، أو القالب العام إن كان وارثًا) إلى
     * ملفٍّ جديد بمعرّف جديد (‏`copy_<uuid>`) ثم يربط التطبيق بالنسخة فيصير وضعه «مخصّصًا». الأصل
     * المشترك لا يُعدَّل، فتعديل هذه النسخة لاحقًا لا يغيّر أيّ تطبيق آخر — وهذا هو الفرق عن
     * وراثة القالب. الاسم يأتي من الواجهة (نصّ مُعرَّب) لأن الـViewModel لا يملك موارد.
     *
     * التشغيل متسامح عن قصد: بلا مصدر (لا ربط ولا قالب عام) أو عند بلوغ حدّ المئة لا تتغيّر
     * الحالة ولا يُرمى استثناء (`repository.update` نفسه يفشل مغلقًا) — والزرّ في الواجهة لا يظهر أصلًا
     * حين لا مصدر، فالحالتان حزامان لا مسارًا اعتياديًّا.
     */
    fun copyProfileForApp(pkg: String, newName: String) = change { latest ->
        runCatching { latest.copyProfileForApp(pkg, "copy_" + UUID.randomUUID(), newName) }.getOrDefault(latest)
    }

    /** يضيف/يزيل وسمًا لتطبيق — على أحدث حالة تحت القفل. وسم متعارض مع آخر قائم يُستبدل به (الأحدث يفوز). */
    fun setTag(pkg: String, tag: CopgTag, enabled: Boolean) = change { latest ->
        val policy = latest.appPolicy(pkg)
        val rendered = tag.render()
        val kept = policy.tags.filterNot { other ->
            val key = other.substringBefore('=')
            key == tag.key || (enabled && CopgTagRules.conflicts(listOf(other, rendered)).isNotEmpty())
        }.toSet()
        latest.setAppPolicy(pkg, policy.copy(tags = if (enabled) kept + rendered else kept))
    }
    fun setPolicy(pkg: String, policy: AppSpoofProfile) = change { it.setAppPolicy(pkg, policy) }

    /**
     * انتحال معالج لهذا التطبيق (`cpu=<key>`)، أو محوه بـ`null`. يُشترط جهاز فعّال كما يفرضه المحرّك،
     * ويُلغى حظر المعالج المتعارض تلقائيًا (انظر [SpoofImpersonation]).
     */
    fun setCpuForApp(pkg: String, model: String?) = change { latest ->
        if (PerAppDeviceModel.effective(latest, pkg) == null) return@change latest
        val policy = latest.appPolicy(pkg)
        latest.setAppPolicy(pkg, policy.copy(tags = SpoofImpersonation.withCpu(policy.tags, model)))
    }

    /** حظر انتحال المعالج لهذا التطبيق. يُشترط جهاز فعّال، وتفعيله يلغي انتحال المعالج المتعارض. */
    fun setBlockCpuForApp(pkg: String, blocked: Boolean) = change { latest ->
        if (PerAppDeviceModel.effective(latest, pkg) == null) return@change latest
        val policy = latest.appPolicy(pkg)
        latest.setAppPolicy(pkg, policy.copy(tags = SpoofImpersonation.withBlock(policy.tags, blocked)))
    }

    /**
     * جهاز مخصّص يُحفظ ويُربط بهذا التطبيق في **معاملة واحدة**. حفظٌ ثم ربطٌ منفصلان قد يتسابقان، فيُرفض
     * الربط لأن الملف لم يُحفظ بعد. الحدّ (100 ملفًّا) يُحترم للملفات الجديدة فقط.
     */
    fun saveCustomDeviceForApp(pkg: String, profile: SpoofProfile) {
        if (!SpoofProfileValidation.valid(profile)) { mutableSaved.value = false; return }
        change { latest ->
            val exists = latest.profiles.any { it.id == profile.id }
            if (!exists && latest.profiles.size >= 100) return@change latest
            latest.upsert(profile).bind(pkg, profile.id)
        }
    }
    fun importWorkspace(incoming: SpoofWorkspace) = change { planSpoofImport(it, incoming).workspace }
    fun refresh() { viewModelScope.launch { engine.refresh() } }
    fun apply(global: Boolean = false, clear: Boolean = false, expectedRevision: Long = configuration.value.revision) {
        viewModelScope.launch { applyLocked(global, clear, expectedRevision) }
    }

    /**
     * إقرار هذا التطبيق (إن لزم) ثم تجهيزه — **بتسلسل واحد لا فجوة فيه**: الإقرار يُقرأ قبل الكتابة،
     * فحوار الواجهة لا يحتاج أن يجمع مراجعة قديمة قد يكون أي إقرار أو تغيير أزاحها.
     */
    fun applyForApp(packageName: String, acknowledgeNeeded: Boolean) {
        viewModelScope.launch {
            if (acknowledgeNeeded) repository.acknowledge(packageName, true)
            applyLocked(global = false, clear = false, expectedRevision = configuration.value.revision)
        }
    }

    /** حذف ربط هذا التطبيق ثم إعادة كتابة المحرّك بلا هذا التطبيق — بتسلسل واحد أيضًا. */
    fun restoreForApp(packageName: String) {
        viewModelScope.launch {
            if (!repository.update { it.bind(packageName, null) }) return@launch
            applyLocked(global = false, clear = false, expectedRevision = configuration.value.revision)
        }
    }

    private suspend fun applyLocked(global: Boolean, clear: Boolean, expectedRevision: Long) {
        val started = SystemClock.elapsedRealtime()
        engine.apply(global, clear, expectedRevision)
        // إنارة مسار كان أعمى بنيويًّا (تصدير 2026-10-06: صفر أحداث تزييف في السجل): النتيجة
        // تُسجَّل دائمًا عبر DiagnosticCenter (بلا بوابة، تظهر في كل تصدير) وصيغة OP_RESULT
        // المقيسة — فالتصدير القادم يسمّي سبب الرفض بالاسم بدل الصمت.
        val write = if (global) engine.lastGlobalWrite.value else engine.lastWrite.value
        val durationMs = SystemClock.elapsedRealtime() - started
        mutableVerifiedRevision.value = expectedRevision.takeIf { write?.applied == true }
        EventLog.result(SCREEN, "apply", null, write?.applied == true, durationMs)
        when {
            write == null -> DiagnosticCenter.record(SCREEN,
                "apply: no engine outcome (config changed under confirmation)",
                level = DiagnosticCenter.Level.WARN)
            write.applied -> DiagnosticCenter.record(SCREEN,
                "apply ok: engine config written and read back",
                level = DiagnosticCenter.Level.INFO)
            else -> DiagnosticCenter.record(SCREEN, "apply refused: ${write.reason}",
                level = DiagnosticCenter.Level.WARN)
        }
    }

    companion object { private const val SCREEN = "SpoofPerApp" }
}
