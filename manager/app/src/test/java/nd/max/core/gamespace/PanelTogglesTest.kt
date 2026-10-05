/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 *
 * MaxManager proprietary source. See LICENSE at the repository root: this file is
 * MaxManager-owned and carries no third-party licence obligations.
 */

package nd.max.core.gamespace

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelTogglesTest {
    @Test
    fun reminderCyclesThroughStepsThenStops() {
        var current = 0
        val seen = mutableListOf<Int>()
        repeat(REMINDER_STEPS.size + 1) {
            current = nextReminderMinutes(current)
            seen += current
        }
        assertEquals(REMINDER_STEPS + 0, seen)
    }

    @Test
    fun unknownReminderValueRestartsFromTheFirstStep() {
        assertEquals(REMINDER_STEPS.first(), nextReminderMinutes(45))
    }
}
