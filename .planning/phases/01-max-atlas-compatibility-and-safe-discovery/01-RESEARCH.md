# Phase 1: Max Atlas Compatibility And Safe Discovery - Research

**Task:** ATLAS-R1, gsd-phase-researcher, research-only planning.
**Researched:** 2026-09-20 UTC.
**Domain:** Android/Linux capability discovery, evidence provenance, bounded I/O and support loop.
**Confidence:** HIGH for cited platform contracts and inspected source branches; MEDIUM for design transfer; LOW for untested OEM/hardware claims.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

### Owner Requirements

- **D-01:** Prioritize broad device compatibility and extensibility over per-model hardcoding.
- **D-02:** Research SmartPack-Kernel-Manager and every named reference in txt.txt. Use them as
  architectural/behavioral references only; do not copy code, scripts or bulk path databases.
- **D-03:** First use reviewed accumulated knowledge; only unresolved features advance to deeper
  discovery. Do not ask users to report before these safe stages have finished.
- **D-04:** The new discovery intelligence is separate from Max AI and has a distinct name.
- **D-05:** Settings provides a last-resort app support report; maintainer analysis leads to
  tested device-support fixes and a normal reviewed publication, not runtime downloaded code.
- **D-06:** Preserve the existing app/control plane and finished work. Keep docs/ai as the
  canonical ADR authority and sole handoff log; this GSD track does not replace old tasks.

### Engineering Constraints From Binding ADRs

- **D-07:** ADR-07/23/33: retain source/freshness/unknown distinctions; capability is not state,
  inferred semantics are not verified control, and null must not become plausible telemetry.
- **D-08:** ADR-09/11/16/17: no new writer or per-screen root probe; retain canonical knobs,
  ownership, safety veto, baseline capture, readback and rollback. Discovery is read-only.
- **D-09:** ADR-03/06/08/14/26: keep existing Settings/navigation and design primitives, explicit
  disabled reasons, English/Arabic resources together and Crowdin registration.

### Claude's Discretion

- Proposed name: **Max Atlas**, suggesting a device capability map rather than a competing
  optimizer. The owner requested a suitable name and approved initialization with this proposal.
- Use deterministic on-device evidence ranking and bounded discovery, not an LLM dependency.
  The word intelligence is not permission to generate root commands or guess safe values.
- Exact budgets, schema, cache lifetime, provider granularity and fixture organization must be
  concrete planning choices, clearly distinguished from measured performance.
- Prefer app-private local evidence, consented Android sharing and normal app releases. Do not
  invent a server, account, recipient address or network-based model.
- Preserve current minSdk/native ABIs; inventory their limits instead of quietly promising
  older Android, 32-bit support, unrestricted HAL access or rootless tuning.

### Deferred Ideas (OUT OF SCOPE)

Not requested or authorized here: cloud AI, new Shizuku/vendor privilege transports, root exploits,
SELinux changes, boot-time mutation, replacing Max AI, signing/releasing in this session, or
importing third-party executable plugins. Architecture may leave seams for separately reviewed work.
</user_constraints>

## Summary

**Primary recommendation:** implement Max Atlas as a deterministic, bounded **read-only evidence
service** beside the existing capability facade, not as a new controller, root-command generator,
model-name database, or automatic hardware experimenter. Reviewed interface knowledge nominates
candidates; current runtime evidence validates observations; only an already reviewed existing
backend may remain control-eligible through its existing authority. No score upgrades a path into
a writer. [DESIGN; D-01..D-08]

External research supports small generic/vendor providers and truthful per-feature fallback, but
also falsifies important overstatements in `txt.txt`. Calibrate's actual root writer is not a
universal readback transaction and includes chmod/service hooks; ACC switch tests mutate charging;
FusionHUD uses unit/load heuristics; Kelvin's generic provider is conditional, not universal.
These projects supply patterns and counterexamples, not safety certificates. [VERIFIED: S02-S04,
S06 targeted source, `01-SOURCES.md`]

All **17 required references plus the identifiable Universal Smart Governor mention** have explicit
assessments in [01-SOURCES.md](01-SOURCES.md), with exact URLs, revisions, licensing evidence and
F/R/S/N inspection levels. No upstream tests were run and no device result was reproduced. Local
product-source exploration belongs to the peer's `01-PATTERNS.md`; local API signatures, versions,
test availability and exact integration files must come from that artifact, not this external
research. No code/database copying, package installation, product change or publication occurred.

## Architectural Responsibility Map

The following is a proposed responsibility split, not a description of invented local APIs. [DESIGN]

| Capability | Primary Owner | Boundary |
| --- | --- | --- |
| Reviewed capability knowledge | Versioned app-bundled catalog | Static semantic metadata and provenance, no executable scripts or downloaded rules |
| Runtime evidence collection | Max Atlas shared read-only service | Typed, bounded I/O; existing privilege state only; no writer dependency |
| Provider selection and evidence interpretation | Small generic/vendor/platform adapters | Per-feature contributions, common contracts, deterministic ambiguity handling |
| Optimization policy | Existing Max AI | Not changed by this phase; discovery cannot become another optimizer |
| Mutation authorization and execution | Existing control plane | Existing canonical knobs, locks, ownership, safety, baseline, readback and rollback remain authoritative |
| Progress, reasons, retry | Existing Settings/Diagnostics surface | Observable shared state, lifecycle-aware presentation; no per-screen root probe |
| Report construction and sharing | App-private support component + Android share UI | Minimized structured evidence; preview/redact/consent; no upload |
| Device support improvement | Maintainer review/test/release workflow | Sanitized report -> fixture -> narrow reviewed support -> ordinary release |

## Standard Stack

**Reuse the existing stack; assume no new packages.** The binding engineering contract specifies
Kotlin, coroutines/Flow, existing DI and Compose conventions. Project versions and concrete test
frameworks are a **peer-verified local baseline**, not something to select from upstream projects.
No SmartPack/FusionHUD/Unity/Calibrate/ACC dependency is recommended. [CITED:
`docs/ai/ENGINEERING-CONTRACT.md` sections 1, 5, 10; D-06]

| Layer | Recommendation | Version / Availability |
| --- | --- | --- |
| Domain models/parsers | Existing Kotlin toolchain, immutable typed results and pure parsing | Preserve local versions from `01-PATTERNS.md`; no version bump |
| Concurrency/state | Existing coroutines/Flow/DI; one coalesced discovery job | Reuse installed dependencies; actual cancellation contract must be tested |
| I/O | Narrow read-only port over bounded existing transport or additive bounded read implementation | Do not assume an existing nullable read wrapper preserves errno or kills blocked shell work |
| Catalog/cache serialization | Existing project serializer/storage primitives | No Room/JSON-schema/database package assumed; schema version explicitly owned by Atlas |
| Android observation | Existing platform API access where available | SDK + runtime capability gated; no new HAL/vendor bridge |
| Settings UI | Existing `ui/design/`, navigation registry, paired EN/AR resources | Preserve minSdk and native ABIs; consult peer baseline per distribution path |
| Report sharing | Existing AndroidX FileProvider/Android Intent support if present | Standard documented content-URI mechanism [VERIFIED: A05]; check existing configured roots |
| Tests | Existing JVM and Android test infrastructure | Use peer-confirmed framework/commands; do not install a new framework |

**Installation:** none. Package legitimacy audit is not applicable because no external package is
being installed or proposed. Upstream project licenses are reference provenance, not dependency
approval. Public source visibility and reference-only design do **not** guarantee legal clearance.

## Platform Contracts

### Linux Interfaces

| Surface | Verified Meaning | Planning Consequence |
| --- | --- | --- |
| sysfs | Driver callbacks behind file I/O; errors/disappearance possible; class links often target `/sys/devices` [VERIFIED: L01] | Allowlist read attributes and constrain resolved paths, deduplicate aliases, retain failures. Do not forbid all symlinks or recursively follow every link. |
| CPU cpufreq | Policy groups, not fixed little/big/prime indices; related CPUs include offline CPUs; kHz attributes; optional tables [VERIFIED: L02] | Enumerate actual bounded `policyN` entries; parse membership; separate hardware range/table/current request/current measurement. Missing a table does not erase otherwise readable CPU observations. |
| GPU devfreq | Non-CPU DVFS class, not exclusively GPUs; frequencies/table/current/limits have different meanings [VERIFIED: L03-L04] | Require driver/device evidence to identify GPU; generic `devfreq` entry or token is only a candidate. Mainline uses Hz; vendor differences need reviewed semantic contracts. |
| Thermal | `type` driver-supplied, `temp` millidegrees C, cooling states not temperatures [VERIFIED: L05] | Never assign battery/CPU/GPU by thermal_zone index. Keep unknown sensor role unknown; classify only as far as evidence supports. No emulation, mode, trip or cooling-state writes. |
| Power supply | microV, microA, microAh, microWh, seconds and tenths C unless specified [VERIFIED: L06] | Typed dimensions, overflow-checked conversion, missing remains absent. Never treat energy as charge or use thermal-zone scale for battery temperature. |

`scaling_cur_freq` may represent last requested P-state; devfreq `cur_freq` may reflect target
without hardware feedback. A successful read is evidence of an ABI value, not always an exact
instantaneous hardware measurement. Keep that semantic qualification in source metadata. Zero
devfreq min/max is a documented no-user-limit sentinel, not necessarily malformed or 0 Hz. Range
and plausibility checks can reject evidence; they cannot establish units or sensor identity.
[VERIFIED: L02-L04; DESIGN]

Even a read invokes driver code. Read-only means Atlas sends no mutation operations, not that
every imaginable vendor/debug node is proven inert. Only reviewed read families/attributes are
opened; arbitrary device files, debugfs, register dumps and full-filesystem scans are excluded.
[VERIFIED: L01; DESIGN]

### Android Is Not An Unrestricted Tuning Backend

- PerformanceHintManager sessions describe the calling application's threads and work duration; foreign thread IDs are not an unrestricted control channel. Use ADPF as an architectural/observation reference, not an automatic cross-app CPU/GPU fallback for MaxManager. [VERIFIED: A01]
- PowerManager thermal status/headroom are signals, not setters. Headroom is an estimate/forecast of envelope use; 1.0 indicates severe throttling, values can exceed 1, NaN must remain unavailable, warmup can return current instead of forecast. It is not a die-temperature reading or a universally calibrated percent of safe performance. [VERIFIED: A02]
- HardwarePropertiesManager raw temperatures/CPU usage/fan data are restricted to device owner/current VR service per its Javadocs. Public class presence is not permission. A provider must return unavailable/denied rather than promise rootless access. [VERIFIED: A03]
- AOSP untrusted-app policy prohibits sysfs writes and restricts several proc/cgroup reads. Root-manager grants, shell UID, app UID and vendor services are different access contexts, not interchangeable capability levels. Do not infer an OEM outcome from upstream SELinux policy alone. [VERIFIED: A04; DESIGN]
- Preserve SDK checks and actual session/privilege state. The exact locally shipped API availability and ABI distribution are peer-owned verification; do not broaden them based on other projects' minimum versions. No new Shizuku UserService, arbitrary binder invocation or SELinux policy adjustment belongs here. [DESIGN; D-08, deferred ideas]

## Architecture Patterns

### Data Flow

```text
App session / explicit Settings retry
              |
              v
     Coalesced Atlas job + generation token
              |
              v
  Reviewed catalog + current identity/privilege context
              |
       known read candidates
              v
  Bounded read-only IO -> parse -> semantic evidence
              |
     per-feature resolution decision
       /                         \
resolved observation          unresolved feature
       |                         |
       |                  bounded deeper discovery
       |                         |
       |                same validation + decision
       |                  /                 \
       |              resolved       reasoned terminal result
       |                  |                 |
       +------> evidence/cache <------------+
                         |
             Settings progress/reasons
                         |
         after safe stages finish, unresolved?
                    /          \
                  no            yes
                  |              |
          no report prompt   optional report preview
                                 |
                        user redact + consent
                                 |
                         local Android share
                                 |
                maintainer fixture -> review -> normal release

Separate unchanged mutation path:
existing intent -> existing canonical backend/control plane -> existing verification/rollback
Atlas has no write edge, and no discovered path is inserted into that path automatically.
```

[DESIGN; supported by S01-S04, S06, U01 and binding ADRs]

### 1. Catalog Describes Contracts, Not Phone Profiles

Store independently authored reviewed entries keyed by **interface/driver semantics**, not one
file per device. Each entry needs schema/catalog version, stable candidate ID, domain, observation
kind, driver/ABI matcher, allowed namespace/attributes, expected representation/unit, semantic
validator, risk class, provenance URL/revision/license notes and test fixture references. An
optional reference to an **existing backend identity** is not a supplied command or a writable
path override. Unknown catalog versions/fields that affect semantics fail closed. [DESIGN]

Model names may provide supporting metadata or a narrowly reviewed quirk match. They must not
block a standard cpufreq or battery observation on an unknown model. A vendor quirk can specialize
one feature without suppressing generic providers for other features. Explicitly match vendor
quirks by more than brand when firmware semantics matter. [DESIGN; S03, S07]

### 2. Generic Core With Small Vendor/Platform Providers

Use one read-only provider contract: given feature request, reviewed catalog, current access
context and budget, return typed observations/candidates plus reasons. Generic Linux and Android
API providers handle standardized observations; small Qualcomm/MediaTek/Exynos-Tensor adapters
add only reviewed identity/format differences. An unrecognized vendor is a normal generic case.
Do not copy Kelvin's global first-provider-wins rule for the whole device: resolve independently
per feature, preserving successful generic evidence when one specialized feature fails.
[DESIGN; VERIFIABLE ANALOGIES: S03, S16/U01]

Provider code must not have mutation, chmod, service-control, mount, raw command, installer or
remote-plugin methods. It receives only the narrow read port. Existing domain backends must be
audited by the local mapper before reuse: a method named `load`, `probe`, `check`, or `supported`
can still apply settings. Source names do not establish read-only behavior. [DESIGN; S02, S06]

### 3. Evidence Ranking And Authorization Are Separate

Recommended descending evidence order: reviewed documented ABI + matching driver identity +
current validated read; reviewed vendor interface + exact applicable fingerprint/driver evidence;
corroborated but incomplete observation; name/token/plausibility heuristic only. This is an
ordering for candidate investigation, **not a numeric permission threshold**. Conflicting equally
credible sources return ambiguous, not arbitrary first match. [DESIGN]

Keep orthogonal fields rather than one overloaded `supported` boolean:

- Observation: absent, permission denied, unreadable, malformed, valid typed value, stale, ambiguous.
- Access/backend: no access, readable, permission-indicated writable, existing backend unavailable/available.
- Semantic certainty: unknown, inferred, reviewed ABI matched.
- Execution evidence: not attempted by Atlas; historical/current verified control outcome only if supplied by the existing control plane, never minted by discovery.
- Lifecycle: not started, known stage, discovery stage, complete, budget-limited, permission-blocked, cancelled or failed.

[DESIGN; D-07/D-08; counterexamples S01-S04/S06]

`test -w`, chmod bits, root availability, successful shell exit, a plausible frequency, a high
confidence score or successful no-op write do not establish a safe backend. Do not test writes
to discover new controls; no readback/rollback experiment is part of Atlas. Existing controls
keep their existing validation before ordinary user-authorized use. [DESIGN; L01, A04, S02, S06]

### 4. Bounded Enumeration And Typed I/O

Try reviewed candidates first, then enumerate only unresolved feature namespaces. Enumerate
direct children of known class roots and read a fixed reviewed attribute set; allow vetted
canonical symlink targets under expected `/sys/devices` subtrees, with depth/loop/escape checks.
Never run `find /`, scan arbitrary `/proc`, read unknown vendor register nodes, or append a
discovered name into an unquoted shell command. [DESIGN; L01-L05]

Preserve errno/exception class and truncated status before parsing. Denial is not absence and a
timeout is not unsupported hardware. Directory limits apply while enumerating, not after an
unbounded `listFiles()`/shell output has already allocated everything. Bounded output must be
enforced at the reader/transport, not by truncating a completed giant string. [DESIGN]

Cancellation must cancel/close the underlying operation where possible and always prevent late
results from changing the current generation. A coroutine timeout alone does not prove a blocking
shared shell or kernel read stopped. If the existing transport cannot satisfy the bound, mark the
privileged discovery stage unavailable rather than launch unbounded work. Bound concurrent and
orphaned operations; document that a userspace timeout cannot guarantee termination of a driver
stuck in uninterruptible kernel I/O. [DESIGN; local feasibility from `01-PATTERNS.md`]

### 5. Cache Evidence, Not Permission To Write

Cache keys include schema/catalog version, normalized device/driver identity, kernel release,
OS build context, boot/session generation, privilege generation and candidate semantic version.
Runtime path disappearance/type/unit changes invalidate just the affected feature and dependents.
Successful discovery has a bounded TTL; denial/missing/malformed failures have typed negative TTLs.
A session/privilege change invalidates denial immediately; manual retry is rate-limited and does
not bypass budgets. Do not persist root grants or classify cached readings as live. [DESIGN]

Use monotonic elapsed time within a boot and explicit nullable timestamps. Across reboot or
inconsistent/future timestamps discard freshness. Cancelled/partial jobs cannot overwrite a
complete snapshot or become permanent negative evidence. Atomic storage replaces an entire
validated cache record; cache corruption means safe recomputation, never restored write authority.
[DESIGN; ADR-07/23/33]

### 6. Support Report Is The Final Optional Fallback

Only after the known stage and all applicable safe discovery steps reach an explicit terminal
outcome may Settings suggest a report for unresolved features. Budget exhaustion and denied
access are explained distinctly; cancellation offers resume/retry and is **not** completed
exhaustion. Permission recovery can stop safely without pressuring users to grant root. Success
in one domain remains usable when another is unresolved. [DESIGN; D-03/D-05; CONTEXT specifics]

Use a versioned allowlisted report schema, not a zip of arbitrary logs/files. Include catalog/app
version, limited public hardware/kernel/build descriptors, per-feature reason, candidate/source
IDs, units/representation, timing and budget summaries, access category and selected structured
app diagnostics. Raw values/paths need bounded, known semantic fields; defaults exclude exact
build fingerprints or identifying free text. Maintainers may need additional public build
context, but the user must preview and explicitly select it. [DESIGN]

Exclude serial/IMEI/Android ID/MAC, account data, package inventories, foreground-app history,
full getprop/env, boot arguments, arbitrary logcat/dmesg/pstore, root configuration, keys/tokens,
user files, precise timestamps where unnecessary and unbounded exception strings. Hashing a
stable identifier is not anonymization; omit it rather than hash-and-export. Sanitize before
persistence and before preview, not only at the final share call. [DESIGN; O01]

Use app-private storage excluded from backup as appropriate, a dedicated narrow FileProvider
subdirectory, `content://` URI with temporary **read-only** grant and the user's Android chooser.
No clipboard-by-default, automatic upload, preset recipient, account, server or model. Define
retention/cleanup and handle no share target, cancellation, process recreation and stale URI.
Once a user shares, the receiver can retain a copy; local deletion cannot retract it. [DESIGN;
VERIFIED: A05 temporary-grant contract]

Maintainer loop: intake sanitized evidence -> reproduce the parser/decision failure in a fixture
-> identify documented or source-reviewed interface semantics -> add the smallest provider/catalog
change -> unit/architecture/privacy checks -> real-device read-only confirmation -> independent
safety review -> normal reviewed app/module release. Catalog rollback/rejection is versioned
ordinary release work; no runtime downloaded executable or authority-bearing support pack.
[DESIGN; D-05/D-06]

## Don't Hand-Roll

| Problem | Do Not Build / Copy | Use Instead |
| --- | --- | --- |
| Hardware ownership and mutation | Atlas writer, competing policy engine or new lease system | Existing canonical control plane unchanged [D-08] |
| Privilege acquisition | Vendor binder exploit, chmod unlock, Shizuku shell service | Existing app-level access state; explicit unavailable result [D-08/deferred] |
| Units and sensor identity | Magnitude guessing, thermal index table, CPU-load-from-frequency | Reviewed ABI/driver parsers and unknown/ambiguous results [L02-L06, S04] |
| Discovery | Shell-generated arbitrary commands, recursive scans, write-to-see-if-it-works | Reviewed read-only operations with transport-enforced limits [S06, L01] |
| Package architecture | Importing Unity loaders, Linux daemon or upstream path corpus | Small independently written provider seam over existing app stack [D-02/D-06] |
| Sharing | Broad exported provider, raw filesystem URI, custom upload service | Android content URI + narrow FileProvider + user chooser [A05] |
| Persistence | New database/dependency by default, custom encryption | Existing serializers/storage; minimize data and use platform protection [D-06, O01] |
| Support distribution | Remote scripts, hot-loaded control definitions | Reviewed fixture/provider/catalog change in ordinary release [D-05] |

## Common Pitfalls

| Failure Mode | Evidence / Why | Required Mitigation |
| --- | --- | --- |
| Treat every upstream probe as harmless | ACC tests switch charging; Calibrate has separate no-op write probes [S02/S06] | No mutation calls reachable from discovery, including existing helpers |
| Conflate write accepted and write verified | Calibrate RootWriter returns from shell success [S02] | Atlas issues no writes; preserve distinct backend execution evidence |
| Missing value becomes hardware/table bound | Calibrate current-cap fallbacks [S02] | Independent fields for table/range/current state; unknown stays null |
| GPU identified by first devfreq/name token | Kelvin/FusionHUD use heuristic candidates [S03/S04] | Driver/path/format corroboration; ambiguity retained; no writer promotion |
| MHz selected from magnitude | FusionHUD source [S04] | Exact source-unit contract; reject unknown scale |
| Memory usage relabeled as PSI | Thrawl documentation offers heuristic fallback [S08]; binding ADR-34 distinguishes them | Separate signal identities; preserve existing policy semantics |
| Generic becomes universal guarantee | Kelvin requires cpufreq; Android access restrictions [S03/A04] | Per-feature unsupported/denied, including a functional no-root journey |
| Strong vendor match hides good generic features | Kelvin/Unity choose global first successful provider [S03/U01] | Per-feature merge/selection; do not downgrade unrelated successes |
| Timeout masks an immortal root task | Blocking transport can outlive caller [DESIGN risk] | Transport cancellation/bounds + generation fence + concurrency cap |
| Success cache outlives OTA/root change | ABI/access context changes [L01, DESIGN] | Context-key invalidation, monotonic TTL, negative retry and runtime revalidation |
| Report after cancellation | Cancellation is not safe-stage exhaustion [CONTEXT] | Separate states and UI eligibility tests |
| Report includes all logs because upstream does | Thrawl unfiltered logs; MAGNETAR report routing [S08/S11] | Allowlisted fields, redaction before storage, preview/consent, no network |
| License badge becomes clearance | Apex missing license; Fusion supplemental terms; MAGNETAR restricted license [S04/S05/S11] | Reference-only design, provenance log, separate legal review for any future copying |

## Code Examples

These are **independently authored pseudocode contracts**, not copied upstream code or existing
local API names. Convert them to peer-confirmed project conventions during planning. [DESIGN]

```text
resolveFeature(feature, context, budget):
    known = validateObservations(readReviewedCandidates(feature, context, budget))
    if known.resolved:
        return known
    if cancelled:
        return Cancelled(partial = known)
    discovered = validateObservations(readAllowedAlternatives(feature, context, budget))
    return chooseEvidenceOrReason(known, discovered)
    # Never: write, chmod, service stop, trial value, backend path registration.
```

```text
observation = {
    candidateId, sourceId, providerId, catalogVersion,
    rawRepresentation, declaredUnit, parsedValueOrNull,
    driverEvidence, semanticStatus, accessResult,
    observedAtElapsedOrNull, bootGeneration, privilegeGeneration,
    reason, truncated
}

if semanticStatus != REVIEWED_MATCH:
    controlEligibilityFromAtlas = NONE
# Even REVIEWED_MATCH supplies evidence only; existing authority is unchanged.
```

## Validation Architecture

All values and test names below are **planning proposals**, not measured performance or existing
tests. `workflow.nyquist_validation` and `security_enforcement` are true in this phase config.
No local product test suite or hardware validation was run by this external researcher.
[CITED: `.planning/config.json`; DESIGN]

### Test Framework And Commands

| Property | Plan |
| --- | --- |
| Framework/version | Existing JVM/Android frameworks, exact version and fixture conventions from `01-PATTERNS.md`; no new package assumed |
| Config | Existing manager Gradle/test configuration; do not create parallel configuration |
| Focused command, after tests exist | From `manager`: `bash gradlew :app:testDebugUnitTest --tests '*Atlas*'`; planner must verify source set and actual test class names |
| Full repository checks | `python3 tools/code_health.py --assert`; `python3 tools/i18n_coverage.py --assert`; `python3 tools/repo_audit.py`; `git diff --check` |
| Android build/device checks | Use authorized commands from `docs/ai/VALIDATION.md`, not an upstream project's build scripts; report separately from static checks |

The project preflight records `code_health` failure solely for owner-supplied `txt.txt`; do not
delete the input or weaken the gate to claim green. The coordinator/local mapper owns current
gate reproduction and environment readiness. Historic toolchain/test counts in HANDOFF are not
current execution evidence. [CITED: `.planning/PROJECT.md` baseline]

### Requirement-To-Test Map

| Requirement | Test / Fixture Design | Verification Layer |
| --- | --- | --- |
| ATLAS-01 | Catalog schema/provenance/unit/risk validation; reject unknown authority-bearing fields; all 17 research references accounted for | Pure JVM + source/manifest review; fixtures to add |
| ATLAS-02 | Unknown vendor with valid cpufreq; vendor GPU miss retains generic CPU; provider exception isolated | Pure JVM provider contract |
| ATLAS-03 | Recorded read trace proves known stage before discovery per unresolved feature; successful feature not re-probed; report not prematurely suggested | JVM state machine + Settings UI |
| ATLAS-04 | Read-port-only dependency graph; spy rejects any writer invocation, chmod, service command or backend registry mutation; existing control contract regression | Architecture scan + behavioral test + independent safety review |
| ATLAS-05 | Denied vs absent, read-only vs unavailable backend, malformed/ambiguous/stale, Hz/kHz/MHz explicit units, negative/overflow/NaN, thermal type conflicts and energy/charge dimensions | Pure parsers + synthetic filesystem |
| ATLAS-06 | Positive/negative TTL, zero/future/backward clock, reboot/build/catalog/privilege change, corrupt cache, path removal, late completion after cancellation | Fake monotonic clock + I/O/cache tests |
| ATLAS-07 | Per-domain matrix of observed/unsupported/denied; no ADPF other-app control; new source cannot register writable path; existing control outcomes retained | Domain contract + real-device read checks |
| ATLAS-08 | Progress/retry/terminal reasons; cancellation not exhaustion; no-root access to Settings; paired EN/AR, RTL, TalkBack, large text, rotation | UI/unit + resource gate + device/emulator |
| ATLAS-09 | Seed secrets/IDs/package names in every raw field and exception; assert removed before disk/preview/share; narrow URI/read grant; no network request; expired/no-target share | Pure sanitizer + Android instrumentation |
| ATLAS-10 | Sanitized report -> deterministic regression fixture -> narrowly matched catalog/provider change; previous catalog rejection/rollback; unsupported version | Maintainer workflow dry run without publication |
| ATLAS-11 | Synthetic Qualcomm/MediaTek/Exynos-Tensor/unknown/no-root matrix; explicit hardware checklist and unchanged safety invariants | Tests + independent review; fixtures are NOT hardware qualification |
| ATLAS-12 | Oversized directory/value, symlink cycle/escape, output flood, slow/blocked reader, denied namespace, repeated retry, concurrent requests, cancellation during each stage | Budget/transport tests + device timing |

### Budgets And Cache Values

Use these initial **design values**, subject to explicit planner adoption and later measurement.
They are not Linux constants, benchmark results, safety thresholds or compatibility promises.

| Resource | Proposed Bound | Enforcement / Expected Test |
| --- | --- | --- |
| Job | 8 seconds total foreground discovery, including known stage | One monotonic deadline; early terminal budget result; report exact stage/partial coverage |
| Known stage | Up to 2 seconds within total, unused time available to deeper stage | Known candidate order deterministic; no repeated whole-device work |
| Single I/O | 250 ms logical deadline; bounded transport cleanup separately tracked | Slow reader cannot publish after generation cancelled; stuck worker prevents more launches |
| Enumeration | 128 entries per root, 512 entries/attributes visited globally | Bound during iteration; mark truncated coverage, never label unvisited entries absent |
| Attribute bytes | 4 KiB for scalar/list metadata, up to 16 KiB only for explicitly reviewed proc summaries | Read cap+1 to detect truncation, reject partial parse; support 16 KiB-page Android by explicit larger family allowance if needed |
| Aggregate input | 256 KiB per job including command output/errors | Enforced while consuming; no giant intermediate string |
| Concurrency | One coalesced job, at most two read operations; at most one privileged operation | Duplicate callers observe one state; cancellation cannot create unbounded orphan workers |
| Positive evidence cache | 10 minutes maximum, invalidated by context/runtime changes | Does not extend telemetry freshness or grant writes |
| Negative cache | 60 seconds missing/malformed; denied until privilege change or 5 minutes | Explicit manual retry once per 10 seconds; no loops after persistent denial |
| Thermal headroom observation | Shared no-faster-than-10-second design cadence if used | Reuse existing sample; NaN not replaced; do not add an Atlas telemetry polling loop |
| Report size/retention | 256 KiB serialized report maximum; one current export plus at most two retained, cleanup after 24 hours/on next app start | Private non-backed-up location; sanitized immutable preview equals shared bytes; never delete a live share immediately |

The phase is discovery, not continuous monitoring: run on explicit retry/context invalidation and
reuse valid evidence. Let existing collectors own live sampling. Measure actual time, I/O count,
bytes, cancellation cleanup and main-thread work on devices; tighten budgets when possible,
document any change before widening them. [DESIGN]

### Device Checks

- Test unknown vendor and no-root permission-denied devices as first-class successes of degradation, not as failed installations. Confirm Settings/retry/report remains reachable without adding privilege transports.
- Test Qualcomm, MediaTek and Exynos/Tensor configurations; include missing GPU tables, numeric devfreq directory names, multiple GPU-like domains, custom kernel, different Android versions, locked-down SELinux and changed privilege mid-job.
- Compare read-only reported topology/units/sensor identity with the actual device's exposed ABI and vendor documentation. Record app/catalog/kernel/build/privilege context, timestamp, observed limits and unresolved items without personal identifiers.
- Verify repeated discovery causes **zero mutation operations** in the read transport; before/after node values alone are insufficient because kernel clocks and temperatures naturally change. Instrument call boundaries in tests and review reachable operations on-device.
- Check suspend/resume, process death, rotation, cancellation, permission withdrawal, no share target, Arabic/English, RTL, TalkBack and large font. Capture budget measurements and report preview/share equality.
- Hardware control validation, if separately authorized for existing controls, belongs to the existing control-plane safety protocol with baseline/readback/rollback. Never perform trial writes for this discovery validation.

[DESIGN; ATLAS-11/12]

### Sampling And Wave-Zero Gaps

Run focused deterministic tests after each behavior task, the full applicable gates at each
integration wave, and a real-device/read-only matrix plus independent safety review before
claiming phase completion. A sub-30-second warm focused suite is a target, not a measured runtime;
Gradle startup/build cost must be reported separately. [DESIGN]

Wave zero must supply the read-only fake/spy, controllable clock, budgeted slow/flooding transport,
candidate fixture schema, privacy-canary fixtures and a runnable focused test command. Exact files
and test framework already present are established by `01-PATTERNS.md`. Do not assume a class
exists because it is named here. Requirements remain pending until implementation and verification,
not because research or plans exist. [DESIGN; `.planning/REQUIREMENTS.md`]

## Security Domain

Config requests ASVS level 1/high-severity blocking, but this is an Android local privileged
utility, not a newly built web backend. Do not claim ASVS compliance from a research checklist.
The ASVS website fetch failed in this run; exact ASVS version/control-number mapping remains for
the security checker. Official mobile MASVS taxonomy was fetched and is relevant as supplemental
guidance, not a replacement for binding ADRs. [CITED: phase config; VERIFIED: O01]

| Concern | Applicable Control / Threat | Design |
| --- | --- | --- |
| Authentication/session | No new account/network session | Preserve existing app/privilege session; do not invent login/token storage |
| Authorization | Spoofed capability, heuristic escalation, raw report command | Atlas cannot write or install support; evidence never carries executable authority |
| Input validation | Malformed pseudo-files, shell injection, path traversal, huge values | Typed bounded I/O, allowlisted namespaces/attributes, no raw shell interpolation |
| Storage/privacy | Secrets in logs/cache/backup/share | Data minimization before storage; narrow private directory and selective sharing |
| Platform IPC | Exported provider or writable/broad URI | Non-exported FileProvider, temporary read grant, one sanitized artifact |
| Availability | Hung driver/shell, recursion, denial loops | Budgets, coalescing, transport cleanup, bounded workers, generation fence |
| Cryptography/network | New upload/signing/encryption code | None required for Atlas; existing release signing unchanged; no custom crypto |
| Supply chain | Remote control pack/plugin | Bundled versioned non-executable catalog, review, fixtures and ordinary release |

[DESIGN; A04/A05/O01]

## Evidence Uncertainties

1. **No hardware proof.** All external device, performance and passing-test claims remain upstream claims. Targeted source inspection only establishes the branches described in `01-SOURCES.md`.
2. **No unconditional safety exemplar.** Calibrate's non-interference/thermal safety and Apex's transactional statements were not comprehensively audited; a Calibrate root path counterexample is source-verified. Kelvin PPM syntax still needs target vendor-kernel evidence before any future mutation work.
3. **API documentation access.** Android Developers and source.android.com guides timed out; authoritative AOSP Java/Javadoc and SELinux sources were fetched instead. SDK introduction levels/latest guide recommendations need rechecking against the project's actual supported SDKs before coding.
4. **Licensing is incomplete.** Apex README promises a LICENSE yet API detects none; Fusion has additional terms; MAGNETAR CC BY-NC-ND is confirmed in its actual license. Other full license/dependency histories were not audited. No legal clearance is claimed.
5. **Local baseline belongs to the peer.** Do not infer current app APIs or toolchain readiness from older HANDOFF prose or from the external projects. Read `01-PATTERNS.md` before fixing file allow-lists and command names.
6. **Design budgets are unmeasured.** Kernel/hardware/SELinux behavior, blocking read cleanup and performance targets need device evidence. Reference docs cannot prove those outcomes.

## Assumptions Log

| ID | Assumption / Unverified Input | Required Planner Action |
| --- | --- | --- |
| A-LOCAL | Exact local integration APIs, versions, test classes and sharing configuration are intentionally not researched here | Use peer `01-PATTERNS.md`; do not add a duplicate root/session/controller abstraction |
| A-BUDGET | Proposed deadlines, byte limits, TTL and retention balance usefulness/cost | Adopt explicitly as design values, test limits, then measure on devices |
| A-VENDOR | Some vendor interfaces may deviate from mainline units or node syntax | Unknown/read-only until narrow source/ABI validation; no trial writes |
| A-IDENTITY | URL-less txt.txt names resolve to selected projects in the source matrix | Keep identity confidence explicit; do not cite them as exact original revisions |

No assumed hardware capability is a prerequisite for planning: unresolved/denied/read-only is
the designed fallback, so these uncertainties do not block research completion.

## Recommendations For Executable Plans

Use the peer's exact local file map to turn these units into exclusive allow-lists. Names below
are **suggested artifact responsibilities**, not claims that local classes already exist. [DESIGN]

| Unit | Required Outcome | Files / Boundaries To Resolve From Peer | Requirements |
| --- | --- | --- | --- |
| 1. Contracts/catalog | Versioned evidence/result/provider/catalog contract; reference provenance and no authority-bearing fields | Minimal additive Atlas domain files and catalog beside existing core capability code; pure tests | 01,02,04,05 |
| 2. Bounded read transport | Typed errno/cancellation/truncation, path/symlink guards, monotonic budgets and mutation spy | Narrow read abstraction/implementation, not mutation APIs or global shell behavior rewrite | 04,05,12 |
| 3. Generic/vendor observations | CPU policies, GPU identity/units, thermal roles, memory/power dimensions; support matrix | Reuse existing domain readers only where peer proves read-only; small adapters/fixtures | 02,03,05,07 |
| 4. Coordinator/cache | Per-feature known -> discovery -> terminal states, partial progress, invalidation, retry and DI | Existing app-level state/DI wiring plus Atlas cache/coordinator; no Max AI policy change | 03,04,06,12 |
| 5. Private support loop | Sanitized report schema, retention, preview model, narrow URI share and fixture intake recipe | Existing diagnostics/sharing seams, app-private export, narrow manifest/XML changes only if required | 09,10,11 |
| 6. Settings UX | Existing Settings/Diagnostics entry, progress/reasons/retry/final fallback, no-root journey and accessibility | Existing Settings/Diagnostics/navigation entries; existing design system; paired EN/AR and Crowdin | 03,08,09 |
| 7. Validation/closure | Deterministic fixtures, no-write evidence, device protocol, independent review, truthful gate report | Existing tests/tools; coordinator updates canonical HANDOFF only, not a second executor log | 01..12 |

Do not write plans that close ATLAS-07 merely by finding more sysfs paths. Require a per-domain
observation/backend-support matrix with explicit no-root/SDK/vendor limitations and unchanged
control authorization. Do not close ATLAS-10 by adding a share button alone: demonstrate one
sanitized report becoming a narrowly matched regression fixture and a reviewable support change,
without actually publishing in this session. [DESIGN]

<phase_requirements>
## Phase Requirements

| ID | Research Support |
| --- | --- |
| ATLAS-01 | All-reference matrix, provenance/risk/unit schema, independent implementation rule |
| ATLAS-02 | Per-feature generic/vendor/platform provider seam, conditional support |
| ATLAS-03 | Ordered resolution state machine and final optional report eligibility |
| ATLAS-04 | Read-only dependency boundary, no backend promotion, unchanged control authority |
| ATLAS-05 | Orthogonal evidence states, official unit/identity contracts, no synthetic telemetry |
| ATLAS-06 | Context-keyed positive/negative cache, TTL, invalidation and cancellation fences |
| ATLAS-07 | Platform limits/domain matrix and explicit ADPF non-cross-app boundary |
| ATLAS-08 | Settings-only integration, transparent state/retry/no-root UX, EN/AR/accessibility |
| ATLAS-09 | Allowlisted private report, preview/redaction/consent and narrow local sharing |
| ATLAS-10 | Maintainer fixture/review/normal-release and catalog rejection/rollback workflow |
| ATLAS-11 | Deterministic scenario matrix, device checks and independent safety gate |
| ATLAS-12 | Explicit design budgets, bounded transport/enumeration, clean cancellation |
</phase_requirements>

## Sources And Metadata

The complete [source matrix](01-SOURCES.md) is the provenance authority for IDs S01-S18,
L01-L06, A01-A05, U01 and O01. Official ABI/API contracts and targeted source are strongest for
their stated semantics; project README feature/test/device claims remain weaker. `txt.txt` is
an unverified inventory to falsify, never implementation authority.

**Research validity:** recheck moving upstream sources before implementation; pinned revisions
remain reproducible evidence of those versions, not permanent claims about current projects.
**Environment:** live HTTPS/GitHub/Gitiles available; some documentation endpoints timed out;
no new runtime/package dependency required for research. Build and hardware execution not done.
**Changed files:** this research and its source matrix only. Local source map is peer-owned.
**Outcome:** RESEARCH COMPLETE, with explicit evidence/compatibility/legal uncertainties;
ready to produce executable plans after integrating `01-PATTERNS.md`.
