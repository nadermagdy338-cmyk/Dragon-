/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.core.atlas

/** Shared capability vocabulary; producers must not turn missing evidence into success. */
enum class AtlasCapabilityState {
    /** A reviewed route exists and a write was verified on this device in this generation. */
    SUPPORTED,
    /** A reviewed route is eligible; nothing has proven a write yet. */
    WRITABLE,
    /** Reads answer; no eligible write route. */
    READ_ONLY,
    /** Visible, but this build lacks a proven adapter. */
    NEEDS_ADAPTER,
    /** Absence or ineligibility established, never assumed from a failed read. */
    UNAVAILABLE,
    /** A reviewed safety rule forbids touching this interface. */
    NEVER_TOUCH,
    /** Nothing measured, or absence was never proved. */
    UNKNOWN,
}
