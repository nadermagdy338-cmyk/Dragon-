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
    // الحصيلة تُقبض في قيمة محلية: `when` بلا موضوع لا يُضيّق النوع داخل فرعه في كل الإعدادات.
    val freedMb = boost.outcome?.freedMb
    val message: String? = when {
        // أثناء التنفيذ يقول الزرّ نفسه «جارٍ…»، فلا سطر ثانٍ يكرّر.
        boost.running -> null
        boost.blocked -> stringResource(R.string.home_memory_boost_blocked)
        freedMb != null -> stringResource(R.string.home_memory_boost_freed, "$freedMb MB")
        boost.outcome != null -> stringResource(R.string.home_memory_boost_none)
        else -> null
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.md),
        ) {
            HomeActionButton(
                text = stringResource(
                    if (boost.running) R.string.home_memory_boost_running else R.string.home_memory_boost,
                ),
                icon = Icons.Rounded.Memory,
                filled = !boost.blocked,
                accent = p.muted,
                onClick = onBoost,
                modifier = Modifier.weight(1f),
            )
            HomeActionButton(
                text = stringResource(MaxDestination.Control.titleRes),
                icon = MaxDestination.Control.icon,
                filled = false,
                accent = p.accent,
                onClick = onControl,
                modifier = Modifier.weight(1f),
            )
        }
        if (message != null) {
            Text(
                message,
                color = if (freedMb != null && !boost.blocked) p.ok else p.muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
