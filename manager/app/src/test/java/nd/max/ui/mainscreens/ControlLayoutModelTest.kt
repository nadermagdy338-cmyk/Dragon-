/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.ui.mainscreens

import nd.max.ui.navigation.MaxDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد ربط الوضعين: الصفحة المختصرة والصفحة الموسّعة تُبنيان من نموذج واحد
 * ([controlLayoutModel]) مشتقّ من السجل [MaxDestination]، فلا شيء يُكتب مرتين.
 *
 * هذه الاختبارات هي ما يمنع الانحدار: أي وجهة جديدة تُضاف تحت Control ويُنسى
 * إظهارها في النموذج تُسقط الاختبار، وأي ازدواج في الصفوف كذلك.
 */
class ControlLayoutModelTest {

    private val bands = controlLayoutModel()

    private val hubs = bands.flatMap { band -> band.hubs }

    @Test
    fun `every destination parented by Control is reachable from the page`() {
        val fromRegistry = MaxDestination.All.filter { it.parent == MaxDestination.Control }.toSet()
        val hubsInModel = hubs.map { it.hub }.toSet()
        val toolsInModel = controlToolEntries().map { it.destination }.toSet()

        assertEquals(fromRegistry, hubsInModel + toolsInModel)
        assertEquals(hubsInModel.size + toolsInModel.size, (hubsInModel + toolsInModel).size)
    }

    @Test
    fun `each hub lists every screen the registry gives it`() {
        hubs.forEach { spec ->
            val owned = MaxDestination.All.filter { it.parent == spec.hub }.map { it.route }
            val listed = spec.features.map { it.destination.route }
            assertTrue(
                "${spec.hub.route} is missing ${owned - listed.toSet()}",
                listed.containsAll(owned),
            )
        }
    }

    @Test
    fun `no screen is listed twice within the same hub`() {
        hubs.forEach { spec ->
            val listed = spec.features.map { it.destination.route }
            assertEquals(
                "${spec.hub.route} lists a screen more than once: $listed",
                listed.distinct(),
                listed,
            )
        }
    }

    @Test
    fun `bands and hubs are listed exactly once`() {
        assertEquals(bands.map { it.key }.distinct().size, bands.size)
        assertEquals(hubs.map { it.hub }.distinct().size, hubs.size)
        assertTrue("empty bands must be dropped", bands.all { it.hubs.isNotEmpty() })
    }

    @Test
    fun `memory hub also surfaces the shared preference editor`() {
        val memory = hubs.first { it.hub == MaxDestination.MemoryHub }
        assertTrue(memory.features.map { it.destination }.contains(MaxDestination.PreferenceTweaks))
    }

    @Test
    fun `every row carries a sub-title resource`() {
        hubs.forEach { spec ->
            assertTrue(spec.hubSubtitleRes != 0)
            spec.features.forEach { feature -> assertTrue(feature.subtitleRes != 0) }
        }
        controlToolEntries().forEach { tool -> assertTrue(tool.subtitleRes != 0) }
    }
}
