# Codebase Structure

**Analysis Date:** 2026-09-18 (rebuilt from the current tree)

## Directory layout

```
optmize-main/
├── manager/                    # Android app (Gradle root, rootProject "MaxManager")
│   ├── app/                    # Main module, namespace nd.max
│   │   ├── src/main/java/nd/max/      # 235 .kt files / 62,660 LOC
│   │   ├── src/main/aidl/nd/max/IMtkService.aidl
│   │   ├── src/main/res/              # values/ + 84 locale folders
│   │   └── src/test/java/nd/max/      # 14 JVM unit-test files
│   ├── terminal-emulator/      # Vendored Termux engine (com.termux.terminal)
│   ├── terminal-view/          # Vendored terminal view
│   ├── kernel-flasher/         # Vendored kernel flasher (+ Room schemas)
│   └── gradle/libs.versions.toml
├── mainfiles/                  # Magisk module payload: 8 .sh + system/ overlay + banner + META-INF
├── archdaemon/jni/             # C: sys.maxmanager-service (14 components + Main.c)
├── thermalcore/src/            # Rust thermal daemon (16 files)
├── binprofiles/src/            # Rust chip-aware profile CLI (10 files)
├── binutils/src/               # Rust utility binaries (3 files)
├── preloadbin/jni/             # C: game-library preloader
├── android/                    # aosp/ (rc, sepolicy, overlay) + kernelsu/ variant
├── docs/ai/                    # Living state: vision, ADRs, tasks, gates, handoff log
├── docs/aegis/                 # Older spec/plan records (history, superseded)
├── .planning/codebase/         # This technical map (rebuilt 2026-09-18)
├── .github/workflows/build.yml # Full CI pipeline
├── AGENTS.md                   # Team roster + model routing + handoff contract
├── maxmanagerApplist.json      # Curated per-game default profiles
├── version / version_type / module.json / update.json / crowdin.yml
└── (بقايا متعقّبة)              # had *.bak/*.backup + build logs — cleaned 2026-09-18, see CONCERNS.md C-04
```

## Where the app code lives

`manager/app/src/main/java/nd/max/` — **top-level files (10)**: `MainActivity.kt`, `MaxManagerApplication.kt`,
`AppMonitor.kt`, `AppMonitorLogger.kt`, `MaxManagerPaths.kt`, `MaxManagerProps.kt`, `MtkUtils.kt`,
`PerAppRefreshRateController.kt`, `RefreshRate.kt`, `XiaomiVendorFeatures.kt`.

| Package | Files | Purpose |
| --- | --- | --- |
| `core/hardware/` | 24 | Control plane: `HardwareControlArbiter`, `ControlOwnership`, `ManualControlLocks`, `ProfileApplier`, backends (Cpu/Gpu/Zram), `HardwareCapabilityResolver`, `DriftGuard`, `PredictiveSafety`, `PerApp*`, `RootFileAccess` |
| `core/maxai/` | 17 | Adaptive engine: `MaxAiEngine`, `MinimalPlanner`, `SafetyGovernor`, `SafetyEngine`, `TrustModel`, `CredibilityStore`, `ControlRegistry`, `ControlOutcomeModel`, `MaxAiJournal(+Codec)`, `MaxAiInsights`, `Objective`, `ResponseModel`, `DynamicIntentLearner`, `ControlPlane`, `CpuCeilingKnobs` |
| `core/jni/` | 2 | JNI boundary only: `PredictorBridge`, `ContextBridge` |
| `core/di/` | 2 | Hilt modules (`AppModule`, `DataModule`) |
| `core/diagnostics/` | 2 | `DiagnosticCenter` + support |
| `core/recommendation/` | 2 | Recommendation text classifier |
| `ui/mainscreens/` | 13 | `HomeScreen`, `ControlScreen`, `MaxAiScreen`, `ApplistScreen`, `SettingsScreen`, `DiagnosticsScreen`, `MaxLiveScreen`, `GetStartedScreen`, `DashboardDetailScreens`, `ControlLayoutModel`, plus three legacy-named but **load-bearing** files: `LegendaryHomeDashboard` (live dashboard body, rendered by `HomeScreen`), `HomeDashboardComponents` and `LegacyTweakComponents` (shared composables — see CONCERNS C-07) |
| `ui/subscreens/` | 26 | Feature screens reached from the domain hubs |
| `ui/component/` | 38 | Shared composables (legacy design system) + `VideoWallpaperPlayer`, `WeatherEffects` (decorative engines, ADR-12 governed). The duplicate `ui/components/` package was merged here on 2026-09-18 (CONCERNS C-05) |
| `ui/design/` | 11 | The design language: `MaxTokens`, `MaxStructure`, `MaxScreenScaffold`, `MaxControlRows`, `MaxMetric`, `MaxDomainCard`, `MaxCondition`, `MaxDialogs`, `MaxHelp`, `MaxViewMenu`, `MaxAiCinematics` |
| `ui/navigation/` | 5 | `MaxDestinations`, `MaxDestinationCatalog`, `MaxNavGraph`, `MaxNavActions`, `MaxNavBar` |
| `ui/viewmodel/` | 22 | Screen state + orchestration (`*ViewModel.kt`, four legacy `*Viewmodel.kt`) |
| `ui/util/` | 31 | Logging, formatting, formatting helpers, event log |
| `ui/theme/` | 6 | Compose theme |
| `ui/terminal/` | 6 | Terminal screen + integration |
| `ui/activitylauncher/` | 4 | Activity launcher palette, VM, screen, floating service |
| `ui/settings/` | 2 | `SettingsViewModel`, `SettingsPreference` |
| `ui/flasher/` | 2 | `KernelFlasherScreen`, `FlasherWorker` |
| `ui/process/` | 1 | `MyLifecycleOwner` |
| `service/`, `TileService/`, `receiver/`, `data/datasources/` | 3 / 2 / 1 / 1 | Foreground services (`FpsOverlayService`, `MtkRootService`, `ProcessOverlayService`), quick tiles, receiver, data source |

**Removed since the 2026-09-16 analysis:** `ui/mtk/` (tabs package) no longer exists — MediaTek handling is now
`MtkUtils.kt` + `service/MtkRootService.kt` + MTK rows hosted by the domain hubs.

## Key file locations

- **App entry**: `MainActivity.kt` (444 lines) → `ui/navigation/MaxNavGraph.kt`.
- **Boot**: `MaxManagerApplication.kt` (Hilt), `AppMonitor.kt` (foreground watching).
- **Module install/boot**: `mainfiles/customize.sh` (`SKIPUNZIP=1`), `service.sh`, `post-fs-data.sh`,
  `action.sh` → `system/bin/sys.maxmanager-service`.
- **Native entries**: `archdaemon/jni/Main.c`, `thermalcore/src/main.rs`, `binprofiles/src/main.rs`, `binutils/src/main.rs`.
- **Config sources of truth**: `manager/gradle/libs.versions.toml`, `manager/app/build.gradle.kts`,
  `version` + `version_type`, `maxmanagerApplist.json`, `android/aosp/maxmanager.rc` + `sepolicy/maxmanager.te`.
- **Critical logic**: `core/hardware/HardwareControlArbiter.kt` (every hardware write),
  `core/maxai/MaxAiEngine.kt`, `thermalcore/src/policy_manager.rs`.
- **Docs**: `README.md` (bilingual EN/中文 overview), `docs/ai/*` (living), `docs/aegis/*` (history), this folder.

## Naming conventions

- Kotlin files: PascalCase matching the primary class; ViewModels end in `*ViewModel.kt` (legacy `*Viewmodel.kt`
  in four files: `SettingViewmodel`, `HomeViewmodel`, `ApplistViewmodel`, `TweakViewmodel`).
- Compose screens: `<Name>Screen.kt`, function `fun <Name>Screen(...)` — 37 files declare a top-level `*Screen(`.
- Rust: snake_case modules (`policy_manager.rs`); C: PascalCase components + `Main.c`; shell: lowercase
  hyphenless (`post-fs-data.sh`); tests: `*Test.kt` mirroring the source package under `src/test/`.

## Where to add new code

| Adding | Where |
| --- | --- |
| New screen | `ui/subscreens/` (or `ui/mainscreens/` if primary) + entry in `ui/navigation/MaxDestinations.kt`; **do not** declare a new `Scaffold` — use `ui/design/MaxScreenScaffold.kt` |
| New hardware control | backend in `core/hardware/` following `CpuHardwareBackend.kt`, key in `HardwareControlKey.kt`, writes only through the arbiter; test under `src/test/java/nd/max/core/hardware/` |
| New Max AI behaviour | `core/maxai/` respecting `SafetyGovernor` + `ManualControlLocks`; journal via `MaxAiJournal` |
| New tweak prop | `mainfiles/preferenced-tweaks.sh` via the `set_default_prop` helper |
| New chipset support | `binprofiles/src/chipsets/{family}.rs` + probing in `HardwareCapabilityResolver.kt` |
| New C daemon component | new directory under `archdaemon/jni/src/` + hook in `Main.c` + `Android.mk` |
| New user-visible string | `res/values/*.xml` **and** `res/values-ar/*.xml` in the same change (ADR-14) |

## Special directories

- `manager/app/src/main/jniLibs/`: `libmaxmanager_native.so` is CI-built and gitignored; `libtermux.so` is a
  deliberate committed exception.
- `.serena/`: LSP artifacts, **tracked in git** (including `intellij-server.log` and telemetry CSV) — see CONCERNS.
- Backups inside source trees: `ui/mainscreens/HomeDashboardComponents.kt.backup`,
  `ui/mainscreens/LegendaryHomeDashboard.kt.bak`, plus root `LegendaryHomeDashboard.kt.backup`.

## Changelog

- 2026-09-18 — rebuilt: `ui/mtk/` removed from the map, `ui/design` now 11 files, navigation package 5 files,
  MainActivity 444 lines, added `.planning/` + `AGENTS.md` to the tree, and per-package file counts re-derived.

<details>
<summary>Evidence</summary>

```sh
for d in $(find manager/app/src/main/java/nd/max -maxdepth 2 -type d); do echo "$(find $d -maxdepth 1 -name '*.kt' | wc -l) $d"; done | sort -rn
ls manager/app/src/main/java/nd/max/ui/design manager/app/src/main/java/nd/max/ui/navigation
grep -rl '^fun .*Screen(' manager/app/src/main/java/nd/max/ui | wc -l     # 37
wc -l manager/app/src/main/java/nd/max/MainActivity.kt                     # 444
find manager/app/src/main/java/nd/max -iname '*mtk*'                       # only MtkUtils.kt + MtkRootService.kt
```
</details>
