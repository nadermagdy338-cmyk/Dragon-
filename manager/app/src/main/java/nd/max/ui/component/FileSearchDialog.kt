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
 * حوار البحث العميق: **الحدود تُكتب قبل البحث لا تُخفى بعده**.
 *
 * وهو الفرق بين بحث يُبنى عليه قرار وبحث يكذب: من بحث في `/data` بعمق ٥ و٥٠٠ نتيجة وعشر
 * ثوانٍ يعرف سلفًا أن غياب ملف قد يعني «لم يُبلَغ»، والنتيجة نفسها تُعلن أعلامها:
 * «بلغ حدّ النتائج» · «بلغ حدّ العمق» · «انتهت المهلة» · «مجلدات لم تُقرأ» · «أُلغي».
 * وحين لا يُرفع علم يُقال صراحةً إن النتيجة كاملة — فيكون للاثنين معنى.
 */
package nd.max.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import nd.max.R
import nd.max.ui.design.MaxSpace
import nd.max.ui.util.DeepSearchOutcome
import nd.max.ui.util.SearchLimits

@Composable
fun FileSearchDialog(
    visible: Boolean,
    root: String,
    query: String,
    onQuery: (String) -> Unit,
    limits: SearchLimits,
    onLimits: (SearchLimits) -> Unit,
    running: Boolean,
    outcome: DeepSearchOutcome?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.max_files_search_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                Text(
                    text = stringResource(R.string.max_files_search_root, root),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    label = { Text(text = stringResource(R.string.max_files_search_query)) },
                    singleLine = true,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                    LimitField(
                        label = stringResource(R.string.max_files_search_depth),
                        value = limits.maxDepth.toString(),
                        enabled = !running,
                        onValue = { raw ->
                            raw.toIntOrNull()?.let { onLimits(limits.copy(maxDepth = it)) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    LimitField(
                        label = stringResource(R.string.max_files_search_results),
                        value = limits.maxResults.toString(),
                        enabled = !running,
                        onValue = { raw ->
                            raw.toIntOrNull()?.let { onLimits(limits.copy(maxResults = it)) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    LimitField(
                        label = stringResource(R.string.max_files_search_timeout),
                        value = (limits.timeoutMs / 1000L).toString(),
                        enabled = !running,
                        onValue = { raw ->
                            raw.toLongOrNull()?.let { onLimits(limits.copy(timeoutMs = it * 1000L)) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                outcome?.let { result ->
                    Text(
                        text = stringResource(R.string.max_files_search_hits, result.hits.size, result.scannedFolders),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = outcomeLine(result),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (result.complete) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        },
        confirmButton = {
            if (running) {
                TextButton(onClick = onCancel) {
                    Text(text = stringResource(R.string.max_files_search_cancel))
                }
            } else {
                TextButton(onClick = onStart) {
                    Text(text = stringResource(R.string.max_files_search_start))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.max_files_cancel))
            }
        },
    )
}

@Composable
private fun LimitField(
    label: String,
    value: String,
    enabled: Boolean,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(text = label, maxLines = 1) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** سطر الحكم: كاملة، أو **بأعلامها** — ولا تُعرض نتيجة مقطوعة كأنها كاملة (ADR-07). */
@Composable
fun outcomeLine(result: DeepSearchOutcome): String = when {
    result.cancelled -> stringResource(R.string.max_files_search_cancelled)
    result.complete -> stringResource(R.string.max_files_search_complete)
    else -> buildList {
        if (result.hitLimitReached) add(stringResource(R.string.max_files_search_hit_limit))
        if (result.depthLimitReached) add(stringResource(R.string.max_files_search_depth_limit))
        if (result.timedOut) add(stringResource(R.string.max_files_search_timed_out))
        if (result.unreadableFolders > 0) {
            add(stringResource(R.string.max_files_search_unreadable, result.unreadableFolders))
        }
    }.joinToString(" · ")
}
