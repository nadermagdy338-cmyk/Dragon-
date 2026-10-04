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
 * كروم النافذة في مدير الملفات: **تبويباها · مسارها · سطر حالتها** — ولا شيء رابع.
 *
 * وهذا هو الفرق بين هذه الشاشة وما كانت عليه: أربعة أشرطة لكل لوح (حبّة ومسار وفتات خبز
 * وحقل بحث دائم)، أي أن أول صفّ ملف كان يبدأ بعد ثلث الشاشة. هنا ثلاثة أشرطة **مرّة
 * واحدة للشاشة كلها**، ثم قائمة تمتدّ إلى آخر بكسل.
 *
 * والقواعد التي تحكم الأشرطة الثلاثة:
 *
 * 1. **النافذة النشطة معلنة بالشكل لا باللون وحده**: تسمية أثقل، وخطّ سفلي أعرض، ومسار
 *    ظاهر تحتها — فلا يخمّن المستخدم أين سيقَع لصقه.
 * 2. **المسار يُقرأ كاملًا**: يُمرَّر أفقيًّا بدل أن يُقصّ، لأن مسارًا مقصوصًا في منتصفه
 *    لا يقول لا اسم الملف ولا المجلدالأب.
 * 3. **سطر الحالة يعلن ما لم يُقرأ**: عدد المخفيّ ظاهر دائمًا (فلا يُقرأ غيابها كأنها
 *    حُذفت)، ومساحة القرص تُقال «لم تُقرأ» إن لم تُقس، ولا تُكتب صفرًا أبدًا (ADR-23).
 */
package nd.max.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.ui.design.MaxCommand
import nd.max.ui.design.MaxCommandMenu
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.util.DiskSpace
import nd.max.ui.util.EntryCounts
import nd.max.ui.util.FileFormat
import nd.max.ui.util.FileWindowState
import nd.max.ui.util.FileWindowsState
import nd.max.ui.util.WindowSide

/**
 * تبويبا النافذة (MT: نوافذه في الأعلى) + قائمة أوامر النافذة.
 *
 * وليس تبويبًا لكل مجلد مفتوح: العدد قرار معلن (نافذتان)، والتسمية يضعها المستخدم فيصرّح
 * بالمعنى («النظام» · «التحميل») بدل اسم مجلد يتكرّر في النافذتين.
 */
@Composable
fun FileWindowTabs(
    windows: FileWindowsState,
    onSelect: (WindowSide) -> Unit,
    menu: List<MaxCommand>,
    menuDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WindowSide.entries.forEach { side ->
            val window = windows.of(side)
            WindowTab(
                window = window,
                active = windows.active == side,
                onClick = { onSelect(side) },
                modifier = Modifier.weight(1f),
            )
        }
        MaxCommandMenu(
            commands = menu,
            contentDescription = menuDescription,
            modifier = Modifier.width(MaxSize.minTouchTarget),
        )
    }
}

@Composable
private fun WindowTab(
    window: FileWindowState,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val labelColor = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = MaxSpace.md, vertical = MaxSpace.sm),
    ) {
        Text(
            text = window.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = window.path,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .padding(top = MaxSpace.xs)
                .fillMaxWidth()
                .height(if (active) MaxSize.activeRing else MaxSize.hairlineBorder)
                .background(if (active) accent else MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/**
 * شريط المسار: رجوع · المسار نفسه (يُلمس فيُكتب مسار آخر) · بحث · أوامر الشاشة.
 *
 * و**الرجوع في التاريخ** لا صعودًا إلى الأب (قرار المالك): من دخل مجلدًا ثم آخر يريد
 * العودة إلى ما كان يقرأه، لا إلى الأب دائمًا.
 */
@Composable
fun FilePathBar(
    path: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onEditPath: () -> Unit,
    onSearch: () -> Unit,
    menu: List<MaxCommand>,
    menuDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaxSpace.sm, vertical = MaxSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = canGoBack) {
            Icon(
                // `AutoMirrored`: سهم الرجوع يجب أن ينقلب في RTL — وهو **الوحيد** في
                // `ui/**` الذي كان على الصيغة غير المنعكسة (قِيس بـ`grep`: ٥٣ موضعًا
                // منعكسًا مقابل هذا). السهم غير المنعكس يشير إلى اليمين في العربية =
                // «تقدّم» لا «رجوع».
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.max_files_back_cd),
                tint = if (canGoBack) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
        Text(
            text = path,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .clickable(onClick = onEditPath)
                .padding(horizontal = MaxSpace.sm, vertical = MaxSpace.sm),
        )
        IconButton(onClick = onSearch) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.max_files_search_open_cd),
            )
        }
        MaxCommandMenu(
            commands = menu,
            contentDescription = menuDescription,
            modifier = Modifier.width(MaxSize.minTouchTarget),
        )
    }
}

/**
 * سطر الحالة: ما قُرئ من هذا المجلد — مجلدات · ملفات · مخفيّ · مساحة.
 *
 * و`counts == null` تعني «القراءة لم تنتهِ» لا «صفر ملفات»: فرق بين سطر ينتظر وسطر يكذب.
 */
@Composable
fun FileStatusLine(
    counts: EntryCounts?,
    disk: DiskSpace?,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val separator = " · "
    val text = when {
        loading && counts == null -> stringResource(R.string.max_files_cond_loading_title)
        counts == null -> stringResource(R.string.max_files_unknown)
        else -> buildList {
            add(stringResource(R.string.max_files_status_folders, counts.folders))
            add(stringResource(R.string.max_files_status_files, counts.files))
            add(stringResource(R.string.max_files_status_hidden, counts.hidden))
            add(
                if (disk == null) {
                    stringResource(R.string.max_files_status_disk_unread)
                } else {
                    stringResource(
                        R.string.max_files_status_disk,
                        FileFormat.size(disk.usedBytes) ?: stringResource(R.string.max_files_unknown),
                        FileFormat.size(disk.totalBytes) ?: stringResource(R.string.max_files_unknown),
                    )
                }
            )
        }.joinToString(separator)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaxSpace.lg, vertical = MaxSpace.xs),
    )
}

/** صفّ أفقي من أزرار بأسماء: كل زرّ أيقونة **ومعها اسمه**، فلا رمز بلا معنى. */
@Composable
fun FileToolButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = MaxSpace.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaxSpace.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.height(MaxSize.iconGlyph),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) tint else tint.copy(alpha = 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
