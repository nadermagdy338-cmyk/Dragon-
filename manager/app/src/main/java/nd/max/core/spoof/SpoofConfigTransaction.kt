/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.spoof

import nd.max.core.hardware.ControlOwnership
import nd.max.core.hardware.HardwareControlArbiter
import nd.max.core.hardware.HardwareControlKey
import nd.max.core.hardware.RootFileAccess
import nd.max.core.hardware.SharedHardwareOwnershipStore
import nd.max.core.privilege.PrivilegeManager
import javax.inject.Inject
import javax.inject.Singleton

/** One file per transaction, no boot script execution and no claim of cross-engine atomicity. */
@Singleton
class SpoofConfigTransaction @Inject constructor(
    private val arbiter: HardwareControlArbiter,
    private val recovery: SpoofRecoveryStore,
) {
    private val transaction = SpoofFileTransaction(
        io = object : SpoofFileTransaction.Io {
            override fun eligibility(engine: String, requestRoot: Boolean) = this@SpoofConfigTransaction.eligibility(engine, requestRoot)
            override fun read(engine: String) = RootFileAccess.read(path(engine))
            override fun write(engine: String, text: String): Boolean {
                val file = path(engine)
                // COPG's own WebUI rewrites COPG.json in place; the per-app engine gets the same write.
                val wrote = if (engine == SpoofCopgContract.MODULE_ID) RootFileAccess.writeTextInPlace(file, text)
                    else RootFileAccess.atomicWriteText(file, text)
                return wrote && RootFileAccess.exec("chmod 644 $file") == 0 &&
                    RootFileAccess.exec("chcon u:object_r:system_file:s0 $file 2>/dev/null") == 0
            }
        },
        journal = object : SpoofFileTransaction.Journal {
            override fun record(engine: String) = recovery.record(engine)
            override fun put(record: SpoofRecoveryRecord) = recovery.put(record)
        },
        control = object : SpoofFileTransaction.Control {
            override fun release(engine: String) {
                arbiter.release(HardwareControlKey.spoofEngine(engine), "identity-file-$engine", restore = false)
            }
            override fun submit(engine: String, desired: String, apply: () -> Boolean, read: () -> String?,
                restore: () -> Boolean, verify: (String, String?) -> Boolean): SpoofFileTransaction.Result {
                val result = arbiter.submit(HardwareControlKey.spoofEngine(engine), ControlOwnership.Owner.GLOBAL_PROFILE,
                    "identity-file-$engine", desired, apply = { apply() }, read = read, restore = { restore() },
                    realized = { _, _ -> false }, verify = verify)
                return SpoofFileTransaction.Result(result.applied, result.verified, result.blocked, result.actual,
                    result.rollbackAttempted, result.rollbackVerified)
            }
        },
    )

    private fun path(engine: String): String = when (engine) {
        SpoofCopgContract.MODULE_ID -> SpoofCopgContract.CONFIG_PATH
        SpoofGlobalContract.MODULE_ID -> SpoofGlobalContract.CONFIG_PATH
        else -> error("unknown-engine")
    }

    /**
     * تهيئة المحرّك: **قياسٌ ثم حكمٌ مسمّى** ([SpoofEngineGate])، لا شرط مركّب يُخرج رمزًا واحدًا.
     *
     * والترتيب مقصود: المسار أوّلًا (رفض أيّ هدف غير مُدرَج قبل لمس الجذر)، ثم تهيئة المخزن،
     * ثم الجذر، ثم هوية الوحدة، ثم علامات مدير الجذر. وكلّ سبب يخرج من هنا يُسمّى بحالته المقيسة
     * — وتلك هي عطب التصدير الذي أُصلح: كان الرمز يقول «ENGINE_UNAVAILABLE» بينما `COPG` مثبَّتة.
     *
     * والعلامات تُسأل **بعد** مطابقة الهوية فقط، فلا تُدفع كلفة ثلاثة أسئلة عن وحدةٍ ليست هي.
     */
    fun eligibility(engine: String, requestRoot: Boolean = true): SpoofEngineReason? {
        path(engine) // reject any non-allowlisted target before root access
        if (!SharedHardwareOwnershipStore.isConfigured()) return SpoofEngineReason.STORE_UNCONFIGURED
        if (!PrivilegeManager.cachedRootGranted() && (!requestRoot || !PrivilegeManager.requestRoot())) return SpoofEngineReason.ROOT_REQUIRED
        val dir = "/data/adb/modules/$engine"
        SpoofEngineGate.identityReason(RootFileAccess.read("$dir/module.prop"), engine)?.let { return it }
        return SpoofEngineGate.markerReason(
            SpoofEngineGate.markers.filterTo(mutableSetOf()) { RootFileAccess.exists("$dir/$it") },
        )
    }

    fun apply(engine: String, original: String, target: String): SpoofEngineWrite = transaction.apply(engine, original, target)
    fun restore(engine: String): SpoofEngineWrite = transaction.restore(engine)
}
