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
 * Max Backup — الشاشة الرئيسية.
 *
 * **لماذا وُجدت:** كان فتح `Max Backup` يهبط بالمستخدم مباشرةً في قائمة تطبيقات
 * وتفصيل تطبيق، فيبدو كأن شيئًا يُنسخ من تلقاء نفسه، ولم يكن في الشاشة زرٌّ واحد
 * يقول «احفظ نسخة الآن» أو «استعد أحدث نسخة». هذه الشاشة تعرض **خيارات أولًا**:
 *
 *  - تبويبان: **نسخ** و**استرجاع** — والاختيار بينهما هو أول قرار، لا آخر خطوة.
 *  - فئتا النسخ: التطبيقات، وبيانات النظام.
 *  - إجراءان صريحان: حفظ نسخة الآن (يفتح المنتقي)، واستعادة أحدث نسخة.
 *  - مكان الأرشيف مكتوبًا بمساره الكامل، مع ما يشغله وما هو متاح.
 *  - أحدث النسخ مع استرجاع لكل واحدة.
 *
 * ولا شيء هنا يكتب عتادًا: كل عملية تمرّ بـ`MaxBackupEngine` (ADR-11).
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.ui.design.MaxBullets
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxConditionNotice
import nd.max.ui.design.MaxConfirmDialog
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSegmented
import nd.max.ui.design.MaxTone
import nd.max.ui.util.MaxBackupCounts
import nd.max.ui.util.MaxBackupEngine
import nd.max.ui.util.MaxBackupFolders
import nd.max.ui.util.MaxBackupModel
import nd.max.ui.util.MaxBackupSchedule
import nd.max.ui.util.MaxBackupScheduler
import nd.max.ui.util.MaxBackupStorage
import nd.max.ui.viewmodel.ApplistViewmodel

// ────────────────────────────────────────────────────────────────────────────
// حالة التخزين — تُقرأ في الخلفية، لا في التركيب
// ────────────────────────────────────────────────────────────────────────────

/**
 * ما تعرفه الشاشة عن مكان الأرشيف.
 *
 * `folderPath` هو **ما استُعمل فعلًا** لا ما طُلب، و`requestedPath` هو المطلوب. والفارق
 * بينهما ليس تفصيلًا: هو سبب وجود صفّ الصلاحية في الشاشة.
 */
internal data class MaxBackupStorageReadout(
    val folderPath: String,
    val requestedPath: String,
    val onRequestedFolder: Boolean,
    val archiveBytes: Long,
    val backupCount: Int,
    val freeBytes: Long?,
    val totalBytes: Long?,
)

/** النسخ وقراءة التخزين في مرور واحد على القرص، لا مرور لكل بطاقة. */
internal data class MaxBackupSnapshot(
    val handles: List<MaxBackupModel.Handle>,
    val storage: MaxBackupStorageReadout,
)

internal suspend fun readBackupSnapshot(context: Context): MaxBackupSnapshot = withContext(Dispatchers.IO) {
    val handles = MaxBackupEngine.list(context)
    MaxBackupSnapshot(handles = handles, storage = readStorage(context, handles))
}

/**
 * قراءة بطاقة التخزين من قائمة نسخ **محمّلة سلفًا**.
 *
 * ولماذا تأخذ النسخ ولا تمسح القرص بنفسها: تفصيل التطبيق يقرأ نسخه أصلًا، فمسح ثانٍ
 * لكل النسخ لأجل بطاقة يعني قراءةً مضاعفة على جهاز بطيء الإدخال لأجل معلومة موجودة.
 */
internal suspend fun readStorage(
    context: Context,
    handles: List<MaxBackupModel.Handle>,
): MaxBackupStorageReadout = withContext(Dispatchers.IO) {
    // الجذر يُثبَّت بفحص كتابة هنا — وهذه دالة قرص، فالأمر في مكانه.
    val folder = MaxBackupEngine.ensureRoot(context)
    val stat = runCatching { StatFs(folder.absolutePath) }.getOrNull()
    MaxBackupStorageReadout(
        folderPath = folder.absolutePath,
        requestedPath = MaxBackupEngine.requestedRoot().absolutePath,
        onRequestedFolder = MaxBackupStorage.isRequested(folder),
        archiveBytes = handles.sumOf { it.bytes },
        backupCount = handles.size,
        freeBytes = stat?.let { runCatching { it.availableBytes }.getOrNull() },
        totalBytes = stat?.let { runCatching { it.totalBytes }.getOrNull() },
    )
}

/**
 * شاشة منح «الوصول لكل الملفات» لهذا التطبيق.
 * `null` على ما قبل أندرويد ١١: القيد غير موجود أصلًا، فلا معنى لطلب صلاحية تحلّه.
 */
internal fun publicStorageGrantIntent(context: Context): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    return Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )
}

/** الشاشة العامة للصلاحية، تُجرَّب بعد شاشة التطبيق لأن بعض الرومات تلغي الأولى. */
private fun publicStorageListIntent(): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
}

// ────────────────────────────────────────────────────────────────────────────
// بطاقة التخزين — مشتركة بين الشاشة الرئيسية وتفصيل التطبيق
// ────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MaxBackupStorageSection(
    readout: MaxBackupStorageReadout?,
    onGrant: (() -> Unit)?,
) {
    val unknown = stringResource(R.string.max_backup_storage_size_unknown)
    val freeText = readout?.freeBytes?.let { free ->
        readout.totalBytes?.let { total ->
            stringResource(
                R.string.max_backup_storage_free,
                MaxBackupModel.humanBytes(free),
                MaxBackupModel.humanBytes(total),
            )
        }
    } ?: unknown

    MaxSection(
        title = stringResource(R.string.max_backup_storage_title),
        description = stringResource(
            if (readout?.onRequestedFolder == true) {
                R.string.max_backup_storage_public_ok
            } else {
                R.string.max_backup_storage_location_desc
            }
        ),
    ) {
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_backup_storage_path),
                subtitle = readout?.folderPath,
                icon = Icons.Rounded.Folder,
                iconTone = MaxTone.Neutral,
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_backup_storage_used),
                subtitle = readout?.let { MaxBackupModel.humanBytes(it.archiveBytes) },
                icon = Icons.Rounded.DataUsage,
                iconTone = MaxTone.Neutral,
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_backup_storage_count),
                subtitle = readout?.backupCount?.toString(),
                icon = Icons.AutoMirrored.Rounded.FactCheck,
                iconTone = MaxTone.Neutral,
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_backup_storage_free_title),
                subtitle = freeText,
                icon = Icons.Rounded.Storage,
                iconTone = MaxTone.Neutral,
            )
        }

        // الصلاحية ناقصة: نقول أين كُتبت النسخ الآن، وأين ستُكتب بعد منحها، ونعطي الزرّ.
        if (readout != null && !readout.onRequestedFolder) {
            MaxConditionNotice(
                MaxCondition(
                    kind = MaxConditionKind.PermissionRequired,
                    title = stringResource(R.string.max_backup_storage_fallback_title),
                    detail = stringResource(
                        R.string.max_backup_storage_fallback_desc,
                        readout.requestedPath,
                        readout.folderPath,
                    ),
                    technicalDetail = readout.folderPath,
                    primaryActionLabel = onGrant?.let { stringResource(R.string.max_backup_storage_grant) },
                    onPrimaryAction = onGrant,
                )
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// الشاشة الرئيسية
// ────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MaxBackupHub(
    navController: NavController,
    onOpenApps: () -> Unit,
    onOpenSystem: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val appListViewModel: ApplistViewmodel = viewModel()

    var tab by rememberSaveable { mutableStateOf(0) }
    var snapshot by remember { mutableStateOf<MaxBackupSnapshot?>(null) }
    var busyStage by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<HubRestorePrompt?>(null) }
    var blocked by remember { mutableStateOf<MaxBackupModel.RestoreBlock?>(null) }
    var askDelete by remember { mutableStateOf<MaxBackupModel.Handle?>(null) }
    var askRestoreDevice by remember { mutableStateOf(false) }
    var deviceRun by remember { mutableStateOf<DeviceRun?>(null) }
    var sweepProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var sweepResult by remember { mutableStateOf<SweepResult?>(null) }

    // `OCR-01`/`OCR-02`: مجموعات المجلدات والخطة المجدولة. تُقرأ من ملفّين صغيرين في مجلد
    // التطبيق، وتُحفظ عند كل تغيير — فلا تنسيق مزدوج ولا حالة تعيش في الذاكرة وحدها.
    var folderSets by remember { mutableStateOf<List<MaxBackupFolders.FolderSet>>(emptyList()) }
    var plan by remember { mutableStateOf(MaxBackupSchedule.Plan()) }
    var policyBusy by remember { mutableStateOf(false) }
    // `OCR-06`: هل أظهر المستخدم بقية النسخ؟ يُحفظ عبر إعادة التركيب لأن طيّ القائمة بعد
    // تمرير طويل يُفقد المكان الذي كان يقرأ فيه.
    var showAllCopies by rememberSaveable { mutableStateOf(false) }

    suspend fun reload() {
        snapshot = readBackupSnapshot(context)
    }

    suspend fun reloadPolicy() {
        val loaded = withContext(Dispatchers.IO) {
            MaxBackupScheduler.readSets(context) to MaxBackupScheduler.readPlan(context)
        }
        folderSets = loaded.first
        plan = loaded.second
    }

    // على كل عودة إلى الواجهة: قد يكون المستخدم منح الصلاحية في الإعدادات، والقرار
    // المخزَّن يقول غير ذلك. إسقاطه ثم إعادة القراءة هو ما يجعل المنح يسري بلا إعادة تشغيل.
    LifecycleResumeEffect(Unit) {
        MaxBackupEngine.invalidateStorageRoot()
        appListViewModel.loadApps(context)
        scope.launch {
            // السلسلة المجدولة تُستأنف عند كل فتح للشاشة: بعد إقلاع، أو بعد أن يُلغي النظام
            // مهمة، أو بعد أن يحذف المستخدم المهمة من إعدادات النظام.
            withContext(Dispatchers.IO) { MaxBackupScheduler.rearm(context) }
            reloadPolicy()
        }
        scope.launch { reload() }
        onPauseOrDispose { }
    }

    val handles = snapshot?.handles.orEmpty()
    val loading = snapshot == null
    val newest = handles.firstOrNull()
    val installed = ApplistViewmodel.apps
    val labels = remember(installed) { installed.associate { it.packageName to it.label } }
    val systemLabel = stringResource(R.string.max_backup_kind_system)
    val busyReason = stringResource(R.string.max_backup_working_detail)
    val stageLabels = stageLabels()

    // اسم المستند أولًا لأنه يصف النسخة **كما أُخذت**، ثم القائمة المثبّتة، ثم المعرّف.
    // والترتيب مقصود: نسخة تطبيق أُزيل لاحقًا لا اسم لها في القائمة المثبّتة.
    fun labelOf(handle: MaxBackupModel.Handle): String {
        val recorded = handle.label?.takeIf { it.isNotBlank() }
        return when {
            handle.pkg == MaxBackupEngine.SYSTEM_PKG -> systemLabel
            recorded != null -> recorded
            labels[handle.pkg] != null -> labels.getValue(handle.pkg)
            else -> handle.pkg
        }
    }

    fun grantPublicStorage() {
        val appScreen = publicStorageGrantIntent(context)
        val opened = appScreen != null && runCatching { context.startActivity(appScreen) }.isSuccess
        if (opened) return
        val listScreen = publicStorageListIntent()
        val openedList = listScreen != null && runCatching { context.startActivity(listScreen) }.isSuccess
        if (!openedList) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.max_backup_storage_grant_failed)) }
        }
    }

    /**
     * يحفظ المجموعات، وإن حُذفت مجموعة **يُنقّي الخطة من اسمها** ثم يُسلّح الجدول من جديد.
     *
     * ولماذا التنقية هنا لا في المحرّك: اسم مجموعة محذوفة يبقى هدفًا في الخطة، فتظهر في
     * الشاشة «لم يعد موجودًا» وتُتخطّى في كل تشغيل. التنقية تجعل الحالتين متطابقتين: ما تراه
     * في الشاشة هو ما سيُنفَّذ.
     */
    fun persistSets(next: List<MaxBackupFolders.FolderSet>, removed: String? = null) {
        policyBusy = true
        val cleaned = if (removed != null) plan.copy(sets = plan.sets - removed) else plan
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                val written = MaxBackupScheduler.writeSets(context, next)
                if (removed != null) MaxBackupScheduler.apply(context, cleaned)
                written
            }
            policyBusy = false
            if (ok) {
                folderSets = next
                if (removed != null) plan = cleaned
            } else {
                snackbarHostState.showSnackbar(context.getString(R.string.max_backup_set_save_failed))
            }
        }
    }

    /**
     * يثبّت الخطة. الحالة تُحدَّث **قبل** الكتابة لأن الشاشة يجب أن تستجيب فورًا، ثم يُعاد
     * الناتج المُنقّى من `apply` ليصير المعروض هو المخزَّن بالضبط.
     */
    fun persistPlan(next: MaxBackupSchedule.Plan) {
        plan = next
        scope.launch {
            plan = withContext(Dispatchers.IO) { MaxBackupScheduler.apply(context, next) }
        }
    }

    /** تشغيل الجدول بيد المستخدم: بلا شروط البطارية والشبكة، لأنه طلب النسخة الآن. */
    fun runScheduleNow() {
        policyBusy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                MaxBackupScheduler.runNow(context) { stage -> busyStage = stage }
            }
            policyBusy = false
            busyStage = null
            plan = withContext(Dispatchers.IO) { MaxBackupScheduler.readPlan(context) }
            reload()
            snackbarHostState.showSnackbar(
                context.getString(
                    when (result) {
                        MaxBackupSchedule.Result.OK -> R.string.max_backup_schedule_done_ok
                        MaxBackupSchedule.Result.FAILED -> R.string.max_backup_schedule_done_failed
                        MaxBackupSchedule.Result.SKIPPED -> R.string.max_backup_schedule_done_skipped
                    }
                )
            )
        }
    }

    fun requestRestore(handle: MaxBackupModel.Handle) {
        scope.launch {
            val decision = withContext(Dispatchers.IO) { MaxBackupEngine.decisionFor(context, handle) }
            if (decision == null || !decision.allowed) {
                blocked = decision?.block ?: MaxBackupModel.RestoreBlock.NO_ENTRIES
            } else {
                pending = HubRestorePrompt(handle, decision)
            }
        }
    }

    fun toggleKeep(handle: MaxBackupModel.Handle) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                MaxBackupEngine.setKeptForever(handle, !handle.keptForever)
            }
            reload()
            // الرسالة من قيمة ما قبل التبديل: كانت محفوظة ⇒ أُزيل وسمها، والعكس.
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
    }

    /**
     * استرجاع الجهاز: أحدث نسخة لكل تطبيق، واحدًا بعد آخر.
     *
     * والنسخة التي يمنعها `decisionFor` **تُتخطّى بلا استثناء لها**: لا قسر لاسترجاع
     * لم يمرّ فحص بصمته، ولا استرجاع لحزمة أُزيلت صلاحيتها. والتخطّي يُعَدّ ويُقال في النهاية.
     */
    suspend fun runDeviceRestore(targets: List<MaxBackupModel.Handle>) {
        var restored = 0
        var skipped = 0
        targets.forEachIndexed { index, handle ->
            deviceRun = DeviceRun(total = targets.size, index = index, label = labelOf(handle))
            val decision = withContext(Dispatchers.IO) { MaxBackupEngine.decisionFor(context, handle) }
            val ok = if (decision == null || !decision.allowed) {
                false
            } else {
                withContext(Dispatchers.IO) {
                    MaxBackupEngine.restore(context, handle) { stage -> busyStage = stage }.success
                }
            }
            if (ok) restored++ else skipped++
        }
        deviceRun = null
        busyStage = null
        reload()
        snackbarHostState.showSnackbar(
            if (restored == 0) {
                context.getString(R.string.max_backup_restore_device_none_done)
            } else {
                context.getString(
                    R.string.max_backup_restore_device_done,
                    restored.toString(),
                    skipped.toString(),
                )
            }
        )
    }

    /**
     *فحص كل النسخ: إعادة قراءة كل ملف ومقارنته ببصمته المسجّلة.
     *
     * ولا يُكتفى بعدّ الناجح: الفاشل يُسمّى بمجلده وعدد ملفاته المطابقة، فالفحص الذي ينتهي
     * إلى «فيه مشكلة» بلا تسمية ليس فحصًا بل قلقًا.
     */
    suspend fun runSweep() {
        val targets = handles
        if (targets.isEmpty()) return
        sweepResult = null
        var intact = 0
        val problems = mutableListOf<SweepProblem>()
        targets.forEachIndexed { index, handle ->
            sweepProgress = (index + 1) to targets.size
            val verdicts = withContext(Dispatchers.IO) { MaxBackupEngine.verify(handle) }
            val matched = verdicts.count { it.value == MaxBackupModel.Integrity.VERIFIED }
            if (verdicts.isNotEmpty() && matched == verdicts.size) {
                intact++
            } else {
                problems += SweepProblem(
                    label = labelOf(handle),
                    stamp = backupStamp(handle.createdAtMs),
                    matched = matched,
                    files = verdicts.size,
                )
            }
        }
        sweepProgress = null
        sweepResult = SweepResult(total = targets.size, intact = intact, problems = problems)
    }

    // أحدث نسخة لكل تطبيق: القائمة مرتّبة بالأحدث أولًا، فأول لقاء لكل حزمة هو أحدثها.
    // وبيانات النظام مستثناة: لها شاشتها ومحرّكها، وخلط الاسترجاعين في زرّ واحد يعِد
    // بما لا يفعله.
    val restorable = remember(handles) {
        handles.filter { it.pkg != MaxBackupEngine.SYSTEM_PKG }.distinctBy { it.pkg }
    }

    // `val` صريحة لا قراءة مباشرة داخل `when`: المتغيّرات المُفوَّضة (`by remember`)
    // لا تُضيّق أنواعها في `when`، والنسخ إلى متغيّر محلّي عادي يجعلها كذلك.
    val stageNow = busyStage
    val run = deviceRun
    val sweepNow = sweepProgress
    val busy = stageNow != null || run != null || sweepNow != null

    // `OCR-06`: الحصيلة والعدّ — من ثلاثة أرقام لكل نسخة، محسوبة في نموذج خالص يُقاس في JVM.
    val counts = remember(handles) { MaxBackupCounts.summarize(handles.map { copyFactOf(it) }) }
    val banner = when {
        run != null -> MaxCondition(
            kind = MaxConditionKind.Applying,
            title = stringResource(R.string.max_backup_restore_one_title, run.label),
            detail = stringResource(
                R.string.max_backup_restore_device_running,
                (run.index + 1).toString(),
                run.total.toString(),
            ),
        )

        sweepNow != null -> MaxCondition(
            kind = MaxConditionKind.Applying,
            title = stringResource(R.string.max_backup_sweep_title),
            detail = stringResource(
                R.string.max_backup_sweep_running,
                sweepNow.first.toString(),
                sweepNow.second.toString(),
            ),
        )

        stageNow != null -> MaxCondition(
            kind = MaxConditionKind.Applying,
            title = stageLabel(stageLabels, stageNow),
            detail = busyReason,
        )

        else -> null
    }

    MaxListScreen(
        title = stringResource(R.string.max_backup_title),
        subtitle = stringResource(R.string.max_backup_home_subtitle),
        onBack = { navController.popBackStack() },
        accentIcon = Icons.Rounded.Backup,
        condition = if (loading) {
            MaxCondition(
                kind = MaxConditionKind.Loading,
                title = stringResource(R.string.max_backup_plan_loading_title),
                detail = stringResource(R.string.max_backup_plan_loading_detail),
            )
        } else {
            null
        },
        banner = banner,
        snackbarHostState = snackbarHostState,
        actions = {
            MaxHelpAction(
                title = stringResource(R.string.max_backup_title),
                body = stringResource(R.string.max_backup_help),
            )
            IconButton(onClick = { scope.launch { reload() } }, enabled = !busy) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.max_action_refresh),
                )
            }
        },
        header = {
            MaxBackupStorageSection(
                readout = snapshot?.storage,
                onGrant = { grantPublicStorage() },
            )

            // أول قرار في الشاشة: نسخ أم استرجاع.
            MaxSegmented(
                options = listOf(
                    stringResource(R.string.max_backup_tab_backup),
                    stringResource(R.string.max_backup_tab_restore),
                ),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
        },
    ) {
        if (tab == 0) {
            item(key = "hub_categories") {
                MaxSection(
                    title = stringResource(R.string.max_backup_cat_title),
                    description = stringResource(R.string.max_backup_not_automatic),
                ) {
                    MaxGroup {
                        MaxRow(
                            title = stringResource(R.string.max_backup_cat_apps_title),
                            subtitle = stringResource(
                                R.string.max_backup_cat_apps_desc,
                                installed.size.toString(),
                                handles.count { it.pkg != MaxBackupEngine.SYSTEM_PKG }.toString(),
                            ),
                            icon = Icons.Rounded.Apps,
                            iconTone = MaxTone.Accent,
                            onClick = onOpenApps,
                        )
                        MaxGroupDivider()
                        MaxRow(
                            title = stringResource(R.string.max_backup_cat_system_title),
                            subtitle = stringResource(R.string.max_backup_cat_system_desc),
                            icon = Icons.Rounded.Storage,
                            iconTone = MaxTone.Caution,
                            onClick = onOpenSystem,
                        )
                    }
                }
            }

            // القرار كله داخل البندين (`MaxBackupScheduleSection.kt`): أي قائمة تنتج من أي
            // إضافة أو حذف، ومتى يكون للجدول موعد. وهنا **الحفظ والرسالة** فحسب.
            maxBackupSetsItem(
                sets = folderSets,
                busy = busy || policyBusy,
                onPersist = { next, removed -> persistSets(next, removed) },
                onRejected = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.max_backup_set_save_failed)
                        )
                    }
                },
            )

            item(key = "hub_actions") {
                MaxSection(title = stringResource(R.string.max_backup_actions_title)) {
                    MaxGroup {
                        MaxRow(
                            title = stringResource(R.string.max_backup_save_now_title),
                            subtitle = stringResource(R.string.max_backup_save_now_desc),
                            icon = Icons.Rounded.Save,
                            iconTone = MaxTone.Accent,
                            enabled = !busy,
                            onClick = onOpenApps,
                        )
                        MaxGroupDivider()
                        MaxRow(
                            title = stringResource(R.string.max_backup_restore_latest_title),
                            subtitle = newest?.let {
                                stringResource(
                                    R.string.max_backup_restore_latest_desc,
                                    labelOf(it),
                                    backupStamp(it.createdAtMs),
                                    MaxBackupModel.humanBytes(it.bytes),
                                )
                            } ?: stringResource(R.string.max_backup_restore_newest_none),
                            icon = Icons.Rounded.Restore,
                            iconTone = MaxTone.Caution,
                            enabled = !busy && newest != null,
                            onClick = { newest?.let { requestRestore(it) } },
                        )
                    }
                }
            }

            maxBackupScheduleItem(
                plan = plan,
                sets = folderSets,
                busy = busy || policyBusy,
                onChange = { persistPlan(it) },
                onRunNow = { runScheduleNow() },
            )

            maxBackupRecentItem(
                handles = handles,
                counts = counts,
                expanded = showAllCopies,
                busy = busy,
                busyReason = busyReason,
                label = { labelOf(it) },
                onToggleExpanded = { showAllCopies = !showAllCopies },
                onVerify = { verifyInto(it, snackbarHostState, scope, context) },
                onRestore = { requestRestore(it) },
                onToggleKeep = { toggleKeep(it) },
                onDelete = { askDelete = it },
            )
        } else {
            item(key = "hub_restore_intro") {
                MaxSection(
                    title = stringResource(R.string.max_backup_actions_title),
                    description = stringResource(R.string.max_backup_restore_tab_desc),
                ) {
                    MaxGroup {
                        MaxRow(
                            title = stringResource(R.string.max_backup_restore_latest_title),
                            subtitle = newest?.let {
                                stringResource(
                                    R.string.max_backup_restore_latest_desc,
                                    labelOf(it),
                                    backupStamp(it.createdAtMs),
                                    MaxBackupModel.humanBytes(it.bytes),
                                )
                            } ?: stringResource(R.string.max_backup_restore_newest_none),
                            icon = Icons.Rounded.Restore,
                            iconTone = MaxTone.Accent,
                            enabled = !busy && newest != null,
                            onClick = { newest?.let { requestRestore(it) } },
                        )
                        MaxGroupDivider()
                        MaxRow(
                            title = stringResource(R.string.max_backup_restore_device_title),
                            subtitle = if (restorable.isEmpty()) {
                                stringResource(R.string.max_backup_restore_device_none)
                            } else {
                                stringResource(
                                    R.string.max_backup_restore_device_desc,
                                    restorable.size.toString(),
                                )
                            },
                            icon = Icons.Rounded.SettingsBackupRestore,
                            iconTone = MaxTone.Caution,
                            enabled = !busy && restorable.isNotEmpty(),
                            onClick = { askRestoreDevice = true },
                        )
                    }
                }
            }

            item(key = "hub_sweep") {
                val result = sweepResult
                MaxSection(
                    title = stringResource(R.string.max_backup_verify_title),
                    description = result?.let {
                        stringResource(
                            R.string.max_backup_sweep_ok,
                            it.intact.toString(),
                            it.total.toString(),
                        )
                    } ?: stringResource(R.string.max_backup_sweep_desc),
                ) {
                    MaxGroup {
                        MaxRow(
                            title = stringResource(R.string.max_backup_sweep_title),
                            icon = Icons.Rounded.HealthAndSafety,
                            iconTone = MaxTone.Accent,
                            enabled = !busy && handles.isNotEmpty(),
                            onClick = { scope.launch { runSweep() } },
                        )
                        result?.problems?.forEach { problem ->
                            MaxGroupDivider()
                            MaxRow(
                                title = "${problem.label} · ${problem.stamp}",
                                subtitle = if (problem.files == 0) {
                                    stringResource(R.string.max_backup_sweep_problem_unreadable)
                                } else {
                                    stringResource(
                                        R.string.max_backup_sweep_problem_detail,
                                        problem.matched.toString(),
                                        problem.files.toString(),
                                    )
                                },
                                icon = Icons.Rounded.WarningAmber,
                                iconTone = MaxTone.Critical,
                            )
                        }
                    }
                }
            }

            if (handles.isEmpty()) {
                item(key = "hub_restore_empty") {
                    MaxConditionNotice(
                        MaxCondition(
                            kind = MaxConditionKind.Empty,
                            title = stringResource(R.string.max_backup_restore_empty_title),
                            detail = stringResource(R.string.max_backup_restore_empty_detail),
                        )
                    )
                }
            } else {
                item(key = "hub_restore_header") {
                    MaxSection(
                        title = stringResource(R.string.max_backup_restore_all_title),
                        description = stringResource(R.string.max_backup_history_desc),
                    ) {
                        MaxGroup {
                            handles.forEachIndexed { index, handle ->
                                if (index > 0) MaxGroupDivider()
                                BackupHistoryRow(
                                    handle = handle,
                                    title = labelOf(handle),
                                    enabled = !busy,
                                    busyReason = busyReason,
                                    onVerify = { verifyInto(handle, snackbarHostState, scope, context) },
                                    onRestore = { requestRestore(handle) },
                                    onToggleKeep = { toggleKeep(handle) },
                                    onDelete = { askDelete = handle },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── الحوارات ────────────────────────────────────────────────────────────

    pending?.let { prompt ->
        val baseMessage = stringResource(R.string.max_backup_restore_message)
        val warningTitle = stringResource(R.string.max_backup_warn_title)
        val warningLines = prompt.decision.warnings.map { warningText(it) }
        val message = if (warningLines.isEmpty()) {
            baseMessage
        } else {
            baseMessage + "\n\n" + warningTitle + "\n" + warningLines.joinToString("\n")
        }
        MaxConfirmDialog(
            visible = true,
            title = stringResource(R.string.max_backup_restore_title),
            message = message,
            confirmLabel = stringResource(R.string.max_backup_restore_confirm),
            destructive = true,
            technicalDetail = MaxBackupModel.folderName(prompt.handle.createdAtMs),
            onConfirm = {
                pending = null
                val target = prompt.handle
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        MaxBackupEngine.restore(context, target) { stage -> busyStage = stage }
                    }
                    busyStage = null
                    reload()
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
            onDismiss = { pending = null },
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

    MaxConfirmDialog(
        visible = askRestoreDevice,
        title = stringResource(R.string.max_backup_restore_device_confirm_title),
        message = stringResource(
            R.string.max_backup_restore_device_confirm_message,
            restorable.size.toString(),
        ),
        confirmLabel = stringResource(R.string.max_backup_restore_confirm),
        destructive = true,
        onConfirm = {
            askRestoreDevice = false
            val targets = restorable
            scope.launch { runDeviceRestore(targets) }
        },
        onDismiss = { askRestoreDevice = false },
    )

    askDelete?.let { handle ->
        MaxConfirmDialog(
            visible = true,
            title = stringResource(R.string.max_backup_delete_title),
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
}

/** نسخة مع قرارها، لشاشة الاسترجاع من الشاشة الرئيسية. */
private data class HubRestorePrompt(
    val handle: MaxBackupModel.Handle,
    val decision: MaxBackupModel.RestoreDecision,
)

/** تقدّم استرجاع الجهاز — اسم التطبيق الجاري وترتيبه. */
private data class DeviceRun(val total: Int, val index: Int, val label: String)

/** نسخة لم يجتز فحصُها كلَّ بصماتها. */
private data class SweepProblem(
    val label: String,
    val stamp: String,
    val matched: Int,
    val files: Int,
)

/** حصيلة فحص كل النسخ: كم سليمة، ومن يحتاج انتباهًا. */
private data class SweepResult(
    val total: Int,
    val intact: Int,
    val problems: List<SweepProblem>,
)

/**
 * فحص سلامة بنتيجة في سطر واحد.
 *
 * الشاشة الرئيسية لا تعرض تقريرًا مفصّلًا لكل ملف كما يفعل تفصيل التطبيق: هي تعرض
 * «كم من كم طابق»، ومن أراد التفصيل يفتح تفصيل التطبيق. والفحص نفسه هو الفحص.
 */
private fun verifyInto(
    handle: MaxBackupModel.Handle,
    host: SnackbarHostState,
    scope: CoroutineScope,
    context: Context,
) {
    scope.launch {
        val verdicts = withContext(Dispatchers.IO) { MaxBackupEngine.verify(handle) }
        val matched = verdicts.count { it.value == MaxBackupModel.Integrity.VERIFIED }
        host.showSnackbar(
            context.getString(
                R.string.max_backup_verify_result,
                matched.toString(),
                verdicts.size.toString(),
            )
        )
    }
}

/**
 * من `Handle` إلى [MaxBackupCounts.CopyFact] — السطر الوحيد الذي يربط المستند بالحساب.
 *
 * ولماذا هنا لا في النموذج: `Handle` يُفكّ من `org.json` فلا يُترجَم على JVM، ووضع العدّ داخله
 * كان سيُعيد الحساب إلى مكان لا يُقاس إلا بجهاز. التحويل سطر واحد، والقاعدة كلها تحت اختبار.
 */
internal fun copyFactOf(handle: MaxBackupModel.Handle): MaxBackupCounts.CopyFact =
    MaxBackupCounts.CopyFact(
        pkg = handle.pkg,
        createdAtMs = handle.createdAtMs,
        bytes = handle.bytes,
        entryCount = handle.entryCount,
        complete = handle.complete,
    )

/** طابع زمني مُنسَّق محليًّا. دالّة عادية لا Composable: تُنادى داخل `remember`. */
internal fun backupStamp(createdAtMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(createdAtMs))
