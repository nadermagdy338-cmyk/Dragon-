/*
 * Copyright (C) 2026 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max

/**
 * Shared `persist.sys.maxmanager*` system-property key constants.
 *
 * Before this file existed, the same key — e.g.
 * `"persist.sys.maxmanagerconf.AIenabled"` — was hand-typed independently in
 * SettingViewmodel, AppSettingsViewmodel, GovSettingsScreen, and related
 * hardware controls. A typo in any one of those copies silently created a
 * dead property that PropertyUtils would write to (or read from) without ever
 * matching what the daemon/binaries actually use. Centralising it here gives
 * callers one source of truth, for the same reason [MaxManagerPaths] exists.
 *
 * The shell scripts (`mainfiles/props.sh`) and the Rust binaries
 * (`binprofiles/src/props.rs`, `binutils/src/utils/mod.rs`) mirror this same
 * grouping and use the exact same key strings, even though the three can't
 * literally share one constant across languages — keep all of them in sync
 * if a key is ever renamed.
 */
object MaxManagerProps {

    // ------------------------------------------------------------------
    // General (persist.sys.maxmanager.*)
    // ------------------------------------------------------------------
    object General {
        /** Master module marker, e.g. `getprop persist.sys.maxmanager`. */
        const val MASTER = "persist.sys.maxmanager"
        const val DEBUG_MODE = "persist.sys.maxmanager.debugmode"
        const val DISABLE_TWEAK = "persist.sys.maxmanager.disabletweak"
        const val DROP_FOREGROUND = "persist.sys.maxmanager.dropforeground"
        const val PROFILE_NOTIFICATIONS = "persist.sys.maxmanager.profilenotifications"
        /** Detected SoC family (0=Unknown, 1=MediaTek, 2=Snapdragon, 3=Exynos, 4=Unisoc, 5=Tensor). */
        const val SOC_TYPE = "persist.sys.maxmanager.soctype"
        /** Debug-build-only override of [SOC_TYPE], used by the debug flavor to force a chipset path. */
        const val SOC_TYPE_DEBUG = "persist.sys.maxmanagerdebug.soctype"
    }

    // ------------------------------------------------------------------
    // Conf / feature toggles (persist.sys.maxmanagerconf.*)
    // ------------------------------------------------------------------
    object Conf {
        const val AI_ENABLED = "persist.sys.maxmanagerconf.AIenabled"
        const val SHOW_TOAST = "persist.sys.maxmanagerconf.showtoast"
        const val AUTO_PRELOAD = "persist.sys.maxmanagerconf.APreload"
        const val DYNAMIC_THERMAL = "persist.sys.maxmanagerconf.DThermal"
        const val SFL = "persist.sys.maxmanagerconf.SFL"
        const val BYPASS_CHARGE = "persist.sys.maxmanagerconf.bypasschg"
        const val BYPASS_CHARGE_THRESHOLD = "persist.sys.maxmanagerconf.bypasschgthreshold"
        const val BYPASS_PATH = "persist.sys.maxmanagerconf.bypasspath"
        const val CLEAR_BG = "persist.sys.maxmanagerconf.clearbg"
        const val CPU_LIMIT = "persist.sys.maxmanagerconf.cpulimit"
        /**
         * Master switch for the detailed EVENT= log trail added across the
         * AppMonitorLogger.kt/EventLog.kt/switch_id work: per-user-action
         * (EventLog) and per-app-switch-correlated (AppMonitorLogger)
         * entries. Off by default -- read via [nd.max.ui.util.PropertyUtils]
         * before either forwards a line to MaxManager.log, so a person who
         * never opts in pays no extra shell-spawn cost per settings tap.
         * Deliberately does NOT gate the native daemon's own log_zenith()
         * calls (SystemLogger.c) -- those are the daemon's pre-existing
         * baseline logging, only reformatted to EVENT=... in this project,
         * and turning them off would be a real regression unrelated to this
         * toggle's purpose.
         */
        const val DETAILED_LOG = "persist.sys.maxmanagerconf.detailedlog"
        const val DISABLE_TRACE = "persist.sys.maxmanagerconf.disabletrace"
        const val DND = "persist.sys.maxmanagerconf.dnd"
        const val FPS_GED = "persist.sys.maxmanagerconf.fpsged"
        const val FREQ_OFFSET = "persist.sys.maxmanagerconf.freqoffset"
        const val FSTRIM = "persist.sys.maxmanagerconf.fstrim"
        const val IO_SCHED = "persist.sys.maxmanagerconf.iosched"
        const val JUST_IN_TIME = "persist.sys.maxmanagerconf.justintime"
        const val LITE_MODE = "persist.sys.maxmanagerconf.litemode"
        const val LOGD = "persist.sys.maxmanagerconf.logd"
        const val MALI_SCHED = "persist.sys.maxmanagerconf.malisched"
        const val PRELOAD_BUDGET = "persist.sys.maxmanagerconf.preloadbudget"
        const val REFRESH_RATE = "persist.sys.maxmanagerconf.refreshrate"
        const val RENDERER = "persist.sys.maxmanagerconf.renderer"
        const val SCHED_TUNES = "persist.sys.maxmanagerconf.schedtunes"
        const val SCHEME_CONFIG = "persist.sys.maxmanagerconf.schemeconfig"
        const val THERMAL_CORE = "persist.sys.maxmanagerconf.thermalcore"
        const val USE_FPSGO = "persist.sys.maxmanagerconf.usefpsgo"
        const val WALT_TUNES = "persist.sys.maxmanagerconf.walttunes"
    }

    // ------------------------------------------------------------------
    // Governor profiles
    //
    // Each hardware knob (CPU governor, I/O scheduler, Mali GPU governor)
    // has parallel keys: `DEFAULT` (value MaxManager auto-detected on first
    // run) plus a `CUSTOM_*` override per profile (default/performance/
    // powersave). Code should always try the matching CUSTOM_* key first
    // and fall back to DEFAULT.
    // ------------------------------------------------------------------
    object Governor {
        const val CPU_DEFAULT = "persist.sys.maxmanager.default_cpu_gov"
        const val CPU_CUSTOM_DEFAULT = "persist.sys.maxmanager.custom_default_cpu_gov"
        const val CPU_CUSTOM_PERFORMANCE = "persist.sys.maxmanager.custom_performance_cpu_gov"
        const val CPU_CUSTOM_POWERSAVE = "persist.sys.maxmanager.custom_powersave_cpu_gov"

        const val IO_DEFAULT = "persist.sys.maxmanager.default_balanced_IO"
        const val IO_CUSTOM_DEFAULT = "persist.sys.maxmanager.custom_default_balanced_IO"
        const val IO_CUSTOM_PERFORMANCE = "persist.sys.maxmanager.custom_performance_IO"
        const val IO_CUSTOM_POWERSAVE = "persist.sys.maxmanager.custom_powersave_IO"

        const val MALIGPU_DEFAULT = "persist.sys.maxmanager.default_maligpu_gov"
        const val MALIGPU_CUSTOM_DEFAULT = "persist.sys.maxmanager.custom_default_maligpu_gov"
        const val MALIGPU_CUSTOM_PERFORMANCE = "persist.sys.maxmanager.custom_performance_maligpu_gov"
        const val MALIGPU_CUSTOM_POWERSAVE = "persist.sys.maxmanager.custom_powersave_maligpu_gov"

    }

    // ------------------------------------------------------------------
    // Mali / GPU
    // ------------------------------------------------------------------
    object Mali {
        const val GED_BOOST = "persist.sys.maxmanager.custom_mali_ged_boost"
        const val MAXFREQ = "persist.sys.maxmanager.custom_mali_maxfreq"
        const val MINFREQ = "persist.sys.maxmanager.custom_mali_minfreq"
        const val POWER_POLICY = "persist.sys.maxmanager.custom_mali_power_policy"
        const val PRESET = "persist.sys.maxmanager.custom_mali_preset"
        const val THROTTLE_BYPASS = "persist.sys.maxmanager.custom_mali_throttle_bypass"
        const val SIC_BOOST = "persist.sys.maxmanager.custom_sic_boost"
    }

    // ------------------------------------------------------------------
    // Adreno / GPU (Qualcomm Snapdragon — kgsl-3d0 devfreq node)
    // ------------------------------------------------------------------
    object Adreno {
        const val MAXFREQ = "persist.sys.maxmanager.custom_adreno_maxfreq"
        const val MINFREQ = "persist.sys.maxmanager.custom_adreno_minfreq"
        const val PRESET = "persist.sys.maxmanager.custom_adreno_preset"
        const val GOVERNOR = "persist.sys.maxmanager.custom_adreno_governor"
        const val THROTTLE_BYPASS = "persist.sys.maxmanager.custom_adreno_throttle_bypass"
    }

    // ------------------------------------------------------------------
    // Network (/proc/sys/net/ipv4/*)
    // ------------------------------------------------------------------
    object Network {
        const val TCP_CONGESTION = "persist.sys.maxmanager.custom_tcp_congestion"
        const val TCP_SYNCOOKIES = "persist.sys.maxmanager.custom_tcp_syncookies"
        const val TCP_REUSE = "persist.sys.maxmanager.custom_tcp_reuse"
        const val TCP_FASTOPEN = "persist.sys.maxmanager.custom_tcp_fastopen"
        const val TCP_SACK = "persist.sys.maxmanager.custom_tcp_sack"
        const val TCP_ECN = "persist.sys.maxmanager.custom_tcp_ecn"
    }

    // ------------------------------------------------------------------
    // Scheduler (/proc/sys/kernel/sched_* and friends)
    // ------------------------------------------------------------------
    object Scheduler {
        const val BORE = "persist.sys.maxmanager.custom_sched_bore"
        const val AUTOGROUP = "persist.sys.maxmanager.custom_sched_autogroup"
        const val CHILD_RUNS_FIRST = "persist.sys.maxmanager.custom_sched_child_runs_first"
        const val SCHEDSTATS = "persist.sys.maxmanager.custom_sched_schedstats"
        const val TUNABLE_SCALING = "persist.sys.maxmanager.custom_sched_tunable_scaling"
        const val CSTATE_AWARE = "persist.sys.maxmanager.custom_sched_cstate_aware"
        const val UCLAMP_MAX = "persist.sys.maxmanager.custom_sched_uclamp_max"
        const val UCLAMP_MIN = "persist.sys.maxmanager.custom_sched_uclamp_min"
        const val PRINTK = "persist.sys.maxmanager.custom_sched_printk"
        /** Serialized "path=value;path=value" overrides for the dynamic Advanced Parameters list. */
        const val GENERIC_OVERRIDES = "persist.sys.maxmanager.custom_sched_generic_overrides"
    }

    // ------------------------------------------------------------------
    // Display / resolution
    // ------------------------------------------------------------------
    object Display {
        const val RES_WIDTH = "persist.sys.maxmanager.custom_res_width"
        const val RES_HEIGHT = "persist.sys.maxmanager.custom_res_height"
        const val RES_DPI = "persist.sys.maxmanager.custom_res_dpi"
        const val HDR_ENABLE = "persist.sys.maxmanager.custom_hdr_enable"
        const val VIDEO_ENHANCE = "persist.sys.maxmanager.custom_video_enhance"
        const val SILKY_BRIGHTNESS = "persist.sys.maxmanager.custom_silky_brightness"
        const val SUNLIGHT_MODE = "persist.sys.maxmanager.custom_sunlight_mode"
    }

    // ------------------------------------------------------------------
    // Touch
    // ------------------------------------------------------------------
    object Touch {
        const val BOOST = "persist.sys.maxmanager.custom_touch_boost"
        const val DT2W = "persist.sys.maxmanager.custom_touch_dt2w"
    }

    // ------------------------------------------------------------------
    // Storage / Zram
    // ------------------------------------------------------------------
    object Storage {
        const val ZRAM_COMP_ALGORITHM = "persist.sys.maxmanager.custom_zram_comp_algorithm"
        const val ZRAM_PRESET = "persist.sys.maxmanager.custom_zram_preset"
        const val ZRAM_SIZE_MB = "persist.sys.maxmanager.custom_zram_size_mb"
        const val SWAPPINESS = "persist.sys.maxmanager.custom_swappiness"
    }

    // ------------------------------------------------------------------
    // Charging
    // ------------------------------------------------------------------
    object Charging {
        const val FASTCHARGE_MA = "persist.sys.maxmanager.custom_fastcharge_ma"
        const val CHARGE_LIMIT_PERCENT = "persist.sys.maxmanager.custom_charge_limit_percent"
    }

    // ------------------------------------------------------------------
    // Doze / standby
    // ------------------------------------------------------------------
    object Doze {
        const val ENABLED = "persist.sys.maxmanager.custom_doze_enabled"
        const val AGGRESSIVE = "persist.sys.maxmanager.custom_doze_aggressive"
        /** Stores a [nd.max.ui.util.GmsDozeMode] name, e.g. "DEFAULT"/"STANDARD"/"AGGRESSIVE". */
        const val GMS_MODE = "persist.sys.maxmanager.custom_doze_gms_mode"
    }
}