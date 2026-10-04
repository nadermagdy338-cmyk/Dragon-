/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.core.gamespace.GameLibraryAccess
import nd.max.core.gamespace.VisibilityChange
import nd.max.core.gamespace.compareVisibility
import nd.max.ui.design.MaxScreen
import nd.max.ui.design.MaxSection
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.navigateTypedTo
import nd.max.ui.util.ModuleHealthUtil

private const val HMA_PACKAGE = "org.frknkrc44.hma_oss"
private data class HmaMeasurement(val count: Int?, val broadPermissionCount: Int?, val version: String?, val moduleCount: Int?)

@Composable
fun HmaCompanionScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var baseline by remember { mutableStateOf<Int?>(null) }
    var reading by remember { mutableStateOf(false) }
    var measurement by remember { mutableStateOf<HmaMeasurement?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }
    suspend fun measure() {
        reading = true
        try {
            val data = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                @Suppress("DEPRECATION")
                val packages = runCatching { pm.getInstalledPackages(PackageManager.GET_PERMISSIONS) }.getOrNull()
                @Suppress("DEPRECATION")
                val version = runCatching { pm.getPackageInfo(HMA_PACKAGE, 0).versionName }.getOrNull()
                val inventory = ModuleHealthUtil.readInstalledModules()
                HmaMeasurement(packages?.size,
                    packages?.count { "android.permission.QUERY_ALL_PACKAGES" in it.requestedPermissions.orEmpty() },
                    version, if (inventory.complete) inventory.modules.size else null)
            }
            measurement = data
            baseline = withContext(Dispatchers.IO) {
                val scopeKey = android.os.Build.FINGERPRINT + ":" + android.os.Build.VERSION.SDK_INT
                if (prefs.getString("hma_baseline_scope", null) == scopeKey && prefs.contains("hma_baseline_count"))
                    prefs.getInt("hma_baseline_count", -1).takeIf { it >= 0 } else null
            }
        } finally { reading = false }
    }
    LaunchedEffect(Unit) { measure() }
    MaxScreen(title = stringResource(R.string.hma_companion_title), onBack = { navController.navigateUp() }) {
        MaxSection(title = stringResource(R.string.hma_companion_presence), description = stringResource(R.string.hma_companion_scope)) {
            if (reading) Text(stringResource(R.string.spoof_reading))
            Text(measurement?.version?.let { stringResource(R.string.hma_companion_version, it) }
                ?: stringResource(R.string.hma_companion_unobserved))
            Text(stringResource(R.string.hma_companion_modules, measurement?.moduleCount?.toString() ?: stringResource(R.string.status_unknown)))
            TextButton(onClick = {
                if (!GameLibraryAccess.launch(context, HMA_PACKAGE)) message = R.string.game_space_launch_failed
            }) { Text(stringResource(R.string.hma_companion_open)) }
            TextButton(onClick = { navController.navigateTypedTo(MaxDestination.ModuleHealth) }) { Text(stringResource(R.string.max_module_title)) }
        }
        MaxSection(title = stringResource(R.string.hma_companion_measure)) {
            message?.let { Text(stringResource(it)) }
            Text(stringResource(R.string.hma_companion_counts,
                measurement?.count?.toString() ?: stringResource(R.string.status_unknown),
                measurement?.broadPermissionCount?.toString() ?: stringResource(R.string.status_unknown)))
            Text(stringResource(when (compareVisibility(baseline, measurement?.count)) {
                VisibilityChange.NO_BASELINE -> R.string.hma_companion_no_baseline
                VisibilityChange.LOWER -> R.string.hma_companion_lower
                VisibilityChange.SAME -> R.string.hma_companion_same
                VisibilityChange.HIGHER -> R.string.hma_companion_higher
                VisibilityChange.INCOMPARABLE -> R.string.hma_companion_unknown
            }))
            TextButton(enabled = !reading, onClick = { scope.launch { measure() } }) { Text(stringResource(R.string.spoof_rescan)) }
            TextButton(enabled = !reading && measurement?.count != null, onClick = {
                val count = measurement?.count ?: return@TextButton
                reading = true
                scope.launch {
                    try {
                        val ok = withContext(Dispatchers.IO) {
                            prefs.edit().putInt("hma_baseline_count", count)
                                .putString("hma_baseline_scope", android.os.Build.FINGERPRINT + ":" + android.os.Build.VERSION.SDK_INT).commit()
                        }
                        if (ok) baseline = count else message = R.string.game_space_save_failed
                    } finally { reading = false }
                }
            }) { Text(stringResource(R.string.hma_companion_baseline)) }
        }
    }
}
