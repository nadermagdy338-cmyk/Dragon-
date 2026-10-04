/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * شريط تبويبات واحد لكل التطبيق — بديلٌ عن `TabRow` لا تجميلٌ له.
 *
 * **العطب الذي وُلد منه.** أبلغ المالك أن تبويبات إعدادات التطبيق تُظهر `Gami…` و`Powe…`.
 * والسبب في تصميم `TabRow` نفسه لا في الليبلات: هي تقسم العرض المتاح **بالسوية** على عدد
 * التبويبات، فخمسة تبويبات على شاشة ٣٦٠dp تعطي كل تبويب ٦٤dp بعد الأيقونة والحدود — أقل من
 * عرض كلمة `Gaming`. ولا ينجو أي ليبل من ذلك بإعداد `maxLines`/`overflow`، لأن المشكلة أنها
 * **ضاقت الحاوية** لا أن النصّ طال.
 *
 * **والعلاج المعماري** ثلاث قيود محسومة في هذا الملف، فهي تُطبَّق على كل شريط في المستودع:
 *
 *  1. **العرض للتبويب لا للصفّ:** المقطع يأخذ عرضه الطبيعي (`softWrap = false`) فيستحيل أن
 *     يُقصّ — والشريط كله يتحرّك أفقيًّا إن زادت عن الشاشة. البديل الوحيد للنصّ المقصوص هو
 *     مساحة، وهي متوفّرة أفقيًّا ومعدومة داخل مقعد ثابت.
 *  2. **لا تصغير خطّ:** كُتب صراحةً في الطلب («do not simply reduce the font size»)، و`labelLarge`
 *     يبقى هو حجم المقطع — الفرق كله في الحاوية.
 *  3. **صندوق لمس ٤٨dp** (`MaxSize.minTouchTarget`) لكل مقطع، وإن بدا المقطع أقصر من ذلك.
 *
 * **وRTL بلا كود شرطيّ:** `LazyRow` تحترم `LocalLayoutDirection`، وكل حشو هنا منطقيّ
 * (`start`/`end`)، والحركة التلقائية إلى المقطع المختار تستعمل الفهرسة ذاتها في الاتجاهين.
 */
package nd.max.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** تبويب واحد: ليبل لا يُقصّ، وأيقونة اختيارية، وعدّاد اختياري. */
data class MaxTab(
    val label: String,
    val icon: ImageVector? = null,
    val badgeCount: Int = 0,
)

/**
 * شريط تبويبات قابل للتمرير الأفقي، بلا حدّ أعلى لعدد التبويبات.
 *
 * ولا يُوصف على أنه `TabRow`: محتوى المقطع يُقاس بعرضه الطبيعي، فالشريط يتجاوز الشاشة
 * ويمرّ بدل أن يضغط عناصره.
 */
@Composable
fun MaxTabStrip(
    tabs: List<MaxTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    if (tabs.isEmpty()) return
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    // المقطع المختار يُقرَّب إلى الشاشة عند التغيير: بلا هذا يصير تبويب مُختار خارج الشاشة
    // عنوانًا لحالة لا تُرى، خصوصًا في العربية حيث يبدأ الشريط من اليمين.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in tabs.indices) {
            runCatching { listState.animateScrollToItem(selectedIndex) }
        }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
        // الحشو داخل الشريط لا خارجه: المقطع الأول والأخير يبقيان قابلين للقراءة عند حافة
        // الشاشة، والحافة نفسها تأتي من هيكل الصفحة فلا تُضاعَف.
        contentPadding = PaddingValues(vertical = MaxSpace.xs),
    ) {
        items(
            count = tabs.size,
            key = { index -> tabs[index].label },
        ) { index ->
            val tab = tabs[index]
            MaxTabItem(
                tab = tab,
                selected = index == selectedIndex,
                accent = accent,
                onSelect = {
                    if (index != selectedIndex) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(index)
                    }
                },
            )
        }
    }
}

/** مقطع واحد. يُنادى من [MaxTabStrip] فقط — لا يُنسخ في شاشة. */
@Composable
private fun MaxTabItem(
    tab: MaxTab,
    selected: Boolean,
    accent: Color,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = MaxAlpha.toneContainerStrong) else scheme.surfaceContainerHigh,
        label = "maxTabContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) accent else scheme.onSurfaceVariant,
        label = "maxTabContent",
    )

    Row(
        modifier = Modifier
            .heightIn(min = MaxSize.minTouchTarget)
            .clip(RoundedCornerShape(MaxRadius.pill))
            .background(container)
            .border(
                width = MaxSize.hairlineBorder,
                color = if (selected) accent.copy(alpha = MaxAlpha.borderStrong) else scheme.outlineVariant,
                shape = RoundedCornerShape(MaxRadius.pill),
            )
            .selectable(selected = selected, role = Role.Tab, onClick = onSelect)
            .padding(horizontal = MaxSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tab.icon?.let { glyph ->
            Icon(
                imageVector = glyph,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(MaxSize.iconGlyphSmall),
            )
            Spacer(Modifier.width(MaxSpace.sm))
        }
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelLarge.copy(lineBreak = LineBreak.Heading),
            color = content,
            maxLines = 1,
            // ولا يُقرأ `softWrap = false` مع `Ellipsis` كتنازل: لا يُقصّ هنا شيء أبدًا، لأن غياب
            // الالتفاف يجعل النصّ يقيس بعرضه الحقيقي — والتجاوز يحمله تمرير الشريط.
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
        if (tab.badgeCount > 0) {
            Spacer(Modifier.width(MaxSpace.sm))
            Text(
                text = tab.badgeCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = content,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
