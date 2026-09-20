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
**Plans:** Pending research and planning.

## Progress

| Phase | Plans Complete | Status | Completed |
| --- | --- | --- | --- |
| 1. Max Atlas compatibility and safe discovery | 0/0 | Pending | - |
