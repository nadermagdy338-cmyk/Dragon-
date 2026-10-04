/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
 * File-protocol contract suite — the C half of the shared fixtures in `fixtures/contracts/`.
 *
 * What it measures
 * ----------------
 * `AppLoader/StatusMonitor.c::read_app_status` is the single parser of the `app_status` file
 * the Java companion writes. Its format was implicit: the writer lived in Kotlin, the reader
 * here, and nothing tied them together — so a renamed key or a reordered value would have
 * broken the feed silently (both sides would keep "working", with the daemon reading defaults).
 *
 * Here the **real** parser is handed the fixture corpus through a link-time `fopen` route
 * (see host/fopen_router.c): valid, torn, and incomplete files, plus a missing file. The Kotlin
 * side asserts it *produces* `app_status.valid.txt` byte-for-byte; this side asserts the parser
 * *reads back* the declared values. One file, two languages, two assertions.
 *
 * The limit: this proves the parse, not the device. No sysfs node is opened, no inotify event
 * is delivered, and no ARM binary is produced. "The format holds" is not "the phone works".
 */
#include <MaxManager.h>

#include "host/host.h"

#ifndef FIXTURE_DIR
#error "FIXTURE_DIR must be defined by the harness Makefile (the path to fixtures/contracts/)"
#endif

/* Points the daemon's own open of the app_status path at one fixture file. */
static void route_app_status_to(const char* fixture_name) {
    static char path[512];
    snprintf(path, sizeof(path), "%s/%s", FIXTURE_DIR, fixture_name);
    host_route_open(APP_MONITOR_FILE, path);
}

void suite_file_protocols(void) {
    printf("\n\033[1m[file_protocols] app_status contract (Kotlin writes / C reads)\033[0m\n");

    SystemStateCache cache;

    /* ── 1) The canonical file: every declared field parses to its declared value. ─────── */
    memset(&cache, 0xFF, sizeof(cache)); /* pre-fill so a missing reset would be visible */
    route_app_status_to("app_status.valid.txt");
    read_app_status(&cache);

    CHECK_STR_EQ(cache.focused_app, "com.example.game", "focused_app: package token parses");
    CHECK(cache.focused_pid == 12288, "focused_app: pid token parses");
    CHECK(cache.screen_awake == 1, "screen_awake parses");
    CHECK(cache.battery_saver == 0, "battery_saver parses");
    CHECK(cache.zen_mode == 0, "zen_mode parses");
    CHECK(cache.battery_level == 87, "battery_level parses");
    CHECK(cache.is_charging == 0, "is_charging parses");
    CHECK_STR_EQ(cache.app_name, "Example Game", "app_name keeps its spaces to end of line");
    CHECK_STR_EQ(cache.switch_id, "sw-1758888888000", "switch_id parses");

    /* ── 2) Torn file: unknown keys, a line with no separator, and a v-line are ignored. ── */
    memset(&cache, 0, sizeof(cache));
    route_app_status_to("app_status.torn.txt");
    read_app_status(&cache);

    CHECK_STR_EQ(cache.focused_app, "com.example.game",
                 "a key we do not know does not disturb the ones we do");
    CHECK(cache.focused_pid == 12288, "torn: pid still parses after an unknown key");
    CHECK(cache.screen_awake == 1, "torn: screen_awake still parses after a separatorless line");
    CHECK(cache.is_charging == 1, "torn: is_charging parses with a different value than the canonical file");
    CHECK_STR_EQ(cache.app_name, "Example Game", "torn: app_name is intact");
    CHECK_STR_EQ(cache.switch_id, "sw-1758888888000", "torn: switch_id is intact");

    /*
     * The version line is the §12 handshake: the writer emits `v 1` first, and this parser
     * must not mistake it for a field. Asserted by the fact that every other value above is
     * correct while `v` is present — a parser that consumed it as data would corrupt one.
     */
    CHECK(cache.battery_level == 87, "torn: the `v 1` handshake line is skipped, not parsed as data");

    /* ── 3) Incomplete file: absent fields keep the parser's declared defaults. ─────────── */
    memset(&cache, 0, sizeof(cache));
    route_app_status_to("app_status.missing.txt");
    read_app_status(&cache);

    CHECK_STR_EQ(cache.app_name, "Unknown", "absent app_name reads as the declared default, not empty");
    CHECK(cache.battery_level == -1, "absent battery_level reads as the declared -1, not 0");
    CHECK(cache.screen_awake == 0, "absent screen_awake reads as 0");
    CHECK(cache.is_charging == 0, "absent is_charging reads as 0");
    CHECK_STR_EQ(cache.switch_id, "", "absent switch_id is empty, not stale");
    CHECK_STR_EQ(cache.focused_app, "com.example.game", "the present field still parses");

    /*
     * ── 4) Missing file: "no update", not "reset". ────────────────────────────────────────
     *
     * This is the distinction the parser makes and §12.2 declares: with no file at all it
     * returns before touching the cache, so a stale-but-real value survives. Treating that as
     * a reset would silently zero the daemon's view of the focused app on every boot race.
     */
    host_route_open(APP_MONITOR_FILE, FIXTURE_DIR "/this-fixture-does-not-exist.txt");
    memset(&cache, 0, sizeof(cache));
    cache.battery_level = 55;
    strcpy(cache.app_name, "Previous");
    read_app_status(&cache);
    CHECK(cache.battery_level == 55, "a missing file leaves the cache untouched (no update)");
    CHECK_STR_EQ(cache.app_name, "Previous", "a missing file does not reset earlier fields");

    host_clear_routes();
}
