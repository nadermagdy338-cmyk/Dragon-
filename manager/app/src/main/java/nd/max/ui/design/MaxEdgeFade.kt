/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * تظليل حواف الصفوف القابلة للتمرير الأفقي — مكوّن مشترك واحد لكل التطبيق.
 *
 * **العطب الذي وُلد منه.** الصفّ القابل للتمرير يقع داخل هامش الصفحة (`MaxSpace.gutter`)، فحدّ القصّ
 * الذي يرسمه الصفّ يقع عند الهامش نفسه، ويُقصّ المقطع المتجاوز عنده بخطّ مستقيم (تبويبات الإعدادات
 * والجهاز). وتظليل الحافة وحده لا يكفي: إن ظُلِّل داخل حدّ الصفّ اختفى أوّل عنصر وهو في مكانه، كما كان في
 * شاشة الثيمات.
 *
 * **العلاج** من ثلاث قطع تعمل معًا:
 *  1. **امتداد إلى الحافة** (`maxBleed`): الصفّ يتجاوز الهامش إلى حافة الشاشة، فلا حدّ قصّ داخل الصفحة.
 *  2. **موضع الراحة عند الهامش** (`contentPadding` و[MaxScrollRow]): أوّل عنصر يستقرّ عند الهامش نفسه،
 *     فلا تتحرّك العناوين فوقه.
 *  3. **تظليل الطرفين** (`maxEdgeFade`): قناع متدرّج يشفّ الحافتين، فالمقطع المتجاوز يذوب عند حافة الشاشة.
 */
package nd.max.ui.design

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/**
 * يمدّ العنصر أفقيًّا إلى حافة الشاشة بتجاوز الهامش [by] على كلّ جانب.
 *
 * ترتيب السلسلة مهمّ: `fillMaxWidth().maxBleed(by).maxEdgeFade(…)`. القناع يقع **داخل** الامتداد، فحدّه
 * حدّ الشاشة لا حدّ الهامش. ولا يُستعمل إلا داخل عنصر يملك هذا الهامش فعلًا، وإلا انزاح العنصر خارج الشاشة.
 */
fun Modifier.maxBleed(by: Dp = MaxSpace.gutter): Modifier = maxBleed(start = by, end = by)

/**
 * امتداد بجانبين مختلفين. [start] و[end] يتبعان اتجاه التخطيط: في العربية يكون [end] على اليسار، فيمتدّ
 * العنصر إلى حافة الشاشة الصحيحة في الحالتين. مفيد للوحة لا تمتدّ إلا نحو الحافة، إذ الجهة الأخرى تلامس لوحة أخرى.
 */
fun Modifier.maxBleed(start: Dp, end: Dp): Modifier = layout { measurable, constraints ->
    val rtl = layoutDirection == LayoutDirection.Rtl
    val leftPx = (if (rtl) end else start).roundToPx().coerceAtLeast(0)
    val rightPx = (if (rtl) start else end).roundToPx().coerceAtLeast(0)
    val totalPx = leftPx + rightPx
    if (totalPx <= 0 || !constraints.hasBoundedWidth) {
        val plain = measurable.measure(constraints)
        return@layout layout(plain.width, plain.height) { plain.place(0, 0) }
    }
    val wider = constraints.copy(
        minWidth = constraints.minWidth + totalPx,
        maxWidth = constraints.maxWidth + totalPx,
    )
    val placeable = measurable.measure(wider)
    layout(constraints.maxWidth, placeable.height) { placeable.place(-leftPx, 0) }
}

/**
 * قناع متدرّج يشفّ طرفي الصفّ بعرض [width] ويترك الوسط كما هو.
 * يُطبَّق على الصفّ نفسه لا على عناصره، فيشمل كل ما داخله بالتساوي.
 */
fun Modifier.maxEdgeFade(width: Dp = MaxSpace.gutter): Modifier = maxEdgeFade(start = width, end = width)

/**
 * تظليل بعرضين مختلفين للجانبين، بالاتجاه نفسه لـ[maxBleed]. الجانب الذي لا يُظلَّل (عرض صفر) يبقى صلبًا.
 */
fun Modifier.maxEdgeFade(start: Dp, end: Dp): Modifier = graphicsLayer(
    compositingStrategy = CompositingStrategy.Offscreen,
).drawWithContent {
    drawContent()
    if (size.width <= 0f) return@drawWithContent
    val rtl = layoutDirection == LayoutDirection.Rtl
    val leftFrac = ((if (rtl) end else start).toPx() / size.width).coerceIn(0f, 0.5f)
    val rightFrac = ((if (rtl) start else end).toPx() / size.width).coerceIn(0f, 0.5f)
    if (leftFrac <= 0f && rightFrac <= 0f) return@drawWithContent
    drawRect(
        brush = Brush.horizontalGradient(
            0f to (if (leftFrac > 0f) Color.Transparent else Color.Black),
            leftFrac to Color.Black,
            1f - rightFrac to Color.Black,
            1f to (if (rightFrac > 0f) Color.Transparent else Color.Black),
        ),
        blendMode = BlendMode.DstIn,
    )
}

/**
 * صفّ أفقي قابل للتمرير بالشكل المعتمد: يمتدّ إلى حافة الشاشة، ويرتاح أوّله وآخره عند [edge]، ويظلّل طرفيه.
 * يحلّ محلّ `Row(…horizontalScroll…)` في صفوف الشرائح والفلاتر، فلا يُكتب قصّ يدويّ في أيّ شاشة.
 *
 * [bleed]   الهامش الذي يقف الصفّ داخله (هامش الصفحة `MaxSpace.gutter` في شاشات القوائم والهياكل).
 * [edge]    المسافة التي يرتاح عندها أوّل عنصر وآخره، وعرض التظليل. الافتراضي هامش الصفحة نفسه.
 * [spacing] الفاصل بين العناصر، وهو يُطرح من الحشو حتى يستقرّ أوّل عنصر عند [edge] بالضبط.
 */
@Composable
fun MaxScrollRow(
    modifier: Modifier = Modifier,
    bleed: Dp = MaxSpace.gutter,
    edge: Dp = MaxSpace.gutter,
    spacing: Dp = MaxSpace.sm,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    // `Arrangement.spacedBy` يضيف الفاصل بين كل عنصرين، ومنها الفاصل الأول الذي يسبق المحتوى.
    val inset = (edge - spacing).coerceAtLeast(MaxSpace.hairline)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .maxBleed(bleed)
            .maxEdgeFade(edge)
            .horizontalScroll(scroll),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Spacer(Modifier.width(inset))
        content()
        Spacer(Modifier.width(inset))
    }
}
