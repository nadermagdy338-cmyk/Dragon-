/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import nd.max.R
import nd.max.core.emuhub.RomEntry
import nd.max.core.emuhub.RomLibraryAccess
import nd.max.core.emuhub.RomSystem
import nd.max.core.emuhub.systemCounts
import nd.max.ui.component.LobbyBackdrop
import nd.max.ui.component.LobbyPalette
import nd.max.ui.component.ShelfRomCard
import nd.max.ui.design.MaxSearchField
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxTab
import nd.max.ui.design.MaxTabStrip
import nd.max.ui.viewmodel.EmulatorHubViewModel

/**
 * مركز المحاكيات — **الرفّ**: ما عندي من ملفّات، وبأيّ نظام، وكيف أشغّلها.
 *
 * تخطيطه **مختلف عن `GameLobbyScreen` عمدًا** (الخطة §٦.١): اللوبي يجيب «كيف ألعب أفضل؟» ببطاقة
 * واحدة كبيرة، والمركز يجيب «ما عندي؟» فمئات الملفّات تحتاج شرائح وشبكة لا Carousel. ويتشاركان
 * **لغة واحدة**: نفس الخلفية واللوحة والحركة (`LobbyBackdrop`/`LobbyPalette`/`lobbyPress`).
 *
 * **وما لا تدّعيه الشاشة:** الفتح يمرّ عبر منتقي النظام، أي أن تطبيقًا آخر هو من يشغّل اللعبة.
 * النصّ يقول ذلك بنصّه، ولا يُقال «بدأت اللعبة».
 */
@Composable
fun EmulatorHubScreen(
    navController: NavController,
    viewModel: EmulatorHubViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var system by remember { mutableStateOf<RomSystem?>(null) }
    var message by remember { mutableIntStateOf(0) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.addFolder(uri)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.addDocuments(uris)
    }

    val counts = remember(state.entries) { systemCounts(state.entries) }
    val rows = remember(state.entries, system, query) {
        nd.max.core.emuhub.shelfRows(state.entries, system, query)
    }

    Box(Modifier.fillMaxSize()) {
        LobbyBackdrop(tone = LobbyPalette.Red)
        Column(Modifier.fillMaxSize().displayCutoutPadding()) {
            HubTopBar(
                count = rows.size,
                onBack = { navController.navigateUp() },
                onRefresh = viewModel::refresh,
            )
            MaxTabStrip(
                tabs = listOf(MaxTab(stringResource(R.string.emu_shelf_all), badgeCount = state.entries.size)) +
                    counts.entries.sortedBy { it.key.name }.map { MaxTab(it.key.name, badgeCount = it.value) },
                selectedIndex = system?.let { counts.keys.sortedBy { k -> k.name }.indexOf(it) + 1 } ?: 0,
                onSelect = { index ->
                    system = if (index == 0) null else counts.keys.sortedBy { k -> k.name }.getOrNull(index - 1)
                },
                modifier = Modifier.padding(horizontal = MaxSpace.gutter),
            )
            MaxSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.emu_shelf_search),
                modifier = Modifier.padding(horizontal = MaxSpace.gutter, vertical = MaxSpace.sm),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> HubNotice(stringResource(R.string.emu_shelf_loading))
                    rows.isEmpty() -> HubNotice(
                        stringResource(hubEmptyText(state.outcome, state.hasFolders, state.hasDocuments, query))
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(MaxSpace.gutter),
                        horizontalArrangement = Arrangement.spacedBy(MaxSpace.md),
                        verticalArrangement = Arrangement.spacedBy(MaxSpace.md),
                    ) {
                        items(rows, key = { it.uri }) { entry ->
                            ShelfRomCard(
                                title = entry.name,
                                meta = hubMetaText(entry, context),
                                systemLabel = hubSystemLabel(entry),
                                playDescription = stringResource(R.string.emu_shelf_open),
                                onPlay = {
                                    message = if (RomLibraryAccess.open(context, entry.parts.first())) {
                                        R.string.emu_shelf_requested
                                    } else {
                                        R.string.emu_shelf_open_failed
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (message != 0) {
                Text(
                    text = stringResource(message),
                    color = LobbyPalette.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = MaxSpace.gutter, vertical = MaxSpace.sm),
                )
            }
            HubSources(
                onAddFolder = { folderPicker.launch(null) },
                onAddFiles = { filePicker.launch(arrayOf("*/*")) },
            )
        }
    }
}

/** الشريط العلوي: رجوع · العنوان · العدد · تحديث. لا تبويبات هنا — التبويبات شرائح أنظمة تحت. */
@Composable
private fun HubTopBar(count: Int, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaxSpace.sm, vertical = MaxSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
    ) {
        HubIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.emu_shelf_back), onBack)
        Text(
            text = stringResource(R.string.emu_hub_title),
            color = LobbyPalette.Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = stringResource(R.string.emu_shelf_count, count),
            color = LobbyPalette.Muted,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        HubIconButton(Icons.Rounded.Refresh, stringResource(R.string.emu_shelf_refresh), onRefresh)
    }
}

@Composable
private fun HubIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(MaxSize.minTouchTarget).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = LobbyPalette.Ink)
    }
}

/** حالة غير قابلة للعب: سبب بنصّه لا «حدث خطأ». */
@Composable
private fun HubNotice(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = LobbyPalette.Muted,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = MaxSpace.xxl),
        )
    }
}

/** زرّا المصادر أسفل الشاشة: مجلد (فهرسة) وملفّات (إضافة مفردة). */
@Composable
private fun HubSources(onAddFolder: () -> Unit, onAddFiles: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaxSpace.gutter, vertical = MaxSpace.sm),
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onAddFolder) {
            Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = LobbyPalette.Cyan)
            Text(stringResource(R.string.emu_shelf_add_folder), color = LobbyPalette.Ink)
        }
        TextButton(onClick = onAddFiles) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = LobbyPalette.Cyan)
            Text(stringResource(R.string.emu_hub_add), color = LobbyPalette.Ink)
        }
    }
}

/** سطر حالة أسفل الشاشة — لا شيء يُعرض كأنه نتيجة. */
private fun hubEmptyText(
    outcome: RomLibraryAccess.ScanOutcome,
    hasFolders: Boolean,
    hasDocuments: Boolean,
    query: String,
): Int = when {
    query.isNotEmpty() -> R.string.emu_shelf_no_match
    outcome == RomLibraryAccess.ScanOutcome.NO_ACCESS -> R.string.emu_shelf_no_access
    outcome == RomLibraryAccess.ScanOutcome.TRUNCATED -> R.string.emu_shelf_truncated
    outcome == RomLibraryAccess.ScanOutcome.FAILED -> R.string.emu_shelf_failed
    hasFolders || hasDocuments -> R.string.emu_shelf_empty_known
    else -> R.string.emu_shelf_empty
}

/** نصّ النظام: اسمه، أو «ملتبس»، أو «مجهول» — لا تخمين ولا ادّعاء معرفة. */
@Composable
private fun hubSystemLabel(entry: RomEntry): String = when {
    entry.discSet -> stringResource(R.string.emu_shelf_disc_set, entry.parts.size)
    entry.system != null -> entry.system.name
    entry.ambiguous -> stringResource(R.string.emu_shelf_ambiguous)
    else -> stringResource(R.string.emu_shelf_unknown)
}

/** الحجم أو شرطة. المجهول لا يصير «0 B» (ADR-07). */
private fun hubMetaText(entry: RomEntry, context: android.content.Context): String {
    val size = entry.sizeBytes?.let { android.text.format.Formatter.formatShortFileSize(context, it) }
    return size ?: "—"
}
