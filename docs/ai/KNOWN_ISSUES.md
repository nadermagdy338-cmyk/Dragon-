# KNOWN_ISSUES

Open problems as of 2026-09-15. `P1` blocks the redesign, `P2` degrades quality, `P3` is debt to retire opportunistically. Each item names where it is fixed.

## Architecture / navigation

| ID | P | Issue | Evidence | Fixed by |
| --- | --- | --- | --- | --- |
| I-01 | P1 | Dual navigation models (`use_scroll_animation` → bottom-bar routes vs `main` pager) duplicate every nav concern | `MainActivity.kt` | NT-01 / ADR-01 |
| I-02 | P1 | 40+ string-literal routes registered inline in an 817-line activity; no typed registry | `MainActivity.kt` | NT-01 / ADR-02 |
| I-03 | P1 | Dead route aliases `maligpufreq`, `adrenogpufreq` both resolve to `GpuStudioScreen` | `MainActivity.kt` | NT-01 |
| I-04 | P1 | Max AI has no primary entry point despite being the product differentiator | route `maxai` | NT-01 → NT-02 |
| I-05 | P2 | Settings holds a primary slot but navigates to only 2 destinations | `SettingsScreen.kt` | NT-01 / ADR-03 |
| I-06 | P2 | Three competing Home implementations (`HomeScreen` 185 + `HomeDashboardComponents` 355 + `LegendaryHomeDashboard` 696 + `ui/component/HomeComponents` 1142) | file sizes | NT-01/NT-03 |
| I-07 | P2 | Root/module status re-probed on every route change and pager swipe; no app-level session state | `LaunchedEffect(rawRoute, pagerState.currentPage)` | ADR-17, own task |
| I-08 | P3 | Nine independent persistence surfaces, several read inside composition | see PROJECT_MAP §state | after NT-05 |

## Design system

| ID | P | Issue | Evidence | Fixed by |
| --- | --- | --- | --- | --- |
| I-10 | P1 | Only 6 of ~46 screens use `ui/design/`; 33 declare their own `Scaffold` | grep `Scaffold(` | NT-03 (per domain) |
| I-11 | P2 | Two design systems + two near-identical packages `ui/component/` and `ui/components/` | package listing | ADR-06 |
| I-12 | P2 | Giant unmaintainable UI files: `CpuCoreControlScreen` 1305, `CustomThemeScreen` 1304, `TweakScreen` 1222, `AppSettingsScreen` 1174, `HomeComponents` 1142, `ExpressiveListComponent` 1040, `DashboardDetailScreens` 959 | `wc -l` | NT-03/NT-04/NT-05 |
| I-13 | P2 | Decorative engines (pulse field, ambient glow/motif, weather, video wallpaper, haze blur) cost frames/battery in a performance app | `ui/component/`, `ui/components/` | ADR-12 / NT-05 |
| I-14 | P3 | `MainActivity` defines its own `ExpressiveShapes` duplicating theme shapes | `MainActivity.kt` | NT-01 |

## Product truthfulness

| ID | P | Issue | Evidence | Fixed by |
| --- | --- | --- | --- | --- |
| I-20 | P1 | Knob ownership, verification results, and rollbacks are invisible in manual control screens | `core/hardware` vs `ui/subscreens` | NT-02 / ADR-09 |
| I-21 | P1 | No audit journal for Max AI actions in the UI | `MaxAiScreen.kt` (card-only) | NT-02 / ADR-10 |
| I-22 | P2 | Unmigrated screens show metrics without freshness/source; “unknown” handling is inconsistent | 33 screens | NT-03 / ADR-07 |
| I-23 | P2 | Capability vs state conflated outside the 6 migrated screens | 33 screens | ADR-08 |
| I-24 | P2 | High-risk tools (Terminal, SetEdit, ActivityLauncher, KernelFlasher) sit beside ordinary tweaks with no risk gate | `tweaks` list | NT-05 / ADR-16 |

## Localization / accessibility

| ID | P | Issue | Evidence | Fixed by |
| --- | --- | --- | --- | --- |
| I-30 | P1 | New design strings exist only in `values/`: 188 keys added, `values-ar` has 77 `max_*` keys; `values/strings.xml` 1474 vs `values-ar/strings.xml` 777 | res grep | NT-07 + ADR-14 in every task |
| I-31 | P2 | Hardcoded English literals remain in unmigrated Compose screens | grep `Text("` | NT-03 |
| I-32 | P2 | No UI/instrumentation tests at all; a11y (state descriptions, touch targets) enforced only by convention in `ui/design/` | test dirs | NT-07 (static test) |

## Environment / process

| ID | P | Issue | Evidence | Fixed by |
| --- | --- | --- | --- | --- |
| I-40 | P1 | Cannot compile here: PATH `gradle` is 4.4.1, wrapper needs 9.5.1, `services.gradle.org` unreachable (JDK 25 is present) | `gradle --version`, `gradle-wrapper.properties` | VALIDATION.md static gates |
| I-41 | P2 | Redesign work is uncommitted/untracked (`ui/design/`, new string files) — easy to lose | `git status` | commit early in NT-01 |
| I-42 | P2 | `manager/FINAL_UI_AUDIT.md` references screens that no longer exist (`AdrenoGpuScreen`, `MaliGpuFreqScreen`, `ThermalDevicesScreen`) | file vs tree | superseded by docs/ai |
| I-43 | P3 | `AppMonitor.kt` fails naive brace-balance checks (pre-existing lexer artifact, identical to HEAD) — do not “fix” in UI work | aegis checkpoint | ignore, baseline |
| I-44 | P3 | `manager/kernel-flasher` is a vendored fork with its own theme/type files — duplicate-looking files are expected | package `com.github.capntrips.kernelflasher` | leave alone |
