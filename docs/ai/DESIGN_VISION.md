# DESIGN_VISION

The product direction MaxManager should be rebuilt toward. This supersedes earlier UI plans (`docs/aegis/*`, `manager/FINAL_UI_AUDIT.md`) as *product direction*; it keeps and extends the `ui/design/` language.

## 1. Positioning

> **MaxManager is a control plane for Android performance that can prove what it is doing.**

Not a tweak launcher. Not a skin. Three promises, in priority order:

1. **Truth** — every number states how fresh it is and where it came from; nothing is invented, nothing stale is shown as live.
2. **Safe control** — every write is a transaction: owner, captured baseline, verified read-back, rollback on failure, and a stated reason when a control is locked.
3. **Auditable intelligence** — Max AI acts autonomously, but always explains what it changed, why, what it measured, and hands control back the moment the user touches a knob.

Design rule derived from this: **if a pixel does not carry truth, control, or explanation, it is decoration and must justify its cost.**

## 2. What is wrong today (one line each)

- Two navigation models for the same four screens (D-01).
- The best engineering in the repo is hidden behind a non-primary route (D-02).
- 40+ flat routes for ~9 device domains, with duplicate and dead routes (D-03).
- Tweaks is a menu of screen names, not a view of the device (D-04).
- Two design systems; the good one covers 13% of screens (D-05).
- Decoration competes with the product's own claim of efficiency (D-07).

## 3. New information architecture

Four primary destinations. Settings leaves the bottom bar; Max AI enters it.

| # | Destination | Answers | Absorbs |
| --- | --- | --- | --- |
| 1 | **Now** | “What is my device doing right now, and what changed?” | HomeScreen, dashboard details, recommendations, reboot/profile actions, activity journal |
| 2 | **Control** | “What can I change, what is applied, what is risky?” | Tweaks + all 24 feature subscreens, grouped into 8 domain hubs |
| 3 | **Apps** | “How does this specific app behave?” | Applist, per-app settings, process manager, debloat/freeze |
| 4 | **Max AI** | “Who is controlling my hardware, and why?” | MaxAiScreen + ownership map + decision journal + safety + learning |

Settings becomes a top-bar entry from **Now** (avatar/gear), containing: Appearance (theme, color scheme, wallpaper, ambient), App behaviour, Module & updates, Advanced tools, Diagnostics, Logs, About.

### Control → 8 domain hubs (replaces 19 flat rows)

| Hub | Merges today's | Hub summary line shows |
| --- | --- | --- |
| **CPU** | `cpucorecontrol`, `governorsettings`, MTK CPU/Freq/PPM | cluster count, governor, live max freq, manual session state |
| **GPU** | `gpustudio` (+ drop `maligpufreq`/`adrenogpufreq`), MTK DRAM/Boost | driver, freq ladder, applied tweaks |
| **Memory** | `zrammanager`, swappiness/VM parts of `preferenced` | ZRAM size/algorithm, pressure |
| **Display** | `displaystudio`, `resolutionscreen`, refresh-rate controls | resolution, refresh rate, DPI |
| **Responsiveness** | `touchboost`, `fpsgoscreen`, `FasScreen`, `fpsoverlay` | touch provider, frame scheduler, overlay on/off |
| **Thermal** | `thermal_detail`, thermalcore controls, safety limits | hottest zone, throttle state |
| **Power** | `chargingscreen`, `bypasschg`, `bypasschg_check`, `dozemode` | charge state, bypass capability, doze policy |
| **Storage & compiler** | `dex2oat` (App Compiler), `storage_detail`, `debloatfreeze` storage side | free space, compiler filter |
| *(plus)* **Network** | `networkscheduler`, `network_detail` | scheduler, live throughput |

Each hub is one `MaxScreen` whose sections are today's screen bodies, reused not rewritten. A feature only keeps a dedicated screen when it is a deep workspace (CPU core grid, GPU ladder, per-app editor, kernel flasher).

### Deletions / demotions

- Delete the pager navigation model and the `use_scroll_animation` flag (D-01).
- Delete route aliases `maligpufreq`, `adrenogpufreq`.
- Demote decorative engines (`WeatherEffects`, `VideoWallpaperPlayer`, `PulseFieldEngine`, `AmbientMotifOverlay`) behind one **Appearance → Ambient effects** switch, default **off**, with an honest cost note. Delete whatever no screen references after the migration.
- Retire `ui/components/` (merge its two files into `ui/component/`), then shrink `ui/component/` as screens migrate to `ui/design/`.
- `terminal`, `setedit`, `activitylauncher`, `kernelflasher` move under **Settings → Advanced tools** with an explicit risk gate, not the main IA.

### New surfaces worth building

1. **Command palette** (search icon in every top bar): fuzzy search over controls, apps, sysfs knobs and settings, returning direct actions. Solves discoverability of 40+ features without deep menus — the single highest-leverage new UI element.
2. **Ownership strip** (`MaxOwnerChip`): on every control row/hub — `You` / `Max AI` / `Per-app` / `Module` / `Kernel default`, tap to release or lock. This is the UI that finally exposes the arbiter + `ManualControlLocks`.
3. **Change journal** (in Now, and filtered per hub): timestamped stream of "who changed what, verified or rolled back", from `EventLog` + arbiter outcomes. Turns Max AI from a black box into a ledger.
4. **Device truth sheet** (Diagnostics rebuilt): per-knob capability proof — readable / writable / ladder-proved / unsupported, straight from `HardwareCapabilityResolver`. Replaces feature-level "supported?" guessing.
5. **Session bar**: when a manual session or an AI action is active, a persistent one-line bar with "applied / verifying / rolled back" and an undo affordance.

## 4. Screen anatomy (one shape, every screen)

```
top bar: title · subtitle(provider/path) · [search] [refresh] [actions]
[session bar]            ← only while a write/AI action is in flight
[condition]  OR  [banner + sections]
section: title + why-line
  group (hairline): rows (48dp min) — control rows | metric lines | destinations
```

Rules (already encoded in `ui/design/`, to be enforced repo-wide):
- Screens never build their own `Scaffold`, gutters, or section spacing.
- Capability and state are never expressed by the same visual (the TouchBoost bug class).
- A disabled control always states its reason (`lockedReason`).
- Every metric carries `MaxDataTrust` + source.
- Colour is never the only carrier of meaning (tone + icon + label).
- Elevation only for floating surfaces; grouping is borders + whitespace.
- Motion budget ≤ `MaxDuration.deliberate` (360ms) and direction-neutral (RTL-safe).

## 5. Design-language additions needed

Add to `ui/design/` (new files, no rewrites of existing ones):

| Component | Purpose |
| --- | --- |
| `MaxHubScreen` / `MaxDomainCard` | domain hub shell + summary row with live state |
| `MaxOwnerChip` | knob ownership + release/lock action |
| `MaxJournalRow` | one audit entry: actor, knob, before→after, verdict |
| `MaxSessionBar` | in-flight write/AI session with undo |
| `MaxRiskDialog` | destructive/irreversible confirm with named consequence |
| `MaxCommandPalette` | global search & jump |
| `MaxNumeric` | tabular-figure text style so live numbers stop jittering |
| `MaxSparkline` | the already-tokenized 28dp history strip, extracted |

## 6. Non-negotiables for every task from here

1. No behaviour regression in `core/maxai` / `core/hardware`; UI adapts to the control plane, never bypasses it (no direct `RootFileAccess.write` from UI).
2. Every new user-visible string goes into `values/` **and** `values-ar/` in the same change.
3. Every screen touched must leave `ui/design/` adoption strictly better, never worse.
4. Static verification is mandatory (see `VALIDATION.md`); compilation is environmentally unverified and must be reported as such.
5. Nothing is deleted without checking references first; dead code is removed in the same task that orphans it.

## 7. Rollout order (why this order)

1. **NT-01 Navigation spine + Control hubs** — unlocks every later task and removes the dual-nav tax. *(next task)*
2. NT-02 Max AI as a primary destination: ownership map + journal + session bar.
3. NT-03 Migrate hub bodies to `ui/design/` domain by domain (Power, Display, CPU, GPU…), deleting legacy components as they are orphaned.
4. NT-04 Apps destination: per-app profile as one workspace.
5. NT-05 Settings & Appearance consolidation + ambient-effects gate + advanced-tools risk gate.
6. NT-06 Command palette + device truth sheet.
7. NT-07 Localization parity sweep + adoption architecture test made blocking.
