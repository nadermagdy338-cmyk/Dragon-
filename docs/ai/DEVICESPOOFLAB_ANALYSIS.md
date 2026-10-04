# DeviceSpoofLab — independent root/hook analysis

Reference only; **no code copied, ported or directly adapted**. LICENSE texts read: Magisk MIT ©2026 @yubunus, Hooks MIT ©2025 @yubunus, both include educational/testing disclaimer. Submodule licensing is NOT inferred from the parent license.

Pinned sources inspected 2026-10-03:
- `DeviceSpoofLab-Magisk@71a9fcd3695f494eb06085c65a45adf0b9623c98`: full recursive tree, LICENSE, [post-fs-data.sh](https://github.com/yubunus/DeviceSpoofLab-Magisk/blob/71a9fcd3695f494eb06085c65a45adf0b9623c98/post-fs-data.sh).
- `DeviceSpoofLab-Hooks@3d02befd919f3c63a054d8437f248ad5d1174524`: tree (truncated extraction), LICENSE, full [MainHook.java](https://github.com/yubunus/DeviceSpoofLab-Hooks/blob/3d02befd919f3c63a054d8437f248ad5d1174524/app/src/main/java/com/devicespooflab/hooks/MainHook.java).

## What they did

Root pre-zygote stage loads a safety guard in a subshell first to avoid a malformed sourced script aborting remaining recovery. Missing safety/value resolver refuses property writes. It modifies only existing properties, rejects unresolved generator tokens, discovers resetprop for Magisk/KSU/APatch, and verifies getprop after writing because exit 0 alone can lie. A boot-count recovery guard is conditioned on prior proof that service health checks execute. Android-ID reconciliation is separate and follows property preparation.

Hooks target LSPosed/Xposed lifecycle, install framework hooks once per process and package-library hooks again for subsequently loaded packages. Original framework SDK is captured before spoofing for API availability decisions. RemotePreferences are loaded before app code where available; Application.attach connects the binder bridge. A generation poll every 2s refreshes Build/locale/kernel/WebView state and native libraries. Display/accounts/native property hooks are opt-in. Self-process exclusions reduce the editor reading its own modified values. Logs deliberately omit identifier values.

## Why useful / MaxManager should improve

Root property mutation and Java hooks are separate coverage, not interchangeable success signals. Use read-back, preserve true framework API version, distinguish process/package lifecycle, configuration generations and visible scope. Our shared immutable repository solves UI synchronization without a second writer. Display/locale/identifier surfaces require an explicit capability adapter and process evidence, not a universal switch.

## What NOT to copy / incompatible components

No source/branding/assets/layout copied, including MIT code (explicit user restriction). No LSPosed dependency added, no telephony/DRM manipulation, no raw SSAID system database edits, no native libc hooks. Existing MaxManager ownership and privilege manager remain authoritative. A polling root service outside the arbiter would violate this contract.

## Gap analysis / evidence limits

The existence of TelephonyHooks, WebViewHooks, property_hooks.cpp or android_id.sh in a tree is not an audit of their implementation. Detailed SSAID ABX conversion, native PLT coverage, individual identifier hooks, scope configuration and uninstall recovery remain unread/unverified. Detection resistance and Android 14/15/16 behavior cannot be concluded from source paths. Root pre-zygote timing cannot be reproduced by changing Build in the manager process. Device and independent native safety review are needed before enabling those adapters.
