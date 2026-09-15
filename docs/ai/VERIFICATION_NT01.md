# VERIFICATION — NT-01 (Navigation spine + Control hubs)

Reviewed: 2026-09-15 by the architect pass. Method: `VALIDATION.md` §1–6 plus a static consistency script (graph refs ↔ declarations, registry ↔ graph, string-resource existence, `values-ar` parity, brace balance, adoption counters). **Compilation is still unverified in this environment** (PATH gradle 4.4.1 vs required 9.5.1, offline).

Verdict: **accepted with fixes.** The spine is real and the acceptance criteria pass. The defects below are small and local, but three of them are behavioural.

## Gates — passed

| Gate | Result |
| --- | --- |
| Pager model removed | `use_scroll_animation`, `HorizontalPager` → 0 hits |
| Route literals outside registry | 0 outside `ui/navigation/` |
| Dead GPU aliases | `maligpufreq`, `adrenogpufreq` → 0 hits |
| `MainActivity` | 386 lines, `composable(` → 0, local `ExpressiveShapes` gone, `maxNavGraph(navController)` single entry |
| Registry ↔ graph | 50 destinations declared, 50 registered, 0 orphans, 0 unreachable screens |
| Graph references | 49 composable references, all declared, no duplicate declarations |
| `nd.max` imports in new files | all resolve (incl. `nd.max.ui.gpu.mtk.MtkScreen`) |
| String resources | every `R.string.*` in new files exists; `max_navigation_strings.xml` 41 EN / 41 AR (parity ✓) |
| Duplicate resource keys | none in `values/` |
| Kotlin brace balance | clean on all new/changed files |
| Primary destinations | `PrimaryDestinations = Now, Control, Apps, MaxAi`; Settings opened from Now top bar; rail ≥840dp preserved |
| Risk model | `MaxRisk` + `maxRiskLabel` wired into Settings → Advanced tools |
| Design adoption | importers 6 → **16**; files with `Scaffold(` 33 → **31** (direction correct) |
| Legacy tweaks | kept as `AllTweaks` with a kdoc naming the 8 homeless toggles — correct honest handling, not a silent drop |

## Defects to fix (NT-01b)

### F-01 — `openActivityDetail` will crash (P1)
`MaxNavActions.openActivityDetail()` navigates to `"app_detail/" + packageName`, but that route is registered only inside the **nested** `NavHost` in `ActivitylauncherScreen.kt:171`, not in `maxNavGraph`. Calling it on the root controller throws `IllegalArgumentException: navigation destination not found`.
→ Remove the function from `MaxNavActions` (the nested host owns that route), or hoist `app_detail/{packageName}` into the registry + graph.

### F-02 — Design layer now depends on navigation (P1, architectural)
`ui/design/MaxDomainCard.kt` imports `nd.max.ui.navigation.MaxDestination`. The design language must stay app-agnostic, otherwise `ui/design` can never be reused or tested in isolation and every future destination change touches the design system.
→ Change the signature to primitives: `MaxDomainCard(title: String, subtitle: String, icon: ImageVector, state: String? = null, trust: MaxDataTrust? = null, onClick: () -> Unit)`; let `ControlScreen` map the destination.

### F-03 — Four destinations share one title string (P1, UX)
`Terminal`, `SetEdit`, `ActivityLauncher`, `KernelFlasher` all use `R.string.max_nav_advanced_tools`, so the Advanced tools list renders four rows labelled “Advanced tools”. Also `AppSettings.titleRes = max_nav_apps` and `GetStarted.titleRes = max_nav_now`.
→ Give each destination its own title key (EN + AR).

### F-04 — Apps children are only reachable through the legacy screen (P2)
`ProcessManager` and `DebloatFreeze` declare `parent = Apps`, but `ApplistScreen` only calls `openApp(pkg)`; it renders no rows for them, so they are reachable only via `AllTweaks`.
→ Add a small header group in `ApplistScreen` listing `MaxDestination.All.filter { it.parent == Apps }` (same pattern as the hubs), or move them into the NT-04 workspace explicitly.

### F-05 — Root re-probe now fires on ~50 routes instead of 4 (P2, regression risk)
`LaunchedEffect(currentRoute) { refreshStatus() }` survived the refactor, and the route space grew by an order of magnitude, so `RootUtils.requestRootAccess()` + `isModuleInstalled()` now run on every hub and feature entry.
→ Pull ADR-17 forward: one app-level session state probed once (plus explicit refresh), or at minimum key the effect on the primary destination only.

### F-06 — Hub summaries carry no live state or trust (P2, vision gap)
`MaxDomainCard` subtitles are static scope descriptions. The vision requires each hub to show current state with `MaxDataTrust` (governor, resolution, ZRAM size…).
→ Land the `state` + `trust` slots in F-02's signature now, populate per domain in NT-03.

### F-07 — Cosmetic / debt (P3)
- `ui/mainscreens/DomainHubScreens.kt` is a 1-line dead stub (package declaration only) → delete.
- `MaxDomainHubScreen` prints the hub title twice (top bar + section header) → drop the section title, keep the description.
- Unused imports: `MaxGroupDivider` in `ControlScreen.kt`, ~10 unused icon imports in `MaxDestinations.kt`.
- Nine one-line hub files are pure delegation; a single `MaxDomainHubScreen(destination)` entry in the graph would remove all nine.
- Fully-qualified inline references (`nd.max.ui.navigation.MaxDestination.Settings` in `HomeScreen`, `MaxRisk` in `SettingsScreen`) → import instead.

## Unchanged debt (expected, not NT-01's fault)

- 188 `max_design_strings` / `max_screen_strings` keys still missing from `values-ar` (I-30). NT-01's own 41 keys are at parity.
- 31 screens still hand-roll `Scaffold` (NT-03).
- 199 direct `RootFileAccess` / shell sites in `ui/**` — pre-existing, mostly in flasher/dashboard/get-started; ADR-11 applies to new code, and these must be migrated per domain in NT-03.
- Three Home implementations still coexist.
