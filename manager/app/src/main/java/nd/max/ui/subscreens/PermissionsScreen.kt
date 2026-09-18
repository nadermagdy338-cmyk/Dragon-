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
 * `GAP-07` — **الصلاحيات و`AppOps`** لتطبيق واحد.
 *
 * ولماذا الاثنان في شاشة واحدة: **صلاحية البيان** تقول «هذا التطبيق طلب الكاميرا»، و`AppOps`
 * تقول ما صار لها فعلًا (يُسمح · يُتجاهل · يُقيَّد بالاستعمال الأمامي · تجاوز مرفوع). ومع كل
 * إصدار أندرويد صار `AppOps` يحكم ما لا تملكه صلاحيات البيان أصلًا — الحافظة والاهتزاز
 * والتنفيذ في الخلفية. فعرض نصف واحد هو مدير صلاحيات يضلّل بلا أن يقصد.
 *
 * وثلاثة أشياء تفصل هذه الشاشة عن نظائرها:
 *
 * 1. **ما لا نعرفه لا يُعرَض كأنه حالة.** `AppOps` تحتاج امتيازًا؛ وبلا جذر تُعرض
 *    **«تعذّرت القراءة»** لا قائمة فارغة تُفهَم «لا أوضاع».
 * 2. **الكتابة تُقرأ بعدها.** كل تغيير يُتبَع بقراءة، ويُعرض الحكم على **القيمة**:
 *    `مُطبَّق` · `رُفع التجاوز` · `الجهاز تجاهله` · `فشل` · `تعذّر التحقّق`. و«الجهاز تجاهله»
 *    ليست فشلًا عندنا ولا نجاحًا — بل نتيجة تُقال.
 * 3. **سياسة مُدوَّنة.** «أعدت التثبيت أو غيّرت الروم فعادت كل الصلاحيات» لا يُعالَج من
 *    الذاكرة: تُحفظ **مرجع صريح**، ويُقارَن بالحاضر، ويُعرَض **الانحراف**، ثم يُستعاد بما يظهر
 *    منه للمستخدم قبل أن يُكتب شيء.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.navigation.NavController
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
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.util.AppOpsUtil
import nd.max.ui.util.PermissionPolicy

/** أقصى ما يُعرض من سطور الانحراف داخل حوار واحد قبل أن نقول «وغيرها». */
private const val DRIFT_PREVIEW_LIMIT = 8

/** آخر تغيير أجراه المستخدم: العملية، والوضع الذي طلبه، وحكم القراءة العكسية. */
private data class LastChange(
    val op: String,
    val mode: PermissionPolicy.OpMode,
    val verdict: PermissionPolicy.WriteVerdict,
)

/** حصيلة استعادة المرجع — تُعرض بالعدّ، لأن «تم» ليست نتيجة. */
private data class RestoreTally(
    var applied: Int = 0,
    var asDefault: Int = 0,
    var ignored: Int = 0,
    var failed: Int = 0,
    var unverifiable: Int = 0,
) {
    fun record(verdict: PermissionPolicy.WriteVerdict) {
        when (verdict) {
            PermissionPolicy.WriteVerdict.APPLIED -> applied++
            PermissionPolicy.WriteVerdict.APPLIED_AS_DEFAULT -> asDefault++
            PermissionPolicy.WriteVerdict.IGNORED_BY_DEVICE -> ignored++
            PermissionPolicy.WriteVerdict.FAILED -> failed++
            PermissionPolicy.WriteVerdict.UNVERIFIABLE -> unverifiable++
        }
    }
}

@Composable
fun PermissionsScreen(navController: NavController, pkg: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var permissions by remember(pkg) { mutableStateOf<List<AppOpsUtil.DeclaredPermission>?>(null) }
    var loaded by remember(pkg) { mutableStateOf(false) }
    var ops by remember(pkg) { mutableStateOf<List<PermissionPolicy.OpState>?>(null) }
    var opsLoaded by remember(pkg) { mutableStateOf(false) }
    var reference by remember(pkg) { mutableStateOf<PermissionPolicy.Reference?>(null) }
    var drift by remember(pkg) { mutableStateOf<List<PermissionPolicy.Drift>?>(null) }

    var choosing by remember { mutableStateOf<PermissionPolicy.OpState?>(null) }
    var askDeleteReference by remember { mutableStateOf(false) }
    var askRestore by remember { mutableStateOf(false) }
    var lastChange by remember { mutableStateOf<LastChange?>(null) }
    // الوسوم تُحلّ في التركيب: `joinToString` ليست inline فلا تصلح فيها دالة مركّبة،
    // وأسماء الأوضاع تُستعمل داخل حوار الاسترجاع أيضًا.
    val labels = modeLabels()
    val absent = stringResource(R.string.max_perms_current_absent)

    val label = remember(pkg) {
        runCatching {
            context.packageManager.getApplicationInfo(pkg, 0)
                .loadLabel(context.packageManager).toString()
        }.getOrDefault(pkg)
    }

    suspend fun reload() {
        val loadedData = withContext(Dispatchers.IO) {
            AppOpsUtil.declaredPermissions(context, pkg) to
                Triple(AppOpsUtil.readOps(pkg), AppOpsUtil.loadReference(context, pkg), Unit)
        }
        permissions = loadedData.first
        loaded = true
        ops = loadedData.second.first
        reference = loadedData.second.second
        opsLoaded = true
    }

    LaunchedEffect(pkg) { reload() }

    val dangerous = permissions.orEmpty().filter { it.dangerous }
    val grantedCount = dangerous.count { it.granted }

    val condition = when {
        !loaded -> MaxCondition(
            kind = MaxConditionKind.Loading,
            title = stringResource(R.string.max_backup_plan_loading_title),
            detail = stringResource(R.string.max_perms_loading_detail),
        )

        permissions == null -> MaxCondition(
            kind = MaxConditionKind.Empty,
            title = stringResource(R.string.max_backup_plan_missing_title),
            detail = stringResource(R.string.max_backup_plan_missing_detail),
            primaryActionLabel = stringResource(R.string.max_action_retry),
            onPrimaryAction = { scope.launch { reload() } },
        )

        else -> null
    }

    MaxListScreen(
        title = label,
        subtitle = stringResource(R.string.max_perms_subtitle),
        onBack = { navController.popBackStack() },
        accentIcon = Icons.Rounded.Shield,
        condition = condition,
        snackbarHostState = snackbarHostState,
        actions = {
            MaxHelpAction(
                title = stringResource(R.string.max_perms_title),
                body = stringResource(R.string.max_perms_help),
            )
        },
        header = {
            // ── صلاحيات البيان (بلا امتياز) ──────────────────────────────
            MaxSection(
                title = stringResource(R.string.max_perms_declared_title),
                description = stringResource(R.string.max_perms_declared_desc),
            ) {
                if (dangerous.isEmpty()) {
                    MaxGroup {
                        MaxRow(
                            title = stringResource(R.string.max_perms_declared_none),
                            icon = Icons.Rounded.CheckCircle,
                            iconTone = MaxTone.Positive,
                        )
                    }
                } else {
                    MaxGroup {
                        dangerous.forEachIndexed { index, permission ->
                            if (index > 0) MaxGroupDivider()
                            MaxRow(
                                title = permission.shortName,
                                subtitle = if (permission.granted) {
                                    stringResource(R.string.max_perms_granted)
                                } else {
                                    stringResource(R.string.max_perms_denied)
                                },
                                icon = if (permission.granted) {
                                    Icons.Rounded.CheckCircle
                                } else {
                                    Icons.Rounded.WarningAmber
                                },
                                iconTone = if (permission.granted) MaxTone.Positive else MaxTone.Caution,
                            )
                        }
                    }
                }
                MaxBullets(
                    lines = listOf(
                        stringResource(
                            R.string.max_perms_declared_count,
                            grantedCount.toString(),
                            dangerous.size.toString(),
                        )
                    )
                )
            }

            // ── المرجع المدوَّن ────────────────────────────────────────────
            MaxSection(
                title = stringResource(R.string.max_perms_ref_title),
                description = stringResource(R.string.max_perms_ref_desc),
            ) {
                MaxGroup {
                    MaxRow(
                        title = stringResource(R.string.max_perms_ref_save),
                        subtitle = stringResource(R.string.max_perms_ref_save_desc),
                        icon = Icons.Rounded.Save,
                        iconTone = MaxTone.Accent,
                        enabled = ops != null,
                        onClick = {
                            val current = ops ?: return@MaxRow
                            scope.launch {
                                val saved = withContext(Dispatchers.IO) {
                                    AppOpsUtil.saveReference(
                                        context,
                                        PermissionPolicy.Reference(
                                            pkg = pkg,
                                            savedAtMs = System.currentTimeMillis(),
                                            ops = current.associate { it.op to it.mode },
                                        ),
                                    )
                                }
                                reload()
                                drink(snackbarHostState, context, saved)
                            }
                        },
                    )
                    if (reference != null) {
                        MaxGroupDivider()
                        MaxRow(
                            title = stringResource(R.string.max_perms_ref_compare),
                            subtitle = stringResource(
                                R.string.max_perms_ref_exists,
                                savedStamp(reference!!.savedAtMs),
                            ),
                            icon = Icons.Rounded.History,
                            iconTone = MaxTone.Neutral,
                            enabled = ops != null,
                            onClick = {
                                val current = ops ?: return@MaxRow
                                val stored = reference ?: return@MaxRow
                                drift = PermissionPolicy.drift(stored, current)
                            },
                        )
                        MaxGroupDivider()
                        MaxRow(
                            title = stringResource(R.string.max_perms_ref_restore),
                            subtitle = stringResource(R.string.max_perms_ref_restore_desc),
                            icon = Icons.Rounded.History,
                            iconTone = MaxTone.Caution,
                            enabled = ops != null,
                            onClick = {
                                val current = ops ?: return@MaxRow
                                val stored = reference ?: return@MaxRow
                                val found = PermissionPolicy.drift(stored, current)
                                drift = found
                                if (found.isEmpty()) {
                                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.max_perms_ref_drift_none)) }
                                } else {
                                    askRestore = true
                                }
                            },
                        )
                        MaxGroupDivider()
                        MaxRow(
                            title = stringResource(R.string.max_perms_ref_delete),
                            icon = Icons.Rounded.WarningAmber,
                            iconTone = MaxTone.Critical,
                            onClick = { askDeleteReference = true },
                        )
                    }
                }
            }

            // ── AppOps: الحالة أو سبب غيابها ──────────────────────────────
            MaxSection(
                title = stringResource(R.string.max_perms_ops_title),
                description = stringResource(R.string.max_perms_ops_desc),
            ) {
                if (opsLoaded && ops == null) {
                    MaxGroup {
                        MaxRow(
                            title = stringResource(R.string.max_perms_ops_needs_root),
                            subtitle = stringResource(R.string.max_perms_ops_unreadable),
                            icon = Icons.Rounded.Shield,
                            iconTone = MaxTone.Caution,
                        )
                    }
                } else {
                    val count = ops?.size ?: 0
                    MaxBullets(
                        lines = listOf(
                            stringResource(R.string.max_perms_ops_count, count.toString()),
                            stringResource(R.string.max_perms_ops_tap_hint),
                        )
                    )
                }
            }

            if (drift != null) {
                val differences = drift!!
                MaxSection(
                    title = stringResource(R.string.max_perms_ref_drift_title),
                    description = if (differences.isEmpty()) {
                        stringResource(R.string.max_perms_ref_drift_none)
                    } else {
                        stringResource(R.string.max_perms_ref_drift_count, differences.size.toString())
                    },
                ) {
                    if (differences.isNotEmpty()) {
                        MaxGroup {
                            differences.take(DRIFT_PREVIEW_LIMIT).forEachIndexed { index, item ->
                                if (index > 0) MaxGroupDivider()
                                MaxRow(
                                    title = item.op,
                                    subtitle = stringResource(
                                        R.string.max_perms_drift_line,
                                        modeText(item.reference),
                                        item.current?.let { modeText(it) }
                                            ?: stringResource(R.string.max_perms_current_absent),
                                    ),
                                    icon = Icons.Rounded.History,
                                    iconTone = MaxTone.Caution,
                                )
                            }
                        }
                        if (differences.size > DRIFT_PREVIEW_LIMIT) {
                            MaxBullets(
                                lines = listOf(
                                    stringResource(
                                        R.string.max_perms_ref_restore_more,
                                        (differences.size - DRIFT_PREVIEW_LIMIT).toString(),
                                    )
                                )
                            )
                        }
                    }
                }
            }

            lastChange?.let { change ->
                MaxSection(title = stringResource(R.string.max_perms_last_change)) {
                    MaxGroup {
                        MaxRow(
                            title = change.op,
                            subtitle = stringResource(
                                R.string.max_perms_verdict_line,
                                modeText(change.mode),
                                verdictText(change.verdict),
                            ),
                            icon = if (succeeded(change.verdict)) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.WarningAmber
                            },
                            iconTone = if (succeeded(change.verdict)) MaxTone.Positive else MaxTone.Caution,
                        )
                    }
                }
            }
        },
    ) {
        val currentOps = ops
        if (currentOps != null && currentOps.isNotEmpty()) {
            items(items = currentOps, key = { it.op }) { state ->
                MaxRow(
                    title = state.op,
                    subtitle = modeText(state.mode),
                    icon = Icons.Rounded.Shield,
                    iconTone = when (state.mode) {
                        PermissionPolicy.OpMode.ALLOW -> MaxTone.Positive
                        PermissionPolicy.OpMode.DENY, PermissionPolicy.OpMode.IGNORE -> MaxTone.Caution
                        else -> MaxTone.Neutral
                    },
                    onClick = { choosing = state },
                )
                MaxGroupDivider()
            }
        }
    }

    // ── حوار اختيار الوضع ───────────────────────────────────────────────────

    choosing?.let { state ->
        OpModeDialog(
            state = state,
            onDismiss = { choosing = null },
            onPick = { mode ->
                choosing = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { AppOpsUtil.setOp(pkg, state.op, mode) }
                    lastChange = LastChange(state.op, mode, result.verdict)
                    ops = withContext(Dispatchers.IO) { AppOpsUtil.readOps(pkg) }
                }
            },
        )
    }

    MaxConfirmDialog(
        visible = askDeleteReference,
        title = stringResource(R.string.max_perms_ref_delete_title),
        message = stringResource(R.string.max_perms_ref_delete_message),
        confirmLabel = stringResource(R.string.max_perms_ref_delete),
        destructive = true,
        onConfirm = {
            askDeleteReference = false
            scope.launch {
                withContext(Dispatchers.IO) { AppOpsUtil.deleteReference(context, pkg) }
                drift = null
                reload()
                snackbarHostState.showSnackbar(context.getString(R.string.max_perms_ref_deleted))
            }
        },
        onDismiss = { askDeleteReference = false },
    )

    val pendingDrift = drift
    if (askRestore && pendingDrift != null && reference != null) {
        val plan = PermissionPolicy.restorePlan(reference!!, pendingDrift)
        // السطر يُبنى بمورده لا بنصّ مركَّب: النقطتان والسهم في نصّ صلب يُقرآن خطأً في العربية،
        // وهذا المورد نفسه هو ما تعرضه قائمة الانحراف فوق.
        val preview = pendingDrift.take(DRIFT_PREVIEW_LIMIT).map { item ->
            stringResource(
                R.string.max_perms_drift_line,
                labels.getValue(item.reference),
                item.current?.let { labels[it] } ?: absent,
            )
        }.joinToString("\n")
        val extra = if (pendingDrift.size > DRIFT_PREVIEW_LIMIT) {
            "\n" + stringResource(
                R.string.max_perms_ref_restore_more,
                (pendingDrift.size - DRIFT_PREVIEW_LIMIT).toString(),
            )
        } else {
            ""
        }
        MaxConfirmDialog(
            visible = true,
            title = stringResource(R.string.max_perms_ref_restore_title),
            message = stringResource(R.string.max_perms_ref_restore_message, plan.size.toString()) +
                "\n\n" + preview + extra,
            confirmLabel = stringResource(R.string.max_perms_ref_restore),
            destructive = true,
            onConfirm = {
                askRestore = false
                scope.launch {
                    val tally = RestoreTally()
                    withContext(Dispatchers.IO) {
                        plan.forEach { (op, mode) ->
                            tally.record(AppOpsUtil.setOp(pkg, op, mode).verdict)
                        }
                    }
                    ops = withContext(Dispatchers.IO) { AppOpsUtil.readOps(pkg) }
                    drift = withContext(Dispatchers.IO) {
                        val stored = AppOpsUtil.loadReference(context, pkg) ?: return@withContext null
                        val now = AppOpsUtil.readOps(pkg) ?: return@withContext null
                        PermissionPolicy.drift(stored, now)
                    }
                    // الأعداد تُبلَّغ منفصلة: دمج «رُفع التجاوز» (نجاح) مع «الجهاز أبقى قيمته»
                    // (ليس نجاحًا) يُفقد التمييز الذي تُصرّ عليه هذه الشاشة في كل موضع آخر.
                    snackbarHostState.showSnackbar(
                        context.getString(
                            R.string.max_perms_ref_restore_done,
                            tally.applied.toString(),
                            tally.asDefault.toString(),
                            tally.ignored.toString(),
                            tally.failed.toString(),
                            tally.unverifiable.toString(),
                        )
                    )
                }
            },
            onDismiss = { askRestore = false },
        )
    }
}

/**
 * حوار الأوضاع. يعرض **كل** الأوضاع القابلة للكتابة مع معناها، لا قائمة أسماء فقط — لأن
 * الفرق بين `ignore` و`deny` و`default` لا يُفهم من الكلمة وحدها، وهو الفرق الذي يهمّ.
 */
@Composable
private fun OpModeDialog(
    state: PermissionPolicy.OpState,
    onDismiss: () -> Unit,
    onPick: (PermissionPolicy.OpMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // نفس نصف قطر حواف `MaxConfirmDialog` — وإلا بدا هذا الحوار من عائلة أخرى.
        shape = RoundedCornerShape(MaxRadius.sheet),
        title = { Text(text = state.op, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)) {
                Text(
                    text = stringResource(R.string.max_perms_choose_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PermissionPolicy.WRITABLE_MODES.forEach { mode ->
                    val selected = mode == state.mode
                    MaxRow(
                        title = stringResource(modeRes(mode)),
                        subtitle = stringResource(modeNoteRes(mode)),
                        icon = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.Shield,
                        iconTone = if (selected) MaxTone.Positive else MaxTone.Neutral,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onPick(mode) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}

/** `true` حين تكون القيمة استقرّت كما طلبناها — و«رُفع التجاوز» نجاح أيضًا. */
private fun succeeded(verdict: PermissionPolicy.WriteVerdict): Boolean =
    verdict == PermissionPolicy.WriteVerdict.APPLIED ||
        verdict == PermissionPolicy.WriteVerdict.APPLIED_AS_DEFAULT

@Composable
private fun modeText(mode: PermissionPolicy.OpMode): String = stringResource(modeRes(mode))

@Composable
private fun modeLabels(): Map<PermissionPolicy.OpMode, String> =
    PermissionPolicy.OpMode.entries.associateWith { stringResource(modeRes(it)) }

private fun modeRes(mode: PermissionPolicy.OpMode): Int = when (mode) {
    PermissionPolicy.OpMode.ALLOW -> R.string.max_perms_mode_allow
    PermissionPolicy.OpMode.FOREGROUND -> R.string.max_perms_mode_foreground
    PermissionPolicy.OpMode.IGNORE -> R.string.max_perms_mode_ignore
    PermissionPolicy.OpMode.DENY -> R.string.max_perms_mode_deny
    PermissionPolicy.OpMode.DEFAULT -> R.string.max_perms_mode_default
    PermissionPolicy.OpMode.ASK -> R.string.max_perms_mode_ask
}

private fun modeNoteRes(mode: PermissionPolicy.OpMode): Int = when (mode) {
    PermissionPolicy.OpMode.ALLOW -> R.string.max_perms_mode_allow_note
    PermissionPolicy.OpMode.FOREGROUND -> R.string.max_perms_mode_foreground_note
    PermissionPolicy.OpMode.IGNORE -> R.string.max_perms_mode_ignore_note
    PermissionPolicy.OpMode.DENY -> R.string.max_perms_mode_deny_note
    PermissionPolicy.OpMode.DEFAULT -> R.string.max_perms_mode_default_note
    PermissionPolicy.OpMode.ASK -> R.string.max_perms_mode_ask_note
}

@Composable
private fun verdictText(verdict: PermissionPolicy.WriteVerdict): String = stringResource(
    when (verdict) {
        PermissionPolicy.WriteVerdict.APPLIED -> R.string.max_perms_verdict_applied
        PermissionPolicy.WriteVerdict.APPLIED_AS_DEFAULT -> R.string.max_perms_verdict_as_default
        PermissionPolicy.WriteVerdict.IGNORED_BY_DEVICE -> R.string.max_perms_verdict_ignored
        PermissionPolicy.WriteVerdict.FAILED -> R.string.max_perms_verdict_failed
        PermissionPolicy.WriteVerdict.UNVERIFIABLE -> R.string.max_perms_verdict_unverifiable
    }
)

private fun savedStamp(atMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(atMs))

private suspend fun drink(
    host: SnackbarHostState,
    context: android.content.Context,
    saved: Boolean,
) {
    host.showSnackbar(
        context.getString(
            if (saved) R.string.max_perms_ref_saved else R.string.max_perms_ref_save_failed
        )
    )
}
