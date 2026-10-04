# Current spoof audit — IDENTITY-01 (2026-10-03)

Status: implementation in progress; not a claim of completed device spoofing.

## Measured paths

`MaxNavGraph → SpoofStudioScreen → SpoofProfilesSection` owns `remember` state, constructs `SpoofProfileStore`, loads/saves `spoof_workspace.txt`, and performs validation in a private editor. `SpoofStudioViewModel` only wraps `SpoofCopgBackend`; AppSettings has no spoof client. Consequently two independent screens cannot observe a single configuration stream.

`SpoofCopgBackend → HardwareControlArbiter → RootFileAccess` writes COPG JSON and compares its signature. This is configuration verification, NOT process identity verification. The backend ignores chmod/chcon results, can plan an absent config, and captures its restore baseline only in memory. Engine detection is metadata, not proof of injection. COPG package tags and foreign package conflicts require explicit handling.

## KEEP / REFACTOR / REPLACE

| Component | Decision | Reason |
|---|---|---|
| SpoofProfile / workspace codec | REFACTOR | Preserve user profiles and migrate v1/v2, add inheritance without a second store |
| SpoofProfileStore | KEEP behind repository | AtomicFile + compare-and-save, bounded decoding |
| SpoofCopgContract | REFACTOR | Documented external interface; preserve foreign keys, reject competing package mappings |
| SpoofCopgBackend | REFACTOR | Existing canonical arbiter/ownership path, strengthen environment checks |
| Old private editor | REPLACE | One reusable editor for both entry points |
| SpoofProfilesSection | REPLACE | UI-owned store/state is the wrong ownership boundary |
| SpoofTransferSection | KEEP | Existing SAF bounded import/export; route mutations through repository |
| Barrier / apply / surfaces | KEEP where relevant | Do not silently drop consent or existing explanations during migration |

The reference screenshot mentioned in the prompt was not attached and has not been visually inspected. AppSettings source and ui/design are the visual references.

## Non-negotiable limits

Build APIs inside MaxManager are observations of this process, not certified real hardware values when an external global spoofer is present. A matching engine file is not target-process read-back. Global resetprop affects other processes; no silent per-app-to-global fallback. A disabled per-app policy under global spoofing requires an isolation adapter. Sensitive identifiers and safety-critical hardware remain unavailable/never-touch, not fabricated features.
