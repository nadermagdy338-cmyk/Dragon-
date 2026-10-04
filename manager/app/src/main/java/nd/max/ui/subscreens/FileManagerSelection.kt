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
 * انتقالات **التحديد** كما تُطبَّق على نافذة، ومتى يُرفع التحديد بعد قائمة سياق أُغلقت.
 *
 * **ولماذا ملفٌّ لا سطورٌ في الشاشة:** `FileManagerScreen` كان عند ٩٧٤ سطرًا من سقف ١٠٠٠
 * المعلن في `tools/code_health.py`، فإضافة هذا السلوك فيه كانت ترفع دَين `oversized_files`
 * (وهو ما رصده سابقًا ونُقل شريط اللوح بسببه). والقاعدة في هذا المستودع: **تفكيك لا توسيع
 * سقف** — فيُخرج المنطق الذي **يُقرأ بمعزل ويُقاس بنفسه** إلى هنا.
 *
 * والاثنان المهمّان، وقد وصفهما المالك:
 *
 * 1. **العكس إلى الصفر خروجٌ من نمط التحديد** لا بقاءٌ فيه — فلا يبقى شريطٌ يقول «محدَّد ٠»
 *    بلا شيء محدَّد. والحدّ يُقاس حيث يقع التغيير ([FileSelection.toggle]) لا في شاشة تنتظر زرًّا.
 * 2. **ضغطٌ طويل بلا اختيار لا يُبقي تحديدًا**: من ضغط طويلًا ثم أُغلقت القائمة بلا أمر كان
 *    يبقى في نمط التحديد بلا سبب — وهي الحالة التي وصفها المالك بأنها «لا يختفي خيار التحديد».
 */
package nd.max.ui.subscreens

import androidx.compose.ui.geometry.Offset
import nd.max.ui.util.FileEntry
import nd.max.ui.util.FileSelection
import nd.max.ui.util.FileWindowState
import nd.max.ui.util.FileWindowsState
import nd.max.ui.util.WindowSide

/**
 * عكس تحديد مدخل — والعكس إلى الصفر يُطفئ نمط التحديد معه.
 *
 * والنقرة **تنقل المرساة** إلى هذا المدخل: من نقر ثم سحب يريد النطاق من آخر ما لمسه لا
 * من موضع نسيَه في القائمة.
 */
internal fun WindowView.toggledSelection(entry: FileEntry): WindowView {
    val next = selection.toggle(entry.path)
    return copy(selecting = next.isNotEmpty, selection = next, swipeAnchor = entry.path)
}

/**
 * سحب الصفّ جانبًا: **يحدّد** ولا يعكس التحديد.
 *
 * والفرق مقصود: السحب المتكرّر يمرّ على صفّ محدَّد فلا ينزعه من التحديد (وهو ما يوقع فيه من
 * ظنّ أن السحب «لمسة ثانية»). ورفع اليد نهاية الإيماءة، فلا حاجة إلى تأكيد.
 *
 * **والسحب الثاني وراءه نطاقٌ لا عنصر (`UX-06 ②`).** الحالة الأولى بلا مرساة: يُضاف
 * المدخل ويصير مرساة. وأمّا المسحوب بعدها فالمرساة معلومة، فيُضاف **كلُّ ما بينهما** في
 * الترتيب المعروض — وهو ما يفعله «حدّد من–إلى» في كل مدير ملفات، ولم يكن عندنا.
 *
 * **وحدُّه المعلن:** النطاق يُبنى على **الترتيب المعروض** ([ordered]) لا على ترتيب القراءة؛
 * ومرساة غابت عن العرض (مجلد آخر، أو اختفت بمرشّح) تُعاد إلى `null` فيبدأ السحب تحديدًا
 * جديدًا بدل أن يمدّ نطاقًا على صفوف لا يراها المستخدم.
 */
internal fun WindowView.swipedSelection(entry: FileEntry, ordered: List<String>): WindowView {
    val anchor = swipeAnchor?.takeIf { it in ordered }
    val next = if (anchor == null) {
        FileSelection(selection.paths + entry.path)
    } else {
        selection.withRange(ordered, anchor, entry.path)
    }
    return copy(selecting = true, selection = next, swipeAnchor = anchor ?: entry.path)
}

/**
 * سحب صفّ في نافذة بعينها: **يجلب الترتيب المعروض للنافذة** ثمّ يمرّ إلى [swipedSelection].
 *
 * ولماذا هنا لا في الشاشة: جلب الترتيب سطرٌ من الحساب لا من الرسم، وإبقاؤه خارج الشاشة
 * يُبقيها على سقف أسطرها (١٠٠٠) ويجعل الاختبار يمرّ من الطريق نفسه الذي تمرّ منه الشاشة.
 */
internal fun WindowView.swipeSelect(
    side: WindowSide,
    windows: FileWindowsState,
    entry: FileEntry,
): WindowView = swipedSelection(entry, visible(windows.of(side), nowEpoch()).map(FileEntry::path))

/**
 * تحديد مدخل واحد **صراحةً** — يُستعمل حين يقفز التطبيق إلى مدخل بعينه (فتح ملفّ من نتائج
 * البحث ثمّ الرجوع إلى مجلده). والمرساة تُوضع عليه: القفزة فعلٌ واحد، فلا تبقى مرساة قديمة
 * تمتدّ نطاقًا بعدها على صفوف لم يلمسها المستخدم في هذه الجلسة.
 */
internal fun WindowView.singleSelection(path: String): WindowView =
    copy(selecting = true, selection = FileSelection(setOf(path)), swipeAnchor = path)

/**
 * ضغط طويل: يُدخل نمط التحديد، **ويحفظ** ما بناه المستخدم إن كان المدخل محدَّدًا أصلًا.
 *
 * والمرساة تنتقل **فقط** إذا أنشأ الضغط الطويل تحديدًا؛ وضغطٌ على محدَّد لا يُزحزح ما اختاره
 * المستخدم بيده (وهو الفرق نفسه الذي يقرأه [selectionToClearAfterMenu] عند إغلاق القائمة).
 */
internal fun WindowView.longPressedSelection(entry: FileEntry, onSelection: Boolean): WindowView = copy(
    selecting = true,
    selection = if (onSelection) selection else FileSelection(setOf(entry.path)),
    swipeAnchor = if (onSelection) swipeAnchor else entry.path,
)

/** رفع التحديد وخروجه من النمط — يُستعمل عند الإلغاء وعند إغلاق القائمة بلا أمر. */
internal fun WindowView.clearedSelection(): WindowView =
    copy(selecting = false, selection = FileSelection(), swipeAnchor = null)

/**
 * «حدّد الكل» — على **ما هو ظاهر** لا على ما قُرئ: الإخفاء والبحث والمرشّح مطبَّقة، فلا
 * يُحدَّد ما لا يراه المستخدم ثم يُنسخ.
 */
internal fun WindowView.allSelected(window: FileWindowState, nowEpochSec: Long): WindowView = copy(
    selecting = true,
    selection = FileSelection().selectAll(visible(window, nowEpochSec)),
    swipeAnchor = null,
)

/** عكس التحديد — على الظاهر كذلك، وهو ما يجعل «حدّد الكل ثم استثنِ واحدًا» ممكنًا. */
internal fun WindowView.invertedSelection(window: FileWindowState, nowEpochSec: Long): WindowView = copy(
    selecting = true,
    selection = selection.invert(visible(window, nowEpochSec)),
    swipeAnchor = null,
)

/**
 * تبديل إظهار المخفي: النمط يُرفع والتحديد يُقصّ على ما صار ظاهرًا.
 *
 * وقصُّ التحديد **شرط لا تجميل**: اختيار اختفى بالمُفتاح ثم يُنسخ بلا أن يُرى هو أسوأ ما
 * يفعله زرّ إخفاء في مدير ملفات (وهو ما يفعله [WindowView.pruned] في مكان واحد).
 */
internal fun WindowView.afterHiddenToggle(hidden: FileWindowState, nowEpochSec: Long): WindowView =
    copy(selecting = false, swipeAnchor = null).pruned(hidden, nowEpochSec)

/**
 * النافذة التي يجب رفع تحديدها بعد إغلاق قائمة السياق، أو `null`.
 *
 * **ولا يُحكم في `onDismiss` نفسه** لأن الأمر المختار يُنفَّذ **بعد** الإغلاق (`CommandRow`
 * يُغلق ثم ينفّذ)، فكان الحكم هناك يفرّغ التحديد قبل أن يقرأه الأمر (`Delete` كان سيحذف
 * صفرًا). فيقع الحكم هنا بعد اكتمال الدورة، والأمر يكون قد قرأ ما يحتاج من التحديد.
 *
 * و`createdOnLongPress` يُسجَّل **فقط** حين أنشأ الضغط الطويل التحديد؛ فإن كان المدخل محدَّدًا
 * أصلًا بناء المستخدم فلا يُهدَم عليه.
 */
internal fun selectionToClearAfterMenu(anchor: Offset?, createdOnLongPress: WindowSide?): WindowSide? =
    if (anchor == null) createdOnLongPress else null
