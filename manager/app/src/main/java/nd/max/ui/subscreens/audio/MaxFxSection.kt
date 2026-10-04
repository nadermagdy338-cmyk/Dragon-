/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * استوديو الصوت — **قسم MaxFx** (تكملة ٢٣٥): مؤثّرنا النظاميّ يُثبَّت ويُضبط من هنا.
 *
 * **وثلاث قواعد تحكم هذا القسم، كلّها من نمط [`VendorEffectSection`] نفسه:**
 *
 * 1. **لا حالة تُصنع هنا.** المعاملات تُبنى من `MaxFxModel.params` (العقد) فمعاملٌ يُضاف هناك يظهر
 *    هنا بلا كود جديد، والوحدات والنطاقات تُقرأ من العقد نفسه — فلا مدى مُخترع ولا مقياسٌ مصنوع.
 * 2. **لا كتابة من هنا.** الشاشة تنادي `viewModel.writeMaxFxParam` فيمرّ بالمحكِّم إلى الخاصية،
 *    ثمّ تُقرأ بعدها — وما يُعرض هو ما قُرئ، والغياب «افتراض العقد» لا صفر (ADR-07).
 * 3. **الفعل الخطر يُوضَّح قبله.** التثبيت يكتب طبقةً تحلّ محلّ ملفّ يقرؤه `audioserver` — فنصّ
 *    التحذير ونافذة التأكيد **هما نصّا الطبقة النظاميّة نفسها** ([`AudioSystemLayerSection`]) لأنّ
 *    الفعل واحد، ونصّان لفعلٍ واحد يفترقان مع الوقت.
 *
 * **ووحدات القيم رموزٌ دوليّة (`dB`/`Hz`/`ms`/`×`):** رقمٌ بلا وحدة يُقرأ على غير وجه، والوحدة
 * رمزٌ لا كلام فلا تُترجم. وصياغة القيمة نفسها من العقد ([MaxFxModel.propValue]) — فما يُعرض
 * حرفيًّا هو ما يُكتب في الخاصية.
 */
package nd.max.ui.subscreens.audio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlin.math.round
import nd.max.R
import nd.max.core.audio.AudioKnobVerdict
import nd.max.core.audio.AudioSystemSnapshot
import nd.max.core.audio.AudioWriteOutcome
import nd.max.core.audio.MaxFxModel
import nd.max.ui.design.MaxCapsule
import nd.max.ui.design.MaxConfirmDialog
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSliderRow
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.design.MaxTone

/**
 * قسم MaxFx كاملًا: الهويّة والحالة، ثمّ التحذير والتثبيت، ثمّ حكم آخر كتابة، ثمّ مقابض العقد.
 *
 * @param values قيم الخصائص كما قُرئت — ومفتاحها مفتاح العقد؛ وغياب المفتاح «لم تُكتب بعد».
 * @param system لقطة الطبقة النظاميّة — يُقرأ منها هل MaxFx مُثبَّت فعلًا في التهيئة.
 * @param knob حكم آخر كتابة في MaxFx — ومُنفصل عن حكم المحرّك، فلا يُقرأ سطرٌ واحد على وجهين.
 * @param onParam كتابة معامل بقيمةٍ خامّة — والقصّ والصياغة في العقد لا هنا.
 * @param onInstall تثبيت MaxFx في طبقة المؤثّرات (سطر الإضافة من [`MaxFxModel.installAddition`]).
 */
@Composable
internal fun MaxFxSection(
    values: Map<String, String>,
    system: AudioSystemSnapshot?,
    knob: AudioKnobVerdict?,
    onParam: (String, Double) -> Unit,
    onInstall: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    // «مُثبَّت» قياسٌ لا نيّة: مؤثّرٌ مُصرَّفٌ في وثيقة الطبقة هو ما يقرأه `audioserver` بعد الإقلاع.
    val installed = system?.document?.effects?.any { it.name == MaxFxModel.EFFECT_CONFIG_NAME } == true

    MaxSection(
        title = stringResource(R.string.max_audio_maxfx_title),
        description = stringResource(R.string.max_audio_maxfx_description),
        collapsible = true,
    ) {
        MaxGroup {
            MaxRow(
                title = MaxFxModel.EFFECT_NAME,
                subtitle = listOf(MaxFxModel.LIBRARY_FILE, MaxFxModel.IMPL_UUID).joinToString("  ·  "),
                icon = Icons.Rounded.Waves,
                iconTone = if (installed) MaxTone.Positive else MaxTone.Neutral,
                trailing = {
                    MaxCapsule(
                        text = stringResource(
                            if (installed) {
                                R.string.max_audio_layer_installed
                            } else {
                                R.string.max_audio_layer_not_installed
                            },
                        ),
                        tone = if (installed) MaxTone.Positive else MaxTone.Neutral,
                    )
                },
            )
        }

        // التحذير قبل الفعل لا بعده — وبنصّ الطبقة نفسها: الفعل واحد.
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_audio_layer_warning_title),
                subtitle = stringResource(R.string.max_audio_layer_warning_body),
                icon = Icons.Rounded.Warning,
                iconTone = MaxTone.Caution,
            )
        }

        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_audio_maxfx_install),
                subtitle = stringResource(R.string.max_audio_maxfx_install_note),
                icon = Icons.Rounded.Add,
                onClick = { confirming = true },
            )
        }

        // حكم آخر كتابة — والمُقارَن ما قُرئ فعلًا، والسبب يُنقل حرفيًّا مترجمًا لا مُصنَّفًا من جديد.
        if (knob != null) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_audio_vendor_last_write),
                    subtitle = listOfNotNull(
                        knob.expected?.let { stringResource(R.string.max_audio_vendor_write_expected, it) },
                        knob.actual?.let { stringResource(R.string.max_audio_vendor_write_actual, it) },
                        engineReasonText(knob.reason),
                    ).joinToString("  ·  ").ifBlank { stringResource(R.string.max_audio_vendor_write_ok) },
                    icon = Icons.Rounded.Tune,
                    iconTone = when {
                        knob.isApplied -> MaxTone.Positive
                        knob.outcome == AudioWriteOutcome.BLOCKED -> MaxTone.Caution
                        else -> MaxTone.Inactive
                    },
                )
            }
        }

        // ثمّ مقابض العقد — **بترتيب الـTSV حرفيًّا** في أربع مجموعات: التمكين، فالنبرة، فالديناميكيّ،
        // فالمعادل. والمجموعات تُصفّي بالـid وحده، فالعقد يبقى مصدر الترتيب لا هذه الشاشة.
        MaxFxParamGroup(
            params = MaxFxModel.params.filter { it.id == 1 },
            values = values,
            onParam = onParam,
        )
        MaxFxParamGroup(
            params = MaxFxModel.params.filter { it.id in 2..8 },
            values = values,
            onParam = onParam,
        )
        MaxFxParamGroup(
            params = MaxFxModel.params.filter { it.id in 9..13 },
            values = values,
            onParam = onParam,
        )
        MaxFxParamGroup(
            params = MaxFxModel.params.filter { it.id in 14..28 },
            values = values,
            onParam = onParam,
        )
    }

    // والتأكيد فنّيًّا لا تخويفًا: سطرُ الإضافة **بما سيُكتب حرفيًّا** تحت رسالة التأكيد.
    if (confirming) {
        MaxConfirmDialog(
            visible = true,
            title = stringResource(R.string.max_audio_layer_confirm_title),
            message = stringResource(R.string.max_audio_layer_confirm_body),
            confirmLabel = stringResource(R.string.max_audio_layer_confirm_action),
            onConfirm = {
                confirming = false
                onInstall()
            },
            onDismiss = { confirming = false },
            destructive = true,
            icon = Icons.Rounded.Warning,
            technicalDetail = MaxFxModel.installAddition(),
        )
    }
}

/** مجموعة مقابض — صفوفها بترتيب العقد، والفاصل بينها كباقي المجموعات. */
@Composable
private fun MaxFxParamGroup(
    params: List<MaxFxModel.Param>,
    values: Map<String, String>,
    onParam: (String, Double) -> Unit,
) {
    if (params.isEmpty()) return
    MaxGroup {
        params.forEachIndexed { index, param ->
            if (index > 0) MaxGroupDivider()
            MaxFxParamRow(
                param = param,
                value = values[param.key],
                onParam = onParam,
            )
        }
    }
}

/**
 * مقبض معامل واحد — **مفتاحٌ للصحيح المُعلن أو منزلقٌ للمدى المُعلن**، لا ثالث.
 *
 * والمزلق لا يكتب أثناء السحب: الحالة المحلّية وحدها تتحرّك، والكتابة عند الإفلات (`AU-03`) —
 * ونهاية السحب تُقارن بالمقروء فتُخطّي الكتابة التي لا تغيّر شيئًا، فلا مئات معاملاتٍ على مقبضٍ واحد.
 */
@Composable
private fun MaxFxParamRow(
    param: MaxFxModel.Param,
    value: String?,
    onParam: (String, Double) -> Unit,
) {
    val unit = unitOf(param.key)
    val read = value?.toDoubleOrNull()
    // والغياب ليس صفرًا (ADR-07): الموضع يبدأ من افتراض العقد — وهو ما يستعمله الطرف C فعلًا
    // (`maxfx_config_default`) — ويُوسم نصُّه «افتراضي» فلا يُقرأ قياسًا.
    val fallback = quantize(unit, param.def)
    var dragged by remember(param.key) { mutableStateOf<Double?>(null) }
    val shown = quantize(unit, dragged ?: read ?: fallback)
    val formatted = MaxFxModel.propValue(param.key, shown).orEmpty()
    val text = valueText(unit, formatted).let { built ->
        if (read == null && dragged == null) {
            stringResource(R.string.max_audio_maxfx_value_default, built)
        } else {
            built
        }
    }
    val title = paramTitle(param)
    val range = stringResource(
        R.string.max_audio_maxfx_range,
        valueText(unit, MaxFxModel.propValue(param.key, param.min).orEmpty()),
        valueText(unit, MaxFxModel.propValue(param.key, param.max).orEmpty()),
    )

    if (param.isInt && param.min == 0.0 && param.max == 1.0) {
        MaxSwitchRow(
            title = title,
            checked = shown >= 1.0,
            onCheckedChange = { onParam(param.key, if (it) 1.0 else 0.0) },
            subtitle = listOf(param.key, text).joinToString("  ·  "),
        )
    } else {
        MaxSliderRow(
            title = title,
            value = shown.toFloat(),
            onValueChange = { dragged = quantize(unit, it.toDouble()) },
            valueText = text,
            subtitle = listOf(param.key, range).joinToString("  ·  "),
            valueRange = param.min.toFloat()..param.max.toFloat(),
            onValueChangeFinished = {
                val target = dragged
                dragged = null
                // ولا كتابة عند التساوي: المطلوب هو التغيير، لا سطرٌ في السجلّ بلا أثر.
                if (target == null || target == (read ?: fallback)) return@MaxSliderRow
                onParam(param.key, target)
            },
        )
    }
}

/** وحدة المعامل — **مشتقّة من اسم العقد** لا من جدولٍ ثانٍ: `_db`/`_hz`/`_ms` والباقي بلا رمز. */
private enum class MaxFxUnit { DB, HZ, MS, RATIO, PLAIN }

private fun unitOf(key: String): MaxFxUnit = when {
    key.endsWith("_db") -> MaxFxUnit.DB
    key.endsWith("_hz") -> MaxFxUnit.HZ
    key.endsWith("_ms") -> MaxFxUnit.MS
    key == "comp_ratio" -> MaxFxUnit.RATIO
    else -> MaxFxUnit.PLAIN
}

/**
 * تقريب العرض إلى مقياسٍ يُقرأ: ٫1 للمستويات والزمن والنسبة، و1 للترددات، و٫01 للعوامل.
 *
 * **ولماذا هنا لا في العقد:** هذه تقريبُ **عرض** لقيمةٍ تصل من منزلقٍ عائم (فتصبح
 * `0.3000000011920929`)، والعقد يبقى بصياغته الحرفيّة؛ والمُرسَل يُقرَّب قبل الكتابة، فما يُعرض
 * هو ما يُكتب بالضبط.
 */
private fun quantize(unit: MaxFxUnit, value: Double): Double {
    val scale = when (unit) {
        MaxFxUnit.DB, MaxFxUnit.MS, MaxFxUnit.RATIO -> 10.0
        MaxFxUnit.HZ -> 1.0
        MaxFxUnit.PLAIN -> 100.0
    }
    return round(value * scale) / scale
}

/** القيمة بوحدتها — والوحدات رموزٌ دوليّة لا تُترجم (`dB`/`Hz`/`ms`/`×`). */
@Composable
private fun valueText(unit: MaxFxUnit, formatted: String): String = when (unit) {
    MaxFxUnit.DB -> stringResource(R.string.max_audio_maxfx_value_db, formatted)
    MaxFxUnit.HZ -> stringResource(R.string.max_audio_maxfx_value_hz, formatted)
    MaxFxUnit.MS -> stringResource(R.string.max_audio_maxfx_value_ms, formatted)
    MaxFxUnit.RATIO -> stringResource(R.string.max_audio_maxfx_value_ratio, formatted)
    MaxFxUnit.PLAIN -> formatted
}

/**
 * اسم المعامل — **المفاتيح هي أسماء العقد نفسه**، فلا تُنسخ تسميةٌ ثانية في الشاشة.
 *
 * والنمط الثلاثي للمعادل (`eq<band>_*`) يُبنى من رقم النطاق، فيُضاف نطاقٌ للعقد بلا جدولٍ هنا؛
 * ومعاملٌ لا يُعرَف هذا المعرف **يُعرض باسمه في العقد** — لا يُلحق بأقرب شبيه فيُقرأ باسمٍ ليس له.
 */
@Composable
private fun paramTitle(param: MaxFxModel.Param): String = when (param.key) {
    "enable" -> stringResource(R.string.max_audio_maxfx_param_enable)
    "input_gain_db" -> stringResource(R.string.max_audio_maxfx_param_input_gain_db)
    "output_gain_db" -> stringResource(R.string.max_audio_maxfx_param_output_gain_db)
    "bass_gain_db" -> stringResource(R.string.max_audio_maxfx_param_bass_gain_db)
    "bass_freq_hz" -> stringResource(R.string.max_audio_maxfx_param_bass_freq_hz)
    "width" -> stringResource(R.string.max_audio_maxfx_param_width)
    "clarity_db" -> stringResource(R.string.max_audio_maxfx_param_clarity_db)
    "tube_amount" -> stringResource(R.string.max_audio_maxfx_param_tube_amount)
    "comp_threshold_db" -> stringResource(R.string.max_audio_maxfx_param_comp_threshold_db)
    "comp_ratio" -> stringResource(R.string.max_audio_maxfx_param_comp_ratio)
    "comp_attack_ms" -> stringResource(R.string.max_audio_maxfx_param_comp_attack_ms)
    "comp_release_ms" -> stringResource(R.string.max_audio_maxfx_param_comp_release_ms)
    "limiter_ceiling_db" -> stringResource(R.string.max_audio_maxfx_param_limiter_ceiling_db)
    else -> when {
        param.key.startsWith("eq") && param.key.endsWith("_gain_db") ->
            stringResource(R.string.max_audio_maxfx_param_eq_gain, bandOf(param.key))
        param.key.startsWith("eq") && param.key.endsWith("_freq_hz") ->
            stringResource(R.string.max_audio_maxfx_param_eq_freq, bandOf(param.key))
        param.key.startsWith("eq") && param.key.endsWith("_q") ->
            stringResource(R.string.max_audio_maxfx_param_eq_q, bandOf(param.key))
        else -> param.key
    }
}

/** رقم نطاق المعادل من مفتاح العقد (`eq3_gain_db` ⇒ 3) — و0 حين لا يُقرأ رقم، فلا يُخترع. */
private fun bandOf(key: String): Int =
    key.removePrefix("eq").substringBefore('_').toIntOrNull() ?: 0
