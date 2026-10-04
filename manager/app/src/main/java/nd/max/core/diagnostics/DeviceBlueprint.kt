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
import nd.max.core.platform.PropertyUtils
import nd.max.core.platform.getChipsetIdentity

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

        // ── هوية الشريحة: كل مصدر بقيمته وطبقته ──────────────────────
        // هذا القسم وُجد لأن العرض قد يجمع **مرشّحين** حين تحمل الشريحة أكثر من اسم تجاري
        // (‏`MT6897` = Dimensity 8300 و8350)، ولم يكن في التقرير ما يقول **لماذا** ولا أيّ
        // مصدر تكلّم. فصار يُطبع كل ما أعلنه الجهاز، وطبقته، وما انتهى إليه الحلّ — فيُسمّى
        // السبب في التقرير نفسه لا في جولة مراسلة تالية.
        appendLine("[chipset-identity]")
        val chipsetIdentity = getChipsetIdentity(context)
        chipsetIdentity.sources.forEach { source ->
            appendLine(
                "  ${source.label.padEnd(42)} ${source.evidence.label.padEnd(16)} " +
                    source.value.ifEmpty { "-" }
            )
        }
        appendLine("  display        : ${chipsetIdentity.display}")
        appendLine("  partCode       : ${chipsetIdentity.partCode ?: "-"}")
        appendLine("  declaredName   : ${chipsetIdentity.declaredName ?: "-"}")
        appendLine("  candidates     : ${chipsetIdentity.candidates.joinToString(" | ").ifEmpty { "-" }}")
        appendLine("  matchedKey     : ${chipsetIdentity.matchedKey ?: "-"}")
        // وثلاثة أسطر تجيب عن «لماذا هذا الاسم؟» بلا مراسلة: حالة الدليل، وهل جاء من مفتاح
        // تامّ أم من استنتاج بالبادئة. وطلب المالك صريح: «ميّز EXACT وAMBIGUOUS وUNKNOWN».
        appendLine("  match          : ${chipsetIdentity.match}")
        appendLine("  inferred       : ${chipsetIdentity.inferred}")

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
