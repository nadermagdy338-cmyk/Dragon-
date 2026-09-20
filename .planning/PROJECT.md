# MaxManager: Max Atlas Compatibility

## Purpose

Extend the existing Android control plane to support more device/kernel/ROM combinations
through an independently implemented capability knowledge base and evidence-driven discovery.
Max Atlas discovers supported ways to observe/control hardware; Max AI continues to choose
optimization policy. They are not competing engines or independent hardware writers.

## Scope And Authority

This request-scoped GSD track was authorized by the owner on 2026-09-20 after preflight
confirmed there was no ROADMAP.md, REQUIREMENTS.md or STATE.md. This is an existing app,
not a greenfield application. Phase 1 numbering is local to this track.

Binding references remain `docs/ai/ENGINEERING-CONTRACT.md`, `docs/ai/DECISIONS.md`,
`docs/ai/VALIDATION.md`, `docs/ai/REVIEW.md`, and `AGENTS.md`.
The single delivery log remains `docs/ai/HANDOFF.md`.

## User Goal

Known, reviewed device knowledge is tried first. When a feature is unresolved, a separate
intelligent discovery component investigates safe alternatives. Only after those stages
does the app suggest sharing a privacy-reviewed support report from Settings, so the
maintainer can reproduce, add support, test and publish an ordinary reviewed release.

Study SmartPack-Kernel-Manager and every reference in `txt.txt` for behavior and architecture,
not copied code or imported path databases. Source claims must be checked live and licenses
recorded without promising legal clearance. No universal-compatibility or world's-best claim.

## Non-Goals

- No product-code changes during this planning session.
- No LLM-generated shell commands, arbitrary sysfs writes, SELinux bypass or root escalation.
- No replacement of Max AI, arbiter ownership, baselines, verification or rollback.
- No automatic telemetry/upload, executable remote plugins or unreviewed device support packs.
- No implicit change to minSdk, native ABI support, signing, boot scripts or module installers.

## Baseline

2026-09-20 preflight: working tree has only owner-provided untracked `txt.txt`.
`i18n_coverage.py --assert` passed; `repo_audit.py` reported PROBLEMS: 0.
`code_health.py --assert` failed solely on `stray_root_file: txt.txt`; debt is 10/29/66/26.
Preserve the input and report this pre-existing gate failure, not weaken the gate.
