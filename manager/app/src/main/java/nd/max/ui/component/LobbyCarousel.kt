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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Shadow
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
import androidx.compose.ui.text.TextStyle
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
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace

/*
 * لوبي بطاقات Carousel بعمق: البطاقة المحدَّدة كبيرة بإطار نيون متدرّج (وردي-أحمر ← أزرق) وتوهّج
 * خارجيّ، وجارتاها أضيق ومائلتان بمنظور قويّ وخافتتان. كل التحويل (مقياس · ميل · شفافية · انزلاق)
 * يُحسب في **مرحلة الطبقة** من إزاحة الصفحة، فلا إعادة تركيب أثناء السحب. وكل الرسم بالكود؛
 * الصور الوحيدة أيقونات التطبيقات.
 *
 * اتجاه الميل: الحافة الخارجية لكل جارة أقرب للمشاهد وأطول، والداخلية أبعد وأقصر («شاشة منحنية»)،
 * وهذا ما تُظهره اللقطة المرجعية. **غير مُتحقَّق على جهاز:** سلوك `blur` تحت API 31 (لا أثر له
 * هناك فتبقى الأيقونة المكبَّرة مموَّهة بالترشيح فقط وتحجبها القاعدة السوداء المتدرّجة).
 */

/** مقياس الجارتين مقابل المركزية (مع الميل تضيق الجارة إلى نحو ٧٣٪ من عرض المركزية). */
private const val SIDE_SCALE = 0.90f

/** شفافية الجارتين. */
private const val SIDE_ALPHA = 0.60f

/** ميل الجارتين بالدرجات حول المحور الرأسيّ. */
private const val SIDE_TILT_DEG = 36f

/** كم تُسحب الجارتان نحو المركز (نسبة من عرض البطاقة): تُبقي بين الحافتين فجوة ضيقة كما في المرجع. */
private const val SIDE_TUCK = 0.08f

/** مسافة الكاميرا (× الكثافة): أصغر منها يشتدّ المنظور. */
private const val CAMERA_DISTANCE = 14f

/** هامش رأسيّ حول البطاقة يتّسع للتوهّج، لأن الـPager يقصّ ما يخرج عن ارتفاعه. تحسبه الشاشة أيضًا لحجم البطاقة. */
internal val LobbyGlowPad = MaxSpace.xl

private val CardCut = MaxSpace.sm
private val GlowWide = 18.dp
private val GlowMid = 10.dp
private val GlowNear = 5.dp
private val ArtBlur = 16.dp
private val UnderlineHeight = 2.dp
private val TabSlotMin = 112.dp
private val StripPad = MaxSpace.xxl + MaxSpace.sm

/** أجزاء من ارتفاع البطاقة تُشتقّ منها أحجام الصورة الدائرية والشارة والنصوص، فتتناسب مع أي مقاس. */
private const val AVATAR_SHARE = 0.27f
private const val BADGE_SHARE = 0.17f
private const val TITLE_SHARE = 0.088f
private const val META_SHARE = 0.068f
/** ظلّ خفيف خلف نصّ البطاقة ليبقى مقروءًا فوق أي غلاف فاتح. */
private val TextShadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 6f)
private val AvatarMin = 40.dp
private val AvatarMax = 64.dp
private val BadgeMin = 26.dp
private val BadgeMax = 34.dp

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
 * التخطيط: خلفية من أيقونة اللعبة، وفي الأسفل صورة دائرية + العنوان + سطر البيانات، وفي الزاوية
 * العليا شارة دائرية داكنة تحمل نجمة المفضّلة.
 *
 * @param starEnabled زرّ المفضّلة يعمل للمركزية وحدها؛ في الجارتين تُرسم الشارة حالةً (نجمة ممتلئة إن
 *   كانت اللعبة مفضّلة) ولا تُلمس، فنقرة الجارة تنقلك إليها.
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
    val icon by rememberLobbyIcon(packageName)
    val tone = rememberLobbyTone(packageName)
    BoxWithConstraints(
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
        val avatar = (maxHeight * AVATAR_SHARE).coerceIn(AvatarMin, AvatarMax)
        val badge = (maxHeight * BADGE_SHARE).coerceIn(BadgeMin, BadgeMax)
        val titleSize = (maxHeight.value * TITLE_SHARE).coerceIn(13f, 18f).sp
        val metaSize = (maxHeight.value * META_SHARE).coerceIn(11f, 14f).sp
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
                        alpha = 0.55f
                    }
            )
        }
        // لمعة قطرية خفيفة من الزاوية العليا تُعطي الغلاف سطحًا لامعًا بدل لون مسطّح.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(0f to Color.White.copy(alpha = 0.14f), 0.45f to Color.Transparent))
        )
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
                .padding(horizontal = MaxSpace.lg, vertical = MaxSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
        ) {
            Box(
                Modifier
                    .size(avatar)
                    .clip(CircleShape)
                    .border(MaxSize.hairlineBorder, LobbyPalette.Ink.copy(alpha = 0.55f), CircleShape)
            ) {
                AppIconImage(packageName = packageName, size = avatar, contentDescription = null)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)) {
                Text(
                    text = title,
                    color = LobbyPalette.Ink,
                    fontSize = titleSize,
                    lineHeight = titleSize * 1.2f,
                    fontWeight = FontWeight.Medium,
                    style = TextStyle(shadow = TextShadow),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        color = LobbyPalette.Muted,
                        fontSize = metaSize,
                        lineHeight = metaSize * 1.25f,
                        style = TextStyle(shadow = TextShadow),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(MaxSpace.xs)
                .size(MaxSize.minTouchTarget)
                .then(
                    if (starEnabled) {
                        Modifier.clickable(role = Role.Button, onClickLabel = favoriteDescription, onClick = onToggleFavorite)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(badge)
                    .clip(CircleShape)
                    .background(LobbyPalette.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                    tint = if (favorite) LobbyPalette.RedBright else LobbyPalette.Ink,
                    modifier = Modifier.size(badge * 0.62f)
                )
            }
        }
    }
}

/** مسار شكل البطاقة بقياس الرسم الحاليّ. */
private fun DrawScope.outlinePath(shape: LobbyChamferShape): Path =
    (shape.createOutline(size, layoutDirection, this) as? Outline.Generic)?.path
        ?: Path().apply { addRect(Rect(Offset.Zero, size)) }

/**
 * فرشاة النيون: وردي-أحمر عند الأعلى والبداية يستقرّ حتى ٤٠٪ ثم ينتقل إلى الأزرق الملكيّ عند النهاية
 * والأسفل — الحافتان العليا واليسرى حمراوان والسفلى واليمنى زرقاوان.
 */
private fun DrawScope.neonBrush(): Brush = Brush.linearGradient(
    0f to LobbyPalette.Neon,
    0.40f to LobbyPalette.Neon,
    0.62f to LobbyPalette.Blue,
    1f to LobbyPalette.Blue,
    start = Offset.Zero,
    end = Offset(size.width, size.height)
)

/** توهّج الإطار: ثلاثة خطوط عريضة شفّافة بالتدرّج نفسه خارج البطاقة، بلا `blur` حيّ. يتنفّس مع [alpha]. */
private fun DrawScope.drawGlow(path: Path, alpha: Float) {
    val brush = neonBrush()
    drawPath(path, brush, alpha = 0.10f * alpha, style = Stroke(width = GlowWide.toPx(), join = StrokeJoin.Round))
    drawPath(path, brush, alpha = 0.20f * alpha, style = Stroke(width = GlowMid.toPx(), join = StrokeJoin.Round))
    drawPath(path, brush, alpha = 0.34f * alpha, style = Stroke(width = GlowNear.toPx(), join = StrokeJoin.Round))
}

/**
 * الحافّة الحادّة بتدرّج النيون. تُرسم بضعف العرض لأن نصفها الخارجيّ يقصّه قصّ الشكل،
 * فيبقى المرئيّ بعرض [MaxSize.activeRing].
 */
private fun DrawScope.drawRim(path: Path, emphasis: Float) {
    drawPath(
        path = path,
        brush = neonBrush(),
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
 * شريط تبويب معلَّق: شبه منحرف أعرض عند الأعلى بكتفين منحنيين وحدّ فولاذيّ خافت على الحافة السفلى.
 * التبويب المحدَّد نصّه أحمر وتحته خطّ أحمر متوهّج. تبويباته **مرشِّحات حقيقية** للقائمة نفسها
 * (الكل · المفضّلة)، لا وجهات بلا شاشة خلفها — أي تبويب لا تقف خلفه ميزة جاهزة لا يُرسم.
 *
 * ارتفاع الشريط يضعه المستدعي في [modifier]؛ والتبويبات تملأ ارتفاعه فيقع الخطّ على حافته السفلى.
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
                    listOf(LobbyPalette.PanelRaised.copy(alpha = 0.55f), LobbyPalette.Surface.copy(alpha = 0.90f))
                )
            )
            .border(
                MaxSize.hairlineBorder,
                Brush.verticalGradient(listOf(Color.Transparent, LobbyPalette.Steel.copy(alpha = 0.55f))),
                LobbyStripShape
            )
            .padding(horizontal = StripPad),
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
                    .fillMaxHeight()
                    .widthIn(min = TabSlotMin)
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
                    .padding(horizontal = MaxSpace.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) LobbyPalette.RedBright else LobbyPalette.Ink.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}
