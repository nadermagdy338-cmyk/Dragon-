# Coding Conventions

**Analysis Date:** 2026-09-16

## Naming Patterns

**Files:**
- Kotlin: PascalCase file = primary class (`HardwareControlArbiter.kt`)
- ViewModels: `*ViewModel.kt` dominant; legacy `*Viewmodel.kt` exists (`SettingViewmodel.kt`, `HomeViewmodel.kt`, `ApplistViewmodel.kt`, `TweakViewmodel.kt`) — new files should use `*ViewModel.kt`
- Rust: snake_case (`policy_manager.rs`); C: PascalCase components + `Main.c`
- Tests: `*Test.kt` under mirrored package (`core/hardware/HardwareControlArbiterTest.kt` → `src/test/.../core/hardware/`)

**Functions:** lowerCamelCase (`applyProfile`, `resolveCapabilities`); KDoc on public classes

**Variables:** lowerCamelCase; constants via `val` (Kotlin) or `static final`-style; Rust `SCREAMING_SNAKE_CASE` for consts (`thermalcore/src/constants.rs`)

**Types:** PascalCase, no `I` prefix; Kotlin data classes for control-plane records (e.g., `HardwareControlArbiter.Request/.Result`)

## Code Style

**Formatting:**
- Kotlin: standard Android/Kotlin style, 4-space indent; no repo-wide formatter config detected (no ktlint/spotless) — match surrounding code
- Comments: KDoc headers on major classes explaining the *why* (see `HardwareControlArbiter.kt`, `MaxAiEngine.kt`); inline comments frequently explain kernel-level rationale; some comments in Arabic and Indonesian (multilingual maintainer base)
- Strings: double quotes (Kotlin), `readonly`/`local` discipline in shell scripts

**Linting:** R8/minify + `proguard-rules.pro` for release; no static analysis tooling configured — rely on unit tests + `ControlPlaneArchitectureTest.kt` style architectural tests

## Import Organization

**Order (observed, e.g. `MaxAiEngine.kt`):**
1. Android framework (`android.*`)
2. Third-party libs (`com.topjohnwu.superuser`, `kotlinx.*`, `dagger.hilt.*`)
3. `javax.inject` / `java.*`
4. Project imports (`nd.max.*`)
5. `kotlin.*` last

- No path aliases; plain package imports

## Error Handling

**Patterns:**
- Shell results checked via libsu; read-back verification after hardware writes (apply → read → compare → restore on mismatch)
- Null-return on unreadable sysfs nodes rather than exceptions; capability probing first (`HardwareCapabilityResolver.kt`)
- Fail-fast only for build config errors (`GradleException("KS_PWD must be set...")`)
- Rust: `Result` flows with `nix` error types; daemon loop continues past per-device errors
- Shell scripts: guard clauses (`[ ! -f "$2" ] && echo ...`), `make_node` idempotency helper

## Logging

**Framework:**
- App: `android.util.Log` + `core/diagnostics/DiagnosticCenter.kt` + `ui/util/EventLog`
- Daemons: `archdaemon/jni/src/SystemLogger/`, thermalcore internal state journal (bincode/serde)

**Patterns:** log control-plane outcomes (winner/actual/applied), never log secrets; user-facing diagnostics via DiagnosticCenter (tested)

## Comments

**When to Comment:**
- Explain why: race conditions, kernel quirks, vendor prop workarounds
- KDoc on every singleton service class with safety semantics
- Shell: block comments documenting per-prop behavior contracts (`customize.sh` set_default_prop docs)

**TODO Comments:** None found in `core/` (grep clean) — debt is tracked implicitly, see CONCERNS.md

## Function Design

**Size:** Core engine classes are large (MaxAiEngine ~several hundred lines, many responsibilities) — newer code favors smaller focused classes (see `core/maxai/` split: MinimalPlanner, TrustModel, SafetyGovernor)

**Parameters:** data-class grouping for multi-field requests (arbiter `Request` pattern); constructor injection with `@Inject constructor()`

**Return Values:** early returns; explicit nullability for probe failures; Kotlin `Result` sparingly

## Module Design

**Exports:** Hilt singletons via `@Singleton class X @Inject constructor()`; DI bound in `core/di/AppModule.kt` / `DataModule.kt`
- Interface + Impl for data sources (`data/datasources/HardwareDataSourceImpl.kt`)
- JNI boundaries isolated in `core/jni/` only
- Rust: mod-tree with `mod.rs`; features gates (`simulator`)

## Shell Script Conventions (mainfiles/)

- `SKIPUNZIP=1` + manual extraction in customize.sh
- Helper functions (`make_node`, `set_default_prop`) collapse repeated patterns — extend these, don't copy-paste
- `readonly` for paths; tab indentation; Apache-2.0 header on every script

---

*Convention analysis: 2026-09-16*
*Update when patterns change*
