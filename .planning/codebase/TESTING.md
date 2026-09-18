# Testing Patterns

**Analysis Date:** 2026-09-18 (rebuilt from the current tree)

## In one line

14 JVM unit tests, all targeting framework-free logic under `core/` (plus two UI/model helpers); **no
instrumented tests, no Compose UI tests, no Rust/C/shell tests, no mocking library**.

## Runner & commands

- **JUnit 4.13.2** (from `manager/gradle/libs.versions.toml`), plain `@Test` methods, JUnit assertions only
  (`assertEquals`, `assertTrue`, `assertNull`, `assertNotEquals`). No Robolectric, no MockK/Mockito.

```sh
cd manager && ./gradlew test                                   # all JVM unit tests
cd manager && ./gradlew :app:testDebugUnitTest --tests 'nd.max.core.*'
cd manager && ./gradlew :app:testDebugUnitTest --tests 'nd.max.core.hardware.HardwareControlArbiterTest'
```

⚠️ In this sandbox the wrapper is **not executable** (`-rw-rw----`) and there is no guarantee the distribution
is cached, so invoke `sh gradlew …` or `chmod +x manager/gradlew` and record the exact output. Until a build is
actually observed to pass, report "compilation unverified in this environment" (ADR-15).

## The 14 test files

| Package | File | What it protects |
| --- | --- | --- |
| `core/hardware/` | `ControlPlaneArchitectureTest.kt` | **the architecture itself** — arbiter-mediated writes must stay the only path |
| | `HardwareControlArbiterTest.kt` | arbitration, journaling, verify-after-write |
| | `ManualControlLocksTest.kt` | manual intent wins per knob |
| | `GpuControlModelTest.kt` | GPU control model semantics |
| `core/maxai/` | `MinimalPlannerTest.kt` | planning decisions |
| | `ControlOutcomeModelTest.kt` | outcome recording |
| | `ControlRegistryTest.kt` | canonical knob registry |
| | `MaxAiJournalCodecTest.kt` | journal serialization round-trip |
| | `ObjectiveTest.kt` | objective scoring |
| | `ResponseModelTest.kt` | response serialization round-trip |
| `core/diagnostics/` | `DiagnosticCenterTest.kt` | user-facing diagnostics |
| `core/recommendation/` | `RecommendationTextClassifierTest.kt` | classifier behaviour |
| `ui/mainscreens/` | `ControlLayoutModelTest.kt` | layout model logic extracted out of the composable |
| `ui/viewmodel/` | `HomeTemperaturePolicyTest.kt` | temperature policy decision logic |

The set that must stay green when a toolchain becomes available: `ControlPlaneArchitectureTest`,
`HardwareControlArbiterTest`, `ManualControlLocksTest`, `MinimalPlannerTest`, `ControlOutcomeModelTest`,
`DiagnosticCenterTest`.

## Patterns to follow

- **Pure-core discipline**: the control plane and Max AI engine are written so they can be tested without the
  Android framework (constructor-injected collaborators, interface boundaries). Keep it that way.
- **Architecture enforcement tests**: `ControlPlaneArchitectureTest` constrains the design, not just behaviour.
  When adding a control-plane feature, extend the architectural test rather than bypassing it.
- **Codec round-trips**: `MaxAiJournalCodecTest` / `ResponseModelTest` assert serialize→deserialize equality for
  persistence formats. Any new persisted format gets the same test.
- **Hand-built fakes**: where a boundary is needed (filesystem, `RootFileAccess`), inject a fake object written
  by hand in the test file. Data is constructed inline; there is no fixtures directory and no factory framework.
- **Test the real logic** — never substitute a mock for the behaviour under test.
- **Extract, don't instrument**: when a decision lives inside a composable or a heavy class, move the decision
  into a plain model (`ControlLayoutModel.kt`, `HomeTemperaturePolicy`) and test the model. This is how the two
  `ui/` tests exist at all.
- **Naming**: `*Test.kt` under the mirrored package. Flat per-class suites, arrange/act/assert inline without
  comment headers.

## Where tests live

```
manager/app/src/test/java/nd/max/
├── core/{diagnostics,hardware,maxai,recommendation}/
└── ui/{mainscreens,viewmodel}/
```

## Gaps (know these before planning)

| Area | State | Consequence |
| --- | --- | --- |
| Compose UI / screens | **0 tests** | visual and interaction regressions rely on review |
| ViewModels | 1 of 22 (`HomeTemperaturePolicyTest`) | state logic is mostly untested |
| Shell (`mainfiles/*.sh`) | **0 tests** | install/boot scripts verified by reading and `bash -n` only |
| Rust (`thermalcore`, `binprofiles`, `binutils`) | **0 tests** | `simulator` feature + `simulator.rs` allow desktop runs, unused by CI |
| C daemons (`archdaemon`, `preloadbin`) | **0 tests** | behaviour validated on-device by maintainers |
| JNI boundary (`core/jni/`) | **0 tests** | native library is CI-built and not exercised in unit tests |
| Localization | no test | EN/AR parity is checked by grep gates in `docs/ai/VALIDATION.md` |
| Coverage tooling | not configured | no JaCoCo, no coverage threshold |

Adding the first test in any of these areas sets the pattern — follow the pure-core/extracted-interface approach
the existing 14 already use, and say in the report that execution is unverified if no toolchain is available.

## Changelog

- 2026-09-18 — rebuilt: test count corrected 13 → **14** (`ControlRegistryTest`, `ObjectiveTest`,
  `ControlLayoutModelTest` now present), added the per-file purpose table, the wrapper-not-executable caveat,
  and the gap table by area.

<details>
<summary>Evidence</summary>

```sh
find manager/app/src/test -name '*.kt' | sed 's|.*/nd/max/||' | sort   # 14 files
find manager/app/src/test -name '*.kt' | wc -l                        # 14
ls -l manager/gradle/../gradlew | cut -c1-11                           # -rw-rw----
grep -n 'junit' manager/gradle/libs.versions.toml
```
</details>
