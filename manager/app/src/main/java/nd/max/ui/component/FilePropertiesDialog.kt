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
 * نافذة الخصائص (بتبويبات كما في MT) وحوار تعارض الأسماء.
 *
 * والتبويبات ليست ترتيبًا جماليًّا: كل تبويب يجيب سؤالًا مختلفًا عن **المدخل نفسه** —
 * ما هو · ما بداخله إن كان حزمة · من قد يقرؤه · من يملكه. وأربعة حوارات منفصلة كانت
 * تعني أربع فتحات بلا إمكان مقارنة، وجسدًا واحدًا طويلًا كان يضع الصلاحيات — وهي سبب
 * فتح النافذة في أغلب الأحيان — تحت الطيّة.
 *
 * وكل قيمة هنا **مقروءة** أو **لم تُقرأ**: لا صفر مُخترع، ولا تاريخ اليوم بدل تاريخ
 * مجهول، ولا سياق SELinux مُخترع على جهاز لا يعلنه.
 */
package nd.max.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxDialogTab
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTabbedDialog
import nd.max.ui.util.AccessBit
import nd.max.ui.util.AccessScope
import nd.max.ui.util.ApkFacts
import nd.max.ui.util.ConflictChoice
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileFormat
import nd.max.ui.util.FileKind
import nd.max.ui.util.PermissionSet

/** التبويبات الممكنة، مرتّبة كما تُعرض. والترتيب هنا لا في مقارنة نصوص. */
private enum class PropertiesTab { Info, Package, Permissions, Owner }

/** صفّ معلومة: اسم على اليسار وقيمة على اليمين — الشكل الذي يُقرأ منه عموديًّا. */
@Composable
private fun InfoRow(label: String, value: String, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.md),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/**
 * نافذة خصائص المدخل.
 *
 * @param tab التبويب المختار، والشاشة تحفظه فلا يعود إلى الأول عند كل تحديث قراءة.
 * @param apkFacts `null` يعني «لم تُقرأ بعد»، والتبويب يعرض طلب القراءة.
 */
@Composable
fun FilePropertiesDialog(
    visible: Boolean,
    entry: FileEntry,
    tab: Int,
    onTab: (Int) -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String,
    selinux: String?,
    onLoadSelinux: () -> Unit,
    apkFacts: ApkFacts?,
    onLoadApk: () -> Unit,
    permissions: PermissionSet?,
    octalDraft: String,
    onOctalDraft: (String) -> Unit,
    onToggleBit: (AccessScope, AccessBit) -> Unit,
    ownerDraft: String,
    groupDraft: String,
    onOwnerDraft: (String) -> Unit,
    onGroupDraft: (String) -> Unit,
    onApplyPermissions: () -> Unit,
    onApplyOwner: () -> Unit,
    onCopyPath: () -> Unit,
) {
    if (!visible) return

    val isPackage = entry.name.lowercase().endsWith(".apk")
    val kinds = buildList {
        add(PropertiesTab.Info)
        if (isPackage) add(PropertiesTab.Package)
        add(PropertiesTab.Permissions)
        add(PropertiesTab.Owner)
    }
    val tabs = kinds.map { kind ->
        MaxDialogTab(label = stringResource(tabLabel(kind)), icon = tabIcon(kind))
    }
    val safeIndex = tab.coerceIn(0, kinds.lastIndex)

    MaxTabbedDialog(
        visible = true,
        title = entry.name,
        tabs = tabs,
        selected = safeIndex,
        onSelect = onTab,
        onDismiss = onDismiss,
        dismissLabel = dismissLabel,
        body = { index ->
            when (kinds[index.coerceIn(0, kinds.lastIndex)]) {
                PropertiesTab.Info -> InfoTab(
                    entry = entry,
                    selinux = selinux,
                    onLoadSelinux = onLoadSelinux,
                    onCopyPath = onCopyPath,
                )
                PropertiesTab.Package -> PackageTab(apkFacts = apkFacts, onLoad = onLoadApk)
                PropertiesTab.Permissions -> PermissionsTab(
                    permissions = permissions,
                    octalDraft = octalDraft,
                    onOctalDraft = onOctalDraft,
                    onToggleBit = onToggleBit,
                    onApply = onApplyPermissions,
                )
                PropertiesTab.Owner -> OwnerTab(
                    ownerDraft = ownerDraft,
                    groupDraft = groupDraft,
                    onOwnerDraft = onOwnerDraft,
                    onGroupDraft = onGroupDraft,
                    onApply = onApplyOwner,
                )
            }
        },
    )
}

@Composable
private fun InfoTab(
    entry: FileEntry,
    selinux: String?,
    onLoadSelinux: () -> Unit,
    onCopyPath: () -> Unit,
) {
    InfoRow(
        label = stringResource(R.string.max_files_detail_path),
        value = entry.path,
        action = {
            IconButton(onClick = onCopyPath) {
                Text(
                    text = stringResource(R.string.max_files_detail_copy_path),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
    InfoRow(label = stringResource(R.string.max_files_detail_kind), value = fileKindLabel(entry))
    InfoRow(
        label = stringResource(R.string.max_files_detail_size),
        value = if (entry.isDirectory) {
            MAX_VALUE_UNAVAILABLE
        } else {
            FileFormat.size(entry.sizeBytes) ?: stringResource(R.string.max_files_unknown)
        },
    )
    InfoRow(
        label = stringResource(R.string.max_files_detail_modified),
        value = FileFormat.date(entry.modifiedEpochSec) ?: stringResource(R.string.max_files_unknown),
    )
    InfoRow(
        label = stringResource(R.string.max_files_detail_permissions),
        value = FileFormat.permissions(entry.permissions) ?: stringResource(R.string.max_files_unknown),
    )
    InfoRow(
        label = stringResource(R.string.max_files_detail_owner),
        value = entry.owner ?: stringResource(R.string.max_files_unknown),
    )
    InfoRow(
        label = stringResource(R.string.max_files_detail_group),
        value = entry.group ?: stringResource(R.string.max_files_unknown),
    )
    entry.symlinkTarget?.let { target ->
        InfoRow(label = stringResource(R.string.max_files_detail_symlink), value = target)
    }
    InfoRow(
        label = stringResource(R.string.max_files_detail_selinux),
        value = selinux ?: stringResource(R.string.max_files_detail_selinux_unknown),
    )
    if (selinux == null) {
        TextButton(onClick = onLoadSelinux) {
            Text(text = stringResource(R.string.max_files_detail_selinux_check))
        }
    }
}

@Composable
private fun PackageTab(apkFacts: ApkFacts?, onLoad: () -> Unit) {
    if (apkFacts == null) {
        Text(
            text = stringResource(R.string.max_files_apk_not_read),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onLoad) {
            Text(text = stringResource(R.string.max_files_apk_read))
        }
        return
    }
    InfoRow(
        label = stringResource(R.string.max_files_apk_package),
        value = apkFacts.packageName ?: stringResource(R.string.max_files_unknown),
    )
    InfoRow(
        label = stringResource(R.string.max_files_apk_version),
        value = listOfNotNull(apkFacts.versionName, apkFacts.versionCode?.toString())
            .joinToString(" · ")
            .ifBlank { stringResource(R.string.max_files_unknown) },
    )
    InfoRow(
        label = stringResource(R.string.max_files_apk_min_sdk),
        value = apkFacts.minSdk?.toString() ?: stringResource(R.string.max_files_unknown),
    )
    InfoRow(
        label = stringResource(R.string.max_files_apk_activities),
        value = apkFacts.activities?.toString() ?: stringResource(R.string.max_files_unknown),
    )
    InfoRow(
        label = stringResource(R.string.max_files_apk_permissions),
        value = apkFacts.permissions.size.toString(),
    )
    apkFacts.permissions.take(PERMISSION_LINES).forEach { permission ->
        Text(
            text = permission,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (apkFacts.permissions.size > PERMISSION_LINES) {
        Text(
            text = stringResource(
                R.string.max_files_apk_permissions_more,
                apkFacts.permissions.size - PERMISSION_LINES,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    InfoRow(
        label = stringResource(R.string.max_files_apk_signature),
        value = listOfNotNull(
            apkFacts.signatureMd5?.let { "MD5 $it" },
            apkFacts.signatureSha1?.let { "SHA-1 $it" },
            apkFacts.signatureSha256?.let { "SHA-256 $it" },
        ).joinToString("\n").ifBlank { stringResource(R.string.max_files_unknown) },
    )
}

@Composable
private fun PermissionsTab(
    permissions: PermissionSet?,
    octalDraft: String,
    onOctalDraft: (String) -> Unit,
    onToggleBit: (AccessScope, AccessBit) -> Unit,
    onApply: () -> Unit,
) {
    if (permissions == null) {
        Text(
            text = stringResource(R.string.max_files_detail_not_read),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    AccessScope.entries.forEach { scope ->
        Text(
            text = when (scope) {
                AccessScope.Owner -> stringResource(R.string.max_files_perm_owner)
                AccessScope.Group -> stringResource(R.string.max_files_perm_group)
                AccessScope.Other -> stringResource(R.string.max_files_perm_other)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
            AccessBit.entries.forEach { bit ->
                FilterChip(
                    selected = bit in permissions.bits(scope),
                    onClick = { onToggleBit(scope, bit) },
                    label = { Text(text = accessBitLabel(bit)) },
                )
            }
        }
    }
    OutlinedTextField(
        value = octalDraft,
        onValueChange = onOctalDraft,
        label = { Text(text = stringResource(R.string.max_files_perm_octal)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.max_files_perm_symbolic, permissions.symbolic),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (permissions.hasSpecial) {
        Text(
            text = stringResource(R.string.max_files_perm_special_set),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    TextButton(onClick = onApply) {
        Text(text = stringResource(R.string.max_files_perm_apply))
    }
}

@Composable
private fun OwnerTab(
    ownerDraft: String,
    groupDraft: String,
    onOwnerDraft: (String) -> Unit,
    onGroupDraft: (String) -> Unit,
    onApply: () -> Unit,
) {
    OutlinedTextField(
        value = ownerDraft,
        onValueChange = onOwnerDraft,
        label = { Text(text = stringResource(R.string.max_files_detail_owner)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = groupDraft,
        onValueChange = onGroupDraft,
        label = { Text(text = stringResource(R.string.max_files_detail_group)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = onApply) {
        Text(text = stringResource(R.string.max_files_owner_apply))
    }
}

/** حوار تعارض الأسماء: استبدال · تخطّي · إبقاء الاثنين · إلغاء — ومعها «لكل». */
@Composable
fun FileConflictDialog(
    visible: Boolean,
    names: List<String>,
    onChoose: (ConflictChoice, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    var forAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.max_files_conflict_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                Text(
                    text = stringResource(R.string.max_files_conflict_body, names.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                names.take(CONFLICT_LINES).forEach { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (names.size > CONFLICT_LINES) {
                    Text(
                        text = stringResource(R.string.max_files_conflict_more, names.size - CONFLICT_LINES),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = forAll, onCheckedChange = { forAll = it })
                    Text(
                        text = stringResource(R.string.max_files_conflict_for_all),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoose(ConflictChoice.Overwrite, forAll) }) {
                Text(text = stringResource(R.string.max_files_conflict_overwrite))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onChoose(ConflictChoice.Rename, forAll) }) {
                    Text(text = stringResource(R.string.max_files_conflict_rename))
                }
                TextButton(onClick = { onChoose(ConflictChoice.Skip, forAll) }) {
                    Text(text = stringResource(R.string.max_files_conflict_skip))
                }
                TextButton(onClick = { onChoose(ConflictChoice.Cancel, false) }) {
                    Text(text = stringResource(R.string.max_files_cancel))
                }
            }
        },
    )
}

/** اسم النوع كما يُقال في الواجهة (نفس مفاتيح شاشة الملفات). */
@Composable
fun fileKindLabel(entry: FileEntry): String = when (entry.kind) {
    FileKind.Directory -> stringResource(R.string.max_files_kind_directory)
    FileKind.RegularFile -> stringResource(R.string.max_files_kind_file)
    FileKind.Symlink -> stringResource(R.string.max_files_kind_symlink)
    FileKind.BlockDevice -> stringResource(R.string.max_files_kind_block)
    FileKind.CharDevice -> stringResource(R.string.max_files_kind_char)
    FileKind.Fifo -> stringResource(R.string.max_files_kind_fifo)
    FileKind.Socket -> stringResource(R.string.max_files_kind_socket)
    else -> stringResource(R.string.max_files_kind_unknown)
}

@Composable
private fun accessBitLabel(bit: AccessBit): String = when (bit) {
    AccessBit.Read -> stringResource(R.string.max_files_perm_read)
    AccessBit.Write -> stringResource(R.string.max_files_perm_write)
    AccessBit.Execute -> stringResource(R.string.max_files_perm_execute)
}

private fun tabLabel(tab: PropertiesTab): Int = when (tab) {
    PropertiesTab.Info -> R.string.max_files_tab_info
    PropertiesTab.Package -> R.string.max_files_tab_package
    PropertiesTab.Permissions -> R.string.max_files_tab_permissions
    PropertiesTab.Owner -> R.string.max_files_tab_owner
}

private fun tabIcon(tab: PropertiesTab): ImageVector = when (tab) {
    PropertiesTab.Info -> Icons.Rounded.Info
    PropertiesTab.Package -> Icons.Rounded.Android
    PropertiesTab.Permissions -> Icons.Rounded.Lock
    PropertiesTab.Owner -> Icons.Rounded.Person
}

private const val PERMISSION_LINES = 12
private const val CONFLICT_LINES = 6
