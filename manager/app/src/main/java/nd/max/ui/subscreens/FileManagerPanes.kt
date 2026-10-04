/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * جسم الشاشة: **النافذة الواحدة أو النافذتان** — ونافذة واحدة داخلهما.
 *
 * وفُصل هذا من ملف الشاشة لأن الأولى صارت تجاوز حدّ الحجم المعلن في المستودع، والحدّ ليس
 * ذوقًا: من يعدّل في الشاشة يقرأ ما يخصّها، ومن يعدّل في العرض يقرأ هذا وحده.
 *
 * والقاعدتان المعلنتان هنا:
 *
 * 1. **قرار العرض المزدوج يأتي جاهزًا** (`split`) من [nd.max.ui.util.FileWindowsRule] في
 *    الشاشة: القاعدة تُقاس في JVM، وخطؤها يقع في التخطيط (قائمتان كسولتان في صندوق لا
 *    يتّسعهما ← استثناء قياس على جهاز المستخدم).
 * 2. **حالة كل نافذة تُقرأ من نافذتها**: في العرض المزدوج يحمل كل شقّ مساره وسطر حالته
 *    وقائمته — ومن رأى قائمتين بلا مسارَين لا يعرف أيّهما يقرأ.
 */
package nd.max.ui.subscreens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.ui.component.FileEntryList
import nd.max.ui.component.FilePathBar
import nd.max.ui.component.FileStatusLine
import nd.max.ui.component.outcomeLine
import nd.max.ui.design.MaxCommand
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxConditionNotice
import nd.max.ui.design.MaxConditionPanel
import nd.max.ui.util.DeepSearchOutcome
import nd.max.ui.util.DirectoryCache
import nd.max.ui.util.DirectoryListing
import nd.max.ui.util.DiskSpace
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileSystemEngine
import nd.max.ui.util.FileWindowState
import nd.max.ui.util.FileWindowsState
import nd.max.ui.util.WindowSide

/** هامش الانتقال بين شقّين في العرض المزدوج — خطّ واحد يُقرأ حدًّا لا فاصلًا ثقيلًا. */
private val PaneDividerWidth = 1.dp

@Composable
internal fun PaneBody(
    windows: FileWindowsState,
    /** يُحسب في الشاشة من [nd.max.ui.util.FileWindowsRule] — فلا يُكتب قرار التخطيط مرّتين. */
    split: Boolean,
    viewOf: (WindowSide) -> WindowView,
    diskOf: (WindowSide) -> DiskSpace?,
    rootGranted: Boolean?,
    results: DeepSearchOutcome?,
    onActivate: (WindowSide) -> Unit,
    onOpenEntry: (FileEntry) -> Unit,
    onToggleSelection: (WindowSide, FileEntry) -> Unit,
    onSwipeSelect: (WindowSide, FileEntry) -> Unit,
    onLongPress: (WindowSide, FileEntry, Offset) -> Unit,
    onBack: (WindowSide) -> Unit,
    onEditPath: (WindowSide) -> Unit,
    onSearch: (WindowSide) -> Unit,
    menu: List<MaxCommand>,
    menuDescription: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (rootGranted == false) {
            MaxConditionPanel(
                condition = MaxCondition(
                    kind = MaxConditionKind.RootRequired,
                    title = stringResource(R.string.max_files_cond_no_root_title),
                    detail = stringResource(R.string.max_files_cond_no_root_detail),
                ),
            )
            return@Box
        }

        val sides = if (split) WindowSide.entries else listOf(windows.active)
        Row(modifier = Modifier.fillMaxSize()) {
            sides.forEachIndexed { index, side ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(PaneDividerWidth)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                WindowPaneSlot(
                    side = side,
                    window = windows.of(side),
                    view = viewOf(side),
                    disk = diskOf(side),
                    split = split,
                    results = if (side == windows.active) results else null,
                    onActivate = { onActivate(side) },
                    onOpenEntry = onOpenEntry,
                    onToggleSelection = { entry -> onToggleSelection(side, entry) },
                    onSwipeSelect = { entry -> onSwipeSelect(side, entry) },
                    onLongPress = { entry, anchor -> onLongPress(side, entry, anchor) },
                    onBack = { onBack(side) },
                    onEditPath = { onEditPath(side) },
                    onSearch = { onSearch(side) },
                    menu = menu,
                    menuDescription = menuDescription,
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                )
            }
        }
    }
}

/**
 * نافذة واحدة داخل المنطقة الوسطى: رأسها المعلن (في العرض المزدوج وحده) ثم قائمتها.
 *
 * والنقرة في الشقّ **تنشّطه** وتُقرأ في المرور الأول بلا استهلاك، فلا تُسرَق النقرة من
 * الصفّ — وتصير النافذة التي تلمسها هي التي يقع فيها الأمر التالي.
 */
@Composable
internal fun WindowPaneSlot(
    side: WindowSide,
    window: FileWindowState,
    view: WindowView,
    disk: DiskSpace?,
    split: Boolean,
    results: DeepSearchOutcome?,
    onActivate: () -> Unit,
    onOpenEntry: (FileEntry) -> Unit,
    onToggleSelection: (FileEntry) -> Unit,
    onSwipeSelect: (FileEntry) -> Unit,
    onLongPress: (FileEntry, Offset) -> Unit,
    onBack: () -> Unit,
    onEditPath: () -> Unit,
    onSearch: () -> Unit,
    menu: List<MaxCommand>,
    menuDescription: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.pointerInput(side) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    onActivate()
                }
            }
        },
    ) {
        if (split) {
            FilePathBar(
                path = window.path,
                canGoBack = window.canGoBack,
                onBack = onBack,
                onEditPath = onEditPath,
                onSearch = onSearch,
                menu = menu,
                menuDescription = menuDescription,
            )
            FileStatusLine(counts = view.counts, disk = disk, loading = view.loading)
        }

        listingBanner(view.listing as? DirectoryListing.Entries)?.let { banner ->
            MaxConditionNotice(condition = banner)
        }

        results?.let { found ->
            MaxConditionNotice(
                condition = MaxCondition(
                    kind = MaxConditionKind.Empty,
                    title = stringResource(R.string.max_files_search_results_title, found.hits.size),
                    detail = outcomeLine(found),
                ),
            )
        }

        val shown = results?.hits ?: view.visible(window, nowEpoch())
        val state = if (results != null) {
            ListingState.Ready
        } else {
            listingState(
                listing = view.listing,
                loading = view.loading,
                visibleCount = shown.size,
                hiddenCount = view.counts?.hidden ?: 0,
                filtering = view.filtering,
            )
        }
        val notice = if (state == ListingState.Ready) {
            null
        } else {
            listingCondition(
                state = state,
                retryLabel = stringResource(R.string.max_files_cond_recheck),
                onRetry = onRetry,
            )
        }

        if (notice == null) {
            FileEntryList(
                entries = shown,
                selection = view.selection,
                selecting = view.selecting,
                onOpen = onOpenEntry,
                onToggleSelection = onToggleSelection,
                onSwipeSelect = onSwipeSelect,
                onLongPress = onLongPress,
                modifier = Modifier.weight(1f),
            )
        } else {
            MaxConditionPanel(condition = notice, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * قراءة مجلد: من الذاكرة فورًا إن وُجد، ثم نسخة طازجة — فلا يُستبدل المحتوى بمؤشّر تحميل
 * عند الرجوع إلى مجلد زاره المستخدم للتوّ.
 */
internal suspend fun loadListing(
    path: String,
    cache: DirectoryCache,
    onListing: (DirectoryListing) -> Unit,
) {
    cache.get(path)?.let(onListing)
    val fresh = withContext(Dispatchers.IO) { FileSystemEngine.list(path) }
    // ولا تُحفظ إلا قراءة ناجحة: حفظ «مُنع الوصول» كان سيجعل المنع يلتصق بالمسار بعد أن
    // يُمنَح الجذر، فيقرأ المستخدم منعًا لم يعد قائمًا.
    if (fresh is DirectoryListing.Entries) cache.put(fresh)
    onListing(fresh)
}

internal fun nowEpoch(): Long = System.currentTimeMillis() / 1000L

/** فترة استطلاع تقدّم المهمة — معايرة بالثمن: قراءة حجم كل ٤٠٠م.س لا تُثقل الجهاز. */
internal const val PROGRESS_POLL_MS = 400L
