# GPU Reality Studio — Design Specification

Date: `2026-09-11`
Status: `approved design, awaiting implementation plan`

## Outcome
Replace the separate, error-prone Mali and Adreno GPU pages with one capability-driven GPU Studio. It discovers the actual kernel GPU interface, reports live values without inventing zeros, previews changes before mutation, verifies every write, and restores the session baseline on failure.

The supplied screenshots are conceptual references only. No code, fixed frequency, preset name, vendor branding, layout, or trade dress is copied.

## Product decisions
- One unified route and screen for all supported GPUs.
- The detected provider determines visible controls. Qualcomm-only controls never appear on MediaTek, and MediaTek-only controls never appear on Qualcomm.
- Generic devfreq capability outranks chipset-name assumptions.
- Modes: Efficiency, Adaptive Balance, Sustained Performance; Advanced adds Dynamic Range, Exact Lock, and driver-advertised Governor.
- Preview-then-apply; no mutation during preview.
- Successful changes are session-scoped first; persistence to Tweaks is explicit.
- Kernel thermal protection stays enabled. Thermal state is observable; thermal bypass is retired.

## Canonical architecture
`GpuHardwareBackend` is the sole owner of discovery, unit normalization, telemetry, capability reporting, preflight, mutation, verification, and rollback. UI/ViewModel code consumes structured state and never accesses sysfs directly. Vendor adapters extend the backend only when semantics can be proven and effects read back.

A provider exposes stable id, diagnostic path, proven vendor family, governors, OPP list/unit, live/current/min/max values, load when valid, per-capability access, and optional verified vendor features.

Initial provider layers:
1. Generic devfreq for discoverable GPU devices.
2. Qualcomm/KGSL adapter only for verified KGSL-specific capabilities.
3. MediaTek/Mali adapter only for verified GED or Mali power-policy contracts.

Adapters do not duplicate frequency/governor ownership.

## Discovery rules
- Score devfreq candidates by GPU-specific evidence; never select an arbitrary governor-bearing device.
- Select one canonical GPU provider. Ambiguity means diagnostics only and no mutation.
- Frequencies come from an explicit OPP source such as `available_frequencies`; imposed min/max are not hardware bounds.
- Normalize units consistently across OPP/current/min/max. Mixed or implausible units disable mutation.
- A path or writable file alone is not a capability. Enumerated values and read-back are required where semantics are nonstandard.
- Missing readings remain unknown (`null`), never synthetic `0 MHz` or `0%`.

## Screen
### Reality header
Shows provider identity, live frequency/load when readable, effective range, governor, thermal state/temperature, freshness, and provider confidence. Requested and effective state remain distinct.

### Intent modes
- Efficiency: lower advertised ceiling with dynamic scaling.
- Adaptive Balance: full safe OPP range and compatible dynamic governor when available.
- Sustained Performance: higher advertised floor without disabling thermal protection or invoking unverified vendor modes.

All values resolve against the current ordered OPP table; no fixed MHz values.

### Advanced Lab
Only proven controls appear: Dynamic Range, Exact Lock, driver-listed Governor, verified vendor features, and exact Session/OEM Restore.

### Apply dock
Pending edits show `current → requested`, expected effect, and validation conflicts. Actions: Apply for session, Cancel preview, Restore baseline, and Save verified state to Tweaks. Save is available only after verified apply.

## Mutation transaction
1. Capture exact live baseline.
2. Revalidate provider identity and live capabilities.
3. Validate governor and frequencies against the live set.
4. Order writes so `min <= max` after every write.
5. Apply one backend transaction.
6. Read back every affected value.
7. Report success only when all match.
8. On failure, restore and verify the exact baseline.
9. Distinguish apply failure from rollback failure.
10. Persist only after verified apply and explicit save.

## Telemetry accuracy
One backend snapshot owns each poll. Samples include freshness. Histories accept only valid readings. Load parsing is provider-specific and range checked to `0..100`. UI state updates atomically from one snapshot.

## Error states
- No provider: explain unsupported kernel interface; no vendor controls.
- Read-only: telemetry works, mutation is disabled with a reason.
- No OPP table: hide range/lock; independently proven governor may remain.
- Ambiguous device or units: diagnostics only.
- Failed apply with successful rollback: concise recoverable failure.
- Failed rollback: prominent warning with exact live values; never claim restoration.

## Retirement and compatibility
Retire direct sysfs ownership from `AdrenoGpuViewModel` and `MaliFreqViewModel`, separate product pages, duplicated polling/writes/presets, fixed path authority, unchecked shell writes, and GPU thermal-bypass UI.

Temporarily route `adrenogpufreq` and `maligpufreq` aliases to the unified screen. Existing properties receive a bounded migration only when they map to currently advertised values. Per-app GPU remains compatible but must consume the canonical backend contract. Remove route/property compatibility after internal callers migrate and one release cycle passes.

## Non-goals
No thermal disabling, overclocking, undervolting, unadvertised frequencies, guessed vendor ids, cross-vendor option leakage, reference-UI copying, or per-app product redesign beyond backend adaptation.

## Acceptance criteria
1. One page opens from both old entry points and identifies the actual provider.
2. Displayed OPP/governor values exactly match fixture-advertised values.
3. Qualcomm and MediaTek options never leak across providers.
4. Missing frequency/load renders unavailable, not zero.
5. Preview performs no write.
6. Apply verifies min/max/governor by read-back.
7. Partial failure restores and verifies baseline; rollback failure is distinct.
8. Exact Lock enforces equality; Dynamic Range enforces ordering.
9. Thermal protection is never modified and bypass UI is absent.
10. Session state is not persisted before explicit Save.
11. ViewModel/screen contain no direct sysfs mutation.
12. Tests cover ambiguous discovery, units, unsupported OPP, ordered writes, partial failure, rollback.
13. Diff checks, affected tests, and Kotlin compilation pass when JDK 17 is available.

## Architecture decision signal
The implementation establishes `GpuHardwareBackend` as sole GPU owner, bounded verified vendor adapters, session-first persistence, and retirement of duplicate vendor ownership. Sync these into architecture baseline only after implementation evidence.

## Intent and baseline
Goal: accurate, safe, extensible GPU control across devices the kernel can prove. Evidence: capability fixtures, transaction tests, ownership checks, Kotlin build, and device validation where available. Stop when acceptance passes without guessed values or duplicate writers. Requirement sources are the approved user decisions and current repository behavior; baseline surfaces are the backend, GPU screens/ViewModels, navigation, capability resolver, and AppMonitor.
