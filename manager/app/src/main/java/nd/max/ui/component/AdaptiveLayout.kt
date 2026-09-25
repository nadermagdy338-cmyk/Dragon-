/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Keeps MaxManager intentionally compact on tablets, foldables and landscape
 * displays instead of stretching every control to the full viewport.
 * Phone layouts are unchanged because the viewport is already below the cap.
 */
fun Modifier.maxAdaptiveContentWidth() =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 920.dp).fillMaxWidth()

/** A narrower reading/control column for preference-heavy screens. */
fun Modifier.maxAdaptiveControlWidth() =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 760.dp).fillMaxWidth()
