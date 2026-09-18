/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.SystemClock
import java.io.File
import nd.max.ui.util.PermissionPolicy.OpMode
import nd.max.ui.util.PermissionPolicy.OpState

/**
 * `GAP-07` — قراءة وكتابة حالة الصلاحيات و`AppOps` لتطبيق واحد.
 *
 * **طبقتان بمصداقيتين مختلفتين، ولا يُخلط بينهما:**
 *
 * - **صلاحيات البيان** — تُقرأ بـ`PackageManager` و**بلا أي امتياز**، بلا حاجة إلى جذر أو
 *   Shizuku. فهي الجزء الذي يعمل دائمًا.
 * - **`AppOps`** — تُقرأ وتُكتب بـ`cmd appops`، وهو **يحتاج امتيازًا** (جذر — أو ADB عبر
 *   الشيزوكو، وهو غير منفَّذ عندنا بعد `I-72`). ولذلك إن كان الجذر غائبًا تُعاد `null`
 *   **يُعلنها الحكم** ولا تُعرَض كأنها «لا أوضاع».
 *
 * ونفس انضباط `PEER-8`: **كل كتابة تُقرأ بعدها**، والحكم يُبنى على القيمة التي وجدناها لا على
 * نجاح الأمر — و«الجهاز تجاهل ما كتبناه» نتيجة معلَنة (بعض العمليات محجوزة لنظام).
 */
object AppOpsUtil {

    const val SCREEN = "Permissions"

    private const val REFERENCE_DIR = "appops_policy"

    // ────────────────────────────────────────────────────────────────────────
    // صلاحيات البيان (بلا امتياز)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * صلاحية كما يعلنها بيان التطبيق.
     *
     * @param dangerous هل مستوى حمايتها «خطيرة» — وهي وحدها ما يُعرض في الشاشة، لأن أذونات
     *        `normal` لا تُمنح ولا تُسحَب من المستخدم أصلًا؛ فعرضها يزيد القائمة بلا معلومة.
     * @param granted هل منحتها المنصّة فعلًا. منفصلة عن التصنيف: صلاحية خطيرة معلَنة وقد لا
     *        تكون ممنوحة — وهذا الفرق هو ما يحتاج المستخدم أن يعرفه.
     */
    data class DeclaredPermission(
        val name: String,
        val shortName: String,
        val dangerous: Boolean,
        val granted: Boolean,
    )

    /** `null` = تعذّرت قراءة حزمة (غير مثبَّتة أو قراءتها ممنوعة)، و`emptyList` = لا أذونات. */
    fun declaredPermissions(context: Context, pkg: String): List<DeclaredPermission>? = runCatching {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info: PackageInfo = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        // على compileSdk 36 الحقلان غير قابلين للعدم (`String[]` / `int[]`)؛ فالاحتياط أدناه
        // لا يضرّ، ويحمي إن عادت المنصّة لتُعيد null كما كانت قبل API 33.
        @Suppress("UNNECESSARY_ELVIS")
        val names: Array<String> = info.requestedPermissions ?: emptyArray()
        @Suppress("UNNECESSARY_ELVIS")
        val flags: IntArray = info.requestedPermissionsFlags ?: IntArray(0)
        names.mapIndexed { index, name ->
            val granted = (flags.getOrElse(index) { 0 } and
                PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            DeclaredPermission(
                name = name,
                shortName = name.substringAfterLast('.'),
                dangerous = isDangerous(pm, name),
                granted = granted,
            )
        }
    }.getOrElse {
        EventLog.error(SCREEN, "declared_permissions", it)
        null
    }

    /**
     * مستوى الحماية «خطيرة». ونستخدم `getProtection()` (API 28+) — وهي **المستوى الأساسي
     * المطلوب** بلا راياته، بحسب وثيقة AOSP (`Permissions.md`)، فنستغني عن قناع
     * `PROTECTION_MASK_BASE` المهجور وعن `protectionLevel` المهجورة معًا.
     * وتعذّر القراءة يعني «لسنا متأكّدين» ⇒ `false`، فلا نُدرج صلاحية في القائمة الحسّاسة
     * بناءً على تخمين.
     */
    private fun isDangerous(pm: PackageManager, name: String): Boolean = runCatching {
        val info = pm.getPermissionInfo(name, 0)
        info.getProtection() == PermissionInfo.PROTECTION_DANGEROUS
    }.getOrDefault(false)

    // ────────────────────────────────────────────────────────────────────────
    // AppOps
    // ────────────────────────────────────────────────────────────────────────

    /**
     * أوضاع `AppOps` لكل عمليات تطبيق. `null` = **تعذّرت القراءة** (لا جذر · أمر مرفوض ·
     * إصدار يكتب صيغة لا نفهمها) — والفرق بينها وبين `emptyList` هو الفرق بين «لا أعرف»
     * و«لا أوضاع»، وهو فرق يحكم هذا المستودع كله.
     */
    fun readOps(pkg: String): List<OpState>? {
        val out = PrivilegedShell.run("cmd appops get ${PrivilegedShell.quote(pkg)} 2>/dev/null")
            ?: return null
        val parsed = PermissionPolicy.parseAppOps(out)
        // مخرج مقروء لكن بلا سطر واحد نفهمه: لا نقول «لا أوضاع»، بل «لم أفهم».
        return parsed.takeIf { it.isNotEmpty() || out.isNotEmpty() }
    }

    /** عملية واحدة كما هي الآن. `null` = لم تُعلَن. */
    fun readOp(pkg: String, op: String): OpMode? = PermissionPolicy.parseSingleOp(
        PrivilegedShell.run(
            "cmd appops get ${PrivilegedShell.quote(pkg)} ${PrivilegedShell.quote(op)} 2>/dev/null",
        )
    )

    data class OpWriteResult(val verdict: PermissionPolicy.WriteVerdict, val readBack: OpMode?)

    /**
     * يكتب وضع عملية ثم **يقرأها ويحكم**. ولا إعادة محاولة ولا «إصلاح» — الحكم يُعلَن والقرار
     * للمستخدم، تمامًا كقاعدة `WriteVerification` في كتابة العقد.
     */
    fun setOp(pkg: String, op: String, mode: OpMode): OpWriteResult {
        val startedAt = SystemClock.elapsedRealtime()
        val commandOk = PrivilegedShell.run(
            "cmd appops set ${PrivilegedShell.quote(pkg)} ${PrivilegedShell.quote(op)} ${mode.id} 2>/dev/null",
        ) != null
        val readBack = if (commandOk) readOp(pkg, op) else null
        val verdict = PermissionPolicy.verdict(wrote = mode, readBack = readBack, commandSucceeded = commandOk)

        try {
            EventLog.result(
                screen = SCREEN,
                action = "appops_set",
                target = "$pkg:$op=${mode.id}",
                success = verdict == PermissionPolicy.WriteVerdict.APPLIED ||
                    verdict == PermissionPolicy.WriteVerdict.APPLIED_AS_DEFAULT,
                durationMs = SystemClock.elapsedRealtime() - startedAt,
            )
        } catch (_: Exception) {
            // التسجيل لا يُسقط كتابةً نجحت.
        }
        return OpWriteResult(verdict = verdict, readBack = readBack)
    }

    // ────────────────────────────────────────────────────────────────────────
    // المرجع المدوَّن
    // ────────────────────────────────────────────────────────────────────────

    /**
     * المرجع يُحفَظ في تخزين التطبيق الخاص لا في `/data/adb`: هو **سياسة يملكها المستخدم**،
     * ولا يستحق أن يحتاج جذرًا ليُحفظ أو يُقرأ. واسم الملف هو اسم الحزمة، وهو معرّف صالح
     * كاسم ملف بطبيعته — فلا تعقيم ولا تشويه.
     */
    private fun referenceFile(context: Context, pkg: String): File =
        File(File(context.filesDir, REFERENCE_DIR), "$pkg.json")

    fun saveReference(context: Context, reference: PermissionPolicy.Reference): Boolean = runCatching {
        val file = referenceFile(context, reference.pkg)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.part")
        temp.writeText(PermissionPolicy.Codec.encode(reference))
        // كتابة ذرّية: مرجع نصف مكتوب أسوأ من عدمه، لأنه سيُقارَن به لاحقًا فيبدو انحرافًا.
        temp.renameTo(file) || file.exists()
    }.getOrDefault(false)

    fun loadReference(context: Context, pkg: String): PermissionPolicy.Reference? = runCatching {
        val file = referenceFile(context, pkg)
        if (!file.isFile) return@runCatching null
        PermissionPolicy.Codec.decode(file.readText())
    }.getOrNull()

    fun deleteReference(context: Context, pkg: String): Boolean =
        runCatching { referenceFile(context, pkg).delete() }.getOrDefault(false)

    fun referenceExists(context: Context, pkg: String): Boolean = referenceFile(context, pkg).isFile
}
