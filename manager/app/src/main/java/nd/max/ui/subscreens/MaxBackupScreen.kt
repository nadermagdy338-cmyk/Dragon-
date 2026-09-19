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
 * Max Backup — نسخ التطبيقات احتياطيًّا واسترجاعها.
 *
 * شاشة قائمة بذاتها، ولها **مدخل من كل تطبيق** في شاشة إعدادات التطبيق. ولها نمطان:
 *
 *  - **بلا تطبيق محدَّد:** منتقي تطبيقات، ليستعملها من فتح الشاشة من مكان آخر.
 *  - **بتطبيق محدَّد:** الجرد، والنطاق، والإنشاء، وسجل النسخ مع الفحص والاسترجاع.
 *
 * والفكرة التي بُنيت عليها ليست «أرشيف ملفات»، بل **ادّعاء قابل للفحص**:
 *
 *  1. الجرد يُعرض **قبل** الكتابة، وبأحجام مقيسة لا مقدَّرة. ما لم يُقس يُعلن أنه لم يُقس.
 *  2. كل ملف يُبصم بـ`sha256` بعد كتابته ثم **يُعاد قراءته** والتحقّق منه قبل أن يُكتب
 *     سطر واحد يقول «تمّ».
 *  3. زر الاسترجاع **لا يعمل** حتى يمرّ كل مدخل بفحص بصمته. و«لم أفحص» ليست «سليم».
 *  4. الأرشيف **غير مشفّر، ويُقال ذلك**. مفتاح داخل الـAPK ليس سرًّا، وادّعاء التشفير
 *     أسوأ من غيابه لأنه يجعل المستخدم يعتمد عليه.
 *
 * ولا تُنفَّذ أي كتابة من هذه الطبقة: كل ما يلمس الجذر أو بيانات التطبيقات يجري في
 * `MaxBackupEngine` داخل `ui/util` (ADR-11).
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
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
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxScreen
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSegmented
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.design.MaxTone
import nd.max.ui.util.MaxBackupEngine
import nd.max.ui.util.MaxBackupModel

/**
 * نمطا `Max Backup`. والفصل مقصود: نسخة تطبيق ونسخة بيانات نظام ليستا الشيء نفسه —
 * الأولى وحدة قابلة لإعادة التثبيت، والثانية مصدر نظام يُقرأ بالجذر أو بمزوّد.
 * ولذلك لكل نمط نطاقه وحواراته ووسومه، ويجمع بينهما **مستند واحد وآلية تحقّق واحدة**.
 */
internal enum class MaxBackupMode { APPS, SYSTEM }

/**
 * صفحات الشاشة الرئيسية.
 *
 * والفصل مقصود: `HOME` تعرض الخيارات، و`APPS` تعرض قائمة إدارتها، و`SYSTEM` بيانات
 * النظام. ودخول تطبيق بعينه لا يمرّ من هنا أصلًا (له تفصيله المستقلّ).
 */
internal enum class MaxBackupPage { HOME, APPS, SYSTEM }

@Composable
fun MaxBackupScreen(
    navController: NavController,
    packageName: String? = null,
) {
    // بمعرّف حزمة: نسخة تطبيق واحدة، وبلا صفحات (المستخدم طلب تطبيقًا بعينه).
    if (!packageName.isNullOrBlank()) {
        MaxBackupDetail(navController, packageName)
        return
    }

    // الصفحة الأولى **خيارات لا قائمة**: ارجع إلى وصف الملف أعلى الشاشة — كان الهبوط
    // المباشر في قائمة تطبيقات يجعل النسخ يبدو تلقائيًّا، وهو ما لا يفعله هذا التطبيق.
    var page by rememberSaveable { mutableStateOf(MaxBackupPage.HOME) }
    when (page) {
        MaxBackupPage.HOME -> MaxBackupHub(
            navController = navController,
            onOpenApps = { page = MaxBackupPage.APPS },
            onOpenSystem = { page = MaxBackupPage.SYSTEM },
        )

        MaxBackupPage.APPS -> MaxBackupAppsPicker(
            navController = navController,
            onBack = { page = MaxBackupPage.HOME },
            onSwitchMode = { page = MaxBackupPage.SYSTEM },
        )

        MaxBackupPage.SYSTEM -> MaxBackupSystemMode(
            onSwitchMode = { page = MaxBackupPage.APPS },
            // الرجوع يعود إلى الخيارات لا يخرج من Max Backup: الصفحة الأولى صارت
            // خيارات، والخروج بقفزة واحدة منها يفقد المستخدم مكانه بلا سبب.
            onBack = { page = MaxBackupPage.HOME },
        )
    }
}

/** مبدّل النمطين — يُعرض في ترويسة الشاشتين بنفس النصّ والترتيب، فلا يبدوان تطبيقين. */
@Composable
internal fun MaxBackupModeSwitch(mode: MaxBackupMode, onSelect: (MaxBackupMode) -> Unit) {
    MaxSegmented(
        options = listOf(
            stringResource(R.string.max_backup_mode_apps),
            stringResource(R.string.max_backup_mode_system),
        ),
        selectedIndex = mode.ordinal,
        onSelect = { onSelect(MaxBackupMode.entries[it]) },
    )
}

// ────────────────────────────────────────────────────────────────────────────
// تفصيل تطبيق
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun MaxBackupDetail(navController: NavController, pkg: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var plan by remember(pkg) { mutableStateOf<MaxBackupModel.Plan?>(null) }
    var backups by remember(pkg) { mutableStateOf<List<MaxBackupModel.Handle>>(emptyList()) }
    var storage by remember(pkg) { mutableStateOf<MaxBackupStorageReadout?>(null) }
    var inventoryLoaded by remember(pkg) { mutableStateOf(false) }
    var busyStage by remember(pkg) { mutableStateOf<String?>(null) }

    var appScope by remember(pkg) { mutableStateOf(MaxBackupModel.Scope(apk = true, appData = false, externalData = false, obb = false)) }

    var askCreate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf<MaxBackupModel.Handle?>(null) }
    var askPrune by remember { mutableStateOf(false) }
    var restorePrompt by remember { mutableStateOf<RestorePrompt?>(null) }
    var blocked by remember { mutableStateOf<MaxBackupModel.RestoreBlock?>(null) }
    var verdicts by remember { mutableStateOf<Pair<MaxBackupModel.Handle, Map<String, MaxBackupModel.Integrity>>?>(null) }

    val label = remember(pkg) {
        runCatching {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            info.loadLabel(context.packageManager).toString()
        }.getOrDefault(pkg)
    }

    suspend fun reload() {
        val loaded = withContext(Dispatchers.IO) {
            val loadedPlan = MaxBackupEngine.inventory(context, pkg)
            val loadedBackups = MaxBackupEngine.list(context, pkg)
            val loadedStorage = readStorage(context, loadedBackups)
            Triple(loadedPlan, loadedBackups, loadedStorage)
        }
        val loadedPlan = loaded.first
        plan = loadedPlan
        backups = loaded.second
        storage = loaded.third
        inventoryLoaded = true
        // النطاق الافتراضي يتبع الصلاحية المتاحة فعلًا: لا نُشعل ما لا يعمل.
        if (loadedPlan != null && busyStage == null) {
            appScope = MaxBackupModel.Scope.forPrivilege(loadedPlan.hasRoot)
        }
    }

    LaunchedEffect(pkg) { reload() }

    val busy = busyStage != null
    val busyReason = stringResource(R.string.max_backup_working_detail)
    // أسماء المراحل تُحلّ **في التركيب** لا داخل كوروتين: `stringResource` ليست متاحة هناك،
    // وقيمتها لا تتغيّر بعد التركيب فتحميلها مرّة واحدة هو الصواب.
    val stageLabels = stageLabels()

    val condition = when {
        !inventoryLoaded -> MaxCondition(
            kind = MaxConditionKind.Loading,
            title = stringResource(R.string.max_backup_plan_loading_title),
            detail = stringResource(R.string.max_backup_plan_loading_detail),
        )

        plan == null -> MaxCondition(
            kind = MaxConditionKind.Empty,
            title = stringResource(R.string.max_backup_plan_missing_title),
            detail = stringResource(R.string.max_backup_plan_missing_detail),
            primaryActionLabel = stringResource(R.string.max_action_retry),
            onPrimaryAction = { scope.launch { reload() } },
        )

        else -> null
    }

    val banner = busyStage?.let {
        MaxCondition(
            kind = MaxConditionKind.Applying,
            title = stageLabel(stageLabels, it),
            detail = busyReason,
        )
    }

    val needRootForData = backups.any { !it.complete }

    MaxScreen(
        title = label,
        subtitle = stringResource(R.string.max_backup_title),
        onBack = { navController.popBackStack() },
        accentIcon = Icons.Rounded.Backup,
        condition = condition,
        banner = banner,
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(onClick = { scope.launch { reload() } }, enabled = !busy) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.max_action_refresh),
                )
            }
        },
    ) {
        val loadedPlan = plan ?: return@MaxScreen

        // ── الصلاحية ────────────────────────────────────────────────────────
        MaxSection(title = stringResource(R.string.max_backup_priv_title)) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_backup_priv_layer),
                    subtitle = stringResource(
                        if (loadedPlan.hasRoot) {
                            R.string.max_backup_priv_root
                        } else {
                            R.string.max_backup_priv_no_root
                        }
                    ),
                    icon = Icons.Rounded.Shield,
                    iconTone = if (loadedPlan.hasRoot) MaxTone.Positive else MaxTone.Caution,
                )
                MaxGroupDivider()
                MaxRow(
                    title = stringResource(R.string.max_backup_priv_encryption),
                    subtitle = stringResource(R.string.max_backup_no_encryption_desc),
                    icon = Icons.Rounded.CloudOff,
                    iconTone = MaxTone.Neutral,
                )
            }
        }

        // ── الجرد ───────────────────────────────────────────────────────────
        MaxSection(
            title = stringResource(R.string.max_backup_plan_title),
            description = stringResource(R.string.max_backup_plan_desc),
        ) {
            MaxGroup {
                loadedPlan.components.forEachIndexed { index, component ->
                    if (index > 0) MaxGroupDivider()
                    MaxRow(
                        title = kindText(component.kind),
                        subtitle = buildString {
                            append(availabilityText(component.availability))
                            component.sizeBytes?.let { append(" · ").append(MaxBackupModel.humanBytes(it)) }
                            if (component.sizeBytes == null && component.availability != MaxBackupModel.Availability.UNAVAILABLE) {
                                append(" · ").append(stringResource(R.string.max_backup_plan_unmeasured))
                            }
                        },
                        icon = Icons.AutoMirrored.Rounded.FactCheck,
                        iconTone = when (component.availability) {
                            MaxBackupModel.Availability.AVAILABLE -> MaxTone.Positive
                            MaxBackupModel.Availability.NEEDS_ROOT -> MaxTone.Caution
                            else -> MaxTone.Neutral
                        },
                    )
                }
            }

            if (loadedPlan.hasUnmeasured) {
                MaxBullets(lines = listOf(stringResource(R.string.max_backup_plan_estimated)))
            }

            val previewTotal = loadedPlan.selected(appScope).sumOf { it.sizeBytes ?: 0L }
            MaxBullets(
                lines = listOf(
                    stringResource(
                        R.string.max_backup_plan_selected,
                        MaxBackupModel.humanBytes(previewTotal),
                        MaxBackupModel.humanBytes(loadedPlan.knownBytes),
                    )
                )
            )
        }

        // ── النطاق ──────────────────────────────────────────────────────────
        MaxSection(
            title = stringResource(R.string.max_backup_scope_title),
            description = stringResource(R.string.max_backup_scope_desc),
        ) {
            MaxGroup {
                MaxSwitchRow(
                    title = kindText(MaxBackupModel.ComponentKind.APK),
                    checked = appScope.apk,
                    enabled = !busy,
                    lockedReason = if (busy) busyReason else null,
                    onCheckedChange = { appScope = appScope.copy(apk = it) },
                )
                MaxGroupDivider()
                MaxSwitchRow(
                    title = kindText(MaxBackupModel.ComponentKind.APP_DATA),
                    checked = appScope.appData,
                    enabled = !busy && loadedPlan.hasRoot,
                    lockedReason = when {
                        busy -> busyReason
                        !loadedPlan.hasRoot -> stringResource(R.string.max_backup_block_root)
                        else -> null
                    },
                    onCheckedChange = { appScope = appScope.copy(appData = it) },
                )
                MaxGroupDivider()
                MaxSwitchRow(
                    title = kindText(MaxBackupModel.ComponentKind.EXTERNAL_DATA),
                    checked = appScope.externalData,
                    enabled = !busy && loadedPlan.hasRoot,
                    lockedReason = when {
                        busy -> busyReason
                        !loadedPlan.hasRoot -> stringResource(R.string.max_backup_block_root)
                        else -> null
                    },
                    onCheckedChange = { appScope = appScope.copy(externalData = it) },
                )
                MaxGroupDivider()
                MaxSwitchRow(
                    title = kindText(MaxBackupModel.ComponentKind.OBB),
                    checked = appScope.obb,
                    enabled = !busy && loadedPlan.hasRoot,
                    lockedReason = when {
                        busy -> busyReason
                        !loadedPlan.hasRoot -> stringResource(R.string.max_backup_block_root)
                        else -> null
                    },
                    onCheckedChange = { appScope = appScope.copy(obb = it) },
                )
            }
        }

        // ── الإنشاء ─────────────────────────────────────────────────────────
        MaxSection(title = stringResource(R.string.max_backup_create_section)) {
            MaxGroup {
                MaxRow(
                    title = stringResource(R.string.max_backup_action_create),
                    subtitle = stringResource(R.string.max_backup_action_create_desc),
                    icon = Icons.Rounded.Backup,
                    iconTone = MaxTone.Accent,
                    enabled = !busy && appScope.anySelected,
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

        // ── السجل ───────────────────────────────────────────────────────────
        MaxSection(
            title = stringResource(R.string.max_backup_history_title),
            description = stringResource(R.string.max_backup_history_desc),
        ) {
            if (backups.isEmpty()) {
                MaxGroup {
                    MaxRow(
                        title = stringResource(R.string.max_backup_history_none),
                        icon = Icons.Rounded.Backup,
                        iconTone = MaxTone.Neutral,
                    )
                }
            } else {
                MaxGroup {
                    backups.forEachIndexed { index, handle ->
                        if (index > 0) MaxGroupDivider()
                        BackupHistoryRow(
                            handle = handle,
                            title = backupStamp(handle.createdAtMs),
                            enabled = !busy,
                            onToggleKeep = {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        MaxBackupEngine.setKeptForever(handle, !handle.keptForever)
                                    }
                                    reload()
                                    // الرسالة من **قيمة ما قبل التبديل**: إن كانت محفوظة فقد
                                    // أُزيل وسمها، والعكس.
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            when {
                                                !ok -> R.string.max_backup_keep_failed
                                                handle.keptForever -> R.string.max_backup_keep_off
                                                else -> R.string.max_backup_keep_on
                                            }
                                        )
                                    )
                                }
                            },
                            busyReason = busyReason,
                            onVerify = {
                                scope.launch {
                                    val report = withContext(Dispatchers.IO) {
                                        handle to MaxBackupEngine.verify(handle)
                                    }
                                    verdicts = report
                                }
                            },
                            onRestore = {
                                scope.launch {
                                    val decision = withContext(Dispatchers.IO) {
                                        MaxBackupEngine.decisionFor(context, handle)
                                    }
                                    if (decision == null || !decision.allowed) {
                                        blocked = decision?.block
                                            ?: MaxBackupModel.RestoreBlock.NO_ENTRIES
                                    } else {
                                        restorePrompt = RestorePrompt(handle, decision)
                                    }
                                }
                            },
                            onDelete = { askDelete = handle },
                        )
                    }
                }
            }
        }

        // ── التخزين ─────────────────────────────────────────────────────────
        // بطاقة واحدة مشتركة مع الشاشة الرئيسية: مسار واحد لا مساران يُصانان معًا
        // وينفصلان عند أول تعديل (`onGrant = null` لأن منح الصلاحية في الشاشة الرئيسية).
        MaxBackupStorageSection(readout = storage, onGrant = null)
        if (needRootForData) {
            MaxBullets(lines = listOf(stringResource(R.string.max_backup_incomplete_note)))
        }

        if (verdicts != null) {
            val report = verdicts!!
            MaxSection(
                title = stringResource(R.string.max_backup_verify_title),
                description = MaxBackupModel.folderName(report.first.createdAtMs),
            ) {
                MaxGroup {
                    report.second.entries.forEachIndexed { index, entry ->
                        if (index > 0) MaxGroupDivider()
                        MaxRow(
                            title = entry.key,
                            subtitle = integrityText(entry.value),
                            icon = if (entry.value == MaxBackupModel.Integrity.VERIFIED) {
                                Icons.AutoMirrored.Rounded.FactCheck
                            } else {
                                Icons.Rounded.WarningAmber
                            },
                            iconTone = if (entry.value == MaxBackupModel.Integrity.VERIFIED) {
                                MaxTone.Positive
                            } else {
                                MaxTone.Critical
                            },
                        )
                    }
                }
            }
        }
    }

    // ── حوارات ──────────────────────────────────────────────────────────────

    val loadedPlan = plan
    MaxConfirmDialog(
        visible = askCreate && loadedPlan != null,
        title = stringResource(R.string.max_backup_create_confirm_title),
        message = stringResource(
            R.string.max_backup_create_confirm_message,
            MaxBackupModel.humanBytes(loadedPlan?.selected(appScope)?.sumOf { it.sizeBytes ?: 0L } ?: 0L),
        ),
        confirmLabel = stringResource(R.string.max_backup_action_create),
        onConfirm = {
            askCreate = false
            val target = loadedPlan ?: return@MaxConfirmDialog
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    val result = MaxBackupEngine.create(context, target, appScope) { stage ->
                        busyStage = stage
                    }
                    MaxBackupEngine.prune(context, pkg, KEEP_VERSIONS)
                    result
                }
                busyStage = null
                reload()
                val message = when {
                    outcome.success -> context.getString(R.string.max_backup_msg_created)
                    outcome.entryCount > 0 -> context.getString(R.string.max_backup_msg_created_partial)
                    else -> context.getString(R.string.max_backup_msg_failed)
                }
                snackbarHostState.showSnackbar(message)
            }
        },
        onDismiss = { askCreate = false },
    )

    askDelete?.let { handle ->
        MaxConfirmDialog(
            visible = true,
            title = stringResource(R.string.max_backup_delete_title),
            // نسخة محفوظة: نقول ذلك في الحوار نفسه، فنعلم أن التقليم لم يكن ليحذفها وأن
            // الحذف الآن فعلٌ مقصود لا أثر جانبي لتنظيف.
            message = stringResource(
                if (handle.keptForever) {
                    R.string.max_backup_delete_kept_message
                } else {
                    R.string.max_backup_delete_message
                }
            ),
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
        message = stringResource(R.string.max_backup_prune_message, KEEP_VERSIONS.toString()),
        confirmLabel = stringResource(R.string.max_backup_prune_confirm),
        destructive = true,
        onConfirm = {
            askPrune = false
            scope.launch {
                val deleted = withContext(Dispatchers.IO) {
                    MaxBackupEngine.prune(context, pkg, KEEP_VERSIONS)
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
        // التحذيرات تُعرَض قبل الكتابة لا بعدها: «أُخذت على جهاز آخر» معلومة تُغيّر القرار،
        // وحجبها حتى يفشل الاسترجاع يهدر آخر لحظة كان المستخدم يستطيع التراجع فيها.
        val baseMessage = stringResource(R.string.max_backup_restore_message)
        val warningTitle = stringResource(R.string.max_backup_warn_title)
        val warningLines = prompt.decision.warnings.map { warningText(it) }
        val restoreMessage = if (warningLines.isEmpty()) {
            baseMessage
        } else {
            baseMessage + "\n\n" + warningTitle + "\n" + warningLines.joinToString("\n")
        }
        MaxConfirmDialog(
            visible = true,
            title = stringResource(R.string.max_backup_restore_title),
            message = restoreMessage,
            confirmLabel = stringResource(R.string.max_backup_restore_confirm),
            destructive = true,
            technicalDetail = MaxBackupModel.folderName(prompt.handle.createdAtMs),
            onConfirm = {
                restorePrompt = null
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        MaxBackupEngine.restore(context, prompt.handle) { stage -> busyStage = stage }
                    }
                    busyStage = null
                    snackbarHostState.showSnackbar(
                        when {
                            outcome.success -> context.getString(R.string.max_backup_restore_done)
                            outcome.failedStage != null -> context.getString(
                                R.string.max_backup_restore_failed,
                                stageLabel(stageLabels, outcome.failedStage),
                            )
                            else -> context.getString(R.string.max_backup_restore_failed, "")
                        }
                    )
                }
            },
            onDismiss = { restorePrompt = null },
        )
    }

    blocked?.let { reason ->
        MaxConfirmDialog(
            visible = true,
            title = stringResource(R.string.max_backup_restore_blocked_title),
            message = blockText(reason),
            confirmLabel = stringResource(android.R.string.ok),
            onConfirm = { blocked = null },
            onDismiss = { blocked = null },
        )
    }
}

/** نسخة واحدة مع قرارها — تُبنى في الخلفية لأنها تقرأ القرص وتتحقّق من البصمات. */
private data class RestorePrompt(
    val handle: MaxBackupModel.Handle,
    val decision: MaxBackupModel.RestoreDecision,
)

/**
 * كم نسخة نحتفظ بها لكل تطبيق. ليست قابلًا للتعديل هنا: الحدّ الأدنى ١ غير قابل للكسر.
 *
 * `internal` لا `private`: المنتقي يستعملها بعد النسخ الجماعي، والمصدر واحد فلا
 * يختلف الاحتفاظ بين مسار ومسار.
 */
internal const val KEEP_VERSIONS = 3

// ────────────────────────────────────────────────────────────────────────────
// صفوف مساعدة
// ────────────────────────────────────────────────────────────────────────────

/**
 * صفّ نسخة واحدة مع أدواتها (فحص · استرجاع · حذف).
 *
 * @param title ما يميّز هذه النسخة في مكان عرضها: اسم التطبيق في الشاشة الرئيسية
 *        (حيث تختلط نسخ كل التطبيقات)، وطابعها الزمني في تفصيل تطبيق واحد (حيث الاسم
 *        مكرّر في كل صف بلا فائدة). ولذلك عنوان لا طابع ثابت.
 */
@Composable
internal fun BackupHistoryRow(
    handle: MaxBackupModel.Handle,
    title: String,
    enabled: Boolean,
    busyReason: String,
    onVerify: () -> Unit,
    onRestore: () -> Unit,
    onToggleKeep: () -> Unit,
    onDelete: () -> Unit,
) {
    val summary = stringResource(
        R.string.max_backup_entry_summary,
        MaxBackupModel.humanBytes(handle.bytes),
        handle.entryCount.toString(),
    )
    // الوسوم تُبنى قائمةً ثم تُلحق: النسخة قد تكون ناقصة **ومحفوظة** معًا، و«إحداهما تحلّ
    // محلّ الأخرى» تكذب على المستخدم في واحدة منهما.
    val flags = buildList {
        if (!handle.complete) add(stringResource(R.string.max_backup_incomplete))
        if (handle.keptForever) add(stringResource(R.string.max_backup_keep_badge))
    }
    val subtitle = if (flags.isEmpty()) summary else summary + " · " + flags.joinToString(" · ")

    MaxRow(
        title = title,
        subtitle = subtitle,
        icon = Icons.Rounded.Backup,
        iconTone = if (handle.complete) MaxTone.Positive else MaxTone.Caution,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)) {
                IconButton(onClick = onToggleKeep, enabled = enabled) {
                    Icon(
                        imageVector = if (handle.keptForever) {
                            Icons.Rounded.Lock
                        } else {
                            Icons.Rounded.LockOpen
                        },
                        contentDescription = stringResource(R.string.max_backup_keep_title),
                        tint = if (handle.keptForever) {
                            MaxTone.Accent.content()
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = onVerify, enabled = enabled) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.FactCheck,
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

// ────────────────────────────────────────────────────────────────────────────
// مفردات مُترجَمة (لا نصوص صلبة في Text)
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun kindText(kind: MaxBackupModel.ComponentKind): String = stringResource(
    when (kind) {
        MaxBackupModel.ComponentKind.APK -> R.string.max_backup_kind_apk
        MaxBackupModel.ComponentKind.SPLIT_APK -> R.string.max_backup_kind_split
        MaxBackupModel.ComponentKind.APP_DATA -> R.string.max_backup_kind_data
        MaxBackupModel.ComponentKind.EXTERNAL_DATA -> R.string.max_backup_kind_external
        MaxBackupModel.ComponentKind.OBB -> R.string.max_backup_kind_obb
        // لا تظهر في نسخة تطبيق: نطاق بيانات النظام له شاشته ونصّه. لكن `when` يجب أن تغطّيها.
        MaxBackupModel.ComponentKind.SYSTEM -> R.string.max_backup_kind_system
    }
)

@Composable
private fun availabilityText(availability: MaxBackupModel.Availability): String = stringResource(
    when (availability) {
        MaxBackupModel.Availability.AVAILABLE -> R.string.max_backup_avail_available
        MaxBackupModel.Availability.NEEDS_ROOT -> R.string.max_backup_avail_needs_root
        MaxBackupModel.Availability.UNAVAILABLE -> R.string.max_backup_avail_unavailable
        MaxBackupModel.Availability.UNKNOWN -> R.string.max_backup_avail_unknown
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

@Composable
internal fun blockText(block: MaxBackupModel.RestoreBlock): String = stringResource(
    when (block) {
        MaxBackupModel.RestoreBlock.NONE -> R.string.max_backup_block_none
        MaxBackupModel.RestoreBlock.NO_ENTRIES -> R.string.max_backup_block_no_entries
        MaxBackupModel.RestoreBlock.INCOMPLETE -> R.string.max_backup_block_incomplete
        MaxBackupModel.RestoreBlock.NOT_VERIFIED -> R.string.max_backup_block_not_verified
        MaxBackupModel.RestoreBlock.ROOT_REQUIRED -> R.string.max_backup_block_root
    }
)

/**
 * مراحل العمل. المعرّف يأتي من المحرّك غير مترجم، والترجمة هنا وحدها — فالمحرّك لا يحمل نصًّا
 * واجهيًّا، والشاشة لا تحمل منطقًا. والمفتاح بينهما معرّف مُعلَن، لا جملة إنجليزية تُقارَن بمثلها.
 */
@Composable
internal fun stageLabels(): Map<String, String> = mapOf(
    MaxBackupEngine.STAGE_APK to stringResource(R.string.max_backup_stage_apk),
    MaxBackupEngine.STAGE_DATA to stringResource(R.string.max_backup_stage_data),
    MaxBackupEngine.STAGE_EXTERNAL to stringResource(R.string.max_backup_stage_external),
    MaxBackupEngine.STAGE_OBB to stringResource(R.string.max_backup_stage_obb),
    MaxBackupEngine.STAGE_MANIFEST to stringResource(R.string.max_backup_stage_manifest),
)

internal fun stageLabel(labels: Map<String, String>, stage: String): String =
    labels[stage] ?: labels.getValue(MaxBackupEngine.STAGE_MANIFEST)

/** تحذيرات الاسترجاع — كل واحدة جملة كاملة تُقرأ وحدها، لا رمزًا يُفكّ. */
@Composable
internal fun warningText(warning: MaxBackupModel.RestoreWarning): String = stringResource(
    when (warning) {
        MaxBackupModel.RestoreWarning.DEVICE_MISMATCH -> R.string.max_backup_warn_device
        MaxBackupModel.RestoreWarning.SOC_MISMATCH -> R.string.max_backup_warn_soc
        MaxBackupModel.RestoreWarning.APP_VERSION_DIFFERS -> R.string.max_backup_warn_version
        MaxBackupModel.RestoreWarning.PACKAGE_NOT_INSTALLED -> R.string.max_backup_warn_not_installed
        MaxBackupModel.RestoreWarning.ENCRYPTED_ARCHIVE -> R.string.max_backup_warn_encrypted
    }
)
