package nd.max.core.atlas

import nd.max.core.hardware.AtlasReadTransport
import nd.max.core.hardware.AtlasTransportList
import nd.max.core.hardware.AtlasTransportRead
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device fixtures (`P9.1`) and the replay transport behind them.
 *
 * The gap this closes is the one every design document in this track admitted: "`needs device`" was
 * a permanent state. A real device's answers could be described in prose but never executed again, so
 * every later change was tested against a fake that its author wrote — which is testing the author's
 * assumption, not the device.
 *
 * A fixture is therefore **not** a story about a device. It is the byte-level answers one real run
 * received, kept as data, replayed later through the *shipped* transport interface. Three rules make
 * that worth anything:
 *
 * 1. **A replayed answer is never invented.** The reference is filled in from the kernel text the run
 *    actually received; every field the shipped code needs is present or the fixture is refused.
 * 2. **An unrecorded path is not "missing".** A replay cannot have observed absence it did not
 *    observe, so an unrecorded path answers `UNKNOWN_CAUSE`, exactly as the boundary treats a failure
 *    whose parent was never enumerated. Silence never becomes a claim about the device.
 * 3. **The origin is recorded, not assumed.** A fixture captured on a build host is labelled [HOST];
 *    it proves the machinery reproduces, and it must never be cited as evidence about a phone. That
 *    single enum is what keeps "we have fixtures now" from turning into "we have device coverage".
 *
 * The codec is explicit and fail-closed, in the same style as the evidence store: unknown schema,
 * missing field, unsafe path, an unsafe name in a listing, or a path outside the approved anchors all
 * produce a refusal and no fixture. A fixture that cannot be trusted cannot be replayed either.
 */

/** Which machine produced a fixture. A host fixture is not device evidence, and says so. */
enum class AtlasFixtureOrigin {
    /** Captured on a real Android device. The only origin that may back a compatibility claim. */
    DEVICE,

    /** Captured on a build host (a JVM filesystem). Proves the replay path, never the phone. */
    HOST,

    /** Written by hand for a test. It asserts what its author believed, and nothing else. */
    SYNTHETIC,
}

/** One recorded answer for one path. Exactly one kind per path, and the kind is part of the record. */
sealed interface AtlasFixtureAnswer {

    /** What the kernel answered: the text, and whether the read was cut at the allowance. */
    data class Text(val text: String, val truncated: Boolean) : AtlasFixtureAnswer

    /** The attempt failed, with the cause the transport reported. `NONE` is not a failure. */
    data class Missing(val cause: AtlasFailure, val reason: String) : AtlasFixtureAnswer {
        init {
            require(cause != AtlasFailure.NONE) { "a recorded failure carries a real cause" }
            require(reason.isNotBlank()) { "a recorded failure says why" }
            require(reason.length <= MAX_REASON_CHARS) { "a recorded reason is a short code, not an essay" }
            require(reason.none { it == '\n' || it == '\r' }) { "a recorded reason is one line" }
        }
    }

    /** A directory that answered. Names are sorted and safe; the cut is recorded separately. */
    data class Listing(val names: List<String>, val truncated: Boolean) : AtlasFixtureAnswer {
        init {
            require(names == names.distinct().sorted()) { "a recorded listing is distinct and sorted" }
            names.forEach { name ->
                require(AtlasIds.isSafeBasename(name)) { "a recorded listing name is not safe: $name" }
            }
        }
    }

    /** The canonical path the run received, so symlinked interfaces replay what the device said. */
    data class Canonical(val resolved: String) : AtlasFixtureAnswer {
        init {
            require(AtlasIds.isSafeAbsolutePath(resolved)) { "a resolved path must be safe: $resolved" }
        }
    }

    /** The run could not resolve the path within its hop bound. `null` is a fact, not a gap. */
    data object Unresolvable : AtlasFixtureAnswer

    companion object {
        /** Long enough for a real errno sentence, short enough that a fixture cannot carry a blob. */
        const val MAX_REASON_CHARS: Int = 160

        /**
         * A recorded value is a scalar, not a dump.
         *
         * This is the same discipline the merged `/proc` summary allowance follows: the fixture format
         * must not become the place a whole file is preserved "just in case".
         */
        const val MAX_TEXT_CHARS: Int = 16 * 1024
    }
}

/**
 * One recorded device, as data.
 *
 * Keyed by path because that is what the transport interface speaks, and validated on construction:
 * every key is a safe absolute path inside the approved anchors, so a fixture cannot widen what this
 * app is willing to look at. The entry bound is enforced here too — an unbounded fixture is how a
 * replay turns into a scan of somebody's whole filesystem.
 */
data class AtlasFixture(
    val origin: AtlasFixtureOrigin,
    val catalogVersion: String,
    val entries: Map<String, AtlasFixtureAnswer>,
) {

    init {
        require(catalogVersion.isNotBlank()) { "a fixture states the catalog revision it was taken with" }
        require(entries.size <= MAX_ENTRIES) { "a fixture holds at most $MAX_ENTRIES answers" }
        entries.keys.forEach { path ->
            require(AtlasIds.isSafeAbsolutePath(path)) { "a fixture key must be a safe path: $path" }
            require(AtlasAnchors.isApproved(path)) { "a fixture key is outside the approved anchors: $path" }
        }
        entries.values.filterIsInstance<AtlasFixtureAnswer.Text>().forEach { answer ->
            require(answer.text.length <= AtlasFixtureAnswer.MAX_TEXT_CHARS) {
                "a recorded value is bounded; refuse rather than keep a dump"
            }
        }
    }

    fun answerFor(path: String): AtlasFixtureAnswer? = entries[path]

    /** Recorded paths, sorted, so an encode is stable and a diff is readable. */
    fun paths(): List<String> = entries.keys.sorted()

    fun counted(kind: (AtlasFixtureAnswer) -> Boolean): Int = entries.values.count(kind)

    fun record(path: String, answer: AtlasFixtureAnswer): AtlasFixture =
        copy(entries = entries + (path to answer))

    companion object {
        /**
         * The documented ceilings.
         *
         * `MAX_ENTRIES` matches the read budget's job-wide entry allowance ([MAX_ENTRIES_PER_JOB]
         * duplicates that number on purpose: this file must not import the boundary just to borrow a
         * constant, and the test asserts they agree).
         */
        const val MAX_ENTRIES: Int = 512

        const val MAX_ENTRIES_PER_JOB: Int = 512

        /** Whole-artifact bound, checked before parsing so a huge text is refused, not walked. */
        const val MAX_BYTES: Int = 512 * 1024

        fun empty(origin: AtlasFixtureOrigin, catalogVersion: String): AtlasFixture =
            AtlasFixture(origin, catalogVersion, emptyMap())
    }
}

/** Why a fixture could not be read. Each one is a different fact, and none of them is "close enough". */
enum class AtlasFixtureDecodeFailure {
    /** Not JSON, or a field is missing or of the wrong type. */
    MALFORMED,

    /** Larger than [AtlasFixture.MAX_BYTES]. Refused rather than truncated. */
    OVERSIZE,

    /** Written by a schema this build does not know. */
    UNSUPPORTED_SCHEMA,

    /** A key, a resolved path or a name is outside what this app may address. */
    UNAPPROVED_PATH,

    /** A listing carried a name that is not a safe basename. */
    UNSAFE_NAME,
}

/** What a decode produced: a fixture and no failure, or the other way round. */
data class AtlasFixtureDecoded(
    val fixture: AtlasFixture?,
    val failure: AtlasFixtureDecodeFailure?,
) {
    val isReadable: Boolean get() = fixture != null && failure == null
}

/**
 * The fixture format, written by hand.
 *
 * No reflection, no "unknown key is fine", every field required for the kind that names it: a format
 * that silently accepts what it does not understand is a format that can invent a device answer a
 * year from now.
 */
object AtlasFixtureCodec {

    const val SCHEMA: Int = 1

    fun encode(fixture: AtlasFixture): String {
        val root = JSONObject()
        root.put(KEY_SCHEMA, SCHEMA)
        root.put(KEY_ORIGIN, fixture.origin.name)
        root.put(KEY_CATALOG, fixture.catalogVersion)
        root.put(
            KEY_ENTRIES,
            JSONArray().apply {
                fixture.paths().forEach { path ->
                    val answer = fixture.entries.getValue(path)
                    put(
                        JSONObject().apply {
                            put(KEY_PATH, path)
                            put(KEY_KIND, kindOf(answer))
                            when (answer) {
                                is AtlasFixtureAnswer.Text -> {
                                    put(KEY_TEXT, answer.text)
                                    put(KEY_TRUNCATED, answer.truncated)
                                }

                                is AtlasFixtureAnswer.Missing -> {
                                    put(KEY_CAUSE, answer.cause.name)
                                    put(KEY_REASON, answer.reason)
                                }

                                is AtlasFixtureAnswer.Listing -> {
                                    put(KEY_NAMES, JSONArray(answer.names))
                                    put(KEY_TRUNCATED, answer.truncated)
                                }

                                is AtlasFixtureAnswer.Canonical -> put(KEY_RESOLVED, answer.resolved)

                                AtlasFixtureAnswer.Unresolvable -> Unit
                            }
                        },
                    )
                }
            },
        )
        return root.toString()
    }

    fun decode(text: String): AtlasFixtureDecoded {
        if (text.toByteArray(StandardCharsets.UTF_8).size > AtlasFixture.MAX_BYTES) {
            return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.OVERSIZE)
        }
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.MALFORMED)
        // Two different facts, kept apart: a document with no schema number is not a fixture at all,
        // while a fixture written by another build is a fixture this build does not understand. Saying
        // "unsupported version" about `{}` would describe a version that was never written down.
        if (!root.has(KEY_SCHEMA)) {
            return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.MALFORMED)
        }
        if (root.optInt(KEY_SCHEMA, -1) != SCHEMA) {
            return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.UNSUPPORTED_SCHEMA)
        }
        val origin = root.optString(KEY_ORIGIN, "")
        val fixtureOrigin = AtlasFixtureOrigin.entries.firstOrNull { it.name == origin }
            ?: return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.MALFORMED)
        val catalog = root.optString(KEY_CATALOG, "")
        if (catalog.isBlank()) return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.MALFORMED)
        val entries = root.optJSONArray(KEY_ENTRIES)
            ?: return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.MALFORMED)
        if (entries.length() > AtlasFixture.MAX_ENTRIES) {
            return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.OVERSIZE)
        }

        val recorded = LinkedHashMap<String, AtlasFixtureAnswer>()
        for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index)
                ?: return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.MALFORMED)
            val path = item.optString(KEY_PATH, "")
            if (!AtlasIds.isSafeAbsolutePath(path)) {
                return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.MALFORMED)
            }
            // The anchor check is the same question the boundary asks, asked here so a fixture can
            // never be the door that widens what this app is willing to read.
            if (!AtlasAnchors.isApproved(path)) {
                return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.UNAPPROVED_PATH)
            }
            if (recorded.containsKey(path)) {
                return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.MALFORMED)
            }
            val answer = when (item.optString(KEY_KIND, "")) {
                KIND_TEXT -> {
                    if (!item.has(KEY_TEXT) || !item.has(KEY_TRUNCATED)) {
                        return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.MALFORMED)
                    }
                    val value = item.optString(KEY_TEXT, "")
                    if (value.length > AtlasFixtureAnswer.MAX_TEXT_CHARS) {
                        return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.OVERSIZE)
                    }
                    AtlasFixtureAnswer.Text(value, item.optBoolean(KEY_TRUNCATED, false))
                }

                KIND_FAILED -> {
                    val cause = AtlasFailure.entries.firstOrNull { it.name == item.optString(KEY_CAUSE, "") }
                        ?: return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.MALFORMED)
                    if (cause == AtlasFailure.NONE) return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.MALFORMED)
                    val reason = item.optString(KEY_REASON, "")
                    if (reason.isBlank()) return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.MALFORMED)
                    AtlasFixtureAnswer.Missing(cause, reason)
                }

                KIND_LISTING -> {
                    val names = item.optJSONArray(KEY_NAMES)
                        ?: return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.MALFORMED)
                    val collected = ArrayList<String>(names.length())
                    for (nameIndex in 0 until names.length()) {
                        val name = names.optString(nameIndex, "")
                        if (!AtlasIds.isSafeBasename(name)) {
                            return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.UNSAFE_NAME)
                        }
                        collected += name
                    }
                    AtlasFixtureAnswer.Listing(collected.distinct().sorted(), item.optBoolean(KEY_TRUNCATED, false))
                }

                KIND_CANONICAL -> {
                    val resolved = item.optString(KEY_RESOLVED, "")
                    if (!AtlasIds.isSafeAbsolutePath(resolved)) {
                        return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.UNAPPROVED_PATH)
                    }
                    AtlasFixtureAnswer.Canonical(resolved)
                }

                KIND_UNRESOLVABLE -> AtlasFixtureAnswer.Unresolvable
                else -> return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.MALFORMED)
            }
            recorded[path] = answer
        }

        val fixture = runCatching { AtlasFixture(fixtureOrigin, catalog, recorded) }.getOrNull()
            ?: return AtlasFixtureDecoded(null, AtlasFixtureDecodeFailure.UNAPPROVED_PATH)
        return AtlasFixtureDecoded(fixture, null)
    }

    private fun kindOf(answer: AtlasFixtureAnswer): String = when (answer) {
        is AtlasFixtureAnswer.Text -> KIND_TEXT
        is AtlasFixtureAnswer.Missing -> KIND_FAILED
        is AtlasFixtureAnswer.Listing -> KIND_LISTING
        is AtlasFixtureAnswer.Canonical -> KIND_CANONICAL
        AtlasFixtureAnswer.Unresolvable -> KIND_UNRESOLVABLE
    }

    private const val KEY_SCHEMA = "schema"
    private const val KEY_ORIGIN = "origin"
    private const val KEY_CATALOG = "catalog"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_PATH = "path"
    private const val KEY_KIND = "kind"
    private const val KEY_TEXT = "text"
    private const val KEY_TRUNCATED = "truncated"
    private const val KEY_CAUSE = "cause"
    private const val KEY_REASON = "reason"
    private const val KEY_NAMES = "names"
    private const val KEY_RESOLVED = "resolved"

    private const val KIND_TEXT = "text"
    private const val KIND_FAILED = "failed"
    private const val KIND_LISTING = "listing"
    private const val KIND_CANONICAL = "canonical"
    private const val KIND_UNRESOLVABLE = "unresolvable"
}

/**
 * A recorded device, as a transport.
 *
 * Drop this into [nd.max.core.hardware.ReadOnlyProbeAccess] in place of the rootless transport and
 * the *whole* shipped chain — path discipline, budgets, absence proof, parsing, resolution, reporting
 * — runs against what a real device said, with no device present.
 *
 * Two defaults are deliberate:
 *
 * - **An unrecorded path is `UNKNOWN_CAUSE`, never `ABSENT`.** A fixture that stayed silent about a
 *   path proves nothing about that path, and the one thing this replay must not do is manufacture a
 *   statement about a device it never read.
 * - **An unrecorded canonical path is the path itself.** A fixture records resolution only where the
 *   run found a link; treating every unrecorded path as link-free is the only reading that does not
 *   invent either a link or a failure.
 */
class AtlasFixtureTransport(private val fixture: AtlasFixture) : AtlasReadTransport {

    override fun readText(path: String, maxBytes: Int): AtlasTransportRead {
        require(maxBytes > 0) { "a read allowance must be positive" }
        return when (val answer = fixture.answerFor(path)) {
            is AtlasFixtureAnswer.Text -> {
                // Byte-aware on the safe side: taking `maxBytes` characters cannot exceed `maxBytes`
                // UTF-8 bytes, so a cut here never hands back more than the caller allowed.
                if (answer.text.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) {
                    AtlasTransportRead.Text(answer.text, answer.truncated)
                } else {
                    AtlasTransportRead.Text(answer.text.take(maxBytes), truncated = true)
                }
            }

            is AtlasFixtureAnswer.Missing -> AtlasTransportRead.Failed(answer.cause, answer.reason)

            is AtlasFixtureAnswer.Listing -> AtlasTransportRead.Failed(
                AtlasFailure.UNKNOWN_CAUSE,
                "recorded as a directory; a directory is not a value",
            )

            is AtlasFixtureAnswer.Canonical, AtlasFixtureAnswer.Unresolvable -> AtlasTransportRead.Failed(
                AtlasFailure.UNKNOWN_CAUSE,
                "recorded without a value; run `listNames` or `canonicalPath` for this path",
            )

            null -> AtlasTransportRead.Failed(AtlasFailure.UNKNOWN_CAUSE, NOT_RECORDED)
        }
    }

    override fun listNames(path: String, limit: Int): AtlasTransportList {
        require(limit > 0) { "a listing allowance must be positive" }
        return when (val answer = fixture.answerFor(path)) {
            is AtlasFixtureAnswer.Listing -> {
                val names = answer.names.take(limit)
                val cut = answer.truncated || answer.names.size > limit
                AtlasTransportList(
                    names = names,
                    cause = AtlasFailure.NONE,
                    reason = if (cut) "truncated at $limit entries" else "complete listing",
                    truncated = cut,
                )
            }

            is AtlasFixtureAnswer.Missing ->
                AtlasTransportList(emptyList(), answer.cause, answer.reason)

            is AtlasFixtureAnswer.Text ->
                AtlasTransportList(emptyList(), AtlasFailure.UNKNOWN_CAUSE, "recorded as a value, not a directory")

            is AtlasFixtureAnswer.Canonical, AtlasFixtureAnswer.Unresolvable ->
                AtlasTransportList(emptyList(), AtlasFailure.UNKNOWN_CAUSE, "recorded without a listing")

            null -> AtlasTransportList(emptyList(), AtlasFailure.UNKNOWN_CAUSE, NOT_RECORDED)
        }
    }

    override fun canonicalPath(path: String, maxHops: Int): String? {
        if (maxHops <= 0) return null
        return when (val answer = fixture.answerFor(path)) {
            is AtlasFixtureAnswer.Canonical -> answer.resolved
            AtlasFixtureAnswer.Unresolvable -> null
            // Identity for everything else, including an unrecorded path: no link was recorded, and
            // inventing one would be a statement the run never made.
            else -> path
        }
    }

    companion object {
        private const val NOT_RECORDED = "the fixture has no record of this path"
    }
}
