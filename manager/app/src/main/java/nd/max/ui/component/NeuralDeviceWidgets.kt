/**
 * Neural device widgets — أدوات «معلومات الجهاز» المشتركة بين الشاشات.
 *
 * هذه الطبقة أُنشئت لأن الرئيسية القديمة كانت ترسم **الأداة نفسها ثلاث مرّات**: كتلة
 * كبيرة للحرارة، وبطاقتان لـCPU وGPU، ولوحة موارد — كلّها دوائر وقضبان وأرقام مكتوبة
 * يدويًّا في مكانها. والنتيجة أن تحسين شكل «بطاقة سعة» يعني تعديل ثلاثة ملفات، وأن
 * الاختلاف بينها يظهر بعد جولة واحدة.
 *
 * فالقاعدة الآن: **كل رسم يقيس نسبة أو يعرض قراءة يعيش هنا مرّة واحدة**، والشاشات ترسم
 * بمكوّنات لا بأشكال. والأدوات الأربع:
 *
 *  - [NeuralRing] — قوس نسبة واحد (٠–١٠٠٪ أو مستخدم/كلي). لا يُخترع له مدى: من لا يملك
 *    مقامًا صحيحًا يمرّر `null` فيرسم الإطار وحده ويعرض `—`، وهو الفرق بين «لا نعرف» و«صفر».
 *  - [NeuralGaugeCard] — البطاقة القياسية لمعلومة واحدة: ترويسة (أيقونة · اسم · شارة) ثم
 *    القوس ثم سطر الدعم ثم خانة سفلية للمقياس. هي «الوِدجت» التي تُبنى بها شاشة معلومات جهاز.
 *  - [NeuralReadoutTile] — بلاطة قراءة صغيرة (اسم فوق، قيمة ملوّنة بلون الحالة). تُستعمل
 *    حيث تكون القيمة نفسها هي الإشارة: حرارة، سرعة شبكة، أي قراءة حكمها في لونها.
 *  - [NeuralCoreGrid] — مصفوفة الأنوية: عمود لكل نواة بارتفاع = تردّدها من سقفها.
 *
 * ولماذا `NeuralCoreReading` ولا `CpuCoreState` مباشرة: طبقة المكوّنات لا تستورد نماذج
 * `viewmodel`؛ فالمصفوفة تُغذّى بقراءة معروضة جاهزة (نصّ التردّد + النسبة + الحالة)،
 * والشاشة هي التي تُسقط `CpuCoreState` عليها. فتبقى المكتبة صالحة لأي شاشة تقيس نوى،
 * بلا اعتماد على نموذج واحد.
 */
package nd.max.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nd.max.ui.theme.MonoValueStyleSmall

/** بداية القوس ومداه: ٢٧٠° تبدأ من الركن الأسفل الأيسر، فيبقى الفتح أسفل الأداة. */
private const val RingStartAngle = 135f
private const val RingSweepAngle = 270f

/**
 * قوس نسبة — أداة العرض الواحدة لكل «كم استُهلك من الكلّ».
 *
 * وأربع قواعد فيها، كلها من قياس لا من ذوق:
 *
 *  1. **`fraction = null` لا يساوي صفرًا**: تُرسم حلقة الأساس وحدها، ويبقى الرقم `—` بلون
 *     خافت. مقام مجهول (ذاكرة لا تُقرأ، بطارية بلا قراءة) لا يجوز أن يُرسم كقوس فارغ، لأن
 *     الفراغ في اللغة البصرية يعني «صفر مستخدم» — وهي كذبة.
 *  2. **التعبئة تُحرَّك بـ`animateFloatAsState`** لا تقفز: القراءة تتغيّر كل دورتَي قياس،
 *     والقفز المباشر يجعل الرقم يرتجّ بلا أن يحمل معنى.
 *  3. **هالتان خلف القوس** (عريضة خافتة وقريبة أقوى) ومعهما **مينا داخلية** خفيفة تحت الرقم:
 *     هذا هو العمق الذي يميّز أداة قياس حديثة عن شريط تقدّم مسطّح — وهو نفس عمق `NeuralPanel`
 *     (ظلّ مُلوَّن + هالة + لمعة حافة) بلغة أقواس. وبلا المينا يبدو الرقم معلّقًا في الهواء.
 *  4. **خمس علامات تدرّج على الأرباع** (٠/٢٥/٥٠/٧٥/١٠٠٪): تُقرأ أداة قياس لا زخرفة،
 *     و**تُضاء العلامة حين يبلغها القوس** — فالقراءة تُعلَن بموضعها لا بالرقم وحده.
 *  5. **الأرقام تُرسم بـ[NeuralValue]** أي LTR مثبّت: «٧٨٪» و«٢.٤ جيجاهرتز» تبقى بترتيبها
 *     اللاتيني في لغة RTL، ولا يقلبها محلّل النصّ ثنائي الاتجاه.
 *  6. **رقم القوس يكبر بكبر قوسه** (`size × 0.22`)، فلا تُمرّر مقاسات خطّ من كل شاشة: قاعدة
 *     التدرّج البصري صارت في الأداة نفسها — عام ٩٤dp يُقرأ ٢١sp، وقوس رئيسي ١٠٤dp يُقرأ ٢٣sp.
 *     وهذا ما تفعله نماذج التصميم فعلًا: الرقم هو البطاقة، والاسم تسمية له.
 */
@Composable
fun NeuralRing(
    fraction: Float?,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 92.dp,
    strokeWidth: Dp = 8.dp,
) {
    val p = neuralPalette()
    val animated by animateFloatAsState(
        targetValue = (fraction ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(560, easing = FastOutSlowInEasing),
        label = "neural-ring",
    )
    // الرقم يكبر مع القوس (مقيّد بين 15 و30sp) — تقاطعٌ هرميّ يمنع أن يُمرَّر مقاس خطّ يدويًّا
    // من كل نداء، ويمنع في الوقت نفسه «رقمًا صغيرًا في قوس كبير» وهو أول ما يظهر في اللقطة.
    val readout = (size.value * .22f).coerceIn(15f, 30f).sp
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            // الهامش يسع أعرض هالة (٢.٦× ⇒ ١.٣×) فلا يُقصّ القوس عند حواف الصندوق.
            val inset = stroke * 1.35f
            val arcSize = Size(this.size.width - inset * 2f, this.size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            val radius = arcSize.width.coerceAtMost(arcSize.height) / 2f
            val center = Offset(topLeft.x + arcSize.width / 2f, topLeft.y + arcSize.height / 2f)

            // ١ · مسار القياس (الأساس الدائم: يبقى حين لا قراءة، فيُقرأ الإطار فراغًا لا صفرًا).
            drawArc(
                color = p.grid,
                startAngle = RingStartAngle,
                sweepAngle = RingSweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // ٢ · التدرّج: خمس علامات على الأرباع، في داخل الحلقة كعدّاد حقيقي. وهي **دائمة**
            //     (لا داخل شرط القراءة): فرسمها بلا قراءة يعطي «أداة قياس لا قيمة لها»، ورسم
            //     الإطار بلا تدرّج يعطي «حلقة فارغة». والعلامة المُضاءة تقول أين بلغ القوس.
            val tickInner = radius - stroke * 2f
            val tickOuter = radius - stroke * 1.35f
            for (step in 0..4) {
                val at = step / 4f
                val angle = Math.toRadians((RingStartAngle + RingSweepAngle * at).toDouble())
                val cos = kotlin.math.cos(angle).toFloat()
                val sin = kotlin.math.sin(angle).toFloat()
                drawLine(
                    color = if (fraction != null && animated >= at - 1e-4f) accent.copy(alpha = .85f) else p.grid,
                    start = Offset(center.x + cos * tickInner, center.y + sin * tickInner),
                    end = Offset(center.x + cos * tickOuter, center.y + sin * tickOuter),
                    strokeWidth = stroke * .26f,
                    cap = StrokeCap.Round,
                )
            }
            if (fraction != null && animated > 0f) {
                // ٣ · المينا: قرص خافت تحت الرقم يجعل المقياس وعاءً لا حلقة فارغة.
                val well = (radius - stroke * 1.1f).coerceAtLeast(1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = .13f), Color.Transparent),
                        center = center,
                        radius = well,
                    ),
                    radius = well,
                    center = center,
                )
                // ٤ · هالتان: عريضة خافتة ثم قريبة أقوى — تدرّج ضوء لا حدّ واحد.
                for ((wide, alpha) in listOf(2.6f to .13f, 1.9f to .24f)) {
                    drawArc(
                        color = accent.copy(alpha = alpha),
                        startAngle = RingStartAngle,
                        sweepAngle = RingSweepAngle * animated,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke * wide, cap = StrokeCap.Round),
                    )
                }
                // ٥ · القوس المقيس.
                drawArc(
                    color = accent,
                    startAngle = RingStartAngle,
                    sweepAngle = RingSweepAngle * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                // ٦ · رأس القوس: نقطة وهالة ضوء حولها. وهي أرخص ما يفرّق مقياسًا «حيًّا» عن قوس
                //     ثابت — وفي اللقطات المرجعية يشغل اللون مساحة أكبر بكثير من حافة رقيقة.
                val tip = Math.toRadians((RingStartAngle + RingSweepAngle * animated).toDouble())
                val tipCenter = Offset(
                    center.x + radius * kotlin.math.cos(tip).toFloat(),
                    center.y + radius * kotlin.math.sin(tip).toFloat(),
                )
                drawCircle(accent.copy(alpha = .28f), radius = stroke * 1.9f, center = tipCenter)
                drawCircle(accent, radius = stroke * .62f, center = tipCenter)
            }
        }
        NeuralValue(
            value,
            style = MonoValueStyleSmall.copy(
                fontSize = readout,
                lineHeight = readout * 1.14f,
                fontWeight = FontWeight.Bold
            ),
            color = if (fraction == null) p.muted else p.text,
        )
    }
}

/**
 * البطاقة القياسية لمعلومة واحدة: **اسم · قوس · سطر دعم · خانة سفلية**.
 *
 * وهي شكل «الوِدجت» الذي تُبنى به شاشة معلومات جهاز: قابلة للوضع في عمودين أو في عرض كامل،
 * وتحمل زرًّا اختياريًّا (النقر على القراءة نفسها أقصر طريق لشاشتها)، وسطرًا سفليًّا يرسم
 * الشاشة ما تحتاجه (مقياس تردّد مثلًا) بلا إضافة شكل جديد إلى المكتبة.
 */
@Composable
fun NeuralGaugeCard(
    caption: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    ringFraction: Float? = null,
    icon: ImageVector? = null,
    badge: String? = null,
    support: String? = null,
    onClick: (() -> Unit)? = null,
    ringSize: Dp = 94.dp,
    footer: (@Composable () -> Unit)? = null,
) {
    val p = neuralPalette()
    NeuralTile(
        modifier,
        accent = accent,
        onClick = onClick,
        verticalSpacing = 9.dp,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                NeuralIconChip(icon, accent, size = 24.dp)
                Spacer(Modifier.width(7.dp))
            }
            NeuralCaption(caption, Modifier.weight(1f), color = accent)
            if (badge != null) {
                NeuralPill(badge, accent, filled = true)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NeuralRing(
                fraction = ringFraction,
                value = value,
                accent = accent,
                size = ringSize.coerceAtMost(82.dp),
                strokeWidth = 7.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (support != null) {
                    NeuralValue(
                        support,
                        style = MonoValueStyleSmall.copy(fontSize = 9.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = p.text,
                        maxLines = 2,
                    )
                }
                if (footer == null && support == null) {
                    Text(
                        stringResource(nd.max.R.string.max_home_unavailable),
                        color = p.muted,
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                    )
                }
            }
        }
        if (footer != null) footer()
    }
}

/**
 * بلاطة قراءة صغيرة: الاسم فوق، والقيمة بلون الحالة تحته.
 *
 * وكما في [NeuralGaugeCard]، الشرط واحد: [accent] يحمل **المعنى**. فحرارة ٤٨° حمراء وحرارة
 * ٣٦° خضراء تُقرأ في لمحة قبل قراءة الرقم، وشبكة تنتقل بلون مسارها. والقيمة تُرسم بـ
 * [NeuralValue] فتبقى «٤.٢ V» بترتيبها في RTL.
 */
@Composable
fun NeuralReadoutTile(
    caption: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val p = neuralPalette()
    NeuralTile(
        modifier,
        onClick = onClick,
        verticalSpacing = 3.dp,
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 10.dp),
    ) {
        NeuralCaption(caption, color = p.muted)
        NeuralValue(
            value,
            style = MonoValueStyleSmall.copy(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
            color = accent,
        )
        if (sub != null) {
            Text(
                sub,
                color = p.muted,
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * نواة واحدة كما ترسمها المصفوفة: نصّ التردّد المعروض، ونسبته من سقفها، وحالتها.
 *
 * والفرق بين «مطفأة» و«تردّدها صفر» محفوظ في [online]: الأولى تُرسم فارغة رمادية (حالة
 * حقيقية أخرجتها hotplug)، والثانية لا تُرسم لها تعبئة لأن لا رقم لها.
 */
@Immutable
data class NeuralCoreReading(
    val id: String,
    val frequency: String,
    val fraction: Float,
    val online: Boolean,
)

/**
 * مصفوفة الأنوية — عمود لكل نواة، وارتفاع العمود = تردّدها من سقفها.
 *
 * ولماذا أعمدة رأسية وليست صفوفًا: المعلومة هنا **مقارنة بين أنوية في اللحظة نفسها**،
 * والعين تقارن أطوالًا رأسية متلاصقة أسرع مما تقارن أشرطة أفقية مكدّسة فوق بعضها. وهذا هو
 * الشكل الذي تعرضه أدوات فحص العتاد، لأنه يعمل: انخفاض عمود واحد في السطر الأول يظهر بلا
 * قراءة أي رقم — أي نواة العنقود الصغير خُنقت.
 *
 * والقيم تُحرَّك كل واحدة نحو قيمتها (٦٠٠ms) لا يُعاد كشفها: المصفوفة تُحدَّث كل دورتين،
 * وإعادة الرسم الكامل تجعل الشاشة ترتجّ بلا معلومة جديدة.
 */
@Composable
fun NeuralCoreGrid(
    cores: List<NeuralCoreReading>,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    perRow: Int = 4,
    barHeight: Dp = 38.dp,
) {
    val p = neuralPalette()
    val tone = accent ?: p.accent
    val rows = if (perRow > 0) cores.chunked(perRow) else listOf(cores)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { core -> CoreCell(core, tone, barHeight, Modifier.weight(1f)) }
                // صفّ ناقص يُكمل بفراغات بنفس الأوزان، فلا تتوسّع الأنوية الأخيرة وحدها
                // ويبدو الصفّ كأنه يُقارن أنوية بعرض مختلف.
                repeat((perRow - row.size).coerceAtLeast(0)) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CoreCell(
    core: NeuralCoreReading,
    tone: Color,
    barHeight: Dp,
    modifier: Modifier,
) {
    val p = neuralPalette()
    val fill by animateFloatAsState(
        targetValue = if (core.online) core.fraction.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "neural-core-fill",
    )
    val barColor = if (core.online) tone else p.muted
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        NeuralValue(
            core.id,
            style = MonoValueStyleSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold),
            color = p.muted,
        )
        Canvas(Modifier.fillMaxWidth().height(barHeight)) {
            val barWidth = (size.width * .22f).coerceAtLeast(8.dp.toPx())
            val left = (size.width - barWidth) / 2f
            val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
            drawRoundRect(
                color = p.grid,
                topLeft = Offset(left, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = corner,
            )
            val lit = if (fill <= 0f) 0f else (size.height * fill).coerceAtLeast(barWidth)
            if (lit > 0f) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(barColor, barColor.copy(alpha = .55f)),
                        startY = size.height - lit,
                        endY = size.height,
                    ),
                    topLeft = Offset(left, size.height - lit),
                    size = Size(barWidth, lit),
                    cornerRadius = corner,
                )
                // رأس العمود يُضاء: مقارنة الأنوية تُقرأ من القمم لا من الأطوال — والنواة
                // المرفوعة تُلمح في طرفها قبل أن تُقرأ أرقامها.
                val cap = Offset(left + barWidth / 2f, size.height - lit)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(barColor.copy(alpha = .38f), Color.Transparent),
                        center = cap,
                        radius = barWidth * 1.7f,
                    ),
                    radius = barWidth * 1.7f,
                    center = cap,
                )
            }
        }
        NeuralValue(
            core.frequency,
            style = MonoValueStyleSmall.copy(fontSize = 8.sp),
            color = if (core.online) p.muted else p.grid,
        )
    }
}
