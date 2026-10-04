/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.subscreens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import nd.max.R
import nd.max.core.platform.ProcessFeed
import nd.max.core.platform.ProcessReading
import nd.max.core.platform.ProcessSample
import nd.max.core.platform.ProcessScope
import nd.max.core.platform.ProcessSort
import nd.max.core.platform.ProcessWatch
import nd.max.service.ProcessOverlayService
import nd.max.ui.component.ConfirmDialogHost
import nd.max.ui.component.CustomBottomSheet
import nd.max.ui.component.ExpressiveListItem
import nd.max.ui.component.MaxEmptyState
import nd.max.ui.component.MaxErrorState
import nd.max.ui.component.MaxStatusPill
import nd.max.ui.component.ScreenAccentProvider
import nd.max.ui.component.baselineOf
import nd.max.ui.component.rememberConfirmDialog
import nd.max.ui.design.MaxCardSpec
import nd.max.ui.design.MaxChoiceRow
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSearchField
import nd.max.ui.design.MaxSegmented
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.util.ProcessOverlayPrefs
import nd.max.ui.viewmodel.ProcessActionKind
import nd.max.ui.viewmodel.ProcessManagerViewModel
import nd.max.ui.component.ProcessSurface

/**
 * مراقب المهام — العمليات التي تعمل الآن، وما تفعله بالمعالج والذاكرة، وتراكب يعرضها فوق أيّ تطبيق.
 *
 * ### ما أُعيد تصميمه، ولماذا كل تغيير مقصود
 *
 * 1. **الرسوم التوضيحية أُزيلت** (حلقة التوزيع وأشرطة أعلى أربع): كانت تعيد الأرقام نفسها التي
 *    فوقها، وتحتلّ نصف الشاشة قبل أن تصل إلى القائمة التي جاء المستخدم من أجلها. والمقارنة صارت
 *    **داخل الصفّ** (شريط تحت الاسم)، فالسؤال «مَن الأثقل؟» يُقرأ من موضع العملية نفسه.
 * 2. **شرائح التصفية صارت مقطعَي اختيار صريحين**: النطاق (الكل · تطبيقات · نظام) والترتيب
 *    (المعالج · الذاكرة · الاسم). والاسم أُضيف لأنّ العثور على تطبيق في قائمة من خمسين أهمّ من
 *    إعادة ترتيبها.
 * 3. **عدّاد الصفوف صار `− ن +`** لا شريحة تدور على ثلاثة أرقام بلا أن تقول إنّها تدور.
 * 4. **البحث** بالاسم المعروض أو اسم الحزمة.
 * 5. **حالتان مختلفتان لما لا عمليات** ([MaxErrorState] لقراءة فاشلة و[MaxEmptyState] لتصفية بلا
 *    نتيجة): كانت واحدة، فتقرأ «لا توجد عمليات» حين يكون الخبر أنّ القراءة لم تصل.
 * 6. **التراكب صار له إعداد** ([OverlaySheet]): عدد الصفوف، والفاصل، والترتيب، والنطاق، وتمييز
 *    الثقيل، وإظهار العدد، والالتصاق بالحافة — مع **معاينة حيّة** تُرسم بالمُصيِّر نفسه الذي يرسم
 *    فوق اللعبة، فما يُختار يُرى قبل تشغيله.
 * 7. **ونسخ اسم الحزمة** أُضيف إلى ورقة التفاصيل: أوّل ما يحتاجه من يريد ضبط تطبيق بعينه.
 *
 * **وحدّ مُعلَن:** الشاشة لا تقرأ بنفسها — تقرأ من [ProcessFeed] عبر القارئ المشترك، فما تراه هو
 * ما فوق اللعبة (والنافذة تقصّ بالمعالج، فترتيب الذاكرة يقع داخل تلك النافذة).
 */
@Composable
fun ProcessManagerScreen(
    navController: NavController,
    viewModel: ProcessManagerViewModel = viewModel()
) {
    val context = LocalContext.current
    // موارد من `LocalResources.current`: الرسائل تُبنى داخل `launch` ولامبدات الإجراءات.
    val resources = LocalResources.current
    val colorScheme = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf<ProcessReading?>(null) }
    var overlayOptions by remember { mutableStateOf(false) }
    var overlayPrefs by remember { mutableStateOf(ProcessOverlayPrefs.load(context)) }
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var overlayRunning by remember { mutableStateOf(ProcessOverlayService.isRunning) }

    val forceStopDialog = rememberConfirmDialog(
        onConfirm = { selected?.let { viewModel.forceStop(it) }; selected = null },
        onDismiss = {}
    )
    val killDialog = rememberConfirmDialog(
        onConfirm = { selected?.let { viewModel.kill(it) }; selected = null },
        onDismiss = {}
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        canDrawOverlays = Settings.canDrawOverlays(context)
        if (canDrawOverlays) startOverlay(context)
        overlayRunning = ProcessOverlayService.isRunning
    }

    fun requestOverlay() {
        if (Settings.canDrawOverlays(context)) {
            startOverlay(context)
            overlayRunning = true
            return
        }
        scope.launch {
            val answer = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.processmgr_overlay_permission_needed),
                actionLabel = resources.getString(R.string.open_settings)
            )
            if (answer == SnackbarResult.ActionPerformed) {
                permissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.start(context)
        overlayRunning = ProcessOverlayService.isRunning
    }
    // مغادرة الشاشة تُلغي تسجيلها عند القارئ المشترك: لا قراءة لشاشة لا تُرى.
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    LaunchedEffect(viewModel.result) {
        val outcome = viewModel.result ?: return@LaunchedEffect
        val message = when (outcome.kind) {
            ProcessActionKind.Killed -> R.string.processmgr_result_killed
            ProcessActionKind.KillFailed -> R.string.processmgr_result_kill_failed
            ProcessActionKind.Stopped -> R.string.processmgr_result_stopped
            ProcessActionKind.StopFailed -> R.string.processmgr_result_stop_failed
        }
        snackbarHostState.showSnackbar(resources.getString(message, outcome.name))
        viewModel.clearResult()
    }

    ScreenAccentProvider(colorScheme.primary) {
        MaxListScreen(
            title = stringResource(R.string.processmgr_title),
            onBack = { navController.popBackStack() },
            accentIcon = Icons.Filled.Memory,
            accent = colorScheme.primary,
            snackbarHostState = snackbarHostState,
            actions = {
                IconButton(onClick = { overlayOptions = true }) {
                    Icon(
                        imageVector = Icons.Outlined.PictureInPictureAlt,
                        contentDescription = stringResource(R.string.processmgr_overlay_title)
                    )
                }
            }
        ) {
            item {
                ReadingHeader(
                    running = viewModel.tally.running,
                    apps = viewModel.tally.apps,
                    system = viewModel.tally.system,
                    failed = viewModel.sample.failed,
                    overlayRunning = overlayRunning,
                    onToggleOverlay = { if (overlayRunning) stopOverlay(context) else requestOverlay() }
                )
                Spacer(Modifier.height(MaxSpace.md))
            }

            item {
                ControlStrip(
                    scope = viewModel.scope,
                    sort = viewModel.sort,
                    limit = viewModel.limit,
                    onScope = viewModel::chooseScope,
                    onSort = viewModel::chooseSort,
                    onMore = viewModel::nextLimit,
                    onLess = viewModel::previousLimit
                )
                Spacer(Modifier.height(MaxSpace.sm))
                MaxSearchField(
                    value = viewModel.query,
                    onValueChange = viewModel::search,
                    placeholder = stringResource(R.string.processmgr_search_hint)
                )
                Spacer(Modifier.height(MaxSpace.sm))
            }

            item {
                ShownCount(shown = viewModel.visible.size, limit = viewModel.limit)
                Spacer(Modifier.height(MaxSpace.xs))
            }

            when {
                viewModel.sample.failed -> item {
                    MaxErrorState(
                        title = stringResource(R.string.processmgr_no_answer),
                        message = stringResource(R.string.processmgr_no_answer_hint),
                        retryLabel = stringResource(R.string.retry),
                        // الزرّ **يقرأ فعلًا**: يلغي تسجيل الشاشة ويعيده، فتُطلب عيّنة جديدة فورًا
                        // بدل انتظار الحلقة — وهي تجربة مختلفة عن «انتظر ثانيتين».
                        onRetry = { viewModel.restart(context) }
                    )
                }

                viewModel.visible.isEmpty() -> item {
                    MaxEmptyState(
                        title = stringResource(R.string.processmgr_empty_title),
                        message = stringResource(R.string.processmgr_no_processes)
                    )
                }

                else -> items(viewModel.visible, key = { it.pid }) { reading ->
                    ProcessRow(
                        reading = reading,
                        sort = viewModel.sort,
                        baseline = baselineOf(viewModel.visible, viewModel.sort),
                        onClick = { selected = reading }
                    )
                }
            }
        }
    }

    ConfirmDialogHost(handle = forceStopDialog)
    ConfirmDialogHost(handle = killDialog)

    CustomBottomSheet(visible = selected != null, onDismiss = { selected = null }) {
        selected?.let { reading ->
            DetailSheet(
                reading = reading,
                onForceStop = {
                    forceStopDialog.showConfirm(
                        title = resources.getString(R.string.processmgr_force_stop_confirm_title, reading.label),
                        content = resources.getString(R.string.processmgr_force_stop_confirm_desc, reading.label),
                        confirm = resources.getString(R.string.processmgr_action_force_stop),
                        dismiss = resources.getString(R.string.no)
                    )
                },
                onKill = {
                    killDialog.showConfirm(
                        title = resources.getString(R.string.processmgr_kill_confirm_title),
                        content = resources.getString(R.string.processmgr_kill_confirm_desc, reading.pid.toString()),
                        confirm = resources.getString(R.string.processmgr_action_kill),
                        dismiss = resources.getString(R.string.no)
                    )
                },
                onAppInfo = { openAppSettings(context, reading.packageName) },
                onCopy = {
                    copyText(context, reading.packageName)
                    selected = null
                },
                onClose = { selected = null }
            )
        }
    }

    CustomBottomSheet(visible = overlayOptions, onDismiss = { overlayOptions = false }) {
        OverlaySheet(
            prefs = overlayPrefs,
            running = overlayRunning,
            onPrefs = { updated ->
                overlayPrefs = updated
                ProcessOverlayPrefs.save(context, updated)
            },
            onToggle = {
                if (overlayRunning) stopOverlay(context) else requestOverlay()
                overlayRunning = ProcessOverlayService.isRunning
            }
        )
    }
}

private fun startOverlay(context: Context) {
    val intent = Intent(context, ProcessOverlayService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopOverlay(context: Context) {
    context.stopService(Intent(context, ProcessOverlayService::class.java))
}

private fun openAppSettings(context: Context, packageName: String) {
    nd.max.ui.util.DebloatFreezeUtil.openAppSystemSettings(context, packageName)
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
}

/**
 * رأس الشاشة: ما قرأه الجهاز الآن، وحالة القراءة نفسها، وزرّ التراكب.
 *
 * وحالة القراءة **ليست زينة**: شاشة تُظهر جدولًا قد تكون كلّه من آخر عيّنة نجحت، فمن يرى
 * «قراءة حيّة» يعرف أنّه ينظر إلى اللحظة، ومن يرى غيره يعرف أنّه ينظر إلى محفوظ.
 */
@Composable
private fun ReadingHeader(
    running: Int,
    apps: Int,
    system: Int,
    failed: Boolean,
    overlayRunning: Boolean,
    onToggleOverlay: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxCardSpec.radius),
        color = colors.surfaceContainerLow,
        tonalElevation = MaxCardSpec.borderWidth
    ) {
        Column(modifier = Modifier.padding(MaxCardSpec.padding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.process_manager_overview_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(MaxSpace.hairline))
                    Text(
                        text = stringResource(R.string.process_manager_overview_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                MaxStatusPill(
                    text = stringResource(
                        if (failed) R.string.processmgr_reading_failed else R.string.processmgr_reading_live
                    ),
                    active = !failed
                )
            }
            Spacer(Modifier.height(MaxSpace.lg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                Metric(
                    label = stringResource(R.string.process_manager_metric_running),
                    value = running.toString(),
                    modifier = Modifier.weight(1f)
                )
                Metric(
                    label = stringResource(R.string.process_manager_metric_apps),
                    value = apps.toString(),
                    modifier = Modifier.weight(1f)
                )
                Metric(
                    label = stringResource(R.string.process_manager_metric_system),
                    value = system.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(MaxSpace.md))
            MaxSwitchRow(
                title = stringResource(R.string.processmgr_overlay_title),
                checked = overlayRunning,
                onCheckedChange = { onToggleOverlay() },
                icon = Icons.Outlined.PictureInPictureAlt
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = RoundedCornerShape(MaxRadius.row), color = colors.surfaceContainer) {
        Column(Modifier.padding(MaxSpace.md)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(MaxSpace.hairline))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * شريط التحكّم: النطاق، والترتيب، وعدد الصفوف.
 *
 * والعدّاد `− ن +` بدل شريحة تدور: الشريحة كانت تقول `Top 10` ولا تقول إنّ لمسها ينقل إلى 20 —
 * فالاختيار الثلاثي كان مخفيًّا في عنصر يشبه عرضًا للقيمة.
 */
@Composable
private fun ControlStrip(
    scope: ProcessScope,
    sort: ProcessSort,
    limit: Int,
    onScope: (ProcessScope) -> Unit,
    onSort: (ProcessSort) -> Unit,
    onMore: () -> Unit,
    onLess: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val scopeOptions = listOf(
        stringResource(R.string.processmgr_scope_all),
        stringResource(R.string.process_manager_metric_apps),
        stringResource(R.string.process_manager_metric_system)
    )
    val sortOptions = listOf(
        stringResource(R.string.processmgr_sort_cpu),
        stringResource(R.string.processmgr_sort_ram),
        stringResource(R.string.processmgr_sort_name)
    )

    Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
        MaxSegmented(
            options = scopeOptions,
            selectedIndex = ProcessScope.entries.indexOf(scope),
            onSelect = { index -> ProcessScope.entries.getOrNull(index)?.let(onScope) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)
        ) {
            MaxSegmented(
                options = sortOptions,
                selectedIndex = ProcessSort.entries.indexOf(sort),
                onSelect = { index -> ProcessSort.entries.getOrNull(index)?.let(onSort) },
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(MaxRadius.pill),
                color = colors.surfaceContainer
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLess) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = stringResource(R.string.processmgr_limit_less)
                        )
                    }
                    Text(
                        text = stringResource(R.string.processmgr_limit_format, limit),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onMore) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.processmgr_limit_more)
                        )
                    }
                }
            }
        }
    }
}

/** «يُعرض ٢٠ من ٣٤٢» — الفرق الذي كان يُقرأ خطأً حين كانت القائمة تُعدّ نفسها. */
@Composable
private fun ShownCount(shown: Int, limit: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaxSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.process_manager_running_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.process_manager_running_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(R.string.processmgr_shown_count, shown, limit),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ProcessRow(
    reading: ProcessReading,
    sort: ProcessSort,
    baseline: Float,
    onClick: () -> Unit
) {
    ExpressiveListItem(
        onClick = onClick,
        leadingContent = { AppIcon(reading) },
        headlineContent = {
            Text(reading.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text(reading.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (sort == ProcessSort.Memory) reading.residentText else reading.cpuText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                // الرقم الثاني دائمًا ظاهر: مَن يُرتّب بالمعالج يرى حجم الذاكرة في السطر نفسه،
                // فلا يحتاج تغيير الترتيب ليعرف الاثنين.
                Text(
                    text = if (sort == ProcessSort.Memory) reading.cpuText else reading.residentText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = stringResource(R.string.processmgr_pid_format, reading.pid.toString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    )
    // الشريط تحت الصفّ: المقارنة في موضع العملية لا في نصف شاشة منفصل.
    Box(Modifier.padding(horizontal = MaxSpace.gutter)) {
        nd.max.ui.design.MaxUsageBar(
            fraction = if (baseline > 0f) {
                (if (sort == ProcessSort.Memory) reading.residentKb.toFloat() else reading.cpuPercent) / baseline
            } else {
                0f
            }
        )
    }
}

/** أيقونة الحزمة — تُطلب مرّة لكل حزمة؛ وثنائيّ نظاميّ بلا حزمة يأخذ رمز الذاكرة. */
@Composable
private fun AppIcon(reading: ProcessReading) {
    val context = LocalContext.current
    val bitmap = remember(reading.packageName) {
        if (!reading.packageName.contains('.')) return@remember null
        val drawable = runCatching {
            context.packageManager.getApplicationIcon(reading.packageName)
        }.getOrNull() ?: return@remember null
        val size = ICON_PIXELS
        val image = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(image)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        image.asImageBitmap()
    }
    Surface(
        shape = RoundedCornerShape(MaxRadius.row),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.size(MaxCardSpec.iconContainer)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = reading.label, modifier = Modifier.size(MaxSpace.xxl))
            } else {
                Icon(
                    imageVector = Icons.Rounded.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** أيقونة الرسم تُبنى بكثافة ثابتة ثم تُصغَّر بالعرض — فالصورة واحدة على كل الشاشات. */
private const val ICON_PIXELS = 96

@Composable
private fun DetailSheet(
    reading: ProcessReading,
    onForceStop: () -> Unit,
    onKill: () -> Unit,
    onAppInfo: () -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaxSpace.gutter, vertical = MaxSpace.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(reading)
        Spacer(Modifier.height(MaxSpace.md))
        Text(reading.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(reading.packageName, style = MaterialTheme.typography.bodyMedium, color = colors.primary)
        Spacer(Modifier.height(MaxSpace.lg))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            DetailBadge(stringResource(R.string.cpu), reading.cpuText)
            DetailBadge(stringResource(R.string.ram), reading.residentText)
            DetailBadge(stringResource(R.string.pid), reading.pid.toString())
        }
        Spacer(Modifier.height(MaxSpace.lg))

        MaxChoiceRow(
            title = stringResource(R.string.processmgr_action_app_info),
            subtitle = null,
            selected = false,
            onSelect = onAppInfo,
            modifier = Modifier.fillMaxWidth()
        )
        MaxChoiceRow(
            title = stringResource(R.string.processmgr_copy_package),
            subtitle = reading.packageName,
            selected = false,
            onSelect = onCopy,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(MaxSpace.md))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)) {
            nd.max.ui.component.StudioTonalButton(
                onClick = onForceStop,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(MaxRadius.control)
            ) {
                Text(stringResource(R.string.processmgr_action_force_stop))
            }
            nd.max.ui.component.StudioButton(
                onClick = onKill,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(MaxRadius.control)
            ) {
                Text(stringResource(R.string.processmgr_action_kill))
            }
        }
        nd.max.ui.component.StudioTextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.processmgr_dialog_close))
        }
        Spacer(Modifier.height(MaxSpace.sm))
    }
}

@Composable
private fun DetailBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * ورقة إعداد التراكب — وفيها **معاينة حيّة** تُرسم بـ[ProcessSurface] نفسها التي ترسم النافذة.
 *
 * والمعاينة هي الفرق العملي في هذه الورقة: بقية الخيارات (عدد صفوف، فاصل) يمكن وصفها بالكلام،
 * أمّا «كيف ستبدو النافذة» فوصفها بالكلام كان يعني الخروج إلى لعبة لتجربتها.
 */
@Composable
private fun OverlaySheet(
    prefs: ProcessOverlayPrefs.State,
    running: Boolean,
    onPrefs: (ProcessOverlayPrefs.State) -> Unit,
    onToggle: () -> Unit
) {
    val sample = ProcessWatchSnapshot()
    val shown = remember(sample, prefs) {
        ProcessFeed.arrange(sample.readings, prefs.scope, "", prefs.sort, prefs.rows)
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaxSpace.gutter),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)
    ) {
        item {
            MaxSwitchRow(
                title = stringResource(R.string.processmgr_overlay_title),
                checked = running,
                onCheckedChange = { onToggle() },
                icon = Icons.Outlined.PictureInPictureAlt
            )
        }
        item {
            OptionBlock(
                label = stringResource(R.string.processmgr_overlay_rows),
                options = ProcessOverlayPrefs.ROW_CHOICES.map { it.toString() },
                selectedIndex = ProcessOverlayPrefs.ROW_CHOICES.indexOf(prefs.rows),
                onSelect = { index ->
                    ProcessOverlayPrefs.ROW_CHOICES.getOrNull(index)?.let { onPrefs(prefs.copy(rows = it)) }
                }
            )
        }
        item {
            OptionBlock(
                label = stringResource(R.string.processmgr_overlay_interval, prefs.intervalSeconds),
                options = ProcessOverlayPrefs.INTERVAL_CHOICES.map { it.toString() },
                selectedIndex = ProcessOverlayPrefs.INTERVAL_CHOICES.indexOf(prefs.intervalSeconds),
                onSelect = { index ->
                    ProcessOverlayPrefs.INTERVAL_CHOICES.getOrNull(index)
                        ?.let { onPrefs(prefs.copy(intervalSeconds = it)) }
                }
            )
        }
        item {
            OptionBlock(
                label = stringResource(R.string.processmgr_overlay_sort),
                options = listOf(
                    stringResource(R.string.processmgr_sort_cpu),
                    stringResource(R.string.processmgr_sort_ram)
                ),
                selectedIndex = if (prefs.sort == ProcessSort.Memory) 1 else 0,
                onSelect = { index ->
                    onPrefs(prefs.copy(sort = if (index == 1) ProcessSort.Memory else ProcessSort.Cpu))
                }
            )
        }
        item {
            OptionBlock(
                label = stringResource(R.string.processmgr_overlay_scope),
                options = listOf(
                    stringResource(R.string.processmgr_scope_all),
                    stringResource(R.string.process_manager_metric_apps),
                    stringResource(R.string.process_manager_metric_system)
                ),
                selectedIndex = ProcessScope.entries.indexOf(prefs.scope),
                onSelect = { index ->
                    ProcessScope.entries.getOrNull(index)?.let { onPrefs(prefs.copy(scope = it)) }
                }
            )
        }
        item {
            MaxSwitchRow(
                title = stringResource(R.string.processmgr_overlay_heavy),
                checked = prefs.markHeavy,
                onCheckedChange = { onPrefs(prefs.copy(markHeavy = it)) }
            )
        }
        item {
            MaxSwitchRow(
                title = stringResource(R.string.processmgr_overlay_tally),
                checked = prefs.showTally,
                onCheckedChange = { onPrefs(prefs.copy(showTally = it)) }
            )
        }
        item {
            MaxSwitchRow(
                title = stringResource(R.string.processmgr_overlay_snap),
                checked = prefs.snapEdges,
                onCheckedChange = { onPrefs(prefs.copy(snapEdges = it)) }
            )
        }
        item {
            Spacer(Modifier.height(MaxSpace.sm))
            Text(
                text = stringResource(R.string.processmgr_overlay_preview),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(MaxSpace.sm))
            ProcessSurface(
                sample = sample.copy(readings = shown),
                sort = prefs.sort,
                baseline = baselineOf(shown, prefs.sort),
                textSizeSp = prefs.textSizeSp,
                backgroundAlpha = prefs.backgroundAlpha,
                markHeavy = prefs.markHeavy,
                showTally = prefs.showTally
            )
            Spacer(Modifier.height(MaxSpace.lg))
        }
    }
}

/** خيار بثلاثة أو أربعة وجوه: التسمية فوق المقطع، والمختار بموضعه لا بشريحة تدور. */
@Composable
private fun OptionBlock(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MaxSegmented(options = options, selectedIndex = selectedIndex, onSelect = onSelect)
    }
}

/** آخر عيّنة من القارئ المشترك — قراءة واحدة يعرضها كل من يسأل. */
@Composable
private fun ProcessWatchSnapshot(): ProcessSample {
    val snapshot by ProcessWatch.snapshot.collectAsState()
    return snapshot
}
