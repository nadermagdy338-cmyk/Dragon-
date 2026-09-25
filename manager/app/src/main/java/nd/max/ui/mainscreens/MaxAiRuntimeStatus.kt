/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.mainscreens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import nd.max.R
import nd.max.core.maxai.MaxAiCycleStatus
import nd.max.ui.design.MaxGroup
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxTone

/** An explicit action and its engine-owned progress; navigation itself never runs a cycle. */
@Composable
internal fun MaxAiRuntimeStatus(status: MaxAiCycleStatus, onRefresh: () -> Unit) {
    MaxGroup {
        MaxRow(
            title = stringResource(
                when {
                    status.inFlight -> R.string.max_ai_cycle_running
                    status.lastFailureAtMs != null -> R.string.max_ai_cycle_failed
                    else -> R.string.max_ai_refresh
                },
            ),
            subtitle = stringResource(
                when {
                    status.inFlight -> R.string.max_ai_cycle_running_desc
                    status.lastFailureAtMs != null -> R.string.max_ai_cycle_failed_desc
                    else -> R.string.max_ai_refresh_desc
                },
            ),
            icon = Icons.Rounded.Refresh,
            iconTone = if (status.lastFailureAtMs != null) MaxTone.Caution else MaxTone.Accent,
            enabled = !status.inFlight,
            onClick = if (status.inFlight) null else onRefresh,
        )
    }
}
