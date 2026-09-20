# Phase 1: Max Atlas Compatibility And Safe Discovery - Pattern Map

**Task:** ATLAS-M1, local architecture/pattern investigation.
**Mapped:** 2026-09-20 against the current working tree, not historical completion summaries.
**Scope:** Read-only investigation; this file is the only file written. No external sources fetched, no RESEARCH artifact edited, no product changes, no commits.
**Planning inputs:** `01-CONTEXT.md`, `.planning/PROJECT.md`, `.planning/REQUIREMENTS.md`; architect brief and HANDOFF read order; binding engineering, ADR, validation and review documents.
**Verification:** Repository gates run below. No JVM tests, Android builds, emulator runs or device probes were run.

## Path Conventions

In the tables below, these are exact repository-relative prefixes, not new directories implied to exist:

| Prefix | Existing Root |
| --- | --- |
| `APP/` | `manager/app/src/main/java/nd/max/` |
| `TEST/` | `manager/app/src/test/java/nd/max/` |
| `RES/` | `manager/app/src/main/res/` |

`APP/core/atlas/` and every Atlas-named Kotlin file in this map are **proposed NEW files**, not existing implementation. Existing files and proposed files are distinguished explicitly. Line references describe the source observed in this task and will move after implementation.

## Decisions For The Planner

1. Atlas should own **observations, discovery progress and an evidence cache**, not policy, hardware ownership, a control registry, writable-path approval or a second telemetry loop. Use the existing canonical keys and existing backend read parsers. Do not inject `MaxAiEngine` or `HardwareControlArbiter` into Atlas.
2. Preserve `HardwareCapabilityResolver.resolve(context)` as the existing synchronous, control-facing facade during this read-only phase. Add a separately named, pure diagnostic projection there if the UI needs a `HardwareCapabilitySnapshot`; do not silently replace this method with asynchronous cached discovery. Its caller `MaxAiEngine.decisionCycle` feeds `ControlRegistry.build`, so changing its `canWrite` results can change AI actions even without editing Max AI.
3. The useful extension seam is **narrow injectable read IO in existing backend discovery**, plus deterministic providers over bounded observations. CPU currently lacks such a seam; GPU already has injectable IO but includes `write`. Extract/read-split these seams, preserving default legacy writer behavior and all existing transaction tests. New discovered paths remain observations until a separately reviewed backend knows their semantics.
4. A `withContext(IO)` or `withTimeout` around `RootFileAccess.read` is **not** an evidence-preserving, cancellable reader. Its Boolean/null/list API has already erased the cause, its shared shell jobs are blocking, and its file/IPC reads have no byte limit. Atlas needs a typed read-only boundary; reuse the existing privilege infrastructure, not the lossy methods as proof.
5. Restore a small **Settings -> Diagnostics** entry, then replace only the legacy capability/report blocks in Diagnostics with Atlas progress, reasons and report preview. The route exists but the Settings link does not. Keep all other diagnostics, primary destinations and the app's visual language.
6. Do not reuse `dumpDiagnosticLogs` for the Atlas report. It gathers broad config, pstore, native state and root-manager logs without preview/redaction. Reuse only the Android share mechanism and a safe, structured selection of `DiagnosticCenter` information.
7. No-root support has an app-entry acceptance dependency, not merely an Atlas parser test. Current startup probes root and binds a root service; onboarding completion uses a root restart. A minimal route to read-only Diagnostics must work independently of those side effects, or no-root UI support must remain explicitly unverified.

## Current Reality

### Platform And Build

| Fact | Verified Source | Planning Consequence |
| --- | --- | --- |
| App minimum is **API 29 / Android 10**, compile/target SDK 36 | `manager/app/build.gradle.kts:21,35-36` | Older Android is out of scope. Do not repeat the older Android 11+ summary. |
| App ABI filters include **arm64-v8a AND armeabi-v7a** | `manager/app/build.gradle.kts:41-43` | Do not describe 32-bit as wholly absent or add ABI support implicitly. |
| Main module installer accepts API 29+ and both ARM architectures | `mainfiles/customize.sh:88-89,107-111` | Catalog identity must distinguish app/runtime ABI from device-supported ABIs. |
| Alternate KernelSU packaging path explicitly requires arm64 | `android/kernelsu/customize.sh:25-32` | Distribution paths do not have identical support envelopes. |
| CI builds native Rust/JNI for both ARM targets | `.github/workflows/build.yml:178-181,202-241`; NDK ABI lists in `archdaemon/jni/Application.mk:1`, `preloadbin/jni/Application.mk:1` | Build targets are not proof all native features work on each ABI. Local source discovery showed `manager/app/src/main/jniLibs/arm64-v8a/libtermux.so`; generated/ignored artifacts were not audited as a release. |
| JDK target 17; AGP 9.2.0, Kotlin 2.3.10, Gradle wrapper 9.5.1 | `manager/app/build.gradle.kts:127-135`; `manager/gradle/libs.versions.toml:3-5`; `manager/gradle/wrapper/gradle-wrapper.properties:3` | Do not use system Gradle or upgrade dependencies for Atlas. |
| Unit-test variants explicitly enabled beyond the tested build type | `manager/gradle.properties:13-15` | Both `testDebugUnitTest` and `testReleaseUnitTest` are intended current tasks. Historical statements that only one exists are stale. |
| Tests use JUnit 4 and real host `org.json` | `manager/app/build.gradle.kts:195-201` | Pure Kotlin/JSON fixtures need no new dependency. `kotlinx-coroutines-test` is not currently declared. |
| Hilt app/KSP already wired | `APP/MaxManagerApplication.kt:34-48`; `manager/app/build.gradle.kts:7-13,184-187` | New repository/VM use existing Hilt, no new service locator. |
| Release artifact guard requires `KS_PWD`; unsigned test/R8 tasks are distinct | `manager/app/build.gradle.kts:23-31,46-53,89-105` | Do not ask for/sign with a secret in this phase. |

### Capability And Authority

`APP/core/hardware/HardwareCapability.kt:9-24` currently defines exactly these feature IDs: CPU frequency, governor, core control and boost; GPU frequency, governor and boost; thermal zones and cooling; battery telemetry; ZRAM; display refresh and resolution; touch control. It has **no** general memory/PSI, storage, network, scheduler, sensor, charging-limit or vendor-HAL feature IDs.

`FeatureCapability` has only `feature`, `access`, `backend`, and a list of string evidence paths (`HardwareCapability.kt:32-40`). `readable` means `access != NONE`; `writable` means `READ_WRITE`. `HardwareCapabilitySnapshot.generatedAtMs` is a wall-clock timestamp, not per-source freshness or a cache identity (`:42-50`). Do not add every Atlas observation to the control enum merely to display it. Reuse `HardwareFeature` where applicable and use explicit observation IDs for non-control domains.

The mutation chain is real and must remain unchanged:

```text
MaxAiEngine.decisionCycle
  -> HardwareCapabilityResolver.resolve(appContext)
  -> ControlRegistry.build(capabilities)
  -> canonical CPU/GPU/boost controls
  -> HardwareControlArbiter / existing backend transactions
```

Evidence: `APP/core/maxai/MaxAiEngine.kt:393-411`; `APP/core/maxai/ControlRegistry.kt:88-98,102-135,140-183`. Only CPU ceiling, GPU ceiling and CPU boost are in this registry today. `HardwareControlKey.cpuLimits`, `gpuFrequency`, `CPU_BOOST` are defined in `APP/core/hardware/HardwareControlKey.kt:10-23`. Preserve their identities; never derive an alternative key from an Atlas provider ID, symlink alias or report record.

`APP/core/hardware/HardwareControlArbiter.kt:10-11,41-96` is a Hilt singleton, checks `ManualControlLocks.blocks`, captures a live baseline, records intent and resolves the winner under the shared transaction. `:158-182` verifies actual values; `:196-215` restores baselines on failure; `:234-244` requires the configured shared ownership store. **No Atlas method should submit/release/reconcile a control or repair drift.** Even calling `submit` to "test" capability is a mutation experiment.

### App Session And Shizuku

There is no `AppSession` type in current main sources. ADR-17 is partially implemented as local Compose `rootStatus`/`moduleInstalled` in `APP/MainActivity.kt:190-209`. `refreshStatus` calls `RootUtils.requestRootAccess` and `isModuleInstalled` once at launch, not a reusable observable capability/privilege session object. `RootUtils` lives in **`APP/ui/util/RootUtil.kt`**, not `RootUtils.kt`; `requestRootAccess` closes a cached non-root shell and invokes `Shell.getShell` (`:147-153`).

Shizuku is **present, partial, and not a shell transport**:

| Existing Part | Verified Source | Meaning |
| --- | --- | --- |
| API/provider 13.1.5 dependencies and permission/provider declarations | `manager/gradle/libs.versions.toml:49-52`; `manager/app/build.gradle.kts:165-168`; `manager/app/src/main/AndroidManifest.xml:49-50,88-96` | Do not add a duplicate Shizuku dependency/provider or claim it is absent. |
| Binder/permission listeners, `StateFlow<ShizukuState>`, version and UID | `APP/core/privilege/ShizukuGateway.kt:16-23,35-102` | Reuse readiness and explicit permission requests. Binder death/permission change must invalidate Atlas evidence. |
| Public property reader | `ShizukuGateway.kt:104-110` | Available facility, but no blanket grant to read sysfs or execute shell. |
| No implemented UserService/shell adapter | `ShizukuGateway.kt:112-118`; no UserService implementation found in main sources | Classify privileged discovery via Shizuku as `backend-unavailable` where no operation exists. New Shizuku/vendor transports are deferred by CONTEXT. |
| Cached root inspection and explicit root request | `APP/core/privilege/PrivilegeManager.kt:21-62` | `cachedRootGranted` does not prompt. Do not call `requestRoot` from discovery. |
| Privilege feature catalog is a required-tier list | `APP/core/privilege/PrivilegeLevel.kt:55-89` | `availableAt` compares ranks; it does NOT prove the listed Shizuku operation is implemented. Do not convert it directly into verified support. |
| Permission UI already exists | `APP/ui/component/PrivilegePanel.kt:68-89,139-149,180-187`; `MaxDestination.Privilege` | Link to it rather than creating a second permission panel in Atlas. |

Important lifecycle mismatch: `ShizukuGateway` listener callbacks refresh its own state (`:45-50`); `PrivilegeManager.snapshot` is recomputed only when its `refresh` is called (`:51-62`). Atlas should observe both existing state sources or explicitly synchronize refresh at the repository boundary; merely collecting `PrivilegeManager.snapshot` is not proof every binder-death transition will reach Atlas.

Early-start constraints:

- `APP/MaxManagerApplication.kt:37-48` injects and starts Max AI at application startup. `APP/core/maxai/MaxAiEngine.kt:251-277` starts 30-second and 1-second jobs; `DeviceStateCollector.collect` is called even before a Diagnostics screen opens. Do not make Atlas start its scan from an injected constructor or make Max AI depend on Atlas.
- `APP/MainActivity.kt:89` unconditionally invokes `RootIpcManager.bind`. The latter invokes `RootService.bind` (`APP/ui/util/RootIpcManager.kt:60-64`), and its `ipc` stays null until connection. Null here can mean **not connected yet**, not unsupported.
- Onboarding reads `PrivilegeManager.snapshot`, allowing a Shizuku-ready state in `canGoNext` (`APP/ui/mainscreens/GetStartedScreen.kt:254-261`), but this Boolean currently only changes button colors (`:377-379`), not `enabled` or the click guard (`:359-375`). It is not a reliable permission gate.
- Onboarding performs automatic root checks on entry/resume (`GetStartedScreen.kt:264-282`) and finishes with root `pm grant` / root `am start -S` (`:367-373`). A no-root user may get the completion preference written without the intended restart. Plan a direct read-only entry/navigation path, not an assumption that onboarding is already correct.
- Primary bar visibility still requires root AND module (`MainActivity.kt:304`); Home's Settings action exists without a root condition at `APP/ui/mainscreens/HomeScreen.kt:86`. Test the actual route instead of interpreting hidden primary navigation as absent read-only APIs.

## Discovery Gaps

These are source-level failure paths, not device reproductions or new product fixes in this task.

| Gap | Exact Existing Source | Required Atlas Treatment |
| --- | --- | --- |
| Exists is treated as readable; write permission as verified capability | `HardwareCapabilityResolver.kt:54-66,73-82,117-132`; `HardwareCapability.kt:38-39` | Separate existence, successful readable value, access-mode observation, known backend eligibility, and actual verified transaction. Never infer verified control from `test -w`. |
| Permission/absence/backend failure erased | `RootFileAccess.kt:15-30,90-104,132-142`; `APP/service/MtkRootService.kt:44-50,85-96` | Typed outcome with backend identity and structured cause; if cause was erased, say unknown/unavailable, not absent. Preserve failures across fallback attempts. |
| IPC exception can terminate the whole read fallback | `RootFileAccess.read:26-30` wraps the whole IPC/File/shell expression in one `runCatching` | Atlas fallback must isolate each transport and continue only under an explicit safe policy. |
| Unbounded, blocking shared shell/file reads | `RootFileAccess.kt:26-30,95-99,134-136`; `MtkRootService.kt:47` | Enforce operation and aggregate bytes/nodes/time before parsing; own cancellable work; do not wedge the app's shared root shell. |
| Directory API is not uniformly directories | `RootFileAccess.kt:90-104`: IPC/shell returns names, direct branch filters directories; `MtkRootService.kt:93` maps every entry | Validate basename and file kind; distinguish unreadable/empty listings; never trust names as safe paths. |
| Shell glob accepts an interpolated pattern | `RootFileAccess.globDirectories:111-117` | Never pass catalog/report/user strings into shell glob syntax. Enumerate fixed parents and validate returned basenames. |
| CPU capability resolver differs from backend | Resolver `:48-66` uses only policy directories; `CpuHardwareBackend.policies:60-81` supports legacy `cpuN/cpufreq` and alias grouping | Reuse backend discovery; don't introduce a third CPU detector. Test legacy topology and partial-policy permissions. |
| CPU topology strings and labels are not canonical proof | `CpuHardwareBackend.kt:73-80` groups by raw `related_cpus`/`affected_cpus`; `CpuTopologyUtil.kt:88-111` parses whitespace and labels by ordinal | Normalize CPU lists/ranges and resolve aliases. Do not infer efficiency/prime status from index, string ordering or popular SoC layouts. |
| GPU unit confidence includes magnitude heuristics | `GpuHardwareBackend.inferFrequencyUnit:434-447`, `readMtkOppMap:544-579` | Keep heuristic evidence labeled as inferred; explicit reviewed ABI units outrank magnitude. Reject overflow, mixed-unit and identity conflicts. Do not upgrade existing READY wording to a new verified transaction. |
| GPU candidates may alias one device | `GpuHardwareBackend.discoverCandidates:453-455`, `selection:117-140` | Canonical symlink/device identity plus evidence; preserve genuine equal-ranked ambiguity. No arbitrary "first GPU wins" rule. |
| Thermal missing values become zero and names are heuristic | `ThermalUtil.kt:80-87,180-203,207-225,319-333`; `GpuHardwareBackend.kt:615-628` | Read raw type+temp with source-specific units; missing/malformed stays null. A zone number or broad `ap`/`tsens` match is not a proven CPU junction sensor. |
| ThermalService fallback loses uncertainty | `ThermalUtil.kt:136-177` returns arrays of zeros on failure | Platform unavailable is not 0 C; retain sensor type/name/source and distinguish battery/skin/CPU/GPU. |
| Memory negative cache conflates all failure causes | `MemoryPressureReader.kt:42-84` | Reuse the retry-policy idea, not the collapsed `UNSUPPORTED` result or wall-clock assumptions. Cache permission denial separately and invalidate on privilege changes. |
| PSI summary is stale documentation | `DeviceStateCollector.kt:47-55,113-120` has seven fields; main-source search found `MemoryPressureReader` only in its own file | PSI reader/parser exists but has no production caller in current sources. Atlas may surface PSI evidence; do not claim an existing AI stall gate or alter AI policy in this phase. |
| Charge versus energy units mixed | `BatteryHealthUtil.kt:44-52,85-94` accepts `energy_full[_design]` as `designUah/currentFullUah` | Only charge nodes provide microamp-hours. Energy nodes need their own units; no conversion without explicit voltage/semantics. Reuse positive-number/cycle parsers only where their units match. |
| "loadState" can write | `ChargingViewModel.kt:191-196`; `TouchBoostViewModel.kt:124-130`; `DisplayStudioViewModel.kt:106-114`; `NetworkSchedulerViewModel.kt:180-184,285-298` | Never instantiate/call those VMs to discover capabilities. Separate read-only access from reapplication behavior. |
| Bypass compatibility checker actively experiments | `BypassCheckScreen.kt:193-194` -> `archdaemon/jni/Main.c:76` -> `archdaemon/jni/src/BinaryCLI/BypassCompatibility.c:53,62,80-85` | Explicitly prohibited probe. It toggles charging nodes and persists properties/config. No `-cbc` in Atlas. |
| Refresh may repair hardware drift | `ThermalDevicesViewModel.kt:135-145` calls `policyGuard.checkAndRepair` | Use raw read functions only, never screen refresh as a read-only backend. |
| Lazy caches have no privilege/build invalidation | `XiaomiVendorHalUtil.kt:48-56`; existing MemoryPressureReader above | Atlas cache must be separately keyed and invalidated; empty once is not unsupported forever. |
| Unknown vendor is weakly inferred | `HardwareCapabilityResolver.kt:21-33` uses broad substrings and manufacturer fallback | Vendor metadata is a hint. Generic providers still run; never reject an unknown phone by model string. |
| Property read defaults erase hidden-API failure | `APP/ui/util/PropertyUtil.kt:25-42` | Blank property is not proof of absent hardware; use public Build fields and explicit unavailable provenance. This file does not use Shizuku as a fallback. |

## Domain Support Matrix

This is the matrix the implementation must preserve/extend with honest evidence. "Existing control" is not permission for Atlas to invoke it. Domains not implemented in the bounded catalog remain visible as deferred/unsupported with a reason, not silently omitted.

| Domain | Current Read Analog And Limits | Phase 1 Resolution / Non-Goals |
| --- | --- | --- |
| CPU policy frequency/governor | `APP/core/hardware/CpuHardwareBackend.kt:7-94`: policy enumeration, kHz bounds, advertised ladder, `stats/time_in_state` fallback, legacy aliases | Reuse with narrow read IO. Capture normalized related/affected CPU sets and policy identity. A missing ladder can still allow observation; never invent OPPs. No new overclock/voltage control. |
| CPU online/boost, cpuset/scheduler/uclamp | Resolver `:68-87`; `APP/ui/util/CpuTopologyUtil.kt:222-249`; `APP/ui/viewmodel/NetworkSchedulerViewModel.kt:50-60,178-281` | Fixed allowlisted read inventory only. Core0/unknown-online defaults are not proof. No hotplug tests, cpuset moves, task-profile changes or scheduler writes. |
| Qualcomm GPU / generic devfreq | `GpuHardwareBackend.kt:453-539`; KGSL load fallback `:593-603` | Reuse selection/parser logic; explicitly validate device identity and ABI units. Current discovery requires a matching `/sys/class/devfreq` entry; KGSL-only control is not a proven standalone backend. |
| MediaTek/Mali GPU | `GpuHardwareBackend.kt:11-19,484-508,544-591` | Read-only OPP/index evidence; keep signed-table, index and raw units distinct. Mali identity alone does not prove MediaTek control semantics. No fixed-index write experiment. |
| Exynos/Tensor/unknown GPU | Current `Family` is `QUALCOMM/MALI/UNKNOWN` (`GpuHardwareBackend.kt:28`), generic name/path scoring `:459-473` | Try generic valid devfreq evidence; no dedicated Exynos/Tensor writer is present in this backend. Unknown/ambiguous remains visible. Synthetic fixtures are not device support proof. |
| Thermal zones/trips/cooling | `APP/ui/util/ThermalUtil.kt:180-241`; pure `ThermalModel.kt:72-92` distinguishes passive/active from shutdown trips | Bounded type/temp/trip/cooling reads. Retain sensor identity and explicit C/milli-C/deci-C semantics. Do not disable zones, kill thermal services or change cooling states. |
| Android thermal status/headroom / ADPF | No `getThermalHeadroom`, thermal status listener or PerformanceHint/ADPF implementation found in current main Kotlin sources | If planned, public thermal status API29/headroom API30+ is read-only, availability-gated and nullable; it is not equivalent to per-zone telemetry. ADPF is not authority over other apps. Performance hints/control are deferred. |
| RAM / pressure / ZRAM | `HardwareDataSource.kt:85-88`; `MemoryPressureReader.kt`; pure `APP/core/maxai/MemoryStall.kt:76-108`; `ZramHardwareBackend.kt:16-37`; `ZramPlatformUtil.kt:42-85` | Reuse PSI parsing with raw-error sidecar, and ZRAM read parsing after safe seam. Current resolver/platform helper hardcode zram0 while backend selects first zram*. Include multiple-device observation explicitly. No swapoff/reset/writeback/compaction experiments. |
| Battery / charging / power | Broadcast analog `HardwareDataSource.kt:90-107`; `ThermalUtil.kt:100-123`; `BatteryHealthUtil.kt:59-82`; charging VM reads `:207-282` | Prefer Android battery API for rootless evidence; fixed power-supply attributes only as safe fallback. Keep microamp, microvolt, microamp-hour and microwatt-hour separate, raw current polarity distinct. Charge limits/SIC/fast charge/bypass control remains existing or unverified, never newly enabled. |
| Display | Resolver `:135-142`; `HardwareDataSource.kt:152-163`; `ResolutionViewModel.kt:74-104` | Public display modes/metrics are rootless reads; refresh/mode capability is not evidence a global override is allowed. Vendor display/HBM/HDR inspection remains narrowly declared; no HAL transactions or guessing mode IDs. |
| Touch / responsiveness / FPS | `TouchBoostViewModel.kt:51-63,78-82`; `APP/XiaomiVendorFeatures.kt:142-154` explicitly disables unverified Xiaomi touch; `XiaomiVendorHalUtil.kt:45-56` is declaration-only | Read existing declared nodes only; touch report-rate candidates are intentionally empty. Do not equate advertised service with usable HAL. FPS/SurfaceFlinger/package data is privacy/privilege-sensitive; no new polling/overlay or tuning engine. |
| Storage / I/O / compiler | `HardwareDataSource.kt:132-139` uses StatFs; `StorageHealthUtil.kt:38-86` checks three devices and parses wear | Rootless capacity available where API permits; safe eMMC/UFS metadata may be optional bounded evidence. A device name is not media identity proof. Read-only queue scheduler inventory can be cataloged; TRIM, block writes, dex2oat and package compilation are deferred. |
| Network | `HardwareDataSource.kt:142-149`; `DeviceStateCollector.kt:138-156`; `NetworkSchedulerViewModel.kt:50-57` | API support/aggregate counters and selected TCP ABI reads only. Do not reuse fixed-interval rate arithmetic as a measured rate without elapsed time. No IP/MAC/SSID/traffic destination/package list in report; no DNS or network-policy writes. |
| Sensors | `APP/ui/util/SensorMonitorUtil.kt:43-58,66-117`; `SensorInventory.kt` typed inventory | Reuse public inventory. Light one-shot timeout/listener cleanup is an analog, not permission for permanent sampling. Existing catch-all converts cancellation to unreadable; Atlas cancellation must remain cancellation. |
| Module/root/native availability | Existing `PrivilegeManager`, `RootIpcManager`, `DeviceBlueprint.rootImplementation:97-102`, native availability in existing diagnostics | Observe connectivity/privilege/backend availability separately. Direct `File.exists` under `/data/adb` cannot prove which root manager is installed. No root escalation, daemon restart, JNI load experiment, SELinux bypass or boot-script changes. |

## File Classification

There are **21 primary candidate product paths** below: **12 proposed NEW Kotlin files** and **9 existing integration/resource files**. They are proposed planning boundaries, not implemented files. Test paths and conditional no-root entry changes are listed separately. Three match classes are used: exact reuse site, role/data-flow analog, and no adequate existing implementation.

| New/Modified File | Role | Data Flow | Closest Existing Analog / Assignment |
| --- | --- | --- | --- |
| **NEW** `APP/core/atlas/AtlasModels.kt` | Model/contracts | Immutable observations/transform | `HardwareCapability.kt:32-50`, `GpuHardwareBackend.kt:81-87`, `ProfileSharing.kt:64-112`; role match, richer provenance needed |
| **NEW** `APP/core/atlas/AtlasCatalog.kt` | Reviewed data/provider registry | Versioned static knowledge -> candidate specs | GPU fixed paths `:11-19`, feature registry `HardwareFeature.entries`; partial match, no existing provenance-versioned catalog |
| **NEW** `APP/core/atlas/AtlasResolver.kt` | Deterministic resolver | Per-feature ordered request-response | `GpuHardwareBackend.selection:117-140`; `FileSearchEngine.kt:36-103`; role/data-flow match, do not reuse broad BFS |
| **NEW** `APP/core/atlas/AtlasEvidenceStore.kt` | Store/codec | Bounded app-private cache | `MemoryPressureReader.shouldProbe:77-84`, `FileStore.kt:41-79,125-143`; partial match, invalidation/atomic persistence are new |
| **NEW** `APP/core/atlas/AtlasRepository.kt` | Service/state owner | Single-flight scan -> StateFlow | `MaxAiViewModel.kt:42-72` for observable state, `CoalescingCycleRunner.kt:11-27` for concurrency idea; do not share the AI runner instance |
| **NEW** `APP/core/atlas/AtlasBackendProvider.kt` | Provider adapter | Existing CPU/GPU parsed evidence -> observations | Existing CPU/GPU backend read methods; strong data-flow match, not an alternate writer |
| **NEW** `APP/core/atlas/AtlasPlatformProvider.kt` | Provider adapter | Thermal/memory/power/display/sensor reads -> typed evidence | `ThermalUtil`, `MemoryStall`, `ZramHardwareBackend`, `BatteryHealthUtil`, `SensorMonitorUtil`; selective reuse only; split by domain only if size warrants |
| **NEW** `APP/core/hardware/ReadOnlyProbeAccess.kt` | IO boundary | Typed bounded file/operation request-response | `RootFileAccess` for privilege context; dedicated-shell lifetime in `LogsViewerViewModel.kt:407-414,435-448,589-603`; **no adequate bounded/error-preserving implementation** |
| **NEW** `APP/core/diagnostics/AtlasSupportReport.kt` | Model/privacy codec | Selected immutable evidence -> versioned sanitized bytes | `ProfileSharing.encode:160-180` and decode schema guard `:201-202`; role match, allowlist/redaction are new |
| **NEW** `APP/core/diagnostics/AtlasReportExporter.kt` | Exporter | Preview-approved bytes -> app cache file/URI | `LogUtil.getShareLogIntent:195-206`; only share mechanics, never log collection |
| **NEW** `APP/ui/viewmodel/AtlasViewModel.kt` | ViewModel | Repository flow + user events | `MaxAiViewModel.kt:37-54,86-90`; constructor-injected Hilt, no hardware IO in VM |
| **NEW** `APP/ui/mainscreens/AtlasDiagnosticsSection.kt` | Component/preview | State -> existing diagnostics sections/dialog | `MaxProgressStrip`, `MaxSection/MaxGroup/MaxRow`, `MaxTabbedDialog`; role match |
| **EXISTING** `APP/core/hardware/CpuHardwareBackend.kt` | Backend read seam | Read request-response | Add injectable discovery-only IO/defaults around `:22-94`; preserve writer methods |
| **EXISTING** `APP/core/hardware/GpuHardwareBackend.kt` | Backend read seam | Read request-response | Split narrow `ReadIo` from existing `Io:35-49`; selection/refresh read-only, transactions retain `Io` |
| **EXISTING** `APP/core/hardware/HardwareCapabilityResolver.kt` | Compatibility facade | Pure diagnostic projection | Exact facade; keep legacy `resolve(context)` behavior isolated from Atlas cache |
| **EXISTING** `APP/core/di/DataModule.kt` | DI bindings | Construction | `:26-40`; register interface implementations once, not both here and AppModule |
| **EXISTING** `APP/core/diagnostics/DiagnosticCenter.kt` | Diagnostic source | Structured in-memory snapshot | Add safe category/level/count projection, leave normal logging semantics intact |
| **EXISTING** `APP/ui/mainscreens/SettingsScreen.kt` | Entry integration | Navigation event | Replace stale comment `:320-323` with a small unconditionally reachable Atlas/Diagnostics row |
| **EXISTING** `APP/ui/mainscreens/DiagnosticsScreen.kt` | UI integration | State subscription | Replace capability/runtime/report invocation sites `:111-117,185-192,221-223`; preserve unrelated cards |
| **EXISTING** `RES/values/max_screen_strings.xml` | Resources | English user copy | Existing diagnostics strings around `:313-361`; use `max_atlas_*` keys in this already registered file |
| **EXISTING** `RES/values-ar/max_screen_strings.xml` | Resources | Arabic user copy | Paired keys/specifiers in same change (ADR-14); no new locale or locale config |

This uses existing `max_screen_strings.xml`, registered in `crowdin.yml:10-11`, so no Crowdin edit is required. If the final planner chooses a separate Atlas strings file instead, both EN/AR files and an explicit Crowdin entry must be added in the same plan; that is a changed allowlist, not an omitted step.

## Pattern Assignments

### Read IO And Existing Backend Adapters

Best existing injectable boundary is `APP/core/hardware/GpuHardwareBackend.kt:35-49`:

```kotlin
interface Io {
    fun exists(path: String): Boolean
    fun writable(path: String): Boolean
    fun read(path: String): String?
    fun write(path: String, value: String): Boolean
    fun listDirectories(path: String): List<String>
}
```

**Copy the injection pattern, not the authority.** The proposed read-only interface has no `write`, `remove`, `exec`, arbitrary command or chmod API. Keep the legacy `Io` extending the read interface so existing writer tests/clients remain valid. Existing CPU discovery should get a default read interface parameter and feed the same parser as Atlas, not a new list of CPU nodes in a screen.

The typed Atlas boundary must retain at least operation, requested and canonical path, backend, privilege-generation, observed time, unit/identity evidence, limited bytes, and result cause. Distinguish `Absent` (positive proof), `PermissionDenied`, `ReadOnly`, `Malformed`, `Ambiguous`, `BackendUnavailable`, `Stale`, `TimedOut`, `BudgetExceeded`, `Cancelled`, and successful observations. These need not be a single mutually exclusive enum: e.g. readability, freshness, semantic confidence and control eligibility are different axes. An unrecognized error is unknown, not ENOENT.

Use a concrete pure data class for observation payload and an interface for IO; do not use lambdas carrying arbitrary shell strings in the catalog. An Atlas provider may only request reviewed read operations, never callbacks that can mutate hardware.

For privileged reads, use an already authorized existing root transport only after checking the current privilege/backend generation. The dedicated-shell analog does not itself prove no new root prompt, hard cancellation or bounded output. Do not call `Shell.getShell`, `RootService.bind` or `Shizuku.requestPermission` as fallback from Atlas. If the available transport cannot preserve error classes and enforce limits, return `BackendUnavailable` for that branch; do not quietly fall back to unbounded RootFileAccess. Blocking vendor syscalls may resist interruption even with a cancelled coroutine, so cap worker concurrency and prove process/descriptor cleanup where possible rather than spawning replacement workers without limit.

### Resolution And Evidence Cache

The GPU selection pattern explicitly retains ambiguous candidates instead of silently choosing one (`GpuHardwareBackend.kt:120-127`):

```kotlin
val bestScore = candidates.maxOf(Candidate::score)
val best = candidates.filter { it.score == bestScore }
if (best.size != 1) {
    return Selection(
        SelectionState.AMBIGUOUS,
        candidates = best.map(Candidate::device),
        reason = "multiple-equally-proven-gpu-providers",
    )
}
```

Apply that property to Atlas, but rank **reviewed ABI matches and validated runtime identity**, not vendor name alone. Per feature: catalog candidates -> validate -> existing backend read parser -> if unresolved, bounded discovery -> explicit terminal reason. Success on CPU must not suppress unresolved GPU, and unknown vendor must not skip generic providers. An error in a candidate is evidence; exhaustion means only that the allowed search completed, not that the device lacks hardware.

Existing negative-cache policy (`MemoryPressureReader.kt:77-84`) is a useful minimal test shape:

```kotlin
fun shouldProbe(
    cached: MemoryStall.Sample,
    lastProbeAtMs: Long?,
    nowMs: Long,
    retryAfterMs: Long = RETRY_AFTER_MS,
): Boolean = cached.measured ||
    lastProbeAtMs == null ||
    (nowMs - lastProbeAtMs) >= retryAfterMs
```

Do not share this singleton cache with Atlas. Atlas's evidence store is independent of `CredibilityStore` and learning: no reward/outcome scores, action priorities or learned knobs. Store only bounded capability observations and retry metadata. Key by catalog/schema version, device/build/kernel identity, process ABI, boot/session freshness and privilege/backend generation. Private identity used for invalidation must not leak into the support report by default.

Recommended initial **design budgets, not measured performance**: one active scan, at most 12 seconds overall, at most 1 second per file/command operation, 128 enumerated entries per parent and 512 attribute attempts total, 4 KiB scalar/64 KiB table cap, 512 KiB aggregate bytes. Use only declared parent roots, with at most two discovery levels and eight symlink resolutions; never traverse all `/sys`, `/proc` or `/data`. Adjust only with tests and later device timing evidence. A platform event probe such as the existing 1.5-second light read must be explicitly charged to the same deadline or left out of default discovery.

Positive static evidence can have a 24-hour upper TTL, transient/absent negative evidence a 10-minute upper retry delay, with immediate invalidation on identity/catalog/privilege/backend change or contradictory runtime evidence. These are starting policy choices, not guarantees that observations remain true. Live telemetry is never served as live from that TTL. Failed reads do not extend last-success freshness. Use monotonic elapsed time within a run; null means no attempt, not zero. Cancellation invalidates the in-flight generation and prevents stale callbacks/cache publication; it is not cached exhaustion. Explicit Retry does not bypass rate/budget limits or request privilege.

`FileSearchEngine.kt:54-68` demonstrates injectable cancellation/clock and `DeepSearchOutcome.complete` rejects incomplete search (`FileSearchPlan.kt:55-66`). It does **not** cancel a blocked lister; do not advertise it as a solution to syscall/shell cancellation.

### Concurrency And DI

Existing coroutine failure discipline to copy is `APP/core/maxai/MaxAiEngine.kt:285-302`:

```kotlin
try {
    cycle()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    // Record a typed failure for this operation.
} finally {
    _cycleStatus.update { it.copy(inFlight = false) }
}
```

The excerpt omits unrelated status/log code intentionally. Do not surround a suspend discovery pipeline with bare `runCatching`/`getOrNull`; that catches `CancellationException`. Share one repository scan across screen recompositions with an explicit job/generation; `CoalescingCycleRunner.kt:16-27` is a concurrency analog, not a dependency on AI policy.

Hilt pattern from `APP/core/di/DataModule.kt:30-40`:

```kotlin
@Provides
@Singleton
fun provideHardwareDataSource(
    impl: HardwareDataSourceImpl
): HardwareDataSource = impl
```

Use constructor injection for concrete Atlas repository/VM classes, an explicit binding only for interfaces, and `@ApplicationContext` for app-private storage. `APP/core/di/AppModule.kt:25-27` already provides `DispatcherProvider`; its `main/io/computation` contract is in `APP/core/threading/DispatcherProvider.kt:6-15`. Constructors do not scan. Avoid a second independent scope/scan owned by the composable.

`@HiltViewModel class AtlasViewModel @Inject constructor(...)` should expose immutable state and `start/retry/cancel/preview` events. Its screen default must be `hiltViewModel()`, never `viewModel()`; existing regression guard is `TEST/ui/viewmodel/ViewModelInstantiationTest.kt:87-108`. Use `collectAsStateWithLifecycle` for the new UI; the lifecycle-compose dependency already exists (`app/build.gradle.kts:171`).

### Safe Report, Not Full Logs

`APP/core/diagnostics/DiagnosticCenter.kt:57-65,75-107` has a 64-entry ring, 240-character message limit, deduplication and observable issue count. Its existing `formatBlock` (`:115-131`) includes free-form messages and throwable text (`:81-82`). First WARN/ERROR recording forwards through the daemon shell (`:99-107,143-146`), while INFO does not. **Neither exporting the whole formatted block nor recording every denied probe as ERROR is a safe Atlas default.** Add a structured read-only snapshot that exposes allowlisted component/level/count summaries; drop arbitrary messages and throwable text unless an individually reviewed redactor explicitly accepts them.

Versioned serialization analog: `APP/ui/util/ProfileSharing.kt:160-180` explicitly writes fields and JSON nulls; `:201-202` rejects unsupported schema. Preserve that explicit-field style. Atlas report input should be the frozen, user-selected sanitized observation snapshot, not a function that performs fresh privileged probes while serializing. Validate all string/array/byte limits before construction and before export.

Report contract:

- Include report schema, app version, catalog version, coarse device/kernel/API/ABI metadata chosen in preview, attempted provider/stage, reason codes, safe unit/identity evidence and limits reached. Preserve null/unknown and observation age.
- Exclude IMEI, serial, Android ID, accounts, network addresses/SSID/MAC, user file paths, app/package lists, ownership tokens, arbitrary command output, full properties, native model paths, pstore, tombstones, configs and unrestricted logs. Hashing a stable personal ID is not anonymization.
- Canonical hardware paths may themselves contain unique identifiers. Export catalog operation IDs and reviewed safe path templates by default; optional exact device-specific metadata is bounded and previewed. Do not put private cache identity/fingerprints into the report implicitly.
- User can exclude optional metadata/diagnostic sections; preview reflects the exact resulting bytes. Generate/share only after explicit consent; no network calls, upload client, recipient address or remote support packs.
- A scan cancelled or stopped at a budget/permission boundary remains marked incomplete. Do not show "all safe options exhausted" or the last-resort report prompt for a cancelled run. Permit explicit review of existing partial evidence without misrepresenting completion.
- Export sanitized bytes to `cacheDir/atlas-reports/` under an app-generated filename, not a user-controlled path. Store private evidence in `filesDir/atlas/` (or `noBackupFilesDir`) rather than a provider-visible cache directory. Use atomic cache replacement and bounded reads; `FileStore.writeText` is only a layout analog, not atomic persistence.
- Retain a shared artifact long enough for the receiving app to open it; age-based cleanup/revocation is safer than immediate deletion when the chooser returns. Current Settings deletes its archive on ActivityResult (`SettingsScreen.kt:225-234`), which does not prove the recipient finished reading.

The current FileProvider is already sufficient for cache shares: `manager/app/src/main/AndroidManifest.xml:98-106` declares `${applicationId}.provider`, non-exported with URI grants; `RES/xml/file_paths.xml:3-4` has two broad cache roots, and `:10` has shared external storage. Do **not** broaden it to `/`, filesDir or raw evidence. No manifest/provider edit is required for the proposed sanitized cache artifact. The existing broad cache exposure is a reason to keep raw evidence elsewhere, not a reason for an app-wide provider migration in this phase.

Share mechanics from `APP/ui/util/LogUtil.kt:195-205`:

```kotlin
val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
val intent = Intent(Intent.ACTION_SEND).apply {
    type = "application/gzip"
    putExtra(Intent.EXTRA_STREAM, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
```

For Atlas, set the actual JSON/text MIME type, use localized chooser text, add `ClipData` for the granted URI, handle no receiving activity, and never grant write access. Do not call `getShareLogIntent` unchanged: its MIME and chooser are specifically logs.

## UI Integration

### Actual Entry Points

- `APP/ui/navigation/MaxDestinations.kt:173-174` already declares Diagnostics and Logs under Settings; `APP/ui/navigation/MaxNavGraph.kt:75-76` already renders both. No Atlas primary destination or new route literal is needed.
- Settings **removed** those entries (`SettingsScreen.kt:320-323`). Diagnostics is still reachable from Home's overview (`APP/ui/mainscreens/LegendaryHomeDashboard.kt:155`), and Diagnostics links to Logs (`DiagnosticsScreen.kt:243-247`). Do not report either screen as dead globally.
- Insert `MaxNavigationRow` to the existing `MaxDestination.Diagnostics` in the Settings feature area, outside `uiState.isLoaded` gates for module settings. Navigation is `MaxNavActions(navController).navigateTo(MaxDestination.Diagnostics)`; permission recovery uses existing `MaxDestination.Privilege`.
- Current Diagnostics runs resolver + runtime snapshot synchronously in `LaunchedEffect` (`:114-117`) and again in an onClick (`:185-189`), doing repeated CPU/GPU discovery. Replace these Atlas-facing invocation sites with one repository state stream; an effect alone does not put work on IO.
- Current `HardwareReportCard` immediately regenerates and copies the report (`DiagnosticsScreen.kt:958-973`). Replace this Atlas report block with preview/cancel/share rather than leaving a competing unreviewed Atlas export action.

### Exact Components And Tokens

| Existing Component | Signature / Source | Use |
| --- | --- | --- |
| `MaxListScreen` | `MaxScreenScaffold.kt:238-251`: title, onBack, subtitle, condition, banner, snackbarHostState, actions, header, `LazyListScope.() -> Unit` content | If removing the existing local Diagnostics Scaffold, preserve its cards inside this lazy shell. Never nest LazyColumn in `MaxScreen`'s vertical scroll. |
| `MaxSection` | `MaxStructure.kt:55-60`: title, modifier, description, trailing, ColumnScope content | One Atlas capability section with a concise explanation. |
| `MaxGroup` / `MaxRow` | `MaxStructure.kt:100-114,139-147` | Group domain/feature reasons, not one decorative card per attribute. Rows can remain inspectable even when control is unsupported. |
| `MaxNavigationRow` | `MaxControlRows.kt:279-288`: title, onClick, modifier, subtitle, valueText, icon, iconTone, enabled, lockedReason | Settings entry, Retry/report actions with explicit unavailable reasons. Uses AutoMirrored caret (`:322`). |
| `MaxProgressStrip` | `MaxProgressStrip.kt:52-59`: title, **nullable percent**, detail, tone, cancelLabel, onCancel | Known/discovery stages, operation counts and cancel. Null progress when total is unknown; no elapsed-time fake percentage. |
| `MaxCondition` / `MaxConditionNotice` | `MaxCondition.kt:65-119,254` | Partial denied/unavailable/cancelled outcomes as nonblocking notice, not an empty whole screen. Do not use `Applied` to describe a completed scan. |
| `MaxMetric` / `MaxMetricLine` | `MaxMetric.kt:61-68,287-289` | Actual values with unit/source/age and Snapshot/Stale/Unreadable trust. These display-only values must not default to Live. |
| `MaxTabbedDialog` | `MaxTabbedDialog.kt:58-70`: visible, title, tabs, selected, onSelect, onDismiss, dismissLabel, optional confirmLabel/onConfirm, body | Bounded report preview; one tab can be used (tab row appears only when >1). Body is height-capped/scrollable (`:108-117`), so do not put an unbounded lazy list inside it. |
| `MaxConfirmDialog` | `MaxDialogs.kt:60-71`: visible, title, message, confirmLabel, onConfirm, onDismiss, technicalDetail, dismissLabel, destructive | Optional final consent for exact frozen bytes. Note it calls **onDismiss before onConfirm** (`:118-120`); do not clear the approved payload before share reads it. |

Tokens verified in `APP/ui/design/MaxTokens.kt`: `MaxSpace.gutter=20.dp`, `section=28.dp`, `row=8.dp`, row padding 14/12.dp (`:31-54`); `MaxRadius.row/group/sheet=14/22/28.dp` (`:78-83`); minimum touch target 48.dp (`:86-99`); motion 90/160/240/360ms (`:153-157`); existing `MaxTone` and `MaxDataTrust` (`:167,238`). Use Material typography through these components, not a new theme/font/palette/animation system.

Proposed minimal visible flow:

```text
Settings / Atlas & Diagnostics (existing Diagnostics route)
  -> Atlas status + version/age (no scan in constructor)
  -> Reviewed knowledge -> Safe discovery for unresolved features
  -> Per-domain observation / reason / source / retry eligibility
  -> Retry or Cancel (cancellation never masquerades as exhaustion)
  -> Optional support-report suggestion only after eligible stages complete
  -> Preview selected/redacted contents -> Cancel OR explicit Share
```

Keep other diagnostics cards and their feature actions unchanged. New Atlas UI must not increase existing literal-string or direct-hardware-write debt. Any touched Atlas surface uses paired EN/AR resources, start/end padding, logical/AutoMirrored navigation, textual status beyond color, accessible action names, and real focus/reading order. Long paths/reasons must be inspectable in preview rather than lost to a two-line row ellipsis. `MaxMetricLine` clears semantics and speaks label/value/trust, not all source/detail text (`MaxMetric.kt:296-320`); include important permission/unit reasons in accessible adjacent rows, not only a technical note. Device checks must include RTL, font scale, small viewport, rotation, back navigation and chooser cancellation.

## Small Plan Allowlists

These are exclusive **candidate** allowlists for the planner to turn into dependency-ordered plans. Do not give one executor every file below. New filenames are proposals, and a later split must update the affected plan explicitly.

| Plan Slice | Product Allowlist | Tests / Acceptance |
| --- | --- | --- |
| A. Evidence contract and reviewed catalog | **NEW** `APP/core/atlas/AtlasModels.kt`, `AtlasCatalog.kt` | **NEW** `TEST/core/atlas/AtlasCatalogTest.kt`: schema/provenance/safety/unit requirements, unknown vendor fallback, duplicate/conflicting entries, invalid paths. No runtime IO. |
| B. Bounded read-only access | **NEW** `APP/core/hardware/ReadOnlyProbeAccess.kt` | **NEW** `TEST/core/hardware/ReadOnlyProbeAccessTest.kt`: denial vs missing, bytes/node/deadline caps, traversal/injection rejection, backend loss, cancellation/cleanup. Reader transport acceptance is required before enabling root discovery. |
| C. CPU/GPU reuse and facade projection | **EXISTING** `CpuHardwareBackend.kt`, `GpuHardwareBackend.kt`, `HardwareCapabilityResolver.kt`; **NEW** `APP/core/atlas/AtlasBackendProvider.kt` | Existing `TEST/core/hardware/GpuControlModelTest.kt`, `CpuProvenRangeTest.kt`; **NEW** `TEST/core/atlas/AtlasBackendProviderTest.kt`. No writer/policy changes; read-only projection cannot create READ_WRITE or new control keys. |
| D. Other domain observations and support matrix | **NEW** `APP/core/atlas/AtlasPlatformProvider.kt`; **NEW-in-A** `AtlasCatalog.kt` only sequentially after A | **NEW** `TEST/core/atlas/AtlasPlatformProviderTest.kt`: thermal identity/units, charge vs energy, PSI malformed/missing, platform API unavailable, ZRAM multi-device, deferred domains. Do not edit existing feature VMs to reuse loadState. |
| E. Ordered engine, cache and DI | **NEW** `APP/core/atlas/AtlasResolver.kt`, `AtlasEvidenceStore.kt`, `AtlasRepository.kt`; **EXISTING** `APP/core/di/DataModule.kt` | **NEW** `TEST/core/atlas/AtlasResolverTest.kt`, `AtlasEvidenceStoreTest.kt`: per-feature ordering, partial results, bounds, cache identities, concurrent retry/cancel, invalidation and corrupt file. State transitions separate completion/exhaustion/cancel. |
| F. Privacy and export | **NEW** `APP/core/diagnostics/AtlasSupportReport.kt`, `AtlasReportExporter.kt`; **EXISTING** `APP/core/diagnostics/DiagnosticCenter.kt` | **NEW** `TEST/core/diagnostics/AtlasSupportReportTest.kt`, `AtlasReportExporterTest.kt`; existing `DiagnosticCenterTest.kt`. Preview bytes == exported bytes; forbidden personal/log fields absent; partial/cancelled preview not shared automatically. |
| G. UI integration | **NEW** `APP/ui/viewmodel/AtlasViewModel.kt`, `APP/ui/mainscreens/AtlasDiagnosticsSection.kt`; **EXISTING** `SettingsScreen.kt`, `DiagnosticsScreen.kt`, both `RES/values{,-ar}/max_screen_strings.xml` | **NEW** `TEST/ui/mainscreens/AtlasPresentationTest.kt`; existing `ViewModelInstantiationTest`, `SettingsDestinationReachabilityTest`. No new destination, no shell in UI, no false progress, paired copy; device acceptance still required. |
| H. No-root entry acceptance, isolated from core discovery | Conditional **EXISTING** `APP/ui/mainscreens/GetStartedScreen.kt`, `APP/MainActivity.kt`, paired existing screen resources | **NEW** `TEST/ui/navigation/AtlasReadOnlyEntryTest.kt`; prove reachable Settings/Diagnostics without root/module. Do not silently broaden this into a Max AI/startup/safety rewrite. If no-prompt whole-app startup is required, the pre-existing Application/engine/RootService paths need a separately explicit lifecycle scope and safety review before claiming it. |
| I. Fixtures, compatibility handoff and closure | No product writer changes; reviewed catalog updates only if independently justified | **NEW** `manager/app/src/test/resources/atlas/{unknown-no-root,qualcomm,mediatek,exynos-tensor,denied,malformed,symlink-alias}.json` and **NEW** `TEST/core/atlas/AtlasArchitectureTest.kt`. Convert sanitized report to reviewed fixture; reject unknown schema/oversize/path injection; no runtime downloaded code. Log in existing HANDOFF by coordinator, not a new competing log. |

For C/D, existing default backend reads can remain as shipped while Atlas passes a bounded reader. This is a concrete compatibility need: current product callers and transaction semantics must not change. Do not use global mutable IO replacement; it would race existing AI and manual controls. Pass IO as a parameter or construct a reader adapter per scan.

**No product changes allowed by these default slices:** `APP/core/maxai/**`, `HardwareControlArbiter.kt`, `HardwareControlKey.kt`, `ManualControlLocks.kt`, `SharedHardwareOwnershipStore.kt`, native code, boot/module scripts, SELinux, signing, `AndroidManifest.xml`, `file_paths.xml`, `manager/app/build.gradle.kts`, or `.github/workflows/**`. These are read references, not implicit edit permissions. Additional public platform probes or a test dependency require an explicitly revised allowlist. Keep `txt.txt` intact.

## Shared Test Patterns

1. **Real parser, fake IO:** `TEST/core/hardware/GpuControlModelTest.kt:11-57` implements IO over maps, records all writes, and supplies directory names. Existing cases `:117-155` exercise mixed units, generic identity, ambiguity and unreadable baseline. Atlas tests must invoke real resolver/provider parsing over fixtures and assert zero writes, not mock away the resolver under test.
2. **Clock injection:** `TEST/core/hardware/MemoryPressureReaderTest.kt:23-76` tests first probe at zero, exact retry boundary and backward time. Extend the idea to privilege/catalog/kernel/build changes, future timestamps, monotonic run time, restart, and cancellation generation. Do not adopt zero as "not sampled".
3. **Deterministic concurrency:** `TEST/core/maxai/CoalescingCycleRunnerTest.kt:23-38,68-79` uses `runBlocking`, `CompletableDeferred`, `CoroutineStart.UNDISPATCHED`, and `cancelAndJoin`; no new coroutine test dependency is needed for these patterns. Inject clocks for deadlines instead of sleep-based timing assertions.
4. **No hidden shell in tests:** `TEST/core/diagnostics/DiagnosticCenterTest.kt:17-31` replaces/restores `forwarder`. An Atlas test that logs a denied probe as ERROR must not accidentally start libsu on the host. Prefer pure outcome assertions and structured diagnostic projection.
5. **Architecture guards:** `TEST/core/hardware/ControlPlaneArchitectureTest.kt:48-106,159-172` guards writers, singleton arbiter and canonical keys. New Atlas guard must fail if source root is missing, as `ViewModelInstantiationTest.kt:87-95` does; do not silently skip a no-write check. Guard imports/calls to writer APIs as well as shell text, and combine source scans with runtime fake-IO zero-write assertions.
6. **Serialization fixtures:** `ProfileSharing` / `TEST/ui/util/ProfileSharingTest.kt` are closest versioned-JSON analogs. Unknown schema, duplicated IDs, invalid numeric types, NaN/overflow, over-limit byte streams and poisoned path text must be rejected rather than coerced into plausible values.

## Existing Commands

These exact paths/task names come from current sources/build configuration. Commands below are proposed implementation verification commands, **not runs performed in this mapping task**. Run Gradle from the `manager/` working directory, with JDK 17 and an existing Android SDK; no installation or signing request is implied.

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME="$HOME/android-sdk" ANDROID_SDK_ROOT="$HOME/android-sdk" bash gradlew :app:testDebugUnitTest --tests 'nd.max.core.hardware.GpuControlModelTest' --tests 'nd.max.core.hardware.CpuProvenRangeTest' --tests 'nd.max.core.hardware.MemoryPressureReaderTest' --tests 'nd.max.core.diagnostics.DiagnosticCenterTest' -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8" --build-cache

JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME="$HOME/android-sdk" ANDROID_SDK_ROOT="$HOME/android-sdk" bash gradlew :app:testDebugUnitTest --tests 'nd.max.core.hardware.ControlPlaneArchitectureTest' --tests 'nd.max.core.hardware.HardwareControlArbiterTest' --tests 'nd.max.core.hardware.ManualControlLocksTest' --tests 'nd.max.ui.viewmodel.ViewModelInstantiationTest' --tests 'nd.max.ui.navigation.SettingsDestinationReachabilityTest' -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8" --build-cache

JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME="$HOME/android-sdk" ANDROID_SDK_ROOT="$HOME/android-sdk" bash gradlew :app:testReleaseUnitTest :app:assembleDebug -x :app:lintVitalRelease -Dorg.gradle.jvmargs="-Xmx6g -XX:MaxMetaspaceSize=1g" --build-cache --parallel
```

New Atlas tests should live in **`src/test/java`**, not `src/testDebug`. After they exist, `:app:testDebugUnitTest --tests 'nd.max.core.atlas.*'` selects the proposed core suite. Reports normally appear under `manager/app/build/test-results/testDebugUnitTest/` or `testReleaseUnitTest/`; build directories are gitignored and not reliably discoverable by Glob. The only debug-source file found was `src/debug/java/nd/max/ui/component/StudioPreviews.kt`. No existing `src/androidTest` or `src/testDebug` files were found; do not claim Compose instrumentation coverage or introduce an instrumentation command without setting up its runner/dependencies explicitly.

Read-only source checks that informed this map are reproducible with repository Grep/Glob: search `HardwareCapabilityResolver` callers, `MemoryPressureReader|MemoryStall` in main sources, `getThermalHeadroom|PerformanceHint|ADPF`, `MaxDestination.Diagnostics` in Settings, and `AppSession`. Empty search results establish only the searched implementation scope, not absence of a platform capability.

## No Adequate Analog

| Concern | Why Existing Code Is Insufficient | Required Planning Outcome |
| --- | --- | --- |
| Error-preserving bounded privileged reads | RootFileAccess and root AIDL return null/false/empty and read whole files; dedicated log shell manages lifetime but is a stream, not a per-operation budgeted reader | Explicit transport lifecycle, byte/node limits, abort behavior and honest fallback. If a backend cannot meet these, mark it unavailable rather than pretending a timeout cancels it. |
| Sysfs canonical topology discovery | Existing name listing and CPU alias grouping do not verify symlink targets/loop limits/canonical identity | Fixed anchor roots, validated basenames, bounded symlink resolution, canonical `/sys/devices/...` target policy and identity checks. Lexically rejecting all class symlinks would reject normal sysfs. |
| Persistent versioned evidence invalidation | MemoryPressureReader has one negative TTL; FileStore has plain overwrite with swallowed failures | Independent bounded schema, atomic commit, corruption handling, privilege/build/catalog invalidation, no stale-control promotion. |
| Privacy-approved support preview | Existing archives copy broad logs/config; hardware report copies immediately | Explicit allowlisted DTO and immutable preview/share state; no raw-text formatter appended as a shortcut. |
| App-wide no-root/no-prompt session contract | MainActivity local state, PrivilegeManager and RootService startup are separate | Narrow user journey test and explicit residual/startup scope; no fictional AppSession API. |

## Verification And Handoff

Commands **actually run** from repository root in ATLAS-M1:

| Command | Observed Result |
| --- | --- |
| `git status --short` | Existing coordinator planning/doc changes plus owner `txt.txt`; no attempt to undo them. |
| `python3 tools/code_health.py --assert` | Failed only `stray_root_file: 1`, `txt.txt`; package/resource/duplicate checks zero. Debt **10 / 29 / 66 / 26** unchanged. 471 Kotlin files, 108868 lines reported by this tool. |
| `python3 tools/i18n_coverage.py --assert` | Passed; 84 target locales + English, picker 85, locale config 85, zero blocking specifier/duplicate/code mismatches. |
| `python3 tools/repo_audit.py` | `PROBLEMS: 0`; 345 Kotlin files, 2544 `R.string` references, 3209 base strings. |
| `git diff --check` | No whitespace errors at the time run. |
| `git diff --no-index --check /dev/null .planning/phases/01-max-atlas-compatibility-and-safe-discovery/01-PATTERNS.md` | No whitespace errors in the new untracked artifact. |

All three repository gates were repeated after writing the map and returned the same baseline results.

The pre-existing `txt.txt` failure must remain visible; do not delete the input, change ignore rules, weaken the health gate or claim overall green. No tests/builds were run: compilation and hardware behavior are unverified in this investigation. Static path/call-chain findings are not device evidence. The later phase needs independent safety review for hardware seams and device validation for unknown/no-root, Qualcomm, MediaTek and Exynos/Tensor cases; simulated fixtures prove parser/ordering behavior only.

External repository behavior, licenses and live-source provenance belong to the other researcher's `01-RESEARCH.md`; this map intentionally makes no new external-source claims. The coordinator should integrate the critical gaps and plan boundaries above, then record delivery in the existing `docs/ai/HANDOFF.md`, which this mapper was not authorized to edit.
