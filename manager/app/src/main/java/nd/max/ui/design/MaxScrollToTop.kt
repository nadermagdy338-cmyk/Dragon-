/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxManager Design Language — العودة إلى أعلى القائمة.
 *
 * **لماذا وُجد هذا الملفّ (والعطب مقيس لا مُتخيَّل):** صفحات MaxManager الطويلة — السجلات،
 * والعمليات، ومناطق الحرارة، وقائمة التطبيقات — تُمرَّر مئات الصفوف، وقيس في طبقة الواجهة كلّها
 * فلم يوجد **نداء واحد** إلى `animateScrollToItem` ولا إلى `animateScrollTo`. أي أنّ القارئ الذي
 * ينزل إلى آخر قائمة عمليات ثمّ يريد البحث أوّل السجلّ يعود إليه بالسحب وحده، ولا شيء في الواجهة
 * يقول له إنّ هناك طريقًا أقصر.
 *
 * وهو ترجمة نمط مأخوذ من مراجعة أدلّة الـREADME الشهيرة التي وجّه المالك إلى تعلّمها:
 * «TOC and Back to top links for easy navigation» (في `awesome-readme`). والصفحة الطويلة في
 * الـREADME تعالجه بوصلة، والقائمة الطويلة في تطبيق تعالجه بالزرّ نفسه — **في الطابق المشترك**
 * (`MaxListScreen`) مرّة واحدة، فلا يبقى على اثنتين وأربعين شاشة أن تتفق على موضعه.
 *
 * **وحدوده معلنة، وهي أهمّ من الزرّ:**
 *
 *   ① **لا يظهر في أوّل الصفحة أبدًا.** زرٌّ ثابت في مكان لا يصلح له ضجيج لا مساعدة؛ ولا يظهر
 *      قبل تجاوز حدّ مضبوط ([RowsBeforeTopButton]) — والشرط نفسه هو ما يجعله معلومة لا زينة.
 *      وهذا هو الفرق المقصود عن زرّ «إلى الأعلى» الدائم في تطبيقات كثيرة.
 *   ② **لا يُزاح به شيء.** هو في خانة الزرّ العائم التي أعدّها `Scaffold` أصلًا، فوق الزرّ الذي
 *      تمرّره الشاشة إن مرّرت واحدًا — لا داخل الجسم، ولا فوق صفّ، ولا يغيّر ترتيب أيّ محتوى.
 *   ③ **وارتفاعه من الطبقة لا من رقم جديد:** `MaxSize.minTouchTarget`، فحدّ اللمس يبقى واحدًا
 *      في التطبيق كلّه.
 *   ④ **ويُرفع بمقدار شريط التنقّل العائم** حين يكون الشريط على الصفحة، وإلا سقط الزرّ تحته
 *      (والشريط يُرسم **فوق** مضيف التنقّل لا بجانبه — وهو سبب وجود `LocalFloatingBottomBarHeight`
 *      ابتداءً). وهذا أثر جانبيّ لم يُقَس على جهاز في هذه الجولة، ومكتوب هنا لا مسكوت عنه.
 */
package nd.max.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import nd.max.R

/**
 * عدد صفوف القائمة الذي يظهر بعده الزرّ.
 *
 * وليس رقمًا عشوائيًّا: صفّ القائمة في هذه الشاشات لا يقلّ عن [MaxSize.minTouchTarget]، فثمانية
 * صفوف تعني أنّ القارئ تجاوز نحو أربع شاشات تمرير — أي أنّه **بَعُد فعلًا** عن أوّلها. وتحت ذلك
 * يكون الزرّ أسرع من الحاجة إليه.
 */
private const val RowsBeforeTopButton = 8

/**
 * زرّ العودة إلى أوّل القائمة.
 *
 * سطح دائريّ بحدّ شعريّ **من نفس مفردات الطبقة** ([MaxAlpha.border] و[MaxSize.hairlineBorder])
 * ولون المحتوى `primary` — فحالته اللونية حالة سمة المستخدم كبقيّة عناصر التحكّم، لا لون ثابت
 * اخترع لهذه المناسبة. والنصّ البديل من الموارد (وهو مترجم في `values-ar` كما تشترط ADR-14)،
 * لأنّ زرًّا بلا نصّ بديل زرٌّ غير موجود لقارئ الشاشة.
 */
@Composable
fun MaxScrollToTopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = scheme.surfaceVariant.copy(alpha = MaxAlpha.toneContainerStrong),
        contentColor = scheme.primary,
        border = BorderStroke(
            MaxSize.hairlineBorder,
            scheme.outline.copy(alpha = MaxAlpha.border)
        )
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(MaxSize.minTouchTarget)
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = stringResource(R.string.cd_back_to_top),
                modifier = Modifier.size(MaxSize.iconGlyph)
            )
        }
    }
}

/**
 * هل بعُدت القائمة عن أوّلها بما يكفي ليستحقّ الزرّ الظهور؟
 *
 * `derivedStateOf` لا `state.firstVisibleItemIndex > n` مباشرةً: الفهرس يتغيّر مع كلّ بكسل تمرير،
 * وقراءته مباشرةً في جسم الـcomposable تُعيد تركيب الصفحة كلّها عند كلّ تغيير. أمّا `derivedStateOf`
 * فلا تُبلّغ إلا حين **تنقلب النتيجة** — أي عند عبور الحدّ مرّة واحدة في كل اتجاه.
 */
@Composable
fun rememberListTopControl(state: LazyListState): Boolean {
    val visible by remember(state) {
        derivedStateOf { state.firstVisibleItemIndex > RowsBeforeTopButton }
    }
    return visible
}

/**
 * خانة الزرّ العائم: زرّ العودة إلى الأعلى فوق زرّ الشاشة (إن وُجد)، وكلاهما عن يمين النهاية.
 *
 * وتُرفع بمقدار [LocalFloatingBottomBarHeight] حين يكون شريط التنقّل العائم على الصفحة؛ وهذا
 * الحساب يمرّ من [floatingBottomBarPadding] نفسه الذي تمرّ منه أجسام الصفحات، فلا يصير للارتفاع
 * الواحد حسابان.
 */
@Composable
internal fun ScrollToTopSlot(
    visible: Boolean,
    onTop: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.padding(bottom = floatingBottomBarPadding(MaxSpace.sm)),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)
    ) {
        if (visible) {
            MaxScrollToTopButton(onClick = onTop)
        }
        content()
    }
}
