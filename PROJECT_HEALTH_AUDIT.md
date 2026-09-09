# Project health audit — findings and roadmap

A deeper sweep beyond the App Settings/thermal investigation, looking for
anything else broken or fragile across the whole repo (Kotlin app, native
daemon, and the three Rust binaries — `thermalcore`, `binprofiles`,
`binutils` — which hadn't been examined at all before this pass). Findings
below are split into what's fixed now, what's flagged for later, and a few
leads that looked promising but turned out to be false alarms once checked
properly — included for transparency, since a "did you check X" audit is
only useful if X was actually checked, not assumed.

## Fixed this pass

### 1. Two exported broadcast receivers had no permission gate (security)

`ZenithReceiver` (`nd.max.ACTION_MANAGE`) and `RefreshRateReceiver`
(`nd.max.SET_FPS`) were both `android:exported="true"` with no
`android:permission`. In practice that means **any other app installed on
the device, with zero declared permissions of its own, could broadcast
either action and have MaxManager act on it** — no root, no user
interaction, nothing beyond just sending an `Intent`.

Concrete impact:
- `RefreshRateReceiver` forces a real `SurfaceFlinger` refresh-rate change
  (`service call SurfaceFlinger 1035 ...` + persisted props) based on
  whatever `fps` extra the caller supplies. Any app could force this
  repeatedly — display glitches, forced-high-refresh battery drain, or
  just a persistent annoyance, all without the user ever opening
  MaxManager.
- `ZenithReceiver` can post notifications/toasts with attacker-controlled
  title/text that look like they came from MaxManager, and can dismiss all
  of MaxManager's own notifications on demand.

**Fix**: added a `signature`-level custom permission
(`nd.max.permission.MANAGE`) and required it on both receivers. This
doesn't change how MaxManager itself uses them — the native daemon reaches
`ZenithReceiver` exclusively through a root-shell `am broadcast` (root/
shell bypasses permission checks entirely, same before and after), and
`RefreshRateReceiver`'s only sender (`TweakViewmodel.kt`) calls
`context.sendBroadcast()` from inside the app itself, which is
auto-granted a signature permission at install time since it's signed with
the same key. It only closes the door on every *other* app.

## Flagged, not fixed yet (needs a deliberate follow-up, not a rushed one)

### 2. Most of the app's own errors are invisible — the biggest single gap

The logging work done earlier this project (`AppMonitorLogger.kt` for the
root companion daemon, `EventLog.kt` for user actions) only covers those
two specific paths. It turns out that's a small fraction of where
exceptions actually get caught in this codebase:

- **119** `catch (e: Exception) { ... }` blocks across **39** files in the
  main app process (ViewModels, Utils, Services) — everything *outside*
  `AppMonitor.kt`, which got the same treatment already.
- Of those 119, only **9** route through `AppMonitorLogger` or `EventLog`.
  The other **110** either do a bare `e.printStackTrace()` (logcat-only,
  gone the moment the process restarts, useless without `adb`) or swallow
  the exception entirely and just return a fallback value with zero trace
  at all.

This is the exact same failure mode that caused the original bug this
whole logging project started from (a `SecurityException` silently
aborting `applyPerAppConfig()`) — just in the UI process instead of the
companion daemon, and at roughly 13x the number of call sites. Any file
I/O failure, JSON parse error, or root-shell failure in a ViewModel today
produces no evidence anywhere a person could actually find it.

**Why not fixed now**: 110 call sites across 39 files is a large,
heterogeneous sweep — the kind of change that needs to go file-by-file
with real understanding of what each catch is actually guarding, the same
way the AppMonitor.kt hardening was done in reviewable batches rather than
one blind pass. Rushing it risks exactly the kind of mistake (miscounted
braces, wrong log level, a catch that actually needs to stay silent for a
good reason) that a careful pass avoids.

**Suggested approach for whoever picks this up**: extend `EventLog.kt`
with an `error(screen, message, throwable)` function (mirrors
`AppMonitorLogger`'s pattern exactly, same `DETAILED_LOG` gate), then go
ViewModel-by-ViewModel the way `AppMonitor.kt`'s batches did — highest
first: `AppSettingsViewmodel.kt`, `TweakViewmodel.kt`, `ApplistViewmodel.kt`
(most user-facing surface area, matches what's already been debugged this
project).

### 3. Naming drift between the shell/Kotlin/Rust copies of prop keys

`props.sh`, `MaxManagerProps.kt`, and `binprofiles/src/props.rs` each keep
their *own* copy of every `persist.sys.maxmanager*` key string (documented
in `props.sh`'s own header comment as a known, accepted tradeoff — "keep
them in sync if a key is ever renamed"). That header comment currently
also claims `binutils/src/utils/mod.rs` shares these strings, which is
accurate, but is easy to misread as this being a 2-language problem when
it's actually 3 independent copies (shell, Kotlin, Rust) that all have to
be renamed together by hand. Not a bug today — everything currently in use
is in sync — but it's exactly the kind of duplication that produces a
silent dead property the next time someone renames one without grepping
all three places. Worth a comment update pointing at all three, and worth
keeping in mind before renaming any key.

## Leads that turned out to be false alarms

Worth recording so the same ground doesn't get re-covered blindly:

- **"~50 unused MaxManagerProps constants"** — an automated first pass
  found ~50 prop constants with no reference anywhere outside
  `MaxManagerProps.kt`. False alarm: Kotlin code correctly references them
  via `MaxManagerProps.Conf.X`, not by duplicating the literal string, so
  a plain string search undercounts real usage. Re-checked by matching the
  actual `MaxManagerProps.X.Y` reference pattern instead — zero truly dead
  props once counted correctly.
- **"SOC_TYPE / default governor props are read but never written"** — a
  second pass, cross-referencing read vs. write call sites, flagged
  `SOC_TYPE`, `CPU_DEFAULT`, `IO_DEFAULT`, `MALIGPU_DEFAULT`, and
  `PRELOAD_BUDGET` as apparently write-only-missing. False alarm on two
  counts: the actual writes happen in `binprofiles` (Rust) and
  `customize.sh` (via a `$PROP_SOC_TYPE` shell variable, not the literal
  string), neither of which the first two passes searched. Once Rust
  source and shell variable indirection were included, every one of these
  turned out to be set correctly (`customize.sh`'s per-chipset `setprop`
  block for `SOC_TYPE`; `binprofiles/src/utils/mod.rs`'s `setprop_cmd()`
  calls for the governor defaults).
- **"MaxManager's own `thermalcore` daemon might fight the per-app GPU
  frequency lock the same way `mi_thermald` does"** — checked directly:
  `thermalcore` only writes `/sys/class/thermal/.../cooling_device*/
  cur_state` (the generic Linux cooling-device throttle interface), never
  `scaling_max_freq`/GPU `max_freq`/`scaling_governor`. Different kernel
  interface than the per-app knobs touch, so no direct conflict. (Real
  thermal throttling under sustained heavy load can still legitimately
  reduce performance even with a custom profile active and correctly
  applied — that's the cooling device doing its job, not the per-app knob
  failing. Worth knowing if this comes up again as a "profile doesn't
  work" report.)

## Future outlook

Roughly in priority order:

1. **Close the observability gap (finding #2).** This is the highest-
   leverage remaining item — it's the same class of bug that's already
   caused one confirmed real incident (the original zen_mode crash) and
   is structurally guaranteed to be hiding others, silently, right now.
   Every other investigation in this project (the thermal drift fix, this
   audit) has been slower and more uncertain than it needed to be because
   most of the codebase still can't tell you when something goes wrong.
2. **Extend `reassertDriftedKnobs()` beyond GPU profile/CPU governor.**
   The drift-guard pattern built for the thermal fix generalizes cleanly
   to any per-app knob another component could plausibly revert (refresh
   rate is the next most likely candidate — vendor display HALs are known
   to reset it on some OEMs).
3. **Once #1 has real data flowing, revisit the "~80% of App Settings"
   report with actual `MaxManager.log` output** rather than static code
   reading. Every field traced structurally sound this session; anything
   still failing is very likely a runtime/device-specific failure (a
   governor this kernel rejects, a node this ROM made read-only, an
   SELinux denial specific to one vendor's policy) that genuinely needs a
   log line naming the exact knob and package, not another blind
   read-through.
4. **Decide what to do about the strcpy() pattern in the native `GameConfig`
   copy paths** (`ProfileUtility.c` and friends). Not an active bug today
   — every copy is between same-sized buffers of the same struct type —
   but it's a fragile pattern that only stays safe as long as every field
   size change gets mirrored everywhere by hand. Worth a pass to
   `strncpy`/bounds-checked copies whenever that code is next touched for
   an unrelated reason, not urgent enough to justify a standalone change.
