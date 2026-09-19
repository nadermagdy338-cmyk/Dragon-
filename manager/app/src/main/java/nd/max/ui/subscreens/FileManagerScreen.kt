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
 * `GAP-09` + `FM-01` + `FM-02` — مدير الملفات بالجذر، **بلوحين**.
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
 * 4. **اللوح النشط معلن.** كل إجراء في الشريط السفلي يُطبَّق على اللوح النشط وحده، وهو
 *    مُعلَّم بإطار أعرض **وبحبّة اسمه** — فاللون وحده ليس جوابًا لمن لا يراه.
 * 5. **السلاسة:** ذاكرة مجلدات LRU — المجلد المزار يُعرض من الذاكرة في الإطار نفسه ثم
 *    تُقرأ نسخته الطازجة في الخلفية، فلا يُستبدل المحتوى بمؤشّر تحميل عند الرجوع.
 *
 * ### جولة FM-02 — إعادة بناء الواجهة (طلب المالك): الشاشة كما تُستعمل لا كما تُوصف
 *
 * نصّ الطلب: «أعِد كتابة شاشة مدير الملفات … أريدها كمثل لقطات الشاشة هذه» (مدير ملفات
 * مرجعي بأشرطة تبويبات وشريط مسار وسطر حالة وصفوف كثيفة وشريط أوامر سفلي). والبنية
 * القديمة كانت **تضاعف الصفوف وتُشتّت الإجراءات**:
 *
 * - كل لوح كان يحمل: حبّة اسم · مسارًا · فتات خبز · حقل بحث دائم · ثلاث قوائم مرشّح ·
 *   سطر أفعال نتائج — أربعة أشرطة قبل أن تبدأ قائمة الملفات.
 * - الإجراءات كانت موزّعة على ثلاثة أماكن (شريط الشاشة، شريط اللوح، شريط التحديد) بأيقونات
 *   بلا أسماء، فمن لا يعرف الرمز لا يعرف الفعل.
 * - كل صفّ كان سطرين: الاسم، ثم `مجلد · ١٫٦ KB · drwxr-xr-x · 9/19/26` مجموعةً — الصلاحيات
 *   في كل صفّ بينما تُسأل مرة واحدة عند الحاجة.
 *
 * والبنية الجديدة تكمل ما هو مُثبت (النموذج الخالص، والحرس، وذاكرة المجلدات) وتبدّل
 * **العرض** وحده:
 *
 * | الشريط | مكانه | ما يحمله |
 * | --- | --- | --- |
 * | شريط الشاشة | الأعلى | الفرز · قائمة أوامر الشاشة (مزامنة · مبادلة · ترتيب اللوحين · ربط · المخفيّ · الطرفية) · الشرح |
 * | شريط اللوح | الأسفل، حين لا تحديد | صعود · تحديث · مجلد جديد · بحث · تحديد الكل · مواقع سريعة |
 * | شريط التحديد | الأسفل، حين يوجد تحديد | العدد واللوح · نسخ · نقل · تسمية · حذف · وقائمة بقيّة الإجراءات بأسمائها |
 *
 * وفي اللوح نفسه: **شريط تبويباته · مساره · سطر حالته · قائمته** — ولا شيء آخر. والبحث
 * يُفتح بطلبه ويُغلق بإغلاقه بدل أن يستهلك سطرًا في كل لوح دائمًا. والملفات المخفيّة مخفيّة
 * افتراضيًّا كما في كل مدير ملفات، **وعددها معلن في سطر الحالة** فلا يُقرأ غيابها كحذف.
 */
package nd.max.ui.subscreens

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.SnackbarHostState
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
import nd.max.ui.component.FilePaneBar
import nd.max.ui.component.FilePaneColumn
import nd.max.ui.component.FilePreviewPanel
import nd.max.ui.component.FileSelectionBar
import nd.max.ui.component.SelinuxState
import nd.max.ui.component.fileRefusalText
import nd.max.ui.component.fileRefusalVerdictText
import nd.max.ui.component.fileSortLabel
import nd.max.ui.design.MaxCommand
import nd.max.ui.design.MaxCommandMenu
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxConfirmDialog
import nd.max.ui.design.MaxHelpAction
import nd.max.ui.design.MaxInputDialog
import nd.max.ui.design.MaxScreen
import nd.max.ui.design.MaxSpace
import nd.max.ui.design.MaxSplitScreen
import nd.max.ui.design.MaxTone
import nd.max.ui.design.MaxViewMenu
import nd.max.ui.design.content
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.util.DirectoryCache
import nd.max.ui.util.DiskSpace
import nd.max.ui.util.DualPane
import nd.max.ui.util.FileActionSet
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
import nd.max.ui.util.FileSearchFilters
import nd.max.ui.util.FileSelection
import nd.max.ui.util.FileSort
import nd.max.ui.util.FileSortKey
import nd.max.ui.util.FileSystemEngine
import nd.max.ui.util.PaneLayout
import nd.max.ui.util.PaneLayoutRule
import nd.max.ui.util.PaneSide
import nd.max.ui.util.PaneTabs
import nd.max.ui.util.RootUtils
import nd.max.ui.util.TextPreview

/** عرض يليه الانقسام: تحته يُرصّ اللوحان فوق بعضهما بدل أن يتضايقا. */
private val SplitThreshold = 600.dp

/**
 * حفظ حالة اللوح عبر إعادة التركيب، وإلا فقد المستخدم مساره بمجرّد تدوير الجهاز.
 *
 * والتبويبات وقرار إظهار المخفيّ يُحفظان معها: هما اختياران وقعا بيد المستخدم، وتدويره
 * للجهاز لا يعني أنه غيّر رأيه.
 */
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
            it.showHidden,
            ArrayList(it.tabs),
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
            showHidden = it[7] as Boolean,
            tabs = (it[8] as ArrayList<*>).filterIsInstance<String>(),
        )
    },
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
    var searchLeft by rememberSaveable { mutableStateOf(false) }
    var searchRight by rememberSaveable { mutableStateOf(false) }
    var rootGranted by remember { mutableStateOf<Boolean?>(null) }
    var panel by remember { mutableStateOf<FilePanel?>(null) }
    var selinux by remember { mutableStateOf<SelinuxState>(SelinuxState.NotQueried) }
    var previewContent by remember { mutableStateOf<TextPreview?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf<FileOpRefusal?>(null) }
    // مساحة نظام الملفات لكل لوح: تُقاس عند كل انتقال، و`null` يعني «لم تُقرأ» — وسطر
    // الحالة يقولها بهذه العبارة لا بصفر (ADR-23).
    var leftSpace by remember { mutableStateOf<DiskSpace?>(null) }
    var rightSpace by remember { mutableStateOf<DiskSpace?>(null) }

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

    fun searchOpenOf(side: PaneSide) = if (side == PaneSide.Left) searchLeft else searchRight
    fun setSearchOpen(side: PaneSide, value: Boolean) {
        if (side == PaneSide.Left) searchLeft = value else searchRight = value
    }

    /**
     * إغلاق البحث: يمسح الاستعلام **والمرشّح**. ولو بقي المرشّح بعد إغلاق الحقل لقُرئت
     * قائمة ناقصة بلا سبب ظاهر — وهو أسوأ من فقدان مرشّح لم يعد له حقل يراه المستخدم.
     */
    fun closeSearch(side: PaneSide) {
        setPane(side, paneOf(side).copy(query = "", search = FileSearchFilters.Filter()))
        setSearchOpen(side, false)
    }

    /**
     * تغيير ما يُعرض (بحث · مرشّح · إخفاء) يُعيد التحديد إلى ما هو معروض.
     *
     * اختيار اختفى بالترشيح ثم نُسخ بلا أن يُرى هو أسوأ ما يمكن أن يفعله مرشّح في مدير
     * ملفات — فيُقصّ التحديد عند كل تغيير يعيد تشكيل القائمة.
     */
    fun keepVisibleSelection(next: FilePaneState): FilePaneState =
        next.copy(selection = FileSelection(next.selection.paths intersect next.visible().map { it.path }.toSet()))

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
    // قراءة القائمة وقياس المساحة في تأثير واحد لكل لوح: كلاهما يخصّ المسار نفسه،
    // فلا يُقاس نظام ملفات انطلقنا منه.
    LaunchedEffect(left.path) {
        refresh(PaneSide.Left)
        leftSpace = withContext(Dispatchers.IO) { FileSystemEngine.diskSpace(left.path) }
    }
    LaunchedEffect(right.path) {
        refresh(PaneSide.Right)
        rightSpace = withContext(Dispatchers.IO) { FileSystemEngine.diskSpace(right.path) }
    }

    val navigate: (PaneSide, String, Boolean) -> Unit = { side, target, push ->
        val before = paneOf(side)
        // الانتقال إلى المجلد الذي نحن فيه ليس انتقالًا: إدخاله في السجل يُنتج زرّ رجوع
        // يوصل إلى المكان نفسه، فيبدو معطلًا وهو ليس كذلك.
        val moved = FileBrowser.normalize(target) != FileBrowser.normalize(before.path)
        if (push && moved) historyOf(side).add(before.path)
        setPane(side, before.at(target))
        active = side

        // Breadcrumb navigation used to bypass the link entirely.  That made the
        // two panes appear linked until the first tap on a breadcrumb, then silently
        // diverge.  Ancestor navigation is deterministic: move the other pane one
        // parent for each breadcrumb jump; never invent a child path that was not read.
        if (linked && moved) {
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
     * ولا يُطبَّق على القفز المطلق (المواقع السريعة): القفز إلى `/sdcard/Download` لا مقابل
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

    /** تثبيت المجلد الحالي كتبويب في اللوح — ومسحه لا يتكرّر (‏[PaneTabs.pin] تتكفّل بذلك). */
    val pinCurrentTab: (PaneSide) -> Unit = { side ->
        val pane = paneOf(side)
        setPane(side, pane.copy(tabs = PaneTabs.pin(pane.tabs, pane.path)))
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

    // الاسم القصير لا الطويل: شريط التحديد يحمل العدد واللوح والإجراءات معًا، و«اللوح الأيسر»
    // كاملةً تُقتطع على هاتف ضيّق فيضيع نصف الإعلان. والاسم الطويل يُقرأ مسموعًا في اللوح نفسه.
    val activePaneLabel = stringResource(
        if (active == PaneSide.Left) {
            R.string.max_files_side_left_short
        } else {
            R.string.max_files_side_right_short
        }
    )

    /** أوامر الشاشة: كل ما لا يخصّ لوحًا بعينه، بأسمائه لا برموزه. */
    val screenCommands = buildList {
        add(
            MaxCommand(
                label = stringResource(R.string.max_files_sync_panes_cd),
                icon = Icons.Rounded.Sync,
                onSelect = { setPane(otherSide, DualPane.syncOther(activePane, otherPane)) },
            )
        )
        add(
            MaxCommand(
                label = stringResource(R.string.max_files_swap_panes_cd),
                icon = Icons.Rounded.SwapHoriz,
                onSelect = {
                    val (swappedLeft, swappedRight) = DualPane.swap(left, right)
                    left = swappedLeft
                    right = swappedRight
                },
            )
        )
        PaneLayout.entries.forEach { mode ->
            add(
                MaxCommand(
                    label = stringResource(layoutLabel(mode)),
                    icon = layoutIcon(mode),
                    active = layout == mode,
                    onSelect = { layout = mode },
                )
            )
        }
        add(
            MaxCommand(
                label = stringResource(
                    if (linked) R.string.max_files_unlink_cd else R.string.max_files_link_cd
                ),
                icon = if (linked) Icons.Rounded.LinkOff else Icons.Rounded.Link,
                active = linked,
                onSelect = { linked = !linked },
            )
        )
        add(
            MaxCommand(
                // الاسم يقول ما سيحدث لا ما هو كائن: القائمة تعرض الفعل القادم.
                label = stringResource(
                    if (activePane.showHidden) R.string.max_files_hide_hidden else R.string.max_files_show_hidden
                ),
                active = activePane.showHidden,
                onSelect = {
                    val pane = paneOf(active)
                    setPane(active, keepVisibleSelection(pane.copy(showHidden = !pane.showHidden)))
                },
            )
        )
        add(
            MaxCommand(
                label = stringResource(R.string.max_files_open_terminal),
                icon = Icons.Filled.Terminal,
                onSelect = { navController.navigate(MaxDestination.Terminal.route) },
            )
        )
    }

    /** المواقع السريعة وأوامر اللوح: تُطبَّق على اللوح النشط لأنها تنقل **هذا** اللوح. */
    val paneCommands = buildList {
        quickLocations().forEach { location ->
            add(
                MaxCommand(
                    label = stringResource(location.labelRes),
                    onSelect = { navigate(active, location.path, true) },
                )
            )
        }
        add(
            MaxCommand(
                label = stringResource(R.string.max_files_tab_pin),
                icon = Icons.Rounded.Add,
                active = PaneTabs.isPinned(activePane.tabs, activePane.path),
                onSelect = { pinCurrentTab(active) },
            )
        )
        add(
            MaxCommand(
                label = stringResource(R.string.max_files_edit_path_title),
                icon = Icons.Rounded.Edit,
                onSelect = {
                    pathInput = paneOf(active).path
                    pathEditSide = active
                },
            )
        )
    }

    MaxSplitScreen(
        title = stringResource(R.string.max_files_title),
        onBack = { navController.popBackStack() },
        accentIcon = MaxDestination.FileManager.icon,
        accent = MaxTone.Neutral.content(),
        condition = rootCondition,
        snackbarHostState = snackbarHostState,
        actions = {
            MaxViewMenu(
                labels = FileSortKey.entries.map { stringResource(fileSortLabel(it)) },
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
            MaxCommandMenu(
                commands = screenCommands,
                contentDescription = stringResource(R.string.max_files_menu_cd),
                triggerIcon = Icons.Rounded.MoreVert,
            )
            MaxHelpAction(
                title = stringResource(R.string.max_files_help_title),
                body = stringResource(R.string.max_files_help_body),
            )
        },
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // كل لوح يُوصَّل **مرّة واحدة**، ولا يُكتب وِراؤه مرتين حسب اتجاه التخطيط.
            // قبل هذا كان سلوك النقر مكتوبًا في **أربعة مواضع** (لوحان × لفّتان)، فأي تغيير
            // فيه يجب أن يُكتب أربع مرّات — ومن ينسى واحدًا يُنتج لوحين يتصرّفان بشكلين.
            val renderPane: @Composable (PaneSide, Modifier) -> Unit = { side, modifier ->
                FilePaneColumn(
                    side = side,
                    state = paneOf(side),
                    // اللوح النشط يُعلن بالإطار واللون **معًا** لا باللون وحده.
                    active = active == side,
                    diskSpace = if (side == PaneSide.Left) leftSpace else rightSpace,
                    linked = linked,
                    searchOpen = searchOpenOf(side),
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
                        // الترشيح يُقاس على الحالة **الجديدة**: القصّ على المجموعة القديمة
                        // كان يُبقي تحديدًا لم يعد ظاهرًا بعد تطبيق المرشّح نفسه.
                        setPane(side, keepVisibleSelection(pane.copy(search = filter)))
                    },
                    onCloseSearch = { closeSearch(side) },
                    onPinCurrent = { pinCurrentTab(side) },
                    onRemoveTab = { path ->
                        val pane = paneOf(side)
                        setPane(side, pane.copy(tabs = PaneTabs.unpin(pane.tabs, path)))
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

        // شريط واحد في الأسفل يتبدّل بدوره: أدوات اللوح النشط، أو إجراءات التحديد. والتبديل
        // مقصود: التحديد عملية طارئة تُصلح لها الأوامر، واللوح حالة دائمة تُصلح لها الأدوات.
        if (activePane.selecting && activePane.selection.isNotEmpty) {
            FileSelectionBar(
                label = stringResource(R.string.max_files_selected_count, activePane.selection.count) +
                    "  ·  " + activePaneLabel,
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
                extra = listOf(
                    MaxCommand(
                        // «حدّد الكل» على **النتائج** لا على المجلد: من مرّر قائمة يحدّد ما يراها.
                        label = stringResource(
                            R.string.max_files_select_all_results,
                            activePane.visible().size.toString(),
                        ),
                        icon = Icons.Rounded.SelectAll,
                        onSelect = {
                            val pane = paneOf(active)
                            setPane(
                                active,
                                pane.copy(selecting = true, selection = pane.selection.selectAll(pane.visible())),
                            )
                        },
                    ),
                    MaxCommand(
                        label = stringResource(R.string.max_files_invert_results),
                        icon = Icons.AutoMirrored.Rounded.CompareArrows,
                        onSelect = {
                            val pane = paneOf(active)
                            setPane(
                                active,
                                pane.copy(selecting = true, selection = pane.selection.invert(pane.visible())),
                            )
                        },
                    ),
                ),
            )
        } else {
            FilePaneBar(
                searchOpen = searchOpenOf(active),
                modifier = Modifier.padding(top = MaxSpace.sm),
                onUp = { goUp(active) },
                onRefresh = { refresh(active) },
                onNewFolder = {
                    input = ""
                    createFolderIn = active
                },
                onToggleSearch = {
                    if (searchOpenOf(active)) closeSearch(active) else setSearchOpen(active, true)
                },
                onToggleSelect = {
                    // الزرّ **يحدّد الكل** لا «يدخل وضع التحديد»: وضع تحديد فارغ شاشةٌ لا
                    // تعرض شيئًا ولا تفعل شيئًا، ومن ضغطه يريد أن يحدّد. وإن لم يُمكن تحديد
                    // شيء (مجلد فارغ أو مرشّح لا يطابق) فالنتيجة إلغاء تحديد لا دخول في وضع.
                    val pane = paneOf(active)
                    val selectable = pane.visible()
                    setPane(
                        active,
                        if (pane.selecting || selectable.isEmpty()) {
                            pane.clearSelection()
                        } else {
                            pane.copy(selecting = true, selection = pane.selection.selectAll(selectable))
                        },
                    )
                },
                quickLocations = paneCommands,
            )
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
        supportingText = fileRefusalVerdictText(renameVerdict),
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
        supportingText = fileRefusalVerdictText(folderVerdict),
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
        supportingText = fileRefusalVerdictText(transferVerdict),
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
        message = refusal?.let { fileRefusalText(it) }.orEmpty(),
        confirmLabel = stringResource(R.string.max_files_cancel),
        onConfirm = { refused = null },
        onDismiss = { refused = null },
    )
}

