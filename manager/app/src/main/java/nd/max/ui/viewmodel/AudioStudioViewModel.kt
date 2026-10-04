/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * ViewModel استوديو الصوت — غلافٌ رقيق، ولا منطق قرار فيه.
 *
 * **ووجودها لسبب واحد:** الكتابة تمرّ بـ`HardwareControlArbiter`، وهو `@Singleton` في هذا التطبيق
 * ولا يُبنى بيد في أيّ موضع غير تمهيد `AppMonitor` (بوّابة `ControlPlaneArchitectureTest`). فالحقن
 * هنا هو الطريق الوحيد الذي لا يفرد للمحكِّم جدولًا ثانيًا — وهو نصّ تعليق `GpuStudioScreen` نفسه.
 *
 * **وهي أيضًا مالك دورة حياة المؤثّرات:** الجلسات تُفتح عند القياس وتُغلق في `onCleared` — فلا يبقى
 * `Equalizer` معلّقًا على الجلسة العامة بعد مغادرة الشاشة يغيّر صوت المستخدم (شرط قبول `AQ-02`).
 * ولا `AudioEffect` يُلمس من هنا مباشرةً: كل نداء يمرّ بـ`AudioEffectBackend`.
 */
package nd.max.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nd.max.core.audio.AudioPresetController
import nd.max.core.audio.AudioPresetApplyResult
import nd.max.core.audio.AudioSoundPreset
import nd.max.core.audio.AudioCapabilityProbe
import nd.max.core.audio.AudioDeviceDescriptor
import nd.max.core.audio.AudioDynamicsSnapshot
import nd.max.core.audio.AudioEffectBackend
import nd.max.core.audio.AudioEffectKind
import nd.max.core.audio.AudioEffectReason
import nd.max.core.audio.AudioEffectSession
import nd.max.core.audio.AudioEqSnapshot
import nd.max.core.audio.AudioFeatureVerdict
import nd.max.core.audio.AudioInventory
import nd.max.core.audio.AudioKnobVerdict
import nd.max.core.audio.MaxFxControlBackend
import nd.max.core.audio.MaxFxModel
import nd.max.core.audio.AudioMixerAttribute
import nd.max.core.audio.AudioMixerBackend
import nd.max.core.audio.AudioMixerSnapshot
import nd.max.core.audio.AudioOutputCapabilities
import nd.max.core.audio.AudioProfileStore
import nd.max.core.audio.AudioProfileV2
import nd.max.core.audio.AudioRouteDevice
import nd.max.core.audio.AudioRouteSnapshot
import nd.max.core.audio.AudioRoutingBackend
import nd.max.core.audio.AudioBackendCandidate
import nd.max.core.audio.AudioBackendMeasurement
import nd.max.core.audio.AudioBackendSelection
import nd.max.core.audio.AudioSpectrumCapture
import nd.max.core.audio.AudioSpectrumFrame
import nd.max.core.audio.DolbyDapParam
import nd.max.core.audio.DolbyDapProtocol
import nd.max.core.audio.VendorAttachProbe
import nd.max.core.audio.VendorAudioBackend
import nd.max.core.audio.VendorAudioReason
import nd.max.core.audio.VendorAudioSnapshot
import nd.max.core.audio.VendorEffectSession
import nd.max.core.audio.audioCapabilityVerdicts
import nd.max.core.audio.measureAudioBackends
import nd.max.core.audio.AudioStreamBackend
import nd.max.core.audio.AudioStreamReading
import nd.max.core.audio.AudioStrengthSnapshot
import nd.max.core.audio.AudioEffectLibraryPlan
import nd.max.core.audio.AudioEffectLibrarySource
import nd.max.core.audio.AudioSystemEffectBackend
import nd.max.core.audio.AudioSystemReason
import nd.max.core.audio.AudioSystemSnapshot
import nd.max.core.audio.AudioVolumeReading
import nd.max.core.audio.AudioWriteVerdict
import nd.max.core.audio.AudioOverlayReason
import nd.max.core.audio.DynamicsParam
import nd.max.core.audio.DynamicsStage
import nd.max.core.audio.audioEffectAdditionOf
import nd.max.core.audio.audioEffectAttachable
import nd.max.core.audio.audioEffectSupport
import nd.max.core.audio.audioKnobNotAttempted
import nd.max.core.audio.pruneProfileForCapabilities
import nd.max.core.audio.slugOf
import nd.max.core.platform.EventLog
import javax.inject.Inject

@HiltViewModel
class AudioStudioViewModel @Inject constructor(
    private val backend: AudioStreamBackend,
    private val effects: AudioEffectBackend,
    private val mixer: AudioMixerBackend,
    private val routing: AudioRoutingBackend,
    private val spectrum: AudioSpectrumCapture,
    private val profileStore: AudioProfileStore,
    private val systemLayer: AudioSystemEffectBackend,
    private val vendorBackend: VendorAudioBackend,
    private val maxFx: MaxFxControlBackend,
) : ViewModel() {

    var state by mutableStateOf(AudioStudioUiState())
        private set

    /** One audit identity across writes; the arbiter owns per-knob intent. */
    private val auditToken = "audio-studio"
    private val writes = AtomicLong(0L)

    /** Last measured library plan; null means unknown, not absent. */
    private var libraryPlan: AudioEffectLibraryPlan? = null

    /** Library source is interpreted only by AudioEffectLibrarySource. */
    private var librarySource = AudioEffectLibrarySource()

    /** الجلسات المفتوحة — مفتاحها النوع، وتُغلق كلّها في `onCleared`. */
    private val sessions = linkedMapOf<AudioEffectKind, AudioEffectSession>()
    private val presetController = AudioPresetController(effects)
    private val presetLock = Mutex()

    fun applySoundPreset(preset: AudioSoundPreset, intensity: Int = state.presetIntensity) =
        changeSound(preset, intensity, null)

    fun compareSound(original: Boolean) = changeSound(state.soundPreset, state.presetIntensity, original)

    private fun changeSound(preset: AudioSoundPreset, intensity: Int, original: Boolean?) {
        if (state.presetBusy || state.presetDiagnosticBusy || (original != null && preset == AudioSoundPreset.OFF)) return
        val current = sessions.toMap()
        state = state.copy(presetBusy = true, presetDiagnostics = null)
        viewModelScope.launch {
            try {
                presetLock.withLock {
                    val result = withContext(Dispatchers.IO) {
                        val record: (String, AudioKnobVerdict, String) -> Unit = ::recordAudioOp
                        try {
                            if (original == null) presetController.apply(preset, intensity, current, ::nextToken, record)
                            else presetController.compare(original, current, ::nextToken, record)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { AudioPresetApplyResult(false, AudioEffectReason.PARAM_UNREADABLE) }
                    }
                    updatePresetReadings(result)
                    if (result.applied) state = state.copy(soundPreset = preset,
                        presetIntensity = intensity, comparingOriginal = original ?: false)
                }
            } finally { state = state.copy(presetBusy = false) }
        }
    }

    fun diagnoseSound() {
        if (state.presetBusy || state.presetDiagnosticBusy) return
        val current = sessions.toMap()
        val original = state.comparingOriginal
        state = state.copy(presetDiagnosticBusy = true, presetDiagnostics = null)
        viewModelScope.launch {
            try {
                val measured = withContext(Dispatchers.IO) {
                    presetLock.withLock { presetController.diagnose(current, original) }
                }
                state = state.copy(presetDiagnostics = measured)
            } finally { state = state.copy(presetDiagnosticBusy = false) }
        }
    }

    private suspend fun updatePresetReadings(result: AudioPresetApplyResult) {
        val current = sessions.toMap()
        val eq = withContext(Dispatchers.IO) {
            current[AudioEffectKind.EQUALIZER]?.let { effects.readEq(it) }
        }
        val strengths = withContext(Dispatchers.IO) {
            current.values.mapNotNull { session -> effects.readStrength(session)?.let { session.kind to it } }.toMap()
        }
        val enabled = withContext(Dispatchers.IO) { current.mapValues { it.value.enabled } }
        state = state.copy(eq = eq, strengths = strengths, enabled = enabled, presetResult = result)
    }

    /** Vendor session opens only on request and is released in onCleared. */
    private var vendorSession: VendorEffectSession? = null

    /** قراءة كاملة — عند الدخول، وعند كل تغيّر جهاز إخراج. لا استثناء يفلت. */
    fun load(context: Context) {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                runCatching { AudioInventory.read(context) }.getOrNull()
            }
            val readings = withContext(Dispatchers.IO) {
                runCatching { backend.readings(context) }.getOrDefault(emptyList())
            }
            // Derive capability and backend selection from the same measured abilities.
            val abilities = withContext(Dispatchers.IO) {
                runCatching { AudioCapabilityProbe.abilities(context) }.getOrNull()
            }
            val capability = abilities?.let { audioCapabilityVerdicts(it) }
            openEngine(context, capability)
            val volumes = withContext(Dispatchers.IO) {
                runCatching { routing.volumes(context) }.getOrDefault(emptyList())
            }
            val mixerSnapshot = withContext(Dispatchers.IO) {
                runCatching { mixer.snapshot(context) }.getOrNull()
            }
            val routeSnapshot = withContext(Dispatchers.IO) {
                runCatching { routing.snapshot(context) }.getOrNull()
            }
            val permission = withContext(Dispatchers.IO) {
                runCatching { AudioCapabilityProbe.abilities(context).spectrumPermissionGranted }
                    .getOrDefault(false)
            }
            val profiles = withContext(Dispatchers.IO) {
                runCatching { profileStore.load(context) }.getOrNull()
            }
            // Passive system-layer read; only explicit install/remove requests root.
            val systemSnapshot = withContext(Dispatchers.IO) {
                runCatching { systemLayer.snapshot(library = libraryPlan) }.getOrNull()
            }
            // Read MaxFx properties passively; missing values are not zero.
            val maxFxValues = withContext(Dispatchers.IO) {
                runCatching { maxFx.readAll() }.getOrDefault(emptyMap<String, String>())
            }
            // والسلّم يُقاس بعد الطبقة النظاميّة: رِفادته تُقرأ من مثبَّتٍ/قابلٍ للتثبيت لا من تخمين.
            val backends = measureAudioBackends(abilities, systemSnapshot)
            state = state.copy(
                loading = false,
                presetDiagnostics = null,
                output = snapshot?.output,
                activeDevice = snapshot?.devices?.firstOrNull { it.isSink },
                readings = readings,
                capability = capability,
                volumes = volumes,
                mixer = mixerSnapshot,
                route = routeSnapshot,
                spectrumPermission = permission,
                profiles = profiles?.profiles ?: emptyList(),
                system = systemSnapshot,
                maxFxValues = maxFxValues,
                vendor = backends.vendor,
                vendorProbe = backends.probe,
                ladder = backends.ladder,
                backend = backends.selection,
            )
        }
    }

    // ──────────────────────────── مؤثّر المصنّع والسلّم (تكملة ٢٣٠) ────────────────────────────

    /** Re-measure through the core backend; attach probing may briefly touch the effect. */
    fun remeasureBackends(context: Context) {
        viewModelScope.launch {
            // ولا يُلمس `loading`: هو لقطة الفتح الأولى، وإعادةُ القياس الرمزية لا تُعيد الشاشة
            // إلى «قيد القراءة» — فالقيم القديمة تبقى معلنة حتى تُبدَّل بالجديدة.
            val abilities = withContext(Dispatchers.IO) {
                runCatching { AudioCapabilityProbe.abilities(context) }.getOrNull()
            }
            val system = withContext(Dispatchers.IO) {
                runCatching { systemLayer.snapshot(library = libraryPlan) }.getOrNull()
            }
            val measured = measureAudioBackends(abilities, system ?: state.system)
            state = state.copy(
                capability = abilities?.let { audioCapabilityVerdicts(it) } ?: state.capability,
                system = system ?: state.system,
                vendor = measured.vendor,
                vendorProbe = measured.probe,
                ladder = measured.ladder,
                backend = measured.selection,
            )
        }
    }

    /** Read declared vendor parameters; omit unreadable values. */
    private fun readVendorValues(session: VendorEffectSession, profile: Int): Map<DolbyDapParam, IntArray> =
        DolbyDapParam.entries.mapNotNull { param ->
            runCatching { vendorBackend.readDapValues(session, param, profile) }.getOrNull()
                ?.let { values -> param to values }
        }.toMap()

    /** Open only the discovered vendor implementation, then read both enabled states. */
    fun openVendor() {
        val uuid = state.vendor?.detected?.firstOrNull()?.uuid ?: return
        viewModelScope.launch {
            val opened = withContext(Dispatchers.IO) {
                runCatching { vendorBackend.open(uuid) }.getOrNull()
            }
            val session = opened?.session
            if (session == null) {
                val verdict = audioKnobNotAttempted(
                    reason = opened?.reason ?: VendorAudioReason.SESSION_CLOSED,
                    expected = uuid,
                )
                recordAudioOp("vendor_open", verdict)
                state = state.copy(vendorOpen = false, vendorKnob = verdict)
                return@launch
            }
            vendorSession = session
            // والقراءة كلّها بعد الفتح: **ما يُقرأ هو ما يُعرض**، وقيمةٌ لم تُقرأ تبقى `null` ويُقال
            // ذلك — فلا يُعرض مفتاحٌ على «مطفأ» ظنًّا لأنّنا لم نقرأه.
            val enable = withContext(Dispatchers.IO) {
                runCatching { vendorBackend.readIntParam(session, DolbyDapProtocol.ENABLE_PARAM) }.getOrNull()
            }
            val profile = withContext(Dispatchers.IO) {
                runCatching { vendorBackend.readIntParam(session, DolbyDapProtocol.PROFILE_PARAM) }.getOrNull()
            }
            val effectEnabled = withContext(Dispatchers.IO) {
                runCatching { vendorBackend.enabled(session) }.getOrNull()
            }
            val values = withContext(Dispatchers.IO) { readVendorValues(session, profile ?: 0) }
            state = state.copy(
                vendorOpen = true,
                vendorDapEnabled = enable,
                vendorProfile = profile,
                vendorEffectEnabled = effectEnabled,
                vendorValues = values,
                vendorKnob = null,
            )
        }
    }

    /** يُغلق جلسة المصنّع — **يُنادى من الزرّ ومن `onCleared`**، وإلّا بقي أثرنا على الصوت. */
    fun closeVendor() {
        val session = vendorSession ?: return
        vendorSession = null
        runCatching { vendorBackend.close(session) }
        state = state.copy(vendorOpen = false)
    }

    /** تمكين/تعطيل **معالج Dolby** (`EFFECT_PARAM_ENABLE`) — ثمّ يُقرأ من المادّة لا من الطلب. */
    fun setVendorDapEnabled(context: Context, enabled: Boolean) {
        val session = vendorSession ?: return
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) {
                vendorBackend.writeDapEnabled(session, enabled, token)
            }
            recordAudioOp("vendor_dap_enable", verdict, token)
            val live = withContext(Dispatchers.IO) {
                runCatching { vendorBackend.readIntParam(session, DolbyDapProtocol.ENABLE_PARAM) }.getOrNull()
            }
            state = state.copy(vendorKnob = verdict, vendorDapEnabled = live ?: state.vendorDapEnabled)
        }
    }

    /** تمكين/تعطيل المؤثّر نفسه — مقبضٌ ثانٍ لا يُخلط بالأوّل. */
    fun setVendorEffectEnabled(context: Context, enabled: Boolean) {
        val session = vendorSession ?: return
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) {
                vendorBackend.setEffectEnabled(session, enabled, token)
            }
            recordAudioOp("vendor_effect_enable", verdict, token)
            val live = withContext(Dispatchers.IO) {
                runCatching { vendorBackend.enabled(session) }.getOrNull()
            }
            state = state.copy(
                vendorKnob = verdict,
                vendorEffectEnabled = live ?: state.vendorEffectEnabled,
            )
        }
    }

    /** يختار الملفّ الشخصيّ للمعالج — ويُقرأ بعده من المادّة. */
    fun setVendorProfile(context: Context, profile: Int) {
        val session = vendorSession ?: return
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) {
                vendorBackend.writeProfile(session, profile, token)
            }
            recordAudioOp("vendor_profile", verdict, token)
            val live = withContext(Dispatchers.IO) {
                runCatching { vendorBackend.readIntParam(session, DolbyDapProtocol.PROFILE_PARAM) }.getOrNull()
            }
            state = state.copy(vendorKnob = verdict, vendorProfile = live ?: state.vendorProfile)
        }
    }

    /**
     * يكتب معامل معالج ملفّ شخصيّ (فيزيائيٌّ · عدّاد · عشرون نطاق معادل) — **ويُقرأ بعده حرفيًّا**.
     *
     * والقيم تُطلب بطولها المُعلَن بالضبط؛ وطلبٌ مخالف يُردّ بسببٍ من الطبقة الصافية لا بكتابة مشوّهة.
     */
    fun writeVendorDapValues(context: Context, param: DolbyDapParam, values: IntArray) {
        val session = vendorSession ?: return
        val profile = state.vendorProfile ?: 0
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) {
                vendorBackend.writeDapValues(session, param, values, profile, token)
            }
            recordAudioOp("vendor_dap_${param.name.lowercase()}", verdict, token)
            val live = withContext(Dispatchers.IO) {
                runCatching { vendorBackend.readDapValues(session, param, profile) }.getOrNull()
            }
            state = state.copy(
                vendorKnob = verdict,
                vendorValues = if (live != null) state.vendorValues + (param to live) else state.vendorValues,
            )
        }
    }

    // ──────────────────────────── ‏AQ-09: طبقة مؤثّرات النظام ────────────────────────────

    /**
     * يقيس حالة الطبقة النظاميّة — **قراءة سلبيّة**: لا `su` ولا كتابة، ولا نافذة صلاحية لمجرّد
     * فتح قسم. ويُنادى عند الدخول وبعد كلّ كتابة، فلا تُعرض حالة قديمة بعد تغيّر.
     */
    fun loadSystemLayer() {
        viewModelScope.launch {
            val measured = withContext(Dispatchers.IO) {
                runCatching { systemLayer.snapshot(library = libraryPlan) }.getOrNull()
            }
            state = state.copy(system = measured)
        }
    }

    /**
     * يثبّت الطبقة من **سطر الإضافة** (`المكتبة|المسار|المؤثّر|uuid[|أجهزة]`).
     *
     * **والتحليل في الطبقة النقيّة لا هنا** (`audioEffectAdditionOf`) — فالرفض بسببٍ مفهوم لا بسطرٍ
     * غامض؛ وثمّ تثبت الحقيقة بالقياس بعد الكتابة ([loadSystemLayer] بعده).
     */
    fun installSystemLayer(addition: String) {
        viewModelScope.launch {
            val parsed = audioEffectAdditionOf(addition)
            if (parsed == null) {
                val rejected = audioKnobNotAttempted(AudioOverlayReason.INVALID_ADDITION, addition)
                recordAudioOp("system_layer_install", rejected)
                state = state.copy(systemVerdict = rejected)
                return@launch
            }
            // والخطّة تُبنى **من سطر الإضافة نفسه** (`parsed.libraryPath` — وهو اسم ملفّ مجرَّد كما
            // يشترطه المصنع) لا من اسمٍ مثبَّت هنا: فقسم الواجهة يسمح بإضافةٍ يدويّة لمكتبةٍ أخرى،
            // وثابتٌ مكتوب عندنا كان سيحمل مسارًا لا يخصّها.
            //
            // **والمصدر يُقاس بالإخراج الفعليّ** لا بتوقّع: `nativeLibraryDir` ليس مجلّدًا حقيقيًّا حين
            // `extractNativeLibs=false`، فتُقرأ المكتبة من حزمة التطبيق نفسها وتُخرج إلى مجلّدنا.
            libraryPlan = withContext(Dispatchers.IO) {
                runCatching { librarySource.planFor(parsed.libraryPath) }.getOrNull()
            }
            if (libraryPlan == null) {
                // **والرفض هنا لا في الخلفيّة:** بلا مصدرٍ مقيس لا شيء يُنسخ، فتُثبّت طبقةٌ بلا مكتبة
                // ويُقرأ «نجحت» ثمّ لا يُسمع فرق — وهو العطب نفسه الذي جئنا نُزيله.
                val refused = audioKnobNotAttempted(
                    AudioSystemReason.LIBRARY_NOT_SHIPPED,
                    parsed.libraryPath,
                )
                recordAudioOp("system_layer_install", refused)
                state = state.copy(systemVerdict = refused)
                return@launch
            }
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) {
                systemLayer.install(parsed, token, library = libraryPlan)
            }
            recordAudioOp("system_layer_install", verdict, token)
            val measured = withContext(Dispatchers.IO) {
                runCatching { systemLayer.snapshot(library = libraryPlan) }.getOrNull()
            }
            state = state.copy(systemVerdict = verdict, system = measured ?: state.system)
        }
    }

    /**
     * **يُعلن خطّة المكتبة قبل التثبيت** — من مسار مكتبات التطبيق (`nativeLibraryDir`) وعمود المعالج.
     *
     * **ولماذا من الطبقة العليا لا من الخلفيّة:** اسم الملفّ يأتي من سطر الإضافة نفسه (`audioEffectAdditionOf`)
     * و[`MaxFxModel.LIBRARY_FILE`]، ومجلّد مكتبات التطبيق **لا يعرفه إلّا من يملك سياقًا** — فالخلفيّة
     * تبقى بلا `android.content`، والخطّة تُبنى في نواةٍ نقيّة تُقاس (`audioEffectLibraryPlan`).
     *
     * ونداؤها قبل التثبيت هو ما يجعل النسخ يحدث أصلًا؛ وبلا نداء تُثبَّت الطبقة وحدها ويُقال ذلك صراحةً
     * في الحكم (`effect-library-not-shipped-in-app`) بدل أن تُقرأ «نجحت» ولا يُسمع فرق.
     */
    fun declareEffectLibrary(
        apkPath: String?,
        nativeLibraryDir: String?,
        stagingDir: String?,
        abi: String,
    ) {
        librarySource = AudioEffectLibrarySource(apkPath, nativeLibraryDir, stagingDir, abi)
    }

    /** يلغي الطبقة بحذف مجلّد وحدتها — وهو الرجوع الكامل: ملفّ النظام لم يُلمَس قطّ. */
    fun removeSystemLayer() {
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) { systemLayer.remove(token) }
            recordAudioOp("system_layer_remove", verdict, token)
            val measured = withContext(Dispatchers.IO) {
                runCatching { systemLayer.snapshot() }.getOrNull()
            }
            state = state.copy(systemVerdict = verdict, system = measured ?: state.system)
        }
    }

    // ──────────────────────────── ‏MaxFx: مؤثّرنا النظاميّ (تكملة ٢٣٥) ────────────────────────────

    /**
     * يكتب معامل MaxFx بقيمةٍ خامّة — **يقصّها العقد ويصيغها** ثمّ يمرّ بالمحكِّم، و**يقرأ بعده**
     * من الخاصية: فالحكم على ما صار لا على ما طُلب.
     *
     * **وما لا يُقاس هنا ويُقال:** القراءة بعدها قراءةُ الخاصية، والخاصية تصل المؤثّر خلال ~250ms؛
     * وأمّا «هل تغيّر الصوت فعلًا» ف**يحتاج جهازًا** (قاعدة `ADR-58`-٩ لا تتغيّر).
     */
    fun writeMaxFxParam(key: String, raw: Double) {
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) { maxFx.write(key, raw, token) }
            recordAudioOp("${MaxFxModel.OP_PREFIX}_$key", verdict, token)
            val values = withContext(Dispatchers.IO) {
                runCatching { maxFx.readAll() }.getOrDefault(emptyMap<String, String>())
            }
            state = state.copy(maxFxValues = values, maxFxVerdict = verdict)
        }
    }

    // ──────────────────────────────── ‏AQ-02: فتح المحرّك وقراءته ────────────────────────────────

    /**
     * يفتح مؤثّرًا **لكل ميزة قالت القدرات إنها قابلة** — ولا يُفتح ما لم يُعلنه الجهاز.
     *
     * **ولا يُخفى سبب الامتناع:** كل نوع لم يُفتح يُكتب سببه في `engineReasons`، فيقرأ المستخدم
     * «جهازك لا يُعلن هذا المؤثّر» أو «الرفض يحتاج جلسة نملكها» بدل قسمٍ فارغ بلا تفسير.
     */
    private suspend fun openEngine(context: Context, verdicts: List<AudioFeatureVerdict>?) {
        AudioEffectKind.entries.forEach { kind ->
            if (sessions.containsKey(kind)) return@forEach
            if (!audioEffectAttachable(verdicts, kind)) {
                val support = audioEffectSupport(verdicts, kind)
                if (support != null) {
                    // وسبب الامتناع يُكتب في السجلّ أيضًا لا في الشاشة وحدها — فقسمٌ «معطَّل» على
                    // الجهاز هو أوّل ما يُبلَّغ عنه، ولا جواب بلا سطر يحمل رمز سببه (تكملة ٢٣٣).
                    recordAudioOp(
                        "engine_open_${kind.token}",
                        audioKnobNotAttempted(reason = support.reason, expected = kind.token),
                    )
                    state = state.copy(engineReasons = state.engineReasons + (kind to support.reason))
                }
                return@forEach
            }
            val opened = withContext(Dispatchers.IO) { runCatching { effects.open(context, kind) }.getOrNull() }
                ?: run {
                    recordAudioOp(
                        "engine_open_${kind.token}",
                        audioKnobNotAttempted(reason = AudioEffectReason.ATTACH_REFUSED, expected = kind.token),
                    )
                    state = state.copy(engineReasons = state.engineReasons + (kind to AudioEffectReason.ATTACH_REFUSED))
                    return@forEach
                }
            val session = opened.session
            if (session == null) {
                val reason = opened.reason ?: AudioEffectReason.ATTACH_REFUSED
                recordAudioOp(
                    "engine_open_${kind.token}",
                    audioKnobNotAttempted(reason = reason, expected = kind.token),
                )
                state = state.copy(
                    engineReasons = state.engineReasons + (kind to reason),
                )
                return@forEach
            }
            sessions[kind] = session
            // والقراءة الأولى على `IO` كذلك: مؤثّرٌ يُنشأ للتوّ لا يُقرأ من الخيط الرئيسيّ.
            val openedStrength = if (kind.isStrengthKind()) {
                withContext(Dispatchers.IO) { runCatching { effects.readStrength(session) }.getOrNull() }
            } else {
                null
            }
            state = state.copy(
                enabled = state.enabled + (kind to session.enabled),
                strengths = if (openedStrength != null) {
                    state.strengths + (kind to openedStrength)
                } else {
                    state.strengths
                },
            )
            when (kind) {
                AudioEffectKind.EQUALIZER -> {
                    val eq = withContext(Dispatchers.IO) { runCatching { effects.readEq(session) }.getOrNull() }
                    if (eq != null) state = state.copy(eq = eq)
                }
                AudioEffectKind.DYNAMICS -> {
                    val dynamics = withContext(Dispatchers.IO) {
                        runCatching { effects.readDynamics(session) }.getOrNull()
                    }
                    if (dynamics != null) state = state.copy(dynamics = dynamics)
                }
                else -> Unit
            }
        }
    }

    /** تمكين/تعطيل مؤثّر — ثمّ يُقرأ من المنصّة لا من الطلب. */
    fun setEffectEnabled(context: Context, kind: AudioEffectKind, enabled: Boolean) {
        val session = sessions[kind] ?: return
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) {
                effects.setEnabled(session, enabled, token)
            }
            recordAudioOp("enable_${kind.token}", verdict, token)
            refresh(kind, context, verdict, kind.token)
        }
    }

    // ──────────────────────────────────── ‏AQ-03: المعادل ────────────────────────────────────

    fun writeEqBand(context: Context, index: Int, levelMb: Int) {
        val session = sessions[AudioEffectKind.EQUALIZER] ?: return
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) {
                effects.writeEqBand(session, index, levelMb, token)
            }
            recordAudioOp("eq_band_$index", verdict, token)
            refresh(AudioEffectKind.EQUALIZER, context, verdict, "eq_band_$index")
        }
    }

    fun useEqPreset(context: Context, index: Int) {
        val session = sessions[AudioEffectKind.EQUALIZER] ?: return
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) { effects.useEqPreset(session, index, token) }
            recordAudioOp("eq_preset", verdict, token)
            refresh(AudioEffectKind.EQUALIZER, context, verdict, "eq_preset")
        }
    }

    // ─────────────────────────────────── ‏AQ-04: الديناميكيّ ───────────────────────────────────

    fun writeEqBandGain(context: Context, stage: DynamicsStage, index: Int, gainDb: Float) {
        val session = sessions[AudioEffectKind.DYNAMICS] ?: return
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) {
                effects.writeEqBandGain(session, stage, index, gainDb, token)
            }
            recordAudioOp("${stage.token}_eq_$index", verdict, token)
            refresh(AudioEffectKind.DYNAMICS, context, verdict, "${stage.token}_eq_$index")
        }
    }

    fun writeEqBandCutoff(context: Context, stage: DynamicsStage, index: Int, cutoffHz: Float) {
        val session = sessions[AudioEffectKind.DYNAMICS] ?: return
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) {
                effects.writeEqBandCutoff(session, stage, index, cutoffHz, token)
            }
            recordAudioOp("${stage.token}_cutoff_$index", verdict, token)
            refresh(AudioEffectKind.DYNAMICS, context, verdict, "${stage.token}_cutoff_$index")
        }
    }

    fun writeMbcParam(context: Context, index: Int, param: DynamicsParam, value: Float) {
        val session = sessions[AudioEffectKind.DYNAMICS] ?: return
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) {
                effects.writeMbcParam(session, index, param, value, token)
            }
            recordAudioOp("mbc_${param.token}_$index", verdict, token)
            refresh(AudioEffectKind.DYNAMICS, context, verdict, "mbc_${param.token}_$index")
        }
    }

    fun writeLimiterParam(context: Context, param: DynamicsParam, value: Float) {
        val session = sessions[AudioEffectKind.DYNAMICS] ?: return
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) {
                effects.writeLimiterParam(session, param, value, token)
            }
            recordAudioOp("limiter_${param.token}", verdict, token)
            refresh(AudioEffectKind.DYNAMICS, context, verdict, "limiter_${param.token}")
        }
    }

    /** التوازن: يكتب دخل القناتين معًا — **ولا يرفع الصوت** (انظر `dynamicsBalanceGains`). */
    fun writeBalance(context: Context, leftDb: Float, rightDb: Float) {
        val session = sessions[AudioEffectKind.DYNAMICS] ?: return
        viewModelScope.launch {
            val leftToken = nextToken()
            val left = withContext(Dispatchers.IO) { effects.writeInputGain(session, 0, leftDb, leftToken) }
            val rightToken = nextToken()
            val right = withContext(Dispatchers.IO) { effects.writeInputGain(session, 1, rightDb, rightToken) }
            // وكلّ قناة بسطرها ورمز تدقيقها: خطّان يُقارنان بخطّي المحكِّم، لا سطرٌ واحد يمزجهما.
            recordAudioOp("input_gain_0", left, leftToken)
            recordAudioOp("input_gain_1", right, rightToken)
            val verdict = if (left.isApplied) right else left
            refresh(AudioEffectKind.DYNAMICS, context, verdict, "input_gain")
        }
    }

    // ───────────────────────────────── ‏AQ-05: المؤثّرات البسيطة ─────────────────────────────────

    fun writeStrength(context: Context, kind: AudioEffectKind, value: Int) {
        val session = sessions[kind] ?: return
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) { effects.writeStrength(session, value, token) }
            recordAudioOp(kind.token, verdict, token)
            refresh(kind, context, verdict, kind.token)
        }
    }

    // ──────────────────────────────────── ‏AQ-06: المازج ────────────────────────────────────

    fun requestMixer(context: Context, target: AudioMixerAttribute) {
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) { mixer.request(context, target, token) }
            recordAudioOp("mixer_request", verdict, token)
            val refreshed = withContext(Dispatchers.IO) { runCatching { mixer.snapshot(context) }.getOrNull() }
            state = state.copy(
                mixer = refreshed ?: state.mixer,
                knobVerdict = verdict,
                knobTarget = "mixer",
            )
        }
    }

    fun clearMixer(context: Context) {
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) { mixer.clear(context, token) }
            recordAudioOp("mixer_clear", verdict, token)
            val refreshed = withContext(Dispatchers.IO) { runCatching { mixer.snapshot(context) }.getOrNull() }
            state = state.copy(
                mixer = refreshed ?: state.mixer,
                knobVerdict = verdict,
                knobTarget = "mixer",
            )
        }
    }

    // ─────────────────────────────────── ‏AQ-06: التوجيه ───────────────────────────────────

    fun route(context: Context, device: AudioRouteDevice) {
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) { routing.route(context, device, token) }
            recordAudioOp("route", verdict, token)
            val refreshed = withContext(Dispatchers.IO) { runCatching { routing.snapshot(context) }.getOrNull() }
            state = state.copy(
                route = refreshed ?: state.route,
                knobVerdict = verdict,
                knobTarget = "route",
            )
        }
    }

    fun clearRoute(context: Context) {
        viewModelScope.launch {
            val token = nextToken()
            val verdict = withContext(Dispatchers.IO) { routing.clearRoute(context, token) }
            recordAudioOp("route_clear", verdict, token)
            val refreshed = withContext(Dispatchers.IO) { runCatching { routing.snapshot(context) }.getOrNull() }
            state = state.copy(
                route = refreshed ?: state.route,
                knobVerdict = verdict,
                knobTarget = "route",
            )
        }
    }

    // ──────────────────────────────────── ‏AQ-08: الطيف ────────────────────────────────────

    /**
     * يبدأ الالتقاط — **والشاشة لا تطلبه من تلقاء نفسها** (أمر المالك: الطلب قرارُ المستخدم من داخل
     * قسمه بشرحه). وقبل الإذن **لا محاولة**، ويُقال السبب من القدرات.
     */
    fun startSpectrum() {
        if (state.spectrumRunning) return
        val started = spectrum.start(state.spectrumPermission) { frame ->
            state = state.copy(spectrum = frame)
        }
        state = state.copy(spectrumRunning = started != null)
    }

    fun stopSpectrum() {
        spectrum.stop()
        state = state.copy(spectrumRunning = false, spectrum = null)
    }

    /**
     * ‏يعيد قراءة الإذن **وقياس قدرات الطيف معه** بعد جواب المستخدم.
     *
     * **ولماذا لا يُلتقط `granted` من النداء وحده:** ما يهمّ هو ما تقوله المنصّة الآن، لا ما ظنّته
     * نافذة الإذن؛ والمصفوفة تُعاد معه لأنّ الطيف صفٌّ منها وحكمه يتبدّل بالإذن — فلا تبقى مصفوفة
     * تقول «يحتاج الإذن» والرسم يعمل، ولا العكس.
     */
    fun refreshSpectrumPermission(context: Context) {
        viewModelScope.launch {
            val granted = withContext(Dispatchers.IO) {
                runCatching { AudioCapabilityProbe.abilities(context).spectrumPermissionGranted }
                    .getOrDefault(false)
            }
            val capability = withContext(Dispatchers.IO) {
                runCatching { AudioCapabilityProbe.verdicts(context) }.getOrNull()
            }
            state = state.copy(
                spectrumPermission = granted,
                capability = capability ?: state.capability,
            )
        }
    }

    // ─────────────────────────────────── ‏AQ-07: البصمات ───────────────────────────────────

    /** Save measured values, pruning unavailable effects and reporting dropped entries. */
    fun saveProfile(context: Context, name: String) {
        val entries = LinkedHashMap<String, String>()
        state.readings.forEach { reading ->
            val level = reading.level ?: return@forEach
            entries["audio_stream:${reading.token}"] = level.toString()
        }
        state.eq?.bands?.forEach { band ->
            val level = band.levelMb ?: return@forEach
            entries["audio_effect:equalizer:eq_band_${band.index}"] = level.toString()
        }
        state.strengths.forEach { (kind, snapshot) ->
            val value = snapshot.value ?: return@forEach
            entries["audio_effect:${kind.token}:strength"] = value.toString()
        }
        val attachable = AudioEffectKind.entries
            .filter { audioEffectAttachable(state.capability, it) }
            .map { it.token }
            .toSet()
        val profile = AudioProfileV2(
            id = "${slugOf(name)}-${System.currentTimeMillis().toString(36)}",
            name = name.ifBlank { slugOf(name) },
            deviceFingerprint = profileStore.deviceFingerprint(),
            entries = entries,
        )
        val pruned = pruneProfileForCapabilities(profile, attachable)
        viewModelScope.launch {
            val updated = state.profiles + pruned.profile
            val saved = withContext(Dispatchers.IO) { profileStore.save(context, updated) }
            val verdict = audioKnobNotAttempted(
                reason = if (saved) "profile-saved" else "profile-save-failed",
                expected = pruned.profile.name,
            )
            recordAudioOp("profile_save", verdict)
            state = state.copy(
                profiles = if (saved) updated else state.profiles,
                profilesDropped = pruned.droppedKeys,
                knobVerdict = verdict,
                knobTarget = "profile",
            )
        }
    }

    /** Export the stored format; clipboard ownership stays in the screen. */
    fun exportProfiles(context: Context) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { profileStore.export(context) }.getOrNull() }
            state = state.copy(profilesExport = text)
        }
    }

    /** تُفرّغ نصّ التصدير بعد نسخه — فلا يبقى في الحالة نصٌّ نُسخ فعلًا. */
    fun consumeExport() {
        state = state.copy(profilesExport = null)
    }

    /** يحذف بصمة — والحذف كتابةٌ على الملفّ، فلا يُعلن نجاحه إلا إن كُتب. */
    fun deleteProfile(context: Context, id: String) {
        viewModelScope.launch {
            val updated = state.profiles.filterNot { it.id == id }
            val saved = withContext(Dispatchers.IO) { profileStore.save(context, updated) }
            val verdict = audioKnobNotAttempted(
                reason = if (saved) "profile-deleted" else "profile-save-failed",
                expected = id,
            )
            recordAudioOp("profile_delete", verdict)
            state = state.copy(
                profiles = if (saved) updated else state.profiles,
                knobVerdict = verdict,
                knobTarget = "profile",
            )
        }
    }

    // ──────────────────────────────────── ‏AQ-03: المستويات ────────────────────────────────────

    /** Commit stream level on release, then read back the actual platform level. */
    fun apply(context: Context, token: String, level: Int) {
        viewModelScope.launch {
            val audit = nextToken()
            val verdict = withContext(Dispatchers.IO) {
                backend.setLevel(
                    context = context,
                    token = token,
                    targetLevel = level,
                    auditToken = audit,
                )
            }
            recordStreamOp(token, level, verdict, audit)
            val refreshed = withContext(Dispatchers.IO) {
                runCatching { backend.readings(context) }.getOrNull()
            }
            state = state.copy(
                readings = refreshed ?: state.readings,
                lastVerdict = verdict,
                lastWrittenToken = token,
                knobVerdict = null,
                knobTarget = null,
            )
        }
    }

    /** يُنسى الحكم بعد أن يُقرأ — فلا يبقى سطر «طُبِّق» وقراءةُ الشاشة تغيّرت تحته. */
    fun clearVerdict() {
        state = state.copy(lastVerdict = null, lastWrittenToken = null, knobVerdict = null, knobTarget = null)
    }

    /** Release every effect and stop capture when the navigation ViewModel is cleared. */
    override fun onCleared() {
        spectrum.stop()
        sessions.values.forEach { runCatching { effects.close(it) } }
        sessions.clear()
        // وجلسة المصنّع مثلها: تُغلق صريحةً — فلا يبقى مؤثّرٌ معلّق على المزج العامّ بعدنا.
        vendorSession?.let { runCatching { vendorBackend.close(it) } }
        vendorSession = null
        super.onCleared()
    }

    /** يقرأ حالة المؤثّر بعد الكتابة ثمّ يكتب الحكم — القراءة هي الحقيقة، والحكم يوصفها. */
    private suspend fun refresh(
        kind: AudioEffectKind,
        context: Context,
        verdict: AudioKnobVerdict,
        target: String,
    ) {
        val session = sessions[kind]
        val eq = if (kind == AudioEffectKind.EQUALIZER && session != null) {
            withContext(Dispatchers.IO) { runCatching { effects.readEq(session) }.getOrNull() }
        } else {
            null
        }
        val dynamics = if (kind == AudioEffectKind.DYNAMICS && session != null) {
            withContext(Dispatchers.IO) { runCatching { effects.readDynamics(session) }.getOrNull() }
        } else {
            null
        }
        val strength = if (session != null && kind.isStrengthKind()) {
            withContext(Dispatchers.IO) { runCatching { effects.readStrength(session) }.getOrNull() }
        } else {
            null
        }
        state = state.copy(
            eq = eq ?: state.eq,
            dynamics = dynamics ?: state.dynamics,
            enabled = session?.let { state.enabled + (kind to it.enabled) } ?: state.enabled,
            strengths = if (strength != null) state.strengths + (kind to strength) else state.strengths,
            knobVerdict = verdict,
            knobTarget = target,
        )
    }

    /** Log each measured outcome with the arbiter token and untranslated reason. */
    private fun recordAudioOp(target: String, verdict: AudioKnobVerdict, token: String? = null) {
        EventLog.audioOp(
            target = target,
            outcome = verdict.outcome.name.lowercase(),
            token = token,
            reason = verdict.reason,
            expected = verdict.expected,
            actual = verdict.actual,
        )
    }

    /** نظير [recordAudioOp] لكتابات الدفقات: حكمُها نوعٌ آخر (`AudioWriteVerdict`) وقراءتُها `liveLevel`. */
    private fun recordStreamOp(streamToken: String, level: Int, verdict: AudioWriteVerdict, token: String) {
        EventLog.audioOp(
            target = "stream_$streamToken",
            outcome = verdict.outcome.name.lowercase(),
            token = token,
            reason = verdict.reason,
            expected = level.toString(),
            actual = verdict.liveLevel?.toString(),
        )
    }

    private fun nextToken(): String = "$auditToken:${writes.incrementAndGet()}"

    /** هل لهذا النوع «قوّة» تُقرأ؟ — الأنواع الأربعة التي لها `strength`/`gain`/`preset`. */
    private fun AudioEffectKind.isStrengthKind(): Boolean = when (this) {
        AudioEffectKind.BASS_BOOST,
        AudioEffectKind.VIRTUALIZER,
        AudioEffectKind.LOUDNESS,
        AudioEffectKind.PRESET_REVERB,
        -> true
        else -> false
    }
}
