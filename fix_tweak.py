import re
import sys

filepath = r"c:\Users\Admin\Desktop\optmize-main (1) (8)\optmize-main\manager\app\src\main\java\zx\azenith\ui\mainscreens\TweakScreen.kt"

with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

start_idx = content.find("item { TweaksSectionTitle(stringResource(R.string.section_additionalsettings)) }")
end_idx = content.find("item {\n                    Spacer(modifier = Modifier.height(10.dp))\n                    if (viewModel.currentRefreshRate != null", start_idx)

if start_idx == -1 or end_idx == -1:
    print("Could not find boundaries")
    sys.exit(1)

new_block = """                item {
                    if (viewModel.preloadState != null && 
                        viewModel.memKillerState != null && 
                        viewModel.appPriorState != null && 
                        viewModel.dndState != null && 
                        viewModel.fstrimState != null) {
                        
                        TweaksSectionTitle(stringResource(R.string.section_features))
                        ExpressiveList(
                            content = buildList {
                                add {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Rounded.RocketLaunch,
                                        title = stringResource(R.string.game_preload),
                                        summary = stringResource(R.string.game_preload_desc),
                                        checked = viewModel.preloadState!!,
                                        onCheckedChange = { viewModel.updatePreloadMode(it) }
                                    )
                                }
                                if (isFullModeEnabled) {
                                    add {
                                        ExpressiveSwitchItem(
                                            icon = Icons.Rounded.CleaningServices,
                                            title = stringResource(R.string.memory_killer),
                                            summary = stringResource(R.string.memory_killer_desc),
                                            checked = viewModel.memKillerState!!,
                                            onCheckedChange = { viewModel.updateMemoryKiller(it) }
                                        )
                                    }
                                }
                                if (isFullModeEnabled) {
                                    add {
                                        ExpressiveSwitchItem(
                                            icon = Icons.Rounded.SwapVerticalCircle,
                                            title = stringResource(R.string.app_priority_control),
                                            summary = stringResource(R.string.app_priority_control_desc),
                                            checked = viewModel.appPriorState!!,
                                            onCheckedChange = { viewModel.updateAppPriority(it) }
                                        )
                                    }
                                }
                                add {
                                    ExpressiveSwitchItem(
                                        icon = Icons.Rounded.DoNotDisturbOn,
                                        title = stringResource(R.string.dnd_mode_gaming),
                                        summary = stringResource(R.string.dnd_mode_gaming_desc),
                                        checked = viewModel.dndState!!,
                                        onCheckedChange = { viewModel.updateDndMode(it) }
                                    )
                                }
                                if (isFullModeEnabled) {
                                    add {
                                        ExpressiveSwitchItem(
                                            icon = Icons.Outlined.ContentCut,
                                            title = stringResource(R.string.trim_filesystem),
                                            summary = stringResource(R.string.trim_filesystem_desc),
                                            checked = viewModel.fstrimState!!,
                                            onCheckedChange = { viewModel.updateFstrim(it) }
                                        )
                                    }
                                }
                                add {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Outlined.SdStorage) },
                                        onClick = { navController.navigate("zrammanager") },
                                        headlineContent = { Text(stringResource(R.string.zram_title)) },
                                        supportingContent = { Text(stringResource(R.string.zram_desc)) },
                                    )
                                }
                                add {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Filled.TouchApp) },
                                        onClick = { navController.navigate("touchboost") },
                                        headlineContent = { Text(stringResource(R.string.touch_boost_title)) },
                                        supportingContent = { Text(stringResource(R.string.touch_boost_desc)) },
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        TweaksSectionTitle(stringResource(R.string.section_CPUSettings))
                        ExpressiveList(
                            content = listOf(
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Filled.Ballot) },
                                        onClick = { navController.navigate("governorsettings") },
                                        headlineContent = { Text(stringResource(R.string.gov_settings)) },
                                        supportingContent = { Text(stringResource(R.string.gov_settingsdesc)) },
                                    )
                                },
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Outlined.DeveloperBoard) },
                                        onClick = { navController.navigate("cpucorecontrol") },
                                        headlineContent = { Text(stringResource(R.string.cpu_core_control_title)) },
                                        supportingContent = { Text(stringResource(R.string.cpu_core_control_desc)) },
                                    )
                                }
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        TweaksSectionTitle(stringResource(R.string.section_mali_gpu))
                        ExpressiveList(
                            content = listOf(
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Outlined.Memory) },
                                        onClick = { navController.navigate("maligpufreq") },
                                        headlineContent = { Text(stringResource(R.string.mali_freq_title)) },
                                        supportingContent = { Text(stringResource(R.string.mali_freq_desc)) },
                                    )
                                },
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Outlined.DeveloperBoard) },
                                        onClick = { navController.navigate("adrenogpufreq") },
                                        headlineContent = { Text(stringResource(R.string.adreno_freq_title)) },
                                        supportingContent = { Text(stringResource(R.string.adreno_menu_desc)) },
                                    )
                                }
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        TweaksSectionTitle(stringResource(R.string.section_display_render_settings))
                        ExpressiveList(
                            content = listOf(
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Filled.Palette) },
                                        onClick = { navController.navigate("displaystudio") },
                                        headlineContent = { Text(stringResource(R.string.display_studio_title)) },
                                        supportingContent = { Text(stringResource(R.string.display_studio_desc)) },
                                    )
                                },
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Filled.AspectRatio) },
                                        onClick = { navController.navigate("resolutionscreen") },
                                        headlineContent = { Text(stringResource(R.string.resolution_title)) },
                                        supportingContent = { Text(stringResource(R.string.resolution_desc)) },
                                    )
                                },
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Outlined.Speed) },
                                        onClick = { navController.navigate("fpsoverlay") },
                                        headlineContent = { Text(stringResource(R.string.fps_overlay_title)) },
                                        supportingContent = { Text(stringResource(R.string.fps_overlay_menu_desc)) },
                                    )
                                }
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        TweaksSectionTitle(stringResource(R.string.section_power_thermal))
                        ExpressiveList(
                            content = listOf(
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Filled.BatteryChargingFull) },
                                        onClick = { navController.navigate("chargingscreen") },
                                        headlineContent = { Text(stringResource(R.string.charging_title)) },
                                        supportingContent = { Text(stringResource(R.string.charging_desc)) },
                                    )
                                },
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Outlined.Bedtime) },
                                        onClick = { navController.navigate("dozemode") },
                                        headlineContent = { Text(stringResource(R.string.dozemode_title)) },
                                        supportingContent = { Text(stringResource(R.string.dozemode_menu_desc)) },
                                    )
                                }
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        TweaksSectionTitle(stringResource(R.string.section_additionalsettings))
                        ExpressiveList(
                            content = listOf(
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Outlined.Hub) },
                                        onClick = { navController.navigate("networkscheduler") },
                                        headlineContent = { Text(stringResource(R.string.net_sched_title)) },
                                        supportingContent = { Text(stringResource(R.string.net_sched_menu_desc)) },
                                    )
                                },
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Outlined.Tune) },
                                        onClick = { navController.navigate("advancedconfig") },
                                        headlineContent = { Text(stringResource(R.string.advanced_config_title)) },
                                        supportingContent = { Text(stringResource(R.string.advanced_config_desc)) },
                                    )
                                },
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Outlined.DeleteSweep) },
                                        onClick = { navController.navigate("debloatfreeze") },
                                        headlineContent = { Text(stringResource(R.string.debloat_freeze_title)) },
                                        supportingContent = { Text(stringResource(R.string.debloat_freeze_desc)) },
                                    )
                                },
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Outlined.Build) },
                                        onClick = { navController.navigate("dex2oat") },
                                        headlineContent = { Text(stringResource(R.string.dex2oat_title)) },
                                        supportingContent = { Text(stringResource(R.string.dex2oat_desc)) },
                                    )
                                },
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Filled.Dns) },
                                        onClick = { navController.navigate("setedit") },
                                        headlineContent = { Text(stringResource(R.string.setedit_title)) },
                                        supportingContent = { Text(stringResource(R.string.setedit_menu_desc)) },
                                    )
                                },
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Filled.Terminal) },
                                        onClick = { navController.navigate("logsviewer") },
                                        headlineContent = { Text(stringResource(R.string.logsviewer_title)) },
                                        supportingContent = { Text(stringResource(R.string.logsviewer_menu_desc)) },
                                    )
                                },
                                {
                                    ExpressiveListItem(
                                        leadingContent = { LeadingIcon(icon = Icons.Filled.Terminal) },
                                        onClick = { navController.navigate("terminal") },
                                        headlineContent = { Text(stringResource(R.string.terminal_shell)) },
                                        supportingContent = { Text(stringResource(R.string.terminal_shell_desc)) },
                                    )
                                }
                            )
                        )

                    } else {
                        SectionLoadingIndicator()
                    }
                }
"""

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content[:start_idx] + new_block + content[end_idx:])

print("Success")
