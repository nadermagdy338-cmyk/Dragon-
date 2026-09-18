# Architecture

**Analysis Date:** 2026-09-18 (rebuilt from the current tree)

## Pattern overview

**Polyglot rooted Android module**: one Compose single-activity app (`nd.max`) as the control surface, plus
three cooperating native executables that do the persistent on-device work, shipped as a systemless
Magisk/KernelSU module.

Four structural ideas carry the whole system:

1. **One write path** — every hardware mutation funnels through `core/hardware/HardwareControlArbiter.kt`.
2. **Capability before control** — nothing is surfaced until `HardwareCapabilityResolver` proves the node exists.
3. **One navigation spine** — routes live in `ui/navigation/MaxDestinations.kt`; no literal `navigate("…")` elsewhere.
4. **One design language** — `ui/design/` (11 files) is the only design system for anything new.

These are architectural invariants, not preferences; the binding rules live in `docs/ai/DECISIONS.md`
(ADR-01…20) and are enforced by `core/hardware/ControlPlaneArchitectureTest.kt` plus the grep gates in
`docs/ai/VALIDATION.md`.

## Layers

**Navigation spine (`ui/navigation/`, 5 files)** — `MaxDestinations.kt` is the single source of route truth
(id, title res, icon, parent, risk level, deep-link key); `MaxDestinationCatalog.kt` + `MaxNavActions.kt` hold
metadata and typed actions; `MaxNavGraph.kt` registers every route; `MaxNavBar.kt` renders the bottom bar
(Now / Control / Apps / Max AI). `MainActivity.kt` is 444 lines with no inline composable registrations.

**UI layer (Compose)** — `ui/mainscreens/` (primary destinations), `ui/subscreens/` (feature screens hosted
from the domain hubs), `ui/component/` (38 shared composables, legacy design system in runoff; the duplicate
`ui/components/` package was merged into it on 2026-09-18), `ui/design/` (the design
language: tokens, structure, screen scaffold, control rows, metrics, domain cards, conditions, dialogs, help,
view menu, Max AI cinematics), `ui/theme/`, `ui/terminal/`, `ui/activitylauncher/`.

**ViewModel layer (`ui/viewmodel/`, 22 files)** — screen state + root-command orchestration via
`StateFlow`; depends on `core/**` and libsu; never writes hardware itself.

**Core control plane (`core/hardware/`, 24 files)** — the arbiter plus per-domain backends, ownership/locks,
capability resolution, drift detection, predictive safety, per-app registry and recovery, `RootFileAccess`
(the libsu boundary), `SharedHardwareOwnershipStore` (cross-process ownership).

**Core Max AI (`core/maxai/`, 17 files)** — `MaxAiEngine` (unified loop) with `SafetyGovernor`/`SafetyEngine`,
`MinimalPlanner`, `TrustModel`/`CredibilityStore`, `ControlRegistry` + `ControlOutcomeModel` (canonical knobs),
`MaxAiJournal` + `MaxAiJournalCodec` (audit ledger), `MaxAiInsights` (learning verdicts), `Objective`,
`ResponseModel`, `DynamicIntentLearner`, `CpuCeilingKnobs`.

**JNI bridge (`core/jni/`, 2 files)** — `PredictorBridge` (RL policy, thermal prediction, digital twin) and
`ContextBridge`, backed by `libmaxmanager_native.so` (built in CI, gitignored). All JNI stays here.

**Native executables** — `archdaemon/jni/` (C `sys.maxmanager-service`: AppLoader, GamePreload, PidTracker,
BypassCharge, SystemProfile, InotifyHandler, BinaryCLI, ConfigHandler, SystemLogger, utilities, StartupInit),
`thermalcore/src/` (Rust thermal policy daemon with a `simulator` cargo feature), `binprofiles` (chipset
strategies: snapdragon, mediatek, exynos, tensor, unisoc), `binutils`, `preloadbin` (C game-lib preloader).

**Module scripts (`mainfiles/`, 8 scripts)** — `customize.sh` (SKIPUNZIP=1 + manual extraction, priv-app
placement), `service.sh`, `post-fs-data.sh`, `action.sh`, `preferenced-tweaks.sh` (property tweaks),
`props.sh`, `verify.sh`, `uninstall.sh`; `system/` holds the systemless overlay and `META-INF/` the installer.

## Data flow

**Manual profile / single-knob apply**
1. UI → ViewModel (`TweakViewmodel`, `GpuStudioViewModel`, …).
2. ViewModel builds a request → `HardwareControlArbiter` (journal intent → resolve ownership via
   `ControlOwnership` + `ManualControlLocks` + `SharedHardwareOwnershipStore`).
3. `ProfileApplier` / backends (`CpuHardwareBackend`, `GpuHardwareBackend`, `ZramHardwareBackend`) write via
   `RootFileAccess` (libsu).
4. Read-back verification compares applied vs actual; mismatch surfaces and rollback is attempted.
5. `DriftGuard` re-checks later; state returns to the UI through ViewModel flows.

**Max AI adaptive loop**
1. `DeviceStateCollector` samples state (with `ContextData` for per-app context).
2. `MaxAiEngine` evaluates: `SafetyGovernor` veto → `MinimalPlanner` proposal (with `Objective`) →
   `TrustModel`/`CredibilityStore` credibility weighting.
3. `core/jni/PredictorBridge` contributes thermal prediction + policy decision.
4. Requests re-enter the **same** arbiter — Max AI can never bypass manual locks.
5. Outcomes are journaled (`MaxAiJournal`, codec-tested) and folded back into learning
   (`ControlOutcomeModel` → `CredibilityStore` → `MaxAiInsights` verdicts).

**Game detection → auto profile**
1. Foreground change observed (`AppMonitor.kt` in-app, `PidTracker` in archdaemon).
2. Per-app settings resolved from `maxmanagerApplist.json` defaults + user overrides (`PerAppControlRegistry`).
3. `binprofiles` applies the chipset-specific profile; `GamePreload` preloads game libraries;
   `AdaptiveProfileEngine` + `PerAppFrequencyController` adjust while running.

## State management

- **No database in the app.** System properties + files under `/data/adb/.config/MaxManager` are the source of
  truth for tweaks and per-app config; `maxmanagerApplist.json` ships curated defaults.
- In-app: `StateFlow` in ViewModels; SharedPreferences for internal journals (`PerAppRecoveryStore`,
  `GpuTweakPersistence`, `CredibilityStore`).
- Cross-process ownership is explicit: `SharedHardwareOwnershipStore` + the journaled arbiter.

## Key abstractions

| Abstraction | File | Why it exists |
| --- | --- | --- |
| Single write choke-point | `core/hardware/HardwareControlArbiter.kt` | prevents Max AI from stomping manual settings; journals + verifies |
| Ownership model | `ControlOwnership.kt`, `ManualControlLocks.kt` | per-knob ownership; manual intent wins (no global "controller mode") |
| Capability ≠ state | `HardwareCapabilityResolver.kt`, `HardwareCapability.kt` | only expose controls the kernel actually provides |
| Domain strategy | `ProfileApplier.kt` + `binprofiles/src/chipsets/*` | one profile applied per SoC family |
| Bounded autonomy | `core/maxai/` split | plan → govern → apply → measure → credit/debit trust |
| Planned vs actual | `VerifiedControl.kt`, `DriftGuard.kt` | published value must equal reality, or be reported |

## Entry points

| Entry | Path |
| --- | --- |
| App UI | `MainActivity.kt` → `ui/navigation/MaxNavGraph.kt` |
| App boot | `MaxManagerApplication.kt` (Hilt), `AppMonitor.kt` |
| Module install | `mainfiles/customize.sh` |
| Module boot | `mainfiles/service.sh` → `action.sh` → `system/bin/sys.maxmanager-service` |
| Native daemons | `archdaemon/jni/Main.c`, `thermalcore/src/main.rs` |
| Quick tiles | `TileService/ProfileTileService.kt`, `BypassChgTileService.kt` |
| Binder surface | `service/MtkRootService.kt` + `src/main/aidl/nd/max/IMtkService.aidl` |

## Error handling

**Strategy:** defense in depth — probe before write, lock before mutate, journal before apply, verify after.

- Read-back verification in the arbiter; mismatch is surfaced and rollback attempted.
- `DriftGuard` detects external drift of controlled values.
- `PredictiveSafety` gates risky changes; `SafetyGovernor` can veto.
- Unreadable sysfs returns null rather than throwing; capability probing avoids unsupported writes.
- Fail-fast is reserved for build config (missing `KS_PWD` for release signing).
- Rust: `Result` + `nix` error types; the daemon loop continues past per-device failures.
- Shell: guard clauses (`[ ! -f "$2" ] && …`) and idempotent helpers (`make_node`).

**Truthfulness rule:** a number without freshness + source is a defect; unknown renders as `status_unknown`,
never as a plausible fallback (ADR-07). Synthesizing telemetry is blocking, not cosmetic.

## Cross-cutting concerns

- **Root access**: libsu 6.0.0 everywhere; app-side privileged I/O only through `RootFileAccess.kt`; daemons use
  their own native paths.
- **Localization**: 84 `values-*` folders (~100 locales via Crowdin). Every new string needs `values/` **and**
  `values-ar/` in the same change (ADR-14). Six string files ship in `values/` (`strings` 1,629 keys,
  `max_ai` 220, `max_screen` 177, `max_navigation` 54, `max_design` 11, `studio` 9) and all six are registered in
  `crowdin.yml` (**ADR-26**, added 2026-09-18), and **all six have a `values-ar/` counterpart** since
  2026-09-18 (the 188 keys that had no Arabic file are translated). Remaining work is coverage inside
  `strings.xml`: 1,629 EN vs 844 AR.
- **Accessibility/RTL**: motion must not imply LTR direction; disabled controls must state `lockedReason`.
- **Obfuscation**: StringFog (XOR) on release, R8 minify + resource shrink.
- **SELinux**: shipped policy `android/aosp/sepolicy/maxmanager.te`; init rc `android/aosp/maxmanager.rc`.
- **Threading**: `core/threading/`; engine loops on `SupervisorJob` coroutines with Mutex-protected sections;
  no blocking work on the main thread.
- **Diagnostics**: `core/diagnostics/DiagnosticCenter.kt` + `ui/util` event log; daemons log via `SystemLogger`.
- **Performance budget**: frame/battery cost is a product concern — decorative engines are opt-in and off by
  default (ADR-12).

## Changelog

- 2026-09-18 — rebuilt: navigation spine now documented as a first-class layer, `ui/design/` described as the
  single design system, `ui/mtk/` removed from the flow (MTK now rows in domain hubs), localization count
  corrected to 84 folders with the 1,629/844 key gap, and `MaxAiInsights`/`ControlRegistry` added to the loop.

<details>
<summary>Evidence</summary>

```sh
S=manager/app/src/main/java/nd/max
find $S/core/hardware -name '*.kt' | wc -l          # 24
find $S/core/maxai -name '*.kt' | wc -l             # 17
find $S/ui/viewmodel -name '*.kt' | wc -l           # 22
grep -rl 'nd.max.ui.design' $S/ui | wc -l           # 30 files import the design language
grep -rl 'Scaffold(' $S/ui | wc -l                  # 25 files still declare their own
ls -d manager/app/src/main/res/values-* | wc -l     # 84 locale folders
grep -c '<string ' manager/app/src/main/res/values/strings.xml     # 1629
grep -c '<string ' manager/app/src/main/res/values-ar/strings.xml  # 844
```
</details>
