/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.core.hardware

/** Result of a hardware mutation after live readback. */
data class VerificationResult<T>(
    val requested: T,
    val actual: T?,
    val writeSucceeded: Boolean,
    val verified: Boolean,
    val error: String? = null,
    val attempts: Int = 1,
) {
    val successful: Boolean get() = writeSucceeded && verified
}

/**
 * Generic write/verify transaction. A successful shell write is never treated
 * as proof of success: the live kernel/vendor value must converge to the request.
 */
object VerifiedControl {
    fun <T> apply(
        requested: T,
        write: (T) -> Boolean,
        read: () -> T?,
        equals: (T, T?) -> Boolean = { expected, actual -> expected == actual },
        maxAttempts: Int = 3,
        retryDelayMs: Long = 40L,
    ): VerificationResult<T> {
        var lastActual: T? = null
        var wrote = false
        var attempts = 0

        repeat(maxAttempts.coerceAtLeast(1)) { index ->
            attempts = index + 1
            wrote = runCatching { write(requested) }.getOrDefault(false) || wrote
            lastActual = runCatching { read() }.getOrNull()
            if (wrote && equals(requested, lastActual)) {
                return VerificationResult(requested, lastActual, true, true, null, attempts)
            }
            if (index + 1 < maxAttempts) {
                runCatching { Thread.sleep((retryDelayMs * (index + 1)).coerceAtMost(500L)) }
            }
        }

        return VerificationResult(
            requested = requested,
            actual = lastActual,
            writeSucceeded = wrote,
            verified = wrote && equals(requested, lastActual),
            error = when {
                !wrote -> "write rejected"
                else -> "live value differs from requested value"
            },
            attempts = attempts,
        )
    }
}
