/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * نافذة عائمة تُرسم فيها شجرة Compose، يملكها Service لا Activity.
 *
 * ### لماذا ملف مستقلّ
 *
 * التراكبان (لوحة الأداء ومراقب المهام) يحتاجان الشيء نفسه بالحرف: أذونات `WindowManager`،
 * وسحب النافذة، ومالك دورة حياة، لأن `ComposeView` في نافذة خام لا يجد نشاطًا يوفّره له. وكانت
 * النسختان تُكرّران ذلك سطرًا بسطر — وقد سُجّل ذلك عيبًا مُعلنًا في `docs/AUTHENTICITY.md`
 * بشرط صريح: «توحيده في وحدة مشتركة عند أوّل تعديل وظيفي على إحدى الخدمتين». وهذا هو ذلك
 * التعديل، فصارت هذه الوحدة هي الموضع الواحد.
 *
 * ### وما أُضيف فيها فوق ما كان
 *
 * 1. **الالتصاق بالحافة** — نافذة تُسحب وتُترك في وسط الشاشة تحجب اللعبة التي تُقاس؛ وعند
 *    الإفلات تنجذب إلى أقرب حافة جانبية، وتُثبَّت **داخل** الشاشة لا خارجها.
 * 2. **الحدّ داخل الشاشة** — كانت النافذة تُسحب خارج حدود الشاشة فلا تُرى ولا تُرجَع (وهو
 *    سلوك `FLAG_LAYOUT_NO_LIMITS` في الخدمتين السابقتين). فحُذف العلم وصار الحدّ محسوبًا من
 *    مقاس النافذة ومقاس الشاشة معًا.
 * 3. **طول العمر أُغلق** — دورة الحياة تُمرَّر صراحةً (create → resume → destroy)، فالـCompose
 *    داخل التراكب يتوقّف عن العمل حين يُزال بلا انتظار جمع المهملات.
 *
 * والحدّ المعلن: **لا إنشِاء من الخدمة إلا على مسار الخيط الرئيسي** (كل منادات `WindowManager`
 * تفترضه)، ولا يُستدعى `mount` مرّتين.
 */
class OverlayWindow(
    context: Context,
    private val startX: Int = 0,
    private val startY: Int = 0
) : SavedStateRegistryOwner, ViewModelStoreOwner {

    private val appContext: Context = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val stores = ViewModelStore()
    private val params: WindowManager.LayoutParams = buildParams()

    private var host: ComposeView? = null
    private var destroyed = false
    private var snapAnimator: ValueAnimator? = null
    private var downAtX = 0
    private var downAtY = 0
    private var fingerX = 0f
    private var fingerY = 0f
    private var snapped = false

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = stores

    /** هل النافذة معروضة الآن؟ — تُقرأ قبل أي إضافة حتى لا تُضاف مرّتين. */
    val isShown: Boolean get() = host != null

    /**
     * @param dragEnabled السحب اختياري: نافذة مثبّتة في موضعها لا تُحرَّك باللمس العابر.
     * @param snapToEdges عند الإفلات تنجذب إلى أقرب حافة جانبية. **دالّة لا قيمة** لأن
     *   الإعداد يُقرأ لحظة الإفلات لا لحظة التركيب — فمن أطفأ الالتصاق أثناء العمل لا يحتاج
     *   إعادة تشغيل التراكب ليُطبَّق.
     */
    fun mount(
        dragEnabled: Boolean = true,
        snapToEdges: () -> Boolean = { true },
        content: @Composable () -> Unit
    ): Boolean {
        if (host != null) return true
        // This owner is one-shot: a destroyed Lifecycle/SavedStateRegistry cannot be reused.
        if (destroyed) return false
        savedStateController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val view = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(this@OverlayWindow)
            setViewTreeSavedStateRegistryOwner(this@OverlayWindow)
            setViewTreeViewModelStoreOwner(this@OverlayWindow)
            setContent(content)
            if (dragEnabled) setOnTouchListener(grip(snapToEdges))
        }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        try {
            windowManager.addView(view, params)
            host = view // Publish only after WindowManager accepted the view.
            return true
        } catch (_: Exception) {
            // Permission revocation/BadToken must not leave composition or a sampler alive.
            runCatching { windowManager.removeView(view) }
            view.disposeComposition()
            unmount()
            return false
        }
    }

    fun unmount() {
        if (destroyed) return
        destroyed = true
        snapAnimator?.cancel()
        snapAnimator = null
        host?.let { view ->
            runCatching { windowManager.removeView(view) }
            view.disposeComposition()
        }
        host = null
        if (registry.currentState.isAtLeast(Lifecycle.State.RESUMED))
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED))
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        // Unmount can be called before mount when the service receives STOP immediately.
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED))
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        stores.clear()
    }

    /** إعادة الرسم بعد تغيّر مقاس المحتوى (فتُصحَّح الحدود فورًا). */
    fun refresh() {
        val view = host ?: return
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    /**
     * يضع النافذة عند إحداثيّات محسوبة مسبقًا (بلا سحب).
     *
     * **ولماذا لا تُحسب هنا:** موضع اللوحة الجانبية يحتاج شرائح النظام وحافة الشاشة، وحسابه
     * دالّة نقيّة مُختبَرة في `core/gamespace` (`panelPlacement`). فالنافذة تبقى **آلة عرض** لا
     * تعرف شيئًا عن الألعاب، والمُستدعي يمرّر ما قاسه. وكانت النافذة تُبنى بـ`startX/startY`
     * ثابتين لا يعرفان شريحة حالة ولا حافة.
     */
    fun place(x: Int, y: Int) {
        params.x = x
        params.y = y
        refresh()
    }
    /** الموضع الحالي للنافذة بالبكسل. */
    fun position(): Pair<Int, Int> = params.x to params.y

    /** تحريك لكل إطار: لا يستدعي النظام إن لم يتغيّر الموضع فعلًا. */
    fun moveTo(x: Int, y: Int) {
        if (params.x == x && params.y == y) return
        params.x = x
        params.y = y
        refresh()
    }

    /** معالج لمس خارجي (السحب الناعم)، أو `null` فيعود اللمس إلى محتوى Compose. */
    fun setTouchHandler(handler: View.OnTouchListener?) {
        host?.setOnTouchListener(handler)
    }

    fun hostView(): View? = host

    /** ينفّذ مرة واحدة بعد أول قياس للمحتوى، فلا تُرى النافذة في غير مكانها. */
    fun onceLaidOut(block: () -> Unit) {
        val view = host ?: return
        if (view.width > 0 && view.height > 0) {
            block()
            return
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (view.width <= 0 || view.height <= 0) return
                if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                block()
            }
        })
    }



    /**
     * وضع ملء الشاشة للوحة المفتوحة: نافذة MATCH_PARENT تمتدّ تحت القصّة (cutout) وشرائط النظام،
     * وتعود إلى WRAP_CONTENT للمقبض. لا يُستدعى إلا على الخيط الرئيسي بعد `mount`.
     */
    fun setFullScreen(on: Boolean, atX: Int? = null, atY: Int? = null) {
        val size = if (on) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT
        params.width = size
        params.height = size
        if (on) {
            params.x = 0
            params.y = 0
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN.inv()
            atX?.let { params.x = it }
            atY?.let { params.y = it }
        }
        refresh()
    }

    /** مقاس الشاشة الحقيقي — يُقرأ للحساب لا للتخزين. */
    fun screenBounds(): Pair<Int, Int> = bounds()

    /** مقاس المحتوى المرسوم الآن، أو `null` قبل أوّل قياس. */
    fun contentSize(): Pair<Int, Int>? {
        val view = host ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        return view.width to view.height
    }

    private fun buildParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // `FLAG_LAYOUT_NO_LIMITS` مُزال عمدًا: كان يُجيز السحب خارج الشاشة.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }
    }

    private fun grip(snapToEdges: () -> Boolean) = View.OnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapAnimator?.cancel()
                snapAnimator = null
                downAtX = params.x
                downAtY = params.y
                fingerX = event.rawX
                fingerY = event.rawY
                snapped = false
                true
            }

            MotionEvent.ACTION_MOVE -> {
                params.x = (downAtX + (event.rawX - fingerX)).toInt()
                params.y = (downAtY + (event.rawY - fingerY)).toInt()
                clampInto(view)
                runCatching { windowManager.updateViewLayout(view, params) }
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!snapped && snapToEdges()) {
                    snapped = true
                    snapToNearestEdge(view)
                }
                true
            }

            else -> false
        }
    }

    /** يمنع أن تخرج النافذة عن الشاشة: ما خرج لا يُسحَب عودةً في أغلب الأجهزة. */
    private fun clampInto(view: View) {
        val (screenW, screenH) = bounds()
        val width = view.width.takeIf { it > 0 } ?: 0
        val height = view.height.takeIf { it > 0 } ?: 0
        params.x = OverlayGeometry.clamp(params.x, screenW, width)
        params.y = OverlayGeometry.clamp(params.y, screenH, height)
    }

    private fun snapToNearestEdge(view: View) {
        val (screenW, _) = bounds()
        val width = view.width.takeIf { it > 0 } ?: return
        val target = OverlayGeometry.snapTarget(params.x, screenW, width)
        if (target == params.x) return
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(params.x, target).apply {
            duration = SNAP_MS
            addUpdateListener {
                if (host === view && !destroyed) {
                    params.x = it.animatedValue as Int
                    runCatching { windowManager.updateViewLayout(view, params) }
                }
            }
            start()
        }
    }

    /** مقاس الشاشة الحقيقيّ حين يُعرفه النظام، وإلا مقاس العرض المُعلَن للنشاط. */
    private fun bounds(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val b = windowManager.currentWindowMetrics.bounds
        b.width() to b.height()
    } else {
        val m = appContext.resources.displayMetrics
        m.widthPixels to m.heightPixels
    }

    private companion object {
        const val SNAP_MS = 160L
    }
}
