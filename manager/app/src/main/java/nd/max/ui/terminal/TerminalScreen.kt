/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package nd.max.ui.terminal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.termux.terminal.TerminalSession
import nd.max.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.blur.blurEffect

@Composable
fun TerminalScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val view = LocalView.current
    val prefs = remember { context.getSharedPreferences("terminal_settings", Context.MODE_PRIVATE) }
    val session = remember { TerminalManager.createSession(context) }
    val scope = rememberCoroutineScope()
    val isRtl = remember(configuration.locales) {
        configuration.locales[0]?.language?.lowercase()?.let { it == "ar" || it == "fa" || it == "ur" } == true
    }

    var showPreferences by remember { mutableStateOf(false) }
    var isMaximized by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var contentScale by remember { mutableStateOf(1f) }
    var commandPulse by remember { mutableStateOf(0) }

    var isBlurEnabled by remember { mutableStateOf(prefs.getBoolean("blur_enabled", true)) }
    var glassBlurRadius by remember { mutableStateOf(prefs.getFloat("glass_radius", 20f)) }
    var isDarkGlass by remember { mutableStateOf(prefs.getBoolean("dark_glass", true)) }
    var isStatusBarHidden by remember { mutableStateOf(prefs.getBoolean("fullscreen", false)) }
    var isBgCustomEnabled by remember { mutableStateOf(prefs.getBoolean("bg_custom", false)) }
    var bgImageBlurRadius by remember { mutableStateOf(prefs.getFloat("bg_img_radius", 0f)) }
    var bgImageDimAlpha by remember { mutableStateOf(prefs.getFloat("bg_img_dim", 0.3f)) }
    var isBubbleBgEnabled by remember { mutableStateOf(prefs.getBoolean("bg_bubble_enabled", true)) }
    var isBgDarkMode by remember { mutableStateOf(prefs.getBoolean("bg_dark_mode", true)) }
    var isLiveGradientEnabled by remember { mutableStateOf(prefs.getBoolean("bg_live_gradient", false)) }
    var isBgVideoEnabled by remember { mutableStateOf(prefs.getBoolean("bg_video_enabled", false)) }
    var videoUriString by remember { mutableStateOf(prefs.getString("bg_video_uri", null)) }
    var backgroundBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(isBlurEnabled) { prefs.edit().putBoolean("blur_enabled", isBlurEnabled).apply() }
    LaunchedEffect(glassBlurRadius) { prefs.edit().putFloat("glass_radius", glassBlurRadius).apply() }
    LaunchedEffect(isDarkGlass) { prefs.edit().putBoolean("dark_glass", isDarkGlass).apply() }
    LaunchedEffect(isStatusBarHidden) { prefs.edit().putBoolean("fullscreen", isStatusBarHidden).apply() }
    LaunchedEffect(isBgCustomEnabled) { prefs.edit().putBoolean("bg_custom", isBgCustomEnabled).apply() }
    LaunchedEffect(bgImageBlurRadius) { prefs.edit().putFloat("bg_img_radius", bgImageBlurRadius).apply() }
    LaunchedEffect(bgImageDimAlpha) { prefs.edit().putFloat("bg_img_dim", bgImageDimAlpha).apply() }
    LaunchedEffect(isBubbleBgEnabled) { prefs.edit().putBoolean("bg_bubble_enabled", isBubbleBgEnabled).apply() }
    LaunchedEffect(isBgDarkMode) { prefs.edit().putBoolean("bg_dark_mode", isBgDarkMode).apply() }
    LaunchedEffect(isLiveGradientEnabled) { prefs.edit().putBoolean("bg_live_gradient", isLiveGradientEnabled).apply() }
    LaunchedEffect(isBgVideoEnabled) { prefs.edit().putBoolean("bg_video_enabled", isBgVideoEnabled).apply() }
    LaunchedEffect(videoUriString) { prefs.edit().putString("bg_video_uri", videoUriString).apply() }

    val hazeState = remember { HazeState() }
    val animated = rememberInfiniteTransition(label = "terminal_motion")
    val pulse by animated.animateFloat(
        initialValue = 0.35f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, "saved_background.jpg")
            if (file.exists()) runCatching {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }.getOrNull()?.let { withContext(Dispatchers.Main) { backgroundBitmap = it } }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isBgCustomEnabled = true; isBgVideoEnabled = false
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(File(context.filesDir, "saved_background.jpg")).use { output -> input.copyTo(output) }
                    }
                    BitmapFactory.decodeFile(File(context.filesDir, "saved_background.jpg").absolutePath)?.asImageBitmap()
                }.getOrNull()?.let { withContext(Dispatchers.Main) { backgroundBitmap = it } }
            }
        }
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) { videoUriString = uri.toString(); isBgVideoEnabled = true; isBgCustomEnabled = false; isBubbleBgEnabled = false }
    }

    LaunchedEffect(isStatusBarHidden) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (isStatusBarHidden) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
    }

    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface

    CompositionLocalProvider(LocalLayoutDirection provides if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
            // Lightweight animated terminal backdrop — no external image dependency.
            Canvas(Modifier.fillMaxSize()) {
                val center = if (isRtl) Offset(size.width * 0.78f, size.height * 0.18f) else Offset(size.width * 0.22f, size.height * 0.18f)
                drawCircle(accent.copy(alpha = 0.055f + pulse * 0.025f), size.minDimension * 0.48f, center)
                drawCircle(accent.copy(alpha = 0.035f), size.minDimension * 0.27f, center)
                for (i in 0..18) {
                    val y = size.height * (0.16f + i * 0.045f)
                    drawLine(onSurface.copy(alpha = 0.018f), Offset(0f, y), Offset(size.width, y), 1f)
                }
            }

            // This screen is edge-to-edge by design, but the terminal chrome is
            // not allowed to occupy the system status-bar hit area. Keeping the
            // inset on the chrome (rather than every child) also keeps the terminal
            // viewport stable when the IME appears.
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(72.dp)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.terminal_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.terminal_shell), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.size(10.dp).clip(CircleShape).background(accent.copy(alpha = pulse)))
                    IconButton(onClick = { showPreferences = true }) { Icon(Icons.Outlined.Settings, stringResource(R.string.action_settings)) }
                    IconButton(onClick = { isLocked = !isLocked }) { Icon(if (isLocked) Icons.Filled.Lock else Icons.Outlined.Lock, stringResource(R.string.action_lock)) }
                }

                // Quick command rail: visual shortcut only; commands are written to the existing session.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("id", "uname -a", "getprop ro.build.version.release", "dumpsys battery").forEach { command ->
                            SuggestionChip(command, accent, onClick = {
                                session.write(command + "\r")
                                commandPulse++
                            })
                        }
                    }
                }

                Surface(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = surface.copy(alpha = 0.94f),
                    tonalElevation = 2.dp
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.terminal_shell_console), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelLarge, color = onSurface.copy(alpha = 0.7f))
                            Spacer(Modifier.weight(1f))
                            Text(stringResource(if (isLocked) R.string.terminal_locked else R.string.terminal_ready), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = accent)
                        }
                        // Terminal content is deliberately LTR even when the application chrome is RTL.
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            TerminalSessionContent(session, contentScale, { contentScale = it })
                        }
                    }
                }
            }

            if (commandPulse > 0) {
                LaunchedEffect(commandPulse) { delay(260); commandPulse = 0 }
            }
        }

        TerminalPreferences(
            isVisible = showPreferences,
            onClose = { showPreferences = false }, hazeState = hazeState,
            isBlurEnabled = isBlurEnabled, onBlurChanged = { isBlurEnabled = it },
            glassBlurRadius = glassBlurRadius, onGlassRadiusChanged = { glassBlurRadius = it },
            isDarkGlass = isDarkGlass, onDarkGlassChanged = { isDarkGlass = it },
            isBgCustomEnabled = isBgCustomEnabled, onBgCustomChanged = { isBgCustomEnabled = it },
            bgImageBlurRadius = bgImageBlurRadius, onBgImageBlurChanged = { bgImageBlurRadius = it },
            bgImageDimAlpha = bgImageDimAlpha, onBgImageDimChanged = { bgImageDimAlpha = it },
            isBubbleBgEnabled = isBubbleBgEnabled, onBubbleBgChanged = { isBubbleBgEnabled = it },
            isBgDarkMode = isBgDarkMode, onBgDarkModeChanged = { isBgDarkMode = it },
            isLiveGradientEnabled = isLiveGradientEnabled, onLiveGradientChanged = { isLiveGradientEnabled = it },
            isStatusBarHidden = isStatusBarHidden, onFullscreenChanged = { isStatusBarHidden = it },
            galleryLauncher = galleryLauncher, isBgVideoEnabled = isBgVideoEnabled,
            onBgVideoChanged = { isBgVideoEnabled = it }, videoLauncher = videoLauncher
        )
    }
}

@Composable
private fun SuggestionChip(command: String, accent: Color, onClick: () -> Unit) {
    androidx.compose.material3.AssistChip(
        onClick = onClick,
        label = { Text(command, fontFamily = FontFamily.Monospace, maxLines = 1) },
        border = androidx.compose.material3.ButtonDefaults.outlinedButtonBorder(enabled = true),
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
fun TerminalSessionContent(
    session: TerminalSession,
    contentScale: Float,
    onScaleChanged: (Float) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val currentScale by rememberUpdatedState(contentScale)
    val currentOnScaleChanged by rememberUpdatedState(onScaleChanged)

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val newScale = (currentScale * zoom).coerceIn(0.5f, 3.0f)
                        currentOnScaleChanged(newScale)
                    }
                }
        ) {
            FastFetchHeader(scale = contentScale)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    com.termux.view.TerminalView(ctx, null).apply {
                        setTextSize((26 * currentScale).toInt()) 
                        keepScreenOn = true
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        isFocusable = true
                        isFocusableInTouchMode = true
                        val paddingPx = (16 * density.density).toInt()
                        setPadding(paddingPx, 0, paddingPx, paddingPx)
                        TerminalManager.uiUpdater = { post { invalidate() } }
                        
                        setTerminalViewClient(object : com.termux.view.TerminalViewClient {
                            override fun onScale(scale: Float): Float { 
                                val newScale = (currentScale * scale).coerceIn(0.5f, 3.0f)
                                currentOnScaleChanged(newScale)
                                return 1.0f 
                            }
                            override fun onSingleTapUp(e: android.view.MotionEvent?) {
                                requestFocus()
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                imm.showSoftInput(this@apply, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                            }
                            override fun shouldEnforceCharBasedInput(): Boolean = true
                            override fun onKeyDown(keyCode: Int, e: android.view.KeyEvent?, session: TerminalSession?): Boolean { if (keyCode == android.view.KeyEvent.KEYCODE_ENTER) { session?.write("\r"); return true }; return false }
                            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean { if (session != null && Character.isValidCodePoint(codePoint)) { session.write(String(Character.toChars(codePoint))); return true }; return false }
                            override fun shouldBackButtonBeMappedToEscape(): Boolean = false
                            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
                            override fun isTerminalViewSelected(): Boolean = true
                            override fun onKeyUp(keyCode: Int, e: android.view.KeyEvent?): Boolean = false
                            override fun onLongPress(event: android.view.MotionEvent?): Boolean = false
                            override fun readControlKey(): Boolean = false
                            override fun readAltKey(): Boolean = false
                            override fun readShiftKey(): Boolean = false
                            override fun readFnKey(): Boolean = false
                            override fun onEmulatorSet() {}
                            override fun copyModeChanged(isCopyMode: Boolean) {}
                            override fun logError(tag: String?, message: String?) {}
                            override fun logWarn(tag: String?, message: String?) {}
                            override fun logInfo(tag: String?, message: String?) {}
                            override fun logDebug(tag: String?, message: String?) {}
                            override fun logVerbose(tag: String?, message: String?) {}
                            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
                            override fun logStackTrace(tag: String?, e: Exception?) {}
                        })
                        attachSession(session)
                    }
                },
                update = { view -> 
                    val newSize = (26 * currentScale).toInt()
                    view.setTextSize(newSize)
                }
            )
        }
    }
}

@Composable
fun TrafficLightButton(color: Color) {
    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
}

@Composable
fun AnimatedBubbleBackground(isDarkMode: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "bubble_anim")

    val bubble1X by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 7000, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "b1x"
    )
    val bubble1Y by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 5000, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "b1y"
    )

    val bubble2X by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 6000, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "b2x"
    )
    val bubble2Y by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 8000, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "b2y"
    )

    val bgColor = if (isDarkMode) Color(0xFF0F0F16) else Color(0xFFF2F4F8)
    val bubble1Color = Color(0xFFCBA6F7).copy(alpha = 0.6f) 
    val bubble2Color = Color(0xFFF5C2E7).copy(alpha = 0.6f) 

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().background(bgColor)) {
        drawCircle(
            brush = Brush.radialGradient(colors = listOf(bubble1Color, Color.Transparent), center = Offset(size.width * bubble1X, size.height * bubble1Y), radius = size.minDimension * 0.6f),
            center = Offset(size.width * bubble1X, size.height * bubble1Y), radius = size.minDimension * 0.6f
        )
        drawCircle(
            brush = Brush.radialGradient(colors = listOf(bubble2Color, Color.Transparent), center = Offset(size.width * bubble2X, size.height * bubble2Y), radius = size.minDimension * 0.5f),
            center = Offset(size.width * bubble2X, size.height * bubble2Y), radius = size.minDimension * 0.5f
        )
    }
}

@Composable
fun AnimatedLinearGradientBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "gradient_anim")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 10000, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "angle"
    )
    val colors = listOf(Color(0xFF4158D0), Color(0xFFC850C0), Color(0xFFFFCC70))

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val radians = Math.toRadians(angle.toDouble())
        val x = kotlin.math.cos(radians).toFloat()
        val y = kotlin.math.sin(radians).toFloat()
        val start = Offset(size.width / 2 + x * size.width, size.height / 2 + y * size.height)
        val end = Offset(size.width / 2 - x * size.width, size.height / 2 - y * size.height)
        drawRect(brush = Brush.linearGradient(colors = colors, start = start, end = end))
    }
}
