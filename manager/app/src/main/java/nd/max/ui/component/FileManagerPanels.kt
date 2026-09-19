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
 * أجزاء شاشة مدير الملفات: شريط المسار · شريط إجراءات اللوح · شريط التحديد · لوحة
 * التفاصيل · لوحة المعاينة.
 *
 * فُصلت عن الشاشة لسببين لا لتنظيم الشكل: الشاشة كانت ستتجاوز حدّ الحجم المعلن في
 * المستودع، و**لأن كل جزء هنا يُقرأ وحده** — لوحة التفاصيل يجب أن تُراجَع بمعزل عن
 * منطق التنقّل، لا أن تكون مدفونة بين مئة سطر من حوارات العمليات.
 */
package nd.max.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.design.MaxSize
import nd.max.ui.design.border
import nd.max.ui.design.container
import nd.max.ui.design.content
import nd.max.ui.util.Crumb
import nd.max.ui.util.FileAction
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileFormat
import nd.max.ui.util.FileKind
import nd.max.ui.util.FileOpRefusal
import nd.max.ui.util.FileOpVerdict
import nd.max.ui.util.FilePaneState
import nd.max.ui.util.FilePermissions
import nd.max.ui.util.FileSortKey
import nd.max.ui.util.PaneSide
import nd.max.ui.util.TextPreview
import java.text.DateFormat
import java.util.Date

/**
 * شريط المسار: كل قطعة تؤدّي إلى مجلدها — التنقّل بلا رجوع متكرّر.
 *
 * `horizontalPadding` قابل للضبط لأن الشريط صار يُستعمل **داخل لوح** لا داخل صفحة:
 * حشوة الصفحة (20dp) كانت ستأكل نصف عرض اللوح في العرض المنقسم.
 */
@Composable
fun FileBreadcrumbs(
    crumbs: List<Crumb>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = MaxSpace.gutter,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = MaxSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs),
    ) {
        crumbs.forEachIndexed { index, crumb ->
            Text(
                text = crumb.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (index == crumbs.lastIndex) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier
                    .clickable(role = Role.Button) { onOpen(crumb.path) }
                    .padding(horizontal = MaxSpace.xs, vertical = MaxSpace.xs),
            )
            if (index != crumbs.lastIndex) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(min = MaxSize.iconGlyphSmall),
                )
            }
        }
    }
}

/** عنوان صفّ: الاسم ثم صفّ الصفات — والصفّ الثاني لا يُخترع فيه رقم أبدًا. */
@Composable
fun fileSubtitle(entry: FileEntry): String {
    val unknown = stringResource(R.string.max_files_unknown)
    val size = FileFormat.size(entry.sizeBytes)
    val permissions = FileFormat.permissions(entry.permissions)
    val modified = entry.modifiedEpochSec?.let { epoch ->
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epoch * 1000))
    }
    val parts = buildList {
        add(entry.kindLabel())
        add(size ?: unknown)
        permissions?.let { add(it) }
        modified?.let { add(it) }
        entry.symlinkTarget?.let { add(stringResource(R.string.max_files_link_to, it)) }
    }
    return parts.joinToString(" · ")
}

/** اسم النوع بلغة المستخدم، من النوع الذي **أعلنه الجهاز** لا من الامتداد. */
@Composable
fun FileKind.label(): String = when (this) {
    FileKind.Directory -> stringResource(R.string.max_files_kind_directory)
    FileKind.RegularFile -> stringResource(R.string.max_files_kind_file)
    FileKind.Symlink -> stringResource(R.string.max_files_kind_symlink)
    FileKind.BlockDevice -> stringResource(R.string.max_files_kind_block)
    FileKind.CharDevice -> stringResource(R.string.max_files_kind_char)
    FileKind.Fifo -> stringResource(R.string.max_files_kind_fifo)
    FileKind.Socket -> stringResource(R.string.max_files_kind_socket)
    FileKind.Unknown -> stringResource(R.string.max_files_kind_unknown)
}

@Composable
private fun FileEntry.kindLabel(): String = kind.label()

/** حالة قراءة سياق SELinux — أربع حالات، لا «فارغ». */
sealed interface SelinuxState {
    data object NotQueried : SelinuxState
    data object Loading : SelinuxState
    data object NotReported : SelinuxState
    data class Found(val context: String) : SelinuxState
}

/** لوحة التفاصيل: كل حقل يعلن هل قُرئ أم لا. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileDetailsPanel(
    entry: FileEntry,
    selinux: SelinuxState,
    onCheckSelinux: () -> Unit,
    onCopyPath: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notRead = stringResource(R.string.max_files_detail_not_read)
    Column(modifier = modifier.fillMaxWidth()) {
        MaxGroup {
            detailRow(stringResource(R.string.max_files_detail_path), entry.path)
            MaxGroupDivider()
            detailRow(stringResource(R.string.max_files_detail_kind), entry.kind.label())
            MaxGroupDivider()
            detailRow(stringResource(R.string.max_files_detail_size), FileFormat.size(entry.sizeBytes) ?: notRead)
            MaxGroupDivider()
            detailRow(
                stringResource(R.string.max_files_detail_modified),
                entry.modifiedEpochSec?.let {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(it * 1000))
                } ?: notRead,
            )
            MaxGroupDivider()
            detailRow(stringResource(R.string.max_files_detail_permissions), permissionsText(entry.permissions) ?: notRead)
            MaxGroupDivider()
            detailRow(stringResource(R.string.max_files_detail_owner), entry.owner ?: notRead)
            MaxGroupDivider()
            detailRow(stringResource(R.string.max_files_detail_group), entry.group ?: notRead)
            if (entry.symlinkTarget != null) {
                MaxGroupDivider()
                detailRow(stringResource(R.string.max_files_detail_symlink), entry.symlinkTarget)
            }
        }

        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_files_detail_selinux),
                subtitle = when (selinux) {
                    SelinuxState.NotQueried -> stringResource(R.string.max_files_detail_not_read)
                    SelinuxState.Loading -> stringResource(R.string.max_files_detail_not_read)
                    SelinuxState.NotReported -> stringResource(R.string.max_files_detail_selinux_unknown)
                    is SelinuxState.Found -> selinux.context
                },
                iconTone = if (selinux is SelinuxState.Found) MaxTone.Neutral else MaxTone.Caution,
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_files_detail_selinux_check),
                onClick = onCheckSelinux,
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_files_detail_copy_path),
                onClick = onCopyPath,
            )
        }
    }
}

/** سطر مفتاح/قيمة داخل لوحة التفاصيل — يُقرأ بمعزل عن صفوف الأزرار. */
@Composable
private fun detailRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaxSpace.rowPaddingHorizontal,
                vertical = MaxSpace.rowPaddingVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun permissionsText(permissions: FilePermissions?): String? {
    val text = FileFormat.permissions(permissions) ?: return null
    return if (permissions != null && permissions.isUnusual) {
        "$text  (${specialFlags(permissions)})"
    } else {
        text
    }
}

@Composable
private fun specialFlags(permissions: FilePermissions): String = buildList {
    if (permissions.setUid) add(stringResource(R.string.max_files_flag_setuid))
    if (permissions.setGid) add(stringResource(R.string.max_files_flag_setgid))
    if (permissions.sticky) add(stringResource(R.string.max_files_flag_sticky))
}.joinToString("+")

/** لوحة المعاينة: لا تعرض شيئًا بلا أن تقول ما الذي تعرضه بالضبط. */
@Composable
fun FilePreviewPanel(
    name: String,
    preview: TextPreview?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.md),
    ) {
        when (preview) {
            null -> Unit
            is TextPreview.Ready -> {
                if (preview.truncated) {
                    NoticeLine(stringResource(R.string.max_files_preview_truncated))
                }
                NoticeLine(stringResource(R.string.max_files_preview_readonly))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(MaxRadius.group),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        text = preview.content.ifEmpty { name },
                        modifier = Modifier.padding(MaxSpace.md).heightIn(max = 420.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextPreview.Binary -> NoticeLine(
                title = stringResource(R.string.max_files_preview_binary_title),
                detail = stringResource(R.string.max_files_preview_binary_detail),
            )
            TextPreview.TooLarge -> NoticeLine(
                title = stringResource(R.string.max_files_preview_large_title),
                detail = stringResource(
                    R.string.max_files_preview_large_detail,
                    FileFormat.size(nd.max.ui.util.FileSystemEngine.MAX_PREVIEW_BYTES).orEmpty(),
                ),
            )
            TextPreview.Unreadable -> NoticeLine(stringResource(R.string.max_files_preview_unreadable))
        }
    }
}

@Composable
private fun NoticeLine(title: String? = null, detail: String? = null, message: String? = null) {
    val tone = MaxTone.Caution
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxRadius.group),
        color = tone.container(),
        border = androidx.compose.foundation.BorderStroke(
            MaxSize.hairlineBorder,
            tone.border(),
        ),
    ) {
        Column(
            modifier = Modifier.padding(MaxSpace.md),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline),
        ) {
            val heading = title ?: message
            if (heading != null) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tone.content(),
                )
            }
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** شريط الإجراءات على التحديد. يظهر فقط حين يوجد تحديد — فلا يشغل الشاشة دائمًا. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileSelectionBar(
    count: Int,
    labelFor: @Composable (Int) -> String,
    /**
     * الإجراءات المنطبقة على هذا التحديد، محسوبة في [nd.max.ui.util.FileActionSet].
     * تُمرَّر جاهزة ولا تُستنتج هنا: ما يصلح وما لا يصلح قرار يُختبر، لا شرط في الرسم.
     */
    actions: List<FileAction>,
    onAction: (FileAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxRadius.group),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(MaxSpace.md),
            verticalArrangement = Arrangement.spacedBy(MaxSpace.xs),
        ) {
            Text(
                text = labelFor(count),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.xs),
            ) {
                actions.forEach { action ->
                    Surface(
                        shape = RoundedCornerShape(MaxRadius.pill),
                        color = if (action.destructive) {
                            MaxTone.Critical.container()
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                        modifier = Modifier.clickable(role = Role.Button) { onAction(action) },
                    ) {
                        Text(
                            text = stringResource(fileActionLabel(action)),
                            modifier = Modifier.padding(horizontal = MaxSpace.sm, vertical = MaxSpace.xs),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (action.destructive) {
                                MaxTone.Critical.content()
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * عنوان الإجراء بلغة المستخدم.
 *
 * وهو هنا لا في [FileAction] عن قصد: الهوية والإتاحة قرار يُختبر في نموذج لا يعرف
 * `R` ولا Compose، والنصّ شأن عرض.
 */
@Composable
fun fileActionLabel(action: FileAction): Int = when (action) {
    FileAction.Copy -> R.string.max_files_action_copy
    FileAction.Move -> R.string.max_files_action_move
    FileAction.Compress -> R.string.max_files_action_compress
    FileAction.Extract -> R.string.max_files_action_extract
    FileAction.Rename -> R.string.max_files_action_rename
    FileAction.Details -> R.string.max_files_action_details
    FileAction.Delete -> R.string.max_files_action_delete
    FileAction.Clear -> R.string.max_files_action_clear_selection
}

/**
 * شريط إجراءات اللوح النشط.
 *
 * وُجد لأنه في العرض المنقسم لا يتّسع لكل لوح شريط إجراءات خاصّ به، ولا يصحّ أن تُخلط
 * إجراءات لوحين في شريط واحد بلا إعلان أيّهما يُقصد. فالشريط يسمّي اللوح النشط، ثم
 * يحمل إجراءاته الأربعة.
 */
@Composable
fun ActivePaneStrip(
    side: PaneSide,
    pane: FilePaneState,
    linked: Boolean,
    onSync: () -> Unit,
    onBack: () -> Unit,
    onSwap: () -> Unit,
    onRefresh: () -> Unit,
    onUp: () -> Unit,
    onNewFolder: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val tone = MaxTone.Accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MaxSpace.sm)
            // الشريط صار يحمل فعلين إضافيين (مزامنة المسار · تبديل اللوحين) بعد أن
            // انتقل الترتيب والربط إلى الشريط العلوي. والتمرير الأفقي يضمن ألا يُقصّ
            // فعل على هاتف ضيّق بدل أن يُضغط بعضه بعضًا — وهو أسوأ من التمرير.
            .horizontalScroll(rememberScrollState()),
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
        StripAction(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            description = stringResource(R.string.max_files_back_cd),
            onClick = onBack,
        )
        StripAction(
            icon = Icons.Rounded.ArrowUpward,
            description = stringResource(R.string.max_files_up_cd),
            onClick = onUp,
        )
        StripAction(
            icon = Icons.Rounded.Sync,
            description = stringResource(R.string.max_files_refresh_cd),
            onClick = onRefresh,
        )
        StripAction(
            icon = Icons.Rounded.CreateNewFolder,
            description = stringResource(R.string.max_files_action_new_folder),
            onClick = onNewFolder,
        )
        StripAction(
            // `AutoMirrored`: سهم التبديل/المقارنة اتجاهي، فيجب أن ينقلب في العربية
            // — والقائمة غير المنقلبة تُنتج زرًّا يشير إلى الجهة الخاطئة في RTL.
            icon = if (pane.selecting) Icons.AutoMirrored.Rounded.CompareArrows else Icons.Rounded.SelectAll,
            description = stringResource(R.string.max_files_select_cd),
            onClick = onToggleSelect,
        )
        StripAction(
            icon = Icons.Rounded.Sync,
            description = stringResource(R.string.max_files_sync_panes_cd),
            onClick = onSync,
        )
        StripAction(
            icon = Icons.Rounded.SwapHoriz,
            description = stringResource(R.string.max_files_swap_panes_cd),
            onClick = onSwap,
        )
        if (linked) {
            // الربط يُقال بالكلام أيضًا: من يفتح هذه الشاشة بلا رؤية للأيقونة الصغيرة
            // في رأس كل لوح يحتاج جملة واحدة تقول إن اللوحين يتحركان معًا.
            Text(
                text = stringResource(R.string.max_files_linked_hint),
                modifier = Modifier.padding(start = MaxSpace.xs),
                style = MaterialTheme.typography.labelSmall,
                color = tone.content(),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StripAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(MaxSize.iconContainer)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** عنوان فرز القائمة بلغة المستخدم — يُترجم عند الاستدعاء كما يترجم أخوه [fileActionLabel]. */
@Composable
fun fileSortLabel(key: FileSortKey): Int = when (key) {
    FileSortKey.Name -> R.string.max_files_sort_name
    FileSortKey.Size -> R.string.max_files_sort_size
    FileSortKey.Modified -> R.string.max_files_sort_date
    FileSortKey.Kind -> R.string.max_files_sort_kind
}

/**
 * أسباب الرفض بلغة المستخدم — الخريطة الوحيدة من [FileOpRefusal] إلى نصّ يراه المستخدم.
 *
 * وهي لا في [FileOpRefusal] عن قصد: القرار يُختبر في نموذج لا يعرف `R` ولا Compose،
 * والنصّ شأن عرض.
 */
@Composable
fun fileRefusalText(reason: FileOpRefusal): String = when (reason) {
    FileOpRefusal.EmptySelection -> stringResource(R.string.max_files_refuse_empty)
    FileOpRefusal.ProtectedPath -> stringResource(R.string.max_files_refuse_protected)
    FileOpRefusal.SelfTarget -> stringResource(R.string.max_files_refuse_self)
    FileOpRefusal.TargetInsideSource -> stringResource(R.string.max_files_refuse_inside)
    FileOpRefusal.InvalidName -> stringResource(R.string.max_files_refuse_name)
    FileOpRefusal.NameTaken -> stringResource(R.string.max_files_refuse_taken)
}

/** النصّ لنتيجة حكم جاهزة، أو `null` إن كان الطلب مقبولًا — فيبقى حقل الدعم فارغًا حين لا رفض. */
@Composable
fun fileRefusalVerdictText(verdict: FileOpVerdict?): String? =
    (verdict as? FileOpVerdict.Refused)?.let { fileRefusalText(it.reason) }
