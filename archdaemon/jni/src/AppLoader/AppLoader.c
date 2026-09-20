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

void free_gamelist_cache(void) {
    pthread_mutex_lock(&cache_mutex);
    if (g_game_cache != NULL) {
        free(g_game_cache);
        g_game_cache = NULL;
    }
    g_game_cache_count = 0;
    pthread_mutex_unlock(&cache_mutex);
}

void reload_gamelist_cache(DaemonContext* ctx) {
    free_gamelist_cache();
    FILE* fp = fopen(GAMELIST, "r");
    if (!fp) {
        log_zenith(LOG_ERROR, "EVENT=GAMELIST_LOAD_FAILED reason=fopen_failed path=%s", GAMELIST);
        return;
    }
    fseek(fp, 0, SEEK_END);
    long size = ftell(fp);
    fseek(fp, 0, SEEK_SET);

    if (size <= 0) {
        fclose(fp);
        log_zenith(LOG_WARN, "EVENT=GAMELIST_LOAD_SKIPPED reason=empty_or_invalid size=%ld", size);
        return;
    }

    char* buf = malloc(size + 1);
    if (!buf) {
        fclose(fp);
        log_zenith(LOG_FATAL, "EVENT=GAMELIST_LOAD_FAILED reason=malloc_read_buffer_failed size=%ld", size);
        return;
    }

    if (fread(buf, 1, size, fp) != (size_t)size) {
        fclose(fp);
        free(buf);
        log_zenith(LOG_ERROR, "EVENT=GAMELIST_LOAD_FAILED reason=fread_short_read expected=%ld", size);
        return;
    }
    fclose(fp);
    buf[size] = '\0';

    pthread_mutex_lock(&cache_mutex);
    int capacity = 16;
    g_game_cache = malloc(capacity * sizeof(GameConfig));
    if (!g_game_cache) {
        free(buf);
        pthread_mutex_unlock(&cache_mutex);
        log_zenith(LOG_FATAL, "EVENT=GAMELIST_LOAD_FAILED reason=malloc_cache_array_failed capacity=%d", capacity);
        return;
    }

    char* ptr = buf;
    while ((ptr = strstr(ptr, "\": {")) != NULL) {
        char* start_quote = ptr - 1;
        while (start_quote > buf && *start_quote != '"')
            start_quote--;

        if (*start_quote == '"') {
            if (g_game_cache_count >= capacity) {
                capacity *= 2;
                GameConfig* temp = realloc(g_game_cache, capacity * sizeof(GameConfig));
                if (!temp) {
                    log_zenith(LOG_FATAL, "EVENT=GAMELIST_LOAD_FAILED reason=realloc_failed capacity=%d parsed_so_far=%d", capacity, g_game_cache_count);
                    free(g_game_cache);
                    g_game_cache = NULL;
                    g_game_cache_count = 0;
                    break;
                }
                g_game_cache = temp;
            }

            size_t pkg_len = ptr - (start_quote + 1);
            if (pkg_len >= sizeof(g_game_cache[g_game_cache_count].package)) {
                pkg_len = sizeof(g_game_cache[g_game_cache_count].package) - 1;
            }
            strncpy(g_game_cache[g_game_cache_count].package, start_quote + 1, pkg_len);
            g_game_cache[g_game_cache_count].package[pkg_len] = '\0';

            char* next_block = strstr(ptr + 4, "\": {");
            char* p;

            p = strstr(ptr, "\"perf_lite_mode\":");
            if (p && (!next_block || p < next_block))
                extract_string_value(g_game_cache[g_game_cache_count].perf_lite_mode, p,
                                     sizeof(g_game_cache[g_game_cache_count].perf_lite_mode));
            else
                strcpy(g_game_cache[g_game_cache_count].perf_lite_mode, "default");

            p = strstr(ptr, "\"dnd_on_gaming\":");
            if (p && (!next_block || p < next_block))
                extract_string_value(g_game_cache[g_game_cache_count].dnd_on_gaming, p,
                                     sizeof(g_game_cache[g_game_cache_count].dnd_on_gaming));
            else
                strcpy(g_game_cache[g_game_cache_count].dnd_on_gaming, "default");

            p = strstr(ptr, "\"app_priority\":");
            if (p && (!next_block || p < next_block))
                extract_string_value(g_game_cache[g_game_cache_count].app_priority, p,
                                     sizeof(g_game_cache[g_game_cache_count].app_priority));
            else
                strcpy(g_game_cache[g_game_cache_count].app_priority, "default");

            p = strstr(ptr, "\"game_preload\":");
            if (p && (!next_block || p < next_block))
                extract_string_value(g_game_cache[g_game_cache_count].game_preload, p,
                                     sizeof(g_game_cache[g_game_cache_count].game_preload));
            else
                strcpy(g_game_cache[g_game_cache_count].game_preload, "default");

            p = strstr(ptr, "\"refresh_rate\":");
            if (p && (!next_block || p < next_block))
                extract_string_value(g_game_cache[g_game_cache_count].refresh_rate, p,
                                     sizeof(g_game_cache[g_game_cache_count].refresh_rate));
            else
                strcpy(g_game_cache[g_game_cache_count].refresh_rate, "default");

            p = strstr(ptr, "\"renderer\":");
            if (p && (!next_block || p < next_block))
                extract_string_value(g_game_cache[g_game_cache_count].renderer, p, sizeof(g_game_cache[g_game_cache_count].renderer));
            else
                strcpy(g_game_cache[g_game_cache_count].renderer, "default");

            p = strstr(ptr, "\"resolution_downscale\":");
            if (p && (!next_block || p < next_block))
                extract_string_value(g_game_cache[g_game_cache_count].resolution_downscale, p,
                                     sizeof(g_game_cache[g_game_cache_count].resolution_downscale));
            else
                strcpy(g_game_cache[g_game_cache_count].resolution_downscale, "default");
            
            p = strstr(ptr, "\"bypass_charging\":");
            if (p && (!next_block || p < next_block))
                extract_string_value(g_game_cache[g_game_cache_count].bypass_charging, p,
                                     sizeof(g_game_cache[g_game_cache_count].bypass_charging));
            else
                strcpy(g_game_cache[g_game_cache_count].bypass_charging, "default");

            // Per-App GPU/CPU controls (see GameConfig in AZenith.h for why these matter).
            p = strstr(ptr, "\"thermal_profile\":");
            if (p && (!next_block || p < next_block))
                extract_string_value(g_game_cache[g_game_cache_count].thermal_profile, p,
                                     sizeof(g_game_cache[g_game_cache_count].thermal_profile));
            else
                strcpy(g_game_cache[g_game_cache_count].thermal_profile, "default");

            p = strstr(ptr, "\"gpu_profile\":");
            if (p && (!next_block || p < next_block))
                extract_string_value(g_game_cache[g_game_cache_count].gpu_profile, p,
                                     sizeof(g_game_cache[g_game_cache_count].gpu_profile));
            else
                strcpy(g_game_cache[g_game_cache_count].gpu_profile, "default");

            p = strstr(ptr, "\"cpu_governor\":");
            if (p && (!next_block || p < next_block))
                extract_string_value(g_game_cache[g_game_cache_count].cpu_governor, p,
                                     sizeof(g_game_cache[g_game_cache_count].cpu_governor));
            else
                strcpy(g_game_cache[g_game_cache_count].cpu_governor, "default");

            p = strstr(ptr, "\"gpu_governor\":");
            if (p && (!next_block || p < next_block))
                extract_string_value(g_game_cache[g_game_cache_count].gpu_governor, p,
                                     sizeof(g_game_cache[g_game_cache_count].gpu_governor));
            else
                strcpy(g_game_cache[g_game_cache_count].gpu_governor, "default");

            p = strstr(ptr, "\"gpu_max_freq\":");
            if (p && (!next_block || p < next_block))
                extract_string_value(g_game_cache[g_game_cache_count].gpu_max_freq, p,
                                     sizeof(g_game_cache[g_game_cache_count].gpu_max_freq));
            else
                strcpy(g_game_cache[g_game_cache_count].gpu_max_freq, "default");

            // CPU policy ranges are owned and verified by AppMonitor, but the
            // native thermal guard must still know that an override is active
            // so vendor thermal code cannot reclaim the policies underneath it.
            p = strstr(ptr, "\"cpu_policy_controls\":");
            if (p && (!next_block || p < next_block))
                extract_string_value(g_game_cache[g_game_cache_count].cpu_policy_controls, p,
                                     sizeof(g_game_cache[g_game_cache_count].cpu_policy_controls));
            else
                g_game_cache[g_game_cache_count].cpu_policy_controls[0] = '\0';

            g_game_cache_count++;
        }
        ptr += 4;
    }
    free(buf);
    pthread_mutex_unlock(&cache_mutex);

    if (!ctx->is_initialize_complete) {
        log_zenith(LOG_INFO, "EVENT=GAMELIST_LOADED count=%d", g_game_cache_count);
    }
}
