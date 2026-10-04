/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * مصفوفة قدرات المحرّك — **حالة كل ميزة وسببها** (`AQ-01`)، وهي التي كان المالك يطلبها بالنصّ:
 * «توضيح حالة كل ميزة: مدعومة · قابلة للكتابة · للقراءة فقط · تحتاج Adapter · غير متاحة… وتعطيل
 * الميزات غير المدعومة بشكل واضح بدل إظهارها وكأنها تعمل».
 *
 * **وهي اليوم بديلُ الصفوف النصّيّة الأربعة القديمة:** كانت تُحكي الحدود بجملة مكتوبة في الكود (ولا
 * تتغيّر بجهاز)، وصارت تُعرض **من قياس** (`core/audio/AudioCapabilityProbe`) — فجهازٌ بلا معادل يقول
 * «غير متاحة · غير مُعلَنة»، وجهازٌ به معادل ويرفض المزج العامّ يقول «تحتاج محوّلًا · الرفض يحتاج جلسة
 * نملكها». وهذا هو الفرق بين شاشة تقول الحقيقة وشاشة تقول ما نتوقّعه.
 *
 * **ولا قرار هنا:** لا حكم ولا ترتيب — الطبقة الصافية تحكم وترتّب، وهذه الشاشة ترسم فقط (ADR-11).
 */
package nd.max.ui.subscreens.audio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import nd.max.R
import nd.max.core.audio.AudioCapabilityReason
import nd.max.core.audio.AudioFeature
import nd.max.core.audio.AudioFeatureGroup
import nd.max.core.audio.AudioFeatureVerdict
import nd.max.core.audio.AudioSupport
import nd.max.core.audio.audioCapabilitySummary
import nd.max.ui.design.MaxCapsule
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxTone

/**
 * المصفوفة كاملة سطرًا لكل ميزة.
 *
 * @param verdicts `null` = **لم تُقس بعد** ⇒ يُعرض «قيد القراءة» — ولا تُعرض قائمة فارغة، لأنّ
 *   القائمة الفارغة تُقرأ «لا ميزات» وهذا كذبٌ على قارئ لم يقرأ شيئًا (ADR-07).
 */
@Composable
fun AudioCapabilitySection(verdicts: List<AudioFeatureVerdict>?) {
    MaxSection(
        title = stringResource(R.string.max_audio_diag_title),
        description = stringResource(R.string.max_audio_caps_description),
        // المصفوفة هي سبب فتح التبويب فيبدأ القسم مفتوحًا — والتفصيل يُطوى بلمسة.
        collapsible = true,
        initiallyExpanded = true,
    ) {
        if (verdicts == null) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_audio_diag_title),
                    subtitle = stringResource(R.string.max_audio_caps_reading),
                    icon = Icons.Rounded.GraphicEq,
                )
            }
            return@MaxSection
        }

        // ‏والحصيلة أربعة أعداد **مقروءة من الأحكام** لا معدودة في الشاشة — فترقيمٌ يُكتب هنا
        // ينحرف عن المصفوفة أوّل تغيير، وترقيمٌ يُحسب لا ينحرف. وتُعرض **سطرًا واحدًا** لا أربع
        // كبسولات متجاورة: الأربع على عرض ضيّق تنكسر أو تُقتطع، والواحدة تُقرأ كاملةً دائمًا.
        val summary = audioCapabilitySummary(verdicts)
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_audio_caps_summary_title),
                subtitle = stringResource(
                    R.string.max_audio_caps_summary,
                    summary.writable,
                    summary.needsAdapter,
                    summary.unavailable,
                    summary.unknown,
                ),
                icon = Icons.Rounded.GraphicEq,
                iconTone = MaxTone.Accent,
            )
        }

        // والكتل: ترتيبها **ثابت من [`AudioFeatureGroup`] لا من ترتيب وصول القياس** — فشاشةٌ تتبدّل
        // كتلها بين قياسٍ وقياسٍ لا يُقارَن فيها شيء. وقائمةٌ من ٤٣ صفًّا مصطفتةً لا تُقرأ؛ والمقسَّمة
        // بسؤالها تُقرأ: ما نقرؤه من المنصّة · ما تُعلنه المنصّة · ما نفّذناه · ما ليس عندنا بعد · المرفوض.
        AudioFeatureGroup.entries.forEach { group ->
            val rows = verdicts.filter { it.feature.group == group }
            if (rows.isEmpty()) return@forEach
            MaxGroup {
                MaxRow(
                    title = stringResource(groupTitle(group)),
                    subtitle = stringResource(R.string.max_audio_caps_group_count, rows.size),
                    icon = groupIcon(group),
                    iconTone = MaxTone.Neutral,
                )
                rows.forEach { verdict ->
                    MaxGroupDivider()
                    CapabilityRow(verdict)
                }
            }
        }
    }
}

/** عنوان الكتلة — **كل مجموعةٍ ولها صياغتها**، ولا كتلةَ بلا عنوان. */
private fun groupTitle(group: AudioFeatureGroup): Int = when (group) {
    AudioFeatureGroup.GLOBAL -> R.string.max_audio_caps_group_global
    AudioFeatureGroup.PLATFORM -> R.string.max_audio_caps_group_platform
    AudioFeatureGroup.SESSION_EFFECTS -> R.string.max_audio_caps_group_session_effects
    AudioFeatureGroup.MAXFX -> R.string.max_audio_caps_group_maxfx
    AudioFeatureGroup.PROJECTS -> R.string.max_audio_caps_group_projects
    AudioFeatureGroup.DIAGNOSTIC -> R.string.max_audio_caps_group_diagnostic
    AudioFeatureGroup.REFUSED -> R.string.max_audio_caps_group_refused
}

/**
 * رمز الكتلة — **والرمز لا يحمل المعنى وحده**: العنوان مكتوب بجانبه، فالكتلة تُقرأ بلا لون.
 * ولا أيقونة جديدة تُخترع: الموجودة تكفي (‏`Tune` للتحكّم · `Extension` لما يحتاج محوّلًا · `Block` للمرفوض).
 */
private fun groupIcon(group: AudioFeatureGroup): ImageVector = when (group) {
    AudioFeatureGroup.GLOBAL -> Icons.Rounded.GraphicEq
    AudioFeatureGroup.PLATFORM -> Icons.Rounded.Tune
    AudioFeatureGroup.SESSION_EFFECTS -> Icons.Rounded.Tune
    AudioFeatureGroup.MAXFX -> Icons.Rounded.GraphicEq
    AudioFeatureGroup.PROJECTS -> Icons.Rounded.Extension
    AudioFeatureGroup.DIAGNOSTIC -> Icons.Rounded.Visibility
    AudioFeatureGroup.REFUSED -> Icons.Rounded.Block
}

/**
 * سطر ميزة واحدة: الاسم، ثمّ **السبب بجملته المترجمة**، ثمّ وسم الحالة.
 *
 * والسبب يُترجم من رمزه (`reason`) في `reasonText` — فالرموز تبقى في الطبقة الصافية للسجلّ
 * والقياس، والمستخدم يقرأ جملة تفهمها أمّه لا رمزًا إنجليزيًّا.
 */
@Composable
private fun CapabilityRow(verdict: AudioFeatureVerdict) {
    val detail = verdict.detail
    MaxRow(
        title = stringResource(featureTitle(verdict.feature)),
        subtitle = listOfNotNull(
            stringResource(
                capabilityReasonRes(verdict.reason) ?: R.string.max_audio_caps_reason_unknown,
            ),
            detail?.takeIf { it.isNotBlank() },
        ).joinToString("  ·  "),
        icon = statusIcon(verdict.support),
        iconTone = statusTone(verdict.support),
        trailing = {
            MaxCapsule(
                text = stringResource(statusText(verdict.support)),
                tone = statusTone(verdict.support),
            )
        },
    )
}

/** الاسم من الموارد — والرموز في [`AudioFeature`] هي المفاتيح، لا الصياغة. */
private fun featureTitle(feature: AudioFeature): Int = when (feature) {
    AudioFeature.GLOBAL_ATTACH -> R.string.max_audio_caps_feature_global_attach
    AudioFeature.EQUALIZER -> R.string.max_audio_caps_feature_equalizer
    AudioFeature.DYNAMICS_PROCESSING -> R.string.max_audio_caps_feature_dynamics
    AudioFeature.BASS_BOOST -> R.string.max_audio_caps_feature_bass_boost
    AudioFeature.VIRTUALIZER -> R.string.max_audio_caps_feature_virtualizer
    AudioFeature.LOUDNESS_ENHANCER -> R.string.max_audio_caps_feature_loudness
    AudioFeature.PRESET_REVERB -> R.string.max_audio_caps_feature_preset_reverb
    AudioFeature.ENVIRONMENTAL_REVERB -> R.string.max_audio_caps_feature_env_reverb
    AudioFeature.SPECTRUM -> R.string.max_audio_caps_feature_spectrum
    AudioFeature.MIXER_ATTRIBUTES -> R.string.max_audio_caps_feature_mixer
    AudioFeature.COMMUNICATION_ROUTING -> R.string.max_audio_caps_feature_call_routing
    AudioFeature.VOLUME_GROUPS -> R.string.max_audio_caps_feature_volume_groups
    AudioFeature.PER_APP_EFFECTS -> R.string.max_audio_caps_feature_per_app
    AudioFeature.CONVOLVER -> R.string.max_audio_caps_feature_convolver
    AudioFeature.PARAM_EQ -> R.string.max_audio_caps_feature_param_eq
    AudioFeature.BASS_SHELF -> R.string.max_audio_caps_feature_bass_shelf
    AudioFeature.CLARITY_SHELF -> R.string.max_audio_caps_feature_clarity
    AudioFeature.STEREO_WIDTH -> R.string.max_audio_caps_feature_width
    AudioFeature.TUBE_SATURATION -> R.string.max_audio_caps_feature_tube
    AudioFeature.COMPRESSOR -> R.string.max_audio_caps_feature_compressor
    AudioFeature.LIMITER -> R.string.max_audio_caps_feature_limiter
    AudioFeature.GLOBAL_GAIN -> R.string.max_audio_caps_feature_gain
    AudioFeature.FIR_EQUALIZER -> R.string.max_audio_caps_feature_fir_eq
    AudioFeature.CONVOLUTION_IR -> R.string.max_audio_caps_feature_convolution
    AudioFeature.DDC_CORRECTION -> R.string.max_audio_caps_feature_ddc
    AudioFeature.SPECTRUM_EXTENSION -> R.string.max_audio_caps_feature_spectrum_extend
    AudioFeature.VIRTUAL_BASS -> R.string.max_audio_caps_feature_virtual_bass
    AudioFeature.MULTIBAND_LIMITER -> R.string.max_audio_caps_feature_multiband_limiter
    AudioFeature.TRANSIENT_ENHANCEMENT -> R.string.max_audio_caps_feature_transient
    AudioFeature.EVEN_HARMONICS -> R.string.max_audio_caps_feature_harmonics
    AudioFeature.FIELD_SURROUND -> R.string.max_audio_caps_feature_field_surround
    AudioFeature.DIFF_SURROUND -> R.string.max_audio_caps_feature_diff_surround
    AudioFeature.REVERBERATION -> R.string.max_audio_caps_feature_reverb_scene
    AudioFeature.DYNAMIC_SYSTEM -> R.string.max_audio_caps_feature_dynamic_system
    AudioFeature.AGC -> R.string.max_audio_caps_feature_agc
    AudioFeature.CROSSFEED -> R.string.max_audio_caps_feature_crossfeed
    AudioFeature.ANALOG_X -> R.string.max_audio_caps_feature_analogx
    AudioFeature.FET_COMPRESSOR -> R.string.max_audio_caps_feature_fet
    AudioFeature.SPEAKER_OPTIMIZATION -> R.string.max_audio_caps_feature_speaker
    AudioFeature.MASTER_GATE -> R.string.max_audio_caps_feature_gate
    AudioFeature.LOW_CUT -> R.string.max_audio_caps_feature_low_cut
    AudioFeature.CHANNEL_BALANCE -> R.string.max_audio_caps_feature_balance
    AudioFeature.CAPTURE_PROCESSING -> R.string.max_audio_caps_feature_capture
}

/** الحالة ← نصّها. ولا سادس لهذه الخمسة. */
private fun statusText(support: AudioSupport): Int = when (support) {
    AudioSupport.WRITABLE -> R.string.max_audio_caps_status_writable
    AudioSupport.READ_ONLY -> R.string.max_audio_caps_status_read_only
    AudioSupport.NEEDS_ADAPTER -> R.string.max_audio_caps_status_needs_adapter
    AudioSupport.UNAVAILABLE -> R.string.max_audio_caps_status_unavailable
    AudioSupport.UNKNOWN -> R.string.max_audio_caps_status_unknown
}

/**
 * والنبرة تتبع الحالة **ولا تحمل المعنى وحدها**: الوسم مكتوب والرمز مرسوم (عقد `MaxTone` نفسه) —
 * فحالةٌ تُقرأ بلا لون.
 */
private fun statusTone(support: AudioSupport): MaxTone = when (support) {
    AudioSupport.WRITABLE -> MaxTone.Positive
    AudioSupport.READ_ONLY -> MaxTone.Neutral
    AudioSupport.NEEDS_ADAPTER -> MaxTone.Caution
    AudioSupport.UNAVAILABLE -> MaxTone.Inactive
    AudioSupport.UNKNOWN -> MaxTone.Caution
}

private fun statusIcon(support: AudioSupport): ImageVector = when (support) {
    AudioSupport.WRITABLE -> Icons.Rounded.Tune
    AudioSupport.READ_ONLY -> Icons.Rounded.Visibility
    AudioSupport.NEEDS_ADAPTER -> Icons.Rounded.Extension
    AudioSupport.UNAVAILABLE -> Icons.Rounded.Block
    AudioSupport.UNKNOWN -> Icons.Rounded.HelpOutline
}

/**
 * رمز سبب القدرة ← **مورد نصّه** — **وهو المرجع الوحيد لهذه الخريطة**.
 *
 * **ولماذا `internal` لا `private`:** الأسباب نفسها تُعرض من موضعين: مصفوفة القدرات هنا، وسلّم
 * المحرّكات في [`engineReasonText`] — وكان الثاني لا يعرفها فيُعرض «سببٌ بلا صياغة مترجمة بعد»
 * **على كلّ صفٍّ من السلّم** (عطبٌ شوهد على جهاز حقيقيّ). فصارت الخريطة واحدة، ومن لم يعرِفه
 * يرجع `null` فلا يُلحق بأقرب سببٍ يشبهه.
 */
internal fun capabilityReasonRes(reason: String): Int? = when (reason) {
    AudioCapabilityReason.EFFECTS_UNREADABLE -> R.string.max_audio_caps_reason_effects_unreadable
    AudioCapabilityReason.NOT_DECLARED -> R.string.max_audio_caps_reason_not_declared
    AudioCapabilityReason.DECLARED_GLOBAL_ACCEPTED -> R.string.max_audio_caps_reason_declared_global
    AudioCapabilityReason.GLOBAL_DENIED -> R.string.max_audio_caps_reason_global_denied
    AudioCapabilityReason.GLOBAL_NOT_MEASURED -> R.string.max_audio_caps_reason_global_unmeasured
    AudioCapabilityReason.GLOBAL_ACCEPTED -> R.string.max_audio_caps_reason_global_accepted
    AudioCapabilityReason.PERMISSION_RECORD_AUDIO -> R.string.max_audio_caps_reason_permission
    AudioCapabilityReason.SESSION_REQUIRED -> R.string.max_audio_caps_reason_session
    AudioCapabilityReason.SUPPORTED_BY_DEVICE -> R.string.max_audio_caps_reason_supported
    AudioCapabilityReason.NOT_SUPPORTED -> R.string.max_audio_caps_reason_not_supported
    AudioCapabilityReason.UNREADABLE -> R.string.max_audio_caps_reason_unreadable
    AudioCapabilityReason.NO_UID_OR_SESSION -> R.string.max_audio_caps_reason_no_uid
    AudioCapabilityReason.NO_EFFECT_CLASS -> R.string.max_audio_caps_reason_no_class
    AudioCapabilityReason.SYSTEM_LAYER_INSTALLED -> R.string.max_audio_caps_reason_system_layer_installed
    AudioCapabilityReason.SYSTEM_LAYER_MISSING -> R.string.max_audio_caps_reason_system_layer_missing
    AudioCapabilityReason.SYSTEM_LAYER_NOT_MEASURED -> R.string.max_audio_caps_reason_system_layer_unmeasured
    AudioCapabilityReason.NO_ENGINE_IMPLEMENTATION -> R.string.max_audio_caps_reason_no_engine
    AudioCapabilityReason.CAPTURE_REFUSED -> R.string.max_audio_caps_reason_capture_refused
    else -> null
}
