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
 * نافذة الضغط: **الاسم · الصيغة · مستوى الضغط** — خياراتٌ تُختار قبل الكتابة لا بعدها.
 *
 * **ولماذا وُجدت:** كان الضغط فعلًا واحدًا باسم مشتقّ من المدخل الأول (`x.zip`) ونصّ الزرّ
 * يقول «اضغط إلى tar.gz» — فالمستخدم لا يختار شيئًا، ثم يُخبره الزرّ بغير ما سيحدث. وهي
 * الحالة التي وصفها المالك بأن الشاشة «تفتقر لخيارات ضغط متعدّدة».
 *
 * والقاعدتان فيها كسائر نوافذ هذا المستودع:
 *
 * 1. **لا تأكيد على قيمة غير صالحة**: [nameProblem] يصل من الحرس ([FileOpGuard]) قبل
 *    الحوار لا بعده، فيُعرض نصُّه ويُعطَّل التأكيد — فلا يُبنى طلب يُرفض فورًا.
 * 2. **الاسم والصيغة لا يفترقان**: تغيير الصيغة يُعيد بناء الامتداد في الشاشة، فلا يُكتب
 *    داخل ملفّ اسمه `.tar.gz` أرشيفُ zip (وهو ما كان ممكنًا قبل ذلك).
 */
package nd.max.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import nd.max.R
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSpace
import nd.max.ui.util.ArchiveFormat
import nd.max.ui.util.CompressionLevel

/** اسم الصيغة كما يُقرأ — تقنيّ فلا يُترجم، ويُكتب مرّة فلا يفترق عن [ArchiveFormat]. */
fun archiveFormatLabel(format: ArchiveFormat): String = when (format) {
    ArchiveFormat.Zip -> "ZIP"
    ArchiveFormat.TarGz -> "TAR.GZ"
}

/** اسم مستوى الضغط — القرار في المحرّك، والاسم هنا (النماذج لا تعرف `R`). */
@Composable
fun compressionLevelLabel(level: CompressionLevel): String = stringResource(
    when (level) {
        CompressionLevel.Store -> R.string.max_files_compress_level_store
        CompressionLevel.Fast -> R.string.max_files_compress_level_fast
        CompressionLevel.Normal -> R.string.max_files_compress_level_normal
        CompressionLevel.Maximum -> R.string.max_files_compress_level_max
    },
)

@Composable
fun FileCompressDialog(
    visible: Boolean,
    name: String,
    format: ArchiveFormat,
    level: CompressionLevel,
    /** عدد المصادر المضغوطة — يُعلن قبل التنفيذ فيعرف المستخدم ما سيُوضع في الأرشيف. */
    sourceCount: Int,
    /** سبب رفض الاسم بنصّه، أو `null`. يأتي من الحرس لا من الواجهة. */
    nameProblem: String?,
    onName: (String) -> Unit,
    onFormat: (ArchiveFormat) -> Unit,
    onLevel: (CompressionLevel) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(MaxRadius.sheet),
        title = { Text(text = stringResource(R.string.max_files_compress_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onName,
                    label = { Text(text = stringResource(R.string.max_files_compress_field)) },
                    singleLine = true,
                    isError = nameProblem != null,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                )
                // سبب الرفض يُقال بجملته: «الاسم مستعمل» غير «الاسم لا يصلح».
                nameProblem?.let { problem ->
                    Text(
                        text = problem,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Text(
                    text = stringResource(R.string.max_files_compress_format),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                    ArchiveFormat.entries.forEach { option ->
                        FilterChip(
                            selected = option == format,
                            onClick = { onFormat(option) },
                            label = { Text(text = archiveFormatLabel(option)) },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.max_files_compress_level),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)) {
                    CompressionLevel.entries.forEach { option ->
                        FilterChip(
                            selected = option == level,
                            onClick = { onLevel(option) },
                            label = { Text(text = compressionLevelLabel(option)) },
                        )
                    }
                }

                Text(
                    text = stringResource(
                        R.string.max_files_compress_summary,
                        sourceCount,
                        archiveFormatLabel(format),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                enabled = nameProblem == null && name.isNotBlank(),
            ) {
                Text(text = stringResource(R.string.max_files_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.max_files_cancel))
            }
        },
    )
}
