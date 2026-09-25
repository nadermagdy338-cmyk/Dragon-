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

    /** One-shot result for the screen to surface as a Snackbar; format "ok:message" / "err:message". */
    var actionResult by mutableStateOf<String?>(null)

    val filteredItems: List<SetEditItem>
        get() = if (searchQuery.isBlank()) {
            items
        } else {
            items.filter { it.key.contains(searchQuery, ignoreCase = true) || it.value.contains(searchQuery, ignoreCase = true) }
        }

    val deletedHistory: List<SetEditHistoryEntry>
        get() = history.filter { it.action == SetEditAction.DELETED }.take(20)

    fun clearActionResult() { actionResult = null }

    fun setCategory(category: SetEditCategory?) {
        if (category == selectedCategory) return
        selectedCategory = category
        refresh()
    }

    fun setSearchQuery(query: String) { searchQuery = query }

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
