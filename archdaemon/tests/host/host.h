/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
 * Host parity harness — shared declarations.
 *
 * The tests below exercise the REAL daemon translation units (compiled for the
 * host with a small shim) and assert on their observable decisions: which
 * property is deleted, which shell command is emitted, which profile is picked.
 * They prove logic parity. They do NOT prove device behaviour: nothing here
 * opens a sysfs node, runs root, or touches a phone. That limit is stated in
 * every report and must never be shaded into "works on device".
 */
#ifndef MAXMANAGER_HOST_H
#define MAXMANAGER_HOST_H

#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* ── capture side channels (implemented in stubs.c) ── */
void host_reset_capture(void);
void host_set_visible_package(const char* pkg);
int host_systemv_count(void);
const char* host_systemv_at(int i);
bool host_saw_systemv(const char* needle);
int host_log_count(void);
bool host_log_contains(const char* needle);
int host_notify_count(void);
bool host_notify_contains(const char* needle);
void host_set_daemon_running(bool running);

/* ── recorded `write2file` calls (the daemon's single write door) ── */
int host_write_count(void);
const char* host_write_path(int i);
const char* host_write_format(int i);
bool host_write_has_int(int i);
int host_write_int(int i);

/*
 * ── fixture routing for `fopen` (implemented in fopen_router.c) ──
 *
 * The daemon sources are compiled for the host **byte-identical to what ships**, so a
 * file path cannot be made a test parameter without editing production code. Instead the
 * harness links with `-Wl,--wrap=fopen`: `host_route_open(from, to)` makes any `fopen` of
 * `from` (e.g. the real /data/adb/... path) open `to` (a fixture) instead. With no route
 * registered every `fopen` behaves exactly as before — the wrapper is a pass-through.
 */
void host_route_open(const char* from, const char* to);
void host_clear_routes(void);

/*
 * ── tiny check framework ──
 *
 * The counters are `extern`, not `static`: with `static` each translation unit kept its own
 * copy, so `main()`'s summary counted only the suites compiled into parity_test.c and printed
 * "all checks passed" while a later suite had already failed. A harness that can report success
 * over a failure is worse than no harness, so the counts are shared and single.
 */
extern int g_checks_run;
extern int g_checks_failed;

#define CHECK(cond, label)                                                                \
    do {                                                                                  \
        g_checks_run++;                                                                   \
        if (cond) {                                                                       \
            printf("  \033[32mPASS\033[0m %s\n", (label));                                \
        } else {                                                                          \
            g_checks_failed++;                                                            \
            printf("  \033[31mFAIL\033[0m %s  (%s:%d)\n", (label), __FILE__, __LINE__);  \
        }                                                                                 \
    } while (0)

#define CHECK_STR_EQ(actual, expected, label)                                             \
    do {                                                                                  \
        const char* _a = (actual);                                                        \
        const char* _e = (expected);                                                      \
        g_checks_run++;                                                                   \
        if (_a && _e && strcmp(_a, _e) == 0) {                                            \
            printf("  \033[32mPASS\033[0m %s\n", (label));                                \
        } else {                                                                          \
            g_checks_failed++;                                                            \
            printf("  \033[31mFAIL\033[0m %s  expected=\"%s\" got=\"%s\"  (%s:%d)\n",     \
                   (label), _e ? _e : "(null)", _a ? _a : "(null)", __FILE__, __LINE__);  \
        }                                                                                 \
    } while (0)

void suite_prop_validator(void);
void suite_string_utils(void);
void suite_profile_logic(void);
void suite_bypass_registry(void);
void suite_resolution(void);
void suite_renderer(void);
void suite_per_app_governor(void);
void suite_module_integrity(void);
void suite_file_protocols(void);
void suite_cli_profile(void);
void suite_broadcast(void);

#endif
