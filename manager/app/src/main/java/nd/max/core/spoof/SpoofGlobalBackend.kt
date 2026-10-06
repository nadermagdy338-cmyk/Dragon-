/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import nd.max.core.hardware.RootFileAccess
import nd.max.core.privilege.PrivilegeManager
import javax.inject.Inject
import javax.inject.Singleton

/** Configuration adapter only: no live resetprop, script execution or runtime identity claim. */
@Singleton
class SpoofGlobalBackend @Inject constructor(private val transaction: SpoofConfigTransaction) {
    fun snapshot(): SpoofEngineConfig {
        val path = SpoofGlobalContract.CONFIG_PATH
        if (!PrivilegeManager.cachedRootGranted()) return SpoofEngineConfig(path, null, null, emptyList())
        val text = RootFileAccess.read(path)
        val obj = text?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        val reason = transaction.eligibility(SpoofGlobalContract.MODULE_ID, requestRoot = false)
        return SpoofEngineConfig(path, RootFileAccess.exists(path),
            if (text == null) null else obj?.get(SpoofGlobalContract.MODULE_ID) is JsonObject, emptyList(),
            reason == null, reason)
    }
    fun prepare(workspace: SpoofWorkspace): SpoofEngineWrite {
        transaction.eligibility(SpoofGlobalContract.MODULE_ID)?.let { return SpoofEngineWrite(false, it) }
        // Until hook ordering is proved, do not activate two independent injection layers together.
        val copgPresent = RootFileAccess.read("${SpoofCopgContract.MODULE_DIR}/module.prop")?.lineSequence()
            ?.any { it.trim() == "id=${SpoofCopgContract.MODULE_ID}" } == true &&
            !RootFileAccess.exists("${SpoofCopgContract.MODULE_DIR}/disable") &&
            !RootFileAccess.exists("${SpoofCopgContract.MODULE_DIR}/remove")
        if (copgPresent) return SpoofEngineWrite(false, SpoofEngineReason.GLOBAL_LAYER_CONFLICT)
        val original = RootFileAccess.read(SpoofGlobalContract.CONFIG_PATH)
            ?: return SpoofEngineWrite(false, SpoofEngineReason.ENGINE_CONFIG_UNREADABLE)
        val target = SpoofGlobalContract.plan(original, workspace)
            ?: return SpoofEngineWrite(false, SpoofEngineReason.UNSUPPORTED_POLICY)
        return transaction.apply(SpoofGlobalContract.MODULE_ID, original, target)
    }
    fun undo(): SpoofEngineWrite = transaction.restore(SpoofGlobalContract.MODULE_ID)
}
