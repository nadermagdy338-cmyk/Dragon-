# Technology Stack

**Analysis Date:** 2026-09-18 (rebuilt from the current tree)

## Languages

| Language | Where | Size |
| --- | --- | --- |
| Kotlin 2.3.10 | `manager/app/src/main/java/nd/max/**` (JVM 17 target) | **235 files / 62,660 LOC** |
| Rust (edition 2024) | `thermalcore/src` 16 files / 1,825 LOC · `binprofiles/src` 10 / 2,167 · `binutils/src` 3 / 501 | **29 files / 4,493 LOC** |
| C (NDK) | `archdaemon/jni` (`sys.maxmanager-service`, 14 components), `preloadbin/jni` | 36 `.c`/`.h` |
| POSIX shell | `mainfiles/*.sh` (8 scripts) + `.github/scripts/*.sh` (5) | module install/boot lifecycle |
| Python | `tools/repo_audit.py` (static audit) · `tools/i18n_coverage.py` (locale gate) · `tools/code_health.py` (cleanliness audit) | 3 files |
| Gradle Kotlin DSL | `manager/*.gradle.kts`, `manager/gradle/libs.versions.toml` | build config |

<details>
<summary>Evidence</summary>

```sh
find manager/app/src/main/java/nd/max -name '*.kt' | wc -l                      # 235
find manager/app/src/main/java/nd/max -name '*.kt' -exec cat {} + | wc -l      # 62660
for c in thermalcore binprofiles binutils; do find $c/src -name '*.rs' -exec cat {} + | wc -l; done
find archdaemon preloadbin -name '*.c' -o -name '*.h' | wc -l                  # 36
ls mainfiles/*.sh | wc -l                                                       # 8
```
</details>

## Runtime

- **Android 11+**: `minSdk 29`, `compileSdk 36`, `targetSdk 36` (API 37 deliberately not adopted).
- **ABIs**: `arm64-v8a`, `armeabi-v7a`.
- **Distribution**: systemless Magisk/KernelSU module (`module.json`: `metamodule: false`) that installs the APK
  as a priv-app; module payload lives in `mainfiles/`.
- **Cargo** for the three Rust crates (lockfiles committed): `rianixia-thermalcore` 2.0.0,
  `maxmanager-profilesettings` 1.0.0, `maxmanager-utilityconf` 1.0.0.

## Frameworks & key versions

From `manager/gradle/libs.versions.toml`:

| Component | Version |
| --- | --- |
| AGP | 9.2.0 |
| Kotlin / KSP | 2.3.10 / 2.3.10 |
| Compose BOM | 2025.10.01 |
| Material3 | 1.4.0-alpha15 |
| Hilt (Dagger) | 2.59.2 |
| Navigation Compose | 2.9.4 |
| activity-compose | 1.11.0 |
| libsu (topjohnwu) | 6.0.0 |
| Coil | 2.7.0 |
| Haze (glassmorphism) | 2.0.0-alpha02 |
| JUnit | 4.13.2 |

Also present: `kotlinx-serialization`, `compose-markdown` 0.7.2, AndroidANSI, material-kolor, uCrop, Media3,
StringFog (release string obfuscation), hiddenapibypass (LSPosed).

Vendored modules inside the Gradle build: `manager/terminal-emulator` (`com.termux.terminal`),
`manager/terminal-view`, `manager/kernel-flasher`.

## Build configuration

- **Signing**: release requires `KS_PWD`; `manager/app/build.gradle.kts` throws
  `GradleException("KS_PWD must be set to produce a signed release artifact")` when absent (fail-fast by design).
- **Version single-sources**: `version` (`5.2`) + `version_type` (`Dazzling`) → sync into `module.prop`,
  daemon binary, and `update.json` (`versionCode` 1823).
- **CI**: `.github/workflows/build.yml` — verify.sh → changelog.sh → determine build type → sync daemon version →
  NDK setup + ccache → compile archdaemon → compile preloadbin → Rust toolchain + cargo-ndk → build thermalcore,
  profilesettings, utility → JNI native library → compile flashable zip → package developer integration bundle →
  validate artifacts → upload artifacts (+ retry) → Telegram upload.
- `.gitignore` excludes the CI-built `libmaxmanager_native.so`; `libtermux.so` is committed on purpose.

## Toolchain reality in this sandbox (verified 2026-09-18)

| Tool | State | Consequence |
| --- | --- | --- |
| JDK | OpenJDK **25.0.4.1** | available |
| `gradle` on PATH | **9.7.0** (was 4.4.1 on 2026-09-15) | now ≥ the wrapper's requirement |
| `manager/gradle/wrapper/gradle-wrapper.properties` | `gradle-9.5.1-bin.zip` | wrapper version is no longer newer than PATH |
| `manager/gradlew` | mode `-rw-rw----` — **not executable** | must be invoked as `sh gradlew` or `chmod +x` first |
| `cargo` / `rustc` | **not installed** | Rust crates cannot be checked locally |
| `shellcheck` | **not installed** | shell linting limited to `bash -n` |

So the earlier blanket claim «a Gradle build cannot run here» is now **outdated as a version conflict** — what
remains is: non-executable wrapper, unknown offline dependency availability, and no Rust/NDK toolchain.
`docs/ai/VALIDATION.md` static gates remain the contract until someone actually attempts a build and records
the output.

## Platform requirements

- **Development**: JDK 17 target (JDK 25 present here works for compilation targets), Android SDK 36,
  NDK for the C daemons, Rust with Android cross-compile targets, `cargo-ndk`, ccache (CI).
- **Production**: Magisk / KernelSU / KernelSU Next; SELinux policy `android/aosp/sepolicy/maxmanager.te`;
  init rc `android/aosp/maxmanager.rc`; optional `android/kernelsu/` variant.

## Changelog

- 2026-09-18 — rebuilt: corrected Kotlin file/LOC counts (235/62,660), Rust LOC split per crate, 8 `mainfiles`
  scripts, 84 locale folders, and added the verified toolchain table above (gradle 9.7.0 on PATH).
