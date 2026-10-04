# Codebase Concerns

**Analysis Date:** 2026-09-18 (rebuilt from the current tree)

Last column names the **owning role** from `AGENTS.md` §3 / `docs/ai/team/`. Priorities: **P1** = misleads or
blocks work, **P2** = degrades quality, **P3** = opportunistic cleanup. Items that were already known and
accepted as baseline noise are kept at the bottom and explicitly marked.

---

## P1 — Misleading or blocking

### C-01 · The "build is impossible here" premise was wrong — the real blocker is a missing Android SDK
- **Evidence (2026-09-18, all four checks)**: `gradle --version` → **9.7.0** (was 4.4.1 on 2026-09-15) and the
  wrapper asks for `gradle-9.5.1-bin.zip`, so the version conflict is **gone**; `services.gradle.org:443` and
  `repo.maven.apache.org:443` are **reachable**; but `ANDROID_HOME`/`ANDROID_SDK_ROOT` are unset, no
  `sdkmanager`, no `manager/local.properties`, and `~/.gradle/wrapper/dists` + `~/.gradle/caches` do not exist.
- **Impact**: the team was reporting "compilation unverified" for a reason that had already expired, and the
  actual requirement (an SDK + dependency provisioning, a global install of gigabytes) stayed unnamed.
- **Fix path (needs an explicit user decision, it installs outside the repo)**: provision cmdline-tools +
  platform 36 + build-tools, then `sh manager/gradlew --offline :app:compileDebugKotlin` and record the raw
  output in `docs/ai/VALIDATION.md` §0. Otherwise CI (`.github/workflows/build.yml`) remains the only place that
  really compiles.
- **Also verified**: `manager/gradlew` is `-rw-rw----` (use `sh gradlew`, do not chmod — that would add a mode
  change to the tree); `cargo`/`rustc`/`shellcheck` absent.
- **Owner**: `verifier` (execute) + `architect` (recorded in I-40/I-45 and NEXT_TASK NT-03)

### C-02 · ~~Crowdin is wired to one file, but there are six~~ — FIXED 2026-09-18
- **Evidence (before)**: `crowdin.yml` listed only `values/strings.xml`, while `values/` shipped six translatable
  files — and **471 of those strings lived only in English**: `max_ai_strings.xml` 220, `max_screen_strings.xml`
  177, `max_navigation_strings.xml` 54, `max_design_strings.xml` 11, `studio_strings.xml` 9. Other locales
  corroborate the miss: `values-de/` and `values-fr/` contain `strings.xml` **only**.
- **Impact**: every redesign string — the Max AI surface, navigation labels, screen copy — was invisible to the
  ~100-locale pipeline and could only ever be translated by hand, which is what ADR-14 exists to prevent.
- **Fix applied**: `crowdin.yml` now registers all six files (`source` + `values-%android_code%/…` target each),
  and the rule is recorded as **ADR-26** (a new string file must be registered in the same change).
- **Owner**: `localizer` (implement) + `architect` (record the choice) — done

### C-03 · Arabic parity gap — the *file* half is FIXED, the *catalogue* half is now Crowdin's job
- **Evidence (before)**: (a) `values/strings.xml` = 1,629 entries vs `values-ar/strings.xml` = 844; (b) **188 keys
  had no Arabic file at all** — `max_screen_strings.xml` (177) and `max_design_strings.xml` (11), while
  `max_ai` (220/220), `max_navigation` (54/54) and `studio` (9/9) were complete.
- **Fix applied 2026-09-18**: `values-ar/max_screen_strings.xml` (177 keys) and
  `values-ar/max_design_strings.xml` (11 keys) now exist, translated with the project's own vocabulary
  (`مفعل/معطل`, `إعادة المحاولة`, `ميجابايت` — taken from existing approved pairs, not invented).
  Verified per file pair: 0 missing keys, 0 extra keys, 0 format-specifier mismatches, 0 duplicate names,
  all `values*/` XML parses. **All six EN string files now have AR counterparts.**
- **Guards added**: `docs/ai/VALIDATION.md` §3 gained three gates — file-level AR parity, per-pair key +
  format-specifier parity, and Crowdin registration — because the old gate only inspected keys added by the
  *current diff* and therefore never saw accumulated debt.
- **CLOSED 2026-09-18 (same day, later pass)**: gap (a) is **gone — Arabic is 2,106/2,106 (100%)**. The 785
  missing keys in `strings.xml` were translated in-repo in three batches (262 + 262 + 261) using the project's own
  approved vocabulary, taken from the existing translations rather than invented (`مفعل/معطل`, `إعادة المحاولة`,
  `إعادة التشغيل`, `إعادة تعيين`, `الحرارة`, `الطاقة`, `أداء`, `الملف`). Verified: 0 missing, 0 duplicate keys,
  `--assert` = 0 defects, all six `values-ar/` files complete. **I-52 closed.**
- **Two real defects in the specifier gate were found by doing this work**: the pattern did not know decimal
  specifiers (`%4$.2f`, `%2$.1f`) — it rejected correct Arabic strings *and* stayed blind to a type change in them —
  and it did not exempt `%%`, an intentional escaped percent. Both fixed and unit-checked; the gate is now
  `0 ERROR` across all 84 locales. Lesson: a gate is only as good as the cases it was written against.
- **What remains**: the other **83 locales × 1,726 keys = 143,258 strings / 4,332,683 source characters** (I-51).
  A full batch runner exists (`tools/i18n_translate.py`) but needs a provider key that this environment does not have.
- **One real specifier finding during this pass**: `detail_readable_paths` — the Arabic drops `%2$s`, which in
  English is the plural suffix `"s"` (`partitions.size == 1 ? "" : "s"`). Verified safe: `String.format` ignores
  extra arguments, and the gate's rule is a **subset** rule (AR asking for an argument callers never pass = crash =
  `ERROR`; AR dropping one = `WARN`). The reason is now written in `values-ar/strings.xml` above the key so nobody
  "restores" it.
- **Residual risk**: a human/Arabic reviewer should read the 188 new strings once (they were written in-repo,
  not by a native translator). The comment at the top of both files says so explicitly.
- **Now measured, not estimated (2026-09-18, updated after round 8)**: `tools/i18n_coverage.py` counts
  **2,106 English keys** across the six files and **84 target locales**. Three are complete at **2,106/2,106
  (100%): `ar`, `es`, `fr`** — each with all six files present. The other **81 locales sit at 380 (18%) with
  5 of 6 files missing — 139,806 keys**. Tracked as **I-51** (all locales), **I-53** (French, now closed) and
  **I-54** (Spanish closed, German still 380). No machine translation is written into the repo by design
  (ADR-28); the tool collects CSVs and merges returned translations append-only after validating specifiers,
  existing keys and in-batch duplicates.
- **Two conventions learned the hard way while finishing Spanish** (both in `VALIDATION.md` §3.1): the project
  writes Unicode as **escapes** (`\u00b7`, `\u00d7`, `\u2014`, `\u2026`, `\u00b0`) in every language file, and
  Android does resolve them — so a translation copies the escape verbatim rather than substituting the
  character. And a row the gate rejects is not automatically wrong: `%%` and mixed-script rows
  (`%1$d%% available · …`) are correct and must be checked against the English source, not the batch.
- **Language identity is now a guarded triple (ADR-27)**: `res/values-*` folders ↔ `AppLanguage.CODES` ↔
  `res/xml/locales_config.xml` are compared by `--check-codes`. The `locales_config` file is not cosmetic:
  `setApplicationLocales` is rejected by the platform on API 33+ without `android:localeConfig`.
- **Owner**: `localizer` (guards in place), `architect` (I-30 tracked)

### C-04 · ~~Tracked junk, including a stale `MainActivity.kt.bak` next to the live file~~ — FIXED 2026-09-18
- **Evidence (before)**: inside the app source tree — `ui/mainscreens/HomeDashboardComponents.kt.backup`,
  `ui/mainscreens/LegendaryHomeDashboard.kt.bak`, `ui/component/NeuralDashboardKit.kt.backup`,
  **`nd/max/MainActivity.kt.bak`**. At the root: `LegendaryHomeDashboard.kt.backup`,
  `build_compile_latest.log` (16 KB), `build_compile_latest2.log` (22 KB), `build_test.log`.
  `.serena/` was tracked: **18 files / 124 KB**, including `intellij-server.log`, telemetry CSV and JSON.
- **Impact**: greps and agents read stale duplicates as if they were code (a `.bak` of MainActivity inside the
  package is the worst case); repo bloat and review noise.
- **Fix applied**: those files deleted from the index and the tree; `.serena/cache` removed;
  `.claude/settings.local.json` and `.serena/project.local.yml` untracked but kept on disk (`project.yml` stays
  tracked as shared config); `.gitignore` now covers `*.bak`, `*.backup`, `*.log`, `.serena/cache/`,
  `.serena/project.local.yml`, `.claude/settings.local.json`.
- **Also**: `CLAUDE.md.bak` was not junk — it is the **binding engineering contract** referenced by `AGENTS.md`
  and the team briefs, so it was moved to `docs/ai/ENGINEERING-CONTRACT.md` (not deleted) and every reference
  was updated.
- **Owner**: `executor-kotlin` (repo hygiene) — no architectural review needed

### C-05 · ~~Two component packages survived the ADR-06 fold~~ — FIXED 2026-09-18
- **Evidence (before)**: `ui/component/` = 36 files, `ui/components/` = 2 files (`VideoWallpaperPlayer.kt`,
  `WeatherEffects.kt`) — two homes for the same kind of composable.
- **Safety check before touching anything**: both files are **load-bearing** — `VideoWallpaperPlayer` is used by
  `ActivitylauncherScreen`, `ActivityLauncherPalette`, `KernelFlasherScreen`; `WeatherEffectOverlay` by the first
  two. A symbol-level collision check against `ui/component/` returned none, and no non-Kotlin file referenced the
  package.
- **Fix applied**: both files moved to `ui/component/` (`git mv`, package declaration updated) and all 5 import
  sites rewritten. `ui/components/` no longer exists; `ui/component/` is now 38 files.
- **Verified**: `grep -rn 'nd.max.ui.components'` → 0 hits; all 5 touched files brace-balanced; moved symbols
  still referenced from their importers. Note: `com.github.capntrips.kernelflasher.ui.components` in
  `KernelFlasherScreen.kt` is the vendored library's package — unrelated and untouched.
- **Owner**: `executor-kotlin` (done)

---

## P2 — Quality degradation

### C-06 · Design-language adoption is half-done
- **Evidence**: **30** files under `ui/` import `nd.max.ui.design`; **25** files still declare their own
  `Scaffold(`. Baseline on 2026-09-15 was 6 importers / 33 Scaffold files, so it is moving in the right direction.
- **Impact**: two visual languages coexist; every hub migrated twice if the old one is later removed.
- **Fix approach**: continue per-domain migration (the `NT-03` direction); the counters are the progress metric —
  they must never go backwards.
- **Owner**: `executor-kotlin`

### C-07 · Legacy-named UI modules are still load-bearing (deletion would break the build)
- **Evidence (symbol-level check, 2026-09-18)**: `LegendaryHomeDashboard.kt` (803 lines) is **live** —
  `HomeScreen.kt:158` renders it inside `HomeDashboardContent`. `HomeDashboardComponents.kt` (65) and
  `LegacyTweakComponents.kt` (149) have **unreferenced filenames but 71 call sites** across the app:
  `IconBadge` 20, `SectionLoadingIndicator` 15, `DashCardWrapper` 11, `TweaksSectionTitle` 8, `LabelText` 5,
  `GlowLinearBar` 5, `formatNetSpeed` 5, `DashSectionLabel` 1, `FreqLimitSliderItem` 1.
- **The trap this item exists to record**: a filename-based dead-code scan says "delete three legacy files"; a
  symbol-level scan says "these hold up the Home and Tweak surfaces". This analysis pass almost acted on the
  first answer.
- **Second trap, found the same day**: a symbol scan that ignores calls **inside the defining file** falsely
  reported four screens (`AppListScreen`, `AppDetailScreen`, `ErrorScreen`, `MainScreen`) as unreachable — all
  four are called locally, two of them from their own nested `NavHost`. Reachability = graph entry **or** any call
  site, same file included. Result after the correction: **0 unreachable `*Screen` functions out of 45**.
- **Impact**: the names advertise "legacy/dead", so the next cleanup pass can remove load-bearing composables —
  and `tools/repo_audit.py` hardcodes these paths in `TARGETS`/`NEW`, so its checks would silently retarget.
- **Fix approach**: **not deletion**. If they should move, relocate them (`ui/component/` or a `home/` package) and
  update the 71 call sites as one task with the `VALIDATION.md` §2/§4/§5 gates. Leave them in place otherwise,
  with this note as the reason. Any future deletion must be justified by a **symbol** reference check, never a
  filename grep.
- **Owner**: `executor-kotlin` (relocate or confirm) + `verifier` (symbol-level evidence)

### C-08 · `module.prop` ships `version=V1` — this is a **template, and it must stay that way**
- **Evidence**: `version` = `5.2`, `version_type` = `Dazzling`, `update.json` = `5.2 (1823-bf02195-Dazzling)`,
  while `mainfiles/module.prop` reads `version=V1` / `versionCode=1`. Two mechanisms explain it:
  `.github/scripts/compile_zip.sh` lines 38–39 rewrite both lines at packaging time, and the CI step
  “Sync Daemon Version String” bakes the same string into `archdaemon/jni/include/MaxManager.h` beforehand —
  the daemon's `check_module_version()` compares the two **byte-for-byte** and exits on mismatch.
  `.github/scripts/verify.sh` never validates `module.prop` itself, and `mainfiles/META-INF/.../update-binary`
  reads it through `grep_prop`.
- **Impact**: the tracked file looks like a bug — but “fixing” it by hand would desynchronise it from the
  daemon and can make the daemon refuse to start.
- **Resolution (documentation only, no edit)**: `module.prop` stays untouched. Nobody should type a version into
  it — the correct value contains the commit count and short SHA (`5.2 (<count>-<sha>-Dazzling)`), which only CI
  can compute.
- **Owner**: `architect` (recorded) — no code change is correct here

### C-09 · Ad-hoc root tooling — FIXED 2026-09-18
- **Evidence**: `check2.py` (static checker, no header, paths relative to the repo root), `fix_tweak.py`
  (**inert**: hardcoded Windows path and the old `zx.azenith` package, which no longer exists — 0 references),
  `manager/FINAL_UI_AUDIT.md` and `manager/CHANGED_FILES_FINAL_UI.md` (audits of a UI state that no longer
  exists). `manager/README.md` is a legitimate module README and was left alone.
- **Fix applied**: both audit documents now open with a “superseded” banner pointing at `docs/ai/`.
  `fix_tweak.py` is **deleted** (`git rm`) — inert by its own header, its target file and package are gone from
  the tree, and nothing referenced it but the docs that described it as dead. `check2.py` → **`tools/repo_audit.py`**.
- **Three real defects fixed in that same move** (found only because the move was tested, not assumed):
  1. **The checker was asserting a file that no longer exists.** `TARGETS`/`NEW` listed
     `ui/design/MaxViewToggle.kt`; the file was deleted in an earlier commit, has **0 references** in code and
     docs, and was never ours to delete. The checker reported it as `MISSING FILE` — a false alarm that would
     cost the next agent a real investigation. Both stub entries were removed and the header now states that
     these two lists are hand-maintained.
  2. **It was CWD-dependent and failed by lying.** Run from `tools/` it printed `kt files scanned: 0` plus
     invented `MISSING FILE` lines — confident, wrong output instead of an error. The root is now derived from
     `__file__` and the script exits loudly if the tree is not found. Verified identical output from the repo
     root, from `tools/`, and via an absolute path from `/tmp`.
  3. It had no honest name: `check2.py` said nothing about what it checks. Now `tools/repo_audit.py`.
- **Owner**: `architect` (triage) + `executor-kotlin` (move/delete)

### C-14 · Route literals outside the navigation package (ADR-02), still open in the flasher
- **Evidence**: `ui/flasher/KernelFlasherScreen.kt` internal nav uses 8 literal routes outside `ui/navigation` —
  lines 537 (`startDestination`), 542, 562, 570, 577, 588, 619, 650. The launcher's instance of the same defect
  (`app_detail/`, `app_list`, `packageName`) was **fixed** on 2026-09-18 by moving them to
  `ui/navigation/ActivityLauncherRoutes.kt`.
- **Why the old gate missed both**: gate §5(b) only greps `navigate("…")`, so `const val X_ROUTE = "…"` and
  `composable("error/{m}")` were invisible. `VALIDATION.md` §5(f) now catches both forms.
- **Impact**: routes multiplied by hand are exactly how the dead-alias bug (I-03) happened before.
- **Fix approach**: repeat the `ActivityLauncherRoutes` pattern; do it as its own task because this screen flips
  partitions — the Safety Reviewer signs off after the change (see NEXT_TASK NT-07).
- **Owner**: `executor-kotlin` + `safety-reviewer`

### C-10 · No tests wherever the risk is highest
- **Evidence**: `TESTING.md` — 0 tests for Compose UI, shell install/boot scripts, Rust crates, C daemons, JNI;
  1 of 22 ViewModels has a test.
- **Impact**: the daemons and the installer can regress invisibly; only on-device testing catches it.
- **Fix approach**: start with what is cheapest and highest-risk — a JVM source-scan test in the existing
  architecture-test style for install/daemon contracts, and extraction-based tests for state logic
  (`ControlLayoutModel` already proves the pattern). Rust `simulator` feature is available for desktop runs.
- **Owner**: `verifier`

---

## P3 — Opportunistic

### C-11 · ViewModel naming split
5 of 22 files use legacy `*Viewmodel.kt` (`AppSettingsViewmodel`, `ApplistViewmodel`, `HomeViewmodel`,
`SettingViewmodel`, `TweakViewmodel`). Rename when touching the file; never as a standalone task. — `executor-kotlin`

### C-12 · ~~`MainActivity.kt` regrew to 444 lines~~ — VERIFIED CLEAN 2026-09-18
It was reduced to 386 at the NT-01 close-out and now measures 444. Checked, and the growth is **not** a
navigation regression: the file contains **0** `composable(` registrations, and the whole `NavHost` body is a
single call to `maxNavGraph(navController)` at line 360. The added lines are the shell chrome and the
`enter/exit/popEnter/popExit` transition blocks. `VALIDATION.md` §5 remains the gate that keeps it this way.
— `verifier` (closed)

### C-13 · `.claude/settings.local.json` is tracked
Local agent permissions in version control; should be ignored like other tool state. — `executor-kotlin`

### C-15 · Maintainability debt was argued in prose — now it is measured and enforced
- **What was wrong**: «clean code» lived as adjectives in review notes ("giant files", "hardcoded strings", "too
  many direct writes"). Nothing failed when any of it grew, and no number existed to say whether a pass helped.
  `VERIFICATION_NT01.md` even recorded the control-plane inheritance as «199 direct `RootFileAccess`/shell sites
  in `ui/**`» — a sentence nobody could enforce.
- **Now**: `tools/code_health.py` separates **correctness** (must be zero) from **debt** (frozen at a ceiling):

  | metric | today | kind |
  | --- | --- | --- |
  | `package_mismatch` · `unresolved_resource` · `duplicate_string_key` · `stray_root_file` | `0` each | correctness |
  | `oversized_files` (>1000 lines) | `10` | debt, ratcheted |
  | `own_wildcard_imports` (`import nd.max.ui.*`) | `29` | debt, ratcheted |
  | `hardcoded_ui_literals` | `80` | debt, ratcheted |
  | `presentation_hw_writes` (ADR-11) | `27` | debt, ratcheted — see I-55 |

  The ceilings live in `tools/code_health_baseline.json`; `--assert` exits `1` on any increase, and the gate was
  **tested by deliberately lowering the ceiling and watching it fail** before being trusted.
- **Three false-alarm modes were found and fixed while building the probes**, which is itself the lesson
  (see C-07's traps and `docs/ai/REVIEW.md` §1):
  1. Reading only XML missed PNG-backed resources — 5 valid `R.drawable.*` calls looked "missing".
  2. `R` is **per Gradle module**: checking `kernel-flasher` refs against `app`'s resources produced **74 false**
     reports. The index is now built per module.
  3. A naive `>` probe flagged `2>/dev/null` (stderr suppression, not a write) and a line of KDoc prose — giving
     **87 hits with zero real writes**. The write probe now skips comments and requires `echo`/`printf`/`tee`
     with a real redirection.
- **Tracking gap also closed (I-57)**: `AGENTS.md` — the entry point every agent reads — was **untracked and not
  ignored**, so it would vanish in any fresh clone; it is now tracked. `.maxmanager-sync-root`, an empty local
  tool marker, is now ignored so it no longer dirties `git status`.
- **Owner**: `verifier` (owns the ceiling) + `executor-kotlin` (pays it down in NT-12)

---

## Baseline noise — do not "fix"

| Item | Evidence | Why it stays |
| --- | --- | --- |
| `AppMonitor.kt` fails naive brace-balance checks | identical to HEAD; lexer artifact | expected; ignore unless you edited the file |
| `manager/kernel-flasher/` duplicate-looking theme/type files | vendored fork `com.github.capntrips.kernelflasher` | expected; leave alone |
| Three "legacy" files in `ui/mainscreens/` | filenames unreferenced, symbols used 71× | see C-07 — the size drop (65 lines) is real, the file is **not** dead |

## Changelog

- 2026-09-18 (round 9 · cleanliness + verification) — **nothing broken, and now provable**: four correctness
  checks at zero (`package_mismatch`, `unresolved_resource`, `duplicate_string_key`, `stray_root_file`), three
  gates green (`i18n --assert`, `code_health --assert`, `repo_audit` = `PROBLEMS: 0`), 124 XML files parsed and
  0 duplicate keys across 85 `values*` dirs. **New C-15** (debt measured + ratcheted) and **I-55** (`27`
  presentation-layer hardware writes, pre-existing, frozen). **Fixes**: `MtkUtils.kt` moved into `ui/util/`
  (it was the only one of 33 files declaring `package nd.max.ui.util` from outside that directory — content
  byte-identical, `0 insertions 0 deletions`); `fix_tweak.py` deleted; `check2.py` → `tools/repo_audit.py` with
  its stale-file assertion, CWD-dependent false output, and dishonest name all fixed; `AGENTS.md` tracked.
  **The CWD defect was not unique**: `tools/i18n_coverage.py` had it too and was fixed the same way — it threw
  `FileNotFoundError` and produced nothing when run from `tools/`, which is how a *gate* silently stops being a
  gate. All three tools now resolve the repo root from `__file__`, verified to give identical output from the
  repo root, from `tools/`, and by absolute path from `/tmp`. Also added `--todo <locale>` to `i18n_coverage.py`:
  the handoff recipe pointed at `build/i18n/todo_*.txt`, but `build/` is gitignored and **`glob`/search cannot see
  it**, so the instruction was unrunnable for any fresh agent. It is a command now, not a file.
- 2026-09-18 (round 4) — **C-01 resolved by evidence**: the blocker is a missing Android SDK, not the Gradle
  version or the network (both fine now); recorded in I-40/I-45 and NEXT_TASK NT-03. **New C-14**: 8 route
  literals in `KernelFlasherScreen`, the same defect fixed in the launcher (`ActivityLauncherRoutes`). Gate §5(f)
  added — first draft produced 90 false positives out of 98 hits, so it now matches only navigation call sites
  and `*ROUTE` constants. C-07 gained the second heuristic trap (same-file call sites). F-04 closed: **0
  unreachable screens out of 45** by symbol-level check.
- 2026-09-18 (round 3) — **C-03 fixed at the file level**: `values-ar/max_screen_strings.xml` (177) and
  `values-ar/max_design_strings.xml` (11) created; every EN string file now has an Arabic counterpart, verified by
  key + format-specifier parity. Three new resource gates added to `docs/ai/VALIDATION.md` §3 so the gap cannot
  reappear silently.
- 2026-09-18 (round 2) — **C-05 FIXED** (the two decorative engines moved into `ui/component/`; the duplicate
  package is gone and 5 imports were rewritten after a symbol + collision check). **C-08 resolved as
  documentation-only** — the placeholder is load-bearing, and the fix is explicitly *not* to edit `module.prop`.
  **C-12 closed** (0 inline `composable(`, single `maxNavGraph` call). **C-09 partially fixed** (audit documents
  marked superseded, headers added to both scripts, the inert one labelled). Also corrected the attribution in
  C-08: `module.prop` is rewritten by `compile_zip.sh`, not by the daemon-version CI step.
- 2026-09-18 (later) — corrections after symbol-level verification: **C-02 and C-04 are FIXED** (Crowdin now
  registers all six files + ADR-26; tracked junk removed and `.gitignore` extended, contract moved to
  `docs/ai/ENGINEERING-CONTRACT.md`). **C-07 was materially wrong** and is rewritten: the legacy-named files are
  load-bearing (71 call sites) and would have been deleted by a filename-based cleanup. **C-03 split into two
  gaps** (main-file parity vs 188 keys with no Arabic file at all).
- 2026-09-18 — rebuilt from scratch. **New findings** not present in the 2026-09-16 analysis: C-01 (toolchain
  versions changed), C-02 (Crowdin wired to one of six string files), C-05 counts re-derive, C-08 (`module.prop`
  placeholder `V1` vs `version` 5.2). Every item names its owning team role.

<details>
<summary>Evidence</summary>

```sh
gradle --version && java -version && ls -l manager/gradlew | cut -c1-11
grep -nE '^\s+- source|translation' crowdin.yml ; ls manager/app/src/main/res/values/*.xml
grep -c '<string ' manager/app/src/main/res/values/strings.xml manager/app/src/main/res/values-ar/strings.xml
find manager/app/src -name '*.bak' -o -name '*.backup' ; git ls-files .serena | wc -l
S=manager/app/src/main/java/nd/max; ls $S/ui/component | wc -l ; ls $S/ui/components | wc -l
grep -rl 'nd.max.ui.design' $S/ui | wc -l ; grep -rl 'Scaffold(' $S/ui | wc -l
wc -l $S/ui/mainscreens/{HomeScreen,HomeDashboardComponents,LegendaryHomeDashboard,LegacyTweakComponents}.kt
grep -n 'version' mainfiles/module.prop ; cat version
```
</details>
