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
 * `MT-FM` — **نموذج النوافذ** الذي يحلّ محلّ نموذج اللوحين.
 *
 * ولماذا استُبدل اللوحان أصلًا: اللوحان كانا قرارًا صحيحًا في مكانه، لكنهما فرضا على
 * الشاشة أربعة أشرطة وثلاث مناطق أوامر قبل أول صفّ ملف. والمالك أراد تقليد MT: نافذة
 * واحدة ممتدّة، ومجلدان **مصطفّان كمفتاحين** لا كصندوقين متجاورين — فيبقى التنقّل بين
 * مجلدين بنقرة واحدة، ويُكسب عرض الشاشة كلّه لقائمة واحدة.
 *
 * والقواعد هنا خالصة بلا Compose ولا shell: التنقّل يسجّل التاريخ (فالرجوع يعود إلى
 * المجلد الذي أتى منه المستخدم حرفيًّا لا إلى الأب)، والمجلد نفسه لا يُدفع مرتين
 * (فلا زرّ رجوع يوصل إلى المكان نفسه)، والتسمية تخصّ النافذة لا المسار.
 */
package nd.max.ui.util

/** أيّ نافذة. النوع مغلق، فلا يُخترع «تبويب ثالث» في مكان آخر من الشاشة. */
enum class WindowSide {
    First,
    Second,
    ;

    val other: WindowSide get() = if (this == First) Second else First
}

object FileWindows {

    /**
     * البداية المعلنة: التخزين في النافذتين (قرار المالك)، ثم يُستعاد اختياره بعد ذلك.
     *
     * والبداية الثابتة وعدٌ لا تُخلفه: من فتح الشاشة أول مرة يجد نفسه في مكان يعرفه
     * لا في آخر مسار جرّبه أحد.
     */
    const val START_PATH: String = "/sdcard"

    /** نافذتان فقط — لا أكثر. الرقم قرار معلن لا صدفة في التركيب. */
    const val COUNT: Int = 2

    /** سقف تاريخ النافذة الواحدة: يكفي لساعات تنقّل ولا ينمو بلا حدّ. */
    const val HISTORY_LIMIT: Int = 200
}

/**
 * نافذة واحدة: مسار · سجل · تسمية · ترتيب · إخفاء · رئيسي.
 *
 * و`title` تسمية **يضعها المستخدم** (كما في MT) لتُفرّق النافذتين بمعنى («النظام» ·
 * «التحميل») بدل اسم مجلد قد يتكرّر في الاثنتين.
 */
data class FileWindowState(
    val path: String = FileWindows.START_PATH,
    val title: String? = null,
    val history: List<String> = emptyList(),
    val sort: FileSort = FileSort(),
    val showHidden: Boolean = false,
    val home: String? = null,
) {
    /** ما يُعرض في التبويب: تسمية المستخدم إن وُجدت، وإلا اسم آخر قطعة في المسار. */
    val label: String
        get() = title?.takeIf { it.isNotBlank() } ?: FileBrowser.nameOf(FileBrowser.normalize(path))

    val canGoBack: Boolean get() = history.isNotEmpty()

    /**
     * انتقال إلى مجلد. المجلد الذي نحن فيه **لا يُدفع** في السجل: دفعه يُنتج زرّ رجوع
     * يوصل إلى المكان نفسه، فيبدو معطّلًا وهو ليس كذلك.
     */
    fun at(target: String, push: Boolean = true): FileWindowState {
        val next = FileBrowser.normalize(target)
        if (next == FileBrowser.normalize(path)) return this
        val stacked = if (push) history + FileBrowser.normalize(path) else history
        return copy(path = next, history = stacked.takeLast(FileWindows.HISTORY_LIMIT))
    }

    /** الرجوع خطوة واحدة في التاريخ — لا صعودًا إلى الأب. */
    fun back(): FileWindowState {
        val previous = history.lastOrNull() ?: return this
        return copy(path = FileBrowser.normalize(previous), history = history.dropLast(1))
    }

    fun withTitle(title: String?): FileWindowState = copy(title = title?.takeIf { it.isNotBlank() })

    fun withSort(sort: FileSort): FileWindowState = copy(sort = sort)

    fun toggleHidden(): FileWindowState = copy(showHidden = !showHidden)

    /** «تعيين كرئيسي»: المكان الذي يُفتح عليه هذا التبويب عند الطلب، لا مكان إجباري. */
    fun setHome(target: String = path): FileWindowState = copy(home = FileBrowser.normalize(target))

    /** المجلد الذي يُفتح عند «الذهاب إلى الرئيسي» — وما لم يُعيَّن رئيسي فهو البداية المعلنة. */
    val homePath: String get() = home ?: FileWindows.START_PATH
}

/**
 * حالة النافذتين والنافذة النشطة.
 *
 * و«النشطة» ليست ترفًا بصريًّا: كل أمر يقع على نافذة بعينها، فشاشة لا تُعلن النشطة
 * تجعل المستخدم يخمّن أين سيقع لصقه — وهو أسوأ ما يمكن أن يخمّنه.
 */
data class FileWindowsState(
    val first: FileWindowState = FileWindowState(),
    val second: FileWindowState = FileWindowState(),
    val active: WindowSide = WindowSide.First,
) {
    fun of(side: WindowSide): FileWindowState = if (side == WindowSide.First) first else second

    fun with(side: WindowSide, state: FileWindowState): FileWindowsState =
        if (side == WindowSide.First) copy(first = state) else copy(second = state)

    val activeWindow: FileWindowState get() = of(active)

    fun activate(side: WindowSide): FileWindowsState = copy(active = side)

    /** انتقال داخل نافذة، والنافذة تصير نشطة — التنقّل يعني الانتباه. */
    fun navigate(side: WindowSide, target: String, push: Boolean = true): FileWindowsState =
        activate(side).with(side, of(side).at(target, push))

    fun back(side: WindowSide): FileWindowsState = activate(side).with(side, of(side).back())

    /**
     * فتح المجلد في النافذة **الأخرى**: هذا هو بديل «الانتقال إلى اللوح الآخر» في MT،
     * وهو الذي يجعل مقارنة مجلدين بلا نسخ مسار ولا كتابة يد.
     */
    fun openInOther(side: WindowSide, target: String): FileWindowsState =
        activate(side.other).with(side.other, of(side.other).at(target))

    /**
     * تبديل النافذتين: يتبادل الموضعان بكل ما فيهما (المسار والسجل والتسمية والترتيب).
     *
     * وتبديل **المسارين وحدهما** كان سيُبقي سجلًا في نافذة لا تخصّه، فيعود زرّ الرجوع
     * في نافذة إلى مجلد لم يزره المستخدم فيها.
     */
    fun swap(): FileWindowsState = copy(first = second, second = first)

    /** توحيد المجلد: النافذة الأخرى تُفتح على مسار النافذة النشطة. */
    fun syncOther(): FileWindowsState =
        with(active.other, of(active.other).at(activeWindow.path))

    /** الذهاب إلى «الرئيسي» في نافذة. */
    fun goHome(side: WindowSide): FileWindowsState =
        activate(side).with(side, of(side).at(of(side).homePath))
}

/**
 * شكل العرض: على الهاتف تبويبان وصفحتان، وعلى الشاشة العريضة النافذتان جنبًا إلى جنب.
 *
 * والقاعدة صافية وتُختبر لأن خطأها يقع في **التخطيط** لا في السلوك: قائمتان كسولتان
 * داخل صندوق لا يتّسعهما ترفعان استثناء قياس، على جهاز المستخدم لا في اختبار.
 */
object FileWindowsRule {

    const val MIN_WIDTH_FOR_SIDE_BY_SIDE: Float = 600f

    fun sideBySide(availableWidth: Float, threshold: Float = MIN_WIDTH_FOR_SIDE_BY_SIDE): Boolean =
        availableWidth >= threshold
}

/**
 * ترقيم حالة النوافذ لحفظها (تدوير الجهاز / عودة إلى الشاشة).
 *
 * والدالّتان **متسامحتان** في الطرفين: ما لا يُفهم يعود إلى الافتراضي ولا يرمي. حالة
 * محفوظة من نسخة أقدم يجب ألا تُسقط الشاشة — أصغر ما تفعله أن تعيد المستخدم إلى
 * البداية المعلنة.
 */
object FileWindowsCodec {

    private const val FIELD = "\u001F"
    private const val LIST = "\u001E"

    fun encode(state: FileWindowsState): List<String> = listOf(
        state.active.name,
        encodeWindow(state.first),
        encodeWindow(state.second),
    )

    private fun encodeWindow(window: FileWindowState): String = listOf(
        clean(window.path),
        clean(window.title.orEmpty()),
        clean(window.home.orEmpty()),
        window.sort.key.name,
        window.sort.ascending.toString(),
        window.sort.directoriesFirst.toString(),
        window.showHidden.toString(),
        window.history.joinToString(LIST) { clean(it) },
    ).joinToString(FIELD)

    /** الفواصل محجوزة: تُنزَع من القيم حتى لا يُقسَّم سطر واحد إلى نافذتين. */
    private fun clean(value: String): String = value.replace(FIELD, "").replace(LIST, "")

    fun decode(values: List<String>): FileWindowsState {
        if (values.size != 3) return FileWindowsState()
        val active = WindowSide.entries.firstOrNull { it.name == values[0] } ?: WindowSide.First
        return FileWindowsState(
            first = decodeWindow(values[1]),
            second = decodeWindow(values[2]),
            active = active,
        )
    }

    private fun decodeWindow(value: String): FileWindowState {
        val parts = value.split(FIELD)
        if (parts.size < 8) return FileWindowState()
        val path = parts[0].takeIf { it.isNotBlank() } ?: FileWindows.START_PATH
        val key = runCatching { FileSortKey.valueOf(parts[3]) }.getOrNull() ?: FileSortKey.Name
        val history = parts[7].split(LIST).filter { it.isNotBlank() }
        return FileWindowState(
            path = FileBrowser.normalize(path),
            title = parts[1].takeIf { it.isNotBlank() },
            home = parts[2].takeIf { it.isNotBlank() }?.let(FileBrowser::normalize),
            sort = FileSort(
                key = key,
                ascending = parts[4].toBooleanStrictOrNull() ?: true,
                directoriesFirst = parts[5].toBooleanStrictOrNull() ?: true,
            ),
            showHidden = parts[6].toBooleanStrictOrNull() ?: false,
            history = history.takeLast(FileWindows.HISTORY_LIMIT),
        )
    }
}
