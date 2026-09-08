#!/system/bin/sh

#
# Copyright (C) 2026-2027 Zexshia
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Single source of truth for every `persist.sys.maxmanager*` property key that
# the module's shell scripts (customize.sh, preferenced-tweaks.sh, service.sh)
# read or write. Before this file existed the same key strings were typed out
# by hand in each script separately, so a typo in one place silently created
# a dead, never-read property instead of a build/lint error.
#
# Source this from any script that needs a key, right after MODDIR is known:
#   . "$MODDIR/props.sh"
#
# The manager app (Kotlin, MaxManagerProps.kt) and the Rust binaries
# (binprofiles/src/props.rs, binutils/src/utils/mod.rs) use these exact same
# key strings, grouped the same way, even though the four can't literally
# share one constant across languages — keep them in sync if a key is ever
# renamed.

# ---- General (persist.sys.maxmanager.*) ----
readonly PROP_SOC_TYPE="persist.sys.maxmanager.soctype"
readonly PROP_DEBUG_MODE="persist.sys.maxmanager.debugmode"
readonly PROP_DISABLE_TWEAK="persist.sys.maxmanager.disabletweak"
readonly PROP_DROP_FOREGROUND="persist.sys.maxmanager.dropforeground"
readonly PROP_PROFILE_NOTIFICATIONS="persist.sys.maxmanager.profilenotifications"
readonly PROP_STATE="persist.sys.maxmanager.state"
readonly PROP_SERVICE="persist.sys.maxmanager.service"

# ---- Conf / feature toggles (persist.sys.maxmanagerconf.*) ----
readonly PROP_CONF_AI_ENABLED="persist.sys.maxmanagerconf.AIenabled"
readonly PROP_CONF_SHOW_TOAST="persist.sys.maxmanagerconf.showtoast"
readonly PROP_CONF_AUTO_PRELOAD="persist.sys.maxmanagerconf.APreload"
readonly PROP_CONF_DYNAMIC_THERMAL="persist.sys.maxmanagerconf.DThermal"
readonly PROP_CONF_SFL="persist.sys.maxmanagerconf.SFL"
readonly PROP_CONF_BYPASS_CHARGE="persist.sys.maxmanagerconf.bypasschg"
readonly PROP_CONF_BYPASS_CHARGE_THRESHOLD="persist.sys.maxmanagerconf.bypasschgthreshold"
readonly PROP_CONF_BYPASS_PATH="persist.sys.maxmanagerconf.bypasspath"
readonly PROP_CONF_CLEAR_BG="persist.sys.maxmanagerconf.clearbg"
readonly PROP_CONF_CPU_LIMIT="persist.sys.maxmanagerconf.cpulimit"
readonly PROP_CONF_DISABLE_TRACE="persist.sys.maxmanagerconf.disabletrace"
readonly PROP_CONF_DND="persist.sys.maxmanagerconf.dnd"
readonly PROP_CONF_FPS_GED="persist.sys.maxmanagerconf.fpsged"
readonly PROP_CONF_FREQ_OFFSET="persist.sys.maxmanagerconf.freqoffset"
readonly PROP_CONF_FSTRIM="persist.sys.maxmanagerconf.fstrim"
readonly PROP_CONF_IO_SCHED="persist.sys.maxmanagerconf.iosched"
readonly PROP_CONF_JUST_IN_TIME="persist.sys.maxmanagerconf.justintime"
readonly PROP_CONF_LOGD="persist.sys.maxmanagerconf.logd"
readonly PROP_CONF_MALI_SCHED="persist.sys.maxmanagerconf.malisched"
readonly PROP_CONF_PRELOAD_BUDGET="persist.sys.maxmanagerconf.preloadbudget"
readonly PROP_CONF_RENDERER="persist.sys.maxmanagerconf.renderer"
readonly PROP_CONF_SCHED_TUNES="persist.sys.maxmanagerconf.schedtunes"
readonly PROP_CONF_SCHEME_CONFIG="persist.sys.maxmanagerconf.schemeconfig"
readonly PROP_CONF_THERMAL_CORE="persist.sys.maxmanagerconf.thermalcore"
readonly PROP_CONF_USE_FPSGO="persist.sys.maxmanagerconf.usefpsgo"
readonly PROP_CONF_WALT_TUNES="persist.sys.maxmanagerconf.walttunes"
