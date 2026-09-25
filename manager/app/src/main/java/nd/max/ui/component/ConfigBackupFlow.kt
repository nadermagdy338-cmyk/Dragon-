/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.MaxManagerProps
import nd.max.R
import nd.max.ui.util.BackupManager
import nd.max.ui.util.ConfigBackupInventory
import nd.max.ui.util.MaxPrefsBundle
import nd.max.ui.util.RootUtils
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
    var optBackupAppearance by remember { mutableStateOf(false) }
    var optBackupAppPrefs by remember { mutableStateOf(false) }

    // `GAP-13`: حالة الجهاز تُقاس **قبل** الإعلان، والإعلان يُحسب من النطاق المختار وحده.
    var deviceState by remember { mutableStateOf<ConfigBackupInventory.DeviceState?>(null) }

    var showRestoreDialog by remember { mutableStateOf(false) }
    var pendingRestoreResult by remember { mutableStateOf<TweakViewModel.ValidationResult?>(null) }
    var optRestoreTweaks by remember { mutableStateOf(true) }
    var optRestoreApplist by remember { mutableStateOf(true) }
    var optRestoreAppearance by remember { mutableStateOf(false) }
    var optRestoreAppPrefs by remember { mutableStateOf(false) }

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
                        optBackupApplist,
                        optBackupAppearance,
                        optBackupAppPrefs
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
                        optRestoreAppearance = result.hasAppearance
                        optRestoreAppPrefs = result.hasAppPrefs
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
    // يفتح نافذة النطاق **بعد** قياس حالة الجهاز، فلا يُعلن إعلانٌ على حالة مفترضة.
    val askBackupScope: () -> Unit = {
        showBackupOptionsDialog = true
        scope.launch {
            deviceState = withContext(Dispatchers.IO) {
                ConfigBackupInventory.DeviceState(
                    hasRoot = RootUtils.isRootGranted(),
                    prefFilesPresent = MaxPrefsBundle.presentFiles(context),
                    prefFilesEmpty = MaxPrefsBundle.emptyFiles(context),
                )
            }
        }
    }
    val openRestorePicker: () -> Unit = {
        openDocLauncher.launch(arrayOf("application/octet-stream", "*/*"))
    }

    /**
     * `GAP-13` — **الإعلان** من النطاق المختار وحالة الجهاز المقيسة. وهو دالّة خالصة، فالواجهة
     * تعرض بالضبط ما سيُكتب.
     *
     * و`null` تعني **«لم يُقس بعد»** لا «قيس فلم يبق شيء». والفرق جوهري في الواجهة: القياس
     * يجري في الخلفية، فلو بُنيت حالة الزرّ عليه لأمكن أن يُعطَّل الزرّ لحظةً ثم يُفعَّل — وزرّ
     * يومض لا يقول شيئًا عن المستخدم، بل عن توقيت قياسنا. فالزرّ يعتمد على **ما اختاره المستخدم**
     * وحده، والإعلان يُضاف متى وصل القياس.
     */
    val selectedScope = ConfigBackupInventory.Scope(
        tweaks = optBackupTweaks,
        applist = optBackupApplist,
        appearance = optBackupAppearance,
        appPrefs = optBackupAppPrefs,
    )
    val declaration = remember(deviceState, selectedScope) {
        deviceState?.let { ConfigBackupInventory.declare(selectedScope, it) }
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
            // يُعطَّل فقط إن لم يختر المستخدم شيئًا — وهذا قراره لا نتيجة قياس عندنا.
            confirmEnabled = selectedScope.chosen.isNotEmpty(),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { optBackupAppearance = !optBackupAppearance }
                ) {
                    Checkbox(
                        checked = optBackupAppearance,
                        onCheckedChange = { optBackupAppearance = it }
                    )
                    Text(
                        stringResource(R.string.max_cfg_backup_appearance),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { optBackupAppPrefs = !optBackupAppPrefs }
                ) {
                    Checkbox(
                        checked = optBackupAppPrefs,
                        onCheckedChange = { optBackupAppPrefs = it }
                    )
                    Text(
                        stringResource(R.string.max_cfg_backup_app_prefs),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // ── الإعلان: ما سيغيب، وما لا يُنسخ أبدًا — قبل الكتابة لا بعدها ──
                val measured = declaration
                if (measured != null && measured.absent.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.max_cfg_declared_absent_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    measured.absent.forEach { absent ->
                        Text(
                            text = stringResource(
                                R.string.max_cfg_declared_absent_line,
                                sectionText(absent.section),
                                absenceReasonText(absent.reason),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.max_cfg_declared_excluded_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                (measured?.exclusions ?: ConfigBackupInventory.exclusions()).forEach { excluded ->
                    Text(
                        text = stringResource(
                            R.string.max_cfg_declared_excluded_line,
                            excluded.what,
                            exclusionReasonText(excluded.reason),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                (optRestoreTweaks && !isSocMismatch) || optRestoreApplist ||
                    optRestoreAppearance || optRestoreAppPrefs
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
                                    optRestoreAppearance,
                                    optRestoreAppPrefs
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
                    if (result.hasAppearance) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { optRestoreAppearance = !optRestoreAppearance }
                        ) {
                            Checkbox(
                                checked = optRestoreAppearance,
                                onCheckedChange = { optRestoreAppearance = it }
                            )
                            Text(
                                stringResource(R.string.max_cfg_backup_appearance),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (result.hasAppPrefs) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { optRestoreAppPrefs = !optRestoreAppPrefs }
                        ) {
                            Checkbox(
                                checked = optRestoreAppPrefs,
                                onCheckedChange = { optRestoreAppPrefs = it }
                            )
                            Text(
                                stringResource(R.string.max_cfg_backup_app_prefs),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.max_cfg_restore_merge_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

/** اسم القسم كما يراه المستخدم — والترجمة من مورد لا من نصّ صلب. */
@Composable
private fun sectionText(section: ConfigBackupInventory.Section): String = stringResource(
    when (section) {
        ConfigBackupInventory.Section.TWEAKS -> R.string.max_cfg_section_tweaks
        ConfigBackupInventory.Section.APPLIST -> R.string.max_cfg_section_applist
        ConfigBackupInventory.Section.APPEARANCE -> R.string.max_cfg_section_appearance
        ConfigBackupInventory.Section.APP_PREFS -> R.string.max_cfg_section_app_prefs
    }
)

/** سبب الغياب — و«غائب» تُقال بصراحة لا تُخفى. */
@Composable
private fun absenceReasonText(reason: ConfigBackupInventory.AbsenceReason): String = stringResource(
    when (reason) {
        ConfigBackupInventory.AbsenceReason.NO_ROOT -> R.string.max_cfg_absent_no_root
        ConfigBackupInventory.AbsenceReason.FILE_MISSING -> R.string.max_cfg_absent_file_missing
        ConfigBackupInventory.AbsenceReason.EMPTY -> R.string.max_cfg_absent_empty
    }
)

/** سبب الاستثناء الدائم. */
@Composable
private fun exclusionReasonText(reason: ConfigBackupInventory.ExclusionReason): String = stringResource(
    when (reason) {
        ConfigBackupInventory.ExclusionReason.SAFETY_POLICY -> R.string.max_cfg_excluded_safety
        ConfigBackupInventory.ExclusionReason.OTHER_APPS -> R.string.max_cfg_excluded_other_apps
        ConfigBackupInventory.ExclusionReason.SECRET -> R.string.max_cfg_excluded_secret
    }
)
