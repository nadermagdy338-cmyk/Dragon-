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

/**
 * `OCR-01` — الجانب المنصّي من النسخ المجدول: تخزين الخطة، وتسجيلها في `JobScheduler`،
 * وتنفيذ النسخة عند مجيء المهمة.
 *
 * **ولماذا `JobScheduler` لا `WorkManager`:** شرط البطارية (`setRequiresCharging`) وشرط
 * الشبكة (`setRequiredNetworkType`) و«يستمرّ بعد الإقلاع» (`setPersisted`) كلها فيه بلا
 * تبعية جديدة. و`WorkManager` يحتاج إضافة مكتبة إلى البناء، وميزتها الأساسية هنا (التراجع
 * والتسلسل) لا نحتاجها: مهمة واحدة يومية. فأقلّ آلة تكفي القانون.
 *
 * **وما لا يمكن أن يعمل بلا جذر:** المجموعات تحت `/data` وأصناف النظام التي تُقرأ من مورد
 * مُprivileged. فالتشغيل المجدول **لا يطلب جذرًا من نفسه**: يُنفّذ ما يستطيعه، ويُبلّغ
 * `FAILED` عمّا لم يستطعه، ولا يخدع المستخدم بنسخة ناقصة تُعلن نجاحًا.
 *
 * **وثالثة:** لا نكتب على العتاد ولا نُعدّل إعدادًا؛ كل ما يفعله هذا الملف قراءة ملفات
 * وكتابة أرشيف في مجلد `Max Backup` نفسه. فلا يمرّ بأي مُحكِّم عتاد (ADR-11) لأنه لا يمسّه.
 */
package nd.max.ui.util

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object MaxBackupScheduler {

    const val SCREEN = "MaxBackup"

    /** معرّف المهمة: ثابت، لأن تسجيلين بمعرّفين مختلفين يُنتجان نسختين مجدولتين. */
    const val JOB_ID = 4261

    /** مجلد أرشيفات المجموعات، داخل تخزين النسخ نفسه. */
    const val FOLDERS_PKG = "folders"

    /** كم تشغيلًا محفوظًا لكل مجموعة. يُشارَك مع تقليم النسخ بالبنية لا بالرقم. */
    const val KEEP_FOLDER_RUNS = 3

    private fun setsFile(context: Context): File = File(context.filesDir, MaxBackupFolders.FILE_NAME)

    private fun planFile(context: Context): File = File(context.filesDir, MaxBackupSchedule.FILE_NAME)

    private fun favoritesFile(context: Context): File =
        File(context.filesDir, MaxBackupFavorites.FILE_NAME)

    // ────────────────────────────────────────────────────────────────────────
    // المجموعات والخطة: قراءة وكتابة ذرّية
    // ────────────────────────────────────────────────────────────────────────

    fun readSets(context: Context): List<MaxBackupFolders.FolderSet> = runCatching {
        val file = setsFile(context)
        if (!file.isFile) emptyList() else MaxBackupFolders.decode(file.readText())
    }.getOrDefault(emptyList())

    private fun writeAtomically(file: File, text: String): Boolean = runCatching {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.part")
        temp.writeText(text)
        // كتابة ذرّية: نصف ملف سياسة أسوأ من غيابه، لأن القارئ سيبني عليه قرار نسخ.
        temp.renameTo(file) || file.exists()
    }.getOrDefault(false)

    fun writeSets(context: Context, sets: List<MaxBackupFolders.FolderSet>): Boolean =
        writeAtomically(setsFile(context), MaxBackupFolders.encode(sets))

    fun readPlan(context: Context): MaxBackupSchedule.Plan = runCatching {
        val file = planFile(context)
        if (!file.isFile) MaxBackupSchedule.Plan() else MaxBackupSchedule.decode(file.readText())
    }.getOrDefault(MaxBackupSchedule.Plan())

    fun writePlan(context: Context, plan: MaxBackupSchedule.Plan): Boolean =
        writeAtomically(planFile(context), MaxBackupSchedule.encode(plan))

    /**
     * `OCR-04`: المفضّلة. تُخزَّن هنا مع بقية السياسة لا في تفضيلات المنصّة — الملف الواحد
     * الذرّي هو نفس الانضباط، ومن قرأ هذه القائمة علِم من أين جاءت.
     */
    fun readFavorites(context: Context): List<String> = runCatching {
        val file = favoritesFile(context)
        if (!file.isFile) emptyList() else MaxBackupFavorites.decode(file.readText())
    }.getOrDefault(emptyList())

    fun writeFavorites(context: Context, favorites: List<String>): Boolean =
        writeAtomically(favoritesFile(context), MaxBackupFavorites.encode(favorites))

    // ────────────────────────────────────────────────────────────────────────
    // الجدولة
    // ────────────────────────────────────────────────────────────────────────

    private fun jobs(context: Context): JobScheduler? =
        runCatching { context.getSystemService(JobScheduler::class.java) }.getOrNull()

    /**
     * يحفظ الخطة ويُسلّح التسجيل القادم: مفعّلة ⇒ مهمة واحدة قادمة، مغلقة ⇒ إلغاء.
     *
     * **ولماذا مهمة واحدة تُسلسل نفسها لا مهمة دورية:** الدورية في هذه المنصّة لا تقبل
     * تأخيرًا أوّلًا، وأول تنفيذ فيها يقع **بعد دورة كاملة** — أي أن من اختار «٣:٠٠ صباحًا»
     * لن يُنسخ شيء عنده حتى اليوم التالي في نفس الساعة. فالتسليح يُحسب لكل مرة: تأخير إلى
     * الموعد القادم، والخدمة بعد أن تنتهي تُسلّح التالي. و`setPersisted(true)` يجعله يبقى
     * بعد الإقلاع، وفتح شاشة النسخ يُعيد التسليح أيضًا — فالسلسلة تُستأنف إن انقطعت.
     */
    fun apply(context: Context, plan: MaxBackupSchedule.Plan): MaxBackupSchedule.Plan {
        val clean = MaxBackupSchedule.normalized(plan)
        writePlan(context, clean)
        arm(context, clean)
        return clean
    }

    /** يُسلّح المهمة القادمة حسب الخطة المعطاة. `false` = لم تُسجَّل (معطّلة أو بلا هدف). */
    fun arm(context: Context, plan: MaxBackupSchedule.Plan): Boolean {
        val scheduler = jobs(context) ?: return false
        runCatching { scheduler.cancel(JOB_ID) }
        if (MaxBackupSchedule.blocker(plan) != null) return false
        val delay = MaxBackupSchedule.minutesUntilNextRun(plan, minuteOfDayNow(), isoDayNow()) ?: return false
        val info = JobInfo.Builder(JOB_ID, ComponentName(context, MaxBackupJobService::class.java))
            .setRequiredNetworkType(
                if (plan.wifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_NONE
            )
            .setRequiresCharging(plan.chargingOnly)
            .setPersisted(true)
            // الحدّ الأدنى للتأخير = «ليس قبل الموعد». وما بعده قرار المنصّة، وهو ما تقوله
            // الشاشة للمستخدم بدل أن تُوعده بساعة يملكها هو لا الجهاز.
            .setMinimumLatency(delay.coerceAtLeast(0) * 60_000L)
            .build()
        return runCatching { scheduler.schedule(info) }.isSuccess
    }

    /** إعادة التسليح من الخطة المخزّنة — يُستدعى بعد كل تشغيل وعند فتح شاشة النسخ. */
    fun rearm(context: Context): Boolean = arm(context, readPlan(context))

    fun cancel(context: Context) {
        runCatching { jobs(context)?.cancel(JOB_ID) }
    }

    /** متى التشغيل القادم، بزمن الجهاز. `null` إن كان الجدول متوقفًا. */
    fun nextRunAtMs(plan: MaxBackupSchedule.Plan): Long? {
        val delay = MaxBackupSchedule.minutesUntilNextRun(plan, minuteOfDayNow(), isoDayNow()) ?: return null
        return System.currentTimeMillis() + delay * 60_000L
    }

    fun minuteOfDayNow(): Int {
        val now = Calendar.getInstance()
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    }

    /** الاثنين=١ … الأحد=٧ — مطابقًا لـ`Calendar` بعد تحويله. */
    fun isoDayNow(): Int {
        val raw = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return if (raw == Calendar.SUNDAY) 7 else raw - 1
    }

    fun grantedNow(context: Context): Set<String> = MaxBackupSystem.ALL_PERMISSIONS.filter {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }.toSet()

    private fun isCharging(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(BatteryManager::class.java) ?: return false
        val status = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }.getOrDefault(false)

    private fun isUnmetered(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)

    // ────────────────────────────────────────────────────────────────────────
    // التنفيذ
    // ────────────────────────────────────────────────────────────────────────

    /**
     * ينفّذ الخطة الآن.
     *
     * `enforceConditions = true` في المسار المجدول (تُحترم شروط البطارية والشبكة)، و`false`
     * في زرّ «شغّل الآن»: من ضغط الزرّ طلب النسخة الآن، وردّه بشروط لم يطلبها إهانة للنية.
     */
    fun runNow(
        context: Context,
        enforceConditions: Boolean = false,
        onStage: (String) -> Unit = {},
    ): MaxBackupSchedule.Result {
        val plan = readPlan(context)
        if (MaxBackupSchedule.blocker(plan) != null) return MaxBackupSchedule.Result.SKIPPED
        if (enforceConditions && !MaxBackupSchedule.allowsRun(plan, isCharging(context), isUnmetered(context))) {
            // لا يُسجَّل كتشغيل: الشرط لم يتحقّق، و«آخر تشغيل» يجب أن يعني تشغيلًا.
            return MaxBackupSchedule.Result.SKIPPED
        }

        val sets = readSets(context)
        var attempted = 0
        var allOk = true

        plan.sets.forEach { name ->
            val set = sets.firstOrNull { it.name == name } ?: return@forEach
            onStage(set.name)
            attempted++
            allOk = backupSet(context, set) && allOk
        }

        val hasRoot = MaxBackupEngine.hasRoot()
        val granted = grantedNow(context)
        val inventory = runCatching { MaxBackupSystemEngine.inventory(context, hasRoot, granted) }.getOrNull()
        plan.kinds.forEach { written ->
            val kind = MaxBackupSystem.Kind.entries.firstOrNull { it.name == written } ?: return@forEach
            val planOfSystem = inventory ?: return@forEach
            onStage(kind.id)
            attempted++
            val scope = MaxBackupSystem.Scope(setOf(kind))
            val outcome = runCatching { MaxBackupSystemEngine.create(context, planOfSystem, scope) }.getOrNull()
            allOk = (outcome?.success == true) && allOk
        }

        val result = when {
            attempted == 0 -> MaxBackupSchedule.Result.SKIPPED
            allOk -> MaxBackupSchedule.Result.OK
            else -> MaxBackupSchedule.Result.FAILED
        }
        writePlan(
            context,
            MaxBackupSchedule.recorded(plan, System.currentTimeMillis(), result),
        )
        return result
    }

    /**
     * نسخة مجموعة مجلدات واحدة: **أرشيف لكل مجلد** في مجلد التشغيل، مع فهرس يشرح كل ملف.
     *
     * ولماذا أرشيف لكل مجلد لا أرشيف واحد: `tar -C <الأب> <الاسم>` هو ما يجعل المسارات
     * داخل الأرشيف **نسبية**. ولا أب مشترك بين `DCIM` و`Documents`، فالجمع في أرشيف واحد
     * كان سيستلزم إمّا مسارات مطلقة داخل الأرشيف (تُفكّ إلى أماكن لم يقصدها المستخدم) أو
     * أداة ضغط غير موجودة عندنا. والفهرس يربط كل أرشيف بأبيه، فيصير الاسترجاع ممكنًا بلا تخمين.
     */
    private fun backupSet(context: Context, set: MaxBackupFolders.FolderSet): Boolean {
        val home = File(File(MaxBackupEngine.ensureRoot(context), FOLDERS_PKG), set.name)
        val stamp = System.currentTimeMillis()
        val runFolder = File(home, MaxBackupModel.folderName(stamp))
        if (!runFolder.exists() && !runFolder.mkdirs()) return false

        val index = StringBuilder()
        var archived = 0
        set.paths.forEach { path ->
            val parent = FileBrowser.parentOf(path) ?: return@forEach
            val name = FileBrowser.nameOf(path)
            if (name.isBlank()) return@forEach
            if (PrivilegedShell.run("test -d ${PrivilegedShell.quote(path)}") == null) {
                // مسار اختفى (بطاقة SD أُزيلت، أو مجلد حُذف): يُسجَّل غيابه ولا يُفشل البقية.
                index.append(name).append('\t').append(path).append('\t').append("missing").append('\n')
                return@forEach
            }
            val target = File(runFolder, "$name.tar.gz")
            val outcome = FileSystemEngine.compress(listOf(path), target.absolutePath)
            if (outcome.verified) {
                archived++
                index.append(name)
                    .append('\t').append(path)
                    .append('\t').append(parent)
                    .append('\t').append(target.name)
                    .append('\t').append(target.length())
                    .append('\n')
            } else {
                index.append(name).append('\t').append(path).append('\t').append("failed").append('\n')
            }
        }

        if (archived == 0) {
            runFolder.delete()
            return false
        }
        File(runFolder, INDEX_FILE).writeText(index.toString())
        pruneRuns(home)
        return true
    }

    internal const val INDEX_FILE = "index.txt"

    /** تقليم تشغيلات المجموعة بنفس قاعدة تقليم النسخ (`MaxBackupRetention`) لا بقاعدة ثانية. */
    private fun pruneRuns(home: File) {
        val runs = home.listFiles { file -> file.isDirectory }?.toList().orEmpty()
        if (runs.size <= KEEP_FOLDER_RUNS) return
        val copies = runs.map { folder ->
            MaxBackupRetention.Copy(
                folder = folder.name,
                createdAtMs = folder.lastModified(),
                kept = false,
            )
        }
        MaxBackupRetention.toRemove(copies, KEEP_FOLDER_RUNS).forEach { old ->
            runCatching { File(home, old.folder).deleteRecursively() }
        }
    }
}

/**
 * الخدمة التي تُشغّل المهمة — رقيقة عن قصد: كل المنطق في [MaxBackupScheduler.runNow]،
 * وهذه تعرف الإشعار فقط (`jobFinished`) ولا تعرف ماذا يُنسخ.
 */
class MaxBackupJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        val context = applicationContext
        scope.launch {
            runCatching { MaxBackupScheduler.runNow(context, enforceConditions = true) }
            // التسليح للتشغيل القادم يُتمّ هنا لا في المهمة نفسها: التسلسل صار مسؤولية
            // هذه الخدمة، ولو نسيته لوقف الجدول بعد أول نسخة بلا أن يشكو شيء.
            runCatching { MaxBackupScheduler.rearm(context) }
            // `false` = لا إعادة مراجعة: الموعد القادم مسجَّل، والإعادة الفورية كانت
            // ستحوّل فشلًا (جذر غائب مثلًا) إلى حلقة محاولات طوال اليوم.
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        scope.cancel()
        return true
    }
}
