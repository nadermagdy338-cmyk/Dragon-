/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.gamespace

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class GameProfileDocumentTest {
    @Test fun patchPreservesOtherAppsAndForeignFields() {
        val before = GameProfileDocument.parse("""{"com.game.one":{"gpu_profile":"power","future":{"v":2}},"com.game.two":{"refresh_rate":"90"}}""")
        val next = GameProfileDocument.patch(before, "com.game.one", JsonObject(mapOf("gpu_profile" to JsonPrimitive("gaming"))))
        assertEquals(before["com.game.two"], next["com.game.two"])
        assertEquals((before["com.game.one"] as JsonObject)["future"], (next["com.game.one"] as JsonObject)["future"])
        assertEquals(JsonPrimitive("gaming"), (next["com.game.one"] as JsonObject)["gpu_profile"])
    }
    @Test fun disablingRemovesOnlySelectedApp() {
        val before = GameProfileDocument.parse("""{"com.game.one":{},"com.game.two":{}}""")
        val next = GameProfileDocument.patch(before, "com.game.one", null)
        assertFalse(next.containsKey("com.game.one"))
        assertEquals(before["com.game.two"], next["com.game.two"])
    }
    @Test fun addingProfileKeepsExistingDocument() {
        val before = GameProfileDocument.parse("""{"com.game.one":{}}""")
        val next = GameProfileDocument.patch(before, "com.game.two", JsonObject(emptyMap()))
        assertEquals(2, next.size)
        assertEquals(before["com.game.one"], next["com.game.one"])
    }
    @Test fun corruptOrNonObjectInputsAreRejected() {
        for (raw in listOf("bad", "[]", "null", "{\"com.game.one\":4}", "{\"0.85\":{}}")) {
            assertThrows(Exception::class.java) { GameProfileDocument.parse(raw) }
        }
    }
    @Test fun invalidPackageNeverBecomesRootConfigKey() {
        assertThrows(IllegalArgumentException::class.java) {
            GameProfileDocument.patch(JsonObject(emptyMap()), "x;reboot", JsonObject(emptyMap()))
        }
    }
    @Test fun sizeBoundRejectsOversizedDocument() {
        assertThrows(IllegalArgumentException::class.java) { GameProfileDocument.parse(" ".repeat(GameProfileDocument.MAX_BYTES + 1)) }
    }
    @Test fun secondEditComposesFromLatestDocument() {
        val first = GameProfileDocument.patch(JsonObject(emptyMap()), "com.game.one", JsonObject(mapOf("gpu_profile" to JsonPrimitive("gaming"))))
        val second = GameProfileDocument.patch(first, "com.game.one", JsonObject(mapOf("refresh_rate" to JsonPrimitive("90"))))
        assertEquals(JsonPrimitive("gaming"), (second["com.game.one"] as JsonObject)["gpu_profile"])
        assertEquals(JsonPrimitive("90"), (second["com.game.one"] as JsonObject)["refresh_rate"])
    }
}
