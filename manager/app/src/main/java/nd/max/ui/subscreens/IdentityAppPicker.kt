/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxSection

@Composable
internal fun IdentityAppPicker(packageName: String, onChoose: (String) -> Unit) {
    val context = LocalContext.current
    var choosing by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(choosing) {
        if (!choosing) return@LaunchedEffect
        try {
            apps = withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
                    .distinctBy { it.first }.sortedBy { it.second.lowercase() }
            }
            failed = false
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { failed = true }
    }
    MaxSection(title = stringResource(R.string.spoof_app_title)) {
        OutlinedTextField(value = packageName, onValueChange = { if (it.length <= 255) onChoose(it) },
            label = { Text(stringResource(R.string.identity_package)) }, singleLine = true)
        TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.spoof_choose_app)) }
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text(stringResource(R.string.spoof_choose_app)) },
        text = { Column {
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text(stringResource(R.string.spoof_search)) })
            Text(stringResource(R.string.spoof_launcher_scope))
            if (failed) Text(stringResource(R.string.spoof_apps_failed))
            else if (apps == null) Text(stringResource(R.string.spoof_reading))
            else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(apps.orEmpty().filter { it.first.contains(query, true) || it.second.contains(query, true) }, key = { it.first }) { app ->
                    MaxRow(title = app.second, subtitle = app.first, onClick = { onChoose(app.first); choosing = false })
                }
            }
        } }, confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.spoof_cancel)) } })
}
