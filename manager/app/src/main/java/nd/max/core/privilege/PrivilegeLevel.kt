/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.core.privilege

import androidx.annotation.StringRes
import nd.max.R

/**
 * طبقات الامتياز التي يمكن أن يحملها MaxManager، تصاعديًّا (`AR-20`).
 *
 * الترتيب مقصود: كل طبقة أعلى تخدم كل ما تخدمه الطبقة الأدنى، والعكس غير صحيح.
 * لا تُخترع طبقة رابعة بلا قرار معلن.
 */
enum class PrivilegeLevel(val rank: Int, @StringRes val labelRes: Int) {
    /** بلا امتياز: ما تسمح به واجهات المنصّة للتطبيقات العادية فقط. */
    NONE(0, R.string.max_privilege_level_none),

    /** امتياز مستوى ADB عبر Shizuku — لا جذر. */
    SHIZUKU(1, R.string.max_privilege_level_shizuku),

    /** جذر كامل مع شجرة نظام الوحدة. */
    ROOT(2, R.string.max_privilege_level_root),
    ;

    fun atLeast(other: PrivilegeLevel): Boolean = rank >= other.rank
}

/**
 * أدنى طبقة يحتاجها المقبض. لا تُقرَّب لأعلى: تُعلن الطبقة التي تعمل فعلًا
 * (نفس مبدأ `XR-19` و`ADR-07`: لا قدرة مُبالَغ فيها).
 */
enum class PrivilegeTier(@StringRes val labelRes: Int) {
    NO_ROOT(R.string.max_privilege_tier_none),
    SHIZUKU(R.string.max_privilege_tier_shizuku),
    ROOT(R.string.max_privilege_tier_root),
}

data class PrivilegeFeature(
    val id: String,
    val tier: PrivilegeTier,
    @StringRes val titleRes: Int,
)

/**
 * فهرس القدرات بحسب طبقة الامتياز.
 *
 * الأساس مُتحقَّق من المستودع نفسه: `AR-20` في `docs/ai/EXTERNAL-RESEARCH-APP.md`
 * يسجّل صراحةً أن الكثافة وDNS وتجميد/تعطيل الحزم عبر `pm` و`AppOps` وقراءة البطارية
 * والسجلات **لا تحتاج جذرًا** بل امتياز ADB — لهذا صُنّفت هنا في `SHIZUKU` لا في `ROOT`.
 * وما يكتب sysfs مباشرةً (ترددات/حرارة/ZRAM/تصريف) يبقى في `ROOT` بلا تمويه.
 */
object PrivilegeCatalog {

    val features: List<PrivilegeFeature> = listOf(
        // ── تعمل بلا امتياز (واجهات المنصّة العادية) ──
        PrivilegeFeature("device_info", PrivilegeTier.NO_ROOT, R.string.max_privilege_feat_device_info),
        PrivilegeFeature("battery_read", PrivilegeTier.NO_ROOT, R.string.max_privilege_feat_battery_read),
        PrivilegeFeature("theme_language", PrivilegeTier.NO_ROOT, R.string.max_privilege_feat_theme),
        PrivilegeFeature("logs_viewer", PrivilegeTier.NO_ROOT, R.string.max_privilege_feat_logs),

        // ── تحتاج امتياز ADB (Shizuku) ──
        PrivilegeFeature("display_density", PrivilegeTier.SHIZUKU, R.string.max_privilege_feat_density),
        PrivilegeFeature("private_dns", PrivilegeTier.SHIZUKU, R.string.max_privilege_feat_dns),
        PrivilegeFeature("freeze_disable", PrivilegeTier.SHIZUKU, R.string.max_privilege_feat_freeze),
        PrivilegeFeature("app_ops", PrivilegeTier.SHIZUKU, R.string.max_privilege_feat_appops),
        PrivilegeFeature("doze_whitelist", PrivilegeTier.SHIZUKU, R.string.max_privilege_feat_doze),
        PrivilegeFeature("network_policy", PrivilegeTier.SHIZUKU, R.string.max_privilege_feat_netpolicy),
        PrivilegeFeature("frame_stats", PrivilegeTier.SHIZUKU, R.string.max_privilege_feat_framestats),

        // ── تحتاج جذرًا ──
        PrivilegeFeature("cpu_gpu_control", PrivilegeTier.ROOT, R.string.max_privilege_feat_cpu),
        PrivilegeFeature("thermal_control", PrivilegeTier.ROOT, R.string.max_privilege_feat_thermal),
        PrivilegeFeature("zram_control", PrivilegeTier.ROOT, R.string.max_privilege_feat_zram),
        PrivilegeFeature("module_health", PrivilegeTier.ROOT, R.string.max_privilege_feat_module),
        PrivilegeFeature("dex2oat", PrivilegeTier.ROOT, R.string.max_privilege_feat_dex2oat),
    )

    fun byTier(tier: PrivilegeTier): List<PrivilegeFeature> =
        features.filter { it.tier == tier }

    /** هل تُتاح هذه الميزة عند الطبقة الحالية؟ (مقارنة صريحة، لا تقريب) */
    fun availableAt(feature: PrivilegeFeature, level: PrivilegeLevel): Boolean = when (feature.tier) {
        PrivilegeTier.NO_ROOT -> true
        PrivilegeTier.SHIZUKU -> level.atLeast(PrivilegeLevel.SHIZUKU)
        PrivilegeTier.ROOT -> level.atLeast(PrivilegeLevel.ROOT)
    }
}
