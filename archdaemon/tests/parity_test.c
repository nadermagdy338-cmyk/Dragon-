/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
 * Host parity suite for `sys.maxmanager-service`.
 *
 * It links the real translation units (PropValidator, DaemonUtility,
 * ProfileUtility, PerAppKernel, ChargingNodes, ResolutionChanger, RenderingHandler,
 * StatusMonitor, CLIUtility) and asserts their observable decisions. Every
 * assertion names a behaviour that would regress silently on a device — the
 * whitelist that was wiping user settings, the shell-escaping that guards
 * command injection, the profile gate, the bypass node registry, the
 * renderer/resolution branches, the per-app governor guard that rejected the
 * kernel's own governors, the app_status wire format the companion
 * writes (fixtures/contracts/), and the --profile/--from-ai decision table.
 */
#include <ctype.h>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>

#include <MaxManager.h>

#include "host/host.h"

/* ────────────────────────────────────────────────────────────────────────────
 * ١) PropValidator — the whitelist that used to delete live user settings.
 * ──────────────────────────────────────────────────────────────────────────── */
void suite_prop_validator(void) {
    printf("\n\033[1m[prop_validator] property whitelist / stale deletion\033[0m\n");

    host_reset_capture();
    host_prop_reset();

    /* Must survive: exact legacy name, conf namespace, custom_ namespace, debug namespace. */
    host_prop_set("persist.sys.maxmanager.state", "running");
    host_prop_set("persist.sys.maxmanagerconf.detailedlog", "1");
    host_prop_set("persist.sys.maxmanagerconf.AIenabled", "true");
    host_prop_set("persist.sys.maxmanager.custom_zram_swappiness", "60");
    host_prop_set("persist.sys.maxmanagerdebug.trace", "1");
    /* Must be removed: under our namespace but owned by nobody we know. */
    host_prop_set("persist.sys.maxmanager.bogus_key", "stale");
    host_prop_set("persist.sys.maxmanager.orphan_two", "stale");
    /* Must be ignored entirely: not under our namespace. */
    host_prop_set("ro.build.version.sdk", "34");

    validateprop();

    CHECK(!host_saw_systemv("detailedlog"), "conf namespace key is never deleted (the regression the comment names)");
    CHECK(!host_saw_systemv("custom_zram_swappiness"), "custom_ namespace key is never deleted");
    CHECK(!host_saw_systemv("maxmanagerdebug.trace"), "debug namespace key is never deleted");
    CHECK(!host_saw_systemv("maxmanager.state"), "exact whitelisted key is never deleted");
    CHECK(!host_saw_systemv("ro.build.version.sdk"), "foreign namespace is not our business");
    CHECK(host_saw_systemv("resetprop -p --delete persist.sys.maxmanager.bogus_key"),
          "unknown key under our namespace is deleted by name");
    CHECK(host_saw_systemv("resetprop -p --delete persist.sys.maxmanager.orphan_two"),
          "second unknown key is deleted too");
    CHECK(host_systemv_count() == 2, "exactly the two unknown keys are deleted");

    /* Boundary: the naive prefix check treats `<ns>X` as ours, so it must be flagged
     * (documented behaviour, not a silent pass). */
    host_reset_capture();
    host_prop_reset();
    host_prop_set("persist.sys.maxmanagerX", "edge");
    validateprop();
    CHECK(host_saw_systemv("persist.sys.maxmanagerX"),
          "prefix without separator is still caught (documented naive-prefix boundary)");

    /* Boundary: the pending list is capped at MAX_PENDING_DELETE; overflow is skipped,
     * never silently truncated into a delete. */
    host_reset_capture();
    host_prop_reset();
    char name[64];
    for (int i = 0; i < MAX_PENDING_DELETE + 5; i++) {
        snprintf(name, sizeof(name), "persist.sys.maxmanager.unknown_%03d", i);
        host_prop_set(name, "x");
    }
    validateprop();
    CHECK(host_systemv_count() == MAX_PENDING_DELETE, "deletions are capped at MAX_PENDING_DELETE");
    CHECK(host_log_contains("STALE_PROP_SKIPPED"), "overflow is logged as SKIPPED, not silently dropped");

    /*
     * ── سمة السطح المُعلَن: لا خاصية مُعلَنة تُحذف في كل إقلاع ────────────────────────────
     *
     * هذه الدعوى وُجدت بقياس: `fixtures/contracts/system_properties.tsv` كشف أن مفاتيح
     * `persist.sys.maxmanager.gpu_studio.*` الأربعة تقع تحت نطاق الفحص (`persist.sys.maxmanager`)
     * بلا غطاء ⇒ فكان الخادم يوسمها `STALE_PROP` **ويحذفها عند كل تشغيل**، أي أن «حفظ GPU Studio»
     * في التطبيق يُمحى بلا سطر عطل ظاهر. والعلاج بادئة رابعة في `VALID_PROP_PREFIXES` (وقياسها
     * مكتوب هناك بجانبها)، وهذه الدعوى تُسقط عودة العطب.
     *
     * والمفاتيح المفحوصة ليست كل السطح (ذلك يقيسه `SystemPropertiesContractTest` على الجدول كاملًا)
     * بل **واحد عن كل صنف غطاء**: بادئة جديدة، وبادئة قديمة، والقائمة الدقيقة، ونطاق التصحيح.
     */
    static const char* declared_keys[] = {
        "persist.sys.maxmanager.gpu_studio.min_freq",
        "persist.sys.maxmanager.gpu_studio.max_freq",
        "persist.sys.maxmanager.gpu_studio.governor",
        "persist.sys.maxmanager.gpu_studio.mode",
        "persist.sys.maxmanagerconf.detailedlog",
        "persist.sys.maxmanagerconf.logmaxkb",
        "persist.sys.maxmanager.custom_doze_gms_mode",
        "persist.sys.maxmanagerdebug.soctype",
        "persist.sys.maxmanagerconf.AIenabled",
    };

    host_reset_capture();
    host_prop_reset();
    for (size_t i = 0; i < sizeof(declared_keys) / sizeof(declared_keys[0]); i++)
        host_prop_set(declared_keys[i], "1");

    validateprop();

    for (size_t i = 0; i < sizeof(declared_keys) / sizeof(declared_keys[0]); i++) {
        char label[192];
        snprintf(label, sizeof(label), "declared property is never deleted: %s", declared_keys[i]);
        CHECK(!host_saw_systemv(declared_keys[i]), label);
    }
    CHECK(host_systemv_count() == 0, "a declared property surface produces no resetprop delete at all");
}

/* ────────────────────────────────────────────────────────────────────────────
 * ٢) String utilities — trimming, shell escaping, value extraction.
 * ──────────────────────────────────────────────────────────────────────────── */
void suite_string_utils(void) {
    printf("\n\033[1m[string_utils] trim / escape / extract\033[0m\n");

    char a[] = "line\n";
    CHECK_STR_EQ(trim_newline(a), "line", "trailing newline is trimmed");
    char b[] = "no-newline";
    CHECK_STR_EQ(trim_newline(b), "no-newline", "string without newline is unchanged");
    char c[] = "a\nb\n";
    CHECK_STR_EQ(trim_newline(c), "a", "only the first newline terminates");
    CHECK(trim_newline(NULL) == NULL, "NULL is handled, not dereferenced");

    char esc[256];
    escape_shell_string(esc, "it's", sizeof(esc));
    CHECK_STR_EQ(esc, "it'\\''s", "single quote is escaped for the shell");
    escape_shell_string(esc, "plain", sizeof(esc));
    CHECK_STR_EQ(esc, "plain", "safe string passes through unchanged");
    escape_shell_string(esc, "", sizeof(esc));
    CHECK_STR_EQ(esc, "", "empty string yields empty string");

    char dst[64];
    extract_string_value(dst, "key: \"value\"", sizeof(dst));
    CHECK_STR_EQ(dst, "value", "quoted value after colon is extracted");
    extract_string_value(dst, "key:\t\"spaced\"", sizeof(dst));
    CHECK_STR_EQ(dst, "spaced", "leading tab/space is skipped");
    extract_string_value(dst, "key: unquoted", sizeof(dst));
    CHECK_STR_EQ(dst, "default", "unquoted value falls back to default");
    extract_string_value(dst, "no-colon-here", sizeof(dst));
    CHECK_STR_EQ(dst, "default", "missing colon falls back to default");
    extract_string_value(dst, NULL, sizeof(dst));
    CHECK_STR_EQ(dst, "default", "NULL input falls back to default");
}

/* ────────────────────────────────────────────────────────────────────────────
 * ٣) ProfileUtility — screen/low-power gates and per-app option resolution.
 * ──────────────────────────────────────────────────────────────────────────── */
void suite_profile_logic(void) {
    printf("\n\033[1m[profile_logic] screen state / low power / gamestart\033[0m\n");

    SystemStateCache cache;
    memset(&cache, 0, sizeof(cache));

    CHECK(get_screenstate_normal(NULL) == true, "missing cache defaults to screen-awake");
    cache.screen_awake = 0;
    CHECK(get_screenstate_normal(&cache) == false, "screen_awake=0 reads as off");
    cache.screen_awake = 1;
    CHECK(get_screenstate_normal(&cache) == true, "screen_awake=1 reads as on");

    CHECK(get_low_power_state_normal(NULL) == false, "missing cache defaults to no low-power");
    cache.battery_saver = 1;
    CHECK(get_low_power_state_normal(&cache) == true, "battery_saver=1 reads as on");
    cache.battery_saver = 0;
    CHECK(get_low_power_state_normal(&cache) == false, "battery_saver=0 reads as off");

    /* get_gamestart: NULL visible package ⇒ NULL; matching cache ⇒ options copied. */
    host_set_visible_package(NULL);
    CHECK(get_gamestart(NULL, &cache) == NULL, "no visible package yields no gamestart");

    host_set_visible_package("com.example.game");
    g_game_cache = NULL;
    g_game_cache_count = 0;
    CHECK(get_gamestart(NULL, &cache) == NULL, "empty cache yields no gamestart");

    static GameConfig one;
    memset(&one, 0, sizeof(one));
    strcpy(one.package, "com.example.game");
    strcpy(one.refresh_rate, "120");
    strcpy(one.renderer, "vulkan");
    strcpy(one.gpu_profile, "performance");
    g_game_cache = &one;
    g_game_cache_count = 1;

    GameConfig out;
    memset(&out, 0, sizeof(out));
    char* pkg = get_gamestart(&out, &cache);
    CHECK(pkg != NULL && strcmp(pkg, "com.example.game") == 0, "matching package is returned");
    CHECK_STR_EQ(out.refresh_rate, "120", "refresh_rate is copied from the cache");
    CHECK_STR_EQ(out.renderer, "vulkan", "renderer is copied from the cache");
    CHECK_STR_EQ(out.gpu_profile, "performance", "gpu_profile is copied from the cache");
    free(pkg);

    /* Unmatched visible package ⇒ NULL, and no partial copy is promised. */
    host_set_visible_package("com.example.other");
    CHECK(get_gamestart(&out, &cache) == NULL, "unmatched package yields no gamestart");

    host_set_visible_package(NULL);
    g_game_cache = NULL;
    g_game_cache_count = 0;
}

/* ────────────────────────────────────────────────────────────────────────────
 * ٤) ChargingNodes — the bypass registry's structural invariants.
 * ──────────────────────────────────────────────────────────────────────────── */
void suite_bypass_registry(void) {
    printf("\n\033[1m[bypass_registry] node table invariants\033[0m\n");

    /* The header exposes `bypass_list` as an incomplete extern array, so the count is
     * read from `bypass_list_size` and pinned here: a silent add/remove of a node is a
     * parity change and must be re-measured, not slipped through. (Measured 2026-09-26:
     * this pin first read 78 from a miscount; the suite failed and corrected it to 77 —
     * which is exactly the point of pinning.) */
    CHECK(bypass_list_size == 77, "registry size is pinned at 77 (re-measure on intentional change)");

    bool names_unique = true, paths_unique = true, paths_absolute = true;
    bool values_present = true, values_differ = true;
    for (int i = 0; i < bypass_list_size; i++) {
        if (!bypass_list[i].name || !bypass_list[i].name[0])
            values_present = false;
        if (!bypass_list[i].path || bypass_list[i].path[0] != '/')
            paths_absolute = false;
        if (!bypass_list[i].on_val || !bypass_list[i].off_val ||
            bypass_list[i].on_val[0] == '\0' || bypass_list[i].off_val[0] == '\0')
            values_present = false;
        else if (strcmp(bypass_list[i].on_val, bypass_list[i].off_val) == 0)
            values_differ = false;
        for (int j = i + 1; j < bypass_list_size; j++) {
            if (strcmp(bypass_list[i].name, bypass_list[j].name) == 0)
                names_unique = false;
            if (strcmp(bypass_list[i].path, bypass_list[j].path) == 0)
                paths_unique = false;
        }
    }
    CHECK(values_present, "every node has a name and both on/off values");
    CHECK(names_unique, "node names are unique");
    CHECK(paths_unique, "node paths are unique");
    CHECK(paths_absolute, "every node path is absolute");
    CHECK(values_differ, "on and off values differ for every node");
}

/* ────────────────────────────────────────────────────────────────────────────
 * ٥) ResolutionChanger — SDK branch and command construction.
 * ──────────────────────────────────────────────────────────────────────────── */
void suite_resolution(void) {
    printf("\n\033[1m[resolution] game-mode API vs legacy device_config\033[0m\n");

    DaemonContext ctx;
    memset(&ctx, 0, sizeof(ctx));
    strcpy(ctx.cur_switch_id, "sw-test");

    host_reset_capture();
    CHECK(apply_resolution_target(&ctx, "com.example.game", "default") == false,
          "'default' downscale is a no-op");
    CHECK(host_systemv_count() == 0, "no-op emits no shell command");

    ctx.resolution_applied = true;
    CHECK(apply_resolution_target(&ctx, "com.example.game", "0.75") == false,
          "already-applied context short-circuits");
    ctx.resolution_applied = false;

    setenv("HOST_FAKE_SDK", "14", 1);
    host_reset_capture();
    CHECK(apply_resolution_target(&ctx, "com.example.game", "0.75") == true,
          "modern SDK applies the downscale");
    CHECK(ctx.resolution_applied == true, "context records the intervention");
    CHECK(ctx.used_legacy_fallback == false, "modern SDK is not the legacy path");
    CHECK(host_saw_systemv("cmd game set --mode 2 --downscale 0.75 com.example.game"),
          "modern SDK emits the game-mode API command");

    memset(&ctx, 0, sizeof(ctx));
    setenv("HOST_FAKE_SDK", "12", 1);
    host_reset_capture();
    CHECK(apply_resolution_target(&ctx, "com.example.game", "0.75") == true,
          "legacy SDK still applies");
    CHECK(ctx.used_legacy_fallback == true, "legacy SDK is marked as fallback");
    CHECK(host_saw_systemv("cmd device_config put game_overlay com.example.game mode=2,downscaleFactor=0.75"),
          "legacy path writes the device_config overlay");
    CHECK(host_saw_systemv("cmd game mode 2 com.example.game"),
          "legacy path also sets the game mode");

    host_reset_capture();
    restore_resolution_target(&ctx, "com.example.game");
    CHECK(host_saw_systemv("cmd device_config delete game_overlay com.example.game"),
          "legacy restore deletes the overlay");
    CHECK(host_saw_systemv("cmd game reset com.example.game"),
          "legacy restore resets the game mode");
    CHECK(ctx.resolution_applied == false, "restore clears the applied flag");

    memset(&ctx, 0, sizeof(ctx));
    host_reset_capture();
    restore_resolution_target(&ctx, "com.example.game");
    CHECK(host_systemv_count() == 0, "restore on a clean context emits nothing");

    /* Modern-path restore uses the API reset form. */
    ctx.resolution_applied = true;
    ctx.used_legacy_fallback = false;
    host_reset_capture();
    restore_resolution_target(&ctx, "com.example.game");
    CHECK(host_saw_systemv("cmd game reset --mode 2 com.example.game"),
          "modern restore resets via the game-mode API");
}

/* ────────────────────────────────────────────────────────────────────────────
 * ٦) RenderingHandler — renderer switch decision.
 * ──────────────────────────────────────────────────────────────────────────── */
void suite_renderer(void) {
    printf("\n\033[1m[renderer] smart renderer switching\033[0m\n");

    char saved_ref[PROP_VALUE_MAX] = {0};
    char saved_sys[PROP_VALUE_MAX] = {0};

    host_reset_capture();
    CHECK(apply_smart_renderer(NULL, saved_ref, saved_sys) == false, "NULL target is a no-op");
    CHECK(apply_smart_renderer("default", saved_ref, saved_sys) == false, "'default' target is a no-op");
    CHECK(apply_smart_renderer("", saved_ref, saved_sys) == false, "empty target is a no-op");

    host_prop_reset();
    host_prop_set("debug.hwui.renderer", "skiagl");
    host_prop_set("persist.sys.maxmanagerconf.renderer", "skiagl");
    saved_ref[0] = '\0';
    saved_sys[0] = '\0';
    host_reset_capture();
    CHECK(apply_smart_renderer("vulkan", saved_ref, saved_sys) == true,
          "a real difference switches the renderer");
    CHECK_STR_EQ(saved_ref, "skiagl", "previous HWUI renderer is saved for restore");
    CHECK_STR_EQ(saved_sys, "skiagl", "previous sys renderer is saved for restore");
    CHECK(host_saw_systemv("sys.maxmanager-utilityconf setrender vulkan"),
          "the utility binary is asked to switch");
    char now[PROP_VALUE_MAX] = {0};
    __system_property_get("persist.sys.maxmanagerconf.renderer", now);
    CHECK_STR_EQ(now, "vulkan", "the sys property reflects the new renderer");

    /* Division of labour: the daemon asks the utility binary to switch and updates only
     * the sys property — it never writes debug.hwui.renderer itself. So idempotency
     * depends on the utility's side effect; model it, don't assume it. */
    char hwui[PROP_VALUE_MAX] = {0};
    __system_property_get("debug.hwui.renderer", hwui);
    CHECK_STR_EQ(hwui, "skiagl", "daemon leaves the HWUI renderer for the utility to change");
    host_prop_set("debug.hwui.renderer", "vulkan"); /* what `setrender vulkan` does on device */

    host_reset_capture();
    CHECK(apply_smart_renderer("vulkan", saved_ref, saved_sys) == false,
          "no switch when already on the target");
    CHECK(!host_saw_systemv("setrender"), "an idempotent call does not re-emit the command");
}

/* ────────────────────────────────────────────────────────────────────────────
 * ٧) PerAppKernel — the per-app governor that nobody was writing.
 * ────────────────────────────────────────────────────────────────────────────
 *
 * The defect this pins was measured on a `TECNO POVA 5 Pro` (MT6833GP, Aetherium kernel): the
 * kernel advertises `[sugov_ext, reflex, conservative, powersave, performance, schedhorizon,
 * schedutil]`, the app offers the user exactly what the kernel advertises, and the daemon's
 * hardcoded `is_safe_cpu_governor()` list of five names rejected `reflex` and `schedhorizon` with
 * `reason=unsafe_value` — so the node was never written. The profiles binary never allowlists: it
 * reads the device. So the two suites below assert that the guard no longer *is* an allowlist, and
 * that a malformed token still cannot reach a kernel node.
 *
 * What is NOT measured here, and must not be claimed: that a write lands on a real node (there is
 * no device sysfs on the host), and that System.c actually reaches this function (that is a
 * code-reading claim about the caller, not a runtime one). Both stay device claims.
 */

/*
 * وحدات تخطّاها **المضيف** لا نحن: تُعدّ وتُعلَن في الملخّص، فلا يُقرأ «261/261» على مضيف
 * أسقط 27 دعوى كأنّه مرّ عليها. والفرق **مقيس في CI نفسه**: التشغيل `36501257551` أعطى
 * `261/261` على runner بلا `policyN`، والتشغيل `36504291660` أعطى `234/234` على runner
 * يُعلنها — والفرق **27** دعوى في مجموعة الحاكم وحدها.
 */
static int g_suites_skipped = 0;

/*
 * Refuses to run the write-path assertions on a machine that exposes real CPU policies: the
 * daemon writes `/sys/devices/system/cpu/cpufreq/policyN/scaling_governor`, and a test host that
 * has them would be writing to the CPU the suite is running on. On a normal container the
 * directory exists but holds no `policyN`, so `collect_cpu_policies()` returns 0 and the branch
 * ends at `reason=no_cpu_policies` without touching anything.
 */
static bool host_exposes_real_cpu_policies(void) {
    DIR* dir = opendir("/sys/devices/system/cpu/cpufreq");
    if (!dir)
        return false;

    bool found = false;
    struct dirent* entry;
    while ((entry = readdir(dir)) != NULL) {
        if (strncmp(entry->d_name, "policy", 6) == 0 && isdigit((unsigned char)entry->d_name[6])) {
            found = true;
            break;
        }
    }
    closedir(dir);
    return found;
}

void suite_per_app_governor(void) {
    printf("\n\033[1m[per_app_governor] guard by shape, kernel as authority\033[0m\n");

    GameConfig cfg;

    /* Nothing configured ⇒ not even a log line: the daemon must not touch a governor it was
     * not asked about. */
    memset(&cfg, 0, sizeof(cfg));
    strcpy(cfg.cpu_governor, "default");
    strcpy(cfg.gpu_governor, "default");
    host_reset_capture();
    enforce_per_app_governors(&cfg, "com.example.game");
    CHECK(host_log_count() == 0, "both fields 'default' produce no governor attempt at all");

    /* The empty-field trap: IS_DEFAULT("") is false (the macro tests NULL and the literal
     * "default" only), so an unset field must be excluded explicitly or a zeroed GameConfig
     * reads as "an explicit governor was configured". */
    cfg.cpu_governor[0] = '\0';
    cfg.gpu_governor[0] = '\0';
    host_reset_capture();
    enforce_per_app_governors(&cfg, "com.example.game");
    CHECK(host_log_count() == 0, "an empty field is not read as configured (IS_DEFAULT ignores emptiness)");

    memset(&cfg, 0, sizeof(cfg));
    strcpy(cfg.cpu_governor, "performance");
    strcpy(cfg.gpu_governor, "performance");
    host_reset_capture();
    enforce_per_app_governors(NULL, "com.example.game");
    enforce_per_app_governors(&cfg, NULL);
    enforce_per_app_governors(&cfg, "");
    CHECK(host_log_count() == 0, "NULL options, NULL package and empty package are all no-ops");

    if (host_exposes_real_cpu_policies()) {
        g_suites_skipped++;
        printf("  \033[33mSKIP\033[0m this host exposes real policyN nodes — refusing to run a "
               "write-path assertion against a live CPU; the rest of this suite is "
               "\033[1mNOT VERIFIED here\033[0m\n");
        return;
    }

    /* ── the measured regression: every governor this device's kernel advertises ── */
    static const char* const device_advertised[] = {
        "sugov_ext", "reflex", "conservative", "powersave", "performance",
        "schedhorizon", "schedutil",
    };
    for (size_t i = 0; i < sizeof(device_advertised) / sizeof(device_advertised[0]); i++) {
        memset(&cfg, 0, sizeof(cfg));
        strcpy(cfg.cpu_governor, device_advertised[i]);
        strcpy(cfg.gpu_governor, "default");
        host_reset_capture();
        enforce_per_app_governors(&cfg, "com.example.game");
        char label[192];
        snprintf(label, sizeof(label),
                 "kernel-advertised CPU governor is not rejected as unsafe: %s",
                 device_advertised[i]);
        CHECK(!host_log_contains("PERAPP_GOVERNOR_REJECTED"), label);
    }

    /* ── the native guard is no longer narrower than its sibling in binprofiles ── */
    static const char* const profiles_known[] = {
        "scx", "walt", "sched_pixel", "uag", "schedplus", "energy_step",
        "ondemand", "interactive", "userspace",
    };
    for (size_t i = 0; i < sizeof(profiles_known) / sizeof(profiles_known[0]); i++) {
        memset(&cfg, 0, sizeof(cfg));
        strcpy(cfg.cpu_governor, profiles_known[i]);
        strcpy(cfg.gpu_governor, "default");
        host_reset_capture();
        enforce_per_app_governors(&cfg, "com.example.game");
        char label[192];
        snprintf(label, sizeof(label),
                 "a governor the profiles binary knows is not rejected by the daemon: %s",
                 profiles_known[i]);
        CHECK(!host_log_contains("PERAPP_GOVERNOR_REJECTED"), label);
    }

    /* ── the GPU side: the driver's published list is the authority, not a hardcoded nine ── */
    static const char* const gpu_tokens[] = {
        "performance", "apupassive-pe", "simple_ondemand", "msm-adreno-tz", "mali_ondemand",
    };
    for (size_t i = 0; i < sizeof(gpu_tokens) / sizeof(gpu_tokens[0]); i++) {
        memset(&cfg, 0, sizeof(cfg));
        strcpy(cfg.cpu_governor, "default");
        strcpy(cfg.gpu_governor, gpu_tokens[i]);
        host_reset_capture();
        enforce_per_app_governors(&cfg, "com.example.game");
        char label[192];
        snprintf(label, sizeof(label),
                 "GPU governor token passes the shape guard instead of being rejected: %s",
                 gpu_tokens[i]);
        CHECK(!host_log_contains("PERAPP_GOVERNOR_REJECTED"), label);
    }

    /* ── and the guard still bites: shape, not membership ── */
    static const char* const malformed[] = {
        "../../etc/passwd",          /* a path, not a governor */
        "performance; rm -rf /",     /* command-shaped */
        "perf ormance",              /* whitespace */
        "performance\n",             /* newline smuggled from a settings file */
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", /* 36 chars: cannot fit cpu_governor[32] */
    };
    for (size_t i = 0; i < sizeof(malformed) / sizeof(malformed[0]); i++) {
        memset(&cfg, 0, sizeof(cfg));
        strcpy(cfg.cpu_governor, malformed[i]);
        strcpy(cfg.gpu_governor, "default");
        host_reset_capture();
        enforce_per_app_governors(&cfg, "com.example.game");
        char label[192];
        snprintf(label, sizeof(label),
                 "malformed governor is rejected before any write: value #%zu", i);
        CHECK(host_log_contains("reason=unsafe_value"), label);
    }

    /* The rejection line is the user's own evidence channel (tools/log_gate.py reads it), so its
     * wire format is pinned, not just its presence. */
    memset(&cfg, 0, sizeof(cfg));
    strcpy(cfg.cpu_governor, "../../etc/passwd");
    strcpy(cfg.gpu_governor, "default");
    host_reset_capture();
    enforce_per_app_governors(&cfg, "com.example.game");
    CHECK(host_log_contains("EVENT=PERAPP_GOVERNOR_REJECTED knob=cpu_governor pkg=com.example.game "
                            "requested=../../etc/passwd reason=unsafe_value"),
          "the rejection names the knob, the package and the offending value");
}

/* ────────────────────────────────────────────────────────────────────────────
 *  Bootstrap: a fake `getprop` on PATH so the popen-based reader is real.
 * ──────────────────────────────────────────────────────────────────────────── */
static char g_path_dir[64];

static void install_fake_getprop(void) {
    char tmpl[] = "/tmp/mm-parity-XXXXXX";
    char* dir = mkdtemp(tmpl);
    if (!dir) {
        fprintf(stderr, "harness: could not create temp dir\n");
        return;
    }
    strncpy(g_path_dir, dir, sizeof(g_path_dir) - 1);

    char script[128];
    snprintf(script, sizeof(script), "%s/getprop", g_path_dir);
    FILE* fp = fopen(script, "w");
    if (!fp)
        return;
    fprintf(fp, "#!/bin/sh\necho \"${HOST_FAKE_SDK:-0}\"\n");
    fclose(fp);
    chmod(script, 0755);

    const char* old = getenv("PATH");
    char newpath[2048];
    snprintf(newpath, sizeof(newpath), "%s:%s", g_path_dir, old ? old : "");
    setenv("PATH", newpath, 1);
}

/*
 * ── module integrity: the guard that must not fire on the module's own prop ──
 *
 * العطب الذي أُخرج الخادم عند **كل إقلاع نظيف**: الحارس كان ينفّذ
 * `grep -q '^name=Max Manager$'`، و`mainfiles/module.prop` يقول `name=MaxManager`
 * (تسمية مقصودة لاحقة). فالحارس أخرج الوحدة لأنها هي هي — وسجل الجهاز يقولها:
 *
 *   F MaxManager: EVENT=MODULE_INTEGRITY_FAILED reason=modified_by_third_party
 *
 * ثم `exit`، فسكت كل ما يملكه الخادم: الملف العام، والحاكم لكل تطبيق، و`--checkbypasschg`
 * الذي تقرأ نتيجته شاشة تجاوز الشحن — وهي رسالة المستخدم نفسها.
 *
 * وهذه المجموعة تقيس **الملفّ المشحون نفسه**: التوجيه إلى `mainfiles/module.prop`
 * الحقيقي، لا إلى نصّ مكتوب في الاختبار. فلو غيّر أحد الاسم في `module.prop` ولم يُتبعه في
 * `MODULE_IDENTITY_NAME`، سقطت الدعوى الأولى هنا وسقطت بوابة `module_identity` في
 * `tools/bundle_contract.py` — أي أنّ العطب يُمسك قبل أن يُثبَّت على جهاز.
 */
void suite_module_integrity(void) {
    printf("\n\033[1m[module_integrity] the daemon's own module.prop must pass its own guard\033[0m\n");

    /* ── ١) الدالّة النقيّة على مدخلات مكتوبة: أين تنحرف الحدود؟ ── */
    CHECK(!module_identity_ok(NULL), "NULL text is not an identity");
    CHECK(module_identity_ok("id=MaxManager\nname=MaxManager\nversion=v1.0\nversionCode=1\nauthor=MaxManager Project\n"),
          "the shipped key order and layout are accepted");
    CHECK(module_identity_ok("name=MaxManager\r\nauthor=MaxManager Project\r\n"),
          "CRLF (a prop rewritten by another tool) is still the same identity");
    CHECK(!module_identity_ok("name=Max Manager\nauthor=MaxManager Project\n"),
          "the old spaced name is refused — it is the name this repo no longer ships");
    CHECK(!module_identity_ok("name=MaxManagerX\nauthor=MaxManager Project\n"),
          "a longer name is not a prefix match");
    CHECK(!module_identity_ok("name =MaxManager\nauthor=MaxManager Project\n"),
          "a space before '=' is not the same key");
    CHECK(!module_identity_ok("name=MaxManager\n"), "the author line is required too, not just the name");
    CHECK(!module_identity_ok("author=MaxManager Project\n"), "the name line is required too, not just the author");
    CHECK(!module_identity_ok("# name=MaxManager\nauthor=MaxManager Project\n"),
          "a commented-out line is not a property (the match is the whole line)");

    /* ── ٢) البوّابة الحقيقية: `fopen` موجَّه إلى الملفّ المشحون نفسه ── */
    host_clear_routes();
    host_route_open(MODULE_PROP, REPO_DIR "/mainfiles/module.prop");
    host_reset_capture();
    CHECK(!module_identity_violated(),
          "the module's own shipped module.prop passes the gate (the daemon survives boot)");
    CHECK(host_log_count() + host_notify_count() == 0,
          "a clean identity logs nothing and notifies no one");

    /* ── ٣) والحارس ليس أعمى: تغيير الاسم من طرف ثالث ما زال يُمسَك ── */
    host_clear_routes();
    const char* renamed = BUILD_DIR "/module_prop_renamed.prop";
    FILE* fixture = fopen(renamed, "w");
    CHECK(fixture != NULL, "the host can write the renamed-prop fixture");
    if (fixture) {
        fputs("id=MaxManager\nname=Max Manager\nversion=v1.0\nversionCode=1\n"
              "author=MaxManager Project\nupdateJson=\n",
              fixture);
        fclose(fixture);
    }
    host_route_open(MODULE_PROP, renamed);
    CHECK(module_identity_violated(),
          "a third party renaming the module is still caught — the guard did not become a no-op");

    /* ── ٤) ملفّ غير مقروء = «مُساء إليه» لا «سليم» (نفس سلوك ما قبل الإصلاح) ── */
    host_clear_routes();
    host_route_open(MODULE_PROP, BUILD_DIR "/no_such_module_prop_here.prop");
    CHECK(module_identity_violated(), "an unreadable module.prop is refused, never assumed clean");

    host_clear_routes();
}

int main(void) {
    printf("\033[1mMaxManager — archdaemon host parity suite\033[0m\n");
    printf("Real daemon units compiled for the host with an Android-property shim.\n");
    printf("This proves LOGIC parity only; device/sysfs behaviour is out of scope here.\n");

    install_fake_getprop();

    suite_prop_validator();
    suite_string_utils();
    suite_profile_logic();
    suite_bypass_registry();
    suite_resolution();
    suite_renderer();
    suite_per_app_governor();
    suite_module_integrity();
    suite_file_protocols();
    suite_cli_profile();
    suite_broadcast();

    printf("\n\033[1mSummary: %d/%d checks passed\033[0m\n",
           g_checks_run - g_checks_failed, g_checks_run);
    if (g_suites_skipped) {
        /* لا يُطوى التخطّي في رقم النجاح: «234/234» على مضيف يُعلن `policyN` ليست تغطيةً كاملة. */
        printf("\033[33m%d suite(s) SKIPPED by this host — their assertions are NOT VERIFIED "
               "here, and no claim is made about them\033[0m\n", g_suites_skipped);
    }
    if (g_checks_failed) {
        printf("\033[31m%d check(s) FAILED\033[0m\n", g_checks_failed);
        return 1;
    }
    printf("\033[32mall checks passed\033[0m\n");
    return 0;
}
