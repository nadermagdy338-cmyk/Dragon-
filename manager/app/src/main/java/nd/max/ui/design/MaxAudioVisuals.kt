/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxManager Design Language — **مفردات الصوت البصريّة** (موجة · أعمدة · بلاطة · كاروسيل).
 *
 * ───────────────────────── Attribution (Apache-2.0) ─────────────────────────
 * The visual idioms below are adapted from the **DolbyUI** interface — branch `rodin` of
 * `Digimend-X-Rodin/packages_apps_DolbyUI` (a fork of `swiitch-OFF-Lab/packages_apps_DolbyUI`),
 * licensed **Apache-2.0** as stated in its own file headers, and used with the copyright
 * holder's permission. This is a rework, not a copy, and the Apache notice is retained; the
 * credit is recorded in `docs/PROVENANCE.md`.
 *
 * ───────────────────────────── نسبة الفضل (بالعربيّة) ─────────────────────────────
 * أصل هذه المفردات ومصدر إلهامها واجهةُ **DolbyUI** — فرع `rodin` من
 * `Digimend-X-Rodin/packages_apps_DolbyUI` (المُنسوخ من `swiitch-OFF-Lab/packages_apps_DolbyUI`)،
 * برخصة **Apache-2.0** (منصوصة في ترويسات ملفّاته) **وبرضًا من صاحبه**. وقد أُعيدت الصياغة لا النقل:
 * كل مكوّن هنا يُبنى من رموز MaxManager (`MaxSpace` · `MaxRadius` · `MaxSize` · `MaxAlpha` ·
 * `MaxDuration`) ويأخذ بياناته من الخارج — **ولا رقمٌ ولا نصٌّ من الأصل في هذا الملفّ**. وتفصيل
 * الإضافة مُسجَّل في `docs/PROVENANCE.md`.
 *
 * ───────────────────────── وحدٌّ واحد يُعلَن لأنّ الأصل يخالفه ─────────────────────────
 * `AnimatedWaveformBanner` في الأصل يرسم **شكلًا ثابتًا** (مجموع خمس دوالّ جاوس بمُعاملاتٍ مكتوبة في
 * الكود) ويحرّكه بجيبة زمنيّة — أي شكلٌ لا يقيس ما يُسمع. وفي تطبيقٍ ثانيته «قياس» يقرؤه المستخدم
 * قراءةً وهو ليس منها، وهذا نصّ ADR-07 لا ذوقًا. فـ[MaxWaveformBanner] هنا يرسم **المستويات كما
 * قِيست**، ولا يرسم شكلًا حين لا قراءة — والفرق بينهما فرقُ واجهةِ تشغيلٍ وواجهةِ قياس.
 */
package nd.max.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.pow
import kotlinx.coroutines.launch
import nd.max.ui.component.maxPressMotion

/**
 * مدّة الدورة المحيطة لأعمدة المعادل: **ضعف `MaxDuration.deliberate`**.
 *
 * و`MaxDuration` تقول عن نفسها إن «ما طال فوق `deliberate` زينةٌ لا تُسمح». وهذه دورةٌ **محيطة** لا
 * انتقال، فليست داخلةً في نصّها حرفيًّا — **والاستثناء مُعلَن لا مسكوت عنه**، ومربوطٌ بحالة: مع
 * `active = false` لا نبض أصلًا، فالأعمدة تُرسم ساكنة. والمُكتسَب من الرمز أنّ الرقم مشتقٌّ من ميزانية
 * الحركة لا مخترعٌ بجانبها.
 */
private const val ambientBarCycleMillis: Int = MaxDuration.deliberate * 2

/* ═══════════════════════════ ① أعمدة المعادل (أيقونةُ حالةٍ لا زينة) ═══════════════════════════ */

/**
 * أعمدة المعادل — أيقونةُ «المحرّك يعمل» في شريط التنقّل وبطاقة البطل.
 *
 * **والحركة مرتبطةٌ بالحالة:** مع [active] تنبض الأعمدة بدورةٍ محيطة، وبدونه تُرسم **ساكنة**. فلا شيء
 * يتحرّك بلا سبب يقرؤه المستخدم — وهو مبدأ «لا رقم بلا قراءة» في مستوى الحركة.
 *
 * @param active هل الصوت يعمل الآن؟ — وهي وحدها ما يشغّل النبض.
 * @param barCount عدد الأعمدة؛ ٥ هو ما يُقرأ «معادلًا» في حجم أيقونة، وأكثره يفقد القراءة.
 */
@Composable
fun MaxEqualizerBars(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = MaxSize.iconGlyph + MaxSpace.xs,
    barCount: Int = 5,
) {
    if (barCount <= 0) return

    val transition = rememberInfiniteTransition(label = "maxEqualizerBars")
    // «الحلقات» هنا `for` لا `List(n) { … }`: بناء `List` بدالّة أوّليّة ليس `inline`، فلا يُسمح
    // فيه بنداءٍ `@Composable` — و`InfiniteTransition.animateFloat` كذلك.
    val pulse = ArrayList<State<Float>>(barCount)
    for (index in 0 until barCount) {
        pulse += transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = ambientBarCycleMillis + index * 50,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "maxEqualizerBar$index",
        )
    }
    // الشكل الساكن: تعرّجٌ هادئ ثابت يُقرأ «معادلًا» ولا يدّعي حركةً ولا قراءة.
    val still = ArrayList<Float>(barCount)
    for (index in 0 until barCount) {
        val position = if (barCount == 1) 0.5f else index.toFloat() / (barCount - 1)
        still += 0.35f + 0.45f * (1f - abs(position - 0.5f) * 2f)
    }

    Canvas(modifier = modifier.size(size)) {
        val canvasWidth = this.size.width
        val canvasHeight = this.size.height
        if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

        // عرض العمود يتناقص عند الطرفين فتُقرأ المجموعة قوسًا لا شبكة — **وهذا شكلٌ لا قياس**،
        // ومكتوبٌ هنا أنه شكل: لا صلة له بأيّ قراءة.
        val widths = FloatArray(barCount)
        var widthSum = 0f
        for (index in 0 until barCount) {
            val position = if (barCount == 1) 0.5f else index.toFloat() / (barCount - 1)
            val offset = (position - 0.5f) * 2f
            widths[index] = 0.5f + (1f - (offset * offset).pow(0.6f)) * 0.5f
            widthSum += widths[index]
        }
        val gap = 0.3f
        val baseWidth = canvasWidth / (widthSum + (barCount - 1) * gap)

        var x = 0f
        for (index in 0 until barCount) {
            val level = if (active) pulse[index].value else still[index]
            val barWidth = baseWidth * widths[index]
            val barHeight = (canvasHeight * level).coerceAtLeast(barWidth)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, canvasHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
            x += barWidth + baseWidth * gap
        }
    }
}

/* ═══════════════════════════ ② شريط الموجة (يرسم ما قِيس) ═══════════════════════════ */

/**
 * شريط الموجة — **أعمدةٌ من الطيف المقروء، لا شكلٌ مُخترع**.
 *
 * @param levels مستويات `0f..1f` من الأدنى ترددًا إلى الأعلى (من `AudioSpectrumFrame.bands`).
 *   **والقائمة الفارغة تعني «لا قراءة»**: تُرسم قاعدةٌ عند المنتصف ولا يُخترع شكل.
 * @param live هل الالتقاط جارٍ؟ — به يُقصَّر الانتقال بين لقطتين (`MaxDuration.quick`)، فالرسم يتبع
 *   الطيف. وبدونه يُبطأ الانتقال فلا تقرأ العين حركةً لا تتبع قياسًا.
 */
@Composable
fun MaxWaveformBanner(
    levels: List<Float>,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    barColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = MaterialTheme.colorScheme.tertiary,
    barCount: Int = 48,
) {
    val idleInk = MaterialTheme.colorScheme.outlineVariant
    if (levels.isEmpty()) {
        Canvas(modifier = modifier) {
            drawLine(
                color = idleInk,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = MaxSize.hairlineBorder.toPx(),
            )
        }
        return
    }

    // يُقيَّد العدد على ما قِيس فعلًا — ولا تُعبَّأ أعمدةٌ لا مقابل لها في القراءة.
    val bounded = barCount.coerceIn(1, levels.size)
    val animated = ArrayList<State<Float>>(bounded)
    for (index in 0 until bounded) {
        val target = levels[index * levels.size / bounded].coerceIn(0f, 1f)
        animated += animateFloatAsState(
            targetValue = target,
            animationSpec = tween(
                durationMillis = if (live) MaxDuration.quick else MaxDuration.standard,
                easing = FastOutSlowInEasing,
            ),
            label = "maxWaveformBar$index",
        )
    }

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

        val centerY = canvasHeight / 2f
        val slot = canvasWidth / bounded
        val barWidth = (slot * 0.42f).coerceAtLeast(1f)
        val radius = barWidth / 2f
        val ceiling = centerY * 0.86f

        for (index in 0 until bounded) {
            val level = animated[index].value
            val half = ceiling * level
            val centerX = (index + 0.5f) * slot
            // **والذروة تُلوَّن من القراءة نفسها:** فوق نصف المقياس يميل العمود إلى لون التمييز —
            // والقرار من المستوى المقروء، لا من منحنى جاوس مرسومٍ في الكود كما في الأصل.
            val ink = if (level > 0.55f) {
                lerpColor(barColor, accentColor, ((level - 0.55f) / 0.45f).coerceIn(0f, 1f))
            } else {
                barColor
            }
            if (half <= radius * 1.15f) {
                drawCircle(
                    color = ink.copy(alpha = 0.65f),
                    radius = radius * 0.72f,
                    center = Offset(centerX, centerY),
                )
            } else {
                drawRoundRect(
                    color = ink,
                    topLeft = Offset(centerX - radius, centerY - half),
                    size = Size(barWidth, half * 2f),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
        }
    }
}

/* ═══════════════════════════ ③ بلاطة الاختيار (شكلٌ ينضغط) ═══════════════════════════ */

/**
 * بلاطة اختيار — بديل البطاقة لأمرٍ قصير يُختار بلمسةٍ واحدة.
 *
 * **وما يُميّز المختارة ليس لونها وحده:** نصف قطرها يتبدّل معها (`group` مقابل `tile`)، وهو نصّ عقد
 * الرموز: «Tone is never the only carrier of meaning». والحدّ من `MaxAlpha` لا من رقم، والانضغاط من
 * `maxPressMotion` القائم لا من انضغاطٍ ثانٍ مكتوب هنا.
 */
@Composable
fun MaxSelectableTile(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val container = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MaxAlpha.disabledContent)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = RoundedCornerShape(if (selected) MaxRadius.group else MaxRadius.tile)
    val glyphShape = RoundedCornerShape(if (selected) MaxRadius.group else MaxRadius.control)
    val glyphContainer = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val glyphInk = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MaxAlpha.disabledContent)
        selected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = BorderStroke(
        width = if (selected) MaxSize.activeRing else MaxSize.hairlineBorder,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = MaxAlpha.borderStrong)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaxAlpha.border)
        },
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.maxPressMotion(interaction),
        shape = shape,
        color = container,
        contentColor = content,
        border = border,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaxSpace.rowPaddingHorizontal,
                    vertical = MaxSpace.rowPaddingVertical,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.md),
        ) {
            Surface(
                modifier = Modifier.size(MaxSize.rowIconContainer),
                shape = glyphShape,
                color = glyphContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = glyphInk,
                        modifier = Modifier.size(MaxSize.iconGlyphSmall),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(lineBreak = LineBreak.Heading),
                        color = content.copy(alpha = MaxAlpha.supportingText),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/* ═══════════════════════════ ④ الكاروسيل (اختيارٌ واحد من عدّة) ═══════════════════════════ */

/** بطاقةٌ في الكاروسيل: مفتاحُ اختيارها وعنوانها ورمزها وسطرٌ مساند اختياريّ. */
@Immutable
data class MaxCarouselItem(
    val key: Int,
    val title: String,
    val icon: ImageVector,
    val caption: String? = null,
)

/**
 * كاروسيل البطاقات — اختيارٌ واحد من عدّة **بلمسة بطاقةٍ لا بشريط انزلاق**.
 *
 * **ولماذا هو بديلٌ هنا:** الشريط الأفقي (`MaxSegmented`) يُقرأ ممتازًا حتى أربع خيارات، ثمّ يضيق
 * النصّ فيه فيُبتر. والكاروسيل يعطي كلّ خيار **بطاقةً كاملة** فلا يُبتر عنوانٌ طويل (عربيًّا كان أو
 * إنجليزيًّا) — وهو العطب نفسه الذي وُجد له `MaxCardSpec.minColumnWidth` في الشبكة.
 *
 * **والبطاقة المجاورة تُرى مصغَّرةً وخافتة:** بها يعرف المستخدم أنّ ثمّة غيرها، فلا يظنّ المعروض كلّ
 * الخيارات. وهي **حالةٌ لا زينة**: مع خيارٍ واحد لا كاروسيل أصلًا.
 *
 * @param onSelect يُنادى **عند استقرار الصفحة** لا مع كل بكسل تمرير — وإلّا كُتب على المنصّة مرارًا
 *   في سحبةٍ واحدة، وهو القرار المتّخذ نفسه في أشرطة المعادل (الكتابة عند الإفلات).
 * @return هل رُسم كاروسيل؟ — `false` حين كان الخيار واحدًا، فيعرض المُنادي عنصرًا وحده بلا هدر مساحة.
 */
@Composable
fun MaxCardCarousel(
    items: List<MaxCarouselItem>,
    selectedKey: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
): Boolean {
    if (items.size < 2) return false

    val initial = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initial) { items.size }
    val scope = rememberCoroutineScope()
    var lastPage by remember { mutableIntStateOf(initial) }

    LaunchedEffect(pagerState.settledPage) {
        val page = pagerState.settledPage
        if (page == lastPage) return@LaunchedEffect
        lastPage = page
        items.getOrNull(page)?.let { onSelect(it.key) }
    }

    val paletteStart = MaterialTheme.colorScheme.primary
    val paletteEnd = MaterialTheme.colorScheme.secondary
    val paletteInk = MaterialTheme.colorScheme.onPrimary

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaxSpace.sm),
    ) {
        label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(MaxCardSpec.minHeight + MaxSpace.pageBottom),
            contentPadding = PaddingValues(horizontal = MaxSpace.gutter * 2),
            pageSpacing = MaxSpace.sm,
        ) { page ->
            val item = items[page]
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val closeness = 1f - abs(offset).coerceIn(0f, 1f)
            val isSelected = page == pagerState.currentPage

            Surface(
                onClick = { scope.launch { pagerState.animateScrollToPage(page) } },
                modifier = Modifier
                    .fillMaxHeight()
                    .graphicsLayer {
                        val scale = lerp(0.86f, 1f, closeness)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(0.45f, 1f, closeness)
                    },
                shape = RoundedCornerShape(MaxRadius.group),
                color = paletteEnd,
                contentColor = paletteInk,
                tonalElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(paletteStart, paletteEnd))),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(MaxSpace.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = paletteInk,
                            modifier = Modifier.size(MaxSize.iconContainer),
                        )
                        Spacer(Modifier.height(MaxSpace.sm))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = paletteInk,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                            Spacer(Modifier.height(MaxSpace.xs))
                            Text(
                                text = caption,
                                style = MaterialTheme.typography.bodySmall,
                                color = paletteInk.copy(alpha = MaxAlpha.supportingText),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (isSelected) {
                        // علامةُ الاختيار **رمزٌ لا لون**: من لا يميّز الألوان يقرؤها.
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(MaxSpace.sm)
                                .size(MaxSize.rowIconContainer / 2),
                            shape = CircleShape,
                            color = paletteInk,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = paletteStart,
                                    modifier = Modifier.size(MaxSize.iconGlyphSmall / 2),
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, _ ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(MaxSpace.hairline)
                        .size(
                            width = if (isSelected) MaxSize.barHeight * 3 else MaxSpace.xs + MaxSpace.hairline,
                            height = MaxSpace.xs + MaxSpace.hairline,
                        )
                        .background(
                            color = if (isSelected) paletteStart else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
    return true
}
