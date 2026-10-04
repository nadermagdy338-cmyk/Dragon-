/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.gamespace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class GameLobbyMetaTest {
    private val day = 86_400_000L

    @Test fun ageIsWholeDays() {
        assertEquals(0, installAgeDays(firstInstallMillis = 1_000L, nowMillis = 1_000L + day - 1))
        assertEquals(3, installAgeDays(firstInstallMillis = 1_000L, nowMillis = 1_000L + 3 * day + 5))
    }

    @Test fun unreadableOrFutureInstallDateNeverGoesNegative() {
        assertNull(installAgeDays(0L, 10 * day))
        assertNull(installAgeDays(-5L, 10 * day))
        assertEquals(0, installAgeDays(firstInstallMillis = 10 * day, nowMillis = 2 * day))
    }

    @Test fun apkSizeSumsOnlyExistingFiles() {
        val a = File.createTempFile("lobby", ".apk").apply { writeBytes(ByteArray(10)); deleteOnExit() }
        val b = File.createTempFile("lobby", ".apk").apply { writeBytes(ByteArray(32)); deleteOnExit() }
        assertEquals(42L, apkTotalBytes(listOf(a.path, null, "", "/no/such/file.apk", b.path)))
    }

    @Test fun apkSizeIsNullNotZeroWhenNothingReadable() {
        assertNull(apkTotalBytes(emptyList()))
        assertNull(apkTotalBytes(listOf(null, "", "/no/such/file.apk")))
    }
}
