# Checkpoint

## Final state (round 3)

- Canonical owner: `GpuHardwareBackend` (injectable `Io`, provider scoring, ambiguity detection, unit inference, generic devfreq + MediaTek signed-OPP/fixed-index adapters, thermal/load telemetry).
- Transaction contract: revalidate provider+capabilities, exact readable baselines, min<=max invariant ordering, verify-all-touched-fields, best-effort rollback across ALL touched fields (never stops at first failure), null baseline never counts as success, apply-failure vs rollback-failure distinguished.
- New since round 2: `Request.releaseLock` — Adaptive mode on MediaTek is a verified lock release (dynamic governor scaling) with its own baseline/rollback; smart intents now work on exact-lock-only devices; MTK discovery reports the *effective* locked frequency as min/max/current; ambiguous units hard-block ALL mutation incl. governor; generic "gpu" providers are Family.UNKNOWN (no fake Mali branding).
- Persistence: `GpuTweakPersistence` re-resolves saved smart modes against the *current* driver (adaptive→release on MTK, full range on devfreq); applied at daemon startup and with precedence over legacy profile replay.
- Per-app: ONE GPU frequency owner via hardwareControlRegistry (profiles + explicit caps merged); MTK verification reads the fixed-lock state; drift repair rides the registry (duplicate owner + driftRetries machinery removed).
- Legacy retired: Adreno/Mali screens+ViewModels, throttle-bypass props/UI, dead per-app GPU helpers.
- Tests: 18 backend unit tests incl. release/rollback-failure/ambiguity/invariant/MTK effective state/OPP-format regressions.
- Parsing hardening: OPP-line selection is proof-driven (labeled freq > bare-labeled > unit-bearing > single-bare; multi-bare rejected) — validated by Python simulation that caught two real defects before any device ran them.
- Verification: git diff --check clean; 16/16 whole-contract static assertions; lexer-aware balance check clean (AppMonitor residual identical to shipped HEAD baseline = stripper artifact); JDK absent so no compile/test run.
