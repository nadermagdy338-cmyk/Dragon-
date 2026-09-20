# Phase 1: Max Atlas Compatibility And Safe Discovery - Execution Plan

**Task:** ATLAS-P1, planner artifact. **Written:** 2026-09-20 UTC.
**Inputs integrated:** `01-CONTEXT.md` (decisions D-01..D-09), `01-RESEARCH.md` (external/platform
contracts, budgets, validation architecture), `01-PATTERNS.md` (local source map, slices A-I),
`01-SOURCES.md` (provenance matrix S01-S18 / L01-L06 / A01-A05 / U01 / O01).
**Authority:** planning only. No product code, no commits, no builds, no signing, no release in
this run (`.planning/PROJECT.md` → Scope; `STATE.md` decisions).
**Verification at write time (measured in this session, not recalled):**

| Command | Observed result |
| --- | --- |
| `python3 tools/code_health.py --assert` | **exit 0**. Health 0/0/0/0 (`package_mismatch`, `unresolved_resource`, `duplicate_string_key`, `stray_root_file`). Debt unchanged: **10** oversized · **29** own wildcard imports · **66** hardcoded UI literals · **26** presentation hw writes (+2 todo, 233 platform wildcards not counted). Reports **477** Kotlin files / **109,630** lines. |
| `python3 tools/i18n_coverage.py --assert` | **exit 0** · 0 obstacles · 84 `values-*` + en · picker 85 · `locales_config` 85 · codes match |
| `python3 tools/repo_audit.py` | `PROBLEMS: 0` · 349 Kotlin files scanned · 2540 `R.string` refs · 3205 base strings |
| `python3 tools/kt_balance.py` | 657 files checked (Kotlin/XML) · **0** obstacles |
| `git status --short` | owner/coordinator planning changes only; no product file modified by this run |

**Baseline change since preflight (factual, not a claim about cause):** `PROJECT.md`, `STATE.md`,
`01-RESEARCH.md` and `01-PATTERNS.md` all recorded a `code_health` failure caused solely by the
owner-supplied `txt.txt` at the repository root. That file **is not present in the working tree
now** (`find` over the repository returns nothing) and `code_health --assert` exits 0. This planner
did not delete it, did not change any ignore rule, and did not weaken the gate. Consequence: the
"preserve the input" instruction cannot be satisfied by this run — the input is absent — and the
plan must not cite its absence as evidence about any upstream reference. Any later requirement that
depends on the inventory's exact wording must ask the owner for the file again.

---

## 0. How To Read This Plan

- Plans are labelled **P0-P9**; the letter in brackets maps to the exclusive candidate allowlist in
  `01-PATTERNS.md` "Small Plan Allowlists" (**A-I**). Where this plan adds a file that the pattern
  map did not name, it says so explicitly (allowlist revision).
- File prefixes follow `01-PATTERNS.md`: `APP/` = `manager/app/src/main/java/nd/max/`,
  `TEST/` = `manager/app/src/test/java/nd/max/`, `RES/` = `manager/app/src/main/res/`.
  **`NEW`** = file does not exist today and is proposed here; **`EXISTING`** = real file, edit
  narrowly with the described seam.
- An allowlist is **exclusive**: an executor may not touch a file outside its plan's allowlist.
  Moving a file between plans requires editing this file first, in the same change.
- Every plan is written to be executable **and independently verifiable**; a plan is not complete
  because its tests exist, but because its acceptance criteria were run and reported honestly.
- Status markers: **DELIVERED** = landed and measured in this repository; `[ ]` = not started.
  Current state: `P0` and `P1` are **DELIVERED** (2026-09-20, commit-less working tree); `P2`-`P9`
  are not started. No requirement is marked complete: `P1` produces contract-level evidence only
  (`ATLAS-01/02/04/05`) and `P0` produces harness evidence (`ATLAS-11/12`), which is partial by
  design until the domains, the resolver and the device protocol exist.
- **Amendment A-1 (2026-09-20):** `P0` and `P1` landed together rather than in two waves, because
  the harness must be typed on `P1`'s own vocabulary — a second, parallel test-only vocabulary is
  exactly the drift §2 forbids. `P0`'s allowlist also gained the test-support paths it named but the
  pattern map did not: `TEST/core/atlas/support/{AtlasBudgets,AtlasClock,AtlasFakeReadPort,AtlasCanaries,AtlasSourceGuard}.kt`.

---

## 1. Execution Order, Dependencies And Waves

```text
P0 (wave-zero harness, test-only)
  └── P1 (A: contracts + reviewed catalog)
        ├── P2 (B: bounded read-only transport)   [privilege boundary]
        ├── P3 (C: CPU/GPU reuse + facade projection)
        └── P4 (D: other domain observations)
              └── P5 (E: ordered resolver + evidence cache + DI)   requires P1+P2+P3+P4
                    ├── P6 (F: privacy + report + export)
                    └── P7 (G: Settings/Diagnostics UI)   (report UI needs P6)
                          └── P8 (H: no-root read-only entry acceptance)
                                └── P9 (I: fixtures + architecture guard + closure)
```

| Plan | Slice | Depends on | Blocks | Requirements |
| --- | --- | --- | --- | --- |
| P0 | wave zero | — | all | 11, 12 | **DELIVERED** |
| P1 | A | P0 | P2, P3, P4 | 01, 02, 04, 05 | **DELIVERED** |
| P2 | B | P0, P1 | P5 (privileged discovery) | 04, 05, 12 |
| P3 | C | P1 | P5 | 02, 03, 04, 07 |
| P4 | D | P1 | P5 | 05, 07 |
| P5 | E | P1, P2, P3, P4 | P6, P7 | 03, 04, 06, 12 |
| P6 | F | P5 | P7 (report block), P9 | 09, 10 |
| P7 | G | P5, P6 | P8 | 03, 08 |
| P8 | H | P7 | P9 | 08 |
| P9 | I | all | — | 01-12 (closure) |

**Why the order is not negotiable.** P1 defines the only vocabulary (observation/outcome axes) that
later plans may use. P2 is the single privilege boundary; P5 may not perform privileged discovery
before P2's transport acceptance exists. P6 must be able to freeze bytes before P7 offers a preview,
otherwise the UI would have to invent its own sanitizer. P9 is last because a closure artifact may
not be written against unverified plans.

**Execution granularity.** One executor per plan, one plan at a time inside a dependency chain; P3
and P4 may run in parallel after P1 (disjoint allowlists), and P6/P7 in parallel only after P5.

---

## 2. Global Constraints (apply to every plan)

1. **Read-only.** No writer, `chmod`, service stop/start, module install, trial value, backend
   registration or "write to test capability". Discovery must be provably write-free: fake-IO spies
   record every operation and assert zero mutations, in addition to source-level guards.
2. **No new dependency, no new module, no manifest/provider change.** `AndroidManifest.xml`,
   `RES/xml/file_paths.xml`, `manager/app/build.gradle.kts`, `gradle/libs.versions.toml` and
   `.github/workflows/**` are **read references, not edit permissions** (`01-PATTERNS.md`).
3. **Control plane untouched.** `APP/core/maxai/**`, `HardwareControlArbiter.kt`,
   `HardwareControlKey.kt`, `ManualControlLocks.kt`, `SharedHardwareOwnershipStore.kt`, native
   code, boot/module scripts, SELinux and signing are out of bounds. `HardwareCapabilityResolver.resolve(context)`
   keeps its current synchronous, control-facing behavior; its caller chain
   (`MaxAiEngine.decisionCycle → resolve → ControlRegistry.build`) must not change semantics.
4. **No synthetic truth.** Absent / permission-denied / read-only / malformed / ambiguous / stale /
   backend-unavailable / timed-out / budget-exceeded / cancelled are distinct outcomes. An
   unrecognized error is **unknown**, not `ENOENT`. Missing values stay `null`; no zero-filling.
5. **Cancellation is not exhaustion.** A cancelled or budget-stopped run is incomplete, never
   "all safe options exhausted", and never unlocks the support-report suggestion.
6. **Budgets are design values, not measurements.** Adopt the reconciled set in §7 (DECISION-1),
   enforce them in code, and label any change "unmeasured until device timing evidence".
7. **Localization and design primitives.** New user-visible copy goes to the existing
   `RES/values/max_screen_strings.xml` **and** `RES/values-ar/max_screen_strings.xml` in the same
   change (ADR-14), using `max_atlas_*` keys; the file is already registered in `crowdin.yml:10-11`,
   so no registration edit and no new locale file. UI uses existing `ui/design/` primitives only.
8. **One delivery log.** Every plan records its report in `docs/ai/HANDOFF.md` by the coordinator
   (template `docs/ai/VALIDATION.md` §8). No second executor log, no GSD-local log file.
9. **Gates per plan (cheap, no compiler):** `python3 tools/kt_balance.py`,
   `python3 tools/code_health.py --assert`, `python3 tools/i18n_coverage.py --assert`,
   `python3 tools/repo_audit.py`, `git diff --check`.
10. **Build policy.** Per owner directive in `AGENTS.md` §0.1 (2026-09-20): do **not** build by
    default. When a plan needs compilation, request it explicitly and narrow it:
    `:app:compileDebugUnitTestKotlin` (plus `:app:testDebugUnitTest --tests '*Atlas*'` once tests
    exist). Full `assembleDebug` / `testReleaseUnitTest` / R8 runs are separate, owner-approved
    events. Compilation unverified is reported as such, never as "passes".
11. **Independent safety review.** Any plan that touches a hardware seam (P2, P3, and any later
    edit inside `core/hardware`) requires review by a reviewer **from a different model family than
    the executor**, per `AGENTS.md` §2/§6. `STATE.md` records that the historically configured
    reviewer model is not selectable in this runtime: no review may be presented as that model's
    approval. If no independent-family reviewer is available, the plan stays open with an explicit
    `UNREVIEWED` marker rather than being closed.
12. **Device truth.** Fixtures prove parser/ordering behavior only. Any compatibility or safety
    statement about Qualcomm / MediaTek / Exynos-Tensor / unknown / no-root requires a real-device
    read-only run recorded in `docs/ai/HANDOFF.md` and `docs/ai/VALIDATION.md` scope.

---

## 3. Wave-Zero: Test Harness (P0) — **DELIVERED**

**Delivered evidence (2026-09-20):** `TEST/core/atlas/support/AtlasBudgets.kt` · `AtlasClock.kt` ·
`AtlasFakeReadPort.kt` · `AtlasCanaries.kt` · `AtlasSourceGuard.kt` · `TEST/core/atlas/AtlasHarnessTest.kt`.
Measured: `:app:testDebugUnitTest --tests 'nd.max.core.atlas.*'` = **BUILD SUCCESSFUL · 25 tests ·
0 failed · 0 errors · 0 skipped** (harness 10, catalog 15) · `kt_balance` 666 files / 0 obstacles ·
`code_health --assert` exit 0 · `repo_audit` `PROBLEMS: 0`. The negative control is real: the audit
rejects a probe that answers without recording, and a read slower than the operation deadline returns
`TIMED_OUT` with no partial value.

**Objective:** make every later plan testable without a device, without a new dependency, and
without any hidden shell.

**Allowlist (test-only; allowlist revision — paths not named in `01-PATTERNS.md`):**
`NEW TEST/core/atlas/support/AtlasTestFakes.kt` — fake read port that records operations and
declares no mutation method; `NEW TEST/core/atlas/support/AtlasClock.kt` — settable monotonic
clock; `NEW TEST/core/atlas/support/AtlasBudgets.kt` — the adopted budget constants single-sourced
for tests; `NEW TEST/core/atlas/support/AtlasFixtures.kt` — loader/validator for
`manager/app/src/test/resources/atlas/*.json`; `NEW TEST/core/atlas/AtlasHarnessTest.kt` —
self-test proving the fakes can fail (a spy that cannot detect a write is not a spy).

**Tasks**
- T0.1 Fake read port implements only the read operations of P1/P2; attempt to call a write must be
  a compile error, and a recorded-operation assertion must fail if a write-like path appears.
- T0.2 Controllable clock: explicit boundaries (first probe, exact retry boundary, backward jump,
  large forward jump, cancellation generation).
- T0.3 Budgeted slow/flooding transport: a reader that blocks past the deadline, one that floods
  more entries/bytes than allowed, and one that dies mid-iteration.
- T0.4 Privacy canary fixture schema: sentinel values (fake IMEI/serial/SSID/path/package) that
  later tests assert are absent from storage, preview and share bytes.
- T0.5 Document the runnable focused command **as a plan value, not a run**:
  `bash gradlew :app:testDebugUnitTest --tests 'nd.max.core.atlas.*'` (from `manager/`).
- T0.6 Harness self-test must fail when the spy is deliberately weakened (negative control).

**Acceptance:** harness self-test green on JVM; zero product files touched; no new dependency; the
focused command is recorded with the exact source set (`src/test/java`, not `src/testDebug`).

**Gates:** `kt_balance`, `code_health --assert`, `repo_audit` (all unchanged by test-only additions).

---

## 4. Plan P1 — Evidence Contract And Reviewed Catalog (slice A) — **DELIVERED**

**Delivered evidence (2026-09-20):** `APP/core/atlas/AtlasModels.kt` (362 lines) ·
`APP/core/atlas/AtlasCatalog.kt` (402 lines) · `TEST/core/atlas/AtlasCatalogTest.kt`.
Measured: 15 tests green · both files compile with **zero warnings** · the fail-closed source guard
reports **no** authority/transport token in either file (`writable` · `canWrite` · `controlEligible` ·
`submitControl` · `chmod` · `RootFileAccess` · `Shell` · `Shizuku` all zero) · seed catalog = **14
reviewed entries** over 8 domains, each carrying provenance. Two honest limits are recorded rather
than hidden: `featureTag` is `null` for every seed (the mapping to existing `HardwareFeature` ids is
`P3`'s job, not this plan's), and nested attributes such as `stats/time_in_state` are deliberately
**not** representable yet because the template grammar allows exactly one safe basename.

**Objective:** one immutable vocabulary for capability evidence, plus a versioned, provenance-bearing
catalog that can express *claims*, never *authority*.

**Allowlist:** `NEW APP/core/atlas/AtlasModels.kt`, `NEW APP/core/atlas/AtlasCatalog.kt`,
`NEW TEST/core/atlas/AtlasCatalogTest.kt`.

**Tasks**
- T1.1 **Observation model** with orthogonal axes, not one flat enum: existence, readability,
  access mode observed, freshness (`observedAtElapsed`, `bootGeneration`, `privilegeGeneration`),
  semantic confidence (`REVIEWED_MATCH` / `INFERRED` / `UNKNOWN`), unit, parsed value or `null`,
  provenance (candidate id, source id, provider id, catalog version, driver evidence), and reason
  code. Include the terminal causes from §2.4.
- T1.2 **No authority-bearing field.** The model must have no `writable`, `canWrite` or
  `controlEligible` field that could promote a path; control eligibility stays a derivation in the
  existing control plane. A test asserts the type has no such property and that a
  `REVIEWED_MATCH` observation cannot be turned into a write by any Atlas API.
- T1.3 **Catalog entry**: stable operation id, feature/domain tag, reviewed path template(s),
  declared unit and unit family, safety class, provider tag (generic / vendor / platform), and
  provenance: `sourceId`, reference URL, observed revision (from `01-SOURCES.md`), license note,
  and reference-confidence (F/R/S/N/D legend). Entries without provenance are rejected.
- T1.4 **Catalog validation:** duplicate ids, conflicting units for one path, malformed or
  absolute-traversal templates, authority-bearing keys and unknown schema versions are rejected, not
  coerced. Unknown vendor never blocks generic entries (`ATLAS-02`).
- T1.5 **Normalization helpers** (pure): CPU list/range parsing and alias resolution; frequency unit
  identity (Hz/kHz/MHz) kept **distinct** rather than scaled by magnitude; temperature scale
  identity (C / milli-C / deci-C); charge vs energy dimensions kept separate. No heuristics that
  upgrade an inferred unit to reviewed.
- T1.6 **Provenance ledger check:** a test asserts every catalog entry maps to an id present in
  `01-SOURCES.md` or is marked as an independently authored design choice (no silent imports).

**Acceptance:** pure JVM catalog tests green; every entry carries provenance; schema rejects all
negative cases; zero I/O in this plan; no file outside the allowlist changed.

**Requirements:** ATLAS-01 (primary), ATLAS-02, ATLAS-04 (no-authority field), ATLAS-05 (unit axes).

---

## 5. Plan P2 — Bounded Read-Only Transport (slice B) — **DELIVERED (T2.1–T2.4, T2.6; T2.5 open)**

**Objective:** a typed, budget-enforced, error-preserving read boundary — the only place in Atlas
that may touch a privileged transport.

**Allowlist:** `NEW APP/core/hardware/ReadOnlyProbeAccess.kt`,
`NEW TEST/core/hardware/ReadOnlyProbeAccessTest.kt`.

**Tasks**
- T2.1 Typed request (operation id, canonical path, max bytes, deadline) and typed result that
  preserves the cause class; no `Boolean`/`null`-only result that erases the difference between
  "missing" and "denied".
- T2.2 Path discipline: fixed anchor roots only; basename validation; no caller/catalog/report string
  in shell syntax (enumerate fixed parents and validate returned basenames — never
  `RootFileAccess.globDirectories`-style interpolation); symlink resolution cap (8) and canonical
  `/sys/devices/...` target policy; reject traversal and loop cases.
- T2.3 Budget enforcement with an injected monotonic clock: per-operation deadline, job deadline,
  128 entries per parent, 512 attributes globally, scalar 4 KiB, reviewed proc summaries 16 KiB,
  aggregate 256 KiB (read cap+1 to detect truncation; a truncated value is *rejected*, never parsed
  partially). Exceeding a budget produces `BudgetExceeded`, not a partial truth.
- T2.4 Concurrency cap: at most two read operations and at most one privileged operation; a stuck
  worker must not cause unbounded replacement workers.
- T2.5 Transport selection: reuse an already-authorized existing transport **only after** checking
  the current privilege/backend generation. Forbidden fallbacks: `Shell.getShell`,
  `RootService.bind`, `Shizuku.requestPermission`. If the available transport cannot preserve cause
  classes and enforce limits → return `BackendUnavailable` for that branch instead of silently using
  unbounded `RootFileAccess.read`.
- T2.6 Cancellation fence: cancel invalidates the in-flight generation; no publication of a value
  after cancellation; on-device evidence of descriptor/process cleanup where possible, and an
  explicit written residual that a blocked kernel syscall may outlive the coroutine.

**Acceptance:** tests distinguish denied vs missing vs read-only vs malformed, prove cap+1
truncation rejection, reject traversal/glob injection, cap symlink loops, stop a flooding reader at
the budget, and record **zero** mutations through the fake transport. Transport acceptance is a
prerequisite for P5 privileged discovery.

**Requirements:** ATLAS-04, ATLAS-05, ATLAS-12. **Review:** independent safety review required (§2.11).

### 5.1 Delivered (2026-09-20)

The boundary is implemented as `core/hardware/ReadOnlyProbeAccess.kt` (product) over an injected
`AtlasReadTransport`, so every rule below is *product* code and the fake only fakes I/O:

| Task | State | Evidence |
| --- | --- | --- |
| T2.1 typed request/result preserving the cause class | done | `AtlasProbeRequest` (product, `AtlasModels.kt`); `AtlasReadResult.Rejected` refuses `NONE` and an empty reason. Cause preserved 1:1 for all 11 causes; `ABSENT` alone is re-derived (below) |
| T2.2 path discipline | done | unsafe template → `MALFORMED` **before any transport call** (`..`, glob, `;`, newline, trailing `/`, relative); outside `AtlasAnchors.APPROVED` → `UNKNOWN_CAUSE` and no call; symlink resolution capped at 8 hops, and a resolved target that escapes the anchors is refused with the read never attempted |
| T2.3 budgets with injected clock | done | operation/job deadline, `maxOperations`, 128 per parent / 512 per job, 4 KiB scalar / 16 KiB reviewed proc, 256 KiB aggregate. Every bound is checked **before** work; a cut read and a cut listing are refused, never parsed partially |
| T2.4 concurrency cap | **not implemented** | Needs the coroutine/dispatcher seam that only exists once a real transport is wired (T2.5); the budget object already carries the design intent. Stated as residual, not claimed |
| T2.5 transport selection over an authorized existing transport | **open — needs review** | `UnavailableAtlasReadTransport` is the honest default: every attempt returns `BACKEND_UNAVAILABLE`, never `ABSENT` and never a guess. No fallback shell/root/Shizuku call exists in the file, and `AtlasSourceGuard` fails closed on those tokens |
| T2.6 cancellation fence | done (logic) | `isCurrent(generation)`; the blocked-syscall residual is unchanged and still stands |

**Two defects the tests caught in the code, not in the expectations** (recorded because they are the
reason the assertions are worth anything):

1. The low-level listing result could not say it had stopped early, so a transport-truncated listing
   looked *complete* to the caller — exactly the shape that turns unvisited entries into false
   absence. `AtlasTransportList.truncated` was added and the access now propagates it.
2. The absence rule was only in the fake. It is now enforced by the product
   (`enumeratedParents`): `ABSENT` is returned only for a path whose parent *this job successfully
   enumerated*, and a listing that failed never licenses one — a job's enumeration cannot make
   another job's absence claim true.

**Measured:** 48 tests, 0 failures (`ReadOnlyProbeAccessTest` 23 + `AtlasHarnessTest` 10 +
`AtlasCatalogTest` 15), zero compiler warnings from the touched files, and the three gates
(`code_health --assert`, `i18n_coverage --assert`, `kt_balance`) green. Compiler scope: the modified
scope only, per the owner's standing rule.

---

## 6. Plan P3 — CPU/GPU Reuse And Facade Projection (slice C)

**Objective:** feed Atlas from the *existing* parsed backend evidence through a narrow read seam,
without changing any writer, transaction or AI behavior.

**Allowlist:** `EXISTING APP/core/hardware/CpuHardwareBackend.kt`,
`EXISTING APP/core/hardware/GpuHardwareBackend.kt`,
`EXISTING APP/core/hardware/HardwareCapabilityResolver.kt`,
`NEW APP/core/atlas/AtlasBackendProvider.kt`,
`NEW TEST/core/atlas/AtlasBackendProviderTest.kt` (+ existing `GpuControlModelTest.kt`,
`CpuProvenRangeTest.kt` must stay green unchanged).

**Tasks**
- T3.1 CPU: add an injectable **discovery-only** read interface parameter with production defaults
  around the existing policy enumeration, so the same parser serves Atlas and product code; keep
  every writer method and its tests intact.
- T3.2 GPU: split a narrow read-only interface out of the existing `Io` (extend the legacy interface
  with the read part) so selection/refresh can read and transactions keep `Io`; preserve ambiguity
  handling (no "first GPU wins").
- T3.3 Provider maps parsed evidence → P1 observations, carrying units and identity evidence;
  magnitude heuristics stay labeled `INFERRED`; a reviewed ABI unit outranks a heuristic.
- T3.4 Facade: keep `HardwareCapabilityResolver.resolve(context)` semantics; add only a **pure
  diagnostic projection** (e.g. a snapshot with per-source freshness) if the UI needs one. It must be
  impossible for the projection to create `READ_WRITE` or a new control key.
- T3.5 Legacy topology: prove legacy `cpuN/cpufreq` layouts, alias grouping and partial-policy
  permissions are handled; a missing ladder still permits observation but never invents OPPs.

**Acceptance:** existing control tests unchanged and green; new provider tests assert zero writes,
unit/identity honesty and ambiguity retention; no new control key; the
`resolve → ControlRegistry.build` chain is behaviorally identical (guard test).

**Requirements:** ATLAS-02, ATLAS-03, ATLAS-04, ATLAS-07 (CPU/GPU part). **Review:** independent
safety review required (§2.11).

---

## 7. Plan P4 — Other Domain Observations And Support Matrix (slice D) — **DELIVERED**

**Objective:** add the non-CPU/GPU domains as *observations with reasons*, including the honest
"deferred" state, without reusing screen view-models or their `loadState` writers.

**Allowlist:** `NEW APP/core/atlas/AtlasPlatformProvider.kt`; sequentially **after P1** the same
`AtlasCatalog.kt` (catalog entries only); `NEW TEST/core/atlas/AtlasPlatformProviderTest.kt`.

**Tasks**
- T4.1 Thermal: read raw `type` + `temp` with source-specific scale; missing/malformed → `null`;
  a zone index or a broad `ap`/`tsens` match never claims a CPU junction sensor; `ThermalUtil`'s
  zero-filled fallback must not become "0 °C" evidence.
- T4.2 Battery/power: prefer the public Android battery API for rootless evidence; keep micro-amp,
  micro-volt, micro-amp-hour and micro-watt-hour **separate**; raw current polarity preserved;
  energy nodes are never converted into charge without explicit voltage semantics.
- T4.3 Memory/pressure/ZRAM: malformed or missing PSI stays `null` with the raw error retained;
  PSI and meminfo heuristics keep separate signal identities (ADR-34); ZRAM observation covers
  multiple `zram*` devices rather than a hardcoded `zram0`.
- T4.4 Display, sensors, storage, network, module/root availability: read from public/rootless
  surfaces where possible; network carries no IP/MAC/SSID/package data even internally in the report
  path; root-manager identity is never inferred from `File.exists` under `/data/adb`.
- T4.5 **Support matrix artifact (in-code model + test):** every domain is either observed with
  provenance or listed as deferred/unavailable **with a reason code**; domains with no backend today
  (e.g. ADPF-ish headroom, vendor HAL) stay visible as deferred, not silently omitted.
- T4.6 Explicit prohibition checks: the provider must not instantiate or call `ChargingViewModel`,
  `TouchBoostViewModel`, `DisplayStudioViewModel`, `NetworkSchedulerViewModel`,
  `ThermalDevicesViewModel.refresh` (policy repair) or the `-cbc` bypass path.

**Acceptance:** matrix test asserts each domain's state and reason; unit/dimension tests for
battery and thermal; multi-device ZRAM test; zero writes; no product view-model touched.

**Requirements:** ATLAS-05, ATLAS-07.

### 7.1 Delivered (2026-09-20)

`core/atlas/AtlasPlatformProvider.kt` (pure Kotlin) turns one injected `AtlasPlatformSource` into a
matrix; `AtlasSupportMatrix` **cannot be constructed with a domain missing**, which is what turns "no
backend yet" into a visible deferred row instead of an omission.

| Task | State | Evidence |
| --- | --- | --- |
| T4.1 thermal | done | scale comes from the source and is never inferred from magnitude (same 42000 read as °C and m°C stays two different readings); `cpu`/`gpu`/`battery` are the only reviewed junction tokens, so `ap`/`tsens` stay `INFERRED`; a missing or unparsable `temp` is `MALFORMED` with the raw text kept and is **never 0** |
| T4.2 battery/power | done | micro-amp, micro-volt, micro-amp-hour and micro-watt-hour are four separate units, discharging current stays negative, and `AtlasUnitRules` confirms energy never becomes a charge |
| T4.3 memory/pressure/ZRAM | done | meminfo KiB→bytes is an explicit named conversion; a malformed PSI line yields `null` + the raw text; every `zram*` device is observed (three devices in the test, no hardcoded `zram0`) |
| T4.4 display/sensors/storage/network/privilege | done | network rows are connected/metered/transport only and are asserted canary-free; privilege is `UNAVAILABLE` unless the control plane verified the identity, so an unverified root manager is not reported as a fact |
| T4.5 support matrix artifact | done | one row per domain enforced by an invariant; each gap carries a reason code (`no-source-wired`, `other-provider`, `partial-support`, `no-observation`, `unverified-identity`); CPU/GPU stay visible as another provider's domain |
| T4.6 prohibition checks | done | the provider source is scanned (comments stripped) for `import android`, every product view-model name, `Shell`/`Shizuku`/`chmod`/`-cbc`/`writable`, `File`, `data/adb` and the identity getters (`ssid`/`macAddress`/`ipAddress`/`wifiInfo`/`packageName`); zero hits |

**Deliberate scope note.** The Android-facing adapter that fills `AtlasPlatformSource` is **not**
written here: it belongs with the wiring plan, like `P2`'s transport adapter, and
`UnavailableAtlasPlatformSource` is the honest default until then. What is delivered is the logic
that decides truthfulness, and it is fully exercised.

**New vocabulary:** `/android/api` joined `AtlasAnchors` as an explicitly **virtual** root, and the
file boundary now refuses anything under it (`UNKNOWN_CAUSE`, "not a file path") so an API surface
can never be smuggled into the file transport later. No catalog entry may anchor there — asserted.

**Measured:** 22 provider tests (`AtlasPlatformProviderTest`), 0 failures; the whole `nd.max.core.*`
scope is 295 tests / 0 failures with zero compiler warnings from the touched files.

---

## 8. Plan P5 — Ordered Resolver, Evidence Cache And DI (slice E)

**Objective:** per-feature ordered resolution with a bounded, context-keyed cache and one coalesced
scan, exposed as immutable state.

**Allowlist:** `NEW APP/core/atlas/AtlasResolver.kt`, `NEW APP/core/atlas/AtlasEvidenceStore.kt`,
`NEW APP/core/atlas/AtlasRepository.kt`, `EXISTING APP/core/di/DataModule.kt`,
`NEW TEST/core/atlas/AtlasResolverTest.kt`, `NEW TEST/core/atlas/AtlasEvidenceStoreTest.kt`.

**Tasks**
- T5.1 State machine per feature: reviewed knowledge → (unresolved) bounded discovery → terminal
  reason. States distinguish **completed / exhausted / partial / cancelled / denied / unavailable**;
  success on one feature never suppresses another (`ATLAS-02`, `ATLAS-03`).
- T5.2 Coalesced single scan with an explicit job + generation; duplicate callers observe one state;
  `CancellationException` propagates (no bare `runCatching` around a suspend pipeline); state is an
  immutable snapshot published through a `StateFlow` from a repository — not from a composable.
- T5.3 Cache identity: `catalogVersion` + schema version + device/build/kernel identity + process ABI
  + boot/session freshness + privilege/backend generation. Private invalidation identity never leaks
  into the report. No reward/outcome scores, no learning — capability observations only.
- T5.4 TTL policy (adopted values in §7): positive evidence capped, negative evidence short and
  separately keyed for denial; failed reads never extend a success's freshness; `null` means "no
  attempt", never zero; explicit Retry respects rate/budget and never requests privilege.
- T5.5 Persistence: app-private location (`filesDir/atlas/` or `noBackupFilesDir`), atomic replace,
  bounded reads, corrupt file → discard and rescan (fail closed), never promote a cached observation
  into writability.
- T5.6 DI: constructor injection for concrete classes, an interface binding only where one exists,
  `DispatcherProvider` from `AppModule`, `@ApplicationContext` for storage; **constructors do not
  scan**; no second scope/scan owned by a screen.

**Acceptance:** ordered-trace test (known stage before discovery, unresolved feature not re-probed
after success), clock boundary/backward/future tests, invalidation on each context change, corrupt
file test, late-completion-after-cancel test, double-call coalescing test, and a test asserting
cancellation is not reported as exhaustion.

**Requirements:** ATLAS-03, ATLAS-04, ATLAS-06, ATLAS-12.

---

## 9. Plan P6 — Privacy, Support Report And Export (slice F)

**Objective:** a minimized, previewable, locally shared report — never a log dump, never an upload.

**Allowlist:** `NEW APP/core/diagnostics/AtlasSupportReport.kt`,
`NEW APP/core/diagnostics/AtlasReportExporter.kt`,
`EXISTING APP/core/diagnostics/DiagnosticCenter.kt` (add a structured read-only projection only),
`NEW TEST/core/diagnostics/AtlasSupportReportTest.kt`,
`NEW TEST/core/diagnostics/AtlasReportExporterTest.kt` (+ existing `DiagnosticCenterTest.kt` green).

**Tasks**
- T6.1 Report DTO + explicit-field versioned serializer (the `ProfileSharing` style): include report
  schema, app version, catalog version, coarse device/kernel/API/ABI metadata chosen in preview,
  attempted provider/stage, reason codes, safe unit/identity evidence, limits reached, observation
  age, and explicit `null`s for unknown. Unknown schema is rejected.
- T6.2 Exclusion list enforced by tests: IMEI, serial, Android ID, accounts, network
  addresses/SSID/MAC, user file paths, package lists, ownership tokens, arbitrary command output,
  full property dumps, native/model paths, pstore, tombstones, configs, unrestricted logs. Hashing a
  stable personal identifier is explicitly **not** anonymization.
- T6.3 Path discipline in the report: catalog operation ids and reviewed safe templates by default;
  any exact device-specific metadata is optional, bounded and visible in preview. Private cache
  identity/fingerprints never included implicitly.
- T6.4 Size/limits validated **before** construction: 256 KiB serialized maximum, string/array
  count limits, oversize or malformed input rejected rather than truncated into plausibility.
- T6.5 Preview equals bytes: the frozen, previewed artifact is byte-identical to the shared artifact
  (asserted), and serialization performs **no** fresh privileged probe.
- T6.6 Export: `cacheDir/atlas-reports/` with an app-generated filename, atomic replace, age-based
  cleanup (24 h / next app start) that does not delete a share that may still be read; MIME is the
  real JSON/text type; single-URI read grant + `ClipData`; no receiving activity handled; no write
  grant; **no network, no upload client, no recipient address**. `FileProvider` and
  `file_paths.xml` stay unchanged.
- T6.7 `DiagnosticCenter`: add a structured projection exposing allowlisted component/level/count
  summaries; arbitrary messages, throwable text and raw forwarder output are **not** exported;
  denied probes are not recorded as ERROR (avoids log spam and hidden shell in tests).

**Acceptance:** privacy-canary tests (sentinel secrets in every raw field and exception absent from
disk, preview and share bytes); preview==export bytes; oversize/unknown-schema rejection; a
cancelled run cannot produce a "complete" report; no network API reachable from this package.

**Requirements:** ATLAS-09, ATLAS-10 (report side), ATLAS-11 (privacy tests).

---

## 10. Plan P7 — Settings/Diagnostics Integration (slice G)

**Objective:** one reachable, honest surface over the repository state — no new destination, no
false progress, no new design language.

**Allowlist:** `NEW APP/ui/viewmodel/AtlasViewModel.kt`,
`NEW APP/ui/mainscreens/AtlasDiagnosticsSection.kt`,
`EXISTING APP/ui/mainscreens/SettingsScreen.kt`,
`EXISTING APP/ui/mainscreens/DiagnosticsScreen.kt`,
`EXISTING RES/values/max_screen_strings.xml`, `EXISTING RES/values-ar/max_screen_strings.xml`,
`NEW TEST/ui/mainscreens/AtlasPresentationTest.kt` (+ existing `ViewModelInstantiationTest`,
`SettingsDestinationReachabilityTest` must stay green).

**Tasks**
- T7.1 Settings: add one `MaxNavigationRow` to the **existing** `MaxDestination.Diagnostics` in the
  feature area, outside module-loaded gates; remove/replace the stale comment at the removed entry.
  Permission recovery links to the existing `MaxDestination.Privilege` — no second permission panel.
- T7.2 Diagnostics: replace the synchronous `LaunchedEffect` + `onClick` re-discovery with one
  repository state stream (an effect alone does not put work on IO); replace the immediate
  report-regenerate-and-copy block with **preview → cancel/share**.
- T7.3 ViewModel: `@HiltViewModel` with constructor injection, immutable state, events
  `start/retry/cancel/preview/share`; screen default is `hiltViewModel()` (guard exists), UI collects
  with `collectAsStateWithLifecycle`; no hardware IO, no shell, no policy in the VM.
- T7.4 Components: `MaxSection`/`MaxGroup`/`MaxRow` for domains and reasons; `MaxProgressStrip` with
  **nullable** percent (unknown total ⇒ no percentage, no elapsed-time fake); `MaxConditionNotice`
  for partial/denied/cancelled; `MaxMetric`/`MaxMetricLine` with unit/source/age and trust states
  that never default to Live; `MaxTabbedDialog` for the bounded preview (no unbounded lazy list
  inside); `MaxConfirmDialog` for final consent, accounting for its dismiss-before-confirm order.
- T7.5 Copy: paired `max_atlas_*` EN/AR keys, specifier-identical; textual status beyond color;
  accessible action names; 48 dp targets; start/end padding; AutoMirrored navigation; long paths and
  reasons inspectable in preview rather than lost to row ellipsis.
- T7.6 The report suggestion appears **only** after eligible stages complete; a cancelled or
  budget-stopped run shows incomplete state and no suggestion.

**Acceptance:** no new route/destination; presentation test (progress nullability, no "Applied" for a
completed scan, cancellation ≠ exhaustion, report gating); reachability + ViewModel-instantiation
guards green; `hardcoded_ui_literals` and `presentation_hw_writes` debt unchanged (66 / 26);
`i18n_coverage --assert` 0 obstacles with AR parity.

**Requirements:** ATLAS-03, ATLAS-08, ATLAS-09 (UI side).

---

## 11. Plan P8 — No-Root Read-Only Entry Acceptance (slice H)

**Objective:** prove the read-only Atlas journey is reachable without root/module — or record, in
writing, that it is not, instead of implying it.

**Allowlist (conditional, only if the journey test proves it necessary):**
`EXISTING APP/ui/mainscreens/GetStartedScreen.kt`, `EXISTING APP/MainActivity.kt`, paired existing
screen resources, `NEW TEST/ui/navigation/AtlasReadOnlyEntryTest.kt`.

**Tasks**
- T8.1 Journey test: from app start without root and without the module, Settings → Diagnostics →
  Atlas status is reachable and does not require a privilege prompt.
- T8.2 Do not broaden this into a startup/safety rewrite. `RootIpcManager.bind` being null means
  "not connected yet", not "unsupported"; onboarding's root `pm grant`/`am start -S` finish and its
  cosmetic `canGoNext` color behavior are documented as pre-existing, not fixed here.
- T8.3 If a fully prompt-free whole-app startup is required, it must be raised as its own explicit
  lifecycle scope with independent safety review **before** any claim — not smuggled into this plan.
- T8.4 Record the residual either way in `docs/ai/HANDOFF.md` (accepted / blocked with reason).

**Acceptance:** the journey test exists and its result is reported honestly; no change to Max AI
startup, the engine, or the root service; residual scope written down.

**Requirements:** ATLAS-08 (entry half).

---

## 12. Plan P9 — Fixtures, Maintainer Handoff And Closure (slice I)

**Objective:** turn a sanitized report into reviewed, deterministic support — and close the phase
only with evidence, never with plans.

**Allowlist:** `NEW manager/app/src/test/resources/atlas/unknown-no-root.json`,
`…/qualcomm.json`, `…/mediatek.json`, `…/exynos-tensor.json`, `…/denied.json`, `…/malformed.json`,
`…/symlink-alias.json`; `NEW TEST/core/atlas/AtlasArchitectureTest.kt`; reviewed `AtlasCatalog.kt`
updates only when independently justified.

**Tasks**
- T9.1 Fixtures: deterministic, schema-validated, sized within limits; invalid schema, oversized
  input, duplicate ids and path-injection text are rejected rather than coerced.
- T9.2 Architecture guard: fails closed if the source root is unreadable (no silent skip); scans for
  writer/import/shell-text violations **and** combines with runtime fake-IO zero-write assertions.
- T9.3 Maintainer recipe (documented, not published): sanitized report → fixture → narrow catalog
  entry/provider nudge → focused test → ordinary release path. Include catalog rejection/rollback
  and unknown-schema handling. No remote code, no runtime support packs.
- T9.4 Device protocol (read-only) executed elsewhere and recorded: unknown vendor, no-root,
  Qualcomm, MediaTek, Exynos/Tensor, missing GPU tables, numeric devfreq directories, multiple
  GPU-like domains, locked SELinux, privilege change mid-job; instrument the boundaries to show
  **zero mutations** (before/after node comparison alone is insufficient).
- T9.5 Requirement traceability: each ATLAS-01..12 marked with its evidence, or explicitly
  `unverified — needs device` / `blocked`.
- T9.6 Independent safety review of P2/P3 diffs; coordinator records the closure report in
  `docs/ai/HANDOFF.md` using `docs/ai/VALIDATION.md` §8.

**Acceptance:** architecture guard green and fail-closed; fixtures validate; traceability table has
no empty cell; no publication, signing or release in this phase.

**Requirements:** all (closure), especially ATLAS-10, ATLAS-11.

---

## 13. Requirement Coverage

| Requirement | Plans that must produce its evidence |
| --- | --- |
| ATLAS-01 | P1 (catalog/provenance), P9.1 (fixtures), P9.6 (traceability) |
| ATLAS-02 | P1.4, P3 (per-feature provider), P5.1 (no cross-feature suppression) |
| ATLAS-03 | P5.1/P5.2 (ordering, states), P7.6 (report gating) |
| ATLAS-04 | P2 (read-only boundary), P3.5/P5.6 (unchanged chain), P9.2 (guard + review) |
| ATLAS-05 | P1.5 (units/axes), P2.1 (cause classes), P4 (domain honesty) |
| ATLAS-06 | P5.3-P5.5 (identity, TTL, atomicity, corruption) |
| ATLAS-07 | P3 (CPU/GPU), P4 (matrix + deferred honesty) |
| ATLAS-08 | P7 (surface/a11y/copy), P8 (no-root journey) |
| ATLAS-09 | P6.1-P6.6, P7.6 |
| ATLAS-10 | P6.7, P9.3, P9.6 |
| ATLAS-11 | P0 (harness), P6.2 (privacy), P9.3/P9.4 (fixtures + device) |
| ATLAS-12 | P2.3/P2.4/P2.6 (budgets, concurrency, cancellation), P5.2 |

Requirements stay `Pending` in `.planning/REQUIREMENTS.md` until evidence exists and is recorded.

---

## 14. Verification Protocol

**Per plan (always):** focused JVM tests for the tests it adds + the five cheap gates of §2.9.
**Per plan (when the owner authorizes a compiler):** `:app:compileDebugUnitTestKotlin` then
`:app:testDebugUnitTest --tests '*Atlas*'`, reported with the actual task names and timings.
**Per integration wave (owner-approved):** full JVM suite on the release variant, debug APK, R8 —
per `AGENTS.md` §5 — and `docs/ai/VALIDATION.md` gates, reported separately from static checks.
**Phase closure (P9):** device read-only matrix, independent safety review, traceability table, and
one `HANDOFF` entry using the §8 template (TASK / FILES / GATES / BUILD / RESIDUAL RISK / NEXT).

**Honesty rules carried from the source artifacts:** fixtures are not hardware qualification; a
passing parser test says nothing about a vendor kernel; "compilation unverified in this
environment" must be said when no compiler ran; cancelled ≠ exhausted; preview bytes == shared
bytes; a budget value is a design choice until measured.

---

## 15. Open Decisions For The Owner (blocking where marked)

| ID | Decision | Why it blocks | Recommendation |
| --- | --- | --- | --- |
| **DECISION-1 — implemented as recommended, owner confirmation still wanted** | Which budget set is adopted: `01-RESEARCH.md` (§Budgets) proposes **8 s** job, **250 ms** per I/O, 128/512 entries, 4 KiB scalar / 16 KiB proc, **256 KiB** aggregate, 10 min positive TTL, 60 s negative; `01-PATTERNS.md` proposes **12 s** job, **1 s** per operation, 4 KiB scalar / **64 KiB** table, **512 KiB** aggregate. They conflict. | The transport cannot enforce two different deadlines. | Adopted a single reconciled set: **8 s job deadline**, **1 s per operation** (sysfs on a cold/slow kernel is slower than 250 ms; a 250 ms cap would manufacture false `TimedOut`), 128 entries per parent / 512 globally, 4 KiB scalar, **16 KiB** only for explicitly reviewed proc summaries, **256 KiB** aggregate, positive TTL 10 min, negative 60 s / denied until privilege change or 5 min. All values remain unmeasured design choices — and they now live in exactly one place, `AtlasReadBudget.DEFAULT` (the test harness delegates to it), so a later decision is a one-file change with the tests following it. |
| **DECISION-2** | Is P8 (no-root entry) in this phase, or deferred to its own plan? | It is the only plan that can touch `MainActivity`/onboarding. | Keep it, but strictly as a **test + honest residual**; any fix beyond navigation is a separate approved scope. |
| **DECISION-3 — implemented as recommended, owner confirmation still wanted** | v1 domain matrix: confirm thermal/battery/memory+ZRAM/display/sensors/storage/network in the read-only matrix, with ADPF-headroom and vendor HAL explicitly deferred. | Determines P4's size and the UI's section count. | Accepted as proposed: those domains have providers, every other domain (CPU/GPU, and any without a backend) is present as a deferred row with a reason code rather than dropped. A domain can no longer be omitted at all, because the matrix refuses to build without all of them. |
| **DECISION-4** | Retention: 24 h / next-start cleanup with at most one current export plus two retained. | Privacy vs usability of the share. | Accept, and never delete a share the chooser may still be reading. |
| **DECISION-5** | Copy location: `max_atlas_*` keys inside the existing registered `max_screen_strings.xml` (no Crowdin edit) vs a new Atlas strings file (+Crowdin registration). | Changes the allowlist and the localization gate. | Use the existing file; a separate file requires both EN/AR files **and** a `crowdin.yml` entry in the same plan. |
| **DECISION-6** | Execution authorization: who executes, when, and whether builds/tests are permitted (the owner's standing rule is "no build unless asked"). | Nothing in this plan may start without it. | Approve plan-by-plan; P0+P1 first, test-only and compiler-free. |

---

## 16. What This Plan Does Not Do

- No product code, no commit, no build, no signing, no release, no publication (this run is planning).
- No upstream code, script, path database or profile is imported; references are architectural only.
- No hardware compatibility, performance or safety claim: everything about devices stays
  `unverified — needs device` until P9's read-only protocol runs and is logged.
- No claim of ASVS compliance: `config.json` requests ASVS L1 with high-severity blocking, and §2's
  constraints are the local mapping, not a certification.
- No promise that an independent-family safety reviewer is available in this runtime.

## 17. Changelog

- **2026-09-20:** created. Integrates `01-PATTERNS.md` slices A-I into ten dependency-ordered plans
  (P0-P9), adds the wave-zero harness allowlist the pattern map did not name, reconciles the two
  conflicting budget sets as DECISION-1, and records the `txt.txt` baseline change factually.
- **2026-09-20 (execution):** owner said "start and make it the best". `P0` and `P1` delivered and
  measured (Amendment A-1 records why they landed together). Owner authorization is what allowed
  product code; the standing no-build-by-default rule was respected by compiling only the modified
  scope, not the full app: `:app:testDebugUnitTest --tests 'nd.max.core.atlas.*'`.
- **2026-09-20 (execution, T2.4 + P4):** owner said "continue", then "finish everything in one batch".
  `P2/T2.4` (concurrency cap) is delivered and `P4` is delivered (§7.1). **Two real defects were
  found by tests, not by reading:** the check-then-enter cap was not atomic (eight threads all passed
  the check before any incremented — a cap that fails exactly under the flood it exists for), and
  `catalog.byDomain` excludes vendor-tagged entries, so counting "entries the catalog holds" with it
  made a matched vendor entry look like more knowledge than the catalog had. Both were fixed in the
  code, and both invariants now have tests. **Amendment A-3:** `AtlasAnchors.PUBLIC_API` added as a
  virtual root with a boundary refusal; `AtlasDomainSupport` gained `matchedEntries`; and
  `tools/kt_balance.py` gained backticked-identifier support after it produced a false positive on a
  test name containing an apostrophe (self-test 17 cases, 0 failures).
- **2026-09-20 (execution, P2):** owner said "continue". `P2` T2.1–T2.4/T2.6 delivered and measured
  (see §5.1); T2.5 remains open because it needs the reviewed adapter and is the reason P2's safety
  review is still outstanding. **Amendment A-2** records the cross-plan edits this forced: the P0
  fake probe became a **fake transport** (so the real access is under test), `AtlasProbeRequest`
  moved from test support into the product, `AtlasBudgets` now delegates to `AtlasReadBudget.DEFAULT`
  instead of holding a second copy, and `AtlasProbeKinds` gained `canonical` as a read-only kind.
  No file outside P0's and P2's allowlists was touched, and no product behaviour outside Atlas changed.
