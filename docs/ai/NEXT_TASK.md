# NEXT_TASK

**Task ID:** NT-01 — Navigation spine + Control domain hubs
**Executor:** DeepSeek Harness
**Type:** UI architecture refactor (no feature logic, no `core/` changes)
**Read first:** `HANDOFF.md`, `DECISIONS.md` (ADR-01–06, ADR-16, ADR-18), `VALIDATION.md` §1–6

## Why this task first

Every later redesign step (Max AI exposure, per-domain migration, settings consolidation) has to register screens, own back-stack behaviour, and place surfaces. Today that is impossible cleanly because navigation is written twice (bottom-bar routes vs pager) with 40+ string literals inline in an 817-line `MainActivity`. Fixing the spine unlocks everything else and immediately deletes a whole class of bugs.

## Goal (one sentence)

One navigation model, one typed destination registry, four primary destinations (**Now / Control / Apps / Max AI**), Settings reached from the Now top bar, and the 19 flat Tweaks rows replaced by 9 domain hubs — **with every existing screen still reachable and unmodified internally**.

## Scope: files

**Create**

| File | Contents |
| --- | --- |
| `nd/max/ui/navigation/MaxDestinations.kt` | sealed `MaxDestination` model: `route`, `titleRes`, `icon`, `parent`, `risk: MaxRisk { Normal, Advanced, Dangerous }`, `isPrimary`. Declares all routes as objects; **the only place route strings exist**. Include `PrimaryDestinations = listOf(Now, Control, Apps, MaxAi)`. |
| `nd/max/ui/navigation/MaxNavGraph.kt` | `NavGraphBuilder.maxNavGraph(...)` registering every destination by referencing `MaxDestination.route` (never a literal). Holds the transitions and the deep-link handling moved out of `MainActivity`. |
| `nd/max/ui/navigation/MaxNavActions.kt` | typed helpers: `navigateTo(dest)`, `navigateToPrimary(dest)` (single-top + `popUpTo` root, `saveState`), `openApp(pkg)`. Screens receive these, not `NavController`. |
| `nd/max/ui/mainscreens/ControlScreen.kt` | new Control destination: `MaxListScreen` listing the 9 domain hubs via `MaxDomainCard` rows (title, subtitle = live summary or `status_unknown` with correct `MaxDataTrust`, chevron). Replaces `TweakScreen` as the tab. |
| `nd/max/ui/subscreens/hubs/<Domain>HubScreen.kt` ×9 | `CpuHubScreen`, `GpuHubScreen`, `MemoryHubScreen`, `DisplayHubScreen`, `ResponsivenessHubScreen`, `ThermalHubScreen`, `PowerHubScreen`, `StorageHubScreen`, `NetworkHubScreen`. Each is a `MaxScreen` with `MaxSection` + `MaxGroup` + `MaxRow` entries pointing to the existing feature screens of that domain (mapping table below). **Do not move feature bodies in this task.** |
| `nd/max/ui/design/MaxDomainCard.kt` | hub summary row component (title, supporting line, trailing state + trust badge, 48dp min, `Role.Button`). Tokens only from `MaxTokens`. |
| `manager/app/src/main/res/values/max_navigation_strings.xml` + `values-ar/max_navigation_strings.xml` | all new copy: 4 primary labels, 9 hub titles + one-line descriptions, “Advanced tools”, risk-gate copy. Both files in the same commit (ADR-14). |

**Modify**

| File | Change |
| --- | --- |
| `MainActivity.kt` | Shrink to: theme + providers + root/module session + `Scaffold` with bottom bar / nav rail + `NavHost { maxNavGraph(...) }` + existing update/reboot dialogs. **Delete** the `HorizontalPager` branch, the `use_scroll_animation` read, the duplicated `currentRoute` derivations, the inline `composable(...)` list, and the local `ExpressiveShapes` (use the theme's shapes). Target under ~350 lines. |
| `SettingsScreen.kt` | Add sections for the destinations it must now host: Appearance (`colorscheme`, `color_palette`), Module & updates, Diagnostics, Logs, **Advanced tools** (terminal, setedit, activitylauncher, kernelflasher) each marked with its `MaxRisk`. Structure only — no visual rewrite of existing rows. |
| `HomeScreen.kt` | Rename destination to **Now**; add a top-bar entry to Settings and a top-bar search placeholder (no palette yet); keep dashboard content as is. |
| `MaxAiScreen.kt` | Registration only: becomes a primary destination with its own bottom-bar entry. Its internal redesign is NT-02 — do not touch the cards. |
| Any screen taking `NavController`/`onNavigate` | Switch to `MaxNavActions` (mechanical). |

**Delete**

- The pager navigation path and the `use_scroll_animation` toggle in Settings.
- Routes `maligpufreq`, `adrenogpufreq` (keep `gpustudio`).
- `TweakScreen.kt` **only after** every one of its 19 destinations appears in a hub or in Settings; verify with gate 5(d). If any row has no home, keep `TweakScreen` reachable from Control as “All tweaks (legacy)” and report it — do not drop a feature silently.

## Domain → existing screens mapping (authoritative for this task)

| Hub | Rows (existing routes) |
| --- | --- |
| CPU | `cpucorecontrol`, `governorsettings`, `preferenced`, `mtkscreen` (CPU tabs) |
| GPU | `gpustudio`, `mtkscreen` (GPU/DRAM) |
| Memory | `zrammanager`, VM/swappiness rows of `preferenced` |
| Display | `displaystudio`, `resolutionscreen` |
| Responsiveness | `touchboost`, `fpsgoscreen`, `FasScreen`, `fpsoverlay` |
| Thermal | `thermal_detail`, thermal rows of `mtkscreen` |
| Power | `chargingscreen`, `bypasschg`, `bypasschg_check`, `dozemode`, `battery_detail` |
| Storage & compiler | `dex2oat`, `storage_detail` |
| Network | `networkscheduler`, `network_detail` |
| Apps destination (not a hub) | `applist`, `app_settings/{pkg}`, `processmanager`, `debloatfreeze` |
| Settings → Advanced tools | `terminal`, `setedit`, `activitylauncher`, `kernelflasher` |
| Settings → other | `colorscheme`, `color_palette`, `diagnostics`, `logsviewer`, `aboutscreen` |

## Rules

1. **No feature behaviour changes.** No edits under `nd/max/core/**`, no ViewModel logic changes, no hardware writes added.
2. New/changed UI files import `nd.max.ui.design` only; no new `Scaffold(` in screen files; no new `nd.max.ui.component.MaxDesignSystem` imports (ADR-06).
3. No string literals in Compose; every new string in `values/` **and** `values-ar/` (ADR-14).
4. Hub summary values must carry `MaxDataTrust`; if a live value isn't available yet, show `status_unknown` with `Unsupported`/`Snapshot` — never a placeholder number (ADR-07).
5. Do not add per-screen root probes; consume the existing session state passed down from `MainActivity` (ADR-17).
6. Back behaviour: primary destinations are single-top with state saved; hubs and feature screens push normally; `get_started` remains the gated start destination.
7. Nav rail at ≥840dp stays; bottom bar has exactly 4 items.
8. Commit the currently untracked `ui/design/` + `max_*_strings.xml` **before** refactoring, so this work is recoverable (I-41).

## Acceptance criteria

- [ ] `grep -rn 'use_scroll_animation\|HorizontalPager' nd/max` → empty.
- [ ] `grep -rn 'navigate("' nd/max | grep -v '/ui/navigation/'` → empty.
- [ ] `grep -rn 'maligpufreq\|adrenogpufreq' nd/max` → empty.
- [ ] Every screen composable in `ui/mainscreens` + `ui/subscreens` is referenced from `MaxNavGraph.kt` (gate 5(d) prints no `UNREACHABLE`).
- [ ] Every route declared in `MaxDestinations.kt` is registered in the graph (gate 5(c) prints empty `ROUTES_NOT_IN_GRAPH`).
- [ ] All 19 former Tweaks destinations reachable in ≤ 2 taps from Control, or Settings, or explicitly reported as deferred.
- [ ] Bottom bar = Now, Control, Apps, Max AI. Settings reachable from Now's top bar.
- [ ] `MainActivity.kt` ≤ 400 lines and contains no `composable(` call.
- [ ] Design-language adoption count does not decrease; `Scaffold(` count does not increase (gate 4(e)).
- [ ] Arabic parity gate (§3) prints no `MISSING_AR`.
- [ ] Kotlin balance gate (§2) clean for all changed files.
- [ ] Report uses the §8 template and states that compilation is unverified in this environment.

## Verification commands

Run `VALIDATION.md` §1, §2, §3, §4, §5, §6 in order, then attempt §0 and record the failure message.

## Explicitly out of scope

- Redesigning `MaxAiScreen` internals (NT-02), ownership chip / journal / session bar (NT-02).
- Migrating feature screen bodies to `ui/design/` (NT-03).
- Apps destination workspace redesign (NT-04).
- Appearance/ambient-effects consolidation and deleting decorative engines (NT-05).
- Command palette implementation (NT-06) — only a disabled/inert top-bar affordance here.
- Any change to the six already-migrated screens beyond their navigation wiring (ADR-18).

## Deliverable

A single coherent change plus a report appended to `docs/ai/HANDOFF.md` (§“Executor log”) listing files, gate results, deferred items, and the suggested next task.
