# Phase 1: Max Atlas Compatibility And Safe Discovery - Context

**Gathered:** 2026-09-20
**Status:** Ready for research and planning
**Source:** Direct owner request plus explicit authorization to initialize and complete this
GSD track using that request as context. No pre-existing phase was overwritten.

<domain>
## Phase Boundary

Make the existing app progressively compatible across devices: reviewed capability knowledge
first, a separate evidence-driven discovery engine second, optional support report last.
Deliver an extensible provider seam and support loop, not a promise that every kernel exposes
every control. Planning only in this run; no product implementation or publication.
</domain>

<decisions>
## Implementation Decisions

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
</decisions>

<canonical_refs>
## Canonical References

- `AGENTS.md`: collaboration, safety review, localization and reporting contract.
- `docs/ai/ENGINEERING-CONTRACT.md`: investigate first, minimal changes, truthful verification.
- `docs/ai/DECISIONS.md`: binding architecture and safety decisions; do not duplicate ADRs here.
- `docs/ai/VALIDATION.md`: repository and Android validation gates.
- `docs/ai/REVIEW.md`: independent review and reproducibility rules.
- `docs/ai/DESIGN_VISION.md`: Settings, Diagnostics, trust, canonical navigation/design language.
- `docs/ai/NEXT_TASK.md`: existing task registry, left intact except a pointer for this request.
- `docs/ai/HANDOFF.md`: read order and the only executor log.
- `txt.txt`: owner-supplied reference inventory, not verified fact or implementation authority.
- `https://github.com/SmartPack/SmartPack-Kernel-Manager`: explicitly requested primary reference.
</canonical_refs>

<specifics>
## Specific Ideas

Reviewed knowledge -> validated candidates -> bounded read-only discovery -> evidence-backed
existing backend OR reasoned unresolved state -> optional report -> maintainer fixture/fix/release.
CPU topology, GPU identity/frequency units and thermal-zone meaning must come from evidence,
not node indices, model popularity or a successful shell exit alone.
Discovery and permission recovery may stop early safely; unsupported capability is not failure
of the whole application. Cancellation must never be reported as completed exhaustion.
</specifics>

<deferred>
## Deferred Ideas

Not requested or authorized here: cloud AI, new Shizuku/vendor privilege transports, root exploits,
SELinux changes, boot-time mutation, replacing Max AI, signing/releasing in this session, or
importing third-party executable plugins. Architecture may leave seams for separately reviewed work.
</deferred>
