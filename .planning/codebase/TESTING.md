# Testing Patterns

**Analysis Date:** 2026-09-16

## Test Framework

**Runner:**
- JUnit 4.13.2 (`libs.versions.toml`: junit = "4.13.2")
- Config: standard Gradle test source set; no custom test config file

**Assertion Library:**
- JUnit 4 built-in asserts (`assertEquals`, `assertTrue`, `assertNull`, `assertNotEquals`)

**Run Commands:**
```bash
cd manager && ./gradlew test          # All JVM unit tests
cd manager && ./gradlew :app:testDebugUnitTest --tests "nd.max.core.maxai.*"   # One package
cd manager && ./gradlew :app:testDebugUnitTest --tests "nd.max.core.hardware.HardwareControlArbiterTest"  # One class
```

## Test File Organization

**Location:**
- `manager/app/src/test/java/nd/max/` — JVM unit tests only, mirrored package structure

**Naming:** `*Test.kt` matching class under test

**Structure:**
```
manager/app/src/test/java/nd/max/
├── core/
│   ├── diagnostics/DiagnosticCenterTest.kt
│   ├── hardware/            # 4 tests: ControlPlaneArchitecture, GpuControlModel,
│   │                        #   HardwareControlArbiter, ManualControlLocks
│   └── maxai/               # 6 tests: ControlOutcomeModel, ControlRegistry,
│                            #   MaxAiJournalCodec, MinimalPlanner, Objective, ResponseModel
│   └── recommendation/RecommendationTextClassifierTest.kt
└── ui/viewmodel/HomeTemperaturePolicyTest.kt
```

**Coverage shape:** 13 test files, all targeting `core/` logic + one ViewModel policy test. Zero tests for: UI/composables, shell scripts, Rust crates, C daemons, JNI bridges.

## Test Structure

**Patterns (from HardwareControlArbiterTest.kt / MinimalPlannerTest.kt style):**
- Plain JUnit 4: `@Test` methods, no `describe` nesting (flat per-class suites)
- arrange/act/assert inline without comments
- Constructor-instantiated pure Kotlin objects — core layer is deliberately testable without Android framework
- No Robolectric, no instrumented tests (`androidTest/` absent)

## Mocking

**Framework:** No mocking library in the version catalog (no Mockito/MockK) — tests exercise real pure-Kotlin logic with hand-built fakes/stubs where needed

**What to Mock (by hand):** file-system boundaries, `RootFileAccess` interactions — kept behind interfaces so tests inject fakes

**What NOT to Mock:** arbiter/planner/journal logic — tested for real (that's the point of the pure core)

## Fixtures and Factories

- Inline data construction in test files; no shared fixture directory, no factory framework

## Coverage

**Requirements:** None enforced; CI (`.github/workflows/build.yml`) builds and verifies, test gate presence depends on workflow steps — verify before assuming tests block merges

**View Coverage:** not configured

## Test Types

**Unit Tests:** JVM-only, fast, framework-free — the sole test type

**Integration Tests:** none in repo; hardware interactions validated on-device by maintainers

**Rust/C testing:** thermalcore has a `simulator` cargo feature + `simulator.rs` for desktop runs (`cargo run --features simulator`); no automated Rust tests detected; C daemons untested

## Common Patterns

**Architecture enforcement tests:** `ControlPlaneArchitectureTest.kt` — tests constrain the design itself (arbiter-mediated writes must stay the only path). Preserve this pattern: when adding control-plane features, extend architectural tests rather than bypassing them.

**Codec round-trip tests:** `MaxAiJournalCodecTest.kt`, `ResponseModelTest.kt` — serialize/deserialize equality for persistence formats

**Error Testing:** invalid/edge inputs asserted via plain JUnit asserts (no `assertThrows` convention observed)

## Gaps to Know When Planning

- No test infrastructure for: Compose UI, ViewModels (except one), shell scripts, Rust, C, JNI
- Adding first tests in those areas = setting the pattern; follow the pure-core/extracted-interface approach the existing 13 tests already use

---

*Testing analysis: 2026-09-16*
*Update when test patterns change*
