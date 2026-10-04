/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.gamespace

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

object GameLibraryAccess {
    /** Caller runs on IO. No package-wide visibility permission or root needed. */
    fun apps(context: Context): List<GameApp> {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { entry ->
                val app = entry.activityInfo.applicationInfo
                @Suppress("DEPRECATION")
                val game = app.category == ApplicationInfo.CATEGORY_GAME || app.flags and ApplicationInfo.FLAG_IS_GAME != 0
                GameApp(entry.activityInfo.packageName, entry.loadLabel(pm).toString(), game)
            }.distinctBy { it.packageName }
    }
    fun manual(context: Context): Set<String> = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getStringSet("game_library_manual", emptySet())?.toSet().orEmpty().let(::validGamePackages)

    /**
     * الألعاب التي أزالها المستخدم صراحةً — مفتاح واحد يقرأه موضعان (المكتبة وخدمة اللوحة)،
     * فلا تفترق نسختان من اسم المفتاح يومًا.
     */
    fun excluded(context: Context): Set<String> = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getStringSet("game_library_excluded", emptySet())?.toSet().orEmpty().let(::validGamePackages)
    fun saveManual(context: Context, packages: Set<String>): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putStringSet("game_library_manual", validGamePackages(packages)).commit()
    fun launch(context: Context, pkg: String): Boolean = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
