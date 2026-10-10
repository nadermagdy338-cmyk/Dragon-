/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.component

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Date
import kotlinx.coroutines.delay

/**
 * لغة اللوبي البصرية — **داكنة دائمًا**: اللوبي سطح لعب لا صفحة إعدادات، فلا يتبع ثيم النظام
 * الفاتح (نفس سابقة `GamePanelSurface`). الألوان هنا ثوابت السطح وحده؛ أمّا الفراغات والأنصاف فتأتي
 * من `MaxSpace`/`MaxRadius` حتى لا يتفرّع لها نظام ثانٍ.
 *
 * ### الشكل الحالي: بطاقات Carousel بعمق
 *
 * بطاقة اللعبة المحدَّدة كبيرة بإطار متوهّج، وجارتاها أصغر ومائلتان وخافتتان؛ شريط تبويب علويّ
 * مائل؛ وصفّ أزرار سفليّ يتوسّطه «ابدأ». هذا الشكل طلبه المالك صراحةً لهذه الشاشة (يتقدّم على
 * بند «لا تقليد شكل» في `AGENTS.md` §0.4 لها وحدها — انظر تسجيله في `NEXT_TASK.md`).
 * **ولم يُنقل أيّ أصل**: لا شعار، لا صورة، لا خط، لا ألوان؛ الأشكال والتوهّج مرسومة هنا بالكود،
 * والصور هي أيقونات التطبيقات المثبَّتة فعلًا على الجهاز.
 *
 * ### أحمر العلامة ثابت هنا عن قصد
 *
 * `DESIGN.md` يجعل الـaccent مستعارًا من ثيم المستخدم. اللوبي سطح لعب بهوية ثابتة، فيُثبَّت
 * أحمره (استثناء معلن لهذه الشاشة وللّوحة الجانبية). وبما أنّ الأحمر هو العلامة فهو **لا يعني
 * خطرًا أبدًا**: حالات التحذير تأخذ [Caution]/[Positive] الثابتتين مع أيقونة، لا أحمرًا وحده.
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

    /** سيان — للبيانات الحيّة فقط (حافة البطاقة، قراءات الجاهزية)، ولا يُستعمل لزرّ إجراء. */
    val Cyan = Color(0xFF3DD9FF)

    /**
     * ألوان مسرح اللوبي (الشكل العرضيّ ذو الأجنحة): كحليّ داكن للأرضية، ووردي-أحمر نيون وأزرق ملكيّ
     * للحافة المتوهّجة والأزرار، وفولاذيّ باهت للأجنحة والخلايا السداسية الزخرفية. ثوابت السطح وحده.
     */
    val Navy = Color(0xFF060B1A)
    val NavyGlow = Color(0xFF14337F)
    val Neon = Color(0xFFFF2D55)
    val Blue = Color(0xFF2F7BFF)
    val BlueBright = Color(0xFF6FA8FF)
    val BlueDeep = Color(0xFF0B1B45)
    val Steel = Color(0xFF9AA6C0)

    /** نفس `Positive` في `DESIGN.md` على الداكن (تباين ١٠٫٢٣). */
    val Positive = Color(0xFF5FD9AC)

    /** نفس `Caution` في `DESIGN.md` على الداكن (تباين ١٠٫٥٠). */
    val Caution = Color(0xFFFFB86B)
}

/**
 * سداسيّ مدبَّب الجانبين — للعناصر الصغيرة التي تحمل صورة. محفوظ كما كان.
 */
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

/**
 * شبه منحرف أعرض عند الأعلى بكتفين منحنيين — شريط التبويب العلويّ المعلَّق: الضلعان ينحدران بحدّة
 * قرب السقف ثم يلتفّان نحو الحافة السفلى الأضيق.
 */
val LobbyStripShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val inset = h * 0.85f
    val shoulder = h * 0.25f
    moveTo(0f, 0f)
    lineTo(w, 0f)
    quadraticBezierTo(w - shoulder, h * 0.55f, w - inset, h)
    lineTo(inset, h)
    quadraticBezierTo(shoulder, h * 0.55f, 0f, 0f)
    close()
}

/**
 * سداسيّ مفلطح مدبَّب الجانبين بحافتين مائلتين ٤٥° — أزرار الصفّ السفليّ. القطع [k] نصف الارتفاع
 * (وبحدّ أقصى ٣٠٪ من العرض) فتبقى الزاويتان متماثلتين مهما اختلف عرض الزرّ.
 */
val LobbyHexButtonShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val k = minOf(h * 0.5f, w * 0.30f)
    moveTo(k, 0f)
    lineTo(w - k, 0f)
    lineTo(w, h / 2f)
    lineTo(w - k, h)
    lineTo(k, h)
    lineTo(0f, h / 2f)
    close()
}

/**
 * بصمة اللوبي: زاويتان مقطوعتان بزاوية ٤٥° (بداية-أعلى ونهاية-أسفل) والأخريان حادّتان.
 * تنعكس في RTL فيبقى القطع على القطر نفسه بصريًّا. شكلٌ واحد للبطاقة والبلاطات بدل
 * `GenericShape` متفرّقة بأحجام مختلفة.
 */
class LobbyChamferShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val w = size.width
        val h = size.height
        val rtl = layoutDirection == LayoutDirection.Rtl
        fun x(v: Float): Float = if (rtl) w - v else v
        val path = Path().apply {
            moveTo(x(c), 0f)
            lineTo(x(w), 0f)
            lineTo(x(w), h - c)
            lineTo(x(w - c), h)
            lineTo(x(0f), h)
            lineTo(x(0f), c)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * هل الحركة مسموحة؟ — مقياس النظام `animator_duration_scale`؛ إذا صفّره المستخدم لا تتنفّس الحافة.
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
 * خلفية اللوبي: أسود بتوهّج بلون اللعبة المحدَّدة خلف البطاقة المركزية، وقاعدة حمراء خافتة من
 * الأسفل. اللون يأتي من أيقونة اللعبة نفسها فتتبدّل الخلفية معها بلا أي أصل مرافق.
 */
@Composable
fun LobbyBackdrop(tone: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(LobbyPalette.Black)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(tone.copy(alpha = 0.30f), tone.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.46f),
                        radius = size.width * 0.55f
                    )
                )
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, LobbyPalette.RedDeep.copy(alpha = 0.40f)),
                        startY = size.height * 0.55f,
                        endY = size.height
                    )
                )
            }
    )
}

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

// ───────────────────────────── قراءات الجاهزية ─────────────────────────────

/**
 * @param tempTenthsC حرارة البطارية بعُشر الدرجة كما يُبلّغها النظام، و`null` إن لم تُبلَّغ.
 *   هي حرارة **البطارية** لا المعالج: المعالج يحتاج مسارًا لا يملكه اللوبي، فلا يُدَّعى.
 */
data class LobbyBattery(val percent: Int, val charging: Boolean, val tempTenthsC: Int? = null)

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
    val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
    LobbyBattery(percent = (level * 100 / scale).coerceIn(0, 100), charging = charging, tempTenthsC = temp)
}.getOrNull()

data class LobbyMemory(val availBytes: Long, val totalBytes: Long)

/** ذاكرة النظام الحرّة (`ActivityManager.MemoryInfo`، بلا إذن)، تُجدَّد كل عشر ثوانٍ. `null` قبل أول قراءة. */
@Composable
fun rememberLobbyMemory(): State<LobbyMemory?> {
    val context = LocalContext.current
    return produceState<LobbyMemory?>(null, context) {
        while (true) {
            value = readMemory(context)
            delay(10_000L)
        }
    }
}

private fun readMemory(context: Context): LobbyMemory? = runCatching {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    LobbyMemory(availBytes = info.availMem, totalBytes = info.totalMem)
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
