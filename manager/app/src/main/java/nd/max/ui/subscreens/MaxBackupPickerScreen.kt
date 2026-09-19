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
 * Max Backup — قائمة التطبيقات (المنتقي).
 *
 * **ما تغيّر ولماذا:** كانت القائمة صفوفًا تُفتح واحدًا واحدًا، فلا سبيل إلى نسخ خمسة
 * تطبيقات معًا، ولا «تحديد الكل»، ولا معرفة أيّ تطبيق له نسخة قبل الدخول إليه. صارت الآن:
 *
 *  - صندوق تحديد لكل صف، وصفُّ **الجسم** يفتح تفصيل التطبيق كما كان (لا نكسر مسارًا قائمًا).
 *  - تحديد الكل **للمعروض** لا لكل التطبيقات: من رشّح «لها نسخة» ثم حدّد الكل لا يُفاجأ
 *    بأنه حدّد ما لم يره.
 *  - مرشّحات: الكل · المستخدم · النظام · لها نسخة · و«المفضّلة فقط» زرًّا مستقلًّا، مع نجمة
 *    في كل صفّ (`OCR-04`). والمفضّلة **تُقدَّم** في القائمة دائمًا، فوجودها لا يتوقّف على
 *    تفعيل مرشّح.
 *  - تاريخ أحدث نسخة وحجمها في كل صف، أو «لم تُنسخ بعد» صراحةً.
 *  - زرّ حفظ جماعي في الأعلى وفي الأسفل، وحوار تأكيد قبل أي كتابة.
 *
 * ولا كتابة من هذه الشاشة: النسخ يجري في `MaxBackupEngine` (ADR-11)، والشاشة تُمرّر
 * النطاق وتُسمّي المرحلة.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.ui.component.AppIconImage
import nd.max.ui.component.StudioButton
import nd.max.ui.design.MaxAlpha
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxConditionNotice
import nd.max.ui.design.MaxConfirmDialog
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSearchField
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSegmented
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.packageRouteOf
import nd.max.ui.design.content
import nd.max.ui.util.MaxBackupEngine
import nd.max.ui.util.MaxBackupFavorites
import nd.max.ui.util.MaxBackupModel
import nd.max.ui.util.MaxBackupScheduler
import nd.max.ui.viewmodel.ApplistViewmodel

/** مرشّحات القائمة. أربعة، لأن الصفّ الواحد يجب أن يبقى مقروءًا بلا التفاف نصّ. */
internal enum class MaxBackupFilter { ALL, USER, SYSTEM, BACKED_UP }

@Composable
internal fun MaxBackupAppsPicker(
    navController: NavController,
    onBack: () -> Unit,
    onSwitchMode: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val appListViewModel: ApplistViewmodel = viewModel()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(MaxBackupFilter.ALL) }
    // `OCR-04`: المفضّلة تُقدَّم في الترتيب دائمًا، وهذا المفتاح لقصر القائمة عليها فعلًا.
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var favorites by remember { mutableStateOf<List<String>>(emptyList()) }
    // التحديد يُحفظ عبر إعادة التركيب: اختيار ثلاثين تطبيقًا ثم فقدانه بدوران الشاشة
    // ليس تفصيلًا، بل يُلغي الوظيفة.
    var selection by rememberSaveable(
        stateSaver = listSaver<Set<String>, String>(
            save = { it.toList() },
            restore = { it.toSet() },
        )
    ) { mutableStateOf(emptySet<String>()) }

    var snapshot by remember { mutableStateOf<MaxBackupSnapshot?>(null) }
    var batch by remember { mutableStateOf<BatchProgress?>(null) }
    var askBatch by remember { mutableStateOf(false) }

    suspend fun reload() {
        snapshot = readBackupSnapshot(context)
    }

    LaunchedEffect(Unit) {
        appListViewModel.loadApps(context)
        favorites = withContext(Dispatchers.IO) { MaxBackupScheduler.readFavorites(context) }
        reload()
    }

    /** وسم وإزالة وسم: تُكتب القائمة كاملة، والملف ذرّي — فلا نصف قائمة مفضّلة. */
    fun toggleFavorite(pkg: String) {
        val next = MaxBackupFavorites.toggle(favorites, pkg)
        favorites = next
        scope.launch { withContext(Dispatchers.IO) { MaxBackupScheduler.writeFavorites(context, next) } }
    }

    val installed = ApplistViewmodel.apps
    val latestByPkg = remember(snapshot) {
        snapshot?.handles.orEmpty().associateBy { it.pkg }
    }
    val labels = remember(installed) { installed.associate { it.packageName to it.label } }

    val matches = remember(installed, query, filter, latestByPkg, favorites, favoritesOnly) {
        val needle = query.trim().lowercase(Locale.getDefault())
        val filtered = installed
            .filter { app ->
                val matchesQuery = needle.isEmpty() ||
                    app.label.lowercase(Locale.getDefault()).contains(needle) ||
                    app.packageName.lowercase(Locale.ROOT).contains(needle)
                val matchesFilter = when (filter) {
                    MaxBackupFilter.ALL -> true
                    MaxBackupFilter.USER -> !app.isSystem
                    MaxBackupFilter.SYSTEM -> app.isSystem
                    MaxBackupFilter.BACKED_UP -> latestByPkg.containsKey(app.packageName)
                }
                val matchesFavorite = !favoritesOnly || app.packageName in favorites
                matchesQuery && matchesFilter && matchesFavorite
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
        // المفضّلة تتقدّم ولا تُخفى: الترتيب الأبجدي يبقى، ويُقدَّم عليه ما وسَمه المستخدم.
        MaxBackupFavorites.ordered(filtered, key = { it.packageName }, favorites = favorites)
    }

    // التحديد يقتصر على ما هو معروض الآن: صفّ اختفى بالترشيح لا يُنسخ بلا أن يُرى.
    val shown = matches.map { it.packageName }.toSet()
    val committed = selection intersect shown

    val busy = batch != null
    val banner = batch?.let { run ->
        MaxCondition(
            kind = MaxConditionKind.Applying,
            title = stringResource(R.string.max_backup_batch_title, run.label),
            detail = stringResource(
                R.string.max_backup_batch_running,
                (run.index + 1).toString(),
                run.total.toString(),
            ),
        )
    }

    val condition = when {
        installed.isEmpty() -> MaxCondition(
            kind = MaxConditionKind.Loading,
            title = stringResource(R.string.max_backup_plan_loading_title),
            detail = stringResource(R.string.max_backup_cat_apps_loading),
        )

        else -> null
    }

    val selectionText = if (committed.isEmpty()) {
        stringResource(R.string.max_backup_selection_none)
    } else {
        stringResource(R.string.max_backup_selection_count, committed.size.toString())
    }
    val saveLabel = if (committed.size == 1) {
        stringResource(R.string.max_backup_save_selected_one)
    } else {
        stringResource(R.string.max_backup_save_selected, committed.size.toString())
    }

    fun toggle(pkg: String) {
        selection = if (pkg in selection) selection - pkg else selection + pkg
    }

    suspend fun runBatch(targets: List<String>) {
        var saved = 0
        var skipped = 0
        targets.forEachIndexed { index, pkg ->
            batch = BatchProgress(
                total = targets.size,
                index = index,
                label = labels[pkg] ?: pkg,
            )
            val outcome = withContext(Dispatchers.IO) {
                val plan = MaxBackupEngine.inventory(context, pkg)
                if (plan == null) {
                    null
                } else {
                    // النطاق يتبع الصلاحية المتاحة: بلا جذر لا يُوعَد ببيانات لا تُقرأ.
                    val outcome = MaxBackupEngine.create(
                        context,
                        plan,
                        MaxBackupModel.Scope.forPrivilege(plan.hasRoot),
                    )
                    MaxBackupEngine.prune(context, pkg, KEEP_VERSIONS)
                    outcome
                }
            }
            if (outcome?.success == true) saved++ else skipped++
        }
        batch = null
        selection = emptySet()
        reload()
        snackbarHostState.showSnackbar(
            if (saved == 0) {
                context.getString(R.string.max_backup_batch_none)
            } else {
                context.getString(R.string.max_backup_batch_done, saved.toString(), skipped.toString())
            }
        )
    }

    MaxListScreen(
        title = stringResource(R.string.max_backup_title),
        subtitle = stringResource(R.string.max_backup_picker_desc),
        onBack = onBack,
        accentIcon = Icons.Rounded.Backup,
        condition = if (batch == null) condition else null,
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
            MaxSection(title = stringResource(R.string.max_backup_mode_title)) {
                MaxBackupModeSwitch(mode = MaxBackupMode.APPS, onSelect = { onSwitchMode() })
            }

            MaxSection(
                title = stringResource(R.string.max_backup_picker_title),
                description = selectionText,
            ) {
                MaxSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.max_backup_search_hint),
                    clearContentDescription = stringResource(R.string.max_backup_search_clear),
                )
                MaxSegmented(
                    options = listOf(
                        stringResource(R.string.max_backup_filter_all),
                        stringResource(R.string.max_backup_filter_user),
                        stringResource(R.string.max_backup_filter_system_apps),
                        stringResource(R.string.max_backup_filter_backed_up),
                    ),
                    selectedIndex = filter.ordinal,
                    onSelect = { filter = MaxBackupFilter.entries[it] },
                    enabled = !busy,
                )
                // المفضّلة: زرّ صريح لا مرشّح خامس (الشريط يحمل أربعة، والخامس يضغط نصّه).
                MaxGroup {
                    MaxRow(
                        title = stringResource(
                            R.string.max_backup_fav_only,
                            favorites.size.toString(),
                        ),
                        icon = if (favoritesOnly) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        iconTone = if (favoritesOnly) MaxTone.Accent else MaxTone.Neutral,
                        enabled = !busy,
                        onClick = { favoritesOnly = !favoritesOnly },
                    )
                }
                MaxGroup {
                    MaxRow(
                        title = stringResource(R.string.max_backup_select_all, matches.size.toString()),
                        icon = Icons.Rounded.CheckCircle,
                        iconTone = MaxTone.Accent,
                        enabled = !busy && matches.isNotEmpty(),
                        onClick = { selection = selection + shown },
                    )
                    MaxGroupDivider()
                    MaxRow(
                        title = stringResource(R.string.max_backup_select_none),
                        icon = Icons.Rounded.Close,
                        iconTone = MaxTone.Neutral,
                        enabled = !busy && committed.isNotEmpty(),
                        // إزالة كاملة لا للوحدات المعروضة فقط: تحديد أزيل ثم عاد عند تغيير
                        // المرشّح يبدو عطبًا وإن لم يُنسخ منه شيء.
                        onClick = { selection = emptySet() },
                    )
                }
                if (committed.isNotEmpty()) {
                    SaveSelectedButton(label = saveLabel, enabled = !busy, onClick = { askBatch = true })
                }
            }
        },
    ) {
        if (matches.isEmpty()) {
            item(key = "picker_empty") {
                MaxConditionNotice(
                    MaxCondition(
                        kind = MaxConditionKind.Empty,
                        title = stringResource(R.string.max_backup_no_match_title),
                        detail = stringResource(R.string.max_backup_picker_empty_detail),
                    )
                )
            }
        } else {
            // صفوف **كسولة** لا عمود داخل عنصر واحد: قائمة بثلاثمئة تطبيق تُبنى كلها عند
            // أول تركيب كانت ستُسقط الإطارات أثناء التمرير — وهو نفس السبب الذي جعل
            // `MaxListScreen` موجودًا أصلًا. والترتيب يطابق بقية قوائم التطبيقات في التطبيق.
            itemsIndexed(items = matches, key = { _, app -> app.packageName }) { index, app ->
                BackupAppRow(
                    app = app,
                    lastCopy = latestByPkg[app.packageName]?.let { handle ->
                        stringResource(
                            R.string.max_backup_row_last_copy,
                            backupStamp(handle.createdAtMs),
                            MaxBackupModel.humanBytes(handle.bytes),
                        )
                    },
                    selected = app.packageName in selection,
                    enabled = !busy,
                    favorite = app.packageName in favorites,
                    onStar = { toggleFavorite(app.packageName) },
                    onToggle = { toggle(app.packageName) },
                    onOpen = {
                        // من سجل الوجهات لا من سلسلة مكتوبة بيد (ADR-02).
                        navController.navigate(
                            packageRouteOf(MaxDestination.MaxBackup.route, app.packageName)
                        )
                    },
                )
                if (index < matches.lastIndex) MaxGroupDivider()
            }

            if (committed.isNotEmpty()) {
                item(key = "picker_save_tail") {
                    SaveSelectedButton(label = saveLabel, enabled = !busy, onClick = { askBatch = true })
                }
            }
        }
    }

    MaxConfirmDialog(
        visible = askBatch,
        title = stringResource(R.string.max_backup_batch_confirm_title),
        message = stringResource(R.string.max_backup_batch_confirm_message, committed.size.toString()),
        confirmLabel = saveLabel,
        onConfirm = {
            askBatch = false
            val targets = matches.map { it.packageName }.filter { it in committed }
            scope.launch { runBatch(targets) }
        },
        onDismiss = { askBatch = false },
    )
}

/** تقدّم النسخ الجماعي — رقم واحد يتقدّم، لا شريط يدور بلا معنى. */
private data class BatchProgress(val total: Int, val index: Int, val label: String)

/**
 * زرّ الحفظ الجماعي.
 *
 * **زرّ ممتلئ بعرض الصفحة** لا صفّ في مجموعة، والسببان متلازمان:
 *
 *  1. الفعل الأساسي في هذه الشاشة يجب أن يُرى **قبل** أن يُقرأ — مقاس الضغط وحده لا
 *     يكفي لدلالة «هنا يحدث النسخ».
 *  2. ويُعرض مرّتين: في الأعلى ليُرافق لحظة التحديد، وفي الأسفل ليُدرَك بعد تمرير قائمة
 *     طويلة. والفعل واحد والحوار واحد، فالتكرار في الموضع لا في السلوك.
 */
@Composable
private fun SaveSelectedButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    StudioButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
    ) {
        Icon(
            imageVector = Icons.Rounded.Save,
            contentDescription = null,
            modifier = Modifier.size(MaxSize.iconGlyph),
        )
        Spacer(Modifier.width(MaxSpace.sm))
        Text(text = label)
    }
}

/**
 * صفّ تطبيق قابل للتحديد.
 *
 * ومنطقتان لا واحدة: **صندوق التحديد** يحدّد، و**الزرّ في الطرف** يفتح تفصيل التطبيق.
 * ضمّهما في نقرة واحدة كان سيُفقد أحد المسارين، وكلاهما مطلوب: نسخ جماعي، وفحص تطبيق واحد.
 */
@Composable
private fun BackupAppRow(
    app: ApplistViewmodel.AppInfo,
    lastCopy: String?,
    selected: Boolean,
    enabled: Boolean,
    favorite: Boolean,
    onStar: () -> Unit,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val selectLabel = stringResource(R.string.max_backup_row_select, app.label)
    val openLabel = stringResource(R.string.max_backup_row_open, app.label)
    val starLabel = stringResource(
        if (favorite) R.string.max_backup_row_unstar else R.string.max_backup_row_star,
        app.label,
    )
    val alpha = if (enabled) 1f else MaxAlpha.disabledContent

    // التحديد **يقلب** ألوان الصفّ (حاوية وأمامية) بدل أن يُضيف رقعة شفافة: المطلوب أن
    // يُرى أيّ صفوف ستُنسخ من نظرة، لا أن يُلمح تلوين خفيف.
    val titleColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = alpha)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    }
    val supportColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = MaxAlpha.supportingText)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        // خلفية شفافية هي القاعدة في القوائم الطويلة هنا (صفوف بلا صناديق، وفاصل شعري بينها).
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MaxSize.minTouchTarget)
                .padding(
                    horizontal = MaxSpace.sm,
                    vertical = MaxSpace.rowPaddingVertical,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = selectLabel },
            )
            AppIconImage(app = app, size = MaxSize.rowIconContainer)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = MaxSpace.xs),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline),
            ) {
                // `LineBreak.Heading` يمنع كسر الكلمة حرفًا حرفًا في الأسماء الطويلة.
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Heading),
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(lineBreak = LineBreak.Heading),
                    color = supportColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = lastCopy ?: stringResource(R.string.max_backup_row_never),
                    style = MaterialTheme.typography.labelSmall,
                    color = supportColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // الوسم قبل سهم الفتح: النجمة حالة تُقلب في مكانها، والسهم يغادر الشاشة —
            // فلا يجاور فعلًا مُتلِفًا فعلًا مُغادرًا في نقطة واحدة.
            IconButton(
                onClick = onStar,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = starLabel },
            ) {
                Icon(
                    imageVector = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = null,
                    tint = if (favorite) {
                        MaxTone.Accent.content()
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    },
                )
            }
            IconButton(onClick = onOpen, enabled = enabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = openLabel,
                )
            }
        }
    }
}
