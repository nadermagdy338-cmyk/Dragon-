# Max AI Control Plane — Architecture Specification

Status: approved by owner (build directive: "افعل ما تراه مناسب")
Supersedes: the profile-switching decision model in `MaxAiEngine.executeDecision`.

## 1. First principle

Max AI's vocabulary is **not a list of action names**. It is the set of
**control knobs discovered on this device**. The mind never "picks a profile";
it proposes the smallest verified change to a real knob through one mandatory
ownership gate.

Everything below follows from that single sentence.

## 2. Why the current design fails (evidence, not opinion)

| Evidence | Consequence |
|---|---|
| `executeDecision` dispatches on Arabic string labels ("رفع التردد") | 8 fixed actions; no new combination can ever be expressed |
| 6 of 8 actions end in `ProfileApplier` | Max AI *is* a profile switcher today |
| Device log: `decision failed: رفع التردد :: policy0=فشل(...)` | Coarsest possible action attempted first; no gradation was ever tried |
| Only 5 files reach `HardwareControlArbiter`; dozens call `RootFileAccess.write` directly | The central gate is optional — an optional gate is not a gate |
| `SafetyEngine` applies eco **via `ProfileApplier`** | Safety rides a channel that can fail = illusory safety |
| `am kill-all` is an AI action | Hostile, irreversible, unverifiable |

## 3. Layers (each builds on what already exists)

```
        ┌──────── SafetyGovernor (veto before + after every write) ────────┐
        │                                                                  │
[DeviceState] → [Objective] → [MinimalPlanner] → [Arbiter] → [Backends]
                      ↑              ↑                 ↓
               [user weights]  [ControlRegistry]  [Verify → Keep/Rollback]
                                     ↑                 ↓
                          [HardwareCapability]   [CredibilityStore]
```

### 3.1 ControlRegistry (new — the vocabulary)
Every writable knob registers itself as an object: key, required
`HardwareFeature`, allowed value ladder, direction (raises performance /
saves energy), change cost, and a **verified** apply/read pair.
Built from `HardwareCapabilityResolver`, so an unsupported knob is **absent
from the vocabulary** rather than filtered later. (Answers #16.)

### 3.2 Objective (new — goal, not mode)
Three measured weights: `performance`, `battery`, `thermalHeadroom`.
Performance/Balanced/Eco become weight presets, not profiles. (Answers #2.)

### 3.3 MinimalPlanner (new — smallest sufficient intervention)
Measures the gap between state and objective, ranks candidates by
`expectedImpact ÷ cost × credibility`, then applies **one knob at the
smallest sufficient step**. Does nothing when there is no measured gap.
(Answers #8 and #13 — a principle, not an option.)

### 3.4 SafetyGovernor (new wrapper — double veto)
Wraps the arbiter: rejects intents before the write, and forces rollback
after it when thermals rise. Safety performs **surgical reduction**, never a
profile switch. (Answers #15.)

## 4. The 25 decisions (binding)

1. Independent mind over real knobs; profiles are presets only.
2. Objective weights, not modes. Eco = battery 0.7.
3. Per-knob ownership matrix; Manual locks only what it touches.
4. Arbiter is the mandatory central owner, protected by an architecture test.
5. Manual = baseline + lock; the rest is the mind's space.
6. Transactional restore of each touched knob's captured baseline.
7. Per-App stays above AI (60 > 40); the mind works in the remaining space.
8. State → Objective → Candidates → MinimalPlan → Verify → Learn.
9. Profiles survive as preset/baseline/fallback only — never as vocabulary.
10. Both: one question at enable + weight adaptation from user corrections.
11. Screen-off continues with a **different objective** (battery + thermal only).
12. Two loops: fast reactive (~1s, events) + slow learning (~30s).
13. Minimum intervention is a core principle.
14. Full transaction per knob (the GPU Studio contract, already proven here).
15. SafetyGovernor with pre+post veto and surgical reduction.
16. Capabilities generate the vocabulary; unsupported = nonexistent.
17. Credibility per (device, app, knob, direction); two failures demote, never ban.
18. App history is a prior; current measurement rules on conflict.
19. `ProfileApplier` becomes a `PresetApplier` behind one Control; the mind never calls it directly.
20. `MaxAiController` as a mode is **deleted**; replaced by an ownership snapshot.
21. `PendingManualStore` is **deleted**; the arbiter already retains blocked intents.
22. Transparent panel: state, what it did, why (numbers), verified result, weights in user's hands.
23. "Active Profile" becomes "Base Profile", shown for manual/baseline only.
24. Per-knob lock; anything the user touched manually is locked by default.
25. Keep Ownership/Arbiter/Safety/Capability/Verified/Backends. Redesign vocabulary, loop, learning. Merge Adaptive+Ceiling+Reward into the planner. Delete Controller/Pending/text classification. Add ControlRegistry + credibility.

## 5. Non-negotiable invariants

- **INV-1** No component writes hardware for policy reasons except through the arbiter.
- **INV-2** AI can never outrank safety (`MAX_AI=40 < SAFETY=80`).
- **INV-3** A knob the user locked is never written by the mind.
- **INV-4** No action without a measured gap; no reward without a measured outcome.
- **INV-5** Unsupported capability ⇒ knob absent from the vocabulary.
- **INV-6** A contested knob (external owner keeps winning) is abandoned, not retried forever.
- **INV-7** No hostile or irreversible action (`am kill-all` removed).

## 6. Acceptance criteria

1. `MaxAiEngine` contains zero Arabic action-label dispatch.
2. The planner can express a knob-level change that no profile can express.
3. Unsupported knobs never appear as candidates on a device lacking them.
4. Safety reduces frequency directly, without `ProfileApplier`.
5. Every applied step has a captured baseline and a verified read-back.
6. Repeated failure on one knob demotes it below alternatives.
7. Screen-off produces only battery/thermal intents.

## 7. Predictive layer (added after measurement)

`ControlOutcomeModel` learns from **experience** — the mind must still try a
knob on the user's device to know its effect. `ResponseModel` removes that cost
by learning the **response function**: how a relative knob change maps to
Δobjective and Δthermal, conditioned on device state.

Design law enforced by measurement:

> **feature vector has no constant term** — a zero step must predict zero
> effect. Including a constant both breaks that physical constraint and makes
> features collinear when step size is fixed (~constant ≈ step term).

Simulated on the same data: removing the constant cut prediction error
**61%** (0.0893 → 0.0352), and conditional discrimination (high-load gain vs
low-load gain) went from **1.06× to 4.0×**.

Additional measured choices:
- **NLMS normalization** (`/ ‖f‖²`) — prevents exploding steps when features
  are large; convergence independent of input scale.
- **slow LR decay** (`0.6/(1+0.01n)`, floor 0.05) — the naive fast decay froze
  weights before the interaction terms were learned.

Learned unit is `stepFraction` (step ÷ ladder span), **not absolute MHz**, so
knowledge transfers between devices with different clock ladders — verified by
simulation (1.0→1.8 GHz and 1.5→2.8 GHz both = 0.333 span).

Honesty gate: no prediction before `MIN_SAMPLES_FOR_TRUST` observations —
`predict()` returns null and the planner falls back to optimistic experience.
