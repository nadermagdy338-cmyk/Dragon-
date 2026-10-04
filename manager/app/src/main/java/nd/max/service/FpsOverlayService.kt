/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
 * خدمة لوحة الأداء: تُشغّل دورة القراءة الواحدة وتملك النافذة العائمة، وتُمرّر الرسم إلى
 * `HudSurface`. ولا تقرأ عتادًا بنفسها ولا تكتب عليه — القارئ هو `HudSampler`.
 * */

package nd.max.service

import android.content.Intent
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import nd.max.R
import nd.max.core.platform.HUD_OWNER_OVERLAY
import nd.max.core.platform.HudLive
import nd.max.core.platform.HudRecorder
import nd.max.core.platform.HudSampler
import nd.max.ui.component.HudRestoreTab
import nd.max.ui.component.HudSurface
import nd.max.ui.overlay.OverlayWindow
import nd.max.ui.util.FpsOverlayPrefs

/**
 * النافذة العائمة التي تُظهر أداء الجهاز فوق أيّ تطبيق آخر.
 *
 * **وما تغيّر في إعادة البناء:** هذه الخدمة كانت تحمل ثلاث مسؤوليات معًا — عقد النافذة
 * العائمة، ورسم اللوحة بثلاث شيفرات منفصلة، وقراءة المقاييس. فصارت تحمل **واحدة**: القيادة.
 *
 * | ما كان | أين صار |
 * | --- | --- |
 * | إنشاء النافذة وسحبها ومالك دورة الحياة | `ui/overlay/OverlayWindow.kt` (مشترك مع مراقب المهام) |
 * | إشعار الخدمة الأمامية | `service/OverlayForeground.kt` (مشترك) |
 * | قراءة المقاييس وحلقة التحديث | `core/platform/HudSession.kt` (`HudSampler`) |
 * | رسم اللوحة بثلاث صور | `ui/component/HudSurface.kt` (مُصيِّر واحد يستعمله التراكب والمعاينة) |
 *
 * **ولماذا هذا ليس تجميلًا:** كانت اللوحة تُرسم بشيفرة لا تعرفها شاشة الإعدادات، فالمعاينة
 * وصفٌ بالكلام لا صورة، وكل اختيار يُجرَّب بالخروج إلى لعبة. ووحدة الرسم تُزيل هذا الصنف من
 * العطب لا تُصلح حالة منه.
 *
 * **والقارئ واحد:** حين تكون شاشة الإعدادات مفتوحة تُغيَّر الحقول فورًا لأنّ `HudSampler` يقرأ
 * التفضيلات كل دورة — لا عند الإقلاع فقط.
 */
class FpsOverlayService : LifecycleService() {

    companion object {
        /** هل التراكب معروض الآن؟ تقرأه شاشة الإعدادات قبل أن تعير القارئ منها. */
        var isRunning = false
            private set

        const val CHANNEL_ID = "maxmanager_fps_overlay"
        const val NOTIF_ID = 9021
        const val ACTION_STOP = "nd.max.service.FpsOverlayService.STOP"

        /** أضيق عرض للوحة قبل معامل العرض — رقم واحد يقرأه الرسم والتلميح معًا. */
        private const val PANEL_MIN_WIDTH_DP = 90f
    }

    private lateinit var panel: OverlayWindow

    /**
     * هل طُويت اللوحة الآن؟
     *
     * **وفي الخدمة لا في التفضيلات عن قصد:** الطيّ قرار لحظة («أُخفيها لألعب الآن») لا إعداد
     * دائم — ولو خُزِّن لعاد التراكب كبسولةً في كل تشغيل، ويظنّ المستخدم أنّ اللوحة لم تعمل
     * أصلًا، وهو عطب أشبه بالاختفاء من أن يكون راحة. تُصفَّر عند إقلاع الخدمة، ويُصفَّر أيضًا
     * إن أُطفئ التراكب ثم أُشعل.
     */
    private val hidden = mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
        panel = OverlayWindow(this)
        hidden.value = false
        startOverlayForeground(
            OverlayNotice(
                channelId = CHANNEL_ID,
                notificationId = NOTIF_ID,
                channelNameRes = R.string.fps_overlay_notif_channel,
                titleRes = R.string.fps_overlay_notif_title,
                stopLabelRes = R.string.fps_overlay_notif_stop,
                stopAction = ACTION_STOP,
                serviceClass = FpsOverlayService::class.java
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!panel.isShown) {
            val mounted = panel.mount(
                dragEnabled = true,
                // يُقرأ لحظة الإفلات لا لحظة التركيب: إطفاء الالتصاق يُطبَّق بلا إعادة تشغيل.
                snapToEdges = { FpsOverlayPrefs.load(applicationContext).snapEdges }
            ) {
                Panel()
            }
            if (!mounted) {
                isRunning = false
                stopSelf()
                return START_NOT_STICKY
            }
            isRunning = true
            HudSampler.start(
                context = applicationContext,
                owner = HUD_OWNER_OVERLAY,
                scope = lifecycleScope,
                fields = { FpsOverlayPrefs.load(applicationContext).fields }
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        HudSampler.stop(HUD_OWNER_OVERLAY)
        panel.unmount()
        isRunning = false
        super.onDestroy()
    }

    /**
     * اللوحة تُقرأ كل دورة: التفضيلات تُعاد قراءتها عند كل عيّنة، فتغيير الشكل أو اللون أو
     * الحقول من الشاشة يظهر في النافذة خلال ثانية بلا إعادة تشغيل الخدمة.
     */
    @Composable
    private fun Panel() {
        val snapshot by HudLive.snapshot.collectAsState()
        val prefs = remember(snapshot.sampledAtMs) { FpsOverlayPrefs.load(applicationContext) }
        val accent = remember(prefs.colorHex) {
            runCatching { Color(android.graphics.Color.parseColor(prefs.colorHex)) }
                .getOrDefault(Color(0xFF00E676))
        }

        // الطيّ: لا تُرسم اللوحة أصلًا بل الكبسولة وحدها. والقارئ (`HudSampler`) لا يتوقّف —
        // فهو مالك مشترك يخدم معاينة الشاشة أيضًا، وإيقافه وإشعاله هنا يُدخلان حالة ثالثة
        // وتوقيتًا لا لزوم له في مقابل قراءة ملفّ في الثانية.
        if (hidden.value) {
            HudRestoreTab(onShow = { hidden.value = false })
            return
        }

        HudSurface(
            form = prefs.form,
            arrangement = prefs.arrangement,
            reading = snapshot.reading,
            fields = prefs.orderedFields,
            accent = accent,
            textSizeSp = prefs.textSizeSp,
            backgroundAlpha = prefs.backgroundAlpha,
            framesHistory = snapshot.framesHistory.takeLast(prefs.graphSpan),
            showGraph = prefs.showGraph,
            recording = snapshot.recording,
            onToggleRecording = {
                if (HudRecorder.isLive) HudRecorder.stop() else HudRecorder.start()
            },
            // الطيّ يبقى في النافذة (الخدمة تعمل)، والإغلاق يُنهيها — وهو الفرق الذي يفصله
            // الشكل نفسه: زرّ العين يُرجع، وزرّ × يُنزل الإشعار.
            onHide = { hidden.value = true },
            onClose = { stopSelf() },
            modifier = Modifier.widthIn(min = (PANEL_MIN_WIDTH_DP * prefs.widthScale).dp)
        )
    }
}
