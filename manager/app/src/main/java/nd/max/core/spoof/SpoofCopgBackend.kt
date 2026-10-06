/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import nd.max.core.hardware.RootFileAccess
import nd.max.core.privilege.PrivilegeManager
import javax.inject.Inject
import javax.inject.Singleton

data class SpoofEngineConfig(
    val configPath: String,
    val present: Boolean?,
    val parseable: Boolean?,
    val ownedKeys: List<String>,
    /** Module eligibility is measured separately from JSON parseability. */
    val engineAvailable: Boolean? = null,
    /**
     * **ولماذا السبب معه لا بدلًا منه:** «غير متاح» صفة، و[unavailableReason] هو الدليل الذي
     * يجيب «وما العمل؟» — فالواجهة تعرضه قبل أن ينقر المستخدم «تجهيز»، فلا يكتشف الرفض بعد الفعل.
     * و`null` هنا تعني الحالتين معًا: متاح، أو غير مقيس (بلا جذر مسبق) — والفرق بينهما يحمله
     * [engineAvailable] وحده، فلا يُقرأ الجهل «متاحًا».
     */
    val unavailableReason: SpoofEngineReason? = null,
)

/** Independent implementation of the external WebUI-documented COPG file interface; no upstream native code bundled. */
@Singleton
class SpoofCopgBackend @Inject constructor(private val transaction: SpoofConfigTransaction) {
    fun snapshot(): SpoofEngineConfig {
        val path = SpoofCopgContract.CONFIG_PATH
        if (!PrivilegeManager.cachedRootGranted()) return SpoofEngineConfig(path, null, null, emptyList())
        val text = RootFileAccess.read(path)
        val keys = text?.let(SpoofCopgContract::ownedKeys)
        // القياس مرّة واحدة: الحكم والسبب من نداء واحد، فلا يُسأل مدير الجذر سؤالًا يعرف جوابه.
        val reason = transaction.eligibility(SpoofCopgContract.MODULE_ID, requestRoot = false)
        return SpoofEngineConfig(path, RootFileAccess.exists(path), if (text == null) null else keys != null,
            keys.orEmpty(), reason == null, reason)
    }

    fun apply(workspace: SpoofWorkspace): SpoofEngineWrite {
        transaction.eligibility(SpoofCopgContract.MODULE_ID)?.let { return SpoofEngineWrite(false, it) }
        // Desired global selection is not runtime evidence. An installed active global layer can
        // exist even when the user cleared the saved selection; hook precedence is not proven.
        if (workspace != SpoofWorkspace() && activeGlobalLayer()) {
            return SpoofEngineWrite(false, SpoofEngineReason.GLOBAL_LAYER_CONFLICT)
        }
        val original = RootFileAccess.read(SpoofCopgContract.CONFIG_PATH)
            ?: return SpoofEngineWrite(false, SpoofEngineReason.ENGINE_CONFIG_UNREADABLE)
        val version = SpoofCopgContract.moduleVersion(RootFileAccess.read("${SpoofCopgContract.MODULE_DIR}/module.prop"))
        return when (val plan = SpoofCopgContract.plan(original, workspace, version)) {
            is SpoofCopgPlanResult.Refused -> SpoofEngineWrite(false, when (plan.reason) {
                SpoofCopgRefusal.CONFIG_UNPARSEABLE -> SpoofEngineReason.CONFIG_UNPARSEABLE
                SpoofCopgRefusal.KEY_COLLISION -> SpoofEngineReason.KEY_COLLISION
                SpoofCopgRefusal.FOREIGN_PACKAGE_CONFLICT -> SpoofEngineReason.FOREIGN_PACKAGE_CONFLICT
                SpoofCopgRefusal.UNSUPPORTED_POLICY -> SpoofEngineReason.UNSUPPORTED_POLICY
                SpoofCopgRefusal.UNSUPPORTED_TAG -> SpoofEngineReason.UNSUPPORTED_TAG
            })
            is SpoofCopgPlanResult.Ready -> transaction.apply(SpoofCopgContract.MODULE_ID, original, plan.plan.json)
        }
    }
    private fun activeGlobalLayer(): Boolean =
        RootFileAccess.read("${SpoofGlobalContract.MODULE_DIR}/module.prop")?.lineSequence()
            ?.any { it.trim() == "id=${SpoofGlobalContract.MODULE_ID}" } == true &&
            !RootFileAccess.exists("${SpoofGlobalContract.MODULE_DIR}/disable") &&
            !RootFileAccess.exists("${SpoofGlobalContract.MODULE_DIR}/remove")

    /** Removes only our namespaced assignments, preserving foreign entries. */
    fun clear(): SpoofEngineWrite = apply(SpoofWorkspace())
}
