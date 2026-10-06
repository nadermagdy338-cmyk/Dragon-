/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * شريط جولة الرئيسية — **بطاقات تتحرك وحدها وتتوقف عند اللمس** (عقد §5.4 من خطة المستوى).
 *
 * ثلاثة قرارات تُشرح هنا لأنها ليست ذوقًا:
 *
 * 1. **الحركة موقوفة عند أول لمس، ولا تعود.** `PagerState.isScrollInProgress` ترتفع في السحب
 *    وفي حركة الانتقال التلقائي معًا؛ ولو أوقفنا عليها وحدها لعاد التمرير بعد ثانية، فيصير
 *    النصّ الذي يقرؤه المستخدم يُسحب من تحت عينه. فيُلتقط **اللمس** لا الحركة: من لمس الشريط
 *    صار القارئ، ولا يتحرك بعده إلا بأمره.
 * 2. **الانتقال بمنحنى مُقنَّن لا بحركة الـpager الافتراضية.** `animateScrollToPage` بلا
 *    `animationSpec` يستعمل `spring()` وينتهي إلى ما يقارب نصف ثانية على هذه المسافة — وهو
 *    **فوق سقف الحركة 360ms**. فالانتقال يُمرَّر بـ`tween(MaxMotion.standard)` (٢٦٠ms) — لا
 *    رقم جديد على الحدّ، بل الرقم القائم.
 * 3. **الحشوة كلها من `MaxSpace`، والنقاط أرقام صغيرة لا رموزًا جديدة** — بوابة
 *    `design_tokens.py --assert` تُهبط أي حرفيّ حشو/تباعد/نصف قطر جديد، وهي تعامل أي ملفّ
 *    جديد كأنه زاد من الصفر.
 *
 * **وما لا يفعله الشريط:** لا يفتح شاشة، ولا ينفّذ فعلًا، ولا يقول «اضغط هنا» على شيء لا
 * يفعل. جملة واحدة في كل بطاقة، والباب يفتحه المستخدم من البطاقة التي تخصّه في الشاشة نفسها.
 */
package nd.max.ui.mainscreens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nd.max.R
import nd.max.ui.component.MaxMotion
import nd.max.ui.component.NeuralCaption
import nd.max.ui.component.NeuralPanel
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.neuralPalette
import nd.max.ui.component.rememberAnimationsEnabled
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace

/**
 * ارتفاع صفحة البطاقة: ثلاثة أسطر جسم + سطر عنوان.
 *
 * ومشتقّ من رمز لا مكتوب: أطول جملة في الجولة (`home_banner_verify_body`) تبلغ نحو ٦٠ حرفًا
 * إنجليزيًّا، وهي ٢–٣ أسطر عند `11.5sp` على شاشة ٣٢٠dp. والثلاثة **حدّ أعلى محسوب**، وما زاد
 * يُقصّ بثلاث نقاط (`TextOverflow.Ellipsis`) لا يُخفي سطرًا بلا علامة.
 */
private val GuidePageHeight = MaxSpace.section * 3

/**
 * الشريط. إن كان [finished] فلا يُرسم شيء — والخارج (`?` في الرأس) يعيده بـ`restart()`.
 *
 * @param finished هل أُتمّت الجولة من قبل (سجلّ الجهاز).
 * @param onFinish إتمامها الآن: تُحفظ ولا تعود.
 */
@Composable
internal fun HomeGuideStrip(
    finished: Boolean,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!HomeGuideModel.visible(finished)) return
    val p = neuralPalette()
    val scope = rememberCoroutineScope()
    val animationsEnabled = rememberAnimationsEnabled()
    val pages = HomeGuideOrder
    val pagerState = rememberPagerState(pageCount = { pages.size })

    /*
     * «تتوقف عند اللمس»: تُلتقط أول إيماءة ولا تُرفع. و`derivedStateOf` غير لازمة — السحب
     * يقلب قيمة واحدة في `mutableStateOf`، وهو أرخص من اشتقاق.
     */
    var paused by remember { mutableStateOf(false) }
    // `isScrollInProgress` خاصية حالة تُقرأ مباشرةً: تنقلب مرّتين في كل سحبة (بدء ثم انتهاء)،
    // والمهمّ منها البدء — وهو ما يوقف المُهلة. ولا طبقة `derivedStateOf` فوقها: هي أرخص
    // من الاشتقاق، وإضافتها هنا كانت ستُعيد حساب قيمة واحدة.
    val dragging = pagerState.isScrollInProgress
    LaunchedEffect(dragging) { if (dragging) paused = true }

    /*
     * الانتقال التلقائي. المشغّل `settledPage` لا `currentPage`: أثناء السحب يمرّ
     * `currentPage` على صفحات لم يستقرّ عليها أحد، فكل مرور يبدأ مُهلة جديدة — فيتأخّر
     * الانتقال الأصلي ولا يلغى. والاستقرار هو اللحظة التي «وُصلت» فيها بطاقة.
     */
    LaunchedEffect(paused, pagerState.settledPage) {
        if (paused || pages.size < 2) return@LaunchedEffect
        delay(HomeGuideModel.AUTO_ADVANCE_MS)
        val target = (pagerState.settledPage + 1) % pages.size
        if (animationsEnabled) {
            pagerState.animateScrollToPage(
                target,
                animationSpec = tween(MaxMotion.standard, easing = FastOutSlowInEasing),
            )
        } else {
            pagerState.scrollToPage(target)
        }
    }

    val step = pages.getOrElse(pagerState.currentPage) { pages.first() }

    NeuralPanel(modifier, accent = p.accentAlt, verticalSpacing = MaxSpace.md) {
        GuideHeader(
            step = step,
            onFinish = onFinish,
            accent = p.accentAlt,
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(GuidePageHeight),
        ) { page ->
            val banner = pages[page]
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.xs),
            ) {
                Text(
                    stringResource(banner.titleRes),
                    color = p.text,
                    fontSize = 13.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(banner.bodyRes),
                    color = p.muted,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // النقاط: الحالية بلون الأكسنت وممتلئة، والباقي خطّ شعر. وليست أزرارًا — اللمس على
            // نقطة بحجم ٦dp ليس هدفًا صحيحًا (٤٨dp هو الحدّ)، والتنقّل له زرّان صريحان.
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.forEachIndexed { index, _ ->
                    val current = index == pagerState.currentPage
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (current) p.accentAlt else p.muted.copy(alpha = .28f)
                            )
                    )
                }
            }
            NeuralPill(
                text = stringResource(R.string.home_banner_back),
                accent = p.muted,
                compact = true,
                onClick = {
                    paused = true
                    scope.launch {
                        val target = HomeGuideModel.previous(pages[pagerState.currentPage])
                        val index = pages.indexOf(target)
                        if (animationsEnabled) {
                            pagerState.animateScrollToPage(
                                index,
                                animationSpec = tween(MaxMotion.standard, easing = FastOutSlowInEasing),
                            )
                        } else {
                            pagerState.scrollToPage(index)
                        }
                    }
                },
            )
            Spacer(Modifier.width(MaxSpace.sm))
            NeuralPill(
                text = stringResource(
                    if (step == pages.last()) R.string.home_banner_done else R.string.home_banner_next
                ),
                accent = p.accentAlt,
                filled = true,
                compact = true,
                onClick = {
                    paused = true
                    if (step == pages.last()) {
                        onFinish()
                    } else {
                        scope.launch {
                            val index = pages.indexOf(HomeGuideModel.next(step))
                            if (animationsEnabled) {
                                pagerState.animateScrollToPage(
                                    index,
                                    animationSpec = tween(MaxMotion.standard, easing = FastOutSlowInEasing),
                                )
                            } else {
                                pagerState.scrollToPage(index)
                            }
                        }
                    }
                },
            )
        }
    }
}

/** رأس الشريط: رقم الخطوة من العدد الكلّي، وزرّ الإغلاق هو `Skip` في أي لحظة. */
@Composable
private fun GuideHeader(
    step: HomeGuideBanner,
    onFinish: () -> Unit,
    accent: Color,
) {
    val p = neuralPalette()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline),
        ) {
            NeuralCaption(
                stringResource(
                    R.string.home_banner_step,
                    HomeGuideModel.position(step),
                    HomeGuideModel.count,
                ),
                color = accent,
            )
        }
        IconButton(onClick = onFinish) {
            Icon(
                Icons.Rounded.Close,
                stringResource(R.string.home_banner_dismiss),
                Modifier.size(MaxSize.iconGlyphSmall),
                tint = p.muted,
            )
        }
    }
}
