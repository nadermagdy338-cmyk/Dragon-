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
 * `GAP-14` — شاشة عقد الطرف الثالث.
 *
 * وهي **العقد معروضًا**، لا صفحة تسويق: القالب الذي يُعرض هنا هو نفسه الذي يقرؤه
 * المحلّل (مُولَّد من [PluginContract] فلا يتقادم)، والمفاتيح والأنواع والقدرات تُقرأ
 * من العقد لا من قائمة مكتوبة بجانبه. وكل رفض يُعرض **بسببه واسم حقله**.
 *
 * وقراءة القرص تقع في [PluginDirectory]، والحكم في [PluginContract] — هذه الشاشة ترسم
 * فقط، فلا تتّخذ قرارًا واحدًا في مسألة أمنية.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.ui.design.MaxCardShell
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxGroupDivider
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTone
import nd.max.ui.design.content
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.util.PLUGIN_API_LEVEL
import nd.max.ui.util.PluginContract
import nd.max.ui.util.PluginDirectory
import nd.max.ui.util.PluginEvaluation
import nd.max.ui.util.PluginRejection
import nd.max.ui.util.PluginScan

@Composable
fun PluginsScreen(navController: NavController) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var scan by remember { mutableStateOf<PluginScan?>(null) }
    var loading by remember { mutableStateOf(true) }

    val reload: () -> Unit = {
        loading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { PluginDirectory.load() }
            scan = result
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    val current = scan
    val condition = when {
        loading -> MaxCondition(
            kind = MaxConditionKind.Loading,
            title = stringResource(R.string.max_plugins_loading_title),
            detail = stringResource(R.string.max_plugins_loading_detail),
        )
        current != null && !current.directoryPresent -> MaxCondition(
            kind = MaxConditionKind.Empty,
            title = stringResource(R.string.max_plugins_cond_missing_title),
            detail = stringResource(R.string.max_plugins_cond_missing_detail, PluginContract.DIRECTORY),
            technicalDetail = PluginContract.DIRECTORY,
            primaryActionLabel = stringResource(R.string.max_files_cond_recheck),
            onPrimaryAction = reload,
        )
        current != null && !current.directoryReadable -> MaxCondition(
            kind = MaxConditionKind.PermissionRequired,
            title = stringResource(R.string.max_plugins_cond_unreadable_title),
            detail = stringResource(R.string.max_plugins_cond_unreadable_detail, PluginContract.DIRECTORY),
            technicalDetail = PluginContract.DIRECTORY,
            primaryActionLabel = stringResource(R.string.max_files_cond_recheck),
            onPrimaryAction = reload,
        )
        else -> null
    }

    MaxListScreen(
        title = stringResource(R.string.max_plugins_title),
        subtitle = stringResource(R.string.max_plugins_subtitle),
        onBack = { navController.popBackStack() },
        accentIcon = MaxDestination.Plugins.icon,
        accent = MaxTone.Accent.content(),
        condition = condition,
        snackbarHostState = snackbarHostState,
        actions = {
            MaxHelpAction(
                title = stringResource(R.string.max_plugins_help_title),
                body = stringResource(R.string.max_plugins_help_body),
            )
            IconButton(onClick = reload) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.max_plugins_refresh_cd),
                )
            }
        },
    ) {
        item(key = "plugin_contract") { ContractSection() }

        item(key = "plugin_template") {
            val copiedLabel = stringResource(R.string.max_plugins_template_copied)
            val template = remember { PluginContract.manifestTemplate() }
            TemplateSection(onCopy = {
                clipboard.setText(AnnotatedString(template))
                scope.launch { snackbarHostState.showSnackbar(copiedLabel) }
            })
        }

        if (current != null) {
            item(key = "plugin_installed") { InstalledSection(current) }
            if (current.state.rejected.isNotEmpty()) {
                item(key = "plugin_rejected_title") {
                    MaxSection(title = stringResource(R.string.max_plugins_rejected_section)) {
                        RejectedGroup(current.state.rejected)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContractSection() {
    MaxSection(
        title = stringResource(R.string.max_plugins_section_contract),
        description = stringResource(R.string.max_plugins_contract_rules),
    ) {
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_plugins_contract_api),
                subtitle = stringResource(R.string.max_plugins_contract_api, PLUGIN_API_LEVEL),
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_plugins_contract_dir),
                subtitle = PluginContract.DIRECTORY,
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_plugins_contract_keys),
                subtitle = PluginContract.readableKeys().joinToString(", "),
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_plugins_contract_kinds),
                subtitle = PluginContract.kinds().joinToString(", ") { it.token },
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_plugins_contract_capabilities),
                subtitle = PluginContract.capabilities().joinToString(", ") { it.token },
            )
        }
    }
}

@Composable
private fun TemplateSection(onCopy: () -> Unit) {
    MaxSection(
        title = stringResource(R.string.max_plugins_template),
        description = stringResource(R.string.max_plugins_template_note),
    ) {
        // **ترحيل إلى القشرة:** كانت بلا حدّ، فيُمرّر `Color.Transparent` صراحةً — فلا يظهر
        // حدّ جديد من ترحيل. والحشو هنا **متباين** ومكتوب على النصّ نفسه، فيبقى مكانه.
        MaxCardShell(
            modifier = Modifier.fillMaxWidth(),
            borderColor = Color.Transparent,
            contentPadding = 0.dp,
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = PluginContract.manifestTemplate(),
                modifier = Modifier.padding(horizontal = MaxSpace.md, vertical = MaxSpace.md),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_plugins_copy_template),
                onClick = onCopy,
            )
        }
    }
}

@Composable
private fun InstalledSection(scan: PluginScan) {
    val accepted = scan.state.accepted
    MaxSection(title = stringResource(R.string.max_plugins_section_installed)) {
        MaxGroup {
            MaxRow(
                title = stringResource(R.string.max_plugins_accepted_count, accepted.size),
            )
            MaxGroupDivider()
            MaxRow(
                title = stringResource(R.string.max_plugins_rejected_count, scan.state.rejected.size),
            )
            if (scan.unreadableManifests.isNotEmpty()) {
                MaxGroupDivider()
                MaxRow(
                    title = stringResource(
                        R.string.max_plugins_unreadable_count,
                        scan.unreadableManifests.size,
                    ),
                    subtitle = scan.unreadableManifests.joinToString(", "),
                    iconTone = MaxTone.Caution,
                )
            }
            if (accepted.isEmpty()) {
                MaxGroupDivider()
                MaxRow(
                    title = stringResource(R.string.max_plugins_cond_empty_title),
                    subtitle = stringResource(R.string.max_plugins_cond_empty_detail),
                )
            }
        }
        accepted.forEach { manifest ->
            MaxGroup {
                MaxRow(
                    title = manifest.name,
                    subtitle = "${manifest.id} · ${manifest.version} · ${manifest.kind.token}",
                    iconTone = MaxTone.Accent,
                )
                MaxGroupDivider()
                MaxRow(
                    title = stringResource(R.string.max_plugins_contract_capabilities),
                    subtitle = manifest.capabilities.joinToString(", ") { it.token },
                )
                MaxGroupDivider()
                keyValueRow(
                    label = stringResource(R.string.max_plugins_detail_label),
                    value = manifest.description ?: manifest.id,
                )
                MaxGroupDivider()
                MaxRow(
                    title = stringResource(R.string.max_plugins_capability_source),
                    subtitle = stringResource(R.string.max_plugins_contract_api, manifest.apiLevel),
                    iconTone = MaxTone.Caution,
                )
            }
        }
    }
}

@Composable
private fun RejectedGroup(rejected: List<PluginEvaluation>) {
    MaxGroup {
        rejected.forEachIndexed { index, evaluation ->
            if (index > 0) MaxGroupDivider()
            MaxRow(
                title = evaluation.id,
                subtitle = reasonText(evaluation.rejection) +
                    (evaluation.detail?.let { " — $it" } ?: ""),
                iconTone = MaxTone.Critical,
            )
        }
    }
}

@Composable
private fun keyValueRow(label: String, value: String) {
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

/** سبب الرفض بنصّه الصريح. `null` تعني «لا رفض» ولا تُعرض أصلًا. */
@Composable
private fun reasonText(reason: PluginRejection?): String = when (reason) {
    null -> ""
    PluginRejection.BadId -> stringResource(R.string.max_plugins_reason_BadId)
    PluginRejection.MissingName -> stringResource(R.string.max_plugins_reason_MissingName)
    PluginRejection.BadVersion -> stringResource(R.string.max_plugins_reason_BadVersion)
    PluginRejection.ApiTooOld -> stringResource(R.string.max_plugins_reason_ApiTooOld)
    PluginRejection.ApiTooNew -> stringResource(R.string.max_plugins_reason_ApiTooNew)
    PluginRejection.MissingKind -> stringResource(R.string.max_plugins_reason_MissingKind)
    PluginRejection.UnknownKind -> stringResource(R.string.max_plugins_reason_UnknownKind)
    PluginRejection.UnknownCapability -> stringResource(R.string.max_plugins_reason_UnknownCapability)
    PluginRejection.NoCapabilities -> stringResource(R.string.max_plugins_reason_NoCapabilities)
    PluginRejection.DuplicateId -> stringResource(R.string.max_plugins_reason_DuplicateId)
}
