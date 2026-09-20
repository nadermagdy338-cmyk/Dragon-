# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-20)

**Core value:** Broader compatibility with evidence, not speculative hardware control.
**Current focus:** Phase 1 - Max Atlas compatibility and safe discovery

## Current Position

Phase: 1 of 1 (Max Atlas compatibility and safe discovery)
Plan: 10 written (P0-P9), 4 delivered (P0, P1, P2 except its reviewed adapter, P4)
Status: Implementing
Last activity: 2026-09-20 - Owner said "continue" then "finish everything in one batch"; `P2/T2.4`
(concurrency cap) and `P4` (other domains + support matrix) delivered and measured.

Progress: [====------] 40%

## Accumulated Context

### Decisions

Binding ADRs remain in docs/ai/DECISIONS.md. Phase inputs live in 01-CONTEXT.md.
Max Atlas is the proposed product name selected under the owner's naming discretion.
No app code, branch, commit, push or release is authorized in this planning run.

Ten executable plans exist in 01-PLAN.md (P0-P9, dependency-ordered, exclusive allowlists).
P0 and P1 are delivered: `:app:testDebugUnitTest --tests 'nd.max.core.atlas.*'` = BUILD SUCCESSFUL,
25 tests, 0 failed/errors/skipped; both product files compile with zero warnings; `kt_balance` 666
files / 0 obstacles; `code_health --assert` exit 0; `repo_audit` PROBLEMS: 0. The seed catalog holds
14 provenance-bearing entries over 8 domains, and the fail-closed source guard finds no authority or
transport token in the Atlas sources (Amendment A-1 in the plan records that P0 and P1 landed together).
P4 is delivered: `core/atlas/AtlasPlatformProvider.kt` (pure, injected `AtlasPlatformSource`) builds a
support matrix that refuses to exist without a row for every domain, so a domain with no backend stays
visible as deferred with a reason code. Thermal scales come from the source (never from magnitude), a
zone's `ap`/`tsens` token never claims a CPU junction, battery dimensions stay four separate units with
current polarity intact, malformed PSI keeps its raw text and yields no value, every `zram*` device is
observed, network rows carry state only, and privilege is unavailable unless the control plane
verified the identity. `T2.4` (concurrency cap) is delivered too: admission is increment-then-check
with rollback, because check-then-enter let a flood through.
P2 is delivered: the boundary is `core/hardware/ReadOnlyProbeAccess.kt` over an injected
`AtlasReadTransport` (so the fake only fakes I/O and the tested rules are the shipped ones), with
path discipline before any call, budgets checked before work, causes preserved, absence only from an
enumeration this job performed, and no mutating operation anywhere. 48 Atlas tests, 0 failures
(`ReadOnlyProbeAccessTest` 23, `AtlasHarnessTest` 10, `AtlasCatalogTest` 15) with zero compiler
warnings from the touched files; the three gates are green.
All four measured scopes are green: `nd.max.core.*` = 295 tests / 0 failures (Atlas 74), zero compiler
warnings from the touched files, and `code_health`/`i18n_coverage`/`kt_balance` all pass.
Six decisions are open in 01-PLAN.md section 15. DECISION-1 (a single reconciled budget set, because
01-RESEARCH.md proposes 8 s/250 ms/256 KiB while 01-PATTERNS.md proposes 12 s/1 s/512 KiB) is now
**implemented as recommended** in `AtlasReadBudget.DEFAULT` — one place, with the test harness
delegating to it — and is no longer a blocker; it still wants the owner's confirmation, and every
value remains unmeasured. `P2`'s T2.5 (adapter over an existing authorized privileged transport)
remains deliberately unbuilt pending the independent safety review this runtime cannot provide;
`UnavailableAtlasReadTransport` returns `BACKEND_UNAVAILABLE` for every attempt meanwhile, so
nothing is guessed at and nothing is claimed.
Owner directive of 2026-09-20 (AGENTS.md section 0.1) also applies to execution: do not build by
default; compile only on request and with the narrowest task.

### Blockers/Concerns

- **Resolved, reported factually:** the preflight code_health failure caused by the owner-provided
  `txt.txt` no longer reproduces. That file is absent from the working tree and
  `python3 tools/code_health.py --assert` exits 0 (health 0/0/0/0, debt 10/29/66/26, 477 Kotlin
  files / 109,630 lines). This planner did not delete it and did not touch any ignore rule or gate.
  Any later need for its exact wording must ask the owner for the file again.
- Hardware compatibility and runtime safety cannot be proven by planning artifacts.
- Configured historical model names are not selectable in this runtime. Available Task agents
  may load GSD role briefs, but no review may be represented as GPT-5.6 Luna approval. Until an
  independent-family reviewer is available, hardware-seam plans (P2, P3) stay `UNREVIEWED`.
- Execution was authorized on 2026-09-20; builds/tests stay narrow and on request only.
- `P2` and `P4` are delivered but **`UNREVIEWED`**: neither touches an existing transport yet, so what
  is outstanding is the reviewed adapter (P2/T2.5) — not the bounds, which are design values no device
  has measured. `P3` is unblocked after `P1` but edits hardware backends, so it stays `UNREVIEWED` until
  an independent reviewer exists; `P5` needs P2+P3+P4, i.e. it is waiting on P3.
- The two defects P2/T2.4 and P4 turned up were both *reasoning* errors that reading could not catch
  (non-atomic admission, a counting API that excludes vendor entries). They are recorded in the plan's
  changelog so a later plan does not repeat them.

## Session Continuity

Last session: 2026-09-20
Stopped at: `P0`, `P1`, `P2` (except T2.5) and `P4` delivered and verified; next is `P3` (CPU/GPU
reuse — edits hardware backends, so `UNREVIEWED` until an independent reviewer exists), which then
unblocks `P5`.
Resume file: .planning/phases/01-max-atlas-compatibility-and-safe-discovery/01-PLAN.md
