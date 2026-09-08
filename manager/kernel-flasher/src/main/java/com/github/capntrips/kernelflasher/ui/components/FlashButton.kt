package com.github.capntrips.kernelflasher.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A tappable row that opens the system file picker and hands the chosen
 * [Uri] to [callback]. Styled to match the other action rows in this module
 * (icon + title/subtitle + chevron - see [ActionItem]/[SlotActionItem]) so it
 * sits naturally inside a [FlasherGlassCard] instead of standing out as a
 * separate flat outlined button.
 */
@Composable
fun FlashButton(
    text: String,
    subtitle: String? = null,
    icon: ImageVector = Icons.Filled.FolderZip,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    mimeTypes: Array<String> = arrayOf("application/zip", "application/octet-stream"),
    callback: (Uri) -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            callback(uri)
        }
    }

    ListItem(
        modifier = Modifier.clickable { launcher.launch(mimeTypes) },
        headlineContent = {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        },
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
