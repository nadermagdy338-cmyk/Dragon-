# External Integrations

**Analysis Date:** 2026-09-18 (rebuilt from the current tree)

## The headline

Max Manager is a **local, offline-first, rooted Android module**. It has **no backend, no user accounts, and no
remote API called by the app**. A grep for `http(s)://` in `manager/app/src/main/java/nd/max/**` returns only
Apache license headers — no HTTP client, no analytics SDK, no update-check call in the Kotlin sources.

Every "integration" is either **on-device kernel/system surface** (mediated by root) or **build/release
infrastructure** (GitHub Actions, Crowdin, Telegram).

## Device-level integrations

**Kernel sysfs / procfs — the real interface** (via libsu + `RootFileAccess.kt`, or direct native I/O in daemons):

| Domain | Where |
| --- | --- |
| CPU: governors, per-core limits, freq ceiling | `core/hardware/CpuHardwareBackend.kt`, `mainfiles/preferenced-tweaks.sh`, `core/maxai/CpuCeilingKnobs.kt` |
| GPU: clocks/offsets, persistence | `GpuHardwareBackend.kt`, `GpuTweakPersistence.kt` |
| Memory: ZRAM size/compression | `ZramHardwareBackend.kt` |
| Thermal: zones, cooling devices | `thermalcore/src/thermal_zones.rs`, `cooling.rs`, `monitor.rs` |
| I/O scheduler, profile application | `ProfileApplier.kt`, `binprofiles/src/chipsets/*.rs` |
| Charging bypass, per-app refresh rate, process freezing | `archdaemon/jni` (`BypassCharge`, `PidTracker`), `PerAppRefreshRateController.kt` |

**Android system services (app side)**:
- PackageManager / ActivityManager → foreground detection, app list (`AppMonitor.kt`, `ApplistViewmodel.kt`).
- Battery/charging state (`ChargingViewModel.kt`), overlays (`FpsOverlayService`, `ProcessOverlayService`).
- Quick settings tiles: `TileService/ProfileTileService.kt`, `BypassChgTileService.kt`.
- Binder: `service/MtkRootService.kt` exposed through `src/main/aidl/nd/max/IMtkService.aidl`.
- Hidden Android APIs via the `hiddenapibypass` (LSPosed) dependency.

**Vendor surfaces** (chipset-specific, probed rather than assumed): `MtkUtils.kt` (MediaTek),
`XiaomiVendorFeatures.kt`, per-SoC strategies in `binprofiles/src/chipsets/{snapdragon,mediatek,exynos,tensor,unisoc}.rs`,
and capability discovery in `HardwareCapabilityResolver.kt`.

**Native boundary**: `core/jni/PredictorBridge.kt` + `ContextBridge.kt` ↔ `libmaxmanager_native.so`
(built by CI, gitignored, packaged into `jniLibs/`). `libtermux.so` is committed deliberately.

## Root-manager integrations

The module ships three entry layers and must work under each:

| Manager | Files |
| --- | --- |
| Magisk / generic systemless | `mainfiles/customize.sh`, `service.sh`, `post-fs-data.sh`, `action.sh`, `module.prop`, `META-INF/` |
| KernelSU / KernelSU Next | `android/kernelsu/` — `customize.sh`, `service.sh`, `action.sh`, `uninstall.sh`, `module.prop`, `skip_mount` |
| AOSP / ROM integration | `android/aosp/` — `maxmanager.rc`, `Android.bp`, `BoardConfig.mk`, `sepolicy/maxmanager.te`, `sepolicy/file_contexts` |

- SELinux: the shipped `maxmanager.te` defines the daemon domains; a denial here breaks the daemon silently.
- Init: `android/aosp/maxmanager.rc` starts the service; boot ordering is handled by `post-fs-data.sh` → `service.sh`.
- Module metadata: `module.json` (`metamodule: false`), `update.json` (Magisk update manifest with
  `versionCode` 1823) — the only "remote" artifact, consumed by the root manager, not by the app.

## CI / release infrastructure

`.github/workflows/build.yml` runs, in order: `verify.sh` → `changelog.sh` → determine build type →
sync daemon version string → NDK setup (`nttld/setup-ndk`) + ccache → compile archdaemon → compile preloadbin →
Rust toolchain (`dtolnay/rust-toolchain`) + `cargo-ndk` → build thermalcore / profilesettings / utility →
build the JNI native library → compile flashable zip (`compile_zip.sh`) → package developer integration bundle →
validate artifacts → upload artifacts (with retry steps) → Telegram upload.

**Secrets used by CI** (never committed): `KS_PWD` / key password for release signing (the build fails fast
without it), and `BOT_TOKEN` + `CHAT_ID` for the Telegram notification in `.github/scripts/telebot.sh`
(`curl https://api.telegram.org/bot$BOT_TOKEN/sendDocument`). The Telegram step is release plumbing only.

## Localization pipeline

`crowdin.yml` registers **all six** translatable files in `res/values/` (since 2026-09-18):

```yaml
files:
  - source: manager/app/src/main/res/values/strings.xml
    translation: manager/app/src/main/res/values-%android_code%/strings.xml
  - source: manager/app/src/main/res/values/max_ai_strings.xml
    translation: manager/app/src/main/res/values-%android_code%/max_ai_strings.xml
  # + max_navigation_strings.xml, max_screen_strings.xml, max_design_strings.xml, studio_strings.xml
```

Before that date only `strings.xml` was wired, so **471 strings** (`max_ai` 220, `max_screen` 177,
`max_navigation` 54, `max_design` 11, `studio` 9) never entered the ~100-locale pipeline — visible in the tree
because `values-de/` and `values-fr/` contained `strings.xml` **only**. The rule is now written down as
**ADR-26**: a string file that is not registered is a defect, and a new file must be registered in the same change.

**Arabic is the reference pair**: all six EN files have a `values-ar/` counterpart (`values-ar/` now contains six
files), and the remaining work is translation *coverage* inside `strings.xml` (1,629 EN vs 844 AR), which is
exactly what the pipeline is for. Three gates in `docs/ai/VALIDATION.md` §3 keep the structure honest:
file-level AR parity, per-pair key + format-specifier parity, and Crowdin registration.

### Measured coverage — `tools/i18n_coverage.py` (2026-09-18)

The pipeline is now *measurable* instead of assumed. 6 English source files hold **2,106 keys**;
`values-ar/` carries **1,321** of them (62.7%), and **each of the other 84 locales sits at 380 (18%) and is
missing 5 of the 6 files entirely — 144,043 keys**. That number is a translation backlog, not a defect:
a missing key falls back to English at runtime, and the gate reports coverage as output, never as a failure.

```sh
python3 tools/i18n_coverage.py                    # coverage table for all 84 locales
python3 tools/i18n_coverage.py --write-manifests   # build/i18n/to_translate_<locale>.csv, one per locale
python3 tools/i18n_coverage.py --locale de --apply-csv <csv> --dry-run   # validate before writing
python3 tools/i18n_coverage.py --assert            # gate: exit 1 only on a real defect
```

`--apply-csv` is append-only and refuses any row whose specifiers the caller never passes, whose key already
exists, or that duplicates a key inside the same batch — so a returned translation file cannot silently break
the build or crash a screen. The same command also enforces that the three descriptions of the language set
stay identical: `res/values-*` folders, `AppLanguage.CODES`, and `res/xml/locales_config.xml`.

## What this repo does *not* integrate with

No Firebase/analytics/crash reporting, no ad SDK, no payment, no auth provider, no cloud sync, no OTA update
client inside the app, no third-party telemetry endpoint. Any future addition of one of these is an architecture
decision (needs an ADR in `docs/ai/DECISIONS.md`), not a local edit.

## Changelog

- 2026-09-18 — rebuilt: added the KernelSU/AOSP entry matrix, the detailed CI step order, the secret names
  actually used, and the **Crowdin single-file gap** discovered while verifying this document.
- 2026-09-18 (later) — added measured per-locale coverage (2,106 keys / 84 locales / 144,043 missing) and the
  `tools/i18n_coverage.py` commands that produce and merge translation batches.

<details>
<summary>Evidence</summary>

```sh
grep -rn 'https\?://' manager/app/src/main/java/nd/max --include='*.kt' | grep -v xmlns   # license headers only
cat crowdin.yml                                                                          # 1 source file
grep -nE 'BOT_TOKEN|CHAT_ID|curl' .github/scripts/telebot.sh
grep -nE '^\s+(- name:|uses:)' .github/workflows/build.yml
find android/kernelsu android/aosp -maxdepth 2
```
</details>
