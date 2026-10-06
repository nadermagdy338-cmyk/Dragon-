/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.component

import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nd.max.core.gamespace.LobbyModel
import nd.max.ui.design.MaxDuration
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace

/*
 * لوبي بطاقات Carousel بعمق: البطاقة المحدَّدة كبيرة بإطار متوهّج، وجارتاها أصغر ومائلتان
 * وخافتتان. كل التحويل (مقياس · ميل · شفافية · انزلاق) يُحسب في **مرحلة الطبقة** من إزاحة
 * الصفحة، فلا إعادة تركيب أثناء السحب. وكل الرسم بالكود؛ الصور الوحيدة أيقونات التطبيقات.
 *
 * **غير مُتحقَّق على جهاز:** اتجاه ميل الجارتين (يُفترض أن حافتيهما الخارجيّتين أقرب للمشاهد،
 * أي «شاشة منحنية»)، وسلوك `blur` تحت API 31 (لا أثر له هناك فتبقى الأيقونة المكبَّرة مموَّهة
 * بالترشيح فقط وتحجبها القاعدة السوداء المتدرّجة).
 */

/** مقياس الجارتين مقابل المركزية. */
private const val SIDE_SCALE = 0.78f

/** شفافية الجارتين. */
private const val SIDE_ALPHA = 0.60f

/** ميل الجارتين بالدرجات حول المحور الرأسيّ. */
private const val SIDE_TILT_DEG = 20f

/** كم تُسحب الجارتان نحو المركز (نسبة من عرض البطاقة) لتعويض صِغرهما. */
private const val SIDE_TUCK = 0.12f

/** مسافة الكاميرا (× الكثافة): أصغر منها يشتدّ المنظور. */
private const val CAMERA_DISTANCE = 14f

/** هامش رأسيّ حول البطاقة يتّسع للتوهّج، لأن الـPager يقصّ ما يخرج عن ارتفاعه. تحسبه الشاشة أيضًا لحجم البطاقة. */
internal val LobbyGlowPad = MaxSpace.xl

private val CardCut = MaxRadius.control
private val AvatarSize = 56.dp
private val GlowWide = 12.dp
private val GlowNear = 6.dp
private val ArtBlur = 24.dp
private val UnderlineHeight = 2.dp

/** أيقونات صغيرة بحجم ثابت تكفي لاستخراج اللون وكخلفية مموَّهة. */
private val IconRaster = 128.dp

private const val NO_TONE = -1
private const val OPAQUE = 0xFF000000.toInt()
private val toneCache = LruCache<String, Int>(96)

// ───────────────────────────── أيقونة اللعبة ولونها ─────────────────────────────

/** أيقونة التطبيق من نفس `AppIconCache` الذي تستعمله بقية الشاشات؛ `null` إلى أن تُقرأ. */
@Composable
private fun rememberLobbyIcon(packageName: String?): State<ImageBitmap?> {
    val context = LocalContext.current
    val density = LocalDensity.current
    val px = remember(density) { with(density) { IconRaster.roundToPx() } }
    return produceState<ImageBitmap?>(packageName?.let { AppIconCache.get(it) }, packageName) {
        if (packageName == null) {
            value = null
        } else if (value == null) {
            value = runCatching { AppIconCache.loadIcon(context.packageManager, packageName, px) }.getOrNull()
        }
    }
}

/**
 * لون اللعبة المميَّز، أو أحمر العلامة إن لم يكن في أيقونتها لون ظاهر (رماديّة/سوداء/شفّافة).
 * يُحسب مرّة لكل حزمة على `Default` ويُخزَّن، فلا يتكرّر عند كل تمرير.
 */
@Composable
fun rememberLobbyTone(packageName: String?): Color {
    val icon by rememberLobbyIcon(packageName)
    val rgb by produceState<Int?>(packageName?.let { toneCache.get(it) }, packageName, icon) {
        val pkg = packageName ?: return@produceState
        toneCache.get(pkg)?.let {
            value = it
            return@produceState
        }
        val bitmap = icon ?: return@produceState
        val computed = withContext(Dispatchers.Default) {
            runCatching {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.readPixels(pixels, 0, 0, bitmap.width, bitmap.height)
                LobbyModel.dominantRgb(pixels)?.let { LobbyModel.lift(it) }
            }.getOrNull()
        }
        val stored = computed ?: NO_TONE
        toneCache.put(pkg, stored)
        value = stored
    }
    val resolved = rgb
    return if (resolved == null || resolved == NO_TONE) LobbyPalette.Red else Color(OPAQUE or resolved)
}

// ───────────────────────────── بطاقة اللعبة ─────────────────────────────

/**
 * بطاقة لعبة واحدة. [emphasis] دالة (لا قيمة) كي تُقرأ في مرحلة الرسم فقط: 1 للمركزية و0 للبعيدة.
 *
 * @param starEnabled زرّ المفضّلة يعمل للمركزية وحدها؛ في الجارتين لا يُرسم ولا يُلمس.
 * @param positionDescription وصف الإتاحة، مثل «Race Master، ٣ من ٧».
 */
@Composable
fun LobbyGameCard(
    packageName: String,
    title: String,
    meta: String,
    favorite: Boolean,
    favoriteDescription: String,
    positionDescription: String,
    emphasis: () -> Float,
    breathe: State<Float>,
    starEnabled: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = remember { LobbyChamferShape(CardCut) }
    val avatarShape = remember { LobbyChamferShape(MaxSpace.sm) }
    val icon by rememberLobbyIcon(packageName)
    val tone = rememberLobbyTone(packageName)
    Box(
        modifier
            .fillMaxSize()
            .semantics { contentDescription = positionDescription }
            .drawBehind {
                val e = emphasis()
                if (e > 0.02f) drawGlow(outlinePath(shape), e * (0.55f + 0.45f * breathe.value))
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tone.copy(alpha = 0.55f), LobbyPalette.Surface)))
            .drawWithContent {
                drawContent()
                drawRim(outlinePath(shape), emphasis())
            }
    ) {
        val art = icon
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(ArtBlur)
                    .graphicsLayer {
                        scaleX = 1.5f
                        scaleY = 1.5f
                        alpha = 0.45f
                    }
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.40f to Color.Transparent,
                        1f to LobbyPalette.Black.copy(alpha = 0.88f)
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(MaxSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
        ) {
            Box(
                Modifier
                    .size(AvatarSize)
                    .clip(avatarShape)
                    .border(MaxSize.hairlineBorder, LobbyPalette.Hairline, avatarShape)
            ) {
                AppIconImage(packageName = packageName, size = AvatarSize, contentDescription = null)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)) {
                Text(
                    text = title,
                    color = LobbyPalette.Ink,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        color = LobbyPalette.Muted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(MaxSize.minTouchTarget)
                .graphicsLayer { alpha = emphasis() }
                .then(
                    if (starEnabled) {
                        Modifier.clickable(role = Role.Button, onClickLabel = favoriteDescription, onClick = onToggleFavorite)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = null,
                tint = if (favorite) LobbyPalette.RedBright else LobbyPalette.Ink
            )
        }
    }
}

/** مسار شكل البطاقة بقياس الرسم الحاليّ. */
private fun DrawScope.outlinePath(shape: LobbyChamferShape): Path =
    (shape.createOutline(size, layoutDirection, this) as? Outline.Generic)?.path
        ?: Path().apply { addRect(Rect(Offset.Zero, size)) }

/** توهّج الإطار: خطّان عريضان شفّافان خارج البطاقة، بلا `blur` حيّ. يتنفّس مع [alpha]. */
private fun DrawScope.drawGlow(path: Path, alpha: Float) {
    drawPath(path, LobbyPalette.Red.copy(alpha = 0.10f * alpha), style = Stroke(width = GlowWide.toPx(), join = StrokeJoin.Round))
    drawPath(path, LobbyPalette.Red.copy(alpha = 0.22f * alpha), style = Stroke(width = GlowNear.toPx(), join = StrokeJoin.Round))
}

/**
 * الحافّة الحادّة: تدرّج أحمر → سيان. تُرسم بضعف العرض لأن نصفها الخارجيّ يقصّه قصّ الشكل،
 * فيبقى المرئيّ بعرض [MaxSize.activeRing].
 */
private fun DrawScope.drawRim(path: Path, emphasis: Float) {
    val brush = Brush.linearGradient(
        colors = listOf(LobbyPalette.RedBright, LobbyPalette.Red, LobbyPalette.Cyan.copy(alpha = 0.85f)),
        start = Offset.Zero,
        end = Offset(size.width, size.height)
    )
    drawPath(
        path = path,
        brush = brush,
        alpha = 0.30f + 0.70f * emphasis,
        style = Stroke(width = MaxSize.activeRing.toPx() * 2f, join = StrokeJoin.Round)
    )
}

// ───────────────────────────── الـPager بعمق ─────────────────────────────

private fun pageOffset(state: PagerState, index: Int): Float =
    (state.currentPage - index) + state.currentPageOffsetFraction

/**
 * Carousel عرضيّ: صفحة مركزية بعرض [cardWidth] وجارتان مائلتان. يراعي RTL: يُعكس اتجاه الميل
 * والسحب نحو المركز بإشارة [direction] بدل شيفرة شرطيّة في كل مكان.
 *
 * @param onSelect نقرة على جارة تنقلك إليها (المركزية لا تستجيب، فلا يُطلق شيء بنقرة عابرة).
 * @param page محتوى الصفحة؛ يستلم [emphasis] ليرسم نفسه حسب قربه من المركز.
 */
@Composable
fun LobbyCardPager(
    state: PagerState,
    cardWidth: Dp,
    cardHeight: Dp,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    key: ((Int) -> Any)? = null,
    page: @Composable (index: Int, emphasis: () -> Float) -> Unit
) {
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val inset = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp)
        HorizontalPager(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight + LobbyGlowPad * 2),
            contentPadding = PaddingValues(horizontal = inset),
            pageSize = PageSize.Fixed(cardWidth),
            beyondViewportPageCount = 1,
            pageSpacing = MaxSpace.md,
            key = key
        ) { index ->
            val emphasis = { 1f - abs(pageOffset(state, index)).coerceIn(0f, 1f) }
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex((100 - abs(index - state.currentPage)).toFloat())
                    .graphicsLayer {
                        val offset = pageOffset(state, index)
                        val side = offset.coerceIn(-1f, 1f)
                        val distance = abs(side)
                        val scale = 1f - (1f - SIDE_SCALE) * distance
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - (1f - SIDE_ALPHA) * distance
                        rotationY = side * SIDE_TILT_DEG * direction
                        translationX = side * size.width * SIDE_TUCK * direction
                        cameraDistance = CAMERA_DISTANCE * density
                    }
                    .then(
                        // المركزية بلا clickable أصلًا (لا «معطَّل» في شجرة الإتاحة)؛ الجارتان تنقلانك إليهما.
                        if (index != state.currentPage) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(index) }
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = LobbyGlowPad)
            ) {
                page(index, emphasis)
            }
        }
    }
}

// ───────────────────────────── شريط التبويب العلويّ ─────────────────────────────

/**
 * شريط تبويب مائل معلَّق. تبويباته **مرشِّحات حقيقية** للقائمة نفسها (الكل · المفضّلة)، لا
 * وجهات بلا شاشة خلفها — أي تبويب لا تقف خلفه ميزة جاهزة لا يُرسم.
 *
 * [descriptions] وصف الإتاحة لكل تبويب (أطول من النص المرئيّ حيث يلزم، مثل «المفضّلة فقط»).
 */
@Composable
fun LobbyTabStrip(
    labels: List<String>,
    descriptions: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .semantics { contentDescription = description }
            .clip(LobbyStripShape)
            .background(
                Brush.verticalGradient(
                    listOf(LobbyPalette.PanelRaised.copy(alpha = 0.55f), LobbyPalette.Surface.copy(alpha = 0.85f))
                )
            )
            .padding(horizontal = MaxSpace.xl),
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            val mark = animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = tween(MaxDuration.quick),
                label = "lobbyTab"
            )
            Box(
                modifier = Modifier
                    .heightIn(min = MaxSize.minTouchTarget)
                    .widthIn(min = MaxSize.minTouchTarget)
                    .semantics { contentDescription = descriptions.getOrElse(index) { label } }
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                    .drawBehind {
                        val a = mark.value
                        if (a > 0.01f) {
                            val w = size.width * 0.70f * a
                            drawRect(
                                color = LobbyPalette.Red.copy(alpha = 0.35f * a),
                                topLeft = Offset((size.width - w) / 2f, size.height - UnderlineHeight.toPx() * 3f),
                                size = Size(w, UnderlineHeight.toPx() * 3f)
                            )
                            drawRect(
                                color = LobbyPalette.RedBright.copy(alpha = a),
                                topLeft = Offset((size.width - w) / 2f, size.height - UnderlineHeight.toPx()),
                                size = Size(w, UnderlineHeight.toPx())
                            )
                        }
                    }
                    .padding(horizontal = MaxSpace.lg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) LobbyPalette.Ink else LobbyPalette.Muted,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}
