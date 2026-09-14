# Max AI Control Plane — Integration Plan

**Approved baseline:** `docs/aegis/specs/2026-09-13-max-ai-control-plane.md`

## Reality correction

The UI app and `AppMonitor` are separate OS processes. Therefore an in-memory Kotlin singleton cannot be the cross-process hardware owner. The canonical authority must be a shared, lock-protected control journal/transaction protocol reachable by both processes (or a bound service); `ControlOwnership` remains only a process cache until migrated.

## TDD Route

- Mode: off
- Decision: skipped
- Strict authority: not applicable
- Test posture: post-change regression plus static architecture assertions
- Verification: Gradle tests when Java is available; deterministic Python/source checks otherwise.

## Task 1 — Make physical-knob identity canonical

Files: `ControlRegistry.kt`, `CpuCeilingKnobs.kt`, `AppMonitor.kt`, new `HardwareControlKey.kt`.

- Define key builders and one value schema for CPU ranges, GPU transactions, and boost.
- Make AI, Safety, and Per-App use the same key for the same node/provider.
- Add a source-level architecture test prohibiting literal divergent keys.

## Task 2 — Make arbiter transactions truthful

Files: `HardwareControlArbiter.kt`, `HardwareControlArbiterTest.kt`.

- Separate pending requests from committed leases.
- Capture baseline from live read on first verified acquisition; reject null baseline.
- Commit lease only after apply+read verification.
- On failure, verified rollback and remove/demote failed request.
- Return rollback/restore status; deterministic same-priority ordering.

## Task 3 — Establish cross-process owner protocol

Files: new hardware control journal/lock owner under `core/hardware`, `AppMonitor.kt`, main-process engine wiring.

- Use an atomic file plus OS file lock under `/data/adb/.config/MaxManager/` as the process-shared ownership snapshot and intent journal.
- AppMonitor publishes only the canonical keys it owns and releases them transactionally.
- Main-process arbiter imports external leases before every submit/reconcile.
- Add stale-owner expiry based on PID/start token; never silently treat stale data as active ownership.

## Task 4 — SafetyGovernor wrapper and fast loop

Files: new `SafetyGovernor.kt`, `SafetyEngine.kt`, `MaxAiEngine.kt`, application lifecycle owner.

- Pre-veto each AI transaction.
- Execute through arbiter.
- Post-read thermals and rollback through arbiter on unsafe rise.
- Run safety collection independently at ~1 s; remove profile application from safety.
- Keep surgical canonical controls only.

## Task 5 — Complete knob-level planner learning

Files: `MinimalPlanner.kt`, `ControlOutcomeModel.kt`, `CredibilityStore.kt`, `MaxAiEngine.kt`, `Objective.kt`.

- Rank expected impact / cost × credibility.
- Observe objective delta and thermal delta per device/app/knob/direction.
- Demote after two failures with cooldown/recovery; never permanent-ban.
- Remove obsolete fixed-action native reward from this path.
- Enforce screen-off objective axes.

## Task 6 — Ownership snapshot and manual locks

Files: `MaxAiModels.kt`, `MaxAiEngine.kt`, `MaxAiViewModel.kt`, `MaxAiScreen.kt`, manual hardware screens.

- Replace global controller mode with per-knob ownership snapshot.
- Manual action captures baseline and locks only touched knob.
- Remove global Per-App early return; planner operates on unowned controls.
- Migrate UI, then retire `PendingManualStore` and obsolete controller semantics.

## Task 7 — Capability descriptors and adapters

Files: capability resolver, registry, CPU/GPU backends.

- Emit per-knob read/write/ladder proof, not feature-level ANY.
- Normalize fixed-index GPU effective readback through backend adapter.
- Unsupported controls are absent.

## Task 8 — Retirement and acceptance

- Remove hostile AI/control-plane actions and direct policy repair outside canonical transactions.
- Add architecture scans: no policy `RootFileAccess.write`, no policy `ProfileApplier`, no ad-hoc arbiter construction outside tests/owner.
- Tests: failure rollback, rollback failure, handoff, equal priority, cross-process lease import, safety post-veto, manual lock, contested recovery, screen-off, unsupported capability.
- Run `git diff --check`, Gradle unit/compile when Java exists, and on-device verification when control channel is authorized.

## Stop / rewind

Stop and revisit architecture if the file-journal protocol cannot provide atomic lease import + mutation under one OS lock, or if AppMonitor cannot share the same backing path/identity. Do not patch around this with another in-memory owner or polling flag.
