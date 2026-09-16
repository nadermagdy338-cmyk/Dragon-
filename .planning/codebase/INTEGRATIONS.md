# External Integrations

**Analysis Date:** 2026-09-16

## APIs & External Services

Max Manager is a **local, offline-first** Android system module. It has no cloud backend and no user-facing remote APIs.

**Telegram build notifications (CI only):**
- `.github/scripts/telebot.sh` — sends built module zip to a Telegram chat
  - Client: `curl` against `https://api.telegram.org/bot$BOT_TOKEN/sendDocument`
  - Auth: `BOT_TOKEN` from GitHub Actions secrets (never committed)
  - Not part of the product; release pipeline only

## Device-Level Integrations (not network)

The "external systems" this codebase talks to are **on-device kernel/system interfaces**, mediated through root:

**Kernel sysfs/procfs (via libsu shell + direct file IO):**
- CPU: freq governors, per-core limits (`core/hardware/CpuHardwareBackend.kt`, `preferenced-tweaks.sh`)
- GPU: clocks/offsets (`GpuHardwareBackend.kt`, `GpuTweakPersistence.kt`)
- Thermal: cooling device lists, thermal zones (`thermalcore/src/thermal_zones.rs`, `cooling.rs`)
- ZRAM: size/compression knobs (`ZramHardwareBackend.kt`)
- I/O schedulers, CPU governor selection (user-configurable defaults)

**Android system services (app side):**
- PackageManager / ActivityManager — foreground app detection, app list (`AppMonitor.kt`, `ApplistViewmodel.kt`)
- Charging/battery state — `ChargingViewModel.kt`, bypass-charge diagnostics
- Per-app refresh rate — `PerAppRefreshRateController.kt` (vendor surfaceflinger props on some chips)
- Vendor props — `XiaomiVendorFeatures.kt`, `MtkUtils.kt` (MediaTek-specific paths), `ui/mtk/` screens

**Inter-process control plane:**
- Manager app ↔ archdaemon (`sys.maxmanager-service`, C, `archdaemon/jni/`): app profiles, preloading, PidTracker, bypass charge, config handling — version-checked via baked-in `MODULE_VERSION` compared against module.prop (`build.yml` "Sync Daemon Version String" step)
- Manager app ↔ thermalcore (Rust daemon): thermal policy, serialized state via serde/bincode; `simulator` cargo feature for desktop testing
- App ↔ per-app settings store: `/data/adb/.config/MaxManager` (module config dir created by `customize.sh`)
- `maxmanagerApplist.json` (repo root) — curated per-game default profile database shipped with module

**Native code (JNI, in-app):**
- `core/jni/PredictorBridge.kt` — RL-based thermal/perf predictor (`libmaxmanager_native.so`, built by CI; gitignored)
- `core/jni/ContextBridge.kt` — native recommendation generation

## Data Storage

**No databases.** All persistence is property/file based:
- Android system props (`resetprop`/`setprop`) — 58+ tweak props in `preferenced-tweaks.sh`
- Config files under `/data/adb/.config/MaxManager`
- SharedPreferences in-app (e.g., `MaxAiEngine.kt` journal/credibility stores)
- Room not used; `manager/kernel-flasher` has `schemas/` (Room export) for its own use

## Authentication & Identity

None. Root authorization is delegated to the installed root manager (KernelSU/Magisk); libsu handles shell elevation.

## Monitoring & Observability

- In-app: `core/diagnostics/DiagnosticCenter.kt` (tested in `DiagnosticCenterTest.kt`), `EventLog` (`ui/util/`)
- Daemon-side: `SystemLogger` component in `archdaemon/jni/src/`, thermalcore's own state journaling
- No crash reporting / analytics service

## CI/CD & Deployment

- GitHub Actions `.github/workflows/build.yml` — triggers on PR/push to main + manual dispatch, path-filtered
  - Steps: verify module → changelog copy → build type from `version_type` → daemon version sync → Gradle release APK (KS_PWD secret) → Rust cross-compile → NDK daemons → `.github/scripts/compile_zip.sh` → sha256 → Telegram notify
  - Secrets: `KS_PWD`/`KEYSTORE_PASSWORD`, `BOT_TOKEN`

## Environment Configuration

**Development:**
- `KS_PWD` required for signed release APK (build fails early otherwise)
- Rust targets: aarch64-linux-android, armv7-linux-androideabi (+ NDK linker config)
- `thermalcore` `simulator` feature enables running the thermal daemon on desktop for testing

**Production:**
- Version string flows: `version` + `version_type` files → `module.prop`, `update.json`, daemon binary — all must agree or module self-verifies as broken (`verify.sh`)

## Webhooks & Callbacks

None. Incoming: none. Outgoing: Telegram (CI only, see above).

---

*Integration audit: 2026-09-16*
*Update when adding/removing external services*
