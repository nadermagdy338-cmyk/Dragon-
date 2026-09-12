# Intent

User report (2026-09-12): Core Grid "FREQUENCY ENVELOPES" UX is bad and confusing; SAFE PREVIEW is useless; no frequency pinning; "KERNEL RESPONSE DIFFERS" alert is cryptic and only appears on Apply; and after any UX work — the frequencies never actually change as selected. Verify from the attached device log archive.

## Diagnosis from MaxManager_Logs (real device, HyperOS/Dimensity)

1. **The module's own profile binary resets CPU limits on a cadence.** `MaxManager_Profiler: Set cpu0..7 maxfreq=... + "Set CPU freq to normal Frequencies"` batches every 20–60 s. Trigger chain: Max AI enabled (ai=1) → each AI decision/app switch → `sys.maxmanager-profilesettings <profile>` → `setfreq()/setfreqppm()` rewrite `scaling_{min,max}_freq` to profile defaults.
2. **The same binary re-locks every `policy*/scaling_*_freq` node to 0444** after each reset (binprofiles setfreq/dsetfreq else-branch). The app's chmod-dance still writes, but the reset itself wipes user values.
3. **Vendor thermal daemon also fights**: MaxAI "رفع التردد" failures show live max fluctuating (1.2→2.1→1.9 GHz on policy0/4/7) — values no MaxManager component set.
4. **UI bug**: slider edit state was keyed on live polled values (`remember(policyPath, control.minKHz, ...)`) — the 3 s poll reset sliders mid-drag.
5. GPU APPLY_VERIFY_FAILED entries in the same log are the pre-redesign GPU code (832 vs 754 MHz devfreq max mismatch on MTK) — already superseded by the GPU Reality Studio fixed-index work.
