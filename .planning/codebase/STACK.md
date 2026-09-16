# Technology Stack

**Analysis Date:** 2026-09-16

## Languages

**Primary:**
- Kotlin 2.3.10 — Manager Android app (`manager/app/`, ~238 .kt files), JVM 17 target
- Rust 2024 edition — Native binaries: `thermalcore/` (thermal daemon, ~most of 4,493 LOC), `binprofiles/` (chip-aware profile setter), `binutils/` (utility binaries)

**Secondary:**
- C (NDK) — Root daemons: `archdaemon/jni/` (sys.maxmanager-service), `preloadbin/jni/` (game lib preloader)
- POSIX shell — Magisk module lifecycle: `mainfiles/*.sh` (customize.sh, service.sh, post-fs-data.sh, action.sh, preferenced-tweaks.sh, props.sh, verify.sh, uninstall.sh)
- Python — `fix_tweak.py` (one-off repo tooling)
- Gradle Kotlin DSL — build config (`manager/*.gradle.kts`, `manager/gradle/libs.versions.toml`)

## Runtime

**Environment:**
- Android 11+ (minSdk 29), target/compile SDK 36 (API 37 deliberately avoided — not published on CI's SDK repo)
- ABI targets: arm64-v8a, armeabi-v7a
- Runs as Magisk module systemlessly: priv-app at `system/product/priv-app/MaxManager/MaxManager.apk`

**Package Manager:**
- Gradle (wrapper `manager/gradlew`) with version catalog `manager/gradle/libs.versions.toml`
- Cargo for all three Rust crates (lockfiles committed)

## Frameworks

**Core:**
- Jetpack Compose (BOM 2025.10.01) + Material3 1.4.0-alpha15 — entire UI, single-activity (`MainActivity.kt`)
- Hilt 2.59.2 (Dagger, KSP) — DI: `core/di/AppModule.kt`, `DataModule.kt`
- Navigation Compose 2.9.4 — `ui/navigation/MaxNavGraph.kt`, bottom bar in `MaxNavBar.kt`
- libsu 6.0.0 (topjohnwu) — root shell access throughout (`RootFileAccess.kt`, MaxAiEngine)

**Testing:**
- JUnit 4.13.2 + androidx.test.ext — 13 JVM unit tests in `manager/app/src/test/java/nd/max/`
- No instrumented tests, no Compose UI tests

**Build/Dev:**
- AGP 9.2.0, KSP 2.3.10, kotlin-parcelize, kotlinx-serialization 1.11.0
- StringFog 5.1.0 (XOR 5.0.0) buildscript plugin — string obfuscation for release APK
- GitHub Actions `.github/workflows/build.yml` — full build: verify.sh → changelog.sh → version detection → daemon version sync → APK + Rust cross-compile → `compile_zip.sh` module zip

## Key Dependencies

**Critical:**
- libsu 6.0.0 — all root operations; the app is useless without root
- hiddenapibypass 6.1 (LSPosed) — hidden Android API access
- Haze 2.0.0-alpha02 — glassmorphism UI effects
- material-kolor 5.0.0-alpha07 — dynamic color theming
- compose-markdown 0.7.2, AndroidANSI (Fox2Code) — markdown rendering + terminal escape sequences
- Termux terminal-emulator — vendored as `manager/terminal-emulator` module (`com.termux.terminal` namespace)

**Infrastructure:**
- Coil 2.7.0 + appiconloader-coil — app icons/images
- Media3 1.3.0 — media features
- uCrop 2.2.11-native — image cropping
- Rust: nix, inotify, serde/bincode, sysinfo (thermalcore); glob (binutils/binprofiles)

## Configuration

**Environment:**
- `KS_PWD` env var required for release signing (`manager/app/azenith.jks`, alias `azenith_key`) — build fails fast without it
- Module version from `version` + `version_type` repo files → synced into module.prop and daemon binary

**Build:**
- `manager/build.gradle.kts` (buildscript for StringFog), `manager/app/build.gradle.kts`
- `manager/gradle/libs.versions.toml` — single source of dependency versions
- `.gitignore` excludes generated `libmaxmanager_native.so` (built by CI JNI step); `libtermux.so` is a deliberate committed exception

## Platform Requirements

**Development:**
- JDK 17, Android SDK 36, Android NDK (C daemons), Rust toolchain with Android cross-compile targets
- Release builds additionally need keystore + KS_PWD

**Production:**
- Distributed as a Magisk module zip (id=MaxManager) installed via KernelSU/Magisk/KernelSU Next
- APK is installed systemlessly as priv-app by `customize.sh`; `android/kernelsu/` variant for KSU-specific flows
- SELinux policy shipped at `android/aosp/sepolicy/maxmanager.te` + `file_contexts`; init rc at `android/aosp/maxmanager.rc`

---

*Stack analysis: 2026-09-16*
*Update after major dependency changes*
