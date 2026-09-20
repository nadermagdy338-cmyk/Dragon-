# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-20)

**Core value:** Broader compatibility with evidence, not speculative hardware control.
**Current focus:** Phase 1 - Max Atlas compatibility and safe discovery

## Current Position

Phase: 1 of 1 (Max Atlas compatibility and safe discovery)
Plan: 10 executable (P0-P9) + 4 proposed (P10-P13) = 14
Status: 12 fully delivered (P0, P1, P3, P4, P5, P6, P7, P8, P9, P10, P12, P13) · 1 partial
(P2 without T2.5) · 1 not started (P11)
Last activity: 2026-09-20 - `P9.1` (fixtures) and `P13` (the Atlas doctor) delivered: a real run is now
replayable data, so "needs device" stops being permanent; focused scope 21 classes / 279 tests green,
four gates green.

Progress: [=========-] 86% (12/14 fully delivered · 1 partial · 1 not started)

## Accumulated Context

### Decisions

Binding ADRs remain in docs/ai/DECISIONS.md. Phase inputs live in 01-CONTEXT.md.
Max Atlas is the product name selected under the owner's naming discretion.
No commit, push or release is authorized in this planning run.
Owner directive of 2026-09-20 (AGENTS.md section 0.1) also applies to execution: do not build by
default; compile only on request and with the narrowest task.

Ten executable plans exist in 01-PLAN.md (P0-P9, dependency-ordered, exclusive allowlists), and four
proposed plans in its section 18 table (P10-P13).

**Delivered and measured.** The last full run is
`:app:testReleaseUnitTest` + `:app:assembleDebug -x lintVitalRelease` = **BUILD SUCCESSFUL 4m51s,
1103 tests, 0 failed / 0 errors / 0 skipped, APK 121,071,768 bytes** (was 1070 before the fixture and
doctor batch). Inside it, the Atlas and diagnostics scope is **22 classes / 306 tests / 0 failing**, and
the focused `:app:testDebugUnitTest` run over the same classes is **21 classes / 279 tests / 0 failing**.
The only compiler warning in either run is the pre-existing `MemoryStallTest.kt:122` (out of scope,
untouched); every touched file is warning-free. `code_health`, `i18n_coverage` (76/76 AR parity,
0 obstacles), `kt_balance` (706 files, 0 obstacles) and `repo_audit` are green.

- **P0/P1** — the frozen vocabulary and the provenance-bearing seed catalog. Amendment A-1 records that
  they landed together.
- **P2** — the boundary is `core/hardware/ReadOnlyProbeAccess.kt` over an injected `AtlasReadTransport`
  (so the fake only fakes I/O and the tested rules are the shipped ones), with path discipline before any
  call, budgets checked before work, causes preserved, absence only from an enumeration this job
  performed, and no mutating operation anywhere. `T2.4` (concurrency cap) is admission that is
  increment-then-check with rollback, because check-then-enter let a flood through.
- **P3** — `AtlasBackendProvider` bridges the existing CPU/GPU parsers into Atlas without a second read;
  a read seam was added to `CpuHardwareBackend`/`GpuHardwareBackend` without touching any existing
  writer, and a test proves a read cannot write. Diagnosis exposed a real defect: the GPU parser accepted
  only pre-trimmed text, so any other reader would silently lose the clock — the parser was fixed, not
  the test. (184 tests green at the time.)
- **P4** — `core/atlas/AtlasPlatformProvider.kt` (pure, injected `AtlasPlatformSource`) builds a support
  matrix that refuses to exist without a row for every domain, so a domain with no backend stays visible
  as deferred with a reason code. Thermal scales come from the source, never from magnitude; a zone's
  `ap`/`tsens` token never claims a CPU junction; battery dimensions stay four separate units with current
  polarity intact; malformed PSI keeps its raw text and yields no value; every `zram*` device is
  observed; network rows carry state only; privilege is unavailable unless the control plane verified
  the identity.
- **P5** — `AtlasResolver` (ordered stages; cancellation is rethrown, never reported as exhaustion),
  `AtlasEvidenceStore` (explicit-field JSON, atomic write, corrupt file is dropped and the scan redone),
  `AtlasRepository` (one coalesced scan; cancellation publishes immediately and outranks a late answer),
  `AtlasFileReadTransport` and `AtlasFileStoreIo`. **23 of these tests run against the real host
  filesystem and real syscalls**, not a fake — they resolve symlinks, read real files and measure
  `canonicalPath`. That real run exposed a coordinate defect: `canonicalPath` returned host-rooted paths
  while reads expected device paths, so the same file was looked up with a doubled prefix; the transport
  was fixed. (227 tests green at the time.)
- **P6** — `AtlasSupportReport` (minimized, numbered schema; refuses what it cannot parse; 256 KiB cap
  checked before the file exists; the preview is the bytes that get shared), `AtlasReportExporter`
  (atomic write, age-based pruning, no network or upload path) and `DiagnosticCenter.structured()`
  (a projection that has no message field at all). Canary tests cover every raw field and every
  exception.
- **P7** — `ui/viewmodel/AtlasViewModel.kt` with `AtlasPresentation` as pure rules tested separately
  (an unknown total is `null` and never `0%`; a one-shot read is `Snapshot`, never `Live`; there is no
  `Applied` state in the enum at all; cancellation is not exhaustion; a candidate reading is never
  counted as reviewed), and `ui/mainscreens/AtlasDiagnosticsSection.kt`, mounted by `DiagnosticsScreen`
  as its own item outside every module-loaded gate. The report flow is preview (frozen bytes) →
  confirmation → system picker, with no clipboard shortcut.
- **P8** — Atlas is reachable without root and without the native module: the Diagnostics destination is
  registered and the section performs no reading of its own. Verified at source and render level only;
  "one tap opens the screen" stays `needs device`.
- **P9.1** — `core/atlas/AtlasFixture.kt` and `AtlasFixtureRecorder.kt`: the byte-level answers one real
  run received, as data, replayed through the *shipped* transport interface. An unrecorded path answers
  `UNKNOWN_CAUSE` (never `ABSENT`), absence is still proven only from an enumeration this run performed,
  and the origin (`DEVICE`/`HOST`/`SYNTHETIC`) is recorded because a host capture proves the machinery
  and never the phone. `AtlasFixtureRecorderTest` runs the same three requests twice — once over the
  shipped `AtlasFileReadTransport`, once over the fixture captured from it — and asserts the two result
  lists are equal object by object.
- **P9.2** — `AtlasArchitectureTest` is the architecture guard (now 17 package files, both new files read).
- **P13** — `core/diagnostics/AtlasDoctor.kt`: does a replayed device still reproduce the report it came
  from? Pure, with `Reproduced`/`Diverged`/`Refused`. An unfinished or cancelled replay is refused rather
  than compared; a different catalog revision is refused; a missing *candidate* interface is returned as
  advice and never as divergence, so a name the community knows being absent cannot open a support loop.
  The outcome vocabulary is now published by `AtlasSupportReport` and imported, instead of copied.
- **P10** — `core/atlas/AtlasDeviceIdentity.kt` derives the device from what the platform declares (no
  `import android`, so the model is pure); `AtlasVendorTags` reads reviewed aliases and model-prefix
  families with a minimum token length; `AtlasKernelRelease` has nowhere to put a platform level; and
  `AtlasAnchors.REVIEWED_FILES` approves one reviewed file (`/proc/cpuinfo`) instead of approving
  `/proc` as a root, so the catalog validator and the boundary ask one shared addressability question.
  Declared identity can only add a vendor candidate, never remove one, and a test proves it per domain.
- **P12** — `AtlasFreshness` (a clock that moved backwards is stale, because an unmeasurable age is not a
  young age) with `AtlasStaleness` carrying the reason — `EXPIRED_BY_TIME`, `SUPERSEDED_BY_BOOT`,
  `SUPERSEDED_BY_PRIVILEGE` or `UNMEASURABLE_CLOCK`, in a fixed precedence so a report names one cause —
  plus `AtlasFailureRetryPolicy`, `AtlasFailureLedger` (per-cause lifetimes, records dropped on a boot or
  privilege change, a record erased the moment the interface answers, `CANCELLED`/`STALE` never
  remembered) and `AtlasProbeScheduler` (deterministic cheapest-first plan over the same budget P2
  enforces). **`AtlasFailure.STALE` still has no emitter**, and P12 is not the plan that should add one:
  the ledger documents that staleness is not a read failure, so the cause belongs to P5's resolver. An
  earlier sentence in these documents claimed otherwise and was corrected.
- **Automatic completion (owner directive of 2026-09-20)** — the second bank, `AtlasCommunityBank`:
  **50 interfaces** passing the same validator as the reviewed catalog, fetching nothing from the named
  upstream projects (S01/S09 are recorded in `provenance` as an inventory, with their licence state) and
  never exceeding `CLAIMED`/`FETCHED` confidence — no entry is `SOURCE_VERIFIED`. Reading from it is
  `INFERRED`, in stage `CANDIDATE_INTERFACE`, and is never promoted. It is asked only for contradiction:
  only domains the first bank did not answer, never re-reading an interface already read, capped at 32
  candidates. **ADR-37** records the decision and the rejected alternative (copying SmartPack's path map:
  mixing GPL-3.0 with Apache-2.0, "guessing on failure", and turning a missing name into a missing
  interface).

**One catalog defect found before any new code was written.** **12 of the 15** reviewed entries addressed
a class directory as if the file sat directly inside it (`/sys/class/devfreq/cur_freq`,
`/sys/class/thermal/temp`, `/sys/class/power_supply/charge_full`, `/sys/class/kgsl/…`,
`/sys/devices/system/cpu/cpufreq/scaling_cur_freq`, `/sys/block/disksize`). On any real device those
directories hold *devices* and the interface sits one level lower, so the paths pointed at a file that
cannot exist while the interface they describe exists and is readable. Nothing in the rules rejected that
shape and no test saw it: the strings exported cleanly and resolved to nothing. The fix is a rule rather
than twelve edits — `AtlasCatalogScope` (`ROOT_FILE` / `CHILD_FILE`) plus an optional `childPrefix` (a
name fragment compared against names the kernel returned, **not** a pattern or glob) — and a new
`AtlasAnchors.ENUMERABLE` grant.

**Atlas is no longer unreachable.** The earlier "zero call sites" finding is resolved: ten files outside
`core/atlas/` now name it and the UI reaches the repository through `AtlasViewModel` (Hilt-injected) →
`AtlasDiagnosticsSection`. "Delivered" for P7/P8 means source- and render-verified, not device-verified.

Six decisions remain open in 01-PLAN.md section 15. DECISION-1 (a single reconciled budget set, because
01-RESEARCH.md proposes 8 s/250 ms/256 KiB while 01-PATTERNS.md proposes 12 s/1 s/512 KiB) is
**implemented as recommended** in `AtlasReadBudget.DEFAULT` — one place, with the test harness delegating
to it — and is still waiting for the owner's confirmation; every value remains unmeasured on hardware.

### Blockers/Concerns

- **Resolved, reported factually:** the preflight `code_health` failure caused by the owner-provided
  `txt.txt` no longer reproduces. That file is absent from the working tree and
  `python3 tools/code_health.py --assert` exits 0. This planner did not delete it and did not touch any
  ignore rule or gate. Any later need for its exact wording must ask the owner for the file again.
- **Hardware compatibility and runtime safety cannot be proven by planning artifacts, and not by this
  build either.** There is no device and no emulator in this environment (`adb` and the emulator binary
  are absent; SDK is installed at `~/android-sdk`, `ANDROID_HOME` is unset by default). The build proves
  "it compiles and the tests pass", never "it works on your phone". Everything that matters on hardware —
  which interfaces a vendor kernel actually exposes, the Adreno/MTK candidate units recorded as `CLAIMED`,
  and the tap that opens the screen — stays `needs device`.
- **Independent safety review is still missing.** Configured historical model names are not selectable in
  this runtime; no review may be represented as GPT-5.6 Luna approval. `P2/T2.5` (the adapter over an
  existing authorized privileged transport) is deliberately unbuilt pending that review, and
  `UnavailableAtlasReadTransport` returns `BACKEND_UNAVAILABLE` meanwhile, so nothing is guessed at and
  nothing is claimed. `P2`, `P3` and `P4` remain `UNREVIEWED`.
- **No fixture from a real phone exists.** Every capture that exists today is a `HOST` capture made by
  the tests, labelled as such. Device coverage still begins with someone running the doctor on a device
  and keeping the file — it does not begin with a file this environment wrote.
- **`P11` is the one plan not started.** It is now unblocked in the only way that matters (there is a
  mechanism to build it on real reports rather than assumptions), which is all that is claimed.
- The defects these plans turned up were mostly *reasoning* errors that only execution could catch
  (non-atomic admission; a counting API that excludes vendor entries; a parser that accepted only
  pre-trimmed input; host-rooted `canonicalPath` against device paths; a flaky cancellation test that
  granted false confidence; a tool false positive where `\bText\(` matched `AtlasTransportRead.Text(`).
  All are recorded in the plan's changelog and in HANDOFF entries so a later plan does not repeat them.
- **Correction to a gap-review fact (2026-09-20):** G-01's first command did not reproduce — it returns
  8 files, not "nothing". The claim that survives, and is now the one written down, was **zero call
  sites**; that has since been superseded by the P7/P8 wiring above. Fixed in `01-GAPS-AND-IDEAS.md`
  and plan sections 18/19.4.

## Session Continuity

Last session: 2026-09-20
Stopped at: `P0`, `P1`, `P3`, `P4`, `P5`, `P6`, `P7`, `P8`, `P9.1`, `P9.2`, `P10`, `P12` and `P13`
delivered and measured — the full release run is **1103 tests / 0 failed / 0 errors / 0 skipped** with the
debug APK produced, and the Atlas plus diagnostics scope inside it is **22 classes / 306 tests / 0 failing**
(the focused same-scope run is 21 classes / 279 tests / 0 failing), 0 build warnings from any touched
file, four gates green.
Next code step: `P11` (the quirk base), which can only *lower* confidence and must be built on real
reports — which now have a mechanism (`P13` + `P9.1`) instead of a guess. The only other open code item
is `P2/T2.5`, gated on an independent-family safety review that this runtime cannot provide.
Resume file: .planning/phases/01-max-atlas-compatibility-and-safe-discovery/01-PLAN.md (section 15
decisions, section 19 delivered work) and docs/ai/HANDOFF.md (تكملة ٥٣ and تكملة ٥٤).
