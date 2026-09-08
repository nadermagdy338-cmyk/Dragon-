/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nd.max.ui.util


import kotlinx.serialization.Serializable


@Serializable
data class AppConfig(
    // ── Performance ─────────────────────────────────────────
    val perf_lite_mode: String = "default",
    val app_priority: String = "default",
    val game_preload: String = "default",
    val cpu_boost: String = "default",          // boost CPU clocks on app launch

    // ── Per-App GPU / Governor ────────────────────────────────
    // gpu_profile changes only the GPU frequency ceiling. Default means no GPU override.
    val gpu_profile: String = "default",        // default | power | balanced | gaming | performance
    val cpu_governor: String = "default",       // actual kernel-supported CPU governor
    val gpu_governor: String = "default",       // actual kernel-supported GPU governor
    val gpu_max_freq: String = "default",       // actual value from the GPU available_frequencies node
    // Legacy field kept for JSON compatibility; migrated to gpu_profile by the ViewModel.
    val thermal_profile: String = "default",

    // ── Display & Render ─────────────────────────────────────
    val refresh_rate: String = "default",
    val renderer: String = "default",
    val resolution_downscale: String = "default",

    // ── Experience & Extras ──────────────────────────────────
    val dnd_on_gaming: String = "default",
    val bypass_charging: String = "default",
    val touch_boost: String = "default",        // boost touchscreen responsiveness
    val haptic_feedback: String = "default",    // reduce haptics for better perf
    val kill_bg_apps: String = "default",       // aggressively kill background apps
    val force_hw_ui: String = "default",        // force hardware UI rendering
    val disable_notifs: String = "default",     // mute notifications during session
    val wifi_no_sleep: String = "default",      // keep WiFi active, no sleep
)

/**
 * How many fields in this config are no longer "default" — i.e. how many
 * App Overrides are actually active. Used by the app list ("Customized (N)"
 * status) and by the per-tab badges in AppSettingsScreen. Keeping this in one
 * place means both call sites agree on what "customized" means as new fields
 * get added to AppConfig later.
 */
fun AppConfig.customizedFieldCount(): Int = listOf(
    perf_lite_mode, app_priority, game_preload, cpu_boost,
    gpu_profile, cpu_governor, gpu_governor, gpu_max_freq,
    refresh_rate, renderer, resolution_downscale,
    dnd_on_gaming, bypass_charging, touch_boost, haptic_feedback,
    kill_bg_apps, force_hw_ui, disable_notifs, wifi_no_sleep
).count { it != "default" }
