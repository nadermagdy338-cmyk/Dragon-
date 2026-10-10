/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import android.util.LruCache
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.core.gamespace.GameApp
import nd.max.core.gamespace.GameLibraryAccess
import nd.max.core.gamespace.GameLobbyMeta
import nd.max.core.gamespace.GameLobbyMetaReader
import nd.max.core.gamespace.LobbyModel
import nd.max.core.gamespace.gameLibrary
import nd.max.core.platform.ForegroundAppResolver
import nd.max.service.GamePanelService
import nd.max.ui.component.LobbyCardPager
import nd.max.ui.component.LobbyGameCard
import nd.max.ui.component.LobbyGlowPad
import nd.max.ui.component.LobbyGridButton
import nd.max.ui.component.LobbyHexButton
import nd.max.ui.component.LobbyHexTone
import nd.max.ui.component.LobbyMenuItem
import nd.max.ui.component.LobbyMenuOverlay
import nd.max.ui.component.LobbyMoreButton
import nd.max.ui.component.LobbyPalette
import nd.max.ui.component.LobbyStageBackdrop
import nd.max.ui.component.LobbyTabStrip
import nd.max.ui.component.LobbyWindowEffect
import nd.max.ui.component.lobbyIntro
import nd.max.ui.component.lobbyLoop
import nd.max.ui.component.rememberLobbyAnimationsEnabled
import nd.max.ui.component.rememberLobbyIntro
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.navigation.openAppSettings
import nd.max.ui.util.AppConfig
import nd.max.ui.util.GamePanelPrefs
import nd.max.ui.util.customizedFieldCount
import nd.max.ui.viewmodel.AppSettingsViewModel
import nd.max.ui.viewmodel.GameSpaceViewModel

private val TopBarHeight = 56.dp
private val TabStripHeight = 52.dp
private val SideHexWidth = 136.dp
private val SideHexHeight = 64.dp
private val StartHexWidth = 208.dp
private val StartHexHeight = 68.dp
private val StartRaise = MaxSpace.sm
private val ActionHeight = StartHexHeight + StartRaise
private val MinCardHeight = 120.dp
private val MaxCardHeight = 260.dp

/** نسبة عرض البطاقة إلى ارتفاعها (نحو ١٫٦٧:١). */
private const val CARD_ASPECT = 1.67f

/** أكبر حصّة من عرض الشاشة تأخذها البطاقة المركزية، فتبقى للجارتين مساحة ظاهرة. */
private const val CARD_MAX_WIDTH_SHARE = 0.36f

/** قراءة حجم الحزمة وعمرها تتكرّر كلما دخلت بطاقة إلى الشاشة؛ التخزين يمنع إعادة قراءة الجهاز في كل تمرير. */
private val metaCache = LruCache<String, GameLobbyMeta>(64)

/**
 * لوبي الألعاب — **سطح لعب عرضيّ كامل الشاشة** على شكل لقطة «مكتبة الألعاب» المرجعية بتفاصيلها:
 * بطاقة مركزية بإطار نيون (وردي-أحمر ← أزرق) وتوهّج، وجارتان أضيق مائلتان بمنظور قويّ، شريط تبويب
 * علويّ معلَّق (الكل · المفضّلة) بنصّ أحمر للمحدَّد، أيقونة شبكة (إدارة الألعاب) يسارًا وثلاث نقاط
 * (تحديث · إغلاق) يمينًا، خلفية كحليّة بأجنحة مائلة وخلايا سداسية، وصفّ سفليّ من ثلاثة أزرار
 * سداسية: [ملف اللعبة | ابدأ | اللوحة الجانبية] — الأوسط أكبر وأحمر ومرفوع قليلًا.
 *
 * ### ما يقوم عليه التصميم
 *
 * - **المحتوى هو البطل:** حلقة مستمرّة **واحدة** هي تنفّس حافة البطاقة وتوهّج «ابدأ».
 * - **لا نصّ مقطوع:** عناوين البطاقات سطران، وبياناتها سطران، وأسماء الأزرار الثانوية سطران.
 * - **لون البطاقة من اللعبة نفسها:** يُستخرج من أيقونتها المثبَّتة؛ أمّا الخلفية فثابتة.
 *
 * ### ما حُذف عن قصد (أمر المالك: «حرفيًّا مثل الصورة»)
 *
 * شعار MAX والساعة وصفّ الجاهزية (بطارية · حرارة · ذاكرة) غير موجودة في اللقطة المرجعية فأُزيلت من
 * هذه الشاشة. قارئاتها (`rememberLobbyBattery` · `rememberLobbyMemory` · `rememberLobbyClock`) باقية
 * في `LobbyVisuals.kt` لإعادتها بلا إعادة كتابة. وزرّ الإغلاق صار في قائمة النقاط الثلاث (وإيماءة
 * الرجوع تعمل كما هي).
 *
 * ### ما لم يتغيّر (حدود معلنة)
 *
 * - لا كتابة عتاد من هذه الشاشة (ADR-11): التشغيل يمرّ بـ`GameLibraryAccess.launch`، والإعدادات
 *   بـ`AppSettingsViewModel.updateSetting`، ومفتاح اللوحة بـ`GamePanelPrefs` وخدمتها.
 * - لا أرقام مختلقة (ADR-07): البطاقة تعرض حجم الحزمة وعمر التثبيت المقروءين فعلًا ولا «وقت لعب».
 * - لا تبويب بلا شاشة خلفه: «الكل» و«المفضّلة» مرشِّحان حقيقيّان للقائمة نفسها.
 * - لا أصول منقولة: كل رسم بالكود، والصور هي أيقونات التطبيقات الحقيقية.
 *
 * **غير مُتحقَّق على جهاز:** القفل العرضي وإخفاء الأشرطة وقصّ الكاميرا (display cutout)، ومواضع
 * الأجنحة والخلايا مقابل المرجع، وانعكاس الـPager في RTL، وسلاسة ٦٠fps أثناء السحب.
 */
@Composable
fun GameLobbyScreen(
    navController: NavController,
    viewModel: GameSpaceViewModel = hiltViewModel(),
    settingsViewModel: AppSettingsViewModel = viewModel()
) {
    LobbyWindowEffect()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val library by viewModel.library.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var profileOpen by rememberSaveable { mutableStateOf(false) }
    var manageOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var launchFailed by remember { mutableStateOf(false) }
    var panelRevision by remember { mutableIntStateOf(0) }
    val panelPrefs = remember(panelRevision) { GamePanelPrefs.load(context) }

    val games = remember(library, favoritesOnly) {
        gameLibrary(library.apps, library.manual, library.excluded)
            .filter { !favoritesOnly || it.packageName in library.favorites }
            .sortedWith(compareByDescending<GameApp> { it.packageName in library.favorites }.thenBy { it.label.lowercase() })
    }
    val gamesState = rememberUpdatedState(games)
    val initialPage = remember { LobbyModel.targetPage(games.map { it.packageName }, selected) }
    val pagerState = rememberPagerState(initialPage = initialPage) { gamesState.value.size }
    val restore = remember { LobbyRestoreGate() }

    // القائمة تغيّرت (تحميل · مرشِّح · إعادة ترتيب بالمفضّلة): أعد الـCarousel إلى اللعبة المحدَّدة.
    LaunchedEffect(games) {
        if (games.isNotEmpty()) {
            val target = LobbyModel.targetPage(games.map { it.packageName }, selected)
            if (pagerState.currentPage != target) pagerState.scrollToPage(target)
            restore.games = games
        }
    }
    // استقرّت صفحة بفعل المستخدم: احفظ الحزمة. تُتجاهل الإصدارات الناتجة عن تغيّر القائمة نفسها،
    // وإلا كتبت فوق التحديد الصحيح مؤشّرًا قديمًا قبل أن تُعيد الدالة أعلاه الموضع.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val current = gamesState.value
            if (restore.games === current) current.getOrNull(page)?.let { selected = it.packageName }
        }
    }
    // نقرة اهتزاز خفيفة كلما عبرت بطاقة جديدة المركز بسحبة حقيقية (لا بإعادة الموضع البرمجية).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.drop(1).collect {
            if (restore.games === gamesState.value && pagerState.isScrollInProgress) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    val active = games.getOrNull(pagerState.currentPage)
    val activeConfig: AppConfig? = active?.let { settingsViewModel.fullConfig[it.packageName] }
    val panelEnabled = active?.packageName in panelPrefs.enabledPackages

    val setPanel: (Boolean) -> Unit = { enabled ->
        active?.packageName?.let {
            if (GamePanelPrefs.setEnabled(context, it, enabled)) {
                panelRevision += 1
                if (enabled) startGamePanel(context) else stopGamePanel(context)
            }
        }
    }
    val openProfile: () -> Unit = { if (active != null) profileOpen = true }
    val animations = rememberLobbyAnimationsEnabled()
    val intro = rememberLobbyIntro(animations)
    val breathe = lobbyLoop(animations, 2800, RepeatMode.Reverse, FastOutSlowInEasing, rest = 0.5f)
    val menu = listOf(
        LobbyMenuItem(Icons.Rounded.Refresh, stringResource(R.string.gaming_refresh), viewModel::refresh),
        LobbyMenuItem(Icons.Rounded.Close, stringResource(R.string.lobby_close)) { navController.navigateUp() }
    )
    val tick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    Box(Modifier.fillMaxSize()) {
        LobbyStageBackdrop()

        Column(
            modifier = Modifier.fillMaxSize().displayCutoutPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LobbyTopBar(
                modifier = Modifier.lobbyIntro(intro, fromBelow = (-24).dp),
                tabs = listOf(stringResource(R.string.lobby_tab_all), stringResource(R.string.gaming_favorites)),
                tabDescriptions = listOf(stringResource(R.string.lobby_tab_all), stringResource(R.string.lobby_favorites_filter)),
                selectedTab = if (favoritesOnly) 1 else 0,
                onTab = { index ->
                    if (favoritesOnly != (index == 1)) tick()
                    favoritesOnly = index == 1
                },
                onManage = { manageOpen = true },
                onMore = { menuOpen = true }
            )

            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val fitHeight = (maxHeight - LobbyGlowPad * 2).coerceIn(MinCardHeight, MaxCardHeight)
                val cardWidth = minOf(fitHeight * CARD_ASPECT, maxWidth * CARD_MAX_WIDTH_SHARE)
                val cardHeight = cardWidth / CARD_ASPECT
                when {
                    games.isNotEmpty() -> LobbyCardPager(
                        state = pagerState,
                        cardWidth = cardWidth,
                        cardHeight = cardHeight,
                        onSelect = { target -> scope.launch { pagerState.animateScrollToPage(target) } },
                        modifier = Modifier.lobbyIntro(intro, fromBelow = 24.dp, start = 0.1f, scaleFrom = 0.94f),
                        key = { index -> games.getOrNull(index)?.packageName ?: index }
                    ) { index, emphasis ->
                        val app = games.getOrNull(index)
                        if (app != null) {
                            val favorite = app.packageName in library.favorites
                            LobbyGameCard(
                                packageName = app.packageName,
                                title = app.label,
                                meta = rememberLobbyMetaText(app.packageName),
                                favorite = favorite,
                                favoriteDescription = stringResource(if (favorite) R.string.gaming_unfavorite else R.string.gaming_favorite),
                                positionDescription = stringResource(R.string.lobby_card_position, app.label, index + 1, games.size),
                                emphasis = emphasis,
                                breathe = breathe,
                                starEnabled = index == pagerState.currentPage,
                                onToggleFavorite = { viewModel.favorite(app.packageName, !favorite) }
                            )
                        }
                    }
                    library.loading -> LobbyNotice(stringResource(R.string.lobby_loading))
                    else -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MaxSpace.lg)
                    ) {
                        LobbyNotice(stringResource(if (favoritesOnly) R.string.lobby_empty_favorites else R.string.lobby_empty))
                        if (!favoritesOnly) {
                            LobbyHexButton(
                                title = stringResource(R.string.lobby_manage_games),
                                tone = LobbyHexTone.Blue,
                                icon = Icons.Rounded.VideogameAsset,
                                width = SideHexWidth,
                                height = SideHexHeight,
                                onClick = { manageOpen = true }
                            )
                        }
                    }
                }
            }

            if (active != null) {
                LobbyActionRow(
                    modifier = Modifier.padding(bottom = MaxSpace.md).lobbyIntro(intro, fromBelow = 32.dp, start = 0.25f),
                    breathe = breathe,
                    panelEnabled = panelEnabled,
                    tweaks = activeConfig?.customizedFieldCount() ?: 0,
                    onPanel = {
                        tick()
                        setPanel(!panelEnabled)
                    },
                    onProfile = openProfile,
                    onStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        launchFailed = !GameLibraryAccess.launch(context, active.packageName)
                    }
                )
            } else {
                Spacer(Modifier.height(ActionHeight + MaxSpace.md))
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).displayCutoutPadding().padding(top = TopBarHeight),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (library.failed) LobbyCaution(stringResource(R.string.gaming_config_failed))
            if (launchFailed) LobbyCaution(stringResource(R.string.game_space_launch_failed))
        }

        if (menuOpen) {
            LobbyMenuOverlay(
                items = menu,
                top = TopBarHeight - MaxSpace.sm,
                onDismiss = { menuOpen = false },
                modifier = Modifier.displayCutoutPadding()
            )
        }
    }

    if (profileOpen && active != null) {
        GameProfileDialog(
            app = active,
            config = activeConfig ?: AppConfig(),
            panelEnabled = panelEnabled,
            onPanelEnabled = setPanel,
            onUpdate = { key, value -> settingsViewModel.updateSetting(active.packageName, key, value) },
            onOpenFull = {
                profileOpen = false
                navController.openAppSettings(active.packageName)
            },
            onDismiss = { profileOpen = false }
        )
    }
    if (manageOpen) {
        ManageGamesDialog(
            apps = library.apps.filter { ForegroundAppResolver.isPackageName(it.packageName) }
                .distinctBy { it.packageName },
            manual = library.manual,
            excluded = library.excluded,
            onToggle = viewModel::membership,
            onDismiss = { manageOpen = false }
        )
    }
}

/** يحمل آخر قائمة أُعيد إليها موضع الـCarousel؛ مرجعٌ لا حالة، فلا يسبّب إعادة تركيب. */
private class LobbyRestoreGate {
    var games: List<GameApp>? = null
}

/**
 * سطر بيانات البطاقة: حجم الحزمة وعمر التثبيت في سطر واحد تفصل بينهما نقطة وسطى (يلتفّ إلى سطرين
 * إن ضاق العرض ولا يُقتطع). أرقام مقروءة فعلًا من النظام (ADR-07)، وما لم يُقرأ لا يُكتب.
 * يقرأ على IO مرّة ويحفظ في [metaCache].
 */
@Composable
private fun rememberLobbyMetaText(packageName: String): String {
    val context = LocalContext.current
    val meta by produceState<GameLobbyMeta?>(metaCache.get(packageName), packageName) {
        if (value == null) {
            val read = withContext(Dispatchers.IO) { GameLobbyMetaReader.read(context, packageName) }
            metaCache.put(packageName, read)
            value = read
        }
    }
    val today = stringResource(R.string.lobby_meta_age_today)
    val ageText = meta?.installedDays?.let { days ->
        if (days < 1) today else stringResource(R.string.lobby_meta_age_days, days)
    }
    val sizeText = meta?.sizeBytes?.let { Formatter.formatShortFileSize(context, it) }
    return listOfNotNull(sizeText, ageText).joinToString(" · ")
}

// ───────────────────────────── الشريط العلويّ ─────────────────────────────

/**
 * ثلاثة عناصر لا غير كما في المرجع: شبكة (إدارة الألعاب) في البداية، والشريط المعلَّق في الوسط،
 * والنقاط الثلاث (تحديث · إغلاق) في النهاية.
 */
@Composable
private fun LobbyTopBar(
    modifier: Modifier,
    tabs: List<String>,
    tabDescriptions: List<String>,
    selectedTab: Int,
    onTab: (Int) -> Unit,
    onManage: () -> Unit,
    onMore: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = MaxSpace.xxl)
    ) {
        LobbyGridButton(
            description = stringResource(R.string.lobby_manage_games),
            onClick = onManage,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        LobbyTabStrip(
            labels = tabs,
            descriptions = tabDescriptions,
            selected = selectedTab,
            onSelect = onTab,
            description = stringResource(R.string.lobby_tab_lobby),
            modifier = Modifier.align(Alignment.TopCenter).height(TabStripHeight)
        )
        LobbyMoreButton(
            description = stringResource(R.string.lobby_more),
            onClick = onMore,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

// ───────────────────────────── حالات فارغة وتحذير ─────────────────────────────

@Composable
private fun LobbyNotice(text: String) {
    Text(
        text = text,
        color = LobbyPalette.Muted,
        fontSize = 15.sp,
        modifier = Modifier.padding(horizontal = MaxSpace.xxl)
    )
}

/** رسالة فشل: لون تنبيه وأيقونة معًا، لا أحمرًا وحده. */
@Composable
private fun LobbyCaution(text: String) {
    Row(
        modifier = Modifier.padding(top = MaxSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)
    ) {
        Icon(imageVector = Icons.Rounded.Warning, contentDescription = null, tint = LobbyPalette.Caution, modifier = Modifier.size(MaxSize.iconGlyphSmall))
        Text(text = text, color = LobbyPalette.Caution, fontSize = 13.sp)
    }
}

// ───────────────────────────── الصفّ السفليّ ─────────────────────────────

/**
 * ثلاثة أزرار سداسية متلاصقة تقريبًا: [ملف اللعبة] أزرق، و[ابدأ] أحمر أكبر ومرفوع، و[اللوحة الجانبية]
 * أزرق. حالة كل زرّ ثانويّ تُكتب نصًّا تحت اسمه («افتراضي» / «٣ تعديلات»، «تشغيل» / «إيقاف»)
 * لأن اللون وحده لا يكفي إتاحةً — وهذا السطر الوحيد الزائد عن المرجع.
 */
@Composable
private fun LobbyActionRow(
    modifier: Modifier,
    breathe: State<Float>,
    panelEnabled: Boolean,
    tweaks: Int,
    onPanel: () -> Unit,
    onProfile: () -> Unit,
    onStart: () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.hairline)
    ) {
        LobbyHexButton(
            title = stringResource(R.string.lobby_tile_profile),
            tone = LobbyHexTone.Blue,
            icon = Icons.Rounded.Tune,
            value = if (tweaks > 0) stringResource(R.string.lobby_tile_profile_value, tweaks) else stringResource(R.string.lobby_tile_profile_none),
            active = tweaks > 0,
            width = SideHexWidth,
            height = SideHexHeight,
            onClick = onProfile,
            onClickLabel = stringResource(R.string.lobby_open_profile)
        )
        LobbyHexButton(
            title = stringResource(R.string.lobby_start),
            tone = LobbyHexTone.Red,
            breathe = breathe,
            width = StartHexWidth,
            height = StartHexHeight,
            onClick = onStart,
            modifier = Modifier.padding(bottom = StartRaise)
        )
        LobbyHexButton(
            title = stringResource(R.string.lobby_tile_panel),
            tone = LobbyHexTone.Blue,
            icon = Icons.Rounded.ViewSidebar,
            value = stringResource(if (panelEnabled) R.string.lobby_state_on else R.string.lobby_state_off),
            active = panelEnabled,
            width = SideHexWidth,
            height = SideHexHeight,
            onClick = onPanel
        )
    }
}

// ───────────────────────────── خدمة اللوحة الجانبية ─────────────────────────────

/** تشغيل خدمة اللوحة من اللوبي — نفس ما كانت تفعله شاشة المكتبة قبل إزالتها. */
private fun startGamePanel(context: Context) {
    val intent = Intent(context, GamePanelService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
    } else {
        context.startService(intent)
    }
}

private fun stopGamePanel(context: Context) {
    context.startService(Intent(context, GamePanelService::class.java).setAction(GamePanelService.ACTION_STOP))
}
