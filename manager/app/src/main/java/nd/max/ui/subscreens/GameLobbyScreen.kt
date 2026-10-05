/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.core.gamespace.GameApp
import nd.max.core.gamespace.GameLibraryAccess
import nd.max.core.gamespace.GameLobbyMeta
import nd.max.core.gamespace.GameLobbyMetaReader
import nd.max.core.gamespace.gameLibrary
import nd.max.core.platform.ForegroundAppResolver
import nd.max.service.GamePanelService
import nd.max.ui.component.AppIconImage
import nd.max.ui.component.LobbyAngledShape
import nd.max.ui.component.LobbyBattery
import nd.max.ui.component.LobbyEmblem
import nd.max.ui.component.LobbyHexShape
import nd.max.ui.component.LobbyPalette
import nd.max.ui.component.LobbyTabShape
import nd.max.ui.component.LobbyWindowEffect
import nd.max.ui.component.lobbyBackdropBrush
import nd.max.ui.component.rememberLobbyAnimationsEnabled
import nd.max.ui.component.rememberLobbyBattery
import nd.max.ui.component.rememberLobbyClock
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.navigation.openAppSettings
import nd.max.ui.util.AppConfig
import nd.max.ui.util.GamePanelPrefs
import nd.max.ui.util.customizedFieldCount
import nd.max.ui.viewmodel.AppSettingsViewModel
import nd.max.ui.viewmodel.GameSpaceViewModel
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.abs
import nd.max.ui.component.LobbyReactor
import nd.max.ui.component.lobbyIntro
import nd.max.ui.component.lobbyLoop
import nd.max.ui.component.lobbyPress
import nd.max.ui.component.lobbySelectionPlate
import nd.max.ui.component.lobbySheen
import nd.max.ui.component.rememberLobbyIntro
import nd.max.ui.component.rememberLobbySheenPhase

private val TopBarHeight = 64.dp
private val BottomBarHeight = 64.dp
private val GameRowHeight = 76.dp
private val GameIconSize = 58.dp
private val GameIconRadius = 14.dp
private val StartWidth = 232.dp
private val StartHeight = 56.dp
private val HexWidth = 56.dp
private val TileWidth = 150.dp
private val TileHeight = 64.dp
private val TileRadius = 14.dp
private val TitleMaxWidth = 340.dp

/**
 * لوبي الألعاب — **سطح لعب عرضيّ كامل الشاشة**، بتصميم لوبي REDMAGIC كما طلب المالك:
 * قائمة ألعاب يسارية، حلقة HUD وسطى، عنوان اللعبة وزرّ «ابدأ» يمينًا، لسان سفلي، وقائمة إعدادات
 * بشريط جانبي ([GameProfileDialog]).
 *
 * ### ما حُذف
 *
 * `GameSpaceScreen` (مكتبة عمودية) أُزيلت بأمر المالك؛ وما كانت تقدّمه انتقل إلى هنا:
 * الإدراج والإزالة في [ManageGamesDialog]، والمفضّلة في الشريط، والملف في الحوار.
 *
 * ### وما لم يتغيّر (حدود معلنة)
 *
 * - لا كتابة عتاد من هذه الشاشة (ADR-11): التشغيل يمرّ بـ`GameLibraryAccess.launch`، والإعدادات
 *   بـ`AppSettingsViewModel.updateSetting`، ومفتاح اللوحة بـ`GamePanelPrefs` وخدمتها.
 * - لا أرقام مختلقة (ADR-07): الصفّ يعرض حجم الحزمة وعمر التثبيت المقروءين فعلًا، ولا «وقت لعب».
 * - لا أصول منقولة: كل رسم بالكود، والأيقونات هي أيقونات التطبيقات الحقيقية.
 *
 * **غير مُتحقَّق على جهاز:** القفل العرضي وإخفاء الأشرطة وقصّ الكاميرا (display cutout).
 */
@Composable
fun GameLobbyScreen(
    navController: NavController,
    viewModel: GameSpaceViewModel = hiltViewModel(),
    settingsViewModel: AppSettingsViewModel = viewModel()
) {
    LobbyWindowEffect()
    val context = LocalContext.current
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
    val active = games.firstOrNull { it.packageName == selected } ?: games.firstOrNull()
    val activeConfig: AppConfig? = active?.let { settingsViewModel.fullConfig[it.packageName] }
    val panelEnabled = active?.packageName in panelPrefs.enabledPackages
    val isFavorite = active?.packageName in library.favorites

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
    val battery by rememberLobbyBattery()
    val clock by rememberLobbyClock()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(lobbyBackdropBrush())
            .displayCutoutPadding()
    ) {
        val listWidth = (maxWidth * 0.30f).coerceIn(220.dp, 340.dp)
        val emblemSize = minOf(maxHeight * 0.80f, maxWidth * 0.40f)
        val tabWidth = (maxWidth * 0.26f).coerceIn(160.dp, 260.dp)

        LobbyReactor(
            Modifier.align(Alignment.Center).size(emblemSize).lobbyIntro(intro, scaleFrom = 0.78f),
            animate = animations,
            pulseKey = active?.packageName
        )

        LobbyGameList(
            modifier = Modifier.align(Alignment.CenterStart).width(listWidth).fillMaxHeight(),
            games = games,
            favorites = library.favorites,
            activePackage = active?.packageName,
            loading = library.loading,
            onSelect = { selected = it },
            intro = intro
        )

        LobbyTopBar(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().lobbyIntro(intro, fromBelow = (-30).dp, start = 0.05f),
            clock = clock,
            battery = battery,
            active = active,
            favoritesOnly = favoritesOnly,
            onFavoritesOnly = { favoritesOnly = !favoritesOnly },
            onRefresh = viewModel::refresh,
            onClose = { navController.navigateUp() }
        )

        if (active != null) {
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = TopBarHeight, end = MaxSpace.lg)
                    .lobbyIntro(intro, fromStart = (-48).dp, start = 0.25f),
                horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)
            ) {
                LobbyFeatureTile(
                    icon = Icons.Rounded.ViewSidebar,
                    title = stringResource(R.string.lobby_tile_panel),
                    value = stringResource(if (panelEnabled) R.string.lobby_state_on else R.string.lobby_state_off),
                    active = panelEnabled,
                    onClick = { setPanel(!panelEnabled) }
                )
                val tweaks = activeConfig?.customizedFieldCount() ?: 0
                LobbyFeatureTile(
                    icon = Icons.Rounded.Tune,
                    title = stringResource(R.string.lobby_tile_profile),
                    value = if (tweaks > 0) stringResource(R.string.lobby_tile_profile_value, tweaks) else stringResource(R.string.lobby_tile_profile_none),
                    active = tweaks > 0,
                    onClick = openProfile
                )
            }
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = MaxSpace.lg, bottom = MaxSpace.md)
                    .lobbyIntro(intro, fromStart = (-72).dp, start = 0.3f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(MaxSpace.md)
            ) {
                AnimatedContent(
                    targetState = active.label,
                    transitionSpec = {
                        (slideInHorizontally(spring(dampingRatio = 0.75f, stiffness = 380f)) { it / 4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(160)) { -it / 4 } + fadeOut(tween(140)))
                    },
                    label = "lobbyTitle"
                ) { title ->
                    Text(
                        text = title,
                        color = LobbyPalette.Ink,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = TitleMaxWidth)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)) {
                    LobbyStartButton(
                        label = stringResource(R.string.lobby_start),
                        onClick = { launchFailed = !GameLibraryAccess.launch(context, active.packageName) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)) {
                    LobbyHexButton(
                        icon = Icons.Rounded.VideogameAsset,
                        description = stringResource(R.string.lobby_manage_games),
                        onClick = { manageOpen = true }
                    )
                    LobbyHexButton(
                        icon = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        description = stringResource(if (isFavorite) R.string.gaming_unfavorite else R.string.gaming_favorite),
                        active = isFavorite,
                        onClick = { viewModel.favorite(active.packageName, !isFavorite) }
                    )
                    LobbyHexButton(
                        icon = Icons.Rounded.Settings,
                        description = stringResource(R.string.lobby_open_profile),
                        accent = true,
                        onClick = openProfile
                    )
                }
            }
        } else if (!library.loading) {
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = MaxSpace.lg, bottom = MaxSpace.md),
                horizontalAlignment = Alignment.End
            ) {
                LobbyHexButton(
                    icon = Icons.Rounded.VideogameAsset,
                    description = stringResource(R.string.lobby_manage_games),
                    accent = true,
                    onClick = { manageOpen = true }
                )
            }
        }

        LobbyBottomTab(
            modifier = Modifier.align(Alignment.BottomCenter).lobbyIntro(intro, fromBelow = 56.dp, start = 0.4f),
            label = stringResource(R.string.lobby_tab_lobby),
            width = tabWidth
        )

        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = TopBarHeight),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (library.failed) {
                Text(stringResource(R.string.gaming_config_failed), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            if (launchFailed) {
                Text(stringResource(R.string.game_space_launch_failed), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
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

// ───────────────────────────── قائمة الألعاب ─────────────────────────────

@Composable
private fun LobbyGameList(
    modifier: Modifier,
    games: List<GameApp>,
    favorites: Set<String>,
    activePackage: String?,
    loading: Boolean,
    onSelect: (String) -> Unit,
    intro: State<Float>
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = TopBarHeight + MaxSpace.sm, bottom = BottomBarHeight + MaxSpace.sm),
        verticalArrangement = Arrangement.spacedBy(MaxSpace.xs)
    ) {
        if (games.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(if (loading) R.string.spoof_reading else R.string.lobby_empty),
                    color = LobbyPalette.Muted,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(MaxSpace.lg)
                )
            }
        }
        itemsIndexed(games, key = { _, game -> game.packageName }) { index, app ->
            LobbyGameRow(
                index = index,
                intro = intro,
                app = app,
                selected = app.packageName == activePackage,
                favorite = app.packageName in favorites,
                onClick = { onSelect(app.packageName) }
            )
        }
    }
}

@Composable
private fun LobbyGameRow(app: GameApp, selected: Boolean, favorite: Boolean, index: Int, intro: State<Float>, onClick: () -> Unit) {
    val context = LocalContext.current
    val meta by produceState<GameLobbyMeta?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) { GameLobbyMetaReader.read(context, app.packageName) }
    }
    val today = stringResource(R.string.lobby_meta_age_today)
    val ageText = meta?.installedDays?.let { days ->
        if (days < 1) today else stringResource(R.string.lobby_meta_age_days, days)
    }
    val sizeText = meta?.sizeBytes?.let { Formatter.formatShortFileSize(context, it) }
    val metaLine = listOfNotNull(sizeText, ageText).joinToString(" · ")
    val shape = RoundedCornerShape(GameIconRadius)
    val sel = animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 420f), label = "rowSel")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = GameRowHeight)
            .lobbyIntro(intro, fromStart = 64.dp, start = (0.10f + index * 0.045f).coerceAtMost(0.62f))
            .lobbySelectionPlate(sel)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = MaxSpace.lg, vertical = MaxSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
    ) {
        Box(
            modifier = Modifier
                .size(GameIconSize)
                .graphicsLayer {
                    val k = 1f + 0.10f * sel.value
                    scaleX = k
                    scaleY = k
                }
                .drawBehind {
                    if (sel.value > 0.01f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(LobbyPalette.Red.copy(alpha = 0.45f * sel.value), Color.Transparent),
                                center = center,
                                radius = size.maxDimension * 0.95f
                            ),
                            radius = size.maxDimension * 0.95f
                        )
                    }
                }
                .clip(shape)
                .then(if (selected) Modifier.border(MaxSize.activeRing, LobbyPalette.Red, shape) else Modifier)
        ) {
            AppIconImage(packageName = app.packageName, size = GameIconSize, contentDescription = null)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MaxSpace.hairline)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)) {
                Text(
                    text = app.label,
                    color = if (selected) LobbyPalette.Ink else LobbyPalette.Ink.copy(alpha = 0.78f),
                    fontSize = 19.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (favorite) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = stringResource(R.string.gaming_favorites),
                        tint = LobbyPalette.RedBright,
                        modifier = Modifier.size(MaxSize.iconGlyphSmall)
                    )
                }
            }
            if (selected && metaLine.isNotEmpty()) {
                Text(text = metaLine, color = LobbyPalette.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ───────────────────────────── الشريط العلوي ─────────────────────────────

@Composable
private fun LobbyTopBar(
    modifier: Modifier,
    clock: String,
    battery: LobbyBattery?,
    active: GameApp?,
    favoritesOnly: Boolean,
    onFavoritesOnly: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = modifier
            .height(TopBarHeight)
            .padding(horizontal = MaxSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)
    ) {
        Text(
            text = stringResource(R.string.lobby_brand),
            color = LobbyPalette.Ink,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            letterSpacing = 8.sp
        )
        Text(text = clock, color = LobbyPalette.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
        LobbyBatteryPill(battery)
        Spacer(Modifier.weight(1f))
        if (active != null) {
            val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .lobbyPress(interaction)
            .size(width = HexWidth, height = MaxSize.minTouchTarget)
                    .clip(LobbyHexShape)
                    .background(LobbyPalette.PanelRaised),
                contentAlignment = Alignment.Center
            ) {
                AppIconImage(packageName = active.packageName, size = 30.dp, contentDescription = null)
            }
        }
        Spacer(Modifier.weight(1f))
        LobbyIconButton(
            icon = if (favoritesOnly) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
            description = stringResource(R.string.lobby_favorites_filter),
            tint = if (favoritesOnly) LobbyPalette.RedBright else LobbyPalette.Ink,
            onClick = onFavoritesOnly
        )
        LobbyIconButton(icon = Icons.Rounded.Refresh, description = stringResource(R.string.gaming_refresh), onClick = onRefresh)
        LobbyIconButton(icon = Icons.Rounded.Close, description = stringResource(R.string.lobby_close), onClick = onClose)
    }
}

@Composable
private fun LobbyIconButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = LobbyPalette.Ink) {
    Box(
        modifier = Modifier
            .size(MaxSize.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = tint)
    }
}

@Composable
private fun LobbyBatteryPill(battery: LobbyBattery?) {
    if (battery == null) return
    val description = stringResource(R.string.lobby_battery_cd, battery.percent)
    val fill = when {
        battery.charging -> Color(0xFF3DDC84)
        battery.percent <= 15 -> LobbyPalette.Red
        else -> Color(0xFF39D353)
    }
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs)
    ) {
        Canvas(Modifier.size(width = 30.dp, height = 14.dp)) {
            val nub = 3.dp.toPx()
            val stroke = 1.6.dp.toPx()
            val body = Size(size.width - nub, size.height)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                size = body,
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(width = stroke)
            )
            val inset = stroke * 1.6f
            drawRoundRect(
                color = fill,
                topLeft = Offset(inset, inset),
                size = Size((body.width - inset * 2f) * battery.percent / 100f, body.height - inset * 2f),
                cornerRadius = CornerRadius(1.5.dp.toPx())
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(body.width, size.height * 0.30f),
                size = Size(nub, size.height * 0.40f),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
        Text(text = "${battery.percent}%", color = LobbyPalette.Ink.copy(alpha = 0.8f), fontSize = 13.sp)
    }
}

// ───────────────────────────── يمين: بلاطات وأزرار ─────────────────────────────

@Composable
private fun LobbyFeatureTile(icon: ImageVector, title: String, value: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(TileRadius)
    Row(
        modifier = Modifier
            .size(width = TileWidth, height = TileHeight)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(LobbyPalette.PanelRaised, LobbyPalette.Surface)))
            .border(MaxSize.hairlineBorder, if (active) LobbyPalette.Red.copy(alpha = 0.8f) else LobbyPalette.Hairline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = MaxSpace.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = if (active) LobbyPalette.RedBright else LobbyPalette.Muted)
        Column {
            Text(text = title, color = LobbyPalette.Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = value,
                color = if (active) LobbyPalette.RedBright else LobbyPalette.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LobbyStartButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val animate = rememberLobbyAnimationsEnabled()
    val sheen = lobbyLoop(animate, 2600)
    val flow = lobbyLoop(animate, 1500)
    val glow = lobbyLoop(animate, 2200, RepeatMode.Reverse, FastOutSlowInEasing, rest = 0.5f)
    Box(
        modifier = Modifier
            .lobbyPress(interaction, 0.95f)
            .size(width = StartWidth, height = StartHeight)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(LobbyPalette.Red.copy(alpha = 0.16f + 0.20f * glow.value), Color.Transparent),
                        center = center,
                        radius = size.width * 0.72f
                    ),
                    topLeft = Offset(-size.width * 0.2f, -size.height * 0.9f),
                    size = Size(size.width * 1.4f, size.height * 2.8f)
                )
            }
            .clip(LobbyAngledShape)
            .background(Brush.horizontalGradient(listOf(LobbyPalette.RedDeep, LobbyPalette.Red, LobbyPalette.RedDeep)))
            .lobbySheen(sheen)
            .border(MaxSize.hairlineBorder, LobbyPalette.RedBright.copy(alpha = 0.6f), LobbyAngledShape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaxSpace.md)) {
            LobbyChevrons(forward = true, phase = flow)
            Text(
                text = label,
                color = LobbyPalette.Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                maxLines = 1
            )
            LobbyChevrons(forward = false, phase = flow)
        }
    }
}

@Composable
private fun LobbyChevrons(forward: Boolean, phase: State<Float>) {
    Canvas(Modifier.size(width = 20.dp, height = 18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        listOf(0f, w * 0.45f).forEachIndexed { i, dx ->
            val k = (phase.value + (if (forward) i else 1 - i) * 0.5f) % 1f
            val alpha = 0.35f + 0.65f * (1f - abs(2f * k - 1f))
            val shift = (if (forward) 1f else -1f) * 2.dp.toPx() * k
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
            translate(left = shift) { drawPath(path = path, color = Color.White.copy(alpha = alpha), style = stroke) }
        }
    }
}

@Composable
private fun LobbyHexButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    active: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(width = HexWidth, height = MaxSize.minTouchTarget)
            .clip(LobbyHexShape)
            .background(
                if (accent) Brush.verticalGradient(listOf(LobbyPalette.Red, LobbyPalette.RedDeep))
                else Brush.verticalGradient(listOf(LobbyPalette.PanelRaised, LobbyPalette.Surface))
            )
            .border(MaxSize.hairlineBorder, if (accent) LobbyPalette.RedBright.copy(alpha = 0.6f) else LobbyPalette.Hairline, LobbyHexShape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) LobbyPalette.RedBright else LobbyPalette.Ink
        )
    }
}

// ───────────────────────────── اللسان السفلي ─────────────────────────────

/**
 * لسان «لوبي الألعاب». وحيد عن قصد: اللسان الثاني في الأصل المرجعيّ (قاعدة/مساحة ثانية) خارج
 * نطاق هذه الجولة بأمر المالك («نعمل على لوبي الألعاب فقط»)، فلا يُرسم لسان بلا شاشة خلفه.
 */
@Composable
private fun LobbyBottomTab(modifier: Modifier, label: String, width: Dp) {
    Row(
        modifier = modifier
            .size(width = width, height = BottomBarHeight - MaxSpace.md)
            .clip(LobbyTabShape)
            .background(Brush.verticalGradient(listOf(LobbyPalette.Red.copy(alpha = 0.92f), LobbyPalette.RedDeep)))
            .lobbySheen(rememberLobbySheenPhase(), 0.2f)
            .semantics {
                role = Role.Tab
                selected = true
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = Icons.Rounded.Home, contentDescription = null, tint = LobbyPalette.Ink)
        Spacer(Modifier.width(MaxSpace.sm))
        Text(text = label, color = LobbyPalette.Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
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
