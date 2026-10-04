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

#include <MaxManager.h>
#include <sys/system_properties.h>

/**
 * @brief Handles performance profile selection via CLI argument.
 * @note Blocks MANUAL selection if AI/Auto Mode is active: while MAX AI
 *       manages performance, manual picks are captured as pending changes
 *       by the app instead of executing. The MAX AI engine itself is the
 *       authorized actor: it passes "--from-ai" to apply its own decisions
 *       (and Safety Engine enforced levels) through this same path.
 * @param argc Number of CLI arguments.
 * @param argv Array of CLI argument strings.
 * @return 0 on success, or 1 if an invalid profile is requested or a
 *         manual selection arrives while Auto Mode is enabled.
 */
int handle_profile(int argc, char** argv) {
    if (argc < 3 || !argv[2] || !argv[2][0]) {
        fprintf(stderr, "ERROR: Missing profile number. Use --profile <1|2|3>\n");
        return 1;
    }

    // "--from-ai" marks the caller as the MAX AI engine itself (or its
    // Safety Engine). Without it, the call is a manual selection.
    int from_ai = 0;
    for (int i = 3; i < argc; i++) {
        if (argv[i] && strcmp(argv[i], "--from-ai") == 0) {
            from_ai = 1;
            break;
        }
    }

    char ai_state[PROP_VALUE_MAX] = {0};
    __system_property_get("persist.sys.maxmanagerconf.AIenabled", ai_state);

    if (!from_ai && !strcmp(ai_state, "1")) {
        log_zenith(LOG_INFO, "EVENT=CLI_PROFILE_MANUAL_BLOCKED ai=1");
        fprintf(stderr, "ERROR: Auto Mode is enabled.\n"
                        "       Manual profile selection is blocked.\n");
        return 1;
    }

    const char* profile = argv[2];

    if (!strcmp(profile, "0")) {
        log_zenith(LOG_WARN, "EVENT=CLI_PROFILE_APPLY_REJECTED reason=profile_zero_is_initialize_only");
        printf("WARN: Cannot Apply Profile 0 (Initialize)\n");
    } else if (!strcmp(profile, "1")) {
        log_zenith(LOG_INFO, "EVENT=CLI_PROFILE_APPLY profile=PERFORMANCE");
        char lite_prop[PROP_VALUE_MAX] = {0};
        __system_property_get("persist.sys.maxmanagerconf.cpulimit", lite_prop);
        if (strcmp(lite_prop, "1") == 0) {
            __system_property_set("persist.sys.maxmanagerconf.litemode", "1");
        } else {
            __system_property_set("persist.sys.maxmanagerconf.litemode", "0");
        }
        run_profiler(PERFORMANCE_PROFILE);
        notify("Performance Profile", "System is now at Powerful state", false, 0);
        printf("Applying Performance Profile\n");
    } else if (!strcmp(profile, "2")) {
        log_zenith(LOG_INFO, "EVENT=CLI_PROFILE_APPLY profile=BALANCED");
        run_profiler(BALANCED_PROFILE);
        notify("Balanced Profile", "System is now at Optimal state", false, 0);
        printf("Applying Balanced Profile\n");
    } else if (!strcmp(profile, "3")) {
        log_zenith(LOG_INFO, "EVENT=CLI_PROFILE_APPLY profile=ECO");
        run_profiler(ECO_MODE);
        notify("ECO Mode", "System is now at Endurance state", false, 0);
        printf("Applying Eco Mode\n");
    } else {
        fprintf(stderr, "Invalid profiles.\n");
        return 1;
    }

    return 0;
}

/**
 * @brief Validates input log levels and forwards the combined message string to the external
 * standard logger.
 * @param argc Number of CLI arguments.
 * @param argv Array of CLI argument strings.
 * @return 0 on success, or 1 on parameter validation errors.
 */
int handle_log(int argc, char** argv) {
    if (argc < 5) {
        fprintf(stderr, "Usage: --log <TAG> <LEVEL> <MESSAGE>\n"
                        "Levels: 0=DEBUG, 1=INFO, 2=WARN, 3=ERROR, 4=FATAL\n");
        return 1;
    }

    const char* tag = argv[2];
    const char* level_str = argv[3];

    int level = atoi(level_str);
    if (level < LOG_DEBUG || level > LOG_FATAL) {
        fprintf(stderr, "ERROR: Invalid log level '%s' (valid 0..4)\n", level_str);
        return 1;
    }

    char message[1024];
    message[0] = '\0';

    size_t remaining = sizeof(message);
    for (int i = 4; i < argc; i++) {
        size_t written = snprintf(message + strlen(message), remaining, "%s%s", argv[i], (i == argc - 1) ? "" : " ");
        if (written >= remaining) {
            fprintf(stderr, "ERROR: Log message too long.\n");
            return 1;
        }
        remaining -= written;
    }

    external_log(level, tag, "%s", message);
    return 0;
}

/**
 * @brief Validates input log levels and forwards the combined message string to the external
 * verbose logger.
 * @param argc Number of CLI arguments.
 * @param argv Array of CLI argument strings.
 * @return 0 on success, or 1 on parameter validation errors.
 */
int handle_verboselog(int argc, char** argv) {
    if (argc < 5) {
        fprintf(stderr, "Usage: --log <TAG> <LEVEL> <MESSAGE>\n"
                        "Levels: 0=DEBUG, 1=INFO, 2=WARN, 3=ERROR, 4=FATAL\n");
        return 1;
    }

    const char* tag = argv[2];
    const char* level_str = argv[3];

    int level = atoi(level_str);
    if (level < LOG_DEBUG || level > LOG_FATAL) {
        fprintf(stderr, "ERROR: Invalid log level '%s' (valid 0..4)\n", level_str);
        return 1;
    }

    char message[1024];
    message[0] = '\0';

    size_t remaining = sizeof(message);
    for (int i = 4; i < argc; i++) {
        size_t written = snprintf(message + strlen(message), remaining, "%s%s", argv[i], (i == argc - 1) ? "" : " ");
        if (written >= remaining) {
            fprintf(stderr, "ERROR: Log message too long.\n");
            return 1;
        }
        remaining -= written;
    }

    external_vlog(level, tag, "%s", message);
    return 0;
}

/**
 * @brief Prints the current MaxManager module version string to stdout.
 */
void printversion(void) {
    printf("%s\n", MODULE_VERSION);
}

/**
 * @brief Directly launches the primary Android MainActivity of the MaxManager application interface.
 */
int openAppMainActivity(void) {
    return systemv("/system/bin/am start --user 0 -a android.intent.action.MAIN -n nd.max/.MainActivity");
}

/**
 * @brief Restricts execution of specific CLI components if the backend daemon engine is not active.
 * @return 1 if the daemon state check passes, 0 otherwise.
 */
int require_daemon_running(void) {
    if (!check_running_state()) {
        fprintf(stderr, "\033[31mERROR:\033[0m MaxManager daemon is not running.\n"
                        "Run: sys.maxmanager-service --run\n");
        return 0;
    }
    return 1;
}

/**
 * @brief Clears all historical and active log caches from storage nodes and triggers an internal
 * app broadcast reset.
 */
void clearlogs(void) {
    systemv("rm -f /data/adb/.config/MaxManager/debug/MaxManager.log");
    systemv("rm -f /data/adb/.config/MaxManager/debug/MaxManager.log.1");
    systemv("rm -f /data/adb/.config/MaxManager/debug/MaxManagerVerbose.log");
    systemv("rm -f /data/adb/.config/MaxManager/debug/MaxManagerVerbose.log.1");
    systemv("rm -f /data/adb/.config/MaxManager/preload/MaxManagerPR.log");
    systemv("rm -f /data/adb/.config/MaxManager/preload/MaxManagerPR.log.1");
    systemv("su -c \"am broadcast -a nd.max.ACTION_MANAGE -n "
            "nd.max/.receiver.MaxManagerReceiver --ez clearall true >/dev/null "
            "2>&1\"");
}

/**
 * @brief Restarts the MaxManager service daemon by spawning a detached child
 *        process. The caller returns immediately without blocking on the
 *        actual restart sequence.
 * @return 0 if the detach/fork succeeded, 1 if fork failed.
 */
int restart_service(void) {
    if (daemon(0, 0)) {
        log_zenith(LOG_FATAL, "EVENT=SERVICE_RESTART_FAILED reason=daemonize_failed errno=%d", errno);
        return 1;
    }
    system("/data/adb/modules/MaxManager/system/bin/sys.maxmanager-utilityconf restartservice");
    return 0;
}


/**
 * @brief Show notifications based on current profiles
 * app broadcast intent.
 */
void shownotifications(void) {
    FILE *fp = fopen("/data/adb/.config/MaxManager/API/current_profile", "r");
    if (!fp) {
        return;
    }

    int profile = 0;
    if (fscanf(fp, "%d", &profile) != 1) {
        fclose(fp);
        return;
    }
    fclose(fp);

    switch (profile) {
        case 1:
            notify("Performance Profile", "System is now at Powerful state", false, 0);
            break;
        case 2:
            notify("Balanced Profile", "System is now at Optimal state", false, 0);
            break;
        case 3:
            notify("ECO Mode", "System is now at Endurance state", false, 0);
            break;
        default:
            break;
    }
}

/**
 * @brief Show notifications based on current profiles
 * app broadcast intent.
 */
void hidenotifications(void) {
    
    systemv("su -c \"am broadcast -a nd.max.ACTION_MANAGE -n "
            "nd.max/.receiver.MaxManagerReceiver --ez clearall true >/dev/null "
            "2>&1\"");
    
}
