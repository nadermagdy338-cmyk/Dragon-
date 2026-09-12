# GPU Reality Studio Implementation Plan

**Goal:** Replace separate Mali/Adreno GPU pages with one capability-driven, verified GPU Studio.

**Architecture:** `GpuHardwareBackend` is the sole GPU discovery, telemetry, and mutation owner. A unified ViewModel stages previews/session state. A unified Compose screen renders only proven capabilities. Legacy routes remain aliases during migration.

**Baseline:** `docs/aegis/specs/2026-09-11-gpu-reality-studio-design.md` and current GPU backend/screens/ViewModels/navigation/AppMonitor.

**Compatibility:** Keep `maligpufreq` and `adrenogpufreq` route aliases. Preserve existing `devices()`, `setGovernor()`, and `clamp()` callers via verified delegates. Do not persist session changes automatically or modify thermal protection.

**TDD Route:** mode `off`; decision `skipped`; strict authority `not applicable`; test posture `post-change regression`; Java is currently unavailable, so run source contracts now and JUnit/compile once JDK 17 exists.

**Execution Route:** inline; backend, ViewModel, UI, and caller migration share one evolving contract. No user confirmation is required because the approved design and compatibility boundary are explicit.

## Task 1 — Harden canonical GPU backend
**Modify:** `manager/app/src/main/java/nd/max/core/hardware/GpuHardwareBackend.kt`
**Create:** `manager/app/src/test/java/nd/max/core/hardware/GpuControlModelTest.kt`

1. Add vendor family, access, snapshot, request, baseline, and transaction-result models.
2. Select only GPU-evidenced devfreq nodes; surface ambiguous/no-provider states safely.
3. Normalize frequencies consistently and retain missing values as null.
4. Add pure smart-mode resolution from ordered advertised OPPs.
5. Add request preflight, invariant-preserving write order, read-back verification, and exact rollback.
6. Preserve existing public compatibility methods as delegates.
7. Add unit tests for mode resolution, validation, and write ordering.

Verify with focused tests and source assertions against guessed OPP/zero fallbacks.

## Task 2 — Add unified session ViewModel
**Create:** `manager/app/src/main/java/nd/max/ui/viewmodel/GpuStudioViewModel.kt`

1. Load canonical snapshot and capture baseline once.
2. Poll atomic snapshots and retain only valid history samples.
3. Stage modes, range, lock, and governor without writing.
4. Apply through backend and expose verification/rollback states.
5. Restore session baseline through backend.
6. Expose explicit persistence only after verified apply through bounded existing property compatibility.

Verify no `Shell`, sysfs paths, or `RootFileAccess` exist in the ViewModel.

## Task 3 — Build GPU Reality Studio UI
**Create:** `manager/app/src/main/java/nd/max/ui/subscreens/GpuStudioScreen.kt`
**Modify:** string resources only as needed.

1. Build Reality Header: identity, nullable frequency/load, range, governor, freshness, provider family.
2. Build three smart intent selectors based on current OPP values.
3. Build Advanced Lab shown only for proven writable capabilities.
4. Build current→requested preview plus Apply/Cancel/Restore/Save actions.
5. Render verified success, restored failure, and rollback failure distinctly.
6. Add diagnostics disclosure without irrelevant vendor controls.
7. Keep layout accessible, explicit, responsive, and independent from supplied reference trade dress.

Verify no direct hardware access and no thermal-bypass UI.

## Task 4 — Migrate navigation and entry points
**Modify:** `manager/app/src/main/java/nd/max/MainActivity.kt`
**Modify:** `manager/app/src/main/java/nd/max/ui/mainscreens/TweakScreen.kt`
**Modify:** `manager/app/src/main/java/nd/max/ui/mainscreens/HomeScreen.kt`

1. Add canonical `gpustudio` route.
2. Route both legacy aliases to the unified screen.
3. Replace duplicate Tweaks entries with one GPU Studio entry.
4. Make Home navigation capability-neutral; family text remains informational.

Verify registrations/callers and ensure legacy screen composables are no longer active routes.

## Task 5 — Ownership/regression review
**Inspect:** changed files, `AppMonitor.kt`, `PerAppKernelUtil.kt`; modify only for real contract mismatches.

1. Check existing callers against compatibility delegates.
2. Confirm no duplicate active page owner or direct ViewModel/UI sysfs access.
3. Confirm thermal protection remains untouched.
4. Run `git diff --check`, focused source contracts, unit test, and Kotlin compilation when JDK 17 is available.
5. Request focused code review and record residual device-validation risk.

**Retirement:** legacy screens/ViewModels stay unreferenced for one bounded migration release, then are removed with compatibility aliases/properties after evidence. No new fallback is added.
