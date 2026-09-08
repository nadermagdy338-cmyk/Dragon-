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

package nd.max.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Consistent, app-wide "this may cause instability" / "heads up" pattern
 * (UX plan §17, Safety UX). Before this, every screen that wanted to warn
 * about a risky setting (manual governors, raw thermal profiles, disabling
 * tweaks entirely...) either rolled its own text or said nothing. One
 * component means one look, and one place to fix if the pattern needs to
 * change later.
 *
 * Deliberately built as a thin wrapper around [ExpressiveInfoCard] — same
 * layout and spacing the rest of the app already uses, just re-tinted for
 * warning/notice severity — instead of a new layout to keep in sync.
 */
enum class WarningSeverity { NOTICE, CAUTION }

@Composable
fun WarningBanner(
    text: String,
    modifier: Modifier = Modifier,
    severity: WarningSeverity = WarningSeverity.CAUTION
) {
    val color = when (severity) {
        WarningSeverity.CAUTION -> MaterialTheme.colorScheme.error
        WarningSeverity.NOTICE -> MaterialTheme.colorScheme.tertiary
    }

    ExpressiveInfoCard(
        modifier = modifier,
        containerColor = color.copy(alpha = 0.08f),
        leadingContent = {
            Icon(
                imageVector = when (severity) {
                    WarningSeverity.CAUTION -> Icons.Rounded.WarningAmber
                    WarningSeverity.NOTICE -> Icons.Rounded.Info
                },
                contentDescription = null,
                tint = color
            )
        },
        supportingContent = {
            Text(text = text, color = color)
        }
    )
}
