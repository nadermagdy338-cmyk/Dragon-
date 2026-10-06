/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nd.max.ui.component.MaxManagerSubScreenTopBar
import nd.max.ui.component.MaxSnackbarHost
import nd.max.ui.component.maxAdaptiveContentWidth

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
 * الفراغ بين الشريط العلوي وأوّل عنصر في جسم الصفحة.
 *
 * **العطب الذي أُصلح — ووصفه المالك بدقة:** «البطاقة مُلتصقة بالبار العلوي في شاشة التحكّم
 * بالشحن». والسبب **بنويّ لا في تلك الشاشة**: `Scaffold` يُعطي `innerPadding.top` = ارتفاع
 * الشريط، والأجسام الثلاثة في هذا الملفّ كانت تُطبّقه **ولا تُضيف فوقه شيئًا** — فيبدأ أوّل
 * عنصر عند حافة الشريط بالضبط، صفر فاصل. فالوصف ينطبق على **كل صفحة تمرّ من هذا الملفّ**،
 * لا على الشاشة التي أُبلغ عنها وحدها؛ ولذلك موضع الإصلاح هنا لا هناك.
 *
 * **ولم أُخترع رقمًا:** الاسم موجود في الطبقة أصلًا — `MaxUiMetrics.screenTopPadding`
 * (= `MaxSpace.lg`) — وقيس فتبيّن أنّ له **صفر مستعمل**، فهو نيّة مسمّاة لم تُطبَّق.
 *
 * **والتحقّق من عدم التضاعف:** قوبل الجرد كلّه فلم تُوجد شاشة تُعوّض الفراغ بنفسها
 * (لا `padding(top = …)` على أيّ منادٍ لهذه الهياكل)، فالفراغ يُضاف **مرّة واحدة**.
 *
 * وحدّه المُعلن: هذا تغيير بصريّ عالميّ بمقدار **١٦dp أعلى كل صفحة** لم يُقَس على جهاز؛
 * لكنّه فراغ لا إعادة ترتيب، وهو بعينه ما طلبه المالك.
 */
private val BodyTopGap = MaxSpace.lg

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
    topBar: (@Composable (TopAppBarScrollBehavior) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
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
            // A screen may own its bar (a search-mode header, a bespoke layout) while the shell keeps
            // owning the Scaffold, insets, content colour and scroll connection. Default null keeps
            // the shared bar, so this slot changes nothing for screens that do not pass it.
            val customTopBar = topBar
            if (customTopBar != null) {
                customTopBar(scrollBehavior)
            } else {
                MaxManagerSubScreenTopBar(
                    scrollBehavior = scrollBehavior,
                    title = title,
                    subtitle = subtitle,
                    onBack = onBack,
                    accentIcon = accentIcon,
                    accent = accent,
                    actions = actions
                )
            }
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
                .padding(top = BodyTopGap)
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

/**
 * Split-layout screen shell: the same top bar, condition and snackbar handling as
 * [MaxScreen], but the body is a **fixed-height slot instead of a vertical scroll**.
 *
 * Why it has to exist rather than reusing [MaxScreen]: a `LazyColumn` nested inside a
 * vertically scrolling `Column` is measured with an infinite maximum height and throws.
 * So any page that owns more than one lazy list — the file manager's two panes are the
 * first — cannot use [MaxScreen] at all. The alternative, a screen-local `Scaffold`,
 * is exactly the drift this file was written to stop.
 *
 * The body fills the available height and arranges itself; the shell deliberately adds
 * no vertical spacing between children, because a split layout's gaps belong to the
 * panes, not to the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaxSplitScreen(
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
    topBar: (@Composable (TopAppBarScrollBehavior) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
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
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            // A screen may own its bar (a search-mode header, a bespoke layout) while the shell keeps
            // owning the Scaffold, insets, content colour and scroll connection. Default null keeps
            // the shared bar, so this slot changes nothing for screens that do not pass it.
            val customTopBar = topBar
            if (customTopBar != null) {
                customTopBar(scrollBehavior)
            } else {
                MaxManagerSubScreenTopBar(
                    scrollBehavior = scrollBehavior,
                    title = title,
                    subtitle = subtitle,
                    onBack = onBack,
                    accentIcon = accentIcon,
                    accent = accent,
                    actions = actions
                )
            }
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
                .padding(top = BodyTopGap)
                .padding(horizontal = MaxSpace.gutter)
                .padding(bottom = bottomPadding)
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

/**
 * List screen variant.
 *
 * Long, data-heavy screens (apps, processes, logs, thermal zones) must stay
 * lazy: building 300 rows eagerly inside a scrolling Column is the main reason
 * the old list screens dropped frames while telemetry was updating.
 *
 * @param floatingNotice an action outcome shown as a floating card pinned to the bottom of
 *        the viewport ([MaxFloatingNotice]) instead of a `banner` at the very top of a long
 *        list. A row at the top of a screen is where the user *is not* after pressing a
 *        button at the bottom — and it is scrolled away by the next flick. The card floats
 *        over space this shell reserves for it (its measured height is added to the list's
 *        bottom content padding), so it never covers a row; see [MaxFloatingNoticeHost].
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
    floatingNotice: MaxCondition? = null,
    snackbarHostState: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingAction: (@Composable () -> Unit)? = null,
    topBar: (@Composable (TopAppBarScrollBehavior) -> Unit)? = null,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    scrollState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()
        .calculateBottomPadding()
    val bottomPadding = floatingBottomBarPadding(MaxSpace.pageBottom + navigationBarPadding)
    // ارتفاع النافذة العائمة المقيس (`0.dp` حين لا نافذة). حالة واحدة تُقرأ في **موضعين** معًا:
    // جسم القائمة (ليحجز الفراغ الذي تطفو فوقه) وخانة الزرّ العائم (لئلّا يتراكب زرّان في
    // موضع واحد). وحالة واحدة لا حالتان، فلا تختلف الأرقام بين الموضعين يومًا.
    var floatingNoticeHeight by remember { mutableStateOf(0.dp) }
    // رفع النافذة عن أسفل منطقة المحتوى: شريط التنقّل العائم إن كان على هذه الصفحة (صفر على
    // الشاشات الفرعية) + فاصل صغير. يُحسب من [floatingBottomBarPadding] نفسه الذي تمرّ منه
    // أجسام الصفحات، فلا يصير للارتفاع الواحد حسابان.
    val floatingLift = floatingBottomBarPadding(0.dp) + MaxSpace.md

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        // A transparent container makes contentColorFor() return an unresolved colour, so
        // every Text/Icon that relies on the ambient content colour (a row headline, an
        // untinted caret) used to render black on the dark surface. State the theme's
        // on-surface colour explicitly — the same one Material would pick for this page.
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            // A screen may own its bar (a search-mode header, a bespoke layout) while the shell keeps
            // owning the Scaffold, insets, content colour and scroll connection. Default null keeps
            // the shared bar, so this slot changes nothing for screens that do not pass it.
            val customTopBar = topBar
            if (customTopBar != null) {
                customTopBar(scrollBehavior)
            } else {
                MaxManagerSubScreenTopBar(
                    scrollBehavior = scrollBehavior,
                    title = title,
                    subtitle = subtitle,
                    onBack = onBack,
                    accentIcon = accentIcon,
                    accent = accent,
                    actions = actions
                )
            }
        },
        snackbarHost = {
            if (snackbarHostState != null) MaxSnackbarHost(snackbarHostState)
        },
        floatingActionButton = {
            // العودة إلى أعلى القائمة — تُركَّب في خانة الزرّ العائم **فوق** الزرّ الذي تمرّره الشاشة
            // إن مرّرت واحدًا، فلا يُزاح صفّ ولا يُغطّى نصّ. وهي لا تُبنى أبدًا قبل عبور الحدّ،
            // فالشرط (`rememberListTopControl`) هو ما يجعلها معلومة لا زينة.
            //
            // **ولا تظهر وفي الشاشة نافذة عائمة:** كلتاهما تسكن أسفل النهاية، فظهورهما معًا
            // يغطّي أحدهما الآخر — وهو نقض الشرط نفسه (لا يُغطّى شيء). والشرط مربوط بارتفاع
            // النافذة المحجوز لا بحالتها اللحظية، فلا يعود الزرّ قبل اكتمال حركة خروجها.
            val scope = rememberCoroutineScope()
            ScrollToTopSlot(
                visible = floatingNoticeHeight == 0.dp && rememberListTopControl(scrollState),
                onTop = { scope.launch { scrollState.animateScrollToItem(0) } }
            ) { floatingAction?.invoke() }
        }
    ) { scaffoldPadding ->
        // **الحاوية التي تجعل «عائمة بلا تغطية» ممكنة:** جسم الصفحة كما كان تمامًا، والنافذة
        // تُركَّب **فوقه** في الـ`Box` نفسه — فهي لا تخرج مع التمرير (ليست داخل `LazyColumn`)
        // ولا تزيح صفًّا. والفراغ الذي تطفو فوقه محجوز لها أدناه في `contentPadding.bottom`
        // بمقدار ارتفاعها المقيس.
        Box(modifier = Modifier.fillMaxSize()) {
            if (condition != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding)
                        .padding(top = BodyTopGap)
                        .maxAdaptiveContentWidth()
                        .padding(horizontal = MaxSpace.gutter)
                ) {
                    MaxConditionPanel(condition)
                }
            } else {
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding)
                        .maxAdaptiveContentWidth(),
                    contentPadding = PaddingValues(
                        start = MaxSpace.gutter,
                        end = MaxSpace.gutter,
                        // أوّل عنصر كان يبدأ عند حافة الشريط بالضبط (صفر فاصل) — وهو عطب كلّ
                        // صفحة قائمة في التطبيق، لا شاشة واحدة.
                        top = BodyTopGap,
                        // الحشوة القائمة + النافذة العائمة ورفعها: فآخر عنصر ينتهي عند حافة
                        // النافذة العلوية بالضبط ويظلّ قابلًا للتمرير إلى ما فوقها.
                        bottom = bottomPadding + floatingNoticeHeight + floatingLift
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
            MaxFloatingNoticeHost(
                // حالة حاجبة (جذر مفقود · معالج غير مدعوم) تعني أن الشاشة كلها معطّلة ولا
                // إجراء وقع أصلًا — فلا تُركَّب معها نافذة إجراء.
                condition = if (condition == null) floatingNotice else null,
                bottomGap = navigationBarPadding + floatingLift,
                onReservedHeightChange = { floatingNoticeHeight = it }
            )
        }
    }
}

/**
 * Full-bleed screen shell: the same Scaffold ownership as [MaxScreen], but with **no shared top
 * bar, no page gutter and no content-width cap**. The body is a `BoxScope` that fills the window.
 *
 * Why it exists: the file manager is not a list or a form — it is a windowed workspace that paints
 * its own surface, manages its own status/navigation insets and carries no screen-level back action.
 * Forcing it through [MaxScreen]/[MaxListScreen]/[MaxSplitScreen] would strip the full-bleed layout
 * and add a gutter and a bar it does not have. This shell keeps the parts that *are* shared (one
 * scaffold, one container colour, one snackbar host) without pretending the chrome is the same.
 *
 * `contentWindowInsets` is zeroed on purpose: the content owns its own insets, so the shell must not
 * apply them a second time.
 */
@Composable
fun MaxFullScreen(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    snackbarHostState: SnackbarHostState? = null,
    floatingAction: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Scaffold(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            if (snackbarHostState != null) MaxSnackbarHost(snackbarHostState)
        },
        floatingActionButton = { floatingAction?.invoke() }
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            content = content
        )
    }
}
