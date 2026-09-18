# PROJECT_MAP

Structural map of MaxManager as it exists today (2026-09-18). Paths are relative to repo root; app sources are under `manager/app/src/main/java/nd/max/`.

## Top level

```
android/ archdaemon/ binprofiles/ binutils/ mainfiles/ preloadbin/ thermalcore/   # module side (Rust/shell)
manager/            # Gradle project: app, kernel-flasher, terminal-emulator, terminal-view
docs/aegis/         # older spec/plan/work records
docs/ai/            # THIS shared memory
tools/              # static gates — no compiler needed, run from any directory
.planning/codebase/ # technical map of the code (.planning/README.md is the index)
AGENTS.md           # team roster + model routing + handoff contract
module.json version version_type maxmanagerApplist.json update.json crowdin.yml
```

## Tools — فحوص سريعة تعمل بلا Android SDK (والـSDK صار مثبَّتًا: `VALIDATION.md` §0)

| file | what it proves | how to run |
| --- | --- | --- |
| `tools/code_health.py` | correctness = 0 (package/path, every `R.*` resolves per Gradle module, duplicate keys, stray root files) and maintenance debt ≤ ceiling | `python3 tools/code_health.py --assert` |
| `tools/i18n_coverage.py` | locale coverage, key + format-specifier parity, folder ↔ picker ↔ `locales_config` identity, CSV merge (`--apply-csv`) and manifests (`--write-manifests`) | `python3 tools/i18n_coverage.py --assert` |
| `tools/repo_audit.py` | an independent second opinion: string keys/duplicates, bracket balance, listed-file presence | `python3 tools/repo_audit.py` |
| `tools/i18n_translate.py` | batch machine translation with placeholder protection + cache (needs a provider key) | `…--estimate` |
| `tools/i18n_glossary.csv` | terms that must not be translated | data file |

Each of these derives the repo root from its own location, so the working directory cannot change the answer.
Replaces the old root-level `check2.py` (stale assertions + CWD-dependent false output) and the inert `fix_tweak.py` (deleted).

## App module layout (297 Kotlin files under `manager/`, of which 238 are `nd.max` app files)

```
nd/max/
  MainActivity.kt (817)        # hosts NavHost + bottom bar + pager duality + update/reboot dialogs
  MaxManagerApplication.kt, AppMonitor.kt (separate process), MaxManagerPaths/Props
  TileService/                 # BypassChg, Profile QS tiles
  core/
    maxai/        ControlPlane, ControlRegistry, MaxAiEngine, MinimalPlanner, Objective,
                  ControlOutcomeModel, CredibilityStore, DynamicIntentLearner, SafetyEngine,
                  SafetyGovernor, ResponseModel, CpuCeilingKnobs, MaxAiModels
    hardware/     HardwareControlArbiter, HardwareControlKey, ControlOwnership, ManualControlLocks,
                  SharedHardwareOwnershipStore, Cpu/Gpu/ZramHardwareBackend, HardwareCapability(+Resolver),
                  AdaptiveProfileEngine, ProfileApplier, PredictiveSafety, DriftGuard, PerApp* controllers
    diagnostics/  DiagnosticCenter, DeviceBlueprint
    recommendation/, jni/ (ContextBridge, PredictorBridge), di/, threading/
  service/        FpsOverlayService, ProcessOverlayService, MtkRootService
  ui/
    design/       # NEW Design Language (untracked): MaxTokens, MaxStructure, MaxScreenScaffold,
                  # MaxCondition, MaxMetric, MaxControlRows, MaxDialogs
    component/    # LEGACY component layer (~30 files): MaxDesignSystem, ScreenChrome, StudioComponents,
                  # ExpressiveListComponent (1040), HomeComponents (1142), GaugeComponent, LiveGraphComponent,
                  # PulseArt, PulseFieldEngine, AmbientGlowCycle, AmbientMotifOverlay, DialogComponent (690)
    components/   # VideoWallpaperPlayer, WeatherEffects  (note: duplicate of `component/` naming)
    mainscreens/  HomeScreen, LegendaryHomeDashboard, HomeDashboardComponents, ApplistScreen,
                  TweakScreen (1222), SettingsScreen (677), MaxAiScreen (538), DiagnosticsScreen,
                  DashboardDetailScreens (959: thermal/storage/network/battery), GetStartedScreen (734)
    subscreens/   24 feature screens (see route table)
    mtk/          MtkScreen + 6 tabs + MtkViewModel
    terminal/, activitylauncher/, flasher/, settings/, theme/, util/ (40 utils), viewmodel/ (24 VMs)
```

## Navigation today

`MainActivity.MainScreen()` owns everything:

- **Two competing navigation models**, chosen by a SharedPreferences flag `use_scroll_animation`:
  - flag off → 4 separate routes `home | applist | tweaks | settings` + bottom bar.
  - flag on → single route `main` containing a `HorizontalPager` over the same 4 pages.
- Start destination: `get_started` until `app_prefs.has_completed_get_started`.
- Bottom bar items: Home, Applist, Tweaks, Settings. Navigation rail at ≥840dp.
- Root + module status is re-probed (`RootUtils.requestRootAccess()`) **on every route change and every pager page change**.

### Route table (all flat, string literals, registered in `MainActivity`)

| Route | Screen | Notes |
| --- | --- | --- |
| `get_started` | GetStartedScreen | onboarding gate |
| `main` | pager of home/applist/tweaks/settings | alternate nav model |
| `home` | HomeScreen | dashboard |
| `applist` | ApplistScreen | per-app entry |
| `tweaks` | TweakScreen | flat list of 19 destinations |
| `settings` | SettingsScreen | only navigates to `aboutscreen`, `color_palette` |
| `maxai` | MaxAiScreen | **the differentiator, buried, no bottom-bar entry** |
| `diagnostics` | DiagnosticsScreen | |
| `cpucorecontrol` `governorsettings` `preferenced` | CPU control | overlapping CPU surfaces |
| `gpustudio` / `maligpufreq` / `adrenogpufreq` | GpuStudioScreen | **3 routes → 1 screen (dead aliases)** |
| `mtkscreen` | MtkScreen (6 tabs) | vendor-specific CPU/GPU/DRAM/PPM/Thermal/Boost |
| `displaystudio` `resolutionscreen` `fpsoverlay` | display/frame | overlapping display surfaces |
| `touchboost` `fpsgoscreen` `FasScreen` | responsiveness | 3 screens, one user intent |
| `zrammanager` `dex2oat` `processmanager` `debloatfreeze` | memory/apps | split across tabs |
| `chargingscreen` `bypasschg` `bypasschg_check` `dozemode` | power | 4 screens, one domain |
| `networkscheduler` | NetworkSchedulerScreen | |
| `thermal_detail` `storage_detail` `network_detail` `battery_detail` | DashboardDetailScreens | |
| `app_settings/{pkg}` | AppSettingsScreen (1174) | only parameterized route |
| `colorscheme` `color_palette` | ColorSchemeScreen / CustomThemeScreen (1304) | appearance |
| `terminal` `setedit` `logsviewer` `activitylauncher` `kernelflasher` | power tools | high-risk, undifferentiated |
| `aboutscreen` | AboutScreen | |

## Design-language adoption

- On new `ui/design/` shell (`MaxScreen`/`MaxListScreen`): **TouchBoost, DozeMode, Resolution, FpsOverlay, Zram, Dex2oat** (6).
- Still hand-rolling their own `Scaffold`: 33 files including all four tab screens, MaxAiScreen, MtkScreen, GpuStudio, CpuCoreControl, Charging, Bypass×2, DisplayStudio, DebloatFreeze, ProcessManager, LogsViewer, SetEdit, AppSettings, ColorScheme, CustomTheme, Applist, Diagnostics, DashboardDetail, GetStarted, ActivityLauncher, About, Gov/FpsGo/Fas/NetworkScheduler/Preferenced.

## Max AI control plane (the real product core)

```
MaxAiEngine → MinimalPlanner (rank expected impact/cost × credibility)
            → SafetyGovernor (pre-veto)  → HardwareControlArbiter (lease + baseline + verified apply)
            → post-read thermals → rollback on unsafe rise
            → ControlOutcomeModel + CredibilityStore (learn per device/app/knob/direction)
HardwareControlKey = canonical knob identity shared by AI, Safety, Per-App, AppMonitor
SharedHardwareOwnershipStore = cross-process lease journal (app process vs AppMonitor process)
ManualControlLocks = user touch locks only the knob they touched
```

Tests exist (source-level + unit): `ControlRegistryTest`, `HardwareControlArbiterTest`, `MinimalPlannerTest`, `ControlOutcomeModelTest`, `ObjectiveTest`, `ResponseModelTest`, `ManualControlLocksTest`, `DiagnosticCenterTest`, `ControlPlaneArchitectureTest`, `GpuControlModelTest`, `HomeTemperaturePolicyTest`, `RecommendationTextClassifierTest`. **No UI tests.**

## Where state lives (fragmented)

`SharedPreferences("settings")`, `SharedPreferences("app_prefs")`, `SettingsPreference`, `FpsOverlayPrefs`, `TerminalPreferences`, `ProfilePresetStore`, `GpuTweakPersistence`, `PerAppRecoveryStore`, `CredibilityStore` — read directly from composables in several places (including `MainActivity`).
