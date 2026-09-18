/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.annotation.StringRes
import nd.max.R

/**
 * `AR-34` — من أين جاء هذا التطبيق؟
 *
 * تُساعد في تشخيص «لماذا لا يتحدّث؟» و«هل هو نسخة نظام؟» بلا تخمين. والتصنيف **محافظ**:
 * حيث لا يملك النظام جوابًا مؤكَّدًا نُعلن `UNKNOWN` ولا نقول «مثبَّت يدويًّا» (ADR-07).
 */
enum class InstallSourceKind(@StringRes val labelRes: Int) {
    SYSTEM(R.string.max_source_system),
    PLAY_STORE(R.string.max_source_play),
    OTHER_STORE(R.string.max_source_other_store),
    INSTALLED_BY_APP(R.string.max_source_by_app),
    UNKNOWN(R.string.max_source_unknown),
}

data class InstallSourceSnapshot(
    val kind: InstallSourceKind,
    /** حزمة المُثبِّت كما أعلنها النظام (`installingPackageName`)، أو `null`. */
    val installerPackage: String?,
    /** الحزمة المُصدِرة (`originatingPackageName`)، على API 30+ فقط. */
    val originatingPackage: String?,
)

object InstallSourceUtil {

    /** متاجر نعرفها بالاسم؛ ما عداها يُصنَّف بأمانة لا بالشهرة. */
    private val KNOWN_STORES: Map<String, InstallSourceKind> = mapOf(
        "com.android.vending" to InstallSourceKind.PLAY_STORE,
        "com.google.android.feedback" to InstallSourceKind.PLAY_STORE,
        "org.fdroid.fdroid" to InstallSourceKind.OTHER_STORE,
        "com.aurora.store" to InstallSourceKind.OTHER_STORE,
        "com.samsung.android.galaxyapps" to InstallSourceKind.OTHER_STORE,
    )

    /**
     * تصنيف نقي قابل للاختبار.
     *
     * - تطبيق نظام ⇒ `SYSTEM` (مهما كان المُثبِّت).
     * - مُثبِّت معروف ⇒ تصنيفه.
     * - مُثبِّت مجهول غير فارغ ⇒ `INSTALLED_BY_APP` (ليس «متجرًا» ولا «يدويًّا»).
     * - لا مُثبِّت ⇒ `UNKNOWN` — لأن Android الحديث قد يُعيد `null` لتطبيق مثبَّت فعلًا،
     *   فالادّعاء «مثبَّت يدويًّا» سيكون كذبًا.
     */
    fun classify(installerPackage: String?, isSystemApp: Boolean): InstallSourceKind {
        if (isSystemApp) return InstallSourceKind.SYSTEM
        val installer = installerPackage?.trim().orEmpty()
        if (installer.isEmpty()) return InstallSourceKind.UNKNOWN
        return KNOWN_STORES[installer] ?: InstallSourceKind.INSTALLED_BY_APP
    }

    fun read(context: Context, pkg: String): InstallSourceSnapshot {
        val pm = context.packageManager
        val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
        val isSystem = appInfo != null &&
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

        var installer: String? = null
        var originating: String? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val info = pm.getInstallSourceInfo(pkg)
                installer = info.installingPackageName
                originating = info.originatingPackageName
            }
        } else {
            @Suppress("DEPRECATION")
            installer = runCatching { pm.getInstallerPackageName(pkg) }.getOrNull()
        }

        return InstallSourceSnapshot(
            kind = classify(installer, isSystem),
            installerPackage = installer,
            originatingPackage = originating,
        )
    }
}
