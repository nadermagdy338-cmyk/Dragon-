/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.activitylauncher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.ui.util.RootUtils

/**
 * State for the Activity Launcher screen.
 *
 * The screen has two levels — the package index and one package's activity list
 * — and both live here rather than in two destinations. Mounting a second route
 * for the detail view would mean the index is rebuilt (and every label resolved
 * again) on every back press; keeping the selection in the ViewModel makes
 * going back free.
 *
 * All `PackageManager` work is pushed to [Dispatchers.IO] and the visible list is
 * derived with [combine] instead of being stored twice, so a filter can never
 * disagree with the index it filters.
 */
class ActivityLauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val _apps = MutableStateFlow<List<IndexedApp>>(emptyList())
    private val _loading = MutableStateFlow(true)

    private val _query = MutableStateFlow("")
    private val _scope = MutableStateFlow(AppScope.ALL)

    private val _rootGranted = MutableStateFlow(false)

    private val _selected = MutableStateFlow<IndexedApp?>(null)
    private val _activities = MutableStateFlow<List<IndexedActivity>>(emptyList())
    private val _activitiesLoading = MutableStateFlow(false)

    private val _outcome = MutableStateFlow<LaunchOutcome?>(null)

    /** The full index, as read from the package manager. */
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** True when a root shell was actually obtained — the fallback path for non-exported activities. */
    val rootGranted: StateFlow<Boolean> = _rootGranted.asStateFlow()

    val query: StateFlow<String> = _query.asStateFlow()
    val scope: StateFlow<AppScope> = _scope.asStateFlow()

    /** Index narrowed by [query] and [scope]; recomputed, never cached separately. */
    val visibleApps: StateFlow<List<IndexedApp>> =
        combine(_apps, _query, _scope) { apps, query, scope ->
            ActivityIndex.filter(apps, query, scope)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** True when the index itself is empty, as opposed to hidden by a filter. */
    val indexEmpty: StateFlow<Boolean> =
        _apps.combine(_loading) { apps, loading -> apps.isEmpty() && !loading }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val selected: StateFlow<IndexedApp?> = _selected.asStateFlow()
    val activities: StateFlow<List<IndexedActivity>> = _activities.asStateFlow()
    val activitiesLoading: StateFlow<Boolean> = _activitiesLoading.asStateFlow()

    /** Last launch attempt, for the screen to explain; cleared by [consumeOutcome]. */
    val outcome: StateFlow<LaunchOutcome?> = _outcome.asStateFlow()

    init {
        refresh()
    }

    /**
     * Re-reads the index. Root is probed here as well as at launch time: without
     * the answer the screen cannot tell the user that the non-exported activities
     * it is listing are reachable at all, and an unexplained "did not start" is
     * exactly the vague failure MaxManager's condition system exists to remove.
     */
    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            val granted = withContext(Dispatchers.IO) {
                runCatching { RootUtils.isRootGranted() }.getOrDefault(false)
            }
            val loaded = withContext(Dispatchers.IO) { ActivityIndex.apps(getApplication()) }
            _rootGranted.value = granted
            _apps.value = loaded
            _loading.value = false
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setScope(value: AppScope) {
        _scope.value = value
    }

    /** Opens the activity list of [app]. */
    fun open(app: IndexedApp) {
        _selected.value = app
        _outcome.value = null
        viewModelScope.launch {
            _activitiesLoading.value = true
            val loaded = withContext(Dispatchers.IO) {
                ActivityIndex.activitiesFor(getApplication(), app.packageName)
            }
            _activities.value = loaded
            _activitiesLoading.value = false
        }
    }

    /** Leaves the activity list and returns to the index. */
    fun closeDetail() {
        _selected.value = null
        _activities.value = emptyList()
        _outcome.value = null
    }

    /**
     * Starts [activity] of the currently selected package.
     *
     * The direct start is attempted first and root is only consulted when the
     * manifest refuses it — never the other way round. Launching every activity
     * through `am` would be simpler but would make root mandatory for the
     * exported majority, which works today without it.
     */
    fun launch(activity: IndexedActivity) {
        val app = _selected.value ?: return
        viewModelScope.launch {
            val context = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                when (val direct = ActivityLauncher.launch(context, app.packageName, activity.name)) {
                    LaunchOutcome.NEEDS_ROOT -> {
                        if (_rootGranted.value &&
                            ActivityLauncher.launchAsRoot(app.packageName, activity.name)
                        ) {
                            LaunchOutcome.STARTED
                        } else {
                            LaunchOutcome.NEEDS_ROOT
                        }
                    }

                    else -> direct
                }
            }
            _outcome.value = result
        }
    }

    /** Marks the last outcome as seen so it is not re-shown. */
    fun consumeOutcome() {
        _outcome.value = null
    }
}
