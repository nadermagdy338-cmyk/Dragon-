# COMPLETED_WORK

What already exists and must be built upon, not redone. Verified by reading the code on 2026-09-15.

## 1. Design Language — `nd/max/ui/design/` (untracked, ~1,900 lines, 7 files)

| File | Provides |
| --- | --- |
| `MaxTokens.kt` | `MaxSpace` (gutter 20, section 28, row 8, pageBottom 40), `MaxRadius` (12/14/22/28/pill), `MaxSize` (minTouchTarget 48, icon 20/16, containers 34/40, readingMaxWidth 560, sparkline 28), `MaxAlpha`, `MaxDuration` (90/160/240/360), `MaxTone` (Neutral/Accent/Positive/Caution/Critical/Inactive with fixed hues), `MaxDataTrust` (Live/Snapshot/Unsupported), `maxIsDarkSurface()` |
| `MaxStructure.kt` | 3-level structure only: `MaxSection` (heading semantics) → `MaxGroup` (single hairline, `surfaceContainerLow`, no elevation) → `MaxRow` (48dp, `Role.Button`, decorative icons), `MaxGroupDivider` |
| `MaxScreenScaffold.kt` | `MaxScreen(...)` and `MaxListScreen(...)`: top bar, adaptive width, snackbar host, blocking `condition` vs non-blocking `banner`, owns section spacing |
| `MaxCondition.kt` | Explained empty/error/unsupported/loading states with `technicalDetail` + primary action; panel and notice variants |
| `MaxMetric.kt` | Metric blocks/lines with value + unit + source + trust badge |
| `MaxControlRows.kt` | `MaxSwitchRow` and friends with `enabled`, `lockedReason`, on/off state descriptions for a11y |
| `MaxDialogs.kt` | Dialog shapes consistent with the tokens |

Supporting resources (untracked, `values/` only): `max_design_strings.xml` (11 keys), `max_screen_strings.xml` (177 keys).

**Assessment:** this is the right foundation and the vision keeps it. It needs *additions* (hub shell, owner chip, journal row, session bar, risk dialog, command palette, numeric style, sparkline) — not revision.

## 2. Screens rebuilt on the new language (6)

`TouchBoostScreen` (reference implementation), `DozeModeScreen`, `ResolutionScreen`, `FpsOverlayScreen`, `ZramManagerScreen`, `Dex2oatScreen` (App Compiler) — plus `Dex2oatViewModel` and `ZramViewModel`.

Pattern established by `TouchBoostScreen` and to be copied everywhere:
- providers/capability section (node paths + `MaxDataTrust`) separated from state switches;
- blocking `MaxCondition(Loading/Unsupported)` with `technicalDetail` and a recheck action;
- all copy via `stringResource`; no literals;
- switches carry `lockedReason` and explicit on/off state descriptions.

## 3. Max AI control plane (`core/maxai`, `core/hardware`)

Delivered by the earlier aegis work and the current uncommitted edits:
- `HardwareControlKey` — canonical knob identity shared by AI, safety, per-app, and `AppMonitor`.
- `HardwareControlArbiter` — leases with captured baselines, verified apply, rollback; per-knob ownership.
- `SharedHardwareOwnershipStore` — cross-process ownership journal under `/data/adb/.config/MaxManager/` (UI app and `AppMonitor` run in different processes; atomic file + OS lock).
- `SafetyGovernor` — pre-action veto plus post-read verification with ~1s rollback window (recent compile fixes included).
- `ManualControlLocks` — a user touch locks only the knob touched, not the whole engine.
- `MinimalPlanner` + `ControlOutcomeModel` + `CredibilityStore` + `DynamicIntentLearner` — per-device/app/knob/direction learning. `ResponseModel` change (removing the constant term) cut error −61% and raised discrimination to 4.0×.
- `PredictiveSafety`, `DriftGuard`, `HardwareCapabilityResolver` (per-knob capability proof), `AdaptiveProfileEngine`, `ProfileApplier`.
- Module side: `MANUAL_FREQ_SESSION` property with `blocked_by_manual_session()` guards across 11 CPU freq/PPM/DVFS write sites in `binprofiles` (Rust).

Tests: `ControlRegistryTest`, `HardwareControlArbiterTest`, `MinimalPlannerTest`, `ControlOutcomeModelTest`, `ManualControlLocksTest`, `ObjectiveTest`, `ResponseModelTest`, `DiagnosticCenterTest`, `ControlPlaneArchitectureTest`, `GpuControlModelTest`, `HomeTemperaturePolicyTest`, `RecommendationTextClassifierTest`.

**Assessment:** functionally strong, UI-invisible. The vision's job is to expose it (ownership chip, journal, session bar, truth sheet) without changing its semantics.

## 4. Earlier UI pass (2026-09-04, `manager/FINAL_UI_AUDIT.md`)

A typography/spacing/contrast pass across the then-current screens. **Partly stale**: it references `AdrenoGpuScreen.kt`, `MaliGpuFreqScreen.kt`, `ThermalDevicesScreen.kt`, which no longer exist. Treat it as history, not as a contract.

## 5. Aegis records (`docs/aegis/`)

Plans/specs/work logs for GPU Reality Studio, core-grid frequency UX, and the Max AI control plane, including the evidence and reflection notes that produced §3. Keep for engineering rationale; the product direction is now `DESIGN_VISION.md`.

## NT-02 — Max AI: من عدّادات إلى نظام سببي مفهوم (2026-09-15)

ملفات جديدة:
- `core/maxai/MaxAiJournal.kt` — دفتر حلقات القرار الدائم (JSON, 80 حلقة، أحدث أولًا) + نماذج `MaxAiReading/MaxAiSample/MaxAiCandidate/MaxAiVerdict/MaxAiEpisode`.
- `core/maxai/MaxAiInsights.kt` — استخلاص نقي (JVM-only) لأحكام كل مقبض من خرائط الأثر + عدادات الحلقات + صدق التنبؤ.
- `ui/design/MaxAiCinematics.kt` — بدائل بصرية سببية: `MaxSparkline`, `MaxDeltaRow`, `MaxCausalStage`, `MaxEpisodeCard`, `MaxWeightBar`, `MaxCapsule` (بلا أي اعتماد على `nd.max.core.*`).
- `res/values/max_ai_strings.xml` + `res/values-ar/max_ai_strings.xml` — 127 مفتاحًا مستخدمًا فعليًا من `MaxAiScreen.kt`، تطابق تام EN/AR وتطابق وسائط التنسيق، بلا تكرار مع `strings.xml`.

ملفات أُعيدت كتابتها:
- `core/maxai/MinimalPlanner.kt` — `planWithTrace` يُخرج كل المرشحين مع سبب الاستبعاد، الجدوى، المصداقية، والتنبؤ.
- `core/maxai/MaxAiEngine.kt` — يبني حلقة كاملة لكل دورة (قبل/بعد/حكم/تعلّم)، يسجل `NO_ACTION` (بخنق 5 دقائق)، يحسب خطأ التنبؤ، ويحتفظ بشريط تطور 120 عيّنة في الذاكرة.
- `ui/viewmodel/MaxAiViewModel.kt` — يمرر `episodes` و`insights` المشتقة.
- `ui/mainscreens/MaxAiScreen.kt` — أُزيل الـScaffold اليدوي، الشاشة الآن على `MaxListScreen` + خط زمني من ٨ مراحل سببية لكل حلقة.

`MaxAiModels.kt`: أُضيفت حقول الحالة `trend/objectiveWeights/objectiveSource/appContext/objectiveScore/satisfactionTarget/memoryPercent/lastSampleAtMs`.
