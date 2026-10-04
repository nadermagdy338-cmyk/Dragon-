/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.core.spoof

import android.content.Context

/**
 * SP-07 — the honesty barrier: **one acknowledgment per app, before anything else can be attempted**.
 *
 * It is deliberately *not* stored inside [SpoofWorkspace]: a workspace file is exported and imported,
 * and a consent record is not a portable artifact. Importing somebody else's file must never carry
 * their acknowledgment, so this lives in its own preferences set and is revoked explicitly.
 *
 * Acknowledging proves a person read the text; it proves nothing about spoofing, and it unlocks no     * write route — engine/environment checks still run separately.
 */
class SpoofBarrierStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Always a copy: the set handed out by SharedPreferences must never be mutated in place. */
    fun acknowledged(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet().orEmpty()

    fun isAcknowledged(packageName: String): Boolean =
        SpoofWorkspace.validPackage(packageName) && packageName in acknowledged()

    fun acknowledge(packageName: String): Boolean = update { current ->
        if (!SpoofWorkspace.validPackage(packageName) || current.size >= MAX_ENTRIES) current
        else current + packageName
    }

    fun revoke(packageName: String): Boolean = update { it - packageName }

    fun revokeAll(): Boolean = update { emptySet() }

    private fun update(change: (Set<String>) -> Set<String>): Boolean = synchronized(LOCK) {
        val next = change(acknowledged()).filter { SpoofWorkspace.validPackage(it) }.take(MAX_ENTRIES).toSet()
        prefs.edit().putStringSet(KEY, next).commit()
    }

    companion object {
        /** Same shared store the app already uses for local settings; no new storage file. */
        internal const val PREFS = "settings"
        internal const val KEY = "spoof_barrier_ack"
        internal const val MAX_ENTRIES = 1000
        private val LOCK = Any()
    }
}
