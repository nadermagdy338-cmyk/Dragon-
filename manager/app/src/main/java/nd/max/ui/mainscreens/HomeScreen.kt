/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.mainscreens
import nd.max.ui.design.floatingBottomBarPadding
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.MaxNavActions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nd.max.core.maxai.MaxAiState
import nd.max.core.maxai.ProfileRequestState
import nd.max.ui.component.RebootBottomSheet
import nd.max.ui.component.RootAppDialog
import nd.max.ui.component.maxAdaptiveContentWidth
import nd.max.ui.util.HomeDeckStore
import nd.max.ui.util.ScreenUsageStore
import nd.max.ui.util.fallbackDeviceName
import nd.max.ui.util.getRealDeviceName
import nd.max.ui.viewmodel.DashboardState
import nd.max.ui.viewmodel.HomeDashboardViewModel
import nd.max.ui.viewmodel.HomeUiState
import nd.max.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    navController: NavHostController,
    isVisible: Boolean = true,
    homeViewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    dashboardViewModel: HomeDashboardViewModel = viewModel(),
    maxAiViewModel: nd.max.ui.viewmodel.MaxAiViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = LocalContext.current
    val navActions = MaxNavActions(navController)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val ui by homeViewModel.uiState.collectAsStateWithLifecycle()
    val dashboard by dashboardViewModel.dashboardState.collectAsStateWithLifecycle()
    val maxAi by maxAiViewModel.state.collectAsStateWithLifecycle()
    val profileRequest by maxAiViewModel.profileRequest.collectAsStateWithLifecycle()
    var showReboot by remember { mutableStateOf(false) }
    // **والاسم لا يُقرأ في التركيب (عطب سرعة مُبلَّغ عنه: «جلب المعلومات في الشاشة الرئسية
    // بطيء، انتظر دقيقة»):** كان `remember { getRealDeviceName(context) }` — و`remember`
    // يُنفَّذ خلال التركيب على **الخيط الرئيسي**، والدالّة تنسخ `devices.db` من الأصول
    // (**٤٫٢ ميغابايت** — مقيس) ثم تفتح SQLite وتستعلم بـ`LIKE` (مسح كامل). فأوّل إطار في
    // الرئيسية كان ينتظر نسخة ٤ ميغابايت وقرصًا واستعلامًا.
    // ⇒ ما لا يكلّف قراءة واحدة (`Build`) يُعرض أوّلًا، والاسم التسويقي يصل بعدها على خيط
    // خلفيّ. ولا «مؤقّت تحميل» هنا: الاسم الافتراضي **اسم حقيقيّ للجهاز نفسه** لا بديل مُصنَّع.
    var deviceName by remember(context) { mutableStateOf(fallbackDeviceName()) }
    LaunchedEffect(context) {
        val resolved = withContext(Dispatchers.IO) {
            runCatching { getRealDeviceName(context) }.getOrNull()
        }
        if (!resolved.isNullOrBlank()) deviceName = resolved
    }

    // **بطاقات «منصة التحكم» (أمر المالك، الجولة ٢٠٢):** ٤ إلى ٦ عناصر — تلقائيًّا بالأكثر
    // استعمالًا أو باختيار المستخدم، والوضع التلقائيّ هو الافتراضيّ. والقاعدة كلها في
    // `homeDeckSelection` (صافية ومقيسة على JVM).
    //
    // **والعدّاد (الجولة ٢٠٣) من السجلّ الواحد `ScreenUsageStore` لا من عدّاد خاصّ بالمنصة:**
    // ومنه تقرأ المنصة عبر `homeDeckUsage` كما يقرأ منه المُوجِّد، فلا يقول أحدهما للمستخدم
    // «الأكثر استعمالًا» ويقول الآخر غيره.
    //
    // **ويُقرأ عند كل ظهور للرئيسية لا مرّةً واحدة عند التركيب:** من فتح شاشةً ثم عاد يرى
    // ترتيبًا يضمّها، ومن فتح ورقة الإعداد يرى الأثر فيه فورًا. و`isVisible` هو مفتاح القراءة.
    val deckStore = remember(context) { HomeDeckStore.of(context) }
    val usageStore = remember(context) { ScreenUsageStore.of(context) }
    var deckMode by remember { mutableStateOf(deckStore.mode) }
    var deckManual by remember { mutableStateOf(deckStore.manualKeys) }
    var showDeckSettings by remember { mutableStateOf(false) }
    val deckUsage = remember(isVisible, deckMode, deckManual, showDeckSettings) {
        homeDeckUsage(usageStore.counts())
    }
    val deckEntries = homeDeckSelection(deckMode, deckManual, deckUsage)

    LifecycleStartEffect(dashboardViewModel, isVisible) {
        if (isVisible && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            dashboardViewModel.setPollingActive(true)
        }
        onStopOrDispose { dashboardViewModel.setPollingActive(false) }
    }

    // ولا مضيف `Snackbar` هنا: كاتبه الوحيد كان ردّ «ملف الأداء» المُزال، ومضيف بلا رسالة
    // لا يرسم شيئًا — فتِرْكُه كان يُبقي أسلاكًا تُشبه قدرةً وهي ليست كذلك.
    Scaffold(containerColor = Color.Transparent) { padding ->
        HomeDashboardContent(
            ui = ui,
            dashboard = dashboard,
            maxAi = maxAi,
            profileRequest = profileRequest,
            deviceName = deviceName,
            deckEntries = deckEntries,
            onOpenDeck = { entry -> navActions.navigateRoute(entry.destination.route) },
            onConfigureDeck = { showDeckSettings = true },
            topPadding = padding.calculateTopPadding(),
            // المسار يمرّ ببوّابة التنقّل نفسها التي تمرّ بها بقية الشاشات، فلا
            // يُنقل نمط `?pkg={pkg}` خامًّا إلى الـNavigator.
            onNavigate = navActions::navigateRoute,
            onReboot = { showReboot = true },
            onSettings = { navActions.navigateTo(MaxDestination.Settings) },
            onAiRetry = maxAiViewModel::refresh
        )
    }

    RootAppDialog {
        RebootBottomSheet(
            show = showReboot,
            onDismiss = { showReboot = false },
            onReboot = homeViewModel::rebootDevice
        )
        HomeDeckSettingsSheet(
            visible = showDeckSettings,
            mode = deckMode,
            manualKeys = deckManual,
            usage = deckUsage,
            onModeChange = { next ->
                deckMode = next
                deckStore.mode = next
            },
            onManualChange = { next ->
                deckManual = next
                deckStore.manualKeys = next
            },
            onDismiss = { showDeckSettings = false },
        )
    }
    // وحوار «ملف الأداء» أُزيل بأمر المالك مع بطاقته: تبديل الملف صار في Max AI وحده
    // (`ProfilesSection`)، وهو سطحه المُدقَّق — فيه أثر مُسجَّل وحالة «مُطبَّق» مقروءة من النظام.
}

@Composable
fun HomeDashboardContent(
    ui: HomeUiState,
    dashboard: DashboardState,
    maxAi: MaxAiState,
    profileRequest: ProfileRequestState,
    deviceName: String,
    deckEntries: List<HomeDeckEntry>,
    onOpenDeck: (HomeDeckEntry) -> Unit,
    onConfigureDeck: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onNavigate: (String) -> Unit,
    onReboot: () -> Unit,
    onSettings: () -> Unit,
    onAiRetry: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val backdrop = remember(colors.background, colors.primary, colors.tertiary) {
        Brush.radialGradient(
            listOf(colors.primary.copy(alpha = .14f), colors.tertiary.copy(alpha = .05f), Color.Transparent),
            center = Offset(220f, 80f), radius = 900f
        )
    }

    Box(Modifier.fillMaxSize().background(colors.background).background(backdrop)) {
        TechnicalBackdrop()
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.maxAdaptiveContentWidth(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = topPadding + 10.dp,
                // This page renders full-bleed to the bottom edge and the floating bar is
                // drawn on top of it, so the card has to reserve the bar's real height
                // itself — otherwise the dashboard's last block stays hidden underneath the
                // pill (the bar no longer insets the navigation host, see MainActivity).
                // Without a bar (navigation-rail layouts) only the gesture bar needs clearance.
                bottom = floatingBottomBarPadding(
                    16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
            )
        ) {
            item {
                LegendaryHomeDashboard(
                    ui = ui,
                    dashboard = dashboard,
                    maxAi = maxAi,
                    profileRequest = profileRequest,
                    deviceName = deviceName,
                    // ولا مسار GPU هنا: صفّ «الرسوم/المعالج» نُقل إلى شاشاته المالكة (خطة storyboard-home
                    // المرحلة 4)، والوصول إلى GPU من الـdeck ← Control ← محور الرسوم. وحقل المسار كان
                    // يُمرَّر إلى صفّ محذوف فلم يبقَ له مستهلك — وحقل بلا مستهلك يبدو كأنه يُوصّل شيئًا.
                    onNavigate = onNavigate,
                    onReboot = onReboot,
                    onSettings = onSettings,
                    onAiRetry = onAiRetry,
                    deckEntries = deckEntries,
                    onOpenDeck = onOpenDeck,
                    onConfigureDeck = onConfigureDeck
                )
            }
        }
        EdgeScrim(colors.background, true, Modifier.align(Alignment.TopCenter))
        // Only the top edge is scrimmed: the dashboard scrolls underneath the floating
        // navigation bar now, and a bottom fade would erase the very content that is
        // supposed to be seen travelling through the bar's translucent surface.
    }
}

@Composable
private fun EdgeScrim(base: Color, top: Boolean, modifier: Modifier = Modifier) {
    val stops = if (top) listOf(base, base.copy(alpha = .72f), Color.Transparent)
    else listOf(Color.Transparent, base.copy(alpha = .72f), base)
    Box(
        modifier
            .fillMaxWidth()
            .height(if (top) 26.dp else 34.dp)
            .background(Brush.verticalGradient(stops))
    )
}

@Composable
private fun TechnicalBackdrop() {
    val line = MaterialTheme.colorScheme.primary.copy(alpha = .035f)
    Canvas(Modifier.fillMaxSize()) {
        val step = 44.dp.toPx()
        var x = 0f
        while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += step }
        drawCircle(line.copy(alpha = .08f), radius = size.minDimension * .38f, center = Offset(size.width * .84f, size.height * .1f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
    }
}

/**
 * مسار شاشة الـGPU حين يعرف الجهاز شريحته، وإلا `null` فيسقط النداء إلى السجلّ.
 *
 * وكانت السلسلة `"gpustudio"` مكتوبة بيد هنا — مسار حرفي خارج السجلّ، وهو ما
 * يمنعه ADR-02: تغيير المسار في `MaxDestinations` كان يترك هذا المدخل يشير إلى
 * مسار غير مسجّل، وهي نفس فصيلة العطب التي جعلت `Max Backup` يُفتح فارغًا.
 */
internal fun gpuRouteForChipset(chipset: String): String? =
    chipset.takeIf(String::isNotBlank)?.let { MaxDestination.GpuStudio.launchRoute }

/*
 * وكان هنا `gpuFamilyForChipset` **الثاني** لقاعدة أسرة الرسوم — بلا مستدعٍ في الشجرة
 * كلها (قيس: صفر)، والقاعدة الحيّة صارت `gpuFamilyOf` في `DeviceInfoModel` **ومقيسة**
 * باختبار (`Adreno` لكوالكوم، `Mali` لميدياتك، و`Exynos` غير معروفة عن قصد). فقاعدتان
 * لنفس الفكرة تفترقان يومًا؛ فبقيت المقيسة وحُذفت الميتة (تكملة ١٩٤).
 */
