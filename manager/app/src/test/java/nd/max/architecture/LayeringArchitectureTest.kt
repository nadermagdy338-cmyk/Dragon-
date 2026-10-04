/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.architecture

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * App-level structural guards for the layer boundaries and the product identity.
 *
 * Each invariant below was measured to hold in the tree at the time it was written. A rule that is
 * only written in a document decays quietly; these tests make the decay fail instead. The guard is
 * deliberately narrow — it asserts only what is already true, so a failure means a real regression,
 * not a stylistic disagreement.
 *
 * Two of the invariants are cross-layer: the broadcast receiver name and the daemon header must be
 * the same across Kotlin, the Android manifest and the C daemon. That rename was done by hand once
 * (S0); this makes the next one impossible to leave half-done.
 */
class LayeringArchitectureTest {

    private lateinit var sourceRoot: File
    private var repoRoot: File? = null

    @Before
    fun locateSourceRoot() {
        val candidates = listOf(
            File("src/main/java/nd/max"),
            File("app/src/main/java/nd/max"),
            File("../app/src/main/java/nd/max"),
        )
        val found = candidates.firstOrNull { it.isDirectory }
        assumeTrue(
            "Cannot locate nd.max sources from ${File("").absolutePath}; guard not evaluated",
            found != null,
        )
        sourceRoot = found!!.canonicalFile
        // The repository root is the nearest ancestor that still carries the native tree. When the
        // test runs from a layout that has no native tree, the cross-layer guards skip loudly rather
        // than pass over files they never opened.
        repoRoot = generateSequence(sourceRoot) { it.parentFile }
            .firstOrNull { File(it, "archdaemon").isDirectory }
    }

    private fun sources(): List<File> =
        sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun relative(file: File): String =
        file.relativeTo(sourceRoot).path.replace('\\', '/')

    /** Code without block and whole-line comments, so a documented rule is not read as a violation. */
    private fun code(file: File): String =
        file.readText()
            .replace(Regex("(?s)/\\*.*?\\*/"), " ")
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")

    @Test
    fun navigationRoutesAreNotStringLiteralsOutsideTheSpine() {
        // ADR-02: route ids live in ui/navigation/MaxDestinations.kt. A literal elsewhere produces a
        // dead alias or a silent no-op; the spine is the only place allowed to spell a route.
        val offenders = sources()
            .filterNot { relative(it).startsWith("ui/navigation/") }
            .filter { code(it).contains("navigate(\"") }
            .map { relative(it) }
        assertTrue(
            "route literals must live in ui/navigation, found in $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun theUiLayerNeverExecutesProcessesDirectly() {
        // ADR-11: privileged I/O is owned by the control plane. A composable or a screen ViewModel
        // that shells out has left the arbiter, the ownership model and the rollback path behind.
        val offenders = sources()
            .filter { relative(it).startsWith("ui/") }
            .filter {
                val body = code(it)
                body.contains("ProcessBuilder") || body.contains("Runtime.getRuntime")
            }
            .map { relative(it) }
        assertTrue(
            "the UI layer must not execute processes directly, found in $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun inheritedIdentityIdentifiersStayRetired() {
        // S0: these names came from the upstream this project grew out of. They were renamed across
        // Kotlin, the manifest, the C daemon and the build; nothing may reintroduce them.
        val retired = listOf("AZenith.h", "ZenithReceiver", "azenith.jks", "az_profile", "az_system", "zx.azenith")
        val hits = sources()
            .mapNotNull { file ->
                val body = file.readText()
                retired.filter { body.contains(it) }.takeIf { it.isNotEmpty() }?.let { "${relative(file)}: $it" }
            }
        assertTrue("retired inherited identifiers must not return: $hits", hits.isEmpty())
    }

    @Test
    fun theReceiverNameIsTheSameAcrossKotlinTheManifestAndTheDaemon() {
        val manifest = repoRoot?.let { File(it, "manager/app/src/main/AndroidManifest.xml") }
        assumeTrue("no Android manifest reachable; cross-layer guard not evaluated", manifest?.isFile == true)

        val registered = Regex("""android:name="\.receiver\.([A-Za-z0-9_]+)"""").find(manifest!!.readText())
        assumeTrue("no receiver is registered under .receiver.", registered != null)
        val simpleName = registered!!.groupValues[1]

        val kotlinFile = File(sourceRoot, "receiver/$simpleName.kt")
        assertTrue(
            "the manifest registers .receiver.$simpleName but receiver/$simpleName.kt does not exist",
            kotlinFile.isFile,
        )

        val nativeDir = File(repoRoot!!, "archdaemon/jni")
        val nativeRoot = nativeDir.walkTopDown()
            .filter { it.isFile && (it.extension == "c" || it.extension == "h") }
            .firstOrNull { it.readText().contains("receiver.$simpleName") }
        assertTrue(
            "the daemon must address the same receiver the manifest registers ($simpleName), but no " +
                "native source references it",
            nativeRoot != null,
        )
    }

    @Test
    fun theDaemonHeaderStillResolvesForItsIncluders() {
        val header = repoRoot?.let { File(it, "archdaemon/jni/include/MaxManager.h") }
        assumeTrue("no native header reachable; guard not evaluated", header?.isFile == true)

        val main = File(repoRoot!!, "archdaemon/jni/Main.c")
        assumeTrue("no Main.c reachable", main.isFile)
        assertTrue(
            "Main.c must include the daemon header by its current name",
            main.readText().contains("MaxManager.h"),
        )
    }
}
