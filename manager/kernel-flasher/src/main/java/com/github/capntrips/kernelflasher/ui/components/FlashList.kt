package com.github.capntrips.kernelflasher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.capntrips.kernelflasher.R
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.hazeEffect

// Matches the terminal green used by the Horizon engine's console, so both
// flashing engines in this app read as the same "live terminal" surface.
private val TerminalGreen = Color(0xFF00E676)

/**
 * A themed terminal card for live flash/restore/backup output, with an
 * optional running/success/failure status banner above it. Shared by every
 * "operation in progress" screen in this module (AK3 flash, partition
 * image flash, backup, restore) so they all read as one consistent surface.
 */
@ExperimentalUnitApi
@Composable
fun ColumnScope.FlashList(
    cardTitle: String,
    output: List<String>,
    isRefreshing: Boolean = false,
    status: Boolean? = null,
    glassTheme: FlasherGlassTheme = FlasherGlassTheme(),
    content: @Composable ColumnScope.() -> Unit
) {
    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    val titleColor = glassTheme.primaryColor ?: MaterialTheme.colorScheme.primary

    // Auto scroll logic
    LaunchedEffect(output.size) {
        if (!isDragged && output.isNotEmpty()) {
            listState.animateScrollToItem(output.size - 1)
        }
    }

    Text(
        text = cardTitle,
        style = MaterialTheme.typography.titleSmall,
        color = titleColor,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
    )

    // --- STATUS BANNER: running / success / failure ---
    if (isRefreshing || status != null) {
        val tint = when {
            isRefreshing -> titleColor
            status == true -> TerminalGreen
            else -> MaterialTheme.colorScheme.error
        }
        val label = when {
            isRefreshing -> stringResource(R.string.running_label)
            status == true -> stringResource(R.string.completed_label)
            else -> stringResource(R.string.failed_label)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(tint.copy(alpha = 0.12f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = tint, strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = if (status == true) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = tint)
        }
        Spacer(Modifier.height(12.dp))
    }

    // --- TERMINAL ---
    val isGlass = glassTheme.isGlassActive && glassTheme.hazeState != null
    val terminalShape = RoundedCornerShape(20.dp)
    val terminalModifier = if (isGlass) {
        Modifier
            .clip(terminalShape)
            .hazeEffect(
                state = glassTheme.hazeState!!,
                style = HazeBlurStyle(
                    backgroundColor = Color(0xFF0A0A0A).copy(alpha = 0.55f),
                    blurRadius = 24.dp,
                    noiseFactor = 0.1f,
                    colorEffects = emptyList()
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.1f), terminalShape)
    } else {
        Modifier
            .clip(terminalShape)
            .background(Color(0xFF1E1E1E).copy(alpha = 0.9f))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 380.dp)
            .then(terminalModifier)
            .padding(16.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.terminal_output),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(Modifier.height(8.dp))
            if (output.isEmpty()) {
                Text(
                    text = stringResource(R.string.waiting_for_output),
                    color = TerminalGreen.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollbar(listState)
                ) {
                    items(output) { message ->
                        Text(
                            text = message,
                            color = TerminalGreen,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    content()
}

fun Modifier.scrollbar(
    state: LazyListState,
    width: Dp = 6.dp,
    color: Color = Color.White.copy(alpha = 0.4f)
): Modifier = composed {
    var visibleItemsCountChanged = false
    var visibleItemsCount by remember { mutableIntStateOf(state.layoutInfo.visibleItemsInfo.size) }
    if (visibleItemsCount != state.layoutInfo.visibleItemsInfo.size) {
        visibleItemsCountChanged = true
        visibleItemsCount = state.layoutInfo.visibleItemsInfo.size
    }

    val hidden = state.layoutInfo.visibleItemsInfo.size == state.layoutInfo.totalItemsCount
    val targetAlpha = if (!hidden && (state.isScrollInProgress || visibleItemsCountChanged)) 0.5f else 0f
    val delay = if (!hidden && (state.isScrollInProgress || visibleItemsCountChanged)) 0 else 250
    val duration = if (hidden || visibleItemsCountChanged) 0 else if (state.isScrollInProgress) 150 else 500

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(delayMillis = delay, durationMillis = duration)
    )

    drawWithContent {
        drawContent()
        val firstVisibleElementIndex = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index
        if (alpha > 0.0f && firstVisibleElementIndex != null && state.layoutInfo.totalItemsCount > 0) {
            val elementHeight = this.size.height / state.layoutInfo.totalItemsCount
            val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
            val scrollbarHeight = state.layoutInfo.visibleItemsInfo.size * elementHeight

            drawRoundRect(
                color = color,
                topLeft = Offset(this.size.width - width.toPx(), scrollbarOffsetY),
                size = Size(width.toPx(), scrollbarHeight),
                cornerRadius = CornerRadius(width.toPx(), width.toPx()),
                alpha = alpha
            )
        }
    }
}
