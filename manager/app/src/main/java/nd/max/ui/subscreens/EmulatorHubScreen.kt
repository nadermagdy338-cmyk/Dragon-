/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import nd.max.core.gamespace.romSystemHint
import nd.max.ui.design.MaxRow
import nd.max.ui.design.MaxScreen
import nd.max.ui.design.MaxSection
import nd.max.ui.navigation.MaxDestination
import nd.max.ui.navigation.navigateTypedTo

private data class RomDocument(val uri: String, val name: String, val mime: String?)

@Composable
fun EmulatorHubScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var documents by remember { mutableStateOf<List<RomDocument>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    var generation by remember { mutableStateOf(0) }
    LaunchedEffect(generation) {
        busy = true
        try {
            documents = withContext(Dispatchers.IO) {
                prefs.getStringSet("emulator_documents", emptySet()).orEmpty().map { value ->
                    val uri = Uri.parse(value)
                    val name = runCatching {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                    }.getOrNull()
                    RomDocument(value, name ?: value, runCatching { context.contentResolver.getType(uri) }.getOrNull())
                }.sortedBy { it.name.lowercase() }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (_: Exception) { message = R.string.emu_hub_save_failed
        } finally { busy = false }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            busy = true
            scope.launch {
                try {
                    val ok = withContext(Dispatchers.IO) {
                        val old = prefs.getStringSet("emulator_documents", emptySet()).orEmpty().toSet()
                        val accepted = uris.filter { uri ->
                            runCatching {
                                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                true
                            }.getOrDefault(false)
                        }.map(Uri::toString)
                        val next = old + accepted
                        next.size <= 1000 && accepted.size == uris.size && prefs.edit().putStringSet("emulator_documents", next).commit()
                    }
                    message = if (ok) R.string.emu_hub_saved else R.string.emu_hub_save_failed
                    generation++
                } finally { busy = false }
            }
        }
    }
    MaxScreen(title = stringResource(R.string.emu_hub_title), onBack = { navController.navigateUp() }) {
        MaxSection(title = stringResource(R.string.emu_hub_library), description = stringResource(R.string.emu_hub_scope)) {
            message?.let { Text(stringResource(it)) }
            TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.emu_hub_add)) }
            TextButton(onClick = { navController.navigateTypedTo(MaxDestination.GameLobby) }) { Text(stringResource(R.string.game_lobby_title)) }
            if (busy) Text(stringResource(R.string.spoof_reading))
            else if (documents.isEmpty()) Text(stringResource(R.string.emu_hub_empty))
            documents.forEach { doc ->
                MaxSection(title = doc.name, description = stringResource(when (romSystemHint(doc.name)) {
                    "PC" -> R.string.emu_hub_pc
                    "AMBIGUOUS", null -> R.string.emu_hub_unknown
                    else -> R.string.emu_hub_hint
                }, romSystemHint(doc.name).orEmpty())) {
                    MaxRow(title = stringResource(R.string.emu_hub_open), onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(doc.uri), doc.mime ?: "application/octet-stream")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        intent.clipData = android.content.ClipData.newRawUri(doc.name, Uri.parse(doc.uri))
                        message = if (runCatching { context.startActivity(Intent.createChooser(intent, null)); true }.getOrDefault(false))
                            R.string.emu_hub_requested else R.string.game_space_launch_failed
                    })
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val ok = withContext(Dispatchers.IO) {
                                    val next = prefs.getStringSet("emulator_documents", emptySet()).orEmpty() - doc.uri
                                    prefs.edit().putStringSet("emulator_documents", next).commit()
                                }
                                if (ok) generation++ else message = R.string.emu_hub_save_failed
                            } finally { busy = false }
                        }
                    }) { Text(stringResource(R.string.emu_hub_remove)) }
                }
            }
        }
    }
}
