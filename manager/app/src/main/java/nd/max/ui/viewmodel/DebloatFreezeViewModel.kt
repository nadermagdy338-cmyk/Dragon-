/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
حالة شاشة «التجميد والحذف»: تحميل الحزم، والبحث والتصفية، والتبويبات، وتنفيذ الإجراءات
 * خارج الخيط الرئيسي. */

package nd.max.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.ui.util.DebloatAppInfo
import nd.max.ui.util.DebloatFreezeUtil
import nd.max.core.platform.EventLog

/** Tab filters for the Debloat & Freeze app list. */
enum class DebloatTab { ALL, USER, SYSTEM, FROZEN }

class DebloatFreezeViewModel : ViewModel() {

    var isLoading by mutableStateOf(true)
        private set
    var allApps by mutableStateOf<List<DebloatAppInfo>>(emptyList())
        private set
    var selectedTab by mutableStateOf(DebloatTab.ALL)
    var searchQuery by mutableStateOf(TextFieldValue(""))
        private set

    val totalCount: Int get() = allApps.size
    val systemCount: Int get() = allApps.count { it.isSystem }
    val userCount: Int get() = allApps.size - systemCount
    val frozenCount: Int get() = allApps.count { !it.isEnabled }

    val filteredApps by derivedStateOf {
        val tabFiltered = when (selectedTab) {
            DebloatTab.USER -> allApps.filter { !it.isSystem }
            DebloatTab.SYSTEM -> allApps.filter { it.isSystem }
            DebloatTab.FROZEN -> allApps.filter { !it.isEnabled }
            DebloatTab.ALL -> allApps
        }
        val query = searchQuery.text.trim()
        if (query.isEmpty()) {
            tabFiltered
        } else {
            tabFiltered.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    fun updateSearch(value: TextFieldValue) {
        searchQuery = value
    }

    fun loadApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            val apps = DebloatFreezeUtil.getInstalledApps(context)
            withContext(Dispatchers.Main) {
                allApps = apps
                isLoading = false
            }
        }
    }

    fun toggleFreeze(context: Context, app: DebloatAppInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val newState = !app.isEnabled
            val ok = DebloatFreezeUtil.toggleAppState(app.packageName, newState)
            EventLog.userAction(
                screen = "DebloatFreeze",
                field = "frozen",
                old = (!app.isEnabled).toString(),
                new = (!newState).toString(),
                pkg = app.packageName,
            )
            if (ok) {
                loadApps(context)
            }
        }
    }

    fun debloatApp(context: Context, app: DebloatAppInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = DebloatFreezeUtil.debloatApp(app.packageName)
            EventLog.userTriggered(
                screen = "DebloatFreeze",
                action = if (ok) "debloat" else "debloat_failed",
                target = app.packageName,
            )
            if (ok) {
                loadApps(context)
            }
        }
    }

    fun openAppSettings(context: Context, packageName: String) {
        DebloatFreezeUtil.openAppSystemSettings(context, packageName)
    }
}
