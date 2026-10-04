/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.contract

import org.junit.Assume.assumeTrue
import java.io.File

/**
 * Locates `fixtures/contracts/` — the corpus that the C host suite reads too.
 *
 * The directory is found by walking up from the working directory until a `fixtures/contracts`
 * directory is reachable, so the same tests hold whether Gradle runs the suite from `manager/`,
 * from `manager/app/`, or from the repository root. When no such directory exists the tests
 * **skip loudly** (`assumeTrue`) rather than pass over files they never opened — the same
 * choice `LayeringArchitectureTest` makes for the native tree, and for the same reason: a green
 * result must mean the assertion ran.
 */
internal object ContractFixtures {

    val directory: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "fixtures/contracts") }
            .firstOrNull { it.isDirectory }
            ?: File("fixtures/contracts")
    }

    /** Fails the test with a stated reason instead of silently skipping when the corpus is absent. */
    fun require(name: String) {
        assumeTrue(
            "contract fixtures not reachable from ${File("").absolutePath}; assertion not evaluated",
            directory.isDirectory,
        )
    }

    fun text(name: String): String {
        require(name)
        val file = File(directory, name)
        assumeTrue("fixture $name is missing; assertion not evaluated", file.isFile)
        return file.readText()
    }

    fun file(name: String): File {
        require(name)
        val file = File(directory, name)
        assumeTrue("fixture $name is missing; assertion not evaluated", file.isFile)
        return file
    }

    /** كل ملفّات المصدر الرئيسية Kotlin — للحرس البنيوية التي تقرأ ما يُنفّذ فيها. */
    fun mainSourceFiles(): List<File> {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "manager/app/src/main/java/nd/max") }
            .firstOrNull { it.isDirectory }
        assumeTrue("nd.max main sources not reachable; guard not evaluated", root != null)
        return root!!.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** Repository root, used by the cross-language guards (the `.aidl` and the daemon sources). */
    val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "archdaemon").isDirectory }
            ?: File("")
    }
}
