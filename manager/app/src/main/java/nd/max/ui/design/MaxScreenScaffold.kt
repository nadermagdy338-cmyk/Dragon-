/*
 * MaxManager Design Language — page shell.
 *
 * Every sub-screen previously built its own Scaffold, its own paddings, its own
 * bottom spacing and its own idea of where a warning goes. That is why the app
 * felt like 40 separate apps. This shell owns page structure once:
 *
 *   top bar (shared) → blocking condition OR [banner + sections]
 *
 * It deliberately reuses the existing MaxManagerSubScreenTopBar and
 * maxAdaptiveContentWidth instead of inventing parallel chrome, so screens can
 * migrate incrementally without two competing shells existing at the same time.
 */
package nd.max.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nd.max.ui.component.MaxManagerSubScreenTopBar
import nd.max.ui.component.MaxSnackbarHost
import nd.max.ui.component.maxAdaptiveContentWidth
import nd.max.ui.component.neuralPageBackdrop

/**
 * Rendered height of the floating bottom navigation pill, published by the app
 * shell (MainActivity) and consumed by every scrollable page body.
 *
 * The shell draws the bar *over* the navigation host instead of reserving layout
 * space for it, so page content keeps scrolling underneath the bar's blur — that
 * motion through a translucent surface is what makes the bar read as floating
 * instead of as a slab bolted to the bottom edge. The trade-off is that nothing
 * reserves space automatically anymore, so a page body must add this height to
 * its bottom content padding or its last row ends up hidden behind the pill.
 *
 * Zero means "no floating bar on this page" (navigation-rail layouts, onboarding
 * and sub-screens), in which case bodies keep their own bottom spacing.
 */
val LocalFloatingBottomBarHeight = compositionLocalOf { 0.dp }

/**
 * Bottom padding a scrolling body must reserve so the floating bar never covers
 * its last row: the bar's real measured height when it is on screen, otherwise
 * [fallback] (the page's own bottom spacing).
 */
@Composable
fun floatingBottomBarPadding(fallback: Dp): Dp {
    val barHeight = LocalFloatingBottomBarHeight.current
    return if (barHeight > 0.dp) barHeight else fallback
}

/**
 * Standard scrolling screen.
 *
 * @param condition when non-null the body is REPLACED by an explained state
 *        (root missing, unsupported SoC, read failed...). Use this only when
 *        nothing on the page is usable.
 * @param banner non-blocking, explained notice pinned above the content
 *        (applying, applied, stale data, partial support). The page stays
 *        usable, which is what makes a control screen feel stable.
 * @param content sections. Spacing between sections is owned by the shell, so
 *        screens must NOT add their own vertical padding between sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaxScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accentIcon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    condition: MaxCondition? = null,
    banner: MaxCondition? = null,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()
        .calculateBottomPadding()
    val bottomPadding = floatingBottomBarPadding(MaxSpace.pageBottom + navigationBarPadding)

    // إضاءة الصفحة قبل المحتوى: كل شاشة فرعية تُضاء من **حافة القراءة** كما تُضاء الرئيسية
    // (`neuralPageBackdrop`)، فلا يوجد سطح مسطّح في التطبيق لأن الصفحة نفسها بلا ضوء.
    Box(
        Modifier
            .fillMaxSize()
            .neuralPageBackdrop(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
    ) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            // A transparent container makes contentColorFor() return an unresolved colour, so
            // every Text/Icon that relies on the ambient content colour (a row headline, an
            // untinted caret) used to render black on the dark surface. State the theme's
            // on-surface colour explicitly — the same one Material would pick for this page.
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                MaxManagerSubScreenTopBar(
                    scrollBehavior = scrollBehavior,
                    title = title,
                    subtitle = subtitle,
                    onBack = onBack,
                    accentIcon = accentIcon,
                    accent = accent,
                    actions = actions
                )
            },
            snackbarHost = {
                if (snackbarHostState != null) MaxSnackbarHost(snackbarHostState)
            },
            floatingActionButton = { floatingAction?.invoke() }
        ) { scaffoldPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .maxAdaptiveContentWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MaxSpace.gutter)
                    .padding(bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.section)
            ) {
                if (condition != null) {
                    MaxConditionPanel(condition)
                } else {
                    banner?.let { MaxConditionNotice(it, modifier = Modifier.fillMaxWidth()) }
                    content()
                }
            }
        }
    }
}

/**
 * List screen variant.
 *
 * Long, data-heavy screens (apps, processes, logs, thermal zones) must stay
 * lazy: building 300 rows eagerly inside a scrolling Column is the main reason
 * the old list screens dropped frames while telemetry was updating.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaxListScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accentIcon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    condition: MaxCondition? = null,
    banner: MaxCondition? = null,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingAction: (@Composable () -> Unit)? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()
        .calculateBottomPadding()
    val bottomPadding = floatingBottomBarPadding(MaxSpace.pageBottom + navigationBarPadding)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        // A transparent container makes contentColorFor() return an unresolved colour, so
        // every Text/Icon that relies on the ambient content colour (a row headline, an
        // untinted caret) used to render black on the dark surface. State the theme's
        // on-surface colour explicitly — the same one Material would pick for this page.
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            MaxManagerSubScreenTopBar(
                scrollBehavior = scrollBehavior,
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                accentIcon = accentIcon,
                accent = accent,
                actions = actions
            )
        },
        snackbarHost = {
            if (snackbarHostState != null) MaxSnackbarHost(snackbarHostState)
        },
        floatingActionButton = { floatingAction?.invoke() }
    ) { scaffoldPadding ->
        if (condition != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // إضاءة الصفحة ثابتة أثناء التمرير: ترسم على حاوية العرض لا على المحتوى
                    // المتدحرج، فالشاشة مُضاءة من حافة القراءة كما تُضاء الرئيسية.
                    .neuralPageBackdrop(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                    .padding(scaffoldPadding)
                    .maxAdaptiveContentWidth()
                    .padding(horizontal = MaxSpace.gutter)
            ) {
                MaxConditionPanel(condition)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .neuralPageBackdrop(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                    .padding(scaffoldPadding)
                    .maxAdaptiveContentWidth(),
                contentPadding = PaddingValues(
                    start = MaxSpace.gutter,
                    end = MaxSpace.gutter,
                    bottom = bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(MaxSpace.row)
            ) {
                if (banner != null) {
                    item(key = "max_banner") {
                        MaxConditionNotice(banner, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (header != null) {
                    item(key = "max_header") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(MaxSpace.md),
                            content = header
                        )
                    }
                }
                content()
            }
        }
    }
}
