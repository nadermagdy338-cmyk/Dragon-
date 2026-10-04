/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

#include <MaxManager.h>
#include <dirent.h>
#include <errno.h>
#include <limits.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#define CPU_POLICY_DIR "/sys/devices/system/cpu/cpufreq"
#define GPU_GOVERNOR_PATH "/sys/class/misc/mali0/device/devfreq/13000000.mali/governor"
#define GPU_GOVERNOR_PATH_ALT "/sys/class/devfreq/13000000.mali/governor"
#define GPU_AVAILABLE_PATH "/sys/class/misc/mali0/device/devfreq/13000000.mali/available_governors"
#define GPU_AVAILABLE_PATH_ALT "/sys/class/devfreq/13000000.mali/available_governors"

/*
 * Value-shape guard — deliberately **not** an allowlist.
 *
 * An allowlist used to sit here (five CPU governors, nine GPU ones) and it was itself the
 * defect. `TECNO POVA 5 Pro` / MT6833GP on the Aetherium kernel advertises exactly
 * `[sugov_ext, reflex, conservative, powersave, performance, schedhorizon, schedutil]` in
 * `scaling_available_governors`; the app offers the user precisely that set; and this list
 * rejected `reflex` and `schedhorizon` with `reason=unsafe_value`, so nothing was ever
 * written and the per-app governor "did not change when selected". Two halves of the same
 * product also disagreed: the profiles binary (`binprofiles/src/utils/mod.rs`) never
 * allowlists — it reads the device and applies what the node reports.
 *
 * The authority on what a node accepts is the node. Every request is preflighted against
 * that policy's `scaling_available_governors` (CPU) or `available_governors` (GPU) before a
 * single byte is written, and the kernel rejects an invalid value itself. What the kernel
 * cannot tell us is whether the *string* is one governor token rather than a path, a flag,
 * or a newline smuggled in from a settings file — that, and only that, is this guard's job.
 */
static bool is_safe_governor_token(const char* governor) {
    if (!governor || !*governor)
        return false;

    /* GameConfig stores cpu_governor/gpu_governor in char[32]; a token that cannot fit
     * there is not a governor this daemon read from its own config. */
    if (strlen(governor) >= 32)
        return false;

    for (const char* p = governor; *p; ++p) {
        const char c = *p;
        const bool accepted = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                              (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
        if (!accepted)
            return false;
    }
    return true;
}

/*
 * `IS_DEFAULT()` recognises NULL and the literal "default" only, so an explicitly empty
 * field (`"cpu_governor": ""` — which extract_string_value() does produce for an empty
 * quoted value) reads to it as *configured*. Nothing is written when there is nothing but
 * emptiness to write, so emptiness is excluded here too.
 */
static bool per_app_value_set(const char* value) {
    return value && value[0] != '\0' && strcmp(value, "default") != 0;
}

static bool read_trimmed_file(const char* path, char* out, size_t out_size) {
    if (!path || !out || out_size < 2)
        return false;

    FILE* fp = fopen(path, "r");
    if (!fp)
        return false;

    if (!fgets(out, (int)out_size, fp)) {
        fclose(fp);
        out[0] = '\0';
        return false;
    }
    fclose(fp);

    out[strcspn(out, "\r\n")] = '\0';
    return true;
}

static bool available_governor(const char* path, const char* governor) {
    char buffer[512] = {0};
    if (!read_trimmed_file(path, buffer, sizeof(buffer)))
        return false;

    for (char* token = strtok(buffer, " \t\r\n");
         token != NULL;
         token = strtok(NULL, " \t\r\n")) {
        if (strcmp(token, governor) == 0)
            return true;
    }
    return false;
}

static bool write_verified_file(const char* path, const char* value) {
    if (!path || !value || !*value)
        return false;

    FILE* fp = fopen(path, "w");
    if (!fp)
        return false;

    const int written = fprintf(fp, "%s\n", value);
    const int flushed = fflush(fp);
    const int close_result = fclose(fp);
    if (written <= 0 || flushed != 0 || close_result != 0)
        return false;

    char actual[128] = {0};
    return read_trimmed_file(path, actual, sizeof(actual)) && strcmp(actual, value) == 0;
}

static int policy_id_from_name(const char* name) {
    if (!name || strncmp(name, "policy", 6) != 0 || name[6] == '\0')
        return -1;

    char* end = NULL;
    errno = 0;
    const long value = strtol(name + 6, &end, 10);
    if (errno != 0 || end == name + 6 || *end != '\0' || value < 0 || value > INT_MAX)
        return -1;
    return (int)value;
}

static size_t collect_cpu_policies(int* ids, size_t capacity) {
    if (!ids || capacity == 0)
        return 0;

    DIR* dir = opendir(CPU_POLICY_DIR);
    if (!dir)
        return 0;

    size_t count = 0;
    struct dirent* entry;
    while ((entry = readdir(dir)) != NULL && count < capacity) {
        const int id = policy_id_from_name(entry->d_name);
        if (id < 0)
            continue;

        char governor_path[512];
        snprintf(governor_path, sizeof(governor_path),
                 "%s/%s/scaling_governor", CPU_POLICY_DIR, entry->d_name);
        if (access(governor_path, R_OK | W_OK) != 0)
            continue;

        ids[count++] = id;
    }
    closedir(dir);
    return count;
}

static bool apply_cpu_governor(const char* governor, const char* package) {
    if (!governor || strcmp(governor, "default") == 0)
        return true;
    if (!is_safe_governor_token(governor)) {
        log_zenith(LOG_WARN,
                   "EVENT=PERAPP_GOVERNOR_REJECTED knob=cpu_governor pkg=%s requested=%s reason=unsafe_value",
                   package ? package : "unknown", governor);
        return false;
    }

    int policies[32] = {0};
    const size_t count = collect_cpu_policies(policies, sizeof(policies) / sizeof(policies[0]));
    if (count == 0) {
        log_zenith(LOG_WARN,
                   "EVENT=PERAPP_GOVERNOR_APPLY_FAILED knob=cpu_governor pkg=%s requested=%s reason=no_cpu_policies",
                   package ? package : "unknown", governor);
        return false;
    }

    bool already_applied = true;
    for (size_t i = 0; i < count; ++i) {
        char live_path[512];
        snprintf(live_path, sizeof(live_path),
                 "%s/policy%d/scaling_governor", CPU_POLICY_DIR, policies[i]);
        char live[64] = {0};
        if (!read_trimmed_file(live_path, live, sizeof(live)) || strcmp(live, governor) != 0) {
            already_applied = false;
            break;
        }
    }
    if (already_applied)
        return true;

    /* Preflight every policy before writing any of them. This avoids a mixed-governor
     * state when one policy does not actually expose the requested governor. */
    for (size_t i = 0; i < count; ++i) {
        char available_path[512];
        snprintf(available_path, sizeof(available_path),
                 "%s/policy%d/scaling_available_governors", CPU_POLICY_DIR, policies[i]);
        if (!available_governor(available_path, governor)) {
            log_zenith(LOG_WARN,
                       "EVENT=PERAPP_GOVERNOR_UNSUPPORTED knob=cpu_governor pkg=%s requested=%s policy=%d",
                       package ? package : "unknown", governor, policies[i]);
            return false;
        }
    }

    bool ok = true;
    for (size_t i = 0; i < count; ++i) {
        char path[512];
        snprintf(path, sizeof(path),
                 "%s/policy%d/scaling_governor", CPU_POLICY_DIR, policies[i]);
        if (!write_verified_file(path, governor))
            ok = false;
    }

    if (!ok) {
        log_zenith(LOG_WARN,
                   "EVENT=PERAPP_GOVERNOR_APPLY_FAILED knob=cpu_governor pkg=%s requested=%s reason=write_or_verify",
                   package ? package : "unknown", governor);
        return false;
    }

    log_zenith(LOG_INFO,
               "EVENT=PERAPP_GOVERNOR_APPLIED knob=cpu_governor pkg=%s requested=%s policies=%zu",
               package ? package : "unknown", governor, count);
    return true;
}

static const char* gpu_governor_path(void) {
    if (access(GPU_GOVERNOR_PATH, R_OK | W_OK) == 0)
        return GPU_GOVERNOR_PATH;
    if (access(GPU_GOVERNOR_PATH_ALT, R_OK | W_OK) == 0)
        return GPU_GOVERNOR_PATH_ALT;
    return NULL;
}

static const char* gpu_available_path(void) {
    if (access(GPU_AVAILABLE_PATH, R_OK) == 0)
        return GPU_AVAILABLE_PATH;
    if (access(GPU_AVAILABLE_PATH_ALT, R_OK) == 0)
        return GPU_AVAILABLE_PATH_ALT;
    return NULL;
}

static bool apply_gpu_governor(const char* governor, const char* package) {
    if (!governor || strcmp(governor, "default") == 0)
        return true;
    if (!is_safe_governor_token(governor)) {
        log_zenith(LOG_WARN,
                   "EVENT=PERAPP_GOVERNOR_REJECTED knob=gpu_governor pkg=%s requested=%s reason=unsafe_value",
                   package ? package : "unknown", governor);
        return false;
    }

    const char* path = gpu_governor_path();
    const char* available = gpu_available_path();
    if (!path || !available) {
        log_zenith(LOG_WARN,
                   "EVENT=PERAPP_GOVERNOR_APPLY_FAILED knob=gpu_governor pkg=%s requested=%s reason=gpu_node_missing",
                   package ? package : "unknown", governor);
        return false;
    }

    char live[128] = {0};
    if (read_trimmed_file(path, live, sizeof(live)) && strcmp(live, governor) == 0)
        return true;

    if (!available_governor(available, governor)) {
        log_zenith(LOG_WARN,
                   "EVENT=PERAPP_GOVERNOR_UNSUPPORTED knob=gpu_governor pkg=%s requested=%s reason=not_reported_by_driver",
                   package ? package : "unknown", governor);
        return false;
    }

    if (!write_verified_file(path, governor)) {
        char live[128] = {0};
        (void)read_trimmed_file(path, live, sizeof(live));
        log_zenith(LOG_WARN,
                   "EVENT=PERAPP_GOVERNOR_APPLY_FAILED knob=gpu_governor pkg=%s requested=%s live=%s reason=write_or_verify",
                   package ? package : "unknown", governor, live[0] ? live : "unknown");
        return false;
    }

    log_zenith(LOG_INFO,
               "EVENT=PERAPP_GOVERNOR_APPLIED knob=gpu_governor pkg=%s requested=%s path=%s",
               package ? package : "unknown", governor, path);
    return true;
}

/*
 * The daemon is the single owner of the per-app governor, and this is the function that
 * writes it. It is a no-op unless the foregrounded app actually configures one.
 *
 * Wiring note (the other half of the bug this function was born into): while an app with an
 * override is in front, `apply_performance_profile()` / `apply_eco_profile()` /
 * `apply_balanced_profile()` / `run_profiler()` all publish
 * `sys.maxmanager.perapp.governor_isolation=1`, which tells the profiles binary to stand
 * down. If nobody calls *this* function, both sides stand down and the node keeps whatever
 * the kernel last chose — silence, not failure. Callers: System.c, at the single point where
 * per-app ownership is (re)established while a tracked app is in the foreground.
 */
void enforce_per_app_governors(const GameConfig* options, const char* package) {
    if (!options || !package || !*package)
        return;

    if (!per_app_value_set(options->cpu_governor) && !per_app_value_set(options->gpu_governor))
        return;

    if (per_app_value_set(options->cpu_governor))
        (void)apply_cpu_governor(options->cpu_governor, package);

    if (per_app_value_set(options->gpu_governor))
        (void)apply_gpu_governor(options->gpu_governor, package);
}
