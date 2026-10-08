/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * صفّ الفعل السريع — **ما أستطيع أن أفعله الآن بضغطة** (سؤال الرئيسية الثالث، `MAX-MANAGER-LEVEL-UP.md` §5).
 *
 * زرّان لا أكثر، وكلاهما فعلٌ لا حالة (الحبّة في Max للحالة، والفعل زرّ بنصف قطر المجموعة):
 *  - **Boost**: يوقف التطبيقات المخبّأة ويقيس الذاكرة **قبل وبعد** (`MemoryBoostEngine`) — فالرقم
 *    الذي يظهر تحته محرَّرٌ فعلًا لا وعدًا. وهو مالك هذا الفعل الوحيد في الشاشة: كان له صفّ داخل
 *    مصفوفة الذاكرة فصار هنا، وبقاء الاثنين كان سيرسم الزرّ مرّتين.
 *  - **Control**: باب منصة التحكم (والتبويب السفلي هو الباب نفسه — الزرّ هنا يُقرأ فعلًا لا قائمة).
 *
 * **والزرّ لا يختفي عند نقص الصلاحية** (نصّ §10.3): يبهت ويقول سطر الحالة تحته ما ينقصه. من لا يرى
 * الزرّ لا يعرف أن الميزة موجودة، فيقرأ التطبيق كأنه بلا Boost.
 */
package nd.max.ui.mainscreens

import nd.max.ui.theme.MonoValueStyleMedium

import nd.max.ui.component.NeuralValue

import nd.max.ui.component.NeuralTile

import nd.max.ui.component.NeuralIconChip

import androidx.compose.foundation.layout.fillMaxHeight

import androidx.compose.foundation.layout.IntrinsicSize

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import nd.max.R
import nd.max.ui.component.neuralClickable
import nd.max.ui.component.neuralPalette
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.viewmodel.MemoryBoostState

private val ActionHeight = MaxSize.minTouchTarget

@Composable
internal fun HomeActionRow(
    boost: MemoryBoostState,
    onBoost: () -> Unit,
    onControl: () -> Unit,
) {
    val p = neuralPalette()
    // ما حرّره «تعزيز» فعلًا يظهر رقمًا على بلاطته، والحالة تُقال بالنص لا باللون وحده.
    val freedMb = boost.outcome?.freedMb?.takeIf { it > 0 }
    val boostAccent = when {
        boost.blocked -> p.warn
        freedMb != null -> p.ok
        else -> p.accent
    }
    val boostSupport = when {
        boost.running -> stringResource(R.string.home_memory_boost_running)
        boost.blocked -> stringResource(R.string.home_memory_boost_blocked)
        boost.outcome != null && freedMb == null -> stringResource(R.string.home_memory_boost_none)
        else -> stringResource(R.string.home_memory_boost_desc)
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
    ) {
        QuickActionTile(
            title = stringResource(R.string.home_memory_boost),
            icon = Icons.Rounded.Memory,
            accent = boostAccent,
            value = freedMb?.let { stringResource(R.string.home_memory_boost_freed, "$it MB") },
            support = boostSupport,
            onClick = onBoost,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        QuickActionTile(
            title = stringResource(MaxDestination.Control.titleRes),
            icon = MaxDestination.Control.icon,
            accent = p.accentAlt,
            value = null,
            support = stringResource(R.string.max_home_control_desc),
            onClick = onControl,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

/**
 * بلاطة فعل: أيقونة وعنوان، ورقم حين يوجد (ما حرّره التعزيز)، وسطر شرح. الارتفاع يأتي من الصفّ
 * نفسه (`IntrinsicSize.Min`) فالبلاطتان بارتفاع واحد مهما اختلف طول شرحهما.
 */
@Composable
private fun QuickActionTile(
    title: String,
    icon: ImageVector,
    accent: Color,
    value: String?,
    support: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val p = neuralPalette()
    NeuralTile(modifier = modifier, accent = accent, onClick = onClick, verticalSpacing = MaxSpace.sm) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeuralIconChip(icon, accent, size = MaxSize.rowIconContainer)
            Spacer(Modifier.width(MaxSpace.sm))
            Text(
                title,
                color = p.text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (value != null) {
            NeuralValue(value, style = MonoValueStyleMedium, color = accent, maxLines = 1)
        }
        Text(
            support,
            color = p.muted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * زرّ الفعل في الرئيسية — يُشاركه صفّ الفعل السريع وبطاقة التنظيف، فلا زرّان بمقاسين.
 *
 * [filled] = الفعل الأوّل في موضعه (لون الثيم الأساسي على نصّه المخصّص `onPrimary`)، وإلا زرّ
 * محدَّد بخطّ شعر بلون [accent]. والشكل بنصف قطر المجموعة لا حبّة: الحبّة في هذا التطبيق حالة.
 */
@Composable
internal fun HomeActionButton(
    text: String,
    icon: ImageVector,
    filled: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = neuralPalette()
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(MaxRadius.group)
    val contentColor = if (filled) scheme.onPrimary else p.text
    val iconColor = if (filled) scheme.onPrimary else accent
    val surface = if (filled) {
        Modifier.background(Brush.horizontalGradient(listOf(scheme.primary, scheme.primary.copy(alpha = .82f))))
    } else {
        Modifier
            .background(p.tile)
            .border(MaxSize.hairlineBorder, accent.copy(alpha = .32f), shape)
    }
    // الضغط (تصغير ٢٫٥٪ + تموّج الثيم) من `neuralClickable` نفسه المستعمل في بقية اللوحة، فلا
    // حركة ضغط ثانية تُكتب هنا. ويأتي أوّلًا في السلسلة ليشمل صندوق اللمس الشكل كلّه.
    Row(
        modifier
            .neuralClickable(onClick, role = Role.Button)
            .height(ActionHeight)
            .clip(shape)
            .then(surface)
            .padding(horizontal = MaxSpace.lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(MaxSize.iconGlyph), tint = iconColor)
        Spacer(Modifier.width(MaxSpace.sm))
        Text(
            text,
            color = contentColor,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
