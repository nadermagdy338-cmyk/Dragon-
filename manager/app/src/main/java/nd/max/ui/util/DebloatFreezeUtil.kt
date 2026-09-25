/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import com.topjohnwu.superuser.Shell
import java.text.Collator

/**
 * تطبيق مثبّت كما تعرضه شاشة «التجميد والحذف».
 *
 * ويُقصد بـ`isEnabled` حالة `PackageManager` الفعلية (مُجمَّد أم لا) — لا اختيار المستخدم في
 * ملفات MaxManager. فالأول حقيقة عن الجهاز، والثاني تفضيل: خلطهما يجعل الشاشة تقول «مُجمَّد»
 * عن تطبيق يعمل.
 */
data class DebloatAppInfo(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val icon: Drawable?
)

/**
 * تجميد/تفعيل/حذف للمستخدم الحالي **بلا صلاحيات مالك الجهاز** (Device Owner).
 *
 * - **التجميد** (`pm disable-user --user 0`) قابل للعكس بـ`pm enable`، ولا يمسّ ملفّات التطبيق
 *   ولا بياناته.
 * - **الحذف** هنا `pm uninstall --user 0`: إزالة للمستخدم الحالي فقط، فتبقى الحزمة في صورة
 *   النظام ويمكن إرجاعها بـ`pm install-existing` أو بتصفير المصنع. وهذا مقصود: «حذف» لا يجوز
 *   أن يكون نهائيًّا في أداة تُستخدم على جهاز فيه تطبيقات نظام لا بدّ منها للإقلاع.
 *
 * وكل الأوامر عبر [Shell] (libsu) — طريق الجذر الواحد في التطبيق.
 */
object DebloatFreezeUtil {

    /** ترتيب عربي/صيني سليم: مُرتِّب اللغة لا مقارنة نقاط الترميز (التطبيق بثمانٍ وثمانين لغة). */
    private val labelCollator: Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    /**
     * كل الحزم المثبّتة، مرتّبة باسم العرض بلغة الواجهة الحالية.
     *
     * وحزمة واحدة معطوبة لا تُسقط القائمة: تُسجَّل وتُتخطّى (`mapNotNull`) — فشاشة التجميد لا
     * يجوز أن تصير فارغة لأن تطبيقًا واحدًا كتب `applicationInfo` غير متوقّع.
     */
    fun getInstalledApps(context: Context): List<DebloatAppInfo> {
        val pm = context.packageManager
        return pm.getInstalledPackages(PackageManager.GET_META_DATA)
            .mapNotNull { pkg -> describe(pm, pkg) }
            .sortedWith(compareBy(labelCollator) { it.label })
    }

    private fun describe(pm: PackageManager, pkg: android.content.pm.PackageInfo): DebloatAppInfo? {
        val app: ApplicationInfo = pkg.applicationInfo ?: return null
        return runCatching {
            DebloatAppInfo(
                label = pm.getApplicationLabel(app).toString(),
                packageName = pkg.packageName,
                versionName = pkg.versionName ?: UNKNOWN_VERSION,
                versionCode = pkg.longVersionCode,
                isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                isEnabled = app.enabled,
                icon = runCatching { pm.getApplicationIcon(app) }.getOrNull(),
            )
        }.onFailure { EventLog.error(SCREEN, "read_package:${pkg.packageName}", it) }
            .getOrNull()
    }

    /**
     * يُجمّد التطبيق أو يُعيد تفعيله للمستخدم الحالي.
     *
     * `--user 0` مُعلن صراحةً في الطرفين: بلا تحديد المستخدم ينفّذ `pm` الأمر على مستخدم آخر
     * في بعض الواجهات، فتظهر النتيجة «نجحت» والحالة لم تتغيّر.
     */
    fun toggleAppState(packageName: String, enable: Boolean): Boolean {
        val action = if (enable) "enable" else "disable-user --user 0"
        return shell("pm $action $packageName")
    }

    /** إزالة للمستخدم الحالي فقط — قابلة للرجوع على تطبيقات النظام بـ`pm install-existing`. */
    fun debloatApp(packageName: String): Boolean = shell("pm uninstall --user 0 $packageName")

    /** صفحة معلومات التطبيق في إعدادات النظام، لمن يريد صلاحيات لا يملكها هذا الملف. */
    fun openAppSystemSettings(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { EventLog.error(SCREEN, "open_app_settings:$packageName", it) }
    }

    private fun shell(command: String): Boolean =
        runCatching { Shell.cmd(command).exec().isSuccess }.getOrDefault(false)

    private const val SCREEN = "Debloat"
    private const val UNKNOWN_VERSION = "?"
}
