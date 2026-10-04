/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 * Proprietary and confidential — not licensed for use, copying, or distribution
 * without prior written permission from the copyright holder.
 *
 * Broadcast contract suite — the C half of `fixtures/contracts/broadcast_extras.tsv`.
 *
 * What it measures
 * ----------------
 * `nd.max.ACTION_MANAGE` was the one surface §4 still declared «❌ no contract»: the daemon
 * builds an `am broadcast … --es/--ez` command line, the Kotlin receiver reads those extras by
 * name, and **nothing tied the two spellings together**. A renamed key on either side does not
 * error — the field simply arrives empty. That is the silent-defect class.
 *
 * Here the **real** `notify()` is called through `__real_notify` (the harness links with
 * `-Wl,--wrap=notify`, so the wrap only exists to keep CLIUtility.c's calls from posting on the
 * host; `__real_notify` reaches the production body) and its emitted shell command is inspected
 * verbatim. `toast()`, `clearlogs()` and `hidenotifications()` call `systemv` directly and are
 * captured the same way.
 *
 * The limit: this proves the command line the daemon would run, not that a phone delivered the
 * intent. `am broadcast`, the receiver, and the notification shade are device behaviour.
 */
#include <MaxManager.h>

#include "host/host.h"

#include "sys/system_properties.h" /* host_prop_set / host_prop_reset (shim) */

/*
 * The unwrapped original. `-Wl,--wrap=notify` renames the definition's references: `notify`
 * becomes `__wrap_notify` (the recorder in host/stubs.c) and the production body stays reachable
 * as `__real_notify`. Declared here with its real variadic signature.
 */
void __real_notify(const char* title, const char* fmt, bool chrono, int timeout_ms, ...);

/* ── token helpers: read the recorded command the way `am` would ───────────────────────────── */

/** True if the recorded command at index `i` contains `--es <key>` or `--ez <key>`. */
static bool command_has_extra(const char* cmd, const char* key) {
    char needle_es[64];
    char needle_ez[64];
    snprintf(needle_es, sizeof(needle_es), "--es %s", key);
    snprintf(needle_ez, sizeof(needle_ez), "--ez %s", key);
    return strstr(cmd, needle_es) != NULL || strstr(cmd, needle_ez) != NULL;
}

/**
 * Counts the distinct `--es`/`--ez` keys in a command, so a key added silently is visible.
 * Only the 6 declared keys can appear; anything else means the emitter grew an extra.
 */
static int count_extra_tokens(const char* cmd) {
    int n = 0;
    for (const char* p = cmd; *p;) {
        if (strncmp(p, "--es ", 5) == 0 || strncmp(p, "--ez ", 5) == 0) {
            n++;
            p += 5;
        } else {
            p++;
        }
    }
    return n;
}

void suite_broadcast(void) {
    printf("\n\033[1m[broadcast] nd.max.ACTION_MANAGE contract (C emits / Kotlin reads)\033[0m\n");

    /* ── 1) notify() without a timeout: exactly the four declared extras ──────────────── */
    host_reset_capture();
    __real_notify("Performance Profile", "System is now at Powerful state", false, 0);

    CHECK(host_systemv_count() == 1, "notify() emits exactly one shell command");
    const char* cmd = host_systemv_at(0);
    CHECK(strstr(cmd, "am broadcast") != NULL, "the command is an `am broadcast`");
    CHECK(strstr(cmd, "-a nd.max.ACTION_MANAGE") != NULL, "the action is the declared one");
    CHECK(strstr(cmd, "-n nd.max/nd.max.receiver.MaxManagerReceiver") != NULL,
          "notify() targets the declared component (fully qualified spelling)");
    CHECK(command_has_extra(cmd, "notifytitle"), "--es notifytitle is emitted");
    CHECK(command_has_extra(cmd, "notifytext"), "--es notifytext is emitted");
    CHECK(command_has_extra(cmd, "chrono_bool"), "--ez chrono_bool is emitted");
    CHECK(strstr(cmd, "--es notifytitle 'Performance Profile'") != NULL,
          "the title travels as a quoted string, not a bare word");
    CHECK(!command_has_extra(cmd, "timeout"),
          "no timeout extra when timeout_ms == 0 (the second branch of notify)");
    CHECK(count_extra_tokens(cmd) == 3, "exactly three extras in the no-timeout branch");

    /* ── 2) notify() with a timeout: the fourth extra appears, chrono flips ───────────── */
    host_reset_capture();
    __real_notify("ECO Mode", "System is now at Endurance state", true, 5000);

    CHECK(host_systemv_count() == 1, "notify() with a timeout still emits one command");
    cmd = host_systemv_at(0);
    CHECK(command_has_extra(cmd, "timeout"), "--es timeout is emitted when timeout_ms > 0");
    CHECK(strstr(cmd, "--es timeout '5000'") != NULL,
          "the timeout travels as a string carrying the integer (the reader parses toLongOrNull)");
    CHECK(strstr(cmd, "--ez chrono_bool true") != NULL, "bool extras render as `true`/`false`");
    CHECK(count_extra_tokens(cmd) == 4, "exactly four extras in the timeout branch");

    /* ── 3) The shell-escaping boundary the contract depends on ───────────────────────── */
    host_reset_capture();
    __real_notify("it's", "a'b", false, 0);
    CHECK(host_saw_systemv("'it'\\''s'"),
          "an apostrophe is escaped as '\\'' before it reaches the shell");
    CHECK(!host_saw_systemv("'it's'"),
          "unescaped apostrophes never reach the command — an injection would break the extras");

    /* ── 4) toast(): gated on the property, and only the declared extra ───────────────── */
    host_prop_reset();
    host_prop_set("persist.sys.maxmanagerconf.showtoast", "1");
    host_reset_capture();
    toast("Reboot required");

    CHECK(host_systemv_count() == 1, "toast() emits one command when the property is 1");
    cmd = host_systemv_at(0);
    CHECK(strstr(cmd, "-a nd.max.ACTION_MANAGE") != NULL, "toast() uses the same action");
    CHECK(strstr(cmd, "-n nd.max/.receiver.MaxManagerReceiver") != NULL,
          "toast() targets the short component spelling (resolves to the same receiver)");
    CHECK(command_has_extra(cmd, "toasttext"), "--es toasttext is emitted");
    CHECK(strstr(cmd, "--es toasttext 'Reboot required'") != NULL, "the toast text travels quoted");
    CHECK(count_extra_tokens(cmd) == 1, "toast() carries exactly one extra");

    host_prop_reset(); /* property absent */
    host_reset_capture();
    toast("must not be sent");
    CHECK(host_systemv_count() == 0, "toast() is silent when the property is not 1");

    /* ── 5) clearall: the two producers of the same key ───────────────────────────────── */
    host_prop_reset();
    host_reset_capture();
    clearlogs();
    CHECK(host_saw_systemv("--ez clearall true"),
          "clearlogs() asks the app to clear notifications through --ez clearall");
    CHECK(host_saw_systemv("-n nd.max/.receiver.MaxManagerReceiver"), "clearlogs() names the receiver");
    CHECK(host_systemv_count() == 7,
          "clearlogs() emits the six log removals plus exactly one broadcast");

    host_reset_capture();
    hidenotifications();
    CHECK(host_systemv_count() == 1, "hidenotifications() emits a single command");
    CHECK(host_saw_systemv("--ez clearall true"), "hidenotifications() uses the same clearall key");
}
