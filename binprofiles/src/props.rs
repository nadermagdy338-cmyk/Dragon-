// Copyright (C) 2026-2027 Zexshia
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! Single source of truth for every `persist.sys.maxmanager*` property key this
//! crate reads or writes with [`crate::utils::getprop`] /
//! [`crate::utils::setprop_cmd`].
//!
//! Before this file existed the same key strings were typed out by hand in
//! `profiles/mod.rs`, `utils/mod.rs` and `chipsets/mediatek.rs`, so a typo in
//! one spot silently created a dead, never-read property. Import from here
//! instead of writing a new `"persist.sys.maxmanager..."` literal.
//!
//! The manager app (Kotlin, `MaxManagerProps.kt`) and the shell scripts
//! (`mainfiles/common/props.sh`) mirror this same grouping and use the exact
//! same key strings, even though the three can't literally share constants
//! across languages — keep the three lists in sync if a key is ever renamed.

// ============================================================================
// GENERAL
// ============================================================================

/// Detected SoC family (`mediatek`, `snapdragon`, `exynos`, `tensor`, `unisoc`).
pub const SOC_TYPE: &str = "persist.sys.maxmanager.soctype";
/// `"true"` when debug/verbose logging is turned on from the manager app.
pub const DEBUG_MODE: &str = "persist.sys.maxmanager.debugmode";
/// `"1"` when the user disabled all tweaks from the manager app.
pub const DISABLE_TWEAK: &str = "persist.sys.maxmanager.disabletweak";
/// `"1"` while the manager app's Core Grid screen holds a manual CPU frequency
/// session. The global profiles re-apply on every Max AI decision and app
/// switch; without this flag they would wipe the user's hand-applied limits
/// within a minute and re-lock the `scaling_*_freq` nodes to 0444. The key is
/// deliberately non-persistent (`sys.*`, not `persist.sys.*`): a crash can at
/// worst leave it set until reboot, and the Java companion clears it on startup.
/// The Kotlin mirror is `MaxManagerProps.CoreControl.MANUAL_FREQ_SESSION`.
pub const MANUAL_FREQ_SESSION: &str = "sys.maxmanager.manual_freq_session";

// ============================================================================
// CONF (feature toggles, `persist.sys.maxmanagerconf.*`)
// ============================================================================

pub const CONF_CLEARBG: &str = "persist.sys.maxmanagerconf.clearbg";
pub const CONF_LITEMODE: &str = "persist.sys.maxmanagerconf.litemode";
pub const CONF_FREQOFFSET: &str = "persist.sys.maxmanagerconf.freqoffset";
pub const CONF_SCHEMECONFIG: &str = "persist.sys.maxmanagerconf.schemeconfig";
pub const CONF_RENDERER: &str = "persist.sys.maxmanagerconf.renderer";
pub const CONF_USEFPSGO: &str = "persist.sys.maxmanagerconf.usefpsgo";

// ============================================================================
// GOVERNOR PROFILES
//
// Each hardware knob (CPU governor, I/O scheduler, Mali GPU governor) has
// three parallel keys:
//   - `default_*`        -> the value MaxManager detected/saved on first run
//   - `custom_default_*` -> user override for the "default" profile
//   - `custom_performance_*` / `custom_powersave_*` -> user override per
//     profile
// `profiles::mod` always tries the custom key first and falls back to the
// detected default, which is why the two always show up in pairs below.
// ============================================================================

pub const CPU_GOV_DEFAULT: &str = "persist.sys.maxmanager.default_cpu_gov";
pub const CPU_GOV_CUSTOM_DEFAULT: &str = "persist.sys.maxmanager.custom_default_cpu_gov";
pub const CPU_GOV_CUSTOM_PERFORMANCE: &str = "persist.sys.maxmanager.custom_performance_cpu_gov";
pub const CPU_GOV_CUSTOM_POWERSAVE: &str = "persist.sys.maxmanager.custom_powersave_cpu_gov";

pub const IO_SCHED_DEFAULT: &str = "persist.sys.maxmanager.default_balanced_IO";
pub const IO_SCHED_CUSTOM_DEFAULT: &str = "persist.sys.maxmanager.custom_default_balanced_IO";
pub const IO_SCHED_CUSTOM_PERFORMANCE: &str = "persist.sys.maxmanager.custom_performance_IO";
pub const IO_SCHED_CUSTOM_POWERSAVE: &str = "persist.sys.maxmanager.custom_powersave_IO";

pub const MALIGPU_GOV_DEFAULT: &str = "persist.sys.maxmanager.default_maligpu_gov";
pub const MALIGPU_GOV_CUSTOM_DEFAULT: &str = "persist.sys.maxmanager.custom_default_maligpu_gov";
pub const MALIGPU_GOV_CUSTOM_PERFORMANCE: &str = "persist.sys.maxmanager.custom_performance_maligpu_gov";
pub const MALIGPU_GOV_CUSTOM_POWERSAVE: &str = "persist.sys.maxmanager.custom_powersave_maligpu_gov";
