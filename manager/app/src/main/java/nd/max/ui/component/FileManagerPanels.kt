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
 * أجزاء شاشة مدير الملفات: **شريط أوامر اللوح · شريط التحديد · لوحة التفاصيل · لوحة
 * المعاينة** — وخريطة الأسماء التي تُترجم بها قرارات النموذج إلى نصّ يراه المستخدم.
 *
 * وُجدت في ملف منفصل لسببين لا لتنظيم الشكل: الشاشة تتجاوز حدّ الحجم المعلن في المستودع،
 * و**كل جزء هنا يُقرأ وحده** — لوحة التفاصيل تُراجَع بمعزل عن منطق التنقّل، وشريط التحديد
 * يُراجَع بمعزل عن حوارات العمليات.
 *
 * والقاعدة البصرية في الشريطين: **الأشرطة في أسفل الشاشة**، كما في كل مدير ملفات يعمل
 * بالإبهام. والشريط السفلي يحمل إمّا أدوات اللوح النشط (حين لا يوجد تحديد) أو إجراءات
 * التحديد (حين يوجد) — فلا يوجد شريطان يتنازعان آخر بوصة من الشاشة، ولا إجراء يختفي لأن
 * الشريط الذي يحمله ليس ظاهرًا الآن.
 *
 * و**كل زرّ فيهما باسمه**: الرمز وحده لا يُقرأ إلا من يعرفه سلفًا، والنصّ المسموع مكتوب
 * في كل موضع فيُقرأ بالقارئ وبنفس الوضوح.
 */
package nd.max.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.ui.design.MaxCommand
import nd.max.ui.design.MaxCommandMenu
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.design.border
import nd.max.ui.design.container
import nd.max.ui.design.content
import nd.max.ui.util.FileAction
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileFormat
import nd.max.ui.util.FileKind
import nd.max.ui.util.FileOpRefusal
import nd.max.ui.util.FileOpVerdict
import nd.max.ui.util.FilePermissions
import nd.max.ui.util.FileSortKey
import nd.max.ui.util.TextPreview
import java.text.DateFormat
import java.util.Date

/** حالة قراءة سياق SELinux — أربع حالات، لا «فارغ». */
sealed interface SelinuxState {
    data object NotQueried : SelinuxState
    data object Loading : SelinuxState
    data object NotReported : SelinuxState
    data class Found(val context: String) : SelinuxState
}

/**
 * تاريخ الصفّ بلغة الجهاز: **الساعة** إن كان التعديل اليوم، و**التاريخ** إن كان قبله.
 *
 * وهو قرار قِصر لا تفصيل: عمود بعرض ٥٨ نقطة لا يحمل `9/19/26 17:04`، وأهمّ ما يُسأل عنه
 * في مدير ملفات «أيّها أُضيف الآن» — فالساعة تكفي اليوم، والتاريخ يكفي لغيره. والقيمة
 * الكاملة تُعرض مجمّعة في لوحة التفاصيل، فلا تُفقد معلومة.
 */
@Composable
fun fileRowDate(epochSec: Long?): String? {
    if (epochSec == null) return null
    val millis = epochSec * 1000L
    val format = if (android.text.format.DateUtils.isToday(millis)) {
        DateFormat.getTimeInstance(DateFormat.SHORT)
    } else {
        DateFormat.getDateInstance(DateFormat.SHORT)
    }
    return format.format(Date(millis))
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

/**
 * شريط أوامر اللوح النشط — ستة أزرار، بلا تمرير: صعود · تحديث · مجلد جديد · بحث ·
 * تحديد · مواقع سريعة.
 *
 * **ولماذا لم يعد شريط رأس اللوح:** كان في رأس كل لوح ثمانية أزرار داخل صفّ يمرّ أفقيًّا،
 * يضاف إليها شريط في الصفحة يقول أيّ لوح يُقصد. والنتيجة أن نصف الإجراءات كانت خارج
 * الشاشة، والمساحة العمودية تُصرف على أشرطة لا على قائمة. الآن: الشريط واحد في الأسفل
 * حيث الإبهام، واللوح النشط **مُعلَن في اللوح نفسه** (إطار أعرض وحبّة اسمه).
 */
@Composable
fun FilePaneBar(
    searchOpen: Boolean,
    modifier: Modifier = Modifier,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onNewFolder: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleSelect: () -> Unit,
    quickLocations: List<MaxCommand>,
) {
    val tools = stringResource(R.string.max_files_tools_cd)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxRadius.group),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaxSpace.xs, vertical = MaxSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BarAction(
                icon = Icons.Rounded.ArrowUpward,
                description = stringResource(R.string.max_files_up_cd),
                onClick = onUp,
            )
            BarAction(
                icon = Icons.Rounded.Sync,
                description = stringResource(R.string.max_files_refresh_cd),
                onClick = onRefresh,
            )
            BarAction(
                icon = Icons.Rounded.CreateNewFolder,
                description = stringResource(R.string.max_files_action_new_folder),
                onClick = onNewFolder,
            )
            BarAction(
                icon = Icons.Rounded.Search,
                description = stringResource(R.string.max_files_search_open_cd),
                onClick = onToggleSearch,
                // البحث مفتوح يُعلن بإطار لا بلون فقط: من لا يميّز اللونين يعرف من الحدّ.
                active = searchOpen,
            )
            BarAction(
                icon = Icons.Rounded.SelectAll,
                description = stringResource(R.string.max_files_select_cd),
                onClick = onToggleSelect,
            )
            MaxCommandMenu(
                commands = quickLocations,
                contentDescription = "$tools · ${stringResource(R.string.max_files_quick_locations_cd)}",
                triggerIcon = Icons.Rounded.Star,
            )
        }
    }
}

/**
 * زرّ في شريط اللوح.
 *
 * و`active` تُعلن الحالة **بإطار وحاوية ورمز** لا بلون وحده: من لا يميّز الأزرق من الرمادي
 * يجب أن يرى أن البحث مفتوح. والحاوية مع `BorderStroke` هي الشكل نفسه المستعمل في بقيّة
 * الشاشة، فلا يُخترع نمط ثانٍ لحالة «مُفعَّل».
 */
@Composable
private fun BarAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(MaxRadius.control),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (active) {
            BorderStroke(MaxSize.hairlineBorder, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier.heightIn(min = MaxSize.minTouchTarget),
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * شريط التحديد: العدد، والأربعة التي تُستعمل دائمًا، والباقي في قائمة أوامر واحدة.
 *
 * والعدد ونصّه يقولان **أيّ لوح** يُقصد («٤ محدَّدة · اللوح الأيسر»): الإجراء يقع على لوح
 * واحد، وشريط لا يسمّي لوحه يجعل المستخدم يخمّن أين ستقع عمليته.
 *
 * والإجراءات المدمِّرة في النهاية وبمدلول الخطأ، والمسح آخرها — ترتيب مقصود لا يُخترع في
 * الواجهة بل يأتي من [nd.max.ui.util.FileActionSet] ومعه [FileAction.destructive].
 */
@Composable
fun FileSelectionBar(
    label: String,
    actions: List<FileAction>,
    modifier: Modifier = Modifier,
    onAction: (FileAction) -> Unit,
    extra: List<MaxCommand> = emptyList(),
) {
    val primary = listOf(FileAction.Copy, FileAction.Move, FileAction.Rename, FileAction.Delete)
        .filter { it in actions }
    val secondary = actions.filterNot { it in primary }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxRadius.group),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaxSpace.sm, vertical = MaxSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs),
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            primary.forEach { action ->
                val icon = fileActionIcon(action) ?: return@forEach
                IconButton(onClick = { onAction(action) }) {
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(fileActionLabel(action)),
                        tint = if (action.destructive) {
                            MaxTone.Critical.content()
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            // الباقي بأسمائه لا برموزه: «فكّ هنا» و«ضغط» لا رمز معروف لهما يعرفه كل أحد،
            // وقائمة الأوامر تُقرأ فيها الأسماء كاملة.
            val commands = secondary.map { action ->
                MaxCommand(
                    label = stringResource(fileActionLabel(action)),
                    onSelect = { onAction(action) },
                    icon = fileActionIcon(action),
                    destructive = action.destructive,
                )
            } + extra
            MaxCommandMenu(
                commands = commands,
                contentDescription = stringResource(R.string.max_files_action_more_cd),
            )
        }
    }
}

/**
 * رمز الإجراء — والأسماء هي الحاملة للمعنى، وهذا للتمييز السريع وحده.
 *
 * و`null` للإجراء الذي لا رمز معروف له: «ضغط» و«فكّ» سيظهران في قائمة الأوامر باسميهما
 * كاملين، ولا يُختصران إلى رمزٍ يحتمل معنيين — رمز مجلد على «فكّ أرشيف» يَعِد بشيء آخر.
 */
private fun fileActionIcon(action: FileAction): ImageVector? = when (action) {
    FileAction.Copy -> Icons.Outlined.ContentCopy
    FileAction.Move -> Icons.AutoMirrored.Rounded.ArrowForward
    FileAction.Rename -> Icons.Rounded.Edit
    FileAction.Delete -> Icons.Rounded.DeleteOutline
    FileAction.Details -> Icons.Rounded.Info
    FileAction.Clear -> Icons.Rounded.Close
    FileAction.Compress, FileAction.Extract -> null
}

/**
 * لوحة التفاصيل: كل حقل يعلن هل قُرئ أم لا.
 *
 * وهي **المكان الوحيد** الذي تُعرض فيه الصلاحيات والمالك، بعد أن كانت الصلاحيات تُطبع في
 * كل صفّ قائمة (`drwxr-xr-x`) — وهي معلومة تُسأل مرة عند الحاجة لا في كل نظرة.
 */
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
        border = BorderStroke(MaxSize.hairlineBorder, tone.border()),
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
