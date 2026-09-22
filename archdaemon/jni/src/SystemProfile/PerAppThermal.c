/*
 * Copyright (C) 2026-2027 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

#include <AZenith.h>
#include <limits.h>

#define MI_THERMAL_SCONFIG "/sys/devices/virtual/thermal/thermal_message/sconfig"
#define MI_THERMAL_NO_LIMITS_MODE 6
#define THERMAL_OWNERSHIP_MARKER "/data/adb/.config/MaxManager/API/perapp_thermal_prev"

/*
 * sconfig=6 is Xiaomi's no-limits policy selector. It is NOT a per-app
 * temperature profile. Per-app CPU/GPU limits are the actual profile; mode 6
 * only prevents mi_thermald from replacing those limits while the override is
 * active.
 *
 * The daemon is the sole writer. The Kotlin companion deliberately does not
 * touch sconfig anymore, and the old xiaomi-extras loop is not launched.
 */
static bool thermal_owned = false;
static int thermal_previous_mode = -1;
static bool unsupported_logged = false;

static bool read_sconfig(int* out) {
    if (!out)
        return false;

    FILE* fp = fopen(MI_THERMAL_SCONFIG, "r");
    if (!fp)
        return false;

    long value = -1;
    const int ok = fscanf(fp, "%ld", &value);
    fclose(fp);

    if (ok != 1 || value < -1 || value > INT_MAX)
        return false;

    /* Xiaomi kernels expose -1 until userspace publishes the first mode. */
    *out = (value == -1) ? 0 : (int)value;
    return true;
}

static bool write_sconfig(int mode) {
    if (mode < 0 || mode > 0x800)
        return false;

    /*
     * Several Xiaomi kernels expose sconfig as 0444 even to uid 0. Match the
     * module's other verified sysfs writers: temporarily make the node
     * writable, write a newline, verify the value, then restore its original
     * mode. A plain fopen() made Thermal & GPU Governor look enabled while
     * every acquire silently failed on those kernels.
     */
    struct stat metadata;
    if (stat(MI_THERMAL_SCONFIG, &metadata) != 0)
        return false;
    const mode_t original_mode = metadata.st_mode & 0777;
    if (chmod(MI_THERMAL_SCONFIG, 0644) != 0)
        return false;

    FILE* fp = fopen(MI_THERMAL_SCONFIG, "w");
    if (!fp) {
        (void)chmod(MI_THERMAL_SCONFIG, original_mode);
        return false;
    }

    const int written = fprintf(fp, "%d\n", mode);
    const int flushed = fflush(fp);
    const int close_result = fclose(fp);
    (void)chmod(MI_THERMAL_SCONFIG, original_mode);
    if (written <= 0 || flushed != 0 || close_result != 0)
        return false;

    int actual = -1;
    return read_sconfig(&actual) && actual == mode;
}

static void settle_vendor_policy(void) {
    /* poll() gives us a portable, signal-aware 300 ms sleep without spawning
     * a shell process or relying on libc feature-test macros. */
    (void)poll(NULL, 0, 300);
}

static bool write_marker(int mode) {
    FILE* fp = fopen(THERMAL_OWNERSHIP_MARKER, "w");
    if (!fp)
        return false;

    const int written = fprintf(fp, "%d\n", mode);
    const int flushed = fflush(fp);
    fclose(fp);
    return written > 0 && flushed == 0;
}

static int read_marker(void) {
    FILE* fp = fopen(THERMAL_OWNERSHIP_MARKER, "r");
    if (!fp)
        return -1;

    int mode = -1;
    const int ok = fscanf(fp, "%d", &mode);
    fclose(fp);
    return (ok == 1 && mode >= 0 && mode <= 0x800) ? mode : -1;
}

static void remove_marker(void) {
    (void)unlink(THERMAL_OWNERSHIP_MARKER);
}

static bool has_custom_override(const GameConfig* options) {
    if (!options)
        return false;

    return !IS_DEFAULT(options->gpu_profile) ||
           !IS_DEFAULT(options->thermal_profile) ||
           !IS_DEFAULT(options->cpu_governor) ||
           !IS_DEFAULT(options->gpu_governor) ||
           !IS_DEFAULT(options->gpu_max_freq) ||
           options->cpu_policy_controls[0] != '\0';
}

static void acquire_thermal_ownership(const char* package) {
    int current = -1;
    if (!read_sconfig(&current)) {
        if (!unsupported_logged) {
            log_zenith(LOG_INFO,
                       "EVENT=PERAPP_THERMAL_UNSUPPORTED reason=sconfig_missing pkg=%s",
                       package ? package : "unknown");
            unsupported_logged = true;
        }
        return;
    }

    unsupported_logged = false;

    if (thermal_owned) {
        /* Reassert only if another vendor component took the mode back. */
        if (current != MI_THERMAL_NO_LIMITS_MODE) {
            if (write_sconfig(MI_THERMAL_NO_LIMITS_MODE)) {
                settle_vendor_policy();
                log_zenith(LOG_WARN,
                           "EVENT=PERAPP_THERMAL_REASSERT pkg=%s mode=%d restored=6",
                           package ? package : "unknown", current);
            } else {
                log_zenith(LOG_ERROR,
                           "EVENT=PERAPP_THERMAL_REASSERT_FAILED pkg=%s live=%d",
                           package ? package : "unknown", current);
            }
        }
        return;
    }

    if (current == MI_THERMAL_NO_LIMITS_MODE) {
        /* Already unrestricted; this daemon did not acquire the ownership. */
        return;
    }

    thermal_previous_mode = current;
    if (!write_marker(current)) {
        thermal_previous_mode = -1;
        log_zenith(LOG_ERROR,
                   "EVENT=PERAPP_THERMAL_ACQUIRE_FAILED pkg=%s reason=marker_write previous=%d",
                   package ? package : "unknown", current);
        return;
    }

    if (!write_sconfig(MI_THERMAL_NO_LIMITS_MODE)) {
        remove_marker();
        thermal_previous_mode = -1;
        log_zenith(LOG_ERROR,
                   "EVENT=PERAPP_THERMAL_ACQUIRE_FAILED pkg=%s previous=%d",
                   package ? package : "unknown", current);
        return;
    }

    settle_vendor_policy();
    thermal_owned = true;
    log_zenith(LOG_INFO,
               "EVENT=PERAPP_THERMAL_ACQUIRED pkg=%s previous=%d mode=6",
               package ? package : "unknown", current);
}

void release_per_app_thermal_policy(void) {
    int previous = thermal_previous_mode;

    if (!thermal_owned) {
        /* Crash recovery can restore ownership before the normal state is built. */
        previous = read_marker();
        if (previous < 0)
            return;
    }

    thermal_owned = false;
    thermal_previous_mode = -1;

    int current = -1;
    if (!read_sconfig(&current)) {
        log_zenith(LOG_WARN,
                   "EVENT=PERAPP_THERMAL_RELEASE_SKIPPED reason=sconfig_missing");
        remove_marker();
        return;
    }

    /* If another controller already selected a different mode, do not clobber it. */
    if (current != MI_THERMAL_NO_LIMITS_MODE) {
        log_zenith(LOG_INFO,
                   "EVENT=PERAPP_THERMAL_RELEASE_ALREADY_REPLACED live=%d previous=%d",
                   current, previous);
        remove_marker();
        return;
    }

    if (write_sconfig(previous)) {
        settle_vendor_policy();
        remove_marker();
        log_zenith(LOG_INFO,
                   "EVENT=PERAPP_THERMAL_RELEASED restored=%d",
                   previous);
    } else {
        /* Keep the marker so a subsequent daemon start can recover it. */
        thermal_previous_mode = previous;
        thermal_owned = true;
        log_zenith(LOG_ERROR,
                   "EVENT=PERAPP_THERMAL_RELEASE_FAILED restored=%d",
                   previous);
    }
}

void recover_per_app_thermal_policy(void) {
    const int previous = read_marker();
    if (previous < 0)
        return;

    int current = -1;
    if (!read_sconfig(&current))
        return;

    if (current != MI_THERMAL_NO_LIMITS_MODE) {
        /* Device reboot/reset already replaced our temporary mode. */
        remove_marker();
        return;
    }

    if (write_sconfig(previous)) {
        settle_vendor_policy();
        remove_marker();
        log_zenith(LOG_WARN,
                   "EVENT=PERAPP_THERMAL_RECOVERED restored=%d",
                   previous);
    } else {
        log_zenith(LOG_ERROR,
                   "EVENT=PERAPP_THERMAL_RECOVERY_FAILED restored=%d",
                   previous);
    }
}

void update_per_app_thermal_policy(const GameConfig* options, const char* package) {
    if (has_custom_override(options)) {
        acquire_thermal_ownership(package);
    } else {
        release_per_app_thermal_policy();
    }
}
