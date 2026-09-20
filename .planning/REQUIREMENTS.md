# Requirements: Max Atlas

**Defined:** 2026-09-20
**Source:** owner's request, `txt.txt` as untrusted research input, and explicit approval
to initialize this request-scoped GSD track and complete research/planning/verification.

## Phase 1 Requirements

- [ ] **ATLAS-01**: Independently authored, versioned capability catalog with source provenance,
  driver/ABI semantics, units and safety classifications; study SmartPack and all references
  from txt.txt without copying code, scripts or bulk databases.
- [ ] **ATLAS-02**: Small generic/vendor/platform provider extensions use a stable capability
  contract; an unknown model never blocks otherwise supported features or crashes the app.
- [ ] **ATLAS-03**: Ordered per-feature resolution uses reviewed knowledge first, bounded safe
  discovery second, and an explicit unresolved result before support-report guidance.
- [ ] **ATLAS-04**: Max Atlas is independent of Max AI policy and has no mutation authority;
  existing canonical knobs, manual locks, arbiter, verification and rollback remain authoritative.
- [ ] **ATLAS-05**: Evidence distinguishes absent, permission denied, read-only, malformed,
  ambiguous, stale, unavailable backend and verified control; units and sensor identity are
  validated and unknown telemetry is never synthesized.
- [ ] **ATLAS-06**: Cache successful and failed observations with bounded retry and invalidation
  on device/kernel/build/catalog/privilege changes, cancellation and changed runtime evidence.
  Read-only inference must never promote an unknown path to writable control.
- [ ] **ATLAS-07**: Reuse existing CPU/GPU/thermal/memory/power and other domain backends where
  valid; publish a per-domain support matrix and preserve safe read-only/unsupported behavior
  for unavailable Android/root/vendor capabilities. Do not promise that ADPF controls other apps.
- [ ] **ATLAS-08**: Settings exposes discovery progress, per-feature reasons and retry, then a
  last-resort support-report suggestion after safe stages finish, with Arabic/English parity,
  RTL/accessibility and no new primary navigation destination.
- [ ] **ATLAS-09**: Support reports contain minimized versioned evidence, selected app diagnostics
  and failure reasons; preview/redact/consent precede user-triggered local share. Exclude secrets,
  stable personal identifiers and unrestricted logs/files. Never auto-upload.
- [ ] **ATLAS-10**: A maintainer can take a sanitized report, create a regression fixture, add
  narrowly matched reviewed support, validate it and ship through the existing release process.
  New support must be rejectable/revertible without remotely executing downloaded code.
- [ ] **ATLAS-11**: Deterministic tests cover fallback, units, stale evidence, permissions,
  cancellation, privacy and no-write discovery. Device matrix includes unknown vendor, no-root,
  Qualcomm, MediaTek and Exynos/Tensor cases; hardware claims require actual device evidence.
- [ ] **ATLAS-12**: Discovery has explicit time/node/byte/concurrency limits, runs off main thread,
  does not recursively scan the entire filesystem, and stops cleanly when denied or cancelled.

## Traceability

| Requirement | Phase | Status |
| --- | --- | --- |
| ATLAS-01 | Phase 1 | Pending — partial evidence: P1 catalog schema, provenance and seed entries (15 entries after P10's `cpu.info.cpuinfo`), P10 device identity from declared-public surfaces only, P12 attaches lifetime to the freshness axis |
| ATLAS-02 | Phase 1 | Pending — partial evidence: P1 unknown-vendor fallback, reinforced by P10 (declared identity can only *add* vendor candidates, never remove one, and unknown text yields no hint, so an unknown device keeps every generic entry); providers land in P3/P4 |
| ATLAS-03 | Phase 1 | Pending |
| ATLAS-04 | Phase 1 | Pending — partial evidence: P1 no-authority guard, P2 boundary is read-only by construction (the transport interface has no mutating call, and the guard scans the boundary file too) |
| ATLAS-05 | Phase 1 | Pending — partial evidence: P1 orthogonal axes and unit/list rules, P2 preserves all 11 causes and never guesses a unit, P4 keeps battery dimensions separate, thermal scales source-declared, a malformed PSI value null with its raw text and privilege unverified without the control plane; P12 separates "still about now" from "still true" and gives staleness a **reason** (`AtlasStaleness`: expired by time · superseded by boot · superseded by privilege · unmeasurable clock) instead of one boolean — but `AtlasFailure.STALE` still has **no emitter**, and its rightful one is P5's resolver, because the ledger documents that staleness is not a read failure |
| ATLAS-06 | Phase 1 | Pending — partial evidence: P12 supplies the rules (per-volatility lifetimes, reason-preserving staleness, per-cause retry, invalidation on a boot or privilege change, cancellation never remembered) but **not the cache**: the store that uses them is P5, so nothing is persisted yet |
| ATLAS-07 | Phase 1 | Pending — partial evidence: P4 builds a support matrix that cannot omit a domain, with every gap carrying a reason code (`no-source-wired`, `other-provider`, `partial-support`, `no-observation`, `unverified-identity`); the per-domain device matrix still needs P3 and real-device reads |
| ATLAS-08 | Phase 1 | Pending |
| ATLAS-09 | Phase 1 | Pending |
| ATLAS-10 | Phase 1 | Pending |
| ATLAS-11 | Phase 1 | Pending — partial evidence: P0 harness and its negative controls |
| ATLAS-12 | Phase 1 | Pending — partial evidence: P0 deadline/entry-budget tests, P2 enforces operation/job deadlines, entry, byte and operation budgets **before** work and stops cleanly when a limit is reached, T2.4's concurrency cap is atomic (increment-then-check with rollback), and P12 plans which probes a job runs over the same budget with a skip reason per probe; device timing and off-main-thread execution are still open |

Requirements are not marked complete by creating plans. Scope expansions such as new rootless
privilege transports, Android API/ABI expansion, remote updates and autonomous mutation experiments
need their own explicit design and approval; provider seams must not prevent such future work.
