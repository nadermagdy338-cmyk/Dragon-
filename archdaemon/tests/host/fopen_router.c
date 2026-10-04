/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
 * Fixture routing for `fopen` — the link-time hook that lets the REAL daemon source read a
 * fixture without a single byte changing in production.
 *
 * Why this exists
 * ---------------
 * `AppLoader/StatusMonitor.c` opens a hardcoded `/data/adb/.config/MaxManager/app_status`.
 * To assert what `read_app_status` does with a valid, torn, or incomplete file we must point
 * that open somewhere else. The two tempting alternatives are both worse:
 *
 *   (1) Edit the daemon to take the path as a parameter or a test-only global — that puts a
 *       test hook into shipping code, and this harness's whole claim is that the audited
 *       sources are byte-identical to what ships.
 *   (2) Copy the parsing function into the test — then the test measures the copy, not the
 *       daemon, and the two drift apart exactly where the risk lives.
 *
 * So the redirection happens in the **linker**, not in the source: the harness links with
 * `-Wl,--wrap=fopen`, and every `fopen` referenced by the compiled objects lands here.
 * With no route registered the call is forwarded untouched, so the wrapper cannot change
 * any behaviour the other suites depend on.
 */
#include <stdio.h>
#include <string.h>

#include "host.h"

/* Set by `host_route_open`; both NULL means "pass everything through". */
static const char* g_route_from;
static const char* g_route_to;

void host_route_open(const char* from, const char* to) {
    g_route_from = from;
    g_route_to = to;
}

void host_clear_routes(void) {
    g_route_from = NULL;
    g_route_to = NULL;
}

/*
 * `--wrap=fopen` renames the references to `__wrap_fopen` and leaves the libc symbol
 * reachable as `__real_fopen`. The declaration is deliberately not in a header: it is an
 * ld contract, not a C API.
 */
extern FILE* __real_fopen(const char* path, const char* mode);

FILE* __wrap_fopen(const char* path, const char* mode) {
    if (g_route_from && g_route_to && path && strcmp(path, g_route_from) == 0)
        path = g_route_to;
    return __real_fopen(path, mode);
}
