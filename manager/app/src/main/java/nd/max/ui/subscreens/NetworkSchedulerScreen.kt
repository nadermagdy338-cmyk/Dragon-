@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.MaxUiMetrics
import nd.max.ui.component.CustomContentDialog
import nd.max.ui.component.ExpressiveDropdownItem
import nd.max.ui.component.ExpressiveInfoCard
import nd.max.ui.component.ExpressiveList
import nd.max.ui.component.ExpressiveListItem
import nd.max.ui.component.ExpressiveSwitchItem
import nd.max.ui.component.LeadingIcon
import nd.max.ui.mainscreens.SectionLoadingIndicator
import nd.max.ui.mainscreens.TweaksSectionTitle
import nd.max.ui.viewmodel.NetworkSchedulerViewModel

/**
 * Network + Scheduler control surface.
 *
 * The screen deliberately uses the application's MaterialTheme directly:
 * no page-local theme, no fixed brand color, and no decorative dashboard
 * chrome that competes with the actual controls.
 */
@Composable
fun NetworkSchedulerScreen(
    navController: NavController,
    viewModel: NetworkSchedulerViewModel = viewModel()
) {
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.loadState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.net_sched_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "TCP/IP · Scheduler",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when (viewModel.isAvailable) {
            null -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { SectionLoadingIndicator() }

            false -> Box(
                Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                ExpressiveInfoCard(
                    leadingContent = {
                        LeadingIcon(Icons.Outlined.SettingsEthernet, null)
                    },
                    supportingContent = {
                        Text(stringResource(R.string.net_sched_unavailable))
                    }
                )
            }

            true -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(
                    start = MaxUiMetrics.screenHorizontalPadding,
                    end = MaxUiMetrics.screenHorizontalPadding,
                    top = MaxUiMetrics.screenTopPadding,
                    bottom = 28.dp + WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(MaxUiMetrics.screenItemGap)
            ) {
                item { NetworkOverview(viewModel) }

                if (hasNetworkControls(viewModel)) {
                    item { TweaksSectionTitle(stringResource(R.string.net_section_title)) }
                    item { NetworkControls(viewModel) }
                }

                if (hasSchedulerControls(viewModel)) {
                    item { TweaksSectionTitle(stringResource(R.string.sched_section_title)) }
                    item { SchedulerControls(viewModel) }
                }

                if (viewModel.hasUclampMax || viewModel.hasUclampMin) {
                    item { TweaksSectionTitle(stringResource(R.string.sched_uclamp_section_title)) }
                    item {
                        ExpressiveInfoCard(
                            leadingContent = { LeadingIcon(Icons.Outlined.Speed, null) },
                            supportingContent = { Text(stringResource(R.string.sched_uclamp_desc)) }
                        )
                    }
                    item { UclampControls(viewModel) }
                }

                if (viewModel.genericTunables.isNotEmpty()) {
                    item { TweaksSectionTitle(stringResource(R.string.sched_advanced_section_title)) }
                    item {
                        ExpressiveInfoCard(
                            leadingContent = { LeadingIcon(Icons.Outlined.Warning, null) },
                            supportingContent = { Text(stringResource(R.string.sched_advanced_desc)) }
                        )
                    }
                    item { AdvancedTunables(viewModel) }
                }

                if (viewModel.hasPrintk) {
                    item { TweaksSectionTitle(stringResource(R.string.sched_kernel_section_title)) }
                    item {
                        RawValueRow(
                            title = stringResource(R.string.sched_printk_title),
                            summary = stringResource(R.string.sched_printk_desc),
                            value = viewModel.printkValue,
                            onConfirm = viewModel::setPrintk
                        )
                    }
                }
            }
        }
    }
}

private fun hasNetworkControls(vm: NetworkSchedulerViewModel) =
    vm.hasTcpCongestion || vm.hasSyncookies || vm.hasTcpReuse ||
        vm.hasTcpFastopen || vm.hasTcpSack || vm.hasTcpEcn

private fun hasSchedulerControls(vm: NetworkSchedulerViewModel) =
    vm.hasBore || vm.hasAutogroup || vm.hasChildRunsFirst ||
        vm.hasSchedstats || vm.hasTunableScaling || vm.hasCstateAware

@Composable
private fun NetworkOverview(vm: NetworkSchedulerViewModel) {
    val active = listOf(
        vm.hasTcpCongestion,
        vm.hasSyncookies,
        vm.hasTcpReuse,
        vm.hasTcpFastopen,
        vm.hasTcpSack,
        vm.hasTcpEcn
    ).count { it }

    val enabled = listOf(
        vm.syncookiesEnabled,
        vm.tcpReuseEnabled,
        vm.tcpFastopenEnabled,
        vm.tcpSackEnabled,
        vm.tcpEcnEnabled,
        vm.boreEnabled,
        vm.autogroupEnabled,
        vm.childRunsFirstEnabled,
        vm.schedstatsEnabled,
        vm.cstateAwareEnabled
    ).count { it }

    Card(
        shape = RoundedCornerShape(MaxUiMetrics.cardRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.NetworkCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "System networking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Live kernel controls exposed by this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        "LIVE",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryPill("TCP controls", active.toString(), Modifier.weight(1f))
                SummaryPill("Enabled", enabled.toString(), Modifier.weight(1f))
                SummaryPill("Congestion", vm.tcpCongestion.ifBlank { "—" }, Modifier.weight(1.2f))
            }
        }
    }
}

@Composable
private fun SummaryPill(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun NetworkControls(vm: NetworkSchedulerViewModel) {
    val rows = buildList<@Composable () -> Unit> {
        if (vm.hasTcpCongestion) add {
            val options = vm.availableCongestion
            val selectedIndex = options.indexOf(vm.tcpCongestion).coerceAtLeast(0)
            ExpressiveDropdownItem(
                icon = Icons.Outlined.NetworkCheck,
                title = stringResource(R.string.net_congestion_title),
                summary = stringResource(R.string.net_congestion_desc),
                items = options,
                selectedIndex = selectedIndex,
                onItemSelected = { i -> options.getOrNull(i)?.let(vm::setTcpCongestion) }
            )
        }
        if (vm.hasSyncookies) add {
            ExpressiveSwitchItem(Icons.Outlined.Security, title = stringResource(R.string.net_syncookies_title), summary = stringResource(R.string.net_syncookies_desc), checked = vm.syncookiesEnabled, onCheckedChange = vm::setSyncookies)
        }
        if (vm.hasTcpReuse) add {
            ExpressiveSwitchItem(Icons.Outlined.Sync, title = stringResource(R.string.net_tcp_reuse_title), summary = stringResource(R.string.net_tcp_reuse_desc), checked = vm.tcpReuseEnabled, onCheckedChange = vm::setTcpReuse)
        }
        if (vm.hasTcpFastopen) add {
            ExpressiveSwitchItem(Icons.Outlined.Bolt, title = stringResource(R.string.net_tcp_fastopen_title), summary = stringResource(R.string.net_tcp_fastopen_desc), checked = vm.tcpFastopenEnabled, onCheckedChange = vm::setTcpFastopen)
        }
        if (vm.hasTcpSack) add {
            ExpressiveSwitchItem(Icons.Outlined.CheckCircle, title = stringResource(R.string.net_tcp_sack_title), summary = stringResource(R.string.net_tcp_sack_desc), checked = vm.tcpSackEnabled, onCheckedChange = vm::setTcpSack)
        }
        if (vm.hasTcpEcn) add {
            ExpressiveSwitchItem(Icons.Outlined.Warning, title = stringResource(R.string.net_tcp_ecn_title), summary = stringResource(R.string.net_tcp_ecn_desc), checked = vm.tcpEcnEnabled, onCheckedChange = vm::setTcpEcn)
        }
    }
    ExpressiveList(content = rows)
}

@Composable
private fun SchedulerControls(vm: NetworkSchedulerViewModel) {
    val rows = buildList<@Composable () -> Unit> {
        if (vm.hasBore) add {
            ExpressiveSwitchItem(Icons.Outlined.Speed, title = "BORE scheduler", summary = "Use the BORE scheduler path when exposed by the kernel.", checked = vm.boreEnabled, onCheckedChange = vm::setBore)
        }
        if (vm.hasAutogroup) add {
            ExpressiveSwitchItem(Icons.Outlined.Sync, title = "Automatic task grouping", summary = "Kernel scheduler autogroup control.", checked = vm.autogroupEnabled, onCheckedChange = vm::setAutogroup)
        }
        if (vm.hasChildRunsFirst) add {
            ExpressiveSwitchItem(Icons.Outlined.Bolt, title = "Child tasks run first", summary = "Prefer a child task before returning to the parent.", checked = vm.childRunsFirstEnabled, onCheckedChange = vm::setChildRunsFirst)
        }
        if (vm.hasSchedstats) add {
            ExpressiveSwitchItem(Icons.Outlined.Speed, title = "Scheduler statistics", summary = "Expose scheduler accounting and statistics.", checked = vm.schedstatsEnabled, onCheckedChange = vm::setSchedstats)
        }
        if (vm.hasTunableScaling) add {
            val labels = listOf("None", "Logarithmic", "Linear")
            ExpressiveDropdownItem(icon = Icons.Outlined.Tune, title = "Tunable scaling", summary = "Choose how scheduler tunables are scaled.", items = labels, selectedIndex = vm.tunableScalingIndex, onItemSelected = vm::setTunableScaling)
        }
        if (vm.hasCstateAware) add {
            ExpressiveSwitchItem(Icons.Outlined.BatteryChargingFull, title = "C-state aware", summary = "Let scheduler decisions account for idle-state behavior.", checked = vm.cstateAwareEnabled, onCheckedChange = vm::setCstateAware)
        }
    }
    ExpressiveList(content = rows)
}

@Composable
private fun UclampControls(vm: NetworkSchedulerViewModel) {
    val rows = buildList<@Composable () -> Unit> {
        if (vm.hasUclampMax) add {
            RawValueRow("UClamp max", value = vm.uclampMaxValue, onConfirm = vm::setUclampMax)
        }
        if (vm.hasUclampMin) add {
            RawValueRow("UClamp min", value = vm.uclampMinValue, onConfirm = vm::setUclampMin)
        }
    }
    ExpressiveList(content = rows)
}

@Composable
private fun AdvancedTunables(vm: NetworkSchedulerViewModel) {
    ExpressiveList(
        content = vm.genericTunables.map { tunable ->
            {
                RawValueRow(
                    title = tunable.label,
                    value = tunable.value,
                    onConfirm = { vm.setGenericTunable(tunable.path, it) }
                )
            }
        }
    )
}

@Composable
private fun RawValueRow(
    title: String,
    summary: String? = null,
    value: String,
    onConfirm: (String) -> Unit
) {
    var dialogVisible by remember { mutableStateOf(false) }
    var pendingValue by remember(value, dialogVisible) { mutableStateOf(value) }

    ExpressiveListItem(
        onClick = {
            pendingValue = value
            dialogVisible = true
        },
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = {
            Text(
                text = value.ifBlank { "—" },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    )

    CustomContentDialog(
        visible = dialogVisible,
        title = title,
        onDismiss = { dialogVisible = false },
        onConfirm = {
            onConfirm(pendingValue.trim())
            dialogVisible = false
        }
    ) {
        OutlinedTextField(
            value = pendingValue,
            onValueChange = { pendingValue = it },
            label = { Text(stringResource(R.string.net_sched_edit_value)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
