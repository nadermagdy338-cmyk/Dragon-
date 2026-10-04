/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.gamespace

import kotlinx.serialization.json.JsonObject

/** Configuration persistence only, never a claim about hardware or a gaming session. */
internal class GameProfilePersistence(private val io: Io, private val validate: (JsonObject) -> Unit = {}) {
    interface Io {
        fun read(): String?
        fun write(text: String): Boolean
    }
    data class Result(val saved: Boolean, val document: JsonObject?)

    /** Caller holds the repository mutex; external root editors still have a documented TOCTOU window. */
    fun update(pkg: String, transform: (JsonObject?) -> JsonObject?): Result {
        val before = GameProfileDocument.parse(io.read() ?: error("profile-document-unreadable"))
        validate(before)
        val next = GameProfileDocument.patch(before, pkg, transform(before[pkg] as? JsonObject))
        validate(next)
        val live = io.read()?.let(GameProfileDocument::parse)
        if (live != before) return Result(false, live)
        if (!io.write(next.toString())) return Result(false, io.read()?.let(GameProfileDocument::parse))
        val actual = io.read()?.let(GameProfileDocument::parse)
        return Result(actual == next, actual)
    }
}
