# Thermal Devices screen — integration notes

Files (drop into the matching package folders):
- `util/ThermalUtil.kt`        -> `ui/util/ThermalUtil.kt`
- `viewmodel/ThermalDevicesViewModel.kt` -> `ui/viewmodel/ThermalDevicesViewModel.kt`
- `subscreens/ThermalDevicesScreen.kt`   -> `ui/subscreens/ThermalDevicesScreen.kt`

Three small edits already applied to your working copy (repeat them if you're
hand-applying this patch elsewhere):

1. `MainActivity.kt` — added a nav route next to `dozemode`:
   `composable("thermaldevices") { nd.max.ui.subscreens.ThermalDevicesScreen(navController) }`

2. `ui/mainscreens/TweakScreen.kt` — added a menu entry next to the Doze Mode
   entry, using `Icons.Outlined.Thermostat`, navigating to `"thermaldevices"`.

3. `res/values/strings.xml` — added the `thermal_*` string block (title, tab
   labels, empty states, trip point / enable-disable labels, etc.) right
   after the `dozemode_unavailable` entry. Only the base (English) file was
   touched — other locales will fall back to English until translated.

## What this screen does
- Reads live kernel thermal zones (`/sys/class/thermal/thermal_zoneN`) and
  cooling devices (`cooling_deviceN`) via root shell/file reads in
  `ThermalUtil`, same approach as `DozeModeUtil` — no MaxManager daemon
  involvement, since the thermal framework is exposed directly by the kernel.
- `ThermalDevicesViewModel` exposes enabled/disabled zones, cooling devices,
  a running max/avg summary, and drives a 2s auto-refresh loop.
- The screen itself reuses your own `ScreenChrome` top bar, `ExpressiveList` /
  `ExpressiveListItem` / `ExpressiveSwitchItem` / `ExpressiveDropdownItem` /
  `ExpressiveSliderItem`, and the `ThermalPulseCard` hero component already
  in your component library — no Haze glass cards, no ZKM-specific styling.
- Three tabs (Zones / Cooling / Disabled) via `SingleChoiceSegmentedButtonRow`,
  matching the tab pattern already used in `DozeModeScreen`.
