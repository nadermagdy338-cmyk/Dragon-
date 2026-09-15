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

@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.mainscreens
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import nd.max.ui.component.maxAdaptiveContentWidth
import nd.max.R
import nd.max.ui.component.ExpressiveList
import nd.max.ui.component.ExpressiveListItem
import nd.max.ui.component.MaxManagerSubScreenTopBar
import nd.max.ui.component.MaxScreenHelpDialog
import nd.max.ui.component.MaxStatusPill
import nd.max.ui.theme.MaxTextRole
import nd.max.ui.component.MaxSurface
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.height
import nd.max.ui.component.StudioSectionHeader
import nd.max.core.hardware.AccessLevel
import nd.max.core.hardware.HardwareCapabilityResolver
import nd.max.core.hardware.HardwareCapabilitySnapshot
import nd.max.core.hardware.HardwareRuntime
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast

// ────────────────────────────────────────────────────────────────────────────
// Diagnostics hub
//
// Everything here is a raw inspection/debugging tool, not a device tweak.
// It intentionally sits outside Home / Tweaks / Apps / Settings' main path —
// reached only via Settings → Tools & Diagnostics — so the primary flows stay
// focused on tuning the device rather than debugging it.
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun DiagnosticsScreen(navController: NavHostController) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var capabilities by remember { mutableStateOf<HardwareCapabilitySnapshot?>(null) }
    var runtime by remember { mutableStateOf<HardwareRuntime.Snapshot?>(null) }
    var showScreenHelp by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        capabilities = HardwareCapabilityResolver.resolve(context)
        runtime = runCatching { HardwareRuntime.snapshot(context) }.getOrNull()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MaxManagerSubScreenTopBar(
                scrollBehavior = scrollBehavior,
                title = stringResource(R.string.section_diagnostics),
                onBack = { navController.popBackStack() },
                accentIcon = Icons.Outlined.Memory,
                accent = MaterialTheme.colorScheme.primary,
                actions = {
                    androidx.compose.material3.IconButton(onClick = { showScreenHelp = true }) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Rounded.HelpOutline,
                            contentDescription = stringResource(R.string.cd_screen_help)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.maxAdaptiveContentWidth(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = 20.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        ) {
            item {
                StudioSectionHeader(
                    title = stringResource(R.string.section_diagnostics),
                    subtitle = stringResource(R.string.diagnostics_intro),
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }

            item {
                ExpressiveList(
                    content = listOf(
                        {
                            ExpressiveListItem(
                                leadingContent = { IconBadge(Icons.Outlined.Analytics, MaterialTheme.colorScheme.primary, 36) },
                                headlineContent = { Text(stringResource(R.string.diagnostics_workflow_title)) },
                                supportingContent = { Text(stringResource(R.string.diagnostics_workflow_desc)) },
                                trailingContent = {
                                    MaxStatusPill(
                                        text = stringResource(R.string.diagnostics_inspection_only),
                                        active = true,
                                        accent = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                        },
                        {
                            ExpressiveListItem(
                                headlineContent = { Text(stringResource(R.string.diagnostics_step_processes)) },
                                supportingContent = { Text(stringResource(R.string.diagnostics_step_logs) + " · " + stringResource(R.string.diagnostics_step_shell)) },
                                leadingContent = { IconBadge(Icons.Filled.Dns, MaterialTheme.colorScheme.primary, 36) }
                            )
                        }
                    )
                )
            }
            item {
                CapabilityMatrixCard(capabilities) {
                    capabilities = HardwareCapabilityResolver.resolve(context)
                    runtime = runCatching { HardwareRuntime.snapshot(context) }.getOrNull()
                }
            }
            item {
                RuntimeHealthCard(runtime)
            }
            item {
                OwnershipDiagnosticsCard()
            }
            item {
                HardwareReportCard(context)
            }
            item {
                StudioSectionHeader(
                    title = stringResource(R.string.diagnostics_tools_title),
                    subtitle = stringResource(R.string.diagnostics_tools_desc),
                    modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
                )
            }
            item {
                ExpressiveList(
                    content = listOf(
                        {
                            ExpressiveListItem(
                                leadingContent = { IconBadge(Icons.Outlined.Memory, MaterialTheme.colorScheme.primary, 36) },
                                onClick = { MaxNavActions(navController).navigateTo(MaxDestination.ProcessManager) },
                                headlineContent = { Text(stringResource(R.string.processmgr_title)) },
                                supportingContent = { Text(stringResource(R.string.processmgr_menu_desc)) }
                            )
                        },
                        {
                            ExpressiveListItem(
                                leadingContent = { IconBadge(Icons.Filled.Terminal, MaterialTheme.colorScheme.primary, 36) },
                                onClick = { MaxNavActions(navController).navigateTo(MaxDestination.Logs) },
                                headlineContent = { Text(stringResource(R.string.logsviewer_title)) },
                                supportingContent = { Text(stringResource(R.string.logsviewer_menu_desc)) }
                            )
                        },
                        {
                            ExpressiveListItem(
                                leadingContent = { IconBadge(Icons.Filled.Terminal, MaterialTheme.colorScheme.primary, 36) },
                                onClick = { MaxNavActions(navController).navigateTo(MaxDestination.Terminal) },
                                headlineContent = { Text(stringResource(R.string.terminal_shell)) },
                                supportingContent = { Text(stringResource(R.string.terminal_shell_desc)) }
                            )
                        }
                    )
                )
            }
        }
    }

    MaxScreenHelpDialog(
        visible = showScreenHelp,
        title = stringResource(R.string.diagnostics_help_title),
        description = stringResource(R.string.diagnostics_workspace_guidance),
        onDismiss = { showScreenHelp = false }
    )

}



@Composable
private fun CapabilityMatrixCard(snapshot: HardwareCapabilitySnapshot?, onRefresh: () -> Unit) {
    MaxSurface(modifier = Modifier.padding(top = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(Icons.Outlined.Memory, MaterialTheme.colorScheme.primary, 36)
            Column(Modifier.weight(1f)) {
                Text("Hardware capability matrix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    snapshot?.let { "${it.vendor} • ${it.platform}" } ?: "Detecting hardware interfaces…",
                    style = MaxTextRole.description, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            nd.max.ui.component.StudioTextButton(onClick = onRefresh) { Text("Refresh") }
        }
        Spacer(Modifier.height(10.dp))
        snapshot?.features?.values?.forEach { capability ->
            val label = capability.feature.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            val status = when (capability.access) {
                AccessLevel.READ_WRITE -> "Supported • read/write"
                AccessLevel.READ_ONLY -> "Detected • read only"
                AccessLevel.NONE -> "Not detected on this device"
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("${capability.backend} • $status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (capability.evidence.isNotEmpty()) {
                        Text(
                            capability.evidence.take(3).joinToString("  ·  "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                        )
                    }
                }
            }
        }
        Text(
            "Unavailable controls stay visible so users can report their device and expand support.",
            style = MaxTextRole.description, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
        )
    }
}


@Composable
private fun RuntimeHealthCard(snapshot: HardwareRuntime.Snapshot?) {
    MaxSurface(modifier = Modifier.padding(top = 14.dp)) {
        Text("Live hardware health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            snapshot?.let { "${it.cpuPolicies.size} CPU policies • ${it.gpuDevices.size} GPU devices • ZRAM ${if (it.zram.exists) "detected" else "not detected"}" }
                ?: "Runtime inspection unavailable",
            style = MaxTextRole.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        snapshot?.health?.forEach { health ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(health.name.uppercase(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                Text(health.health.name, style = MaterialTheme.typography.labelMedium)
            }
            Text(health.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        snapshot?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "${it.supportedCount} detected • ${it.writableCount} writable • ${it.unsupportedCount} not detected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun OwnershipDiagnosticsCard() {
    val leases = nd.max.core.hardware.ControlOwnership.snapshot()
    MaxSurface(modifier = Modifier.padding(top = 14.dp)) {
        Text("Active control ownership", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (leases.isEmpty()) "No policy currently owns a hardware control."
            else "${leases.size} active control lease${if (leases.size == 1) "" else "s"}. Higher-priority owners can block lower-priority policies.",
            style = MaxTextRole.description, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        leases.forEach { lease ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lease.key, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("${lease.owner} • ${lease.ownerToken}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(lease.desired, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun HardwareReportCard(context: android.content.Context) {
    MaxSurface(modifier = Modifier.padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Hardware report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Generate a compact report for support and compatibility reports.", style = MaxTextRole.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            nd.max.ui.component.StudioTextButton(onClick = {
                val report = HardwareRuntime.compactReport(context)
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("MaxManager Hardware Report", report))
                Toast.makeText(context, "Hardware report copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
        }
    }
}
