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
 * `MT-FM` — مدير الملفات كما طلبه المالك: **نافذة واحدة ممتدّة بأسلوب MT Manager**، وعلى
 * الشاشة العريضة نافذتان جنبًا إلى جنب.
 *
 * «كيف يمكن استخدامه حتى أجعلها كما أخبرتك، كتقليد متناسق لي MT Manager … ويكون full screen
 * وليس هناك أزرار تحكّم في خارج مدير الملفات، أزلها واجعلها داخل الملفات: عند الضغط الطويل
 * على ملف تظهر قائمة بها الحذف أو النسخ أو اللصق وخِلافه».
 *
 * فأُزيلت اللوحة الثانية بكل ما فرضته (شريطا لوحين · مقارنة · ربط تنقّل · تبديل ترتيب)، وصار:
 *
 * | المنطقة | ما فيها |
 * | --- | --- |
 * | الأعلى | **تبويبا نافذة** (مجلدان مصطفّان)، والنشطة معلنة بالشكل |
 * | تحته | **شريط مسار**: رجوع في التاريخ · المسار يُلمس ليُكتب · بحث · أوامر الشاشة |
 * | تحته | **سطر حالة**: مجلدات · ملفات · **مخفي معلَن** · مساحة مقروءة أو «لم تُقرأ» |
 * | الوسط | **قائمة بسطر واحد لكل مدخل** (رمز · اسم · حجم · تاريخ) تمتدّ إلى آخر الشاشة |
 * | الأسفل | شريط أدوات بأسماء يُستبدل بشريط التحديد، ويعلوه شريط الحافظة وشريط المهام |
 *
 * وما بقي من الجولات السابقة لم يُمَسّ لأنه مُثبت: النموذج الخالص، و`FileOpGuard` قبل كل
 * نداء، والأحكام الثلاثة للنتيجة، وذاكرة المجلدات LRU، وعدّ المخفيّ، و«لم تُقرأ» بدل صفر.
 *
 * والقواعد الأربع الجديدة:
 *
 * 1. **الضغط الطويل يفتح القائمة عند الإصبع** على المدخل نفسه (يحدّده ثم يعرض أوامره
 *    بأسمائها)، والنقرة تفتح: مجلدًا · نصًّا في المحرّر · ملفًا بالتطبيق الافتراضي.
 * 2. **الحافظة بدل حوار «إلى أين؟»**: نسخ/قصّ ← تنقّل ← لصق، وتعارض الأسماء يُسأل عنه
 *    بخطة **تُحسب على الدفعة كاملة** قبل أي كتابة.
 * 3. **المهام الخلفية** بنسبة **مقيسة بالاستطلاع**، وإلغاء حقيقي بين العناصر.
 * 4. **التفاصيل في ملفات مجاورة**: `FileManagerCommands.kt` للقوائم، و`FileManagerPanes.kt`
 *    للعرض، و`FileManagerDialogs.kt` للنوافذ، و`FileManagerState.kt` للحالة — وهذه تركيب.
 */
package nd.max.ui.subscreens

import android.content.ClipData
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.ui.component.FileClipboardBar
import nd.max.ui.component.FileDrawerContent
import nd.max.ui.component.FileEntryList
import nd.max.ui.component.FilePathBar
import nd.max.ui.component.FileSelectionBar
import nd.max.ui.component.FileStatusLine
import nd.max.ui.component.FileTaskStrip
import nd.max.ui.component.FileToolBar
import nd.max.ui.component.FileWindowTabs
import nd.max.ui.design.MaxCommand
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxConditionPanel
import nd.max.ui.design.MaxContextMenu
import nd.max.ui.design.MaxDrawer
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.util.AccessBit
import nd.max.ui.util.AccessScope
import nd.max.ui.util.ApkInspector
import nd.max.ui.util.ClipboardMode
import nd.max.ui.util.ConflictChoice
import nd.max.ui.util.DeepSearchOutcome
import nd.max.ui.util.DirectoryCache
import nd.max.ui.util.DiskSpace
import nd.max.ui.util.FileAction
import nd.max.ui.util.FileActionSet
import nd.max.ui.util.FileBookmark
import nd.max.ui.util.FileBookmarks
import nd.max.ui.util.FileBrowser
import nd.max.ui.util.FileClipboard
import nd.max.ui.util.FileClipboardRules
import nd.max.ui.util.FileConflictRules
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileHistory
import nd.max.ui.util.FileOpenPlan
import nd.max.ui.util.FileOpenRoute
import nd.max.ui.util.FileOpGuard
import nd.max.ui.util.FileOpOutcome
import nd.max.ui.util.FileOpRefusal
import nd.max.ui.util.FileOpRequest
import nd.max.ui.util.FileOperation
import nd.max.ui.util.FileOpVerdict
import nd.max.ui.util.FilePermissionRules
import nd.max.ui.util.FileSearchEngine
import nd.max.ui.util.FileSearchPlan
import nd.max.ui.util.FileSelection
import nd.max.ui.util.FileSortKey
import nd.max.ui.util.FileSystemEngine
import nd.max.ui.util.FileTargets
import nd.max.ui.util.FileTask
import nd.max.ui.util.FileTaskKind
import nd.max.ui.util.FileTaskQueue
import nd.max.ui.util.FileTaskState
import nd.max.ui.util.FileWindowState
import nd.max.ui.util.FileWindowsCodec
import nd.max.ui.util.FileWindowsRule
import nd.max.ui.util.FileWindowsState
import nd.max.ui.util.HistoryEntry
import nd.max.ui.util.MountAccess
import nd.max.ui.util.RootMount
import nd.max.ui.util.TextPreview
import nd.max.ui.util.WindowSide
import nd.max.ui.util.executeFileOperation
import java.util.concurrent.atomic.AtomicBoolean

private val WindowsSaver = listSaver<FileWindowsState, String>(
    save = { FileWindowsCodec.encode(it) },
    restore = { FileWindowsCodec.decode(it) },
)

@Composable
fun FileManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardApi = LocalClipboard.current
    val snackbar = remember { SnackbarHostState() }
    val cache = remember { DirectoryCache() }
    val searchCancelled = remember { AtomicBoolean(false) }
    val stores = rememberFileManagerStores()

    var windows by rememberSaveable(stateSaver = WindowsSaver) { mutableStateOf(FileWindowsState()) }
    var firstView by remember { mutableStateOf(WindowView()) }
    var secondView by remember { mutableStateOf(WindowView()) }
    var refreshToken by remember { mutableStateOf(0) }
    var rootGranted by remember { mutableStateOf<Boolean?>(null) }
    var firstDisk by remember { mutableStateOf<DiskSpace?>(null) }
    var secondDisk by remember { mutableStateOf<DiskSpace?>(null) }

    var fileClipboard by remember { mutableStateOf<FileClipboard?>(null) }
    var clipboardOriginSide by remember { mutableStateOf<WindowSide?>(null) }
    var conflict by remember { mutableStateOf<ConflictRequest?>(null) }
    var tasks by remember { mutableStateOf<List<FileTask>>(emptyList()) }
    var cancelledTasks by remember { mutableStateOf(setOf<Long>()) }
    var menuAnchor by remember { mutableStateOf<Offset?>(null) }
    var drawerOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var mountAccess by remember { mutableStateOf<MountAccess?>(null) }
    var bookmarks by rememberStoredBookmarks(stores.bookmarks)
    var history by rememberStoredHistory(stores.history)
    var results by remember { mutableStateOf<DeepSearchOutcome?>(null) }
    var pendingSelect by remember { mutableStateOf<String?>(null) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var rename by remember { mutableStateOf<FileEntry?>(null) }
    var renameWindowOpen by remember { mutableStateOf(false) }
    var newFolderOpen by remember { mutableStateOf(false) }
    var newFileOpen by remember { mutableStateOf(false) }
    var deleteTargets by remember { mutableStateOf<List<String>?>(null) }
    var pathEditOpen by remember { mutableStateOf(false) }
    var inputDraft by remember { mutableStateOf("") }
    var refused by remember { mutableStateOf<FileOpRefusal?>(null) }
    var properties by remember { mutableStateOf<PropertiesState?>(null) }
    var editor by remember { mutableStateOf<EditorState?>(null) }
    var search by remember { mutableStateOf(SearchState()) }

    val window = windows.activeWindow
    val view = if (windows.active == WindowSide.First) firstView else secondView

    fun viewOf(side: WindowSide): WindowView = if (side == WindowSide.First) firstView else secondView

    fun updateView(side: WindowSide, transform: (WindowView) -> WindowView) {
        if (side == WindowSide.First) firstView = transform(firstView) else secondView = transform(secondView)
    }

    /** كل تفاعل مع نافذة **ينشّطها** أولًا: فلا يقع أمر على نافذة يظنّ المستخدم أنه في غيرها. */
    fun activate(side: WindowSide) {
        if (windows.active != side) windows = windows.activate(side)
    }

    fun toast(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun outcomeText(outcome: FileOpOutcome, count: Int): String = when {
        outcome.ok -> context.getString(R.string.max_files_outcome_ok) + " " +
            context.getString(R.string.max_files_outcome_count, count)
        outcome.executed -> context.getString(R.string.max_files_outcome_unverified)
        else -> context.getString(R.string.max_files_outcome_failed)
    }

    /** انتقال: يسجّل الزيارة، ويمسح نتائج بحث قديم فلا تُقرأ على مجلد آخر. */
    fun go(side: WindowSide, target: String, push: Boolean = true) {
        results = null
        windows = windows.navigate(side, target, push)
        history = FileHistory.push(history, target, System.currentTimeMillis())
    }

    fun selectedEntries(side: WindowSide): List<FileEntry> {
        val current = viewOf(side)
        return current.entries.filter { it.path in current.selection.paths }
    }

    /**
     * تنفيذ عملية فورية: الحرس يُسأل **بأسماء نافذة الوجهة** لا نافذة المصدر — تعارض
     * الأسماء يقع حيث تُكتب.
     */
    fun guardAndRun(request: FileOpRequest, side: WindowSide = windows.active) {
        val targetView = viewOf(side)
        val names = targetView.entries.mapTo(HashSet()) { it.name }
        val directories = targetView.entries.filter { it.isDirectory }.mapTo(HashSet()) { it.path }
        when (val verdict = FileOpGuard.check(request, names, directories)) {
            is FileOpVerdict.Refused -> refused = verdict.reason
            FileOpVerdict.Allowed -> scope.launch {
                val outcome = withContext(Dispatchers.IO) { executeFileOperation(request) }
                cache.invalidateAll()
                refreshToken++
                toast(outcomeText(outcome, request.sources.size.coerceAtLeast(1)))
            }
        }
    }

    /**
     * مهمة خلفية لنسخ/نقل عدة عناصر: **عنصرًا عنصرًا**.
     *
     * ولماذا عنصرًا عنصرًا لا نداءً واحدًا: لأن الإلغاء يجب أن يكون حقيقيًّا. `cp` واحدة على
     * عشرين عنصرًا لا تُقطع في المنتصف، فتصير «إلغاء» زرًّا يكذب. هنا يُقطع بين العناصر.
     *
     * والنسبة **مقيسة بالاستطلاع**: حجم العنصر في الوجهة يُقرأ كل فترة ما دام في الطيران،
     * والمجموع من أحجام القائمة. وإن سقط حجم واحد بقي التقدّم «غير معروف» (ADR-07).
     */
    fun startTransfer(kind: FileTaskKind, request: FileOpRequest, sourceSide: WindowSide) {
        val destination = request.destination.orEmpty()
        val sourceView = viewOf(sourceSide)
        val sizes = request.sources.map { path -> sourceView.entries.firstOrNull { it.path == path }?.sizeBytes }
        val total = if (sizes.all { it != null }) sizes.filterNotNull().sum() else null
        val id = FileTaskQueue.nextId(tasks)
        tasks = FileTaskQueue.add(
            tasks,
            FileTask(
                id = id,
                kind = kind,
                sources = request.sources,
                destination = destination,
                totalBytes = total,
                doneBytes = if (total == null) null else 0L,
                startedAtMs = System.currentTimeMillis(),
            ),
        )

        scope.launch {
            var done = 0L
            var failed = false
            for ((index, source) in request.sources.withIndex()) {
                if (id in cancelledTasks) break
                val single = request.copy(
                    sources = listOf(source),
                    renamed = mapOf(source to FileTargets.nameFor(source, request.renamed)),
                )
                val target = FileTargets.destinationFor(source, destination, request.renamed)
                val size = sizes.getOrNull(index)
                val poller = if (size != null && total != null) {
                    scope.launch {
                        while (isActive) {
                            delay(PROGRESS_POLL_MS)
                            val grown = withContext(Dispatchers.IO) { FileSystemEngine.nodeBytes(target) } ?: 0L
                            tasks = FileTaskQueue.progress(tasks, id, done + grown)
                        }
                    }
                } else {
                    null
                }
                val outcome = withContext(Dispatchers.IO) { executeFileOperation(single) }
                poller?.cancel()
                if (!outcome.ok) {
                    failed = true
                    break
                }
                done += size ?: 0L
                tasks = FileTaskQueue.progress(tasks, id, done)
            }
            // الإلغاء يُقرأ في النهاية أيضًا: من ألغى بينما آخر عنصر في الطيران لا يُقال له
            // إن العملية «تمّت».
            val state = when {
                id in cancelledTasks -> FileTaskState.Cancelled
                failed -> FileTaskState.Failed
                else -> FileTaskState.Done
            }
            tasks = FileTaskQueue.finish(tasks, id, state, System.currentTimeMillis())
            cancelledTasks = cancelledTasks - id
            cache.invalidateAll()
            refreshToken++
        }
    }

    fun pasteIntoActive() {
        val clip = fileClipboard ?: return
        if (FileClipboardRules.pointlessHere(clip, window.path)) {
            toast(context.getString(R.string.max_files_clipboard_same_folder))
            return
        }
        val request = FileClipboardRules.pasteRequest(clip, window.path)
        val destinationNames = view.entries.mapTo(HashSet()) { it.name }
        val scan = FileConflictRules.scan(clip.sources.map { FileBrowser.nameOf(it) }, destinationNames)
        if (scan.hasCollisions) {
            conflict = ConflictRequest(scan.collisions, destinationNames, request)
            return
        }
        startTransfer(
            if (clip.mode == ClipboardMode.Cut) FileTaskKind.Move else FileTaskKind.Copy,
            request,
            clipboardOriginSide ?: windows.active,
        )
        fileClipboard = clip.afterPaste()
    }

    /** تعارض الأسماء: الخطة تُحسب على الدفعة كاملة، ثم تُنفَّذ بمهمة واحدة. */
    fun resolveConflict(choice: ConflictChoice, forAll: Boolean) {
        val pending = conflict ?: return
        val effective = if (forAll) FileConflictRules.forAll(choice) else choice
        val sourceNames = pending.request.sources.map { FileBrowser.nameOf(it) }
        val plan = FileConflictRules.plan(sourceNames, pending.destinationNames, effective)
        conflict = null
        if (plan == null || plan.cancel) return

        val byName = pending.request.sources.associateBy { FileBrowser.nameOf(it) }
        val renamed = plan.rename.mapNotNull { (old, fresh) -> byName[old]?.let { it to fresh } }.toMap()
        val proceed = plan.proceed.mapNotNull { byName[it] }
        if (proceed.isNotEmpty() || renamed.isNotEmpty()) {
            startTransfer(
                if (pending.request.operation == FileOperation.Move) FileTaskKind.Move else FileTaskKind.Copy,
                pending.request.copy(sources = proceed + renamed.keys, renamed = renamed),
                clipboardOriginSide ?: windows.active,
            )
        }
        if (plan.skip.isNotEmpty()) {
            toast(context.getString(R.string.max_files_conflict_skipped, plan.skip.size))
        }
        fileClipboard = fileClipboard?.afterPaste()
    }

    /** المحرّر: يُفتح بالقراءة أولًا، ويقول امتناعه إن كان ثنائيًّا أو أكبر من الحدّ. */
    fun openEditor(entry: FileEntry) {
        editor = EditorState(path = entry.path)
        scope.launch {
            val preview = withContext(Dispatchers.IO) { FileSystemEngine.preview(entry.path) }
            val text = (preview as? TextPreview.Ready)?.content.orEmpty()
            editor = editor?.copy(preview = preview, text = text, savedText = text)
        }
    }

    /**
     * تسليم ملف لتطبيق آخر — **بالتخزين المشترك وحده**.
     *
     * ولماذا الحدّ: `FileProvider` يشارك ما تُعلنه `file_paths.xml`، وهي هنا التخزين المشترك.
     * وملف في `/data` أو `/system` لا يُسلَّم لتطبيق آخر — ويُقال ذلك بنصّه بدل انهيار
     * `FileUriExposed` أو مشاركة مسار لا يُقرأ.
     */
    fun handOff(entry: FileEntry) {
        if (!entry.path.startsWith(SHARED_STORAGE_PREFIX)) {
            toast(context.getString(R.string.max_files_open_external_local))
            return
        }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileOpenPlan.extensionOf(entry.name))
            ?: "*/*"
        val launched = runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                java.io.File(entry.path),
            )
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.isSuccess
        if (!launched) toast(context.getString(R.string.max_files_open_failed))
    }

    /** مصير النقرة العادية: مجلد يُدخل · نصّ يُفتح في المحرّر · ملف يُسلَّم لتطبيق آخر. */
    fun openEntry(entry: FileEntry) {
        when (FileOpenPlan.routeOf(entry)) {
            FileOpenRoute.EnterFolder -> go(windows.active, entry.path)
            FileOpenRoute.TextEditor -> openEditor(entry)
            FileOpenRoute.External -> handOff(entry)
        }
    }

    fun perform(action: FileAction, side: WindowSide) {
        val chosen = selectedEntries(side)
        val sidePath = windows.of(side).path
        when (action) {
            FileAction.Copy -> {
                FileClipboardRules.of(ClipboardMode.Copy, chosen.map { it.path }, sidePath)?.let { fresh ->
                    fileClipboard = fresh
                    clipboardOriginSide = side
                }
                updateView(side) { it.copy(selecting = false, selection = FileSelection()) }
            }
            FileAction.Move -> {
                FileClipboardRules.of(ClipboardMode.Cut, chosen.map { it.path }, sidePath)?.let { fresh ->
                    fileClipboard = fresh
                    clipboardOriginSide = side
                }
                updateView(side) { it.copy(selecting = false, selection = FileSelection()) }
            }
            FileAction.Rename -> {
                rename = chosen.singleOrNull()
                inputDraft = rename?.name.orEmpty()
            }
            FileAction.Details -> properties = chosen.singleOrNull()?.let(PropertiesState::of)
            FileAction.Delete -> deleteTargets = chosen.map { it.path }
            FileAction.Clear -> updateView(side) { it.copy(selecting = false, selection = FileSelection()) }
            FileAction.Compress, FileAction.Extract ->
                immediateRequest(action, chosen, sidePath)?.let { guardAndRun(it, side) }
        }
    }

    // ── القراءة ───────────────────────────────────────────────────────────────

    LaunchedEffect(refreshToken) {
        rootGranted = withContext(Dispatchers.IO) { RootMount.granted() }
    }

    LaunchedEffect(windows.first.path, refreshToken) {
        loadListing(windows.first.path, cache) { fresh ->
            firstView = firstView.copy(listing = fresh, loading = false)
        }
    }

    LaunchedEffect(windows.second.path, refreshToken) {
        loadListing(windows.second.path, cache) { fresh ->
            secondView = secondView.copy(listing = fresh, loading = false)
        }
    }

    LaunchedEffect(windows.first.path, refreshToken) {
        firstDisk = withContext(Dispatchers.IO) { FileSystemEngine.diskSpace(windows.first.path) }
    }

    LaunchedEffect(windows.second.path, refreshToken) {
        secondDisk = withContext(Dispatchers.IO) { FileSystemEngine.diskSpace(windows.second.path) }
    }

    LaunchedEffect(window.path, refreshToken) {
        mountAccess = withContext(Dispatchers.IO) { RootMount.currentAccess(window.path) }
    }

    // التحديد المعلَّق (من نتائج البحث) يُطبَّق حين يظهر المدخل فعلًا في القائمة.
    LaunchedEffect(view.listing, pendingSelect) {
        val target = pendingSelect ?: return@LaunchedEffect
        if (view.entries.any { it.path == target }) {
            updateView(windows.active) { it.copy(selecting = true, selection = FileSelection(setOf(target))) }
            pendingSelect = null
        }
    }

    BackHandler(enabled = menuAnchor != null) { menuAnchor = null }
    BackHandler(enabled = menuAnchor == null && view.selecting) {
        updateView(windows.active) { it.copy(selecting = false, selection = FileSelection()) }
    }
    BackHandler(enabled = menuAnchor == null && !view.selecting && results != null) { results = null }
    BackHandler(enabled = menuAnchor == null && !view.selecting && results == null && window.canGoBack) {
        windows = windows.back(windows.active)
    }

    // ── الأوامر ───────────────────────────────────────────────────────────────

    val screenCommands = fileManagerScreenCommands(
        window = window,
        resultsVisible = results != null,
        onSort = { sort -> windows = windows.with(windows.active, window.withSort(sort)) },
        onToggleHidden = {
            val toggled = window.toggleHidden()
            windows = windows.with(windows.active, toggled)
            updateView(windows.active) { it.copy(selecting = false).pruned(toggled, nowEpoch()) }
        },
        onAddBookmark = { bookmarks = FileBookmarks.add(bookmarks, window.path) },
        onSelectAll = {
            updateView(windows.active) {
                it.copy(selecting = true, selection = FileSelection().selectAll(it.visible(window, nowEpoch())))
            }
        },
        onInvertSelection = {
            updateView(windows.active) {
                it.copy(
                    selecting = true,
                    selection = it.selection.invert(it.visible(window, nowEpoch())),
                )
            }
        },
        onClearResults = { results = null },
        onTerminal = { navController.navigate(MaxDestination.Terminal.route) },
        onHelp = { helpOpen = true },
    )

    val windowCommands = fileManagerWindowCommands(
        window = window,
        onRenameWindow = {
            inputDraft = window.title.orEmpty()
            renameWindowOpen = true
        },
        onSetHome = { windows = windows.with(windows.active, window.setHome()) },
        onGoHome = { go(windows.active, window.homePath) },
        onOpenInOther = { windows = windows.openInOther(windows.active, window.path) },
        onSwap = { windows = windows.swap() },
    )

    val contextCommands: List<MaxCommand> = if (menuAnchor == null) {
        emptyList()
    } else {
        fileManagerContextCommands(
            entries = view.entries,
            selection = view.selection,
            onOpen = ::openEntry,
            onAction = { action -> perform(action, windows.active) },
        )
    }

    val callbacks = FileManagerCallbacks(
        onInput = { inputDraft = it },
        onRename = { name ->
            rename?.let { entry ->
                guardAndRun(FileOpRequest(FileOperation.Rename, sources = listOf(entry.path), newName = name))
            }
            rename = null
        },
        onRenameWindow = { title ->
            windows = windows.with(windows.active, window.withTitle(title))
            renameWindowOpen = false
        },
        onNewFolder = { name ->
            guardAndRun(FileOpRequest(FileOperation.CreateDirectory, destination = window.path, newName = name))
            newFolderOpen = false
        },
        onNewFile = { name ->
            guardAndRun(FileOpRequest(FileOperation.CreateFile, destination = window.path, newName = name))
            newFileOpen = false
        },
        onDelete = {
            deleteTargets?.let { targets -> guardAndRun(FileOpRequest(FileOperation.Delete, sources = targets)) }
            deleteTargets = null
        },
        onPath = { raw ->
            pathEditOpen = false
            val target = FileBrowser.normalize(raw)
            if (target.isNotEmpty()) go(windows.active, target)
        },
        onProperties = { next -> properties = next },
        onLoadSelinux = {
            properties?.let { current ->
                properties = current.copy(loadingSelinux = true)
                scope.launch {
                    val found = withContext(Dispatchers.IO) { FileSystemEngine.selinuxContext(current.entry.path) }
                    properties = properties?.copy(selinux = found, loadingSelinux = false)
                }
            }
        },
        onLoadApk = {
            properties?.let { current ->
                properties = current.copy(loadingApk = true)
                scope.launch {
                    val facts = withContext(Dispatchers.IO) {
                        ApkInspector.inspect(current.entry.path, context.packageManager)
                    }
                    properties = properties?.copy(apk = facts, loadingApk = false)
                }
            }
        },
        onToggleBit = { accessScope, bit -> properties = properties?.toggled(accessScope, bit) },
        onApplyPermissions = {
            properties?.let { current ->
                guardAndRun(
                    FileOpRequest(
                        operation = FileOperation.ChangePermissions,
                        sources = listOf(current.entry.path),
                        newName = current.octal,
                    ),
                )
            }
        },
        onApplyOwner = {
            properties?.let { current ->
                val spec = FilePermissionRules.ownerSpec(current.owner, current.group)
                if (spec == null) {
                    refused = FileOpRefusal.InvalidName
                } else {
                    guardAndRun(
                        FileOpRequest(
                            operation = FileOperation.ChangeOwner,
                            sources = listOf(current.entry.path),
                            newName = spec,
                        ),
                    )
                }
            }
        },
        onCopyPath = { value ->
            // النسخ يُقاس: الواجهة الحديثة قد تفشل، وقول «نُسخ» بلا قياس هو الكذب نفسه الذي
            // نمنعه في كل شاشة.
            scope.launch {
                val copied = runCatching {
                    clipboardApi.setClipEntry(
                        ClipEntry(
                            ClipData.newPlainText(
                                context.getString(R.string.max_files_detail_copy_label),
                                value,
                            ),
                        ),
                    )
                }.isSuccess
                toast(
                    context.getString(
                        if (copied) R.string.max_files_detail_copied else R.string.max_files_detail_copy_failed,
                    ),
                )
            }
        },
        onEditor = { next -> editor = next },
        onSaveEditor = {
            editor?.let { current ->
                val request = FileOpRequest(
                    operation = FileOperation.WriteText,
                    sources = listOf(current.path),
                    content = current.text,
                )
                when (val verdict = FileOpGuard.check(request)) {
                    is FileOpVerdict.Refused -> {
                        editor = current.copy(verdict = context.getString(refusalText(verdict.reason)))
                    }
                    FileOpVerdict.Allowed -> {
                        editor = current.copy(saving = true, verdict = null)
                        scope.launch {
                            val outcome = withContext(Dispatchers.IO) { executeFileOperation(request) }
                            editor = editor?.copy(
                                saving = false,
                                savedText = if (outcome.ok) current.text else editor?.savedText.orEmpty(),
                                verdict = outcomeText(outcome, 1),
                            )
                            cache.invalidateAll()
                            refreshToken++
                        }
                    }
                }
            }
        },
        onReloadEditor = {
            editor?.let { current ->
                scope.launch {
                    val preview = withContext(Dispatchers.IO) { FileSystemEngine.preview(current.path) }
                    val text = (preview as? TextPreview.Ready)?.content.orEmpty()
                    editor = editor?.copy(preview = preview, text = text, savedText = text, verdict = null)
                }
            }
        },
        onSearch = { next -> search = next },
        onStartSearch = {
            FileSearchPlan.of(search.root.ifBlank { window.path }, search.query, search.limits)?.let { request ->
                searchCancelled.set(false)
                search = search.copy(running = true, outcome = null, startedAtMs = System.currentTimeMillis())
                results = null
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        FileSearchEngine.search(
                            request = request,
                            lister = { FileSystemEngine.list(it) },
                            isCancelled = { searchCancelled.get() },
                        )
                    }
                    search = search.copy(running = false, outcome = outcome)
                    results = outcome
                }
            }
        },
        onCancelSearch = {
            searchCancelled.set(true)
            search = search.copy(running = false)
        },
        onConflict = { choice, forAll -> resolveConflict(choice, forAll) },
        onDismiss = { dialog ->
            when (dialog) {
                FileDialog.Rename -> rename = null
                FileDialog.RenameWindow -> renameWindowOpen = false
                FileDialog.NewFolder -> newFolderOpen = false
                FileDialog.NewFile -> newFileOpen = false
                FileDialog.Delete -> deleteTargets = null
                FileDialog.Path -> pathEditOpen = false
                FileDialog.Refused -> refused = null
                FileDialog.Properties -> properties = null
                FileDialog.Editor -> editor = null
                FileDialog.Search -> search = search.copy(open = false)
                FileDialog.Conflict -> conflict = null
                FileDialog.Help -> helpOpen = false
            }
        },
    )

    // ── التركيب ──────────────────────────────────────────────────────────────

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onGloballyPositioned { coordinates -> rootSize = coordinates.size },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val split = FileWindowsRule.sideBySide(with(density) { maxWidth.toPx() })

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                FileWindowTabs(
                    windows = windows,
                    onSelect = { side -> activate(side) },
                    menu = windowCommands,
                    menuDescription = stringResource(R.string.max_files_window_menu_cd),
                )

                // الشاشة المفردة تحمل مسار النافذة النشطة وسطر حالتها مرّة واحدة. وفي العرض
                // المزدوج يحمل كل شقّ مساره وحالته بنفسه، فلا يُكرَّر الإعلان.
                if (!split) {
                    FilePathBar(
                        path = window.path,
                        canGoBack = window.canGoBack,
                        onBack = { windows = windows.back(windows.active) },
                        onEditPath = {
                            inputDraft = window.path
                            pathEditOpen = true
                        },
                        onSearch = { search = SearchState(open = true, root = window.path, query = view.query) },
                        menu = screenCommands,
                        menuDescription = stringResource(R.string.max_files_menu_cd),
                    )

                    FileStatusLine(
                        counts = view.counts,
                        disk = if (windows.active == WindowSide.First) firstDisk else secondDisk,
                        loading = view.loading,
                    )
                }

                PaneBody(
                    windows = windows,
                    split = split,
                    viewOf = ::viewOf,
                    diskOf = { side -> if (side == WindowSide.First) firstDisk else secondDisk },
                    rootGranted = rootGranted,
                    results = results,
                    onActivate = ::activate,
                    onOpenEntry = { entry ->
                        if (results != null) {
                            val parent = FileBrowser.parentOf(entry.path)
                            results = null
                            if (parent != null) {
                                pendingSelect = entry.path
                                go(windows.active, parent)
                            }
                        } else {
                            openEntry(entry)
                        }
                    },
                    onToggleSelection = { side, entry ->
                        updateView(side) { it.copy(selecting = true, selection = it.selection.toggle(entry.path)) }
                    },
                    onLongPress = { side, entry, anchor ->
                        val current = viewOf(side)
                        val onSelection = entry.path in current.selection.paths
                        updateView(side) {
                            it.copy(
                                selecting = true,
                                selection = if (onSelection) it.selection else FileSelection(setOf(entry.path)),
                            )
                        }
                        activate(side)
                        menuAnchor = anchor
                    },
                    onBack = { side -> windows = windows.back(side) },
                    onEditPath = { side ->
                        activate(side)
                        inputDraft = windows.of(side).path
                        pathEditOpen = true
                    },
                    onSearch = { side ->
                        activate(side)
                        search = SearchState(open = true, root = windows.of(side).path, query = viewOf(side).query)
                    },
                    menu = screenCommands,
                    menuDescription = stringResource(R.string.max_files_menu_cd),
                    onRetry = { refreshToken++ },
                    modifier = Modifier.weight(1f),
                )

                fileClipboard?.let { clip ->
                    FileClipboardBar(
                        count = clip.count,
                        origin = clip.origin,
                        onPaste = { pasteIntoActive() },
                        onClear = {
                            fileClipboard = null
                            clipboardOriginSide = null
                        },
                    )
                }

                if (tasks.isNotEmpty()) {
                    FileTaskStrip(
                        tasks = tasks,
                        labelFor = { task -> stringResource(taskLabel(task.kind)) },
                        detailFor = ::taskDetail,
                        onCancel = { id ->
                            cancelledTasks = cancelledTasks + id
                            tasks = FileTaskQueue.cancel(tasks, id, System.currentTimeMillis())
                        },
                        onClearFinished = { tasks = FileTaskQueue.clearFinished(tasks) },
                    )
                }

                if (view.selecting) {
                    FileSelectionBar(
                        count = view.selection.count,
                        onCopy = { perform(FileAction.Copy, windows.active) },
                        onCut = { perform(FileAction.Move, windows.active) },
                        onDelete = { perform(FileAction.Delete, windows.active) },
                        onRename = { perform(FileAction.Rename, windows.active) },
                        menu = FileActionSet.forSelection(view.entries, view.selection)
                            .filter { it != FileAction.Copy && it != FileAction.Move }
                            .map { action ->
                                MaxCommand(
                                    label = stringResource(actionLabel(action)),
                                    icon = actionIcon(action),
                                    destructive = action.destructive,
                                    onSelect = { perform(action, windows.active) },
                                )
                            },
                        menuDescription = stringResource(R.string.max_files_action_more_cd),
                        onClear = { perform(FileAction.Clear, windows.active) },
                    )
                } else {
                    FileToolBar(
                        upEnabled = FileBrowser.parentOf(window.path) != null,
                        onUp = { FileBrowser.parentOf(window.path)?.let { parent -> go(windows.active, parent) } },
                        onRefresh = {
                            cache.invalidateAll()
                            refreshToken++
                        },
                        onNewFolder = {
                            inputDraft = ""
                            newFolderOpen = true
                        },
                        onSearch = { search = SearchState(open = true, root = window.path, query = view.query) },
                        onSelect = { updateView(windows.active) { it.copy(selecting = true) } },
                        onDrawer = { drawerOpen = true },
                    )
                }
            }
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))

        MaxDrawer(
            open = drawerOpen,
            title = stringResource(R.string.max_files_title),
            onClose = { drawerOpen = false },
            closeDescription = stringResource(R.string.max_files_drawer_close),
        ) {
            FileDrawerContent(
                path = window.path,
                crumbs = FileBrowser.breadcrumbs(window.path),
                bookmarks = bookmarks,
                history = history,
                hiddenShown = window.showHidden,
                mountAccess = mountAccess,
                runningTasks = FileTaskQueue.running(tasks).size,
                onNavigate = { target ->
                    drawerOpen = false
                    go(windows.active, target)
                },
                onAddBookmark = { bookmarks = FileBookmarks.add(bookmarks, window.path) },
                onRemoveBookmark = { path -> bookmarks = FileBookmarks.remove(bookmarks, path) },
                onClearHistory = { history = FileHistory.clear() },
                onToggleHidden = { windows = windows.with(windows.active, window.toggleHidden()) },
                onRemount = { readWrite ->
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) { RootMount.remount(window.path, readWrite) }
                        mountAccess = withContext(Dispatchers.IO) { RootMount.currentAccess(window.path) }
                        toast(outcomeText(outcome, 1))
                    }
                },
                onTerminal = {
                    drawerOpen = false
                    navController.navigate(MaxDestination.Terminal.route)
                },
            )
        }

        MaxContextMenu(
            visible = menuAnchor != null,
            anchor = menuAnchor ?: Offset.Zero,
            commands = contextCommands,
            onDismiss = { menuAnchor = null },
            container = rootSize,
        )

        FileManagerDialogs(
            windows = windows,
            rename = rename,
            renameWindowOpen = renameWindowOpen,
            newFolderOpen = newFolderOpen,
            newFileOpen = newFileOpen,
            deleteTargets = deleteTargets,
            pathEditOpen = pathEditOpen,
            inputDraft = inputDraft,
            refused = refused,
            properties = properties,
            editor = editor,
            search = search,
            conflict = conflict,
            helpOpen = helpOpen,
            callbacks = callbacks,
        )
    }
}

/**
 * جسم المنطقة الوسطى: نافذة واحدة، أو نافذتان جنبًا إلى جنب على شاشة عريضة.
 *
 * والحكم من [FileWindowsRule] (يُمرَّر جاهزًا) لا من شرط مكتوب هنا: القاعدة تُقاس في JVM،
 * وخطؤها يوقع قائمتين كسولتين في صندوق لا يتّسعهما.
 */
private const val SHARED_STORAGE_PREFIX = "/sdcard"
