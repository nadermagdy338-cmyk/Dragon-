/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */

package nd.max.core.diagnostics

import android.content.Context
import android.os.Build
import nd.max.core.hardware.CpuHardwareBackend
import nd.max.core.hardware.HardwareCapabilityResolver
import nd.max.ui.util.PropertyUtils

/**
 * بصمة الجهاز: لقطة قراءة فقط تُدمج في كل تصدير تشخيصي.
 *
 * الغاية: حين يرسل المستخدم السجل، يحمل الملف كل ما يلزم لإعادة بناء
 * صورة جهازه عن بُعد — طوبولوجيا سياسات المعالج الفعلية (أسماء
 * السياسات، المدى المسموح من العتاد، الحكّام المتاحون)، مستويات
 * وصول كل ميزة (READ_WRITE / READ_ONLY / NONE) مع الأدلة (مسارات
 * sysfs المكتشفة)، والهوية الكاملة (fingerprint، ABI، مستوى تصحيح
 * الأمان). هكذا يجيب السجل مسبقًا على أسئلة مثل: "لماذا يظهر ضبط X
 * مقفولًا؟" — لأن السياسة policy4 بمدى 300–1804 kHz فقط.
 *
 * الكلفة: تُولَّد عند التصدير فقط (ليست حلقة مراقبة).
 */
object DeviceBlueprint {

    fun generate(context: Context): String = buildString {
        appendLine("--- DEVICE BLUEPRINT (read-only fingerprint) ---")

        // ── الهوية ──────────────────────────────────────────────────
        appendLine("[identity]")
        appendLine("  model         : ${Build.MODEL}")
        appendLine("  manufacturer  : ${Build.MANUFACTURER}")
        appendLine("  device        : ${Build.DEVICE}")
        appendLine("  chipset       : ${chipset()}")
        appendLine("  android       : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("  securityPatch : ${Build.VERSION.SECURITY_PATCH}")
        appendLine("  kernel        : ${System.getProperty("os.version") ?: "unknown"}")
        appendLine("  abis          : ${Build.SUPPORTED_ABIS.joinToString(",")}")
        appendLine("  fingerprint   : ${Build.FINGERPRINT}")
        appendLine("  rootImpl      : ${rootImplementation()}")

        // ── طوبولوجيا سياسات المعالج ────────────────────────────────
        // هذا القسم هو ما يسمح "بتشغيل جهاز المستخدم" من طرف المطور:
        // أسماء السياسات الحقيقية ومداها المسموح من العتاد.
        appendLine("[cpu-policies]")
        val policies = CpuHardwareBackend.policies()
        if (policies.isEmpty()) {
            appendLine("  (no cpufreq policies discovered)")
        } else {
            policies.forEach { p ->
                appendLine(
                    "  ${p.name}: governor=${p.governor ?: "?"} " +
                        "range=${p.minKHz ?: "?"}..${p.maxKHz ?: "?"}kHz " +
                        "hw=${p.hwMinKHz ?: "?"}..${p.hwMaxKHz ?: "?"}kHz " +
                        "available=[${p.governors.joinToString(",")}]"
                )
            }
            appendLine(
                "  commonGovernors: " +
                    CpuHardwareBackend.commonGovernors().joinToString(",").ifEmpty { "(none)" }
            )
        }

        // ── قدرات الميزات مع الأدلة ─────────────────────────────────
        appendLine("[capabilities]")
        val snapshot = HardwareCapabilityResolver.resolve(context)
        appendLine("  vendor        : ${snapshot.vendor}")
        appendLine("  platform      : ${snapshot.platform}")
        snapshot.features.forEach { (feature, cap) ->
            appendLine(
                "  ${feature.name.padEnd(20)} ${cap.access.name.padEnd(11)} " +
                    "backend=${cap.backend}" +
                    if (cap.evidence.isEmpty()) "" else " evidence=${cap.evidence.size} nodes"
            )
        }

        appendLine("[end]")
    }

    private fun chipset(): String {
        listOf("ro.soc.manufacturer", "ro.soc.model").forEach { key ->
            val value = PropertyUtils.get(key)
            if (value.isNotBlank()) return value
        }
        return Build.HARDWARE ?: "unknown"
    }

    /**
     * كشف تنفيذ الجذر بمسارات مجلداته الحقيقية (نفس المسارات التي
     * تجمع منها السجلات: /data/adb/ksu و /data/adb/ap و magisk.log)
     * بدل أسماء props غير موثوقة عبر التنفيذات.
     */
    private fun rootImplementation(): String = when {
        java.io.File("/data/adb/ksu").exists() -> "kernel-su"
        java.io.File("/data/adb/ap").exists() -> "apatch"
        java.io.File("/data/adb/magisk").exists() || java.io.File("/cache/magisk.log").exists() ||
            java.io.File("/data/adb/magisk.log").exists() -> "magisk"
        else -> "unknown"
    }
}
