/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
 * Link-time stubs for the daemon symbols the audited units call but that only
 * matter on a device (shell execution, logging, window visibility). Each one
 * records what was asked of it so the tests can assert on the daemon's
 * *decisions* without any real sysfs, root shell, or phone.
 */
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#include <MaxManager.h>

/* ── shared check counters (declared in host.h) ── */
int g_checks_run;
int g_checks_failed;

/* ── captured side channels ── */
#define HOST_CAP 256
#define HOST_CAP_LEN 1024

static char g_systemv[HOST_CAP][HOST_CAP_LEN];
static int g_systemv_count;
static char g_logs[HOST_CAP][HOST_CAP_LEN];
static int g_log_count;
static char g_notify[HOST_CAP][HOST_CAP_LEN];
static int g_notify_count;
static char g_writes[HOST_CAP][HOST_CAP_LEN];
static char g_write_formats[HOST_CAP][HOST_CAP_LEN];
static int g_write_ints[HOST_CAP];
static bool g_write_has_int[HOST_CAP];
static int g_write_count;
static char g_visible[128];
static bool g_visible_set;
static bool g_daemon_running = true;

void host_reset_capture(void) {
    g_systemv_count = 0;
    g_log_count = 0;
    g_notify_count = 0;
    g_write_count = 0;
    g_visible_set = false;
    g_visible[0] = '\0';
}

int host_write_count(void) { return g_write_count; }

const char* host_write_path(int i) {
    return (i >= 0 && i < g_write_count) ? g_writes[i] : "";
}

const char* host_write_format(int i) {
    return (i >= 0 && i < g_write_count) ? g_write_formats[i] : "";
}

bool host_write_has_int(int i) {
    return i >= 0 && i < g_write_count && g_write_has_int[i];
}

int host_write_int(int i) {
    return (i >= 0 && i < g_write_count) ? g_write_ints[i] : 0;
}

/*
 * `require_daemon_running()` is the CLI's outermost gate (Main.c calls it before every command
 * past `--rerun`). With no switch it could only be assumed, not measured — so the stub is
 * settable and the CLI suite asserts both branches.
 */
void host_set_daemon_running(bool running) { g_daemon_running = running; }

void host_set_visible_package(const char* pkg) {
    if (pkg) {
        strncpy(g_visible, pkg, sizeof(g_visible) - 1);
        g_visible[sizeof(g_visible) - 1] = '\0';
        g_visible_set = true;
    } else {
        g_visible_set = false;
    }
}

int host_systemv_count(void) { return g_systemv_count; }

const char* host_systemv_at(int i) {
    return (i >= 0 && i < g_systemv_count) ? g_systemv[i] : "";
}

bool host_saw_systemv(const char* needle) {
    for (int i = 0; i < g_systemv_count; i++)
        if (strstr(g_systemv[i], needle))
            return true;
    return false;
}

int host_log_count(void) { return g_log_count; }

bool host_log_contains(const char* needle) {
    for (int i = 0; i < g_log_count; i++)
        if (strstr(g_logs[i], needle))
            return true;
    return false;
}

int host_notify_count(void) { return g_notify_count; }

bool host_notify_contains(const char* needle) {
    for (int i = 0; i < g_notify_count; i++)
        if (strstr(g_notify[i], needle))
            return true;
    return false;
}


/* ── globals the audited units reference ── */
GameConfig opts;
GameConfig* g_game_cache = NULL;
int g_game_cache_count = 0;
pthread_mutex_t cache_mutex = PTHREAD_MUTEX_INITIALIZER;
bool is_restarting_renderer = false;
char* gamestart = NULL;
pid_t game_pids[MAX_GAME_PIDS];
int game_pid_count = 0;

/* ── stubbed behaviour ── */
void log_zenith(LogLevel level, const char* message, ...) {
    (void)level;
    if (g_log_count >= HOST_CAP)
        return;
    va_list args;
    va_start(args, message);
    vsnprintf(g_logs[g_log_count], HOST_CAP_LEN, message, args);
    va_end(args);
    g_log_count++;
}

int systemv(const char* format, ...) {
    if (g_systemv_count >= HOST_CAP)
        return 0;
    va_list args;
    va_start(args, format);
    vsnprintf(g_systemv[g_systemv_count], HOST_CAP_LEN, format, args);
    va_end(args);
    g_systemv_count++;
    return 0;
}

/*
 * `notify` is DEFINED by a unit in UNITS (DaemonUtility.c), so a plain stub would collide at link
 * time; the harness links with `-Wl,--wrap=notify` and the reference made by CLIUtility.c lands
 * here. It is not forwarded to the real symbol: on the host there is no notification to post, and
 * the record IS the measurement — the suite asserts which text the user would see.
 *
 * `run_profiler` is deliberately NOT wrapped: it is safe to run on the host (its `write2file`
 * and `uidof` are link-time stubs, and `is_kanged` now runs for real against a routed
 * `module.prop`) and doing so measures the real path instead of a recorder standing in for it —
 * the CLI suite asserts the `sys.maxmanager-profilesettings <n>` command the real function emits.
 */
void __wrap_notify(const char* title, const char* fmt, bool chrono, int timeout_ms, ...) {
    (void)chrono;
    (void)timeout_ms;
    if (g_notify_count >= HOST_CAP)
        return;
    va_list args;
    va_start(args, timeout_ms);
    vsnprintf(g_notify[g_notify_count], HOST_CAP_LEN, fmt, args);
    va_end(args);
    if (title && title[0]) {
        /* Keep the title too: the profile notification is identified by both halves. */
        size_t used = strlen(g_notify[g_notify_count]);
        if (used < HOST_CAP_LEN - 4)
            snprintf(g_notify[g_notify_count] + used, HOST_CAP_LEN - used, " | %s", title);
    }
    g_notify_count++;
}

/* The external loggers only matter on device (logcat). Here they are absorption points. */
void external_log(LogLevel level, const char* tag, const char* message, ...) {
    (void)level;
    (void)tag;
    (void)message;
}

void external_vlog(LogLevel level, const char* tag, const char* message, ...) {
    (void)level;
    (void)tag;
    (void)message;
}

/* `require_daemon_running()` calls this; the state is settable so both branches are asserted. */
int check_running_state(void) { return g_daemon_running ? 1 : 0; }

void release_per_app_thermal_policy(void) {}

/*
 * `is_kanged` **لم يبقَ stub**: `ModuleIntegrity.c` صار داخل `UNITS` بعد أن أخرج الخادم نفسه
 * على كل تنصيب نظيف، فالحارس الحقيقي هو ما يُربط هنا. و`--wrap=fopen` هو ما يجعله قابلًا
 * للقياس: توجيه `/data/adb/modules/MaxManager/module.prop` إلى الملفّ المشحون (أو إلى نصّ
 * مُخالف مكتوب في `build/`) يقيس **القرار** الذي كان يُنهي الخادم. وهذا هو الدرس المكرّر في
 * هذا الـharness: ما لا يُترجم كمصدر حقيقي يبقى ادّعاءً.
 */

int uidof(pid_t pid) { return pid > 0 ? 10000 + (int)pid : 0; }

/*
 * `write2file` is the daemon's single write door, and the CLI path's real output goes through it
 * (`run_profiler` asks for `API/current_profile` and `/data/data/nd.max/API/current_profile`).
 * A silent stub made "كتابة الملف" unmeasurable — §12.6 listed it as a coverage gap. So the call
 * is **recorded including its format and its integer argument**: the suite can then assert that
 * the profile number the user asked for is the profile number written where it belongs. What it
 * still cannot assert is that the bytes land on a device's disk — that part stays a device claim.
 */
int write2file(const char* filename, const bool append, const bool use_flock, const char* data, ...) {
    (void)append;
    (void)use_flock;
    if (g_write_count < HOST_CAP) {
        snprintf(g_writes[g_write_count], HOST_CAP_LEN, "%s", filename ? filename : "");
        snprintf(g_write_formats[g_write_count], HOST_CAP_LEN, "%s", data ? data : "");
        g_write_ints[g_write_count] = 0;
        g_write_has_int[g_write_count] = data && strstr(data, "%d") != NULL;
        if (g_write_has_int[g_write_count]) {
            va_list args;
            va_start(args, data);
            g_write_ints[g_write_count] = va_arg(args, int);
            va_end(args);
        }
        g_write_count++;
    }
    return 0;
}

/* Mirrors the real contract: caller frees the returned pointer. */
char* get_visible_package(SystemStateCache* cache) {
    (void)cache;
    if (!g_visible_set)
        return NULL;
    size_t n = strlen(g_visible) + 1;
    char* out = (char*)malloc(n);
    if (!out)
        return NULL;
    memcpy(out, g_visible, n);
    return out;
}
