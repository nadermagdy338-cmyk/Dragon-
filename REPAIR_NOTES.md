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
     `MaxManager.h`, and `mainfiles/service.sh`.
   - The service script does not launch the old Xiaomi extras thermal writer.

## Important integration rule

Do not keep a newer Java/Kotlin path that writes
`/sys/devices/virtual/thermal/thermal_message/sconfig` or emits
`PERAPP_THERMAL_CURVE`. Those are a second writer/state machine and would defeat
the single-owner design restored here.

## What this does NOT claim

A full Gradle APK build was not executed in this environment because the wrapper
requires downloading Gradle 9.5.1 and network access was unavailable.

---

## Appendix — the same family of defect, measured on a TECNO LH8n (تكملة ٢٢٣)

Added 2026-10-01. Nothing above is rewritten; this only records how the
package-identity failure above was closed, because it is the same symptom
wearing a different colour.

**The log above and this one are one bug.** There, `APP_SWITCH pkg=<A>` was
followed by per-app knob records tagged `<B>` — the switch said one app and the
work was done for another. Here, the foreground field carried a decimal instead
of any app at all, so no work was done for anyone:

```
app_status            focused_app 0.85 0 0        app_name 0.85        perapp_active 0
sysmon.log  170       EVENT=APP_SWITCH pkg=0.85 prev= sw=sw-1790786527785
sysmon.log  171       EVENT=PERAPP_GRACE_ARMED pkg=0.85 reason=foreground-process-missing
sysmon.log  172       EVENT=PERAPP_DEFERRED_REVERT pkg=0.85 reason=app_died
runtime/per_app_hw_status   pkg=               at=0
API/gameinfo                NULL 0 0
```

**Why it produced no error.** `AppMonitor` accepted the first token containing a
dot as a package name, so the float `0.85` passed. The native daemon compares the
field literally (`get_gamestart` -> `strcmp` against the applist keys,
`archdaemon/jni/src/SystemProfile/ProfileUtility.c:93`), so it matched nothing and
stayed silent. Nothing was refused; it was never asked.

**Why the second complaint ("I set it and it reverts") has the same root.**
`reassertDriftedKnobs()` re-applies a diverged knob every 10 s -- but it returns
early while `lastAppliedPkg` is blank. The registry had no knob registered,
because no app was ever applied, so the drift guard was running with nothing to
guard. The vendor was not beating us; our safety net was attached to nothing.

**What changed (4 files, no new dependency, no new permission):**

| File | State | What it holds |
| --- | --- | --- |
| `core/platform/ForegroundAppResolver.kt` | new, 117 lines | package-name shape (letter starts each label, two labels minimum), extraction from a noisy string, `dumpsys` marker parsing |
| `src/test/.../ForegroundAppResolverTest.kt` | new, 218 lines | 16 tests, `OK (16 tests)` on the JVM |
| `AppMonitor.kt` | 2920 -> 2995 | two ordered foreground sources (reflection, then `dumpsys`), a search that no longer stops at a non-package result, and an ownership condition on the grace revert |
| `core/hardware/RootFileAccess.kt` | 282 -> 293 | `readCommand(command)` -- read-only, returns the text even when the exit code is non-zero |

**Two further defects caught before delivery, not by a log but by reading the
shape Android itself prints:**

1. `Intent.toString()` writes the action before the component, and
   `android.intent.action.MAIN` satisfies the package-name shape -- so the action
   would have become "the managed app", the same defect in another colour. The
   rule now prefers the `pkg/` component form and skips Android's constant
   namespaces (`android.intent.`, `android.permission.`) when there is no
   component.
2. `PERAPP_DEFERRED_REVERT pkg=0.85` ran a full restore of the global profile for
   a package that owned nothing: arming the grace timer was not gated on
   ownership, while the revert at switch time was. Arming now requires
   `hasPerAppOwnership(pkg)`.

**Still open, and it needs the device:**

- `dmesg` shows 133 `update cpufreq limit idx min 15---max 0,freq min ...` lines
  and 4 `iofi: Watchdog caught vendor rollback ('0-7')! Re-enforcing.` -- but the
  values match the hardware limits the device advertises, so this is not proof
  that a write of ours was seized. Whether `performance` holds longer than 10 s on
  this unit is a device measurement.
- The Mali node reports the default governor `dummy` and the bundle carries no
  `available_governors` for it. If only `dummy` is advertised then requesting
  `performance` is correctly refused, and the existing `governor-not-advertised`
  diagnostic code is the intended answer. Read
  `/sys/class/devfreq/13000000.mali/available_governors` on the device.
- `AppMonitor.kt` cannot be compiled in this environment (Android + libsu +
  HiddenApiBypass) -- compilation unverified; only the pure rule is measured.
