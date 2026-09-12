# Checkpoint

## Fixes
- Module (binprofiles, Rust): `MANUAL_FREQ_SESSION` prop (props.rs) + `blocked_by_manual_session()` guard in all 8 CPU-freq writers (setfreqppm/setfreq/setgamefreqppm/setgamefreq/dsetfreqppm/dsetfreq/dsetgamefreqppm/dsetgamefreq). While the app holds a manual session the profile binary stands down; chipset paths are GPU-only (verified).
- ViewModel: session ownership map + bounded re-assertion (3 attempts/policy, then honest conflict state); `applyPinnedFrequency`; `writeAccepted` distinguishes node refusal from external overwrite; `externalConflict`/`sessionOwned` state; manual-session prop lifecycle (set on apply, cleared on session restore; non-persistent; AppMonitor clears stale copy at startup).
- Screen: SAFE PREVIEW removed entirely; section renamed FREQUENCY CONTROL with Arabic subtitle; pin toggle (min=max lock) with single slider; verification strip rewritten in Arabic with honest three-case messaging + verification-snapshot vs live-now rows; session/conflict chips; Arabic buttons; **slider edit state decoupled from the 3 s poll** (the mid-drag reset bug).
- Reviewer findings from the GPU task closed: CRITICAL-1 `shell(` unresolved reference → readCpuGovernors now delegates to CpuHardwareBackend.policies(); CRITICAL-2 verified already fixed (reviewer read stale tree); IMPORTANT-3 MTK unconditional lock release moved before releaseAll().

## Evidence
- git diff --check clean.
- 14/14 Core-Grid contract assertions pass (rust guards ×8, prop mirror, session defense, pin, conflict flags, UI gating/messaging, stale-clear, shell fix).
- Structural balance clean on all touched Kotlin files (AppMonitor residual identical to shipped HEAD baseline — lexer artifact).
- No JDK/kotlinc/cargo on device: Kotlin and Rust compilations remain environmentally unverified.


## Review round 2 (independent agent) — findings closed
- C1 CRITICAL: duplicate companion objects (compile blocker) → merged into one; verified `companion object` count == 1.
- H1 HIGH: MTK `ppm_fix_freq` (per-cluster CPU pin via /proc/ppm/policy/ut_fix_freq_idx) + mediatek_performance DVFS mode toggles (cpufreq_cci_mode=1, cpufreq_power_mode=3) were unguarded → both now refuse during a manual session (guard total: 11 call sites across utils + mediatek.rs).
- M1: banner severity keywords now include "أعاد ضبط"/"كِيان خارجي" — external-reset message renders as error, not green success.
- L3: resetFrequencyLimits returns an honest "مدى العتاد غير معلن من الدرايفر" early when proven bounds are unknown instead of a misleading failure banner.
- L4: cluster summary GHz formatting unified to Locale.US.
- L1 (allowed-list keyed on live lower/upper only when hardware bounds unreadable) and L2 (companion-restart clears the session flag mid-session) accepted as documented trade-offs; both now carry rationale comments.
- Final verification: git diff --check clean; 9/9 post-fix assertions pass; structural balance clean on both edited Kotlin files.
