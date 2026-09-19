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
 * `FM-02` — اللوح الواحد في مدير الملفات: **تبويباته · مساره · سطر حالته · قائمته**.
 *
 * وأُعيدت كتابته لأن الشاشة كانت تُكرّر داخل كل لوح كلَّ شيء: فتات خبز، وحقل بحث، وثلاث
 * قوائم مرشّح، وشريط أفعال نتائج، ورأس فيه حبّة ولون — فيصير اللوح عمودًا من الأشرطة
 * وقائمته آخر ما يُرى. والبنية الجديدة تُبقي في اللوح ما يخصّ اللوح وحده:
 *
 * 1. **تبويباته** — شريط أعلى يضمّ المجلد الحالي وكل مجلد ثبّته المستخدم فيه.
 * 2. **شريط مساره** — المسار نصًّا واحدًا يُنقَر فيُحرَّر، لا خمس قطع تُمرَّر.
 * 3. **سطر حالته** — عدد المجلدات والملفات، وكم مُخفيّ، ومساحة نظام الملفات الذي يقف
 *    عليه هذا اللوح. وكل رقم فيه **مقيس**؛ وما لم يُقس يقول «غير مقروءة» لا صفرًا
 *    (ADR-07/ADR-23: الرقم الكاذب في أداة قياس أسوأ من غياب الرقم).
 * 4. **قائمته** — صفّ واحد لكل مدخل: رمز · اسم · حجم · تاريخ. الصفّ الذي كان سطرين
 *    بخمس صفات مجموعة صار سطرًا واحدًا بأربع معلومات، فتتّسع الشاشة لضعف عدد الصفوف.
 *
 * وسطر الحالة هو ما يجعل الإخفاء مشروعًا: الملفات المخفيّة مخفيّة افتراضيًّا كما في كل
 * مدير ملفات، لكن عددها **يُعلن**، فلا يُقرأ غيابها كأنها حُذفت.
 *
 * والقائمة كسولة داخل اللوح لا في الصفحة: لوحان لا يصيران قائمة واحدة، ولا تُوضع قائمة
 * كسولة داخل تمرير عمودي.
 */
package nd.max.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.ui.design.MaxAlpha
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxConditionPanel
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSearchField
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.design.MaxViewMenu
import nd.max.ui.design.container
import nd.max.ui.design.content
import nd.max.ui.util.DirectoryListing
import nd.max.ui.util.DiskSpace
import nd.max.ui.util.FileBrowser
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileFormat
import nd.max.ui.util.FileKind
import nd.max.ui.util.FilePaneState
import nd.max.ui.util.FileSearchFilters
import nd.max.ui.util.ListingFailure
import nd.max.ui.util.PaneSide
import nd.max.ui.util.PaneTabs

/** عرض اللوح الذي يبدأ دونه بإخفاء عمود التاريخ: العمودان معًا يعنيان اسماً بعرض حرفين. */
private val DateColumnThreshold = 250.dp

/**
 * عمود المدخلات في لوح واحد.
 *
 * @param diskSpace مساحة نظام الملفات الذي يقف عليه هذا اللوح، أو `null` إن لم تُقرأ.
 * @param searchOpen هل شريط البحث مفتوح في هذا اللوح (يُفتح بطلبه ويُغلق بإغلاقه).
 * @param onPinCurrent تثبيت المجلد الحالي كتبويب في **هذا اللوح**.
 * @param onRemoveTab إزالة تبويب مثبّت من هذا اللوح.
 */
@Composable
fun FilePaneColumn(
    side: PaneSide,
    state: FilePaneState,
    active: Boolean,
    diskSpace: DiskSpace?,
    linked: Boolean,
    searchOpen: Boolean,
    modifier: Modifier = Modifier,
    onActivate: () -> Unit,
    onPathEdit: () -> Unit,
    onNavigate: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchChange: (FileSearchFilters.Filter) -> Unit,
    onCloseSearch: () -> Unit,
    onPinCurrent: () -> Unit,
    onRemoveTab: (String) -> Unit,
    onEntryClick: (FileEntry) -> Unit,
    onEntryLongPress: (FileEntry) -> Unit,
) {
    val entries = state.visible()

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(MaxRadius.group),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            if (active) MaxSize.activeRing else MaxSize.hairlineBorder,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // اللوح كلّه منطقة تنشيط: النقر على أي موضع فيه (ولو فراغًا) يُعلن هذا
                // اللوح نشطًا، فلا تذهب عملية إلى لوح ظنّ المستخدم أنه غيره.
                .clickable(role = Role.Button, onClick = onActivate)
                .padding(vertical = MaxSpace.xs),
        ) {
            PaneTabStrip(
                state = state,
                onActivate = onActivate,
                onNavigate = onNavigate,
                onPin = onPinCurrent,
                onRemoveTab = onRemoveTab,
            )
            PanePathRow(
                side = side,
                state = state,
                active = active,
                linked = linked,
                onActivate = onActivate,
                onPathEdit = onPathEdit,
            )
            PaneStatusRow(state = state, diskSpace = diskSpace, entries = entries.size)

            if (searchOpen) {
                MaxSearchField(
                    value = state.query,
                    onValueChange = { onActivate(); onQueryChange(it) },
                    placeholder = stringResource(R.string.max_files_search_placeholder),
                    clearContentDescription = stringResource(R.string.max_files_search_clear),
                    modifier = Modifier.padding(horizontal = MaxSpace.sm),
                )
                FileSearchFilterRow(
                    filter = state.search,
                    onChange = { onActivate(); onSearchChange(it) },
                    onClose = { onActivate(); onCloseSearch() },
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = MaxSpace.xs),
                thickness = MaxSize.hairlineBorder,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaxAlpha.border),
            )

            // كل ما يحتاج تركيبًا يُحسب **قبل** الدخول في نطاق `LazyColumn`: نطاق القائمة
            // الكسولة ليس سياق تركيب، ودالّة @Composable لا تُنادى داخله أصلًا.
            val condition = paneCondition(state, entries.isEmpty(), searchOpen)
            val banner = paneBanner(state)
            if (condition != null) {
                Box(
                    modifier = Modifier
                        .padding(MaxSpace.sm)
                        // `weight` لا `fillMaxSize`: داخل `Column` يقيس الأبناء غير الموزونين
                        // بأقصى ارتفاع متاح لا بالمتبقّي منه، فقائمة تأخذ الارتفاع كلّه تدفع
                        // الرأس وسطر الحالة خارج حدود اللوح (عطب تخطيط صامت).
                        .weight(1f),
                ) {
                    MaxConditionPanel(condition)
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val showDate = maxWidth >= DateColumnThreshold
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = MaxSpace.sm),
                    ) {
                        banner?.let { notice ->
                            item(key = "pane_banner") { FilePaneNotice(notice) }
                        }
                        items(entries, key = { it.path }) { entry ->
                            FileEntryRow(
                                entry = entry,
                                selected = state.isSelected(entry.path),
                                selecting = state.selecting,
                                showDate = showDate,
                                onClick = { onActivate(); onEntryClick(entry) },
                                onLongClick = { onActivate(); onEntryLongPress(entry) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * شريط التبويبات: المجلد الحالي، ثم كل مجلد ثبّته المستخدم في هذا اللوح، ثم «+».
 *
 * والتبويب **مفتاح لا موضع**: النقر ينقل اللوح إلى ذلك المجلد، والمسارات المثبّتة تبقى
 * مع التنقّل. ولذلك يظهر المجلد الحالي كأول تبويب: من ثبّت مجلدين يرى أين هو من الثلاثة
 * بلا أن يقرأ المسار.
 */
@Composable
private fun PaneTabStrip(
    state: FilePaneState,
    onActivate: () -> Unit,
    onNavigate: (String) -> Unit,
    onPin: () -> Unit,
    onRemoveTab: (String) -> Unit,
) {
    val current = FileBrowser.normalize(state.path)
    val pinned = state.tabs.filterNot { it == current }
    val stripLabel = stringResource(R.string.max_files_tabs_cd)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = MaxSpace.xs, vertical = MaxSpace.hairline)
            .semantics { contentDescription = stripLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs),
    ) {
        // التبويب الحالي **علامة «أنت هنا»** لا زرّ انتقال: النقر عليه يُعلن اللوح نشطًا فقط،
        // لأن إعادة الانتقال إلى المجلد نفسه تدفع سجلًا لا معنى له فيصير الرجوع بلا أثر.
        PaneChip(
            label = PaneTabs.label(current),
            active = true,
            onSelect = onActivate,
        )
        pinned.forEach { path ->
            PaneChip(
                label = PaneTabs.label(path),
                active = false,
                onSelect = { onActivate(); onNavigate(path) },
                onRemove = { onActivate(); onRemoveTab(path) },
            )
        }
        PaneChip(
            label = null,
            active = false,
            onSelect = { onActivate(); onPin() },
        )
    }
}

/**
 * تبويب واحد. و`label = null` هو تبويب الإضافة: رمز `+` وحده مع وصفه المسموع، لأنه فعل
 * لا اسم له في القائمة.
 */
@Composable
private fun PaneChip(
    label: String?,
    active: Boolean,
    onSelect: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val tone = if (active) MaxTone.Accent else MaxTone.Neutral
    val removeDescription = stringResource(R.string.max_files_tab_close)
    val addDescription = stringResource(R.string.max_files_tab_pin)

    Surface(
        shape = RoundedCornerShape(MaxRadius.row),
        color = if (active) tone.container() else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            MaxSize.hairlineBorder,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.heightIn(min = MaxSize.minTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (label == null) {
                IconButton(onClick = onSelect) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = addDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = label,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onSelect)
                        .padding(start = MaxSpace.sm, end = MaxSpace.xs, top = MaxSpace.xs, bottom = MaxSpace.xs),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) tone.content() else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onRemove != null) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(MaxSize.iconContainer)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "$removeDescription: $label",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** شريط المسار: أيّ لوح، وعلى أي مجلد — والمسار يُنقَر فيُحرَّر. */
@Composable
private fun PanePathRow(
    side: PaneSide,
    state: FilePaneState,
    active: Boolean,
    linked: Boolean,
    onActivate: () -> Unit,
    onPathEdit: () -> Unit,
) {
    val tone = if (active) MaxTone.Accent else MaxTone.Neutral
    val sideName = stringResource(
        if (side == PaneSide.Left) R.string.max_files_pane_left else R.string.max_files_pane_right
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaxSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs),
    ) {
        Surface(
            shape = RoundedCornerShape(MaxRadius.pill),
            color = tone.container(),
            modifier = Modifier.semantics { contentDescription = sideName },
        ) {
            Text(
                text = stringResource(
                    if (side == PaneSide.Left) {
                        R.string.max_files_side_left_short
                    } else {
                        R.string.max_files_side_right_short
                    }
                ),
                modifier = Modifier.padding(horizontal = MaxSpace.sm, vertical = MaxSpace.hairline),
                style = MaterialTheme.typography.labelMedium,
                color = tone.content(),
                maxLines = 1,
            )
        }

        Text(
            text = state.path,
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button) { onActivate(); onPathEdit() }
                .padding(vertical = MaxSpace.sm),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // علامة الربط رمزٌ **و**وصف: من لا يرى الأيقونة يسمع الحالة، ولا يعتمد الربط على
        // مقارنة مسارين لِيُفهَم.
        if (linked) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = stringResource(R.string.max_files_linked_cd),
                modifier = Modifier.size(MaxSize.iconGlyphSmall),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        IconButton(onClick = { onActivate(); onPathEdit() }) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.max_files_edit_path_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * سطر حالة اللوح: كم مجلدًا وكم ملفًا، وكم مُخفيّ، ومساحة النظام الذي يقف عليه.
 *
 * والعدّ على **ما قُرئ** لا على المعروض: من أخفى المخفيّ يريد أن يعرف أن هناك ثلاثة
 * مُخفيّة، لا أن يُخبر بأن المجلد فيه عدد أقل. وعند الترشيح يُضاف عدد النتائج صريحًا،
 * فلا يُقرأ فرق العدد كأن شيئًا ضاع.
 */
@Composable
private fun PaneStatusRow(
    state: FilePaneState,
    diskSpace: DiskSpace?,
    entries: Int,
) {
    val counts = FileBrowser.counts(state.entries)
    val parts = buildList {
        add(stringResource(R.string.max_files_status_folders, counts.folders))
        add(stringResource(R.string.max_files_status_files, counts.files))
        if (counts.hidden > 0) add(stringResource(R.string.max_files_status_hidden, counts.hidden))
        if (state.filtering) add(stringResource(R.string.max_files_results_count, entries.toString()))
    }
    val summary = parts.joinToString("  ·  ")
    val summaryLabel = stringResource(R.string.max_files_status_cd)
    val space = diskSpace?.let { disk ->
        stringResource(
            R.string.max_files_status_disk,
            FileFormat.size(disk.usedBytes).orEmpty(),
            FileFormat.size(disk.totalBytes).orEmpty(),
        )
    } ?: stringResource(R.string.max_files_status_disk_unread)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaxSpace.sm, vertical = MaxSpace.hairline),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs),
    ) {
        Text(
            text = summary,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = summaryLabel },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            // المساحة غير المقروءة تُكتب «غير مقروءة» ولا تُسكت: مدير ملفات الصامت عن
            // المساحة أسوأ من الذي يقول إنه لم يقرأها.
            text = space,
            style = MaterialTheme.typography.labelSmall,
            color = if (diskSpace == null) {
                MaxTone.Caution.content()
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

/** حالة اللوح: قراءة · تعذّر · فارغ · لا نتائج. ولا تُعرض حالة بينما توجد مدخلات. */
@Composable
private fun paneCondition(state: FilePaneState, empty: Boolean, searchOpen: Boolean): MaxCondition? = when {
    state.loading && state.entries.isEmpty() -> MaxCondition(
        kind = MaxConditionKind.Loading,
        title = stringResource(R.string.max_files_cond_loading_title),
        detail = stringResource(R.string.max_files_cond_loading_detail),
    )
    state.listing is DirectoryListing.Unreadable -> {
        val reason = state.listing.reason
        MaxCondition(
            kind = when (reason) {
                ListingFailure.NotFound -> MaxConditionKind.Error
                ListingFailure.NotADirectory -> MaxConditionKind.Unsupported
                ListingFailure.PermissionDenied -> MaxConditionKind.PermissionRequired
                ListingFailure.ShellUnavailable -> MaxConditionKind.Disconnected
            },
            title = stringResource(readableTitle(reason)),
            detail = stringResource(readableDetail(reason)),
            technicalDetail = state.path,
        )
    }
    empty && state.query.isNotEmpty() -> MaxCondition(
        kind = MaxConditionKind.Empty,
        title = stringResource(R.string.max_files_pane_no_match_title),
        detail = stringResource(R.string.max_files_pane_no_match_detail, state.query),
    )
    // «لا نتائج» يخصّ سطر الحالة أيضًا: القراءة نجحت وفي المجلد مدخلات، والفارغ هنا
    // نتيجة ترشيح أو إخفاء — فالتفريق بينه وبين مجلد فارغ ليس تفصيلًا.
    empty && !searchOpen && state.entries.isNotEmpty() -> MaxCondition(
        kind = MaxConditionKind.Empty,
        title = stringResource(R.string.max_files_cond_hidden_title),
        detail = stringResource(R.string.max_files_cond_hidden_detail),
    )
    empty -> MaxCondition(
        kind = MaxConditionKind.Empty,
        title = stringResource(R.string.max_files_cond_empty_title),
        detail = stringResource(R.string.max_files_cond_empty_detail),
    )
    else -> null
}

private fun readableTitle(reason: ListingFailure): Int = when (reason) {
    ListingFailure.NotFound -> R.string.max_files_cond_notfound_title
    ListingFailure.NotADirectory -> R.string.max_files_cond_notdir_title
    ListingFailure.PermissionDenied -> R.string.max_files_cond_denied_title
    ListingFailure.ShellUnavailable -> R.string.max_files_cond_shell_title
}

private fun readableDetail(reason: ListingFailure): Int = when (reason) {
    ListingFailure.NotFound -> R.string.max_files_cond_notfound_detail
    ListingFailure.NotADirectory -> R.string.max_files_cond_notdir_detail
    ListingFailure.PermissionDenied -> R.string.max_files_cond_denied_detail
    ListingFailure.ShellUnavailable -> R.string.max_files_cond_shell_detail
}

/** ما يجب أن يُقال عن هذه القراءة بالذات: صفات ناقصة أو صفوف لم تُفهم. */
@Composable
private fun paneBanner(state: FilePaneState): MaxCondition? {
    val loaded = state.listing as? DirectoryListing.Entries ?: return null
    return when {
        !loaded.attributesAvailable -> MaxCondition(
            kind = MaxConditionKind.Unavailable,
            title = stringResource(R.string.max_files_degraded_title),
            detail = stringResource(R.string.max_files_degraded_detail),
        )
        loaded.skippedLines > 0 -> MaxCondition(
            kind = MaxConditionKind.Disconnected,
            title = stringResource(R.string.max_files_skipped_title),
            detail = stringResource(R.string.max_files_skipped_detail, loaded.skippedLines),
        )
        else -> null
    }
}

/** شريط صدق مصغَّر: اللوح ضيّق، فلا يُعرض عبر لوحة كاملة العرض. */
@Composable
private fun FilePaneNotice(condition: MaxCondition) {
    val tone = if (condition.kind == MaxConditionKind.Unavailable) MaxTone.Inactive else MaxTone.Caution
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaxSpace.sm, vertical = MaxSpace.xs),
        shape = RoundedCornerShape(MaxRadius.row),
        color = tone.container(),
    ) {
        Column(
            modifier = Modifier.padding(MaxSpace.sm),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline),
        ) {
            Text(
                text = condition.title,
                style = MaterialTheme.typography.labelLarge,
                color = tone.content(),
            )
            Text(
                text = condition.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * صفّ مدخل واحد — سطر واحد بأربع معلومات.
 *
 * الضغط الطويل يبدأ التحديد ويحدّد المدخل، والضغط القصير على مجلد يفتحه وعلى ملفّ يفتح
 * معاينته. والقائمة **لا** تفتح قائمة سياق عند كل لمسة: القائمة السياقية في شريط الأسفل،
 * حيث تُرى الإجراءات كلها معاً بلا أن تُغطّى القائمة.
 *
 * والحجم لا يُعرض للمجلدات: حجم مجلد غير مقيس، و«0 B» أمامه كذب صريح. والتاريخ يُعرض
 * فقط حين يتّسع اللوح له (عمودان على هاتف ضيّق يعنيان اسمًا بعرض حرفين).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileEntryRow(
    entry: FileEntry,
    selected: Boolean,
    selecting: Boolean,
    showDate: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val tone = if (entry.isDirectory) MaxTone.Accent else MaxTone.Neutral
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MaxSize.minTouchTarget)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = MaxSpace.sm, vertical = MaxSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
    ) {
        Box(
            modifier = Modifier.size(MaxSize.iconGlyph),
            contentAlignment = Alignment.Center,
        ) {
            if (selecting) {
                Icon(
                    imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(MaxSize.iconGlyph),
                )
            } else {
                Icon(
                    imageVector = entry.rowIcon(),
                    contentDescription = null,
                    tint = tone.content(),
                    modifier = Modifier.size(MaxSize.iconGlyph),
                )
            }
        }

        Text(
            text = entry.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // الحجم في عمود ثابت العرض: صفوف بأحجام مختلفة يجب أن تصطفّ في عمود واحد، وإلا
        // تحرّك الرقم مع كل اسم طويل — وهو أصل إحساس «القائمة غير مرتّبة».
        if (!entry.isDirectory) {
            Text(
                text = FileFormat.size(entry.sizeBytes).orEmpty(),
                modifier = Modifier.width(SizeColumnWidth),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        } else {
            Box(modifier = Modifier.width(SizeColumnWidth))
        }

        if (showDate) {
            Text(
                text = fileRowDate(entry.modifiedEpochSec).orEmpty(),
                modifier = Modifier.width(DateColumnWidth),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }

        if (entry.isDirectory) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.max_files_open_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(MaxSize.iconGlyphSmall),
            )
        }
    }
}

/** عرض عمود الحجم. ثابت لأن العمود الذي يتغيّر عرضه ليس عمودًا. */
private val SizeColumnWidth = 62.dp

/** عرض عمود التاريخ — يكفي لـ«9/19/26» بأي لغة. */
private val DateColumnWidth = 58.dp

private fun FileEntry.rowIcon() = when {
    isSymlink -> Icons.Rounded.Link
    kind == FileKind.Directory -> Icons.Rounded.Folder
    kind == FileKind.RegularFile -> Icons.Rounded.Description
    // الرمز `AutoMirrored`: ورقة بمَرْوَن اتجاهي فيجب أن تنقلب في العربية — والرمز غير
    // المنقلب يُنتج ورقةً مقلوبة الوجه في واجهة RTL.
    else -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

/**
 * `OCR-10` — صفّ المرشّح: ثلاثة قوائم صغيرة (النوع · الحجم · العمر) وزرّ إغلاق البحث.
 *
 * **ولماذا قوائم لا رقاع مرشّح متعدّدة:** المرشّح هنا **مركّب** (صورة أكبر من ١٠ م.ب من
 * آخر أسبوع)، وثلاثة أشرطة من الرقاع المتعدّدة تُنتج خيارات متنافية زائفة. وثلاث قوائم
 * مستقلّة تُنتج التوليفات كلها بعنصر واحد لكل بُعد. وكل قائمة تحمل **«الكل»** في أولها.
 *
 * والصفّ لا يظهر إلا مع فتح البحث: كان يستهلك سطرًا دائمًا في كل لوح وهو لا يُستعمل إلا
 * حين يُبحث.
 */
@Composable
private fun FileSearchFilterRow(
    filter: FileSearchFilters.Filter,
    onChange: (FileSearchFilters.Filter) -> Unit,
    onClose: () -> Unit,
) {
    val kinds = FileSearchFilters.Kind.entries
    val sizes = FileSearchFilters.Size.entries
    val ages = FileSearchFilters.Age.entries
    val tone = if (filter.isActive) MaxTone.Accent else MaxTone.Neutral

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaxSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs),
    ) {
        MaxViewMenu(
            labels = kinds.map { stringResource(kindLabel(it)) },
            selectedIndex = kinds.indexOf(filter.kind),
            contentDescription = stringResource(R.string.max_files_filter_kind_cd),
            onSelect = { onChange(filter.copy(kind = kinds[it])) },
            icons = emptyList(),
            triggerIcon = Icons.Rounded.FilterAlt,
        )
        MaxViewMenu(
            labels = sizes.map { stringResource(sizeLabel(it)) },
            selectedIndex = sizes.indexOf(filter.size),
            contentDescription = stringResource(R.string.max_files_filter_size_cd),
            onSelect = { onChange(filter.copy(size = sizes[it])) },
            icons = emptyList(),
            triggerIcon = Icons.Rounded.FilterAlt,
        )
        MaxViewMenu(
            labels = ages.map { stringResource(ageLabel(it)) },
            selectedIndex = ages.indexOf(filter.age),
            contentDescription = stringResource(R.string.max_files_filter_age_cd),
            onSelect = { onChange(filter.copy(age = ages[it])) },
            icons = emptyList(),
            triggerIcon = Icons.Rounded.FilterAlt,
        )
        if (filter.isActive) {
            Text(
                text = stringResource(R.string.max_files_filter_active),
                style = MaterialTheme.typography.labelSmall,
                color = tone.content(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(modifier = Modifier.weight(1f))
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.SearchOff,
                contentDescription = stringResource(R.string.max_files_search_close_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun kindLabel(kind: FileSearchFilters.Kind): Int = when (kind) {
    FileSearchFilters.Kind.ANY -> R.string.max_files_filter_kind_any
    FileSearchFilters.Kind.FOLDER -> R.string.max_files_filter_kind_folder
    FileSearchFilters.Kind.IMAGE -> R.string.max_files_filter_kind_image
    FileSearchFilters.Kind.VIDEO -> R.string.max_files_filter_kind_video
    FileSearchFilters.Kind.AUDIO -> R.string.max_files_filter_kind_audio
    FileSearchFilters.Kind.DOCUMENT -> R.string.max_files_filter_kind_document
    FileSearchFilters.Kind.ARCHIVE -> R.string.max_files_filter_kind_archive
    FileSearchFilters.Kind.APK -> R.string.max_files_filter_kind_apk
}

private fun sizeLabel(size: FileSearchFilters.Size): Int = when (size) {
    FileSearchFilters.Size.ANY -> R.string.max_files_filter_size_any
    FileSearchFilters.Size.OVER_1MB -> R.string.max_files_filter_size_1mb
    FileSearchFilters.Size.OVER_10MB -> R.string.max_files_filter_size_10mb
    FileSearchFilters.Size.OVER_100MB -> R.string.max_files_filter_size_100mb
}

private fun ageLabel(age: FileSearchFilters.Age): Int = when (age) {
    FileSearchFilters.Age.ANY -> R.string.max_files_filter_age_any
    FileSearchFilters.Age.TODAY -> R.string.max_files_filter_age_today
    FileSearchFilters.Age.WEEK -> R.string.max_files_filter_age_week
    FileSearchFilters.Age.MONTH -> R.string.max_files_filter_age_month
}
