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
 * `OCR-02` + `OCR-01` — قسمان في لوحة `Max Backup`: **مجموعات المجلدات** و**النسخ المجدول**.
 *
 * **ولماذا في ملف مستقل:** لوحة `Max Backup` بلغت نحو ٩٠٠ سطر، وقاعدة المستودع تحصر الملف
 * في ١٠٠٠؛ فالقسمان — وكلاهما جديد ومستقل دلاليًّا — يُكتبان هنا ويُستدعيان من اللوحة بسطرين.
 *
 * **وثلاثة قرارات واجهية مقصودة:**
 *
 *  ١. **الجدول لا يعد بالساعة.** المنصّة تُنفّذ في أول فرصة بعد الموعد، والنصّ يقول ذلك بدل
 *     أن يترك المستخدم ينتظر «٣:٠٠ صباحًا» تمامًا ثم يظن أن الميزة معطوبة.
 *  ٢. **الهدف يُعلَن بالضبط.** لا يبدأ شيء بلا مجموعة أو صنف مختار؛ والجدول بلا هدف يُقال عنه
 *     «اختر هدفًا» لا «يعمل» — لأن «يعمل» معناه أنه سينسخ شيئًا.
 *  ٣. **ما اختفى يُسمّى.** مجموعة حُذفت واسمها ما زال في الجدول تُعرض في قائمة «لم يعد موجودًا»،
 *     ولا تُنفَّذ بدلًا منها مجموعة أخرى.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import nd.max.R
import nd.max.ui.component.StudioButton
import nd.max.ui.design.MaxAlpha
import nd.max.ui.design.MaxBullets
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxConditionNotice
import nd.max.ui.design.MaxConfirmDialog
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxInputDialog
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSwitchRow
import nd.max.ui.design.MaxTone
import nd.max.ui.util.MaxBackupFolders
import nd.max.ui.util.MaxBackupSchedule
import nd.max.ui.util.MaxBackupScheduler
import nd.max.ui.util.MaxBackupSystem

/**
 * بند «مجموعات المجلدات» في لوحة `Max Backup`.
 *
 * **ولماذا بند لا استدعاء مباشر:** اللوحة كلها قائمة كسولة، وبنودها تُبنى في `LazyListScope`.
 * ووضع القرار هنا (أي قائمة تنتج من أي إضافة أو حذف) يترك في اللوحة **الحفظ والرسالة** فحسب،
 * وهي التي لا تُختبَر في JVM أصلًا.
 */
internal fun LazyListScope.maxBackupSetsItem(
    sets: List<MaxBackupFolders.FolderSet>,
    busy: Boolean,
    onPersist: (next: List<MaxBackupFolders.FolderSet>, removed: String?) -> Unit,
    onRejected: () -> Unit,
) {
    item(key = "hub_sets") {
        MaxBackupFoldersSection(
            sets = sets,
            busy = busy,
            onAdd = { name, path ->
                val created = MaxBackupFolders.create(
                    name = name,
                    paths = listOf(path),
                    addedAtMs = System.currentTimeMillis(),
                    taken = sets.map { it.name },
                )
                if (created == null) onRejected() else onPersist(sets + created, null)
            },
            onAddPath = { set, path ->
                val grown = MaxBackupFolders.addPath(set, path)
                if (grown == null) {
                    onRejected()
                } else {
                    onPersist(sets.map { if (it.name == set.name) grown else it }, null)
                }
            },
            onDelete = { set ->
                onPersist(sets.filterNot { it.name == set.name }, set.name)
            },
        )
    }
}

/** بند «النسخ المجدول». وفي الموعد الأقصى شرط: يُحسب فقط حين يُنفَّذ الجدول فعلًا. */
internal fun LazyListScope.maxBackupScheduleItem(
    plan: MaxBackupSchedule.Plan,
    sets: List<MaxBackupFolders.FolderSet>,
    busy: Boolean,
    onChange: (MaxBackupSchedule.Plan) -> Unit,
    onRunNow: () -> Unit,
) {
    item(key = "hub_schedule") {
        MaxBackupScheduleSection(
            plan = plan,
            sets = sets,
            kinds = MaxBackupSystem.Kind.entries,
            busy = busy,
            // حساب «القادم» لجدول متوقف يعرض وعدًا لا وجود له، فلا يُحسب أصلًا.
            nextRunAtMs = if (MaxBackupSchedule.blocker(plan) == null) {
                MaxBackupScheduler.nextRunAtMs(plan)
            } else {
                null
            },
            onChange = onChange,
            onRunNow = onRunNow,
            kindLabel = { kind -> systemKindText(kind) },
        )
    }
}

/**
 * مجموعات المجلدات: أسماؤها ومساراتها، وإضافة مجموعة أو مسار، وحذف مجموعة.
 *
 * والتحقّق يُعلَن **قبل** الحفظ لا بعده: اسم يحمل فاصلًا، أو مسار مستحيل (`/`، `/proc`)، أو مسار
 * مكرّر — كلها تُرفض بسبب مكتوب في الحوار، لأن «لم يُضف شيء» بلا سبب أسوأ من الرفض نفسه.
 */
@Composable
internal fun MaxBackupFoldersSection(
    sets: List<MaxBackupFolders.FolderSet>,
    busy: Boolean,
    onAdd: (name: String, path: String) -> Unit,
    onAddPath: (MaxBackupFolders.FolderSet, String) -> Unit,
    onDelete: (MaxBackupFolders.FolderSet) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var addingTo by remember { mutableStateOf<MaxBackupFolders.FolderSet?>(null) }
    var askDelete by remember { mutableStateOf<MaxBackupFolders.FolderSet?>(null) }

    MaxSection(
        title = stringResource(R.string.max_backup_sets_title),
        description = stringResource(R.string.max_backup_sets_desc),
    ) {
        if (sets.isEmpty()) {
            MaxConditionNotice(
                MaxCondition(
                    kind = MaxConditionKind.Empty,
                    title = stringResource(R.string.max_backup_sets_empty_title),
                    detail = stringResource(R.string.max_backup_sets_empty_detail),
                )
            )
        } else {
            MaxGroup {
                sets.forEachIndexed { index, set ->
                    if (index > 0) MaxGroupDivider()
                    MaxRow(
                        title = set.name,
                        subtitle = stringResource(
                            R.string.max_backup_set_paths_count,
                            set.paths.size.toString(),
                        ) + " · " + set.paths.take(2).joinToString(" · ") { MaxBackupFolders.labelOf(it) },
                        icon = Icons.Rounded.Folder,
                        iconTone = MaxTone.Accent,
                        enabled = !busy,
                        onClick = { addingTo = set },
                        trailing = {
                            IconButton(onClick = { askDelete = set }, enabled = !busy) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.max_backup_set_delete_cd),
                                )
                            }
                        },
                    )
                }
            }
            // تقاطع مجموعتين يُعلَن لا يُمنع: نسخ المجلد نفسه مرّتين مقصود أحيانًا، وأثره
            // المضاعفة — فيُقال للمستخدم ليقرّر.
            val overlapping = remember(sets) {
                buildList {
                    for (i in sets.indices) {
                        for (j in i + 1 until sets.size) {
                            val shared = MaxBackupFolders.sharedPaths(sets[i], sets[j])
                            if (shared.isNotEmpty()) add(Triple(sets[i].name, sets[j].name, shared.size))
                        }
                    }
                }
            }
            if (overlapping.isNotEmpty()) {
                MaxBullets(
                    lines = overlapping.take(3).map { (left, right, count) ->
                        stringResource(
                            R.string.max_backup_set_shared,
                            left,
                            right,
                            count.toString(),
                        )
                    }
                )
            }
        }

        StudioButton(
            onClick = { creating = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.padding(end = MaxSpace.xs),
            )
            Text(text = stringResource(R.string.max_backup_set_add))
        }
    }

    val deleting = askDelete
    MaxConfirmDialog(
        visible = deleting != null,
        title = stringResource(R.string.max_backup_set_delete_title),
        message = stringResource(
            R.string.max_backup_set_delete_message,
            deleting?.name.orEmpty(),
            deleting?.paths?.size ?: 0,
        ),
        confirmLabel = stringResource(R.string.max_backup_set_delete_confirm),
        destructive = true,
        onConfirm = {
            deleting?.let(onDelete)
            askDelete = null
        },
        onDismiss = { askDelete = null },
    )

    if (creating) {
        SetEditorDialog(
            title = stringResource(R.string.max_backup_set_add),
            initialName = "",
            initialPath = "",
            lockName = false,
            taken = sets.map { it.name },
            existing = null,
            onDismiss = { creating = false },
            onConfirm = { name, path ->
                onAdd(name, path)
                creating = false
            },
        )
    }

    val target = addingTo
    if (target != null) {
        SetEditorDialog(
            title = stringResource(R.string.max_backup_set_add_path_title, target.name),
            initialName = target.name,
            initialPath = "",
            // الاسم ثابت هنا: هذا الحوار يضيف **مسارًا** إلى مجموعة قائمة، وتغيير الاسم من هنا
            // يجعل الحوار يفعل شيئين ثم لا يُعرف أيّهما حدث.
            lockName = true,
            taken = emptyList(),
            existing = target,
            onDismiss = { addingTo = null },
            onConfirm = { _, path ->
                onAddPath(target, path)
                addingTo = null
            },
        )
    }
}

/**
 * حوار اسم ومسار. يعرض سبب الرفض **قبل** الضغط لا بعده: الاسم أو المسار غير الصالح يُقال
 * عنه لماذا، فلا يبحث المستخدم عن السبب في مكان آخر.
 */
@Composable
private fun SetEditorDialog(
    title: String,
    initialName: String,
    initialPath: String,
    lockName: Boolean,
    taken: Collection<String>,
    existing: MaxBackupFolders.FolderSet?,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var path by remember { mutableStateOf(initialPath) }

    val nameError = when {
        lockName -> null
        name.isBlank() -> null
        MaxBackupFolders.sanitizeName(name) == null -> stringResource(R.string.max_backup_set_bad_name)
        else -> null
    }
    val pathError = when {
        path.isBlank() -> null
        !MaxBackupFolders.isBackupable(path) -> stringResource(R.string.max_backup_set_bad_path)
        existing?.paths?.contains(path.trim()) == true -> stringResource(R.string.max_backup_set_dup_path)
        else -> null
    }
    val usable = MaxBackupFolders.sanitizeName(name) != null && MaxBackupFolders.isBackupable(path)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(MaxRadius.sheet),
        title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                if (!lockName) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.max_backup_set_name_field)) },
                        supportingText = nameError?.let { message -> { Text(text = message) } },
                        isError = nameError != null,
                    )
                }
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.max_backup_set_path_field)) },
                    placeholder = { Text(text = stringResource(R.string.max_backup_set_path_hint)) },
                    supportingText = pathError?.let { message -> { Text(text = message) } },
                    isError = pathError != null,
                )
                // الاسم المتعارض لا يُرفض: يُعاد باسم جديد ويُقال ذلك — فلا يخسر المستخدم
                // ما كتبه لأنه اختار اسمًا موجودًا.
                val renamed = if (!lockName && name.trim() in taken) {
                    MaxBackupFolders.uniqueName(name.trim(), taken)
                } else {
                    null
                }
                if (renamed != null) {
                    Text(
                        text = stringResource(R.string.max_backup_set_will_rename, renamed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), path.trim()) }, enabled = usable) {
                Text(text = stringResource(R.string.max_files_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.max_files_cancel)) }
        },
    )
}

/**
 * النسخ المجدول: تفعيل، ووقت، وشروط، وأيام، وأهداف — ثم سطر يقول متى التشغيل القادم.
 */
@Composable
internal fun MaxBackupScheduleSection(
    plan: MaxBackupSchedule.Plan,
    sets: List<MaxBackupFolders.FolderSet>,
    kinds: List<MaxBackupSystem.Kind>,
    busy: Boolean,
    nextRunAtMs: Long?,
    onChange: (MaxBackupSchedule.Plan) -> Unit,
    onRunNow: () -> Unit,
    kindLabel: @Composable (MaxBackupSystem.Kind) -> String,
) {
    var editingTime by remember { mutableStateOf(false) }
    var timeDraft by remember { mutableStateOf("") }

    val blocker = MaxBackupSchedule.blocker(plan)
    val missing = remember(plan, sets, kinds) {
        MaxBackupSchedule.missing(plan, sets.map { it.name }, kinds.map { it.name })
    }

    MaxSection(
        title = stringResource(R.string.max_backup_schedule_title),
        description = stringResource(R.string.max_backup_schedule_desc),
    ) {
        MaxSwitchRow(
            title = stringResource(R.string.max_backup_schedule_enable),
            subtitle = stringResource(R.string.max_backup_schedule_enable_desc),
            checked = plan.enabled,
            enabled = !busy,
            onCheckedChange = { onChange(plan.copy(enabled = it)) },
        )
        MaxSwitchRow(
            title = stringResource(R.string.max_backup_schedule_charging),
            subtitle = stringResource(R.string.max_backup_schedule_charging_desc),
            checked = plan.chargingOnly,
            enabled = !busy,
            onCheckedChange = { onChange(plan.copy(chargingOnly = it)) },
        )
        MaxSwitchRow(
            title = stringResource(R.string.max_backup_schedule_wifi),
            subtitle = stringResource(R.string.max_backup_schedule_wifi_desc),
            checked = plan.wifiOnly,
            enabled = !busy,
            onCheckedChange = { onChange(plan.copy(wifiOnly = it)) },
        )

        MaxGroup {
            MaxRow(
                title = stringResource(
                    R.string.max_backup_schedule_time,
                    MaxBackupSchedule.clock(plan.minuteOfDay),
                ),
                subtitle = stringResource(R.string.max_backup_schedule_time_desc),
                icon = Icons.Rounded.Schedule,
                iconTone = MaxTone.Accent,
                enabled = !busy,
                onClick = {
                    timeDraft = MaxBackupSchedule.clock(plan.minuteOfDay)
                    editingTime = true
                },
            )
        }

        DayPicker(
            selected = plan.effectiveDays,
            enabled = !busy,
            onToggle = { day ->
                val current = plan.effectiveDays
                val next = if (day in current) current - day else current + day
                // لا أيام مختارة = كل يوم، وهذا **يُعلَن** في السطر تحته لا يُترك ضمنيًّا.
                onChange(plan.copy(days = if (next.size == 7) emptySet() else next))
            },
        )
        // أسماء الأيام تُجمع **قبل** `stringResource`: دالّة `joinToString` ليست `inline`،
        // فنداء دالّة `@Composable` داخل محوّلها لا يترجم (وهذا خطأ الترجمة الوحيد هنا).
        // و`map` inline، فيصحّ فيها نداء `dayText`.
        val dayNames = plan.effectiveDays.sorted().map { dayText(it) }
        MaxBullets(
            lines = listOf(
                stringResource(
                    R.string.max_backup_schedule_days_summary,
                    if (plan.days.isEmpty()) {
                        stringResource(R.string.max_backup_schedule_every_day)
                    } else {
                        dayNames.joinToString(" ")
                    },
                )
            )
        )
    }

    MaxSection(
        title = stringResource(R.string.max_backup_schedule_targets),
        description = stringResource(R.string.max_backup_schedule_targets_desc),
    ) {
        if (sets.isEmpty() && kinds.isEmpty()) {
            MaxConditionNotice(
                MaxCondition(
                    kind = MaxConditionKind.Empty,
                    title = stringResource(R.string.max_backup_schedule_no_target_title),
                    detail = stringResource(R.string.max_backup_schedule_no_target_detail),
                )
            )
        } else {
            MaxGroup {
                var first = true
                sets.forEach { set ->
                    val checked = set.name in plan.sets
                    if (!first) MaxGroupDivider()
                    first = false
                    MaxRow(
                        title = set.name,
                        subtitle = stringResource(R.string.max_backup_sets_title),
                        icon = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        iconTone = if (checked) MaxTone.Positive else MaxTone.Neutral,
                        enabled = !busy,
                        onClick = {
                            onChange(
                                plan.copy(
                                    sets = if (checked) plan.sets - set.name else plan.sets + set.name
                                )
                            )
                        },
                    )
                }
                kinds.forEach { kind ->
                    val checked = kind.name in plan.kinds
                    if (!first) MaxGroupDivider()
                    first = false
                    MaxRow(
                        title = kindLabel(kind),
                        subtitle = stringResource(R.string.max_backup_system_title),
                        icon = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        iconTone = if (checked) MaxTone.Positive else MaxTone.Neutral,
                        enabled = !busy,
                        onClick = {
                            onChange(
                                plan.copy(
                                    kinds = if (checked) plan.kinds - kind.name else plan.kinds + kind.name
                                )
                            )
                        },
                    )
                }
            }
        }

        if (missing.isNotEmpty()) {
            MaxBullets(
                lines = missing.take(3).map { stringResource(R.string.max_backup_schedule_missing, it) }
            )
        }

        MaxBullets(
            lines = buildList {
                add(stringResource(R.string.max_backup_schedule_platform_note))
                when (blocker) {
                    MaxBackupSchedule.Blocker.DISABLED ->
                        add(stringResource(R.string.max_backup_schedule_off_note))

                    MaxBackupSchedule.Blocker.NO_TARGET ->
                        add(stringResource(R.string.max_backup_schedule_no_target_note))

                    null -> nextRunAtMs?.let {
                        add(stringResource(R.string.max_backup_schedule_next, backupStamp(it)))
                    }
                }
                plan.lastRunAtMs?.let { last ->
                    add(
                        stringResource(
                            R.string.max_backup_schedule_last,
                            backupStamp(last),
                            stringResource(resultRes(plan.lastResult)),
                        )
                    )
                }
            }
        )

        StudioButton(
            onClick = onRunNow,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && plan.hasTarget,
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                modifier = Modifier.padding(end = MaxSpace.xs),
            )
            Text(text = stringResource(R.string.max_backup_schedule_run_now))
        }
    }

    if (editingTime) {
        val typed = MaxBackupSchedule.parseClock(timeDraft)
        MaxInputDialog(
            visible = true,
            title = stringResource(R.string.max_backup_schedule_time_title),
            fieldLabel = stringResource(R.string.max_backup_schedule_time_field),
            value = timeDraft,
            onValueChange = { timeDraft = it },
            placeholder = stringResource(R.string.max_backup_schedule_time_placeholder),
            // نصّ لا يُقرأ يُرفض بسببه، والوقت يبقى كما كان — لا وقت مخمَّن.
            supportingText = if (typed == null) {
                stringResource(R.string.max_backup_schedule_time_invalid)
            } else {
                null
            },
            confirmLabel = stringResource(R.string.max_files_confirm),
            confirmEnabled = typed != null,
            onConfirm = {
                typed?.let { onChange(plan.copy(minuteOfDay = it)) }
                editingTime = false
            },
            onDismiss = { editingTime = false },
        )
    }
}

/** أيام الأسبوع: سبعة، والاختيار متعدّد ومقلوب اللون عند الاختيار. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayPicker(
    selected: Set<Int>,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.xs),
    ) {
        (1..7).forEach { day ->
            val active = day in selected
            Surface(
                shape = RoundedCornerShape(MaxRadius.pill),
                color = if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = MaxAlpha.toneContainerStrong)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                modifier = Modifier.clickable(
                    enabled = enabled,
                    role = Role.Checkbox,
                    onClick = { onToggle(day) },
                ),
            ) {
                Text(
                    text = dayText(day),
                    modifier = Modifier.padding(horizontal = MaxSpace.sm, vertical = MaxSpace.xs),
                    // `LineBreak.Heading` يمنع تكسير «الأربعاء» حرفًا حرفًا في عرض ضيّق.
                    style = MaterialTheme.typography.labelLarge.copy(lineBreak = LineBreak.Heading),
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** اسم اليوم من الموارد — الاثنين=١ … الأحد=٧. */
@Composable
private fun dayText(isoDay: Int): String = stringResource(
    when (isoDay) {
        1 -> R.string.max_backup_day_mon
        2 -> R.string.max_backup_day_tue
        3 -> R.string.max_backup_day_wed
        4 -> R.string.max_backup_day_thu
        5 -> R.string.max_backup_day_fri
        6 -> R.string.max_backup_day_sat
        else -> R.string.max_backup_day_sun
    }
)

private fun resultRes(result: MaxBackupSchedule.Result?): Int = when (result) {
    MaxBackupSchedule.Result.OK -> R.string.max_backup_schedule_result_ok
    MaxBackupSchedule.Result.FAILED -> R.string.max_backup_schedule_result_failed
    MaxBackupSchedule.Result.SKIPPED -> R.string.max_backup_schedule_result_skipped
    null -> R.string.max_backup_schedule_result_none
}
