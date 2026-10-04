# Device identity architecture — IDENTITY-01

Task size: large. Scope: core/spoof, existing spoof ViewModel/screens, AppSettings integration, English/Arabic resources, tests and reference reports. No sysfs, thermal, watchdog, SELinux policy, native library or boot-script modification.

## One source of configuration

Extend the existing `spoof_workspace.txt` with a versioned migration. Global active profile, profile library, package assignment, app mode, category policy and field overrides live in one immutable workspace. `SpoofConfigurationRepository` is a Hilt singleton. Both entry points collect the same StateFlow; only repository methods perform persistence. Neither UI opens configuration files.

Effective resolver: app DISABLED → observed host values; otherwise per-category REAL / GLOBAL / CUSTOM policy, then per-field app override, selected profile, global default, host observation. Origin is carried per field. The resolved target is an intention, not proof of effective runtime state. User/work-profile scope must not be inferred from a package-only external contract.

## Engine/evidence

COPG per-app interface: namespaced `PACKAGES_MAXMANAGER_*` arrays and `_DEVICE` maps. Foreign configuration remains preserved. COPG-VD global interface: `/data/adb/COPG-VD.json`, `COPG-VD` object. No installed module ⇒ no write; disabled/update/removal ⇒ no write; malformed/foreign conflict ⇒ fail closed. Arbiter owns writes using existing `HardwareControlKey.spoofEngine`.

Evidence levels: SAVED intention → CONFIG_VERIFIED after file read-back → PROCESS_UNVERIFIED until authenticated target-process evidence exists. Never elevate a matching JSON signature to Verified identity. Restart/reboot behavior and actual Build/native property reads need a device. Engine config preparation is not execution of global resetprop.

## Safety / gaps

No synthetic SDK/SoC claims, no blind ro.* editor, no changing SDK above the real framework, no global fallback for a per-app failure. Critical hardware/identifiers are never-touch or need adapter. COPG and COPG-VD are independent external injection implementations: equal desired configuration does not prove hook ordering or isolation. Disabled/category REAL under active global injection cannot be promised by removing a COPG package assignment. Cross-engine transactions need durable recovery before claiming atomic global+per-app apply.

## Implemented boundary / remaining Definition of Done

Implemented: shared configuration and engine StateFlows (`SpoofConfigurationRepository` + `SpoofApplyEngine` singletons), migrated workspace v3 with v1/v2 reading, per-category/field origin resolver, profile create/edit/duplicate/delete/import/export, shared editor in AppSettings, global default selection, independent global/per-app configuration adapters, read-back and arbiter rollback reporting, foreign package conflict rejection, revision-bound confirmation, per-app acknowledgments outside portable exports.

**Not complete:** global live execution, authenticated process verification, durable crash/reboot recovery, cross-engine atomicity/hook precedence, per-user/clone/work-profile keys, advanced Build/manufacturer/CPU/GPU/property/locale/display adapters, full capability matrix with measured read/write/risk/rollback methods, persistent developer journal/last-applied timestamps, Max AI/CredibilityStore integration, preset catalog, Android 14/15/16 device matrix, full reference repository research and independent safety review. A model field or target preview does not implement its native surface. Unsupported domains are explicitly non-writeable; no bypass or fake success is added.

Global preparation intentionally refuses existing advanced global keys; the shipped COPG-VD example contains advanced keys, so **that default configuration will be refused** rather than destructively trimmed. This is a conservative configuration adapter, not a claim that phase 5 is done. No global disable is achieved merely by clearing the saved default; UI labels this action as clearing a saved reference.

Local engine evidence is shared across ViewModels but is session memory only and file snapshots are measurements of configuration, not target identity. Global session undo uses arbiter session baseline; durable baseline recovery and external-editor race protection during release still need redesign before a production runtime claim.

## IDENTITY-02 continuation — durable configuration recovery

`SpoofConfigTransaction` now centralizes both adapters. A private AtomicFile journal (`identity_recovery.txt`) captures bounded original/target content before write, with PREPARED/CONFIG_VERIFIED/RESTORED/FAILED/CONFLICT states. Only redacted engine/time/state/reason summaries enter UI; content is never exported. Interrupted journal blocks another write until user-triggered restore. No root request/automatic restore at startup. Restore compares current content against recorded original/target, refuses unreadable or unrelated content, and writes through the existing canonical arbiter key. Repeated apply preserves the prior original undo point while its target remains live; failed subsequent writes preserve the earlier verified recovery point. One-shot requests release their token without replaying a stale session callback.

This supersedes IDENTITY-01's session-only **file** recovery limitation; it does NOT implement property-level boot recovery or target-process verification. Private AtomicFile persistence and root operations still need Android/process-death/device tests. External editors do not take our arbiter lock: an unavoidable read/write race remains between comparison and external mutation; no cross-writer atomicity claim.

Capability matrix carries scope/read/write/verification/rollback/risk and explicitly false process compatibility. A readable config with an unavailable module is not writable. Independent active COPG/COPG-VD layers are now blocked from competing preparation because hook precedence/isolation has not been proved. This is fail-closed interoperation, **not completion of the requested unified native engine**. Global shipped advanced defaults remain refused.

Pure recovery/matrix tests + full local Kotlin/Compose type harness verify model/code shape, not actual injection. Native implementation/authenticated process evidence/MaxAI integration/preset catalog/advanced adapters/per-user support/full reference research/Android14–16 device matrix/independent safety review remain incomplete. Neither a fake process probe nor config-success-as-identity-success has been added.

## IDENTITY-03 continuation — failure-tested transaction policy

`SpoofFileTransaction` runs the actual transaction policy behind IO/journal/control ports. The injected production `SpoofConfigTransaction` retains the existing HardwareControlArbiter, fixed allowlisted paths and RootFileAccess writer; no new ownership implementation. 28 fault-injection tests cover interruption/restart of the policy instance, failed prepare/final journal, eligibility changes, captured-baseline races, repeated updates, rollback and external writers. The callback driver is explicitly not an OS-lock/Android AtomicFile/device test.

Restore rollback now accepts its actual attempted ORIGINAL content, not only TARGET; metadata failure after an undo can return safely to the captured TARGET. Final readback must still match the desired signature before success or a verified/restored journal state is published. A previous recovery point is retained after failed updates only while its target is actually live. The captured arbiter baseline must match pre-mutation comparison; an external writer can still race after that comparison. No cross-editor atomicity or target-process identity is claimed.

Privacy inspection: application allowBackup=false; FileProvider exposes cache/external storage, not filesDir. Recovery remains private and outside portable export. Android persistence and process death remain device checks. Aggregate pure harness: 396 tests/0; main+debug local type harness: 547 inputs/3236 classes/0 errors. Full Android compilation/resources remain unverified.

## IDENTITY/GAME omission follow-up — 2026-10-03

Add-only import now treats an explicit local GLOBAL/DISABLED app policy as local assignment: imported bindings cannot attach a dormant custom profile to it. A binding referencing a colliding foreign profile cannot smuggle a GLOBAL policy into the local workspace. Existing CUSTOM policies/overrides remain untouched. COPG materialization now groups GLOBAL apps by the global profile ID, not a preserved dormant CUSTOM binding; a genuine custom app can use that dormant profile without a false KEY_COLLISION. Four new production-policy regression tests. No additional native surface or runtime evidence claimed.

Combined pure harness422/0; main+debug types552 inputs/3262 classes/0 errors, with existing duplicate audio warnings. Device/native/independent review/full Android limits above remain unchanged; this is a bounded corrective tranche, not completion of the whole identity specification.

## Verification

Pure tests for inheritance, field source, disabled/category policy, deletion references, schema migration and bounds, unsupported fields and external conflicts. Compile/type-check shared API changes when possible; run repository contract gates. Android AtomicFile/SAF/lifecycle/RTL and hook effects remain device checks. Independent large-task safety review is required and cannot be fabricated by this executor.
