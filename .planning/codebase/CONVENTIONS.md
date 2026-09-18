# Coding Conventions

**Analysis Date:** 2026-09-18 (rebuilt from the current tree)

Observed conventions, not aspirations. Where the code is inconsistent, the inconsistency is stated and the
direction of travel is named — new code should follow the target, not the majority.

## Naming

**Files**
- Kotlin: PascalCase matching the primary class (`HardwareControlArbiter.kt`).
- ViewModels: `*ViewModel.kt` is the target. **5 of 22 files still use legacy `*Viewmodel.kt`**
  (`AppSettingsViewmodel`, `ApplistViewmodel`, `HomeViewmodel`, `SettingViewmodel`, `TweakViewmodel`) —
  rename opportunistically when touching them; do not open a rename-only task.
- Compose screens: `<Name>Screen.kt` with a top-level `fun <Name>Screen(...)` (37 files declare one).
- Rust: snake_case modules (`policy_manager.rs`); C: PascalCase components + `Main.c`;
  shell: lowercase, hyphenless (`post-fs-data.sh`).
- Tests: `*Test.kt` mirroring the source package under `src/test/java/nd/max/`.

**Code**
- Functions/variables: lowerCamelCase (`applyProfile`, `resolveCapabilities`); constants in Kotlin via `val`;
  Rust constants `SCREAMING_SNAKE_CASE` (`thermalcore/src/constants.rs`).
- Types: PascalCase, no `I` prefix. Control-plane records are data classes
  (`HardwareControlArbiter.Request` / `.Result`).
- Resource strings: lowercase snake_case, prefixed by feature (`max_<screen>_<purpose>`), format specifiers
  identical between EN and AR (`%1$s`, `%2$d`).

## Style

- Kotlin: 4-space indent, standard Android/Kotlin style. **No ktlint/spotless configured** — match the
  surrounding file; do not introduce a formatter.
- Strings: double quotes; Compose text must come from `stringResource`, never a literal.
- KDoc on public classes with the *why* (`HardwareControlArbiter`, `MaxAiEngine` are the reference style).
- Comments are frequently in **Arabic or Indonesian** for kernel quirks and vendor workarounds — that is the
  house style, not noise. Keep the language of the surrounding block when editing it.
- `TODOs`: no `TODO` markers under `core/`; debt is tracked in `KNOWN_ISSUES.md` / this folder's
  `CONCERNS.md`. Add debt there rather than as a new comment.

## Import organization

Observed order (see `MaxAiEngine.kt`):
1. Android framework (`android.*`)
2. Third-party (`com.topjohnwu.superuser`, `kotlinx.*`, `dagger.hilt.*`)
3. `javax.inject` / `java.*`
4. Project (`nd.max.*`)
5. `kotlin.*`

No path aliases. No wildcard imports observed in the core layer.

## Architecture rules that also read as conventions

- **All hardware writes** go through `HardwareControlArbiter`; backends take the shape of
  `CpuHardwareBackend.kt`; a new knob gets an entry in `HardwareControlKey.kt`.
- **Capability before control**: probe via `HardwareCapabilityResolver`; a control with no proven node must not
  render as an enabled switch, and a disabled control always carries `lockedReason`.
- **No direct root I/O in UI**: no `RootFileAccess` / `Shell.cmd` / `su -c` under `ui/**`.
- **Routes**: add to `ui/navigation/MaxDestinations.kt`; literal `navigate("…")` outside that package is a
  gate violation.
- **Design language**: import `nd.max.ui.design`; do not declare a `Scaffold` in a screen file; the legacy
  `ui/component/MaxDesignSystem` must not be newly imported.
- **Truthfulness**: unknown is `status_unknown`, stale is a `Snapshot`, unsupported is `Unsupported`; never
  format a fallback as a live reading.
- **Localization**: every new user-visible string lands in `values/` **and** `values-ar/` in the same change.

## Error handling

- libsu results are checked; hardware writes are read back and compared, and a mismatch is surfaced with a
  rollback attempt.
- Unreadable sysfs returns `null` rather than throwing; failures are expected on unknown devices.
- Capability probing happens before writing, so unsupported paths are skipped rather than attempted.
- Fail-fast is reserved for build configuration (missing `KS_PWD` → `GradleException`).
- Rust: `Result` with `nix` error types; the daemon loop logs and continues past per-device errors.
- Shell: guard clauses (`[ ! -f "$2" ] && echo … && exit 1`), idempotent helpers, `readonly` for paths.

## Logging

- App: `android.util.Log` + `core/diagnostics/DiagnosticCenter.kt` + the `ui/util` event log.
- Daemons: `archdaemon/jni/src/SystemLogger`; thermalcore keeps a serde/bincode state journal.
- Log control-plane *outcomes* (winner / applied / actual / verified), never secrets, tokens, or keystore paths.
- User-facing diagnostics belong in `DiagnosticCenter` (it is unit-tested) rather than ad-hoc toasts.

## Function & module design

- Newer code favours small focused classes — the `core/maxai/` split (`MinimalPlanner`, `TrustModel`,
  `SafetyGovernor`, `ControlRegistry`) is the reference; older engines such as `MaxAiEngine` are large because
  they were grown, not because size is the convention.
- Multi-field inputs are grouped in data classes (the arbiter `Request` pattern); dependencies are injected with
  `@Inject constructor()` and bound as `@Singleton` in `core/di/AppModule.kt` / `DataModule.kt`.
- Data sources are interface + Impl (`data/datasources/HardwareDataSourceImpl.kt`).
- JNI stays inside `core/jni/` — nowhere else.
- Rust uses a mod-tree with `mod.rs` and feature gates (`simulator`).

## Shell conventions (`mainfiles/`)

- `customize.sh` uses `SKIPUNZIP=1` and manual extraction.
- Reuse the helpers instead of copy-paste: `make_node()` (idempotent node creation) and
  `set_default_prop()` (property defaults with documented contracts).
- Tab indentation; a commented header block at the top. Most scripts carry the Apache-2.0 block
  (8 of 13 shell files repo-wide mention Apache) but the copyright line differs between maintainers
  (`Zexshia`, `Rem01Gaming`) — keep the header shape of the file you edit rather than normalizing it.
- Per-prop behaviour contracts are documented as block comments (`customize.sh`, `preferenced-tweaks.sh`).

## Changelog

- 2026-09-18 — rebuilt: ViewModel inconsistency corrected to 5 of 22 (was "4 legacy"), added the architecture
  rules that now function as conventions (arbiter, registry routes, design language, AR parity), and replaced
  the inaccurate "Apache-2.0 header on every script" claim with the observed header reality.

<details>
<summary>Evidence</summary>

```sh
ls manager/app/src/main/java/nd/max/ui/viewmodel | grep -c 'Viewmodel\.kt'   # 5
grep -rl '^fun .*Screen(' manager/app/src/main/java/nd/max/ui | wc -l      # 37
grep -l 'Apache' mainfiles/*.sh .github/scripts/*.sh | wc -l               # 8 of 13
grep -rn 'make_node\|set_default_prop' mainfiles/customize.sh | head
grep -rn 'TODO' manager/app/src/main/java/nd/max/core || echo "no TODO in core"
```
</details>
