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
 * قائمة المدخلات: **سطر واحد لكل مدخل** كما في MT.
 *
 * وكان كل صفّ سطرين (الاسم، ثم «مجلد · 1.6 KB · drwxr-xr-x · 9/19/26» مجموعةً): أي أن
 * الشاشة كانت تنفق نصف ارتفاعها على معلومات تُسأل مرّة واحدة عند الحاجة، وتُعيد الصلاحيات
 * في كل صفّ لأن لا مكان لها غيره. هنا: رمز · اسم · حجم · تاريخ — والصلاحيات في نافذة
 * الخصائص، حيث تُعدَّل أصلًا.
 *
 * ولماذا عمودان ثابتا العرض للحجم والتاريخ: قائمة تتحرّك أعمدةُ أرقامها مع كل اسم طويل
 * تُقرأ سطرًا سطرًا لا عمودًا عمودًا، والغرض من الصفّ أن يُقارَن بجاره بنظرة.
 *
 * ولماذا موضع اللمس يُقاس: قائمة الأوامر تُفتح **عند الإصبع** لا في زاوية الشاشة، وهذا
 * يقتضي أن يُعرف موضع الصفّ في الجذر وقت الضغط الطويل — يُقاس بـ`positionInRoot` ويُحفظ
 * لكل مسار، فيبقى صحيحًا بعد أي تمرير بلا حساب فهارس ولا ارتفاع صفّ.
 */
package nd.max.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxSpace
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileFormat
import nd.max.ui.util.FileKind
import nd.max.ui.util.FileSearchFilters
import nd.max.ui.util.FileSelection

/**
 * قائمة القراءة الوحيدة في الشاشة.
 *
 * @param onOpen النقرة العادية: فتح المجلد، أو المحرّر، أو تسليم الملف لتطبيق آخر.
 * @param onLongPress الضغط الطويل: يحدّد المدخل **ويفتح قائمته** عند موضع الإصبع المطلق.
 */
@Composable
fun FileEntryList(
    entries: List<FileEntry>,
    selection: FileSelection,
    selecting: Boolean,
    onOpen: (FileEntry) -> Unit,
    onToggleSelection: (FileEntry) -> Unit,
    onLongPress: (FileEntry, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowOrigins = remember { mutableStateMapOf<String, Offset>() }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(items = entries, key = { entry -> entry.path }) { entry ->
            FileEntryRow(
                entry = entry,
                selected = entry.path in selection.paths,
                selecting = selecting,
                onOpen = { onOpen(entry) },
                onToggleSelection = { onToggleSelection(entry) },
                onLongPress = { local ->
                    onLongPress(entry, (rowOrigins[entry.path] ?: Offset.Zero) + local)
                },
                onPositioned = { origin -> rowOrigins[entry.path] = origin },
            )
        }
    }
}

@Composable
private fun FileEntryRow(
    entry: FileEntry,
    selected: Boolean,
    selecting: Boolean,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
    onLongPress: (Offset) -> Unit,
    onPositioned: (Offset) -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates -> onPositioned(coordinates.positionInRoot()) }
            .background(background)
            .pointerInput(entry.path, selecting) {
                detectTapGestures(
                    onTap = { if (selecting) onToggleSelection() else onOpen() },
                    onLongPress = { offset -> onLongPress(offset) },
                )
            }
            .heightIn(min = ROW_MIN_HEIGHT)
            .padding(horizontal = MaxSpace.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
    ) {
        if (selecting) {
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                modifier = Modifier.size(CheckboxSize),
            )
        }
        Icon(
            imageVector = entryGlyph(entry),
            contentDescription = null,
            tint = if (entry.isDirectory) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(GlyphSize),
        )
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (entry.isDirectory) MAX_VALUE_UNAVAILABLE else FileFormat.size(entry.sizeBytes) ?: MAX_VALUE_UNAVAILABLE,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(SizeColumnWidth),
        )
        Text(
            text = FileFormat.date(entry.modifiedEpochSec) ?: MAX_VALUE_UNAVAILABLE,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(DateColumnWidth),
        )
    }
}

/**
 * رمز النوع. والامتداد يُقرأ من مصنّف المرشّحات نفسه ([FileSearchFilters.classify]) فلا
 * يختلف ما يراه الصفّ عمّا يرشّحه المستخدم بنوعه.
 */
private fun entryGlyph(entry: FileEntry): ImageVector = when {
    entry.isDirectory -> Icons.Rounded.Folder
    entry.isSymlink -> Icons.Rounded.Link
    entry.kind == FileKind.BlockDevice || entry.kind == FileKind.CharDevice -> Icons.Rounded.Memory
    else -> when (FileSearchFilters.classify(entry.name, isDirectory = false)) {
        FileSearchFilters.Kind.APK -> Icons.Rounded.Android
        FileSearchFilters.Kind.ARCHIVE -> Icons.Rounded.FolderZip
        FileSearchFilters.Kind.IMAGE -> Icons.Rounded.Image
        FileSearchFilters.Kind.VIDEO -> Icons.Rounded.Movie
        FileSearchFilters.Kind.AUDIO -> Icons.Rounded.MusicNote
        FileSearchFilters.Kind.DOCUMENT -> Icons.Rounded.Description
        else -> Icons.Rounded.InsertDriveFile
    }
}

private val ROW_MIN_HEIGHT = 44.dp
private val GlyphSize = 20.dp
private val CheckboxSize = 20.dp
private val SizeColumnWidth = 68.dp
private val DateColumnWidth = 84.dp
