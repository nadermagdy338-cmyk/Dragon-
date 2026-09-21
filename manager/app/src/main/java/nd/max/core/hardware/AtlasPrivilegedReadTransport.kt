package nd.max.core.hardware

import nd.max.core.atlas.AtlasFailure
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Root-backed read transport for Atlas. It has no mutation API and is selected only when root is
 * already cached as granted; opening Atlas never prompts for privilege. The boundary above this class
 * still enforces approved anchors, path safety and read budgets.
 */
class AtlasPrivilegedReadTransport : AtlasReadTransport {
    override fun readText(path: String, maxBytes: Int): AtlasTransportRead {
        val raw = RootFileAccess.read(path)
            ?: return if (RootFileAccess.exists(path)) {
                AtlasTransportRead.Failed(AtlasFailure.PERMISSION_DENIED, "privileged read was refused")
            } else {
                AtlasTransportRead.Failed(AtlasFailure.ABSENT, "the interface does not exist")
            }
        val bytes = raw.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size <= maxBytes) return AtlasTransportRead.Text(raw, truncated = false)
        // Truncation is cut on a character boundary: a byte slice could split a multi-byte character and
        // hand the parser a replacement glyph that looks like the interface's own text.
        val cut = String(bytes, 0, maxBytes, StandardCharsets.UTF_8).dropLastWhile { it == '\uFFFD' }
        return AtlasTransportRead.Text(cut, truncated = true)
    }

    override fun listNames(path: String, limit: Int): AtlasTransportList {
        val names = RootFileAccess.listNames(path)
        if (names.isEmpty() && !RootFileAccess.exists(path)) {
            return AtlasTransportList(emptyList(), AtlasFailure.ABSENT, "the directory does not exist")
        }
        return AtlasTransportList(
            names = names.take(limit),
            truncated = names.size > limit,
            reason = "listed through the authorized root read path",
        )
    }

    override fun canonicalPath(path: String, maxHops: Int): String? = runCatching {
        File(path).canonicalPath.takeIf { it.startsWith("/") }
    }.getOrNull()
}

/**
 * Chooses privileged reads when already available and falls back to the ordinary transport otherwise.
 * A root read failure is not hidden: fallback is attempted only for permission/backend failures, not
 * for a proven missing interface.
 */
class AtlasAdaptiveReadTransport(
    private val ordinary: AtlasReadTransport,
    private val privileged: AtlasReadTransport,
    private val privilegedAvailable: () -> Boolean,
) : AtlasReadTransport {
    override fun readText(path: String, maxBytes: Int): AtlasTransportRead {
        if (!privilegedAvailable()) return ordinary.readText(path, maxBytes)
        return when (val elevated = privileged.readText(path, maxBytes)) {
            is AtlasTransportRead.Text -> elevated
            is AtlasTransportRead.Failed -> when (elevated.cause) {
                AtlasFailure.PERMISSION_DENIED,
                AtlasFailure.BACKEND_UNAVAILABLE,
                -> ordinary.readText(path, maxBytes)
                else -> elevated
            }
        }
    }

    override fun listNames(path: String, limit: Int): AtlasTransportList {
        if (!privilegedAvailable()) return ordinary.listNames(path, limit)
        val elevated = privileged.listNames(path, limit)
        return if (elevated.cause == AtlasFailure.PERMISSION_DENIED ||
            elevated.cause == AtlasFailure.BACKEND_UNAVAILABLE
        ) {
            ordinary.listNames(path, limit)
        } else {
            elevated
        }
    }

    override fun canonicalPath(path: String, maxHops: Int): String? {
        val elevated = if (privilegedAvailable()) privileged.canonicalPath(path, maxHops) else null
        return elevated ?: ordinary.canonicalPath(path, maxHops)
    }
}
