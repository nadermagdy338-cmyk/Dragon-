/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.overlay

import android.content.Context
import android.os.Build
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt
import nd.max.core.gamespace.handleFraction
import nd.max.core.gamespace.handleY
import nd.max.core.gamespace.pullShouldOpen
import nd.max.core.gamespace.rubberBand

/** حالة المقبض المرئية: تُقرأ في مرحلة الرسم فقط، فلا إعادة تركيب أثناء السحب. */
class HandleFx {
    var pressed by mutableStateOf(false)
    var dragging by mutableStateOf(false)
    var armed by mutableStateOf(false)

    /** 0..1: مدى اقتراب السحب الأفقي من عتبة الفتح. */
    var pull by mutableFloatStateOf(0f)

    /** −1..1: السرعة الرأسية المطبَّعة (موجبة = للأسفل). */
    var speed by mutableFloatStateOf(0f)

    /** عدّاد لمسات (يعيد المقبض من خفوته). */
    var touches by mutableIntStateOf(0)

    /** عدّاد استقرارات (يطلق «نبضة» الاستقرار). */
    var settles by mutableIntStateOf(0)
}

/**
 * سحب المقبض بنعومة الحرير. النافذة تتحرّك مرة واحدة لكل إطار عرض (Choreographer) بدل كل حدث لمس،
 * والإصبع يُتتبَّع بتمليس أسّي يزيل ارتعاش العيّنات، وعند الإفلات: قذف رأسي باحتكاك، وزنبرك مخمَّد
 * يعيد الحافة بتجاوز طفيف. السحب الرأسي يعيد التموضع على الحافة؛ والسحب الأفقي نحو الداخل «شدّ»
 * مطّاطي يفتح اللوحة عند العتبة أو بقذفة سريعة. النقرة تفتح مباشرة.
 */
class SilkDragController(
    context: Context,
    private val window: OverlayWindow,
    private val dockedStart: () -> Boolean,
    private val savedFraction: () -> Float,
    private val onOpen: (yFraction: Float) -> Unit,
    private val onSettled: (yFraction: Float) -> Unit
) : View.OnTouchListener, Choreographer.FrameCallback {

    val fx = HandleFx()

    /** صحيح أثناء اللمس وأثناء الحركة التالية للإفلات: الخدمة لا تُعيد التموضع حينها. */
    val busy: Boolean get() = touching || running

    private enum class Axis { None, Vertical, Horizontal }

    private val density = context.resources.displayMetrics.density
    private val slop = 8f * density
    private val openAt = 76f * density
    private val reach = 150f * density
    private val flingMin = 24f * density
    private val flingSpeed = 1100f * density
    private val maxFling = MAX_FLING_DP * density
    private val edgeGap = 6f * density
    private val speedRef = 2400f * density
    private val springC = 2f * sqrt(SPRING_K) * 0.66f
    private val choreographer: Choreographer = Choreographer.getInstance()
    private val confirmKind =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK

    private var view: View? = null
    private var tracker: VelocityTracker? = null
    private var axis = Axis.None
    private var touching = false
    private var running = false
    private var moved = false
    private var armed = false
    private var lastNanos = 0L
    private var downX = 0f
    private var downY = 0f
    private var startY = 0f
    private var homeX = 0f
    private var minY = 0f
    private var maxY = 0f
    private var px = 0f
    private var py = 0f
    private var vx = 0f
    private var vy = 0f
    private var tx = 0f
    private var ty = 0f

    /** يضع النافذة على الحافة عند الارتفاع المحفوظ؛ لا يفعل شيئًا أثناء السحب أو دون ارتفاع محفوظ. */
    fun dock() {
        val v = window.hostView() ?: return
        if (busy || v.width <= 0 || v.height <= 0) return
        measure(v)
        val fraction = savedFraction()
        val y = if (fraction < 0f) window.position().second else handleY(fraction, minY, maxY).roundToInt()
        window.moveTo(homeX.roundToInt(), y)
    }

    fun release() {
        stopLoop()
        touching = false
        tracker?.recycle()
        tracker = null
        view = null
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> down(v, event)
            MotionEvent.ACTION_MOVE -> move(event)
            MotionEvent.ACTION_UP -> up(event, cancel = false)
            MotionEvent.ACTION_CANCEL -> up(event, cancel = true)
        }
        return true
    }

    private fun measure(v: View) {
        val (screenW, screenH) = window.screenBounds()
        homeX = if (dockedStart()) 0f else (screenW - v.width).toFloat()
        minY = edgeGap
        maxY = (screenH - v.height - edgeGap).coerceAtLeast(minY)
    }

    private fun down(v: View, e: MotionEvent) {
        stopLoop()
        view = v
        measure(v)
        val (x, y) = window.position()
        px = x.toFloat()
        py = y.toFloat()
        vx = 0f
        vy = 0f
        tx = px
        ty = py
        startY = py
        downX = e.rawX
        downY = e.rawY
        axis = Axis.None
        moved = false
        armed = false
        touching = true
        tracker?.recycle()
        tracker = VelocityTracker.obtain().also { it.addMovement(e) }
        fx.pressed = true
        fx.touches++
        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun move(e: MotionEvent) {
        if (!touching) return
        tracker?.addMovement(e)
        val dx = e.rawX - downX
        val dy = e.rawY - downY
        if (axis == Axis.None) {
            if (hypot(dx, dy) < slop) return
            axis = if (abs(dy) >= abs(dx) * 0.9f) Axis.Vertical else Axis.Horizontal
            moved = true
            fx.dragging = true
            startLoop()
        }
        if (axis == Axis.Vertical) {
            tx = homeX
            ty = (startY + dy).coerceIn(minY, maxY)
        } else {
            val inward = (if (dockedStart()) dx else -dx).coerceAtLeast(0f)
            val shown = rubberBand(inward, reach)
            tx = homeX + if (dockedStart()) shown else -shown
            ty = (startY + dy * 0.12f).coerceIn(minY, maxY)
            fx.pull = (inward / openAt).coerceIn(0f, 1f)
            val reached = inward >= openAt
            if (reached != armed) {
                armed = reached
                fx.armed = reached
                if (reached) view?.performHapticFeedback(confirmKind)
            }
        }
    }

    private fun up(e: MotionEvent, cancel: Boolean) {
        if (!touching) return
        touching = false
        fx.pressed = false
        val t = tracker
        tracker = null
        t?.addMovement(e)
        t?.computeCurrentVelocity(1000)
        val yVel = t?.yVelocity ?: 0f
        val xVel = t?.xVelocity ?: 0f
        t?.recycle()
        if (!moved) {
            if (!cancel) {
                view?.performHapticFeedback(confirmKind)
                onOpen(handleFraction(py, minY, maxY))
            }
            return
        }
        if (!cancel && axis == Axis.Horizontal) {
            val inward = (if (dockedStart()) e.rawX - downX else downX - e.rawX).coerceAtLeast(0f)
            val inwardV = if (dockedStart()) xVel else -xVel
            if (pullShouldOpen(inward, inwardV, openAt, flingMin, flingSpeed)) {
                stopLoop()
                fx.dragging = false
                fx.armed = false
                fx.pull = 0f
                fx.speed = 0f
                onOpen(handleFraction(py, minY, maxY))
                return
            }
        }
        vy = if (axis == Axis.Vertical) yVel.coerceIn(-maxFling, maxFling) else 0f
        fx.armed = false
        startLoop()
    }

    private fun startLoop() {
        if (running) return
        running = true
        lastNanos = 0L
        choreographer.postFrameCallback(this)
    }

    private fun stopLoop() {
        running = false
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (lastNanos == 0L) {
            lastNanos = frameTimeNanos
            choreographer.postFrameCallback(this)
            return
        }
        val dt = ((frameTimeNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.032f)
        lastNanos = frameTimeNanos
        if (touching) follow(dt) else glide(dt)
        if (!running) return
        window.moveTo(px.roundToInt(), py.roundToInt())
        fx.speed = (vy / speedRef).coerceIn(-1f, 1f)
        choreographer.postFrameCallback(this)
    }

    /** الإصبع على الشاشة: تمليس أسّي نحو الهدف بثابت زمني ثابت مهما اختلف معدّل العرض. */
    private fun follow(dt: Float) {
        val a = 1f - exp(-dt / FOLLOW_TAU)
        val nx = px + (tx - px) * a
        val ny = py + (ty - py) * a
        vx = (nx - px) / dt
        vy = (ny - py) / dt
        px = nx
        py = ny
    }

    /** بعد الإفلات: قذف رأسي باحتكاك + زنبرك أفقي نحو الحافة. */
    private fun glide(dt: Float) {
        vy *= exp(-dt * FRICTION)
        py += vy * dt
        if (py < minY) {
            py = minY
            vy = 0f
        } else if (py > maxY) {
            py = maxY
            vy = 0f
        }
        vx += (-SPRING_K * (px - homeX) - springC * vx) * dt
        px += vx * dt
        fx.pull = (abs(px - homeX) / openAt).coerceIn(0f, 1f)
        if (abs(px - homeX) < 0.6f && abs(vx) < 8f && abs(vy) < 14f) settle()
    }

    private fun settle() {
        px = homeX
        vx = 0f
        vy = 0f
        stopLoop()
        window.moveTo(px.roundToInt(), py.roundToInt())
        fx.dragging = false
        fx.armed = false
        fx.pull = 0f
        fx.speed = 0f
        fx.settles++
        onSettled(handleFraction(py, minY, maxY))
    }

    private companion object {
        const val FOLLOW_TAU = 0.024f
        const val FRICTION = 4.6f
        const val SPRING_K = 360f
        const val MAX_FLING_DP = 6000f
    }
}
