/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import nd.max.R
import nd.max.core.atlas.AtlasRouteStatus
import nd.max.core.diagnostics.HardwareRouteHealth
import nd.max.core.hardware.HardwareFeature

/**
 * One control's activation verdict, as a single line under its capability row.
 *
 * It renders `nothing` when no verdict exists for the feature, so a caller does not have to guard the
 * lookup itself — and so a capability the route engine does not cover cannot be shown as if it had been
 * judged.
 *
 * **Only the status word is translated.** The code beside it
 * (`blocked:privilege_unavailable`, `review_required:route_not_reviewed`) is `AtlasRouteReason`
 * vocabulary and stays as-is on purpose: it is the string a support report needs to be compared
 * against, and a translated reason would be a different reason in every language. This is the
 * inspection surface, not a settings screen.
 */
@Composable
fun RouteVerdictLabel(
    routes: List<HardwareRouteHealth.Verdict>,
    feature: HardwareFeature,
    modifier: Modifier = Modifier,
) {
    val verdict = routes.firstOrNull { it.feature == feature } ?: return
    val text = when (verdict.status) {
        AtlasRouteStatus.ELIGIBLE ->
            stringResource(R.string.diagnostics_route_eligible, verdict.providerId ?: verdict.code)

        AtlasRouteStatus.REVIEW_REQUIRED ->
            stringResource(R.string.diagnostics_route_not_reviewed, verdict.code)

        else -> stringResource(R.string.diagnostics_route_blocked, verdict.code)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (verdict.status == AtlasRouteStatus.ELIGIBLE) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
        },
        modifier = modifier,
    )
}
