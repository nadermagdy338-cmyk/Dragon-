/*
 * Copyright (C) 2024-2025 Zexshia
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

#include "MaxManager.h"

/**
 * @brief Thread worker function to run GamePreload asynchronously.
 * @param arg Pointer to PreloadArgs structure.
 * @return NULL
 */
void* async_preload_worker(void* arg) {
    PreloadArgs* args = (PreloadArgs*)arg;
    GamePreload(args->package);
    free(args);
    return NULL;
}

/**
 * @brief Applies system tuning parameters specifically for Performance Mode.
 * @param ctx Pointer to DaemonContext structure.
 */
void apply_performance_profile(DaemonContext* ctx) {
    toast("Applying Performance Profile");

    ctx->cur_mode = PERFORMANCE_PROFILE;
    ctx->need_profile_checkup = false;

    notify("Performance Profile", "Running at %s", false, 0, active_app_name ? active_app_name : gamestart);
    log_zenith(LOG_INFO, "EVENT=PROFILE_APPLY profile=PERFORMANCE target=%s sw=%s",
               active_app_name ? active_app_name : gamestart, ctx->cur_switch_id[0] ? ctx->cur_switch_id : "unknown");

    if (IS_TRUE(opts.perf_lite_mode)) {
        __system_property_set("persist.sys.maxmanagerconf.litemode", "1");
    } else if (IS_FALSE(opts.perf_lite_mode)) {
        __system_property_set("persist.sys.maxmanagerconf.litemode", "0");
    } else {
        char lite_prop[PROP_VALUE_MAX] = {0};
        __system_property_get("persist.sys.maxmanagerconf.cpulimit", lite_prop);
        __system_property_set("persist.sys.maxmanagerconf.litemode", (strcmp(lite_prop, "1") == 0) ? "1" : "0");
    }

    if (ctx->saved_zen_mode < 0) {
        ctx->saved_zen_mode = current_system_cache.zen_mode;
    }

    if (IS_TRUE(opts.dnd_on_gaming)) {
        if (ctx->saved_zen_mode == 0) {
            systemv("sys.maxmanager-utilityconf enableDND");
        }
        ctx->dnd_enabled = true;
    } else if (!IS_FALSE(opts.dnd_on_gaming)) {
        char dnd_state[PROP_VALUE_MAX] = {0};
        __system_property_get("persist.sys.maxmanagerconf.dnd", dnd_state);
        if (strcmp(dnd_state, "1") == 0) {
            if (ctx->saved_zen_mode == 0) {
                systemv("sys.maxmanager-utilityconf enableDND");
            }
            ctx->dnd_enabled = true;
        }
    }

    // Governor ownership is established before ANY global profile writes. This
    // is deliberately done for Performance, Balanced and Eco: all three profiles
    // have their own governor paths and any one of them can otherwise overwrite
    // an explicit per-app governor.
    bool per_app_governor_override =
        gamestart != NULL &&
        (!IS_DEFAULT(opts.gpu_profile) || !IS_DEFAULT(opts.thermal_profile) ||
         !IS_DEFAULT(opts.cpu_governor) || !IS_DEFAULT(opts.gpu_governor) ||
         !IS_DEFAULT(opts.gpu_max_freq) || opts.cpu_policy_controls[0] != '\0');
    __system_property_set("sys.maxmanager.perapp.governor_isolation",
                          per_app_governor_override ? "1" : "0");

    // Per-App Settings (Kotlin side) now owns GPU/CPU governor and frequency for any app
    // that has an explicit override configured -- previously run_profiler() below forced
    // max GPU frequency / a "performance" governor on *every* tracked app unconditionally,
    // which silently fought and overrode whatever was chosen per-app (Default, Balanced,
    // Gaming, an explicit governor, or a fixed frequency all looked like "nothing happens"
    // from the per-app screen, because this global forcing ran again on every poll and won).
    // Apps that don't use the newer per-app GPU/CPU controls keep the previous behavior.
    bool has_per_app_gpu_override =
        !IS_DEFAULT(opts.gpu_profile) || !IS_DEFAULT(opts.thermal_profile) ||
        !IS_DEFAULT(opts.cpu_governor) ||
        !IS_DEFAULT(opts.gpu_governor) || !IS_DEFAULT(opts.gpu_max_freq) ||
        opts.cpu_policy_controls[0] != '\0';

    if (!has_per_app_gpu_override) {
        EXECUTE("Performance Profile", run_profiler(PERFORMANCE_PROFILE));
    } else {
        log_zenith(LOG_INFO, "EVENT=PROFILE_SKIP_GPU_FORCE reason=per_app_override_active target=%s sw=%s",
                   active_app_name ? active_app_name : gamestart, ctx->cur_switch_id[0] ? ctx->cur_switch_id : "unknown");
    }

    bool is_preload_active = false;
    if (!IS_FALSE(opts.game_preload)) {
        char preload_active[PROP_VALUE_MAX] = {0};
        if (__system_property_get("persist.sys.maxmanagerconf.APreload", preload_active) > 0) {
            is_preload_active = (strcmp(preload_active, "1") == 0);
        }
    }

    if (IS_TRUE(opts.game_preload) || is_preload_active) {
        notify("MaxManager Preload", "Preloading initiated for: %s", true, 10000, active_app_name ? active_app_name : gamestart);

        PreloadArgs* p_args = malloc(sizeof(PreloadArgs));
        if (p_args) {
            strncpy(p_args->package, gamestart, sizeof(p_args->package) - 1);
            p_args->package[sizeof(p_args->package) - 1] = '\0';

            pthread_t preload_thread;
            pthread_attr_t attr;
            pthread_attr_init(&attr);
            pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);

            if (pthread_create(&preload_thread, &attr, async_preload_worker, p_args) != 0) {
                log_zenith(LOG_ERROR, "EVENT=PRELOAD_SPAWN_FAILED reason=pthread_create_failed target=%s", gamestart);
                free(p_args);
            }
            pthread_attr_destroy(&attr);
        } else {
            log_zenith(LOG_ERROR, "EVENT=PRELOAD_SPAWN_FAILED reason=malloc_failed target=%s", gamestart);
        }
    }
    
    save_daemon_state(ctx);
    
}

/**
 * @brief Reverts system to Endurance state (Eco Mode).
 * @param ctx Pointer to DaemonContext structure.
 */
void apply_eco_profile(DaemonContext* ctx) {

    // Preserve explicit Per-App CPU/GPU governors while this global profile is applied.
    bool per_app_governor_override =
        gamestart != NULL &&
        (!IS_DEFAULT(opts.gpu_profile) || !IS_DEFAULT(opts.thermal_profile) ||
         !IS_DEFAULT(opts.cpu_governor) || !IS_DEFAULT(opts.gpu_governor) ||
         !IS_DEFAULT(opts.gpu_max_freq) || opts.cpu_policy_controls[0] != '\0');
    __system_property_set("sys.maxmanager.perapp.governor_isolation",
                          per_app_governor_override ? "1" : "0");
    if (ctx->cur_mode == ECO_MODE)
        return;

    toast("Applying Eco Mode");

    ctx->cur_mode = ECO_MODE;
    ctx->need_profile_checkup = false;

    notify("ECO Mode", "System is now at Endurance state", false, 0);
    log_zenith(LOG_INFO, "EVENT=PROFILE_APPLY profile=ECO sw=%s", ctx->cur_switch_id[0] ? ctx->cur_switch_id : "unknown");

    if (ctx->dnd_enabled) {
        if (ctx->saved_zen_mode == 0) {
            systemv("sys.maxmanager-utilityconf disableDND");
        }
        ctx->dnd_enabled = false;
    }
    ctx->saved_zen_mode = -1;

    if (strlen(ctx->saved_renderer) > 0) {
        char current_now[PROP_VALUE_MAX] = {0};
        __system_property_get("debug.hwui.renderer", current_now);

        if (strlen(current_now) == 0)
            strcpy(current_now, "default");

        if (strcmp(current_now, ctx->saved_renderer) != 0) {
            log_zenith(LOG_INFO, "EVENT=RENDERER_RESTORE profile=ECO renderer=%s sw=%s", ctx->saved_renderer,
                       ctx->cur_switch_id[0] ? ctx->cur_switch_id : "unknown");
            if (strcmp(ctx->saved_renderer, "default") == 0) {
                systemv("sys.maxmanager-utilityconf setrender default");
                __system_property_set("persist.sys.maxmanagerconf.renderer", "default");
            } else {
                systemv("sys.maxmanager-utilityconf setrender %s", ctx->saved_renderer);
                __system_property_set("persist.sys.maxmanagerconf.renderer", ctx->saved_sys_renderer);
            }
        }
        memset(ctx->saved_renderer, 0, sizeof(ctx->saved_renderer));
        memset(ctx->saved_sys_renderer, 0, sizeof(ctx->saved_sys_renderer));
    }
    
    EXECUTE("ECO Mode", run_profiler(ECO_MODE));
    
    systemv("rm -rf /data/adb/.config/MaxManager/daemon_state");

}

/**
 * @brief Reverts system to Optimal state (Balanced Mode).
 * @param ctx Pointer to DaemonContext structure.
 */
void apply_balanced_profile(DaemonContext* ctx) {

    // Preserve explicit Per-App CPU/GPU governors while this global profile is applied.
    bool per_app_governor_override =
        gamestart != NULL &&
        (!IS_DEFAULT(opts.gpu_profile) || !IS_DEFAULT(opts.thermal_profile) ||
         !IS_DEFAULT(opts.cpu_governor) || !IS_DEFAULT(opts.gpu_governor) ||
         !IS_DEFAULT(opts.gpu_max_freq) || opts.cpu_policy_controls[0] != '\0');
    __system_property_set("sys.maxmanager.perapp.governor_isolation",
                          per_app_governor_override ? "1" : "0");
    if (ctx->is_initialize_complete && ctx->cur_mode == BALANCED_PROFILE)
        return;

    toast("Applying Balanced Profile");

    ctx->cur_mode = BALANCED_PROFILE;
    ctx->need_profile_checkup = false;

    notify("Balanced Profile", "System is now at Optimal state", false, 0);
    log_zenith(LOG_INFO, "EVENT=PROFILE_APPLY profile=BALANCED sw=%s", ctx->cur_switch_id[0] ? ctx->cur_switch_id : "unknown");

    if (ctx->dnd_enabled) {
        if (ctx->saved_zen_mode == 0) {
            systemv("sys.maxmanager-utilityconf disableDND");
        }
        ctx->dnd_enabled = false;
    }
    ctx->saved_zen_mode = -1;

    if (strlen(ctx->saved_renderer) > 0) {
        char current_now[PROP_VALUE_MAX] = {0};
        __system_property_get("debug.hwui.renderer", current_now);

        if (strlen(current_now) == 0)
            strcpy(current_now, "default");

        if (strcmp(current_now, ctx->saved_renderer) != 0) {
            log_zenith(LOG_INFO, "EVENT=RENDERER_RESTORE profile=BALANCED renderer=%s sw=%s", ctx->saved_renderer,
                       ctx->cur_switch_id[0] ? ctx->cur_switch_id : "unknown");
            if (strcmp(ctx->saved_renderer, "default") == 0) {
                systemv("sys.maxmanager-utilityconf setrender default");
                __system_property_set("persist.sys.maxmanagerconf.renderer", "default");
            } else {
                systemv("sys.maxmanager-utilityconf setrender %s", ctx->saved_renderer);
                __system_property_set("persist.sys.maxmanagerconf.renderer", ctx->saved_sys_renderer);
            }
        }
        memset(ctx->saved_renderer, 0, sizeof(ctx->saved_renderer));
        memset(ctx->saved_sys_renderer, 0, sizeof(ctx->saved_sys_renderer));
    }

    EXECUTE("Balanced Profile", run_profiler(BALANCED_PROFILE));

    if (!ctx->is_initialize_complete) {
        notify("Daemon Info", "MaxManager is running successfully", false, 60000);
        ctx->is_initialize_complete = true;
    }
    
    systemv("rm -rf /data/adb/.config/MaxManager/daemon_state");
    
}
