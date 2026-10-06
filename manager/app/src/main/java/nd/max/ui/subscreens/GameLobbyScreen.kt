/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Thermostat
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import nd.max.ui.component.LobbyAngledShape
import nd.max.ui.component.LobbyBackdrop
import nd.max.ui.component.LobbyBattery
import nd.max.ui.component.LobbyCardPager
import nd.max.ui.component.LobbyChamferShape
import nd.max.ui.component.LobbyGameCard
import nd.max.ui.component.LobbyGlowPad
import nd.max.ui.component.LobbyMemory
import nd.max.ui.component.LobbyPalette
import nd.max.ui.component.LobbyTabStrip
import nd.max.ui.component.LobbyWindowEffect
import nd.max.ui.component.lobbyIntro
import nd.max.ui.component.lobbyLoop
import nd.max.ui.component.lobbyPress
import nd.max.ui.component.rememberLobbyAnimationsEnabled
import nd.max.ui.component.rememberLobbyBattery
import nd.max.ui.component.rememberLobbyClock
import nd.max.ui.component.rememberLobbyIntro
import nd.max.ui.component.rememberLobbyMemory
import nd.max.ui.component.rememberLobbyTone
import nd.max.ui.design.MAX_VALUE_UNAVAILABLE
import nd.max.ui.design.MaxDuration
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.navigation.openAppSettings
import nd.max.ui.util.AppConfig
import nd.max.ui.util.GamePanelPrefs
import nd.max.ui.util.customizedFieldCount
import nd.max.ui.viewmodel.AppSettingsViewModel
import nd.max.ui.viewmodel.GameSpaceViewModel

private val TopBarHeight = 56.dp
private val ActionHeight = 60.dp
private val ReadinessHeight = 28.dp
private val StartWidth = 220.dp
private val TileWidth = 148.dp
private val MinCardHeight = 120.dp
private val MaxCardHeight = 260.dp
private val CompactWidth = 700.dp

/** نسبة عرض البطاقة إلى ارتفاعها (١٦:١٠). */
private const val CARD_ASPECT = 1.6f

/** أكبر حصّة من عرض الشاشة تأخذها البطاقة المركزية، فتبقى للجارتين مساحة ظاهرة. */
private const val CARD_MAX_WIDTH_SHARE = 0.46f

/** حرارة بطارية تُعلَّم تنبيهًا (٤٢°م) — عتبة عرض للّون والأيقونة، لا قرارًا على العتاد. */
private const val WARM_TENTHS = 420

/** بطارية منخفضة تُعلَّم تنبيهًا. */
private const val LOW_BATTERY_PERCENT = 15

/** قراءة حجم الحزمة وعمرها تتكرّر كلما دخلت بطاقة إلى الشاشة؛ التخزين يمنع إعادة قراءة الجهاز في كل تمرير. */
private val metaCache = LruCache<String, GameLobbyMeta>(64)

/**
 * لوبي الألعاب — **سطح لعب عرضيّ كامل الشاشة** ببطاقات Carousel بعمق كما طلب المالك:
 * البطاقة المحدَّدة كبيرة بإطار متوهّج وجارتاها مائلتان، شريط تبويب علويّ مائل (الكل · المفضّلة)،
 * صفّ جاهزية حقيقيّ (بطارية · حرارة البطارية · ذاكرة حرّة)، وصفّ أزرار سفليّ يتوسّطه «ابدأ».
 *
 * ### ما يقوم عليه التصميم
 *
 * - **المحتوى هو البطل:** لا حلقة مفاعل ولا جمرات ولا ضوء ماسح؛ حلقة مستمرّة **واحدة** هي
 *   تنفّس حافة البطاقة وتوهّج «ابدأ».
 * - **لا نصّ مقطوع:** عناوين البطاقات سطران، وبياناتها سطران، وأسماء البلاطات سطران.
 * - **لون الخلفية من اللعبة نفسها:** يُستخرج من أيقونتها المثبَّتة فتتبدّل الخلفية بتبدّل البطاقة.
 *
 * ### ما لم يتغيّر (حدود معلنة)
 *
 * - لا كتابة عتاد من هذه الشاشة (ADR-11): التشغيل يمرّ بـ`GameLibraryAccess.launch`، والإعدادات
 *   بـ`AppSettingsViewModel.updateSetting`، ومفتاح اللوحة بـ`GamePanelPrefs` وخدمتها.
 * - لا أرقام مختلقة (ADR-07): البطاقة تعرض حجم الحزمة وعمر التثبيت المقروءين فعلًا ولا «وقت لعب»،
 *   وصفّ الجاهزية يعرض «—» لما لم يُقرأ.
 * - لا تبويب بلا شاشة خلفه: «الكل» و«المفضّلة» مرشِّحان حقيقيّان للقائمة نفسها.
 * - لا أصول منقولة: كل رسم بالكود، والصور هي أيقونات التطبيقات الحقيقية.
 *
 * **غير مُتحقَّق على جهاز:** القفل العرضي وإخفاء الأشرطة وقصّ الكاميرا (display cutout)، واتجاه ميل
 * الجارتين، وانعكاس الـPager في RTL، وسلاسة ٦٠fps أثناء السحب.
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
    val battery by rememberLobbyBattery()
    val memory by rememberLobbyMemory()
    val clock by rememberLobbyClock()
    val backdropTone by animateColorAsState(
        targetValue = rememberLobbyTone(active?.packageName),
        animationSpec = tween(MaxDuration.standard),
        label = "lobbyTone"
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < CompactWidth
        LobbyBackdrop(tone = backdropTone)

        Column(
            modifier = Modifier.fillMaxSize().displayCutoutPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LobbyTopBar(
                modifier = Modifier.lobbyIntro(intro, fromBelow = (-24).dp),
                compact = compact,
                clock = clock,
                tabs = listOf(stringResource(R.string.lobby_tab_all), stringResource(R.string.gaming_favorites)),
                tabDescriptions = listOf(stringResource(R.string.lobby_tab_all), stringResource(R.string.lobby_favorites_filter)),
                selectedTab = if (favoritesOnly) 1 else 0,
                onTab = { favoritesOnly = it == 1 },
                onRefresh = viewModel::refresh,
                onManage = { manageOpen = true },
                onClose = { navController.navigateUp() }
            )

            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val cardHeight = (maxHeight - LobbyGlowPad * 2).coerceIn(MinCardHeight, MaxCardHeight)
                val cardWidth = minOf(cardHeight * CARD_ASPECT, maxWidth * CARD_MAX_WIDTH_SHARE)
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
                            LobbyTile(
                                icon = Icons.Rounded.VideogameAsset,
                                title = stringResource(R.string.lobby_manage_games),
                                value = null,
                                active = true,
                                onClick = { manageOpen = true }
                            )
                        }
                    }
                }
            }

            LobbyReadiness(battery = battery, memory = memory)

            if (active != null) {
                LobbyActionBar(
                    modifier = Modifier.padding(bottom = MaxSpace.md).lobbyIntro(intro, fromBelow = 32.dp, start = 0.25f),
                    breathe = breathe,
                    panelEnabled = panelEnabled,
                    tweaks = activeConfig?.customizedFieldCount() ?: 0,
                    onPanel = { setPanel(!panelEnabled) },
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
 * سطرا بيانات البطاقة: حجم الحزمة وعمر التثبيت، كلٌّ في سطر. أرقام مقروءة فعلًا من النظام (ADR-07)،
 * وما لم يُقرأ لا يُكتب. يقرأ على IO مرّة ويحفظ في [metaCache].
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
    return listOfNotNull(sizeText, ageText).joinToString("\n")
}

// ───────────────────────────── الشريط العلوي ─────────────────────────────

@Composable
private fun LobbyTopBar(
    modifier: Modifier,
    compact: Boolean,
    clock: String,
    tabs: List<String>,
    tabDescriptions: List<String>,
    selectedTab: Int,
    onTab: (Int) -> Unit,
    onRefresh: () -> Unit,
    onManage: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = MaxSpace.sm)
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)
        ) {
            LobbyIconButton(icon = Icons.Rounded.Close, description = stringResource(R.string.lobby_close), onClick = onClose)
            if (!compact) {
                Text(
                    text = stringResource(R.string.lobby_brand),
                    color = LobbyPalette.Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = 4.sp
                )
            }
            Text(text = clock, color = LobbyPalette.Muted, fontSize = 14.sp)
        }
        LobbyTabStrip(
            labels = tabs,
            descriptions = tabDescriptions,
            selected = selectedTab,
            onSelect = onTab,
            description = stringResource(R.string.lobby_tab_lobby),
            modifier = Modifier.align(Alignment.Center)
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LobbyIconButton(icon = Icons.Rounded.Refresh, description = stringResource(R.string.gaming_refresh), onClick = onRefresh)
            LobbyIconButton(icon = Icons.Rounded.VideogameAsset, description = stringResource(R.string.lobby_manage_games), onClick = onManage)
        }
    }
}

@Composable
private fun LobbyIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(MaxSize.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = LobbyPalette.Ink)
    }
}

// ───────────────────────────── صفّ الجاهزية ─────────────────────────────

/**
 * ثلاث قراءات حيّة تحت البطاقة. المجهول «—» لا صفر (ADR-07)، والتنبيه **لونٌ وأيقونة معًا** —
 * لا أحمر، لأن الأحمر هو العلامة هنا فلا يصلح إشارة خطر.
 */
@Composable
private fun LobbyReadiness(battery: LobbyBattery?, memory: LobbyMemory?) {
    val unavailable = stringResource(R.string.lobby_ready_unavailable)
    val lowBattery = battery != null && !battery.charging && battery.percent <= LOW_BATTERY_PERCENT
    val warm = (battery?.tempTenthsC ?: 0) >= WARM_TENTHS
    val batteryText = battery?.let { "${it.percent}%" }
    val tempText = LobbyModel.temperatureText(battery?.tempTenthsC)
    val memoryText = LobbyModel.freeMemoryText(memory?.availBytes)
    Row(
        modifier = Modifier.height(ReadinessHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.xl)
    ) {
        ReadinessItem(
            icon = when {
                lowBattery -> Icons.Rounded.BatteryAlert
                battery?.charging == true -> Icons.Rounded.BatteryChargingFull
                else -> Icons.Rounded.BatteryFull
            },
            text = batteryText ?: MAX_VALUE_UNAVAILABLE,
            tint = when {
                lowBattery -> LobbyPalette.Caution
                battery?.charging == true -> LobbyPalette.Positive
                else -> LobbyPalette.Cyan
            },
            description = if (battery != null) {
                stringResource(R.string.lobby_battery_cd, battery.percent)
            } else {
                stringResource(R.string.lobby_ready_battery_cd, unavailable)
            }
        )
        ReadinessItem(
            icon = if (warm) Icons.Rounded.Warning else Icons.Rounded.Thermostat,
            text = tempText ?: MAX_VALUE_UNAVAILABLE,
            tint = if (warm) LobbyPalette.Caution else LobbyPalette.Cyan,
            description = stringResource(R.string.lobby_ready_temp_cd, tempText ?: unavailable)
        )
        ReadinessItem(
            icon = Icons.Rounded.Memory,
            text = memoryText ?: MAX_VALUE_UNAVAILABLE,
            tint = LobbyPalette.Cyan,
            description = stringResource(R.string.lobby_ready_ram_cd, memoryText ?: unavailable)
        )
    }
}

@Composable
private fun ReadinessItem(icon: ImageVector, text: String, tint: Color, description: String) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(MaxSize.iconGlyphSmall))
        Text(
            text = text,
            color = LobbyPalette.Ink.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1
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

@Composable
private fun LobbyActionBar(
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
    ) {
        LobbyTile(
            icon = Icons.Rounded.Tune,
            title = stringResource(R.string.lobby_tile_profile),
            value = if (tweaks > 0) stringResource(R.string.lobby_tile_profile_value, tweaks) else stringResource(R.string.lobby_tile_profile_none),
            active = tweaks > 0,
            onClick = onProfile,
            onClickLabel = stringResource(R.string.lobby_open_profile)
        )
        LobbyStartButton(label = stringResource(R.string.lobby_start), breathe = breathe, onClick = onStart)
        LobbyTile(
            icon = Icons.Rounded.ViewSidebar,
            title = stringResource(R.string.lobby_tile_panel),
            value = stringResource(if (panelEnabled) R.string.lobby_state_on else R.string.lobby_state_off),
            active = panelEnabled,
            onClick = onPanel
        )
    }
}

/**
 * بلاطة بزاوية مشطوفة: أيقونة + اسم (حتى سطرين) + حالة. [value] اختياريّة للبلاطة التي لا حالة لها.
 */
@Composable
private fun LobbyTile(
    icon: ImageVector,
    title: String,
    value: String?,
    active: Boolean,
    onClick: () -> Unit,
    onClickLabel: String? = null
) {
    val shape = remember { LobbyChamferShape(MaxSpace.sm) }
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .lobbyPress(interaction)
            .size(width = TileWidth, height = ActionHeight)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(LobbyPalette.PanelRaised, LobbyPalette.Surface)))
            .border(MaxSize.hairlineBorder, if (active) LobbyPalette.Red.copy(alpha = 0.8f) else LobbyPalette.Hairline, shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = MaxSpace.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = if (active) LobbyPalette.RedBright else LobbyPalette.Muted)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = LobbyPalette.Ink,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (value != null) {
                Text(
                    text = value,
                    color = if (active) LobbyPalette.RedBright else LobbyPalette.Muted,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** زرّ «ابدأ»: الإجراء الأوحد المتوهّج في الشاشة. توهّجه يتنفّس مع حافة البطاقة (الحلقة الوحيدة). */
@Composable
private fun LobbyStartButton(label: String, breathe: State<Float>, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .lobbyPress(interaction, 0.95f)
            .size(width = StartWidth, height = ActionHeight)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(LobbyPalette.Red.copy(alpha = 0.16f + 0.20f * breathe.value), Color.Transparent),
                        center = center,
                        radius = size.width * 0.72f
                    ),
                    topLeft = Offset(-size.width * 0.2f, -size.height * 0.9f),
                    size = Size(size.width * 1.4f, size.height * 2.8f)
                )
            }
            .clip(LobbyAngledShape)
            .background(Brush.horizontalGradient(listOf(LobbyPalette.RedDeep, LobbyPalette.Red, LobbyPalette.RedDeep)))
            .border(MaxSize.hairlineBorder, LobbyPalette.RedBright.copy(alpha = 0.6f), LobbyAngledShape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)) {
            LobbyChevrons(forward = true)
            Text(
                text = label,
                color = LobbyPalette.Ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                maxLines = 1
            )
            LobbyChevrons(forward = false)
        }
    }
}

/** شيفرونان ثابتان يحيطان بنص الزرّ (متماثلان، فلا يحتاجان انعكاس RTL). */
@Composable
private fun LobbyChevrons(forward: Boolean) {
    Canvas(Modifier.size(width = MaxSize.iconGlyph, height = MaxSize.iconGlyphSmall)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        listOf(0f, w * 0.45f).forEach { dx ->
            val path = Path().apply {
                if (forward) {
                    moveTo(dx + w * 0.10f, h * 0.10f)
                    lineTo(dx + w * 0.45f, h * 0.50f)
                    lineTo(dx + w * 0.10f, h * 0.90f)
                } else {
                    moveTo(dx + w * 0.45f, h * 0.10f)
                    lineTo(dx + w * 0.10f, h * 0.50f)
                    lineTo(dx + w * 0.45f, h * 0.90f)
                }
            }
            drawPath(path = path, color = Color.White.copy(alpha = 0.85f), style = stroke)
        }
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
