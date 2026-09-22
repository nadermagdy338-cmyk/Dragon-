# MaxManager Per-App Thermal Reliability Fix

Baseline: `fix.zip` supplied by the user.

## What the supplied runtime log showed

The current runtime repeatedly emitted `PERAPP_THERMAL_CURVE` and delayed the final
thermal knob event by many seconds. Across the sampled switch IDs in the 2026-09-22
log:

- median time from APP_SWITCH -> thermal knob event: about 12.23 s
- worst sampled delay: about 19.64 s
- median APP_SWITCH -> first per-app commit: about 8.23 s
- 749 package-label mismatches were observed between the package in APP_SWITCH and
  later PERAPP_KNOB/PERAPP_COMMIT records under the same switch ID.

Example:
`APP_SWITCH pkg=com.google.android.apps.translate sw=sw-...`
was followed by per-app knob records tagged as `com.franco.kernel` or `nd.max`.

The supplied `fix.zip` does not contain `PERAPP_THERMAL_CURVE` at all. Its thermal
architecture has a single native owner for Xiaomi
`/sys/devices/virtual/thermal/thermal_message/sconfig`, and the legacy
`xiaomi-extras.sh` writer is not launched.

## Changes in this bundle

1. `AppMonitor.kt`
   - Restored from `fix.zip` (no Java/UI `sconfig` writer and no
     `PERAPP_THERMAL_CURVE` path).
   - The foreground package/switch ID is now atomically published to `app_status`
     before the slow per-app hardware transaction begins. This lets the native
     thermal state machine react to the new foreground target immediately.

2. `MtkUtils.kt`
   - Corrected the Kotlin raw-string regex used to parse `[index] frequency`.
   - Added a 60-second cache for the static MTK OPP table so repeated per-app
     capability scans do not reread the same kernel table.

3. `PerAppKernelUtil.kt`
   - Added a 10-second cache for GPU capability detection.
   - This reduces repeated root I/O during profile application/drift checks.

4. `ProfilePresetStore.kt`
   - Kept the `fix.zip` preset model: Power 65%, Balanced 70%, Gaming 85%,
     Performance 100% by default. The selected percentage is converted to the
     nearest real supported OPP.

5. Native thermal/service files
   - Restored the `fix.zip` single-owner thermal architecture:
     `PerAppThermal.c`, `System.c`, `ProfileUtility.c`, `StatusMonitor.c`,
     `AZenith.h`, and `mainfiles/service.sh`.
   - The service script does not launch the old Xiaomi extras thermal writer.

## Important integration rule

Do not keep a newer Java/Kotlin path that writes
`/sys/devices/virtual/thermal/thermal_message/sconfig` or emits
`PERAPP_THERMAL_CURVE`. Those are a second writer/state machine and would defeat
the single-owner design restored here.

## What this does NOT claim

A full Gradle APK build was not executed in this environment because the wrapper
requires downloading Gradle 9.5.1 and network access was unavailable.
