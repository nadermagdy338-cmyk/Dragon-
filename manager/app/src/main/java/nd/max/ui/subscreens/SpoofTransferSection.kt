/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.ui.subscreens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nd.max.R
import nd.max.core.spoof.SpoofWorkspace
import nd.max.core.spoof.SpoofWorkspaceCodec
import nd.max.core.spoof.planSpoofImport
import nd.max.ui.design.MaxSection

@Composable
internal fun SpoofTransferSection(workspace: SpoofWorkspace?, enabled: Boolean, onImport: (SpoofWorkspace) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<SpoofWorkspace?>(null) }
    var exportSnapshot by remember { mutableStateOf<SpoofWorkspace?>(null) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    val plan = remember(workspace, pending) {
        workspace?.let { current -> pending?.let { incoming -> runCatching { planSpoofImport(current, incoming) }.getOrNull() } }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            working = true
            message = null
            scope.launch {
                try {
                    pending = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(4096)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= SpoofWorkspaceCodec.MAX_BYTES)
                                output.write(buffer, 0, count)
                            }
                            SpoofWorkspaceCodec.decode(output.toString("UTF-8"))
                        } ?: error("unreadable-document")
                    }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { message = R.string.spoof_import_failed
                } finally { working = false }
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val snapshot = exportSnapshot
        exportSnapshot = null
        if (uri != null && snapshot != null) {
            working = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val bytes = SpoofWorkspaceCodec.encode(snapshot).toByteArray(Charsets.UTF_8)
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() }
                            ?: error("unwritable-document")
                        val readBack = context.contentResolver.openInputStream(uri)?.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(4096)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= SpoofWorkspaceCodec.MAX_BYTES)
                                output.write(buffer, 0, count)
                            }
                            SpoofWorkspaceCodec.decode(output.toString("UTF-8"))
                        }
                        check(readBack == snapshot)
                    }
                    message = R.string.spoof_export_saved
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { message = R.string.spoof_export_failed
                } finally { working = false }
            }
        }
    }
    MaxSection(title = stringResource(R.string.spoof_transfer_title),
        description = stringResource(R.string.spoof_transfer_notice)) {
        message?.let { Text(stringResource(it)) }
        if (working) Text(stringResource(R.string.spoof_reading))
        TextButton(enabled = enabled && workspace != null && !working, onClick = {
            pending = null
            importer.launch(arrayOf("text/plain", "application/octet-stream"))
        }) { Text(stringResource(R.string.spoof_import)) }
        TextButton(enabled = enabled && workspace != null && !working, onClick = {
            exportSnapshot = workspace
            exporter.launch("MaxManager-spoof-profiles.txt")
        }) { Text(stringResource(R.string.spoof_export)) }
    }
    if (pending != null) AlertDialog(onDismissRequest = { pending = null },
        title = { Text(stringResource(R.string.spoof_import_preview_title)) },
        text = {
            if (plan == null) Text(stringResource(R.string.spoof_import_limit))
            else Text(stringResource(R.string.spoof_import_preview, plan.addedProfiles, plan.addedBindings, plan.conflicts))
        },
        confirmButton = {
            TextButton(enabled = enabled && !working && plan != null, onClick = {
                plan?.let { onImport(it.workspace) }
                pending = null
            }) { Text(stringResource(R.string.spoof_import_merge)) }
        },
        dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.spoof_cancel)) } })
}
