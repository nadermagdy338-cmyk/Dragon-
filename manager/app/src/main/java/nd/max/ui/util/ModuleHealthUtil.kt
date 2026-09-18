/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.util

import nd.max.core.hardware.RootFileAccess

/**
 * لقطة صحة الوحدة والإنقاذ (`AR-05` + `AR-18`) — **قراءة فقط**.
 *
 * المشكلة التي تحلّها (FIND-2/FIND-3 في `docs/ai/EXTERNAL-RESEARCH-APP.md`):
 * الوحدة تكتب سجل تعافٍ ولا يقرأه أحد، وتُفعِّل الإنقاذ ولا يُبلَّغ به المستخدم.
 * هذه اللقطة تُحيي السجل وتُعلن الإنقاذ — بلا كتابة أي ملف.
 *
 * **قاعدة الصدق (ADR-07):** ما لم نقرأه يُعلَن «غير معروف»، ولا يُفسَّر كـ«سليم».
 * ولهذا يحمل كل حقل قيمة `null` معنى «لم نستطع الجزم» لا «لا شيء».
 */
data class ModuleHealth(
    /** هل عُثر على مجلد الوحدة؟ */
    val installed: Boolean,
    /** هل عُثر على ملف `disable` (أي أن الإنقاذ عطّل الوحدة)؟ */
    val disabled: Boolean?,
    /** قيمة `BOOTCOUNT` من `count.sh`، أو `null` إن تعذّرت القراءة. */
    val bootCount: Int?,
    /** عدد أسطر `package-recovery.log`، أو `null` إن تعذّرت القراءة. */
    val recoveryEventCount: Int?,
    /** آخر سطر في سجل التعافي، أو `null`. */
    val lastRecoveryEvent: String?,
    /** هل يوجد ملف `update` (تحديث ينتظر إعادة الإقلاع)؟ */
    val updatePending: Boolean?,
    /** `versionCode` من `module.prop`، أو `-1` إن تعذّرت القراءة. */
    val versionCode: Int,
    /** هل توجد نسخة أصلية `module.prop.orig` (تُستعاد عند التعافي)؟ */
    val hasOriginalProp: Boolean?,
    /**
     * `id=` من `module.prop`، أو `null`. **مهم**: المستودع يحمل أكثر من `module.prop`
     * واحد (`mainfiles/` و`android/kernelsu/`) بمعرّفين مختلفين — فالقراءة هنا تقول
     * **ما هو مركَّب فعلًا على الجهاز**، لا ما نتمنّاه (انظر `VersionIdentity`).
     */
    val id: String? = null,
    /** `version=` من `module.prop` كنصّ كما كتبه الحزم (`V1` · `v1.0.0-rc`)، أو `null`. */
    val version: String? = null,
) {
    /** هل وقع الإنقاذ فعلًا؟ «نعم» فقط عند دليل قاطع؛ وإلا `null` (غير معروف). */
    val rescueTriggered: Boolean?
        get() = when {
            disabled == true -> true
            (bootCount ?: 0) > 1 -> true
            disabled == false && bootCount != null -> false
            else -> null
        }

    /** هل هناك ما يستحق الإبلاغ عنه في سجل التعافي؟ */
    val hasRecoveryHistory: Boolean get() = (recoveryEventCount ?: 0) > 0
}

object ModuleHealthUtil {

    const val MODULE_DIR = "/data/adb/modules/MaxManager"
    private const val COUNT_FILE = "$MODULE_DIR/count.sh"
    private const val DISABLE_FILE = "$MODULE_DIR/disable"
    private const val UPDATE_FILE = "$MODULE_DIR/update"
    private const val PROP_FILE = "$MODULE_DIR/module.prop"
    private const val PROP_ORIG_FILE = "$MODULE_DIR/module.prop.orig"

    /** مطابق لِـ`mainfiles/service.sh` (`MODULE_CONFIG`). */
    const val RECOVERY_LOG = "/data/adb/.config/MaxManager/package-recovery.log"

    /** `mainfiles/post-fs-data.sh` يكتب `BOOTCOUNT=<n>` — نستخرج الرقم فقط. */
    fun parseBootCount(raw: String?): Int? {
        val match = raw?.let { Regex("BOOTCOUNT\\s*=\\s*(\\d+)").find(it) } ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    /** `module.prop` يكتب `versionCode=<n>` وقت التحزيم. */
    fun parseVersionCode(raw: String?): Int {
        val match = raw?.let { Regex("(?m)^\\s*versionCode\\s*=\\s*(\\d+)").find(it) } ?: return -1
        return match.groupValues.getOrNull(1)?.toIntOrNull() ?: -1
    }

    /** أي حقل نصّي مفرد من `module.prop` (`id=` · `version=`) — بلا تخمين صيغة. */
    fun parseField(raw: String?, key: String): String? {
        val pattern = Regex("(?m)^\\s*" + Regex.escape(key) + "\\s*=\\s*(\\S+)\\s*$")
        val value = pattern.find(raw ?: return null)?.groupValues?.getOrNull(1)?.trim()
        return value?.takeIf { it.isNotEmpty() }
    }

    /** عدد أحداث التعافي في السجل (أسطر غير فارغة). */
    fun countRecoveryEvents(raw: String?): Int? =
        raw?.lines()?.count { it.isNotBlank() }

    fun read(): ModuleHealth {
        val installed = RootFileAccess.exists(MODULE_DIR)
        if (!installed) {
            return ModuleHealth(
                installed = false,
                disabled = null,
                bootCount = null,
                recoveryEventCount = null,
                lastRecoveryEvent = null,
                updatePending = null,
                versionCode = -1,
                hasOriginalProp = null,
                id = null,
                version = null,
            )
        }

        val countRaw = RootFileAccess.read(COUNT_FILE)
        val propRaw = RootFileAccess.read(PROP_FILE)
        val recoveryRaw = RootFileAccess.read(RECOVERY_LOG)

        return ModuleHealth(
            installed = true,
            disabled = RootFileAccess.exists(DISABLE_FILE),
            bootCount = parseBootCount(countRaw),
            recoveryEventCount = countRecoveryEvents(recoveryRaw),
            lastRecoveryEvent = recoveryRaw?.lines()?.lastOrNull { it.isNotBlank() }?.trim(),
            updatePending = RootFileAccess.exists(UPDATE_FILE),
            versionCode = parseVersionCode(propRaw),
            hasOriginalProp = RootFileAccess.exists(PROP_ORIG_FILE),
            id = parseField(propRaw, "id"),
            version = parseField(propRaw, "version"),
        )
    }
}
