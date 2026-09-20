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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Drawer that slides in over a screen's own content.
 *
 * Why it exists rather than a full second screen: the file manager's drawer is a
 * **shortcut list** (places, bookmarks, history, tools) that the user opens, taps once
 * and leaves. A pushed route would cost a navigation transition, a back press and the
 * window state around it, to end up at the same folder.
 *
 * Contract:
 *  - the scrim is a real dismiss target (tap outside closes) and the close button is
 *    always named, so the drawer is never a trap;
 *  - the panel is scrollable on its own, so a long history cannot push the tools off
 *    the bottom of the screen;
 *  - it renders nothing at all when [open] is false, so a caller can keep it mounted
 *    unconditionally without paying for it.
 */
@Composable
fun MaxDrawer(
    open: Boolean,
    title: String,
    onClose: () -> Unit,
    closeDescription: String,
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.88f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(animationSpec = tween(MaxDuration.quick)),
            exit = fadeOut(animationSpec = tween(MaxDuration.quick)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose,
                    )
            )
        }

        AnimatedVisibility(
            visible = open,
            enter = slideInHorizontally(animationSpec = tween(MaxDuration.standard)) { width -> -width },
            exit = slideOutHorizontally(animationSpec = tween(MaxDuration.standard)) { width -> -width },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(widthFraction),
                shape = RoundedCornerShape(topEnd = MaxRadius.sheet, bottomEnd = MaxRadius.sheet),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = MaxSpace.lg, end = MaxSpace.sm, top = MaxSpace.sm, bottom = MaxSpace.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = closeDescription,
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaxAlpha.border),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = MaxSpace.lg,
                                end = MaxSpace.lg,
                                top = MaxSpace.lg,
                                bottom = MaxSpace.pageBottom,
                            ),
                        content = content,
                    )
                }
            }
        }
    }
}
