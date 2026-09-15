# DECISIONS

Binding decisions from the architect pass (2026-09-15). Executors follow these unless a decision is explicitly revised here. Format: decision → why → consequence.

## ADR-01 — One navigation model; the pager mode is deleted
**Why:** `use_scroll_animation` produces two runtime navigation architectures for the same four screens, doubling every nav concern (D-01), and it is invisible to users.
**Consequence:** delete the `main` pager route, the flag read in `MainActivity`, and its settings toggle. Bottom bar + nav rail is the only model.

## ADR-02 — Routes become a typed registry outside `MainActivity`
**Why:** 40+ string-literal routes inline in an 817-line activity produced dead aliases and untestable navigation (D-03).
**Consequence:** `ui/navigation/MaxDestinations.kt` is the single source of truth (route id, title res, icon, parent, risk level, deep-link key). `navigate("literal")` outside that package becomes a static-test violation.

## ADR-03 — Primary destinations are Now / Control / Apps / Max AI; Settings leaves the bottom bar
**Why:** Settings held a primary slot while navigating to two destinations, and Max AI — the product differentiator — had no primary entry (D-02, D-09).
**Consequence:** Settings is reached from the Now top bar and aggregates appearance, module/update, diagnostics, logs, advanced tools, about.

## ADR-04 — Control is organized by device domain, not by screen name
**Why:** ~9 real domains were split across 19 launcher rows with overlapping scope (D-03, D-04).
**Consequence:** 8–9 domain hubs (CPU, GPU, Memory, Display, Responsiveness, Thermal, Power, Storage & compiler, Network). A dedicated screen survives only for deep workspaces (CPU core grid, GPU ladder, per-app editor, kernel flasher).

## ADR-05 — Merge by absorption, never by rewrite
**Why:** six screens were just rebuilt on the new language; re-doing them for the sake of the new IA would burn work and risk regressions (explicit user constraint).
**Consequence:** hubs host existing screen bodies as sections; migration of a body to `ui/design/` happens in its own later task, not in the IA task.

## ADR-06 — `ui/design/` is the only design system; `ui/component/` is legacy in runoff
**Why:** two parallel systems, the better one covering 13% of screens (D-05).
**Consequence:** new UI imports `nd.max.ui.design` only. No new `Scaffold(` in screen files. Legacy components are deleted in the task that orphans them. `ui/components/` is folded into `ui/component/` and the duplicate package name disappears.

## ADR-07 — Data trust is a product rule, not a screen detail
**Why:** `MaxDataTrust` + source labelling is the credible differentiator in a category full of fake telemetry (D-06).
**Consequence:** any surfaced number must carry freshness + source; unknown renders as `status_unknown`, never as a plausible value. Synthesizing or interpolating telemetry is a blocking defect.

## ADR-08 — Capability and state must be visually separate
**Why:** the TouchBoost rebuild proved the old pattern conflated "device supports it" with "it is on".
**Consequence:** every control surface shows a providers/capability section (with node path + trust) distinct from its state switches; every disabled control supplies `lockedReason`.

## ADR-09 — Ownership of every knob is user-visible
**Why:** the arbiter, manual locks, and cross-process ownership journal already exist but are invisible where they matter (D-02).
**Consequence:** `MaxOwnerChip` (You / Max AI / Per-app / Module / Kernel default) renders on control rows and hub summaries and can release or lock, backed by `HardwareControlArbiter` + `ManualControlLocks`. No new ownership concept is invented in the UI layer.

## ADR-10 — Max AI must be auditable: ledger over dashboard
**Why:** trust in autonomy comes from history, not from status cards.
**Consequence:** a change journal (actor, knob, before→after, verified/rolled back, measurement) is a first-class surface in Now and in Max AI. `MaxAiScreen`'s generic cards are replaced by: objective, ownership map, journal, safety, learning credibility.

## ADR-11 — UI never writes hardware directly
**Why:** bypassing the control plane breaks leases, baselines, rollback, and safety veto.
**Consequence:** no `RootFileAccess`/shell writes from composables or screen ViewModels; all writes go through the arbiter/control-plane APIs.

## ADR-12 — Efficiency is part of the brand: decoration is opt-in and off by default
**Why:** blur, ambient glow, motif overlays, weather effects, and video wallpaper cost frames and battery in an app that promises to save both (D-07).
**Consequence:** one Appearance → Ambient effects switch, default off, with an honest cost note; effect engines are not composed when off; unreferenced engines are deleted.

## ADR-13 — Semantic tones stay hard-coded, not dynamic
**Why:** Positive/Caution/Critical must survive any wallpaper-derived palette; a "critical" that renders pastel green is a safety bug.
**Consequence:** keep `MaxTone`'s fixed hues and the light/dark surface derivation in `MaxTokens.kt`; dynamic colour only drives Neutral/Accent.

## ADR-14 — Localization parity is part of "done"
**Why:** new screens added 188 English-only keys in an RTL-first, ~100-locale app (D-10).
**Consequence:** every task that adds user-visible copy updates `values/` **and** `values-ar/` in the same change; no string literals in Compose.

## ADR-15 — Static verification is the gate; compilation is best-effort
**Why:** no usable Gradle/JDK toolchain combination here (D-11).
**Consequence:** each task ships with grep/python contract checks (`VALIDATION.md`) and, where cheap, a JVM source-scan test in the existing architecture-test style. Reports must say "compilation unverified in this environment" rather than implying a build passed.

## ADR-16 — High-risk tools are gated, not featured
**Why:** Terminal, SetEdit, ActivityLauncher, KernelFlasher can brick or wedge a device and currently sit beside ordinary tweaks.
**Consequence:** they live under Settings → Advanced tools with an explicit risk gate and `MaxRiskDialog` on destructive actions; risk level is declared in the destination registry (ADR-02).

## ADR-17 — Root/module status becomes one app-level session state
**Why:** root is re-probed on every navigation event and each screen invents its own unsupported story (D-08).
**Consequence:** a single observable session state (root, module, SELinux, capability snapshot) provided once; screens read it and render `MaxCondition` from it. Not in NT-01's scope, but no new per-screen probes may be added.

## ADR-18 — Nothing already-built is redone for aesthetic reasons
**Why:** explicit user constraint and respect for sunk, good work.
**Consequence:** the six migrated screens and the control-plane work are treated as foundations; they change only when a functional rule above (ownership chip, journal, localization) requires an additive change.

## ADR-19 — دفتر الحلقات هو الذاكرة السردية، لا العدّادات
العدّادات الأربعة و"آخر إجراء" كانت تُهدر سلسلة السبب/النتيجة كل ٣٠ ثانية. صار كل قرار حلقة دائمة مُسلسلة حقلًا بحقل.

## ADR-20 — إظهار المرشحين المرفوضين
القرار غير مفهوم بدون البدائل. `planWithTrace` يُخرج كل مرشح مع سبب الاستبعاد (`measured_harm`/`predicted_harm`/`no_step`/`unreadable`).

## ADR-21 — `NO_ACTION` حدث يُسجَّل
المراقبة الواعية ليست خمولًا؛ تُسجل حلقة بلا تنفيذ (بخنق ٥ دقائق) كي لا يبدو النظام ميتًا حين يقرر ألا يتدخل.

## ADR-22 — التنبؤ يُقارن بالقياس دائمًا
كل حلقة تحفظ `predictedGain` و`predictionErrorGain = |تنبؤ − مقيس|`، فتُعرض دقة النموذج بدل ادعائها.

## ADR-23 — ما لا يُقاس يُعرض كغير متوفر
لا صفر افتراضي ولا رسم وهمي: `after == null` ⇒ "تعذر القياس"، والخط الزمني فارغ قبل أول حلقة حقيقية.

## ADR-24 — شريط التطور في الذاكرة فقط
عيّنات الجلسة الحالية (120) لا تُحفظ على القرص كي لا يُعرض رسم "حي" من جلسة سابقة.

## ADR-25 — بدائل سينمائية بلا تبعية للنواة
`MaxAiCinematics.kt` لا يستورد `nd.max.core.*` ولا التنقل، فتبقى لغة التصميم قابلة لإعادة الاستخدام والاختبار.
