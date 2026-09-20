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
| P3 | CPU/GPU reuse + facade projection (slice C) | P1 |
| P4 | Other domain observations + support matrix (slice D) | P1 | **done** |
| P5 | Ordered resolver + context-keyed evidence cache + DI (slice E) | P1-P4 |
| P6 | Privacy, minimized support report, local export (slice F) | P5 |
| P7 | Settings/Diagnostics integration (slice G) | P5, P6 |
| P8 | No-root read-only entry acceptance (slice H) | P7 |
| P9 | Fixtures, maintainer handoff, closure (slice I) | all |

Six decisions in `01-PLAN.md` §15 are open. `DECISION-1` (one reconciled budget set) is now
**implemented as recommended** in `AtlasReadBudget.DEFAULT` — the single source the test harness
delegates to, so changing it later is a one-file change — and it still wants the owner's word.
The owner authorized execution on 2026-09-20 ("start and make it the best"); `P0`, `P1`, `P2` and `P4`
are delivered and measured (74 Atlas tests, 0 failures), and the standing rule remains
*do not build by default* — only the modified scope is compiled
(`:app:testDebugUnitTest --tests 'nd.max.core.atlas.*'`). `P2`'s T2.5 (the adapter over an existing
authorized transport) is deliberately unbuilt: it needs the independent safety review that is not
available in this runtime, and `UnavailableAtlasReadTransport` keeps that honest meanwhile.

## Progress

| Phase | Plans Complete | Status | Completed |
| --- | --- | --- | --- |
| 1. Max Atlas compatibility and safe discovery | 4/10 | In progress — `P0`, `P1`, `P4` delivered; `P2` delivered except its reviewed adapter (T2.5); `P3` and `P5` next | - |
