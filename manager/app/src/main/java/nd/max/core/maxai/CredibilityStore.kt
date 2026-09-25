/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 */
package nd.max.core.maxai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Per-(knob, direction, app) credibility with recoverable cooldown. */
@Singleton
class CredibilityStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private data class Record(
        var successes: Int = 0,
        var attempts: Int = 0,
        var consecutiveFailures: Int = 0,
        var cooldownUntilMs: Long = 0L,
    )

    private val file = File(context.filesDir, FILE_NAME)
    private val records = linkedMapOf<String, Record>()

    init {
        runCatching {
            if (file.exists()) {
                val root = JSONObject(file.readText())
                root.keys().forEach { key ->
                    val row = root.getJSONObject(key)
                    records[key] = Record(
                        successes = row.optInt("s", 0),
                        attempts = row.optInt("a", 0),
                        consecutiveFailures = row.optInt("f", 0),
                        cooldownUntilMs = row.optLong("c", 0L),
                    )
                }
            }
        }
    }

    @Synchronized
    fun credibility(
        key: String,
        direction: ControlRegistry.Direction,
        appContext: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Float {
        val record = records[compose(key, direction, appContext)] ?: return NEUTRAL_CREDIBILITY
        val estimate = ((record.successes + 1).toFloat() / (record.attempts + 2).toFloat())
        val cooldownPenalty = if (nowMs < record.cooldownUntilMs) COOLDOWN_PENALTY else 1f
        return (estimate * cooldownPenalty).coerceIn(MIN_CREDIBILITY, 1f)
    }

    @Synchronized
    fun record(
        key: String,
        direction: ControlRegistry.Direction,
        appContext: String,
        verified: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val record = records.getOrPut(compose(key, direction, appContext)) { Record() }
        record.attempts++
        if (verified) {
            record.successes++
            record.consecutiveFailures = 0
            record.cooldownUntilMs = 0L
        } else {
            record.consecutiveFailures++
            if (record.consecutiveFailures >= FAILURES_BEFORE_COOLDOWN) {
                record.cooldownUntilMs = nowMs + COOLDOWN_MS
            }
        }
        persist()
    }

    @Synchronized
    fun snapshot(): Map<String, Pair<Int, Int>> =
        records.mapValues { (_, record) -> record.successes to record.attempts }

    private fun compose(key: String, direction: ControlRegistry.Direction, appContext: String): String =
        "$key|${direction.name}|$appContext"

    private fun persist() {
        runCatching {
            val root = JSONObject()
            records.forEach { (key, record) ->
                root.put(
                    key,
                    JSONObject()
                        .put("s", record.successes)
                        .put("a", record.attempts)
                        .put("f", record.consecutiveFailures)
                        .put("c", record.cooldownUntilMs),
                )
            }
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        }
    }

    companion object {
        private const val FILE_NAME = "maxai_credibility.json"
        private const val FAILURES_BEFORE_COOLDOWN = 2
        private const val COOLDOWN_MS = 5 * 60_000L
        private const val COOLDOWN_PENALTY = 0.1f
        private const val MIN_CREDIBILITY = 0.05f
        private const val NEUTRAL_CREDIBILITY = 0.5f
    }
}
