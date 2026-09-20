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

package nd.max.ui.navigation

import nd.max.core.atlas.support.AtlasSourceGuard
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `P8`: the Atlas reading pass must be reachable **without root and without the native module**.
 *
 * Two different defects live behind that sentence, and both have happened in this repository:
 *
 * 1. **A built screen with no entrance** — `PluginsScreen` was built, registered in the nav graph and
 *    documented, and no screen linked to it (`SettingsDestinationReachabilityTest` was written for
 *    exactly that).
 * 2. **An entrance that needs privilege** — a screen behind root, or behind a module-loaded flag, is
 *    unreachable precisely for the user whose device is not answering. That is the case Atlas exists
 *    for, so it is the one case it must not require.
 *
 * What this test proves is a source-and-graph claim. What it cannot prove is that a finger press on a
 * real device opens the right screen: there is no device and no emulator in this environment, and that
 * half stays `needs device` in the handoff rather than being implied by a green run.
 */
class AtlasReadOnlyEntryTest {

    @Test
    fun `the diagnostics destination is still a registered destination under settings`() {
        val destinations = AtlasSourceGuard.read("src/main/java/nd/max/ui/navigation/MaxDestinations.kt")
        val graph = AtlasSourceGuard.read("src/main/java/nd/max/ui/navigation/MaxNavGraph.kt")

        assertTrue("Diagnostics stays under Settings, not in a separate hub", destinations.contains("Settings"))
        assertTrue(
            "the Atlas surface lives on an existing destination; P7 adds no route",
            graph.contains("composable(MaxDestination.Diagnostics.route)"),
        )
    }

    @Test
    fun `settings links to diagnostics from a screen, not only from its own definition`() {
        val settings = AtlasSourceGuard.read("src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt")

        assertTrue(
            "Settings must navigate to Diagnostics; a destination nothing links to is a screen nobody opens",
            settings.contains("MaxDestination.Diagnostics"),
        )
    }

    @Test
    fun `the atlas surface is on the diagnostics screen`() {
        val diagnostics = AtlasSourceGuard.read("src/main/java/nd/max/ui/mainscreens/DiagnosticsScreen.kt")

        assertTrue(
            "the diagnostics screen must render the Atlas section",
            diagnostics.contains("AtlasDiagnosticsSection()"),
        )
    }

    @Test
    fun `nothing on the atlas read path can prompt for privilege`() {
        // The reading transport is the rootless one, and it is the only transport wired in.
        val module = AtlasSourceGuard.read("src/main/java/nd/max/core/di/DataModule.kt")
        assertTrue(
            "the Atlas transport is the rootless file transport",
            module.contains("AtlasFileReadTransport()"),
        )

        // And a request that needs privilege is refused by default rather than escalated.
        val discovery = AtlasSourceGuard.code("src/main/java/nd/max/core/atlas/AtlasDiscovery.kt")
        assertTrue(
            "privilege is off unless a caller turns it on explicitly",
            discovery.contains("var privilegeAvailable: () -> Boolean = { false }"),
        )
    }

    @Test
    fun `the atlas user interface names no transport and no authority`() {
        listOf(
            "src/main/java/nd/max/ui/mainscreens/AtlasDiagnosticsSection.kt",
            "src/main/java/nd/max/ui/viewmodel/AtlasViewModel.kt",
        ).forEach { relative ->
            val code = AtlasSourceGuard.code(relative)
            val hits = AtlasSourceGuard.forbiddenHits(code)
            assertTrue("$relative must not name: $hits", hits.isEmpty())
            assertTrue(
                "$relative must not shell out",
                !code.contains("Runtime.getRuntime") && !code.contains("ProcessBuilder"),
            )
        }
    }
}
