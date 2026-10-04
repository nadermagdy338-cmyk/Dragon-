# Device Faker — independent technical analysis

Reference only; **zero code, binaries, templates, assets or layout imported**. GPL-3.0 is incompatible with this closed-source project's source reuse policy. Inspected 2026-10-03 at `Seyud/device_faker@8d7f4aafa0e8dd2df566188732eae7ebe3b46db0`.

Sources: [lib.rs](https://github.com/Seyud/device_faker/blob/8d7f4aafa0e8dd2df566188732eae7ebe3b46db0/src/lib.rs), [config.rs](https://github.com/Seyud/device_faker/blob/8d7f4aafa0e8dd2df566188732eae7ebe3b46db0/src/config.rs), [cow_props.rs](https://github.com/Seyud/device_faker/blob/8d7f4aafa0e8dd2df566188732eae7ebe3b46db0/src/cow_props.rs) (first 14k extracted, not the whole 43k file), [CONFIG](https://github.com/Seyud/device_faker/blob/8d7f4aafa0e8dd2df566188732eae7ebe3b46db0/docs/en/CONFIG.md), LICENSE header and repository license metadata. Companion implementation and Vue UI have NOT yet been fully audited; no claim of whole-repository review.

## What they did / why useful

- Zygisk V4 pre-app-specialize resolves package from app data directory, falls back to process name without `:process`, derives Android user as uid / 100000. Explicit `pkg@user` wins over package fallback. Server specialize unloads rather than modifying system_server.
- Missing/invalid TOML skips spoofing and unloads. File is re-read for each new process. This is launch-time reload, NOT live mutation of already running apps.
- Direct app record replaces the whole template; missing app fields do not inherit template values. This differs from MaxManager's requested field-wise inheritance and must NOT be imitated.
- JNI Build modification precedes per-process property work. COW uses private property-area copies; native reads and Build fields are different surfaces. Serial area, long values and bionic prefix routing make naive property patching unsafe.
- Strict COW explicitly refuses global resetprop fallback; companion_resetprop is an explicit separate mode. Otherwise isolation would be a false promise. DPI is a global `wm density` operation even in COW mode; CONFIG describes foreground recovery, which needs device proof.
- Config helpers expand recognized property families but leave board/hardware/SoC as single-property surfaces. They parse fingerprint structure for consistency; unknown TOML fields are ignored, a risk for silent unsupported input.
- Debug logging defaults off. Current lib.rs disables all levels when off, contradicting CONFIG's older “Error-only” sentence: source wins.

## MaxManager should learn / improve

Keep process identity evidence separate from saved intent, expose scope explicitly, fail closed without an adapter, validate unsupported fields instead of silently dropping them, preserve observed framework SDK for runtime compatibility checks, and attach origin to every resolved field. A package-only adapter must never claim per-user support.

## What should NOT be copied

GPL source, COW pointer layout, hooks, Rust/JNI implementation, anti-detection map manipulation, denylist behavior, TOML templates, UI assets/branding. MaxManager's independent resolver and capability model do not reproduce their code. No stealth/detection-bypass feature is added.

## Incompatible with current architecture / gaps

Their companion owns global resetprop/DPI recovery; importing that would create an ownership path outside HardwareControlArbiter. COW layout resolution needs Android-version/ABI/device tests and independent native safety review. No MaxManager COW adapter or authenticated process verification channel exists in this task yet. Listing a field in this report does not make it supported.
