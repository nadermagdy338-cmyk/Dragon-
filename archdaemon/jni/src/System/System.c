/*
 * Copyright (C) 2026-2027 Zexshia
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

/*
 * Best-effort native cleanup for MediaTek's authoritative GPU OPP lock.
 * The Kotlin companion normally releases this through MtkUtils, but the
 * native daemon can outlive a crashed/force-killed companion. Never leave
 * fix_target_opp_index pinned across daemon shutdown.
 */
static void release_native_mtk_gpu_lock(void) {
    const char* paths[] = {
        "/proc/gpufreqv2/fix_target_opp_index",
        "/proc/gpufreq/gpufreq_opp_freq",
    };

    for (size_t i = 0; i < sizeof(paths) / sizeof(paths[0]); ++i) {
        if (access(paths[i], F_OK) != 0)
            continue;

        FILE* fp = fopen(paths[i], "w");
        if (!fp)
            continue;

        if (fprintf(fp, "-1\\n") > 0 && fflush(fp) == 0) {
            fclose(fp);
            log_zenith(LOG_INFO, "EVENT=GPU_OPP_LOCK_RELEASED path=%s value=-1", paths[i]);
        } else {
            fclose(fp);
            log_zenith(LOG_WARN, "EVENT=GPU_OPP_LOCK_RELEASE_FAILED path=%s", paths[i]);
        }
    }
}

static void cleanup_runtime_state(DaemonContext* ctx, const char* reason) {
    if (ctx && gamestart && ctx->resolution_applied) {
        restore_resolution_target(ctx, gamestart);
        ctx->resolution_applied = false;
    }

    release_per_app_thermal_policy();
    release_native_mtk_gpu_lock();
    __system_property_set("sys.maxmanager.perapp.governor_isolation", "0");

    if (reason) {
        log_zenith(LOG_INFO, "EVENT=RUNTIME_CLEANUP reason=%s", reason);
    }
}

int main_daemon(void) {
    verify_system_integrity();

    if (daemon(0, 0)) {
        log_zenith(LOG_FATAL, "EVENT=DAEMON_START_FAILED reason=daemonize_failed errno=%d", errno);
        __system_property_set("persist.sys.maxmanager.service", "");
        __system_property_set("persist.sys.maxmanager.state", "stopped");
        return 1;
    }

    signal(SIGINT, sighandler);
    signal(SIGTERM, sighandler);

    DaemonContext ctx;
    init_daemon_context(&ctx);
    restore_daemon_state(&ctx);
    // Recover a thermal ownership transaction left incomplete by a previous
    // daemon crash/reboot before the Java companion starts issuing profiles.
    recover_per_app_thermal_policy();
    wait_for_java_companion(&ctx);

    if (pipe(java_lock_pipe) != 0) {
        log_zenith(LOG_ERROR, "EVENT=JAVA_LOCK_PIPE_FAILED reason=pipe_syscall_failed errno=%d", errno);
    } else {
        pthread_t lock_thread;
        pthread_attr_t attr;
        pthread_attr_init(&attr);
        pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
        pthread_create(&lock_thread, &attr, java_lock_watcher_thread, (void*)ctx.java_lock_path);
        pthread_attr_destroy(&attr);
    }

    log_zenith(LOG_INFO, "EVENT=APPLIST_READ_START");
    read_app_status(&current_system_cache);
    reload_gamelist_cache(&ctx);
    log_zenith(LOG_INFO, "EVENT=APPLIST_READ_SUCCESS");

    // Initiate PID
    log_zenith(LOG_INFO, "EVENT=DAEMON_STARTED pid=%d", getpid());
    __system_property_set("persist.sys.rianixia.learning_enabled", "true");
    __system_property_set("persist.sys.maxmanager.state", "running");
    notify("Initializing...", "Starting MaxManager service...", false, 0);
    setspid();

    FILE* fp_ai_init = fopen(DAEMON_MODES, "r");
    if (fp_ai_init) {
        if (fgets(ctx.prev_ai_state, sizeof(ctx.prev_ai_state), fp_ai_init))
            trim_newline(ctx.prev_ai_state);
        fclose(fp_ai_init);
    }
    int inotify_fd = setup_inotify_watchers();
    load_initial_config_files(&ctx);

    checkstate();
    is_kanged();
    check_module_version();
    validateprop();
    log_zenith(LOG_INFO, "EVENT=MODULE_INTEGRITY_PASSED");

    log_zenith(LOG_INFO, "EVENT=DAEMON_READY");
    ctx.need_profile_checkup = true;
    bool need_loop = true;
    runthermalcore();
    run_profiler(PERFCOMMON);

    /* Main Daemon Loop */
    while (1) {
        int poll_timeout = -1;
        if (need_loop) {
            poll_timeout = 0;
        } else if (ctx.grace_period_active) {
            double elapsed = difftime(time(NULL), ctx.screen_off_timer);
            if (elapsed < 10.0)
                poll_timeout = (int)((10.0 - elapsed) * 1000);
            else
                poll_timeout = 0;
        } else if (ctx.fg_away_active) {
            double elapsed = difftime(time(NULL), ctx.fg_away_timer);
            if (elapsed < 30.0)
                poll_timeout = (int)((30.0 - elapsed) * 1000);
            else
                poll_timeout = 0;
        }

        bool should_exit = process_inotify_events(inotify_fd, &ctx, poll_timeout);
        need_loop = false;

        if (java_daemon_died) {
            log_zenith(LOG_FATAL, "EVENT=DAEMON_STOPPED reason=java_companion_lock_released");
            notify("Daemon Error", "Java companion daemon crashed. Stopping MaxManager.", false, 0);
            cleanup_runtime_state(&ctx, "java_companion_exit");
            __system_property_set("persist.sys.maxmanager.service", "");
            __system_property_set("persist.sys.maxmanager.state", "stopped");
            break;
        }

        if (should_exit)
            break;

        int real_screen_state = get_screenstate(&current_system_cache);

        if (strcmp(ctx.config_freqoffset, "Disabled") == 0) {
            if (strcmp(ctx.last_freqoffset, "Disabled") != 0) {
                systemv("sys.maxmanager-profilesettings applyfreqbalance");
            }
        } else if (real_screen_state && (ctx.cur_mode == BALANCED_PROFILE || ctx.cur_mode == ECO_MODE)) {
            systemv("sys.maxmanager-profilesettings applyfreqbalance");
        }
        strcpy(ctx.last_freqoffset, ctx.config_freqoffset);

        handle_dynamic_bypass(&ctx);

        if (ctx.is_initialize_complete && strcmp(ctx.prev_ai_state, "0") == 0)
            continue;

        if (ctx.need_profile_checkup) {
            char* current_focused_game = get_gamestart(&opts, &current_system_cache);
            if (current_focused_game) {
                if (gamestart && strcmp(gamestart, current_focused_game) == 0) {
                    free(current_focused_game);
                    ctx.need_profile_checkup = false;
                } else {

                    if (gamestart && ctx.resolution_applied) {
                        restore_resolution_target(&ctx, gamestart);
                    }

                    if (gamestart)
                        free(gamestart);
                    if (active_app_name)
                        free(active_app_name);
                    gamestart = current_focused_game;
                    active_app_name = strdup(current_system_cache.app_name);
                    strncpy(ctx.cur_switch_id, current_system_cache.switch_id, sizeof(ctx.cur_switch_id) - 1);
                    log_zenith(LOG_INFO, "EVENT=APP_SWITCH pkg=%s sw=%s", active_app_name ? active_app_name : gamestart,
                               ctx.cur_switch_id[0] ? ctx.cur_switch_id : "unknown");
                    game_pid_count = 0;
                    ctx.pid_retries = 0;
                    ctx.has_applied_renderer = false;
                    ctx.need_profile_checkup = true;
                }
            } else {
                // The visible package is no longer configured for Per-App management.
                // Drop the native target immediately; otherwise the previous package can
                // remain in gamestart and keep renderer/thermal/profile state alive.
                update_per_app_thermal_policy(NULL, NULL);
                __system_property_set("sys.maxmanager.perapp.governor_isolation", "0");
                if (gamestart) {
                    if (ctx.resolution_applied)
                        restore_resolution_target(&ctx, gamestart);
                    free(gamestart);
                    gamestart = NULL;
                }
                if (active_app_name) {
                    free(active_app_name);
                    active_app_name = NULL;
                }
                ctx.has_applied_renderer = false;
                ctx.pid_retries = 0;
                game_pid_count = 0;
                if (ctx.cur_mode != BALANCED_PROFILE && ctx.cur_mode != ECO_MODE)
                    ctx.need_profile_checkup = false;
            }
        }

        int effective_screen_state = real_screen_state;
        if (real_screen_state != ctx.prev_screen_state) {
            if (real_screen_state == 0) {
                if (ctx.cur_mode == PERFORMANCE_PROFILE) {
                    ctx.screen_off_timer = time(NULL);
                    ctx.grace_period_active = true;
                    log_zenith(LOG_INFO, "EVENT=GRACE_PERIOD_START duration_s=10 reason=screen_off");
                }
            } else {
                if (ctx.grace_period_active) {
                    log_zenith(LOG_INFO, "EVENT=GRACE_PERIOD_ABORTED reason=screen_on action=keep_performance");
                    ctx.grace_period_active = false;
                }
                ctx.screen_off_timer = 0;
            }
            ctx.prev_screen_state = real_screen_state;
        }

        if (ctx.grace_period_active) {
            if (difftime(time(NULL), ctx.screen_off_timer) < 10.0)
                effective_screen_state = 1;
            else {
                log_zenith(LOG_INFO, "EVENT=GRACE_PERIOD_EXPIRED action=drop_performance_profile");
                ctx.grace_period_active = false;
                ctx.screen_off_timer = 0;
                effective_screen_state = 0;
                ctx.need_profile_checkup = true;
            }
        }
        
        // Keep Xiaomi thermal ownership aligned with the exact runtime state.
        // Ownership is established before apply_performance_profile() below,
        // and is released as soon as the screen is effectively off.
        if (gamestart && effective_screen_state) {
            update_per_app_thermal_policy(&opts, gamestart);
            // The per-app governor rides the same ownership point, and for the same reason
            // the thermal policy does: while an app with a CPU/GPU override is in front,
            // every profile path publishes sys.maxmanager.perapp.governor_isolation=1, which
            // makes the profiles binary skip its own governor write. So the daemon must be the
            // one that writes the node -- without this call both sides stand down and the
            // governor silently keeps whatever the kernel chose, which is exactly the
            // "governor does not change when selected" report. `opts` was just refilled for
            // this package by get_gamestart() above, and the function is a no-op unless the app
            // has an explicit governor. See PerAppKernel.c.
            enforce_per_app_governors(&opts, gamestart);
        } else {
            update_per_app_thermal_policy(NULL, NULL);
        }

        if (gamestart && ctx.cur_mode == PERFORMANCE_PROFILE) {
            char dropfg_val[PROP_VALUE_MAX] = {0};
            bool dropforeground_enabled = (__system_property_get("persist.sys.maxmanager.dropforeground", dropfg_val) > 0 &&
                                            dropfg_val[0] == '1');
        
            if (dropforeground_enabled) {
                bool is_focused = (strcmp(current_system_cache.focused_app, gamestart) == 0);
                if (!is_focused) {
                    if (!ctx.fg_away_active) {
                        ctx.fg_away_timer = time(NULL);
                        ctx.fg_away_active = true;
                        
                    } else if (difftime(time(NULL), ctx.fg_away_timer) >= 30.0) {
                        log_zenith(LOG_INFO, "EVENT=FOREGROUND_LOST_TIMEOUT pkg=%s away_s=30 action=drop_to_balanced",
                                   active_app_name ? active_app_name : gamestart);
                        ctx.fg_away_active = false;
                        ctx.fg_away_timer = 0;
                        restore_resolution_target(&ctx, gamestart);
                        free(gamestart);
                        gamestart = NULL;
                        if (active_app_name) {
                            free(active_app_name);
                            active_app_name = NULL;
                        }
                        ctx.need_profile_checkup = true;
                    }
                } else if (ctx.fg_away_active) {
                    ctx.fg_away_active = false;
                    ctx.fg_away_timer = 0;
                }
            } else if (ctx.fg_away_active) {
                ctx.fg_away_active = false;
                ctx.fg_away_timer = 0;
            }
        }

        if (ctx.is_initialize_complete && ctx.cur_mode != PERFORMANCE_PROFILE && gamestart == NULL && !ctx.need_profile_checkup) {
            
            bool is_low_power = get_low_power_state(&current_system_cache);
            
            if (is_low_power && ctx.cur_mode != ECO_MODE) {
                goto apply_mode_eco;
            }
            
            if (!is_low_power && ctx.cur_mode == ECO_MODE) {
                goto apply_mode_balanced;
            }

            continue;
        }

        if (ctx.is_initialize_complete && gamestart && effective_screen_state) {
            if (!ctx.need_profile_checkup && ctx.cur_mode == PERFORMANCE_PROFILE && ctx.has_applied_renderer && game_pid_count > 0)
                continue;

            if (!ctx.has_applied_renderer) {
                bool renderer_changed = false;
                if (!IS_DEFAULT(opts.renderer)) {
                    renderer_changed = apply_smart_renderer(opts.renderer, ctx.saved_renderer, ctx.saved_sys_renderer);
                }

                bool reso_changed = false;
                if (!IS_DEFAULT(opts.resolution_downscale)) {
                    reso_changed = apply_resolution_target(&ctx, gamestart,
                                                            opts.resolution_downscale);
                }

                if (renderer_changed || reso_changed) {
                    restart_target_app(gamestart);
                    game_pid_count = 0;
                    ctx.pid_retries = 0;
                }

                ctx.has_applied_renderer = true;
            }

            if (game_pid_count == 0) [[clang::unlikely]] {
                if (strcmp(current_system_cache.focused_app, gamestart) == 0) {
                    game_pid_count = get_pids_of(gamestart, game_pids, MAX_GAME_PIDS);
                }
                if (game_pid_count == 0) {
                    if (ctx.pid_retries < 5) {
                        ctx.pid_retries++;
                        log_zenith(LOG_WARN, "EVENT=PID_SPAWN_WAIT pkg=%s retry=%d max_retries=5", active_app_name ? active_app_name : gamestart,
                                   ctx.pid_retries);
                        usleep(200000);
                        need_loop = true;
                        continue;
                    } else {
                        log_zenith(LOG_ERROR, "EVENT=PID_FETCH_GAVE_UP pkg=%s retries=5",
                                   active_app_name ? active_app_name : gamestart);
                        free(gamestart);
                        gamestart = NULL;
                        if (active_app_name) {
                            free(active_app_name);
                            active_app_name = NULL;
                        }
                        ctx.pid_retries = 0;
                        ctx.need_profile_checkup = true;
                        continue;
                    }
                }
                ctx.pid_retries = 0;
                for (int i = 0; i < game_pid_count; i++) {
                    if (IS_TRUE(opts.app_priority))
                        set_priority(game_pids[i]);
                    else if (!IS_FALSE(opts.app_priority)) {
                        char val[PROP_VALUE_MAX] = {0};
                        if (__system_property_get("persist.sys.maxmanagerconf.iosched", val) > 0 && val[0] == '1')
                            set_priority(game_pids[i]);
                    }
                }
            }
            apply_performance_profile(&ctx);
        } else if (ctx.is_initialize_complete && get_low_power_state(&current_system_cache)) {
            apply_mode_eco:
                apply_eco_profile(&ctx);
        } else {
            apply_mode_balanced:
                apply_balanced_profile(&ctx);
        }
    }

    cleanup_runtime_state(&ctx, java_daemon_died ? "java_companion_exit" : "daemon_loop_exit");
    if (inotify_fd >= 0)
        close(inotify_fd);
    return 0;
}
