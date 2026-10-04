/*
 * Copyright (C) 2025-2026 Zexshia
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
#include <regex.h>

/**
 * @brief Reads an Android system property via getprop.
 */
static int get_prop_int(const char* prop) {
    char cmd[128];
    char buf[64] = {0};

    snprintf(cmd, sizeof(cmd), "getprop %s", prop);

    FILE* fp = popen(cmd, "r");
    if (!fp) return 0;

    if (fgets(buf, sizeof(buf), fp) == NULL) {
        pclose(fp);
        return 0;
    }
    pclose(fp);

    return (int)strtol(buf, NULL, 10);
}

/**
 * @brief Applies per-app resolution downscale via Android Game Mode API.
 * @return true if a new intervention was applied (needs restart), false if
 *         already active or nothing to apply.
 */
bool apply_resolution_target(DaemonContext* ctx, const char* pkg,
                               const char* downscale) {
    if (IS_DEFAULT(downscale))
        return false;

    if (ctx->resolution_applied) {
        log_zenith(LOG_INFO, "EVENT=RESOLUTION_SKIP reason=already_applied pkg=%s sw=%s", pkg,
                   ctx->cur_switch_id[0] ? ctx->cur_switch_id : "unknown");
        return false;
    }

    int sdk = get_prop_int("ro.build.version.release");
    char cmd[256];

    if (sdk >= 13) {
        int n = snprintf(cmd, sizeof(cmd), "cmd game set --mode 2");

        if (!IS_DEFAULT(downscale))
            n += snprintf(cmd + n, sizeof(cmd) - n, " --downscale %s", downscale);
        snprintf(cmd + n, sizeof(cmd) - n, " %s", pkg);

        systemv(cmd);
        ctx->resolution_applied = true;
        ctx->used_legacy_fallback = false;
        log_zenith(LOG_INFO, "EVENT=RESOLUTION_APPLIED pkg=%s method=game_mode_api sdk=%d cmd=%s sw=%s", pkg, sdk, cmd,
                   ctx->cur_switch_id[0] ? ctx->cur_switch_id : "unknown");
        return true;
    }

    char config[128];
    int n = snprintf(config, sizeof(config), "mode=2");

    if (!IS_DEFAULT(downscale))
        n += snprintf(config + n, sizeof(config) - n, ",downscaleFactor=%s", downscale);

    systemv("cmd device_config put game_overlay %s %s", pkg, config);
    systemv("cmd game mode 2 %s", pkg);

    ctx->resolution_applied = true;
    ctx->used_legacy_fallback = true;
    log_zenith(LOG_INFO, "EVENT=RESOLUTION_APPLIED pkg=%s method=device_config sdk=%d config=%s sw=%s", pkg, sdk, config,
               ctx->cur_switch_id[0] ? ctx->cur_switch_id : "unknown");
    return true;
}

/**
 * @brief Restores window size/density from ctx's saved fallback values.
 * @param ctx Daemon context holding the saved fallback.
 */
void restore_resolution_target(DaemonContext* ctx, const char* pkg) {
    if (!ctx->resolution_applied) return;

    if (ctx->used_legacy_fallback) {
        systemv("cmd device_config delete game_overlay %s", pkg);
        systemv("cmd game reset %s", pkg);
    } else {
        systemv("cmd game reset --mode 2 %s", pkg);
    }

    log_zenith(LOG_INFO, "EVENT=RESOLUTION_RESET pkg=%s legacy_fallback=%d sw=%s", pkg, ctx->used_legacy_fallback ? 1 : 0,
               ctx->cur_switch_id[0] ? ctx->cur_switch_id : "unknown");
    ctx->resolution_applied = false;
}
