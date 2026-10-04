/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.service

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import nd.max.R
import nd.max.core.platform.ProcessFeed
import nd.max.core.platform.ProcessWatch
import nd.max.core.platform.ProcessWatchSettings
import nd.max.ui.component.ProcessSurface
import nd.max.ui.component.baselineOf
import nd.max.ui.overlay.OverlayWindow
import nd.max.ui.util.ProcessOverlayPrefs

/**
 * نافذة مراقب المهام العائمة — **قيادة فقط**.
 *
 * ### ما كانت وما صارت
 *
 * كانت تحمل أربع مسؤوليات في ملفّ واحد: عقد `WindowManager` والسحب، وإشعار الخدمة الأمامية،
 * وقراءة `top` وحلقتها، ورسم القائمة. ومنها كان العطب الحقيقي: **السحب بلا حدّ** (`FLAG_LAYOUT_NO_LIMITS`
 * يُجيز إخراج النافذة عن الشاشة فلا تُرجَع)، وثمانية صفوف وفاصل ثانيتين **ثوابت لا خيار**، و`emptyList()`
 * عند الفشل تُقرأ «لا توجد عمليات».
 *
 * | ما كان | أين صار |
 * | --- | --- |
 * | النافذة والسحب والالتصاق ومالك دورة الحياة | [OverlayWindow] (مشترك مع لوحة الأداء) |
 * | الإشعار الأمامي وزرّ الإيقاف | [startOverlayForeground] (مشترك) |
 * | قراءة الشجرة | [ProcessFeed] + [ProcessWatch] (قارئ واحد للشاشة والتراكب) |
 * | الرسم | [ProcessSurface] (يُرسم بها في الشاشة أيضًا، فلا فرق بين المعاينة والحقيقة) |
 *
 * **والتفضيلات تُقرأ كل دورة** ([ProcessOverlayPrefs]) لا عند الإقلاع: تغيير عدد الصفوف أو
 * الفاصل أو الترتيب من الشاشة يظهر في النافذة بلا إطفائها وتشغيلها.
 */
class ProcessOverlayService : LifecycleService() {

    companion object {
        /** هل التراكب معروض الآن؟ تقرأه الشاشة قبل أن تَعِد المستخدم بشيء. */
        var isRunning = false
            private set

        const val CHANNEL_ID = "maxmanager_process_overlay"
        const val NOTIF_ID = 9022
        const val ACTION_STOP = "nd.max.service.ProcessOverlayService.STOP"

        /** معرّف هذا الطالب عند القارئ المشترك — واحد لا يتكرّر. */
        private const val WATCH_OWNER = "process_overlay"
    }

    private lateinit var window: OverlayWindow

    override fun onCreate() {
        super.onCreate()
        window = OverlayWindow(this)
        startOverlayForeground(
            OverlayNotice(
                channelId = CHANNEL_ID,
                notificationId = NOTIF_ID,
                channelNameRes = R.string.processmgr_notif_channel,
                titleRes = R.string.processmgr_notif_title,
                stopLabelRes = R.string.processmgr_notif_stop,
                stopAction = ACTION_STOP,
                serviceClass = ProcessOverlayService::class.java
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!window.isShown) {
            val mounted = window.mount(
                dragEnabled = true,
                // يُقرأ لحظة الإفلات لا لحظة التركيب: إطفاء الالتصاق يُطبَّق بلا إعادة تشغيل.
                snapToEdges = { ProcessOverlayPrefs.load(applicationContext).snapEdges }
            ) {
                Panel()
            }
            if (!mounted) {
                isRunning = false
                stopSelf()
                return START_NOT_STICKY
            }
            isRunning = true
            ProcessWatch.start(
                context = applicationContext,
                owner = WATCH_OWNER,
                scope = lifecycleScope,
                settings = {
                    val prefs = ProcessOverlayPrefs.load(applicationContext)
                    // النافذة تطلب عدد صفوفها هي، والفاصل الذي اختاره المالك للتراكب.
                    ProcessWatchSettings(limit = prefs.rows, intervalSeconds = prefs.intervalSeconds)
                }
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ProcessWatch.stop(WATCH_OWNER)
        window.unmount()
        isRunning = false
        super.onDestroy()
    }

    /** اللوحة: تُرتَّب من العيّنة نفسها التي تراها الشاشة، فالمعاينة والحقيقة مصدرهما واحد. */
    @Composable
    private fun Panel() {
        val sample by ProcessWatch.snapshot.collectAsState()
        val tick by ProcessWatch.samples.collectAsState()
        val prefs = remember(tick) { ProcessOverlayPrefs.load(applicationContext) }
        val shown = remember(sample, prefs) {
            ProcessFeed.arrange(sample.readings, prefs.scope, "", prefs.sort, prefs.rows)
        }

        ProcessSurface(
            sample = sample.copy(readings = shown),
            sort = prefs.sort,
            baseline = baselineOf(shown, prefs.sort),
            textSizeSp = prefs.textSizeSp,
            backgroundAlpha = prefs.backgroundAlpha,
            markHeavy = prefs.markHeavy,
            showTally = prefs.showTally,
            onClose = { stopSelf() }
        )
    }
}
