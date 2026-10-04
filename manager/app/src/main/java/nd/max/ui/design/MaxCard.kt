/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * The one card, and the one grid.
 *
 * Why this file exists (measured, not asserted): the tree held **38 separate `*Card`
 * composables** built from **20 different corner radii** and **14 padding values** written as
 * literals. The visible consequence is the audit's first complaint — four tiles in the home
 * "quick actions" deck whose icon, title and description sit at four different heights because
 * each tile was laid out on its own, in two independent `Row`s, with no height contract at all.
 *
 * So the card is not a style to copy, it is a composable to call, and the grid is the only
 * place that decides how many columns fit. Two rules are enforced rather than requested:
 *
 *  • **A title always occupies [MaxCardSpec.titleLines] lines**, so the description of every
 *    card in a row starts at the same y even when one title wraps and its neighbour does not.
 *  • **Cards in a row share one height** (`IntrinsicSize.Min` + `fillMaxHeight`), so a card with
 *    a two-word description cannot sit squashed next to one with a full sentence.
 *
 * **And one card, two sizes ([MaxCardSize]).** كانت البطاقة مقاسًا واحدًا: مصمّمة لمحتوى بوصف
 * وسطرين من العنوان، فتبدو **كبيرة أكثر من اللازم** في شبكة لا تحمل إلا كلمتين — وهي شكوى المالك
 * عن «منصة التحكم» بالحرف («بحجم متوسّط ليست كبيرة وليست صغيرة»). فالمقاس صار **وسيطًا يُمرَّر**
 * (‏`size =`)، لا أرقامًا تُنسخ في الشاشة: الأربعة القابلة للتبدّل تعيش في [MaxCardMetrics] ومعهما
 * رمزان — والرسم يقرأ واحدًا منهما، فلا تفترق نسختان من البطاقة يومًا.
 *
 * The grid drops to fewer columns instead of squeezing: truncating `Power…` happens because a
 * column was allowed to become narrower than a word, and no call site is permitted to trade
 * words for column count here.
 */
package nd.max.ui.design

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.floor

/**
 * One card's content, as data rather than as a layout.
 *
 * Keeping the payload a value object is what makes the grid able to guarantee equal heights: it
 * owns the arrangement, the caller only supplies words, a glyph and a tone.
 *
 * @param description optional supporting line. Absent renders nothing — never a placeholder and
 *        never a fabricated number (ADR-07).
 * @param tone semantic state, resolved to colour by [MaxTone.content]. Accent is the default
 *        because a deck tile is normally *the thing you came to press*, not a status.
 */
@Immutable
data class MaxCardData(
    val title: String,
    val icon: ImageVector,
    val description: String? = null,
    val tone: MaxTone = MaxTone.Accent,
    val onClick: (() -> Unit)? = null,
)

/**
 * A single card: icon container, title, optional description, on one border and one radius.
 *
 * Prefer [MaxCardGrid] when there is more than one card — a card laid out alone cannot be made
 * to match its neighbours, which is exactly how the deck this replaced drifted.
 */
@Composable
fun MaxCard(
    data: MaxCardData,
    modifier: Modifier = Modifier,
    size: MaxCardSize = MaxCardSize.Regular,
) = MaxCardSurface(
    title = data.title,
    icon = data.icon,
    description = data.description,
    tone = data.tone,
    onClick = data.onClick,
    metrics = size.metrics,
    modifier = modifier,
)

/** Primitive overload, so an existing screen can adopt the card without building a [MaxCardData]. */
@Composable
fun MaxCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null,
    tone: MaxTone = MaxTone.Accent,
    onClick: (() -> Unit)? = null,
    size: MaxCardSize = MaxCardSize.Regular,
) = MaxCardSurface(
    title = title,
    icon = icon,
    description = description,
    tone = tone,
    onClick = onClick,
    metrics = size.metrics,
    modifier = modifier,
)

@Composable
private fun MaxCardSurface(
    title: String,
    icon: ImageVector,
    description: String?,
    tone: MaxTone,
    onClick: (() -> Unit)?,
    metrics: MaxCardMetrics,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val content = tone.content()
    val shape = RoundedCornerShape(MaxCardSpec.radius)
    val interaction = remember { MutableInteractionSource() }

    var surface = modifier
        .defaultMinSize(minHeight = metrics.minHeight)
        .clip(shape)
        .background(scheme.surfaceContainerLow)
        .border(MaxCardSpec.borderWidth, content.copy(alpha = MaxAlpha.border), shape)

    if (onClick != null) {
        surface = surface.clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            role = Role.Button,
            onClick = onClick,
        )
    }

    Column(
        modifier = surface.padding(metrics.padding),
        verticalArrangement = Arrangement.spacedBy(MaxCardSpec.gap),
    ) {
        Box(
            modifier = Modifier
                .size(metrics.iconContainer)
                .clip(RoundedCornerShape(MaxRadius.control))
                .background(content.copy(alpha = MaxAlpha.toneContainer)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(metrics.iconGlyph),
            )
        }

        // `minLines` is the alignment contract, not decoration: it reserves the slot so the
        // description below it cannot ride up on a card whose title happened to fit on one line.
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
            minLines = MaxCardSpec.titleLines,
            maxLines = MaxCardSpec.titleLines,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
        )

        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                minLines = 1,
                maxLines = MaxCardSpec.descriptionLines,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Cards in a responsive grid of between [minColumns] and [maxColumns] columns.
 *
 * The column count is derived from the width actually available, not from a device class: a card
 * narrower than [MaxCardSpec.minColumnWidth] cannot wrap a normal word, so the grid drops a column
 * instead. A four-tile deck therefore reads as two rows of two on a phone, and on a very narrow one
 * as four full-width rows — never as four truncated labels.
 *
 * **[minColumns] is the counterweight, and it exists because the width heuristic alone got a deck
 * wrong.** أبلغ المالك أن بطاقات «منصة التحكم» ظهرت **أربعًا مُكدّسة**، والمطلوب اثنتان بجانب
 * بعضهما: فالمقاس قال «عمود واحد» لأن الحاوية أضيق من ضِعف [MaxCardSpec.minColumnWidth]، بينما
 * الليبلات هناك كلمتان قصيرتان لا تنكسران في نصف العرض أصلًا. فالحاوية التي **تعرف** أن محتواها
 * قصير تفرض [minColumns]، ويبقى المقاس هو الذي يقرّر فوقه.
 *
 * Not lazy: these grids are fixed, small decks. A screen enumerating an unbounded collection should
 * keep using `LazyVerticalGrid` and call [MaxCard] with the grid's own sizing.
 */
@Composable
fun MaxCardGrid(
    cards: List<MaxCardData>,
    modifier: Modifier = Modifier,
    maxColumns: Int = 2,
    minColumns: Int = 1,
    size: MaxCardSize = MaxCardSize.Regular,
) {
    if (cards.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val spacing = MaxCardSpec.gridSpacing
        // `.value` explicitly: this must be plain arithmetic on numbers, not an operator whose
        // existence depends on which Compose version defines a `Dp / Dp` overload.
        val columns = remember(maxWidth, maxColumns, minColumns, spacing, cards.size) {
            val perColumn = MaxCardSpec.minColumnWidth.value + spacing.value
            val usable = maxWidth.value + spacing.value
            val fits = floor(usable / perColumn).toInt()
            // `minColumns` يُطبَّق بعد القياس لا قبله، ولا يتجاوز عدد البطاقات: صفّ فارغ في النهاية
            // ليس شبكة، بل فراغ يُقرأ كخطأ.
            val floorColumns = minColumns.coerceAtMost(maxColumns).coerceAtMost(cards.size)
            fits.coerceIn(floorColumns, maxColumns)
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            cards.chunked(columns).forEach { row ->
                Row(
                    // One height for the whole row: the tallest card sets it, everyone fills it.
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    row.forEach { card ->
                        MaxCard(card, Modifier.weight(1f).fillMaxHeight(), size)
                    }
                    // A short last row keeps its cards at one-per-column width. Stretching the
                    // survivor across the full row would make it a visibly different component.
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
