# Codebase Concerns

**Analysis Date:** 2026-09-16

## Tech Debt

**Tracked backup/log/junk files in git:**
- Issue: `CLAUDE.md.bak`, `LegendaryHomeDashboard.kt.backup`, `manager/.../HomeDashboardComponents.kt.backup`, `build_compile_latest.log`, `build_compile_latest2.log`, `build_test.log`, `.serena/cache/` (LSP cache incl. `intellij-server.log`) are committed
- Why: ad-hoc commits ("Update MaxManager" ×many) without cleanup hygiene
- Impact: repo noise; `.backup` files can drift from real sources and confuse greps/agents; a tracked LSP log can bloat
- Fix approach: delete backups/logs from index, add `*.backup`, `*.bak`, `*.log`, `.serena/` to `.gitignore`; also the stale `CLAUDE.md.bak` suggests an old CLAUDE.md workflow

**Two component directories (`ui/component/` + `ui/components/`):**
- Issue: 36 files in `ui/component/`, 2 in `ui/components/` — parallel homes for composables
- Why: historical split never reconciled
- Impact: "where does this composable go?" ambiguity; duplicate-adjacent naming
- Fix approach: merge `components/` into `component/` (or pick one) in a mechanical rename phase

**ViewModel suffix inconsistency:**
- Issue: `*Viewmodel.kt` (4 files) vs `*ViewModel.kt` (18 files)
- Impact: minor; rename during touch (don't dedicate a phase)
- Fix approach: opportunistic renames; standard is `*ViewModel.kt`

**Version string spread across 4 files:**
- Issue: `version`, `version_type`, `update.json` (version + versionCode), `module.prop` must all agree; CI "Sync Daemon Version String" must run before archdaemon compiles
- Why: Magisk manager compatibility requires update.json + module.prop versioning
- Impact: manual release bump misses = self-failing module (`verify.sh` catches baked-in `MODULE_VERSION` mismatch post-release)
- Fix approach: single-source via CI generation from `version`/`version_type` (partially exists — could be completed)

**Duplicated version-guard logic in app build.gradle.kts:**
- Issue: KS_PWD release check implemented twice (GradleException at config time + `doFirst check()` on package tasks)
- Why: belt-and-suspenders during a signing migration
- Impact: low; harmless redundancy
- Fix approach: consolidate when next touching signing config

## Known Bugs

(None explicitly recorded in repo issue tracker; historical fixes visible in `changelog.md`, e.g. "profiler failed to apply in some devices", "false positive in bypass charge diagnostic" — both claimed fixed in 5.2.)

## Security Considerations

**Root shell surface is the trust boundary:**
- Risk: any injection into shell command strings from app settings = full device compromise; user-controlled strings (app names, file paths) reaching libsu commands
- Current mitigation: control-plane arbiter with fixed control keys; capability probing; SELinux policy (`android/aosp/sepolicy/maxmanager.te`) constrains daemon; StringFog obscures release strings (not a security control)
- Recommendations: audit any new code that interpolates values into shell commands; prefer file/prop-based IPC over shell string building; keep SELinux domains tight when adding daemon features

**Telegram CI token:**
- Risk: release zip exfiltration if BOT_TOKEN leaked
- Current mitigation: GitHub secret, not in repo (verified: only `$BOT_TOKEN` reference in `telebot.sh`)
- Recommendations: keep as-is; least-privilege chat

**Keystore in repo dir:**
- Risk: `manager/app/azenith.jks` referenced by build; if the keystore file itself is ever committed, signing identity leaks
- Current mitigation: password via `KS_PWD` secret; keystore presence in repo unverified (gitignored?)
- Recommendations: confirm `.jks` is gitignored; rotate if it ever landed in history

## Performance Bottlenecks

**`preferenced-tweaks.sh` prop blast:**
- Problem: 58+ `resetprop` calls at boot; linear shell startup cost on every boot
- Cause: sequential prop writes with no batching
- Improvement path: skip no-op writes when prop already matches default (partially handled by `set_default_prop`)

**App cold start with root probing:**
- Problem: capability resolution + app-list load on UI entry (`HardwareCapabilityResolver`, `ApplistViewmodel`, icon loading for hundreds of apps)
- Cause: shell round-trips per probe; icon decode on main path
- Improvement path: cache capabilities per boot; already partially addressed (per-app refresh rates were removed from per-app settings in 5.2 for this reason)

## Fragile Areas

**HardwareControlArbiter + ManualControlLocks (core/hardware/):**
- Why fragile: concurrency + cross-process correctness; the arbiter's journal/lock/read-back sequence is load-bearing for device stability
- Common failures: any bypass path added ad-hoc reintroduces MaxAI-vs-manual clobbering
- Safe modification: extend via arbiter API only; keep `ControlPlaneArchitectureTest.kt` green; add tests for new keys
- Test coverage: yes (4 tests) — extend, don't work around

**CI daemon-version sync ordering:**
- Why fragile: daemon `check_module_version()` compares baked-in string vs module.prop; any build-script reorder breaks module self-verify
- Safe modification: never compile archdaemon before the sync step; document when editing build.yml

**customize.sh SKIPUNZIP=1 manual extraction:**
- Why fragile: installer manually unzips and places priv-app/binaries; a missed path = broken module on-device with no CI detection
- Safe modification: test on real device + `verify.sh` after any change

**compileSdk 36 ceiling comments:**
- Why fragile: several deps pinned by "SDK-37 not available" comments (material3 alpha, haze alpha); SDK 37 availability will make these pins stale
- Safe modification: when upgrading, revisit the whole pinned set together

## Scaling Limits

(Not applicable — single-user, single-device runtime. Repo scale: 750 tracked files; manager dir 56M incl. build outputs.)

## Dependencies at Risk

**Alpha-pinned UI stack:**
- Risk: `material3 1.4.0-alpha15`, `haze 2.0.0-alpha02`, `material-kolor 5.0.0-alpha07` — alpha churn, breaking API changes
- Impact: upgrades require coordinated bumps
- Migration plan: bump together against Compose BOM 2025.10.01; verify compileSdk 36 AAR metadata

**AGP 9.2.0 / Kotlin 2.3.10 bleeding edge:**
- Risk: very new toolchain; companion pinned libs (KSP) must move in lockstep
- Migration plan: bump Kotlin+KSP+Compose-compiler atomically

**Vendored Termux terminal:**
- Risk: `terminal-emulator`/`terminal-view` vendored from Termux; security fixes upstream won't arrive automatically
- Impact: low (isolated terminal feature)
- Migration plan: periodic upstream sync check

## Missing Critical Features

**Rust/C test infrastructure:**
- Problem: thermalcore (4.5k LOC, safety-critical throttling logic) has simulator feature but no automated tests; archdaemon untested
- Blocks: confident refactoring of thermal policy
- Complexity: medium — thermalcore simulator makes `cargo test` feasible

**No user-facing docs beyond README:**
- Problem: `docs/` covers aegis/ai subsystems only; tweak props undocumented
- Blocks: contributor onboarding for script layer
- Complexity: low-medium

## Test Coverage Gaps

**Shell script layer (mainfiles/):**
- What's not tested: install flow, prop defaults, verify.sh
- Risk: broken release zip ships to users (CI verifies only)
- Priority: High — every release passes through here
- Difficulty: shell test harness needed (e.g., run customize.sh against fake root fs)

**Rust thermal policy:**
- What's not tested: learning/prediction/cooling logic
- Risk: regressions in throttle behavior unnoticeable until device testing
- Priority: Medium-High (simulator exists — leverage it)
- Difficulty: low-medium via simulator feature

**ViewModels/UI:**
- What's not tested: 21 of 22 ViewModels, all composables
- Risk: UI regressions found only manually
- Priority: Medium
- Difficulty: needs Robolectric or extraction of pure logic (follow HomeTemperaturePolicyTest.kt pattern)

---

*Concerns audit: 2026-09-16*
*Update as issues are fixed or new ones discovered*
