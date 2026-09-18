/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * `Max Backup` — نمط **بيانات النظام**: شبكات الواي‑فاي · أزواج البلوتوث · جهات الاتصال
 * وأرقامها · سجل المكالمات · الرسائل.
 *
 * وثلاثة قرارات تعرضها هذه الشاشة صراحةً، لأن كتمانها يجعل الفشل مفاجأة:
 *
 * 1. **الصلاحية تُطلَب لا تُفترض.** فئة تحتاج جذرًا تُعرض «تحتاج جذرًا»، وفئة تحتاج إذنًا
 *    تُعرض «تحتاج إذنًا» مع زر يطلبه فعلًا — ولا واحدة منهما تُعرَض متاحة ثم تفشل.
 * 2. **معنى الاسترجاع يُقال قبل الضغط.** واي‑فاي وبلوتوث **تُستبدل ملفاتهما**، وجهات الاتصال
 *    وسجل المكالمات **تُدمج صفوفًا** (ولا يُمسح ما ليس في النسخة)، والرسائل **قراءة فقط**
 *    لأن كتابتها تطلب أن يكون التطبيق تطبيق الرسائل الافتراضي — وهذا حجب منصّة لا نقص عندنا.
 * 3. **نتيجة الدمج تُعَدّ.** «أُدرج ١٤٢ · تُخُطّي ٩ · بلا مفتاح ٣» نتيجة، و«تم» ليست نتيجة.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.ui.design.MaxBullets
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxConfirmDialog
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.design.MaxTone
import nd.max.ui.util.MaxBackupEngine
import nd.max.ui.util.MaxBackupModel
import nd.max.ui.util.MaxBackupSystem
import nd.max.ui.util.MaxBackupSystemEngine

/** كم نسخة نظام نحتفظ بها. وحدّ أدنى ١ غير قابل للكسر في `MaxBackupModel.toPrune`. */
private const val SYSTEM_KEEP_VERSIONS = 3

/** نسخة نظام مع قرارها لكل فئة داخلها — تُبنى في الخلفية لأنها تقرأ القرص وتتحقّق من البصمات. */
private data class SystemRestorePrompt(
    val handle: MaxBackupModel.Handle,
    val runnable: List<MaxBackupSystem.Kind>,
    val blocked: List<Pair<MaxBackupSystem.Kind, MaxBackupSystem.RestoreBlock>>,
    val warnings: List<MaxBackupSystem.RestoreWarning>,
)

@Composable
internal fun MaxBackupSystemMode(navController: NavController, onSwitchMode: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var plan by remember { mutableStateOf<MaxBackupSystem.Plan?>(null) }
    var backups by remember { mutableStateOf<List<MaxBackupModel.Handle>>(emptyList()) }
    var inventoryLoaded by remember { mutableStateOf(false) }
    var busyStage by remember { mutableStateOf<String?>(null) }
    var systemScope by remember { mutableStateOf(MaxBackupSystem.Scope(emptySet())) }

    var askCreate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf<MaxBackupModel.Handle?>(null) }
    var askPrune by remember { mutableStateOf(false) }
    var restorePrompt by remember { mutableStateOf<SystemRestorePrompt?>(null) }
    var blocked by remember { mutableStateOf<MaxBackupSystem.RestoreBlock?>(null) }
    var verdicts by remember { mutableStateOf<Pair<MaxBackupModel.Handle, Map<String, MaxBackupModel.Integrity>>?>(null) }

    fun grantedNow(): Set<String> = MaxBackupSystem.ALL_PERMISSIONS.filter {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }.toSet()

    var granted by remember { mutableStateOf(grantedNow()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // لا نضيف ما لم يُمنح: الإذن المرفوض يجب أن يبقى ظاهرًا كمرفوض، لا أن يُفترض.
        granted = granted + result.filterValues { it }.keys
    }

    suspend fun reload() {
        val loaded = withContext(Dispatchers.IO) {
            val hasRoot = MaxBackupEngine.hasRoot()
            MaxBackupSystemEngine.inventory(context, hasRoot, granted) to
                MaxBackupEngine.list(context, MaxBackupEngine.SYSTEM_PKG)
        }
        plan = loaded.first
        backups = loaded.second
        inventoryLoaded = true
        // الافتراضي يُضبط مرّة واحدة: لا تُلغى اختيارات المستخدم عند كل تحديث أو منح إذن.
        if (busyStage == null && !systemScope.anySelected) {
            systemScope = MaxBackupSystem.Scope.from(loaded.first)
        }
    }

    LaunchedEffect(granted) { reload() }

    val busy = busyStage != null
    val busyReason = stringResource(R.string.max_backup_working_detail)
    val unmeasuredLabel = stringResource(R.string.max_backup_plan_unmeasured)
    val unknownLabel = stringResource(R.string.status_unknown)
    val stageLabels = systemStageLabels()
    val kindLabels = systemKindLabels()

    val condition = if (!inventoryLoaded) {
        MaxCondition(
            kind = MaxConditionKind.Loading,
            title = stringResource(R.string.max_backup_plan_loading_title),
            detail = stringResource(R.string.max_backup_system_loading_detail),
        )
    } else {
        null
    }

    val banner = busyStage?.let {
        MaxCondition(
            kind = MaxConditionKind.Applying,
            title = stageLabels[it] ?: stringResource(R.string.max_backup_stage_manifest),
            detail = busyReason,
        )
    }

    MaxListScreen(
        title = stringResource(R.string.max_backup_title),
        subtitle = stringResource(R.string.max_backup_system_subtitle),
        onBack = { navController.popBackStack() },
        accentIcon = Icons.Rounded.SimCard,
        condition = condition,
        banner = banner,
        snackbarHostState = snackbarHostState,
        actions = {
            MaxHelpAction(
                title = stringResource(R.string.max_backup_mode_system),
                body = stringResource(R.string.max_backup_system_help),
            )
        },
        header = {
            MaxSection(title = stringResource(R.string.max_backup_mode_title)) {
                MaxBackupModeSwitch(mode = MaxBackupMode.SYSTEM, onSelect = { onSwitchMode() })
            }

            if (plan?.hasRoot == false) {
                MaxSection(title = stringResource(R.string.max_backup_priv_title)) {
                    MaxGroup {
                        MaxRow(
                            title = stringResource(R.string.max_backup_system_no_root_title),
                            subtitle = stringResource(R.string.max_backup_system_no_root_desc),
                            icon = Icons.Rounded.Shield,
                            iconTone = MaxTone.Caution,
                        )
                    }
                }
            }

            val needsPermission = plan?.components?.filter {
                it.availability == MaxBackupSystem.Availability.NEEDS_PERMISSION
            }.orEmpty()
            if (needsPermission.isNotEmpty()) {
                MaxSection(
                    title = stringResource(R.string.max_backup_system_permissions_title),
                    description = stringResource(R.string.max_backup_system_permissions_desc),
                ) {
                    MaxGroup {
                        MaxRow(
                            title = stringResource(R.string.max_backup_system_grant),
                            subtitle = stringResource(
                                R.string.max_backup_system_grant_desc,
                                needsPermission.size.toString(),
                            ),
                            icon = Icons.Rounded.LockOpen,
                            iconTone = MaxTone.Accent,
                            enabled = !busy,
                            onClick = { permissionLauncher.launch(MaxBackupSystem.ALL_PERMISSIONS.toTypedArray()) },
                        )
                    }
                }
            }

            MaxSection(
                title = stringResource(R.string.max_backup_system_sources_title),
                description = stringResource(R.string.max_backup_system_sources_desc),
            ) {
                val loadedPlan = plan
                MaxGroup {
                    MaxBackupSystem.Kind.entries.forEachIndexed { index, kind ->
                        if (index > 0) MaxGroupDivider()
                        val component = loadedPlan?.component(kind)
                        val availability = component?.availability ?: MaxBackupSystem.Availability.UNKNOWN
                        MaxSwitchRow(
                            title = systemKindText(kind),
                            checked = systemScope.selects(kind),
                            enabled = !busy && availability == MaxBackupSystem.Availability.AVAILABLE,
                            subtitle = componentSubtitle(component, kind, unmeasuredLabel),
                            iconTone = when (availability) {
                                MaxBackupSystem.Availability.AVAILABLE -> MaxTone.Positive
                                MaxBackupSystem.Availability.NEEDS_ROOT,
                                MaxBackupSystem.Availability.NEEDS_PERMISSION -> MaxTone.Caution
                                else -> MaxTone.Neutral
                            },
                            lockedReason = if (availability != MaxBackupSystem.Availability.AVAILABLE) {
                                availabilityText(availability)
                            } else if (busy) {
                                busyReason
                            } else {
                                null
                            },
                            onCheckedChange = { systemScope = systemScope.toggle(kind, it) },
                        )
                    }
                }
                MaxBullets(lines = listOf(stringResource(R.string.max_backup_system_semantics_note)))
            }

            MaxSection(title = stringResource(R.string.max_backup_system_create_section)) {
                MaxGroup {
                    MaxRow(
                        title = stringResource(R.string.max_backup_system_action_create),
                        subtitle = stringResource(R.string.max_backup_system_action_create_desc),
                        icon = Icons.Rounded.Backup,
                        iconTone = MaxTone.Accent,
                        enabled = !busy && systemScope.anySelected,
                        onClick = { askCreate = true },
                    )
                    MaxGroupDivider()
                    MaxRow(
                        title = stringResource(R.string.max_backup_action_prune),
                        subtitle = stringResource(R.string.max_backup_action_prune_desc),
                        icon = Icons.Rounded.DeleteOutline,
                        iconTone = MaxTone.Caution,
                        enabled = !busy && backups.size > 1,
                        onClick = { askPrune = true },
                    )
                }
            }

            MaxSection(
                title = stringResource(R.string.max_backup_system_history_title),
                description = stringResource(R.string.max_backup_system_history_desc),
            ) {
                if (backups.isEmpty()) {
                    MaxGroup {
                        MaxRow(
                            title = stringResource(R.string.max_backup_system_history_none),
                            icon = Icons.Rounded.Backup,
                            iconTone = MaxTone.Neutral,
                        )
                    }
                }
            }
        },
    ) {
        itemsIndexed(items = backups, key = { _, handle -> handle.folder }) { index, handle ->
            SystemHistoryRow(
                handle = handle,
                enabled = !busy,
                busyReason = busyReason,
                onVerify = {
                    scope.launch {
                        verdicts = withContext(Dispatchers.IO) {
                            handle to MaxBackupEngine.verify(handle)
                        }
                    }
                },
                onRestore = {
                    scope.launch {
                        val prompt = withContext(Dispatchers.IO) { buildPrompt(context, handle, granted) }
                        if (prompt == null) {
                            blocked = MaxBackupSystem.RestoreBlock.EMPTY
                        } else if (prompt.runnable.isEmpty()) {
                            blocked = prompt.blocked.firstOrNull()?.second
                                ?: MaxBackupSystem.RestoreBlock.EMPTY
                        } else {
                            restorePrompt = prompt
                        }
                    }
                },
                onDelete = { askDelete = handle },
            )
            if (index < backups.lastIndex) MaxGroupDivider()
        }

        if (verdicts != null) {
            val report = verdicts!!
            item {
                MaxSection(
                    title = stringResource(R.string.max_backup_verify_title),
                    description = MaxBackupModel.folderName(report.first.createdAtMs),
                ) {
                    MaxGroup {
                        report.second.entries.forEachIndexed { entryIndex, entry ->
                            if (entryIndex > 0) MaxGroupDivider()
                            val verified = entry.value == MaxBackupModel.Integrity.VERIFIED
                            MaxRow(
                                title = kindLabels[MaxBackupSystem.kindOfEntry(entry.key)] ?: entry.key,
                                subtitle = integrityText(entry.value),
                                icon = if (verified) Icons.Rounded.Backup else Icons.Rounded.WarningAmber,
                                iconTone = if (verified) MaxTone.Positive else MaxTone.Critical,
                            )
                        }
                    }
                }
            }
        }

        item {
            MaxSection(title = stringResource(R.string.max_backup_storage_title)) {
                MaxGroup {
                    MaxRow(
                        title = stringResource(R.string.max_backup_storage_count),
                        subtitle = backups.size.toString(),
                        icon = Icons.Rounded.Backup,
                        iconTone = MaxTone.Neutral,
                    )
                    MaxGroupDivider()
                    MaxRow(
                        title = stringResource(R.string.max_backup_storage_total),
                        subtitle = MaxBackupModel.humanBytes(backups.sumOf { it.bytes }),
                        icon = Icons.Rounded.Backup,
                        iconTone = MaxTone.Neutral,
                    )
                    MaxGroupDivider()
                    MaxRow(
                        title = stringResource(R.string.max_backup_storage_location),
                        subtitle = File(MaxBackupEngine.backupsRoot(context), MaxBackupEngine.SYSTEM_PKG).absolutePath,
                        icon = Icons.Rounded.Shield,
                        iconTone = MaxTone.Neutral,
                    )
                }
                MaxBullets(
                    lines = listOf(
                        stringResource(R.string.max_backup_no_encryption_desc),
                        stringResource(R.string.max_backup_storage_location_desc),
                    )
                )
            }
        }
    }

    // ── حوارات ──────────────────────────────────────────────────────────────

    // الأسماء تُحلّ في التركيب قبل الحوار: `joinToString` ليست inline، فلا تُنادى فيها دالة
    // مركّبة. وهذا ليس تهرّبًا من القاعدة، بل نفس سبب حلّ أسماء المراحل مبكرًا.
    val selectedLabels = systemScope.selectedKinds.map { systemKindText(it) }.joinToString(", ")
    MaxConfirmDialog(
        visible = askCreate,
        title = stringResource(R.string.max_backup_create_confirm_title),
        message = stringResource(
            R.string.max_backup_system_create_confirm_message,
            selectedLabels,
        ),
        confirmLabel = stringResource(R.string.max_backup_action_create),
        onConfirm = {
            askCreate = false
            val target = plan ?: return@MaxConfirmDialog
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    val result = MaxBackupSystemEngine.create(context, target, systemScope) { stage ->
                        busyStage = stage
                    }
                    MaxBackupEngine.prune(context, MaxBackupEngine.SYSTEM_PKG, SYSTEM_KEEP_VERSIONS)
                    result
                }
                busyStage = null
                reload()
                snackbarHostState.showSnackbar(
                    context.getString(
                        when {
                            outcome.success -> R.string.max_backup_msg_created
                            outcome.entryCount > 0 -> R.string.max_backup_msg_created_partial
                            else -> R.string.max_backup_msg_failed
                        }
                    )
                )
            }
        },
        onDismiss = { askCreate = false },
    )

    askDelete?.let { handle ->
        MaxConfirmDialog(
            visible = true,
            title = stringResource(R.string.max_backup_delete_title),
            message = stringResource(R.string.max_backup_delete_message),
            confirmLabel = stringResource(R.string.max_backup_delete_confirm),
            destructive = true,
            onConfirm = {
                askDelete = null
                scope.launch {
                    val removed = withContext(Dispatchers.IO) { MaxBackupEngine.delete(handle) }
                    if (verdicts?.first?.folder == handle.folder) verdicts = null
                    reload()
                    snackbarHostState.showSnackbar(
                        context.getString(
                            if (removed) R.string.max_backup_delete_done else R.string.max_backup_delete_failed
                        )
                    )
                }
            },
            onDismiss = { askDelete = null },
        )
    }

    MaxConfirmDialog(
        visible = askPrune,
        title = stringResource(R.string.max_backup_prune_title),
        message = stringResource(R.string.max_backup_prune_message, SYSTEM_KEEP_VERSIONS.toString()),
        confirmLabel = stringResource(R.string.max_backup_prune_confirm),
        destructive = true,
        onConfirm = {
            askPrune = false
            scope.launch {
                val deleted = withContext(Dispatchers.IO) {
                    MaxBackupEngine.prune(context, MaxBackupEngine.SYSTEM_PKG, SYSTEM_KEEP_VERSIONS)
                }
                reload()
                snackbarHostState.showSnackbar(
                    context.getString(R.string.max_backup_prune_done, deleted.size.toString())
                )
            }
        },
        onDismiss = { askPrune = false },
    )

    restorePrompt?.let { prompt ->
        val baseMessage = stringResource(R.string.max_backup_system_restore_message)
        val willApply = stringResource(
            R.string.max_backup_system_restore_will_apply,
            prompt.runnable.map { kindLabels.getValue(it) }.joinToString(", "),
        )
        val skippedTitle = stringResource(R.string.max_backup_system_skipped_title)
        val skippedLines = prompt.blocked.map { (kind, block) ->
            kindLabels.getValue(kind) + ": " + systemBlockText(block)
        }
        val warningLines = prompt.warnings.map { systemWarningText(it) }
        val message = buildString {
            append(baseMessage)
            append("\n\n")
            append(willApply)
            if (warningLines.isNotEmpty()) {
                append("\n\n")
                append(warningLines.joinToString("\n"))
            }
            if (skippedLines.isNotEmpty()) {
                append("\n\n")
                append(skippedTitle)
                append("\n")
                append(skippedLines.joinToString("\n"))
            }
        }
        MaxConfirmDialog(
            visible = true,
            title = stringResource(R.string.max_backup_system_restore_title),
            message = message,
            confirmLabel = stringResource(R.string.max_backup_restore_confirm),
            destructive = true,
            technicalDetail = MaxBackupModel.folderName(prompt.handle.createdAtMs),
            onConfirm = {
                restorePrompt = null
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        MaxBackupSystemEngine.restore(context, prompt.handle, granted) { stage ->
                            busyStage = stage
                        }
                    }
                    busyStage = null
                    reload()
                    val messageText = when {
                        outcome.success && outcome.inserted + outcome.skipped + outcome.undedupable > 0 ->
                            context.getString(
                                R.string.max_backup_system_restore_summary,
                                outcome.inserted.toString(),
                                outcome.skipped.toString(),
                                outcome.undedupable.toString(),
                            )
                        outcome.success -> context.getString(R.string.max_backup_restore_done)
                        else -> context.getString(
                            R.string.max_backup_system_restore_failed,
                            outcome.failedKind?.let { kindLabels[it] }
                                ?: unknownLabel,
                        )
                    }
                    snackbarHostState.showSnackbar(messageText)
                }
            },
            onDismiss = { restorePrompt = null },
        )
    }

    blocked?.let { reason ->
        MaxConfirmDialog(
            visible = true,
            title = stringResource(R.string.max_backup_restore_blocked_title),
            message = systemBlockText(reason),
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = { blocked = null },
            onDismiss = { blocked = null },
        )
    }
}

/**
 * يبني قرار الاسترجاع لنسخة نظام: أي فئات ستُنفَّذ، وأيها سيُتخطّى ولماذا، وما التحذيرات.
 *
 * ويعمل **قبل** السؤال لا بعده — لأن حوارًا يقول «سيُتخطّى: الرسائل» أنفع من فشل صامت
 * ثم رسالة خطأ لا تشرح شيئًا.
 */
private fun buildPrompt(
    context: android.content.Context,
    handle: MaxBackupModel.Handle,
    granted: Set<String>,
): SystemRestorePrompt? {
    val folder = File(handle.folder)
    val manifest = MaxBackupEngine.readManifest(folder) ?: return null
    val verdicts = MaxBackupEngine.verify(handle)
    val hasRoot = MaxBackupEngine.hasRoot()

    val runnable = mutableListOf<MaxBackupSystem.Kind>()
    val blocked = mutableListOf<Pair<MaxBackupSystem.Kind, MaxBackupSystem.RestoreBlock>>()
    val warnings = linkedSetOf<MaxBackupSystem.RestoreWarning>()

    manifest.entries.forEach { entry ->
        val kind = MaxBackupSystem.kindOfEntry(entry.file) ?: return@forEach
        val source = MaxBackupSystem.source(kind) ?: return@forEach
        // المدخل الذي لم يجتز الفحص يُتخطّى **هنا** كما يُتخطّى في التنفيذ: نفس الحكم، مرة واحدة.
        if (verdicts[entry.file] != MaxBackupModel.Integrity.VERIFIED) {
            blocked += kind to MaxBackupSystem.RestoreBlock.NOT_VERIFIED
            return@forEach
        }
        val decision = MaxBackupSystem.restoreDecision(
            kind = kind,
            entryCount = 1,
            availability = MaxBackupSystem.Availability.AVAILABLE,
            hasRoot = hasRoot,
            permissionsGranted = source.permissions.all { it in granted },
        )
        warnings += decision.warnings
        if (decision.allowed) runnable += kind else blocked += kind to decision.block
    }

    return SystemRestorePrompt(handle, runnable, blocked, warnings.toList())
}

@Composable
private fun SystemHistoryRow(
    handle: MaxBackupModel.Handle,
    enabled: Boolean,
    busyReason: String,
    onVerify: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val stamp = remember(handle.createdAtMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(handle.createdAtMs))
    }
    val summary = stringResource(
        R.string.max_backup_entry_summary,
        MaxBackupModel.humanBytes(handle.bytes),
        handle.entryCount.toString(),
    )
    val incomplete = stringResource(R.string.max_backup_incomplete)

    MaxRow(
        title = stamp,
        subtitle = if (handle.complete) summary else "$summary · $incomplete",
        icon = Icons.Rounded.SimCard,
        iconTone = if (handle.complete) MaxTone.Positive else MaxTone.Caution,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)) {
                IconButton(onClick = onVerify, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Rounded.Backup,
                        contentDescription = stringResource(R.string.max_backup_action_verify),
                    )
                }
                IconButton(onClick = onRestore, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Rounded.Restore,
                        contentDescription = stringResource(R.string.max_backup_action_restore),
                    )
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.max_backup_action_delete),
                    )
                }
            }
        },
    )
    if (!enabled) {
        Text(
            text = busyReason,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaxSpace.rowPaddingHorizontal)
                .padding(bottom = MaxSpace.xs),
        )
    }
}

/**
 * وصف الفئة: من أين تُقرأ · كم · وما معنى استرجاعها. وكل الأجزاء من الموارد لا من سلاسل صلبة.
 *
 * والوصف يحمل معلومة القرار لا الزينة: مستخدم يقرأ «تُستبدل ملفاتها» قبل أن يضغط، لا بعد.
 */
@Composable
private fun componentSubtitle(
    component: MaxBackupSystem.Component?,
    kind: MaxBackupSystem.Kind,
    unmeasured: String,
): String {
    val availability = availabilityText(component?.availability ?: MaxBackupSystem.Availability.UNKNOWN)
    val semantics = semanticsText(MaxBackupSystem.source(kind)?.semantics)
    val method = methodText(MaxBackupSystem.source(kind)?.method)
    val measured = component?.sizeBytes?.let { MaxBackupModel.humanBytes(it) }
    val rows = component?.rowCount?.let { stringResource(R.string.max_backup_system_rows, it.toString()) }
    val files = component?.resolved?.takeIf { it.isNotEmpty() }
        ?.let { stringResource(R.string.max_backup_system_files, it.size.toString()) }

    return buildString {
        append(availability)
        append(" · ")
        append(method)
        append(" · ")
        append(semantics)
        measured?.let { append(" · ").append(it) }
        rows?.let { append(" · ").append(it) }
        files?.let { append(" · ").append(it) }
        if (component?.sizeBytes == null &&
            component?.availability == MaxBackupSystem.Availability.AVAILABLE
        ) {
            append(" · ").append(unmeasured)
        }
    }
}

@Composable
private fun systemKindText(kind: MaxBackupSystem.Kind): String = stringResource(systemKindRes(kind))

/** خريطة الأنواع كاملةً — تُستعمل حيث لا تصل دالة مركّبة (كوروتين · حوار · بناء سلسلة). */
@Composable
private fun systemKindLabels(): Map<MaxBackupSystem.Kind, String> =
    MaxBackupSystem.Kind.entries.associateWith { stringResource(systemKindRes(it)) }

private fun systemKindRes(kind: MaxBackupSystem.Kind): Int = when (kind) {
    MaxBackupSystem.Kind.WIFI -> R.string.max_backup_system_kind_wifi
    MaxBackupSystem.Kind.BLUETOOTH -> R.string.max_backup_system_kind_bluetooth
    MaxBackupSystem.Kind.CONTACTS -> R.string.max_backup_system_kind_contacts
    MaxBackupSystem.Kind.CALL_LOG -> R.string.max_backup_system_kind_call_log
    MaxBackupSystem.Kind.SMS -> R.string.max_backup_system_kind_sms
    MaxBackupSystem.Kind.USER_DICTIONARY -> R.string.max_backup_system_kind_dictionary
}

@Composable
private fun availabilityText(availability: MaxBackupSystem.Availability): String = stringResource(
    when (availability) {
        MaxBackupSystem.Availability.AVAILABLE -> R.string.max_backup_avail_available
        MaxBackupSystem.Availability.NEEDS_ROOT -> R.string.max_backup_avail_needs_root
        MaxBackupSystem.Availability.NEEDS_PERMISSION -> R.string.max_backup_system_avail_needs_permission
        MaxBackupSystem.Availability.UNAVAILABLE -> R.string.max_backup_avail_unavailable
        MaxBackupSystem.Availability.UNKNOWN -> R.string.max_backup_avail_unknown
    }
)

@Composable
private fun semanticsText(semantics: MaxBackupSystem.Semantics?): String = stringResource(
    when (semantics) {
        MaxBackupSystem.Semantics.REPLACE_FILES -> R.string.max_backup_system_semantics_replace
        MaxBackupSystem.Semantics.MERGE_ROWS -> R.string.max_backup_system_semantics_merge
        MaxBackupSystem.Semantics.READ_ONLY_BY_POLICY -> R.string.max_backup_system_semantics_readonly
        null -> R.string.max_backup_avail_unknown
    }
)

@Composable
private fun methodText(method: MaxBackupSystem.Method?): String = stringResource(
    when (method) {
        MaxBackupSystem.Method.ROOT_FILES -> R.string.max_backup_system_method_root
        MaxBackupSystem.Method.PROVIDER -> R.string.max_backup_system_method_provider
        null -> R.string.max_backup_avail_unknown
    }
)

@Composable
private fun systemBlockText(block: MaxBackupSystem.RestoreBlock): String = stringResource(
    when (block) {
        MaxBackupSystem.RestoreBlock.NONE -> R.string.max_backup_block_none
        MaxBackupSystem.RestoreBlock.EMPTY -> R.string.max_backup_system_block_empty
        MaxBackupSystem.RestoreBlock.NOT_VERIFIED -> R.string.max_backup_block_not_verified
        MaxBackupSystem.RestoreBlock.READ_ONLY_BY_POLICY -> R.string.max_backup_system_block_readonly
        MaxBackupSystem.RestoreBlock.NEEDS_ROOT -> R.string.max_backup_block_root
        MaxBackupSystem.RestoreBlock.NEEDS_PERMISSION -> R.string.max_backup_system_block_permission
        MaxBackupSystem.RestoreBlock.UNAVAILABLE -> R.string.max_backup_system_block_unavailable
    }
)

@Composable
private fun systemWarningText(warning: MaxBackupSystem.RestoreWarning): String = stringResource(
    when (warning) {
        MaxBackupSystem.RestoreWarning.SERVICE_MAY_OVERWRITE -> R.string.max_backup_system_warn_overwrite
        MaxBackupSystem.RestoreWarning.MERGE_ADDS_ROWS -> R.string.max_backup_system_warn_merge
        MaxBackupSystem.RestoreWarning.BLOCKED_BY_PLATFORM -> R.string.max_backup_system_warn_blocked
    }
)

@Composable
private fun integrityText(integrity: MaxBackupModel.Integrity): String = stringResource(
    when (integrity) {
        MaxBackupModel.Integrity.VERIFIED -> R.string.max_backup_verify_verified
        MaxBackupModel.Integrity.MISSING -> R.string.max_backup_verify_missing
        MaxBackupModel.Integrity.CORRUPT -> R.string.max_backup_verify_corrupt
        MaxBackupModel.Integrity.UNVERIFIABLE -> R.string.max_backup_verify_unverifiable
    }
)

/** مراحل نمط النظام — بنفس معرّفات المحرّك، بلا نصّ واجهيّ فيه. */
@Composable
private fun systemStageLabels(): Map<String, String> = mapOf(
    MaxBackupSystemEngine.STAGE_ROOT to stringResource(R.string.max_backup_stage_system_root),
    MaxBackupSystemEngine.STAGE_PROVIDER to stringResource(R.string.max_backup_stage_system_rows),
    MaxBackupEngine.STAGE_MANIFEST to stringResource(R.string.max_backup_stage_manifest),
)
