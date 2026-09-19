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
 * `GAP-09` + `FM-01` — مدير الملفات بالجذر، **بلوحين**.
 *
 * القرارات الحاكمة، وكلها مما يميّزها عن «عارض مجلدات»:
 *
 * 1. **القراءة معلنة.** المدخلات من تمريرة `stat` واحدة بصيغة معلنة، وما لم يُقس يظهر
 *    «مجهولًا» لا صفرًا، والصفوف التي لم تُفهم تُعلَن بعددها. و`ls` **لا يُحلَّل للحصول
 *    على بيانات أبدًا** — الدرس مأخوذ من `MaterialFiles` حرفيًّا.
 * 2. **كل عملية محروسة قبل الـshell.** ومع لوحين يصير الحرس أهمّ لا أقلّ: الوجهة صارت
 *    **مجلدًا اختاره المستخدم في اللوح الآخر**، وهذا هو الشكل الذي يظهر فيه «نسخ مجلد
 *    داخل نفسه». و`FileOpGuard` هو من يحكم — لا تكرار للقواعد هنا.
 * 3. **النتيجة ثلاثة أحكام لا حكمان:** «نُفِّذ وتُحقِّق منه» ≠ «نُفِّذ ولم يُتحقّق» ≠ «فشل».
 * 4. **اللوح النشط معلن.** كل إجراء في الشريط يُطبَّق على اللوح النشط وحده، وهو مُعلَّم
 *    بإطار أعرض ولون — فاللون وحده ليس جوابًا لمن لا يراه.
 * 5. **السلاسة:** ذاكرة مجلدات LRU — المجلد المزار يُعرض من الذاكرة في الإطار نفسه ثم
 *    تُقرأ نسخته الطازجة في الخلفية، فلا يُستبدل المحتوى بمؤشّر تحميل عند الرجوع.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package nd.max.ui.subscreens

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.ui.component.FileDetailsPanel
import nd.max.ui.component.FilePaneColumn
import nd.max.ui.component.FilePreviewPanel
import nd.max.ui.component.FileSelectionBar
import nd.max.ui.component.SelinuxState
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxConfirmDialog
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxInputDialog
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxScreen
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSplitScreen
import nd.max.ui.design.MaxTone
import nd.max.ui.design.MaxViewMenu
import nd.max.ui.design.container
import nd.max.ui.design.content
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.util.DirectoryCache
import nd.max.ui.util.DirectoryListing
import nd.max.ui.util.DualPane
import nd.max.ui.util.EventLog
import nd.max.ui.util.FileAction
import nd.max.ui.util.FileActionSet
import nd.max.ui.util.FileArchive
import nd.max.ui.util.FileBrowser
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileFormat
import nd.max.ui.util.FileOpGuard
import nd.max.ui.util.FileOpOutcome
import nd.max.ui.util.FileOpRefusal
import nd.max.ui.util.FileOpRequest
import nd.max.ui.util.FileOpVerdict
import nd.max.ui.util.FileOperation
import nd.max.ui.util.executeFileOperation
import nd.max.ui.util.FilePaneState
import nd.max.ui.util.FileSelection
import nd.max.ui.util.FileSort
import nd.max.ui.util.FileSortKey
import nd.max.ui.util.FileSystemEngine
import nd.max.ui.util.PaneLayout
import nd.max.ui.util.PaneLayoutRule
import nd.max.ui.util.PaneSide
import nd.max.ui.util.RootUtils
import nd.max.ui.util.TextPreview

/** عرض يليه الانقسام: تحته يُرصّ اللوحان فوق بعضهما بدل أن يتضايقا. */
private val SplitThreshold = 600.dp

/** اللوحة المفتوحة بدل اللوحين: تفاصيل أو معاينة. */
private sealed interface FilePanel {
    data class Details(val entry: FileEntry) : FilePanel
    data class Preview(val entry: FileEntry) : FilePanel
}

/** حفظ حالة اللوح عبر إعادة التركيب، وإلا فقد المستخدم مساره بمجرّد تدوير الجهاز. */
private val PaneSaver = listSaver<FilePaneState, Any>(
    save = {
        listOf(
            it.path,
            it.query,
            it.sort.key.name,
            it.sort.ascending,
            it.sort.directoriesFirst,
            it.selecting,
            ArrayList(it.selection.paths),
        )
    },
    restore = {
        FilePaneState(
            path = it[0] as String,
            query = it[1] as String,
            sort = FileSort(
                key = FileSortKey.valueOf(it[2] as String),
                ascending = it[3] as Boolean,
                directoriesFirst = it[4] as Boolean,
            ),
            selecting = it[5] as Boolean,
            selection = FileSelection((it[6] as ArrayList<*>).filterIsInstance<String>().toSet()),
        )
    },
)

/** طلب نسخ/نقل: أيّ لوح طلبه، وماذا، وإلى أين. */
private data class TransferRequest(
    val operation: FileOperation,
    val sources: List<String>,
    val from: PaneSide,
)

@Composable
fun FileManagerScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val context = LocalContext.current

    val cache = remember { DirectoryCache() }
    var left by rememberSaveable(stateSaver = PaneSaver) { mutableStateOf(FilePaneState(path = "/")) }
    var right by rememberSaveable(stateSaver = PaneSaver) { mutableStateOf(FilePaneState(path = "/sdcard")) }
    var active by rememberSaveable { mutableStateOf(PaneSide.Left) }
    // ترتيب اللوحين وقرار الربط يُحفظان: من ضبط لوحينه جنبًا إلى جنب على هاتفه يعيد
    // الضبط في كل دخول إن لم يُحفظا، وهو أول ما يُشكى منه في هذه الشاشة.
    var layout by rememberSaveable { mutableStateOf(PaneLayout.Auto) }
    var linked by rememberSaveable { mutableStateOf(false) }
    var rootGranted by remember { mutableStateOf<Boolean?>(null) }
    var panel by remember { mutableStateOf<FilePanel?>(null) }
    var selinux by remember { mutableStateOf<SelinuxState>(SelinuxState.NotQueried) }
    var previewContent by remember { mutableStateOf<TextPreview?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf<FileOpRefusal?>(null) }

    var renameTarget by remember { mutableStateOf<Pair<PaneSide, FileEntry>?>(null) }
    var createFolderIn by remember { mutableStateOf<PaneSide?>(null) }
    var input by remember { mutableStateOf("") }
    var deleteTargets by remember { mutableStateOf<Pair<PaneSide, List<String>>?>(null) }
    var transfer by remember { mutableStateOf<TransferRequest?>(null) }
    var destination by remember { mutableStateOf("") }
    var pathEditSide by remember { mutableStateOf<PaneSide?>(null) }
    var pathInput by remember { mutableStateOf("") }

    val leftHistory = remember { mutableStateListOf<String>() }
    val rightHistory = remember { mutableStateListOf<String>() }

    fun paneOf(side: PaneSide) = if (side == PaneSide.Left) left else right
    fun historyOf(side: PaneSide) = if (side == PaneSide.Left) leftHistory else rightHistory
    fun setPane(side: PaneSide, next: FilePaneState) {
        if (side == PaneSide.Left) left = next else right = next
    }

    val activePane = paneOf(active)
    val otherSide = active.other
    val otherPane = paneOf(otherSide)

    val refresh: (PaneSide) -> Unit = { side ->
        val requested = paneOf(side)
        val cached = cache.get(requested.path)
        // إن كان المجلد في الذاكرة فلا نستبدل المحتوى بمؤشّر تحميل: القائمة تبقى مرئية
        // وتُستبدل عند وصول القراءة الطازجة — وهذا الفرق هو الإحساس بالسلاسة كلّه.
        setPane(side, if (cached != null) requested.withListing(cached) else requested.copy(loading = true))
        scope.launch {
            val fresh = withContext(Dispatchers.IO) { FileSystemEngine.list(requested.path) }
            cache.put(fresh)
            // حرس القراءة العالقة: نتيجة قراءة مجلد غادرناه أثناء القراءة **لا تُكتب عليه**،
            // وإلا عرض لوحٌ محتوى مجلد آخر.
            if (paneOf(side).path == requested.path) setPane(side, paneOf(side).withListing(fresh))
        }
    }

    LaunchedEffect(Unit) {
        rootGranted = withContext(Dispatchers.IO) {
            runCatching { RootUtils.isRootGranted() }.getOrDefault(false)
        }
    }
    LaunchedEffect(left.path) { refresh(PaneSide.Left) }
    LaunchedEffect(right.path) { refresh(PaneSide.Right) }

    val navigate: (PaneSide, String, Boolean) -> Unit = { side, target, push ->
        val before = paneOf(side)
        if (push) historyOf(side).add(before.path)
        setPane(side, before.at(target))
        active = side

        // Breadcrumb navigation used to bypass the link entirely.  That made the
        // two panes appear linked until the first tap on a breadcrumb, then silently
        // diverge.  Ancestor navigation is deterministic: move the other pane one
        // parent for each breadcrumb jump; never invent a child path that was not read.
        if (linked && target != before.path) {
            val twin = side.other
            val other = paneOf(twin)
            val mirrored = when {
                target == FileBrowser.parentOf(before.path) -> DualPane.mirrorParent(other)
                FileBrowser.isInside(before.path, target) ->
                    DualPane.mirrorAncestor(other, before.path, target)
                else -> other.entries.firstOrNull { it.path == target }?.path
            }
            mirrored?.let {
                historyOf(twin).add(other.path)
                setPane(twin, other.at(it))
            }
        }
    }

    /**
     * التنقّل المرتبط — يُنقل اللوح الآخر إلى **المجلد ذي الاسم نفسه** من مساره هو،
     * أو يبقى مكانه إن لم يُوجد. والانتقال يدفع سجل اللوح الآخر أيضًا، فيعمل الرجوع فيه.
     *
     * ولا يُطبَّق على القفز المطلق (شريط الأثر): القفز إلى `/sdcard/Download` لا مقابل
     * اسميّ له في اللوح الآخر، فمقابلته بمسار مخمَّن هي بالضبط ما نمنعه.
     */
    val mirrorIntoOther: (PaneSide, FileEntry) -> Unit = { side, entry ->
        if (linked) {
            // «الآخر» يُحسب من **اللوح الذي تحرّك** لا من النشط: النقر قد يقع في اللوح
            // غير النشط، وحينها لو حُسب من النشط لكُتب المسار على اللوح المنقور نفسه.
            val twin = side.other
            val other = paneOf(twin)
            DualPane.mirrorFolder(other, entry)?.let { target ->
                historyOf(twin).add(other.path)
                setPane(twin, other.at(target))
            }
        }
    }

    val mirrorUpInOther: (PaneSide) -> Unit = { side ->
        if (linked) {
            val twin = side.other
            val other = paneOf(twin)
            DualPane.mirrorParent(other)?.let { target ->
                historyOf(twin).add(other.path)
                setPane(twin, other.at(target))
            }
        }
    }

    val goUp: (PaneSide) -> Unit = { side ->
        val current = paneOf(side)
        FileBrowser.parentOf(current.path)?.let { parent ->
            val history = historyOf(side)
            if (history.lastOrNull() == parent) history.removeAt(history.lastIndex)
            setPane(side, current.at(parent))
            // الصعود **نسبي** مثل الدخول: خطوة واحدة للأعلى في اللوحين معًا.
            mirrorUpInOther(side)
        }
    }

    // رجوع النظام ليس «صعودًا».  MT-style navigation must return to the exact
    // folder the user came from, even when that folder is not the direct parent.
    val goBack: (PaneSide) -> Unit = { side ->
        val history = historyOf(side)
        history.removeLastOrNull()?.let { previous ->
            setPane(side, paneOf(side).at(previous))
            if (linked) {
                val twin = side.other
                val other = paneOf(twin)
                DualPane.mirrorAncestor(other, other.path, previous)?.let { target ->
                    historyOf(twin).add(other.path)
                    setPane(twin, other.at(target))
                }
            }
        }
    }

    val showOutcome: (FileOpOutcome, Int) -> Unit = { outcome, count ->
        val message = when {
            outcome.ok -> context.getString(R.string.max_files_outcome_ok) + " " +
                context.getString(R.string.max_files_outcome_count, count)
            outcome.executed -> context.getString(R.string.max_files_outcome_unverified)
            else -> context.getString(R.string.max_files_outcome_failed)
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
        // عملية وقعت على القرص تُبطل الذاكرة كلها، ثم يُحدَّث اللوحان معًا: اللوح الآخر
        // قد يكون هو المقصد، وإبقاءه قديمًا يُنتج قائمة تُكذّب ما حدث للتوّ.
        cache.invalidateAll()
        refresh(PaneSide.Left)
        refresh(PaneSide.Right)
    }

    /**
     * تنفيذ عملية على اللوح `side`. الحرس يُسأل بأسماء ومدخلات **لوح الوجهة**، لأن
     * تعارض الأسماء يقع حيث تُكتب، لا حيث قُرئت.
     */
    val runOperation: (PaneSide, FileOpRequest) -> Unit = { side, request ->
        val pane = paneOf(side)
        val names = pane.entries.map { it.name }.toSet()
        val directories = pane.entries.filter { it.isDirectory }.map { it.path }.toSet()
        when (val verdict = FileOpGuard.check(request, names, directories)) {
            is FileOpVerdict.Refused -> refused = verdict.reason
            FileOpVerdict.Allowed -> scope.launch {
                val outcome = withContext(Dispatchers.IO) { executeFileOperation(request) }
                showOutcome(outcome, request.sources.size.coerceAtLeast(1))
            }
        }
    }

    val openPanel: (FilePanel) -> Unit = { target ->
        selinux = SelinuxState.NotQueried
        previewContent = null
        panel = target
    }

    // حرس الرجوع بترتيب الأولوية: اللوحة · تحديد اللوح النشط · سجل اللوح النشط.
    BackHandler(enabled = panel != null) { panel = null }
    BackHandler(enabled = panel == null && activePane.selecting) {
        setPane(active, activePane.clearSelection())
    }
    BackHandler(enabled = panel == null && !activePane.selecting && historyOf(active).isNotEmpty()) {
        goBack(active)
    }

    when (val open = panel) {
        is FilePanel.Details -> {
            MaxScreen(
                title = stringResource(R.string.max_files_details_title),
                subtitle = open.entry.name,
                onBack = { panel = null },
                accentIcon = MaxDestination.FileManager.icon,
                accent = MaxTone.Neutral.content(),
            ) {
                FileDetailsPanel(
                    entry = open.entry,
                    selinux = selinux,
                    onCheckSelinux = {
                        selinux = SelinuxState.Loading
                        scope.launch {
                            val found = withContext(Dispatchers.IO) {
                                FileSystemEngine.selinuxContext(open.entry.path)
                            }
                            selinux = found?.let { SelinuxState.Found(it) } ?: SelinuxState.NotReported
                        }
                    },
                    onCopyPath = {
                        // النتيجة تُقاس لا تُفترض: الواجهة الحديثة تُعلّق (suspend) فقد تفشل،
                        // وقول «نُسخ» بلا قياس هو بالضبط الكذب الذي نمنعه في كل شاشة.
                        scope.launch {
                            val copied = runCatching {
                                clipboard.setClipEntry(
                                    ClipEntry(
                                        ClipData.newPlainText(
                                            context.getString(R.string.max_files_detail_copy_label),
                                            open.entry.path,
                                        )
                                    )
                                )
                            }.isSuccess
                            snackbarHostState.showSnackbar(
                                context.getString(
                                    if (copied) R.string.max_files_detail_copied
                                    else R.string.max_files_detail_copy_failed
                                )
                            )
                        }
                    },
                )
            }
            return
        }

        is FilePanel.Preview -> {
            LaunchedEffect(open.entry.path) {
                previewLoading = true
                previewContent = withContext(Dispatchers.IO) { FileSystemEngine.preview(open.entry.path) }
                previewLoading = false
            }
            MaxScreen(
                title = stringResource(R.string.max_files_preview_title),
                subtitle = open.entry.name,
                onBack = { panel = null },
                accentIcon = MaxDestination.FileManager.icon,
                accent = MaxTone.Neutral.content(),
                condition = if (previewLoading) {
                    MaxCondition(
                        kind = MaxConditionKind.Loading,
                        title = stringResource(R.string.max_files_preview_loading_title),
                        detail = stringResource(
                            R.string.max_files_preview_loading_detail,
                            FileFormat.size(FileSystemEngine.MAX_PREVIEW_BYTES).orEmpty(),
                        ),
                    )
                } else {
                    null
                },
            ) {
                FilePreviewPanel(name = open.entry.name, preview = previewContent)
            }
            return
        }

        null -> Unit
    }

    val rootCondition = if (rootGranted == false) {
        MaxCondition(
            kind = MaxConditionKind.RootRequired,
            title = stringResource(R.string.max_files_cond_no_root_title),
            detail = stringResource(R.string.max_files_cond_no_root_detail),
        )
    } else {
        null
    }

    MaxSplitScreen(
        title = stringResource(R.string.max_files_title),
        subtitle = "${activePane.path}  →  ${otherPane.path}",
        onBack = { navController.popBackStack() },
        accentIcon = MaxDestination.FileManager.icon,
        accent = MaxTone.Neutral.content(),
        condition = rootCondition,
        snackbarHostState = snackbarHostState,
        actions = {
            MaxViewMenu(
                labels = FileSortKey.entries.map { stringResource(sortLabel(it)) },
                selectedIndex = FileSortKey.entries.indexOf(activePane.sort.key),
                contentDescription = stringResource(R.string.max_files_sort_cd),
                // القائمة بأسماء فقط: أيقونة «مجدول/موسّع» بجانب «الاسم/الحجم» لا معنى لها.
                icons = emptyList(),
                triggerIcon = Icons.AutoMirrored.Rounded.Sort,
                onSelect = { index ->
                    val key = FileSortKey.entries[index]
                    val next = if (activePane.sort.key == key) {
                        activePane.sort.copy(ascending = !activePane.sort.ascending)
                    } else {
                        activePane.sort.copy(key = key)
                    }
                    setPane(active, activePane.copy(sort = next))
                },
            )
            MaxViewMenu(
                labels = listOf(
                    stringResource(R.string.max_files_layout_auto),
                    stringResource(R.string.max_files_layout_side),
                    stringResource(R.string.max_files_layout_stack),
                ),
                selectedIndex = PaneLayout.entries.indexOf(layout),
                contentDescription = stringResource(R.string.max_files_layout_cd),
                icons = listOf(
                    Icons.Rounded.AspectRatio,
                    Icons.Rounded.ViewColumn,
                    Icons.Rounded.ViewAgenda,
                ),
                onSelect = { index -> layout = PaneLayout.entries[index] },
            )
            IconButton(onClick = { linked = !linked }) {
                Icon(
                    imageVector = if (linked) Icons.Rounded.Link else Icons.Rounded.LinkOff,
                    contentDescription = stringResource(
                        if (linked) R.string.max_files_unlink_cd else R.string.max_files_link_cd
                    ),
                    // اللون **مع** الرمز: الرمز وحده لا يُقرأه من لا يميّز الأشكال الدقيقة.
                    tint = if (linked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            MaxHelpAction(
                title = stringResource(R.string.max_files_help_title),
                body = stringResource(R.string.max_files_help_body),
            )
        },
    ) {
        ActivePaneStrip(
            side = active,
            pane = activePane,
            linked = linked,
            onSync = { setPane(otherSide, DualPane.syncOther(activePane, otherPane)) },
            onBack = { goBack(active) },
            onSwap = {
                val (newLeft, newRight) = DualPane.swap(left, right)
                left = newLeft
                right = newRight
            },
            onRefresh = { refresh(active) },
            onUp = { goUp(active) },
            onNewFolder = {
                input = ""
                createFolderIn = active
            },
            onToggleSelect = {
                setPane(
                    active,
                    if (activePane.selecting) activePane.clearSelection() else activePane.copy(selecting = true),
                )
            },
        )

        if (activePane.selecting) {
            FileSelectionBar(
                count = activePane.selection.count,
                labelFor = { count -> stringResource(R.string.max_files_selected_count, count) },
                // الإجراءات المنطبقة تُحسب في النموذج: زرّ لا يعد بما لا يمكن فعله.
                actions = FileActionSet.forSelection(activePane.entries, activePane.selection),
                onAction = { action ->
                    onSelectionAction(
                        side = active,
                        action = action,
                        pane = activePane,
                        onOpenPanel = openPanel,
                        onRename = { entry ->
                            input = entry.name
                            renameTarget = active to entry
                        },
                        onDelete = { deleteTargets = active to it },
                        onTransfer = { operation, sources ->
                            // الوجهة الافتراضية هي اللوح الآخر — وهذا هو معنى اللوحين:
                            // لا يكتب المستخدم مسارًا ولا ينسخ إلى مكان لم يره.
                            destination = otherPane.path
                            transfer = TransferRequest(operation, sources, active)
                        },
                        onRun = { targetSide, request -> runOperation(targetSide, request) },
                        onClear = { setPane(active, activePane.clearSelection()) },
                    )
                },
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = MaxSpace.sm),
        ) {
            // كل لوح يُوصَّل **مرّة واحدة**، ولا يُكتب وِراؤه مرتين حسب اتجاه التخطيط.
            // قبل هذا كان سلوك النقر مكتوبًا في **أربعة مواضع** (لوحان × لفّتان)، فأي تغيير
            // فيه يجب أن يُكتب أربع مرّات — ومن ينسى واحدًا يُنتج لوحين يتصرّفان بشكلين.
            // والأربعة كانوا متطابقين، فالتكرار لم يشترِ شيئًا غير خطر النسيان.
            val renderPane: @Composable (PaneSide, Modifier) -> Unit = { side, modifier ->
                val pane = paneOf(side)
                FilePaneColumn(
                    side = side,
                    state = pane,
                    // اللوح النشط يُعلن بالإطار واللون **معًا** لا باللون وحده.
                    active = active == side,
                    linked = linked,
                    modifier = modifier,
                    onActivate = { active = side },
                    onPathEdit = {
                        pathInput = paneOf(side).path
                        pathEditSide = side
                    },
                    onNavigate = { navigate(side, it, true) },
                    // القراءة الطازجة عند الإدخال (`paneOf`) لا الملتقطة عند التركيب،
                    // وإلا كتب بحث اللوح في لقطة قديمة بعد أن تغيّر مجلده.
                    onQueryChange = { query -> setPane(side, paneOf(side).copy(query = query)) },
                    onSearchChange = { filter ->
                        val pane = paneOf(side)
                        // تغيير المرشّح يغيّر المجموعة المعروضة، فالتحديد يُعاد إلى ما هو معروض:
                        // اختيار اختفى بالترشيح ثم نُسخ بلا أن يُرى هو أسوأ ما يمكن أن يفعله
                        // مرشّح في مدير ملفات.
                        setPane(
                            side,
                            pane.copy(
                                search = filter,
                                selection = FileSelection(pane.selection.paths intersect pane.visible().map { it.path }.toSet()),
                            ),
                        )
                    },
                    onSelectAllResults = {
                        val pane = paneOf(side)
                        // **النتائج** لا المجلد: من بحث ثم ضغط «حدّد الكل» يقصد ما يراه.
                        setPane(side, pane.copy(selecting = true, selection = pane.selection.selectAll(pane.visible())))
                    },
                    onInvertResults = {
                        val pane = paneOf(side)
                        setPane(side, pane.copy(selecting = true, selection = pane.selection.invert(pane.visible())))
                    },
                    onEntryClick = { entry ->
                        when {
                            paneOf(side).selecting -> setPane(side, paneOf(side).toggleSelection(entry.path))
                            entry.isDirectory -> {
                                // الربط **قبل** الانتقال: اللوح الآخر يُنقل انطلاقًا من مساره
                                // هو، لا انطلاقًا من مسار اللوح المنقور بعد تغييره.
                                mirrorIntoOther(side, entry)
                                navigate(side, entry.path, true)
                            }

                            else -> openPanel(FilePanel.Preview(entry))
                        }
                    },
                    onEntryLongPress = { setPane(side, paneOf(side).toggleSelection(it.path)) },
                )
            }

            // الوضع الصريح يتقدّم على قياس الشاشة: من اختار «جنبًا إلى جنب» على هاتف ضيّق
            // يريده كذلك، ومن اختار «فوق/تحت» لا يُجبَر على عمودين ضيّقين. والقرار في نموذج
            // يُختبر لا في شرط داخل التركيب.
            if (PaneLayoutRule.sideBySide(layout, maxWidth.value, SplitThreshold.value)) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(MaxSpace.sm),
                ) {
                    renderPane(PaneSide.Left, Modifier.weight(1f))
                    renderPane(PaneSide.Right, Modifier.weight(1f))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(MaxSpace.sm),
                ) {
                    renderPane(PaneSide.Left, Modifier.weight(1f))
                    renderPane(PaneSide.Right, Modifier.weight(1f))
                }
            }
        }
    }

    // ── الحوارات ────────────────────────────────────────────────────────────

    val rename = renameTarget
    val renameVerdict = rename?.let { (side, entry) ->
        FileOpGuard.check(
            FileOpRequest(FileOperation.Rename, sources = listOf(entry.path), newName = input),
            paneOf(side).entries.map { it.name }.toSet(),
            paneOf(side).entries.filter { it.isDirectory }.map { it.path }.toSet(),
        )
    }
    MaxInputDialog(
        visible = rename != null,
        title = stringResource(R.string.max_files_rename_title),
        fieldLabel = stringResource(R.string.max_files_rename_field),
        value = input,
        onValueChange = { input = it },
        confirmLabel = stringResource(R.string.max_files_confirm),
        supportingText = refusalTextOrNull(renameVerdict),
        onConfirm = {
            if (rename != null) runOperation(
                rename.first,
                FileOpRequest(FileOperation.Rename, sources = listOf(rename.second.path), newName = input),
            )
            renameTarget = null
        },
        onDismiss = { renameTarget = null },
    )

    val folderSide = createFolderIn
    val folderVerdict = folderSide?.let { side ->
        FileOpGuard.check(
            FileOpRequest(
                FileOperation.CreateDirectory,
                destination = paneOf(side).path,
                newName = input,
            ),
            paneOf(side).entries.map { it.name }.toSet(),
            paneOf(side).entries.filter { it.isDirectory }.map { it.path }.toSet(),
        )
    }
    MaxInputDialog(
        visible = folderSide != null,
        title = stringResource(R.string.max_files_new_folder_title),
        fieldLabel = stringResource(R.string.max_files_new_folder_field),
        value = input,
        onValueChange = { input = it },
        confirmLabel = stringResource(R.string.max_files_confirm),
        supportingText = refusalTextOrNull(folderVerdict),
        onConfirm = {
            if (folderSide != null) runOperation(
                folderSide,
                FileOpRequest(
                    FileOperation.CreateDirectory,
                    destination = paneOf(folderSide).path,
                    newName = input,
                ),
            )
            createFolderIn = null
        },
        onDismiss = { createFolderIn = null },
    )

    val transferRequest = transfer
    val transferVerdict = transferRequest?.let {
        FileOpGuard.check(
            FileOpRequest(it.operation, sources = it.sources, destination = destination),
            otherPane.entries.map { entry -> entry.name }.toSet(),
            otherPane.entries.filter { entry -> entry.isDirectory }.map { entry -> entry.path }.toSet(),
        )
    }
    MaxInputDialog(
        visible = transferRequest != null,
        title = stringResource(
            if (transferRequest?.operation == FileOperation.Move) R.string.max_files_move_title
            else R.string.max_files_copy_title
        ),
        fieldLabel = stringResource(R.string.max_files_destination_field),
        value = destination,
        onValueChange = { destination = it },
        confirmLabel = stringResource(R.string.max_files_confirm),
        placeholder = stringResource(R.string.max_files_destination_hint),
        supportingText = refusalTextOrNull(transferVerdict),
        confirmEnabled = destination.isNotBlank(),
        onConfirm = {
            if (transferRequest != null) runOperation(
                transferRequest.from.other,
                FileOpRequest(
                    operation = transferRequest.operation,
                    sources = transferRequest.sources,
                    destination = destination,
                ),
            )
            transfer = null
        },
        onDismiss = { transfer = null },
    )

    val deletion = deleteTargets
    MaxConfirmDialog(
        visible = deletion != null,
        title = stringResource(R.string.max_files_delete_title),
        message = stringResource(R.string.max_files_delete_body, deletion?.second?.size ?: 0),
        confirmLabel = stringResource(R.string.max_files_action_delete),
        destructive = true,
        onConfirm = {
            if (deletion != null) runOperation(
                deletion.first,
                FileOpRequest(FileOperation.Delete, sources = deletion.second),
            )
            deleteTargets = null
        },
        onDismiss = { deleteTargets = null },
    )

    val pathSide = pathEditSide
    MaxInputDialog(
        visible = pathSide != null,
        title = stringResource(R.string.max_files_edit_path_title),
        fieldLabel = stringResource(R.string.max_files_edit_path_field),
        value = pathInput,
        onValueChange = { pathInput = it },
        confirmLabel = stringResource(R.string.max_files_confirm),
        placeholder = stringResource(R.string.max_files_destination_hint),
        confirmEnabled = pathInput.isNotBlank(),
        onConfirm = {
            pathSide?.let { navigate(it, pathInput.trim(), true) }
            pathEditSide = null
        },
        onDismiss = { pathEditSide = null },
    )

    val refusal = refused
    MaxConfirmDialog(
        visible = refusal != null,
        title = stringResource(R.string.max_files_refused_title),
        message = refusal?.let { refusalText(it) }.orEmpty(),
        confirmLabel = stringResource(R.string.max_files_cancel),
        onConfirm = { refused = null },
        onDismiss = { refused = null },
    )
}

/**
 * شريط إجراءات اللوح النشط.
 *
 * وُجد لأنه في العرض المنقسم لا يتّسع لكل لوح شريط إجراءات خاصّ به، ولا يصحّ أن تُخلط
 * إجراءات لوحين في شريط واحد بلا إعلان أيّهما يُقصد. فالشريط يسمّي اللوح النشط، ثم
 * يحمل إجراءاته الأربعة.
 */
@Composable
private fun ActivePaneStrip(
    side: PaneSide,
    pane: FilePaneState,
    linked: Boolean,
    onSync: () -> Unit,
    onBack: () -> Unit,
    onSwap: () -> Unit,
    onRefresh: () -> Unit,
    onUp: () -> Unit,
    onNewFolder: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val tone = MaxTone.Accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MaxSpace.sm)
            // الشريط صار يحمل فعلين إضافيين (مزامنة المسار · تبديل اللوحين) بعد أن
            // انتقل الترتيب والربط إلى الشريط العلوي. والتمرير الأفقي يضمن ألا يُقصّ
            // فعل على هاتف ضيّق بدل أن يُضغط بعضه بعضًا — وهو أسوأ من التمرير.
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaxSpace.xs),
    ) {
        Surface(
            shape = RoundedCornerShape(MaxRadius.pill),
            color = tone.container(),
        ) {
            Text(
                text = stringResource(
                    if (side == PaneSide.Left) R.string.max_files_pane_left else R.string.max_files_pane_right
                ),
                modifier = Modifier.padding(horizontal = MaxSpace.sm, vertical = MaxSpace.hairline),
                style = MaterialTheme.typography.labelLarge,
                color = tone.content(),
            )
        }
        StripAction(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            description = stringResource(R.string.max_files_back_cd),
            onClick = onBack,
        )
        StripAction(
            icon = Icons.Rounded.ArrowUpward,
            description = stringResource(R.string.max_files_up_cd),
            onClick = onUp,
        )
        StripAction(
            icon = Icons.Rounded.Sync,
            description = stringResource(R.string.max_files_refresh_cd),
            onClick = onRefresh,
        )
        StripAction(
            icon = Icons.Rounded.CreateNewFolder,
            description = stringResource(R.string.max_files_action_new_folder),
            onClick = onNewFolder,
        )
        StripAction(
            // `AutoMirrored`: سهم التبديل/المقارنة اتجاهي، فيجب أن ينقلب في العربية
            // — والقائمة غير المنقلبة تُنتج زرًّا يشير إلى الجهة الخاطئة في RTL.
            icon = if (pane.selecting) Icons.AutoMirrored.Rounded.CompareArrows else Icons.Rounded.SelectAll,
            description = stringResource(R.string.max_files_select_cd),
            onClick = onToggleSelect,
        )
        StripAction(
            icon = Icons.Rounded.Sync,
            description = stringResource(R.string.max_files_sync_panes_cd),
            onClick = onSync,
        )
        StripAction(
            icon = Icons.Rounded.SwapHoriz,
            description = stringResource(R.string.max_files_swap_panes_cd),
            onClick = onSwap,
        )
        if (linked) {
            // الربط يُقال بالكلام أيضًا: من يفتح هذه الشاشة بلا رؤية للأيقونة الصغيرة
            // في رأس كل لوح يحتاج جملة واحدة تقول إن اللوحين يتحركان معًا.
            Text(
                text = stringResource(R.string.max_files_linked_hint),
                modifier = Modifier.padding(start = MaxSpace.xs),
                style = MaterialTheme.typography.labelSmall,
                color = tone.content(),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StripAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(MaxSize.iconContainer)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * إجراءات شريط التحديد — دالّة واحدة تأخذ الحالة وترجع الأفعال، فتبقى الشاشة قابلة
 * للقراءة ولا تتفرّع قائمة `when` داخل تركيب الواجهة.
 */
private fun onSelectionAction(
    side: PaneSide,
    action: FileAction,
    pane: FilePaneState,
    onOpenPanel: (FilePanel) -> Unit,
    onRename: (FileEntry) -> Unit,
    onDelete: (List<String>) -> Unit,
    onTransfer: (FileOperation, List<String>) -> Unit,
    onRun: (PaneSide, FileOpRequest) -> Unit,
    onClear: () -> Unit,
) {
    val sources = pane.selection.paths.toList().sorted()
    // المدخلات المختارة فعلًا، لا المسارات وحدها: نوع المدخل (أرشيف؟) يحدّد الإجراء.
    val chosen = pane.entries.filter { it.path in pane.selection.paths }
    when (action) {
        FileAction.Copy -> onTransfer(FileOperation.Copy, sources)
        FileAction.Move -> onTransfer(FileOperation.Move, sources)
        FileAction.Delete -> onDelete(sources)
        FileAction.Clear -> onClear()
        FileAction.Compress -> {
            val first = chosen.firstOrNull() ?: return
            val archive = FileBrowser.childPath(pane.path, FileArchive.archiveNameFor(first.name))
            onRun(side, FileOpRequest(FileOperation.Compress, sources = sources, destination = archive))
        }
        // الفكّ يذهب إلى **مجلد اللوح الحالي**: هذا أين ينظر المستخدم، لا حوار وجهة
        // إضافي لعملية أمنها منخفض ونتيجتها مرئية فورًا.
        FileAction.Extract -> {
            val archive = chosen.singleOrNull() ?: return
            onRun(
                side,
                FileOpRequest(
                    operation = FileOperation.Extract,
                    sources = listOf(archive.path),
                    destination = pane.path,
                )
            )
        }
        FileAction.Rename -> chosen.singleOrNull()?.let(onRename) ?: return
        FileAction.Details -> onOpenPanel(FilePanel.Details(chosen.singleOrNull() ?: return))
    }
}

@Composable
private fun sortLabel(key: FileSortKey): Int = when (key) {
    FileSortKey.Name -> R.string.max_files_sort_name
    FileSortKey.Size -> R.string.max_files_sort_size
    FileSortKey.Modified -> R.string.max_files_sort_date
    FileSortKey.Kind -> R.string.max_files_sort_kind
}

/** نصّ سبب الرفض، أو `null` إن كان الطلب مقبولًا. */
@Composable
private fun refusalTextOrNull(verdict: FileOpVerdict?): String? =
    (verdict as? FileOpVerdict.Refused)?.let { refusalText(it.reason) }

@Composable
private fun refusalText(reason: FileOpRefusal): String = when (reason) {
    FileOpRefusal.EmptySelection -> stringResource(R.string.max_files_refuse_empty)
    FileOpRefusal.ProtectedPath -> stringResource(R.string.max_files_refuse_protected)
    FileOpRefusal.SelfTarget -> stringResource(R.string.max_files_refuse_self)
    FileOpRefusal.TargetInsideSource -> stringResource(R.string.max_files_refuse_inside)
    FileOpRefusal.InvalidName -> stringResource(R.string.max_files_refuse_name)
    FileOpRefusal.NameTaken -> stringResource(R.string.max_files_refuse_taken)
}
