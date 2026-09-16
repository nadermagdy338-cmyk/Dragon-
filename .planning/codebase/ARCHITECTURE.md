# Architecture

**Analysis Date:** 2026-09-16

## Pattern Overview

**Overall:** Polyglot Android root module — Compose single-activity app + native daemons, distributed as a systemless Magisk module

**Key Characteristics:**
- Four cooperating executables: Kotlin app (UI + control plane), C daemon (archdaemon), Rust daemon (thermalcore), Rust CLI utilities (binprofiles/binutils)
- Root-mediated kernel tuning; app is a privileged dashboard and control plane, daemons do the persistent work
- Property-driven configuration: module state lives in Android system props + `/data/adb/.config/MaxManager`, not a database
- Chipset-aware: per-SoC logic in Rust (`binprofiles/src/chipsets/{snapdragon,mediatek,exynos,tensor,unisoc}.rs`) and Kotlin (`MtkUtils.kt`, `XiaomiVendorFeatures.kt`)

## Layers

**UI Layer (Compose):**
- Purpose: dashboards, per-app configuration, terminal, diagnostics screens
- Contains: `ui/mainscreens/*` (Home, Tweak, Control, Applist, MaxAI, MaxLive, Settings, Diagnostics), `ui/component/` (36 reusable composables), `ui/subscreens/`, `ui/mtk/tabs/`
- Depends on: ViewModels, design system
- Used by: `MainActivity.kt` via `ui/navigation/MaxNavGraph.kt` + bottom bar `MaxNavBar.kt`

**ViewModel Layer:**
- Purpose: screen state, root-command orchestration from UI
- Contains: 22 ViewModels in `ui/viewmodel/` (HomeDashboard, CpuCoreControl, GpuStudio, Zram, Per-app stuff, etc.)
- Depends on: core layer (hardware, maxai), libsu shell
- Used by: Compose screens (lifecycle-runtime-compose collection)

**Core Control Plane:**
- Purpose: hardware writes with arbitration, safety, and ownership semantics
- Contains: `core/hardware/` (24 files: HardwareControlArbiter, ManualControlLocks, ProfileApplier, PerAppFrequencyController, DriftGuard, PredictiveSafety...), `core/maxai/` (17 files: MaxAiEngine, SafetyGovernor, TrustModel, MinimalPlanner, journaling)
- Depends on: RootFileAccess (libsu), DI modules
- Used by: ViewModels, services

**Native/Bridge Layer:**
- Purpose: ML predictor + recommendation engine via JNI
- Contains: `core/jni/PredictorBridge.kt` (RL agent, thermal prediction, digital twin), `ContextBridge.kt`; native lib built in CI
- Depends on: `libmaxmanager_native.so` (gitignored, CI-built)
- Used by: MaxAiEngine, recommendation engine

**System Daemons (out-of-app):**
- Purpose: always-on work without an Android process
- Contains: `archdaemon/jni/` — sys.maxmanager-service (AppLoader, GamePreload, PidTracker, BypassCharge, SystemProfile, InotifyHandler, BinaryCLI, ConfigHandler); `thermalcore/src/` — thermal policy daemon (monitor, policy_manager, learning, prediction, cooling); `preloadbin/jni/` — game library preloader
- Depends on: root, inotify, proc/sysfs
- Used by: init rc (`android/aosp/maxmanager.rc`), module scripts

**Module Scripts Layer:**
- Purpose: install-time and boot-time module behavior
- Contains: `mainfiles/customize.sh` (systemless install, priv-app placement, defaults), `service.sh`, `post-fs-data.sh`, `action.sh` (launches service binary), `preferenced-tweaks.sh` (58+ tweak props), `props.sh`, `verify.sh` (self-check), `uninstall.sh`
- Depends on: Magisk/KernelSU module runtime
- Used by: root manager at install/boot

## Data Flow

**Manual profile apply (user taps Performance):**
1. UI → ViewModel (e.g., `TweakViewmodel.kt`)
2. ViewModel builds control request → `core/hardware/HardwareControlArbiter.kt`
3. Arbiter journals intent, resolves winner via OS lock, verifies publication (read-back)
4. `ProfileApplier.kt` / backends (`CpuHardwareBackend`, `GpuHardwareBackend`, `ZramHardwareBackend`) write sysfs via `RootFileAccess.kt`
5. `DriftGuard.kt` re-checks applied values; state visible in UI via ViewModel Flows

**MaxAI adaptive control loop:**
1. `DeviceStateCollector.kt` samples device state
2. `MaxAiEngine.kt` (unified smart engine) evaluates: SafetyGovernor → MinimalPlanner → TrustModel credibility weighting
3. JNI `PredictorBridge` provides thermal prediction + RL policy decision
4. Control requests pass through the same HardwareControlArbiter (never bypasses manual locks: `ManualControlLocks.kt`)
5. Outcomes journaled to `MaxAiJournal.kt` (codec-tested), feeding `CredibilityStore.kt` learning

**Game detection → auto profile:**
1. Foreground change observed (`AppMonitor.kt` / archdaemon PidTracker)
2. Per-app settings resolved from `maxmanagerApplist.json` defaults + user overrides (`PerAppControlRegistry.kt`)
3. `binprofiles` binary applies chipset-specific profile (Rust `chipsets/`)
4. GamePreload (archdaemon) preloads game libraries

**State Management:**
- No database. Android system props = source of truth for tweaks; files under `/data/adb/.config/MaxManager` for per-app + module config
- In-app: StateFlow in ViewModels; SharedPreferences for app-internal journals
- Cross-process ownership via `SharedHardwareOwnershipStore.kt` + journaled arbiter

## Key Abstractions

**HardwareControlArbiter (`core/hardware/HardwareControlArbiter.kt`):**
- Purpose: single choke-point for every hardware mutation; prevents MaxAI from stomping manual settings
- Pattern: process-shared arbiter with journaling + read-back verification (architecture enforced by `ControlPlaneArchitectureTest.kt`)

**ControlOwnership / ManualControlLocks:**
- Purpose: ownership model (manual user vs MaxAI vs profile) over control keys; manual always wins when locked
- Pattern: explicit lock store, tested in `ManualControlLocksTest.kt`, `HardwareControlArbiterTest.kt`

**HardwareCapabilityResolver:**
- Purpose: probe what the kernel actually exposes before showing controls
- Pattern: capability discovery → UI shows only applicable knobs

**ProfileApplier + binprofiles chipsets:**
- Purpose: chipset-abstracted profile (Performance/Balanced/ECO) application
- Pattern: strategy per SoC family

**MaxAI engine components (`core/maxai/`):**
- Purpose: bounded autonomous tuning with safety gates and learning from outcomes
- Pattern: plan → govern → apply → measure → credit/debit trust

## Entry Points

**App UI:** `manager/app/src/main/java/nd/max/MainActivity.kt` — single activity, Compose nav
**App boot:** `MaxManagerApplication.kt` — Hilt app; `AppMonitor.kt` starts foreground watching
**Module install:** `mainfiles/customize.sh` (SKIPUNZIP=1, manual extraction, priv-app placement)
**Module boot:** `mainfiles/service.sh` → `action.sh` → `system/bin/sys.maxmanager-service` (archdaemon); thermalcore started alongside
**Quick tile:** `TileService/` — quick settings tile control
**Root service:** `service/MtkRootService.kt` (AIDL `IMtkService.aidl`) — MTK-specific binder surface

## Error Handling

**Strategy:** Defense in depth — verify after write, journal before write, lock before mutate

**Patterns:**
- Read-back verification in arbiter (applied ≠ actual → surfaced, restore attempted)
- `DriftGuard` detects external drift of controlled values
- `PredictiveSafety.kt` gates risky control changes
- Shell failures non-fatal; capability probing avoids unsupported writes
- Build fails fast on missing signing config (release)

## Cross-Cutting Concerns

**Root access:** libsu 6.0.0 everywhere; every privileged operation funnels through `RootFileAccess.kt` in the app, or native binaries
**Diagnostics:** `DiagnosticCenter.kt` centralizes self-diagnostics; `EventLog` util; daemon `SystemLogger`
**Localization:** 1656 strings, ~70+ locales via Crowdin (`crowdin.yml`) — all UI text must use string resources
**Obfuscation:** StringFog XOR on release; R8 minify + resource shrink
**SELinux:** shipped policy `android/aosp/sepolicy/maxmanager.te` — daemon domains and file contexts
**Threading:** `core/threading/`; engine loops on SupervisorJob coroutines with Mutex-protected sections

---

*Architecture analysis: 2026-09-16*
*Update when major patterns change*
