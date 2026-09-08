package com.github.capntrips.kernelflasher.ui.screens.slot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.ui.components.FlashButton
import com.github.capntrips.kernelflasher.ui.components.FlashList
import com.github.capntrips.kernelflasher.ui.components.FlasherGlassCard
import com.github.capntrips.kernelflasher.ui.components.FlasherGlassTheme
import com.github.capntrips.kernelflasher.ui.components.SlotCard

@ExperimentalAnimationApi
@ExperimentalMaterial3Api
@ExperimentalUnitApi
@Composable
fun ColumnScope.SlotFlashContent(
    viewModel: SlotViewModel,
    slotSuffix: String,
    navController: NavController,
    glassTheme: FlasherGlassTheme = FlasherGlassTheme()
) {
    val context = LocalContext.current
    val titleColor = glassTheme.primaryColor ?: MaterialTheme.colorScheme.primary
    val bodyColor = glassTheme.contentColor ?: MaterialTheme.colorScheme.onSurface
    val mutedColor = bodyColor.copy(alpha = 0.7f)
    val dividerColor = (glassTheme.contentColor ?: MaterialTheme.colorScheme.outlineVariant).copy(alpha = if (glassTheme.isGlassActive) 0.15f else 0.3f)
    val primaryColor = glassTheme.primaryColor ?: MaterialTheme.colorScheme.primary
    val onPrimaryColor = if (primaryColor.luminance() > 0.5f) Color.Black else Color.White
    val route = navController.currentDestination!!.route!!

    if (!listOf("/flash/ak3", "/flash/image/flash", "/backup/backup").any { route.endsWith(it) }) {
        SlotCard(
            title = stringResource(if (slotSuffix == "_a") R.string.slot_a else if (slotSuffix == "_b") R.string.slot_b else R.string.slot),
            viewModel = viewModel,
            navController = navController,
            isSlotScreen = true,
            showDlkm = false,
            glassTheme = glassTheme
        )
        Spacer(Modifier.height(24.dp))

        if (route.endsWith("/flash")) {
            // --- ROOT FLASH SCREEN: pick AK3 zip vs. a raw partition image ---
            SectionLabel(stringResource(R.string.flash), titleColor)
            FlasherGlassCard(
                theme = glassTheme,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp),
                fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                FlashButton(
                    text = stringResource(R.string.flash_ak3_zip),
                    subtitle = stringResource(R.string.flash_ak3_zip_subtitle),
                    icon = Icons.Default.FolderZip,
                    contentColor = bodyColor,
                    callback = { uri ->
                        navController.navigate("slot$slotSuffix/flash/ak3") {
                            popUpTo("slot$slotSuffix")
                        }
                        viewModel.flashAk3(context, uri)
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor)
                SlotActionItem(
                    icon = Icons.Default.Layers,
                    title = stringResource(R.string.flash_partition_image),
                    subtitle = stringResource(R.string.flash_partition_image_subtitle),
                    onClick = { navController.navigate("slot$slotSuffix/flash/image") },
                    contentColor = bodyColor
                )
            }
        } else if (route.endsWith("/flash/image")) {
            // --- PARTITION IMAGE PICKER ---
            SectionLabel(stringResource(R.string.select_partition), titleColor)
            val partitions = viewModel.backupPartitions.keys.sorted()
            if (partitions.isNotEmpty()) {
                FlasherGlassCard(
                    theme = glassTheme,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    partitions.forEachIndexed { index, partitionName ->
                        FlashButton(
                            text = partitionName,
                            subtitle = stringResource(R.string.tap_to_select_image),
                            icon = Icons.Outlined.SdStorage,
                            contentColor = bodyColor,
                            callback = { uri ->
                                navController.navigate("slot$slotSuffix/flash/image/flash") {
                                    popUpTo("slot$slotSuffix")
                                }
                                viewModel.flashImage(context, uri, partitionName)
                            }
                        )
                        if (index < partitions.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor)
                        }
                    }
                }
            } else {
                EmptyPartitionsState(mutedColor)
            }
        } else if (route.endsWith("/backup")) {
            // --- BACKUP PARTITION SELECTION ---
            SectionLabel(stringResource(R.string.select_partitions_to_backup), titleColor)
            val partitions = viewModel.backupPartitions.keys.sorted()
            if (partitions.isNotEmpty()) {
                FlasherGlassCard(
                    theme = glassTheme,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    partitions.forEachIndexed { index, partitionName ->
                        val isChecked = viewModel.backupPartitions[partitionName] == true
                        ListItem(
                            modifier = Modifier.clickable {
                                viewModel.backupPartitions[partitionName] = !isChecked
                            },
                            headlineContent = { Text(partitionName, fontWeight = FontWeight.Medium, color = bodyColor) },
                            leadingContent = { Icon(Icons.Outlined.SdStorage, null, tint = bodyColor.copy(alpha = 0.7f)) },
                            trailingContent = {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { viewModel.backupPartitions[partitionName] = it }
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        if (index < partitions.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = dividerColor)
                        }
                    }
                }
            } else {
                EmptyPartitionsState(mutedColor)
            }

            Spacer(Modifier.height(24.dp))

            Button(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = viewModel.backupPartitions.any { it.value },
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = onPrimaryColor),
                onClick = {
                    viewModel.backup(context)
                    navController.navigate("slot$slotSuffix/backup/backup") {
                        popUpTo("slot$slotSuffix")
                    }
                }
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.backup_now), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    } else {
        // --- LIVE OUTPUT: ak3 flash / image flash / backup progress ---
        val isAk3 = route.contains("ak3")
        val isBackupOutput = route.endsWith("/backup/backup")
        val listTitle = when {
            isAk3 -> stringResource(R.string.flash)
            isBackupOutput -> stringResource(R.string.backup)
            else -> stringResource(R.string.flash)
        }
        val saveLogLabel = when {
            isAk3 -> stringResource(R.string.save_ak3_log)
            isBackupOutput -> stringResource(R.string.save_backup_log)
            else -> stringResource(R.string.save_flash_log)
        }

        FlashList(
            cardTitle = listTitle,
            output = if (isAk3) viewModel.uiPrintedOutput else viewModel.flashOutput,
            isRefreshing = viewModel.isRefreshing,
            status = viewModel.wasFlashSuccess,
            glassTheme = glassTheme
        ) {
            AnimatedVisibility(!viewModel.isRefreshing && viewModel.wasFlashSuccess != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        onClick = { viewModel.saveLog(context) }
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text(saveLogLabel)
                    }

                    if (isAk3) {
                        AnimatedVisibility(!route.endsWith("/backups/{backupId}/flash/ak3") && viewModel.wasFlashSuccess != false) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                onClick = {
                                    viewModel.backupZip(context) {
                                        navController.navigate("slot$slotSuffix/backups") {
                                            popUpTo("slot$slotSuffix")
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Archive, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.save_ak3_zip_as_backup))
                            }
                        }
                    }

                    if (viewModel.wasFlashSuccess != false && isBackupOutput) {
                        Button(
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = onPrimaryColor),
                            onClick = { navController.popBackStack() }
                        ) {
                            Text(stringResource(R.string.back))
                        }
                    } else {
                        Button(
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = onPrimaryColor),
                            onClick = { navController.navigate("reboot") }
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.reboot))
                        }
                    }
                }
            }
        }
    }
}

// --- HELPER COMPOSABLES ---

@Composable
private fun SectionLabel(title: String, color: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun EmptyPartitionsState(mutedColor: Color) {
    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.SdStorage, null, modifier = Modifier.size(40.dp), tint = mutedColor)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.no_partitions_available), color = mutedColor)
        }
    }
}
