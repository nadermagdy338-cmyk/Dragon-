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

package nd.max.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One line of "something is running", with its progress and a way out.
 *
 * The `percent` is nullable on purpose and that is the whole point of this component: a
 * background copy whose destination size was never measured shows an **indeterminate**
 * bar and says so, instead of an animated fake percentage (ADR-07 — the unknown stays
 * unknown). A caller that can measure passes an integer and gets a real bar.
 */
@Composable
fun MaxProgressStrip(
    title: String,
    percent: Int?,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tone: MaxTone = MaxTone.Accent,
    cancelLabel: String? = null,
    onCancel: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MaxRadius.row),
        color = tone.container(),
        border = BorderStroke(MaxSize.hairlineBorder, tone.border()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaxSpace.md, vertical = MaxSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    if (percent != null) {
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.bodySmall,
                            color = tone.content(),
                        )
                    }
                }
                detail?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val barHeight = MaxSpace.hairline + MaxSpace.xs
                if (percent != null) {
                    MaxUsageBar(
                        fraction = percent / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MaxSpace.xs),
                        tone = tone,
                        height = barHeight,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MaxSpace.xs)
                            .height(barHeight)
                            .clip(RoundedCornerShape(MaxRadius.pill)),
                        color = tone.content(),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
            }
            if (onCancel != null && cancelLabel != null) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = cancelLabel,
                        tint = tone.content(),
                        modifier = Modifier.size(MaxSize.iconGlyph),
                    )
                }
            }
        }
    }
}
