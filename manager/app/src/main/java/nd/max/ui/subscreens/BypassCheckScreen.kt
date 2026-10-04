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
 
package nd.max.ui.subscreens

import nd.max.ui.design.MaxCardSpec
import nd.max.ui.design.MaxRadius
import nd.max.MaxManagerProps
import nd.max.MaxManagerPaths


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.fox2code.androidansi.ktx.parseAsAnsiAnnotatedString
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.core.daemon.DaemonStarter
import nd.max.core.hardware.RootFileAccess
import nd.max.ui.component.*
import nd.max.ui.design.MaxListScreen
import nd.max.core.platform.PropertyUtils
import nd.max.ui.util.RootUtils


data class ShellOutput(
    val text: String,
    val isCompleted: Boolean = false
)

@Composable
fun BypassChargeCheckScreen(navController: NavController) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    // موارد من `LocalResources.current`: نصوص هذه الشاشة تُقرأ داخل `scope.launch`/`withContext`
    // ولامبدات `onClick` — وهي سياقات لا تُبطل فيها قراءة `LocalContext.current.resources`.
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()


    val confirmDialogHandle = rememberConfirmDialog()


    var activePath by remember { mutableStateOf("") }
    var availablePaths by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isChargerConnected by remember { mutableStateOf(false) }
    

    val logs = remember { mutableStateListOf<ShellOutput>() }
    var isRunning by remember { mutableStateOf(false) }
    var hasRunDiagnosis by remember { mutableStateOf(false) }
    var isConsoleClosed by remember { mutableStateOf(false) }

    // ── الخادم المتوقّف: حالتُه مستقلّة عن نتيجة الفحص ─────────────────
    // و`-cbc` يقف **بعد** بوّابة `require_daemon_running()` في `Main.c` (عقد `DaemonCliContract`
    // يعدّ الأعلام قبلها وبعدها)، فخادم متوقّف يعني أنّ الفحص **لم يُنفَذ** لا أنّه فحص سلبيّ. وكانت الشاشة
    // تُلقي رسالة الخادم في الكونسول ثمّ تُبقي «لا عقد» كأنّها حكم على العتاد — بلا طريق للتشغيل.
    var isDaemonDown by remember { mutableStateOf(false) }
    var isStartingDaemon by remember { mutableStateOf(false) }
    

    val logScrollState = rememberScrollState()


    fun refreshBypassData(onComplete: ((List<Pair<String, String>>) -> Unit)? = null) {
        activePath = PropertyUtils.get(MaxManagerProps.Conf.BYPASS_PATH, "UNSUPPORTED")
        
        scope.launch(Dispatchers.IO) {
            val binary = MaxManagerPaths.SERVICE_BIN
            val result = Shell.cmd("$binary -bpl 2>&1").exec()
            val output = result.out
            val ansi = Regex("\u001B\\[[;\\d]*m")
            val parsedList = output.map { it.replace(ansi, "") }
                .mapNotNull { line ->
                    val clean = line.trim()
                    if (!clean.contains("[FOUND]", ignoreCase = true)) return@mapNotNull null
                    val parts = clean.split("|")
                    if (parts.size < 3) return@mapNotNull null
                    val name = parts[0].replace("[FOUND]", "", ignoreCase = true).trim()
                    val path = parts.drop(2).joinToString("|").trim()
                    if (path.isBlank()) null else Pair(name.ifBlank { resources.getString(R.string.status_unknown) }, path)
                }
                .distinctBy { it.second }
            withContext(Dispatchers.Main) {
                availablePaths = parsedList
                onComplete?.invoke(parsedList)
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshBypassData()
    }


    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                isChargerConnected = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                                     status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }


    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logScrollState.animateScrollTo(logScrollState.maxValue)
        }
    }


    val blockParentScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return available
            }
        }
    }


    /**
     * هل الخادم حيّ؟ نفس ما تقيسه `RootUtils.getServiceStatusRes` — `pidof` وسطرُه من `Main.c`.
     *
     * **وموضعها قبل [runCompatibilityCheck] شرط ترجمة لا ترتيب جماليّ:** دوال Kotlin المحلّية
     * تُعرَف من **نقطة تعريفها لا قبلها**، واستدعاؤها قبل ذلك يرفضه المُصرّف
     * (`unresolved reference`) — وهو عطب الترجمة الذي أُصلح هنا.
     */
    fun daemonAlive(): Boolean = RootUtils.getServiceStatusRes().first == R.string.status_alive

    fun runCompatibilityCheck() {
        logs.clear()
        isRunning = true
        hasRunDiagnosis = false
        isConsoleClosed = false
    
        scope.launch(Dispatchers.IO) {
            val callbackList = object : CallbackList<String>() {
                override fun onAddElement(line: String) {
                    scope.launch(Dispatchers.Main.immediate) {
                        logs.add(ShellOutput(line, true))
                    }
                }
            }
    
            // المسار من `MaxManagerPaths` لا مكتوبًا هنا ثانيةً: مركزيّ منذ `SERVICE_BIN`،
            // ونسخة ثانية منه تنحرف بأوّل تغيير للمسار.
            Shell.cmd("${MaxManagerPaths.SERVICE_BIN} -cbc 2>&1").to(callbackList).submit { result ->
                isRunning = false
                hasRunDiagnosis = true
    
                val successRegex = "Found working node:\\s*(\\S+)".toRegex()
                val cleanLogs = logs.map { it.text.replace("\u001B\\[[;\\d]*m".toRegex(), "") }
                val successNode = cleanLogs.firstNotNullOfOrNull { successRegex.find(it)?.groupValues?.get(1) }

                refreshBypassData { }

                // وهل الخادم حيّ؟ يُقاس بـ`pidof` (ما يقيسه `RootUtils`) لا بنصّ خطأ،
                // فيُعرض التشغيل في مكانه بدل أن يُقرأ الحاجز كأنّه نتيجة.
                scope.launch {
                    val alive = withContext(Dispatchers.IO) { daemonAlive() }
                    isDaemonDown = !alive
                }

                if (successNode != null) {
                    scope.launch {
                        val dialogResult = confirmDialogHandle.awaitConfirm(
                            title = resources.getString(R.string.dialog_diagnosis_complete_title),
                            content = resources.getString(R.string.dialog_diagnosis_complete_content, successNode),
                            confirm = resources.getString(R.string.dialog_apply),
                            dismiss = resources.getString(R.string.dialog_dismiss)
                        )
                        if (dialogResult == ConfirmResult.Confirmed) {
                            PropertyUtils.set(MaxManagerProps.Conf.BYPASS_PATH, successNode)
                            withContext(Dispatchers.IO) {
                                RootFileAccess.write(
                                    "/data/adb/.config/MaxManager/bypasschgconfig/bypasspath",
                                    successNode
                                )
                            }
                            activePath = successNode
                        }
                    }
                }
            }
        }
    }

    /**
     * تشغيل الخادم ثم إعادة الفحص — **بنفس الوظيفة الخلفية** لا بمسار ثانٍ يخالفها.
     *
     * كان هنا `--rerun`، وهو ليس "تشغيلًا" بل سلسلة قتل وإعادة: يمرّ بـ
     * `sys.maxmanager-utilityconf restartservice` (`binutils/src/utils/mod.rs:255`) فينفّذ
     * `pkill -9 -f sys.maxmanager-appmonitoring` — **أي يقتل الرفيق الذي يشغّل المشرف** — ثم
     * `sh service.sh` الذي يبدأ بـ`"$BIN_SVC" --clearlogs` **فيمحو سجلّ الخادم** في كل محاولة.
     * ومحاولة فاشلة تمحو دليل فشلها بيدها، فيبقى أمام المستخدم «لم يقم» بلا سبب.
     *
     * و[DaemonStarter] تفعل ما تفعله الشجرة في الإقلاع نفسه: `--run` مباشرةً
     * (`mainfiles/service.sh`)، ثم مؤاكدة بقياس مستقلّ حتى ٢٥ ثانية، ثم **سبب الخروج الذي كتبته
     * هذه المحاولة** من السجلّ ([DaemonExit.code]) — فلا يُدَّعى قيامٌ ولا يُترك فشلٌ بلا اسم.
     */
    fun startDaemonAndRetry() {
        if (isStartingDaemon) return
        isStartingDaemon = true
        scope.launch(Dispatchers.IO) {
            val outcome = DaemonStarter().startAndConfirm()
            withContext(Dispatchers.Main) {
                isStartingDaemon = false
                isDaemonDown = !outcome.alive
                if (!outcome.alive) {
                    logs.add(
                        ShellOutput(
                            resources.getString(R.string.str_daemon_start_failed, MaxManagerPaths.MAXMANAGER_LOG),
                            true
                        )
                    )
                    outcome.exit?.let { exit ->
                        logs.add(
                            ShellOutput(
                                resources.getString(R.string.str_daemon_start_reason, exit.code),
                                true
                            )
                        )
                    }
                }
                // وإعادة الفحص من الخيط الرئيسي: `runCompatibilityCheck` يلمس حالة الواجهة.
                if (outcome.alive) runCompatibilityCheck()
            }
        }
    }

    ConfirmDialogHost(handle = confirmDialogHandle)

    ScreenAccentProvider(MaterialTheme.colorScheme.tertiary) {
        MaxListScreen(
            title = stringResource(R.string.CompatibilityCheck),
            onBack = { navController.popBackStack() },
            accentIcon = Icons.AutoMirrored.Filled.FactCheck,
            accent = MaterialTheme.colorScheme.tertiary
        ) {

                item {
                    BypassCheckTitle(text = stringResource(R.string.section_current_status))
                        
                    ExpressiveList(
                        content = listOf {
                            val isUnsupported = activePath == "UNSUPPORTED" || activePath.isEmpty()
                            ExpressiveListItem(
                                headlineContent = { 
                                    AnimatedContent(targetState = activePath, label = "activePathAnim") { path ->
                                        Text(
                                            text = if (path == "UNSUPPORTED" || path.isEmpty()) stringResource(R.string.str_no_active_nodes) else path,
                                            fontWeight = FontWeight.Bold
                                        ) 
                                    }
                                },
                                supportingContent = { 
                                    Text(if (isUnsupported) stringResource(R.string.str_no_active_nodes_desc) else stringResource(R.string.str_active_bypass_node)) 
                                },
                                leadingContent = { 
                                    LeadingIcon(
                                        icon = if (isUnsupported) Icons.Rounded.Block else Icons.Rounded.ElectricBolt,
                                        containerColor = if (isUnsupported) colorScheme.error.copy(alpha = 0.12f) else colorScheme.primary.copy(alpha = 0.12f),
                                        contentColor = if (isUnsupported) colorScheme.error else colorScheme.primary
                                    ) 
                                }
                            )
                        }
                    )
                }
                item {
                    Column {
                        BypassCheckTitle(text = stringResource(R.string.section_diagnostics))
                        
                        Surface(
                            shape = RoundedCornerShape(MaxCardSpec.radius),
                            color = colorScheme.surfaceColorAtElevation(1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AnimatedContent(
                                                targetState = isRunning,
                                                label = "scanIconAnim"
                                            ) { running ->
                                                LeadingIcon(
                                                    icon = if (running) Icons.Rounded.Memory else Icons.AutoMirrored.Rounded.ManageSearch,
                                                    contentDescription = stringResource(R.string.cd_scan_icon)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                text = if (isRunning) stringResource(R.string.str_diagnostic_in_progress_title) else stringResource(R.string.str_scan_nodes),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }


                                        Spacer(modifier = Modifier.height(6.dp))
                                        AnimatedContent(
                                            targetState = Triple(isChargerConnected, isRunning, hasRunDiagnosis),
                                            label = "chargerStatusAnim"
                                        ) { (connected, running, _) ->
                                            Text(
                                                text = if (!connected) stringResource(R.string.str_plug_in_charger)
                                                       else if (running) stringResource(R.string.str_checking_current)
                                                       else stringResource(R.string.str_safely_test),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (!connected) colorScheme.error else colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    
                                    AnimatedVisibility(
                                        visible = isRunning,
                                        enter = fadeIn() + scaleIn(),
                                        exit = fadeOut() + scaleOut()
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 3.dp,
                                            color = colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                nd.max.ui.component.StudioButton(
                                    onClick = {
                                        scope.launch {
                                            val result = confirmDialogHandle.awaitConfirm(
                                                title = resources.getString(R.string.dialog_start_hw_test_title),
                                                content = resources.getString(R.string.dialog_start_hw_test_content),
                                                confirm = resources.getString(R.string.dialog_begin_check),
                                                dismiss = resources.getString(R.string.dialog_cancel)
                                            )
                                            if (result == ConfirmResult.Confirmed) {
                                                runCompatibilityCheck()
                                            }
                                        }
                                    },
                                    enabled = isChargerConnected && !isRunning,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(MaxRadius.inset)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.str_launch_compatibility_check))
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = logs.isNotEmpty() && !isConsoleClosed,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {

                            Column {
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 280.dp)
                                        .nestedScroll(blockParentScroll), 
                                    shape = RoundedCornerShape(MaxCardSpec.radius),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F141C))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        SelectionContainer {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(12.dp)
                                                    .padding(top = 28.dp)
                                                    .verticalScroll(logScrollState)
                                                    .horizontalScroll(rememberScrollState())
                                            ) {
                                                logs.forEach { line ->
                                                    Text(
                                                        text = line.text.parseAsAnsiAnnotatedString(),
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontFamily = FontFamily.Monospace,
                                                            lineHeight = 16.sp
                                                        ),
                                                        color = Color.White,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                        
                                        IconButton(
                                            onClick = { isConsoleClosed = true },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.str_close_logs),
                                                tint = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = isDaemonDown,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            BypassCheckTitle(text = stringResource(R.string.section_daemon))
                            Spacer(modifier = Modifier.height(8.dp))

                            ExpressiveList(
                                content = listOf {
                                    ExpressiveListItem(
                                        headlineContent = { Text(stringResource(R.string.str_daemon_not_running)) },
                                        supportingContent = { Text(stringResource(R.string.str_daemon_not_running_desc)) },
                                        leadingContent = {
                                            LeadingIcon(
                                                icon = Icons.Rounded.WarningAmber,
                                                containerColor = colorScheme.error.copy(alpha = 0.12f),
                                                contentColor = colorScheme.error
                                            )
                                        }
                                    )
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            StudioButton(
                                onClick = { startDaemonAndRetry() },
                                enabled = !isStartingDaemon,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(MaxRadius.inset)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stringResource(
                                        if (isStartingDaemon) R.string.str_starting_daemon
                                        else R.string.str_start_daemon_retry
                                    )
                                )
                            }
                        }
                    }
                }

                item { 
                    BypassCheckTitle(text = stringResource(R.string.str_available_nodes, availablePaths.size))
                
                    if (availablePaths.isEmpty()) {
                        ExpressiveList(
                            content = listOf {
                                ExpressiveListItem(
                                    headlineContent = { Text(stringResource(R.string.str_no_compatible_nodes)) },
                                    supportingContent = { Text(stringResource(R.string.str_run_diagnostics_above_or_check)) },
                                    leadingContent = { LeadingIcon(icon = Icons.Rounded.SearchOff) }
                                )
                            }
                        )
                    } else {
                        ExpressiveList(
                            content = availablePaths.map { pathNode ->
                                {
                                    val isSelected = activePath == pathNode.first
                                    


                                    val textScale by animateFloatAsState(
                                        targetValue = if (isSelected) 1.08f else 1.0f,
                                        animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing),
                                        label = "textScaleAnim"
                                    )

                                    // `ExpressiveListItem` نفسه: كان هذا الصفّ نسخة ثانية منه بحرفه
                                    // لمجرد خلفية اختيار، فصارت الخلفية معاملًا في الصفّ الواحد.
                                    ExpressiveListItem(
                                        containerColor = if (isSelected) colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                                        onClick = {
                                            if (!isRunning) {
                                                scope.launch {
                                                    val result = confirmDialogHandle.awaitConfirm(
                                                        title = resources.getString(R.string.dialog_switch_node_title),
                                                        content = resources.getString(R.string.dialog_switch_node_content, pathNode.first),
                                                        confirm = resources.getString(R.string.dialog_apply_path),
                                                        dismiss = resources.getString(R.string.dialog_dismiss)
                                                    )
                                                    if (result == ConfirmResult.Confirmed) {
                                                        PropertyUtils.set(MaxManagerProps.Conf.BYPASS_PATH, pathNode.first)
                                        withContext(Dispatchers.IO) {
                                            RootFileAccess.write(
                                                "/data/adb/.config/MaxManager/bypasschgconfig/bypasspath",
                                                pathNode.first
                                            )
                                        }
                                                        activePath = pathNode.first
                                                    }
                                                }
                                            }
                                        },
                                        headlineContent = { 
                                            Text(
                                                text = pathNode.first,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) colorScheme.primary else colorScheme.onSurface,
                                                modifier = Modifier.graphicsLayer {
                                                    scaleX = textScale
                                                    scaleY = textScale
                                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                                }
                                            ) 
                                        },
                                        supportingContent = { Text(pathNode.second) },
                                        leadingContent = {
                                            LeadingIcon(
                                                icon = Icons.Rounded.FolderOpen,
                                                containerColor = if (isSelected) colorScheme.primary.copy(alpha = 0.15f) else colorScheme.surfaceVariant,
                                                contentColor = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant
                                            )
                                        },
                                        trailingContent = {

                                            AnimatedVisibility(
                                                visible = isSelected,
                                                enter = scaleIn(tween(durationMillis = 200, easing = LinearOutSlowInEasing)) + fadeIn(tween(200)),
                                                exit = scaleOut(tween(durationMillis = 150)) + fadeOut(tween(150))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                    tint = colorScheme.primary
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
}


@Composable
fun BypassCheckTitle(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
