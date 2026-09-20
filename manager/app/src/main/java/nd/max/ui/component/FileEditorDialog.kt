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
 * المحرّر النصّي الداخلي: يفتح · يُعدّل · **يحفظ بقياس**.
 *
 * وهو مُقيَّد بثلاثة حدود معلنة، كلٌّ منها مفاتيح صدق لا عوائق:
 *
 * 1. **حجم** ([FileOpenPlan.MAX_EDITOR_BYTES]): فوقه يُسلَّم الملف لتطبيق خارجي، لأن محرّرًا
 *    يبتلع ميغابايتات في `TextField` يجمّد الشاشة ثم يُفقد التعديل.
 * 2. **ثنائي**: ما ليس نصًّا لا يُعرض نصًّا (يُقال ذلك بدل عرض رموز بلا معنى).
 * 3. **الحفظ عبر الحرس**: `FileOperation.WriteText` يمرّ بـ[FileOpGuard] مثل كل عملية،
 *    والإثبات **مقاس**: حجم الملف على القرص بعد الكتابة يقارَن بحجم النصّ بالبايت.
 *
 * و«سطور عريضة» تسمية مقصودة لا «إلغاء الالتفاف»: `BasicTextField` في Compose يلفّ عند
 * عرض صندوقه، فالبديل الأمين هو توسيع الصندوق إلى عرض كبير يُمرَّر أفقيًّا — وهو يكفي كل
 * أسطر الإعدادات عمليًّا، **وحدّه معلن**: سطر أطول من العرض الموسَّع يبقى يلفّ.
 */
package nd.max.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.WrapText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import nd.max.R
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.util.TextPreview

@Composable
fun FileEditorDialog(
    visible: Boolean,
    path: String,
    preview: TextPreview?,
    text: String,
    onTextChange: (String) -> Unit,
    wideLines: Boolean,
    onToggleWideLines: () -> Unit,
    dirty: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReload: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    saving: Boolean,
    verdict: String?,
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaxSpace.sm, vertical = MaxSpace.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.max_files_editor_close),
                        )
                    }
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onUndo, enabled = canUndo) {
                        Icon(
                            imageVector = Icons.Rounded.Undo,
                            contentDescription = stringResource(R.string.max_files_editor_undo),
                        )
                    }
                    IconButton(onClick = onRedo, enabled = canRedo) {
                        Icon(
                            imageVector = Icons.Rounded.Redo,
                            contentDescription = stringResource(R.string.max_files_editor_redo),
                        )
                    }
                    IconButton(onClick = onToggleWideLines) {
                        Icon(
                            imageVector = Icons.Rounded.WrapText,
                            contentDescription = stringResource(R.string.max_files_editor_wide_lines),
                            tint = if (wideLines) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = onReload) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.max_files_editor_reload),
                        )
                    }
                    IconButton(onClick = onSave, enabled = dirty && !saving) {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = stringResource(R.string.max_files_editor_save),
                            tint = if (dirty) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                if (saving) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                verdict?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = MaxSpace.lg, vertical = MaxSpace.xs),
                    )
                }

                when (preview) {
                    null -> Text(
                        text = stringResource(R.string.max_files_preview_loading_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(MaxSpace.lg),
                    )
                    is TextPreview.Binary -> Refusal(
                        title = stringResource(R.string.max_files_preview_binary_title),
                        detail = stringResource(R.string.max_files_preview_binary_detail),
                    )
                    is TextPreview.TooLarge -> Refusal(
                        title = stringResource(R.string.max_files_preview_large_title),
                        detail = stringResource(
                            R.string.max_files_preview_large_detail,
                            stringResource(R.string.max_files_preview_limit),
                        ),
                    )
                    is TextPreview.Unreadable -> Refusal(
                        title = stringResource(R.string.max_files_preview_unreadable),
                        detail = stringResource(R.string.max_files_editor_unreadable_detail),
                    )
                    is TextPreview.Ready -> EditorBody(
                        text = text,
                        onTextChange = onTextChange,
                        wideLines = wideLines,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.EditorBody(
    text: String,
    onTextChange: (String) -> Unit,
    wideLines: Boolean,
) {
    val fieldModifier = if (wideLines) {
        Modifier
            .widthIn(min = WIDE_LINE_WIDTH)
            .padding(MaxSpace.lg)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(MaxSpace.lg)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = MaxSpace.md)
            .border(
                width = MaxSize.hairlineBorder,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(MaxRadius.row),
            )
            .horizontalScroll(rememberScrollState()),
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = fieldModifier
                .heightIn(min = MaxSize.dialogListMax)
                .verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun Refusal(title: String, detail: String) {
    Column(modifier = Modifier.padding(MaxSpace.lg)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val WIDE_LINE_WIDTH = 3600.dp
