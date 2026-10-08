/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * «منصة التحكم» — بطاقات الرئيسية التي تُفتح بضغطة (طلب المالك: تحسين التصميم وتصغير الأقسام).
 *
 * الرسم فقط: البطاقات تأتيه من `homeDeckSelection` (٤ إلى ٦)، وزرّ الإعداد في رأس القسم نفسه.
 * والقسم بلاطة **صفّ** من نظام التصميم (`DESIGN.md` · «Rows and list items»): أيقونة بحاوية
 * الصفوف `MaxSize.rowIconContainer`، وعنوان واحد، وحشوة الصفوف `MaxSpace.rowPaddingHorizontal/Vertical`.
 * فلا وصف يضخّم البلاطة، ولا رقم مكتوب بيد. ولونها من نغمتها `MaxTone.content()` لا من النظام الديناميكي.
 */
package nd.max.ui.mainscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import nd.max.R
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralSectionHeader
import nd.max.ui.component.neuralClickable
import nd.max.ui.component.neuralPalette
import nd.max.ui.design.MaxAlpha
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.content

@Composable
internal fun CommandDeck(
    entries: List<HomeDeckEntry>,
    onOpen: (HomeDeckEntry) -> Unit,
    onConfigure: () -> Unit,
) {
    val palette = neuralPalette()
    NeuralPanel {
        NeuralSectionHeader(
            title = stringResource(R.string.home_quick_actions),
            caption = stringResource(R.string.home_quick_actions_desc),
            accent = palette.accentAlt,
            trailing = {
                IconButton(onClick = onConfigure) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.home_deck_settings_cd),
                        tint = palette.muted,
                    )
                }
            },
        )
        // صفوف من بلاطتين بارتفاع واحد: `IntrinsicSize.Min` يعطي الصفّ ارتفاع أطوله، و`fillMaxHeight`
        // يمدّ البلاطة إليه. والصفّ المنفرد يترك خانة فارغة بالعرض نفسه فلا تتمدّد بلاطته.
        entries.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
            ) {
                pair.forEach { entry ->
                    DeckRowTile(
                        icon = entry.icon,
                        title = stringResource(entry.titleRes),
                        accent = entry.tone.content(),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onOpen(entry) },
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** بلاطة صفّ: أيقونة وعنوان، على لون البلاطة بحدّ شعريّ بلون نغمتها. */
@Composable
private fun DeckRowTile(
    icon: ImageVector,
    title: String,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val p = neuralPalette()
    val shape = RoundedCornerShape(MaxRadius.row)
    Row(
        modifier
            .neuralClickable(onClick, role = Role.Button)
            .clip(shape)
            .background(p.tile)
            .border(MaxSize.hairlineBorder, accent.copy(alpha = MaxAlpha.border), shape)
            .padding(horizontal = MaxSpace.rowPaddingHorizontal, vertical = MaxSpace.rowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeuralIconChip(icon, accent, size = MaxSize.rowIconContainer)
        Spacer(Modifier.width(MaxSpace.sm))
        Text(
            title,
            color = p.text,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
