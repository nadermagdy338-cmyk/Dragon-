/*
 * Configuration backup / restore — one implementation, two entry points.
 *
 * The flow used to live inside the flat tweaks workspace, behind a "more" icon.
 * It now has an explained page under Settings (ConfigBackupScreen) *and* the
 * Control screen's top-bar action, and both must behave identically: same file
 * pickers, same "what is inside this file" checkboxes, same SoC mismatch guard,
 * same messages.
 *
 * So the pickers, the two content dialogs and the loading/confirm hosts live
 * here once. Callers only decide *where* the trigger sits:
 *
 *   val backup = rememberConfigBackupFlow(viewModel) { snackbarHostState.showSnackbar(it) }
 *   IconButton(onClick = backup::showSheet) { ... }
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import nd.max.MaxManagerProps
import nd.max.R
import nd.max.ui.util.BackupManager
import nd.max.ui.util.PropertyUtils
import nd.max.ui.viewmodel.TweakViewModel

/**
 * The three entry points a screen needs. Everything else is internal.
 */
interface ConfigBackupFlow {
    /** Opens the two-action backup sheet (the old "more" menu shape). */
    fun showSheet()

    /** Opens the system picker and writes a new `.zx` backup. */
    fun startBackup()

    /** Opens the system picker, then explains what the chosen file contains. */
    fun startRestore()
}

/**
 * Wires the whole backup/restore flow into the calling screen.
 *
 * @param onMessage one-shot user feedback (success / failure copy).
 */
@Composable
fun rememberConfigBackupFlow(
    viewModel: TweakViewModel,
    onMessage: (String) -> Unit
): ConfigBackupFlow {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnMessage by rememberUpdatedState(onMessage)

    var sheetVisible by remember { mutableStateOf(false) }
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

    val backupSuccessMessage = stringResource(R.string.dialog_backup_success)
    val backupFailedMessage = stringResource(R.string.dialog_backup_fail)
    val restoreFailedTitle = stringResource(R.string.dialog_restore_fail_title)
    val acknowledge = stringResource(android.R.string.ok)

    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { destination ->
            scope.launch {
                val success = loadingDialog.withLoading {
                    viewModel.createConfigFileBackup(
                        context,
                        destination,
                        optBackupTweaks,
                        optBackupApplist
                    )
                }
                currentOnMessage(if (success) backupSuccessMessage else backupFailedMessage)
            }
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { source ->
            sheetVisible = false
            scope.launch {
                loadingDialog.withLoading {
                    val result = viewModel.validateAndRestoreFile(context, source)
                    if (result.isValid && result.data != null) {
                        pendingRestoreResult = result
                        optRestoreTweaks = result.hasTweaks
                        optRestoreApplist = result.hasApplist
                        showRestoreDialog = true
                    } else {
                        confirmDialog.showConfirm(
                            restoreFailedTitle,
                            result.message,
                            acknowledge,
                            null
                        )
                    }
                }
            }
        }
    }

    // Named apart from the public ConfigBackupFlow members below: a member and a
    // local of the same name would make the member call itself.
    val openBackupPicker: () -> Unit = {
        val timestamp = SimpleDateFormat("ddMMyyyy_HHmmss", Locale.getDefault()).format(Date())
        createDocLauncher.launch("MaxManagerConfig_Backup_$timestamp.zx")
    }
    val askBackupScope: () -> Unit = { showBackupOptionsDialog = true }
    val openRestorePicker: () -> Unit = {
        openDocLauncher.launch(arrayOf("application/octet-stream", "*/*"))
    }

    RootAppDialog {
        BackupRestoreBottomSheet(
            show = sheetVisible,
            onDismiss = { sheetVisible = false },
            onBackup = {
                sheetVisible = false
                askBackupScope()
            },
            onRestore = {
                sheetVisible = false
                openRestorePicker()
            }
        )
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
                openBackupPicker()
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
                val isSocMismatch =
                    result.socType != PropertyUtils.get(MaxManagerProps.General.SOC_TYPE)
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
                                    optRestoreApplist
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

    return object : ConfigBackupFlow {
        override fun showSheet() {
            sheetVisible = true
        }

        override fun startBackup() = askBackupScope()

        override fun startRestore() = openRestorePicker()
    }
}
