/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */

package nd.max.core.daemon

import java.io.File

/**
 * قياس الخادم الأصلي: **حَيّ · ميّت · مجهول** — ثلاثة أحكام لا اثنان.
 *
 * **لماذا وُجد هذا الملفّ (مقيسًا من الشجرة لا محكيّا):** كان في التطبيق **تعريف واحد** لـ«الخادم
 * حيّ» (`pidof` في `RootUtils.getServiceStatusRes`) يستعمله البانر وشاشة الفحص، وفي الخادم تعريف
 * ثانٍ مختلف تمامًا: بوّابة `check_running_state()` في `archdaemon/jni/src/FileUtility/LockFile.c`
 * تفتح `/data/adb/.config/MaxManager/API/.lock` وتجرّب `flock(LOCK_EX|LOCK_NB)` — فنجاح القفل
 * يعني **لا خادم**، وفشله يعني خادمًا حيًّا. ومعها ثالث: `persist.sys.maxmanager.service` (رقم
 * العملية) الذي يكتبه `PidTracker.c` ويُصفّره كل مسار خروج. فمن قرأ تعريفًا غير تعريف البوّابة
 * رأى «شغّال» وهي تقول «لا» — وهذا صنف عطب لا يُشخَّص في الواجهة.
 *
 * **والمقصود هنا ليس توحيد الثلاثة** (`persist.sys.maxmanager.*` مفتاح **يملكه الخادم** ولا يقرؤه
 * التطبيق — صفّه في `fixtures/contracts/system_properties.tsv` يقول `daemon`)، بل:
 *
 * 1. **أن يكون للحيّ تعريفٌ واحد في التطبيق** ([DaemonLivenessProbe])، وهو `pidof` نفسه الذي
 *    يقيسه البانر، **مؤكَّدًا بقياس ثانٍ مستقلّ** (`ps -A`) — لأن اسم `sys.maxmanager-service`
 *    أطول من `TASK_COMM_LEN` (١٥ حرفًا) فيُقتطع في `/proc/<pid>/stat`، فلا يُترك الحكم على أداة
 *    واحدة قد لا تطابق.
 * 2. **وأن يبقى «مجهول» مجهولًا:** الصدفة التي لم تُشغَّل، أو `pidof` غير الموجود، لا تُقرأ
 *    «الخادم ميّت» — لأن أحدًا لا يُعيد تشغيل خادم لم يُثبت موته (ADR-07).
 *
 * وكل ما هنا **خالص** إلا [RootDaemonIo]: النصوص والحكم تُقاس في اختبارات وحدات بلا جهاز،
 * والتنفيذ وحده هو ما يلمس الجهاز.
 */

// ── الحكم على الحياة ────────────────────────────────────────────────────────────

/** ثلاثة أحكام لا حكمان: [Unknown] ليست [Dead]، ولا يُبنى على [Unknown] فعل. */
enum class DaemonLiveness { Alive, Dead, Unknown }

/**
 * حكمٌ خالص على مخرَج الأمرين — بلا صدفة ولا ملفّ.
 *
 * ودلالات `pidof` الحقيقية هي ما يلي، لا ما يُتوقَّع: وجد العمليّة ⇒ يطبعها ويخرج **0**؛ لم يجدها
 * ⇒ لا مخرَج ويخرج **1**؛ والأداة غير موجودة ⇒ الصدفة تقول `... not found` وتخرج **127**.
 */
object DaemonProbe {

    /** أول رقم عملية في مخرَج `pidof` (`"4150 4151"` ⇒ `4150`). و`null` حين لا رقم. */
    fun pid(output: String): Int? = Regex("\\d+").find(output)?.value?.toIntOrNull()

    fun liveness(
        primaryExit: Int,
        primaryStdout: String,
        primaryStderr: String,
        confirmCount: Int?,
    ): DaemonLiveness = when {
        pid(primaryStdout) != null -> DaemonLiveness.Alive
        // التأكيد المستقلّ: العملية موجودة وإن لم يطابقها `pidof` (اقتطاع الاسم).
        (confirmCount ?: 0) >= 1 -> DaemonLiveness.Alive
        // الأمر نفسه لم يُنفَّذ: لا حكم على الخادم من فشل أداة.
        primaryExit < 0 -> DaemonLiveness.Unknown
        primaryExit == 127 || primaryStderr.contains("not found", ignoreCase = true) -> DaemonLiveness.Unknown
        // هذا هو نصّ `pidof` عند الغياب بعينه: بلا مخرَج ورمز 1.
        primaryExit == 1 && primaryStdout.isBlank() -> DaemonLiveness.Dead
        // وما عداه لا يُقال فيه شيء: رمز خروج غير مفهوم ليس إثباتًا للغياب.
        else -> DaemonLiveness.Unknown
    }
}

/** نتيجة أمر صدفة واحد. */
data class ShellOutcome(val exit: Int, val stdout: String, val stderr: String)

/**
 * ما يلمس الجهاز — خلف واجهة واحدة، فيُقاس منطق المشرف كلّه بمُزيف (fake) بلا جهاز ولا صدفة.
 */
interface DaemonIo {
    fun shell(command: String): ShellOutcome
    fun readText(path: String): String?
    fun exists(path: String): Boolean
    fun sleep(ms: Long)
    /** ساعة أحاديّة (مونوتونيك) بالملي ثانية — لا ساعة حائط، فلا يقفز بها ضبط الوقت. */
    fun uptimeMs(): Long
}

/**
 * التشغيل الحقيقي. ويُراد منه أمران فقط:
 *
 * * **القراءة قبل الانتظار:** المخرَج يُقرأ ثم `waitFor` — والحدّ المعلن أنّ كل أمر في هذا الملفّ
 *   يُخرج ما هو أصغر من أنبوبة النظام بأشواط (‏`pidof` سطر، و`ps` سطر، و`tail -n 80` ≈ ١٠ كيلوبايت
 *   مقابل ٦٤)، فالقراءة التسلسلية (out ثم err) لا تملأ أنبوبة ولا تُقفل.
 * * **ساعة مونوتونيك:** `System.nanoTime()` لا `SystemClock` — فالملفّ يُقاس على JVM في اختبارات
 *   الوحدات، و`SystemClock` يُرمي خارج جهاز أندرويد.
 */
object RootDaemonIo : DaemonIo {

    override fun shell(command: String): ShellOutcome = try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exit = process.waitFor()
        process.destroy()
        ShellOutcome(exit, stdout, stderr)
    } catch (t: Throwable) {
        ShellOutcome(-1, "", t.message.orEmpty())
    }

    override fun readText(path: String): String? = runCatching { File(path).readText() }.getOrNull()

    override fun exists(path: String): Boolean = File(path).exists()

    override fun sleep(ms: Long) {
        if (ms > 0) Thread.sleep(ms)
    }

    override fun uptimeMs(): Long = System.nanoTime() / 1_000_000L
}

/**
 * التشغيل من **عملية التطبيق** — بصدفة `su` لا بـ`Runtime.exec`.
 *
 * **والفرق ليس تفصيلًا (عطب كامن في الشجرة، مكتشف مع هذه التكملة):** `Runtime.exec("sh -c …")`
 * من التطبيق يُنفَّذ بـ**uid التطبيق** لا بالجذر، و`sys.maxmanager-service` ثنائيّة الوحدة في
 * `/data/adb/modules/MaxManager` — فلا يُنفَّذ بها شيء. ومَن كان يستعمل [`RootDaemonIo`] من الواجهة
 * (شاشة فحص الشحن) كان يستدعي [`DaemonStarter`] بصدفة بلا جذر، فيعود `accepted=false` دائمًا
 * ويُظنّ الخادم عصيًّا على التشغيل. والرفيق وحده كان سليمًا في هذا الشأن — لأنه **يعمل بالجذر
 * أصلًا**، فمنه يُستعمل [`RootDaemonIo`] كما هو، ومن التطبيق يُستعمل هذا.
 *
 * ولا ينتظر هذا المُنفِّذ صدفةً أبدًا في مسار الواجهة: `libsu` تُخزّن الصدفة بعد أوّل منح، ومن
 * لا جذر له يعود بـ`exit = -1` و«مجهول» لا «ميّت» (ADR-07).
 */
object RootShellDaemonIo : DaemonIo {

    override fun shell(command: String): ShellOutcome = try {
        val result = com.topjohnwu.superuser.Shell.cmd(command).exec()
        ShellOutcome(
            if (result.isSuccess) 0 else 1,
            result.out.joinToString("\n"),
            result.err.joinToString("\n"),
        )
    } catch (t: Throwable) {
        ShellOutcome(-1, "", t.message.orEmpty())
    }

    override fun readText(path: String): String? = runCatching { File(path).readText() }.getOrNull()

    override fun exists(path: String): Boolean = File(path).exists()

    override fun sleep(ms: Long) {
        if (ms > 0) Thread.sleep(ms)
    }

    override fun uptimeMs(): Long = System.nanoTime() / 1_000_000L
}

/**
 * قياس الحياة: الأمر الأساسي، ثم **تأكيد مستقلّ** لا يُنفَّذ إلا إن نفى الأساسي وجود الخادم
 * (فلا كلفة في المسار السليم)، ثم الحكم الخالص في [DaemonProbe.liveness].
 */
class DaemonLivenessProbe(private val io: DaemonIo = RootDaemonIo) {

    fun read(): DaemonLiveness {
        val primary = io.shell(DaemonCommands.PROBE)
        if (DaemonProbe.pid(primary.stdout) != null) return DaemonLiveness.Alive
        if (primary.exit < 0 || primary.exit == 127) {
            return DaemonProbe.liveness(primary.exit, primary.stdout, primary.stderr, null)
        }
        val confirm = io.shell(DaemonCommands.PROBE_CONFIRM).stdout.trim().toIntOrNull()
        return DaemonProbe.liveness(primary.exit, primary.stdout, primary.stderr, confirm)
    }
}

// ── الأوامر ─────────────────────────────────────────────────────────────────────

/**
 * نصوص الأوامر في موضع واحد — فيُقاس نصّها في اختبار، ولا تُنسخ في شاشة أخرى فتنحرف.
 *
 * و[START] **ليس `--rerun`**: ذاك يمرّ بـ`sys.maxmanager-utilityconf restartservice`
 * (`binutils/src/utils/mod.rs:255`) فيقتل بند `pkill -9 -f sys.maxmanager-appmonitoring` **الرفيق
 * نفسه** — وهو من يشغّل هذه الوظيفة — ثم ينفّذ `service.sh` الذي يبدأ بـ`"$BIN_SVC" --clearlogs`
 * **فيمحو سجلّ الخادم** في كل محاولة. ولذلك صار التشغيل هنا **مباشرًا**: `--run` هو نفسه الذي
 * ينفّذه `mainfiles/service.sh` في الإقلاع (`exec "$BIN_SVC" --run`)، والخادم يُدمج نفسه
 * (`daemon(0,0)` في `archdaemon/jni/src/System/System.c:67`) فلا يحتاج `setsid` ولا `nohup`.
 */
object DaemonCommands {

    /** ما يقيسه التطبيق كلّه (البانر · شاشة الفحص · `RootUtils.getServiceStatusRes`). */
    const val PROBE = "pidof sys.maxmanager-service"

    /**
     * القياس الثاني. والقوسان حول الحرف الأول **مقصودان**: بغيرهما يُطابق `grep` سطر نفسه في
     * `ps` (لأن نمطه داخل سطر أوامر عملية الـ`grep`)، فيُقال «حَيّ» عن ميّت.
     */
    const val PROBE_CONFIRM = "ps -A 2>/dev/null | grep -c '[s]ys.maxmanager-service'"

    /** يشغّل الخادم مفصولًا **بلا** مسح سجلّ: مخرَجه يُضاف إلى سجلّه هو (رسائل `stderr` الخام). */
    fun start(serviceBin: String, logPath: String): String =
        "$serviceBin --run >> '$logPath' 2>&1 </dev/null &"

    /** إصدار الخادم من فمه: `printversion` قبل بوّابة التوفّر في `Main.c` فيعمل والخادم متوقّف. */
    fun version(serviceBin: String): String = "$serviceBin --version 2>/dev/null"

    /** ذيل سجلّ الخادم — أصغر أمر يكفي لقراءة آخر سطر حاسم. */
    fun logTail(logPath: String, lines: Int): String = "tail -n $lines '$logPath' 2>/dev/null"

    /** مسح نداءات مسح السجلّ — يُقاس في اختبارٍ أن مسار التشغيل لا يستعملها. */
    val DESTRUCTIVE = listOf("--rerun", "--clearlogs")
}

// ── قراءة سبب الخروج من سجلّ الخادم نفسه ───────────────────────────────────────

/**
 * لماذا خرج الخادم. والرموز هنا **نصوص الخادم نفسه** لا صياغة ثانية لها، فالسطر يُطابق
 * `EVENT=…` الذي كتبه `log_zenith` في `MaxManager.log`.
 *
 * والقائمة كاملة من المصدر: المسارات التي تُنفّذ `exit(EXIT_FAILURE)` ستّة
 * (`StartupInit/DaemonStartup.c` ٢٥/٤٣/٤٨/٦٩ · `MaxManagerUtility/ModuleIntegrity.c` ١٤٧/١٦٥ ·
 * ومسار الخروج من `InotifyWatcher.c`)، ولكلٍّ سطرٌ مميّز — فلا يُخمَّن السبب ولا يُترك «متوقّفٌ فقط».
 */
enum class DaemonExit(val code: String) {
    /** حارس الهويّة: `module.prop` لا يحمل `name`/`author` المشحونين، أو غير مقروء أصلًا. */
    ModuleIdentityMismatch("MODULE_INTEGRITY_FAILED reason=modified_by_third_party"),

    /** سطر `version=` في `module.prop` لا يساوي `MODULE_VERSION` المطبوع في الخادم. */
    ModuleVersionMismatch("MODULE_INTEGRITY_FAILED reason=version_mismatch"),

    /** مهلة انتظار الرفيق الـJava: ١٢٠ فحصًا بلا قفل ⇒ خروج. */
    JavaCompanionTimeout("JAVA_COMPANION_TIMEOUT"),

    /**
     * **غياب عمليّة الرفيق نفسها** عن `/proc` بعد ٢٠ ثانية (بلا قفل أيضًا) ⇒ خروج فوريّ بسبب مسمّى.
     *
     * والفرق عن [JavaCompanionTimeout] ليس تسميةً: ذاك يعني «لم يحمل القفل في ١٢٠ ثانية» — وهو ما
     * ينطبق على رفيق يتهيّأ ببطء **كما** ينطبق على ميّت. وهذا يعني «لا عمليّة باسم `nd.max.AppMonitor`
     * على الجهاز» — وهو قياس مباشر لا استنتاج من قفل. والجهاز الحقيقي (٢٠٢٦-١٠-٠١) خرج بالأول بعد
     * **دقيقتين** من موت الثاني في الثانية الأولى.
     */
    JavaCompanionAbsent("JAVA_COMPANION_ABSENT"),

    /** حارس سلامة الملفّات: `dumpsys` معدَّل، أو قائمة الألعاب مفقودة. */
    IntegrityGuard("INTEGRITY_CHECK_FAILED"),

    /** عُلّم مجلد الوحدة بـ`update`: خروج مقصود حتى الإقلاع. */
    ModuleUpdateDetected("MODULE_UPDATE_DETECTED"),

    /** عُلّم مجلد الوحدة بـ`remove`: خروج مقصود. */
    ModuleRemoved("MODULE_REMOVED"),

    /**
     * أحد أخرج الخادم لأنّ خصيصة الحالة صارت `stopped` — وهذا ما يفعله مسار `--rerun` نفسه
     * (`restartservice` يكتب `persist.sys.maxmanager.state=stopped` قبل أن يُعيد الإقلاع).
     * وهو **الفرق العمليّ بين المسارين**: تشغيلنا المباشر لا يلمس الحالة أصلًا.
     */
    StateStopped("DAEMON_STOPPED reason=checkstate_state="),

    /** قفل الرفيق انفتح: `DAEMON_STOPPED reason=java_companion_lock_released`. */
    JavaCompanionGone("DAEMON_STOPPED reason=java_companion_lock_released"),

    /** إشارة (`SIGTERM`/`SIGINT`) — مَن أوقفه أمر آخر. */
    Signalled("DAEMON_EXIT signal="),

    /** `daemon()` نفسه فشل عند الإقلاع. */
    DaemonizeFailed("DAEMON_START_FAILED"),
}

/** الأحداث العابرة التي تشبه الخروج وليست خروجًا — تُطرح قبل الحكم. */
private val NON_TERMINAL_MARKERS = listOf(
    "MODULE_PROP_MODIFIED",
    "MODULE_INTEGRITY_PASSED",
    "DAEMON_STARTED",
    "DAEMON_READY",
)

/**
 * قراءة أسطر الخادم — دوالّ خالصة تُقاس بأسطر حقيقيّة مأخوذة من السجلّ.
 *
 * وترتيب المسح **من الآخر**: آخر سطر حاسم هو السبب، فما قبله تاريخ. وقائمة [NON_TERMINAL_MARKERS]
 * ليست ترفًا: `MODULE_PROP_MODIFIED` يسبق الخروج مباشرةً في الجهاز الحقيقي (ينتبه `inotify` إلى
 * تعديل `module.prop` ثم يُعيد الحارس فحص الهوية)، فلو قُرئ هو لسُجّل «مُعدَّل» بدل السبب.
 */
object DaemonLog {

    fun exitOf(line: String): DaemonExit? {
        if (NON_TERMINAL_MARKERS.any { line.contains(it) }) return null
        return DaemonExit.entries.firstOrNull { line.contains(it.code) }
    }

    /** آخر سبب حاسم في الأسطر، أو `null` إن لم يُقل سبب (فلا يُخترع واحد). */
    fun lastExitOf(lines: List<String>): DaemonExit? =
        lines.asReversed().firstNotNullOfOrNull(::exitOf)

    /** ذيل السجلّ يُقسَّم أسطرًا غير فارغة. */
    fun lastExitOf(output: String): DaemonExit? =
        lastExitOf(output.lineSequence().filter { it.isNotBlank() }.toList())

    /** هل مُسّ `module.prop` داخل هذه الأسطر؟ (سياق السبب لا السبب). */
    fun propWasModified(lines: List<String>): Boolean =
        lines.any { it.contains("MODULE_PROP_MODIFIED") }

    /**
     * الأسطر التي **زادت** بين قراءتين لذيل السجلّ — فتُنسب إلى المحاولة لا إلى تاريخ الملفّ.
     *
     * وثلاث حالات معلنة: الإضافة الطبيعيّة (الذيل القديم بادئة للجديد ⇒ تُعاد الزيادة وحدها)،
     * وسجلّ مُسح أو مُدوَّر (لا بادئة ⇒ **تفريغ**، فلا يُنسب نصّ قديم إلى محاولة لم تكتبه)،
     * وأول قراءة (لا سابق ⇒ كل المخرَج جديد بمنطق «لا شيء قبله»).
     */
    fun newLines(before: String, after: String): List<String> = when {
        after.isBlank() -> emptyList()
        before.isBlank() -> after.lineSequence().filter { it.isNotBlank() }.toList()
        after.startsWith(before) -> after.substring(before.length)
            .lineSequence().filter { it.isNotBlank() }.toList()
        else -> emptyList()
    }
}

// ── هويّة الوحدة (نفس قاعدة الحارس في الخادم) ──────────────────────────────────

/** سطور `module.prop` المعنيّة — كما هي بلا تنظيف، لأن الحارس يقارن بلا تنظيف. */
data class ModulePropIdentity(
    val name: String?,
    val author: String?,
    val version: String?,
)

/** حكم الهوية: مطابقة، أو مخالفة بأسبابها المسمّاة. */
sealed interface IdentityVerdict {
    data object Holds : IdentityVerdict

    data class Violated(
        val identity: ModulePropIdentity,
        val versionMismatch: Boolean,
    ) : IdentityVerdict
}

/**
 * البندقة نفسها التي ينفّذها `module_identity_ok()` في
 * `archdaemon/jni/src/MaxManagerUtility/ModuleIntegrity.c` — **نسخة مطابقة لا رأي جديد**: سطر
 * `key=value` بلا بادئة ولا مسافة حول `=`، والقيمة تُقارن حرفيًّا (فـ`name=MaxManager ` بمسافة
 * زائدة **مخالفة**، كما في `prop_line_equals`).
 *
 * **ولماذا يقيّم التطبيق قاعدة الخادم:** ليس ليُصلحها، بل ليُجيب سؤالًا واحدًا لا يجيبه أحد غيره:
 * «أأُعيد المحاولة أم يتكرّر الموت حتمًا؟». فمخالفة الهوية **حتميّة** (إعادة التشغيل لا تُصلح
 * `module.prop`)، أمّا خروجٌ بسبب هويّة **ملفٍّ صحيح الآن** فهو قراءة ممزّقة لحظة كتابة الملفّ
 * — وهي عابرة، وإعادة المحاولة هي العلاج الصحيح لها.
 *
 * وحدّها المُعلن: الإصدار لا يُحكم عليه إلا إذا قاله الخادم نفسه (`--version` → `MODULE_VERSION`).
 * فإن لم يُقل، لا يُحكم بالبطلان — لأن المجهول ليس دليلًا على مخالفة (ADR-07).
 */
object DaemonIdentity {

    const val EXPECTED_NAME = "MaxManager"
    const val EXPECTED_AUTHOR = "MaxManager Project"

    fun parse(propText: String): ModulePropIdentity {
        var name: String? = null
        var author: String? = null
        var version: String? = null
        propText.lineSequence().forEach { raw ->
            val line = raw.trimEnd('\r', '\n')
            val equals = line.indexOf('=')
            if (equals <= 0) return@forEach
            val key = line.substring(0, equals)
            val value = line.substring(equals + 1)
            when (key) {
                "name" -> name = value
                "author" -> author = value
                "version" -> version = value
            }
        }
        return ModulePropIdentity(name, author, version)
    }

    fun verdict(propText: String, daemonVersion: String?): IdentityVerdict {
        val identity = parse(propText)
        val nameHolds = identity.name == EXPECTED_NAME
        val authorHolds = identity.author == EXPECTED_AUTHOR
        val versionHolds = daemonVersion.isNullOrBlank() || identity.version == daemonVersion
        return if (nameHolds && authorHolds && versionHolds) {
            IdentityVerdict.Holds
        } else {
            IdentityVerdict.Violated(identity, versionMismatch = !versionHolds)
        }
    }
}
