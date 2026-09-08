@file:OptIn(ExperimentalMaterial3Api::class)

package com.github.capntrips.kernelflasher.ui.screens.backups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.common.PartitionUtil
import com.github.capntrips.kernelflasher.ui.components.FlasherGlassCard
import com.github.capntrips.kernelflasher.ui.components.FlasherGlassTheme

@ExperimentalMaterial3Api
@Composable
fun ColumnScope.BackupsContent(
    viewModel: BackupsViewModel,
    navController: NavController,
    glassTheme: FlasherGlassTheme = FlasherGlassTheme()
) {
    val context = LocalContext.current
    val titleColor = glassTheme.primaryColor ?: MaterialTheme.colorScheme.primary
    val bodyColor = glassTheme.contentColor ?: MaterialTheme.colorScheme.onSurface
    val mutedColor = bodyColor.copy(alpha = 0.7f)
    val dividerColor = (glassTheme.contentColor ?: MaterialTheme.colorScheme.outlineVariant).copy(alpha = if (glassTheme.isGlassActive) 0.15f else 0.3f)

    // === VIEW 1: DETAIL BACKUP (Saat salah satu backup dipilih) ===
    if (viewModel.currentBackup != null && viewModel.backups.containsKey(viewModel.currentBackup)) {
        val currentBackup = viewModel.backups.getValue(viewModel.currentBackup!!)
        
        // 1. HEADER INFO
        Text(
            text = "Backup Details",
            style = MaterialTheme.typography.titleSmall,
            color = titleColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )

        FlasherGlassCard(
            theme = glassTheme,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            fallbackColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            BackupInfoItem(Icons.Default.Label, stringResource(R.string.backup_type), currentBackup.type, contentColor = bodyColor)
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = dividerColor)
            BackupInfoItem(Icons.Default.Memory, stringResource(R.string.kernel_version), currentBackup.kernelVersion, contentColor = bodyColor)

            if (currentBackup.type == "raw") {
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = dividerColor)
                BackupInfoItem(
                    icon = Icons.Default.Fingerprint,
                    title = stringResource(R.string.boot_sha1),
                    value = currentBackup.bootSha1!!.substring(0, 8),
                    isMonospace = true,
                    contentColor = bodyColor
                )
            }
        }

        // 2. PARTITION HASHES (Jika ada)
        if (currentBackup.type == "raw" && currentBackup.hashes != null) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Partition Hashes",
                style = MaterialTheme.typography.titleSmall,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )

            FlasherGlassCard(
                theme = glassTheme,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                // [FIX] Menggunakan .get() != null sebagai pengganti containsKey
                val partitions = PartitionUtil.PartitionNames.filter { currentBackup.hashes!!.get(it) != null }

                partitions.forEachIndexed { index, partitionName ->
                    // [FIX] Menggunakan .get() manual
                    val hash = currentBackup.hashes!!.get(partitionName)

                    if (hash != null) {
                        BackupInfoItem(
                            icon = Icons.Outlined.Storage,
                            title = partitionName,
                            value = hash.substring(0, 8),
                            isMonospace = true,
                            contentColor = bodyColor
                        )
                        if (index < partitions.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = dividerColor)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 3. ACTIONS
        Text(
            text = "Actions",
            style = MaterialTheme.typography.titleSmall,
            color = titleColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )

        FlasherGlassCard(
            theme = glassTheme,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp),
            fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            AnimatedVisibility(!viewModel.isRefreshing) {
                Column {
                    // RESTORE / FLASH
                    if (currentBackup.type == "raw") {
                        BackupActionItem(
                            icon = Icons.Default.Restore,
                            title = stringResource(R.string.restore),
                            subtitle = "Restore partitions to active slot",
                            onClick = { navController.navigate("backups/${viewModel.currentBackup!!}/restore") },
                            contentColor = bodyColor,
                            subtitleColor = mutedColor
                        )
                    } else if (currentBackup.type == "ak3") {
                        BackupActionItem(
                            icon = Icons.Default.SystemUpdate,
                            title = stringResource(R.string.flash),
                            subtitle = "Flash this AK3 zip",
                            onClick = {
                                navController.navigate("backups/${viewModel.currentBackup!!}/restore") 
                            },
                            contentColor = bodyColor,
                            subtitleColor = mutedColor
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor)

                    // DELETE
                    BackupActionItem(
                        icon = Icons.Default.Delete,
                        title = stringResource(R.string.delete),
                        subtitle = "Permanently remove this backup",
                        onClick = { viewModel.delete(context) { navController.popBackStack() } },
                        isDestructive = true,
                        contentColor = bodyColor,
                        subtitleColor = mutedColor
                    )
                }
            }
        }

    } else {
        // === VIEW 2: LIST BACKUP (Tampilan Awal) ===
        
        // Header / Migrate
        AnimatedVisibility(viewModel.needsMigration) {
            FlasherGlassCard(
                theme = glassTheme,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable { viewModel.migrate(context) },
                fallbackColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null, tint = bodyColor)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Legacy Backups Found", fontWeight = FontWeight.Bold, color = bodyColor)
                        Text("Tap to migrate to new format", style = MaterialTheme.typography.bodySmall, color = mutedColor)
                    }
                }
            }
        }

        if (viewModel.backups.isNotEmpty()) {
            Text(
                text = "Local Backups",
                style = MaterialTheme.typography.titleSmall,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )

            // List Item
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (id in viewModel.backups.keys.sortedByDescending { it }) {
                    val currentBackup = viewModel.backups[id]!!

                    FlasherGlassCard(
                        theme = glassTheme,
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (!viewModel.isRefreshing) {
                                navController.navigate("backups/$id")
                            }
                        },
                        cornerRadius = 20.dp,
                        fallbackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon based on type
                            val icon = if (currentBackup.type == "ak3") Icons.Default.Archive else Icons.Default.Backup
                            Icon(icon, null, tint = titleColor, modifier = Modifier.size(24.dp))
                            
                            Spacer(Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = id, // Date string as ID
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = bodyColor
                                )
                                Text(
                                    text = currentBackup.kernelVersion,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = mutedColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = mutedColor)
                        }
                    }
                }
            }
        } else {
            // Empty State
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Backup, null, modifier = Modifier.size(48.dp), tint = mutedColor)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.no_backups_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = mutedColor
                    )
                }
            }
        }
    }
}

// --- HELPER COMPONENTS ---

@Composable
fun BackupInfoItem(
    icon: ImageVector,
    title: String,
    value: String,
    isMonospace: Boolean = false,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = contentColor) },
        supportingContent = { 
            Text(
                value, 
                style = if (isMonospace) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodySmall, 
                color = contentColor.copy(alpha = 0.7f), 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis
            ) 
        },
        leadingContent = { Icon(icon, null, tint = contentColor) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun BackupActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = contentColor.copy(alpha = 0.7f)
) {
    val color = if (isDestructive) MaterialTheme.colorScheme.error else contentColor
    val iconColor = if (isDestructive) MaterialTheme.colorScheme.error else contentColor

    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold, color = color) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.padding(8.dp)
            )
        },
        trailingContent = {
             Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = subtitleColor, modifier = Modifier.size(16.dp))
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
