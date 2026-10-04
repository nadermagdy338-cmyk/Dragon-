/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * أُخرج من `NeuralDashboardKit.kt` لا لأنّه أجمل في ملفّ وحده، بل لأن سقف حجم الملفّ
 * (`OVERSIZE_LINES` في `tools/code_health.py`) صار حدًّا مفروضًا وأمسك التجاوز: الملفّ
 * بلغ ١٠٣٥ سطرًا بعد تقوية الوسم، والسقف ١٠٠٠. فالاختيار بين توسيع السقف وتفكيك الملفّ،
 * والسقف موجود لمنع الأول. ولا سلوك تغيّر — نقل نصًّا.
 *
 * **و`compact` أُضيفت بأمر المالك** («قم بتصغير OPEN DEVICE OVERVIEW زر في الشاشة الرئيسية»)،
 * وعطبها مقيس لا مذوق: الشرط الإتاحي يفرض على كل حبّة **قابلة للضغط** ارتفاعًا أدنى **٤٨dp**
 * (`MaxSize.minTouchTarget`)، وكان ذلك الحدّ يُطبَّق **على شكل الحبّة نفسه** (القصّ والخلفية
 * والحدّ) لا على صندوق مستقلّ عنها. فحبّة بنصّ ١١sp ارتفاعها الطبيعيّ ≈٢٤dp كانت **تُرسم ٤٨dp**:
 * أي أنّ ما يراه المستخدم ضِعف ما صُمِّم. و`compact` تفصل الأمرين: صندوق اللمس ٤٨dp يبقى
 * (السياسة §١٣ لم تُمسّ)، والحبّة المرئيّة تعود إلى مقاسها المشدود أو أقلّ.
 *
 * ولم تُعمَّم على الأربع القابلة للضغط في الرئيسية: الأمر سمّى زرًّا واحدًا، وتغيير الثلاثة
 * الأخرى عملٌ لم يُطلب (ADR-18). والمفتاح واحد إن طُلب لاحقًا: `compact = true`.
 *
 * **ورُفع المضغوط ٤٪ بأمر المالك (الجولة ٢٠٧):** «وزر معلومات الجهاز أكبر بنسبة ٤٪». والمعامل
 * ([COMPACT_SCALE]) هنا لا في نداء الرئيسية، لأنّه **مقاس المفتاح** لا استثناء في شاشة: زرٌّ
 * يُطلب في مكان آخر بالمقاس نفسه يجده جاهزًا، ورقمٌ يُكتب في الشاشة يصير مقاسًا ثانيًا لا يعلمه
 * غيرها. **ويُطبَّق على الستّة كلها وعلى النصّ:** تكبير الحشو وحده يوسّع الورقة حول كلمتها بلا
 * أن تكبر الكلمة — أي «أكبر» تُقرأ فراغًا لا زرًّا.
 *
 * **وما لم يُمسّ: صندوق اللمس.** ٤٨dp يبقى ٤٨dp — المعامل يحرّك ما **يُرى** لا ما **يُلمس**،
 * وهذا هو الفرق نفسه الذي وُلد منه `compact`: ما يُرى يُقاس بمقاسه، وما يُلمس يُقاس بالسياسة
 * ‏(§١٣ · `MaxSize.minTouchTarget`).
 */
package nd.max.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace

/**
 * معامل المقاس المضغوط: ‏+٤٪ بأمر المالك («وزر معلومات الجهاز أكبر بنسبة ٤٪»).
 *
 * ويُطبَّق على **كل** أرقام المقاس المضغوط (الحشو الأفقي والرأسي، الفاصل، النقطة، الأيقونة،
 * السهم) وعلى حجم النصّ — فالطلب مقاس لا زاوية واحدة منه. والقيمة في النموذج نفسه لأنّها تُقرأ
 * في اختبار (‏`UserScaleRequestsTest`) : رقم مجرّد في وسط دالّة لا يُقاس إلا بلقطة، وهذا رقم يُقاس
 * بقراءة.
 */
private const val COMPACT_SCALE = 1.04f

/**
 * Status pill — ومعه **دلالة الباب** عند الطلب.
 *
 * `navigates` سهمٌ **مُتّجه مع اتجاه اللغة** (`AutoMirrored`) يُضاف في نهاية الوسم فيصير
 * الشكل «اسم ← مكان»، وهو العُرف نفسه في الشاشات الأخرى. والوسم يبقى بلا سهم في المواضع
 * التي **تفعل** ولا **تنتقل** (مثل «إعادة المحاولة») — لأن السهم هناك كذب.
 *
 * (مدخل Max AI في الرئيسية لم يعد يمرّ من هنا: له [MaxAiEntryButton].)
 *
 * @param compact حبّة **أصغر تُرى** مع **صندوق لمس ٤٨dp لا يُمسّ**. تُستعمل حيث تكون الحبّة
 *   فعلًا لا شارةً مصاحبة، فيُقاس ظاهرها بمقاسها لا بحدّ الإتاحة.
 * @see COMPACT_SCALE معامل المقاس المضغوط (‏+٤٪ بأمر المالك).
 */
@Composable
fun NeuralPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    dot: Boolean = false,
    icon: ImageVector? = null,
    navigates: Boolean = false,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    /*
     * مقاييس المقاسين في مكان واحد، والمضغوط يستعير **رموز المسافات** لا أرقامًا جديدة:
     * `tools/design_tokens.py --assert` يمنع أيّ حرفيّ يتجاوز سقفه، والنصيحة في رسالته نفسها
     * أن يُضاف الرقم الجديد إلى طبقة الرموز — وهذه هي (`MaxSpace.sm` = ٨ · `MaxSpace.xs` = ٤).
     */
    // والمعامل `1f` في غير المضغوط: القياسيّ لا يتغيّر بهذا الأمر، والضرب في واحد لا يحرّك شيئًا
    // (وهو أصدق من فرعين: فرعٌ ثانٍ يعني مقاسًا ثانيًا يُنسى عند أول تعديل).
    val scale = if (compact) COMPACT_SCALE else 1f
    val hPad = (if (compact) MaxSpace.sm else 10.dp) * scale
    val vPad = (if (compact) MaxSpace.xs else 5.dp) * scale
    val gap = (if (compact) MaxSpace.xs else 6.dp) * scale
    val dotSize = (if (compact) 5.dp else 6.dp) * scale
    val iconSize = (if (compact) 12.dp else 13.dp) * scale
    val arrowSize = (if (compact) 12.dp else 14.dp) * scale

    val capsule = Modifier
        .clip(CircleShape)
        .background(if (filled) accent.copy(alpha = .16f) else Color.Transparent)
        .border(BorderStroke(1.dp, accent.copy(alpha = if (filled) .42f else .28f)), CircleShape)
        .padding(horizontal = hPad, vertical = vPad)

    val body: @Composable (Modifier) -> Unit = { shape ->
        PillBody(
            text = text,
            accent = accent,
            dot = dot,
            icon = icon,
            navigates = navigates,
            gap = gap,
            dotSize = dotSize,
            iconSize = iconSize,
            arrowSize = arrowSize,
            scale = scale,
            modifier = shape,
        )
    }

    if (onClick == null) {
        body(modifier.then(capsule))
        return
    }

    val clickable = modifier.neuralClickable(onClick, role = Role.Button)
    if (!compact) {
        // السلوك القائم كما هو: الحبّة نفسها هي صندوق اللمس، بحدّه الأدنى ٤٨dp.
        body(clickable.heightIn(min = MaxSize.minTouchTarget).then(capsule))
        return
    }
    /*
     * المضغوط: الحدّ الأدنى على **حاوية** حول الحبّة لا عليها، فتبقى ٤٨dp منطقة ضغط شفّافة
     * وسطها الحبّة بمقاسها. والفراغ الزائد يوزّع على الجانبين (`Center`) فلا تُزاح الحبّة
     * عن موضعها في تخطيط الأب.
     */
    Box(
        clickable.heightIn(min = MaxSize.minTouchTarget),
        contentAlignment = Alignment.Center,
    ) {
        body(capsule)
    }
}

/** جسم الحبّة وحده — مفصول عن حاويتها فيُعاد استعماله في المقاسين. */
@Composable
private fun PillBody(
    text: String,
    accent: Color,
    dot: Boolean,
    icon: ImageVector?,
    navigates: Boolean,
    gap: Dp,
    dotSize: Dp,
    iconSize: Dp,
    arrowSize: Dp,
    /** معامل المقاس للوسم وحده: النصّ ليس `Dp` فيحتاج العامل صريحًا ([COMPACT_SCALE]). */
    scale: Float,
    modifier: Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (dot) Box(Modifier.size(dotSize).clip(CircleShape).background(accent))
        if (icon != null) Icon(icon, null, Modifier.size(iconSize), tint = accent)
        Text(
            text,
            color = accent,
            style = TextStyle(fontSize = 11.sp * scale, lineHeight = 14.sp * scale),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        // السهم بعد النصّ لا قبله: القارئ يقرأ الاسم ثم يرى إلى أين — لا العكس.
        if (navigates) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(arrowSize),
                tint = accent,
            )
        }
    }
}
