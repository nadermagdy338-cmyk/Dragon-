/*
 * Original code from: libxzr (HorizonKernelFlasher) and capntrips (KernelFlasher)
 * Modified and integrated by: Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.ui.unit.ExperimentalUnitApi::class,
    kotlinx.serialization.ExperimentalSerializationApi::class
)

package nd.max.ui.flasher

import nd.max.ui.settings.BgType
import nd.max.ui.component.VideoWallpaperPlayer
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

// --- IMPORT COIL & SETTINGS ---
import coil.compose.AsyncImage
import coil.request.ImageRequest
import nd.max.R





// --- IMPORT ZUAN UTILS ---
import nd.max.ui.util.KernelInfoUtils
import nd.max.ui.util.BootInfo

// --- IMPORT HAZE ---
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect

// --- IMPORT MODULE CAPNTRIPS ---
import com.github.capntrips.kernelflasher.FilesystemService
import com.github.capntrips.kernelflasher.IFilesystemService
import com.github.capntrips.kernelflasher.ui.screens.main.MainContent
import com.github.capntrips.kernelflasher.ui.screens.main.MainViewModel
import com.github.capntrips.kernelflasher.ui.components.FlasherGlassTheme
import com.github.capntrips.kernelflasher.ui.screens.backups.BackupsContent
import com.github.capntrips.kernelflasher.ui.screens.slot.SlotContent
import com.github.capntrips.kernelflasher.ui.screens.slot.SlotFlashContent
import com.github.capntrips.kernelflasher.ui.screens.backups.SlotBackupsContent

// --- IMPORT LIBSU ---
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File 
import java.io.InputStream

enum class FlasherMode { HORIZON, CAPNTRIPS }



// =========================================================================
// [FIXED] FUNGSI SMART EXTRACT: DETEKSI NAMA ASSET OTOMATIS
// =========================================================================
private fun setupFlashTools(context: Context): String {
    // Map: Nama target di folder files -> Kemungkinan nama di assets
    val toolsMap = mapOf(
        "httools_static" to listOf("httools_static", "libhttools_static", "libhttools_static.so"),
        "lptools_static" to listOf("lptools_static", "liblptools_static", "liblptools_static.so"),
        "magiskboot"     to listOf("magiskboot", "libmagiskboot", "libmagiskboot.so"),
        "flash_ak3.sh"   to listOf("flash_ak3.sh")
    )
    
    var status = "Ready"
    
    try {
        val filesDir = context.filesDir
        if (!filesDir.exists()) filesDir.mkdirs()

        for ((targetName, possibleAssetNames) in toolsMap) {
            val destFile = File(filesDir, targetName)
            
            // Cek apakah perlu copy (jika belum ada atau size 0)
            if (!destFile.exists() || destFile.length() == 0L) {
                var copied = false
                
                // Coba cari aset satu per satu (untuk mengatasi masalah nama 'lib...')
                for (assetName in possibleAssetNames) {
                    try {
                        context.assets.open(assetName).use { inputStream ->
                            destFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        copied = true
                        break // Berhasil copy, lanjut ke file berikutnya
                    } catch (e: Exception) {
                        // Lanjut coba nama aset berikutnya
                    }
                }
                
                if (copied) {
                    // Set Permission Eksekusi
                    if (!destFile.setExecutable(true, false)) {
                        Shell.cmd("chmod 755 ${destFile.absolutePath}").exec()
                    }
                } else {
                    // Gagal menemukan aset dengan nama apapun
                    status = "Missing asset for: $targetName"
                }
            }
        }
        
        // Final touch: Set PATH
        Shell.cmd("export PATH=\$PATH:${filesDir.absolutePath}").exec()
        Shell.cmd("chmod 755 ${filesDir.absolutePath}/*").exec() // Paksa chmod semua
        
    } catch (e: Exception) {
        status = "Error: ${e.message}"
    }
    return status
}

@Composable
fun KernelFlasherScreen(
    rootNavController: NavController,
) {
    val settingsViewModel: nd.max.ui.settings.SettingsViewModel = viewModel()
    val bgType by settingsViewModel.bgType.collectAsStateWithLifecycle()
    val isHazeEnabled by settingsViewModel.isHazeEnabled.collectAsStateWithLifecycle()
    val cardDarkness by settingsViewModel.cardDarkness.collectAsStateWithLifecycle()
    val isDynamic by settingsViewModel.isDynamicColor.collectAsStateWithLifecycle()
    val themeColorName by settingsViewModel.currentThemeColor.collectAsStateWithLifecycle()
    val isCustomColor by settingsViewModel.isCustomColor.collectAsStateWithLifecycle()
    val customPrimary by settingsViewModel.customPrimaryColor.collectAsStateWithLifecycle()
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val isVideo by settingsViewModel.isVideoWallpaper.collectAsStateWithLifecycle()
    val isBgBlur by settingsViewModel.isBgBlur.collectAsStateWithLifecycle()
    val blurStrength by settingsViewModel.blurStrength.collectAsStateWithLifecycle()
    val bgSaturation by settingsViewModel.bgSaturation.collectAsStateWithLifecycle()
    val bgContrast by settingsViewModel.bgContrast.collectAsStateWithLifecycle()
    val bgUriString by settingsViewModel.backgroundImageUri.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val effectivePrimary = if (isDynamic) MaterialTheme.colorScheme.primary
    else if (isCustomColor) Color(customPrimary) else themeColorName.primary
    val isCustomBg = bgType != BgType.SYSTEM
    val isGlassActive = isHazeEnabled && isCustomBg
    val finalCardColor = when {
        isGlassActive -> Color.Transparent
        isCustomBg -> Color.Black.copy(alpha = cardDarkness)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isCustomBg) Color.White else MaterialTheme.colorScheme.onSurface
    val localHazeState = rememberHazeState()

    var currentMode by remember { mutableStateOf(FlasherMode.CAPNTRIPS) }
    var fsManager by remember { mutableStateOf<FileSystemManager?>(null) }
    var extractionStatus by remember { mutableStateOf(context.getString(R.string.flasher_init)) }
    var mainViewModel by remember { mutableStateOf<MainViewModel?>(null) }

    DisposableEffect(Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                try {
                    val serviceInterface = IFilesystemService.Stub.asInterface(service)
                    fsManager = FileSystemManager.getRemote(serviceInterface.getFileSystemService())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) { fsManager = null }
        }
        val intent = Intent(context, FilesystemService::class.java)
        RootService.bind(intent, connection)
        onDispose { try { RootService.unbind(connection) } catch (_: Exception) {} }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            extractionStatus = context.getString(R.string.flasher_checking_tools)
            val result = setupFlashTools(context)
            Shell.cmd("cd ${context.filesDir.absolutePath}").exec()
            extractionStatus = result
        }
    }

    LaunchedEffect(fsManager, extractionStatus) {
        if (fsManager != null && extractionStatus == "Ready" && mainViewModel == null) {
            withContext(Dispatchers.IO) {
                val vm = MainViewModel(context.applicationContext, fsManager!!, rootNavController)
                withContext(Dispatchers.Main) { mainViewModel = vm }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(if (isCustomBg) Color.Transparent else MaterialTheme.colorScheme.surface)) {
            if (isCustomBg && bgUriString != null) {
                val blurModifier = if (isBgBlur && blurStrength > 0f) Modifier.blur(blurStrength.dp)
                else if (isBgBlur) Modifier.blur(20.dp) else Modifier
                val saturationMatrix = ColorMatrix().apply { setToSaturation(bgSaturation) }
                Box(Modifier.fillMaxSize().hazeSource(state = localHazeState, zIndex = 0f)) {
                    if (isVideo) {
                        VideoWallpaperPlayer(Uri.parse(bgUriString), Modifier.fillMaxSize().then(blurModifier))
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(Uri.parse(bgUriString)).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().then(blurModifier),
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.colorMatrix(saturationMatrix)
                        )
                    }
                    if (bgContrast > 0f) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgContrast)))
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().hazeSource(state = localHazeState, zIndex = 0f))
            }
        }

        // Edge-to-edge background remains visible behind the system bars, while
        // the interactive flasher chrome starts below the status-bar inset.
        // This prevents the back button/title from colliding with system icons.
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            KernelFlasherHeader(
                primary = effectivePrimary,
                content = contentColor,
                currentMode = currentMode,
                onModeChange = { currentMode = it },
                onBack = { rootNavController.popBackStack() },
                ready = mainViewModel != null,
                status = extractionStatus,
                glass = isGlassActive,
                hazeState = localHazeState
            )

            AnimatedContent(
                targetState = currentMode,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                label = "kernel-flasher-workspace",
                modifier = Modifier.weight(1f)
            ) { mode ->
                when (mode) {
                    FlasherMode.HORIZON -> HorizonFlasherContent(
                        navController = rootNavController,
                        headerPadding = 8.dp,
                        isGlassActive = isGlassActive,
                        hazeState = localHazeState,
                        cardColor = finalCardColor,
                        contentColor = contentColor,
                        primaryColor = effectivePrimary
                    )
                    FlasherMode.CAPNTRIPS -> {
                        if (mainViewModel != null) {
                            CapntripsContainer(
                                rootNavController = rootNavController,
                                headerPadding = 8.dp,
                                viewModel = mainViewModel!!,
                                glassTheme = FlasherGlassTheme(
                                    isGlassActive = isGlassActive,
                                    hazeState = localHazeState,
                                    cardColor = finalCardColor,
                                    contentColor = contentColor,
                                    primaryColor = effectivePrimary
                                )
                            )
                        } else {
                            FlasherLoadingState(
                                primary = effectivePrimary,
                                content = contentColor,
                                rootReady = fsManager != null,
                                extractionStatus = extractionStatus
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KernelFlasherHeader(
    primary: Color,
    content: Color,
    currentMode: FlasherMode,
    onModeChange: (FlasherMode) -> Unit,
    onBack: () -> Unit,
    ready: Boolean,
    status: String,
    glass: Boolean,
    hazeState: HazeState
) {
    val infinite = rememberInfiniteTransition(label = "flasher-orbit")
    val rotation by infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "orbit"
    )
    val pulse by infinite.animateFloat(
        0.94f, 1.04f,
        infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "pulse"
    )

    val shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            // haze 2.x: the blur style is configured inside `blurEffect {}`.
            // The deprecated `hazeEffect(state, style)` overload is removed in
            // the next haze release, so no surface here uses it any more.
            .then(
                if (glass) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurEffect {
                            backgroundColor = Color.Black.copy(alpha = 0.18f)
                            blurRadius = 24.dp
                            noiseFactor = 0.06f
                            colorEffects = emptyList()
                        }
                    }
                } else Modifier
            ),
        color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back), tint = content)
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier.size(56.dp).graphicsLayer { rotationZ = rotation * 0.08f; scaleX = pulse; scaleY = pulse },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(primary.copy(alpha = 0.12f), radius = size.minDimension * 0.45f)
                        drawCircle(primary.copy(alpha = 0.34f), radius = size.minDimension * 0.31f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                    }
                    Icon(Icons.Default.Memory, null, tint = primary, modifier = Modifier.size(25.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.kernel_flasher_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = content)
                    Text(
                        text = if (ready) stringResource(R.string.flasher_workspace_ready) else status,
                        style = MaterialTheme.typography.labelMedium,
                        color = content.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusDot(ready = ready, primary = primary)
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(content.copy(alpha = 0.06f)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FlasherModeChip(
                    selected = currentMode == FlasherMode.CAPNTRIPS,
                    title = stringResource(R.string.flasher_mode_capntrips),
                    icon = Icons.Default.AutoAwesome,
                    primary = primary,
                    content = content,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeChange(FlasherMode.CAPNTRIPS) }
                )
                FlasherModeChip(
                    selected = currentMode == FlasherMode.HORIZON,
                    title = stringResource(R.string.flasher_mode_horizon),
                    icon = Icons.Default.Bolt,
                    primary = primary,
                    content = content,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeChange(FlasherMode.HORIZON) }
                )
            }
        }
    }
}

@Composable
private fun FlasherModeChip(
    selected: Boolean,
    title: String,
    icon: ImageVector,
    primary: Color,
    content: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (selected) 1f else 0.97f, tween(180), label = "mode-scale")
    Surface(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(14.dp)),
        onClick = onClick,
        color = if (selected) primary.copy(alpha = 0.18f) else Color.Transparent,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) primary else content.copy(alpha = 0.65f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = if (selected) primary else content.copy(alpha = 0.72f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun StatusDot(ready: Boolean, primary: Color) {
    val transition = rememberInfiniteTransition(label = "ready-pulse")
    val alpha by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "ready-alpha")
    Box(Modifier.size(12.dp).clip(CircleShape).background(if (ready) primary.copy(alpha = alpha) else MaterialTheme.colorScheme.error.copy(alpha = 0.75f)))
}

@Composable
private fun FlasherLoadingState(
    primary: Color,
    content: Color,
    rootReady: Boolean,
    extractionStatus: String
) {
    val rotation by rememberInfiniteTransition(label = "loading-ring").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "loading-rotation"
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(Modifier.size(92.dp).graphicsLayer { rotationZ = rotation }, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { 0.72f }, modifier = Modifier.fillMaxSize(), strokeWidth = 3.dp, color = primary.copy(alpha = 0.35f))
                Icon(Icons.Default.Memory, null, tint = primary, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = when {
                    !rootReady -> stringResource(R.string.flasher_connecting_root)
                    extractionStatus != "Ready" -> extractionStatus
                    else -> stringResource(R.string.flasher_scanning_partitions)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = content
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.please_wait), color = content.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ... CapntripsContainer & Lainnya SAMA SEPERTI FILE SEBELUMNYA ...
@Composable
fun CapntripsContainer(
    rootNavController: NavController, 
    headerPadding: Dp,
    viewModel: MainViewModel,
    glassTheme: FlasherGlassTheme = FlasherGlassTheme()
) {
    val internalNavController = rememberNavController()
    val topSpacer = @Composable { Spacer(Modifier.height(headerPadding + 16.dp)) }

    NavHost(
        navController = internalNavController,
        startDestination = "main",
        modifier = Modifier.fillMaxSize()
    ) {
        
        // Error Route
        composable("error/{message}") { backStackEntry ->
            val message = backStackEntry.arguments?.getString("message") ?: stringResource(R.string.unknown_error)
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.error_occurred), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(24.dp))
                nd.max.ui.component.StudioButton(onClick = { internalNavController.popBackStack("main", false) }) {
                    Text(stringResource(R.string.go_back))
                }
            }
        }

        // --- MAIN SCREENS ---
        composable("main") {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                topSpacer()
                MainContent(viewModel = viewModel, navController = internalNavController, glassTheme = glassTheme)
                Spacer(Modifier.height(100.dp))
            }
        }

        composable("backups") {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                topSpacer()
                BackupsContent(viewModel = viewModel.backups, navController = internalNavController, glassTheme = glassTheme)
                Spacer(Modifier.height(100.dp))
            }
        }
         composable("backups/{backupId}") { backStackEntry ->
            val backupId = backStackEntry.arguments?.getString("backupId")
            viewModel.backups.currentBackup = backupId
             Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                topSpacer()
                BackupsContent(viewModel = viewModel.backups, navController = internalNavController, glassTheme = glassTheme)
                Spacer(Modifier.height(100.dp))
             }
        }

        // --- SLOT A ---
        composable("slot_a") {
            val slotVM = viewModel.slotA
            if (slotVM != null) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    topSpacer()
                    SlotContent(viewModel = slotVM, slotSuffix = "_a", navController = internalNavController, glassTheme = glassTheme)
                    Spacer(Modifier.height(100.dp))
                }
            } else ErrorScreen(stringResource(R.string.slot_a_not_found), headerPadding)
        }

        val slotARoutes = listOf(
            "slot_a/flash", "slot_a/flash/ak3", "slot_a/flash/image", 
            "slot_a/flash/image/flash", "slot_a/backup", "slot_a/backup/backup"
        )
        slotARoutes.forEach { route ->
            composable(route) { HandleSlotFlash(viewModel.slotA, "_a", internalNavController, headerPadding, glassTheme) }
        }

        val slotABackupsRoutes = listOf(
            "slot_a/backups", "slot_a/backups/{backupId}", "slot_a/backups/{backupId}/restore",
            "slot_a/backups/{backupId}/restore/restore", "slot_a/backups/{backupId}/flash/ak3"
        )
        slotABackupsRoutes.forEach { route ->
            composable(route) { backStackEntry ->
                if(route.contains("{backupId}")) viewModel.backups.currentBackup = backStackEntry.arguments?.getString("backupId")
                HandleSlotBackups(viewModel.slotA, viewModel.backups, "_a", internalNavController, headerPadding, glassTheme)
            }
        }

        // --- SLOT B ---
        composable("slot_b") {
            val slotVM = viewModel.slotB
            if (slotVM != null) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    topSpacer()
                    SlotContent(viewModel = slotVM, slotSuffix = "_b", navController = internalNavController, glassTheme = glassTheme)
                    Spacer(Modifier.height(100.dp))
                }
            } else ErrorScreen(stringResource(R.string.slot_b_not_available), headerPadding)
        }

        val slotBRoutes = listOf(
            "slot_b/flash", "slot_b/flash/ak3", "slot_b/flash/image", 
            "slot_b/flash/image/flash", "slot_b/backup", "slot_b/backup/backup"
        )
        slotBRoutes.forEach { route ->
            composable(route) { HandleSlotFlash(viewModel.slotB, "_b", internalNavController, headerPadding, glassTheme) }
        }
        
        val slotBBackupsRoutes = listOf(
            "slot_b/backups", "slot_b/backups/{backupId}", "slot_b/backups/{backupId}/restore",
            "slot_b/backups/{backupId}/restore/restore", "slot_b/backups/{backupId}/flash/ak3"
        )
        slotBBackupsRoutes.forEach { route ->
            composable(route) { backStackEntry ->
                if (route.contains("{backupId}")) viewModel.backups.currentBackup = backStackEntry.arguments?.getString("backupId")
                HandleSlotBackups(viewModel.slotB, viewModel.backups, "_b", internalNavController, headerPadding, glassTheme) 
            }
        }

        // --- REBOOT ---
         composable("reboot") {
            Box(Modifier.fillMaxSize().padding(top=headerPadding), contentAlignment = Alignment.Center) {
                nd.max.ui.component.StudioButton(onClick = { Shell.cmd("reboot").submit() }) { Text(stringResource(R.string.confirm_reboot_system)) }
            }
        }
    }
}

// ==========================================
// 3. HORIZON CONTENT (UPDATED FOR STYLING)
// ==========================================

@Composable
fun HorizonFlasherContent(
    navController: NavController, 
    headerPadding: Dp,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    contentColor: Color,
    primaryColor: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var logs by remember { mutableStateOf("") }
    var isFlashing by remember { mutableStateOf(false) }
    var showRebootDialog by remember { mutableStateOf(false) }

    val activeSlot by produceState(initialValue = stringResource(R.string.loading)) { value = KernelInfoUtils.getActiveSlot() }
    val deviceModel = remember { KernelInfoUtils.getDeviceModel() }
    val kernelVersion = remember { KernelInfoUtils.getKernelVersion() }
    val bootInfoA by produceState(initialValue = BootInfo(stringResource(R.string.scanning), "...", "...")) { value = KernelInfoUtils.getBootInfo("a") }
    val bootInfoB by produceState(initialValue = BootInfo(stringResource(R.string.scanning), "...", "...")) { value = KernelInfoUtils.getBootInfo("b") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isFlashing = true
            logs = context.getString(R.string.flasher_init_process) + "\nTarget: $uri\n"
            scope.launch {
                val worker = nd.max.ui.flasher.FlasherWorker(context, uri) { newLog ->
                    logs += "$newLog\n"
                }
                val success = worker.startFlashing()
                isFlashing = false
                if (success) {
                    logs += "\n" + context.getString(R.string.flasher_success)
                    showRebootDialog = true
                } else {
                    logs += "\n" + context.getString(R.string.flasher_failed)
                    Toast.makeText(context, context.getString(R.string.flasher_toast_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showRebootDialog) {
        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = primaryColor) },
            title = { Text(stringResource(R.string.flashing_complete)) },
            text = { Text(stringResource(R.string.flasher_reboot_message)) },
            confirmButton = { nd.max.ui.component.StudioButton(onClick = { nd.max.ui.flasher.FlasherWorker.rebootDevice() }, colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) { Text(stringResource(R.string.reboot_system)) } },
            dismissButton = { nd.max.ui.component.StudioTextButton(onClick = { showRebootDialog = false }, colors = ButtonDefaults.textButtonColors(contentColor = primaryColor)) { Text(stringResource(R.string.later)) } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = headerPadding + 16.dp, bottom = 120.dp) 
    ) {
        item {
            FlasherSectionHeader(stringResource(R.string.horizon_engine_legacy), Icons.Default.Info, primaryColor)
            StyledCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                isGlassActive = isGlassActive,
                hazeState = hazeState,
                cardColor = cardColor
            ) {
                Column {
                    PixelListItem(Icons.Default.PhoneAndroid, stringResource(R.string.device_model), deviceModel, contentColor)
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp), color = contentColor.copy(0.08f))
                    PixelListItem(Icons.Default.Memory, stringResource(R.string.kernel_version), kernelVersion, contentColor)
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp), color = contentColor.copy(0.08f))
                    PixelListItem(Icons.Default.SystemUpdate, stringResource(R.string.active_slot), stringResource(R.string.slot_current, activeSlot.uppercase()), primaryColor)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }

        item {
            FlasherSectionHeader(stringResource(R.string.boot_partitions), Icons.Default.SdStorage, primaryColor)
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val isSlotA = activeSlot.contains("A", ignoreCase = true)
                val isSlotB = activeSlot.contains("B", ignoreCase = true)
                Box(Modifier.weight(1f)) { SlotStatusCard(stringResource(R.string.slot_a), isSlotA, bootInfoA, cardColor, contentColor, primaryColor, isGlassActive, hazeState) }
                Box(Modifier.weight(1f)) { SlotStatusCard(stringResource(R.string.slot_b), isSlotB, bootInfoB, cardColor, contentColor, primaryColor, isGlassActive, hazeState) }
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }

        item {
            FlasherSectionHeader(stringResource(R.string.flasher_console), Icons.Default.Terminal, primaryColor)
            StyledCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 180.dp, max = 300.dp),
                isGlassActive = isGlassActive,
                hazeState = hazeState,
                cardColor = if(isGlassActive) Color.Transparent else Color(0xFF121212)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF27C93F)))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.terminal_output).uppercase(), color = Color.White.copy(0.4f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (logs.isEmpty()) stringResource(R.string.horizon_ready) else logs, 
                        color = Color(0xFF00E676), 
                        fontFamily = FontFamily.Monospace, 
                        fontSize = 12.sp, 
                        lineHeight = 18.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            nd.max.ui.component.StudioButton(
                onClick = { if (!isFlashing) filePickerLauncher.launch("*/*") },
                enabled = !isFlashing,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = if (primaryColor.luminance() > 0.5f) Color.Black else Color.White)
            ) {
                if (isFlashing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.flashing_kernel_progress), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Bolt, null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.flash_horizon_method), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

// ==========================================
// 4. HELPER COMPOSABLES
// ==========================================

@Composable
fun FlasherSectionHeader(title: String, icon: ImageVector, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 0.dp, end = 24.dp, bottom = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun StyledCard(
    modifier: Modifier = Modifier,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    
    val glassModifier = if (isGlassActive) {
        Modifier.clip(shape)
            .hazeEffect(state = hazeState) {
                blurEffect {
                    backgroundColor = cardColor.copy(alpha = 0.5f)
                    blurRadius = 24.dp
                    noiseFactor = 0.1f
                    colorEffects = emptyList()
                }
            }
            .border(1.dp, Color.White.copy(0.1f), shape)
    } else {
        Modifier.clip(shape)
    }

    Card(
        modifier = modifier.then(glassModifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if(isGlassActive) Color.Transparent else cardColor)
    ) {
        content()
    }
}

@Composable
fun HandleSlotFlash(slotVM: com.github.capntrips.kernelflasher.ui.screens.slot.SlotViewModel?, suffix: String, navController: NavController, headerPadding: Dp, glassTheme: FlasherGlassTheme = FlasherGlassTheme()) {
    if (slotVM != null) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(headerPadding + 16.dp)) 
            SlotFlashContent(viewModel = slotVM, slotSuffix = suffix, navController = navController, glassTheme = glassTheme)
            Spacer(Modifier.height(100.dp))
        }
    } else ErrorScreen(stringResource(R.string.slot_vm_not_found), headerPadding)
}

@Composable
fun HandleSlotBackups(
    slotVM: com.github.capntrips.kernelflasher.ui.screens.slot.SlotViewModel?, 
    backupsVM: com.github.capntrips.kernelflasher.ui.screens.backups.BackupsViewModel,
    suffix: String, 
    navController: NavController,
    headerPadding: Dp,
    glassTheme: FlasherGlassTheme = FlasherGlassTheme()
) {
    if (slotVM != null) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(headerPadding + 16.dp)) 
            SlotBackupsContent(slotViewModel = slotVM, backupsViewModel = backupsVM, slotSuffix = suffix, navController = navController, glassTheme = glassTheme)
            Spacer(Modifier.height(100.dp))
        }
    } else ErrorScreen(stringResource(R.string.slot_vm_not_found), headerPadding)
}

@Composable
fun ErrorScreen(msg: String, topPadding: Dp) {
    Box(Modifier.fillMaxSize().padding(top=topPadding + 32.dp), contentAlignment = Alignment.Center) {
        Text(msg)
    }
}

@Composable
fun SectionTitle(title: String, color: Color) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 32.dp, bottom = 8.dp))
}

@Composable
fun PixelListItem(icon: ImageVector, title: String, value: String, contentColor: Color) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = contentColor) },
        supportingContent = { Text(value, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(icon, null, tint = contentColor.copy(0.7f)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SlotStatusCard(
    slotName: String, 
    isActive: Boolean, 
    data: BootInfo, 
    cardColor: Color, 
    contentColor: Color, 
    primaryColor: Color, 
    isGlassActive: Boolean, 
    hazeState: HazeState
) {
    val bgColor = if (isActive) primaryColor.copy(alpha = if(isGlassActive) 0.6f else 1f) else if(isGlassActive) cardColor.copy(alpha=0.3f) else MaterialTheme.colorScheme.surfaceContainerHighest
    
    val textColor = if (isActive) (if(primaryColor.luminance() > 0.5f) Color.Black else Color.White) else contentColor
    
    val shape = RoundedCornerShape(20.dp)

    // A slow breathing glow behind the active slot only — a quiet way of
    // saying "this is the one that will actually boot" beyond the flat fill.
    val glowTransition = rememberInfiniteTransition(label = "slot_glow")
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.18f, targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "slot_glow_alpha"
    )

    val glassModifier = if (isGlassActive) {
        Modifier.clip(shape)
            .hazeEffect(state = hazeState) {
                blurEffect {
                    backgroundColor = bgColor
                    blurRadius = 15.dp
                    colorEffects = emptyList()
                }
            }
            .border(1.dp, Color.White.copy(0.1f), shape)
    } else {
        Modifier.clip(shape)
    }

    Box {
        if (isActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(4.dp)
                    .background(
                        Brush.radialGradient(listOf(primaryColor.copy(alpha = glowAlpha), Color.Transparent)),
                        shape
                    )
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth().then(glassModifier),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = if(isGlassActive) Color.Transparent else bgColor),
            border = if (isActive && !isGlassActive) BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)) else null
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (isActive) Icons.Default.CheckCircle else Icons.Outlined.SdStorage, null, tint = textColor, modifier = Modifier.size(20.dp))
                    if (isActive) Text(stringResource(R.string.active_label).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = textColor)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(slotName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = textColor)
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("SHA1: ${data.sha1.take(8)}...", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = textColor.copy(alpha = 0.7f))
                    Text(stringResource(R.string.format_label, data.format), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
                }
            }
        }
    }
}
