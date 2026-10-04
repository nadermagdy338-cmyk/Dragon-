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

/**
 * Shared "chrome" for every screen that isn't Home: one top bar, one section
 * label, used everywhere instead of the twenty-odd near-identical copies that
 * used to live one per screen (same gradient scrim, same LargeFlexibleTopAppBar,
 * same back arrow — only the title string ever changed). Consolidating them
 * here means a single place to evolve the look, and a consistent per-category
 * accent color instead of every icon in the app reading as the same undifferentiated
 * blue chip.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSectionSpec
import nd.max.ui.design.MaxSpace
import nd.max.R

/**
 * An opaque toolbar surface keeps scrolling content from reducing title contrast.
 */
@Composable
fun MaxManagerTopBarScrim(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colorScheme.surface)
            .drawWithContent {
                drawContent()
                drawLine(
                    color = colorScheme.outlineVariant.copy(alpha = 0.45f),
                    start = androidx.compose.ui.geometry.Offset(0f, size.height),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            },
        content = content
    )
}

/**
 * The screen's category color (tertiary/secondary/primary), provided once at
 * the top of each screen and read by every body-level icon — [LeadingIcon],
 * [SmallLeadingIcon], etc. — so a screen's whole icon set matches its top bar
 * glyph instead of every list icon defaulting to the same flat primary tint.
 * `null` (the default outside any screen) means "no override, use primary".
 */
val LocalScreenAccent = compositionLocalOf<Color?> { null }

/**
 * Wraps [content] so every themed icon inside it picks up [accent] as its
 * default color via [LocalScreenAccent]. Call this once per screen, around
 * the same Scaffold whose top bar receives the identical accent value.
 */
@Composable
fun ScreenAccentProvider(accent: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalScreenAccent provides accent, content = content)
}

/**
 * A small glowing icon chip that sits next to a screen's title, giving each
 * destination a color identity (tertiary for hardware/performance, secondary
 * for display/apps, primary for appearance/meta) instead of the single flat
 * primary tint every leading icon in the app used to share.
 *
 * v2 adds a hairline border and a soft outer halo so the glyph reads as a
 * back-lit indicator lamp on the screen's instrument panel.
 */
@Composable
fun ScreenAccentGlyph(
    icon: ImageVector,
    accent: Color,
    size: androidx.compose.ui.unit.Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(MaxRadius.inset)
    Box(
        modifier = modifier
            .size(size + 10.dp)
            .clip(shape)
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.20f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size * 0.65f)
        )
    }
}

/**
 * The one top bar every sub-screen (everything except Home) now renders
 * through: back arrow, scrim, large flexible title — plus an accent glyph
 * that used to be missing entirely. Screens with extra actions (search,
 * shortcuts) pass them through `actions`; screens with nothing special just
 * omit it.
 *
 * `subtitle` is the screen's description and renders as a paragraph under
 * the bar, aligned to the reading direction (see the comment inside), not
 * as a second title line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaxManagerSubScreenTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    accentIcon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    actions: @Composable RowScope.() -> Unit = {}
) {
    var titleVisible by remember { mutableStateOf(false) }
    LaunchedEffect(title) {
        titleVisible = false
        titleVisible = true
    }

    // Tied 1:1 to the same collapsedFraction Material3 reads internally to shrink/reposition
    // the title row. A separate tween() here would chase a constantly-moving target and drift
    // out of sync with M3's own (un-eased) transform, leaving the icon visibly stranded beside
    // the back arrow for a moment before it finally caught up and popped away.
    val collapsedFraction = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
    val accentIconAlpha = 1f - collapsedFraction
    val accentIconSlotWidth = 50.dp * accentIconAlpha
    val accentIconSpacerWidth = 12.dp * accentIconAlpha

    // The bar and the screen's description are one opaque header column. The
    // description used to be a one-line subtitle inside the title row, so any
    // screen whose description is a real sentence (Core Grid, ZRAM, Doze…) had
    // it ellipsized, and in RTL the reserved line shoved the title around.
    // It now reads as a paragraph under the bar, aligned to the start edge
    // (right in RTL, left in LTR) rather than centred, separated from the bar
    // by the scrim's own hairline — which fixes every screen from this one place.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        MaxManagerTopBarScrim {
            MediumTopAppBar(
                title = {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = titleVisible,
                        enter = fadeIn(tween(260)) + scaleIn(initialScale = 0.96f, animationSpec = tween(260)),
                        label = "subScreenTitleEnter"
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (accentIcon != null) {
                                Box(
                                    modifier = Modifier
                                        .width(accentIconSlotWidth)
                                        .clipToBounds()
                                ) {
                                    ScreenAccentGlyph(
                                        icon = accentIcon,
                                        accent = accent,
                                        modifier = Modifier.graphicsLayer {
                                            alpha = accentIconAlpha
                                            scaleX = 0.94f + (0.06f * accentIconAlpha)
                                            scaleY = 0.94f + (0.06f * accentIconAlpha)
                                        }
                                    )
                                }
                                Spacer(Modifier.width(accentIconSpacerWidth))
                            }
                            Text(
                                text = title,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 4.dp).background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(MaxRadius.inset)
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                scrollBehavior = scrollBehavior,
                // Consume the real system status-bar inset at the app-bar level.
                // This prevents large titles/actions from sliding underneath the phone
                // status icons while keeping the scrim itself edge-to-edge.
                windowInsets = WindowInsets.statusBars
            )
        }
        if (!subtitle.isNullOrBlank()) {
            // The description wraps to as many lines as it needs: it is the one
            // piece of copy that explains the screen, so truncating it would
            // hide exactly the sentence the user came to read. TextAlign.Start
            // resolves to the reading direction (right in RTL/Arabic, left in
            // LTR) instead of a fixed side, so the insight mark and the first
            // line of text always sit together at the same edge.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp, bottom = 14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp).padding(top = 1.dp)
                )
                Text(
                    text = subtitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

/**
 * The app's one recurring section-label motif — a short trace lead-in echoing
 * a routed circuit trace terminating at a pad — now shared by every screen
 * instead of being redeclared per-file as a plain, colorless label. `accent`
 * lets each screen tint its own trace with its category color so the label
 * visually belongs to the screen it's on.
 */
@Composable
fun MaxManagerInsight(
    text: String,
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(19.dp).padding(top = 1.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun MaxManagerSectionTitle(text: String, accent: Color = MaterialTheme.colorScheme.primary) {
    var visible by remember(text) { mutableStateOf(false) }
    LaunchedEffect(text) {
        visible = false
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(240)) + scaleIn(initialScale = 0.98f, animationSpec = tween(240)),
        label = "sectionTitleEnter"
    ) {
        MaxSectionHeader(
            title = text,
            accent = accent,
            // الفراغان من عقد القسم لا أرقامًا محلّية: هما ما يجعل تباعد قسم-لقسم واحدًا
            // في كل شاشة، والتغيير يجري من مكان واحد.
            modifier = Modifier.padding(
                start = MaxSpace.xs,
                end = MaxSpace.xs,
                top = MaxSectionSpec.spaceBefore,
                bottom = MaxSectionSpec.spaceAfter,
            )
        )
    }
}
