/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/** Small, shared semantics helpers so interactive surfaces remain discoverable
 * with TalkBack without forcing every screen to hand-roll semantics nodes. */
fun Modifier.maxButtonSemantics(label: String? = null): Modifier = semantics(mergeDescendants = true) {
    role = Role.Button
    if (!label.isNullOrBlank()) contentDescription = label
}

fun Modifier.maxHeadingSemantics(): Modifier = semantics { heading() }
