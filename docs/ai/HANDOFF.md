# HANDOFF

From: Architect pass (Notion AI), 2026-09-15 — exploration, product vision, documentation. **No app code was modified in this pass.**
To: DeepSeek Harness (Executor)

## Read order

1. `PROJECT_STATE.md` — where the repo and the toolchain stand.
2. `DESIGN_VISION.md` — the product direction you are implementing.
3. `DECISIONS.md` — binding rules (ADR-01–18).
4. `NEXT_TASK.md` — **NT-01**, the task to execute now.
5. `VALIDATION.md` — the gates you must run and report.
6. `PROJECT_MAP.md`, `DISCOVERIES.md`, `KNOWN_ISSUES.md`, `COMPLETED_WORK.md` — reference while working.

## Current state in one paragraph

MaxManager has a strong engine and a fragmented surface. `core/maxai` + `core/hardware` implement a real control plane (canonical knob keys, leases with baselines, verified apply/rollback, safety veto, per-knob manual locks, cross-process ownership journal, per-knob learning). A good design language exists in `ui/design/` and six screens have been rebuilt on it (TouchBoost is the reference). But navigation is written twice (bottom-bar routes vs a pager behind `use_scroll_animation`), 40+ route literals live inside an 817-line `MainActivity`, ~46 screens cover only ~9 device domains with duplicate/dead routes, Max AI is not a primary destination, and 33 screens still hand-roll their own `Scaffold`. The vision reframes the product as *a control plane that can prove what it is doing* with four destinations — Now, Control, Apps, Max AI — and NT-01 builds that spine.

## Invariants — never break these

1. **Control plane is the only write path.** UI/ViewModels must not write sysfs or run root shells directly; go through the arbiter/control plane so leases, baselines, verification and rollback hold (ADR-11).
2. **No synthetic telemetry.** Unknown is `status_unknown`; stale is `Snapshot`; unsupported is `Unsupported`. Never format a fallback as a live value (ADR-07).
3. **Manual intent wins per knob.** Touching a control locks only that knob (`ManualControlLocks`); never reintroduce a global “controller mode”.
4. **Capability ≠ state.** Providers/capability sections stay visually separate from state controls; disabled controls always state `lockedReason` (ADR-08).
5. **Localization + RTL.** New copy lands in `values/` and `values-ar/` together; motion must not imply LTR direction; no literals in Compose (ADR-14).
6. **Don't redo finished work.** The 6 migrated screens and the control plane change only when a rule above requires an additive change (ADR-18).
7. **Don't “fix” baseline noise.** `AppMonitor.kt` brace imbalance (I-43) and the vendored `kernel-flasher` duplicate theme files (I-44) are expected.
8. **Commit the untracked foundation first.** `ui/design/`, `max_design_strings.xml`, `max_screen_strings.xml` are unversioned; commit before refactoring (I-41).

## Environment facts you will hit

- Root: `/mnt/sdcard/MaxManger/optmize-main`; app sources at `manager/app/src/main/java/nd/max/`.
- JDK 25 present, but PATH `gradle` is 4.4.1 while the wrapper wants 9.5.1 and the network is down → **you cannot compile.** Use `VALIDATION.md` static gates and report “compilation unverified in this environment”.
- Working tree is intentionally dirty with the previous agent's redesign work — do not discard it.

## What the Executor needs to produce for NT-01

- `ui/navigation/` package (destinations, graph, actions) as the single source of routes.
- `ControlScreen` + 9 domain hubs that *host* existing feature screens without moving their bodies.
- A slimmed `MainActivity` with one navigation model and 4 primary destinations, Settings moved to Now's top bar, Advanced tools gated.
- Gate results per `VALIDATION.md` §8 and a list of anything deferred.

## Queued after NT-01

| ID | Task |
| --- | --- |
| NT-02 | Max AI as a primary surface: ownership map (`MaxOwnerChip`), change journal (`MaxJournalRow`), session bar, safety + credibility views; replace the card stack |
| NT-03 | Migrate hub bodies to `ui/design/` domain by domain (Power → Display → CPU → GPU → …), deleting legacy components as they are orphaned |
| NT-04 | Apps destination: per-app profile as one workspace (applist + app settings + process + freeze) |
| NT-05 | Settings & Appearance consolidation, ambient-effects switch default off, advanced-tools risk gate, delete unreferenced decorative engines |
| NT-06 | Command palette + device truth sheet (per-knob capability proof from `HardwareCapabilityResolver`) |
| NT-07 | Localization parity sweep + make the design-adoption static test blocking; first UI tests |

## Open questions (decide by defaulting, do not block on the user)

- Whether `preferenced` rows split cleanly between CPU and Memory hubs — default: keep the screen whole under CPU and list a Memory row that deep-links to it, then split in NT-03.
- Whether `mtkscreen` tabs should be dissolved into the domain hubs — default: keep the tabbed screen for now, linked from CPU/GPU/Thermal; dissolve in NT-03 if each tab is thin.
- `LegendaryHomeDashboard` vs `HomeDashboardComponents` — default: keep whichever `HomeScreen` currently renders, delete the other in NT-03 after reference check.

## Executor log

<!-- Append one entry per completed task: task id, files, gate results, deferred items, next suggestion. -->

### NT-01 Executor log

Implemented the typed navigation registry, graph, actions, four primary destinations, Control screen, nine domain hub entry points, localized navigation resources, and reduced MainActivity to a single NavHost. Removed the obsolete scroll-animation preference from CustomThemeScreen. Static hygiene and XML parsing passed; MainActivity is 88 lines with no inline composable registrations and legacy GPU aliases are absent. The build is unverified because the Gradle wrapper is not executable in this environment (permission denied). Existing MTK feature tabs still contain an internal HorizontalPager; hub row wiring and Settings/Home top-bar integration remain follow-up work.

### NT-01 completion pass — Executor update

- Implemented: restored the single NavHost spine with Now / Control / Apps / Max AI, typed registry/actions/graph, nine domain hubs, legacy All Tweaks fallback, Settings diagnostics/logs/advanced-tools sections, Home Settings entry, and existing update/reboot/session behavior.
- Added: semantic destination names, risk levels, hub descriptions, English/Arabic navigation resources, typed navigation at former literal call sites, and separated nav bar components.
- Gates: XML parsing OK; Arabic parity OK (0 missing / 0 extra); registry objects 50 = graph routes 50; no literal navigate calls outside navigation package; no old GPU aliases or scroll-animation setting; MainActivity 386 lines and no inline composable registrations; diff-check OK.
- Build: not verified — offline wrapper attempt failed because JAVA_HOME/java is unavailable in this environment.
- Deferred safely: internal MTK feature pager remains feature-local; legacy TweakScreen remains reachable because its functional toggle rows have no hub home yet; compilation/device behavior still requires a real Android toolchain/device.
- Next: NT-02 Max AI ownership/journal/session surface, after the compiler gate is available.

