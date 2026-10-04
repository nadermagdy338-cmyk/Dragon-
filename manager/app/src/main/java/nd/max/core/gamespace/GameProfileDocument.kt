/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.gamespace

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import nd.max.core.platform.ForegroundAppResolver

/** The existing AppMonitor applist document, not a new gaming profile database. */
object GameProfileDocument {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_APPS = 4096
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): JsonObject {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val document = json.parseToJsonElement(raw) as? JsonObject ?: error("profile-document-not-object")
        require(document.size <= MAX_APPS)
        require(document.all { (pkg, value) -> ForegroundAppResolver.isPackageName(pkg) && value is JsonObject })
        return document
    }

    /** An update replaces known fields only; future/foreign per-app fields survive. */
    fun patch(document: JsonObject, pkg: String, knownFields: JsonObject?): JsonObject {
        require(ForegroundAppResolver.isPackageName(pkg))
        val next = document.toMutableMap()
        if (knownFields == null) next.remove(pkg)
        else next[pkg] = JsonObject((document[pkg] as? JsonObject).orEmpty() + knownFields)
        return JsonObject(next).also { parse(it.toString()) }
    }
}
