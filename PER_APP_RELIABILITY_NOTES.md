# Per-app settings reliability — reference notes

## Why this file exists

Reported symptoms: several App Settings knobs appear to do nothing, the
thermal/GPU profile picker specifically doesn't seem to hold, and the Apps
list didn't look right either. To investigate, I read through **Rodin**
(a separate, unrelated root-tuning project the user pointed me
at as a reference) to see how a project solving the *same class of problem*
— per-app CPU/GPU/thermal overrides on Xiaomi/MediaTek hardware — is
architected, specifically to find out what MaxManager might be structurally
missing.

**Nothing in this file or in the resulting code changes is copied from that
project.** What follows is my own understanding of the *pattern* it uses,
written from scratch, plus the concrete MaxManager changes that pattern
justified. Where a specific kernel/vendor interface is named (e.g.
`thermal_message/sconfig`), that's documented Xiaomi kernel behavior — the
same node multiple independent community tools target for the same reason
— not anything proprietary to the reference project.

## The core gap: one-shot apply vs. owned, defended state

MaxManager's per-app pipeline (`AppMonitor.kt`'s `applyPerAppConfig()`) has
always worked the same way: when the foreground app changes, read that
app's config once, write the relevant sysfs nodes once, done. Nothing ever
checks back.

That's fine as long as MaxManager is the *only* thing touching those nodes.
It usually isn't. Two known always-running Android/vendor components
routinely rewrite the exact same values MaxManager just set:

- **Xiaomi's `mi_thermald`** periodically republishes CPU/GPU frequency
  ceilings based on its own encrypted thermal policy, completely
  independent of anything a third-party app does.
- **MediaTek's GED / thermal HAL** drives GPU frequency out-of-band on
  many chips — writing `governor`/`min_freq`/`max_freq` to the devfreq
  node is frequently accepted but silently ignored, because GED never
  reads those files in the first place (this is *already* handled
  correctly in `PerAppKernelUtil.kt` via the `gpufreqv2`/legacy OPP-index
  path — see its doc comment).

When one of these reasserts itself minutes (sometimes seconds) after
MaxManager's one-shot write, the result looks *exactly* like "I picked
Gaming/Performance and nothing happened" — except something did happen, it
just didn't stay. MaxManager had no way to notice, because it never looked
again.

This is very likely the single biggest contributor to "the thermal profile
doesn't work" and, more broadly, to the general sense that App Settings
knobs are unreliable — not because the writes fail, but because they don't
*hold*.

### What changed as a result

**`archdaemon/jni/src/SystemProfile/PerAppThermal.c`** (new) — moves Xiaomi
`thermal_message/sconfig` ownership into the privileged daemon:

- A custom per-app CPU/GPU override acquires temporary ownership of `sconfig`.
- The previous mode is saved before switching to Xiaomi's no-limits mode `6`.
- Ownership is reasserted if vendor userspace changes the mode while the
  override remains active.
- The original mode is restored when the override ends or when the daemon is
  terminated.
- A small marker file allows recovery if the daemon dies after changing the
  kernel node but before restoration.
- Legacy `thermal_profile` is still parsed for compatibility.

**`AppMonitor.kt`** no longer writes `sconfig` directly. It remains responsible
for the per-app GPU/CPU operations that already belong to the Java companion.
This removes the previous split ownership between Kotlin and the privileged
daemon.

**`mainfiles/service.sh`** no longer launches the old `xiaomi-extras.sh` loop.
That loop could overwrite the daemon's temporary no-limits mode every two
seconds with its own charging/gaming/default modes, which directly defeated
per-app thermal ownership.

**`archdaemon/jni/src/System/System.c`** now synchronizes thermal ownership with
the same foreground/app configuration state used by the native profile engine,
and releases it when the tracked app is no longer active or the screen is
effectively off.

## What I could *not* confirm from reading code alone

I read `AppSettingsScreen.kt`/`AppSettingsViewModel.kt`/`AppMonitor.kt`'s
per-app pipeline field by field (all ~20 `AppConfig` fields: which UI
control writes it, whether `updateSetting()`'s `when` covers it, whether
something downstream — either `AppMonitor.kt` or the native daemon's
`GameConfig` parsing in `AppLoader.c` — actually consumes it). Every field
*is* wired end-to-end somewhere; I did not find a knob that's silently
dead on the Kotlin/native side the way the thermal-drift issue was dead.

That doesn't rule out the "~80%" impression being accurate on a specific
device — it means the remaining failures are very likely **runtime**
failures (a governor MaxManager offers isn't actually accepted by this
kernel, a sysfs node this ROM exposes read-only, a SELinux denial specific
to this vendor's policy, etc.), not structural ones visible from the source
alone. Those need actual on-device data to pin down — which is exactly
what the Detailed Activity Log exists for: enable it, reproduce, and
`EVENT=*_FAILED`/`EVENT=APPLY_DRIFT` lines will name the specific knob and
package involved instead of a general "something in App Settings."

The Apps list screen (`ApplistScreen.kt`/`ApplistViewmodel.kt`) read as
structurally sound on inspection — install list loading, search/filter,
config-status badges, and navigation into App Settings all trace through
cleanly. If something there is still off, it needs a concrete repro (what
did you expect vs. what showed up) rather than another blind code read.

## Checklist for adding (or fixing) a per-app knob

Distilled from the gap above, for whoever — human or Claude — touches this
pipeline next:

1. **Does anything else on this device write the same node?** If a vendor
   daemon, thermal HAL, or another root tool can touch it, a one-shot write
   at app-switch time is not enough. Either take explicit ownership (like
   `VendorThermalUtil`) or add the knob to `reassertDriftedKnobs()`.
2. **Verify by reading back, don't assume `isSuccess` means it stuck.** A
   shell exit code of 0 means the write syscall didn't fail; it says
   nothing about whether some other component silently changed the value a
   moment later. `PerAppKernelUtil.applyGpuFixedFrequency()`'s MTK/devfreq
   split already does this the right way for GPU frequency — read back and
   compare, not just trust the write.
3. **Log the expected vs. live value, not just success/failure.** An
   `AppMonitorLogger`/`EventLog` line that only says "applied" can't
   distinguish "worked" from "worked for four seconds." Include both
   values so a look at `MaxManager.log` answers the question without
   needing a fresh repro.
4. **Group genuinely related writes, don't scatter them.** GPU
   bounds/governor/frequency and CPU governor are conceptually separate
   subsystems with separate failure modes — keep them as independent
   `runCatching` blocks (already the pattern in `applyPerAppConfig()`) so
   one failing knob never blocks the others.
