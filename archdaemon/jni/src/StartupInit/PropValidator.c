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

#include "AZenith.h"

const char* VALID_MAXMANAGER_PROPS[] = {
    "persist.sys.maxmanager",
    "persist.sys.maxmanager.custom_default_balanced_IO",
    "persist.sys.maxmanager.custom_default_cpu_gov",
    "persist.sys.maxmanager.custom_default_maligpu_gov",
    "persist.sys.maxmanager.custom_performance_IO",
    "persist.sys.maxmanager.custom_performance_cpu_gov",
    "persist.sys.maxmanager.custom_performance_maligpu_gov",
    "persist.sys.maxmanager.custom_powersave_IO",
    "persist.sys.maxmanager.custom_powersave_cpu_gov",
    "persist.sys.maxmanager.custom_powersave_maligpu_gov",
    "persist.sys.maxmanager.debugmode",
    "persist.sys.maxmanager.default_balanced_IO",
    "persist.sys.maxmanager.default_cpu_gov",
    "persist.sys.maxmanager.default_maligpu_gov",
    "persist.sys.maxmanager.disabletweak",
    "persist.sys.maxmanager.service",
    "persist.sys.maxmanager.soctype",
    "persist.sys.maxmanager.state",
    "persist.sys.maxmanager.profilenotifications",
    "persist.sys.maxmanager.dropforeground",
    "persist.sys.maxmanagerconf.AIenabled",
    "persist.sys.maxmanagerconf.APreload",
    "persist.sys.maxmanagerconf.DThermal",
    "persist.sys.maxmanagerconf.SFL",
    "persist.sys.maxmanagerconf.bypasschg",
    "persist.sys.maxmanagerconf.bypasschgthreshold",
    "persist.sys.maxmanagerconf.renderer",
    "persist.sys.maxmanagerconf.bypasspath",
    "persist.sys.maxmanagerconf.clearbg",
    "persist.sys.maxmanagerconf.cpulimit",
    "persist.sys.maxmanagerconf.disabletrace",
    "persist.sys.maxmanagerconf.dnd",
    "persist.sys.maxmanagerconf.fpsged",
    "persist.sys.maxmanagerconf.freqoffset",
    "persist.sys.maxmanagerconf.fstrim",
    "persist.sys.maxmanagerconf.iosched",
    "persist.sys.maxmanagerconf.justintime",
    "persist.sys.maxmanagerconf.litemode",
    "persist.sys.maxmanagerconf.logd",
    "persist.sys.maxmanagerconf.malisched",
    "persist.sys.maxmanagerconf.preloadbudget",
    "persist.sys.maxmanagerconf.renderer",
    "persist.sys.maxmanagerconf.schedtunes",
    "persist.sys.maxmanagerconf.schemeconfig",
    "persist.sys.maxmanagerconf.showtoast",
    "persist.sys.maxmanagerconf.thermalcore",
    "persist.sys.maxmanagerconf.usefpsgo",
    "persist.sys.maxmanagerconf.walttunes",
};
const size_t VALID_MAXMANAGER_PROPS_COUNT = sizeof(VALID_MAXMANAGER_PROPS) / sizeof(VALID_MAXMANAGER_PROPS[0]);

/*
 * Namespace prefixes owned by the Android app (MaxManagerProps.kt).
 * The app's feature surface (Conf toggles, custom tweaks, debug keys)
 * grows faster than the exact list above can track; keys under these
 * prefixes are always live app-owned settings.
 *
 * Real-device proof this was needed (rodin/HyperOS 3 log, 2026-09-08):
 * persist.sys.maxmanagerconf.detailedlog was set from the Settings screen,
 * then flagged STALE_PROP and deleted here on every daemon start —
 * silently wiping the "detailed activity log" toggle on each boot. The
 * same fate awaited ~30 other app keys (refreshrate, custom_zram_*,
 * custom_sched_*, custom_mali_*, custom_doze_*, ...) the moment users
 * set them.
 */
static const char* VALID_PROP_PREFIXES[] = {
    "persist.sys.maxmanagerconf.",
    "persist.sys.maxmanager.custom_",
    "persist.sys.maxmanagerdebug.",
};

/**
 * @brief Checks whether a given property name belongs to the known/whitelisted
 *        set of MaxManager properties currently in use, either by exact match
 *        against the legacy list above or by namespace prefix ownership.
 *
 * @param name Null-terminated property name to check.
 * @return true if the property is recognized as valid, false otherwise.
 */
static bool is_known_maxmanager_prop(const char *name) {
    for (size_t i = 0; i < VALID_MAXMANAGER_PROPS_COUNT; i++) {
        if (strcmp(name, VALID_MAXMANAGER_PROPS[i]) == 0)
            return true;
    }
    for (size_t i = 0; i < sizeof(VALID_PROP_PREFIXES) / sizeof(VALID_PROP_PREFIXES[0]); i++) {
        if (strncmp(name, VALID_PROP_PREFIXES[i], strlen(VALID_PROP_PREFIXES[i])) == 0)
            return true;
    }
    return false;
}

typedef struct {
    char names[MAX_PENDING_DELETE][MAX_PROP_NAME_BUF];
    int  count;
} StalePropList;

/**
 * @brief Callback invoked by __system_property_read_callback for each property.
 *        Receives the original, untruncated name and value directly from the
 *        property area.
 *
 * @param cookie  Pointer to a StalePropList used to accumulate results.
 * @param name    Full-length original property name.
 * @param value   Property value (unused here).
 * @param serial  Property serial number (unused here).
 */
static void read_prop_cb(void *cookie, const char *name, const char *value, uint32_t serial) {
    (void)value;
    (void)serial;
    StalePropList *pending = (StalePropList *)cookie;

    if (strncmp(name, MAXMANAGER_PROPERTIES, MAXMANAGER_PROPERTIES_LEN) != 0)
        return;

    if (is_known_maxmanager_prop(name))
        return;

    if (pending->count < MAX_PENDING_DELETE) {
        strlcpy(pending->names[pending->count], name, MAX_PROP_NAME_BUF);
        pending->count++;
        log_zenith(LOG_WARN, "EVENT=STALE_PROP_FLAGGED name=%s", name);
    } else {
        log_zenith(LOG_WARN, "EVENT=STALE_PROP_SKIPPED reason=pending_buffer_full name=%s", name);
    }
}

/**
 * @brief Foreach-level callback invoked per prop_info by __system_property_foreach.
 *        Forwards to __system_property_read_callback to obtain the full,
 *        untruncated property name.
 *
 * @param pi     Property handle provided by foreach.
 * @param cookie Pointer to a StalePropList, forwarded to the next callback.
 */
static void foreach_prop_cb(const prop_info *pi, void *cookie) {
    __system_property_read_callback(pi, read_prop_cb, cookie);
}

/**
 * @brief Validates crucial system files and module integrity before startup.
 */
void validateprop(void) {
    StalePropList pending = { .count = 0 };

    __system_property_foreach(foreach_prop_cb, &pending);

    if (pending.count == 0) {
        log_zenith(LOG_INFO, "EVENT=PROP_VALIDATION_DONE stale_count=0");
        return;
    }

    log_zenith(LOG_INFO, "EVENT=PROP_VALIDATION_DONE stale_count=%d action=cleaning", pending.count);

    for (int i = 0; i < pending.count; i++) {
        char cmd[MAX_PROP_NAME_BUF + 32];
        snprintf(cmd, sizeof(cmd), "resetprop -p --delete %s", pending.names[i]);
        log_zenith(LOG_WARN, "EVENT=STALE_PROP_DELETED name=%s", pending.names[i]);
        systemv(cmd);
    }
}
