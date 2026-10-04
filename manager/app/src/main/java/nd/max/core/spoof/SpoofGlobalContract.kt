/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Interface measured from COPG-VD module/service.sh + COPG-VD.json.example, pinned in the architecture report. */
object SpoofGlobalContract {
    const val MODULE_ID = "COPG-VD"
    const val MODULE_DIR = "/data/adb/modules/$MODULE_ID"
    const val CONFIG_PATH = "/data/adb/COPG-VD.json"

    /** Prepares identity fields only. Never invokes service.sh, resetprop or a reboot. */
    fun plan(existing: String, workspace: SpoofWorkspace): String? {
        val profile = workspace.profiles.firstOrNull { it.id == workspace.globalProfileId } ?: return null
        if (!SpoofProfileValidation.valid(profile) || profile.sdkInt != null) return null
        // Exclusion from a global injection layer is not implemented: refuse an isolation promise.
        if (workspace.appPolicies.values.any { it.mode == SpoofInheritanceMode.DISABLED ||
                it.categories.values.any { mode -> mode == SpoofCategoryMode.REAL } }) return null
        val previous = runCatching { Json.parseToJsonElement(existing) as? JsonObject }.getOrNull() ?: return null
        val device = previous[MODULE_ID] as? JsonObject ?: return null
        // Foreign advanced fields could contradict this identity; refuse rather than silently erase them.
        val allowed = setOf("BRAND", "MODEL", "DEVICE", "PRODUCT", "FINGERPRINT")
        if (device.keys.any { it !in allowed }) return null
        val fields = profile.identityValues().filterKeys { it != SpoofField.SDK_INT }
            .mapKeys { it.key.name }.mapValues { JsonPrimitive(it.value) }
        return JsonObject(previous + (MODULE_ID to JsonObject(fields))).toString()
    }
}
