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
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
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
import nd.max.ui.component.CapabilityMatrixCard
import nd.max.ui.component.MaxDeviceInfoShortcut
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.component.maxAdaptiveContentWidth
import nd.max.R
import nd.max.ui.component.ExpressiveList
import nd.max.ui.component.ExpressiveListItem
import nd.max.ui.component.MaxManagerSubScreenTopBar
import nd.max.ui.component.MaxScreenHelpDialog
import nd.max.ui.component.MaxStatusPill
import nd.max.ui.component.RouteVerdictLabel
import nd.max.ui.theme.MaxTextRole
import nd.max.ui.component.MaxSurface
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.height
import nd.max.ui.component.StudioSectionHeader
import nd.max.core.hardware.AccessLevel
import nd.max.core.diagnostics.HardwareRouteHealth
import nd.max.core.hardware.HardwareCapabilityResolver
import nd.max.core.hardware.HardwareCapabilitySnapshot
import nd.max.core.hardware.HardwareRuntime
import nd.max.ui.util.BatteryHealth
import nd.max.ui.util.BatteryHealthSource
import nd.max.ui.util.BatteryHealthUtil
import nd.max.ui.util.BootHistory
import nd.max.ui.util.BootHistoryUtil
import nd.max.ui.util.ChargeLedger
import nd.max.ui.util.ChargeVerdict
import nd.max.ui.util.CrashLogSummary
import nd.max.ui.util.CrashLogUtil
import nd.max.ui.component.SensorInventoryCard
import nd.max.ui.util.MemoryLedger
import nd.max.ui.util.ZramPlatformState
import nd.max.ui.util.ZramPlatformUtil
import nd.max.ui.util.StorageHealthUtil
import nd.max.ui.util.StorageMediaHealth
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
    // Route verdicts are computed from the same snapshot the matrix renders, so the matrix can say
    // *why* a control has no verified activation route instead of only that it exists. Read-only: the
    // verdict is built from read seams and never from a write path.
    var routes by remember { mutableStateOf<List<HardwareRouteHealth.Verdict>>(emptyList()) }
    var runtime by remember { mutableStateOf<HardwareRuntime.Snapshot?>(null) }
    var showScreenHelp by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // **وعلى خيط IO لا على خيط التركيب (عطب سرعة مُبلَّغ عنه):** `LaunchedEffect` يُنفَّذ على
        // موزّع التركيب — أي **الرئيسي** — وهذه الثلاثة **حاجبة** (استقصاء واجهات العتاد ومساراتها
        // ثم لقطة وقت التشغيل)، فكانت تحجب أوّل إطار عند فتح الشاشة ولا يُرسم شيء قبلها.
        // والتحديث من خيط خلفيّ إلى حالة اللقطة آمن (‏`mutableStateOf` تُكتب من أي خيط).
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            capabilities = HardwareCapabilityResolver.resolve(context).also { snapshot ->
                routes = runCatching { HardwareRouteHealth.verdicts(snapshot) }.getOrDefault(emptyList())
            }
            runtime = runCatching { HardwareRuntime.snapshot(context) }.getOrNull()
        }
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
                            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
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
                // وزرّ قسم **المستشعرات** في «معلومات الجهاز» على جانب عنوان هذه البطاقة — وهي
                // البطاقة الأولى ذات العنوان في شاشة التشخيص (`SensorInventoryCard` بطاقة
                // المستشعرات فيها)، وهو نصّ طلب المالك: «في شاشة التشخيص زرّ يدخلك على قسم
                // المستشعرات»، ولهذا صار الاختيار للمستشعرات لا للنظام (انظر
                // `deviceInfoShortcutSection`).
                CapabilityMatrixCard(
                    snapshot = capabilities,
                    routes = routes,
                    onRefresh = {
                        capabilities = HardwareCapabilityResolver.resolve(context).also { snapshot ->
                            routes = runCatching { HardwareRouteHealth.verdicts(snapshot) }.getOrDefault(emptyList())
                        }
                        runtime = runCatching { HardwareRuntime.snapshot(context) }.getOrNull()
                    },
                    trailing = {
                        // وبطاقة مصفوفة القدرات تحشو نفسها (`MaxSurface` ← `cardPadding`)،
                        // فحاشية الباب صفر فلا يُحتسب البُعد مرّتين.
                        MaxDeviceInfoShortcut(
                            navController = navController,
                            from = MaxDestination.Diagnostics,
                            inset = 0.dp,
                        )
                    },
                )
            }
            item {
                RuntimeHealthCard(runtime)
            }
            item {
                BootHistoryCard()
            }
            item {
                BatteryHealthCard()
            }
            item {
                ChargeLedgerCard()
            }
            item {
                MemoryLedgerCard()
            }
            item {
                SensorInventoryCard()
            }
            item {
                StorageHealthCard()
            }
            item {
                CrashLogCard()
            }
            item {
                ZramPlatformCard()
            }
            item {
                OwnershipDiagnosticsCard()
            }
            item {
                HardwareReportCard(context)
            }
            //
            // Max Atlas (`P7`). Its own item, outside every module-loaded gate, because a device fact
            // must not depend on the native module being installed: the reading path is app-private and
            // rootless. The section reads the repository's state and performs no reading of its own.
            item {
                AtlasDiagnosticsSection()
            }
            //
            // ولا تُكرَّر هنا روابط مراقب المهام ووحدة السجل وطرفية الأوامر: مكانها
            // `Control → Tools` وحده. وكان في هذه الشاشة صفّان لثلاث أدوات، فصار للشيء الواحد
            // مدخلان يفترقان في الوصف — وهو ما تمنعه قائمة الوجهات المفردة (ADR-02).
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
private fun RuntimeHealthCard(snapshot: HardwareRuntime.Snapshot?) {
    MaxSurface(modifier = Modifier.padding(top = 14.dp)) {
        Text(stringResource(R.string.diagnostics_live_hw_health), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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


/**
 * `AR-02` — «لماذا أقلع الجهاز؟» — قراءة فقط، بلا كتابة وبلا امتياز.
 *
 * تُجيب السؤال الذي يطرحه المستخدم بعد كل فلاش: هل أقلع نظيفًا؟ وإن انهار، هل هناك أثر؟
 * وثلاث حالات إجبارية: `Live` (مقروء) · `Unsupported` (الروم لا يُعلنه) ·
 * «لا نستطيع الجزم» عند تعذّر قراءة `pstore` (ADR-07).
 */
@Composable
private fun BootHistoryCard() {
    var history by remember { mutableStateOf<BootHistory?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        history = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            BootHistoryUtil.read()
        }
    }

    MaxSurface(modifier = Modifier.padding(top = 22.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconBadge(Icons.Outlined.Memory, MaterialTheme.colorScheme.primary, 36)
            Text(
                text = stringResource(R.string.max_boot_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.max_boot_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        val snapshot = history
        if (snapshot == null) {
            Text(
                text = stringResource(R.string.max_boot_reading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (snapshot.isSupported) {
                BootRow(
                    label = stringResource(R.string.max_boot_loader),
                    value = snapshot.loaderReason ?: stringResource(R.string.status_unknown),
                    ok = true
                )
                BootRow(
                    label = stringResource(R.string.max_boot_system),
                    value = snapshot.systemReason ?: stringResource(R.string.status_unknown),
                    ok = true
                )
            } else {
                BootRow(
                    label = stringResource(R.string.max_boot_loader),
                    value = stringResource(R.string.max_boot_unsupported),
                    ok = false
                )
            }

            if (snapshot.isThermalShutdown) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.max_boot_thermal_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(8.dp))

            when {
                !snapshot.pstoreReadable -> BootRow(
                    label = stringResource(R.string.max_boot_pstore),
                    value = stringResource(R.string.max_boot_unreadable),
                    ok = false
                )
                snapshot.hasCrashArtifact -> BootRow(
                    label = stringResource(R.string.max_boot_pstore),
                    value = stringResource(
                        R.string.max_boot_crash_found,
                        snapshot.pstoreEntries.size
                    ),
                    ok = false
                )
                else -> BootRow(
                    label = stringResource(R.string.max_boot_pstore),
                    value = stringResource(R.string.max_boot_no_crash),
                    ok = true
                )
            }
        }
    }
}

/** `AR-08` — صحة البطارية، قراءة فقط، بلا رقم مُخترَع. */
@Composable
private fun BatteryHealthCard() {
    var health by remember { mutableStateOf<BatteryHealth?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        health = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            BatteryHealthUtil.read()
        }
    }

    MaxSurface(modifier = Modifier.padding(top = 22.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconBadge(Icons.Outlined.Memory, MaterialTheme.colorScheme.primary, 36)
            Text(
                text = stringResource(R.string.max_battery_health_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.max_battery_health_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        val snapshot = health
        when {
            snapshot == null -> DetailRow(stringResource(R.string.max_boot_reading), null)
            snapshot.source == BatteryHealthSource.UNSUPPORTED ->
                DetailRow(stringResource(R.string.max_battery_unsupported), null)
            else -> {
                DetailRow(
                    stringResource(R.string.max_battery_design),
                    snapshot.designUah?.let { microAh(it) }
                )
                DetailRow(
                    stringResource(R.string.max_battery_full),
                    snapshot.currentFullUah?.let { microAh(it) }
                )
                DetailRow(
                    stringResource(R.string.max_battery_soh),
                    snapshot.stateOfHealthPercent?.let {
                        stringResource(R.string.max_percent_format, it)
                    }
                )
                DetailRow(
                    stringResource(R.string.max_battery_cycles),
                    snapshot.cycleCount?.toString()
                )
                if (snapshot.source == BatteryHealthSource.ESTIMATED) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.max_battery_estimated),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** `AR-11` — تآكل وسائط التخزين كما تُعلنه الوسيطة نفسها. */
@Composable
private fun StorageHealthCard() {
    var health by remember { mutableStateOf<StorageMediaHealth?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        health = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            StorageHealthUtil.read()
        }
    }

    MaxSurface(modifier = Modifier.padding(top = 22.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconBadge(Icons.Outlined.Memory, MaterialTheme.colorScheme.primary, 36)
            Text(
                text = stringResource(R.string.max_storage_health_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.max_storage_health_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        val snapshot = health
        when {
            snapshot == null -> DetailRow(stringResource(R.string.max_boot_reading), null)
            !snapshot.supported -> DetailRow(stringResource(R.string.max_storage_unsupported), null)
            else -> {
                DetailRow(stringResource(R.string.max_storage_device), snapshot.device)
                DetailRow(
                    stringResource(R.string.max_storage_wear),
                    snapshot.usedPercent?.let {
                        stringResource(R.string.max_percent_format, it)
                    }
                )
                DetailRow(
                    stringResource(R.string.max_storage_eol),
                    when (snapshot.preEol) {
                        1 -> stringResource(R.string.max_storage_eol_normal)
                        2 -> stringResource(R.string.max_storage_eol_warning)
                        3 -> stringResource(R.string.max_storage_eol_urgent)
                        else -> null
                    }
                )
            }
        }
    }
}

/** `AR-06` — ملخّص الانهيارات وANR من `/data/anr` و`/data/tombstones`. قراءة فقط. */
@Composable
private fun CrashLogCard() {
    var summary by remember { mutableStateOf<CrashLogSummary?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        summary = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            CrashLogUtil.read()
        }
    }

    MaxSurface(modifier = Modifier.padding(top = 22.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconBadge(Icons.Outlined.Memory, MaterialTheme.colorScheme.primary, 36)
            Text(
                text = stringResource(R.string.max_crash_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.max_crash_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        val snapshot = summary
        when {
            snapshot == null -> DetailRow(stringResource(R.string.max_boot_reading), null)
            !snapshot.readable -> DetailRow(stringResource(R.string.max_crash_unsupported), null)
            snapshot.total == 0 -> DetailRow(stringResource(R.string.max_crash_none), null)
            else -> {
                DetailRow(
                    stringResource(R.string.max_crash_anr),
                    snapshot.anrCount?.toString() ?: stringResource(R.string.status_unknown)
                )
                DetailRow(
                    stringResource(R.string.max_crash_tombstones),
                    snapshot.tombstoneCount?.toString() ?: stringResource(R.string.status_unknown)
                )
                DetailRow(
                    stringResource(R.string.max_crash_latest),
                    snapshot.latestAtMs?.let { formatTimestamp(it) }
                )
            }
        }
    }
}

/** `AR-10` — ZRAM كمسار معلَن: من يديره؟ بأي مرحلة؟ قراءة فقط. */
@Composable
private fun ZramPlatformCard() {
    var state by remember { mutableStateOf<ZramPlatformState?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        state = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ZramPlatformUtil.read()
        }
    }

    MaxSurface(modifier = Modifier.padding(top = 22.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconBadge(Icons.Outlined.Memory, MaterialTheme.colorScheme.primary, 36)
            Text(
                text = stringResource(R.string.max_zram_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.max_zram_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        val snapshot = state
        when {
            snapshot == null -> DetailRow(stringResource(R.string.max_boot_reading), null)
            !snapshot.supported -> DetailRow(stringResource(R.string.max_zram_unsupported), null)
            else -> {
                DetailRow(
                    stringResource(R.string.max_zram_managed),
                    triState(snapshot.platformManaged)
                )
                DetailRow(
                    stringResource(R.string.max_zram_algorithm),
                    snapshot.algorithm
                )
                DetailRow(stringResource(R.string.max_zram_size), snapshot.sizeRaw)
                DetailRow(
                    stringResource(R.string.max_zram_writeback),
                    triState(snapshot.writebackEnabled)
                )
                DetailRow(
                    stringResource(R.string.max_zram_recompress),
                    triState(snapshot.recompressSupported)
                )
                DetailRow(
                    stringResource(R.string.max_zram_idle),
                    triState(snapshot.idleTrackingPresent)
                )
                DetailRow(
                    stringResource(R.string.max_zram_disk),
                    snapshot.disksizeBytes?.let {
                        stringResource(R.string.max_mb_format, (it / (1024L * 1024L)).toString())
                    }
                )
            }
        }
    }
}

/** نعم / لا / غير معروف — بلا تقريب ولا تخمين (ADR-07). */
@Composable
private fun triState(value: Boolean?): String = when (value) {
    true -> stringResource(R.string.max_yes)
    false -> stringResource(R.string.max_no)
    null -> stringResource(R.string.status_unknown)
}

/** تنسيق زمن محلي، بلا نمط صلب في الواجهة. */
private fun formatTimestamp(ms: Long): String =
    java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM,
        java.text.DateFormat.SHORT
    ).format(java.util.Date(ms))

/** يحوّل µAh إلى نصّ mAh بوحدة معلنة في النصّ نفسه. */
@Composable
private fun microAh(value: Long): String =
    stringResource(R.string.max_mah_format, (value / 1000).toString())

/**
 * `AR-09` — سجل دورات الشحن: حكم طولي لا لحظي.
 *
 * يُسجّل لقطة **عند فتح هذه الشاشة فقط** (لا خدمة خلفية)، ويعرض الحكم بحالاته الصريحة —
 * ومنها «العدّاد غير مدعوم» و«لا حكم بعد». الأساس معلَن في البطاقة نفسها، لا مخفيًّا.
 */
@Composable
private fun ChargeLedgerCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var snapshot by remember { mutableStateOf<ChargeLedger.Snapshot?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        snapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ChargeLedger.observe(context)
        }
    }

    MaxSurface(modifier = Modifier.padding(top = 22.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconBadge(Icons.Outlined.BatteryChargingFull, MaterialTheme.colorScheme.primary, 36)
            Text(
                text = stringResource(R.string.max_charge_ledger_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.max_charge_ledger_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        val current = snapshot
        when {
            current == null -> DetailRow(stringResource(R.string.max_boot_reading), null)
            else -> {
                DetailRow(
                    stringResource(R.string.max_charge_sessions),
                    current.sessions.size.toString()
                )
                current.sessions.lastOrNull()?.let { last ->
                    DetailRow(
                        stringResource(R.string.max_charge_last_range),
                        stringResource(R.string.max_charge_range_format, last.levelFrom, last.levelTo)
                    )
                    DetailRow(
                        stringResource(R.string.max_charge_last_energy),
                        microAh(last.chargeCounterDeltaUah)
                    )
                    last.uahPerPoint?.let { perPoint ->
                        DetailRow(
                            stringResource(R.string.max_charge_last_per_point),
                            microAh(perPoint)
                        )
                    }
                }
                when (val verdict = current.verdict) {
                    ChargeVerdict.CounterUnavailable -> DetailRow(
                        stringResource(R.string.max_charge_verdict),
                        stringResource(R.string.max_charge_verdict_no_counter)
                    )
                    is ChargeVerdict.InsufficientData -> DetailRow(
                        stringResource(R.string.max_charge_verdict),
                        stringResource(
                            R.string.max_charge_verdict_insufficient,
                            verdict.have.toString(),
                            verdict.need.toString()
                        )
                    )
                    is ChargeVerdict.Stable -> DetailRow(
                        stringResource(R.string.max_charge_verdict),
                        stringResource(
                            R.string.max_charge_verdict_stable,
                            microAh(verdict.medianUahPerPoint)
                        )
                    )
                    is ChargeVerdict.Drifting -> DetailRow(
                        stringResource(R.string.max_charge_verdict),
                        stringResource(
                            R.string.max_charge_verdict_drifting,
                            microAh(verdict.medianUahPerPoint),
                            microAh(verdict.recentUahPerPoint)
                        )
                    )
                }
            }
        }
    }
}

/**
 * `AR-24` — دفتر ذاكرة التطبيق: PSS + مقارنة لقطات.
 *
 * يُقاس **تطبيقنا نحن بلا امتياز** (المسار الموثوق)، وتُعلَن الطريقة. ولا يُقارَن رقمان بطريقتين
 * مختلفتين — تظهر «لا مقارنة» بصراحة.
 */
@Composable
private fun MemoryLedgerCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var report by remember { mutableStateOf<MemoryLedger.Report?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        report = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            MemoryLedger.observeOwn(context)
        }
    }

    MaxSurface(modifier = Modifier.padding(top = 22.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconBadge(Icons.Outlined.Memory, MaterialTheme.colorScheme.primary, 36)
            Text(
                text = stringResource(R.string.max_memory_ledger_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.max_memory_ledger_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        val current = report
        when {
            current == null || current.current == null ->
                DetailRow(stringResource(R.string.max_memory_reading), null)
            else -> {
                DetailRow(
                    stringResource(R.string.max_memory_current),
                    stringResource(R.string.max_memory_kb_format, current.current.totalPssKb.toString())
                )
                DetailRow(
                    stringResource(R.string.max_memory_method),
                    when (current.current.method) {
                        MemoryLedger.Method.OWN_PROCESS -> stringResource(R.string.max_memory_method_own)
                        MemoryLedger.Method.DUMPSYS -> stringResource(R.string.max_memory_method_dumpsys)
                    }
                )
                DetailRow(
                    stringResource(R.string.max_memory_snapshots),
                    current.snapshots.count { it.key == current.current.key }.toString()
                )
                when (val delta = current.delta) {
                    MemoryLedger.MemoryDelta.Insufficient -> DetailRow(
                        stringResource(R.string.max_memory_trend),
                        stringResource(R.string.max_memory_trend_insufficient)
                    )
                    is MemoryLedger.MemoryDelta.Stable -> DetailRow(
                        stringResource(R.string.max_memory_trend),
                        stringResource(
                            R.string.max_memory_trend_stable,
                            delta.previousKb.toString()
                        )
                    )
                    is MemoryLedger.MemoryDelta.Changed -> DetailRow(
                        stringResource(R.string.max_memory_trend),
                        if (delta.deltaKb > 0) {
                            stringResource(
                                R.string.max_memory_trend_grew,
                                delta.percent.toString(),
                                delta.deltaKb.toString()
                            )
                        } else {
                            stringResource(
                                R.string.max_memory_trend_shrank,
                                delta.percent.toString(),
                                (-delta.deltaKb).toString()
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        value?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BootRow(label: String, value: String, ok: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun OwnershipDiagnosticsCard() {
    val leases = nd.max.core.hardware.ControlOwnership.snapshot()
    MaxSurface(modifier = Modifier.padding(top = 14.dp)) {
        Text(stringResource(R.string.diagnostics_active_ownership), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                Text(stringResource(R.string.diagnostics_hardware_report), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.diagnostics_hardware_report_desc), style = MaxTextRole.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            nd.max.ui.component.StudioTextButton(onClick = {
                val report = HardwareRuntime.compactReport(context)
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("MaxManager Hardware Report", report))
                Toast.makeText(context, "Hardware report copied", Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.diagnostics_copy)) }
        }
    }
}
