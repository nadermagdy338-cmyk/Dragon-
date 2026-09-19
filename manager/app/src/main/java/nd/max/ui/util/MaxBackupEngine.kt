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
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Environment
import com.topjohnwu.superuser.Shell
import java.io.File
import nd.max.ui.util.MaxBackupModel.Availability
import nd.max.ui.util.MaxBackupModel.ComponentKind
import nd.max.ui.util.MaxBackupModel.Handle
import nd.max.ui.util.MaxBackupModel.Integrity
import nd.max.ui.util.MaxBackupModel.Manifest
import nd.max.ui.util.MaxBackupModel.ManifestCodec
import nd.max.ui.util.MaxBackupModel.ManifestEntry
import nd.max.ui.util.MaxBackupModel.Plan
import nd.max.ui.util.MaxBackupModel.PlannedComponent
import nd.max.ui.util.MaxBackupModel.Scope

/**
 * Max Backup — محرّك التنفيذ.
 *
 * في `ui/util` لا في شاشة، لأن الكتابة إلى عقد/أنظمة أخرى ممنوعة من طبقة العرض (ADR-11)،
 * وتفويضها إلى الأدوات هو المسار المشروع — نفس مسار `LogUtil` الذي يستعمل `tar` و`chown`
 * و`restorecon` من هنا بالضبط.
 *
 * **ما يفترق به هذا المحرّك عن النسخ الاحتياطي المعتاد، بنيّةً لا بالشعارات:**
 *
 * 1. **لا يكتب ثم يقول «تم».** كل ملف يُشفَّر بـ`sha256` بعد كتابته، ويُعاد قراءته والتحقّق
 *    منه **قبل** كتابة المستند. نسخة لم تثبت سلامتها تُوسم `complete=false`.
 * 2. **لا يسترجع بلا تحقّق.** كل مدخل يجب أن يكون `VERIFIED`؛ `UNVERIFIABLE` تمنع الاسترجاع.
 * 3. **لا يخترع حجمًا ولا نجاحًا.** حجم تعذّر قياسه `null`، ومرحلة تعذّرت تُسمّى باسمها في
 *    `omissions` وتبقى في المستند لتُقرأ لاحقًا.
 * 4. **لا تشفير وهميّ.** الأرشيف غير مشفّر، ونقول ذلك صراحةً: مفتاح مشحون داخل APK ليس سرًّا،
 *    وادّعاء تشفيره أسوأ من غيابه.
 */
object MaxBackupEngine {

    /** معرّف الشاشة في السجل (نمط `EventLog`). */
    const val SCREEN = "MaxBackup"

    /**
     * معرّف نسخة **بيانات النظام** في المستودع. ليس اسم حزمة ولا يمكن أن يصير كذلك
     * (علامة `@` غير مسموحة في أسماء الحزم) ⇒ فلا تصادم بين نسخ التطبيقات ونسخ النظام،
     * ويُعاد استخدام نفس المستند والتحقّق والسجل بلا مسار ثانٍ يوازيه.
     */
    const val SYSTEM_PKG = "@system"

    internal const val MANIFEST_FILE = "manifest.json"
    private const val APK_SUFFIX = ".apk"
    internal const val PART_SUFFIX = ".part"

    /**
     * معرّفات المراحل. **عامّة عن قصد**: تعبر من المحرّك إلى الشاشة لتُترجَم هناك، فيبقى
     * المحرّك بلا نصّ واجهيّ والواجهة بلا منطق — والمفتاح بينهما معرّف واحد، لا جملة إنجليزية
     * تُقارَن بمثلها فيُخطئ التطابق الأول حرفًا واحدًا.
     */
    const val STAGE_APK = "apk"
    const val STAGE_DATA = "data"
    const val STAGE_EXTERNAL = "external"
    const val STAGE_OBB = "obb"
    const val STAGE_MANIFEST = "manifest"

    /**
     * الجذر الذي استقرّ عليه الفحص الكتابي. `null` = لم يُفحص بعد.
     * `@Volatile` لأن القراءة من خيط الواجهة والكتابة من خيط القرص.
     */
    @Volatile
    private var cachedRoot: File? = null

    // ────────────────────────────────────────────────────────────────────────
    // نتائج العمليات
    // ────────────────────────────────────────────────────────────────────────

    data class CreateOutcome(
        val success: Boolean,
        val folder: String?,
        val bytes: Long,
        val entryCount: Int,
        val omissions: List<String>,
        /** المرحلة التي فشلت، باسمها المُعلَن — `null` إن لم يفشل شيء. */
        val failedStage: String?,
    )

    data class RestoreOutcome(
        val success: Boolean,
        val failedStage: String?,
        val block: MaxBackupModel.RestoreBlock,
    )

    // ────────────────────────────────────────────────────────────────────────
    // الصلاحية والأدوات
    // ────────────────────────────────────────────────────────────────────────

    internal fun quote(value: String): String = PrivilegedShell.quote(value)

    /** جذر فعلي: نتحقّق بأن UID يساوي 0 ولا نفترضه من وجود الوحدة. */
    fun hasRoot(): Boolean = runCatching {
        val result = Shell.cmd("id -u").exec()
        result.isSuccess && result.out.firstOrNull()?.trim() == "0"
    }.getOrDefault(false)

    internal fun shell(vararg commands: String): List<String>? = PrivilegedShell.run(*commands)

    /** بصمة `sha256` لملف. `null` = تعذّرت القراءة — ولا نُخترع بصمة. */
    internal fun sha256Of(path: String): String? {
        val out = shell("sha256sum ${quote(path)} 2>/dev/null") ?: return null
        return out.firstOrNull()?.split(' ')?.firstOrNull()?.trim()?.takeIf { it.length == 64 }
    }

    /**
     * `du -sk` بالكيلوبايت ثم نحوّلها. `null` عند أي فشل — حتى لا يتحوّل «لم أقس» إلى «صفر».
     */
    private fun directoryBytes(path: String): Long? {
        val out = shell("du -sk ${quote(path)} 2>/dev/null") ?: return null
        val kb = out.firstOrNull()?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull() ?: return null
        return if (kb < 0) null else kb * 1024L
    }

    internal fun fileCount(path: String): Int? {
        val out = shell("find ${quote(path)} -type f 2>/dev/null | wc -l") ?: return null
        return out.firstOrNull()?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
    }

    internal fun isDirectory(path: String): Boolean =
        shell("test -d ${quote(path)} && echo y")?.firstOrNull() == "y"

    // ────────────────────────────────────────────────────────────────────────
    // الجرد
    // ────────────────────────────────────────────────────────────────────────

    /**
     * جرد كامل **قبل** أي كتابة. هذه هي الدالة التي تجعل «ماذا سينسخ؟» سؤالًا له جواب، بدل
     * شريط تقدّم يخفي ما يفعله.
     */
    @Suppress("DEPRECATION")
    fun inventory(context: Context, pkg: String, root: Boolean = hasRoot()): Plan? {
        val pm = context.packageManager
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return null
        val versionCode = runCatching {
            pm.getPackageInfo(pkg, 0).let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() }
        }.getOrNull() ?: 0L
        val versionName = runCatching { pm.getPackageInfo(pkg, 0).versionName }.getOrNull()

        val components = mutableListOf<PlannedComponent>()

        // ---- الـAPK: يُقرأ دائمًا، بلا جذر -----------------------------------
        val baseApk = info.sourceDir?.takeIf { it.isNotBlank() }
        components += PlannedComponent(
            kind = ComponentKind.APK,
            source = baseApk,
            sizeBytes = baseApk?.let { File(it).takeIf(File::isFile)?.length() },
            availability = if (baseApk != null) Availability.AVAILABLE else Availability.UNKNOWN,
        )

        val splits = info.splitSourceDirs?.filter { it.isNotBlank() }.orEmpty()
        if (splits.isNotEmpty()) {
            val known = splits.all { File(it).isFile }
            components += PlannedComponent(
                kind = ComponentKind.SPLIT_APK,
                source = splits.first(),
                sizeBytes = if (known) splits.sumOf { File(it).length() } else null,
                availability = if (known) Availability.AVAILABLE else Availability.UNKNOWN,
                fileCount = splits.size,
            )
        }

        // ---- البيانات: تحتاج جذرًا -------------------------------------------
        val dataDir = "/data/data/$pkg"
        val externalDir = "/sdcard/Android/data/$pkg"
        val obbDir = "/sdcard/Android/obb/$pkg"

        components += dataComponent(ComponentKind.APP_DATA, dataDir, root)
        components += dataComponent(ComponentKind.EXTERNAL_DATA, externalDir, root)
        components += dataComponent(ComponentKind.OBB, obbDir, root)

        return Plan(
            pkg = pkg,
            label = runCatching { info.loadLabel(pm).toString() }.getOrNull(),
            versionCode = versionCode,
            versionName = versionName,
            isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            uid = info.uid,
            hasRoot = root,
            components = components,
        )
    }

    /**
     * مكوّن دليل بيانات.
     *
     * ثلاث حالات صريحة، ولا واحدة منهما تُخفي الأخرى:
     * - بلا جذر ⇒ `NEEDS_ROOT` **ولا نقرأ الحجم أصلًا** (لا نظهر رقمًا لا نملكه).
     * - بالجذر والدليل غير موجود ⇒ `UNAVAILABLE` بحجم **صفر مقيس** (قِسناه: لا شيء).
     * - بالجذر والدليل موجود ⇒ `AVAILABLE` بحجمه المقيس، أو `null` إن تعذّرت القراءة.
     */
    internal fun dataComponent(kind: ComponentKind, path: String, root: Boolean): PlannedComponent {
        if (!root) {
            return PlannedComponent(kind, path, null, Availability.NEEDS_ROOT)
        }
        if (!isDirectory(path)) {
            return PlannedComponent(kind, path, 0L, Availability.UNAVAILABLE, fileCount = 0)
        }
        return PlannedComponent(
            kind = kind,
            source = path,
            sizeBytes = directoryBytes(path),
            availability = Availability.AVAILABLE,
            fileCount = fileCount(path),
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    // المستودع
    // ────────────────────────────────────────────────────────────────────────

    /**
     * الجذر المُستخدَم فعلًا. **لا يلمس القرص**: القرار بالصلاحية وحدها، حتى تُنادى من
     * التركيب بلا حجب خيط الواجهة — والفحص الكتابي يثبّته عند أول عملية قرص ([ensureRoot]).
     */
    fun storageRoot(context: Context): File {
        cachedRoot?.let { return it }
        return if (publicStorageGranted(context)) {
            MaxBackupStorage.requestedRoot()
        } else {
            fallbackRoot(context)
        }
    }

    /**
     * هل يملك التطبيق صلاحية الكتابة في التخزين العام؟
     *
     * قبل أندرويد ١١ لا يوجد هذا القيد، فنقول «نعم» ولا نحرم مستخدمًا من مسار لن يواجه
     * مانعه. والقراءة محفوظة في `runCatching` لأن سؤال النظام عن صلاحية لا يجوز أن يُسقط شاشة.
     */
    fun publicStorageGranted(context: Context): Boolean = runCatching {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }.getOrDefault(false)

    /** المسار العام المطلوب — تعرضه الشاشة كما هو ليعرفه المستخدم ويصل إليه بمدير ملفاته. */
    fun requestedRoot(): File = MaxBackupStorage.requestedRoot()

    /**
     * جذر الاحتياط: مجلد التطبيق الخارجي. **يُقرأ منه دائمًا**، لا عند غياب العام فقط،
     * حتى لا تختفي نسخة أُخذت قبل منح الصلاحية أو قبل هذا التغيير.
     */
    fun fallbackRoot(context: Context): File {
        val external = runCatching { context.getExternalFilesDir(null) }.getOrNull()
        return MaxBackupStorage.fallbackRoot(external ?: context.filesDir)
    }

    /**
     * يثبّت الجذر بفحص كتابة **فعلي** — ولا يُنادى إلا من عمليات القرص.
     *
     * ولماذا فحص كتابة لا `mkdir` ناجحة: مجلد ينشئه root ويبقى مملوكًا له يوجد بنجاح ثم
     * يفشل عند أول كتابة من التطبيق — وذلك بالضبط كيف تُنتَج نسخة تُعلن نجاحًا ولم تُكتب.
     */
    internal fun ensureRoot(context: Context): File {
        cachedRoot?.let { return it }
        val chosen = MaxBackupStorage.choose(
            requested = MaxBackupStorage.requestedRoot(),
            fallback = fallbackRoot(context),
            requestedUsable = writable(MaxBackupStorage.requestedRoot()),
        )
        cachedRoot = chosen
        return chosen
    }

    /**
     * يُسقط القرار المخزَّن. يُنادى بعد أن يمنح المستخدم صلاحية «الوصول لكل الملفات»،
     * وإلا بقي الأرشيف في مجلد التطبيق حتى إعادة تشغيل العملية بلا سبب مفهوم.
     */
    fun invalidateStorageRoot() {
        cachedRoot = null
    }

    private fun writable(dir: File): Boolean {
        if (!dir.isDirectory && !runCatching { dir.mkdirs() }.getOrDefault(false)) return false
        return runCatching {
            val probe = File(dir, MaxBackupStorage.PROBE_FILE)
            probe.writeText("")
            probe.delete()
            true
        }.getOrDefault(false)
    }

    /** الجذر المُستخدَم — الاسم الذي تناديه الشاشة للعرض. */
    fun backupsRoot(context: Context): File = storageRoot(context)

    private fun appFolder(context: Context, pkg: String): File =
        MaxBackupStorage.folderOf(ensureRoot(context), pkg)

    fun readManifest(folder: File): Manifest? {
        val file = File(folder, MANIFEST_FILE)
        if (!file.isFile) return null
        return runCatching { ManifestCodec.decode(file.readText()) }.getOrNull()
    }

    internal fun writeManifest(folder: File, manifest: Manifest): Boolean = runCatching {
        val target = File(folder, MANIFEST_FILE)
        val temp = File(folder, MANIFEST_FILE + PART_SUFFIX)
        temp.writeText(ManifestCodec.encode(manifest))
        // الكتابة الذرّية: لا يبقى مستند نصف مكتوب يُقرأ لاحقًا على أنه سليم.
        temp.renameTo(target) || target.exists()
    }.getOrDefault(false)

    /** كل النسخ على القرص، أو نسخ تطبيق واحد. المجلدات بلا مستند صالح تُتجاهل بصمت معلَن. */
    fun list(context: Context, pkg: String? = null): List<Handle> {
        // الجذران معًا: المُستخدَم والاحتياطي. نسخة واحدة لا تختفي لأن مكانها تغيّر.
        val roots = MaxBackupStorage.searchRoots(ensureRoot(context), fallbackRoot(context))
        val appFolders = if (pkg != null) {
            roots.map { MaxBackupStorage.folderOf(it, pkg) }
        } else {
            roots.flatMap { root -> root.listFiles()?.filter(File::isDirectory).orEmpty() }
        }

        return appFolders.distinctBy { it.absolutePath }.flatMap { folder ->
            folder.listFiles()?.filter(File::isDirectory).orEmpty().mapNotNull { backup ->
                val manifest = readManifest(backup) ?: return@mapNotNull null
                Handle(
                    pkg = manifest.pkg,
                    folder = backup.absolutePath,
                    createdAtMs = manifest.createdAtMs,
                    bytes = manifest.entries.sumOf { it.bytes },
                    complete = manifest.complete,
                    entryCount = manifest.entries.size,
                    encrypted = manifest.encrypted,
                    label = manifest.label,
                    keptForever = manifest.keptForever,
                )
            }
        }.sortedByDescending { it.createdAtMs }
    }

    // ────────────────────────────────────────────────────────────────────────
    // الإنشاء
    // ────────────────────────────────────────────────────────────────────────

    /**
     * ينشئ نسخة كاملة: كتابة ← بصمة ← تحقّق ← مستند.
     *
     * @param onStage يُنادى باسم المرحلة (معرّف غير مترجم، تُترجمه الشاشة) — للتقدّم لا للتزيين.
     */
    fun create(
        context: Context,
        plan: Plan,
        scope: Scope,
        onStage: (String) -> Unit = {},
    ): CreateOutcome {
        val startedAt = System.currentTimeMillis()
        val root = plan.hasRoot
        val pkg = plan.pkg
        val folder = File(appFolder(context, pkg), MaxBackupModel.folderName(startedAt))
        val omissions = mutableListOf<String>()

        if (!scope.anySelected) {
            return CreateOutcome(false, null, 0, 0, listOf("nothing_selected"), null)
        }
        if (!folder.exists() && !folder.mkdirs()) {
            EventLog.error(SCREEN, "create_folder")
            return CreateOutcome(false, null, 0, 0, listOf("folder_unwritable"), null)
        }

        val entries = mutableListOf<ManifestEntry>()
        var failedStage: String? = null

        // ---- APK والتقسيمات --------------------------------------------------
        if (scope.apk) {
            onStage(STAGE_APK)
            val apkFiles = buildList {
                plan.component(ComponentKind.APK)?.source?.let { add(it) }
                if (plan.component(ComponentKind.SPLIT_APK)?.availability == Availability.AVAILABLE) {
                    context.packageManager.getApplicationInfo(pkg, 0).splitSourceDirs?.forEach { add(it) }
                }
            }.distinct()
            apkFiles.forEachIndexed { index, source ->
                val target = File(folder, "base_$index$APK_SUFFIX")
                val copied = runCatching {
                    File(source).takeIf(File::isFile)?.inputStream()?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } != null
                }.getOrDefault(false)
                if (copied) {
                    entries += entryFor(target, if (index == 0) ComponentKind.APK else ComponentKind.SPLIT_APK, folder)
                } else {
                    omissions += "apk_$index"
                    failedStage = failedStage ?: STAGE_APK
                }
            }
        }

        // ---- البيانات: أرشيفات الجذر ----------------------------------------
        val archives = listOf(
            Triple(STAGE_DATA, ComponentKind.APP_DATA, "app_data.tar.gz"),
            Triple(STAGE_EXTERNAL, ComponentKind.EXTERNAL_DATA, "external.tar.gz"),
            Triple(STAGE_OBB, ComponentKind.OBB, "obb.tar.gz"),
        )
        archives.forEach { (stage, kind, name) ->
            val selected = when (kind) {
                ComponentKind.APP_DATA -> scope.appData
                ComponentKind.EXTERNAL_DATA -> scope.externalData
                else -> scope.obb
            }
            val component = plan.component(kind)
            if (!selected || component?.availability != Availability.AVAILABLE || component.source == null) return@forEach
            if (!root) return@forEach

            onStage(stage)
            val target = File(folder, name)
            val tarred = tarInto(component.source, target, plan.uid)
            if (tarred) {
                entries += entryFor(target, kind, folder)
            } else {
                omissions += stage
                failedStage = failedStage ?: stage
                target.delete()
            }
        }

        // ---- المستند ---------------------------------------------------------
        onStage(STAGE_MANIFEST)

        // التحقّق **قبل** إعلان النجاح: نعيد حساب بصمة كل ملف كتبناه ونقارنها بما سجّلنا.
        val verifiedEntries = entries.map { entry ->
            val actual = sha256Of(File(folder, entry.file).absolutePath)
            if (entry.sha256 != null && actual != null && entry.sha256.equals(actual, ignoreCase = true)) {
                entry
            } else {
                omissions += "unverified:${entry.file}"
                failedStage = failedStage ?: STAGE_MANIFEST
                entry
            }
        }

        val verified = verifiedEntries.isNotEmpty() &&
            omissions.none { it.startsWith("unverified:") }

        val manifest = Manifest(
            pkg = pkg,
            label = plan.label,
            versionCode = plan.versionCode,
            versionName = plan.versionName,
            createdAtMs = startedAt,
            deviceModel = currentDeviceModel(context),
            soc = ProfileSharing.currentSoc(),
            hadRoot = root,
            encrypted = false,
            complete = verified,
            entries = verifiedEntries,
            omissions = omissions,
        )

        if (!writeManifest(folder, manifest)) {
            EventLog.error(SCREEN, "write_manifest")
            return CreateOutcome(false, folder.absolutePath, 0, 0, omissions, STAGE_MANIFEST)
        }

        val total = verifiedEntries.sumOf { it.bytes }
        try {
            EventLog.result(
                screen = SCREEN,
                action = "backup",
                target = pkg,
                success = verified,
                durationMs = System.currentTimeMillis() - startedAt,
            )
        } catch (_: Exception) {
            // التسجيل لا يُسقط نسخة نجحت.
        }

        return CreateOutcome(
            success = verified,
            folder = folder.absolutePath,
            bytes = total,
            entryCount = verifiedEntries.size,
            omissions = omissions,
            failedStage = failedStage,
        )
    }

    /**
     * أرشيف دليل بـ`tar`.
     *
     * ونستعيد الملكية إلى UID التطبيق بعد الكتابة: الملف يُنشئه root، فإن بقي ملكًا له تعذّر
     * على التطبيق حذفه لاحقًا (فتتحوّل «إدارة النسخ» إلى وظيفة تحتاج جذرًا دائمًا بلا سبب).
     */
    private fun tarInto(source: String, target: File, appUid: Int?): Boolean {
        val ok = shell(
            "mkdir -p ${quote(target.parentFile?.absolutePath ?: "")}",
            "tar -c -z -f ${quote(target.absolutePath)} -C ${quote(source)} . 2>/dev/null",
            "test -s ${quote(target.absolutePath)} && echo y",
        )?.firstOrNull() == "y"
        if (!ok) return false
        adoptOwnership(target.absolutePath, appUid)
        return true
    }

    /**
     * يُعيد ملكية ما كتبه root إلى UID التطبيق.
     *
     * ليس ترفًا: على أندرويد ١١+ يُرى تخزين التطبيق عبر FUSE، وملف يملكه root قد لا يمرّ
     * إلى التطبيق أصلًا. نفس ما يفعله `LogUtil` بملفّه بعد كتابته من الجذر: `chown` ثم
     * `chmod` ثم `restorecon`.
     */
    internal fun adoptOwnership(path: String, appUid: Int?) {
        val owner = appUid ?: return
        shell(
            "chown $owner:$owner ${quote(path)} 2>/dev/null",
            "chmod 600 ${quote(path)} 2>/dev/null",
            "restorecon ${quote(path)} 2>/dev/null",
        )
    }

    internal fun entryFor(file: File, kind: ComponentKind, folder: File): ManifestEntry = ManifestEntry(
        file = file.relativeTo(folder).path,
        kind = kind,
        bytes = file.length(),
        sha256 = sha256Of(file.absolutePath),
    )

    fun currentDeviceModel(context: Context): String? =
        runCatching { getRealDeviceName(context).takeIf { it.isNotBlank() } }.getOrNull()
            ?: Build.MODEL?.takeIf { it.isNotBlank() }

    // ────────────────────────────────────────────────────────────────────────
    // الفحص
    // ────────────────────────────────────────────────────────────────────────

    /** حكم سلامة لكل مدخل، مقروءًا من القرص الآن لا من ذاكرة سابقة. */
    fun verify(handle: Handle): Map<String, Integrity> {
        val folder = File(handle.folder)
        val manifest = readManifest(folder) ?: return emptyMap()
        return manifest.entries.associate { entry ->
            val file = File(folder, entry.file)
            entry.file to MaxBackupModel.integrityOf(
                expectedSha = entry.sha256,
                actualSha = if (file.isFile) sha256Of(file.absolutePath) else null,
                exists = file.isFile,
            )
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // الاسترجاع
    // ────────────────────────────────────────────────────────────────────────

    /**
     * يسترجع نسخة **بعد** اجتياز بوابة `MaxBackupModel.restoreDecision` — ولا يعيد الحكم هنا،
     * فالقرار قرار واحد في مكان واحد (قاعدة المستودع: مصدر حقيقة واحد).
     *
     * ⚠️ هذا هو المسار الوحيد في المحرّك الذي يكتب **خارج** مجلدنا: إلى بيانات التطبيقات.
     * ولهذا: كل مدخل يُفحص قبل أي كتابة، والتطبيق يُوقف قسرًا قبل الفكّ، والملكية وسياق SELinux
     * يُعادان بعد الفكّ — وإلا خرج التطبيق معطوبًا بدل مستعادًا.
     */
    /**
     * يحسب قرار الاسترجاع لهذه النسخة: هل هو مسموح، وما التحذيرات.
     *
     * وُجدت لأن **الشاشة تحتاج الحكم قبل أن تسأل المستخدم**، ولكي لا يتفرّع الحكم إلى نسختين:
     * واحدة تعرض وواحدة تنفّذ — وهو بالضبط كيف يمرّ استرجاع لم يُفحص.
     */
    @Suppress("DEPRECATION")
    fun decisionFor(context: Context, handle: Handle): MaxBackupModel.RestoreDecision? {
        val manifest = readManifest(File(handle.folder)) ?: return null
        val installed = runCatching {
            context.packageManager.getApplicationInfo(manifest.pkg, 0)
        }.isSuccess
        val versionCode = runCatching {
            context.packageManager.getPackageInfo(manifest.pkg, 0).let {
                if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong()
            }
        }.getOrNull()
        return MaxBackupModel.restoreDecision(
            manifest = manifest,
            verdicts = verify(handle),
            hasRoot = hasRoot(),
            currentDeviceModel = currentDeviceModel(context),
            currentSoc = ProfileSharing.currentSoc(),
            currentVersionCode = versionCode,
            packageInstalled = installed,
        )
    }

    fun restore(context: Context, handle: Handle, onStage: (String) -> Unit = {}): RestoreOutcome {
        val folder = File(handle.folder)
        val manifest = readManifest(folder) ?: return RestoreOutcome(false, null, MaxBackupModel.RestoreBlock.NO_ENTRIES)

        val decision = decisionFor(context, handle)
            ?: return RestoreOutcome(false, null, MaxBackupModel.RestoreBlock.NO_ENTRIES)
        if (!decision.allowed) {
            EventLog.userTriggered(SCREEN, "restore_blocked", manifest.pkg)
            return RestoreOutcome(false, null, decision.block)
        }

        var failedStage: String? = null
        val uid = runCatching { context.packageManager.getApplicationInfo(manifest.pkg, 0).uid }.getOrNull()

        // ---- إعادة تثبيت الحزم: أوّلًا، لأن فكّ البيانات فوق نسخة قديمة يُفسدها ----------
        val apkEntries = manifest.entries.filter { it.kind == ComponentKind.APK || it.kind == ComponentKind.SPLIT_APK }
        if (apkEntries.isNotEmpty()) {
            onStage(STAGE_APK)
            val ok = apkEntries.all { entry ->
                val apk = File(folder, entry.file)
                Shell.cmd("pm install -r -d ${quote(apk.absolutePath)} 2>/dev/null").exec().isSuccess
            }
            if (!ok) failedStage = STAGE_APK
        }

        // ---- إيقاف قسري: الفكّ فوق تطبيق يعمل يعني ملفات مفتوحة تُكتب فوقها ------------
        Shell.cmd("am force-stop ${quote(manifest.pkg)}").exec()

        val dataEntries = manifest.entries.filter { it.kind == ComponentKind.APP_DATA }
        if (dataEntries.isNotEmpty()) {
            onStage(STAGE_DATA)
            val destination = MaxBackupModel.destinationOf(ComponentKind.APP_DATA, manifest.pkg)
            if (destination == null || !extractInto(folder, dataEntries, destination)) {
                failedStage = failedStage ?: STAGE_DATA
            } else {
                // الملكية والسياق بعد الفكّ — لا نعتمد على أعلام tar لأن toybox لا يضمنها.
                if (uid != null) {
                    shell("chown -R $uid:$uid ${quote(destination)} 2>/dev/null")
                }
                shell("restorecon -RF ${quote(destination)} 2>/dev/null")
            }
        }

        val externalEntries = manifest.entries.filter { it.kind == ComponentKind.EXTERNAL_DATA }
        if (externalEntries.isNotEmpty()) {
            onStage(STAGE_EXTERNAL)
            val destination = MaxBackupModel.destinationOf(ComponentKind.EXTERNAL_DATA, manifest.pkg)
            if (destination == null || !extractInto(folder, externalEntries, destination)) {
                failedStage = failedStage ?: STAGE_EXTERNAL
            }
        }

        val obbEntries = manifest.entries.filter { it.kind == ComponentKind.OBB }
        if (obbEntries.isNotEmpty()) {
            onStage(STAGE_OBB)
            val destination = MaxBackupModel.destinationOf(ComponentKind.OBB, manifest.pkg)
            if (destination == null || !extractInto(folder, obbEntries, destination)) {
                failedStage = failedStage ?: STAGE_OBB
            }
        }

        val success = failedStage == null
        try {
            EventLog.result(
                screen = SCREEN,
                action = "restore",
                target = manifest.pkg,
                success = success,
                durationMs = 0L,
            )
        } catch (_: Exception) {
            // التسجيل لا يُسقط استرجاعًا نجح.
        }

        return RestoreOutcome(success, failedStage, MaxBackupModel.RestoreBlock.NONE)
    }

    private fun extractInto(folder: File, entries: List<ManifestEntry>, destination: String): Boolean {
        val archive = entries.firstOrNull()?.let { File(folder, it.file) } ?: return false
        if (!archive.isFile) return false
        val created = shell("mkdir -p ${quote(destination)} && echo y")?.firstOrNull() == "y"
        if (!created) return false
        return shell(
            "tar -x -z -f ${quote(archive.absolutePath)} -C ${quote(destination)} 2>/dev/null && echo y",
        )?.firstOrNull() == "y"
    }

    // ────────────────────────────────────────────────────────────────────────
    // الحذف والاحتفاظ
    // ────────────────────────────────────────────────────────────────────────

    fun delete(handle: Handle): Boolean {
        val folder = File(handle.folder)
        // الملفات التي أنشأها root تحتاج root للحذف؛ ونجرّب الحذف العادي أولًا فلا نطلب
        // صلاحية لما لا يحتاجها.
        val plain = runCatching { folder.deleteRecursively() }.getOrDefault(false)
        if (plain && !folder.exists()) return true
        val removed = shell("rm -rf ${quote(folder.absolutePath)} && echo y")?.firstOrNull() == "y"
        EventLog.userTriggered(SCREEN, "delete_backup", handle.pkg)
        return removed
    }

    /**
     * يوسم نسخةً «تُحفَظ للأبد»، أو يرفع الوسم عنها.
     *
     * والوسم في المستند نفسه لا في تفضيلات التطبيق: النسخة قد تُنسخ إلى جهاز آخر،
     * وقرار «لا تحذف هذه» يجب أن يسافر معها لا أن يبقى في جهاز أخذها.
     *
     * ولا يمسّ الوسم ما فُحص: البصمات في المستند عن الملفات، والتقليم يُقرأ منه وحده.
     */
    fun setKeptForever(handle: Handle, value: Boolean): Boolean {
        val folder = File(handle.folder)
        val manifest = readManifest(folder) ?: return false
        if (manifest.keptForever == value) return true
        val written = writeManifest(folder, manifest.copy(keptForever = value))
        if (written) {
            EventLog.userTriggered(SCREEN, if (value) "keep_forever" else "release_keep", handle.pkg)
        }
        return written
    }

    /** يُطبّق سياسة الاحتفاظ على تطبيق واحد ويعيد ما حُذف فعلًا. */
    fun prune(context: Context, pkg: String, keep: Int): List<Handle> {
        val pruned = MaxBackupModel.toPrune(list(context, pkg), keep).filter(::delete)
        if (pruned.isNotEmpty()) {
            EventLog.userTriggered(SCREEN, "prune", "$pkg:${pruned.size}")
        }
        return pruned
    }
}
