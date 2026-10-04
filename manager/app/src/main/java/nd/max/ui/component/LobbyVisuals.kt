/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * لغة اللوبي البصرية — **داكنة دائمًا**: اللوبي سطح لعب لا صفحة إعدادات، فلا يتبع ثيم النظام
 * الفاتح (نفس سابقة `GamePanelSurface`). الألوان هنا ثوابت السطح وحده؛ أمّا الفراغات والأنصاف فتأتي
 * من `MaxSpace`/`MaxRadius` حتى لا يتفرّع لها نظام ثانٍ.
 *
 * ### وما استُعير وما لم يُستعَر
 *
 * الشكل العام (قائمة ألعاب يسارية · حلقة HUD وسطى · زرّ بدء مائل · قائمة إعدادات بشريط جانبي) هو
 * ما طلبه المالك صراحةً في هذه الجولة (يتقدّم على بند «لا تقليد شكل» في `AGENTS.md` §0.4 لهذه
 * الشاشة وحدها — انظر تسجيله في `NEXT_TASK.md`). **ولم يُنقل أيّ أصل**: لا شعار، لا شخصية، لا صورة،
 * لا خط؛ الحلقة والعلامة المركزية مرسومتان هنا بالكود، والعلامة مختلفة عن علامة أي جهة.
 */
object LobbyPalette {
    val Black = Color(0xFF050608)
    val Surface = Color(0xFF0E1015)
    val Panel = Color(0xFF1B1E2C)
    val PanelRaised = Color(0xFF282D44)
    val PanelSelected = Color(0xFF454B78)
    val Red = Color(0xFFE5262B)
    val RedBright = Color(0xFFFF5257)
    val RedDeep = Color(0xFF5A0A10)
    val Ink = Color(0xFFF2F3F8)
    val Muted = Color(0xFF8F96AB)
    val Hairline = Color(0x33FFFFFF)
}

/** سداسيّ مدبَّب الجانبين — شكل الأزرار الصغيرة (المقبض، الشارة، زرّ التقدّم). */
val LobbyHexShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val k = w * 0.25f
    moveTo(k, 0f)
    lineTo(w - k, 0f)
    lineTo(w, h / 2f)
    lineTo(w - k, h)
    lineTo(k, h)
    lineTo(0f, h / 2f)
    close()
}

/** ثُمانيّ بزوايا مقطوعة — زرّ «ابدأ». */
val LobbyAngledShape = GenericShape { size, _ ->
    val c = size.height * 0.30f
    val w = size.width
    val h = size.height
    moveTo(c, 0f)
    lineTo(w - c, 0f)
    lineTo(w, c)
    lineTo(w, h - c)
    lineTo(w - c, h)
    lineTo(c, h)
    lineTo(0f, h - c)
    lineTo(0f, c)
    close()
}

/** شبه منحرف أعرض عند القاعدة — لسان التبويب السفلي. */
val LobbyTabShape = GenericShape { size, _ ->
    val s = size.height * 0.60f
    moveTo(s, 0f)
    lineTo(size.width - s, 0f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

/**
 * هل الحركة مسموحة؟ — مقياس النظام `animator_duration_scale`؛ إذا صفّره المستخدم لا تدور الحلقة.
 * (دعم الحركة المخفَّضة يمرّ من هذا الباب الواحد، لا من فحص في كل مكوّن.)
 */
@Composable
fun rememberLobbyAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
}

/**
 * حلقة الـHUD الوسطى: توهّج أحمر · حلقة علامات تدور ببطء · قوسان جانبيان · قرص داخلي بنقاط شبكية ·
 * علامة مركزية. كلّها `Canvas` — لا أصل نقطيّ ولا ملف.
 *
 * الطبقة الدوّارة **منفصلة** عن الثابتة وتُدار بـ`graphicsLayer`، فالدوران لا يعيد رسم القرص
 * الداخلي (مئات النقاط) كل إطار.
 */
@Composable
fun LobbyEmblem(modifier: Modifier = Modifier, animate: Boolean = true) {
    val spin: State<Float> = if (animate) {
        val transition = rememberInfiniteTransition(label = "lobbyRing")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 90_000, easing = LinearEasing), RepeatMode.Restart),
            label = "lobbyRingSpin"
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) { drawEmblemStatic(this) }
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin.value }) { drawEmblemTicks(this) }
    }
}

private fun drawEmblemStatic(scope: DrawScope) = with(scope) {
    val c = center
    val r = min(size.width, size.height) / 2f
    // توهّج خلفي
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(LobbyPalette.Red.copy(alpha = 0.34f), LobbyPalette.Red.copy(alpha = 0.08f), Color.Transparent),
            center = c,
            radius = r * 1.18f
        ),
        radius = r * 1.18f,
        center = c
    )
    // الحلقة الخارجية الداكنة
    drawCircle(color = LobbyPalette.Surface, radius = r * 0.93f, center = c)
    drawCircle(color = Color(0xFF22252E), radius = r * 0.93f, center = c, style = Stroke(width = r * 0.012f))
    // قوسان جانبيان (يسار/يمين)
    val arcRadius = r * 0.74f
    val arcStroke = r * 0.055f
    val arcTopLeft = Offset(c.x - arcRadius, c.y - arcRadius)
    val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
    listOf(130f to 100f, -50f to 100f).forEach { (start, sweep) ->
        drawArc(
            color = Color(0xFF3A3D48),
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = arcStroke, cap = StrokeCap.Butt)
        )
        drawArc(
            color = LobbyPalette.Red.copy(alpha = 0.55f),
            startAngle = start + 8f,
            sweepAngle = sweep * 0.45f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = arcStroke * 0.30f, cap = StrokeCap.Butt)
        )
    }
    // القرص الداخلي + شبكة النقاط
    val inner = r * 0.52f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF2B0609), Color(0xFF0B0405)),
            center = c,
            radius = inner
        ),
        radius = inner,
        center = c
    )
    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, inner)) }
    clipPath(clip) {
        val step = r * 0.047f
        val dot = r * 0.0075f
        var y = c.y - inner
        while (y <= c.y + inner) {
            var x = c.x - inner
            while (x <= c.x + inner) {
                drawCircle(color = LobbyPalette.Red.copy(alpha = 0.30f), radius = dot, center = Offset(x, y))
                x += step
            }
            y += step
        }
    }
    drawCircle(color = LobbyPalette.Red.copy(alpha = 0.55f), radius = inner, center = c, style = Stroke(width = r * 0.008f))
    drawEmblemMark(this, c, inner * 0.52f)
}

private fun drawEmblemTicks(scope: DrawScope) = with(scope) {
    val c = center
    val r = min(size.width, size.height) / 2f
    val count = 120
    for (i in 0 until count) {
        val angle = (2.0 * PI * i / count).toFloat()
        val major = i % 10 == 0
        val inner = r * (if (major) 0.835f else 0.855f)
        val outer = r * 0.90f
        val color = if (major) LobbyPalette.Red.copy(alpha = 0.85f) else Color(0xFF50535F)
        drawLine(
            color = color,
            start = Offset(c.x + cos(angle) * inner, c.y + sin(angle) * inner),
            end = Offset(c.x + cos(angle) * outer, c.y + sin(angle) * outer),
            strokeWidth = if (major) r * 0.010f else r * 0.006f
        )
    }
}

/** العلامة المركزية: ثلاث شفرات مائلة (وسطى أطول). رسم أصلي، لا يطابق علامة أي جهة. */
private fun drawEmblemMark(scope: DrawScope, c: Offset, s: Float) = with(scope) {
    fun blade(cx: Float, top: Float, bottom: Float, width: Float, skew: Float): Path = Path().apply {
        moveTo(c.x + (cx - width / 2f + skew) * s, c.y + top * s)
        lineTo(c.x + (cx + width / 2f + skew) * s, c.y + top * s)
        lineTo(c.x + (cx + width / 2f) * s, c.y + bottom * s)
        lineTo(c.x + (cx - width / 2f) * s, c.y + bottom * s)
        close()
    }
    val brush = Brush.verticalGradient(
        colors = listOf(LobbyPalette.RedBright, LobbyPalette.RedDeep),
        startY = c.y - s,
        endY = c.y + s
    )
    drawPath(blade(cx = 0f, top = -1.0f, bottom = 0.95f, width = 0.34f, skew = 0f), brush = brush)
    drawPath(blade(cx = -0.70f, top = -0.62f, bottom = 0.70f, width = 0.30f, skew = 0.22f), brush = brush)
    drawPath(blade(cx = 0.70f, top = -0.62f, bottom = 0.70f, width = 0.30f, skew = -0.22f), brush = brush)
}

/** خلفية اللوبي: أسود بتدرّج أحمر خافت من الوسط. */
fun lobbyBackdropBrush(): Brush = Brush.radialGradient(
    colors = listOf(Color(0xFF1A0508), LobbyPalette.Black),
    radius = 1400f
)

// ───────────────────────────── نافذة الشاشة: عرضي + ملء الشاشة ─────────────────────────────

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * يقفل الاتجاه عرضيًّا ويُخفي أشرطة النظام ما دام اللوبي ظاهرًا، ويُعيد ما كان عند الخروج.
 * الأشرطة تظهر بسحبة من الحافة (سلوك النظام)، فلا يُحبس المستخدم خارج التنقّل.
 */
@Composable
fun LobbyWindowEffect() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val activity = context.findActivity()
        val previousOrientation = activity?.requestedOrientation
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (activity != null && previousOrientation != null) activity.requestedOrientation = previousOrientation
        }
    }
}

// ───────────────────────────── قراءات الشريط العلوي ─────────────────────────────

data class LobbyBattery(val percent: Int, val charging: Boolean)

/** بطارية الجهاز: قراءة لاصقة (sticky) بلا تسجيل مستقبِل، تُجدَّد كل نصف دقيقة. `null` قبل أول قراءة. */
@Composable
fun rememberLobbyBattery(): State<LobbyBattery?> {
    val context = LocalContext.current
    return produceState<LobbyBattery?>(null, context) {
        while (true) {
            value = readBattery(context)
            delay(30_000L)
        }
    }
}

private fun readBattery(context: Context): LobbyBattery? = runCatching {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    LobbyBattery(percent = (level * 100 / scale).coerceIn(0, 100), charging = charging)
}.getOrNull()

/** ساعة الجهاز بتنسيق النظام (12/24)، تتحدّث عند بداية كل دقيقة. */
@Composable
fun rememberLobbyClock(): State<String> {
    val context = LocalContext.current
    return produceState("", context) {
        val format = DateFormat.getTimeFormat(context)
        while (true) {
            val now = System.currentTimeMillis()
            value = format.format(Date(now))
            delay(60_000L - now % 60_000L + 50L)
        }
    }
}
