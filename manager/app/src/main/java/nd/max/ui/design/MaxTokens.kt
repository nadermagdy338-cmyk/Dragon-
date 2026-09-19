/*
 * MaxManager Design Language — token layer.
 *
 * Why this file exists:
 * screens used to hard-code dp values, alphas and status colours inline, so the
 * same idea (a section gap, a warning, a disabled control) looked different on
 * every screen. Reworked surfaces must consume these tokens instead of
 * literals, which is what makes the app read as one product instead of a pile
 * of screens.
 *
 * Tokens and pure colour resolution only — no composable UI lives here.
 */
package nd.max.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 4dp-based spacing scale.
 *
 * `gutter`, `section`, `row` and `pageBottom` are the only values a screen body
 * should need; the raw steps exist for component internals.
 */
object MaxSpace {
    val hairline: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 28.dp

    /** Horizontal page gutter shared by every scrollable screen body. */
    val gutter: Dp = 20.dp

    /** Vertical gap between two sections of a screen. */
    val section: Dp = 28.dp

    /** Gap between sibling rows inside one section. */
    val row: Dp = 8.dp

    /** Inner padding of a grouped container. */
    val groupPadding: Dp = 4.dp

    /** Inner padding of a single row. */
    val rowPaddingHorizontal: Dp = 14.dp
    val rowPaddingVertical: Dp = 12.dp

    /** Breathing room so the bottom navigation never covers the last row. */
    val pageBottom: Dp = 40.dp

    /**
     * Safe minimum footprint of the floating pill bottom bar (pill height +
     * its own top/bottom margins), *excluding* the system navigation-bar
     * inset, which is layered on separately.
     *
     * Primary screens reserve at least this much space before the bar's real
     * pixel height is known. It must stay generous enough to cover a wrapped
     * two-line label (long Arabic strings) without the last row of content
     * ever sitting behind the bar — see [pageBottom], which adds further
     * clearance on top of this.
     */
    val bottomBarReserve: Dp = 112.dp
}

/**
 * Shape language: rows are calm (14dp), groups frame them (22dp), sheets and
 * dialogs are the softest (28dp). Pills are reserved for state, never for
 * primary actions — a pill in MaxManager means "this is a status".
 */
object MaxRadius {
    val control: Dp = 12.dp
    val row: Dp = 14.dp
    val group: Dp = 22.dp
    val sheet: Dp = 28.dp
    val pill: Dp = 100.dp
}

object MaxSize {
    /** Accessibility floor for anything clickable. Never go below this. */
    val minTouchTarget: Dp = 48.dp

    val iconGlyph: Dp = 20.dp
    val iconGlyphSmall: Dp = 16.dp

    /** Icon container inside a list row. */
    val rowIconContainer: Dp = 34.dp

    /** Icon container in headers and notices. */
    val iconContainer: Dp = 40.dp

    val hairlineBorder: Dp = 1.dp

    /**
     * Border width of the one element on a split page that owns the next action.
     *
     * Added for the file manager's two panes: with two identical columns the user has
     * no way to tell where an action will land, and a colour alone is not an answer for
     * anyone who cannot see it — so the active pane is also wider, not only tinted.
     */
    val activeRing: Dp = 2.dp

    /** Keeps long explanatory copy readable on tablets/landscape. */
    val readingMaxWidth: Dp = 560.dp

    /** Height of the inline history strip used by telemetry rows. */
    val sparklineHeight: Dp = 28.dp

    /**
     * Largest height a scrollable list may take inside a dialog.
     *
     * Added for the `AppOps` operation picker: a device exposes a couple of hundred
     * operation names, and a dialog that grows to fit them covers the screen it is
     * asking about. The list scrolls inside this cap instead, so the title and the
     * confirm button stay visible while choosing.
     */
    val dialogListMax: Dp = 360.dp

    /**
     * Height of a fractional usage bar (storage share, zone heat, cooling state).
     *
     * A token rather than a literal at each call site: the storage screen and the
     * thermal screen both draw these, and two bar heights for one idea is exactly the
     * drift this token layer exists to stop.
     */
    val barHeight: Dp = 8.dp
}

/**
 * Opacity vocabulary. Borders stay hairline-quiet; tone containers stay low so
 * numbers remain the brightest thing on screen.
 */
object MaxAlpha {
    const val border = 0.16f
    const val borderStrong = 0.28f
    const val toneContainer = 0.10f
    const val toneContainerStrong = 0.18f
    const val disabledContent = 0.38f
    const val supportingText = 0.80f
}

/**
 * Motion budget. Motion exists to explain a transition or confirm a state
 * change; anything longer than [deliberate] is decoration and is not allowed.
 */
object MaxDuration {
    const val instant = 90
    const val quick = 160
    const val standard = 240
    const val deliberate = 360
}

/**
 * Semantic tone.
 *
 * Tone is never the only carrier of meaning: every component that renders a
 * tone also renders an icon and a text label, so state survives colour
 * blindness, greyscale screenshots and high-contrast modes.
 */
enum class MaxTone {
    /** Ordinary information. */
    Neutral,

    /** The screen's own accent — "this is the thing you came here for". */
    Accent,

    /** Confirmed good: applied, verified, within limits. */
    Positive,

    /** Needs attention but still working: throttling, near a limit. */
    Caution,

    /** Failed, blocked or unsafe. */
    Critical,

    /** Off, unsupported or not owned by MaxManager. */
    Inactive
}

/*
 * Caution / Critical / Positive are intentionally NOT taken from the dynamic
 * colour scheme. Material You can turn `tertiary` into a pastel that reads as
 * decoration, and a thermal alert that looks decorative is a correctness bug in
 * a performance tool. These hues are fixed and contrast-checked against both
 * light and dark MaxManager surfaces; only Accent follows the user's theme.
 */
private val PositiveOnLight = Color(0xFF0B6B4F)
private val PositiveOnDark = Color(0xFF5FD9AC)
private val CautionOnLight = Color(0xFF8A5200)
private val CautionOnDark = Color(0xFFFFB86B)
private val CriticalOnLight = Color(0xFF9A1B1B)
private val CriticalOnDark = Color(0xFFFF9A90)

/** True when the active scheme is dark, derived from the surface itself. */
@Composable
fun maxIsDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** Foreground colour for a tone (text, icon, stroke). */
@Composable
fun MaxTone.content(): Color {
    val scheme = MaterialTheme.colorScheme
    val dark = maxIsDarkSurface()
    return when (this) {
        MaxTone.Neutral -> scheme.onSurfaceVariant
        MaxTone.Accent -> scheme.primary
        MaxTone.Positive -> if (dark) PositiveOnDark else PositiveOnLight
        MaxTone.Caution -> if (dark) CautionOnDark else CautionOnLight
        MaxTone.Critical -> if (dark) CriticalOnDark else CriticalOnLight
        MaxTone.Inactive -> scheme.outline
    }
}

/** Low-emphasis container behind a tone's content. */
@Composable
fun MaxTone.container(strong: Boolean = false): Color =
    content().copy(alpha = if (strong) MaxAlpha.toneContainerStrong else MaxAlpha.toneContainer)

/** Hairline border for a tone. */
@Composable
fun MaxTone.border(strong: Boolean = false): Color =
    content().copy(alpha = if (strong) MaxAlpha.borderStrong else MaxAlpha.border)

/**
 * How trustworthy a displayed number is.
 *
 * MaxManager is a measurement tool, so the UI must distinguish "this is live",
 * "this is the last good sample" and "this sensor does not exist on your
 * device". Formatting a stale or missing value as if it were live is treated as
 * a bug, not a cosmetic issue.
 */
enum class MaxDataTrust {
    /** Sampled within the expected interval. */
    Live,

    /** Real reading, but older than the expected interval. */
    Stale,

    /** Read once at startup / on demand; not a stream. */
    Snapshot,

    /** The source exists but returned nothing readable right now. */
    Unreadable,

    /** The source does not exist on this device or kernel. */
    Unsupported
}

@Immutable
data class MaxTrustVisual(val tone: MaxTone, val showsValue: Boolean)

fun MaxDataTrust.visual(): MaxTrustVisual = when (this) {
    MaxDataTrust.Live -> MaxTrustVisual(MaxTone.Accent, showsValue = true)
    MaxDataTrust.Stale -> MaxTrustVisual(MaxTone.Caution, showsValue = true)
    MaxDataTrust.Snapshot -> MaxTrustVisual(MaxTone.Neutral, showsValue = true)
    MaxDataTrust.Unreadable -> MaxTrustVisual(MaxTone.Caution, showsValue = false)
    MaxDataTrust.Unsupported -> MaxTrustVisual(MaxTone.Inactive, showsValue = false)
}

/**
 * Placeholder rendered instead of a number when there is nothing real to show.
 * Deliberately not "0", not "--%" and not an empty string: a zero would be read
 * as a measurement.
 */
const val MAX_VALUE_UNAVAILABLE = "—"

/** Tabular figure size used by metric readouts so digits never reflow. */
object MaxMetricType {
    val valueLarge: TextUnit = 34.sp
    val valueMedium: TextUnit = 22.sp
    val valueSmall: TextUnit = 16.sp
}
