/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * قشرة البطاقة — نصفُ نظام البطاقات الذي كان غائبًا.
 *
 * **التشخيص الذي وُلدت منه:** الطلب (§٢) يسرد ما يجب أن يكون واحدًا في كل بطاقة: نصف القطر ·
 * عرض الحدّ · لونه · الخلفية · الحشو الداخلي. و`MaxCard` يفرض ذلك **على بطاقة
 * «أيقونة ← عنوان ← وصف»** فقط. أمّا بقية البطاقات فمحتواها ليس هذا الشكل أصلًا — مقياس دائريّ،
 * مخطط، جدول، معاينة سمة — وهي **٣٤ بطاقة من ٣٨** ترسم قشرتها بأيدى أصحابها:
 *
 *     Card(shape = RoundedCornerShape(MaxCardSpec.radius), containerColor = surfaceContainerHigh) { … }
 *
 * فحيث **لا يمكن** توحيد المحتوى (ولن يُشوَّه ليُوحَّد — مقياسٌ ليس أيقونة)، تُوحَّد **القشرة**:
 * شكل واحد، وحدّ واحد، وخلفية واحدة، وحشو واحد. وهذا هو ما يسمّى في §٢ «reusable values» وفي
 * §١٢ «prefer reusable components over duplicated UI implementations».
 *
 * **وحدّها المُعلن:** تُوحَّد الهندسة ولا تُوحَّد الكثافة. بطاقةٌ محتواها جدولٌ من ٩ صفوف تبقى
 * أطول من بطاقةٍ محتواها سطر — وهذا مقصود، لأن تسوية الارتفاع بين محتويين مختلفين تعني حشوًا
 * فارغًا (والحشو الفارغ نقصُ معلومة لا اتساق).
 */
package nd.max.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import nd.max.ui.design.MaxCardSpec
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import nd.max.ui.component.maxPressMotion
import nd.max.ui.component.rememberAnimationsEnabled

/**
 * قشرة بطاقة واحدة: شكل واحد، وحدّ واحد، وخلفية واحدة، وحشو واحد.
 *
 * @param container الخلفية. الافتراضيّ `surfaceContainerLow` — نفس ما تستعمله [MaxCard]، لأن
 *   الشكلين يجب أن يقعا على نفس الطبقة البصرية لا أن يبدو أحدهما أرفع من الآخر.
 * @param accent لون تمييزيّ اختياريّ؛ يُضفي أثرًا على الحدّ وحده (كما في [MaxCard]) ولا يبدّل
 *   الخلفية — حتى لا يصير «مميّز» و«مُنبَّه» شيئًا واحدًا.
 * @param minHeight أرضية الارتفاع. تُمرَّر فقط حيث توجد شبكة تحتاج تساويًا؛ غيابها يعني أن
 *   البطاقة تقيس بمحتواها.
 * @param onClick يجعل القشرة **كلها** الهدف القابل للضغط، لا عنصرًا داخلها — وهو ما يمنع
 *   «زرّ صغير داخل بطاقة كبيرة» الذي يقيسه `MaxSize.minTouchTarget`.
 */
@Composable
fun MaxCardShell(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    // الشفافية من `MaxAlpha` لا قيمة بلا اسم: كانت القوائم المجمَّعة ترسم `0.32f` وهو **فوق**
    // `borderStrong` (0.28f) ولا وجود له في السلّم — أي أن «أهدأ حدّ» كان أعلى الثلاثة صوتًا.
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaxAlpha.borderStrong),
    contentPadding: Dp = MaxCardSpec.padding,
    minHeight: Dp? = null,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(MaxCardSpec.gap),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(MaxCardSpec.radius)
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val animationsEnabled = rememberAnimationsEnabled()

    val targetStroke = if (accent != null) {
        if (isPressed && onClick != null) accent.copy(alpha = MaxAlpha.borderStrong)
        else accent.copy(alpha = MaxAlpha.edgeLight)
    } else {
        if (isPressed && onClick != null) MaterialTheme.colorScheme.outlineVariant
        else borderColor
    }
    val animatedStroke by animateColorAsState(
        targetValue = targetStroke,
        animationSpec = tween(MaxDuration.quick),
        label = "cardShellStroke"
    )
    val stroke = if (animationsEnabled) animatedStroke else targetStroke

    var surface = modifier
        .then(if (minHeight != null) Modifier.defaultMinSize(minHeight = minHeight) else Modifier)
        .then(if (onClick != null && animationsEnabled) Modifier.maxPressMotion(interaction, pressedScale = 0.985f) else Modifier)
        .clip(shape)
        .background(container)
        .border(BorderStroke(MaxCardSpec.borderWidth, stroke), shape)

    if (onClick != null) {
        surface = surface.clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            role = Role.Button,
            onClick = onClick,
        )
    }

    Column(
        modifier = surface.padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}
