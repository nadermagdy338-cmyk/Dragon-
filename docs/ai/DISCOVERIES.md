# DISCOVERIES

Findings from the full exploration pass (2026-09-15). Only things with lasting value are recorded; each one is evidence-backed.

## D-01 — The app has TWO navigation architectures at runtime

`MainActivity` picks between them with a SharedPreferences boolean `use_scroll_animation`:
- off → four independent routes (`home`,`applist`,`tweaks`,`settings`) with a bottom bar.
- on → one route `main` wrapping a `HorizontalPager` over the same four screens, with `currentRoute` derived from `pagerState.currentPage`.

Every navigation concern (bottom-bar highlighting, `popUpTo` targets, tile deep-links, transitions) is written twice, once per model. This is the single largest source of accidental complexity in the UI layer and it is a **user-invisible setting** — nobody benefits from the duality.

## D-02 — The differentiator is not in the navigation at all

`core/maxai` + `core/hardware` implement something genuinely rare in this product category: canonical knob identity (`HardwareControlKey`), leases with captured baselines and verified apply/rollback (`HardwareControlArbiter`), a safety governor that pre-vetoes and post-verifies thermals, per-knob manual locks, a cross-process ownership journal, and per-knob learning with credibility decay.

But in the UI this is **one route (`maxai`) that is not in the bottom bar**, rendered as a stack of generic cards (MasterSwitch / Objective / ProfileMode / EngineStatus / Safety / ActivityCounters). Meanwhile the manual control screens (CPU, GPU, ZRAM…) do not show who currently owns a knob, whether a value was verified after writing, or whether Max AI is about to move it. **The product's best asset is invisible exactly where it would create trust.**

## D-03 — Feature sprawl is really domain fragmentation

~46 screens / 40+ flat routes, but only ~9 real device domains. Concrete overlaps found:

| Domain | Screens today |
| --- | --- |
| Power/battery | `chargingscreen`, `bypasschg`, `bypasschg_check`, `dozemode` |
| Display/frame | `displaystudio`, `resolutionscreen`, `fpsoverlay`, RefreshRate components |
| Responsiveness | `touchboost`, `fpsgoscreen`, `FasScreen` |
| CPU | `cpucorecontrol`, `governorsettings`, `preferenced`, MTK CPU/Freq/PPM tabs |
| GPU | `gpustudio` (+ 2 dead aliases), MTK DRAM/Boost tabs |
| Apps/processes | `applist`, `app_settings/{pkg}`, `processmanager`, `debloatfreeze` |
| Memory/storage | `zrammanager`, `dex2oat`, `storage_detail` |

`gpustudio`, `maligpufreq`, `adrenogpufreq` all resolve to the same composable — two are dead vendor-era aliases.

## D-04 — Tweaks is a launcher, not a tool

`TweakScreen` (1222 lines) is a flat `ListItem` list whose rows are screen names ("Touch Boost", "ZRAM", "Dex2oat"…). It tells the user nothing about current device state, what is already applied, what is risky, or what Max AI owns. Discovery of 19 features therefore depends on the user already knowing the vocabulary.

## D-05 — Two design systems coexist, and the good one is the newer minority

- `ui/design/` (new, untracked, 7 files): tokens (`MaxSpace/Radius/Size/Alpha/Duration`), semantic `MaxTone`, **`MaxDataTrust` (Live / Snapshot / Unsupported)**, 3-level structure (Section → Group → Row), one page shell (`MaxScreen` / `MaxListScreen`), explained blocking `MaxCondition` vs non-blocking banner, `MaxMetric` with source + trust, control rows with `lockedReason`.
- `ui/component/` + `ui/components/` (legacy, ~32 files): `MaxDesignSystem`, `ScreenChrome`, `StudioComponents`, `ExpressiveListComponent` (1040), `HomeComponents` (1142), `DialogComponent` (690), plus decorative engines `PulseArt`, `PulseFieldEngine`, `AmbientGlowCycle`, `AmbientMotifOverlay`, `WeatherEffects`, `VideoWallpaperPlayer`.

Adoption: **6 of ~46 screens**; 33 still declare their own `Scaffold`. Two packages named `component` and `components` also exist side by side.

## D-06 — `MaxDataTrust` is the strongest product idea already in the codebase

The reworked screens mark every number as Live / Snapshot / Unsupported, name the source (sysfs path, vendor HAL), and state why a control is locked. For a root performance tool — a category full of apps that show fake or stale numbers — **provable honesty is the differentiating brand promise**, and it is already implemented as a component contract. It should be lifted from "detail in 6 screens" to "the product's core rule".

## D-07 — Decoration costs credibility in this specific product

Blur (`haze`), ambient glow cycles, motif overlays, weather effects, video wallpaper and pulse fields run in an app whose entire promise is saving power and frames. `expressive_blur_ui` and wallpaper effects are opt-in but the machinery is always compiled in and partly always composed. A performance tool must be measurably cheap to display.

## D-08 — Root/module status is re-probed on every navigation event

`LaunchedEffect(rawRoute, pagerState.currentPage) { refreshStatus() }` runs `RootUtils.requestRootAccess()` + `isModuleInstalled()` on **every** route change and page swipe, and also re-reads SharedPreferences during composition. There is no single app-level session state for root/module/SELinux availability, so screens each invent their own "unsupported" story.

## D-09 — Settings occupies a first-class slot it does not deserve

One of four bottom-bar slots is `settings`, which itself navigates to only two destinations (`aboutscreen`, `color_palette`) plus in-page switches, while appearance work is enormous (`CustomThemeScreen` 1304 lines, `ColorSchemeScreen` 501, wallpaper/video/weather). Meanwhile Max AI, Diagnostics, Terminal, Logs, SetEdit, ActivityLauncher, KernelFlasher have no home in the primary IA.

## D-10 — Localization parity has regressed for the new screens

`values/strings.xml` has 1474 strings, `values-ar/strings.xml` has 777. The new design work added `max_screen_strings.xml` (177 keys) + `max_design_strings.xml` (11 keys) in `values/` **only**; `values-ar` has 77 `max_*` keys. So the rebuilt screens are partially English-only in an app that is RTL-first for its author and translated to ~100 locales. `MainActivity` already documents a rule that motion must not imply LTR reading direction — that rule needs the same enforcement for copy.

## D-11 — Verification must be static here

`java` is OpenJDK 25, but the only `gradle` on PATH is 4.4.1 and the wrapper needs 9.5.1 from a network that is unreachable. Prior work logs (`FINAL_UI_AUDIT.md`, aegis checkpoints) hit the same wall. The codebase compensated with **source-level architecture tests** (`ControlPlaneArchitectureTest`) and python/grep contract assertions. That pattern is the right lever for design-language enforcement too.

## D-12 — Known structural outlier

`AppMonitor.kt` fails naive brace-balance checks and is documented in `docs/aegis/work/.../20-checkpoint.md` as a pre-existing lexer artifact identical to HEAD. Do not "fix" it as part of UI work; treat it as baseline noise.

## D-13 — State fragmentation

Nine independent persistence surfaces (`settings`, `app_prefs`, `SettingsPreference`, `FpsOverlayPrefs`, `TerminalPreferences`, `ProfilePresetStore`, `GpuTweakPersistence`, `PerAppRecoveryStore`, `CredibilityStore`) with no repository boundary; several are read inside composition. Any settings redesign must land a single read path first or it will fight itself.
