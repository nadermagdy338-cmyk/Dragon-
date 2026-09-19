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
 * `FM-01` — العمود الذي يُعرض داخل كل لوح من لوحَي مدير الملفات.
 *
 * وهو يملك **قائمته الكسولة** بنفسه، وهذا هو السبب المعماري لوجوده: صفحة ذات لوحين
 * لا تستطيع أن تكون قائمة واحدة، ولا تستطيع أن تضع قائمة كسولة داخل تمرير عمودي.
 * فكل لوح يصير وحدة كاملة: رأسه · مساره · بحثه · مدخلاته.
 *
 * والقاعدة البصرية: **اللوح النشط وحده يحمل إطارًا ملوّنًا**، لأن كل إجراء في الشريط
 * العلوي (تحديث · صعود · مجلد جديد · تحديد) يُطبَّق على اللوح النشط — ولو تساوى
 * اللوحان بصريًّا لصار على المستخدم أن يخمّن أين ستقع عمليته.
 */
package nd.max.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import nd.max.R
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxConditionPanel
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSearchField
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.design.container
import nd.max.ui.design.content
import nd.max.ui.util.DirectoryListing
import nd.max.ui.util.FileBrowser
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileKind
import nd.max.ui.util.FilePaneState
import nd.max.ui.util.ListingFailure
import nd.max.ui.util.PaneSide

@Composable
fun FilePaneColumn(
    side: PaneSide,
    state: FilePaneState,
    active: Boolean,
    modifier: Modifier = Modifier,
    /** هل تنقّل هذا اللوح مرتبط بالآخر — يُعلَن في الرأس، فلا يبقى الربط حالة مخفية. */
    linked: Boolean = false,
    onActivate: () -> Unit,
    onNavigate: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onEntryClick: (FileEntry) -> Unit,
    onEntryLongPress: (FileEntry) -> Unit,
) {
    val entries = state.visible()
    val tone = if (active) MaxTone.Accent else MaxTone.Neutral

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(MaxRadius.group),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            if (active) MaxSize.activeRing else MaxSize.hairlineBorder,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaneHeader(
                side = side,
                state = state,
                active = active,
                linked = linked,
                tone = tone,
                onActivate = onActivate,
            )

            FileBreadcrumbs(
                crumbs = FileBrowser.breadcrumbs(state.path),
                onOpen = { target -> onActivate(); onNavigate(target) },
                horizontalPadding = MaxSpace.sm,
            )

            MaxSearchField(
                value = state.query,
                onValueChange = { onActivate(); onQueryChange(it) },
                placeholder = stringResource(R.string.max_files_search_placeholder),
                clearContentDescription = stringResource(R.string.max_files_search_clear),
                modifier = Modifier.padding(horizontal = MaxSpace.sm),
            )

            // كل ما يحتاج تركيبًا يُحسب **قبل** الدخول في نطاق `LazyColumn`: فنطاق القائمة
            // الكسولة ليس سياق تركيب، ودالّة @Composable لا تُنادى داخله أصلًا.
            val condition = paneCondition(state, entries.isEmpty())
            val notice = paneBanner(state)
            if (condition != null) {
                // `weight` لا `fillMaxSize`: داخل `Column` يقيس الأبناء غير الموزونين بأقصى
                // ارتفاع متاح لا بالمتبقّي منه، فقائمة تأخذ الارتفاع كلّه تدفع الرأس والبصحة
                // خارج حدود اللوح (وهو عطب تخطيط صامت، لا تحذير من المصرّف).
                Box(
                    modifier = Modifier
                        .padding(MaxSpace.sm)
                        .weight(1f),
                ) {
                    MaxConditionPanel(condition)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = MaxSpace.sm,
                        end = MaxSpace.sm,
                        top = MaxSpace.xs,
                        bottom = MaxSpace.md,
                    ),
                    verticalArrangement = Arrangement.spacedBy(MaxSpace.xs),
                ) {
                    notice?.let { banner ->
                        item(key = "pane_banner") { FilePaneNotice(banner) }
                    }
                    items(entries, key = { it.path }) { entry ->
                        FileEntryRow(
                            entry = entry,
                            selected = state.isSelected(entry.path),
                            selecting = state.selecting,
                            onClick = { onActivate(); onEntryClick(entry) },
                            onLongClick = { onActivate(); onEntryLongPress(entry) },
                        )
                    }
                }
            }
        }
    }
}

/** رأس اللوح: أيّ لوح، وعلى أي مجلد، وهل هو النشط. */
@Composable
private fun PaneHeader(
    side: PaneSide,
    state: FilePaneState,
    active: Boolean,
    linked: Boolean,
    tone: MaxTone,
    onActivate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(role = Role.Button, onClick = onActivate)
            .padding(horizontal = MaxSpace.sm, vertical = MaxSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs),
    ) {
        Surface(
            shape = RoundedCornerShape(MaxRadius.pill),
            color = tone.container(),
        ) {
            Text(
                text = stringResource(
                    if (side == PaneSide.Left) R.string.max_files_pane_left else R.string.max_files_pane_right
                ),
                modifier = Modifier.padding(horizontal = MaxSpace.sm, vertical = MaxSpace.hairline),
                style = MaterialTheme.typography.labelLarge,
                color = tone.content(),
            )
        }
        Text(
            text = if (state.loading) stringResource(R.string.max_files_cond_loading_title) else state.path,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // علامة الربط رمزٌ **و**وصف: من لا يرى الأيقونة يسمع الحالة، ولا يعتمد الربط على
        // مقارنة مسارين لِيُفهَم. وتُعرض في اللوح الآخر أيضًا — لأن الربط علاقة بين اثنين.
        if (linked) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = stringResource(R.string.max_files_linked_cd),
                modifier = Modifier.size(MaxSize.iconGlyphSmall),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** حالة اللوح: قراءة · تعذّر · فارغ. ولا تُعرض حالة بينما توجد مدخلات. */
@Composable
private fun paneCondition(state: FilePaneState, empty: Boolean): MaxCondition? = when {
    state.loading && state.entries.isEmpty() -> MaxCondition(
        kind = MaxConditionKind.Loading,
        title = stringResource(R.string.max_files_cond_loading_title),
        detail = stringResource(R.string.max_files_cond_loading_detail),
    )
    state.listing is DirectoryListing.Unreadable -> {
        val reason = (state.listing as DirectoryListing.Unreadable).reason
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
    val tone = condition.kind.let { kind ->
        if (kind == MaxConditionKind.Unavailable) MaxTone.Inactive else MaxTone.Caution
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
 * صفّ مدخل داخل لوح.
 *
 * الضغط الطويل يبدأ التحديد، والضغط القصير على مجلد يفتحه وعلى ملفّ يفتح معاينته —
 * فلا قائمة سياق تُفتح عند كل لمسة، وهذا فرق محسوس على قائمة طويلة.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileEntryRow(
    entry: FileEntry,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val tone = if (entry.isDirectory) MaxTone.Accent else MaxTone.Neutral
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(MaxRadius.row),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (selected) {
            BorderStroke(MaxSize.hairlineBorder, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MaxSize.minTouchTarget)
                .padding(horizontal = MaxSpace.sm, vertical = MaxSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
        ) {
            Surface(
                shape = RoundedCornerShape(MaxRadius.control),
                color = tone.container(),
                modifier = Modifier.size(MaxSize.rowIconContainer),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = entry.rowIcon(),
                        contentDescription = null,
                        tint = tone.content(),
                        modifier = Modifier.size(MaxSize.iconGlyphSmall),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline),
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = fileSubtitle(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                selecting -> Icon(
                    imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                entry.isDirectory -> Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = stringResource(R.string.max_files_open_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun FileEntry.rowIcon() = when (kind) {
    FileKind.Directory -> Icons.Rounded.Folder
    FileKind.Symlink -> Icons.Rounded.Link
    FileKind.RegularFile -> Icons.Rounded.Description
    else -> Icons.Rounded.InsertDriveFile
}
