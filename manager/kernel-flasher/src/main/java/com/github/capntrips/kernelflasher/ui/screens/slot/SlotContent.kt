@file:OptIn(ExperimentalMaterial3Api::class)

package com.github.capntrips.kernelflasher.ui.screens.slot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.ui.components.SlotCard
import com.github.capntrips.kernelflasher.ui.components.FlasherGlassCard
import com.github.capntrips.kernelflasher.ui.components.FlasherGlassTheme

@ExperimentalAnimationApi
@ExperimentalMaterial3Api
@ExperimentalUnitApi
@Composable
fun ColumnScope.SlotContent(
    viewModel: SlotViewModel,
    slotSuffix: String,
    navController: NavController,
    glassTheme: FlasherGlassTheme = FlasherGlassTheme()
) {
    val context = LocalContext.current
    val titleColor = glassTheme.primaryColor ?: MaterialTheme.colorScheme.primary
    val bodyColor = glassTheme.contentColor ?: MaterialTheme.colorScheme.onSurface
    val dividerColor = (glassTheme.contentColor ?: MaterialTheme.colorScheme.outlineVariant).copy(alpha = if (glassTheme.isGlassActive) 0.15f else 0.3f)

    // --- SLOT CARD (HEADER) ---
    // SlotCard bawaan sudah kita bagusin sebelumnya, jadi biarkan saja
    SlotCard(
        title = stringResource(if (slotSuffix == "_a") R.string.slot_a else if (slotSuffix == "_b") R.string.slot_b else R.string.slot),
        viewModel = viewModel,
        navController = navController,
        isSlotScreen = true
    )

    AnimatedVisibility(!viewModel.isRefreshing) {
        Column {
            Spacer(Modifier.height(24.dp))

            // --- GROUP 1: CORE OPERATIONS ---
            Text(
                text = "Core Operations",
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
                    // FLASH
                    if (viewModel.isActive) {
                        SlotActionItem(
                            icon = Icons.Default.SystemUpdate, // Ikon Flash
                            title = stringResource(R.string.flash),
                            subtitle = "Flash AK3 zip or partition images",
                            onClick = { navController.navigate("slot$slotSuffix/flash") },
                            contentColor = bodyColor
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor)
                    }

                    // BACKUP
                    SlotActionItem(
                        icon = Icons.Default.Save, // Ikon Backup
                        title = stringResource(R.string.backup),
                        subtitle = "Backup boot, dtbo, and others",
                        onClick = {
                            viewModel.clearFlash(context)
                            navController.navigate("slot$slotSuffix/backup")
                        },
                        contentColor = bodyColor
                    )

                    // RESTORE
                    if (viewModel.isActive) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor)
                        SlotActionItem(
                            icon = Icons.Default.Restore, // Ikon Restore
                            title = stringResource(R.string.restore),
                            subtitle = "Restore partitions from backups",
                            onClick = { navController.navigate("slot$slotSuffix/backups") },
                            contentColor = bodyColor
                        )
                    }
            }

            Spacer(Modifier.height(24.dp))

            // --- GROUP 2: UTILITIES ---
            Text(
                text = "Utilities",
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
                    // CHECK KERNEL VERSION
                    SlotActionItem(
                        icon = Icons.Default.Memory,
                        title = stringResource(R.string.check_kernel_version),
                        subtitle = "Read version string from kernel binary",
                        onClick = { if (!viewModel.isRefreshing) viewModel.getKernel(context) },
                        contentColor = bodyColor
                    )

                    // VENDOR DLKM LOGIC
                    if (viewModel.hasVendorDlkm) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor)
                        
                        AnimatedVisibility(!viewModel.isRefreshing) {
                            Column {
                                // MOUNTED
                                AnimatedVisibility(viewModel.isVendorDlkmMounted) {
                                    SlotActionItem(
                                        icon = Icons.Default.Eject,
                                        title = stringResource(R.string.unmount_vendor_dlkm),
                                        subtitle = "Unmount vendor_dlkm partition",
                                        onClick = { viewModel.unmountVendorDlkm(context) },
                                        contentColor = bodyColor
                                    )
                                }

                                // NOT MOUNTED BUT MAPPED
                                AnimatedVisibility(!viewModel.isVendorDlkmMounted && viewModel.isVendorDlkmMapped) {
                                    Column {
                                        SlotActionItem(
                                            icon = Icons.Default.SdStorage,
                                            title = stringResource(R.string.mount_vendor_dlkm),
                                            subtitle = "Mount vendor_dlkm to system",
                                            onClick = { viewModel.mountVendorDlkm(context) },
                                            contentColor = bodyColor
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = dividerColor)
                                        SlotActionItem(
                                            icon = Icons.Default.LinkOff,
                                            title = stringResource(R.string.unmap_vendor_dlkm),
                                            subtitle = "Unmap vendor_dlkm device",
                                            onClick = { viewModel.unmapVendorDlkm(context) },
                                            contentColor = bodyColor
                                        )
                                    }
                                }

                                // NOT MAPPED
                                AnimatedVisibility(!viewModel.isVendorDlkmMounted && !viewModel.isVendorDlkmMapped) {
                                    SlotActionItem(
                                        icon = Icons.Default.Link,
                                        title = stringResource(R.string.map_vendor_dlkm),
                                        subtitle = "Map vendor_dlkm partition",
                                        onClick = { viewModel.mapVendorDlkm(context) },
                                        contentColor = bodyColor
                                    )
                                }
                            }
                        }
                    }
            }
            
            // Spacer bawah agar tidak kepotong navbar/scroll
            Spacer(Modifier.height(100.dp))
        }
    }
}

// --- HELPER COMPONENT ---

@Composable
fun SlotActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold, color = contentColor) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.7f)) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.padding(8.dp)
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor.copy(alpha = 0.5f)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
