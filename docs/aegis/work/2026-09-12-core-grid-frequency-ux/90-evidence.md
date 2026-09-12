# Evidence

- Log archive: `.tmplogs/` (workspace) — MaxManager_Profiler reset batches every 20–60 s; `CLI_PROFILE_MANUAL_BLOCKED ai=1`; `decision failed: رفع التردد :: policyN=فشل(min:max)` with externally fluctuating live max; GPU APPLY_VERIFY_FAILED 832→754 MHz (pre-redesign GPU path).
- Module reset code path: binprofiles/src/utils/mod.rs setfreq() else-branch writes defaults then `chmod 0444` on all policy*/scaling_*_freq.
- Post-fix static suite: 14/14 PASS; git diff --check exit 0.
- Environment limits: no Java (Gradle blocked), no Rust toolchain (cargo build blocked), device control channels unauthorized — on-device verification impossible in this session.
