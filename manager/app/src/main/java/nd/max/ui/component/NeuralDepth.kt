/**
 * Neural depth — ضوء وعمق المكتبة، في مكان واحد.
 *
 * كانت الألواح المسطّحة (تدرّج + حدّ رفيع) كافية حين كانت الشاشة مجموعة بطاقات رمادية. ولمّا
 * صارت المكتبة هي لغة كل الشاشات، صار الفرق بين «بطاقة» و«سطح حقيقي» في **الضوء**: مادة
 * تُضاء من أعلى فتبدو بارزة، لا مستطيل مصبوغ.
 *
 * وهذه الطبقة تحمل ثلاثة عناصر فقط، وكلها مشتقّة من لون المفتاح — **لا لون مثبّت واحد**:
 *
 *  1. **ظلّ مُلوَّن** (`shadow` بلون المفتاح لا أسود): الظلّ الأسود يبدو اتساخًا على سطح داكن
 *     مصبوغ، والظلّ الملوّن يبدو عمقًا. وهو أول ما يُشعر أن اللوحة تطفو فوق الخلفية.
 *  2. **هالة ركنية** (`glow`): تدرّجان شعاعيان — واحد من **ركن القراءة** وواحد خفيف من الركن
 *     المقابل. وهذا هو «تدرّج الصور المرجعية» بعينه: الأسطح هناك **مصبوغة بلون مفتاحها** لا
 *     محايدة، والهالة هي ما يعطيها ذلك الإحساس بالحجم. و«ركن القراءة» يعني **حافة البدء**
 *     الفعلية: في العربية RTL تكون الهالة القوية من اليمين، وهو ما يقيسه `--verify` أسفل.
 *  3. **لمعة الحافة العليا** (sheen): خطّ ضوء بارتفاع 1dp أعلى السطح. عين الإنسان تفترض أن
 *     الضوء من فوق، فهذه اللمعة وحدها تجعل السطح مقاومًا للانطباق. وشدة اللمعة **تُشتقّ من
 *     إضاءة السطح نفسه** (`luminance`): فاتح ⇒ حدّ أبيض واضح، داكن ⇒ لمعة خفيفة (الحدّ الأبيض
 *     القوي على سطح أسود يبدو خدشًا لا ضوءًا).
 *
 * ولا تُستدعى هذه الدوال من الشاشات: الشاشات تستعمل `NeuralPanel`/`NeuralTile`، فيبقى قرار
 * «كم عمقًا» في المكتبة — وتحسينه مرّة يحسّن ٦٩ شاشة بلا لمس أي منها.
 */
package nd.max.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * شدة لمعة الحافة العليا من إضاءة السطح.
 *
 * والأرقام مقيسة على اللوحة نفسها: سطح داكن (`luminance ≈ 0.02`) يحتاج 8.5٪ أبيض ليُقرأ
 * خطًّا هادئًا؛ وسطح فاتح (`luminance ≈ 0.90`) يحتاج 60٪ وإلا اختفت اللمعة في بياضه.
 * و`luminance()` حسابيّ لا تخميني، فلا يُخترع رقم لكل ثيم يختاره المستخدم.
 */
internal fun neuralSheenAlpha(surface: Color): Float =
    if (surface.luminance() > .5f) .60f else .085f

/**
 * سطح المكتبة الموحّد: عمق + صبغة + لمعة + حدّ — بالسلسلة الصحيحة للرسم.
 *
 * وترتيب الرسم مقصود: الظلّ **خارج** القصّ (وإلا قُصّ فاختفى)، ثم التدرّج، ثم الهالة، ثم
 * اللمعة، ثم الحدّ في الأعلى. وأي إعادة ترتيب تُنتج سطحًا بلا عمق أو بحدّ مقطوع.
 *
 * و[sheen] مُمرَّر صراحةً حين تحتاج الواجهة لمعةً أخفّ (بلاطة ملوّنة لا تحتمل حدًّا أبيض
 * واضحًا فوق صبغتها)؛ و`-1f` تعني «اشتقّها من إضاءة السطح».
 */
internal fun Modifier.neuralSurface(
    shape: Shape,
    top: Color,
    bottom: Color,
    border: Color,
    glow: Color? = null,
    elevation: Dp = 0.dp,
    sheen: Float = -1f,
    glowStrength: Float = 1f,
    rtl: Boolean = false,
): Modifier {
    val sheenAlpha = if (sheen >= 0f) sheen else neuralSheenAlpha(bottom)
    var m: Modifier = this
    if (elevation > 0.dp) {
        val tint = glow ?: bottom
        m = m.shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = tint.copy(alpha = .50f),
            spotColor = tint.copy(alpha = .65f),
        )
    }
    m = m.clip(shape).background(Brush.verticalGradient(listOf(top, bottom)))
    if (glow != null) {
        m = m.drawBehind {
            drawNeuralAura(this, glow, glowStrength, rtl)
        }
    }
    return m
        .drawBehind {
            val hairline = 1.dp.toPx()
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = sheenAlpha), Color.Transparent),
                    startY = 0f,
                    endY = hairline,
                ),
                size = Size(size.width, hairline),
            )
        }
        .border(BorderStroke(1.dp, border), shape)
}

/**
 * هالة مستقلّة لسطح يرسم نفسه (البطل في الرئيسية مثلًا).
 *
 * موجودة لأن بعض الأسطح لا تستعمل [neuralSurface]: الحاوية التي تحتاج هالة **وحدها** فوق
 * محتوى مرسوم بيدها. وهي نفس هالة `neuralSurface` بالضبط، فلا يتفرّع شكل الهالة إلى نسختين.
 */
internal fun Modifier.neuralAura(accent: Color, secondary: Color? = null, rtl: Boolean = false): Modifier =
    drawBehind { drawNeuralAura(this, accent, 1f, rtl, secondary) }

/**
 * إضاءة الصفحة — هالة واحدة خلف كل شيء، من **حافة القراءة**.
 *
 * كانت الرئيسية ترسم هالة خلفيتها بإحداثيات **مطلقة بالبكسل** (`center = 220, 80` و`radius = 900`)
 * وهي ثلاثة أخطاء في سطر واحد: لا تتغيّر مع مقاس الشاشة (على لوحي تصبح بقعة في الزاوية)، ولا
 * تعرف الاتّجاه (في العربية يبدأ الضوء من اليسار)، ولا تتفق مع هالات الأسطح فوقها. فصارت هنا
 * نسبة من مقاس الصفحة، ومصدرها واحد: حافة القراءة.
 *
 * وهي **خلف المحتوى**: تُوضع على الحاوية الممتلئة للشاشة (لا داخل قائمة التمرير) فيبقى الضوء
 * ثابتًا والنصّ يتحرّك — وهذا فرق الإحساس الذي يفرّق صفحة مصمّمة من صفحة مرصوفة.
 */
@Composable
fun Modifier.neuralPageBackdrop(accent: Color, secondary: Color): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return drawBehind {
        val span = size.maxDimension
        drawRect(
            Brush.radialGradient(
                colors = listOf(accent.copy(alpha = .13f), Color.Transparent),
                center = Offset(if (rtl) size.width * .94f else size.width * .06f, size.height * .02f),
                radius = span * .78f,
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(secondary.copy(alpha = .06f), Color.Transparent),
                center = Offset(if (rtl) size.width * .06f else size.width * .94f, size.height * .40f),
                radius = span * .66f,
            )
        )
    }
}

/**
 * رسم الهالة نفسها — دالة واحدة تقرأ منها كل الأسطح، فلا تتفرّع الهالة إلى نسختين تختلفان
 * عند أول تعديل. و[rtl] يقلب الركن القوي إلى **حافة القراءة** (يمين في العربية).
 */
private fun drawNeuralAura(
    scope: DrawScope,
    glow: Color,
    strength: Float,
    rtl: Boolean,
    secondary: Color? = null,
) {
    val back = secondary ?: glow
    val frontX = if (rtl) scope.size.width else 0f
    val backX = if (rtl) 0f else scope.size.width
    scope.drawRect(
        Brush.radialGradient(
            colors = listOf(glow.copy(alpha = .22f * strength), Color.Transparent),
            center = Offset(frontX, 0f),
            radius = scope.size.maxDimension * .78f,
        )
    )
    scope.drawRect(
        Brush.radialGradient(
            colors = listOf(back.copy(alpha = .11f * strength), Color.Transparent),
            center = Offset(backX, scope.size.height),
            radius = scope.size.maxDimension * .55f,
        )
    )
}
