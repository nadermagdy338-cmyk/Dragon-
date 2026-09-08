/*
 * MaxManager semantic typography tokens.
 * Keep visual hierarchy expressed through named roles instead of ad-hoc sizes.
 */
package nd.max.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle

object MaxTextRole {
    /** Screen-level supporting copy and descriptions. */
    val description: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium

    /** Small supporting metadata such as version/build information. */
    val metadata: TextStyle
        @Composable get() = MaterialTheme.typography.bodySmall

    /** Compact status/category labels. */
    val status: TextStyle
        @Composable get() = MaterialTheme.typography.labelSmall

    /** Values that change frequently and must remain visually stable. */
    val liveValue: TextStyle
        @Composable get() = MonoValueStyleMedium
}
