/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxManager Design Language — metric presentation.
 *
 * The single most important rule in this app: a number on screen must be
 * traceable. [MaxMetric] therefore cannot be constructed without declaring how
 * much the reading can be trusted, and the renderer refuses to print a value
 * whose trust level says there is nothing real to print.
 *
 * This is what stops "nice looking" placeholders (0%, --, last known value
 * styled as live) from leaking into a performance tool.
 */
package nd.max.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.HistoryToggleOff
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.ui.settings.rememberAdvancedMode
import nd.max.ui.theme.MonoValueStyleLarge
import nd.max.ui.theme.MonoValueStyleMedium
import nd.max.ui.theme.MonoValueStyleSmall

/**
 * One measured quantity, ready to render.
 *
 * @param value pre-formatted text WITHOUT the unit, or null when the source
 *        produced nothing. Never pass "0" to mean "unknown".
 * @param trust how the value must be read. Drives colour, chip and whether the
 *        value is printed at all.
 * @param source short provenance such as "thermal_zone0" or "dumpsys gfxinfo".
 * @param age caller-formatted, localized relative time ("2s", "just now").
 * @param note why the value is missing / what the caveat is.
 */
@Immutable
data class MaxMetric(
    val label: String,
    val value: String?,
    val unit: String? = null,
    val trust: MaxDataTrust = MaxDataTrust.Live,
    val source: String? = null,
    val age: String? = null,
    val note: String? = null
)

enum class MaxMetricSize { Large, Medium, Small }

@Composable
fun maxTrustLabel(trust: MaxDataTrust): String = stringResource(
    when (trust) {
        MaxDataTrust.Live -> R.string.max_trust_live
        MaxDataTrust.Stale -> R.string.max_trust_stale
        MaxDataTrust.Snapshot -> R.string.max_trust_snapshot
        MaxDataTrust.Loading -> R.string.max_trust_loading
        MaxDataTrust.Unreadable -> R.string.max_trust_unreadable
        MaxDataTrust.Unsupported -> R.string.max_trust_unsupported
    }
)

private fun trustIcon(trust: MaxDataTrust): ImageVector? = when (trust) {
    MaxDataTrust.Live -> null // rendered as a dot, the quiet default
    MaxDataTrust.Stale -> Icons.Rounded.HistoryToggleOff
    MaxDataTrust.Snapshot -> Icons.Rounded.Schedule
    MaxDataTrust.Loading -> Icons.Rounded.HourglassEmpty
    MaxDataTrust.Unreadable -> Icons.Rounded.RemoveCircleOutline
    MaxDataTrust.Unsupported -> Icons.Rounded.Block
}

/**
 * Trust indicator. Always icon-or-dot PLUS text, never colour alone, so the
 * meaning survives greyscale, colour blindness and screenshots.
 */
@Composable
fun MaxTrustChip(
    trust: MaxDataTrust,
    modifier: Modifier = Modifier
) {
    // Same gate as the caption line: a Snapshot chip is shown only under Advanced Mode.
    if (!provenanceVisible(trust)) return
    val tone = trust.visual().tone
    val content = tone.content()
    val icon = trustIcon(trust)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MaxRadius.pill),
        color = tone.container(),
        border = BorderStroke(MaxSize.hairlineBorder, tone.border())
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MaxSpace.sm, vertical = MaxSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)
        ) {
            if (icon == null) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(content, CircleShape)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(MaxSize.iconGlyphSmall)
                )
            }
            Text(
                text = maxTrustLabel(trust),
                style = MaterialTheme.typography.labelSmall,
                color = content
            )
        }
    }
}

/**
 * هل يُطبع سطر المنشأ لهذه الثقة؟
 *
 * **«Snapshot» يُخفى افتراضيًّا ويظهر بالوضع المتقدّم وحده (أمر المالك):** القيمة المقروءة مرّة
 * (ثابت بناء، لقطة إقلاع) كان سطرها `Snapshot · Source Build` تفصيلًا عن **مصدرها** لا عن قيمتها،
 * فيقرأ المستخدم صفوف القيم كتقرير مطوّرين. وهو ما قاله المالك نصًّا: «أخفِ Snapshot من التطبيق
 * بأكمله مثل SnapshotSource Build … وتظهر عند الضغط على Advanced Mode».
 *
 * **وما ليس Snapshot يبقى ظاهرًا في الحالتين — وهو خروجٌ مُعلَن عن «كل القيم غير الحيّة»**
 * (جواب المالك على سؤال النطاق): سطر `Loading · Unreadable · Unsupported` هو **التفسير الوحيد**
 * لقيمة لا تُطبع (شرطة)، وإخفاؤه يجعل الفراغ بلا معنى — وهو ما يمنعه `ADR-07`؛ وسطر
 * `Stale` («آخر عيّنة») تحذيرٌ بأن الرقم قديم، فكتمانه يجعل القديم يبدو حيًّا وهو **عين** ما
 * يمنعه `ADR-07` وروح هذا الملف. فقُدّمت الجودة على الحرف، والتعارض مُعلَن لا مسكوت عنه
 * (نقدّم الجودة على الحرف ونُعلن التعارض — أمر المالك §0).
 */
@Composable
private fun provenanceVisible(trust: MaxDataTrust): Boolean =
    trust != MaxDataTrust.Snapshot || rememberAdvancedMode()

/** Caption line: trust · updated · source. Only prints what it actually knows. */
@Composable
fun MaxMetricProvenance(
    metric: MaxMetric,
    modifier: Modifier = Modifier
) {
    if (!provenanceVisible(metric.trust)) return
    val separator = stringResource(R.string.max_provenance_separator)
    val updatedLabel = stringResource(R.string.max_updated_label)
    val sourceLabel = stringResource(R.string.max_source_label)

    val parts = buildList {
        metric.age?.takeIf { it.isNotBlank() }?.let { add("$updatedLabel $it") }
        metric.source?.takeIf { it.isNotBlank() }?.let { add("$sourceLabel $it") }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)
    ) {
        MaxTrustChip(metric.trust)
        if (parts.isNotEmpty()) {
            Text(
                text = parts.joinToString(separator),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Primary metric presentation: label, value, unit, provenance.
 * Digits use the mono styles so a changing value never reflows the layout.
 */
@Composable
fun MaxMetricReadout(
    metric: MaxMetric,
    modifier: Modifier = Modifier,
    size: MaxMetricSize = MaxMetricSize.Medium,
    showProvenance: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val showsValue = metric.trust.visual().showsValue && metric.value != null
    val valueText = if (showsValue) metric.value.orEmpty() else MAX_VALUE_UNAVAILABLE
    val unavailableA11y = stringResource(R.string.max_value_unavailable_a11y)
    val trustText = maxTrustLabel(metric.trust)
    val trustAudible = provenanceVisible(metric.trust)

    val valueStyle = when (size) {
        MaxMetricSize.Large -> MonoValueStyleLarge
        MaxMetricSize.Medium -> MonoValueStyleMedium
        MaxMetricSize.Small -> MonoValueStyleSmall
    }
    val valueColor = if (showsValue) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MaxAlpha.supportingText)
    }

    val spoken = buildString {
        append(metric.label)
        append(", ")
        if (showsValue) {
            append(valueText)
            metric.unit?.let { append(" ").append(it) }
        } else {
            append(unavailableA11y)
        }
        if (trustAudible) {
            append(", ")
            append(trustText)
            metric.age?.takeIf { it.isNotBlank() }?.let { append(", ").append(it) }
        }
        metric.note?.takeIf { it.isNotBlank() }?.let { append(". ").append(it) }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // A metric pads itself, exactly like [MaxRow]. It did not, and every
            // caller that dropped it straight into a MaxGroup got its label and
            // value flush against the group's border — the values read as clipped
            // because they effectively were. Fixing it here rather than at each
            // call site is what makes it true for screens written after this one.
            .padding(
                horizontal = MaxSpace.rowPaddingHorizontal,
                vertical = MaxSpace.rowPaddingVertical,
            )
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)
        ) {
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)
            ) {
                Text(
                    text = valueText,
                    style = valueStyle,
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showsValue) {
                    metric.unit?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = MaxSpace.hairline)
                        )
                    }
                }
            }
            metric.note?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showProvenance && provenanceVisible(metric.trust)) {
                MaxMetricProvenance(metric)
            }
        }
        trailing?.invoke()
    }
}

/**
 * Dense variant for lists of readings: label left, value right.
 * Provenance is only printed when the reading is NOT live, because in a list
 * repeating "Live" on every row is noise — the exception is the signal.
 */
@Composable
fun MaxMetricLine(
    metric: MaxMetric,
    modifier: Modifier = Modifier
) {
    val showsValue = metric.trust.visual().showsValue && metric.value != null
    val valueText = if (showsValue) metric.value.orEmpty() else MAX_VALUE_UNAVAILABLE
    val unavailableA11y = stringResource(R.string.max_value_unavailable_a11y)
    val trustText = maxTrustLabel(metric.trust)
    val trustAudible = provenanceVisible(metric.trust)

    val spoken = buildString {
        append(metric.label)
        append(", ")
        if (showsValue) {
            append(valueText)
            metric.unit?.let { append(" ").append(it) }
        } else {
            append(unavailableA11y)
        }
        if (metric.trust != MaxDataTrust.Live && trustAudible) {
            append(", ")
            append(trustText)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // See the note on the large readout above: this is the shared fix for
            // readings sitting flush against a group's edge.
            .padding(
                horizontal = MaxSpace.rowPaddingHorizontal,
                vertical = MaxSpace.rowPaddingVertical,
            )
            .clearAndSetSemantics { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
        ) {
            Text(
                text = metric.label,
                // `Heading` لا `Paragraph`: كلمة واحدة طويلة تُقتطع بعلامة، ولا تُكسّر
                // حرفًا حرفًا في عرض ضيّق.
                style = MaterialTheme.typography.bodyMedium.copy(lineBreak = LineBreak.Heading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (showsValue && !metric.unit.isNullOrBlank()) {
                    "$valueText ${metric.unit}"
                } else {
                    valueText
                },
                style = MonoValueStyleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (showsValue) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        if (metric.trust != MaxDataTrust.Live && provenanceVisible(metric.trust)) {
            MaxMetricProvenance(metric)
        }
    }
}
