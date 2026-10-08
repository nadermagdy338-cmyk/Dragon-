/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * غطاء جولة أول فتح — **تعتيم الشاشة وثقبٌ مضيء حول العنصر المشروح وبطاقة شرح** (`HomeTourModel`).
 *
 * أربع قرارات هندسية، لأن هذا النوع من الأغطية يفشل بصمت:
 *
 * 1. **المرساة تُقاس بموضعها لا بحدودها المقصوصة.** `boundsInWindow()` تقصّ المستطيل بحدّ الشاشة،
 *    فيصير ما تحت الشاشة بلا حجم ولا يُعرف كم نمرّر إليه. فنقرأ `positionInWindow()` + `size`
 *    (موضع حقيقي حتى خارج الإطار)، والرئيسية عنصر واحد في `LazyColumn` فكل كتلتها مركّبة ومقيسة.
 * 2. **التمرير إلى العنصر قبل إضاءته.** كل خطوة تمرّر القائمة حتى يستقرّ أعلى العنصر عند ١٦٪ من
 *    الارتفاع (`ScrollAnchor`)، فتبقى تحته مساحةٌ لبطاقة الشرح. ويُنتظر قياس المرساة بـ`snapshotFlow`
 *    لا بتأخير ثابت — فلا تتوقف الجولة على جهاز بطيء ولا تسبق القياس على سريع.
 * 3. **الثقب بـ`BlendMode.Clear` داخل طبقة منفصلة** (`CompositingStrategy.Offscreen`): بلا الطبقة يمحو
 *    `Clear` الخلفية كلها إلى الشفاف فيظهر أسود. والثقب يتبع العنصر كل إطار بلا تحريك مرافق —
 *    كان التحريك سيتخلف عن التمرير ويُضيء مكانًا ليس فيه العنصر.
 * 4. **الغطاء يبتلع اللمس** (`pointerInput`)، فلا يضغط المستخدم زرًّا تحت التعتيم ولا يمرّر الشاشة
 *    خلف الجولة. و**زر الرجوع يتخطّى** (يُتمّ الجولة) بدل أن يغلق الشاشة من تحتها.
 *
 * **ما لا تفعله:** لا تُغيّر حالة التطبيق ولا تطلب صلاحية؛ إتمامها يحفظ علمًا واحدًا
 * (`HomeGuideStore.tourFinished`) و`?` في الرأس يعيدها. وبعد احترام إيقاف الحركة في النظام:
 * بلا حركة يقفز التمرير إلى موضعه.
 */
package nd.max.ui.mainscreens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import nd.max.R
import nd.max.ui.component.MaxMotion
import nd.max.ui.component.NeuralIconChip
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.NeuralTrack
import nd.max.ui.component.maxAdaptiveContentWidth
import nd.max.ui.component.neuralPalette
import nd.max.ui.component.rememberAnimationsEnabled
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.floatingBottomBarPadding

/** أين يستقرّ أعلى العنصر المشروح من ارتفاع الشاشة بعد التمرير. */
private const val ScrollAnchor = 0.16f

/** عتمة الغطاء: تُخفي ما حول العنصر وتُبقي شكل الشاشة مقروءًا. */
private const val ScrimAlpha = 0.78f

/**
 * سجلّ مواضع المراسي — تكتبه الرئيسية أثناء الرسم، ويقرؤه الغطاء. حالة قابلة للمراقبة، فيتتبع
 * الثقبُ العنصرَ أثناء التمرير.
 */
@Stable
internal class HomeTourTargets {
    private val bounds = mutableStateMapOf<HomeTourTarget, Rect>()

    operator fun get(target: HomeTourTarget): Rect? = bounds[target]

    /** لا تُكتب حالة بقيمة لم تتغيّر: كل كتابة تُعيد تركيب الغطاء. */
    fun report(target: HomeTourTarget, rect: Rect) {
        if (bounds[target] != rect) bounds[target] = rect
    }
}

/** تعليم كتلة بأنها مرساة. بلا سجلّ (`null`) لا يفعل شيئًا — فمعاينات الاستوديو لا تحتاجه. */
internal fun Modifier.homeTourTarget(target: HomeTourTarget, targets: HomeTourTargets?): Modifier =
    if (targets == null) {
        this
    } else {
        this.onGloballyPositioned { coordinates ->
            targets.report(target, Rect(coordinates.positionInWindow(), coordinates.size.toSize()))
        }
    }

@Composable
internal fun HomeTourOverlay(
    targets: HomeTourTargets,
    listState: LazyListState,
    onFinish: () -> Unit,
) {
    val p = neuralPalette()
    val scheme = MaterialTheme.colorScheme
    val animationsEnabled = rememberAnimationsEnabled()
    var index by remember { mutableIntStateOf(0) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val step = HomeTourModel.steps[index]
    val last = HomeTourModel.isLast(index)

    BackHandler(onBack = onFinish)

    LaunchedEffect(index, size) {
        if (size == IntSize.Zero) return@LaunchedEffect
        val anchored = snapshotFlow { targets[step.target] }.filterNotNull().first()
        val delta = anchored.top - origin.y - size.height * ScrollAnchor
        if (abs(delta) < 1f) return@LaunchedEffect
        if (animationsEnabled) {
            listState.animateScrollBy(delta, tween(MaxMotion.standard, easing = FastOutSlowInEasing))
        } else {
            listState.scrollBy(delta)
        }
    }

    // الإحداثيات من النافذة إلى الغطاء نفسه: إن لم يبدأ عند (٠،٠) (شريط نظام، نافذة منقسمة)
    // أزاح الثقبُ العنصرَ بمقدار الإزاحة.
    val rect = targets[step.target]?.translate(-origin)

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInWindow()
                size = it.size
            }
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            drawRect(scheme.scrim.copy(alpha = ScrimAlpha))
            if (rect != null) {
                val pad = MaxSpace.sm.toPx()
                val topLeft = Offset(rect.left - pad, rect.top - pad)
                val box = Size(rect.width + pad * 2f, rect.height + pad * 2f)
                val corner = CornerRadius(MaxRadius.group.toPx() + pad / 2f)
                drawRoundRect(Color.Black, topLeft, box, corner, blendMode = BlendMode.Clear)
                drawRoundRect(
                    color = p.accent.copy(alpha = .85f),
                    topLeft = topLeft,
                    size = box,
                    cornerRadius = corner,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        // البطاقة بعيدةٌ عن العنصر: تحته إن كان في النصف الأعلى، وفوقه إن كان في الأسفل.
        val cardAtBottom = rect == null || rect.center.y < size.height / 2f
        val cardEdge = if (cardAtBottom) {
            Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = floatingBottomBarPadding(
                        MaxSpace.lg + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                )
        } else {
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + MaxSpace.lg)
        }
        TourCard(
            step = step,
            index = index,
            total = HomeTourModel.count,
            last = last,
            onBack = { index = HomeTourModel.previous(index) },
            onNext = {
                if (last) {
                    onFinish()
                } else {
                    index = HomeTourModel.next(index)
                }
            },
            onSkip = onFinish,
            modifier = cardEdge
                .maxAdaptiveContentWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = MaxSpace.lg),
        )
    }
}

private fun HomeTourStep.glyph(): ImageVector = when (this) {
    HomeTourStep.Identity -> Icons.Rounded.Shield
    HomeTourStep.Vitals -> Icons.Rounded.Speed
    HomeTourStep.Actions -> Icons.Rounded.Bolt
    HomeTourStep.Pulse -> Icons.Rounded.Insights
    HomeTourStep.Memory -> Icons.Rounded.Memory
    HomeTourStep.Cleaner -> Icons.Rounded.CleaningServices
    HomeTourStep.Deck -> Icons.Rounded.Tune
}

@Composable
private fun TourCard(
    step: HomeTourStep,
    index: Int,
    total: Int,
    last: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier,
) {
    val p = neuralPalette()
    NeuralPanel(modifier = modifier, accent = p.accent, verticalSpacing = MaxSpace.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeuralIconChip(step.glyph(), p.accent, size = 36.dp)
            Spacer(Modifier.width(MaxSpace.sm))
            Text(
                stringResource(R.string.home_tour_step, index + 1, total),
                color = p.muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            NeuralPill(
                text = stringResource(R.string.home_banner_skip),
                accent = p.muted,
                compact = true,
                onClick = onSkip,
            )
        }
        Text(
            stringResource(step.titleRes),
            color = p.text,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(step.bodyRes),
            color = p.muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
        NeuralTrack((index + 1f) / total, p.accent)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)) {
            HomeActionButton(
                text = stringResource(R.string.home_tour_back),
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                filled = false,
                accent = if (index == 0) p.muted else p.accent,
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
            HomeActionButton(
                text = stringResource(if (last) R.string.home_banner_done else R.string.home_tour_next),
                icon = if (last) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                filled = true,
                accent = p.accent,
                onClick = onNext,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
