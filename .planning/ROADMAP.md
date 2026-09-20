# Roadmap: Max Atlas Compatibility

## Overview

One request-scoped milestone for the existing app: reviewed knowledge, safe evidence-driven
discovery, transparent fallback and a last-resort support loop. Existing completed tasks and
ADRs in docs/ai are not renumbered or duplicated here.

## Phases

- [ ] **Phase 1: Max Atlas compatibility and safe discovery** - Expand device capability coverage without speculative hardware writes.

## Phase Details

### Phase 1: Max Atlas compatibility and safe discovery

**Goal:** A user on a supported or unfamiliar device receives truthful per-feature capability
results from reviewed knowledge, then bounded Max Atlas discovery, and only then guidance to
share a minimized diagnostic report from Settings; the maintainer can add tested device support
without changing Max AI policy or bypassing the existing control plane.
**Depends on:** Nothing (existing app/control plane is the baseline)
**Requirements:** ATLAS-01, ATLAS-02, ATLAS-03, ATLAS-04, ATLAS-05, ATLAS-06, ATLAS-07, ATLAS-08, ATLAS-09, ATLAS-10, ATLAS-11, ATLAS-12
**Success Criteria** (what must be TRUE):
1. An unknown device can resolve supported capabilities without a model-name allowlist; each
   result carries source, unit, freshness and reason rather than fabricated support.
2. Discovery performs no hardware writes, respects budgets/permissions and reuses existing
   control backends only after explicit semantic validation.
3. A failing feature progresses through reviewed knowledge and bounded discovery before Settings
   suggests an optional sanitized report; successful features remain usable independently.
4. Reports are previewed and locally shared only with consent; a maintainer can turn one into
   a fixture and a reviewed support change released using the existing release pipeline.
5. Automated regression coverage and a real-device validation protocol distinguish simulated
   correctness from actual OEM/kernel compatibility and require independent safety review.

**Canonical references:** `docs/ai/DECISIONS.md`, `docs/ai/ENGINEERING-CONTRACT.md`,
`docs/ai/VALIDATION.md`, `docs/ai/REVIEW.md`, `docs/ai/DESIGN_VISION.md`, `txt.txt`.
**Plans:** written in `phases/01-max-atlas-compatibility-and-safe-discovery/01-PLAN.md` — ten
plans (P0-P9) over the pattern-map allowlists A-I, dependency-ordered and exclusive:

| Plan | Scope | Depends on |
| --- | --- | --- |
| P0 | Wave-zero test harness (read-only fakes, clock, budgets, fixtures, privacy canaries) | — | **done** |
| P1 | Evidence contract + reviewed provenance catalog (slice A) | P0 | **done** |
| P2 | Bounded read-only transport (slice B) | P0, P1 | **done except T2.5 (needs reviewed adapter)** |
| P3 | CPU/GPU reuse + facade projection (slice C) | P1 | **done** (plan §21.1) |
| P4 | Other domain observations + support matrix (slice D) | P1 | **done** |
| P5 | Ordered resolver + context-keyed evidence cache + DI (slice E) | P1-P4 | **done** (plan §21.2) |
| P6 | Privacy, minimized support report, local export (slice F) | P5 | **done** (plan §21.3) |
| P7 | Settings/Diagnostics integration (slice G) | P5, P6 | **done** (plan §20.3) |
| P8 | No-root read-only entry acceptance (slice H) | P7 | **done as a source/render claim** (plan §20.4) |
| P9 | Fixtures, maintainer handoff, closure (slice I) | all | **done** — `P9.1` (fixture format, recorder, replay transport) and `P9.2` (architecture guard), plan §20.4 and §22.1 |

Four more plans came out of the gap review (`01-GAPS-AND-IDEAS.md`, plan §18) — they are **not** part of
the original ten and are recorded here only because the owner said "نفذ" and two of them are delivered:

| Plan | Scope | Same plan text | Status |
| --- | --- | --- | --- |
| P10 | Device identity from declared-public surfaces (slice J) — plan §19.1 | — | **done** |
| P11 | Quirk base + availability tiers (`EXPECTED`/`DEVICE_DEPENDENT`/`EXPECTED_DENIED`) | — | proposed — should follow `P13`, or it becomes a base of assumptions |
| P12 | Evidence lifetime, negative evidence, probe plan (slice K) — plan §19.2 | — | **done** |
| P13 | On-device doctor + report→fixture loop | — | **done** (`AtlasDoctor`, plan §22.2) — the loop is built; running it on a phone is what remains |

Six decisions in `01-PLAN.md` §15 are open. `DECISION-1` (one reconciled budget set) is now
**implemented as recommended** in `AtlasReadBudget.DEFAULT` — the single source the test harness
delegates to, so changing it later is a one-file change — and it still wants the owner's word.
The owner authorized execution on 2026-09-20 ("start and make it the best", later "continue" then "نفذ");
`P0`, `P1`, `P3`, `P4`, `P5`, `P6`, `P7`, `P8`, `P9.1`, `P9.2`, `P10`, `P12` and `P13` are delivered and
measured — the newest full release run is **1103 tests / 0 failures / 0 errors / 0 skipped** with the debug
APK produced, and the Atlas plus diagnostics scope inside it is 22 classes / 306 tests with 0 failures —
and the standing rule remains
*do not build by default* — only the modified scope is compiled
(`:app:testDebugUnitTest --tests 'nd.max.core.atlas.*'`). `P2`'s T2.5 (the adapter over an existing
authorized transport) is deliberately unbuilt: it needs the independent safety review that is not
available in this runtime, and `UnavailableAtlasReadTransport` keeps that honest meanwhile.

## Progress

| Phase | Plans Complete | Status | Completed |
| --- | --- | --- | --- |
| 1. Max Atlas compatibility and safe discovery | 12/14 | In progress — of the ten planned: `P0`, `P1`, `P3`, `P4`, `P5`, `P6`, `P7`, `P8` and `P9` delivered; `P2` delivered except its reviewed adapter (T2.5). Of the four proposed: `P10`, `P12` and `P13` delivered, `P11` not started (it can only *lower* confidence, so it must be built on real reports — which now have a mechanism). **The app reaches Atlas** (`AtlasViewModel` → `AtlasDiagnosticsSection`, plus a Settings entry), and a real run is now replayable data, so "needs device" is no longer permanent. What no run here can still prove is the device itself | - |
