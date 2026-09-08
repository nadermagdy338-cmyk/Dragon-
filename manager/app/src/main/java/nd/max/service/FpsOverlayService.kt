/*
 * Adapted from ZKM (Zuan Kernel Manager) services/FpsOverlayService.kt.
 * Original: Copyright (c) 2025 ZKM, licensed GPL-3.0.
 * Adaptation: Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import nd.max.MainActivity
import nd.max.R
import nd.max.ui.util.FpsMonitorUtil
import nd.max.ui.util.FpsOverlayPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Draggable floating window showing live FPS / CPU / RAM / battery watt+temp
 * while a game or any app is in the foreground. Toggled from
 * [nd.max.ui.subscreens.FpsOverlayScreen]; needs "draw over other apps"
 * permission, requested from that screen before this service is started.
 */
class FpsOverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        var isRunning = false
        const val CHANNEL_ID = "maxmanager_fps_overlay"
        const val NOTIF_ID = 9021
        const val ACTION_STOP = "nd.max.service.FpsOverlayService.STOP"
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private var state by mutableStateOf(FpsOverlayPrefs.State())

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        FpsMonitorUtil.init(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        layoutParams = createLayoutParams()
        startForegroundNotification()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        state = FpsOverlayPrefs.load(this)
        if (overlayView == null) setupOverlay()
        return START_STICKY
    }

    private fun setupOverlay() {
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FpsOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FpsOverlayService)
            setViewTreeViewModelStoreOwner(this@FpsOverlayService)
            setContent { OverlayContent(state) }
            setOnTouchListener(object : View.OnTouchListener {
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = this@FpsOverlayService.layoutParams.x
                            initialY = this@FpsOverlayService.layoutParams.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            this@FpsOverlayService.layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                            this@FpsOverlayService.layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(overlayView, this@FpsOverlayService.layoutParams)
                            return true
                        }
                    }
                    return false
                }
            })
        }
        windowManager.addView(overlayView, layoutParams)
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 150
        }
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.fps_overlay_notif_channel), NotificationManager.IMPORTANCE_MIN)
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, FpsOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.fps_overlay_notif_title))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.fps_overlay_notif_stop), stopIntent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
    }
}

private data class OverlayMetrics(
    val fpsRaw: Float = 0f,
    val fps: String = "--",
    val cpu: String = "--%",
    val ram: String = "--",
    val watt: String = "--",
    val tempRaw: Float = 0f,
    val temp: String = "--",
    val renderLabel: String = "FPS"
)

@Composable
private fun OverlayContent(state: FpsOverlayPrefs.State) {
    var metrics by remember { mutableStateOf(OverlayMetrics()) }
    val fpsHistory = remember { mutableStateListOf<Float>() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var isRecording by remember { mutableStateOf(false) }

    LaunchedEffect(state.showFps, state.showCpu, state.showRam, state.showWatt, state.showTemp, state.showRender) {
        withContext(Dispatchers.Default) {
            while (isActive) {
                val start = System.currentTimeMillis()
                try {
                    val fgPkg = if (state.showRender) FpsMonitorUtil.getForegroundPackage() else ""
                    val rawFps = FpsMonitorUtil.getFps()
                    val fpsVal = if (state.showFps) "%.0f".format(rawFps) else "--"
                    val cpuVal = if (state.showCpu) "${FpsMonitorUtil.getCpuLoad()}%" else "--%"
                    val ramVal = if (state.showRam) "${FpsMonitorUtil.getRamInfo(context).usedMb}" else "--"
                    val wattVal = if (state.showWatt) "%.1fW".format(FpsMonitorUtil.getPowerWatt()) else "--"
                    val rawTemp = FpsMonitorUtil.getBatteryTemp(context)
                    val tempVal = if (state.showTemp) "%.0f\u00b0C".format(rawTemp) else "--"
                    val render = if (state.showRender) FpsMonitorUtil.getCurrentRenderer(fgPkg) else "FPS"
                    
                    metrics = OverlayMetrics(rawFps, fpsVal, cpuVal, ramVal, wattVal, rawTemp, tempVal, render)
                    
                    if (state.showFps) {
                        fpsHistory.add(rawFps)
                        if (fpsHistory.size > 20) fpsHistory.removeAt(0)
                    }
                } catch (e: Exception) { /* keep last values */ }
                delay((1000 - (System.currentTimeMillis() - start)).coerceAtLeast(150))
            }
        }
    }

    val accentColor = remember(state.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(state.colorHex)) }.getOrDefault(Color(0xFF00E676))
    }

    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = state.bgAlpha), RoundedCornerShape(12.dp))
            .padding(10.dp)
            .width(IntrinsicSize.Max)
            .widthIn(min = (90 * state.widthScale).dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (state.styleMode) {
                1 -> PcStyleOverlay(state, metrics, accentColor)
                2 -> MiniOverlay(state, metrics)
                else -> AndroidStyleOverlay(state, metrics, accentColor)
            }
            
            if (state.showFps && fpsHistory.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                LiveFpsGraph(fpsHistory, accentColor)
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 0.5.dp)
            Spacer(Modifier.height(4.dp))
            
            // Ported and Improved Benchmark/Record button from ZKM
            RecordControlButton(isRec = isRecording) {
                isRecording = !isRecording
            }
        }
    }
}

@Composable
private fun LiveFpsGraph(history: List<Float>, color: Color) {
    Canvas(modifier = Modifier.fillMaxWidth().height(30.dp)) {
        val maxFps = 120f
        val points = history.mapIndexed { index, fps ->
            val x = index * (size.width / 20f)
            val y = size.height - (fps / maxFps * size.height).coerceIn(0f, size.height)
            Offset(x, y)
        }
        
        val path = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun RecordControlButton(isRec: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (isRec) Color.White.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isRec) {
            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.Red, modifier = Modifier.size(18.dp))
        } else {
            Icon(Icons.Default.FiberManualRecord, contentDescription = "Record", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AndroidStyleOverlay(state: FpsOverlayPrefs.State, m: OverlayMetrics, color: Color) {
    val tempColor = if (m.tempRaw >= 42f) Color(0xFFFF5252) else color // Color Warning

    val rows = buildList {
        if (state.showFps) add(Triple("FPS", m.fps, color))
        if (state.showCpu) add(Triple("CPU", m.cpu, color))
        if (state.showRam) add(Triple("RAM", "${m.ram}MB", color))
        if (state.showWatt) add(Triple("PWR", m.watt, color))
        if (state.showTemp) add(Triple("TMP", m.temp, tempColor))
    }
    if (state.orientation == 0) {
        Column(horizontalAlignment = Alignment.Start) {
            rows.forEach { (label, value, clr) -> OverlayRow(label, value, clr, state.textSizeSp) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { (label, value, clr) -> OverlayRow(label, value, clr, state.textSizeSp) }
        }
    }
}

@Composable
private fun OverlayRow(label: String, value: String, color: Color, size: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "$label ", color = Color.LightGray, fontSize = (size * 0.7f).sp)
        Text(text = value, color = color, fontSize = size.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PcStyleOverlay(state: FpsOverlayPrefs.State, m: OverlayMetrics, accent: Color) {
    val font = FontFamily.Monospace
    val orange = Color(0xFFFF8C00)
    val tempColor = if (m.tempRaw >= 42f) Color(0xFFFF5252) else orange

    Column {
        if (state.showTemp) PcRow("TMP", m.temp, accent, tempColor, font, state.textSizeSp)
        if (state.showWatt) PcRow("PWR", m.watt, accent, orange, font, state.textSizeSp)
        if (state.showRam) PcRow("MEM", "${m.ram}MB", accent, orange, font, state.textSizeSp)
        if (state.showCpu) PcRow("CPU", m.cpu, Color(0xFF00BFFF), orange, font, state.textSizeSp)
        if (state.showFps) PcRow(if (state.showRender) m.renderLabel else "FPS", m.fps, Color(0xFFECA3A3), Color.White, font, state.textSizeSp)
    }
}

@Composable
private fun PcRow(label: String, value: String, labelColor: Color, valueColor: Color, font: FontFamily, size: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = labelColor, fontSize = size.sp, fontFamily = font, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(16.dp))
        Text(value, color = valueColor, fontSize = size.sp, fontFamily = font, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniOverlay(state: FpsOverlayPrefs.State, m: OverlayMetrics) {
    val tempColor = if (m.tempRaw >= 42f) Color(0xFFFF5252) else Color(0xFFFF8C00)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.showFps) {
            Text(m.fps, color = Color.White, fontSize = (state.textSizeSp * 1.2f).sp, fontWeight = FontWeight.ExtraBold)
            Text("FPS", color = Color.Gray, fontSize = (state.textSizeSp * 0.6f).sp)
        }
        if (state.showCpu || state.showTemp) {
            Spacer(Modifier.height(4.dp))
            Row {
                if (state.showCpu) Text(m.cpu, color = Color(0xFF00BFFF), fontSize = (state.textSizeSp * 0.8f).sp, modifier = Modifier.padding(end = 4.dp))
                if (state.showTemp) Text(m.temp, color = tempColor, fontSize = (state.textSizeSp * 0.8f).sp)
            }
        }
    }
}
