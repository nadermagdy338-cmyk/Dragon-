/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.mainscreens

import nd.max.MaxManagerProps


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.AddHome
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.topjohnwu.superuser.Shell
import kotlin.system.exitProcess
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nd.max.R
import nd.max.core.privilege.PrivilegeLevel
import nd.max.core.privilege.PrivilegeManager
import nd.max.ui.component.ExpressiveList
import nd.max.ui.component.PrivilegePanel
import nd.max.ui.component.ExpressiveSwitchItem
import nd.max.ui.component.ScreenAccentGlyph
import nd.max.ui.component.ScreenAccentProvider
import nd.max.ui.util.PropertyUtils
import nd.max.ui.util.RootUtils

/**
 * Background dinamis yang posisinya pindah dengan mulus antar halaman.
 * Berada di layer terbawah agar tidak terpotong saat transisi AnimatedContent.
 */
@Composable
private fun ExpressiveSystemBackground(
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    val targetOffsetA = when (currentPage) {
        0 -> Offset(0.2f, 0.2f)
        1 -> Offset(0.8f, 0.1f)
        2 -> Offset(0.1f, 0.8f)
        3 -> Offset(0.5f, 0.5f)
        else -> Offset(0.5f, 0.5f)
    }

    val targetOffsetB = when (currentPage) {
        0 -> Offset(0.8f, 0.8f)
        1 -> Offset(0.2f, 0.7f)
        2 -> Offset(0.9f, 0.2f)
        3 -> Offset(0.5f, 0.5f)
        else -> Offset(0.5f, 0.5f)
    }

    val animSpec = spring<Offset>(dampingRatio = 0.8f, stiffness = 50f)
    val posA by animateOffsetAsState(targetValue = targetOffsetA, animationSpec = animSpec, label = "posA")
    val posB by animateOffsetAsState(targetValue = targetOffsetB, animationSpec = animSpec, label = "posB")

    val targetScale = if (currentPage == 3) 1.5f else 0.6f
    val scaleAnim by animateFloatAsState(targetValue = targetScale, animationSpec = spring(dampingRatio = 0.8f, stiffness = 50f), label = "scale")

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surface

    Canvas(modifier = modifier.background(surface)) {
        val w = size.width
        val h = size.height

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.2f), Color.Transparent),
                center = Offset(w * posA.x, h * posA.y),
                radius = w * scaleAnim
            ),
            radius = w * scaleAnim,
            center = Offset(w * posA.x, h * posA.y)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tertiary.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(w * posB.x, h * posB.y),
                radius = w * scaleAnim
            ),
            radius = w * scaleAnim,
            center = Offset(w * posB.x, h * posB.y)
        )
    }
}

/**
 * Draw-on status glyph untuk root check dan status selesai.
 */
@Composable
private fun StatusGlyph(
    success: Boolean,
    modifier: Modifier = Modifier,
    strokeWidthDp: Dp = 3.dp
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(success) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(550, easing = FastOutSlowInEasing))
    }

    val color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Canvas(modifier = modifier) {
        val strokePx = strokeWidthDp.toPx()
        val radius = size.minDimension / 2f - strokePx
        val center = Offset(size.width / 2f, size.height / 2f)

        val ringProgress = (progress.value / 0.5f).coerceIn(0f, 1f)
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * ringProgress,
            useCenter = false,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f)
        )

        val glyphProgress = ((progress.value - 0.5f) / 0.5f).coerceIn(0f, 1f)
        if (glyphProgress > 0f) {
            if (success) {
                val p1 = Offset(center.x - radius * 0.45f, center.y + radius * 0.05f)
                val p2 = Offset(center.x - radius * 0.08f, center.y + radius * 0.4f)
                val p3 = Offset(center.x + radius * 0.5f, center.y - radius * 0.35f)
                val path = Path().apply {
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val outPath = Path()
                measure.getSegment(0f, measure.length * glyphProgress, outPath, true)
                drawPath(
                    path = outPath,
                    color = color,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            } else {
                val inset = radius * 0.42f
                val a1 = Offset(center.x - inset, center.y - inset)
                val a2 = Offset(center.x + inset, center.y + inset)
                val b1 = Offset(center.x + inset, center.y - inset)
                val b2 = Offset(center.x - inset, center.y + inset)
                if (glyphProgress <= 0.5f) {
                    val t = (glyphProgress / 0.5f).coerceIn(0f, 1f)
                    drawLine(
                        color = color,
                        start = a1,
                        end = Offset(a1.x + (a2.x - a1.x) * t, a1.y + (a2.y - a1.y) * t),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                } else {
                    drawLine(color = color, start = a1, end = a2, strokeWidth = strokePx, cap = StrokeCap.Round)
                    val t = ((glyphProgress - 0.5f) / 0.5f).coerceIn(0f, 1f)
                    drawLine(
                        color = color,
                        start = b1,
                        end = Offset(b1.x + (b2.x - b1.x) * t, b1.y + (b2.y - b1.y) * t),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
fun GetStartedScreen(navController: NavController) {
    var currentPage by remember { mutableIntStateOf(0) }
    var rootAccessGranted by remember { mutableStateOf<Boolean?>(null) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    var stateToast by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // قراءة داخل العملية: لا صدفة على مسار أول تشغيل (شاشة البداية).
        val toastOut = PropertyUtils.get(MaxManagerProps.Conf.SHOW_TOAST)
        if (toastOut == "0") stateToast = false
    }

    var isFinalizing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val totalPages = 4

    // صفحة الامتياز الموحّدة (الجذر + Shizuku): تُقرأ هنا لتحديد إمكانية التقدّم.
    val privilegeSnapshot by PrivilegeManager.snapshot.collectAsState()

    val canGoNext = when (currentPage) {
        // مستخدم الشيزوكو بلا جذر له الحق في إكمال التثبيت، فلا تُحبَسه بوابتنا
        // الجذرية وحدها — والقراءة سلبية فلا تُفتح نافذة صلاحية لمجرّد التقدّم.
        1 -> rootAccessGranted == true || privilegeSnapshot.level != PrivilegeLevel.NONE
        else -> true
    }

    // الكشف تلقائي: عند الوصول إلى صفحة الامتياز يُطلب الجذر مرّة (طلب واحد صريح لا
    // يتكرّر)، ثم تُحدَّث اللوحة من المدير نفسه — فلا يحتاج المستخدم إلى زرّ ليعرف وضعه.
    LaunchedEffect(currentPage) {
        if (currentPage == 1 && rootAccessGranted == null) {
            delay(500)
            rootAccessGranted = RootUtils.requestRootAccess()
            PrivilegeManager.refresh()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, currentPage) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentPage == 1 && rootAccessGranted != true) {
                coroutineScope.launch {
                    delay(300)
                    rootAccessGranted = RootUtils.requestRootAccess()
                    PrivilegeManager.refresh()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (isFinalizing) 0f else 1f,
        animationSpec = tween(300),
        label = "contentAlpha"
    )

    Scaffold(
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 24.dp)) {
                AnimatedVisibility(
                    visible = !isFinalizing,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(300)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.padding(bottom = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(totalPages) { index ->
                                val isSelected = index == currentPage
                                val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                val width by animateDpAsState(targetValue = if (isSelected) 24.dp else 8.dp, label = "indicatorWidth")

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .height(8.dp)
                                        .width(width)
                                        .background(color, CircleShape)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (currentPage > 0) {
                                nd.max.ui.component.StudioButton(
                                    onClick = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (currentPage > 0) currentPage-- 
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                ) {
                                    Text(stringResource(R.string.cd_back), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }

                            nd.max.ui.component.StudioButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (currentPage < totalPages - 1) {
                                        currentPage++
                                    } else {
                                        isFinalizing = true
                                        coroutineScope.launch {
                                            RootUtils.isModuleInstalled()
                                            delay(2000)

                                            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                            prefs.edit().putBoolean("has_completed_get_started", true).commit()

                                            Shell.cmd("su -c pm grant nd.max android.permission.READ_EXTERNAL_STORAGE && su -c pm grant nd.max android.permission.POST_NOTIFICATIONS && su -c pm grant nd.max android.permission.READ_MEDIA_IMAGES && su -c am start -S -n nd.max/.MainActivity").exec()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (canGoNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (canGoNext) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            ) {
                                Text(
                                    text = if (currentPage == totalPages - 1) stringResource(R.string.lets_go) else stringResource(R.string.next),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            
            ExpressiveSystemBackground(
                currentPage = currentPage,
                modifier = Modifier.fillMaxSize()
            )

            AnimatedContent(
                targetState = currentPage,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = 20.dp)
                    .alpha(contentAlpha),
                transitionSpec = {
                    val motion = spring<Float>(dampingRatio = 0.82f, stiffness = 260f)
                    val contentTransform =
                        (fadeIn(tween(260)) + scaleIn(initialScale = 0.975f, animationSpec = motion)) togetherWith
                            (fadeOut(tween(160)) + scaleOut(targetScale = 0.99f, animationSpec = tween(160)))
                    contentTransform.using(SizeTransform(clip = false))
                },
                label = "GetStartedNeutralAnimation"
            ) { page ->
                
                val enterTransition = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    enterTransition.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 150f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (page) {
                        0 -> {
                            Text(
                                text = stringResource(R.string.str_welcome_to).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 4.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier.graphicsLayer {
                                    alpha = enterTransition.value
                                    translationY = 50f * (1f - enterTransition.value)
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.graphicsLayer {
                                    val titleProgress = (enterTransition.value - 0.2f).coerceAtLeast(0f) / 0.8f
                                    alpha = titleProgress
                                    translationY = 60f * (1f - titleProgress)
                                    scaleX = 0.9f + (0.1f * titleProgress)
                                    scaleY = 0.9f + (0.1f * titleProgress)
                                }
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            val underlineColor = MaterialTheme.colorScheme.primary
                            Canvas(
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(3.dp)
                                    .graphicsLayer {
                                        val lineProgress = (enterTransition.value - 0.4f).coerceAtLeast(0f) / 0.6f
                                        alpha = lineProgress
                                        scaleX = lineProgress
                                    }
                            ) {
                                drawLine(
                                    color = underlineColor,
                                    start = Offset(0f, size.height / 2f),
                                    end = Offset(size.width, size.height / 2f),
                                    strokeWidth = size.height,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                        1 -> {
                            // الصفحتان كانتا منفصلتين (طلب جذر · لوحة امتياز)، ووُحّدتا في
                            // صفحة واحدة: الجذر وShizuku في السطح نفسه، والفحص تلقائي عند
                            // الفتح مع أزرار طلب صريحة لمن أراد أن يطلب بنفسه.
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                ScreenAccentGlyph(
                                    icon = Icons.Rounded.Shield,
                                    accent = MaterialTheme.colorScheme.tertiary,
                                    size = 44.dp,
                                    modifier = Modifier.graphicsLayer {
                                        alpha = enterTransition.value
                                        translationY = 30f * (1f - enterTransition.value)
                                    }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.str_let_s_grant_maxmanager_a_root_acc),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.graphicsLayer {
                                        alpha = enterTransition.value
                                        translationY = 40f * (1f - enterTransition.value)
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.str_privilege_intro),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(20.dp))

                                PrivilegePanel()
                            }
                        }
                        2 -> {
                            ScreenAccentGlyph(
                                icon = Icons.Rounded.Tune,
                                accent = MaterialTheme.colorScheme.secondary,
                                size = 44.dp,
                                modifier = Modifier.graphicsLayer {
                                    alpha = enterTransition.value
                                    translationY = 30f * (1f - enterTransition.value)
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.str_let_s_adjust_a_few_basic_setti),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.graphicsLayer {
                                    alpha = enterTransition.value
                                    translationY = 40f * (1f - enterTransition.value)
                                }
                            )
                            Spacer(modifier = Modifier.height(32.dp))

                            Box(modifier = Modifier.graphicsLayer {
                                val listProgress = (enterTransition.value - 0.2f).coerceAtLeast(0f) / 0.8f
                                alpha = listProgress
                                translationY = 50f * (1f - listProgress)
                            }) {
                                ScreenAccentProvider(MaterialTheme.colorScheme.secondary) {
                                ExpressiveList(
                                    content = listOf(
                                        // ولا مفتاح إخفاء للأيقونة: الأيقونة تبقى ظاهرة دائمًا —
                                        // تطبيق الوحدة هو الطريق إلى مدير الروت بعد التفليش، وإخفاؤه
                                        // يجعل من أراد منحه الإذن يرى «تطبيقي لا يظهر».
                                        // (التفصيل في تحويل الأيقونة إلى alias مُفعَّل دائمًا.)
                                        {
                                            ExpressiveSwitchItem(
                                                icon = Icons.Filled.Notifications,
                                                title = stringResource(R.string.show_toast),
                                                checked = stateToast,
                                                onCheckedChange = { isChecked ->
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    stateToast = isChecked
                                                    Shell.cmd("su -c setprop ${MaxManagerProps.Conf.SHOW_TOAST} ${if (isChecked) "1" else "0"}").submit()
                                                }
                                            )
                                        }
                                        // مفتاح Max AI لا يظهر هنا — التثبيت
                                        // الجديد يتركه مطفأ (تحكم يدوي)،
                                        // وتفعيله قرار المستخدم من شاشة Max AI.
                                    )
                                )
                                }
                            }
                        }
                        3 -> {
                            StatusGlyph(
                                success = true,
                                modifier = Modifier
                                    .size(100.dp)
                                    .graphicsLayer {
                                        alpha = enterTransition.value
                                        scaleX = 0.5f + (0.5f * enterTransition.value)
                                        scaleY = 0.5f + (0.5f * enterTransition.value)
                                    },
                                strokeWidthDp = 6.dp
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            Text(
                                text = stringResource(R.string.str_you_re_all_set),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.graphicsLayer {
                                    val textProgress = (enterTransition.value - 0.2f).coerceAtLeast(0f) / 0.8f
                                    alpha = textProgress
                                    translationY = 40f * (1f - textProgress)
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.str_maxmanager_is_now_ready_to_go),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.graphicsLayer {
                                    val subTextProgress = (enterTransition.value - 0.4f).coerceAtLeast(0f) / 0.6f
                                    alpha = subTextProgress
                                    translationY = 30f * (1f - subTextProgress)
                                }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isFinalizing,
                enter = fadeIn(tween(500, delayMillis = 200)) + scaleIn(initialScale = 0.9f),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            stringResource(R.string.setting_up),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
