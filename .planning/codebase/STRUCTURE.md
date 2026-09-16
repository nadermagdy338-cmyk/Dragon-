# Codebase Structure

**Analysis Date:** 2026-09-16

## Directory Layout

```
optmize-main/
├── manager/               # Android app (Gradle root, rootProject "MaxManager")
│   ├── app/               # Main app module (namespace nd.max)
│   │   ├── src/main/java/nd/max/   # ~238 Kotlin files
│   │   ├── src/main/aidl/nd/max/   # IMtkService.aidl
│   │   ├── src/main/res/           # strings.xml × ~70 locales
│   │   └── src/test/java/nd/max/   # 13 JVM unit tests
│   ├── terminal-emulator/ # Vendored Termux terminal (com.termux.terminal)
│   ├── terminal-view/     # Terminal view library
│   ├── kernel-flasher/    # Kernel flashing lib (Room schemas/)
│   └── gradle/libs.versions.toml   # Version catalog (single source of versions)
├── mainfiles/             # Magisk module payload (scripts + system/ overlay + banner)
├── archdaemon/            # C daemon sys.maxmanager-service (NDK, jni/)
├── thermalcore/           # Rust thermal policy daemon
├── binprofiles/           # Rust profile-setter CLI (chipset strategies)
├── binutils/              # Rust utility binaries
├── preloadbin/            # C game-lib preloader (NDK, jni/)
├── android/               # AOSP integration: aosp/ (rc, sepolicy, overlay), kernelsu/ variant
├── docs/                  # aegis/ + ai/ docs
├── .github/workflows/     # build.yml (CI)
├── .github/scripts/       # verify.sh, changelog.sh, compile_zip.sh, generatesha256.sh, telebot.sh
├── maxmanagerApplist.json # Curated per-game default profile DB
├── version, version_type  # Version single-sources (read by CI)
├── update.json            # Magisk update manifest (version/zipUrl/changelog)
├── changelog.md           # Release notes (copied into module by CI)
├── module.json, crowdin.yml, fix_tweak.py, logo.jpg
└── CLAUDE.md.bak, *.log, *.backup   # Tracked artifacts (see CONCERNS.md)
```

## Directory Purposes

**manager/app/src/main/java/nd/max/**
- Purpose: All application code
- Key files: `MainActivity.kt`, `MaxManagerApplication.kt`, `AppMonitor.kt`, `MaxManagerProps.kt`
- Subdirectories:
  - `core/hardware/` — 24 control-plane files (arbiter, backends, locks, capabilities)
  - `core/maxai/` — 17 files, the MaxAI adaptive engine
  - `core/jni/` — PredictorBridge, ContextBridge
  - `core/di/` — Hilt modules (AppModule, DataModule)
  - `core/diagnostics/` — DiagnosticCenter
  - `core/recommendation/`, `core/threading/`
  - `data/datasources/` — HardwareDataSourceImpl
  - `service/` — FpsOverlayService, MtkRootService, ProcessOverlayService
  - `receiver/`, `TileService/`
  - `ui/` — mainscreens (12), component (36), viewmodel (22), navigation (4), subscreens, mtk tabs, terminal (6), theme, design, util (31)

**mainfiles/**
- Purpose: The actual Magisk module — everything that lands on-device
- Key files: `customize.sh` (SKIPUNZIP=1 installer), `service.sh`, `post-fs-data.sh`, `action.sh`, `preferenced-tweaks.sh`, `verify.sh`, `module.prop`, `system/bin/` (daemon binaries placed here)
- Subdirectories: `META-INF/` (update-binary), `system/` (systemless overlay tree)

**archdaemon/jni/**
- Purpose: C source of `sys.maxmanager-service` background daemon
- Key files: `Main.c`, `Android.mk`, `include/AZenith.h`
- Subdirectories: 14 components (`AppLoader/`, `GamePreload/`, `PidTracker/`, `BypassCharge/`, `ConfigHandler/`, `BinaryCLI/`, `SystemProfile/`, `InotifyHandler/`, `SystemLogger/`, `MaxManagerUtility/`, `FileUtility/`, `ShellUtility/`, `StartupInit/`, `System/`)

**thermalcore/src/**
- Purpose: Rust thermal management daemon (`rianixia-thermalcore`)
- Key files: `main.rs`, `policy_manager.rs`, `monitor.rs`, `learning.rs`, `prediction.rs`, `cooling.rs`, `thermal_zones.rs`, `state.rs`
- Special: `simulator.rs` + `simulator` cargo feature — desktop testing without a device

**binprofiles/src/**
- Purpose: Rust CLI applying performance profiles per chipset
- Subdirectories: `chipsets/` (snapdragon, mediatek, exynos, tensor, unisoc), `profiles/`, `utils/`

## Key File Locations

**Entry Points:**
- `manager/app/src/main/java/nd/max/MainActivity.kt` — app UI entry
- `mainfiles/customize.sh` — module installation entry
- `mainfiles/action.sh` — launches `sys.maxmanager-service`
- `archdaemon/jni/Main.c`, `thermalcore/src/main.rs`, `binprofiles/src/main.rs` — daemon/binary entries

**Configuration:**
- `manager/gradle/libs.versions.toml` — all dependency versions
- `manager/app/build.gradle.kts` — SDK levels, signing (KS_PWD), ABI filters
- `version` + `version_type` — version source consumed by CI and scripts
- `maxmanagerApplist.json` — per-game default profiles
- `android/aosp/maxmanager.rc` + `sepolicy/maxmanager.te` — init + SELinux

**Core Logic:**
- `core/hardware/HardwareControlArbiter.kt` — every hardware write
- `core/maxai/MaxAiEngine.kt` — adaptive engine
- `thermalcore/src/policy_manager.rs` — thermal decisions

**Testing:**
- `manager/app/src/test/java/nd/max/` — 13 unit test files (core layer only)

**Documentation:**
- `README.md` — bilingual (EN/中文) overview + feature list
- `docs/aegis/`, `docs/ai/` — subsystem docs
- `changelog.md` — release notes

## Naming Conventions

**Files:**
- Kotlin: PascalCase matching class (`HardwareControlArbiter.kt`); ViewModels end in `*ViewModel.kt` or `*Viewmodel.kt` (inconsistent: both `SettingViewmodel.kt` and `ChargingViewModel.kt`)
- Rust: snake_case modules (`policy_manager.rs`)
- Shell: lowercase hyphenless (`customize.sh`, `post-fs-data.sh`)
- Tests: `*Test.kt` mirroring source package under `src/test/`

**Directories:**
- Kotlin packages by layer: `core/`, `ui/`, `data/`, `service/`, `receiver/`
- `ui/component/` vs `ui/components/` both exist (see CONCERNS.md)

## Where to Add New Code

**New UI screen:** `ui/mainscreens/` or `ui/subscreens/` + route in `ui/navigation/MaxDestinations.kt` / `MaxNavGraph.kt` + ViewModel in `ui/viewmodel/`; all strings → `res/values/strings.xml` (then Crowdin)
**New hardware control:** backend in `core/hardware/` (follow `CpuHardwareBackend.kt` shape), register key in `HardwareControlKey.kt`, arbiter-mediated writes only; test in `src/test/java/nd/max/core/hardware/`
**New MaxAI behavior:** `core/maxai/` respecting SafetyGovernor/ManualControlLocks; journal via MaxAiJournal
**New tweak prop:** `mainfiles/preferenced-tweaks.sh` via `set_default_prop` helper (see comment block in `customize.sh`)
**New chipset support:** strategy in `binprofiles/src/chipsets/{family}.rs` + capability probing in `HardwareCapabilityResolver.kt`
**New daemon component (C):** directory under `archdaemon/jni/src/` + hook into `Main.c` + `Android.mk`

## Special Directories

**`manager/app/src/main/jniLibs/`:**
- Purpose: packaged native libs
- `libmaxmanager_native.so` is CI-built and gitignored; `libtermux.so` deliberately committed

**`.serena/cache/` (tracked accidentally):** IDE/LSP cache including a log file — cleanup candidate

**Backup/log files tracked in git:** `CLAUDE.md.bak`, `LegendaryHomeDashboard.kt.backup`, `HomeDashboardComponents.kt.backup`, `build_*.log` — see CONCERNS.md

---

*Structure analysis: 2026-09-16*
*Update when directory structure changes*
