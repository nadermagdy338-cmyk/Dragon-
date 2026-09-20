package nd.max.core.atlas.support

import nd.max.core.atlas.AtlasFailure
import nd.max.core.hardware.AtlasReadTransport
import nd.max.core.hardware.AtlasTransportList
import nd.max.core.hardware.AtlasTransportRead

/** Operation kinds the read surface is allowed to perform. Anything else is a mutation. */
object AtlasProbeKinds {
    const val READ = "read"
    const val LIST = "list"
    const val CANONICAL = "canonical"
    val SURFACE: Set<String> = setOf(READ, LIST, CANONICAL)
}

/**
 * One recorded transport call. A read surface that can only read cannot hide a write: a mutation
 * would have to appear here, which is exactly what the zero-mutation assertions check.
 */
data class AtlasRecordedOperation(
    val kind: String,
    val path: String,
    val providerId: String,
    val generationAtAttempt: Long,
)

/** Minimal contract for "this probe records what it did". */
interface AtlasRecordingProbe {
    val operations: List<AtlasRecordedOperation>
}

/** A file the fake serves. `slowMs` simulates a kernel interface that takes its time. */
data class AtlasFakeFile(
    val raw: String?,
    val exists: Boolean = true,
    val readable: Boolean = true,
    val slowMs: Long = 0L,
    val maxBytes: Int? = null,
)

/**
 * The lowest-level transport the real [nd.max.core.hardware.ReadOnlyProbeAccess] runs against (`P2`).
 *
 * It only fakes *I/O*, never policy: budgets, path discipline, cause mapping, the absence rule and
 * value parsing are the product's own code, so these tests exercise the code that ships rather than
 * a test double that re-implements it. The transport can only read, and it records every call.
 */
class AtlasFakeTransport(
    private val clock: AtlasClock,
    private val providerLabel: String = "fake",
) : AtlasReadTransport, AtlasRecordingProbe {

    private val files = linkedMapOf<String, AtlasFakeFile>()
    private val directories = linkedMapOf<String, List<String>>()
    private val directorySlowMs = linkedMapOf<String, Long>()
    private val aliases = linkedMapOf<String, String>()
    private val unresolvable = mutableSetOf<String>()

    override val operations: MutableList<AtlasRecordedOperation> = mutableListOf()

    var entriesVisited: Int = 0
        private set

    var providerId: String = providerLabel

    fun putFile(path: String, file: AtlasFakeFile): AtlasFakeTransport {
        files[path] = file
        return this
    }

    fun putDirectory(path: String, names: List<String>, slowMs: Long = 0L): AtlasFakeTransport {
        directories[path] = names
        directorySlowMs[path] = slowMs
        return this
    }

    /** Models a symlink chain: [path] resolves to [target]. */
    fun putAlias(path: String, target: String): AtlasFakeTransport {
        aliases[path] = target
        return this
    }

    /** Models a link loop or a chain longer than the hop bound. */
    fun breakResolution(path: String): AtlasFakeTransport {
        unresolvable.add(path)
        return this
    }

    fun recorded(kind: String): List<AtlasRecordedOperation> = operations.filter { it.kind == kind }

    override fun readText(path: String, maxBytes: Int): AtlasTransportRead {
        operations.add(AtlasRecordedOperation(AtlasProbeKinds.READ, path, providerId, clock.generation()))

        val file = files[path]
        if (file != null) clock.advance(file.slowMs)

        if (file == null) {
            return AtlasTransportRead.Failed(AtlasFailure.UNKNOWN_CAUSE, "transport has no view of this path")
        }
        if (!file.exists) {
            return AtlasTransportRead.Failed(AtlasFailure.ABSENT, "transport reports the path does not exist")
        }
        if (!file.readable) {
            return AtlasTransportRead.Failed(AtlasFailure.PERMISSION_DENIED, "path exists but this attempt could not read it")
        }
        val raw = file.raw
            ?: return AtlasTransportRead.Failed(AtlasFailure.MALFORMED, "empty value")
        val limit = minOf(maxBytes, file.maxBytes ?: maxBytes)
        val bytes = raw.toByteArray(Charsets.UTF_8)
        if (bytes.size > limit) {
            return AtlasTransportRead.Text(String(bytes, 0, limit, Charsets.UTF_8), truncated = true)
        }
        return AtlasTransportRead.Text(raw, truncated = false)
    }

    override fun listNames(path: String, limit: Int): AtlasTransportList {
        operations.add(AtlasRecordedOperation(AtlasProbeKinds.LIST, path, providerId, clock.generation()))
        clock.advance(directorySlowMs[path] ?: 0L)

        val names = directories[path]
            ?: return AtlasTransportList(emptyList(), AtlasFailure.BACKEND_UNAVAILABLE, "no such directory")
        if (names.size > limit) {
            return AtlasTransportList(names.take(limit), AtlasFailure.NONE, "transport truncated at $limit", truncated = true)
        }
        return AtlasTransportList(names, AtlasFailure.NONE, "ok")
    }

    override fun canonicalPath(path: String, maxHops: Int): String? {
        operations.add(AtlasRecordedOperation(AtlasProbeKinds.CANONICAL, path, providerId, clock.generation()))
        if (path in unresolvable) return null

        var current = path
        var hops = 0
        while (true) {
            val next = aliases[current] ?: return current
            hops += 1
            if (hops > maxHops) return null
            current = next
        }
    }
}

/**
 * Deliberately weakened transport used as a negative control: it answers reads but records nothing,
 * so an audit that trusts it would silently lose the ability to detect a mutation.
 */
class AtlasUnrecordedFakeTransport(private val clock: AtlasClock) : AtlasRecordingProbe {

    override val operations: List<AtlasRecordedOperation> = emptyList()

    private val transport = AtlasFakeTransport(clock)

    fun answer(path: String): String? = if (path.isEmpty() || clock.nowMs() < 0L) null else "value"

    val delegate: AtlasReadTransport get() = transport
}

/** Audit helpers: they turn "we believe it only reads" into an assertion. */
object AtlasFakeAudit {

    fun mutations(probe: AtlasRecordingProbe): List<AtlasRecordedOperation> =
        probe.operations.filter { it.kind !in AtlasProbeKinds.SURFACE }

    fun problems(probe: AtlasRecordingProbe, expectedOperations: Int): List<String> {
        val found = mutableListOf<String>()
        if (probe.operations.size != expectedOperations) {
            found.add("expected $expectedOperations recorded operations, saw ${probe.operations.size}")
        }
        val mutations = mutations(probe)
        if (mutations.isNotEmpty()) {
            found.add("mutation operations recorded: ${mutations.map(AtlasRecordedOperation::kind).distinct()}")
        }
        return found
    }
}
