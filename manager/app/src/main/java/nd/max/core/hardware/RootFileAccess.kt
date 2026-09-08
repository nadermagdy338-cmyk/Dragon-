/*
 * Generic privileged filesystem access used by hardware discovery and control.
 * It deliberately knows nothing about a specific vendor or SoC.
 */
package nd.max.core.hardware

import com.topjohnwu.superuser.Shell
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

    fun write(path: String, value: String): Boolean = runCatching {
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
