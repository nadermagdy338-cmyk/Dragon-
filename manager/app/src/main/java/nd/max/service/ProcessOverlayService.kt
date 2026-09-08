/*
 * Adapted from ZKM (Zuan Kernel Manager) ui/proces/FloatingProcessService.kt.
 * Original: Copyright (c) 2025 ZKM, licensed GPL-3.0.
 * Adaptation: Copyright (C) 2026-2027 Zexshia
 *
 * Rebuilt on the same LifecycleService/ViewModelStoreOwner/SavedStateRegistryOwner
 * scaffolding as nd.max.service.FpsOverlayService, and polls processes via
 * ProcessMonitorUtil (libsu Shell) instead of ZKM's manual ComposeView
 * lifecycle wiring and raw `Runtime.exec("su -c top ...")` call.
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import nd.max.MainActivity
import nd.max.R
import nd.max.ui.util.ProcessInfo
import nd.max.ui.util.ProcessMonitorUtil
import nd.max.ui.util.ProcessSortType

/**
 * Draggable floating window showing the top few processes by CPU. Toggled
 * from [nd.max.ui.subscreens.ProcessManagerScreen]; needs "draw over
 * other apps" permission, requested from that screen before this is started.
 */
class ProcessOverlayService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        var isRunning = false
        const val CHANNEL_ID = "maxmanager_process_overlay"
        const val NOTIF_ID = 9022
        const val ACTION_STOP = "nd.max.service.ProcessOverlayService.STOP"
        private const val OVERLAY_LIMIT = 8
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
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
        if (overlayView == null) setupOverlay()
        return START_STICKY
    }

    private fun setupOverlay() {
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ProcessOverlayService)
            setViewTreeSavedStateRegistryOwner(this@ProcessOverlayService)
            setViewTreeViewModelStoreOwner(this@ProcessOverlayService)
            setContent { ProcessOverlayContent(onClose = { stopSelf() }) }
            setOnTouchListener(object : View.OnTouchListener {
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = this@ProcessOverlayService.layoutParams.x
                            initialY = this@ProcessOverlayService.layoutParams.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            this@ProcessOverlayService.layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                            this@ProcessOverlayService.layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager.updateViewLayout(overlayView, this@ProcessOverlayService.layoutParams)
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
        val metrics = resources.displayMetrics
        return WindowManager.LayoutParams(
            (240 * metrics.density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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
                NotificationChannel(CHANNEL_ID, getString(R.string.processmgr_notif_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, ProcessOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.processmgr_notif_title))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.processmgr_notif_stop), stopIntent)
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

    @Composable
    private fun ProcessOverlayContent(onClose: () -> Unit) {
        val context = androidx.compose.ui.platform.LocalContext.current
        var processes by remember { mutableStateOf<List<ProcessInfo>>(emptyList()) }
        var isFirstLoad by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    val data = runCatching { ProcessMonitorUtil.getTopProcesses(context, OVERLAY_LIMIT, ProcessSortType.CPU) }
                        .getOrDefault(emptyList())
                    withContext(Dispatchers.Main) {
                        processes = data
                        isFirstLoad = false
                    }
                    delay(2000)
                }
            }
        }

        MaterialTheme(colorScheme = darkColorScheme()) {
            androidx.compose.material3.Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
                elevation = CardDefaults.cardElevation(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.DragHandle, contentDescription = null, tint = Color.White.copy(0.6f), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = context.getString(R.string.processmgr_title),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = Color(0xFFFF5252))
                        }
                    }
                    Spacer(Modifier.height(6.dp))

                    if (isFirstLoad) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            processes.forEach { process -> OverlayProcessRow(process) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun OverlayProcessRow(process: ProcessInfo) {
        val cpuValue = process.cpu.removeSuffix("%").toFloatOrNull() ?: 0f
        val progress = (cpuValue / 100f).coerceIn(0f, 1f)
        val barColor = if (cpuValue > 50f) Color(0xFFFF5252) else Color(0xFF69F0AE)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(process.appName, color = Color.White, fontSize = 10.sp, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = barColor,
                    trackColor = Color.Gray.copy(alpha = 0.3f)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(process.cpu, color = barColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 32.dp))
        }
    }
}
