package nd.max.core.atlas

import nd.max.core.hardware.AtlasReadTransport
import nd.max.core.hardware.AtlasTransportList
import nd.max.core.hardware.AtlasTransportRead
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemLoopException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The real read path, without root (`P2`/`T2.5`, rootless half; `P8`).
 *
 * This is the transport the app actually uses. It opens files as the ordinary app user does, over
 * `java.nio.file`, and it has no privileged, shell, module or exit-door path: nothing here escalates,
 * nothing here writes, and the type it implements ([AtlasReadTransport]) has no mutating member at
 * all. The privileged half of `T2.5` remains deliberately unbuilt — when a reviewed privileged
 * transport exists, it plugs in beside this one, and until then a `requiresPrivilege` attempt is
 * refused by the boundary as `BACKEND_UNAVAILABLE` rather than guessed at.
 *
 * **Why it takes a root.** On a device the root is `/` and the paths are the kernel's own. In a test
 * the same class is pointed at a directory, so the *shipped* code is what runs: real syscalls, real
 * `ENOENT`, real `EACCES`, real symlinks. A second implementation for tests would mean testing a
 * different program and calling it evidence.
 *
 * **Failure mapping is the point.** Every cause the vocabulary distinguishes comes from a real errno
 * here, which is what stops "I could not look" from being reported as "it is not there":
 *
 * | errno | cause |
 * | --- | --- |
 * | `ENOENT` | `ABSENT` |
 * | `EACCES` | `PERMISSION_DENIED` |
 * | `ELOOP` | `UNKNOWN_CAUSE` with the loop named |
 * | `ENOTDIR` | `ABSENT` (a path component is not a directory, so the child cannot exist) |
 * | anything else | `UNKNOWN_CAUSE`, never `ABSENT` |
 *
 * `AtlasFailure` itself lives in this package: the evidence vocabulary owns the causes, and the
 * transport only reports which one the kernel produced.
 *
 * Read-only products of this class can never be partial silently: a value larger than the allowance
 * comes back with `truncated = true` and the boundary refuses to parse it.
 */
class AtlasFileReadTransport(private val root: Path = Paths.get(ROOT_DEVICE)) : AtlasReadTransport {

    init {
        require(root.isAbsolute) { "the transport root must be absolute" }
    }

    override fun readText(path: String, maxBytes: Int): AtlasTransportRead {
        require(maxBytes > 0) { "a read allowance must be positive" }
        val target = resolve(path)
        // A directory answered with EISDIR on some kernels and with a Java-level IOException on
        // others; neither is a value, and both are the same fact.
        if (Files.isDirectory(target)) {
            return AtlasTransportRead.Failed(
                AtlasFailure.UNKNOWN_CAUSE,
                "EISDIR: the path is a directory, not a readable interface",
            )
        }
        return try {
            Files.newInputStream(target).use { input ->
                // One byte past the allowance is what turns truncation into a fact instead of a hope.
                val buffer = ByteArray(maxBytes + 1)
                var filled = 0
                while (filled < buffer.size) {
                    val read = input.read(buffer, filled, buffer.size - filled)
                    if (read < 0) break
                    filled += read
                }
                if (filled > maxBytes) {
                    AtlasTransportRead.Text(String(buffer, 0, maxBytes, StandardCharsets.UTF_8), truncated = true)
                } else {
                    AtlasTransportRead.Text(String(buffer, 0, filled, StandardCharsets.UTF_8), truncated = false)
                }
            }
        } catch (missing: NoSuchFileException) {
            AtlasTransportRead.Failed(AtlasFailure.ABSENT, "ENOENT: the interface does not exist")
        } catch (denied: AccessDeniedException) {
            AtlasTransportRead.Failed(AtlasFailure.PERMISSION_DENIED, "EACCES: refused by the kernel or by policy")
        } catch (loop: FileSystemLoopException) {
            AtlasTransportRead.Failed(AtlasFailure.UNKNOWN_CAUSE, "ELOOP: symbolic link loop")
        } catch (notDirectory: NotDirectoryException) {
            AtlasTransportRead.Failed(AtlasFailure.ABSENT, "ENOTDIR: a path component is not a directory")
        } catch (error: IOException) {
            AtlasTransportRead.Failed(
                errnoCause(error),
                "IO error ${error.javaClass.simpleName}: ${error.message?.take(MAX_REASON_CHARS) ?: "no message"}",
            )
        }
    }

    /**
     * The last-resort errno read.
     *
     * Java throws `FileSystemException` for several distinct errnos with the kernel's text in the
     * message and no field to read, so the text is the only signal available. It is used **only** in
     * the catch-all path, it never turns an unrecognized error into `ABSENT`, and the raw message is
     * preserved in the reason so a report shows what actually happened rather than the guess.
     */
    private fun errnoCause(error: IOException): AtlasFailure {
        val text = error.message?.lowercase().orEmpty()
        return when {
            "not a directory" in text || "enotdir" in text -> AtlasFailure.ABSENT
            "permission denied" in text || "eacces" in text -> AtlasFailure.PERMISSION_DENIED
            "no such file" in text || "enoent" in text -> AtlasFailure.ABSENT
            else -> AtlasFailure.UNKNOWN_CAUSE
        }
    }

    override fun listNames(path: String, limit: Int): AtlasTransportList {
        require(limit > 0) { "a listing allowance must be positive" }
        val target = resolve(path)
        return try {
            Files.newDirectoryStream(target).use { stream ->
                val names = ArrayList<String>(minOf(limit + 1, INITIAL_CAPACITY))
                var truncated = false
                val iterator = stream.iterator()
                while (iterator.hasNext()) {
                    if (names.size >= limit) {
                        // The caller must never mistake a cut list for a complete one; it is what turns
                        // "not visited" into "absent" downstream if it is lost.
                        truncated = true
                        break
                    }
                    names += iterator.next().fileName.toString()
                }
                AtlasTransportList(
                    names = names,
                    cause = AtlasFailure.NONE,
                    reason = if (truncated) "truncated at $limit entries" else "ok",
                    truncated = truncated,
                )
            }
        } catch (missing: NoSuchFileException) {
            AtlasTransportList(emptyList(), AtlasFailure.ABSENT, "ENOENT: the directory does not exist")
        } catch (denied: AccessDeniedException) {
            AtlasTransportList(emptyList(), AtlasFailure.PERMISSION_DENIED, "EACCES: refused by the kernel or by policy")
        } catch (notDirectory: NotDirectoryException) {
            AtlasTransportList(emptyList(), AtlasFailure.ABSENT, "ENOTDIR: not a directory")
        } catch (loop: FileSystemLoopException) {
            AtlasTransportList(emptyList(), AtlasFailure.UNKNOWN_CAUSE, "ELOOP: symbolic link loop")
        } catch (error: IOException) {
            AtlasTransportList(
                emptyList(),
                errnoCause(error),
                "IO error ${error.javaClass.simpleName}: ${error.message?.take(MAX_REASON_CHARS) ?: "no message"}",
            )
        }
    }

    /**
     * The canonical path, returned in the **same coordinate system it was asked in**.
     *
     * On a device the root is `/` and this is invisible. With a non-root root it is load-bearing: the
     * caller compares the answer with the path it asked about and then reads it, so handing back a
     * host path would make the anchor check compare two different things and make the read look for
     * the file under a directory that duplicates itself — which is a real `ENOENT`, and `ENOENT` is
     * reported as absence. That exact defect was found by running this class against a real directory.
     */
    override fun canonicalPath(path: String, maxHops: Int): String? {
        if (maxHops <= 0) return null
        val resolved = resolveWithHops(resolve(path), maxHops) ?: return null
        return toDevicePath(resolved)
    }

    /**
     * Resolution with a **counted** hop budget.
     *
     * `Path.toRealPath()` would follow links too, but it cannot say how many it followed, and a bound
     * nobody can count is not a bound. Resolving component by component keeps the count real: the
     * loop is broken by the budget itself, so a symlink cycle returns `null` instead of hanging.
     */
    private fun resolveWithHops(start: Path, maxHops: Int): Path? {
        val absolute = start.toAbsolutePath().normalize()
        var prefix: Path = absolute.root ?: return null
        var hops = 0
        for (part in absolute) {
            var candidate = prefix.resolve(part)
            while (Files.isSymbolicLink(candidate)) {
                hops += 1
                if (hops > maxHops) return null
                val linkTarget = try {
                    Files.readSymbolicLink(candidate)
                } catch (denied: IOException) {
                    return null
                }
                candidate = if (linkTarget.isAbsolute) {
                    linkTarget.normalize()
                } else {
                    (candidate.parent ?: prefix).resolve(linkTarget).normalize()
                }
            }
            prefix = candidate
        }
        return prefix
    }

    /**
     * Maps an absolute device path onto the transport root, so tests run the real code.
     *
     * Idempotent on purpose: a path that is already under the root is returned as it is, because
     * [canonicalPath] answers in device coordinates and a caller may hand that answer straight back.
     * Mapping it twice would produce a silent `ENOENT` — the truth reported as absence.
     */
    private fun resolve(path: String): Path {
        require(path.startsWith("/")) { "a transport path is absolute: $path" }
        val rooted = root.toString()
        if (rooted == ROOT_DEVICE) return Paths.get(path)
        if (path == rooted || path.startsWith("$rooted/")) return Paths.get(path)
        return root.resolve(path.removePrefix("/"))
    }

    /** The inverse of [resolve]: a host path expressed as the device path that addressed it. */
    private fun toDevicePath(path: Path): String {
        val rooted = root.toString()
        val text = path.toString()
        if (rooted == ROOT_DEVICE) return text
        return when {
            text == rooted -> "/"
            text.startsWith("$rooted/") -> "/" + text.removePrefix("$rooted/")
            else -> text
        }
    }

    companion object {
        const val ROOT_DEVICE: String = "/"

        private const val INITIAL_CAPACITY = 64
        private const val MAX_REASON_CHARS = 120
    }
}
