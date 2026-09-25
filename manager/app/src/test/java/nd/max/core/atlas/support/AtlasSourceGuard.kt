/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.atlas.support

import java.io.File

/**
 * Source guard (`P0`).
 *
 * Some Atlas invariants are structural rather than behavioral: the evidence vocabulary must contain
 * no field that could authorize a control action, and the read path must not name a privileged
 * transport. A test is the right place for that check only if it cannot silently pass, so this guard
 * **fails closed**: if a file cannot be located or read, it throws instead of skipping the check.
 */
object AtlasSourceGuard {

    /**
     * Tokens that must not appear in the Atlas evidence/catalog sources. Each one would represent an
     * authority or transport decision that belongs to the existing control plane, not to Atlas.
     */
    val FORBIDDEN_TOKENS: List<String> = listOf(
        "writable",
        "canWrite",
        "controlEligible",
        "submitControl",
        "chmod",
        "RootFileAccess",
        "Shell",
        "Shizuku",
    )

    /** Locates a project file by walking up from the test working directory. */
    fun projectFile(relativePath: String): File {
        var directory: File? = File("").absoluteFile
        repeat(MAX_LEVELS) {
            val current = directory
            if (current != null) {
                val direct = File(current, relativePath)
                if (direct.isFile) return direct
                val fromRoot = File(current, "manager/app/$relativePath")
                if (fromRoot.isFile) return fromRoot
                directory = current.parentFile
            }
        }
        throw IllegalStateException("source guard cannot locate $relativePath (failing closed)")
    }

    fun read(relativePath: String): String {
        val file = projectFile(relativePath)
        if (!file.canRead()) {
            throw IllegalStateException("source guard cannot read ${file.absolutePath} (failing closed)")
        }
        return file.readText()
    }

    /**
     * The file with its comments removed.
     *
     * A token guard that also matches prose would punish a file for *explaining* what it refuses to
     * do ("no SSID is stored" is the sentence we want), and a guard people work around is worse than
     * none. Comments are therefore stripped before the scan; code and string literals keep their
     * tokens, so a real field or call still trips the check.
     */
    fun code(relativePath: String): String = stripComments(read(relativePath))

    /** Best-effort Kotlin comment removal that respects string and character literals. */
    fun stripComments(source: String): String {
        val out = StringBuilder(source.length)
        var index = 0
        while (index < source.length) {
            val character = source[index]
            when {
                character == '"' -> {
                    val end = endOfLiteral(source, index, '"')
                    out.append(source, index, end)
                    index = end
                }

                character == '`' -> {
                    // A backticked Kotlin name may contain an apostrophe (`fun `it's fine`()`), and
                    // without this the apostrophe would open a phantom character literal and swallow
                    // real code until the next quote — a guard that silently reads less than it
                    // claims is worse than no guard.
                    val end = source.indexOf('`', index + 1)
                    if (end < 0) {
                        out.append(source, index, source.length)
                        index = source.length
                    } else {
                        out.append(source, index, end + 1)
                        index = end + 1
                    }
                }

                character == '\'' -> {
                    // A character literal never spans a newline, so a lone quote is just a quote.
                    val end = endOfLiteral(source, index, '\'')
                    out.append(source, index, end)
                    index = end
                }

                character == '/' && source.getOrNull(index + 1) == '/' -> {
                    while (index < source.length && source[index] != '\n') index += 1
                }

                character == '/' && source.getOrNull(index + 1) == '*' -> {
                    index += 2
                    while (index < source.length && !(source[index] == '*' && source.getOrNull(index + 1) == '/')) index += 1
                    index += 2
                }

                else -> {
                    out.append(character)
                    index += 1
                }
            }
        }
        return out.toString()
    }

    private fun endOfLiteral(source: String, start: Int, delimiter: Char): Int {
        var index = start + 1
        while (index < source.length) {
            val character = source[index]
            if (character == '\\') {
                index += 2
                continue
            }
            if (character == delimiter) return index + 1
            if (character == '\n' && delimiter == '\'') return index
            index += 1
        }
        return source.length
    }

    /** Whole-word hits, so `Shell` does not match an unrelated longer identifier. */
    fun forbiddenHits(text: String, tokens: List<String> = FORBIDDEN_TOKENS): List<String> =
        tokens.filter { token -> Regex("\\b" + Regex.escape(token) + "\\b").containsMatchIn(text) }

    private const val MAX_LEVELS = 8
}
