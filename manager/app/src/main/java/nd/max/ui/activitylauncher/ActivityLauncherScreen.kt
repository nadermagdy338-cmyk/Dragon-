/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.ui.activitylauncher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.AppIconImage
import nd.max.ui.component.ExpressiveListItem
import nd.max.ui.design.MaxCondition
import nd.max.ui.design.MaxConditionKind
import nd.max.ui.design.MaxConditionPanel
import nd.max.ui.design.MaxListScreen
import nd.max.ui.design.MaxRadius
import nd.max.ui.design.MaxSearchField
import nd.max.ui.design.MaxSegmented
import nd.max.ui.design.MaxSize
import nd.max.ui.design.MaxSpace

/**
 * Activity Launcher — lists what is installed, then what each package declares,
 * and starts a chosen activity.
 *
 * Two levels in one destination: the top bar's back arrow and the system back
 * gesture both leave the activity list before they leave the screen, so a user
 * exploring a package is never thrown out to the tool list mid-inspection.
 */
@Composable
fun ActivityLauncherScreen(navController: NavController) {
    val viewModel: ActivityLauncherViewModel = viewModel()

    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val indexEmpty by viewModel.indexEmpty.collectAsStateWithLifecycle()
    val visible by viewModel.visibleApps.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val scope by viewModel.scope.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val activities by viewModel.activities.collectAsStateWithLifecycle()
    val activitiesLoading by viewModel.activitiesLoading.collectAsStateWithLifecycle()
    val rootGranted by viewModel.rootGranted.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Outcome text is resolved here, in composition, because the snackbar call
    // itself is a suspend function and cannot read resources.
    val startedText = stringResource(R.string.max_launcher_outcome_started)
    val needsRootText = stringResource(R.string.max_launcher_outcome_needs_root)
    val refusedText = stringResource(R.string.max_launcher_outcome_refused)
    val missingText = stringResource(R.string.max_launcher_outcome_missing)

    LaunchedEffect(outcome) {
        val current = outcome ?: return@LaunchedEffect
        val message = when (current) {
            LaunchOutcome.STARTED -> startedText
            LaunchOutcome.NEEDS_ROOT -> needsRootText
            LaunchOutcome.REFUSED -> refusedText
            LaunchOutcome.NOT_FOUND -> missingText
        }
        snackbarHostState.showSnackbar(message)
        viewModel.consumeOutcome()
    }

    BackHandler(enabled = selected != null) { viewModel.closeDetail() }

    // `popBackStack()` returns Boolean while `closeDetail()` returns Unit; without the explicit
    // Unit type the lambda infers `() -> Any` and no longer satisfies the shell's `() -> Unit`.
    val onBack: () -> Unit = {
        if (selected != null) viewModel.closeDetail() else navController.popBackStack()
    }

    val current = selected
    if (current == null) {
        // The index either has content, is still loading, or legitimately holds
        // nothing. There is no fourth "something went wrong" branch: that vagueness
        // is what MaxManager's condition system exists to remove.
        val condition = when {
            loading -> MaxCondition(
                kind = MaxConditionKind.Loading,
                title = stringResource(R.string.max_title_activity_launcher),
                detail = stringResource(R.string.max_launcher_loading),
            )

            indexEmpty -> MaxCondition(
                kind = MaxConditionKind.Empty,
                title = stringResource(R.string.max_title_activity_launcher),
                detail = stringResource(R.string.max_launcher_empty),
            )

            else -> null
        }

        IndexLevel(
            apps = visible,
            query = query,
            scope = scope,
            condition = condition,
            snackbarHostState = snackbarHostState,
            onQueryChange = viewModel::setQuery,
            onScopeChange = viewModel::setScope,
            onOpen = viewModel::open,
            onRefresh = viewModel::refresh,
            onBack = onBack,
        )
    } else {
        DetailLevel(
            app = current,
            activities = activities,
            loading = activitiesLoading,
            rootGranted = rootGranted,
            snackbarHostState = snackbarHostState,
            onLaunch = viewModel::launch,
            onBack = onBack,
        )
    }
}

@Composable
private fun IndexLevel(
    apps: List<IndexedApp>,
    query: String,
    scope: AppScope,
    condition: MaxCondition?,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onScopeChange: (AppScope) -> Unit,
    onOpen: (IndexedApp) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val scopeLabels = listOf(
        stringResource(R.string.max_launcher_scope_all),
        stringResource(R.string.max_launcher_scope_user),
        stringResource(R.string.max_launcher_scope_system),
    )

    MaxListScreen(
        title = stringResource(R.string.max_title_activity_launcher),
        subtitle = stringResource(R.string.max_launcher_subtitle),
        accentIcon = Icons.AutoMirrored.Rounded.Launch,
        onBack = onBack,
        condition = condition,
        snackbarHostState = snackbarHostState,
        header = {
            MaxSearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.max_launcher_search_placeholder),
                clearContentDescription = stringResource(R.string.max_launcher_search_clear),
            )
            MaxSegmented(
                options = scopeLabels,
                selectedIndex = scope.ordinal,
                onSelect = { index -> onScopeChange(AppScope.entries[index]) },
            )
        },
    ) {
        if (apps.isEmpty() && condition == null) {
            item(key = "no_match") {
                MaxConditionPanel(
                    condition = MaxCondition(
                        kind = MaxConditionKind.Empty,
                        title = stringResource(R.string.max_launcher_no_match_title),
                        detail = stringResource(R.string.max_launcher_no_match_detail),
                        primaryActionLabel = stringResource(R.string.max_launcher_no_match_clear),
                        onPrimaryAction = {
                            onQueryChange("")
                            onScopeChange(AppScope.ALL)
                            onRefresh()
                        },
                    ),
                )
            }
        }

        items(apps, key = { it.packageName }) { app ->
            ExpressiveListItem(
                onClick = { onOpen(app) },
                headlineContent = {
                    Text(
                        text = app.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                // أيقونة التطبيق الحقيقية. كان هنا حرفًا بدلًا منها بحجّة أن حلّ `Drawable` لكل
                // صفّ أغلى ما يفعله فهرس تطبيقات — والحجّة سبقت `AppIconCache`: الصورة تُرسم
                // مرّة واحدة لكل حزمة على `Dispatchers.IO`، وكل صفّ بعدها يقرأها من `LruCache`
                // في O(1) بلا أي نداء إلى `PackageManager`.
                leadingContent = {
                    AppIconImage(
                        packageName = app.packageName,
                        size = MaxSize.rowIconContainer,
                    )
                },
                trailingContent = {
                    Text(
                        text = stringResource(
                            R.string.max_launcher_activity_count,
                            app.activityCount,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

@Composable
private fun DetailLevel(
    app: IndexedApp,
    activities: List<IndexedActivity>,
    loading: Boolean,
    rootGranted: Boolean,
    snackbarHostState: SnackbarHostState,
    onLaunch: (IndexedActivity) -> Unit,
    onBack: () -> Unit,
) {
    val condition = when {
        loading -> MaxCondition(
            kind = MaxConditionKind.Loading,
            title = app.label,
            detail = stringResource(R.string.max_launcher_reading_activities),
        )

        activities.isEmpty() -> MaxCondition(
            kind = MaxConditionKind.Empty,
            title = app.label,
            detail = stringResource(R.string.max_launcher_no_activities),
        )

        else -> null
    }

    // Without root the non-exported rows on this page cannot be started at all, so
    // say so up front instead of turning every tap into a failure message.
    val banner = if (rootGranted) {
        null
    } else {
        MaxCondition(
            kind = MaxConditionKind.RootRequired,
            title = stringResource(R.string.max_launcher_root_off_title),
            detail = stringResource(R.string.max_launcher_root_off_detail),
            technicalDetail = app.packageName,
        )
    }

    MaxListScreen(
        title = app.label,
        subtitle = app.packageName,
        accentIcon = Icons.AutoMirrored.Rounded.Launch,
        onBack = onBack,
        condition = condition,
        banner = banner,
        snackbarHostState = snackbarHostState,
    ) {
        items(activities, key = { it.name }) { activity ->
            ExpressiveListItem(
                onClick = { onLaunch(activity) },
                headlineContent = {
                    Text(
                        text = activity.label ?: activity.name.substringAfterLast('.'),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = activity.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    val tag = when {
                        !activity.enabled -> ActivityTag.DISABLED
                        !activity.exported -> ActivityTag.PRIVATE
                        else -> null
                    }
                    if (tag != null) {
                        ActivityTagChip(tag)
                    }
                },
            )
        }
    }
}

/** What the activity list needs to say about a row, beyond its name. */
private enum class ActivityTag { PRIVATE, DISABLED }

@Composable
private fun ActivityTagChip(tag: ActivityTag) {
    val label = when (tag) {
        ActivityTag.PRIVATE -> stringResource(R.string.max_launcher_tag_private)
        ActivityTag.DISABLED -> stringResource(R.string.max_launcher_tag_disabled)
    }
    Surface(
        shape = RoundedCornerShape(MaxRadius.pill),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MaxSpace.sm, vertical = MaxSpace.xs),
        )
    }
}

/*
 * كان هنا `AppBadge`: حرف بدل أيقونة التطبيق، بحجّة أن حلّ `Drawable` لكل صفّ أغلى ما يفعله
 * فهرس تطبيقات. الحجّة صحيحة لكنها لم تعد تنطبق: `AppIconCache` يحفظ الصورة لكل حزمة،
 * و`loadIcon(pm, packageName, …)` يحلّها مرّة على `Dispatchers.IO` — فالشارة الحرفية صارت
 * أيقونةً ناقصة لا قرارًا مبنيًّا على قياس. أُزيلت ولا تُعاد.
 */
