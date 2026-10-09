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
 * درج مدير الملفات: **الأماكن · المفضّلة · السجل · الأدوات**.
 *
 * وُطلب أن يكون «أحسن بكثير» من درج MT، والفرق المقاس ثلاثة:
 *
 * 1. **المسار نفسه في رأس الدرج** — فتتطابق المسارات في درج واحد بدل أن يكون «الأماكن»
 *    وحده هو القابل للمس، والمجلد الذي أنت فيه لا يُلمس.
 * 2. **المفضّلة والسجل مفصولان**: المفضّلة ما اختاره المستخدم ليُثبَّت، والسجل ما زاره
 *    عرَضًا. دمجهما كان يمحو الفرق، فيضيع المُثبَّت بين عشرات الزيارات.
 * 3. **الأدوات تقول حالتها المقيسة** لا اسمها فقط: تعليق القراءة/الكتابة يُعلن ما قُرئ من
 *    `/proc/mounts` (`r/w` · `r/o` · لم تُقرأ) لا أن الزرّ «مضغوط».
 */
package nd.max.ui.component

import nd.max.ui.design.MaxScrollRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.ui.design.MaxSpace
import nd.max.ui.util.Crumb
import nd.max.ui.util.FileBookmark
import nd.max.ui.util.HistoryEntry
import nd.max.ui.util.MountAccess

@Composable
fun FileDrawerContent(
    path: String,
    crumbs: List<Crumb>,
    bookmarks: List<FileBookmark>,
    history: List<HistoryEntry>,
    hiddenShown: Boolean,
    mountAccess: MountAccess?,
    runningTasks: Int,
    onNavigate: (String) -> Unit,
    onAddBookmark: () -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onClearHistory: () -> Unit,
    onToggleHidden: () -> Unit,
    onRemount: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.lg)) {

        // ── المسار الحالي، شرائح تُلمس ──────────────────────────────────────
        Section(title = stringResource(R.string.max_files_drawer_places)) {
            MaxScrollRow(bleed = MaxSpace.lg, edge = MaxSpace.lg, spacing = MaxSpace.sm) {
                crumbs.forEach { crumb ->
                    AssistChip(
                        onClick = { onNavigate(crumb.path) },
                        label = { Text(text = crumb.label, maxLines = 1) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
            Text(
                text = path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = MaxSpace.xs),
            )
        }

        // ── المفضّلة ────────────────────────────────────────────────────────
        Section(
            title = stringResource(R.string.max_files_drawer_bookmarks),
            onAction = stringResource(R.string.max_files_bookmark_add),
            actionIcon = Icons.Rounded.BookmarkAdd,
            onActionClick = onAddBookmark,
        ) {
            if (bookmarks.isEmpty()) {
                EmptyLine(text = stringResource(R.string.max_files_bookmarks_empty))
            } else {
                bookmarks.forEach { bookmark ->
                    DrawerRow(
                        title = bookmark.display,
                        subtitle = bookmark.path,
                        icon = Icons.Rounded.Bookmark,
                        onClick = { onNavigate(bookmark.path) },
                        trailing = {
                            IconButton(onClick = { onRemoveBookmark(bookmark.path) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.max_files_bookmark_remove),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                    )
                }
            }
        }

        // ── السجل ───────────────────────────────────────────────────────────
        Section(
            title = stringResource(R.string.max_files_drawer_history),
            onAction = stringResource(R.string.max_files_history_clear),
            actionIcon = null,
            onActionClick = onClearHistory,
        ) {
            if (history.isEmpty()) {
                EmptyLine(text = stringResource(R.string.max_files_history_empty))
            } else {
                history.take(HISTORY_VISIBLE).forEach { entry ->
                    DrawerRow(
                        title = FileDisplayName(entry.path),
                        subtitle = entry.path,
                        icon = Icons.Rounded.History,
                        onClick = { onNavigate(entry.path) },
                    )
                }
            }
        }

        // ── الأدوات ─────────────────────────────────────────────────────────
        Section(title = stringResource(R.string.max_files_drawer_tools)) {
            DrawerRow(
                title = if (hiddenShown) {
                    stringResource(R.string.max_files_hide_hidden)
                } else {
                    stringResource(R.string.max_files_show_hidden)
                },
                subtitle = if (hiddenShown) {
                    stringResource(R.string.max_files_drawer_hidden_shown)
                } else {
                    stringResource(R.string.max_files_drawer_hidden_hidden)
                },
                icon = if (hiddenShown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                onClick = onToggleHidden,
            )
            DrawerRow(
                title = stringResource(R.string.max_files_root_access),
                subtitle = mountAccessText(mountAccess),
                icon = if (mountAccess == MountAccess.ReadWrite) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                onClick = { onRemount(mountAccess != MountAccess.ReadWrite) },
            )
            if (runningTasks > 0) {
                DrawerRow(
                    title = stringResource(R.string.max_files_drawer_tasks),
                    subtitle = stringResource(R.string.max_files_drawer_tasks_running, runningTasks),
                    icon = Icons.Rounded.Storage,
                )
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    onAction: String? = null,
    actionIcon: ImageVector? = null,
    onActionClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (onAction != null && onActionClick != null) {
                TextButton(onClick = onActionClick) {
                    if (actionIcon != null) {
                        Icon(
                            imageVector = actionIcon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 2.dp),
                        )
                    }
                    Text(text = onAction)
                }
            }
        }
        content()
    }
}

@Composable
private fun DrawerRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = MaxSpace.sm, vertical = MaxSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.md),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaxSpace.sm),
    )
}

/** حالة تعليق القراءة/الكتابة كما قُرئت — لا كما وُعد بها. */
@Composable
private fun mountAccessText(access: MountAccess?): String = when (access) {
    MountAccess.ReadWrite -> stringResource(R.string.max_files_root_state_rw)
    MountAccess.ReadOnly -> stringResource(R.string.max_files_root_state_ro)
    null, MountAccess.Unknown -> stringResource(R.string.max_files_root_state_unknown)
}

/** اسم المجلد من مساره: لا منطق مسارات جديد في طبقة العرض. */
private fun FileDisplayName(path: String): String {
    val trimmed = path.trimEnd('/')
    if (trimmed.isEmpty()) return path
    val name = trimmed.substringAfterLast('/')
    return name.ifEmpty { "/" }
}

private const val HISTORY_VISIBLE = 12
