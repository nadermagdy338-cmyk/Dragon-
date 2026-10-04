# MaxManager Gaming — GAME-REBUILD-01

Source request: owner's `game.txt`, Google Drive id `1U6TXUcYsZM-cq_J2oK60xPcNNWbXNjX8`, read in full on 2026-10-03. Task size **large**. This is an implementation-in-progress, **not the completed 45-section specification** and not a production certification.

## Architecture and implemented flow

```
Game Space / App Settings
       ↓
GameProfileRepository (process-wide StateFlow + mutex)
       ↓
existing MaxManagerPaths.APPLIST_JSON (AppConfig; no new profile DB)
       ↓
existing rooted AppMonitor
       ↓
PerAppControlRegistry → HardwareRepairExecutor → HardwareControlArbiter
       ↓
PerAppHardwareStatus → App Settings
```

`GameProfileDocument` validates a bounded JSON object (2 MiB / 4096 apps) and package keys. Patches preserve other apps and unknown fields within the edited app. `GameProfilePersistence` runs actual read → transform → compare current file → atomic write → readback. Corrupt, missing or unreadable storage is not replaced by an empty database. Known AppConfig field types are checked before writing. State is published as saved only after matching readback. External root importers are not serialized by our process mutex; the comparison detects some conflicts but **does not provide cross-editor atomicity**.

AppSettingsViewModel no longer writes whole stale per-ViewModel maps or executes root writes directly. It observes the shared repository; master switch follows persisted repository state, not an optimistic local success. GameSpaceViewModel observes the same profiles. `GameSpaceRepository` owns library membership/favorites using existing `settings` preferences; this stores library metadata only, not hardware profiles. Existing `game_library_manual` entries remain intact.

(Removed in GAME-LOBBY-REDMAGIC-STYLE-02; the lobby replaced it.) Former GameSpaceScreen used the `MaxSplitScreen` shell: a game list and selected-game console side by side at ≥600 dp, vertically split below that width. Search and selection survive configuration recreation; favorites persist, launcher candidates and manual games remain supported; one navigation entry opens the existing profile editor. No external artwork, layouts or icons copied. This is an adaptive first layout, **not a completed premium visual/motion pass**. Device accessibility/RTL/thumb reach and short-landscape height are unverified.

The displayed GPU policy is the saved **GPU ceiling policy**, not a comprehensive verified performance mode. Refresh target is not measured FPS. No target FPS or runtime success is fabricated. The UI explicitly states that the full gaming session system/side panel is not available yet.

## Reused runtime and audited blockers

- AppMonitor already detects foreground applications, snapshots per-app baselines, applies controls and restores through PerAppControlRegistry. Reuse this writer rather than launch another gaming optimizer that contends for the same hardware.
- AppMonitor's current `recoverStalePerAppState` calls `PerAppRecoveryStore.clear()` even after partial restore; recovery is not yet a sufficient crash-safe gaming contract. PerAppRecoveryStore.restore has direct restoration without comprehensive readback. Neither was changed in this tranche; independent safety review and fault tests required before extending runtime.
- Existing FpsMonitorUtil global SurfaceFlinger counter, kernel display FPS and first averageFPS match are **not authenticated per-game FPS**. HudSampler cannot be relabelled as game FPS. A package/layer-scoped measurement with stale/ambiguous rejection must precede game-FPS claims.
- HudSampler already offers bounded recording and shared reads, but owner handoff/lifecycle and per-game scope need design. Do not create parallel CPU delta samplers.
- OverlayWindow mount now returns success only after addView succeeds. Failed mount disposes composition/lifecycle; FPS/process services stop instead of starting readers or claiming isRunning. Unmount is idempotent, clears ViewModelStore and cancels edge animation; destroyed owners cannot remount. This fixes the audited false-mount blocker, not the gaming side-panel. WindowManager/permission revocation, input drag handling, rotation/insets and crosshair touch-through still need Android/device tests.
- Existing SafetyEngine/ThermalCeilingRouter/AtlasAdaptiveExecutor remain the control authorities. No thermal service disabling, hard-coded overclock, independent gaming safety engine or new global device_config optimizer added.

## Remaining specification work

| Area | Status / required acceptance |
| --- | --- |
| Research | Partial source review recorded in REFERENCES; full ProfileApplier/GamingCoordinator/overlay/capture study outstanding |
| Profile source | Shared repository implemented; no second DB; global defaults/custom resolution and all editor writers/importers still need comprehensive integration tests |
| Library | Search/manual/favorites/selected profile implemented; icons/covers, actual recents and recorded last-session summaries outstanding |
| Session lifecycle | Existing per-app runtime available, no new complete GameSessionManager/durable game history/verified restore integration yet |
| Detection | Existing AppMonitor reused by plan; no game-specific observer/lifecycle tests implemented yet |
| Telemetry/graphs | Existing infrastructure audited, no complete game telemetry adapter; null/freshness/source requirements mandatory |
| FPS | Package/layer-scoped adapter + fixture/device measurement outstanding; never use overlay Choreographer as game FPS |
| Overlay/HUD/crosshair | Existing window/HUD primitives, no actual gaming side-panel or per-game HUD editor implemented yet |
| Capture | MediaProjection consent/service/recording/screenshot lifecycle not implemented |
| Network | No automatic probes or acceleration claims; explicit endpoint/scope/capability needed |
| Display/tools | Must reuse per-app adapters and actual advertised modes; no unsupported controls enabled |
| Thermal | Existing safety and Atlas path, no final gaming integration/device proof |
| Max AI/Atlas | Existing authorities audited; game evidence explanations/objective bridge outstanding |
| Polish/accessibility | Initial adaptive composition only; actual landscape/insets/large-font/controller/reduced-motion tests outstanding |
| Validation | Local tests/type harness only; Android16 rooted-device matrix and independent reviewer unavailable/not performed |

## Tranche: landscape Game Space lobby + edge-docked side panel — 2026-10-03

Owner request: a **professional landscape Game Space at OEM level** (REDMAGIC as the visual/functional target) and a **Game Space overlay that does not cover the game** — a side panel instead.

### What was built

**Screen** (`ui/subscreens/GameSpaceScreen.kt`, rewritten). The old surface was a name list beside a text column: no game artwork, no header, no action row — a two-game settings page, not a lobby. Now: real launcher icons read from `PackageManager` through the existing `AppIconImage` (no bundled or copied assets), a hero header (icon, label, package, detected/manual provenance, favourite), a library pane with search + manage + favourites and a *counted* footer, and a console pane with an Overview/Console segmented tab. The Console tab is a `MaxCardGrid` of four command tiles (launch, profile, identity, panel) plus the side-panel card. Breakpoint moved 600 dp → **720 dp**, because a 40 % list column below ~288 dp truncates words — the rule `MaxCardSpec.minColumnWidth` exists to prevent. Membership add/remove moved **onto the row it affects** (it previously acted on the selected game while drawn in another card). Every colour, radius, gap and motion value comes from `ui/design/`; nothing is styled after the REDMAGIC app.

**Side panel** (`core/gamespace/GameSessionPanel.kt` pure model, `ui/component/GamePanelSurface.kt` render, `service/GamePanelService.kt` driver, `ui/util/GamePanelPrefs.kt` per-game preference, `AndroidManifest.xml` service entry). It reuses `OverlayWindow` (no second overlay engine), `HudSampler`/`HudLive`/`HudRecorder` (the one reader) and `HudSurface` (the one metric renderer) — it opens no hardware path and contains no `RootFileAccess` call, so ADR-11 is satisfied by construction.

**The honesty the request forces:** "the overlay does not cover the game" is half true. Collapsed the panel is an 18 dp handle on the screen edge — no game area taken. Open it takes up to 292 dp until collapsed. Both statements are in the strings and in the panel card, so the claim is not one the first tap disproves.

### Measured behaviour (pure harness)

`core/gamespace/GameSessionPanelTest` — 10 tests: an unreadable foreground sample is `Unknown`, never "not a game"; enabled + tracked always yields at least a handle; only `Open` reports `coversGame`; a missing sample collapses the panel without erasing the user's choice; leaving the game resets the open flag; the chosen side survives collapsing; and placement honours top/bottom insets on a wide screen and stays inside a narrow one. Pure harness **433 tests / 0 failures**.

### Follow-up in the same tranche: the auto panel and the lobby

The owner rejected the first pass — the panel was a manual switch that could not see a game opened from outside MaxManager, and the surface was not a landscape lobby. Both were fixed here rather than in a new tranche:

- **`GameLobbyScreen`** (`MaxDestination.GameLobby`) is a separate landscape surface: horizontal game rail with real launcher icons, a console dock with a Play/Profile segmented tab, and the panel switch. It is a lobby, not a second library — `GameSpaceScreen` stays the vertical library where membership and profiles are edited. It has **two doors**: the library screen's top bar, and a first-position row in the Apps destination (`ApplistScreen.GameLobbyDoor`, excluded from the generic workspace list so it is not listed twice).
- **The panel now appears by itself.** `GamePanelService.ensureRunning` starts at app launch (`MaxManagerApplication`) whenever at least one game is enabled, waits up to `IDLE_LIMIT` (40 polls ≈ 1 min) for such a game to come to the front, and keeps running while one is in front — so a game launched from any external launcher still gets the panel. `panelServiceLifetime` stops the service once the game has left (or once an old intent never saw one). With no enabled game, nothing is started: no foreground service and no permanent notification without a reason.
- **Panel tools, each routed to its existing owner:** refresh rate cycles 60 → 90 → 120 → *no override* (the last step clears the forced mode instead of pinning 60) and is sent to the existing `RefreshRateReceiver`; recording toggles `HudRecorder`; Do-Not-Disturb is marked `ControlledElsewhere` and opens App Settings because its effect is global and its owner lives there; screenshot is marked `NotAvailableYet`. `gamePanelTileState` is unit-tested, so a tile cannot silently pretend to act.

Gates after the follow-up: pure harness **437/0**; type harness **557 inputs / 3297 classes / 0 errors**; kt_balance 2171/0; health clean with debt unchanged (7 oversized / 26 wildcard / 5 inline copy / 4 hardcoded / 20 presentation writes); i18n specifiers 0 and orphan keys 0; RTL clean; repo audit PROBLEMS 0 (550 files / 3904 R.string refs / 4678 base strings); licence gate no GPL. `en`/`ar` key parity for the gaming strings verified key-by-key (65/65).

### Explicit limits

- Foreground-game identity comes from `FpsMonitorUtil.getForegroundPackage()` — a **once-per-poll shell read**, not an instant signal; the poll is 1500 ms for that reason. It is not a per-game FPS measurement and is never labelled as one.
- `HudSampler` is a **single-owner** reader (`HUD_OWNER_OVERLAY`): the Game Space panel and the existing FPS overlay cannot both run. Starting one takes the reader from the other. This is an existing declared constraint, not a new defect.
- No session history is written to disk; the panel shows only the in-memory `HudRecorder` tally, and says so when there is none. Dropped samples are reported, not hidden.
- The panel's refresh-rate cycle is not persisted: after the service stops, the last forced rate stays in the system until `RefreshRateReceiver` is asked to reset (the library/lobby offers no reset button yet). That is a known gap, not a claimed feature.
- Do-Not-Disturb is **not** written from the panel. `AppMonitor` already owns `zen_mode` for the per-app session; writing it from a second place would be a second writer for a global setting.
- The auto-start depends on `MaxManagerApplication.onCreate`, so a device that never launches MaxManager after a reboot gets no panel until it does. A boot receiver was **not** added.
- No `SYSTEM_ALERT_WINDOW` permission flow is added here: without the grant `OverlayWindow.mount` fails, the service stops itself and `isRunning` stays false — it does not claim to run.
- Device-verified: nothing. Overlay permission grant/revocation, rotation, RTL mirroring of the handle, touch-through, controller input, insets on a real landscape game and process death remain **Android/device tests**, not performed.
- Compilation of the full Android build remains **unverified in this environment** (no AGP/Hilt/KSP/aapt2). The type harness is not a build.

## Follow-up: omitted membership and overlay failure paths — 2026-10-03

Automatic games can now be explicitly removed via persisted `game_library_excluded`; exclusions win over detection and old manual entries. Re-adding clears exclusion. Manual/exclusion sets are committed together using existing settings preferences, without deleting shared hardware profiles or favorites. Metadata validates package names and bounds (4096); invisible packages are retained rather than guessed uninstalled. Failed preference operations preserve published state and show the existing error. Five regression tests exercise production membership/filter policy; Android SharedPreferences commit failure itself remains unverified.

Alongside this, spoof import/local-policy and GLOBAL dormant-binding regressions were fixed. Final combined pure harness: **422 tests /0 failures**, 73 inputs /253 classes; type harness **552 inputs /3262 classes /0 errors** (four existing duplicate audio-root warnings). Structural **2165/0**, self-test17/17, health/i18n/prune/RTL/source-JNI clean; repo audit545/3856/4629/PROBLEMS0. No aapt2/binary JNI/device/reviewer/AGP claim, no Gradle/APK/commit/push. No new text or third-party code.

## Verification of the first tranche (historical)

- Pure harness `bash build/kverify-audio/run-all.sh`: **413 tests / 0 failures**, 73 source inputs /252 classes (17 new shared-document/persistence tests over 396).
- Local Android/Kotlin/Compose main+debug type harness `bash build/kverify-android/run-all.sh`: **552 inputs /3261 classes /0 errors**, re-run after the final persisted-master-switch edit. R/BuildConfig are stubs; this is not AGP/Hilt/KSP/aapt2/R8.
- Structural/health/i18n/prune/RTL/source-JNI gates executed; resource gate reports no aapt2, not resource success. UI hardware-write debt dropped 21→20 and ceiling ratcheted down.
- No Gradle/APK/deploy/commit/push. **Compilation unverified in this environment** for the full Android build. Device readback/SELinux/root/AtomicFile/overlay and process death remain unverified.

See existing `docs/ai/GAME-SPACE-PLAN.md` for prior seven-stage planning. The owner's new specification requires more than that plan's first-stage library; this README does not mark the entire request done.
