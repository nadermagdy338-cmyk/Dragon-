/*
 * Generic privileged filesystem access used by hardware discovery and control.
 * It deliberately knows nothing about a specific vendor or SoC.
 */
package nd.max.core.hardware

import com.topjohnwu.superuser.Shell
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
        val result = Shell.cmd("ls -1A ${quote(path)} 2>/dev/null").exec()
        if (!result.isSuccess) emptyList()
        else result.out.map(String::trim).filter { it.isNotEmpty() && it != "." && it != ".." }.distinct()
    }.getOrDefault(emptyList())

    fun listDirectories(path: String): List<String> {
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
