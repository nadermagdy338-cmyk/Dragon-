/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.gamespace

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GameProfilePersistenceTest {
    private class Io : GameProfilePersistence.Io {
        var live: String? = "{}"
        var reads = 0
        var writes = 0
        var writable = true
        var onRead: (Int) -> Unit = {}
        var afterWrite: () -> Unit = {}
        override fun read(): String? { onRead(++reads); return live }
        override fun write(text: String): Boolean {
            writes++
            if (!writable) return false
            live = text
            afterWrite()
            return true
        }
    }
    private fun fields(value: String) = JsonObject(mapOf("gpu_profile" to JsonPrimitive(value)))
    @Test fun readbackRequiredBeforeSuccess() {
        val io = Io()
        assertTrue(GameProfilePersistence(io).update("com.game.one") { fields("gaming") }.saved)
        assertEquals(3, io.reads)
        assertEquals(1, io.writes)
    }
    @Test fun unavailableOrCorruptStorageDoesNotBecomeEmptyDatabase() {
        for (raw in listOf(null, "broken", "[]")) {
            val io = Io().apply { live = raw }
            assertThrows(Exception::class.java) { GameProfilePersistence(io).update("com.game.one") { fields("gaming") } }
            assertEquals(0, io.writes)
        }
    }
    @Test fun externalEditDetectedBeforeWriteIsPreserved() {
        val io = Io().apply { onRead = { if (it == 2) live = "{\"com.other.app\":{}}" } }
        val result = GameProfilePersistence(io).update("com.game.one") { fields("gaming") }
        assertFalse(result.saved)
        assertEquals(0, io.writes)
        assertTrue(result.document!!.containsKey("com.other.app"))
    }
    @Test fun unsuccessfulWriterCannotPublishSuccess() {
        val io = Io().apply { writable = false }
        assertFalse(GameProfilePersistence(io).update("com.game.one") { fields("gaming") }.saved)
        assertEquals("{}", io.live)
    }
    @Test fun externalReadbackMismatchDoesNotOverwriteForeignChange() {
        val io = Io().apply { afterWrite = { live = "{\"com.other.app\":{}}" } }
        val result = GameProfilePersistence(io).update("com.game.one") { fields("gaming") }
        assertFalse(result.saved)
        assertEquals(1, io.writes)
        assertTrue(result.document!!.containsKey("com.other.app"))
    }
    @Test fun sequentialEditorsResolveAgainstFreshDiskNotTheirUiMaps() {
        val io = Io()
        val persistence = GameProfilePersistence(io)
        assertTrue(persistence.update("com.game.one") { fields("gaming") }.saved)
        assertTrue(persistence.update("com.game.two") { fields("power") }.saved)
        assertEquals(2, GameProfileDocument.parse(io.live!!).size)
    }
    @Test fun transformExceptionNeverWrites() {
        val io = Io()
        assertThrows(IllegalStateException::class.java) { GameProfilePersistence(io).update("com.game.one") { error("invalid") } }
        assertEquals(0, io.writes)
    }
    @Test fun rejectedKnownFieldTypesPreventMutation() {
        val io = Io()
        val persistence = GameProfilePersistence(io, validate = { error("invalid-field-type") })
        assertThrows(IllegalStateException::class.java) { persistence.update("com.game.one") { fields("gaming") } }
        assertEquals(0, io.writes)
    }
    @Test fun invalidTransformedDocumentPreventsMutation() {
        val io = Io()
        val persistence = GameProfilePersistence(io, validate = { document ->
            check(document.values.none { (it as JsonObject)["gpu_profile"] == JsonPrimitive("invalid") })
        })
        assertThrows(IllegalStateException::class.java) { persistence.update("com.game.one") { fields("invalid") } }
        assertEquals(0, io.writes)
    }
    @Test fun unreadableReadbackRemainsUnverified() {
        val io = Io().apply { afterWrite = { live = null } }
        val result = GameProfilePersistence(io).update("com.game.one") { fields("gaming") }
        assertFalse(result.saved)
        assertEquals(null, result.document)
    }
}
