/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.hardware

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Architectural guards for the single control plane.
 *
 * Every invariant below was already violated once in this codebase (direct
 * writes around the gate, a second gate instance, a hostile `am kill-all`
 * action, duplicated key literals). A rule that is only written down decays;
 * these tests make the decay fail the build instead of shipping quietly.
 */
class ControlPlaneArchitectureTest {
    private lateinit var sourceRoot: File

    @Before
    fun locateSourceRoot() {
        val candidates = listOf(
            File("src/main/java/nd/max"),
            File("app/src/main/java/nd/max"),
            File("../app/src/main/java/nd/max"),
        )
        val found = candidates.firstOrNull { it.isDirectory }
        // Rather than pretend to verify, skip loudly when the convention differs
        // (e.g. a Gradle layout that changes the unit-test working directory).
        assumeTrue(
            "Cannot locate nd.max sources from ${File("").absolutePath}; guard not evaluated",
            found != null,
        )
        sourceRoot = found!!
    }

    private fun sources(): List<File> =
        sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun contents(file: File): String = file.readText()

    private fun offenders(needle: String, skip: Set<String> = emptySet()): List<String> =
        sources()
            .filterNot { it.name in skip }
            .filter { contents(it).contains(needle) }
            .map { it.name }

    @Test
    fun policyLayerNeverWritesHardwareDirectly() {
        val policyDir = File(sourceRoot, "core/maxai")
        assumeTrue("no policy layer directory", policyDir.isDirectory)
        val offenders = policyDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { contents(it).contains("RootFileAccess.write") }
            .map { it.name }
            .toList()
        assertTrue(
            "core/maxai must reach hardware only through the arbiter/backends, found direct writes in $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun hostileProcessKillsAreGoneForGood() {
        // The old mind could run `am kill-all`, an irreversible action against the
        // user's own apps with no rollback possible.
        assertTrue(
            "am kill-all is retired and must not return",
            offenders("kill-all").isEmpty(),
        )
    }

    @Test
    fun oneArbiterInstancePerProcess() {
        // In the app process the arbiter is a Hilt @Singleton. This process (the
        // root AppMonitor bootstrap) is not a Hilt component, so it owns exactly
        // one explicitly. Any other construction would split the request table.
        val constructions = offenders("HardwareControlArbiter()")
        assertTrue(
            "HardwareControlArbiter() may only be constructed in the AppMonitor bootstrap, found $constructions",
            constructions.all { it == "AppMonitor.kt" },
        )
    }

    @Test
    fun arbiterRemainsSingletonScoped() {
        val arbiter = File(sourceRoot, "core/hardware/HardwareControlArbiter.kt")
        assumeTrue("arbiter source missing", arbiter.isFile)
        val text = contents(arbiter)
        assertTrue(
            "HardwareControlArbiter must stay @Singleton, otherwise each injection site gets its own request table",
            text.contains("@Singleton"),
        )
    }

    @Test
    fun canonicalKeysAreNotReinvented() {
        // Two spellings of the same knob silently break priority arbitration:
        // the ledger would treat them as unrelated keys.
        val offenders = listOf("\"cpu_limits:", "\"gpu_frequency:")
            .flatMap { offenders(it, skip = setOf("HardwareControlKey.kt")) }
            .distinct()
        assertTrue(
            "hardware keys must come from HardwareControlKey, found literals in $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun presetsStayOutOfTheSafetyAndDecisionPath() {
        // Decision #15/#19: safety acts surgically and the mind proposes knob
        // deltas. Routing either through the coarse preset applier would put
        // safety back on a channel that can fail wholesale.
        val decisionFiles = setOf(
            "MinimalPlanner.kt",
            "SafetyGovernor.kt",
            "SafetyEngine.kt",
            "CpuCeilingKnobs.kt",
            "ControlRegistry.kt",
            "CredibilityStore.kt",
        )
        val offenders = offenders("ProfileApplier").filter { it in decisionFiles }
        assertTrue(
            "the safety/decision path must not use ProfileApplier, found in $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun maxAiDecisionPathContainsNoStubbedSuccessOrCorruptedSyntax() {
        val engine = File(sourceRoot, "core/maxai/MaxAiEngine.kt")
        assumeTrue("engine source missing", engine.isFile)
        val text = contents(engine)
        assertTrue("Max AI must never report a hard-coded successful profile result", !text.contains("val ok = true"))
        assertTrue("Max AI source must not contain a dangling standalone null in publish", !text.contains("        null\n        val prev = _state.value"))
        assertTrue("Max AI imports must remain in the file header", text.substringAfterLast("package nd.max.core.maxai").substringBefore("/**").trim().lines().none { it.startsWith("import ") && text.substringAfter("/**").contains(it) })
    }

    @Test
    fun dynamicIntentLearnerDoesNotCreateASecondKnobCredibilityStore() {
        val learner = File(sourceRoot, "core/maxai/DynamicIntentLearner.kt")
        assumeTrue("dynamic learner source missing", learner.isFile)
        val text = contents(learner)
        assertTrue("knob credibility must have one canonical owner", !text.contains("knobSuccessCache"))
        assertTrue("legacy duplicate knob persistence must stay retired", !text.contains("knobPriorities"))
        assertTrue("learner must reuse the canonical credibility store", text.contains("credibility.credibility("))
    }

    @Test
    fun retiredOwnershipConceptsStayRetired() {
        val retired = listOf("PendingManualStore", "MaxAiController", "pendingChanges")
        val offenders = retired.flatMap { offenders(it) }.distinct()
        assertTrue(
            "retired ownership concepts must not reappear, found in $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun manualLocksAreEnforcedAtTheGateNotJustInThePlanner() {
        val arbiter = File(sourceRoot, "core/hardware/HardwareControlArbiter.kt")
        assumeTrue("arbiter source missing", arbiter.isFile)
        assertTrue(
            "the arbiter itself must refuse locked knobs (INV-3), a cooperative planner is not enough",
            contents(arbiter).contains("ManualControlLocks.blocks"),
        )
        val engine = File(sourceRoot, "core/maxai/MaxAiEngine.kt")
        assumeTrue("engine source missing", engine.isFile)
        assertTrue(
            "the mind must also skip locked knobs so it spends no decision on them",
            contents(engine).contains("ManualControlLocks"),
        )
    }
}
