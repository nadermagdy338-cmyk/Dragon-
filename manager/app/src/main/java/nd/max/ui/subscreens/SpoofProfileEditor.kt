/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.ui.subscreens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nd.max.R
import nd.max.core.spoof.SpoofProfile
import nd.max.core.spoof.SpoofProfileValidation

/** Same editor in both entry points; no store, engine or duplicate validation in the UI. */
@Composable
internal fun SpoofProfileEditor(profile: SpoofProfile, onDismiss: () -> Unit, onSave: (SpoofProfile) -> Unit) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var brand by rememberSaveable(profile.id) { mutableStateOf(profile.brand) }
    var model by rememberSaveable(profile.id) { mutableStateOf(profile.model) }
    var device by rememberSaveable(profile.id) { mutableStateOf(profile.device) }
    var product by rememberSaveable(profile.id) { mutableStateOf(profile.product) }
    var fingerprint by rememberSaveable(profile.id) { mutableStateOf(profile.fingerprint.orEmpty()) }
    val valid = runCatching {
        profile.copy(name = name.trim(), brand = brand.trim(), model = model.trim(), device = device.trim(),
            product = product.trim(), fingerprint = fingerprint.trim().takeIf(String::isNotEmpty), sdkInt = null)
    }.getOrNull()?.takeIf(SpoofProfileValidation::valid)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.spoof_edit)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item { IdentityField(name, { name = it }, stringResource(R.string.spoof_profile_name)) }
                item { IdentityField(brand, { brand = it }, stringResource(R.string.spoof_brand)) }
                item { IdentityField(model, { model = it }, stringResource(R.string.spoof_model)) }
                item { IdentityField(device, { device = it }, stringResource(R.string.spoof_device)) }
                item { IdentityField(product, { product = it }, stringResource(R.string.spoof_product)) }
                item { IdentityField(fingerprint, { fingerprint = it }, stringResource(R.string.spoof_fingerprint)) }
                item { Text(stringResource(R.string.identity_validation)) }
                // Preserve legacy SDK fields in storage, but never offer framework API spoofing as a safe control.
                if (profile.sdkInt != null) item { Text(stringResource(R.string.identity_legacy_sdk)) }
                if (valid == null) item { Text(stringResource(R.string.identity_invalid)) }
            }
        },
        confirmButton = { TextButton(enabled = valid != null, onClick = { valid?.let(onSave) }) {
            Text(stringResource(R.string.spoof_save_profile))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.spoof_cancel)) } },
    )
}

@Composable
private fun IdentityField(value: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(value = value, onValueChange = { if (it.length <= 120) change(it) },
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}
