/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
/*
 * MaxManager interaction language.
 * Small, reusable states used by hardware controls and settings screens.
 */
package nd.max.ui.component

import androidx.compose.animation.AnimatedContent
import nd.max.ui.design.MaxRadius
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A deliberately small state vocabulary shared by settings and hardware controls. */
enum class MaxControlState {
    Idle,
    Applying,
    Applied,
    Failed
}

@Composable
fun MaxControlStatus(
    state: MaxControlState,
    appliedLabel: String = "Applied",
    applyingLabel: String = "Applying…",
    failedLabel: String = "Failed",
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val accent = when (state) {
        MaxControlState.Idle -> scheme.onSurfaceVariant
        MaxControlState.Applying -> scheme.primary
        MaxControlState.Applied -> scheme.primary
        MaxControlState.Failed -> scheme.error
    }
    val container = accent.copy(alpha = if (state == MaxControlState.Idle) 0.07f else 0.12f)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MaxRadius.chip),
        color = container,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "control_status_icon"
            ) { target ->
                when (target) {
                    MaxControlState.Applying -> CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 2.dp,
                        color = accent
                    )
                    MaxControlState.Applied -> Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(14.dp))
                    MaxControlState.Failed -> Icon(Icons.Rounded.Close, null, tint = accent, modifier = Modifier.size(14.dp))
                    MaxControlState.Idle -> Icon(Icons.Rounded.Sync, null, tint = accent, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                text = when (state) {
                    MaxControlState.Idle -> ""
                    MaxControlState.Applying -> applyingLabel
                    MaxControlState.Applied -> appliedLabel
                    MaxControlState.Failed -> failedLabel
                },
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun MaxInfoStrip(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxRadius.row),
        color = accent.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Info, null, tint = accent, modifier = Modifier.size(18.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MaxInlineEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxRadius.tile),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(MaxRadius.row),
                color = accent.copy(alpha = 0.10f)
            ) {
                Icon(Icons.Rounded.Info, null, tint = accent, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.size(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
