package nd.max.ui.navigation

import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import nd.max.ui.component.MaxMotion
import nd.max.ui.component.maxPressMotion

/**
 * Bottom-bar / nav-rail item backed by a [MaxDestination] route.
 * Kept as a thin data holder so the pill composable stays destination-agnostic.
 */
data class NavItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val gradientColors: List<Color> = listOf(Color.Transparent, Color.Transparent),
)

/**
 * Pill-style navigation rail for wide layouts (>= 840dp).
 * Mirrors [BottomNavBar]; the two must stay visually identical.
 */
@Composable
fun NavigationRailBar(
    items: List<NavItem>,
    selectedRoute: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    isBlurEnabled: Boolean = false,
    hazeState: HazeState? = null,
) {
    val navigationShape = RoundedCornerShape(28.dp)

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(104.dp)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 12.dp, top = 16.dp, bottom = 16.dp)
            .clip(navigationShape)
            .then(
                if (isBlurEnabled && hazeState != null) {
                    Modifier.hazeEffect(state = hazeState) {
                        blurEffect { blurRadius = 24.dp }
                    }
                } else Modifier
            ),
        shape = navigationShape,
        color = if (isBlurEnabled) {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        shadowElevation = if (isBlurEnabled) 0.dp else 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            items.forEach { item ->
                val isSelected = selectedRoute == item.route
                NavPill(
                    item = item,
                    isSelected = isSelected,
                    isBlurEnabled = isBlurEnabled,
                    onClick = { onItemSelected(item.route) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                )
            }
        }
    }
}

/**
 * Floating pill bottom bar for compact layouts.
 * Exactly the four primary destinations (ADR-03).
 */
@Composable
fun BottomNavBar(
    items: List<NavItem>,
    selectedRoute: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    isBlurEnabled: Boolean = false,
    hazeState: HazeState? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .then(
                    if (isBlurEnabled && hazeState != null) {
                        Modifier.hazeEffect(state = hazeState) {
                            blurEffect {
                                blurRadius = 24.dp
                            }
                        }
                    } else Modifier
                ),
            shape = RoundedCornerShape(28.dp),
            color = if (isBlurEnabled) {
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            shadowElevation = if (isBlurEnabled) 0.dp else 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val isSelected = selectedRoute == item.route
                    NavPill(
                        item = item,
                        isSelected = isSelected,
                        isBlurEnabled = isBlurEnabled,
                        onClick = { onItemSelected(item.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavPill(
    item: NavItem,
    isSelected: Boolean,
    isBlurEnabled: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (isSelected) colors.primaryContainer else Color.Transparent,
        tween(MaxMotion.standard), label = "studioNavBackground",
    )
    val foreground by animateColorAsState(
        if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        tween(MaxMotion.fast), label = "studioNavForeground",
    )
    val lift by animateFloatAsState(
        if (isSelected) 1.08f else 1f,
        MaxMotion.controlSpring, label = "studioNavIcon",
    )
    Column(
        modifier = modifier
            .maxPressMotion(interactionSource)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    if (!isSelected) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                },
            )
            .heightIn(min = 64.dp)
            .padding(horizontal = 3.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(23.dp).scale(lift),
        )
        Text(
            text = stringResource(item.labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = foreground,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
