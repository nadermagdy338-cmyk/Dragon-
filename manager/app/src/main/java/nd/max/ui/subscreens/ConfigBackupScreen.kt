/*
 * Configuration backup / restore.
 *
 * This used to be a "more" menu hidden inside the flat tweaks workspace. That
 * workspace is gone, so the flow now has its own explained page under Settings
 * instead of living behind an icon nobody found.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import nd.max.MaxManagerProps
import nd.max.R
import nd.max.ui.component.ConfirmDialogHost
import nd.max.ui.component.CustomContentDialog
import nd.max.ui.component.LoadingDialogHost
import nd.max.ui.component.RootAppDialog
import nd.max.ui.component.rememberConfirmDialog
import nd.max.ui.component.rememberLoadingDialog
import nd.max.ui.design.MaxBullets
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxScreen
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxTone
import nd.max.ui.navigation.MaxNavActions
import nd.max.ui.util.BackupManager
import nd.max.ui.util.PropertyUtils
import nd.max.ui.viewmodel.TweakViewModel

@Composable
fun ConfigBackupScreen(
    navController: NavHostController,
    viewModel: TweakViewModel = viewModel(),
) {
    val navActions = MaxNavActions(navController)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showBackupOptionsDialog by remember { mutableStateOf(false) }
    var optBackupTweaks by remember { mutableStateOf(true) }
    var optBackupApplist by remember { mutableStateOf(true) }

    var showRestoreDialog by remember { mutableStateOf(false) }
    var pendingRestoreResult by remember { mutableStateOf<TweakViewModel.ValidationResult?>(null) }
    var optRestoreTweaks by remember { mutableStateOf(true) }
    var optRestoreApplist by remember { mutableStateOf(true) }

    val loadingDialog = rememberLoadingDialog()
    val confirmDialog = rememberConfirmDialog(onConfirm = {}, onDismiss = {})

    LoadingDialogHost(handle = loadingDialog)
    ConfirmDialogHost(handle = confirmDialog)

    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            scope.launch {
                val success = loadingDialog.withLoading {
                    viewModel.createConfigFileBackup(context, it, optBackupTweaks, optBackupApplist)
                }
                snackbarHostState.showSnackbar(
                    context.getString(
                        if (success) R.string.dialog_backup_success else R.string.dialog_backup_fail
                    )
                )
            }
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                loadingDialog.withLoading {
                    val result = viewModel.validateAndRestoreFile(context, it)
                    if (result.isValid && result.data != null) {
                        pendingRestoreResult = result
                        optRestoreTweaks = result.hasTweaks
                        optRestoreApplist = result.hasApplist
                        showRestoreDialog = true
                    } else {
                        confirmDialog.showConfirm(
                            context.getString(R.string.dialog_restore_fail_title),
                            result.message,
                            context.getString(android.R.string.ok),
                            null,
                        )
                    }
                }
            }
        }
    }

    MaxScreen(
        title = stringResource(R.string.max_nav_config_backup),
        subtitle = stringResource(R.string.max_config_backup_desc),
        onBack = navActions::back,
        accentIcon = Icons.Rounded.Backup,
        snackbarHostState = snackbarHostState,
        actions = {
            MaxHelpAction(
                title = stringResource(R.string.max_nav_config_backup),
                body = stringResource(R.string.max_config_backup_help),
            )
        },
    ) {
        MaxSection(title = stringResource(R.string.max_config_backup_section)) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_config_backup_action),
                    subtitle = stringResource(R.string.max_config_backup_action_desc),
                    icon = Icons.Rounded.Backup,
                    iconTone = MaxTone.Accent,
                    onClick = { showBackupOptionsDialog = true },
                )
                MaxGroupDivider()
                MaxRow(
                    title = stringResource(R.string.max_config_restore_action),
                    subtitle = stringResource(R.string.max_config_restore_action_desc),
                    icon = Icons.Rounded.Restore,
                    iconTone = MaxTone.Caution,
                    onClick = { openDocLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                )
            }
        }

        MaxSection(title = stringResource(R.string.max_config_backup_scope_title)) {
            MaxBullets(
                lines = listOf(
                    stringResource(R.string.str_tweak_configuration_settings),
                    stringResource(R.string.str_per_app_applist_settings),
                    stringResource(R.string.max_config_backup_scope_note),
                )
            )
        }
    }

    RootAppDialog {
        CustomContentDialog(
            visible = showBackupOptionsDialog,
            title = stringResource(R.string.dialog_backup_options_title),
            confirmText = stringResource(R.string.dialog_backup_options_confirm),
            confirmEnabled = optBackupTweaks || optBackupApplist,
            onDismiss = { showBackupOptionsDialog = false },
            onConfirm = {
                showBackupOptionsDialog = false
                val sdf = java.text.SimpleDateFormat("ddMMyyyy_HHmmss", java.util.Locale.getDefault())
                val timestamp = sdf.format(java.util.Date())
                createDocLauncher.launch("MaxManagerConfig_Backup_$timestamp.zx")
            }
        ) {
            Column {
                Text(
                    text = stringResource(R.string.str_select_the_configurations_you),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { optBackupTweaks = !optBackupTweaks }
                ) {
                    Checkbox(checked = optBackupTweaks, onCheckedChange = { optBackupTweaks = it })
                    Text(
                        stringResource(R.string.str_tweak_configuration_settings),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { optBackupApplist = !optBackupApplist }
                ) {
                    Checkbox(checked = optBackupApplist, onCheckedChange = { optBackupApplist = it })
                    Text(
                        stringResource(R.string.str_per_app_applist_settings),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    RootAppDialog {
        CustomContentDialog(
            visible = showRestoreDialog,
            title = stringResource(R.string.str_restore_configuration),
            confirmText = stringResource(R.string.dialog_restore_confirm),
            confirmEnabled = pendingRestoreResult?.let { result ->
                val isSocMismatch = result.socType != PropertyUtils.get(MaxManagerProps.General.SOC_TYPE)
                (optRestoreTweaks && !isSocMismatch) || optRestoreApplist
            } ?: false,
            onDismiss = { showRestoreDialog = false },
            onConfirm = {
                showRestoreDialog = false
                pendingRestoreResult?.let { result ->
                    val dataToRestore = result.data
                    val isSocMismatch =
                        result.socType != PropertyUtils.get(MaxManagerProps.General.SOC_TYPE)
                    if (dataToRestore != null) {
                        scope.launch {
                            loadingDialog.withLoading {
                                viewModel.applyRestoreData(
                                    context,
                                    dataToRestore,
                                    optRestoreTweaks && !isSocMismatch,
                                    optRestoreApplist,
                                )
                                viewModel.loadAllConfiguration(context)
                            }
                        }
                    }
                }
            }
        ) {
            pendingRestoreResult?.let { result ->
                val socName = BackupManager.getSocName(result.socType)
                val isSocMismatch =
                    result.socType != PropertyUtils.get(MaxManagerProps.General.SOC_TYPE)

                Column {
                    Text(
                        text = stringResource(R.string.str_backup_content_detected_select),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    if (isSocMismatch && result.hasTweaks) {
                        Text(
                            stringResource(R.string.str_warning_backup_is_for_socname, socName),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (result.hasTweaks) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (!isSocMismatch) optRestoreTweaks = !optRestoreTweaks }
                        ) {
                            Checkbox(
                                checked = optRestoreTweaks && !isSocMismatch,
                                onCheckedChange = { if (!isSocMismatch) optRestoreTweaks = it },
                                enabled = !isSocMismatch
                            )
                            Text(
                                stringResource(R.string.str_tweak_configuration_settings),
                                color = if (isSocMismatch) {
                                    MaterialTheme.colorScheme.outline
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                    if (result.hasApplist) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { optRestoreApplist = !optRestoreApplist }
                        ) {
                            Checkbox(
                                checked = optRestoreApplist,
                                onCheckedChange = { optRestoreApplist = it }
                            )
                            Text(
                                stringResource(R.string.str_per_app_applist_settings),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
