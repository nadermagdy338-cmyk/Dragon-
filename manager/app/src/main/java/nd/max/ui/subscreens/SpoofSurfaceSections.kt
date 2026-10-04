/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.ui.subscreens

import android.content.Context
import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nd.max.MaxManagerPaths
import nd.max.R
import nd.max.core.hardware.RootFileAccess
import nd.max.core.spoof.SpoofExistingPerApp
import nd.max.core.spoof.SpoofReadiness
import nd.max.core.spoof.SpoofTargetState
import nd.max.core.spoof.spoofFpsTargets
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection
import kotlin.math.roundToInt

/**
 * SP-07 — the honesty barrier for one app. The acknowledgment is stored per package and is
 * deliberately not part of the exported workspace, so importing a file can never carry it.
 */
@Composable
internal fun SpoofBarrierSection(
    packageName: String?,
    acknowledged: Boolean,
    enabled: Boolean,
    onAcknowledge: () -> Unit,
    onRevoke: () -> Unit,
) {
    MaxSection(title = stringResource(R.string.spoof_barrier_title),
        description = stringResource(R.string.spoof_barrier_text)) {
        Text(stringResource(if (acknowledged) R.string.spoof_barrier_recorded
        else R.string.spoof_barrier_not_recorded))
        TextButton(enabled = enabled && packageName != null && !acknowledged, onClick = onAcknowledge) {
            Text(stringResource(R.string.spoof_barrier_accept))
        }
        TextButton(enabled = enabled && packageName != null && acknowledged, onClick = onRevoke) {
            Text(stringResource(R.string.spoof_barrier_revoke))
        }
    }
}

/**
 * SP-06 — documented frame-rate targets for one game. No row can read as opened: without a
 * verified engine contract the best a row can say is that the contract is unverified, and a rate
 * the measured display cannot reach is reported as such instead of being promised.
 */
@Composable
internal fun SpoofFpsTargetsSection(packageName: String?, readiness: SpoofReadiness) {
    if (packageName == null) return
    val context = LocalContext.current
    val maxRefreshHz = remember(context) { deviceMaxRefreshHz(context) }
    val targets = remember(packageName, readiness, maxRefreshHz) {
        spoofFpsTargets(packageName, readiness, maxRefreshHz)
    }
    MaxSection(title = stringResource(R.string.spoof_fps_title),
        description = stringResource(R.string.spoof_fps_notice)) {
        if (targets.isEmpty()) Text(stringResource(R.string.spoof_fps_empty))
        targets.forEach { target ->
            MaxRow(title = stringResource(R.string.spoof_fps_frames, target.frames),
                subtitle = stringResource(when (target.state) {
                    SpoofTargetState.NO_ENGINE -> R.string.spoof_fps_no_engine
                    SpoofTargetState.CONTRACT_UNVERIFIED -> R.string.spoof_fps_unverified
                    SpoofTargetState.ABOVE_DEVICE_REFRESH -> R.string.spoof_fps_above_device
                    SpoofTargetState.DEVICE_REFRESH_UNKNOWN -> R.string.spoof_fps_device_unknown
                }))
            Text(stringResource(R.string.spoof_fps_source, target.source))
        }
    }
}

/** Measured display modes, or `null` when the platform cannot answer — never a guessed ceiling. */
private fun deviceMaxRefreshHz(context: Context): Int? = runCatching {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    context.display?.supportedModes?.maxOfOrNull { it.refreshRate }?.roundToInt()
}.getOrNull()

/**
 * SP-09 — the per-app keys that already exist elsewhere, shown on the same surface so one knob has
 * one writer. This section only reads; the spoof side never writes these keys.
 */
@Composable
internal fun SpoofExistingPerAppSection(packageName: String?) {
    var document by remember(packageName) { mutableStateOf<String?>(null) }
    var failed by remember(packageName) { mutableStateOf(false) }
    LaunchedEffect(packageName) {
        if (packageName == null) return@LaunchedEffect
        document = null
        failed = false
        try {
            val text = withContext(Dispatchers.IO) { RootFileAccess.read(MaxManagerPaths.APPLIST_JSON) }
            if (text == null) failed = true else document = text
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    if (packageName == null) return
    MaxSection(title = stringResource(R.string.spoof_existing_title),
        description = stringResource(R.string.spoof_existing_notice)) {
        when {
            failed -> Text(stringResource(R.string.spoof_existing_unreadable))
            document == null -> Text(stringResource(R.string.spoof_reading))
            else -> SpoofExistingPerApp.DISPLAY_KEYS.forEach { knob ->
                val value = SpoofExistingPerApp.value(document.orEmpty(), packageName, knob.key)
                MaxRow(title = knob.key,
                    subtitle = if (value == null) stringResource(R.string.spoof_existing_absent)
                    else stringResource(R.string.spoof_existing_value, value))
                Text(stringResource(R.string.spoof_existing_owner, knob.owner))
                if (!knob.consumedByConfig) Text(stringResource(R.string.spoof_existing_dropped))
            }
        }
    }
}

/** SP-08 — drops every local tag of one app (its draft link and its acknowledgment), nothing else. */
@Composable
internal fun SpoofClearAppSection(
    packageName: String?,
    hasLocalTags: Boolean,
    enabled: Boolean,
    onClear: () -> Unit,
) {
    MaxSection(title = stringResource(R.string.spoof_clear_app_title),
        description = stringResource(R.string.spoof_clear_app_notice)) {
        TextButton(enabled = enabled && packageName != null && hasLocalTags, onClick = onClear) {
            Text(stringResource(R.string.spoof_clear_app_action))
        }
    }
}
