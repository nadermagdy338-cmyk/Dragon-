/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * استوديو الصوت — **أقسام المحرّك** (`AQ-03`…`AQ-06`): المعادل بمنحناه، والديناميكيّ بكل معامله،
 * والمؤثّرات البسيطة، والمازج.
 *
 * **ولا قرار في هذا الملفّ:** لا حكم على ميزة ولا ترتيب ميزات — القدرات (‏`AQ-01`) تحكم، والمحرّك
 * (‏`AQ-02`) يقرأ ويكتب، وهذا يرسم. وكل شريط هنا **لا يكتب أثناء السحب**: الحالة المحلّية تتحرّك،
 * والكتابة تقع عند الإفلات، ثمّ يُقرأ ما صار — فلا يتحوّل السحب الواحد إلى مئات الطلبات.
 *
 * **وثلاث حالات لا رابع في كل قسم:** قراءةٌ تُعرض · غياب قراءة (`—` وقسم مُعطَّل **بسببه**) · كتابةٌ لم
 * تُطبَّق (سطر بسبب مكتوب). ولا يُعرض مقبضٌ لشيء لم يُعلنه الجهاز (ADR-07، وقاعدة المستودع).
 */
package nd.max.ui.subscreens.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import nd.max.R
import nd.max.core.audio.AudioBackendReason
import nd.max.core.audio.AudioCapabilityReason
import nd.max.core.audio.AudioDynamicsBounds
import nd.max.core.audio.AudioEffectReason
import nd.max.core.audio.AudioOverlayReason
import nd.max.core.audio.AudioDynamicsSnapshot
import nd.max.core.audio.AudioEffectKind
import nd.max.core.audio.AudioEqBand
import nd.max.core.audio.AudioEqSnapshot
import nd.max.core.audio.AudioMixerAttribute
import nd.max.core.audio.AudioMixerSnapshot
import nd.max.core.audio.AudioReverbPreset
import nd.max.core.audio.AudioStrengthBounds
import nd.max.core.audio.AudioStrengthSnapshot
import nd.max.core.audio.AudioSystemReason
import nd.max.core.audio.DynamicsEqBand
import nd.max.core.audio.VendorAudioReason
import nd.max.core.audio.DynamicsParam
import nd.max.core.audio.DynamicsStage
import nd.max.core.audio.audioMixerLabel
import nd.max.core.audio.audioMixerSignature
import nd.max.core.audio.dynamicsBalanceGains
import nd.max.core.audio.dynamicsBalanceOf
import nd.max.core.audio.dynamicsFormatDb
import nd.max.core.audio.dynamicsFormatHz
import nd.max.core.audio.dynamicsFormatMs
import nd.max.core.audio.dynamicsFormatRatio
import nd.max.core.audio.eqCurveMetricsOf
import nd.max.core.audio.eqCurveNodesOf
import nd.max.core.audio.eqFormatFrequency
import nd.max.core.audio.eqFormatLevel
import nd.max.core.audio.eqIsFlat
import nd.max.core.audio.eqLevelFraction
import nd.max.core.audio.eqLevelFromCurveHeight
import nd.max.core.audio.eqLevelFromFraction
import nd.max.core.audio.eqNearestDraggableBandIndex
import nd.max.core.audio.loudnessFormat
import nd.max.core.audio.strengthFormat
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxCapsule
import nd.max.ui.design.MaxCollapsibleGroup
import nd.max.ui.design.MaxCardCarousel
import nd.max.ui.design.MaxCardShell
import nd.max.ui.design.MaxCarouselItem
import nd.max.ui.design.MaxCurvePlot
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxPlotAxis
import nd.max.ui.design.MaxPlotPoint
import nd.max.ui.design.MaxPlotTick
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSliderRow
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone

/** مفتاح المقبض المعروض في سطر الحكم — يُترجم هنا ولا يُبنى جملةً في المحرّك. */
internal fun knobTargetText(target: String?): Int? = when {
    target == null -> null
    target == "mixer" -> R.string.max_audio_mixer_title
    target == "route" -> R.string.max_audio_route_title
    target == "profile" -> R.string.max_audio_profiles_title
    target.startsWith("eq_") -> R.string.max_audio_eq_title
    target.startsWith("pre_") || target.startsWith("post_") -> R.string.max_audio_dyn_title
    target.startsWith("mbc_") || target.startsWith("limiter_") || target == "input_gain" ->
        R.string.max_audio_dyn_title
    else -> null
}

/** رمز سبب المحرّك ← جملته — ورمزٌ لا نصَّ له يُعرض «سبب غير معروف» لا يُلحق بأقرب شبيه. */
@Composable
internal fun engineReasonText(reason: String?): String = when (reason) {
    null -> ""
    "effect-not-attached" -> stringResource(R.string.max_audio_engine_reason_not_attached)
    "effect-attach-refused" -> stringResource(R.string.max_audio_engine_reason_attach_refused)
    // ── `AQ-05` سلّم الإرفاق (تكملة ٢٣٩): كل خطوة تُسمّى، **والحكم المعروض واحد** لا يخفي محاولة ──
    "attach-platform-default-refused" ->
        stringResource(R.string.max_audio_engine_reason_attach_platform_default)
    "attach-resolution-variant-refused" ->
        stringResource(R.string.max_audio_engine_reason_attach_resolution_variant)
    "attach-time-variant-refused" ->
        stringResource(R.string.max_audio_engine_reason_attach_time_variant)
    "effect-attach-refused-after-all-steps" ->
        stringResource(R.string.max_audio_engine_reason_attach_all_steps)
    "control-not-owned-by-us" -> stringResource(R.string.max_audio_engine_reason_control_not_owned)
    // ── `٣ح-أ` (تكملة ٢٤٣): مؤثّرٌ نملكه والجهاز معطّله ⇒ الكتابة تُقبل ولا تُسمع ──
    AudioEffectReason.DISABLED_BY_ENGINE ->
        stringResource(R.string.max_audio_engine_reason_disabled_by_engine)
    "band-out-of-range" -> stringResource(R.string.max_audio_engine_reason_band_out_of_range)
    "param-not-in-contract" -> stringResource(R.string.max_audio_maxfx_reason_param_unknown)
    "value-not-finite" -> stringResource(R.string.max_audio_maxfx_reason_value_invalid)
    "param-unreadable" -> stringResource(R.string.max_audio_engine_reason_param_unreadable)
    "strength-not-supported-by-device" -> stringResource(R.string.max_audio_engine_reason_not_supported)
    "control-store-unconfigured" -> stringResource(R.string.max_audio_engine_reason_store_unconfigured)
    "arbiter-unavailable" -> stringResource(R.string.max_audio_engine_reason_arbiter_unavailable)
    "no-audio-manager" -> stringResource(R.string.max_audio_engine_reason_no_manager)
    "below-platform-version" -> stringResource(R.string.max_audio_engine_reason_below_api)
    "unknown-route" -> stringResource(R.string.max_audio_engine_reason_unknown_route)
    "profile-saved" -> stringResource(R.string.max_audio_engine_reason_profile_saved)
    "profile-deleted" -> stringResource(R.string.max_audio_engine_reason_profile_deleted)
    "profile-save-failed" -> stringResource(R.string.max_audio_engine_reason_profile_save_failed)
    // ── `AQ-09`: أسباب الطبقة النظاميّة — الرمز نفسه يُقرأ في السجلّ، وهنا يُقال بالعربيّة ──
    AudioSystemReason.ROOT_REQUIRED -> stringResource(R.string.max_audio_layer_reason_root_required)
    AudioSystemReason.NO_EFFECTS_FILE -> stringResource(R.string.max_audio_layer_reason_no_source)
    AudioSystemReason.SOURCE_UNPARSABLE -> stringResource(R.string.max_audio_layer_reason_unparsable)
    AudioSystemReason.NO_ADDITION -> stringResource(R.string.max_audio_layer_reason_no_addition)
    AudioSystemReason.MODULE_PATH_UNWRITABLE -> stringResource(R.string.max_audio_layer_reason_module_unwritable)
    AudioSystemReason.NOT_INSTALLED -> stringResource(R.string.max_audio_layer_reason_not_installed)
    AudioSystemReason.OVERLAY_NOT_READABLE -> stringResource(R.string.max_audio_layer_reason_not_readable)
    // ── مكتبة المؤثّر (تكملة ٢٤٠): الحالات الثلاث التي كانت تُقرأ «نجحت» ثمّ لا يُسمع فرق ──
    AudioSystemReason.LIBRARY_NOT_SHIPPED ->
        stringResource(R.string.max_audio_layer_reason_library_not_shipped)
    AudioSystemReason.LIBRARY_NOT_INSTALLED ->
        stringResource(R.string.max_audio_layer_reason_library_not_installed)
    AudioSystemReason.LIBRARY_NOT_READABLE ->
        stringResource(R.string.max_audio_layer_reason_library_not_readable)
    AudioSystemReason.READY -> stringResource(R.string.max_audio_layer_reason_ready)
    AudioOverlayReason.INVALID_ADDITION -> stringResource(R.string.max_audio_layer_reason_addition_invalid)
    AudioOverlayReason.LIBRARY_CONFLICT -> stringResource(R.string.max_audio_layer_reason_addition_library_conflict)
    AudioOverlayReason.EFFECT_CONFLICT -> stringResource(R.string.max_audio_layer_reason_addition_effect_conflict)
    AudioOverlayReason.NOTHING_TO_ADD -> stringResource(R.string.max_audio_layer_reason_addition_declared)
    // ── تكملة ٢٣٠: أسباب سلّم المحرّكات ومؤثّر المصنّع — والرمز نفسه يُقرأ في السجلّ ──
    AudioCapabilityReason.EFFECTS_UNREADABLE -> stringResource(R.string.max_audio_caps_reason_effects_unreadable)
    AudioBackendReason.ABILITIES_NOT_MEASURED -> stringResource(R.string.max_audio_backend_reason_unmeasured)
    AudioBackendReason.VENDOR_NOT_MEASURED -> stringResource(R.string.max_audio_vendor_not_measured)
    AudioBackendReason.SYSTEM_LAYER_INSTALLED -> stringResource(R.string.max_audio_backend_reason_layer_installed)
    AudioBackendReason.SYSTEM_LAYER_NOT_INSTALLED -> stringResource(R.string.max_audio_backend_reason_layer_installable)
    AudioBackendReason.SYSTEM_LAYER_NOT_MEASURED -> stringResource(R.string.max_audio_backend_reason_layer_unmeasured)
    AudioBackendReason.NO_BACKEND_AVAILABLE -> stringResource(R.string.max_audio_backend_reason_none)
    AudioBackendReason.NO_BACKEND_MEASURED -> stringResource(R.string.max_audio_backend_reason_none_unmeasured)
    VendorAudioReason.NO_VENDOR_EFFECT -> stringResource(R.string.max_audio_vendor_reason_none_declared)
    VendorAudioReason.DECLARED_IN_CONFIG_ONLY -> stringResource(R.string.max_audio_vendor_reason_config_only)
    VendorAudioReason.ATTACH_NOT_MEASURED -> stringResource(R.string.max_audio_vendor_reason_attach_unmeasured)
    VendorAudioReason.ATTACHED_AND_OWNED -> stringResource(R.string.max_audio_vendor_reason_attached_owned)
    VendorAudioReason.NOT_CONTROLLABLE -> stringResource(R.string.max_audio_vendor_reason_owned_by_other)
    VendorAudioReason.CONTROL_UNMEASURED -> stringResource(R.string.max_audio_vendor_reason_control_unmeasured)
    VendorAudioReason.ATTACH_REFUSED -> stringResource(R.string.max_audio_vendor_reason_attach_refused)
    VendorAudioReason.ROUTE_UNSUPPORTED -> stringResource(R.string.max_audio_vendor_reason_route_unsupported)
    VendorAudioReason.HIDDEN_API_BLOCKED -> stringResource(R.string.max_audio_vendor_reason_hidden_api)
    VendorAudioReason.NO_PUBLIC_ROUTE -> stringResource(R.string.max_audio_vendor_reason_no_public_route)
    VendorAudioReason.INVALID_UUID -> stringResource(R.string.max_audio_vendor_reason_invalid_uuid)
    VendorAudioReason.ATTACH_THREW -> stringResource(R.string.max_audio_vendor_reason_attach_threw)
    VendorAudioReason.SESSION_CLOSED -> stringResource(R.string.max_audio_vendor_reason_session_closed)
    VendorAudioReason.PARAM_LENGTH_MISMATCH -> stringResource(R.string.max_audio_vendor_reason_param_mismatch)
    // والآخر يُسأل عنها في **خريطة القدرات** أولًا: الأسباب نفسها تأتي من موضعين (مصفوفة القدرات
    // وسلّم المحرّكات)، ونقلُ صياغتها إلى خريطةٍ واحدة هو ما أوقف عرضَ «سببٌ بلا صياغة مترجمة
    // بعد» على كل صفٍّ من السلّم — عطبٌ شوهد على جهاز حقيقيّ لا في نظريّة.
    else -> stringResource(
        capabilityReasonRes(reason) ?: R.string.max_audio_engine_reason_unknown,
    )
}

// ───────────────────────────────────────── ‏AQ-03: المعادل ─────────────────────────────────────────

/**
 * المعادل: **المنحنى أوّلًا**، ثمّ نطاقٌ لكل شريط، ثمّ أنماط المنصّة.
 *
 * والمنحنى **مستهدفٌ لا مقيس** — وهذا يُكتب في الشاشة صراحةً: ما نرسمه هو ما **طلبنا** كتابته، لا
 * استجابةً مقيَّلة بالتقاط الصوت (والتقاط مرفوض في الخطّة §4). فتسميته «استجابة مقيسة» كذب.
 */
@Composable
internal fun AudioEqSection(
    snapshot: AudioEqSnapshot?,
    reason: String?,
    onBandCommit: (Int, Int) -> Unit,
    onPreset: (Int) -> Unit,
) {
    MaxSection(
        title = stringResource(R.string.max_audio_eq_title),
        description = stringResource(R.string.max_audio_eq_description),
        // أوّل قسمٍ في تبويب المحرّك فيبدأ مفتوحًا — والمنحنى هو ما يُطلب هنا — ويُطوى بلمسة.
        collapsible = true,
        initiallyExpanded = true,
    ) {
        if (snapshot == null) {
            UnavailableRow(
                title = stringResource(R.string.max_audio_eq_title),
                detail = engineReasonText(reason).takeIf { it.isNotBlank() },
            )
            return@MaxSection
        }
        // **المنحنى أوّلًا، ثمّ الأنماط — والشرائح مطويّة:** عشرة أشرطةٍ بين البطاقةَين كانتا تدفعان
        // المنحنى والأنماط خارج الشاشة، وهما أوّل ما جاء المستخدم من أجله. والطويّ لا يُخفي حالةً:
        // العنوان يقول «١٠ نطاقات» والمنحنى نفسه يعرض قيمها.
        EqCurveCard(snapshot = snapshot, onBandCommit = onBandCommit)
        MaxCollapsibleGroup(
            title = stringResource(R.string.max_audio_eq_bands_title),
            summary = stringResource(R.string.max_audio_eq_band_count, snapshot.bands.size),
        ) {
            MaxGroup {
                snapshot.bands.forEachIndexed { index, band ->
                    if (index > 0) MaxGroupDivider()
                    EqBandSlider(band = band, onCommit = onBandCommit)
                }
            }
        }
        if (snapshot.presets.isNotEmpty()) {
            // والرأس في مجموعته ثمّ **بطاقاتٌ لا صفوف**: الجهاز قد يُعلن عشرة أنماط، وقائمةٌ من عشرة
            // صفوف تُخرج المعادل نفسه من الشاشة — وهو أوّل ما جاء المستخدم من أجله.
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_audio_eq_presets_title),
                    subtitle = stringResource(R.string.max_audio_eq_presets_note),
                    icon = Icons.Rounded.Waves,
                    trailing = {
                        MaxCapsule(
                            text = stringResource(
                                R.string.max_audio_eq_preset_current,
                                snapshot.currentPreset ?: 0,
                            ),
                            tone = MaxTone.Neutral,
                        )
                    },
                )
            }
            PlatformPresetCarousel(snapshot = snapshot, onPreset = onPreset)
        }
    }
}

/**
 * بطاقة المنحنى — **لوحٌ يُسحب، لا رسمٌ يُتفرَّج عليه**.
 *
 * **والفرق عن الرسم القديم ليس شكلًا:** كان المنحنى يُرسم ثمّ تُضبط النطاقات من عشرة أشرطة تحته —
 * فتضبط النطاق السابعَ من عشرة وقد فقدت صورة المجموع منذ السطر الأوّل. والآن نقطةٌ واحدة تُسحب فيُكتب
 * نطاقها، ويبقى المنحنى كلّه أمام العين أثناء السحب.
 *
 * **وثلاثُ حقائق تُقال في هذه البطاقة ولا تُخفي:** (١) ما يُرسم **مستهدفٌ لا مقيس** (نصّ الشاشة في
 * وصفه)؛ (٢) **حدّ السحب مدى المنصّة المُعلَن**، فإن لم يُعلن الجهاز مدى كسبٍ رُسم المنحنى ولم يُسحب
 * — ويُقال ذلك أيضًا (`max_audio_eq_curve_readonly`)؛ (٣) ما يُكتب تحت الإصبع هو **ما سيُكتب فعلًا**
 * بعد التقييد، لا ما يريده الإصبع (`onDragSnap`)، فلا يفترق المرئيّ عن المكتوب.
 */
@Composable
private fun EqCurveCard(snapshot: AudioEqSnapshot, onBandCommit: (Int, Int) -> Unit) {
    val metrics = remember(snapshot.bands) { eqCurveMetricsOf(snapshot.bands) }
    val nodes = remember(snapshot.bands, metrics) { eqCurveNodesOf(snapshot.bands, metrics) }
    val bandByIndex = remember(snapshot.bands) { snapshot.bands.associateBy { it.index } }

    val points = remember(nodes) { nodes.map { MaxPlotPoint(key = it.index, x = it.x, y = it.y) } }
    val ticks = remember(nodes, bandByIndex) {
        nodes.mapNotNull { node ->
            val label = eqFormatFrequency(bandByIndex[node.index]?.centerHz) ?: return@mapNotNull null
            MaxPlotTick(x = node.x, label = label)
        }
    }

    MaxCardShell {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline),
            ) {
                Text(
                    text = stringResource(R.string.max_audio_eq_curve_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        if (eqIsFlat(snapshot.bands)) R.string.max_audio_eq_flat else R.string.max_audio_eq_curve_note,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MaxCapsule(
                text = stringResource(R.string.max_audio_eq_band_count, snapshot.bands.size),
                tone = MaxTone.Neutral,
            )
        }

        if (nodes.size < 2) {
            Text(
                text = stringResource(R.string.max_audio_eq_curve_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@MaxCardShell
        }

        MaxCurvePlot(
            points = points,
            editable = metrics.isDraggable,
            active = true,
            axis = MaxPlotAxis(
                top = eqFormatLevel(metrics.yScaleMb) ?: MAX_VALUE_UNAVAILABLE,
                middle = eqFormatLevel(0) ?: MAX_VALUE_UNAVAILABLE,
                bottom = eqFormatLevel(-metrics.yScaleMb) ?: MAX_VALUE_UNAVAILABLE,
            ),
            ticks = ticks,
            badgeLabel = { key, y ->
                val band = bandByIndex[key]
                val level = band?.let { eqLevelFromCurveHeight(it, metrics, y) }
                eqFormatLevel(level) ?: MAX_VALUE_UNAVAILABLE
            },
            onDragSnap = { key, y ->
                val band = bandByIndex[key]
                val level = band?.let { eqLevelFromCurveHeight(it, metrics, y) }
                if (level == null || metrics.yScaleMb <= 0) {
                    y
                } else {
                    (level.toFloat() / metrics.yScaleMb).coerceIn(-1f, 1f)
                }
            },
            onPick = { x -> eqNearestDraggableBandIndex(snapshot.bands, metrics, x) },
            onDrop = { key, y ->
                val band = bandByIndex[key] ?: return@MaxCurvePlot
                val level = eqLevelFromCurveHeight(band, metrics, y) ?: return@MaxCurvePlot
                if (level != band.levelMb) onBandCommit(band.index, level)
            },
        )

        Text(
            text = stringResource(
                if (metrics.isDraggable) {
                    R.string.max_audio_eq_drag_hint
                } else {
                    R.string.max_audio_eq_curve_readonly
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * أنماط المنصّة كاروسيل — **والعدد هو سبب الشكل**: الجهاز يُعلن ما يُعلن، والقائمة تطول بقدره.
 *
 * والبطاقة المختارة تُعلن نفسها بسطر «مستعمل» في متنها وبعلامةٍ في طرفها، فتُقرأ الحالة من البطاقة
 * لا من موضعها في الشريط وحده.
 */
@Composable
private fun PlatformPresetCarousel(snapshot: AudioEqSnapshot, onPreset: (Int) -> Unit) {
    val inUse = stringResource(R.string.max_audio_eq_preset_in_use)
    MaxCardCarousel(
        items = snapshot.presets.mapIndexed { index, name ->
            MaxCarouselItem(
                key = index,
                title = name,
                icon = Icons.Rounded.GraphicEq,
                caption = if (index == snapshot.currentPreset) inUse else null,
            )
        },
        selectedKey = snapshot.currentPreset ?: 0,
        onSelect = { index -> if (index != snapshot.currentPreset) onPreset(index) },
    )
}

/** شريط نطاق واحد — يُكتب عند الإفلات، ويُقرأ بعدها. */
@Composable
private fun EqBandSlider(band: AudioEqBand, onCommit: (Int, Int) -> Unit) {
    val live = eqLevelFraction(band)
    var dragged by remember(band.index) { mutableStateOf<Float?>(null) }
    val title = eqFormatFrequency(band.centerHz) ?: stringResource(R.string.max_audio_eq_band, band.index + 1)
    MaxSliderRow(
        title = title,
        value = dragged ?: live ?: 0.5f,
        valueText = eqFormatLevel(band.levelMb) ?: MAX_VALUE_UNAVAILABLE,
        subtitle = stringResource(
            R.string.max_audio_eq_band_range,
            eqFormatFrequency(band.rangeLowHz) ?: MAX_VALUE_UNAVAILABLE,
            eqFormatFrequency(band.rangeHighHz) ?: MAX_VALUE_UNAVAILABLE,
        ),
        enabled = band.isWritable && live != null,
        lockedReason = if (live == null || !band.isWritable) stringResource(R.string.max_audio_eq_band_locked) else null,
        onValueChange = { dragged = it },
        onValueChangeFinished = {
            val fraction = dragged
            dragged = null
            if (fraction == null) return@MaxSliderRow
            val target = eqLevelFromFraction(band, fraction) ?: return@MaxSliderRow
            if (target != band.levelMb) onCommit(band.index, target)
        },
    )
}

// ───────────────────────────────────── ‏AQ-04: الديناميكيّ ─────────────────────────────────────

/** أداة عرض معامل: مداه من حدودنا المُعلَنة، وصياغته من الطبقة الصافية. */
private class ParamUi(
    val param: DynamicsParam,
    val min: Float,
    val max: Float,
    val format: (Float) -> String,
)

private val MBC_PARAMS = listOf(
    ParamUi(DynamicsParam.THRESHOLD, AudioDynamicsBounds.UI_THRESHOLD_MIN_DB, AudioDynamicsBounds.UI_THRESHOLD_MAX_DB) { dynamicsFormatDb(it) ?: "" },
    ParamUi(DynamicsParam.RATIO, AudioDynamicsBounds.UI_RATIO_MIN, AudioDynamicsBounds.UI_RATIO_MAX) { dynamicsFormatRatio(it) ?: "" },
    ParamUi(DynamicsParam.ATTACK, AudioDynamicsBounds.UI_ATTACK_MIN_MS, AudioDynamicsBounds.UI_ATTACK_MAX_MS) { dynamicsFormatMs(it) ?: "" },
    ParamUi(DynamicsParam.RELEASE, AudioDynamicsBounds.UI_RELEASE_MIN_MS, AudioDynamicsBounds.UI_RELEASE_MAX_MS) { dynamicsFormatMs(it) ?: "" },
    ParamUi(DynamicsParam.KNEE, AudioDynamicsBounds.UI_KNEE_MIN_DB, AudioDynamicsBounds.UI_KNEE_MAX_DB) { dynamicsFormatDb(it) ?: "" },
    ParamUi(DynamicsParam.GATE, AudioDynamicsBounds.UI_GATE_MIN_DB, AudioDynamicsBounds.UI_GATE_MAX_DB) { dynamicsFormatDb(it) ?: "" },
    ParamUi(DynamicsParam.EXPANDER, AudioDynamicsBounds.UI_RATIO_MIN, AudioDynamicsBounds.UI_RATIO_MAX) { dynamicsFormatRatio(it) ?: "" },
    ParamUi(DynamicsParam.PRE_GAIN, AudioDynamicsBounds.UI_GAIN_MIN_DB, AudioDynamicsBounds.UI_GAIN_MAX_DB) { dynamicsFormatDb(it) ?: "" },
    ParamUi(DynamicsParam.POST_GAIN, AudioDynamicsBounds.UI_GAIN_MIN_DB, AudioDynamicsBounds.UI_GAIN_MAX_DB) { dynamicsFormatDb(it) ?: "" },
)

private val LIMITER_PARAMS = MBC_PARAMS.filter { it.param.isLimiterParam }

/**
 * الديناميكيّ كاملًا: معادلا المحرّك (قبل/بعد)، ثمّ الضاغط متعدّد النطاقات، ثمّ المُحدِّد، ثمّ التوازن.
 *
 * **وحدود الأشرطة حدُّنا المُعلَن** — المنصّة لا تُعلن مدًى لهذه المعاملات، وهذا مكتوب في الشاشة.
 * وما تعود به المنصّة **بعد** الكتابة هو المعروض، ولو قيّدت.
 */
@Composable
internal fun AudioDynamicsSection(
    snapshot: AudioDynamicsSnapshot?,
    reason: String?,
    onEqGain: (DynamicsStage, Int, Float) -> Unit,
    onEqCutoff: (DynamicsStage, Int, Float) -> Unit,
    onMbc: (Int, DynamicsParam, Float) -> Unit,
    onLimiter: (DynamicsParam, Float) -> Unit,
    onBalance: (Float, Float) -> Unit,
) {
    MaxSection(
        title = stringResource(R.string.max_audio_dyn_title),
        description = stringResource(R.string.max_audio_dyn_description),
        collapsible = true,
    ) {
        if (snapshot == null) {
            UnavailableRow(
                title = stringResource(R.string.max_audio_dyn_title),
                detail = engineReasonText(reason).takeIf { it.isNotBlank() },
            )
            return@MaxSection
        }
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_audio_dyn_bounds_title),
                subtitle = stringResource(R.string.max_audio_dyn_bounds_note),
                trailing = {
                    MaxCapsule(
                        text = stringResource(R.string.max_audio_dyn_channels, snapshot.channels),
                        tone = MaxTone.Neutral,
                    )
                },
            )
        }
        DynamicsEqStage(snapshot, DynamicsStage.PRE, R.string.max_audio_dyn_preeq, onEqGain, onEqCutoff)
        DynamicsEqStage(snapshot, DynamicsStage.POST, R.string.max_audio_dyn_posteq, onEqGain, onEqCutoff)
        if (snapshot.mbc.isNotEmpty()) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_audio_dyn_mbc),
                    subtitle = stringResource(R.string.max_audio_dyn_mbc_note),
                    icon = Icons.Rounded.GraphicEq,
                    trailing = {
                        MaxCapsule(
                            text = stringResource(R.string.max_audio_dyn_band_count, snapshot.mbc.size),
                            tone = MaxTone.Neutral,
                        )
                    },
                )
                snapshot.mbc.forEach { band ->
                    MaxGroupDivider()
                    MaxRow(
                        title = stringResource(R.string.max_audio_dyn_band, band.index + 1),
                        subtitle = dynamicsFormatHz(band.cutoffHz) ?: MAX_VALUE_UNAVAILABLE,
                    )
                    MBC_PARAMS.forEach { ui ->
                        val current = readMbcParam(band, ui.param)
                        ParamSlider(
                            title = stringResource(paramTitle(ui.param)),
                            value = current,
                            ui = ui,
                            onCommit = { onMbc(band.index, ui.param, it) },
                        )
                    }
                }
            }
        }
        snapshot.limiter?.let { limiter ->
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_audio_dyn_limiter),
                    subtitle = stringResource(
                        if (limiter.inUse) R.string.max_audio_dyn_limiter_in_use
                        else R.string.max_audio_dyn_limiter_not_in_use,
                    ),
                    trailing = {
                        MaxCapsule(
                            text = stringResource(R.string.max_audio_dyn_band_count, 1),
                            tone = if (limiter.inUse) MaxTone.Positive else MaxTone.Neutral,
                        )
                    },
                )
                LIMITER_PARAMS.forEach { ui ->
                    MaxGroupDivider()
                    ParamSlider(
                        title = stringResource(paramTitle(ui.param)),
                        value = ui.param.readLimiterRaw(limiter),
                        ui = ui,
                        onCommit = { onLimiter(ui.param, it) },
                    )
                }
            }
        }
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_audio_dyn_balance),
                subtitle = stringResource(R.string.max_audio_dyn_balance_note),
                icon = Icons.Rounded.Waves,
            )
            BalanceSlider(snapshot.inputGainsDb, onBalance)
        }
    }
}

/** مرحلة معادل المحرّك (قبل/بعد) — أشرطة الكسب وعتبة القطع لكل نطاق مُعلَن. */
@Composable
private fun DynamicsEqStage(
    snapshot: AudioDynamicsSnapshot,
    stage: DynamicsStage,
    titleRes: Int,
    onGain: (DynamicsStage, Int, Float) -> Unit,
    onCutoff: (DynamicsStage, Int, Float) -> Unit,
) {
    val bands: List<DynamicsEqBand> = if (stage == DynamicsStage.PRE) snapshot.preEq else snapshot.postEq
    if (bands.isEmpty()) return
    MaxGroup {
        MaxRow(
            title = stringResource(titleRes),
            subtitle = stringResource(R.string.max_audio_dyn_eq_note),
            trailing = {
                MaxCapsule(
                    text = stringResource(R.string.max_audio_dyn_band_count, bands.size),
                    tone = MaxTone.Neutral,
                )
            },
        )
        bands.forEach { band ->
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_audio_dyn_band, band.index + 1),
                subtitle = dynamicsFormatHz(band.cutoffHz) ?: MAX_VALUE_UNAVAILABLE,
            )
            DragSlider(
                title = stringResource(R.string.max_audio_dyn_gain),
                value = band.gainDb,
                valueText = dynamicsFormatDb(band.gainDb) ?: MAX_VALUE_UNAVAILABLE,
                min = AudioDynamicsBounds.UI_GAIN_MIN_DB,
                max = AudioDynamicsBounds.UI_GAIN_MAX_DB,
                enabled = true,
                onCommit = { onGain(stage, band.index, it) },
            )
            DragSlider(
                title = stringResource(R.string.max_audio_dyn_cutoff),
                value = band.cutoffHz,
                valueText = dynamicsFormatHz(band.cutoffHz) ?: MAX_VALUE_UNAVAILABLE,
                min = AudioDynamicsBounds.UI_CUTOFF_MIN_HZ,
                max = AudioDynamicsBounds.UI_CUTOFF_MAX_HZ,
                enabled = true,
                onCommit = { onCutoff(stage, band.index, it) },
            )
        }
    }
}

/** شريط معامل: يقرأ الحاليّ، ويتحرّك محلّيًّا، ويكتب عند الإفلات. */
@Composable
private fun ParamSlider(title: String, value: Float, ui: ParamUi, onCommit: (Float) -> Unit) {
    DragSlider(
        title = title,
        value = value,
        valueText = ui.format(value),
        min = ui.min,
        max = ui.max,
        enabled = true,
        onCommit = onCommit,
    )
}

/** شريط سحبٍ عامّ — الحالة المحلّية أثناء السحب، والكتابة عند الإفلات. */
@Composable
private fun DragSlider(
    title: String,
    value: Float,
    valueText: String,
    min: Float,
    max: Float,
    enabled: Boolean,
    onCommit: (Float) -> Unit,
) {
    var dragged by remember(title, value) { mutableStateOf<Float?>(null) }
    MaxSliderRow(
        title = title,
        value = dragged ?: value,
        valueText = valueText,
        valueRange = if (max > min) min..max else 0f..1f,
        enabled = enabled && max > min,
        onValueChange = { dragged = it },
        onValueChangeFinished = {
            val fraction = dragged
            dragged = null
            if (fraction == null) return@MaxSliderRow
            onCommit(fraction)
        },
    )
}

/** التوازن: مقبض واحد `-1..1` يُترجم إلى دخل قناتين — **ولا يرفع الصوت** (خفضٌ فقط). */
@Composable
private fun BalanceSlider(inputGainsDb: List<Float>, onBalance: (Float, Float) -> Unit) {
    val left = inputGainsDb.getOrNull(0)
    val right = inputGainsDb.getOrNull(1)
    val current = dynamicsBalanceOf(inputGainsDb) ?: 0f
    var dragged by remember(current) { mutableStateOf<Float?>(null) }
    MaxSliderRow(
        title = stringResource(R.string.max_audio_dyn_balance_position),
        value = dragged ?: current,
        valueRange = -1f..1f,
        valueText = dynamicsFormatDb(left) ?: MAX_VALUE_UNAVAILABLE,
        subtitle = stringResource(
            R.string.max_audio_dyn_balance_value,
            dynamicsFormatDb(left) ?: MAX_VALUE_UNAVAILABLE,
            dynamicsFormatDb(right) ?: MAX_VALUE_UNAVAILABLE,
        ),
        onValueChange = { dragged = it },
        onValueChangeFinished = {
            val position = dragged
            dragged = null
            if (position == null) return@MaxSliderRow
            val (l, r) = dynamicsBalanceGains(position)
            onBalance(l, r)
        },
    )
}

// ───────────────────────────────── ‏AQ-05: المؤثّرات البسيطة ─────────────────────────────────

/** المؤثّرات البسيطة: سطرٌ لكل مؤثّر **مفتوح فعلًا** — وما لم يُفتح لا سطر له بل سببه يُعرض. */
@Composable
internal fun AudioEffectsSection(
    strengths: Map<AudioEffectKind, AudioStrengthSnapshot>,
    reasons: Map<AudioEffectKind, String>,
    onStrength: (AudioEffectKind, Int) -> Unit,
) {
    val kinds = listOf(
        AudioEffectKind.BASS_BOOST,
        AudioEffectKind.VIRTUALIZER,
        AudioEffectKind.LOUDNESS,
        AudioEffectKind.PRESET_REVERB,
    )
    if (kinds.none { strengths.containsKey(it) || reasons.containsKey(it) }) return
    MaxSection(
        title = stringResource(R.string.max_audio_effects_simple_title),
        description = stringResource(R.string.max_audio_effects_description),
        collapsible = true,
    ) {
        kinds.forEach { kind ->
            val snapshot = strengths[kind]
            if (snapshot == null) {
                val reason = reasons[kind]
                if (reason != null) {
                    UnavailableRow(
                        title = stringResource(effectTitle(kind)),
                        detail = engineReasonText(reason).takeIf { it.isNotBlank() },
                    )
                }
                return@forEach
            }
            SimpleEffectGroup(kind, snapshot, onStrength)
        }
    }
}

@Composable
private fun SimpleEffectGroup(
    kind: AudioEffectKind,
    snapshot: AudioStrengthSnapshot,
    onStrength: (AudioEffectKind, Int) -> Unit,
) {
    MaxGroup {
        MaxRow(
            title = stringResource(effectTitle(kind)),
            subtitle = effectNote(kind),
            icon = Icons.Rounded.Waves,
            trailing = {
                MaxCapsule(
                    text = if (snapshot.isWritable) {
                        stringResource(R.string.max_audio_effects_writable)
                    } else {
                        stringResource(R.string.max_audio_effects_not_supported)
                    },
                    tone = if (snapshot.isWritable) MaxTone.Positive else MaxTone.Inactive,
                )
            },
        )
        when (kind) {
            AudioEffectKind.PRESET_REVERB -> snapshot.presets.forEachIndexed { index, token ->
                MaxGroupDivider()
                MaxRow(
                    title = stringResource(reverbTitle(token)),
                    enabled = index != snapshot.currentPreset,
                    onClick = { if (index != snapshot.currentPreset) onStrength(kind, index) },
                )
            }
            else -> SimpleStrengthSlider(kind, snapshot, onStrength)
        }
    }
}

@Composable
private fun SimpleStrengthSlider(
    kind: AudioEffectKind,
    snapshot: AudioStrengthSnapshot,
    onStrength: (AudioEffectKind, Int) -> Unit,
) {
    val min = if (kind == AudioEffectKind.LOUDNESS) {
        AudioStrengthBounds.UI_LOUDNESS_MIN_MB
    } else {
        AudioStrengthBounds.PLATFORM_STRENGTH_MIN
    }
    val max = if (kind == AudioEffectKind.LOUDNESS) {
        AudioStrengthBounds.UI_LOUDNESS_MAX_MB
    } else {
        AudioStrengthBounds.PLATFORM_STRENGTH_MAX
    }
    var dragged by remember(kind, snapshot.value) { mutableStateOf<Float?>(null) }
    val live = if (snapshot.value == null) null else (snapshot.value - min).toFloat() / (max - min)
    MaxSliderRow(
        title = stringResource(R.string.max_audio_effects_strength),
        value = dragged ?: live ?: 0f,
        valueText = if (kind == AudioEffectKind.LOUDNESS) {
            loudnessFormat(snapshot.value) ?: MAX_VALUE_UNAVAILABLE
        } else {
            strengthFormat(snapshot.value) ?: MAX_VALUE_UNAVAILABLE
        },
        enabled = snapshot.isWritable,
        lockedReason = if (!snapshot.isWritable) stringResource(R.string.max_audio_effects_locked) else null,
        onValueChange = { dragged = it },
        onValueChangeFinished = {
            val fraction = dragged
            dragged = null
            if (fraction == null) return@MaxSliderRow
            val target = nd.max.core.audio.strengthValueFrom(fraction, min, max)
            if (target != snapshot.value) onStrength(kind, target)
        },
    )
}

// ──────────────────────────────────── ‏AQ-06: المازج ────────────────────────────────────

/** المازج: ما يُعلنه المخرج، وما هو ساريٌّ الآن، وإلغاء التفضيل — وكلّه من المنصّة. */
@Composable
internal fun AudioMixerSection(
    snapshot: AudioMixerSnapshot?,
    onRequest: (AudioMixerAttribute) -> Unit,
    onClear: () -> Unit,
) {
    MaxSection(
        title = stringResource(R.string.max_audio_mixer_title),
        description = stringResource(R.string.max_audio_mixer_description),
        collapsible = true,
    ) {
        if (snapshot == null) {
            UnavailableRow(
                title = stringResource(R.string.max_audio_mixer_title),
                detail = stringResource(R.string.max_audio_mixer_unavailable),
            )
            return@MaxSection
        }
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_audio_mixer_current),
                subtitle = snapshot.current?.let { audioMixerLabel(it) }
                    ?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.max_audio_mixer_none),
                icon = Icons.Rounded.Waves,
                trailing = {
                    if (snapshot.current != null) {
                        MaxCapsule(text = stringResource(R.string.max_audio_mixer_preferred), tone = MaxTone.Accent)
                    }
                },
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_audio_mixer_clear),
                subtitle = stringResource(R.string.max_audio_mixer_clear_note),
                enabled = snapshot.current != null,
                onClick = { onClear() },
            )
        }
        if (snapshot.supported.isNotEmpty()) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_audio_mixer_supported),
                    subtitle = stringResource(R.string.max_audio_mixer_supported_note),
                )
                snapshot.supported.forEach { attribute ->
                    val signature = audioMixerSignature(attribute)
                    val current = snapshot.current?.let(::audioMixerSignature)
                    MaxGroupDivider()
                    MaxRow(
                        title = audioMixerLabel(attribute),
                        enabled = signature != current,
                        onClick = { if (signature != current) onRequest(attribute) },
                        trailing = {
                            if (signature == current) {
                                MaxCapsule(
                                    text = stringResource(R.string.max_audio_mixer_in_use),
                                    tone = MaxTone.Positive,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────── الأدوات ──────────────────────────────────────────

/** سطر «غير متاح بسبب» — يُعرض بدل قسم صامت، فما لا يعمل يُقال سببه (ADR-07). */
@Composable
internal fun UnavailableRow(title: String, detail: String?) {
    MaxGroup {
        MaxRow(
            title = title,
            subtitle = detail ?: stringResource(R.string.max_audio_engine_reason_not_attached),
            trailing = {
                MaxCapsule(
                    text = stringResource(R.string.max_audio_engine_unavailable),
                    tone = MaxTone.Inactive,
                )
            },
        )
    }
}

internal fun effectTitle(kind: AudioEffectKind): Int = when (kind) {
    // والمعادل له عنوان قسمه (`max_audio_eq_title`) — فلا نسخة ثانية لنصّ واحد.
    AudioEffectKind.EQUALIZER -> R.string.max_audio_eq_title
    AudioEffectKind.DYNAMICS -> R.string.max_audio_effect_dynamics
    AudioEffectKind.BASS_BOOST -> R.string.max_audio_effect_bass
    AudioEffectKind.VIRTUALIZER -> R.string.max_audio_effect_spatial
    AudioEffectKind.LOUDNESS -> R.string.max_audio_effect_loudness
    AudioEffectKind.PRESET_REVERB -> R.string.max_audio_effect_reverb
}

@Composable
private fun effectNote(kind: AudioEffectKind): String = stringResource(
    when (kind) {
        AudioEffectKind.BASS_BOOST -> R.string.max_audio_effect_bass_note
        AudioEffectKind.VIRTUALIZER -> R.string.max_audio_effect_spatial_note
        AudioEffectKind.LOUDNESS -> R.string.max_audio_effect_loudness_note
        else -> R.string.max_audio_effect_reverb_note
    },
)

internal fun reverbTitle(token: String): Int = when (token) {
    AudioReverbPreset.NONE -> R.string.max_audio_reverb_none
    AudioReverbPreset.SMALL_ROOM -> R.string.max_audio_reverb_small_room
    AudioReverbPreset.MEDIUM_ROOM -> R.string.max_audio_reverb_medium_room
    AudioReverbPreset.LARGE_ROOM -> R.string.max_audio_reverb_large_room
    AudioReverbPreset.MEDIUM_HALL -> R.string.max_audio_reverb_medium_hall
    AudioReverbPreset.LARGE_HALL -> R.string.max_audio_reverb_large_hall
    AudioReverbPreset.PLATE -> R.string.max_audio_reverb_plate
    else -> R.string.max_audio_reverb_none
}

internal fun paramTitle(param: DynamicsParam): Int = when (param) {
    DynamicsParam.THRESHOLD -> R.string.max_audio_param_threshold
    DynamicsParam.RATIO -> R.string.max_audio_param_ratio
    DynamicsParam.ATTACK -> R.string.max_audio_param_attack
    DynamicsParam.RELEASE -> R.string.max_audio_param_release
    DynamicsParam.KNEE -> R.string.max_audio_param_knee
    DynamicsParam.GATE -> R.string.max_audio_param_gate
    DynamicsParam.EXPANDER -> R.string.max_audio_param_expander
    DynamicsParam.PRE_GAIN -> R.string.max_audio_param_pre_gain
    DynamicsParam.POST_GAIN -> R.string.max_audio_param_post_gain
}

/** قراءة معامل نطاق ضغط من اللقطة — بلاحقة `dynamicsFormat*` في الطبقة الصافية. */
private fun readMbcParam(band: nd.max.core.audio.DynamicsMbcBand, param: DynamicsParam): Float = when (param) {
    DynamicsParam.THRESHOLD -> band.thresholdDb
    DynamicsParam.RATIO -> band.ratio
    DynamicsParam.ATTACK -> band.attackMs
    DynamicsParam.RELEASE -> band.releaseMs
    DynamicsParam.KNEE -> band.kneeDb
    DynamicsParam.GATE -> band.noiseGateDb
    DynamicsParam.EXPANDER -> band.expanderRatio
    DynamicsParam.PRE_GAIN -> band.preGainDb
    DynamicsParam.POST_GAIN -> band.postGainDb
}

/** قراءة معامل المُحدِّد من اللقطة — نفس المفردات، ومصدرٌ آخر. */
private fun DynamicsParam.readLimiterRaw(limiter: nd.max.core.audio.DynamicsLimiter): Float = when (this) {
    DynamicsParam.THRESHOLD -> limiter.thresholdDb
    DynamicsParam.RATIO -> limiter.ratio
    DynamicsParam.ATTACK -> limiter.attackMs
    DynamicsParam.RELEASE -> limiter.releaseMs
    DynamicsParam.POST_GAIN -> limiter.postGainDb
    else -> 0f
}

