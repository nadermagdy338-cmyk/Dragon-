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
 * `OCR-06` — بند «أحدث النسخ» في لوحة `Max Backup`: عدّاد في العنوان، وصفّ تجميع يُنقر.
 *
 * **ولماذا في ملف مستقل:** لوحة `Max Backup` بلغت حدّ الحجم (١٠٠٠ سطر) بعد إضافة سياسة النسخ
 * والعدّاد؛ وهذا البند مكتفٍ بذاته — يأخذ النسخ والتيارات ويعيد العرض.
 *
 * **قراران مقصودان:**
 *
 *  ١. **العدّاد في العنوان** («أحدث النسخ (١١٩)») لا في سطر تحته: الرقم يُعرَف قبل أي تمرير.
 *  ٢. **التجميع إظهار مؤجَّل لا حذف**: «+N أخرى» صفٌّ يُنقر فتظهر البقية **في مكانها**، ثم
 *     «اطوِ القائمة». والقائمة المطويّة تُحفظ عبر إعادة التركيب، فمن كان يقرأ في آخرها لا
 *     يُعاد إلى أولها.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.res.stringResource
import nd.max.R
import nd.max.ui.design.MaxBullets
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxTone
import nd.max.ui.util.MaxBackupCounts
import nd.max.ui.util.MaxBackupModel

/** كم نسخة تُعرض قبل التجميع. والبقية **لا تُخفى**: تُطوى ورقمها ظاهر. */
private const val RECENT_LIMIT = 5

/** بند «أحدث النسخ»: العدّاد، والصفوف، وصفّ التجميع، وتنبيه النسخ غير المكتملة. */
internal fun LazyListScope.maxBackupRecentItem(
    handles: List<MaxBackupModel.Handle>,
    counts: MaxBackupCounts.Summary,
    expanded: Boolean,
    busy: Boolean,
    busyReason: String,
    label: (MaxBackupModel.Handle) -> String,
    onToggleExpanded: () -> Unit,
    onVerify: (MaxBackupModel.Handle) -> Unit,
    onRestore: (MaxBackupModel.Handle) -> Unit,
    onToggleKeep: (MaxBackupModel.Handle) -> Unit,
    onDelete: (MaxBackupModel.Handle) -> Unit,
) {
    if (handles.isEmpty()) return
    val visible = MaxBackupCounts.visibleCount(handles.size, RECENT_LIMIT, expanded)
    val hidden = MaxBackupCounts.overflow(visible, handles.size)

    item(key = "hub_recent_header") {
        MaxSection(
            title = stringResource(R.string.max_backup_recent_title_count, counts.copies.toString()),
            description = stringResource(R.string.max_backup_recent_desc),
        ) {
            MaxGroup {
                handles.take(visible).forEachIndexed { index, handle ->
                    if (index > 0) MaxGroupDivider()
                    BackupHistoryRow(
                        handle = handle,
                        title = label(handle),
                        enabled = !busy,
                        busyReason = busyReason,
                        onVerify = { onVerify(handle) },
                        onRestore = { onRestore(handle) },
                        onToggleKeep = { onToggleKeep(handle) },
                        onDelete = { onDelete(handle) },
                    )
                }
                // صفّ التجميع يظهر إن بقي شيء **أو** إن كان مطويًّا، فلا يعلق المستخدم في
                // قائمة مفتوحة بلا طريق إلى طيّها.
                if (hidden > 0 || expanded) {
                    MaxGroupDivider()
                    MaxRow(
                        title = if (expanded) {
                            stringResource(R.string.max_backup_collapse)
                        } else {
                            stringResource(R.string.max_backup_more_copies, hidden.toString())
                        },
                        icon = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        iconTone = MaxTone.Neutral,
                        onClick = onToggleExpanded,
                    )
                }
            }
            // وما لا يمكن إصلاحه صامتًا يُقال: عدد النسخ غير المكتملة.
            if (counts.incomplete > 0) {
                MaxBullets(
                    lines = listOf(
                        stringResource(R.string.max_backup_incomplete_count, counts.incomplete.toString())
                    )
                )
            }
        }
    }
}
