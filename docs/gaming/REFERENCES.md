# Gaming references — research only

All observations below were fetched on 2026-10-03. Workflow: **read → understand → design → independently implement**. No source, binaries, assets, logos, animations or layouts from these repositories were incorporated. Therefore THIRD_PARTY_NOTICES is not changed for these references. README Credits acknowledges research, not bundled code.

## Licence classification

### A — research only (every reference in this task)

| Reference | Licence evidence | Reading depth |
| --- | --- | --- |
| [GameCore](https://github.com/Dreamucxe/GameCore) | Full LICENSE: MIT ©2026 Dreamucxe | Pinned `b39733afeb0b0f79a00a045b8f19f44ea1c7002a`; README portion, domain/core tree portions; full GameDetector (~9KB), GameWatch (~15KB), ThermalWatch (~6KB), SessionRecorder (~16KB) |
| [FrameX](https://github.com/MaheshSharan/FrameX-Android) | Full LICENSE: MIT ©2026 MaheshSharan | Pinned `751c5637c9205cfb678745cc34208f42034fac04`; README, tree portion, full GamingOptimizationSnapshot (~6KB), KNOWN_LIMITATIONS (~4KB) |
| [FPS Meter](https://github.com/rdevz-ph/FPS-Meter-Android) | Full LICENSE: MIT ©2026 Romel Brosas | README/tree portion; full SurfaceFlingerFpsMonitor (~20KB) on `main` (mutable revision, no pinned full commit established; blob `9432450c759dc7d73665971fb1a4004c7a54ac1e` discovered) |
| [Horizon Game Booster](https://github.com/Horizon-25/Game-Booster) | README claims MIT; root LICENSE fetch 404, full licence text not established | Root/app tree portions (`app` tree `f28177f92e3cb7578cd0ec85ac2498e34c2fe90c`); full FpsMonitor (~2KB) on master. No reuse authorised by an unverified licence claim |
| [Game-BoosterX-Plus](https://github.com/disa12311/Game-BoosterX-Plus) | Root LICENSE fetch 404; licence not established | README/root/app tree portions (`app` tree `5ff6298defda401c6fd3b3f38414a1d7cdc94257`); full GameModeManager (~4KB) on main |
| [REDMAGIC official UX article](https://mea.redmagic.gg/en/blogs/game-space/unlock-your-potential-a-reintroduction-to-redmagic-s-game-space) | Proprietary vendor content, not a code-reuse licence | Article portion: landscape lobby, edge access, display/performance/tools concepts; last portion truncated. No assets or exact visual design copied |

### B — incorporated components

**None added by this gaming tranche.** Existing project libraries retain their existing notices. MIT permission does not change the owner's explicit no-copy requirement for this task.

### C — unresolved licence / proprietary references

Horizon and BoosterX remain licence-unverified; REDMAGIC is a functional UX reference only. No code/visual copying. GitHub README capability claims are not measurements of MaxManager or proof that a vendor control works on another device.

Two further candidates were discovered in this tranche (owner request: "search for other projects that achieve the goal"). **Neither was read as source, and neither has an established licence**, so nothing is taken from them:

| Reference | Licence evidence | What was read |
| --- | --- | --- |
| [Argosy Launcher](https://github.com/rommapp/argosy-launcher) | README fetched; **no LICENSE text read in this tranche** | README feature list only. Its **concept** — a "Quick Settings" right-side panel for instant adjustments while a game is on screen — is the same *shape* of interaction the owner asked for, and is acknowledged in `README` Credits as an idea reference. No code, layout, asset or motion copied; the panel is drawn from MaxManager's `ui/design/` tokens |
| [Game Space Replacer](https://github.com/TheRealCrazyfuy/GameSpaceReplacer) | README fetched; **no LICENSE text read** | README only. It is a REDMAGIC red-switch remapper, not a game-space surface: it needs usage-stats, overlay and notification-listener permissions to *replace* the vendor switch. Nothing usable for our side panel, and no code read |

The licence rule of `AGENTS.md` §0.4 is applied strictly: an unverified licence means **no transfer**, and an idea-level acknowledgement is recorded in `README` Credits rather than in `THIRD_PARTY_NOTICES.md` (which stays reserved for actual bundled code).

## Findings grounded in inspected source

### GameCore — remaining reading (this tranche)

A recursive tree listing at the pinned commit was fetched and the module layout confirmed: `domain/gaming/{GameDetector,GameWatch,SessionRecorder}`, `domain/monitoring/ThermalWatch`, an `aimlab` engine (control layout, geometry, fire control, personal records), a Room schema lineage (16 versions), capture and network packages. **The full `ProfileApplier`/`GamingCoordinator`/overlay implementation was still not read line by line** — so this remains partial source research, and the side panel implemented here is designed from MaxManager's own `OverlayWindow`/`HudSampler`, not from GameCore's overlay.

What *was* taken as a requirement (not as code): GameWatch's separation of tracked/other/own-UI/unreadable, and its rule that an unreadable foreground sample is not evidence about the game. That requirement is implemented independently in `core/gamespace/GameSessionPanel.kt` (`PanelSubject.Unknown` + `panelSubject`) with its own tests.

### GameCore

GameDetector emits a cold event flow and uses a conflated stop channel rather than a permanently hot collector; unreadable foreground polling slows. GameWatch explicitly separates tracked/other/own-UI/unreadable, timestamps absence separately from blindness, suppresses restart after manual stop and sequences stop before start on game switch. Its 12-second grace/45-second blind defaults are reference policy, **not automatically adopted**: MaxManager's existing AppMonitor intentionally restores on foreground loss to avoid leaked overrides. Integration must not create conflicting notions of active ownership.

SessionRecorder begins a persisted row before sampling, consumes the already-existing sampler rather than reads CPU deltas independently, and flushes pending samples in bounded batches. These highlight requirements for our existing HudSampler and per-app writer integration; no Room/encryption repository or recording implementation copied. ThermalWatch debounces reported platform alert levels and retains unavailability instead of calling an unreadable device cool; MaxManager must retain its own SafetyEngine and Atlas thermal authority.

Still unread in this tranche: complete ProfileApplier, GamingCoordinator, overlay render/input implementation, MediaProjection/capture, network implementation and repository migrations. This is **partial source research**, not a comprehensive GameCore audit.

### FrameX

GamingOptimizationSnapshot distinguishes absent settings from present values, essential for restoring a deleted key rather than writing a string default. KNOWN_LIMITATIONS documents a Shizuku disconnect during teardown that can leave OEM state unreverted while session state is cleared; this reinforces why MaxManager must preserve unresolved recovery instead of erase partial failure. The thermal limitations text contains evolving claims (no zones followed by later sysfs fix); support must be measured here, not copied from the document's conclusion. Snapshot inspected, full activation/revert engine not yet inspected.

### FPS Meter

SurfaceFlingerFpsMonitor binds sampling to a foreground package and candidate layer, excludes non-rendering layers, resets cursors on package changes and filters pending timestamps. Source also includes **unproven renderer guesses**: known package names→Vulkan and default OpenGL; these must not be adopted. It derives reciprocal FPS as frame time, which is not a measured frame-time distribution. Layer substring/candidate ranking and fallback do not by themselves authenticate game rendering. MaxManager needs its own strict package/layer fixture and unknown/ambiguity handling; global compositor or overlay Choreographer readings must not be labelled measured game FPS.

### Horizon

FpsMonitor selects the first matching layer, filters intervals to 1–100ms, caps to 240 and returns zero on failures. These choices can hide low FPS and confuse unavailability with zero. Independently designed parsing must keep unavailable separate and must not use fixed plausible maxima. No algorithm transplanted.

### BoosterX

GameModeManager sends `cmd game mode set ...` and `device_config` changes, logs command results but has no readback/rollback transaction in that inspected class. Its getter receives a package argument yet reflects no-argument getGameMode; it does not establish target-package state. README cannot establish command grammar/OEM support. MaxManager must verify actual platform interfaces and arbitrate/restore before exposing these operations.

## Design boundary

Initial shared repository and adaptive library are authored from MaxManager's AppConfig, existing UI shell and persistence conventions. No OEM overclock/DND/thermal-disable/capture/automation or network acceleration claims imported. Research credit is not a promise of equivalent feature coverage. Remaining reading and device validation are explicit in README/HANDOFF.
