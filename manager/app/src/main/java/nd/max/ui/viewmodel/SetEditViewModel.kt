/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
حالة محرّر الإعدادات: البحث والتصنيف، والتعديل بإثبات، وتاريخ يسمح بالرجوع. */

package nd.max.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.ui.util.SetEditCategory
import nd.max.ui.util.SetEditItem
import nd.max.ui.util.SetEditUtil

enum class SetEditAction { CREATED, MODIFIED, DELETED }

/**
 * ترتيب القائمة.
 *
 * و`Natural` ليس "بلا قرار": هو ترتيب ما خرج من الجهاز (وفيه الأسماء المهمّة أوّلًا في الغالب)،
 * و`ByKey` يجعل العين تجد ما تعرف اسمه بلا تمرير — وهو الفرق الذي يطلبه من يحرّر مفتاحًا
 * بعينه.
 */
enum class SetEditSort { Natural, ByKey }

data class SetEditHistoryEntry(
    val id: Long = System.currentTimeMillis(),
    val action: SetEditAction,
    val item: SetEditItem,
    val previousValue: String? = null
)

class SetEditViewModel : ViewModel() {

    /** null = "All categories" tab. */
    var selectedCategory by mutableStateOf<SetEditCategory?>(null)
        private set
    var searchQuery by mutableStateOf("")
        @JvmName("setSearchQueryState") private set
    var items by mutableStateOf<List<SetEditItem>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var history by mutableStateOf<List<SetEditHistoryEntry>>(emptyList())
        private set

    /** الترتيب و«ما غيّرتُه فقط» — حالات عرض لا تُعيد قراءة من الجهاز. */
    var sort by mutableStateOf(SetEditSort.Natural)
        private set
    var onlyEdited by mutableStateOf(false)
        private set

    /** One-shot result for the screen to surface as a Snackbar; format "ok:message" / "err:message". */
    var actionResult by mutableStateOf<String?>(null)

    /** المفاتيح التي تغيّرت في هذه الجلسة — تُقرأ لتُعلَّم صفوفها في القائمة. */
    val editedKeys: Set<String>
        get() = history.filter { it.action != SetEditAction.DELETED }.map { it.item.key }.toSet()

    /**
     * اليوميّة كاملةً (تعديل وإنشاء وحذف) بأحدثها أوّلًا.
     *
     * وكان المعروض منها **المحذوف وحده** (`deletedHistory`): أي أنّ تعديلًا كتبته للتوّ لا تجد
     * له أثرًا، وهذا أسوأ من لا يوميّة: تعرف أنّك غيّرت شيئًا ولا تعرف ما كان. واليوميّة الآن
     * تشمل الثلاثة، ومعها القيمة السابقة للرجوع — وهي أنفع ما في مثل هذه الشاشة.
     */
    val journal: List<SetEditHistoryEntry> get() = history.reversed()

    /** القيمة السابقة لمفتاح من اليوميّة — تُستعمل لزرّ “أعد القيمة القديمة”. */
    fun previousValueOf(key: String): String? =
        history.lastOrNull { it.item.key == key && it.action == SetEditAction.MODIFIED }?.previousValue

    val filteredItems: List<SetEditItem>
        get() {
            val needle = searchQuery.trim()
            val base = items.filter { item ->
                (needle.isEmpty() ||
                    item.key.contains(needle, ignoreCase = true) ||
                    item.value.contains(needle, ignoreCase = true)) &&
                    (!onlyEdited || editedKeys.contains(item.key))
            }
            return when (sort) {
                SetEditSort.Natural -> base
                SetEditSort.ByKey -> base.sortedBy { it.key }
            }
        }

    fun clearActionResult() { actionResult = null }

    fun setCategory(category: SetEditCategory?) {
        if (category == selectedCategory) return
        selectedCategory = category
        refresh()
    }

    fun setSearchQuery(query: String) { searchQuery = query }

    // والأسماء `choose*`/`filter*` لا `set*`: الخصائص `var` تولّد `setSort` و`setOnlyEdited`
    // تلقائيًّا، ودالّة بالاسم نفسه **تصادم على الـJVM** (أمسكه المُصرّف في الجولتين).
    fun chooseSort(value: SetEditSort) { if (value != sort) sort = value }

    fun filterOnlyEdited(value: Boolean) { onlyEdited = value }

    /**
     * إرجاع مفتاح إلى قيمته السابقة مباشرةً — نفس مسار الكتابة الواحد ([SetEditUtil.set])،
     * فتسجيل اليوميّة لا يختلف عن أي تعديل آخر.
     */
    fun revert(key: String, resultLabel: (Boolean, String) -> String) {
        val previous = previousValueOf(key) ?: return
        val item = items.firstOrNull { it.key == key } ?: return
        saveItem(item, previous, resultLabel)
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            val loaded = try {
                SetEditUtil.loadItems(selectedCategory)
            } catch (e: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                items = loaded
                isLoading = false
            }
        }
    }

    fun saveItem(item: SetEditItem, newValue: String, resultLabel: (Boolean, String) -> String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = SetEditUtil.set(item.category, item.key, newValue)
            if (ok) {
                history = history + SetEditHistoryEntry(action = SetEditAction.MODIFIED, item = item.copy(value = newValue), previousValue = item.value)
                refresh()
            }
            withContext(Dispatchers.Main) { actionResult = (if (ok) "ok:" else "err:") + resultLabel(ok, item.key) }
        }
    }

    fun deleteItem(item: SetEditItem, resultLabel: (Boolean, String) -> String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = SetEditUtil.delete(item.category, item.key)
            if (ok) {
                history = history + SetEditHistoryEntry(action = SetEditAction.DELETED, item = item)
                refresh()
            }
            withContext(Dispatchers.Main) { actionResult = (if (ok) "ok:" else "err:") + resultLabel(ok, item.key) }
        }
    }

    fun createItem(category: SetEditCategory, key: String, value: String, resultLabel: (Boolean, String) -> String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = SetEditUtil.set(category, key, value)
            if (ok) {
                history = history + SetEditHistoryEntry(action = SetEditAction.CREATED, item = SetEditItem(key, value, category))
                refresh()
            }
            withContext(Dispatchers.Main) { actionResult = (if (ok) "ok:" else "err:") + resultLabel(ok, key) }
        }
    }

    /** إنشاء أو تعديل أو حذف — واليوميّة تفقد السطر المُرجَع إليه بعد نجاح الكتابة، فلا تَعِد بزرّ رجوع لا يجد ما يُرجِع. */
    fun restoreFromHistory(entry: SetEditHistoryEntry, resultLabel: (Boolean, String) -> String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = SetEditUtil.set(entry.item.category, entry.item.key, entry.item.value)
            if (ok) {
                history = history.filter { it.id != entry.id }
                refresh()
            }
            withContext(Dispatchers.Main) { actionResult = (if (ok) "ok:" else "err:") + resultLabel(ok, entry.item.key) }
        }
    }
}
