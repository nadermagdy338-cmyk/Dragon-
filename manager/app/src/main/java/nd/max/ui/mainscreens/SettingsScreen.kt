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
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions
import nd.max.ui.navigation.MaxRisk


import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import nd.max.ui.design.MaxSpace
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.topjohnwu.superuser.Shell
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.ui.component.maxAdaptiveContentWidth
import nd.max.BuildConfig
import nd.max.R
import nd.max.ui.component.*
import nd.max.ui.component.MaxSnackbarHost
import nd.max.ui.util.*
import nd.max.ui.viewmodel.*
// NOTE: nd.max.ui.viewmodel.SettingsViewModel (via the wildcard import above) and
// nd.max.ui.settings.SettingsViewModel are two different classes that happen to
// share a name — the wildcard import above resolves to the former. Aliasing the
// latter here avoids silently colliding with it.
import nd.max.ui.settings.AppLanguage
import nd.max.ui.settings.AppLanguageSheet
import nd.max.ui.settings.SettingsViewModel as PreferenceSettingsViewModel


/**
 * سجل ظهور الأيقونة الذي تكتبه الوحدة عند كل إقلاع (`service.sh`).
 *
 * والأيقونة صارت **ظاهرة دائمًا**: الـalias في البيان مُفعَّل، والوحدة تُعيد تفعيله في كل إقلاع
 * (لأن حالة المكوّن المحفوظة في `PackageManager` تسبق قيمة البيان). ويُبقى هذا الثابت لأن الملف
 * نفسه ما زال يُكتب، وقراءته تُجيب «هل حالتها كما تركناها؟» بلا صندوق أوامر.
 */
internal const val LAUNCHER_VISIBILITY_PATH = "/data/adb/.config/MaxManager/launcher_visibility"

fun isLauncherIconEnabled(context: Context): Boolean {
    val pkg = context.packageManager
    val componentName = ComponentName(context.packageName, "${context.packageName}.Launcher")
    val state = pkg.getComponentEnabledSetting(componentName)
    return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

}

@Composable
fun SettingsScreen(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel = viewModel(),
    preferenceSettingsViewModel: PreferenceSettingsViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    // موارد من `LocalResources.current`: نصوص هذه الشاشة تُقرأ داخل دوال محلية و`onClick`
    // و`LaunchedEffect`، وهي سياقات لا تُبطل فيها قراءة `LocalContext.current.resources`.
    val resources = LocalResources.current
    val listState = rememberLazyListState()

    var showLogBottomSheet by remember { mutableStateOf(false) }

    val restartToastText = stringResource(R.string.toast_restarting_service)

    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val isAdvancedMode by preferenceSettingsViewModel.isAdvancedMode.collectAsStateWithLifecycle()
    val currentLanguage by preferenceSettingsViewModel.currentLanguage.collectAsStateWithLifecycle()

    // تلقائي: نبيّن اللغة التي يقررها النظام فعلًا، وإلا بقي المستخدم لا يعرف ما يرى.
    val languageLabel = if (currentLanguage == AppLanguage.AUTO) {
        stringResource(R.string.max_language_auto) + " · " + AppLanguage.displayName(AppLanguage.AUTO)
    } else {
        AppLanguage.nativeName(currentLanguage)
    }

    var showChangelogSheet by remember { mutableStateOf(false) }
    var showScreenHelp by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var changelogText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                changelogText = context.assets.open("changelog.md").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                changelogText = resources.getString(R.string.err_failed_load_changelog) + "\n${e.message}"
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()


    val loadingDialog = rememberLoadingDialog()
    val uninstallDialog = rememberConfirmDialog(
        onConfirm = {
            Shell.cmd("sh /data/adb/modules/MaxManager/uninstall.sh").submit()
        },
        onDismiss = {}
    )

    // --- Reboot confirm dialog plumbing ---
    var pendingToggle by remember { mutableStateOf<(() -> Unit)?>(null) }
    val rebootDialog = rememberConfirmDialog(
        onConfirm = {
            pendingToggle?.invoke()
            pendingToggle = null
        },
        onDismiss = { pendingToggle = null }
    )

    fun toggleWithRebootCheck(key: String, isChecked: Boolean, apply: () -> Unit) {
        if (RebootManager.wouldRequireReboot(key, isChecked)) {
            pendingToggle = apply
            rebootDialog.showConfirm(
                title = resources.getString(R.string.dialog_reboot_required_title),
                content = resources.getString(R.string.reboot_required_content),
                confirm = resources.getString(R.string.yes),
                dismiss = resources.getString(R.string.no)
            )
        } else {
            apply()
        }
    }
    // ---------------------------------------

    LaunchedEffect(uiState.isLoaded) {
        if (uiState.isLoaded) {
            RebootManager.captureBaselineOnce("disable_tweak", uiState.disableTweak)
        }
    }

    val createLogLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
        uri?.let { destinationUri ->
            coroutineScope.launch {
                val success = loadingDialog.withLoading {

                    val logFile = dumpDiagnosticLogs(context, saveToDownloads = false)

                    if (logFile != null && logFile.exists()) {

                        try {
                            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                                logFile.inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            true
                        } catch (e: Exception) {
                            false
                        } finally {

                            logFile.delete()
                        }
                    } else {
                        false
                    }
                }

                if (success) {
                    snackbarHostState.showSnackbar(resources.getString(R.string.toast_log_save_success))
                } else {
                    snackbarHostState.showSnackbar(resources.getString(R.string.toast_log_save_fail))
                }
            }
        }
    }

    var logFileToDelete by remember { mutableStateOf<File?>(null) }

    val shareLogLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        logFileToDelete?.let { file ->
            if (file.exists()) {
                file.delete()
            }
            logFileToDelete = null
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                SettingsScreenTopAppBar(
                    scrollBehavior = scrollBehavior,
                    onChangelogClick = { showChangelogSheet = true },
                    onHelpClick = { showScreenHelp = true }
                )
            },
            snackbarHost = {
                MaxSnackbarHost(snackbarHostState)
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.maxAdaptiveContentWidth(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + MaxSpace.md,
                    start = MaxSpace.gutter,
                    end = MaxSpace.gutter,
                    bottom = 110.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
                // بلا هذا الترتيب تُرصف الكتل عند حافة بعضها: هذا `LazyColumn`
                // كان الوحيد بلا مسافة بين عناصره، بينما كل صفحة تبنيها `MaxListScreen`
                // تستعمل `MaxSpace.row`. والنتيجة كانت أن آخر بطاقة في كتلة تُلتصق بأول
                // بطاقة في الكتلة التالية وتُقرأ كبطاقة واحدة مكسورة — «صحة الوحدة والإنقاذ»
                // كانت آخر صفّ في قسم الميزات، فبدت ملتصقة بكتلة المفاتيح التي تحتها.
                verticalArrangement = Arrangement.spacedBy(MaxSpace.row),
            ) {
                item {
                    ExpressiveList(
                        content = listOf(
                            { AppInfoHeaderContent() },
                            //
                            // **السمة عادت إلى هنا (طلب المالك)** — وهي الصفّ الأول فوق بطاقة
                            // اللغة: تفضيلان يخصّان الواجهة نفسها، فمظهرها ولغتها في مكان واحد.
                            // وكانت نُقلت إلى `Control → Tools` بحجّة أنها أداة لا تفضيل، والحجّة
                            // كانت عن التصنيف لا عن الوصول: من يريد تغيير السمة يفتح الإعدادات
                            // أولًا. **ومخطّط الألوان بقي في الأدوات**: ذاك سؤال «أي ألوان تُشتقّ
                            // من البذرة» لا «ما السمة» — أداة ضبط لا تفضيل واجهة.
                            {
                                ExpressiveListItem(
                                    onClick = {
                                        MaxNavActions(navController).navigateTo(MaxDestination.ColorPalette)
                                    },
                                    headlineContent = { Text(stringResource(R.string.theme)) },
                                    supportingContent = { Text(stringResource(R.string.theme_desc)) },
                                    leadingContent = { LeadingIcon(icon = Icons.Rounded.Palette) },
                                    trailingContent = {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                    }
                                )
                            },
                            {
                                ExpressiveListItem(
                                    onClick = { showLanguageSheet = true },
                                    headlineContent = { Text(stringResource(R.string.max_language_title)) },
                                    supportingContent = { Text(stringResource(R.string.max_language_desc)) },
                                    leadingContent = { LeadingIcon(icon = Icons.Filled.Language) },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // سطر القيمة يحمل حدًّا فضيّقًا أحيانًا (اسم لغة طويل
                                            // أمام سهم): بلا حدّ يُكسّر الاسم حرفًا حرفًا.
                                            Text(
                                                text = languageLabel,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    lineBreak = LineBreak.Heading
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                        }
                                    }
                                )
                            }
                        )
                    )
                }

                item { SettingsSectionTitle(stringResource(R.string.section_features)) }

                item {
                    // The log viewer stays out of Settings: it is a developer surface, not a preference.
                    // Diagnostics came back for a narrower reason — Max Atlas reports what this device
                    // exposes, and a device fact belongs next to the other device facts, reachable without
                    // the native module and without root. Backup/restore moved here earlier from the
                    // retired flat tweaks workspace.
                    //
                    // It is deliberately outside every `isLoaded` gate: a gate on the settings profile
                    // would have made the one screen that explains an unreadable device unreachable
                    // exactly when reading is already failing.
                    ExpressiveList(
                        content = listOf(
                            {
                                ExpressiveListItem(
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.Diagnostics) },
                                    headlineContent = { Text(stringResource(R.string.section_diagnostics)) },
                                    supportingContent = { Text(stringResource(R.string.diagnostics_intro)) },
                                    leadingContent = { LeadingIcon(icon = Icons.Rounded.Memory) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                                )
                            },
                            {
                                ExpressiveListItem(
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.ConfigBackup) },
                                    headlineContent = { Text(stringResource(R.string.max_nav_config_backup)) },
                                    supportingContent = { Text(stringResource(R.string.max_config_backup_desc)) },
                                    leadingContent = { LeadingIcon(icon = Icons.Rounded.Backup) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                                )
                            },
                            {
                                // عقد إضافات الطرف الثالث (GAP-14): كانت الشاشة مبنية ومسارها
                                // مسجّلًا لكن **لا مدخل لها**، فلا يصل إليها أحد. وُضعت هنا لأنها
                                // حالة النظام لا تحكّم أداء — وبنصّ العقد في متناول من يكتب إضافة.
                                ExpressiveListItem(
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.Plugins) },
                                    headlineContent = { Text(stringResource(R.string.max_plugins_title)) },
                                    supportingContent = { Text(stringResource(R.string.max_plugins_subtitle)) },
                                    leadingContent = { LeadingIcon(icon = Icons.Rounded.Extension) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                                )
                            },
                            {
                                // طبقة الامتياز الثانية (AR-20): تُعرَض في الإعدادات
                                // بجانب بقية الأسطح، وبنفس اللوحة المستخدمة في شاشة البداية.
                                ExpressiveListItem(
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.Privilege) },
                                    headlineContent = { Text(stringResource(R.string.max_privilege_title)) },
                                    supportingContent = { Text(stringResource(R.string.max_privilege_desc)) },
                                    leadingContent = { LeadingIcon(icon = Icons.Rounded.Shield) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                                )
                            },
                            {
                                // صحة الوحدة والإنقاذ (AR-05 + AR-18): قراءة فقط وبلا شبكة.
                                ExpressiveListItem(
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.ModuleHealth) },
                                    headlineContent = { Text(stringResource(R.string.max_module_title)) },
                                    supportingContent = { Text(stringResource(R.string.max_module_health_desc)) },
                                    leadingContent = { LeadingIcon(icon = Icons.Rounded.Build) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                                )
                            },
                        )
                    )
                }

                item {
                    if (uiState.isLoaded) {
                        ExpressiveList(
                            content = listOf(
                                {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Filled.Tune,
                                        title = stringResource(R.string.advanced_mode_title),
                                        summary = stringResource(R.string.advanced_mode_desc),
                                        checked = isAdvancedMode,
                                        onCheckedChange = preferenceSettingsViewModel::setAdvancedMode
                                    )
                                },
                                {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Filled.Notifications,
                                        title = stringResource(R.string.show_toast),
                                        checked = uiState.stateToast,
                                        onCheckedChange = settingsViewModel::setShowToast
                                    )
                                },
                                {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Filled.NotificationsActive,
                                        title = stringResource(R.string.show_notifications),
                                        checked = uiState.profileNotifications,
                                        onCheckedChange = settingsViewModel::setProfileNotifications
                                    )
                                },
                                {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Filled.DeveloperBoardOff,
                                        title = stringResource(R.string.disable_tweak),
                                        summary = stringResource(R.string.disable_tweak_desc),
                                        checked = uiState.disableTweak,
                                        onCheckedChange = { isChecked ->
                                            toggleWithRebootCheck("disable_tweak", isChecked) {
                                                settingsViewModel.setDisableTweak(isChecked)
                                                RebootManager.checkAgainstBaseline("disable_tweak", isChecked)
                                            }
                                        }
                                    )
                                },
                                {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Filled.Timer,
                                        title = stringResource(R.string.profile_timeout),
                                        summary = stringResource(R.string.profile_timeout_desc),
                                        checked = uiState.profileTimeout,
                                        onCheckedChange = settingsViewModel::setProfileTimeout
                                    )
                                }
                            )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }

                // بطاقة النشاط: التخصيص الوحيد الذي يملكه المستخدم على ما تعرضه الرئيسية.
                //
                // ومكانه تحت عنوان «الشاشة الرئيسية» لا داخل قسم التنبيهات: عنوان القسم يقول
                // **ما يُضبط** لا **أين يُضبط**، ومن فتح الإعدادات يبحث عن وجهة لا عن رقم سطر.
                // والقسم مستقلّ لأنّ كل تفضيل عرض في الرئيسية يقع فيه، فلا يُضاف قسم ثانٍ غدًا.
                item { SettingsSectionTitle(stringResource(R.string.settings_home_section)) }
                item { ActivityCardSettingsItem() }

                item {
                    SettingsSectionTitle(stringResource(R.string.section_others))
                }
                item {
                    if (uiState.isLoaded) {
                        ExpressiveList(
                            content = listOf(
                                // مفتاح Max AI الرئيسي انتقل إلى شاشة Max AI
                                // الموحدة (maxai) — مصدر حقيقة واحد للمفتاح.
                                //
                                // ومفتاح «إظهار أيقونة التطبيق» أُزيل مع مساره: الأيقونة تبقى
                                // ظاهرة دائمًا. السبب عَمَليّ لا ذوقيّ: تطبيق الوحدة هو الطريق
                                // الوحيد إلى مدير الروت بعد التفليش، ومدير إخفائه من الواجهة كان
                                // يقطع الطريق على من أراد منحه الإذن — ثم يقول «تطبيقي لا يظهر».
                                // طلب إذن الروت يدويًّا — زرّ واحد يفتح باب مدير الروت.
                                //
                                // ولماذا لزم: التطبيق يُثبّته المُثبِّت **تطبيق نظام** (`/product/priv-app`)،
                                // ومديرو الروت (KernelSU Next · APatch · Magisk) يبنون قائمة القبول من
                                // **الطلبات** التي تصلهم من التطبيق؛ فمن لم يُسأل لا يظهر في القائمة
                                // فيقول صاحبه «تطبيقي غير موجود لأمنحه الإذن». والضغط هنا يُنفّذ أمرًا
                                // بصلاحية جذر عبر `libsu`، فيصل الطلب إلى المدير ويُسجَّل التطبيق في
                                // قائمته — ولذلك الشرح أدناه يقول ما سيحدث بالضبط لا «جرّب».وعلى كل حال،
                                // الكتابات الروتينية تأخذ أذوناتها من `service.sh` عند الإقلاع،
                                // فهذا الزرّ للأذن الشخصي لا لتشغيل التطبيق.
                                {
                                    ExpressiveListItem(
                                        onClick = {
                                            coroutineScope.launch {
                                                val output = withContext(Dispatchers.IO) {
                                                    PrivilegedShell.run("id")
                                                }
                                                val granted = output?.any { it.contains("uid=0") } == true
                                                snackbarHostState.showSnackbar(
                                                    resources.getString(
                                                        if (granted) R.string.root_grant_ok else R.string.root_grant_denied,
                                                    ),
                                                )
                                            }
                                        },
                                        headlineContent = { Text(stringResource(R.string.root_grant_title)) },
                                        supportingContent = { Text(stringResource(R.string.root_grant_desc)) },
                                        leadingContent = { LeadingIcon(icon = Icons.Filled.VerifiedUser) },
                                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                                    )
                                },
                                {
                                    ExpressiveListItem(
                                        onClick = {
                                            Shell.cmd("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-service --rerun").submit { result ->
                                                if (result.isSuccess) {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(restartToastText)
                                                    }
                                                }
                                            }
                                        },
                                        headlineContent = { Text(stringResource(R.string.restart_service)) },
                                        supportingContent = { Text(stringResource(R.string.restart_service_desc)) },
                                        leadingContent = { LeadingIcon(icon = Icons.Filled.RestartAlt) },
                                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                                    )
                                },
                                {
                                    ExpressiveListItem(
                                        onClick = { showLogBottomSheet = true },
                                        headlineContent = { Text(stringResource(R.string.save_log)) },
                                        supportingContent = { Text(stringResource(R.string.save_log_desc)) },
                                        leadingContent = { LeadingIcon(icon = Icons.Filled.Save) },
                                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                                    )
                                },
                                {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Filled.BugReport,
                                        title = stringResource(R.string.allow_verbose_log),
                                        summary = stringResource(R.string.allow_verbose_log_desc),
                                        checked = uiState.debugMode,
                                        onCheckedChange = settingsViewModel::setDebugMode
                                    )
                                },
                                {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Filled.History,
                                        title = stringResource(R.string.detailed_activity_log),
                                        summary = stringResource(R.string.detailed_activity_log_desc),
                                        checked = uiState.detailedLog,
                                        onCheckedChange = settingsViewModel::setDetailedLog
                                    )
                                },
                                {
                                    ExpressiveListItem(
                                        onClick = {
                                            uninstallDialog.showConfirm(
                                                title = resources.getString(R.string.uninstall),
                                                content = resources.getString(R.string.uninstall_confirm_content),
                                                confirm = resources.getString(R.string.yes),
                                                dismiss = resources.getString(R.string.no)
                                            )
                                        },
                                        headlineContent = { Text(stringResource(R.string.uninstall), color = MaterialTheme.colorScheme.error) },
                                        leadingContent = {
                                            LeadingIcon(
                                                icon = Icons.Filled.Delete,
                                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                                    )
                                }
                            )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }

                item { SettingsSectionTitle(stringResource(R.string.section_about)) }
                item {
                    ExpressiveList(
                        content = listOf {
                            ExpressiveListItem(
                                onClick = { MaxNavActions(navController).navigateTo(MaxDestination.About) },
                                headlineContent = { Text(stringResource(R.string.about_maxmanager)) },
                                supportingContent = {
                                    Text(stringResource(R.string.version_format, BuildConfig.VERSION_NAME))
                                },
                                leadingContent = { LeadingIcon(icon = Icons.Filled.ContactPage) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                            )
                        }
                    )
                }
            }
        }


        LoadingDialogHost(handle = loadingDialog)
        ConfirmDialogHost(handle = uninstallDialog)
        ConfirmDialogHost(handle = rebootDialog)

        RootAppDialog {
            AppLanguageSheet(
                visible = showLanguageSheet,
                selected = currentLanguage,
                onSelect = { tag ->
                    // نغلق الورقة قبل التطبيق: تغيير اللغة يُعيد إنشاء النشاط، والعودة بورقة مفتوحة تبدو كخطأ.
                    showLanguageSheet = false
                    preferenceSettingsViewModel.setAppLanguage(tag)
                },
                onDismiss = { showLanguageSheet = false }
            )

            CustomBottomSheet(
                visible = showLogBottomSheet,
                onDismiss = { showLogBottomSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                        )
                ) {
                    Text(
                        text = stringResource(R.string.str_logs_diagnostics),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    )

                    ExpressiveList(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        content = listOf(
                            {
                                ExpressiveListItem(
                                    headlineContent = { Text(stringResource(R.string.save_log), color = MaterialTheme.colorScheme.onSurface) },
                                    supportingContent = { Text(stringResource(R.string.str_save_compressed_logs_to_a_fold), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingContent = { LeadingIcon(Icons.Rounded.FolderSpecial) },
                                    onClick = {
                                        showLogBottomSheet = false


                                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                        val fileName = "MaxManager_Logs_$timeStamp.tar.gz"
                                        createLogLauncher.launch(fileName)
                                    }
                                )
                            },
                            {
                                ExpressiveListItem(
                                    headlineContent = { Text(stringResource(R.string.str_send_logs), color = MaterialTheme.colorScheme.onSurface) },
                                    supportingContent = { Text(stringResource(R.string.str_share_compressed_logs_to_other), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    leadingContent = { LeadingIcon(Icons.Rounded.Share) },
                                    onClick = {
                                        showLogBottomSheet = false
                                        coroutineScope.launch {
                                            val logFile = loadingDialog.withLoading {
                                                dumpDiagnosticLogs(context, saveToDownloads = false)
                                            }

                                            if (logFile != null) {
                                                logFileToDelete = logFile
                                                val intent = getShareLogIntent(context, logFile)
                                                shareLogLauncher.launch(intent)
                                            } else {
                                                snackbarHostState.showSnackbar(resources.getString(R.string.toast_log_gather_fail))
                                            }
                                        }
                                    }
                                )
                            }
                        )
                    )
                }
            }
        }
        RootAppDialog {
            CustomBottomSheet(
                visible = showChangelogSheet,
                onDismiss = { showChangelogSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                ) {
                    Text(
                        text = stringResource(R.string.str_changelog),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        MarkdownText(
                            markdown = changelogText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(
                            modifier = Modifier.height(
                                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 64.dp
                            )
                        )
                    }
                }
            }
        }

    }

    MaxScreenHelpDialog(
        visible = showScreenHelp,
        title = stringResource(R.string.settings_help_title),
        description = stringResource(R.string.settings_workspace_guidance),
        onDismiss = { showScreenHelp = false }
    )
    }



/** Thin wrapper over [MaxManagerSectionTitle] — kept so existing call sites don't change. */
@Composable
fun SettingsSectionTitle(text: String) {
    MaxManagerSectionTitle(text = text, accent = MaterialTheme.colorScheme.primary)
}

@Composable
fun SettingsScreenTopAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onChangelogClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    val accentIconAlpha by animateFloatAsState(
        targetValue = 1f - scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f),
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "accentIconScrollAlpha"
    )
    val colorScheme = MaterialTheme.colorScheme

    MaxManagerTopBarScrim {
        LargeTopAppBar(
            navigationIcon = {
                Box(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                        .graphicsLayer { alpha = accentIconAlpha }
                ) {
                    ScreenAccentGlyph(
                        icon = Icons.Rounded.Settings,
                        accent = colorScheme.primary,
                        size = 38.dp
                    )
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.settings),
                    fontWeight = FontWeight.Bold
                )
            },
            actions = {
                IconButton(onClick = onHelpClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                        contentDescription = stringResource(R.string.cd_screen_help)
                    )
                }
                IconButton(onClick = onChangelogClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.TextSnippet,
                        contentDescription = stringResource(R.string.cd_changelog)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            ),
            scrollBehavior = scrollBehavior,
            windowInsets = WindowInsets.statusBars
        )
    }
}
/** Risk label resource for the Settings -> Advanced tools gate (ADR-16). */
internal fun maxRiskLabel(risk: MaxRisk): Int = when (risk) {
    MaxRisk.Normal -> R.string.max_risk_normal
    MaxRisk.Advanced -> R.string.max_risk_advanced
    MaxRisk.Dangerous -> R.string.max_risk_dangerous
}
