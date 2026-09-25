/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
حالة شاشة العمليات: القراءة الدورية، والترتيب، وإجراءات الإيقاف/القتل. */

package nd.max.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nd.max.ui.util.EventLog
import nd.max.ui.util.ProcessInfo
import nd.max.ui.util.ProcessMonitorUtil
import nd.max.ui.util.ProcessSortType

class ProcessManagerViewModel : ViewModel() {

    var processList by mutableStateOf<List<ProcessInfo>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var limitOption by mutableStateOf(20)
        private set
    var sortType by mutableStateOf(ProcessSortType.CPU)
        private set
    var userProcessCount by mutableStateOf(0)
        private set
    var systemProcessCount by mutableStateOf(0)
        private set

    /** Set by kill/force-stop actions; the screen surfaces this as a Snackbar and clears it. */
    var actionResult by mutableStateOf<String?>(null)

    fun clearActionResult() { actionResult = null }

    fun setSort(newSort: ProcessSortType) { sortType = newSort }
    fun setLimit(newLimit: Int) { limitOption = newLimit }

    /** Polls the process list every 3s for as long as this ViewModel (and the screen) is alive. */
    fun startMonitoring(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                refresh(context)
                delay(3000)
            }
        }
    }

    private suspend fun refresh(context: Context) {
        val data = ProcessMonitorUtil.getTopProcesses(context, limitOption, sortType)
        val userCount = data.count { !it.isSystem }
        val systemCount = data.size - userCount

        kotlinx.coroutines.withContext(Dispatchers.Main) {
            processList = data
            userProcessCount = userCount
            systemProcessCount = systemCount
            isLoading = false
        }
    }

    fun killProcess(context: Context, process: ProcessInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ProcessMonitorUtil.killProcess(process.pid)
            EventLog.userTriggered(
                screen = "ProcessManager",
                action = if (ok) "kill_process" else "kill_process_failed",
                target = "${process.packageName}:${process.pid}",
            )
            refresh(context)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                actionResult = if (ok) "killed:${process.appName}" else "kill_failed:${process.appName}"
            }
        }
    }

    fun forceStopApp(context: Context, process: ProcessInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ProcessMonitorUtil.forceStopApp(process.packageName)
            EventLog.userTriggered(
                screen = "ProcessManager",
                action = if (ok) "force_stop" else "force_stop_failed",
                target = process.packageName,
            )
            refresh(context)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                actionResult = if (ok) "stopped:${process.appName}" else "stop_failed:${process.appName}"
            }
        }
    }
}
