/*
 * Generic privileged filesystem access used by hardware discovery and control.
 * It deliberately knows nothing about a specific vendor or SoC.
 */
package nd.max.core.hardware

import com.topjohnwu.superuser.Shell
import nd.max.core.jni.ProbeBridge
import nd.max.ui.util.EventLog
import nd.max.ui.util.RootIpcManager
import java.io.File

object RootFileAccess {
    private fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun exists(path: String): Boolean = runCatching {
        if (RootIpcManager.ipc?.nodeExists(path) == true) return@runCatching true
        if (File(path).exists()) return@runCatching true
        shellTest(path)
    }.getOrDefault(false)

    /**
     * وجود عدة مسارات في **نداء واحد** — سؤال الوجود لا القيمة.
     *
     * وشاشات الاكتشاف تُجرّب مرشّحين بالتتابع، فكل `exists` كان رحلة كاملة (IPC ثم `test`
     * عبر صدفة). والدفعة الأصليّة تجيب عن القائمة كلها مرة واحدة، والقائمة الفارغة تُحسم
     * بلا أي نداء. وغياب القارئ الأصلي أو حزمة بعدد مخالف يعود إلى [exists] لكل مسار —
     * نفس السلوك القديم حرفيًّا.
     */
    fun existing(paths: List<String>): List<Boolean> {
        if (paths.isEmpty()) return emptyList()
        val native = ProbeBridge.existing(paths)
        if (native != null && native.size == paths.size) return native
        return paths.map { exists(it) }
    }

    /**
     * أول مرشّح **موجود** من قائمة — نداء واحد للمرشّحين كلهم لا رحلة لكل واحد.
     *
     * والعائد `null` يعني «لا أحد منهم موجود» كما كانت `firstOrNull { exists(it) }` تُعيده.
     */
    fun <T> firstExisting(candidates: List<T>, pathOf: (T) -> String): T? {
        if (candidates.isEmpty()) return null
        val flags = existing(candidates.map(pathOf))
        return candidates.indices.firstOrNull { flags.getOrElse(it) { false } }?.let { candidates[it] }
    }

    fun writable(path: String): Boolean = runCatching {
        if (File(path).canWrite()) return@runCatching true
        Shell.cmd("test -w ${quote(path)}").exec().isSuccess
    }.getOrDefault(false)

    fun read(path: String): String? = runCatching {
        RootIpcManager.ipc?.readNode(path)?.trim()?.takeIf { it.isNotEmpty() }
            ?: File(path).takeIf { it.isFile && it.canRead() }?.readText()?.trim()
            ?: shellRead(path)
    }.getOrNull()

    /**
     * قراءة عدة عقد في **نداء واحد** — نفس دلالة [read] لكل عقدة: القيمة المقصوصة غير
     * الفارغة، أو `null` إن لم تُقرأ. والترتيب محفوظ بمحاذاة الفهرس.
     *
     * **لماذا، وبأي ترتيب (تكملة ١١٢):** ثلاث طبقات، الأسرع أولًا:
     *
     * 1. **قارئ Rust الأصلي** (`ProbeBridge`): يفتح الملفات داخل العملية في نداء JNI واحد —
     *    بلا معاملة binder وبلا صدفة. وهذا هو مسار الحالة الشائعة: عقد sysfs الحرارية
     *    وقيم ترددات الأنوية و`/proc/stat` يقرأها uid التطبيق عادةً.
     * 2. **ما حجبته النواة عن uid التطبيق يعود `null` من الطبقة الأولى، فيُسأل عنه وحده**
     *    عبر معاملة IPC واحدة إلى `MtkRootService`. فالسرعة **لا تُشتري بقدرة**: كل عقدة
     *    كانت تُقرأ بالجذر تبقى تُقرأ بالجذر، ولا تُخفى عقدة ولا تُفترض قيمة.
     * 3. وسقوط الحالة (بلا IPC متصل، أو خدمة لا تعرف الدفعة) يعود إلى [read] لكل عقدة —
     *    نفس السلوك القديم حرفيًّا.
     *
     * فالعقدة الغائبة من كل الطبقات تبقى `null` كما كانت. والسقوط في أي طبقة **ليس عطبًا**،
     * بل الطريق الاحتياطي المصرَّح (ونفس مبدأ `PredictorBridge` حين تغيب المكتبة).
     */
    fun readMany(paths: List<String>): List<String?> {
        if (paths.isEmpty()) return emptyList()
        return mergeNativeAndPrivileged(paths, ProbeBridge.readMany(paths), ::readManyPrivileged)
    }

    /**
     * الدمج نفسه — دالة نقية لتُقاس بذاتها (والمحاذاة هي ما يجب أن يُحرَس):
     *
     * * أي قائمة أصلية بطول مخالف للمدخل تُرفض كاملةً وتُسلَّم المهمة للطريق المصرَّح —
     *   فمحاذاة مخمَّنة تَنسب قيمة عقدة إلى عقدة أخرى، وذلك أسوأ من عدم التسريع.
     * * وما لم يُقرأ وحده يُسأل عنه: **لا يُطلب بالجذر ما قُرئ أصلًا**، ولا يُسقط ما فشل.
     */
    internal fun mergeNativeAndPrivileged(
        paths: List<String>,
        native: List<String?>?,
        privileged: (List<String>) -> List<String?>,
    ): List<String?> {
        if (paths.isEmpty()) return emptyList()
        if (native == null || native.size != paths.size) return privileged(paths)
        val missing = paths.filterIndexed { index, _ -> native[index] == null }
        if (missing.isEmpty()) return native
        val fallback = privileged(missing)
        var cursor = 0
        return paths.mapIndexed { index, _ -> native[index] ?: fallback.getOrNull(cursor++) }
    }

    /**
     * الطريق المصرَّح: معاملة IPC واحدة، ثم [read] لكل عقدة لم تُقرأ (صدفة الجذر آخرًا).
     * وهو الطريق الوحيد الذي كان قائمًا قبل تكملة ١١٢ — محفوظ كما هو حرفيًّا.
     */
    private fun readManyPrivileged(paths: List<String>): List<String?> {
        if (paths.isEmpty()) return emptyList()
        val batch = runCatching { RootIpcManager.ipc?.readNodes(paths.toMutableList()) }.getOrNull()
        if (batch != null && batch.size == paths.size) {
            return batch.map { value -> value?.trim()?.takeIf { it.isNotEmpty() } }
        }
        return paths.map { read(it) }
    }

    /**
     * يكتب ويعيد **نجاح أمر الكتابة** — نفس معنى العائد قبل `PEER-8` تمامًا
     * (`!= WRITE_FAILED` مكافئة لـ`exec().isSuccess` القديمة).
     */
    fun write(path: String, value: String): Boolean =
        writeOutcome(path, value) != WriteVerification.Outcome.WRITE_FAILED

    /**
     * كتابة **متحقَّقة**: تُعيد حكمًا على **القيمة** لا على الأمر. الحكم يُحسب **مرّة واحدة**
     * هنا ويُعاد استخدامه، فلا تُقرأ العقدة مرّتين لمتصل يريد الحكم.
     */
    fun writeVerified(path: String, value: String): WriteVerification.Outcome =
        writeOutcome(path, value)

    /**
     * النواة الواحدة: أمر الكتابة، ثم **قراءة للتحقّق**، ثم تسجيل الحكم. لا إصلاح ولا إعادة
     * محاولة — القرار يبقى للمتصل.
     */
    private fun writeOutcome(path: String, value: String): WriteVerification.Outcome {
        val wrote = runCatching {
            // IPC first: MtkRootService.writeNode now performs the HyperOS chmod
            // dance internally, so a false return is a real failure and the shell
            // path below re-runs the same dance. Plain root writes to 0444 sysfs
            // nodes are denied with EACCES on HyperOS 3 — real-device log showed
            // every registry PERAPP_COMMIT ending in live-value-mismatch without it.
            // A binder exception must not block the shell fallback either.
            val viaIpc = try { RootIpcManager.ipc?.writeNode(path, value) } catch (_: Exception) { false }
            if (viaIpc == true) return@runCatching true
            val p = quote(path)
            Shell.cmd(
                "m=\$(stat -c %a $p 2>/dev/null)",
                "chmod 644 $p 2>/dev/null",
                "printf '%s\\n' ${quote(value)} > $p",
                "chmod \$m $p 2>/dev/null"
            ).exec().isSuccess
        }.getOrDefault(false)

        // `PEER-8`/`AR-31`: كل كتابة تُقرأ بعدها وتُسجَّل نتيجتها. الكلفة: قراءة واحدة إضافية —
        // وليست في مسار إطار-بإطار بل في تغيير إعداد/تطبيق ملف.
        val readBack = if (wrote) read(path) else null
        val outcome = if (!wrote) {
            WriteVerification.Outcome.WRITE_FAILED
        } else {
            WriteVerification.compare(value, readBack)
        }
        try {
            EventLog.writeCheck(
                path = path,
                wrote = value,
                readBack = readBack,
                verdict = WriteVerification.verdictWord(outcome),
            )
        } catch (_: Exception) {
            // التسجيل لا يُسقط كتابةً نجحت.
        }
        return outcome
    }

    /** Read-only listing of both files and directories for discovery transports. */
    fun listNames(path: String): List<String> = runCatching {
        // القارئ الأصلي أولًا (بلا صدفة `ls`) — وغيابه يُعيدنا إلى الصدفة كما كانت.
        ProbeBridge.listNames(path)?.let { return it }
        val result = Shell.cmd("ls -1A ${quote(path)} 2>/dev/null").exec()
        if (!result.isSuccess) emptyList()
        else result.out.map(String::trim).filter { it.isNotEmpty() && it != "." && it != ".." }.distinct()
    }.getOrDefault(emptyList())

    fun listDirectories(path: String): List<String> {
        // الترتيب: قراءة داخل العملية ← IPC الجذر ← صدفة `ls` ← `File`. وكل طبقة
        // تسبق التي تليها بلا مساس بالقدرة: ما لا يقرأه uid التطبيق يسأله الجذر.
        ProbeBridge.listNames(path, dirsOnly = true)?.let { return it }
        RootIpcManager.ipc?.let { service ->
            runCatching { service.listDirectories(path).filter(String::isNotBlank) }
                .getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it.distinct() }
        }
        runCatching {
            val result = Shell.cmd("ls -1 ${quote(path)} 2>/dev/null").exec()
            if (result.isSuccess) {
                val names = result.out.map(String::trim).filter(String::isNotEmpty)
                if (names.isNotEmpty()) return names.distinct()
            }
        }
        return runCatching {
            File(path).listFiles()?.filter(File::isDirectory)?.map(File::getName).orEmpty().distinct()
        }.getOrDefault(emptyList())
    }

    fun remove(path: String): Boolean = runCatching {
        Shell.cmd("rm -f ${quote(path)}").exec().isSuccess
    }.getOrDefault(false)

    /** Expand a shell glob into directory paths when direct directory listing is hidden. */
    fun globDirectories(pattern: String): List<String> = runCatching {
        val result = Shell.cmd(
            "for d in $pattern; do [ -d \"\$d\" ] && printf '%s\\n' \"\$d\"; done"
        ).exec()
        if (!result.isSuccess) emptyList() else result.out.map(String::trim).filter(String::isNotBlank).distinct()
    }.getOrDefault(emptyList())

    fun atomicWriteText(path: String, content: String): Boolean = runCatching {
        val tmp = "$path.tmp"
        val encoded = android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        val parent = File(path).parent
        if (!parent.isNullOrBlank()) Shell.cmd("mkdir -p ${quote(parent)}").exec()
        val result = Shell.cmd(
            "printf '%s' ${quote(encoded)} | base64 -d > ${quote(tmp)}",
            "chmod 600 ${quote(tmp)} 2>/dev/null || true",
            "mv -f ${quote(tmp)} ${quote(path)}"
        ).exec()
        result.isSuccess
    }.getOrDefault(false)

    private fun shellTest(path: String): Boolean = Shell.cmd("test -e ${quote(path)}").exec().isSuccess

    private fun shellRead(path: String): String? {
        val result = Shell.cmd("cat ${quote(path)} 2>/dev/null").exec()
        return if (result.isSuccess) result.out.joinToString("\n").trim().takeIf { it.isNotEmpty() } else null
    }

    /** Execute a root shell command. Callers must construct only allow-listed commands. */
    fun exec(command: String): Int = runCatching {
        if (Shell.cmd(command).exec().isSuccess) 0 else -1
    }.getOrDefault(-1)
}
