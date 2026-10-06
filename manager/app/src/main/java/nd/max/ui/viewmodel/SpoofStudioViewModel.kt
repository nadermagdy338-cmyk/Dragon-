/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.viewmodel

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import nd.max.core.spoof.AppSpoofProfile
import nd.max.core.spoof.CopgTag
import nd.max.core.spoof.CopgTagRules
import nd.max.core.spoof.SampleDevice
import nd.max.core.spoof.SpoofApplyEngine
import nd.max.core.spoof.SpoofConfigurationRepository
import nd.max.core.spoof.SpoofField
import nd.max.core.spoof.SpoofInheritanceMode
import nd.max.core.spoof.SpoofProfile
import nd.max.core.spoof.SpoofProfileValidation
import nd.max.core.spoof.SpoofWorkspace
import nd.max.core.spoof.planSpoofImport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init { viewModelScope.launch { repository.load(); engine.refresh() } }
    fun acknowledge(pkg: String, accepted: Boolean) { viewModelScope.launch { repository.acknowledge(pkg, accepted) } }
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
    fun applySample(sample: SampleDevice, pkg: String?) = change { latest ->
        val exists = latest.profiles.any { it.id == sample.profileId }
        if (!exists && latest.profiles.size >= 100) return@change latest
        val withProfile = latest.upsert(sample.toProfile())
        if (pkg != null) withProfile.bind(pkg, sample.profileId) else withProfile.copy(globalProfileId = sample.profileId)
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
    fun importWorkspace(incoming: SpoofWorkspace) = change { planSpoofImport(it, incoming).workspace }
    fun refresh() { viewModelScope.launch { engine.refresh() } }
    fun apply(global: Boolean = false, clear: Boolean = false, expectedRevision: Long = configuration.value.revision) {
        viewModelScope.launch { engine.apply(global, clear, expectedRevision) }
    }
}
