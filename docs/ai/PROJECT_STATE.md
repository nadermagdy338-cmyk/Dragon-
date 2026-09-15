# PROJECT_STATE

Updated: 2026-09-15 (Architect pass, exploration + vision only — no app code changed)

## What MaxManager is

An Android 11+ performance suite with two halves that ship together:

1. **Magisk/KernelSU module** (`mainfiles/`, `binprofiles/`, `binutils/`, `archdaemon/`, `preloadbin/`, `thermalcore/`) — Rust/shell binaries that apply profiles, CPU/GPU policies, I/O scheduler, thermal service, game preload.
2. **Manager app** (`manager/app`, package `nd.max`, Kotlin + Compose + Hilt) — the control surface and the home of **Max AI**, an autonomous knob-level optimizer with a safety governor.

Version: `5.2` / `Dazzling` (`version`, `version_type`, `module.json`). License Apache-2.0. Localized to ~100 locales via Crowdin.

## Repo state at handoff

- HEAD: `4e5d833 Update MaxManager`, branch has a dirty working tree (intentional, previous agent's work in progress):
  - Modified: `AdaptiveProfileEngine`, `HardwareControlArbiter`, `ControlOutcomeModel`, `MaxAiEngine`, `MinimalPlanner`, `ResponseModel`, `SafetyGovernor`, `Dex2oatScreen`, `DozeModeScreen`, `FpsOverlayScreen`, `ResolutionScreen`, `TouchBoostScreen`, `ZramManagerScreen`, `Dex2oatViewModel`, `ZramViewModel`, `README.md`.
  - Untracked: `manager/app/src/main/java/nd/max/ui/design/` (the new Design Language), `res/values/max_design_strings.xml`, `res/values/max_screen_strings.xml`, `manager/kernel-flasher/schemas/`.
- **Nothing is committed for the redesign yet.** Do not `git checkout`/`stash` these paths.
- No `docs/ai/` existed before this pass; `docs/aegis/` holds the older spec/plan/work records (still useful history, superseded as product direction).

## Build/verification reality on this machine

- `java` = OpenJDK 25.0.4, `gradle` on PATH = **4.4.1** (too old), wrapper wants **9.5.1** from `services.gradle.org` and the network is unavailable → **a real Gradle compile cannot be run here.**
- Consequence: verification is static (see `VALIDATION.md`). Every task must be written so it can be verified without compiling, and must state honestly that compilation is environmentally unverified.

## Phase status

| Phase | Status |
| --- | --- |
| Design Language foundation (`ui/design/`) | Done, 7 files, used by 6 screens |
| Screen rebuilds on the new language | 6 of ~46 done |
| Max AI control plane (keys, arbiter, safety, planner) | Core done, UI exposure poor |
| Information architecture / navigation | **Not started — this is the next bottleneck** |
| Product vision | Defined in `DESIGN_VISION.md` (this pass) |

## Next executable unit

`NEXT_TASK.md` → **NT-01: Navigation spine + Control hub.** Read `HANDOFF.md` first.
