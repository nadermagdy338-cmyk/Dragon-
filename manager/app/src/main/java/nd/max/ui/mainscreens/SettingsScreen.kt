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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import nd.max.core.hardware.RootFileAccess
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
import nd.max.ui.settings.SettingsViewModel as PreferenceSettingsViewModel


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
    val listState = rememberLazyListState()

    var showLogBottomSheet by remember { mutableStateOf(false) }

    val restartToastText = stringResource(R.string.toast_restarting_service)

    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val isAdvancedMode by preferenceSettingsViewModel.isAdvancedMode.collectAsStateWithLifecycle()

    var isLauncherVisible by rememberSaveable {
        mutableStateOf(isLauncherIconEnabled(context))
    }
    var showChangelogSheet by remember { mutableStateOf(false) }
    var showScreenHelp by remember { mutableStateOf(false) }
    var changelogText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                changelogText = context.assets.open("changelog.md").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                changelogText = context.getString(R.string.err_failed_load_changelog) + "\n${e.message}"
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
                title = context.getString(R.string.dialog_reboot_required_title),
                content = context.getString(R.string.reboot_required_content),
                confirm = context.getString(R.string.yes),
                dismiss = context.getString(R.string.no)
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
                    snackbarHostState.showSnackbar(context.getString(R.string.toast_log_save_success))
                } else {
                    snackbarHostState.showSnackbar(context.getString(R.string.toast_log_save_fail))
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
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 110.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
            ) {
                item {
                    ControlScreenIntro(
                        icon = Icons.Rounded.Settings,
                        title = stringResource(R.string.settings_workspace_title),
                        description = stringResource(R.string.settings_workspace_guidance),
                        status = stringResource(R.string.settings_workspace_state),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = MaxUiMetrics.sectionGap)
                    )

                    ExpressiveList(
                        content = listOf(
                            { AppInfoHeaderContent() },
                            {
                                ExpressiveListItem(
                                    onClick = { MaxNavActions(navController).navigateTo(MaxDestination.ColorPalette) },
                                    headlineContent = { Text(stringResource(R.string.theme)) },
                                    supportingContent = { Text(stringResource(R.string.theme_desc)) },
                                    leadingContent = { LeadingIcon(icon = Icons.Filled.Palette) },
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
                                )
                            }
                        )
                    )
                }

                item { SettingsSectionTitle(stringResource(R.string.section_features)) }

                item {
                    ExpressiveList(
                        content = listOf(
                            { ExpressiveListItem(onClick = { MaxNavActions(navController).navigateTo(MaxDestination.Diagnostics) }, headlineContent = { Text(stringResource(R.string.max_settings_diagnostics)) }, leadingContent = { LeadingIcon(icon = Icons.Rounded.BugReport) }) },
                            { ExpressiveListItem(onClick = { MaxNavActions(navController).navigateTo(MaxDestination.Logs) }, headlineContent = { Text(stringResource(R.string.max_settings_logs)) }, leadingContent = { LeadingIcon(icon = Icons.Rounded.ListAlt) }) },
                        )
                    )
                }

                item { SettingsSectionTitle(stringResource(R.string.max_nav_advanced_tools)) }
                item {
                    ExpressiveList(
                        content = listOf(
                            { ExpressiveListItem(onClick = { MaxNavActions(navController).navigateTo(MaxDestination.Terminal) }, headlineContent = { Text(stringResource(R.string.max_tool_terminal)) }, leadingContent = { LeadingIcon(icon = Icons.Rounded.Terminal) }) },
                            { ExpressiveListItem(onClick = { MaxNavActions(navController).navigateTo(MaxDestination.SetEdit) }, headlineContent = { Text(stringResource(R.string.max_tool_setedit)) }, leadingContent = { LeadingIcon(icon = Icons.Rounded.Edit) }) },
                            { ExpressiveListItem(onClick = { MaxNavActions(navController).navigateTo(MaxDestination.ActivityLauncher) }, headlineContent = { Text(stringResource(R.string.max_tool_activity_launcher)) }, leadingContent = { LeadingIcon(icon = Icons.Rounded.Launch) }) },
                            { ExpressiveListItem(onClick = { MaxNavActions(navController).navigateTo(MaxDestination.KernelFlasher) }, headlineContent = { Text(stringResource(R.string.max_tool_kernel_flasher)) }, leadingContent = { LeadingIcon(icon = Icons.Rounded.Build) }) },
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

                item {
                    SettingsSectionTitle(stringResource(R.string.section_others))
                }
                item {
                    if (uiState.isLoaded) {
                        ExpressiveList(
                            content = listOf(
                                // مفتاح Max AI الرئيسي انتقل إلى شاشة Max AI
                                // الموحدة (maxai) — مصدر حقيقة واحد للمفتاح.
                                {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Rounded.AddHome,
                                        title = stringResource(R.string.show_icon),
                                        checked = isLauncherVisible,
                                        onCheckedChange = { isChecked ->
                                            coroutineScope.launch {
                                                val pkg = context.packageManager
                                                val componentName = ComponentName(context.packageName, "${context.packageName}.Launcher")
                                                val newState = if (isChecked) {
                                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                                                } else {
                                                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                                                }
                                                val applied = runCatching {
                                                    pkg.setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP)
                                                    isLauncherIconEnabled(context) == isChecked
                                                }.getOrDefault(false)
                                                if (applied) {
                                                    isLauncherVisible = isChecked
                                                    withContext(Dispatchers.IO) {
                                                        RootFileAccess.atomicWriteText(
                                                            LAUNCHER_VISIBILITY_PATH,
                                                            if (isChecked) "shown\n" else "hidden\n"
                                                        )
                                                    }
                                                }
                                            }
                                        }
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
                                                title = context.getString(R.string.uninstall),
                                                content = context.getString(R.string.uninstall_confirm_content),
                                                confirm = context.getString(R.string.yes),
                                                dismiss = context.getString(R.string.no)
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
                                                snackbarHostState.showSnackbar(context.getString(R.string.toast_log_gather_fail))
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
                        imageVector = Icons.Rounded.HelpOutline,
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
