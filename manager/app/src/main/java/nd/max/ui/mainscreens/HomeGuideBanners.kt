/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * بانرات الرئيسية — **بطاقات عريضة تتحرك وحدها وتتوقف عند اللمس** (عقد §5.4 من خطة المستوى).
 *
 * كانت شريط خطوات صغيرًا (رقم + نصّ + Back/Next) داخل بطاقة بأعلى ارتفاع وأقلّ مضمون، فتقرأها
 * العين خلل تخطيط لا بانرًا. وصارت **بانرًا بصفحة كاملة**: عنوان كبير، جملة صدق، رقم ترتيبها
 * خافتًا خلف النصّ، ورسم من دوائر الأكسنت حول أيقونة الموضوع، ونقاط تحتها تُظهر موضعك. والتنقّل
 * بالسحب أو بالانتظار؛ ولا Back/Next لأنها بانرات لا معالج إدخال.
 *
 * ثلاثة قرارات تُشرح هنا لأنها ليست ذوقًا:
 *
 * 1. **الإيقاف عند اللمس لا عند الحركة.** كان الإيقاف مربوطًا بمؤشّر «التمرير جارٍ» في `PagerState`، وهذا
 *    ترتفع في السحب **وفي الانتقال التلقائي معًا** (مؤشّر «التمرير جارٍ» لا يفرّق بين اللمس والحركة) — فيتوقف التمرير التلقائي بعد أول انتقال
 *    له وحده. المُلتقَط الآن هو **السحب** (`collectIsDraggedAsState`): من لمس الشريط صار القارئ،
 *    ولا يتحرك بعده إلا بأمره.
 * 2. **الانتقال بمنحنى مُقنَّن** `tween(MaxMotion.standard)` (٢٦٠ms): `animateScrollToPage`
 *    بلا مواصفة يستعمل `spring()` وينتهي إلى ما يقارب نصف ثانية — فوق سقف الحركة ٣٦٠ms.
 * 3. **الحشوة والنقاط من رموز المسافات** — بوابة `design_tokens.py --assert` تُهبط أي حرفيّ جديد.
 *
 * **وما لا يفعله الشريط:** لا يفتح شاشة ولا ينفّذ فعلًا. جملة واحدة في كل بطاقة، والباب يفتحه
 * المستخدم من البطاقة التي تخصّه في الشاشة نفسها. والرسم من لغة Max (دوائر الأكسنت من الثيم)، لا
 * رسم تطبيق آخر ولا لونه.
 */
package nd.max.ui.mainscreens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.delay
import nd.max.R
import nd.max.ui.component.MaxMotion
import nd.max.ui.component.NeuralPalette
import nd.max.ui.component.NeuralPill
import nd.max.ui.component.neuralPalette
import nd.max.ui.component.rememberAnimationsEnabled
import nd.max.ui.design.MaxAlpha
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace

/**
 * ارتفاع البانر: عنوان بسطرين + جملة بثلاثة أسطر + حشوة، وما زاد يُقصّ بثلاث نقاط
 * (`TextOverflow.Ellipsis`) لا يُخفي سطرًا بلا علامة.
 */
private val BannerHeight = 176.dp

/** قطر رسم البانر والأيقونة داخله. */
private val BannerArtSize = 92.dp
private val BannerGlyphSize = 38.dp

/** النقطة العاديّة وعرض النقطة الحالية (تتمدّد حبّةً فتُقرأ «أنت هنا»). */
private val DotSize = 6.dp
private val DotActiveWidth = 20.dp

/**
 * الشريط. إن كان [finished] فلا يُرسم شيء — والخارج (`?` في الرأس) يعيده بـ`restart()`.
 *
 * @param finished هل أُتمّت الجولة من قبل (سجلّ الجهاز).
 * @param onFinish إتمامها الآن (`Skip` في أي لحظة أو `Done` على آخر بطاقة): تُحفظ ولا تعود.
 */
@Composable
internal fun HomeGuideStrip(
    finished: Boolean,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!HomeGuideModel.visible(finished)) return
    val p = neuralPalette()
    val animationsEnabled = rememberAnimationsEnabled()
    val pages = HomeGuideOrder
    val pagerState = rememberPagerState(pageCount = { pages.size })

    // «تتوقف عند اللمس»: يُلتقط أول سحب ولا يُرفع (القرار الأول في رأس الملفّ).
    var paused by remember { mutableStateOf(false) }
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) paused = true }

    /*
     * الانتقال التلقائي. المشغّل `settledPage` لا `currentPage`: أثناء السحب يمرّ `currentPage`
     * على صفحات لم يستقرّ عليها أحد فيبدأ كلّ مرور مُهلة جديدة. والاستقرار هو اللحظة التي
     * «وُصلت» فيها بطاقة.
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

    val last = pagerState.currentPage >= pages.lastIndex
    val accent = bannerAccent(pagerState.currentPage, p)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaxSpace.md)) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = MaxSpace.md,
            modifier = Modifier.fillMaxWidth().height(BannerHeight),
        ) { page ->
            BannerCard(
                banner = pages[page],
                number = page + 1,
                accent = bannerAccent(page, p),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // النقاط ليست أزرارًا: لمس نقطة بحجم ٦dp ليس هدفًا صحيحًا (٤٨dp هو الحدّ)، والتنقّل
            // بالسحب. وهي تُظهر الموضع فقط.
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.indices.forEach { index ->
                    GuideDot(current = index == pagerState.currentPage, accent = accent)
                }
            }
            NeuralPill(
                text = stringResource(if (last) R.string.home_banner_done else R.string.home_banner_skip),
                accent = if (last) accent else p.muted,
                filled = last,
                compact = true,
                onClick = onFinish,
            )
        }
    }
}

/** لون كل بانر: دورة على لوحة الثيم لا ألوان ثابتة، فيتبع الأكسنت اختيار المستخدم. */
private fun bannerAccent(index: Int, p: NeuralPalette): Color = when (index % 3) {
    0 -> p.accent
    1 -> p.accentAlt
    else -> p.ok
}

/** أيقونة موضوع كل بانر — هنا لا في `HomeGuideModel` لأن ذلك الملفّ لا يعرف `Compose`. */
private fun HomeGuideBanner.glyph(): ImageVector = when (this) {
    HomeGuideBanner.Local -> Icons.Rounded.PhoneAndroid
    HomeGuideBanner.Access -> Icons.Rounded.Shield
    HomeGuideBanner.MaxAi -> Icons.Rounded.AutoAwesome
    HomeGuideBanner.Verify -> Icons.Rounded.CheckCircle
    HomeGuideBanner.Clean -> Icons.Rounded.CleaningServices
    HomeGuideBanner.Cpu -> Icons.Rounded.Memory
}

@Composable
private fun BannerCard(banner: HomeGuideBanner, number: Int, accent: Color) {
    val p = neuralPalette()
    val shape = RoundedCornerShape(MaxRadius.group)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(p.panelTop.copy(alpha = .92f), p.panel)))
            .background(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = .18f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = 620f,
                ),
            )
            .border(MaxSize.hairlineBorder, accent.copy(alpha = MaxAlpha.borderStrong), shape),
    ) {
        // رقم الترتيب خلف النصّ بشفافية ٦٪: يعطي البطاقة ثقلًا بصريًّا بلا أن يُقرأ كمعلومة.
        Text(
            String.format(Locale.US, "%02d", number),
            color = p.text.copy(alpha = .06f),
            fontSize = 72.sp,
            lineHeight = 76.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = MaxSpace.sm, end = MaxSpace.lg),
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(MaxSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.sm),
            ) {
                Text(
                    stringResource(banner.titleRes),
                    color = p.text,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(banner.bodyRes),
                    color = p.muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(MaxSpace.md))
            BannerArt(banner.glyph(), accent)
        }
    }
}

/** ثلاث دوائر متّحدة المركز بشفافيات متدرّجة وحلقة شعرية حول الأيقونة — ثابتة، لا حركة لا نهائية. */
@Composable
private fun BannerArt(icon: ImageVector, accent: Color) {
    Box(Modifier.size(BannerArtSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(accent.copy(alpha = .06f), radius, center)
            drawCircle(accent.copy(alpha = .10f), radius * .76f, center)
            drawCircle(accent.copy(alpha = .16f), radius * .52f, center)
            drawCircle(
                color = accent.copy(alpha = .30f),
                radius = radius - 1.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        Icon(icon, null, Modifier.size(BannerGlyphSize), tint = accent)
    }
}

@Composable
private fun GuideDot(current: Boolean, accent: Color) {
    val p = neuralPalette()
    val width by animateDpAsState(
        targetValue = if (current) DotActiveWidth else DotSize,
        animationSpec = tween(MaxMotion.fast, easing = FastOutSlowInEasing),
        label = "guide-dot",
    )
    Box(
        Modifier
            .height(DotSize)
            .width(width)
            .clip(CircleShape)
            .background(if (current) accent else p.muted.copy(alpha = .28f)),
    )
}
