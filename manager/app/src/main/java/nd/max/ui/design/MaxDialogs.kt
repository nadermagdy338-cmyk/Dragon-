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
package nd.max.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import nd.max.ui.theme.MonoValueStyleSmall

/**
 * The single confirmation surface of the MaxManager Design Language.
 *
 * Why this exists as a primitive instead of a local AlertDialog per screen:
 *
 *  - Every destructive performance action in this app (recreating the swap
 *    device, recompiling every package, resetting kernel nodes) asks the same
 *    question shape: what will happen, what does it cost, can it be undone.
 *    When each screen writes its own dialog, that shape drifts and some screens
 *    end up confirming nothing at all.
 *  - Confirmations here must be able to show machine truth (a kernel path, a
 *    command, a package count) without turning the message into jargon. The
 *    optional [technicalDetail] slot is rendered in mono under the message, the
 *    same contract as MaxCondition, so power users get the fact and everyone
 *    else can ignore one quiet line.
 *  - The confirm label is required on purpose. "OK" tells the user nothing; the
 *    button must name the action it performs ("Compact now", "Compile all").
 *
 * [destructive] only changes the confirm label colour; it never changes the
 * layout, so the meaning does not depend on colour alone: the label text itself
 * always states the consequence.
 */
@Composable
fun MaxConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    technicalDetail: String? = null,
    dismissLabel: String = stringResource(android.R.string.cancel),
    icon: ImageVector? = null,
    destructive: Boolean = false
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(MaxRadius.sheet),
        icon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (technicalDetail != null) {
                    Text(
                        text = technicalDetail,
                        style = MonoValueStyleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = MaxAlpha.supportingText
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm()
                }
            ) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissLabel)
            }
        }
    )
}

/**
 * The single single-line input surface of the MaxManager Design Language.
 *
 * Added for the file manager, which needs to name a file, a folder or a
 * destination path, and had no shared primitive to do it with. Written here
 * rather than locally for the same reason as [MaxConfirmDialog]: a screen-local
 * dialog grows its own shape, and the sheet radius every other dialog in this
 * app carries would have been the first thing lost.
 *
 * The confirm button is disabled while [confirmEnabled] is false, so an empty
 * name can never reach the shell as an operation with no argument.
 */
@Composable
fun MaxInputDialog(
    visible: Boolean,
    title: String,
    fieldLabel: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    confirmEnabled: Boolean = value.isNotBlank(),
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(MaxRadius.sheet),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(text = fieldLabel) },
                    placeholder = placeholder?.let { { Text(text = it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                )
                supportingText?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                enabled = confirmEnabled,
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}
