/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * استوديو الصوت — **بطاقة البطل**: أوّل ما يلقاه القارئ، وما تُقال فيه حقيقةٌ واحدة كلّ مرّة.
 *
 * ───────────────────────── Attribution (Apache-2.0) ─────────────────────────
 * The hero-card layout (a gradient band over the screen's own readings, with a state capsule on
 * it) is adapted from the **DolbyUI** interface — branch `rodin` of
 * `Digimend-X-Rodin/packages_apps_DolbyUI` (a fork of `swiitch-OFF-Lab/packages_apps_DolbyUI`),
 * licensed **Apache-2.0** as stated in its own file headers, and used with the copyright holder's
 * permission. This is a rework, not a copy — and the banner here draws measured levels only,
 * where the source drew a fixed decorative shape; the credit is in `docs/PROVENANCE.md`.
 *
 * ────────────────────── لماذا وُجدت (الشكوى التي وُلدت منها) ──────────────────────
 * الشاشة كانت تبدأ بعنوان قسم ورقمين، ثمّ بشريط تبويبات داخل التمرير، ثمّ قائمة. فما يراه المستخدم
 * أوّلًا **لا يقول شيئًا عن الصوت**: لا شكل، ولا حالة، ولا قراءة حيّة — ويحتاج أن يقرأ سطرين ليصل
 * إلى ما جاء من أجله. والبطاقة هنا تفعل العكس: تُعرض **حالة الجهارة كاملة في شاشة واحدة** (شكلٌ من
 * قراءةٍ حقيقيّة · الجهاز النشط · حالتا المحرّك والالتقاط · ثلاثة أرقام)، ثمّ يقرّر القارئ أيّ تبويب
 * يريد قبل أن يمرّر شيئًا.
 *
 * ────────────────────── وما تُعرض فيه مُقاس لا مُتخيَّل ──────────────────────
 * الشريط يُرسم من **ثلاث قراءات مختلفة، ولا رابعة**: (١) **الطيف الحيّ** إن كان الالتقاط جاريًا —
 * وهو المقروء فعلًا من `AudioSpectrumFrame.bands`؛ (٢) **المنحنى المستهدف** إن لم يكن، وهو ما
 * طلبناه من المعادل (`eqCurveNodesOf`) لا استجابةً مقيَّلة — والوسم يقول أيّهما يُرى فلا يُقرأ
 * أحدهما مكان الآخر؛ (٣) **قاعدةٌ ساكنة** حين لا قراءة أصلًا. **ولا شكل رابع مصنوع** (وهو ما تفعله
 * واجهة المصدر: شكلٌ ثابت يُرسم دائمًا فيُقرأ قراءةً وهو ليس منها).
 *
 * والرسم هنا **للعرض لا للتحرير**: لا كتابة من هذه البطاقة. الطيفُ يُشغَّل من قسمه في «النظام» لأن
 * طلب إذن التسجيل يجب أن يقع بلمسٍ صريح داخل قسمه بشرحه (أمر المالك) — فالبطاقة **تُرشد** إلى هناك
 * بسطرٍ صريح، ولا تزرع مفتاحًا ثانيًا لشيءٍ واحد.
 */
package nd.max.ui.subscreens.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.core.audio.AudioEqSnapshot
import nd.max.core.audio.EqCurveNode
import nd.max.core.audio.eqCurveNodesOf
import nd.max.ui.component.NeuralKpiTile
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxAlpha
import nd.max.ui.design.MaxCapsule
import nd.max.ui.design.MaxCardShell
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.design.MaxWaveformBanner
import nd.max.ui.viewmodel.AudioStudioUiState

/** ارتفاع نطاق الرسم في البطل — رقمٌ واحد يُقاس عليه كل ما داخله، فلا يقفز الشكل بين حالاته. */
private val heroBandHeight = 148.dp

/** الرسم داخل النطاق يترك حشوًا رأسيًّا فلا يلامس حدّ البطاقة. */
private val heroPlotInset = MaxSpace.md

/**
 * بطاقة البطل.
 *
 * @param onCaptureHint يُنادى حين يلمس المستخدم سطر «الالتقاط في تبويب النظام» — والانتقال شأن الشاشة.
 */
@Composable
internal fun AudioHeroCard(
    state: AudioStudioUiState,
    onCaptureHint: () -> Unit,
) {
    val hz = stringResource(R.string.max_audio_unit_hz)
    val capture = state.spectrumRunning

    MaxCardShell(contentPadding = 0.dp) {
        HeroBand(state = state)

        Column(
            modifier = Modifier.padding(MaxSpace.lg),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.md),
        ) {
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
                        text = stringResource(R.string.max_audio_engine_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.max_audio_engine_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MaxCapsule(
                    text = stringResource(
                        when {
                            capture -> R.string.max_audio_spectrum_live
                            state.activeDevice != null -> R.string.max_audio_engine_live
                            else -> R.string.max_audio_engine_no_device
                        },
                    ),
                    tone = when {
                        capture -> MaxTone.Accent
                        state.activeDevice != null -> MaxTone.Positive
                        else -> MaxTone.Inactive
                    },
                )
            }

            // **والجهاز النشط يُسمّى بوصفه ثمّ باسمه:** «المخرج النشط» تقول **ما هذا السطر**، والاسم
            // يقول **أيّه** — فالسطر الذي يحمل اسمًا بلا وصف يُقرأ إعدادًا لا قراءة.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
            ) {
                Text(
                    text = stringResource(R.string.max_audio_engine_device),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.activeDevice?.productName
                        ?: stringResource(R.string.max_audio_engine_no_device_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.row),
            ) {
                NeuralKpiTile(
                    caption = stringResource(R.string.max_audio_sample_rate_caption),
                    value = state.output?.sampleRateHz?.let { "$it $hz" } ?: MAX_VALUE_UNAVAILABLE,
                    accent = MaterialTheme.colorScheme.primary,
                    support = stringResource(R.string.max_audio_sample_rate_support),
                    modifier = Modifier.weight(1f),
                )
                NeuralKpiTile(
                    caption = stringResource(R.string.max_audio_frames_caption),
                    value = state.output?.framesPerBuffer?.toString() ?: MAX_VALUE_UNAVAILABLE,
                    accent = MaterialTheme.colorScheme.tertiary,
                    support = stringResource(R.string.max_audio_frames_support),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // والإرشاد يظهر **حين يكون ذا معنى فقط**: مع التقاطٍ جارٍ لا يحتاج المستخدم أن يُدَلّ عليه.
        if (!capture) {
            MaxRow(
                title = stringResource(R.string.max_audio_hero_capture_hint),
                icon = Icons.Rounded.Waves,
                iconTone = MaxTone.Accent,
                onClick = onCaptureHint,
            )
        }
    }
}

/**
 * نطاق الرسم — والوسم يقول **ما يُرى بالضبط**: طيفٌ مقروء الآن، أو منحنى مستهدف، أو لا قراءة.
 */
@Composable
private fun HeroBand(state: AudioStudioUiState) {
    val bands = state.spectrum?.bands.orEmpty()
    val live = state.spectrumRunning && bands.isNotEmpty()
    val nodes = remember(state.eq) { eqNodesOf(state.eq) }
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroBandHeight)
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.primaryContainer,
                        scheme.secondaryContainer,
                        scheme.surfaceContainerLow,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            live -> MaxWaveformBanner(
                levels = bands,
                live = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroBandHeight - heroPlotInset * 2)
                    .padding(horizontal = MaxSpace.md),
            )

            nodes.isNotEmpty() -> {
                val curveInk = scheme.primary
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroBandHeight - heroPlotInset * 2)
                        .padding(horizontal = MaxSpace.md),
                ) {
                    val centerY = size.height / 2f
                    drawLine(
                        color = curveInk.copy(alpha = MaxAlpha.borderStrong),
                        start = Offset(0f, centerY),
                        end = Offset(size.width, centerY),
                        strokeWidth = MaxSize.hairlineBorder.toPx(),
                    )
                    if (nodes.size < 2) return@Canvas
                    val path = Path()
                    nodes.forEachIndexed { index, node ->
                        val x = node.x.coerceIn(0f, 1f) * size.width
                        val y = centerY - node.y.coerceIn(-1f, 1f) * centerY * 0.82f
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, curveInk, style = Stroke(width = 3f))
                    nodes.forEach { node ->
                        drawCircle(
                            color = curveInk,
                            radius = 4f,
                            center = Offset(
                                node.x.coerceIn(0f, 1f) * size.width,
                                centerY - node.y.coerceIn(-1f, 1f) * centerY * 0.82f,
                            ),
                        )
                    }
                }
            }

            else -> MaxWaveformBanner(
                levels = emptyList(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroBandHeight),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(MaxSpace.md),
        ) {
            MaxCapsule(
                text = stringResource(
                    when {
                        live -> R.string.max_audio_spectrum_live
                        nodes.isNotEmpty() -> R.string.max_audio_eq_curve_title
                        else -> R.string.max_audio_spectrum_idle
                    },
                ),
                tone = if (live) MaxTone.Accent else MaxTone.Neutral,
            )
        }
    }
}

/**
 * نقاط المنحنى المستهدف من لقطة المعادل — **والقياس على JVM** (`eqCurveNodesOf`)، فلا حساب في
 * طبقة الواجهة ولا مقياس مصنوع هنا.
 */
private fun eqNodesOf(snapshot: AudioEqSnapshot?): List<EqCurveNode> =
    snapshot?.bands?.let { eqCurveNodesOf(it) } ?: emptyList()
