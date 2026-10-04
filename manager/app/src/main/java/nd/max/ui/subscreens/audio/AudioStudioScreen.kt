/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * استوديو الصوت — **سطح التحكّم** (`AU-03` + `AU-05` + `AQ-02`…`AQ-09`): ثلاثة تبويبات، وبفرقٍ واحد
 * هو كلّ الفرق: **كل مقبض هنا يكتب فعلًا**، وكل ما لا يُكتب يُسمّى بسببه.
 *
 * ───────────────────── إعادة التصميم (طلب المالك: «تجربة الاستخدام صعبة والواجهة سيئة») ─────────────────────
 * **والعطب كان بنيويًّا لا ذوقًا**، وثلاثة أرقام تقوله:
 *
 * ① **اختيار التبويب كان داخل التمرير.** الشاشة كانت `عمود ← بطاقة محرّك ← شريط تبويبات ← الأقسام`،
 *    وقسم المحرّك وحده ٧٩٣ سطرًا. فمن نزل ليضبط معادلًا لم يبقَ أمامه إلّا أن يعود إلى أعلى الصفحة
 *    ليبدّل تبويبًا — أي أنّ التبويب الآخر كان **يحتاج تمريرًا كاملًا**. والشريط الآن خارج التمرير
 *    ([AudioTabBar] في `Box` فوق الشاشة) فلا يفارق العين أبدًا.
 * ② **أوّل ما يُرى لم يكن له علاقة بالصوت**: عنوانٌ ورقمان ثمّ قائمة. والآن أوّله **بطاقة البطل**
 *    ([AudioHeroCard]): شكلٌ من قراءةٍ حقيقيّة، والجهاز النشط، وحالتا المحرّك والالتقاط، وثلاثة أرقام.
 * ③ **المعادل كان عشرة أشرطة وقراءة**: تضبط نطاقًا في السابعة من عشرة فتفقد صورة المجموع. والمنحنى
 *    الآن **يُسحب** ([AudioEqSection]) فيُكتب نطاقه ويُقرأ — والمنحنى نفسه يُرى في البطل.
 *
 * **والتبويبات الثلاثة حدودٌ لا ذوق:** «مستويات» ما يكتبه الطريق الوحيد القائم (دفقات)، و«محرّك»
 * المؤثّرات (معادل · ديناميكيّ · بسيطة · مازج)، و«نظام» ما يُقاس ويُخزَّن (مصفوفة القدرات · الطبقة
 * النظاميّة · التوجيه · الطيف · البصمات). و«ثلاثة» حدٌّ لا اختيار: الشريط يسع ثلاثةً بأسماءٍ عربيّة
 * غير مبثورة على شاشةٍ ضيّقة.
 *
 * **ولا عرضَ جردٍ هنا:** أجهزة الإخراج والمؤثرات المُعلَنة في «معلومات الجهاز» — فلهذه الشاشة بابٌ واحد
 * إليه (`MaxDeviceInfoShortcut`)، فلا تُعرض الحقيقة الواحدة في موضعين.
 *
 * **والكتابة تمرّ بالـViewModel لا من هنا (ADR-11):** حزمة `ui/` لا تكتب ولا تلمس `AudioEffect`؛
 * الشاشة تنادي `viewModel.*`، وهو يمرّرها إلى المحرّك ← `HardwareControlArbiter`، ثم تُقرأ.
 *
 * **وثلاث حالات لا رابع:** قراءة (شريط حيّ) · غياب قراءة (`—` وشريط مُعطَّل بسببه) · كتابة لم تُطبَّق
 * (سطر بسبب مكتوب، لا `applied=true` كاذبة).
 */
package nd.max.ui.subscreens.audio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.core.audio.AudioBackendId
import nd.max.core.audio.AudioDeviceCatalog
import nd.max.core.audio.AudioEffectKind
import nd.max.core.audio.MaxFxModel
import nd.max.core.audio.AudioStreamCatalog
import nd.max.core.audio.AudioStreamReading
import nd.max.core.audio.AudioWriteOutcome
import nd.max.core.audio.audioBackendRoleOf
import nd.max.core.audio.audioDbFormat
import nd.max.ui.component.MaxDeviceInfoShortcut
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxScreen
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSliderRow
import nd.max.ui.design.MaxSpace
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.viewmodel.AudioStudioUiState
import nd.max.ui.viewmodel.AudioStudioViewModel

@Composable
fun AudioStudioScreen(
    navController: NavController,
    // `hiltViewModel()` لا `viewModel()`: لهذا الـViewModel مُنشئ بوسائط (المحكِّم)، و`viewModel()`
    // بلا مصنع ينادي مُنشئًا بلا وسائط — فيخرج التطبيق لحظة فتح الشاشة (نفس نصّ `GpuStudioScreen`).
    viewModel: AudioStudioViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.state
    val title = stringResource(R.string.max_audio_studio_title)

    LaunchedEffect(Unit) { viewModel.load(context) }

    // تدفّق حيّ: وصل جهازٌ أو نُزع (سمّاعة · بلوتوث) ⇒ تُعاد القراءة. والإلغاء في `onDispose`.
    DisposableEffect(context) {
        val stop = AudioDeviceCatalog.observeDevices(context) { viewModel.load(context) }
        onDispose { stop?.invoke() }
    }

    // والالتقاط يتوقّف عند مغادرة الشاشة — فلا يبقى مؤشّر تسجيل مضاءً ولا معالجةٌ بلا مستخدم.
    DisposableEffect(Unit) { onDispose { viewModel.stopSpectrum() } }

    // وتصدير البصمات يُنسخ من الشاشة (شأن واجهة) — والمخزن يُعطي النصّ في الحالة ثمّ يُفرَّغ.
    val exported = state.profilesExport
    LaunchedEffect(exported) {
        if (exported.isNullOrEmpty()) return@LaunchedEffect
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("maxmanager-audio-profiles", exported))
        viewModel.consumeExport()
    }

    // **والتبويب يُحفظ باسمه لا بكائن التعداد:** `rememberSaveable` يحفظ أنواعًا أساسيّة، فالاسم هو
    // الطريق، والرجوع إلى «مستويات» عند اسمٍ غريب لا يترك الشاشة بلا تبويب.
    // **والتبويب الأوّل هو المحرّك** لا المستويات: ما يُطلب من هذه الشاشة هو المنحنى والأنماط
    // ومؤثّر المصنّع، والمستويات تحكّمٌ عاديّ يُطلب مرّةً في الشهر — فلا يُقدَّم ما يُطلب أقلّ.
    var tabName by rememberSaveable { mutableStateOf(AudioTab.Engine.name) }
    val selected = remember(tabName) { AudioTab.entries.firstOrNull { it.name == tabName } ?: AudioTab.Engine }

    Box(modifier = Modifier.fillMaxSize()) {
        MaxScreen(
            // و`fillMaxSize` صريحة لا ضمنيّة: الشاشة هنا داخل `Box` لأن الشريط العائم يُرسم **فوقها**
            // لا داخلها (ولو كان داخل التمرير لفارق العين مع أوّل تمرير — وهو أصل العطب).
            modifier = Modifier.fillMaxSize(),
            title = title,
            onBack = { navController.popBackStack() },
            subtitle = stringResource(R.string.max_audio_studio_subtitle),
            accentIcon = Icons.AutoMirrored.Rounded.VolumeUp,
            condition = if (state.loading) {
                MaxCondition(
                    kind = MaxConditionKind.Loading,
                    title = title,
                    detail = stringResource(R.string.max_audio_studio_loading),
                )
            } else {
                null
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.section),
            ) {
                // **وحكم آخر مقبض يُعرض في الشاشة لا في تبويب:** المقابض تُكتب من التبويبات الثلاثة
                // (معادل · ديناميكيّ · مازج · توجيه · طبقة نظاميّة)، فإخفاؤه داخل تبويبٍ واحد يعني أن
                // من كتب من تبويبٍ آخر لا يرى حكمَ كتابته أصلًا.
                KnobVerdictNotice(state)

                when (selected) {
                    AudioTab.Levels -> AudioLiveTab(
                        state = state,
                        onCommit = { token, level -> viewModel.apply(context, token, level) },
                        onCaptureHint = { tabName = AudioTab.System.name },
                    )

                    AudioTab.Engine -> AudioEngineTab(state, viewModel, context)

                    AudioTab.System -> AudioSystemTab(navController, state, viewModel, context)
                }

                // ويُحجَز للشريط العائم قدرُه المُعلَن، فلا يقف آخر صفٍّ تحته (الرمز واحد يُشار إليه).
                Spacer(Modifier.height(audioTabBarReserve))
            }
        }

        AudioTabBar(
            selected = selected,
            onSelect = { tabName = it.name },
            engineActive = engineActive(state),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = MaxSpace.gutter, vertical = MaxSpace.sm),
        )
    }
}

/**
 * هل المحرّك يعمل الآن؟ — **سؤالٌ له جوابٌ مقروء لا تقدير**.
 *
 * والجواب: مؤثّرٌ واحد على الأقلّ **مُمكَّن فعلًا** كما قرأته المنصّة (`enabled` تُقرأ بعد كل كتابة،
 * و`null` فيها تعني «لم تُقرأ» فلا تُحسب عملًا)، **أو** التقاطٌ جارٍ يغذّي الشاشة بقراءة. ومع غياب
 * الاثنين تسكن أعمدة تبويب المحرّك — فلا أيقونة تنبض بلا قراءة تسندها.
 */
private fun engineActive(state: AudioStudioUiState): Boolean =
    state.spectrumRunning || state.enabled.values.any { it == true }

/**
 * `LIVE`: **الشريط الحيّ لكل دفق** — والكتابة عند إنهاء السحب لا مع كل بكسل.
 *
 * ودفقٌ لا قراءة له يُعرض مُعطَّلًا **بسببه**، لا بشريط يقبل سحبًا لا يصل. والديسيبل من المنصّة
 * (`getStreamVolumeDb`) — وهي الوحدة الصادقة، إذ الدرجة وحدها لا تعني شيئًا بين جهازين.
 */
@Composable
private fun AudioLiveTab(
    state: AudioStudioUiState,
    onCommit: (String, Int) -> Unit,
    onCaptureHint: () -> Unit,
) {
    // **وبطاقة المستويات الحيّة لم تُحذف بل نُقلت إلى هنا:** كانت أوّلَ بطاقةٍ في الشاشة كلّها،
    // فكان أوّل ما يراه المستخدم رقمًا لا يستطيع كتابته — وهي الآن في تبويبها، فيبدأ المحرّك بالمنحنى.
    AudioHeroCard(state = state, onCaptureHint = onCaptureHint)
    MaxSection(
        title = stringResource(R.string.max_audio_levels_title),
        description = stringResource(R.string.max_audio_levels_description),
        // أوّل قسمٍ في تبويبه فيبدأ مفتوحًا فلا يبدو التبويب فارغًا — ويُطوى بلمسة لتوفير المساحة.
        collapsible = true,
        initiallyExpanded = true,
    ) {
        val verdict = state.lastVerdict
        if (verdict != null) {
            AudioVerdictNotice(
                outcome = verdict.outcome,
                reason = verdict.reason,
                token = state.lastWrittenToken,
            )
        }
        if (state.readings.isEmpty()) {
            AudioNotice(
                title = stringResource(R.string.max_audio_levels_unreadable),
                description = stringResource(R.string.max_audio_levels_unreadable_desc),
                icon = Icons.Rounded.ErrorOutline,
            )
        } else {
            MaxGroup {
                state.readings.forEachIndexed { index, reading ->
                    if (index > 0) MaxGroupDivider()
                    val db = state.volumes.firstOrNull { it.token == reading.token }?.db
                    StreamSlider(
                        reading = reading,
                        db = audioDbFormat(db),
                        onCommit = onCommit,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamSlider(reading: AudioStreamReading, db: String?, onCommit: (String, Int) -> Unit) {
    val live = AudioStreamCatalog.fractionOf(reading.level, reading.maxLevel)
    var dragged by remember(reading.token) { mutableStateOf<Float?>(null) }
    val maxLevel = reading.maxLevel ?: 0

    MaxSliderRow(
        title = stringResource(streamTitle(reading.token)),
        value = dragged ?: live ?: 0f,
        valueText = if (reading.level != null && reading.maxLevel != null) {
            AudioStreamCatalog.levelText(reading.level, reading.maxLevel)
        } else {
            MAX_VALUE_UNAVAILABLE
        },
        subtitle = listOfNotNull(streamSubtitle(reading), db).joinToString("  ·  ").ifBlank { null },
        enabled = live != null && maxLevel > 0,
        lockedReason = if (live == null) stringResource(R.string.max_audio_levels_unreadable) else null,
        // لا كتابة أثناء السحب: الحالة المحلّية وحدها تتحرّك.
        onValueChange = { dragged = it },
        onValueChangeFinished = {
            val fraction = dragged
            dragged = null
            if (fraction == null || reading.level == null || maxLevel <= 0) return@MaxSliderRow
            val target = AudioStreamCatalog.levelOf(fraction = fraction, maxLevel = maxLevel)
            if (target != reading.level) onCommit(reading.token, target)
        },
    )
}

/**
 * `ENGINE`: **المحرّك كاملًا** — معادلٌ بمنحناه، وديناميكيّ بكل معامله، ومؤثّراتٌ بسيطة، ثمّ المازج.
 *
 * وكلٌّ يُعرض **بحالة قدرته**: قسمٌ لم يُفتح مؤثّره يقول سببه من `engineReasons` ولا يختفي — فالاختفاء
 * يُقرأ «لا ميزة»، وهو أسوأ من «ميزةٌ لم تُفتح لأنّ المنصّة رفضتها».
 */
@Composable
private fun AudioEngineTab(
    state: AudioStudioUiState,
    viewModel: AudioStudioViewModel,
    context: Context,
) {
    AudioSoundPresetsSection(
        state = state,
        onPreset = { preset, intensity -> viewModel.applySoundPreset(preset, intensity) },
        onCompare = viewModel::compareSound,
        onDiagnose = viewModel::diagnoseSound,
    )
    MaxSection(
        title = stringResource(R.string.audio_presets_advanced),
        description = stringResource(R.string.audio_presets_advanced_note),
        collapsible = true,
        initiallyExpanded = false,
    ) {
    // Manual edits would invalidate the A/B baseline, so restore the recipe before editing.
    if (state.soundPreset != nd.max.core.audio.AudioSoundPreset.OFF || state.presetBusy ||
        state.presetResult?.failedRestore?.isNotEmpty() == true) {
        androidx.compose.material3.Text(stringResource(R.string.audio_presets_manual_guard))
    } else {
    AudioEngineLeadGroup(backend = state.backend, ladder = state.ladder)
    AudioEqSection(
        snapshot = state.eq,
        reason = state.engineReasons[AudioEffectKind.EQUALIZER],
        onBandCommit = { index, level -> viewModel.writeEqBand(context, index, level) },
        onPreset = { index -> viewModel.useEqPreset(context, index) },
    )
    // **ومؤثّر المصنّع (Dolby Atmos) بعد المنحنى والأنماط مباشرةً** — لا في آخر الشاشة: هو أوّل
    // ما يبحث عنه المستخدم هنا («أين هو Dolby Atmos؟»)، فما يُطلب أكثر يُقدَّم.
    VendorEffectSection(
        verdict = state.vendor?.verdict,
        probe = state.vendorProbe,
        role = audioBackendRoleOf(AudioBackendId.VENDOR_EFFECT, state.backend),
        open = state.vendorOpen,
        dapEnabled = state.vendorDapEnabled,
        effectEnabled = state.vendorEffectEnabled,
        profile = state.vendorProfile,
        values = state.vendorValues,
        knob = state.vendorKnob,
        onOpen = { viewModel.openVendor() },
        onClose = { viewModel.closeVendor() },
        onDapEnabled = { enabled -> viewModel.setVendorDapEnabled(context, enabled) },
        onEffectEnabled = { enabled -> viewModel.setVendorEffectEnabled(context, enabled) },
        onProfile = { profile -> viewModel.setVendorProfile(context, profile) },
        onValue = { param, values -> viewModel.writeVendorDapValues(context, param, values) },
    )
    // Experimental system engine remains available here, not advertised as a universal path.
    MaxFxSection(
        values = state.maxFxValues,
        system = state.system,
        knob = state.maxFxVerdict,
        onParam = { key, raw -> viewModel.writeMaxFxParam(key, raw) },
        // **ويُعلن مجلّد مكتبات التطبيق قبل التثبيت** — وهو ما يجعل «نسخ `libmaxfx.so`» يحدث أصلًا:
        // بلا هذا النداء تُثبَّت الطبقة وحدها ويُقال ذلك في الحكم صراحةً (لا «نجحتْ» ثمّ صفر تغيير).
        onInstall = {
            declareEffectLibrary(viewModel, context)
            viewModel.installSystemLayer(MaxFxModel.installAddition())
        },
    )
    AudioDynamicsSection(
        snapshot = state.dynamics,
        reason = state.engineReasons[AudioEffectKind.DYNAMICS],
        onEqGain = { stage, index, gain -> viewModel.writeEqBandGain(context, stage, index, gain) },
        onEqCutoff = { stage, index, hz -> viewModel.writeEqBandCutoff(context, stage, index, hz) },
        onMbc = { index, param, value -> viewModel.writeMbcParam(context, index, param, value) },
        onLimiter = { param, value -> viewModel.writeLimiterParam(context, param, value) },
        onBalance = { left, right -> viewModel.writeBalance(context, left, right) },
    )
    AudioEffectsSection(
        strengths = state.strengths,
        reasons = state.engineReasons,
        onStrength = { kind, value -> viewModel.writeStrength(context, kind, value) },
    )
    AudioMixerSection(
        snapshot = state.mixer,
        onRequest = { attribute -> viewModel.requestMixer(context, attribute) },
        onClear = { viewModel.clearMixer(context) },
    )
    }
    }
}

/**
 * `SYSTEM`: **التشخيص** أوّلًا — وهو جوهر الصدق — ثمّ الطبقة النظاميّة والتوجيه والطيف والبصمات، ثمّ
 * بابٌ واحد إلى الجرد.
 *
 * ولا مفتاحَ `Global processing` ولا `Auto start` هنا: كلاهما يحتاج نمطًا محفوظًا ليحكم شيئًا،
 * ومفتاحٌ بلا نمط يعد ولا يفعل — وهو ما تمنعه قاعدة هذا المستودع.
 */
@Composable
private fun AudioSystemTab(
    navController: NavController,
    state: AudioStudioUiState,
    viewModel: AudioStudioViewModel,
    context: Context,
) {
    // والمصفوفة تُقاس ولا تُحكي: كانت هنا أربعة صفوف نصّيّة ثابتة تقول الحدود بجملة مكتوبة في
    // الكود، فصارت تُعرض من قياس (`AQ-01`) — ولكل جهاز جوابه، ومعه سببه.
    AudioCapabilitySection(state.capability)
    // والسلّم بعد المصفوفة: المصفوفة تقول «ما يمكن»، وهذا يقول **«ما يقود الآن»** ومن يقود لولاه.
    // ولا يُبنى قبل القياس: بلا قياس تُعرض «قيد القياس» لا قائمةٌ فارغة (ADR-07).
    AudioBackendLadderSection(
        vendor = state.vendor,
        probe = state.vendorProbe,
        ladder = state.ladder,
        backend = state.backend,
        onRemeasure = { viewModel.remeasureBackends(context) },
    )
    // والطبقة النظاميّة بعد المصفوفة: المصفوفة تقول **ما يُكتب**، وهذه تقول **ما سيحدث للجهاز** —
    // وتحتها تحذيرها دائمًا وطريق رجوعها، فلا يُضغط زرّها بغفلة.
    AudioSystemLayerSection(
        snapshot = state.system,
        verdict = state.systemVerdict,
        onInstall = { addition ->
            // وقسم الطبقة العامّ كذلك: الإضافة اليدويّة قد تسمّي مكتبةً أخرى — والخطّة تُبنى من اسمها
            // في سطر الإضافة نفسه ([AudioStudioViewModel.installSystemLayer]) لا من ثابتٍ عندنا.
            declareEffectLibrary(viewModel, context)
            viewModel.installSystemLayer(addition)
        },
        onRemove = { viewModel.removeSystemLayer() },
    )
    AudioRoutingSection(
        snapshot = state.route,
        onRoute = { device -> viewModel.route(context, device) },
        onClear = { viewModel.clearRoute(context) },
    )
    AudioSpectrumSection(
        permissionGranted = state.spectrumPermission,
        running = state.spectrumRunning,
        frame = state.spectrum,
        onStart = { viewModel.startSpectrum() },
        onStop = { viewModel.stopSpectrum() },
        // ولا يُلتقط الجواب بالنيّة: تُعاد قراءة الإذن من المنصّة وتُعاد قياس القدرات معه.
        onPermissionResult = { viewModel.refreshSpectrumPermission(context) },
    )
    // والاسم يُحلّ في نطاق `@Composable` هنا لا داخل الـlambda: `stringResource` لا تُنادى من لامدا
    // عاديّة (وهو ما يمنعه المُصرّف صراحةً) — فالنداء في موضعه، واللامدا تنقل النصّ جاهزًا.
    val defaultProfileName = stringResource(R.string.max_audio_profiles_default_name, state.profiles.size + 1)
    AudioProfilesSection(
        profiles = state.profiles,
        dropped = state.profilesDropped,
        onSave = { viewModel.saveProfile(context, defaultProfileName) },
        onExport = { viewModel.exportProfiles(context) },
        onDelete = { id -> viewModel.deleteProfile(context, id) },
    )
    MaxDeviceInfoShortcut(navController = navController, from = MaxDestination.AudioStudio)
}

/** حكم مقبض المحرّك سطرًا واحدًا — وسببُه مكتوب حين لم يُطبَّق. */
@Composable
private fun KnobVerdictNotice(state: AudioStudioUiState) {
    val verdict = state.knobVerdict ?: return
    val target = knobTargetText(state.knobTarget)
        ?.let { stringResource(it) }
        ?: stringResource(R.string.max_audio_knob_generic)
    val reason = verdict.reason?.let { engineReasonText(it) }
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.max_audio_write_reason_none)
    val text = when (verdict.outcome) {
        AudioWriteOutcome.APPLIED -> stringResource(R.string.max_audio_knob_applied, target)
        AudioWriteOutcome.BLOCKED -> stringResource(R.string.max_audio_knob_blocked, target, reason)
        AudioWriteOutcome.FAILED -> stringResource(R.string.max_audio_knob_failed, target, reason)
        AudioWriteOutcome.NOT_ATTEMPTED -> stringResource(R.string.max_audio_knob_not_attempted, target, reason)
    }
    AudioNotice(
        title = text,
        description = listOfNotNull(verdict.expected, verdict.actual).joinToString("  →  "),
        icon = if (verdict.isApplied) Icons.Rounded.GraphicEq else Icons.Rounded.ErrorOutline,
    )
}

/** حكم الكتابة سطرًا واحدًا — وسببُه مكتوب حين لم تُطبَّق. */
@Composable
private fun AudioVerdictNotice(outcome: AudioWriteOutcome, reason: String?, token: String?) {
    val titleRes = when (outcome) {
        AudioWriteOutcome.APPLIED -> R.string.max_audio_write_applied
        AudioWriteOutcome.BLOCKED -> R.string.max_audio_write_blocked
        AudioWriteOutcome.FAILED -> R.string.max_audio_write_failed
        AudioWriteOutcome.NOT_ATTEMPTED -> R.string.max_audio_write_not_attempted
    }
    val stream = token?.let { stringResource(streamTitle(it)) }
        ?: stringResource(R.string.max_audio_stream_unknown)
    AudioNotice(
        title = stringResource(titleRes, stream),
        // والسبب يأتي من المحكِّم حرفيًّا: `manual-lock` · `apply-not-verified-baseline-restored`.
        description = reason?.let { engineReasonText(it) }
            ?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.max_audio_write_reason_none),
        icon = if (outcome == AudioWriteOutcome.APPLIED) {
            Icons.Rounded.GraphicEq
        } else {
            Icons.Rounded.ErrorOutline
        },
    )
}

@Composable
private fun AudioNotice(title: String, description: String, icon: ImageVector) {
    MaxGroup {
        MaxRow(title = title, subtitle = description, icon = icon)
    }
}

/** ما يُعرض تحت شريط الدفق: كتمه إن أُعلن، وأنّه لا قراءة إن غابت. */
@Composable
private fun streamSubtitle(reading: AudioStreamReading): String? = when {
    reading.level == null || reading.maxLevel == null -> stringResource(R.string.max_audio_levels_unreadable)
    reading.muted == true -> stringResource(R.string.max_audio_stream_muted)
    else -> null
}

/** رمز الدفق ← نصّه في الموارد (غير `@Composable`: جدول ثابت لا يرسم). */
private fun streamTitle(token: String): Int = when (token) {
    "media" -> R.string.max_audio_stream_media
    "call" -> R.string.max_audio_stream_call
    "ring" -> R.string.max_audio_stream_ring
    "notification" -> R.string.max_audio_stream_notification
    "alarm" -> R.string.max_audio_stream_alarm
    "system" -> R.string.max_audio_stream_system
    else -> R.string.max_audio_stream_unknown
}

/**
 * **يُعلن مجلّد مكتبات التطبيق وعمود المعالج** (مخلَصا خطّة المكتبة) قبل ضغط زرّ التثبيت.
 *
 * **ولماذا يُقرأ من `applicationInfo.nativeLibraryDir` لا من مسار مكتوب:** التطبيق يعيش تحت مسارٍ
 * يتغيّر مع كل تحديث (`/data/app/~~<عشوائيّ>/nd.max-<عشوائيّ>/…`)، فمسارٌ مثبَّت عندنا يكون صحيحًا
 * اليوم وخطأً بعد أوّل تحديث — **والمصنّع يقرأه من المنصّة لا من ذاكرتنا**.
 *
 * ولماذا هذه الطبقة (الواجهة) لا الـViewModel: المجلّد والعمود **سياقُ جهاز**، والـViewModel يعمل بلا
 * `android.content` عمدًا، فتبقى الخلفيّة قابلة للقياس بمعزل عن المنصّة.
 */
private fun declareEffectLibrary(viewModel: AudioStudioViewModel, context: Context) {
    val info = context.applicationInfo
    viewModel.declareEffectLibrary(
        // ‏`sourceDir` هو الحزمة التي **فيها** مدخل `lib/<abi>/<اسم>.so` — تُقرأ منها لأنّ
        // `nativeLibraryDir` لا يكون مجلّدًا حقيقيًّا حين `extractNativeLibs=false`.
        apkPath = info.sourceDir ?: info.publicSourceDir,
        nativeLibraryDir = info.nativeLibraryDir,
        // ويُخرج الملفّ إلى مجلّد التطبيق، **والجذر يقرأ ما لا يقرؤه غيره** (`su` بـ`uid 0`).
        stagingDir = context.filesDir?.absolutePath,
        abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
    )
}
