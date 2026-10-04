/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.gamespace

import android.content.Context
import java.io.File

/**
 * بيانات صفّ اللعبة في اللوبي — **أرقام مقروءة لا مُختلقة** (ADR-07).
 *
 * @param sizeBytes مجموع ملفات الـAPK (الأساس + الأقسام المنفصلة)، و`null` إن لم يُقرأ شيء.
 *   وهو **حجم التثبيت المُعلَن للحزمة لا حجم بيانات اللعبة**: بيانات اللعبة تحتاج إذنًا لا يملكه
 *   التطبيق، فلا يُدَّعى رقمٌ لا يُقاس.
 * @param installedDays عدد الأيام الكاملة منذ أوّل تثبيت، و`null` إن لم يُقرأ التاريخ.
 *
 * ولا وقت لعب هنا عن قصد: قراءته تتطلّب إذن «الوصول إلى الاستخدام» وهو غير مُعلَن في المانيفست،
 * فعرض «٣ ساعات» كان سيكون رقمًا بلا مصدر.
 */
data class GameLobbyMeta(val sizeBytes: Long?, val installedDays: Int?)

private const val MILLIS_PER_DAY = 86_400_000L

/** أيام كاملة بين تاريخين، ولا تنزل تحت الصفر إذا انحرفت ساعة الجهاز إلى الوراء. */
fun installAgeDays(firstInstallMillis: Long, nowMillis: Long): Int? {
    if (firstInstallMillis <= 0L) return null
    val days = (nowMillis - firstInstallMillis) / MILLIS_PER_DAY
    return days.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

/** مجموع أحجام ملفات موجودة فعلًا؛ يُسقط المسارات الفارغة ولا يعدّ ملفًّا غير موجود صفرًا. */
fun apkTotalBytes(paths: List<String?>): Long? {
    val sizes = paths.filterNotNull().filter { it.isNotBlank() }
        .mapNotNull { path -> File(path).takeIf { it.isFile }?.length() }
    return if (sizes.isEmpty()) null else sizes.sum()
}

object GameLobbyMetaReader {
    /** يعمل على IO. أي فشل في القراءة يُرجع قيمًا `null` لا استثناءً يسقط معه الصفّ. */
    fun read(context: Context, pkg: String, nowMillis: Long = System.currentTimeMillis()): GameLobbyMeta = runCatching {
        val info = context.packageManager.getPackageInfo(pkg, 0)
        val app = info.applicationInfo
        val paths = buildList {
            add(app?.sourceDir)
            app?.splitSourceDirs?.forEach { add(it) }
        }
        GameLobbyMeta(sizeBytes = apkTotalBytes(paths), installedDays = installAgeDays(info.firstInstallTime, nowMillis))
    }.getOrDefault(GameLobbyMeta(null, null))
}
