/*
 * Copyright (c) 2025 ZKM
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package nd.max.ui.activitylauncher

import nd.max.ui.settings.BgType
import nd.max.ui.settings.WeatherEffect
import nd.max.ui.util.EventLog
import nd.max.ui.component.MaxManagerSubScreenTopBar
import nd.max.ui.component.ScreenAccentProvider
import nd.max.ui.components.VideoWallpaperPlayer
import nd.max.ui.components.WeatherEffectOverlay
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest




import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.HazeProgressive
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Manual stand-in for the old HazeMaterials.regular() style (haze-materials artifact isn't
// part of this project's dependency set). Matches the same "regular" translucency/blur weight
// used by the other frosted-glass surfaces in this file.
private val overlayHazeStyle = HazeBlurStyle(
    backgroundColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f),
    blurRadius = 20.dp,
    noiseFactor = 0.1f,
    colorEffects = listOf(HazeColorEffect.tint(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)))
)

// --- DATA CLASS (Lightweight - No Drawable) ---

data class AppData(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: String,
    val isSystemApp: Boolean,
    val activityCount: Int
)

data class ActivityItem(
    val name: String,
    val label: String,
    val isExported: Boolean
)

enum class SortOption { NAME_ASC, NAME_DESC }
enum class FilterOption { ALL, SYSTEM, USER }



@Composable
fun ActivityLauncherScreen(
    rootNavController: NavController,
    viewModel: ActivityLauncherViewModel = viewModel(),
    
) {
    val internalNavController = rememberNavController()

    NavHost(navController = internalNavController, startDestination = "app_list") {
        composable("app_list") {
            AppListScreen(
                viewModel = viewModel,
                
                onBack = { rootNavController.popBackStack() },
                onAppClick = { app ->
                    internalNavController.navigate("app_detail/${app.packageName}")
                }
            )
        }

        composable("app_detail/{packageName}") { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            AppDetailScreen(
                packageName = packageName,
                
                onBack = { internalNavController.popBackStack() }
            )
        }
    }
}

// --- HELPER: ASYNC ICON LOADER ---
@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val icon by produceState<Drawable?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName)
            } catch (e: Exception) {
                null
            }
        }
    }

    if (icon != null) {
        Image(
            bitmap = icon!!.toBitmap().asImageBitmap(),
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Android, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(4.dp).fillMaxSize()
            )
        }
    }
}

// --- ACTIVITY OBSERVATORY ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: ActivityLauncherViewModel,
    onBack: () -> Unit,
    onAppClick: (AppData) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allApps by viewModel.allApps.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loadingProgress by viewModel.loadProgress.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterOption by rememberSaveable { mutableStateOf(FilterOption.ALL) }
    var sortOption by rememberSaveable { mutableStateOf(SortOption.NAME_ASC) }
    var isRefreshing by remember { mutableStateOf(false) }
    var inspectorActive by remember { mutableStateOf(FloatingActivityService.isRunning) }
    val isArabic = java.util.Locale.getDefault().language == "ar"
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val displayedApps by remember(allApps, searchQuery, filterOption, sortOption) {
        derivedStateOf {
            allApps.asSequence()
                .filter { filterOption == FilterOption.ALL || (filterOption == FilterOption.SYSTEM) == it.isSystemApp }
                .filter { searchQuery.isBlank() || it.label.contains(searchQuery, true) || it.packageName.contains(searchQuery, true) }
                .let { if (sortOption == SortOption.NAME_ASC) it.sortedBy { a -> a.label.lowercase() } else it.sortedByDescending { a -> a.label.lowercase() } }
                .toList()
        }
    }

    fun toggleInspector() {
        val next = !inspectorActive
        if (next && !Settings.canDrawOverlays(context)) {
            Toast.makeText(context, if (isArabic) "يلزم السماح بالظهور فوق التطبيقات" else "Overlay permission required", Toast.LENGTH_SHORT).show()
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            return
        }
        val intent = Intent(context, FloatingActivityService::class.java)
        if (next) context.startService(intent) else context.stopService(intent)
        inspectorActive = next
    }

    ScreenAccentProvider(MaterialTheme.colorScheme.secondary) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                MaxManagerSubScreenTopBar(
                    scrollBehavior = scrollBehavior,
                    title = if (isArabic) "مراقب المكوّنات" else "Activity Observatory",
                    subtitle = if (inspectorActive) (if (isArabic) "المفتش المباشر يعمل" else "Live inspector is active")
                    else (if (isArabic) "استكشف نقاط الدخول" else "Explore application entry points"),
                    onBack = onBack,
                    accentIcon = Icons.Outlined.Apps,
                    accent = MaterialTheme.colorScheme.secondary,
                    actions = {
                        IconButton(onClick = { toggleInspector() }) {
                            Icon(
                                if (inspectorActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (inspectorActive) "Disable inspector" else "Enable inspector"
                            )
                        }
                    }
                )
            }
        ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ObservatoryHero(
                    totalApps = allApps.size,
                    visibleApps = displayedApps.size,
                    totalActivities = allApps.sumOf { it.activityCount },
                    inspectorActive = inspectorActive,
                    isArabic = isArabic,
                    onInspectorClick = { toggleInspector() }
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) ({ IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } }) else null,
                    placeholder = { Text(if (isArabic) "ابحث باسم التطبيق أو الحزمة" else "Search app or package") }
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
                        listOf(FilterOption.ALL, FilterOption.USER, FilterOption.SYSTEM).forEach { option ->
                            val label = when (option) {
                                FilterOption.ALL -> if (isArabic) "الكل" else "All"
                                FilterOption.USER -> if (isArabic) "مستخدم" else "User"
                                FilterOption.SYSTEM -> if (isArabic) "نظام" else "System"
                            }
                            FilterChip(
                                selected = filterOption == option,
                                onClick = { filterOption = option },
                                label = { Text(label) },
                                leadingIcon = if (filterOption == option) ({ Icon(Icons.Default.Check, null, Modifier.size(15.dp)) }) else null
                            )
                        }
                    }
                    IconButton(onClick = { sortOption = if (sortOption == SortOption.NAME_ASC) SortOption.NAME_DESC else SortOption.NAME_ASC }) {
                        Icon(if (sortOption == SortOption.NAME_ASC) Icons.Default.SortByAlpha else Icons.Default.SwapVert, null)
                    }
                }
            }

            if (isLoading) {
                item {
                    LoadingObservatory(progress = loadingProgress, isArabic = isArabic)
                }
            } else if (displayedApps.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Default.SearchOff,
                        if (isArabic) "لا توجد نتائج" else "Nothing found",
                        if (isArabic) "جرّب اسم حزمة أو فلترًا آخر." else "Try another package name or filter.",
                        if (isArabic) "مسح البحث والفلاتر" else "Clear filters",
                        MaterialTheme.colorScheme.onBackground,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.primary
                    ) { searchQuery = ""; filterOption = FilterOption.ALL }
                }
            } else {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isArabic) "التطبيقات" else "Applications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${displayedApps.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                items(displayedApps, key = { it.packageName }) { app ->
                    AnimatedAppRow(app = app, onClick = { onAppClick(app) }, isArabic = isArabic)
                }
            }

            item {
                nd.max.ui.component.StudioOutlinedButton(
                    onClick = {
                        scope.launch {
                            isRefreshing = true
                            viewModel.loadApps(true)
                            delay(250)
                            isRefreshing = false
                        }
                    },
                    enabled = !isRefreshing && !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRefreshing) (if (isArabic) "جارٍ تحديث الفهرس…" else "Refreshing index…") else (if (isArabic) "تحديث فهرس المكوّنات" else "Refresh component index"))
                }
            }
        }
        }
    }
}

@Composable
private fun ObservatoryHero(
    totalApps: Int,
    visibleApps: Int,
    totalActivities: Int,
    inspectorActive: Boolean,
    isArabic: Boolean,
    onInspectorClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "observatory_motion")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseOutCubic), RepeatMode.Reverse),
        label = "hero_pulse"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label = "hero_rotation"
    )
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(68.dp).graphicsLayer(rotationZ = rotation).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .22f), CircleShape))
                    Box(Modifier.size(52.dp).graphicsLayer(scaleX = pulse, scaleY = pulse).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AccountTree, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (isArabic) "خريطة المكوّنات" else "Component map", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (inspectorActive) (if (isArabic) "المفتش المباشر متصل الآن" else "Live inspector is connected")
                        else (if (isArabic) "افتح تطبيقًا لفحص نقاط دخوله" else "Open an app to inspect its entry points"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ObservatoryMetric(if (isArabic) "التطبيقات" else "Apps", totalApps.toString(), Modifier.weight(1f))
                ObservatoryMetric(if (isArabic) "المعروض" else "Shown", visibleApps.toString(), Modifier.weight(1f))
                ObservatoryMetric(if (isArabic) "الأنشطة" else "Activities", totalActivities.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            nd.max.ui.component.StudioTonalButton(onClick = onInspectorClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                Icon(if (inspectorActive) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (inspectorActive) (if (isArabic) "إيقاف المفتش المباشر" else "Stop live inspector") else (if (isArabic) "تشغيل المفتش المباشر" else "Start live inspector"))
            }
        }
    }
}

@Composable
private fun ObservatoryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun LoadingObservatory(progress: Float, isArabic: Boolean) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(350), label = "index_progress")
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(progress = { animated }, modifier = Modifier.size(34.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(if (isArabic) "فهرسة المكوّنات" else "Indexing components", fontWeight = FontWeight.SemiBold)
                    Text(if (isArabic) "قراءة حزم النظام والأنشطة الحقيقية…" else "Reading installed packages and activities…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)))
            Text("${(animated * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AnimatedAppRow(app: AppData, onClick: () -> Unit, isArabic: Boolean) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(280), label = "app_alpha")
    val scale by animateFloatAsState(if (visible) 1f else .975f, tween(280, easing = EaseOutCubic), label = "app_scale")
    Card(
        modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = alpha, scaleX = scale, scaleY = scale).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, Modifier.size(50.dp).clip(RoundedCornerShape(15.dp)))
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, false))
                    if (app.isSystemApp) {
                        Spacer(Modifier.width(7.dp))
                        Surface(shape = RoundedCornerShape(7.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(if (isArabic) "نظام" else "SYSTEM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                    }
                }
                Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountTree, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("${app.activityCount} ${if (isArabic) "نشاط" else "activities"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// --- SCREEN 2: APP DETAIL ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(packageName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isArabic = java.util.Locale.getDefault().language == "ar"
    var appData by remember { mutableStateOf<AppData?>(null) }
    var activities by remember { mutableStateOf<List<ActivityItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    var selectedActivity by remember { mutableStateOf<ActivityItem?>(null) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val info = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
                val appInfo = info.applicationInfo
                appIcon = pm.getApplicationIcon(packageName)
                if (appInfo != null) {
                    appData = AppData(pm.getApplicationLabel(appInfo).toString(), info.packageName, info.versionName ?: "Unknown", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toString() else info.versionCode.toString(), (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0, info.activities?.size ?: 0)
                    activities = (info.activities ?: emptyArray()).map { ActivityItem(it.name, it.loadLabel(pm).toString(), it.exported) }.sortedBy { it.label.lowercase() }
                }
            } catch (_: Exception) { }
            isLoading = false
        }
    }

    fun launch(name: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_MAIN).apply { component = ComponentName(packageName, name); flags = Intent.FLAG_ACTIVITY_NEW_TASK })
            EventLog.userTriggered("ActivityLauncher", "launch_activity", "$packageName/$name")
        } catch (e: SecurityException) {
            EventLog.userTriggered("ActivityLauncher", "launch_activity_denied", "$packageName/$name")
            Toast.makeText(context, if (isArabic) "تم رفض الإذن" else "Permission denied", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            EventLog.userTriggered("ActivityLauncher", "launch_activity_failed", "$packageName/$name: ${e.message}")
            Toast.makeText(context, e.message ?: if (isArabic) "تعذر التشغيل" else "Unable to launch", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(appData?.label ?: if (isArabic) "تفاصيل التطبيق" else "Component details", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    appData?.let { app ->
                        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (appIcon != null) Image(appIcon!!.toBitmap().asImageBitmap(), null, Modifier.size(62.dp).clip(RoundedCornerShape(17.dp))) else Icon(Icons.Default.Android, null, Modifier.size(52.dp))
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(app.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ObservatoryMetric(if (isArabic) "الأنشطة" else "Activities", app.activityCount.toString(), Modifier.weight(1f))
                                    ObservatoryMetric("v${app.versionName}", app.versionCode, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                item {
                    Column {
                        Text(if (isArabic) "نقاط الدخول" else "Entry points", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(if (isArabic) "اختر مكوّنًا لفحصه أو تشغيله. أسماء الحزم تبقى LTR." else "Inspect or launch a component. Package names remain LTR.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(activities, key = { it.name }) { activity ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedActivity = activity }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(if (activity.isExported) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
                                Icon(if (activity.isExported) Icons.Default.Launch else Icons.Default.Lock, null, tint = if (activity.isExported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(activity.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(activity.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    selectedActivity?.let { act ->
        val app = appData
        if (app != null) {
            AlertDialog(
                onDismissRequest = { selectedActivity = null },
                icon = { Icon(if (act.isExported) Icons.Default.Launch else Icons.Default.Lock, null) },
                title = { Text(act.label) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!act.isExported) Text(if (isArabic) "هذا النشاط غير مُصدّر؛ قد يرفض Android تشغيله مباشرة." else "This activity is not exported; Android may reject a direct launch.", color = MaterialTheme.colorScheme.error)
                        Text(act.name, style = MaterialTheme.typography.bodySmall)
                        Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = { nd.max.ui.component.StudioButton(onClick = { launch(act.name); selectedActivity = null }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (isArabic) "تشغيل" else "Launch") } },
                dismissButton = { nd.max.ui.component.StudioTextButton(onClick = { clipboardManager.setText(AnnotatedString("${app.packageName}/${act.name}")); Toast.makeText(context, if (isArabic) "تم نسخ المكوّن" else "Component copied", Toast.LENGTH_SHORT).show(); selectedActivity = null }) { Text(if (isArabic) "نسخ المكوّن" else "Copy component") } }
            )
        }
    }
}

// --- GLASSMORPHISM COMPONENTS ---

@Composable
fun GlassCard(
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    
    val glassModifier = if (isGlassActive) {
        Modifier
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeBlurStyle(
                    backgroundColor = cardColor.copy(alpha = 0.5f),
                    blurRadius = 24.dp,
                    noiseFactor = 0.1f,
                    colorEffects = emptyList()
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = shape
            )
    } else {
        Modifier.clip(shape)
    }

    val containerColor = if (isGlassActive) Color.Transparent else cardColor

    Card(
        modifier = modifier.then(glassModifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        content()
    }
}

@Composable
fun GlassListItem(
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    modifier: Modifier = Modifier,
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)
    
    val glassModifier = if (isGlassActive) {
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeBlurStyle(
                    backgroundColor = cardColor.copy(alpha = 0.4f),
                    blurRadius = 20.dp,
                    noiseFactor = 0.08f,
                    colorEffects = emptyList()
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = shape
            )
    } else {
        Modifier
    }

    val containerColor = if (isGlassActive) Color.Transparent else cardColor

    ListItem(
        modifier = modifier.then(glassModifier),
        colors = ListItemDefaults.colors(containerColor = containerColor),
        headlineContent = headlineContent,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent
    )
}

// --- UPDATED COMPONENTS WITH THEMING ---

@Composable
fun ActivityStatsDashboard(
    allApps: List<AppData>,
    currentFilter: FilterOption,
    onFilterChange: (FilterOption) -> Unit,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color
) {
    val totalApps = allApps.size
    val systemApps = allApps.count { it.isSystemApp }
    val userApps = allApps.count { !it.isSystemApp }
    val totalActivities = allApps.sumOf { it.activityCount }
    
    val userColor = primaryColor
    val systemColor = if (isGlassActive) Color.White.copy(0.3f) else MaterialTheme.colorScheme.surfaceContainerHighest 

    val userProgress = if (totalApps > 0) userApps.toFloat() / totalApps.toFloat() else 0f
    
    val animatedProgress by animateFloatAsState(
        targetValue = userProgress,
        animationSpec = spring(
            dampingRatio = 0.6f, 
            stiffness = Spring.StiffnessLow
        ),
        label = "WavyProgress"
    )

    GlassCard(
        isGlassActive = isGlassActive,
        hazeState = hazeState,
        cardColor = cardColor,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                val animatedTotal by animateIntAsState(
                    targetValue = totalApps,
                    animationSpec = tween(durationMillis = 700, easing = EaseOutCubic),
                    label = "total_apps_count"
                )
                Text("Apps Installed ($animatedTotal)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)
                
                Row(modifier = Modifier.height(36.dp).clip(RoundedCornerShape(50)).background(if (isGlassActive) Color.White.copy(0.1f) else MaterialTheme.colorScheme.surfaceContainerHigh).border(1.dp, if (isGlassActive) Color.White.copy(0.3f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onFilterChange(if (currentFilter == FilterOption.USER) FilterOption.ALL else FilterOption.USER) }, modifier = Modifier.size(36.dp).background(if (currentFilter == FilterOption.USER) primaryColor else Color.Transparent, CircleShape)) {
                        Icon(Icons.Default.GridView, null, tint = if (currentFilter == FilterOption.USER) Color.White else subTextColor, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onFilterChange(if (currentFilter == FilterOption.SYSTEM) FilterOption.ALL else FilterOption.SYSTEM) }, modifier = Modifier.size(36.dp).background(if (currentFilter == FilterOption.SYSTEM) primaryColor.copy(0.8f) else Color.Transparent, CircleShape)) {
                        Icon(Icons.Default.Android, null, tint = if (currentFilter == FilterOption.SYSTEM) Color.White else subTextColor, modifier = Modifier.size(18.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    // Soft breathing halo behind the ring — echoes the donut's
                    // own color instead of sitting on a flat card background.
                    val haloTransition = rememberInfiniteTransition(label = "donut_halo")
                    val haloAlpha by haloTransition.animateFloat(
                        initialValue = 0.12f, targetValue = 0.28f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "donut_halo_alpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .background(
                                Brush.radialGradient(listOf(userColor.copy(alpha = haloAlpha), Color.Transparent)),
                                CircleShape
                            )
                    )
                    WavyDonutChart(
                        progress = animatedProgress,
                        size = 110.dp,
                        color = userColor,
                        trackColor = systemColor
                    )
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendItem(label = "User Apps", count = userApps, color = userColor, textColor = subTextColor, onClick = { onFilterChange(FilterOption.USER) })
                    LegendItem(label = "System Apps", count = systemApps, color = systemColor, textColor = subTextColor, onClick = { onFilterChange(FilterOption.SYSTEM) })
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardCard(icon = Icons.Default.Apps, label = "All Apps", count = totalApps, textColor = textColor, subTextColor = subTextColor, accentColor = primaryColor, modifier = Modifier.weight(1f), onClick = { onFilterChange(FilterOption.ALL) })
                DashboardCard(icon = Icons.Default.List, label = "Total Activities", count = totalActivities, textColor = textColor, subTextColor = subTextColor, accentColor = primaryColor, modifier = Modifier.weight(1f), onClick = {})
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavyDonutChart(
    progress: Float,
    size: Dp,
    color: Color,
    trackColor: Color
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { 16.dp.toPx() }

    CircularProgressIndicator(
        progress = { progress },
        modifier = Modifier.size(size),
        color = color,
        trackColor = trackColor,
        stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
        trackStroke = Stroke(width = strokeWidthPx), 
        gapSize = 0.dp, 
        amplitude = { 1.5f } 
    )
}

@Composable
fun LegendItem(label: String, count: Int, color: Color, textColor: Color, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onClick)) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color)); Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = textColor); Spacer(modifier = Modifier.weight(1f))
        Text(text = "($count)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = textColor)
    }
}

@Composable
fun DashboardCard(
    icon: ImageVector, 
    label: String, 
    count: Int, 
    textColor: Color,
    subTextColor: Color,
    accentColor: Color = textColor,
    modifier: Modifier = Modifier, 
    onClick: () -> Unit
) {
    // Numbers count up into place instead of just appearing — a small,
    // cheap way to make the dashboard feel alive on first render.
    val animatedCount by animateIntAsState(
        targetValue = count,
        animationSpec = tween(durationMillis = 600, easing = EaseOutCubic),
        label = "dashboard_count"
    )

    Card(
        onClick = onClick, 
        modifier = modifier.height(80.dp), 
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(1.dp, subTextColor.copy(0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.Start) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(15.dp))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = subTextColor)
                Text(animatedCount.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = textColor)
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Shared empty-state used both when a search/filter yields nothing (with a
 * "clear filters" action) and when an app genuinely has no activities to show
 * (no action, just an explanation) — replaces the old plain "No apps found" text.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String?,
    textColor: Color,
    subTextColor: Color,
    accentColor: Color,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(30.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = subTextColor,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null) {
            Spacer(modifier = Modifier.height(16.dp))
            nd.max.ui.component.StudioOutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/** Small pill flagging a non-exported activity, replacing the old bare warning icon. */
@Composable
fun NotExportedBadge(subTextColor: Color, isCustomBg: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isCustomBg) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Warning, contentDescription = "Not exported", tint = subTextColor, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Not exported", style = MaterialTheme.typography.labelSmall, color = subTextColor)
    }
}

@Composable
fun AppListItem(
    app: AppData, 
    onClick: () -> Unit,
    isGlassActive: Boolean,
    hazeState: HazeState,
    cardColor: Color,
    textColor: Color,
    subTextColor: Color,
    primaryColor: Color
) {
    GlassListItem(
        isGlassActive = isGlassActive,
        hazeState = hazeState,
        cardColor = cardColor,
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { 
            Text(
                text = app.label, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis, 
                fontWeight = FontWeight.SemiBold,
                color = textColor
            ) 
        },
        supportingContent = {
            Column {
                Text(
                    text = app.packageName, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = primaryColor, 
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "v${app.versionName}", style = MaterialTheme.typography.labelSmall, color = subTextColor)
                    if (app.isSystemApp) { 
                        Spacer(modifier = Modifier.width(8.dp))
                        SuggestionChip(
                            onClick = {}, 
                            label = { Text("System", style = MaterialTheme.typography.labelSmall) }, 
                            modifier = Modifier.height(24.dp),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = primaryColor.copy(0.2f),
                                labelColor = primaryColor
                            )
                        )
                    }
                }
            }
        },
        leadingContent = { 
            AppIcon(packageName = app.packageName, modifier = Modifier.size(48.dp))
        },
        trailingContent = { 
            Column(horizontalAlignment = Alignment.End) { 
                Text(
                    text = "${app.activityCount}", 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold, 
                    color = primaryColor
                )
                Text(
                    text = "Activities", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = subTextColor
                ) 
            } 
        }
    )
}

@Composable
fun BottomSheetContent(currentSort: SortOption, currentFilter: FilterOption, onSortSelected: (SortOption) -> Unit, onFilterSelected: (FilterOption) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Surface(modifier = Modifier.size(width = 32.dp, height = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.extraLarge) {} }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Display Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortOutlinedButton(text = "Name A-Z", icon = Icons.Default.ArrowDownward, selected = currentSort == SortOption.NAME_ASC, onClick = { onSortSelected(SortOption.NAME_ASC) }, modifier = Modifier.weight(1f))
            SortOutlinedButton(text = "Name Z-A", icon = Icons.Default.ArrowUpward, selected = currentSort == SortOption.NAME_DESC, onClick = { onSortSelected(SortOption.NAME_DESC) }, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterCard(text = "All", selected = currentFilter == FilterOption.ALL, onClick = { onFilterSelected(FilterOption.ALL) }, modifier = Modifier.weight(1f))
            FilterCard(text = "System", selected = currentFilter == FilterOption.SYSTEM, onClick = { onFilterSelected(FilterOption.SYSTEM) }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterCard(text = "User", selected = currentFilter == FilterOption.USER, onClick = { onFilterSelected(FilterOption.USER) }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun SortOutlinedButton(text: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
    val borderColor = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline
    nd.max.ui.component.StudioOutlinedButton(onClick = onClick, modifier = modifier, colors = ButtonDefaults.outlinedButtonColors(containerColor = containerColor, contentColor = contentColor), border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)) { Icon(icon, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(text) }
}

@Composable
fun FilterCard(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Card(modifier = modifier.fillMaxWidth().height(64.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor), shape = MaterialTheme.shapes.medium) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium) } }
}

