# MaxManager UI final-pass audit — 2026-09-04

## Scope
This pass continues from `MaxManager-home-command-center-rtl.zip` and does not rebuild Home again. It focuses on the remaining Dashboard Detail screens plus cross-screen UI consistency and RTL-safe motion.

## Rebuilt Dashboard Detail screens
- `manager/app/src/main/java/nd/max/ui/mainscreens/DashboardDetailScreens.kt`
  - Thermal: live thermal map, peak status, grouped zones, live zone paths and bars.
  - Storage: internal capacity hierarchy, filesystem/mount views, refresh indicator and filesystem caveat.
  - Network: live counters, one-second download timeline, totals and peaks.
  - Battery: charge gauge, live power state, battery identity and exposed sysfs/Android properties.

## Backend preservation
The detail screens continue to use the existing real sources: `ThermalUtil.readThermalZones()`, `StatFs`/`df`, `TrafficStats`, `ACTION_BATTERY_CHANGED`, `BatteryManager`, and the existing battery sysfs nodes. No synthetic telemetry or fake values were introduced.

## UI consistency pass
- Removed screen-local `MaterialExpressiveTheme` wrappers so the screens consume the app-level theme consistently.
- Added Arabic/localized strings for the new detail UI and the previously introduced Studio section subtitles.
- Replaced directional horizontal page/list motion in onboarding/FastFetch with direction-neutral fade/scale motion.
- Preserved intentional native/interactive motion such as expand/collapse, pulse and gesture-driven resizing.

## Validation
- `values/strings.xml` and `values-ar/strings.xml`: XML parse OK.
- Kotlin structural check: all checked UI files are balanced after fixing the pre-existing extra closure in `BypassCheckScreen.kt`; `AppMonitor.kt` remains the known existing structural outlier and was not altered in this pass.
- Full Gradle compile could not run because the wrapper requires Gradle 9.5.1 and the environment cannot resolve `services.gradle.org` (network unavailable).

## Files changed since the immediately previous working tree
- `app/src/main/java/nd/max/ui/component/StudioSectionHeader.kt`
- `app/src/main/java/nd/max/ui/mainscreens/ApplistScreen.kt`
- `app/src/main/java/nd/max/ui/mainscreens/DashboardDetailScreens.kt`
- `app/src/main/java/nd/max/ui/mainscreens/GetStartedScreen.kt`
- `app/src/main/java/nd/max/ui/mainscreens/HomeScreen.kt`
- `app/src/main/java/nd/max/ui/mainscreens/SettingsScreen.kt`
- `app/src/main/java/nd/max/ui/mainscreens/TweakScreen.kt`
- `app/src/main/java/nd/max/ui/subscreens/AboutScreen.kt`
- `app/src/main/java/nd/max/ui/subscreens/AdrenoGpuScreen.kt`
- `app/src/main/java/nd/max/ui/subscreens/BypassCheckScreen.kt`
- `app/src/main/java/nd/max/ui/subscreens/ChargingScreen.kt`
- `app/src/main/java/nd/max/ui/subscreens/ColorSchemeScreen.kt`
- `app/src/main/java/nd/max/ui/subscreens/CpuCoreControlScreen.kt`
- `app/src/main/java/nd/max/ui/subscreens/CustomThemeScreen.kt`
- `app/src/main/java/nd/max/ui/subscreens/DebloatFreezeScreen.kt`
- `app/src/main/java/nd/max/ui/subscreens/Dex2oatScreen.kt`
- `app/src/main/java/nd/max/ui/subscreens/FasSettingsScreen.kt`
- `app/src/main/java/nd/max/ui/subscreens/FpsGoSettingsScreen.kt`
- `app/src/main/java/nd/max/ui/subscreens/FpsOverlayScreen.kt`
- `app/src/main/java/nd/max/ui/subscreens/GovSettingsScreen.kt`
- `app/src/main/java/nd/max/ui/subscreens/MaliGpuFreqScreen.kt`
- `app/src/main/java/nd/max/ui/subscreens/PreferencedTweakScreen.kt`
- `app/src/main/java/nd/max/ui/subscreens/ThermalDevicesScreen.kt`
- `app/src/main/java/nd/max/ui/terminal/FastFetchView.kt`
- `app/src/main/res/values-ar/strings.xml`
- `app/src/main/res/values/strings.xml`
