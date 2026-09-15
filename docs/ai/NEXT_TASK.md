# NEXT_TASK

NT-01 is **accepted with fixes** — see `VERIFICATION_NT01.md` for the gate results. Two tasks are queued: a short correction pass (NT-01b), then the next product step (NT-02).

---

# NT-01b — Correction pass on the navigation spine

**Executor:** DeepSeek Harness · **Type:** small fixes, no new surfaces · **Read:** `VERIFICATION_NT01.md`, `DECISIONS.md` (ADR-02, ADR-06, ADR-07, ADR-14, ADR-17)

## Fixes, in order

1. **F-01 crash path.** Remove `openActivityDetail` from `MaxNavActions` (the `app_detail/{packageName}` route belongs to the nested host inside `ActivitylauncherScreen`). If any caller needs it, hoist the route into `MaxDestinations` + `maxNavGraph` instead — do not leave a function that navigates to an unregistered route.
2. **F-01b registry purity.** Build `openApp(pkg)` from the registry: `MaxDestination.AppSettings.route.replace("{pkg}", pkg)`. No hand-written route strings anywhere, including inside `ui/navigation/`.
3. **F-02 layering.** Change `MaxDomainCard` to primitives and remove its `nd.max.ui.navigation` import:
   `MaxDomainCard(title: String, subtitle: String, icon: ImageVector, state: String? = null, trust: MaxDataTrust? = null, onClick: () -> Unit, modifier: Modifier = Modifier)`.
   `ControlScreen` maps `MaxDestination` → these parameters. Render `state`/`trust` when present; when absent render nothing (never a placeholder number, ADR-07).
4. **F-03 titles.** Give `Terminal`, `SetEdit`, `ActivityLauncher`, `KernelFlasher`, `AppSettings`, `GetStarted` their own `titleRes` keys; add them to `values/` **and** `values-ar/` in the same change (ADR-14).
5. **F-04 Apps children.** In `ApplistScreen`, add one `MaxSection` + `MaxGroup` header listing `MaxDestination.All.filter { it.parent == MaxDestination.Apps && it != MaxDestination.AppSettings }` (Process manager, Debloat & freeze) using `MaxRow`, so they no longer depend on the legacy tweaks screen.
6. **F-05 root probing.** Stop re-probing per route. Either key the effect on the current **primary** destination, or (preferred) hoist one session state (`root`, `module`, refresh action) provided once in `MainActivity` and consumed by screens. Do not add any new per-screen probe.
7. **F-07 cleanup.** Delete `ui/mainscreens/DomainHubScreens.kt`; remove the duplicated section title in `MaxDomainHubScreen`; drop unused imports (`MaxGroupDivider` in `ControlScreen`, unused icons in `MaxDestinations`); replace fully-qualified inline references with imports. Optionally collapse the nine one-line hub files into a single parameterized graph entry.

## Acceptance criteria

- [ ] `grep -rn 'navigate("' nd/max` → no hand-written route strings at all (registry-derived only).
- [ ] `grep -rn 'nd.max.ui.navigation' nd/max/ui/design` → empty.
- [ ] No two destinations share a `titleRes`; AR parity gate prints no `MISSING_AR`.
- [ ] Process manager and Debloat & freeze reachable from Apps without touching `AllTweaks`.
- [ ] `RootUtils.requestRootAccess()` is not invoked from an effect keyed on every route.
- [ ] `DomainHubScreens.kt` gone; no unused-import warnings introduced in changed files.
- [ ] `VALIDATION.md` §1–6 clean; adoption counters not worse than 16 design importers / 31 `Scaffold(` files.

---

# NT-02 — Max AI as a primary, auditable surface

**Type:** product feature on top of the existing control plane · **Read:** `DESIGN_VISION.md` §3–§5, `DECISIONS.md` (ADR-09, ADR-10, ADR-11), `COMPLETED_WORK.md` §3

## Goal

Make ownership and history visible: the user must be able to see **who controls each knob** and **what was changed, verified, or rolled back** — without inventing any new control semantics.

## Build

| Component (in `ui/design/`) | Contract |
| --- | --- |
| `MaxOwnerChip` | ownership state (You / Max AI / Per-app / Module / Kernel default) + optional release/lock action; tone from `MaxTone`, never colour-only |
| `MaxJournalRow` | one audit entry: timestamp, actor, knob, before → after, verdict (applied / verified / rolled back / vetoed) |
| `MaxSessionBar` | in-flight write or AI action with state and an undo affordance |
| `MaxNumeric` | tabular-figure text style so live values stop jittering |

## Rewrite `MaxAiScreen` (drop the card stack) into four sections

1. **Objective & autonomy** — master switch, objective, profile mode (reuse existing state).
2. **Ownership map** — knobs currently held, grouped by domain, each row an `MaxOwnerChip`; release returns the knob to its baseline through the arbiter.
3. **Journal** — newest-first stream from the arbiter outcomes / event log; filterable by domain; every entry carries its verdict.
4. **Safety & learning** — governor vetoes, rollbacks, and per-knob credibility with `MaxDataTrust` on every number.

Also: surface the session bar on feature screens while a write is in flight, and add an `MaxOwnerChip` to control rows in the six already-migrated screens (additive only, ADR-18).

## Rules

- Read-only access to `core/**`; no new hardware write paths and no UI-side writes (ADR-11). If the control plane does not expose something the UI needs, add a read-only accessor and say so in the report.
- No synthesized history. If the journal source has no entries, show a `MaxCondition` explaining that, not an example row (ADR-07).
- Every string in `values/` + `values-ar/`.
- `MaxAiScreen` must stop declaring its own `Scaffold`; it moves to `MaxScreen`/`MaxListScreen`.

## Acceptance criteria

- [ ] Max AI screen uses the design shell; `Scaffold(` count drops by at least one.
- [ ] Ownership shown for every knob the arbiter currently holds, with a working release action.
- [ ] Journal renders real entries with verdicts, or an explained empty state.
- [ ] No new writes into `core/**` behaviour; existing unit tests unchanged.
- [ ] `VALIDATION.md` §1–6 clean, including §6 (no UI direct hardware writes added).

## Out of scope

Migrating other screen bodies (NT-03), Apps workspace (NT-04), Settings/Appearance consolidation (NT-05), command palette (NT-06).
