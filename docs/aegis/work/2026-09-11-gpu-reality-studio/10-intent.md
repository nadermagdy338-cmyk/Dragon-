# GPU Reality Studio Work Intent

- Outcome: one capability-driven GPU page with accurate nullable telemetry, preview, verified apply, rollback, and session-first persistence.
- Scope: GPU backend, unified ViewModel/screen, navigation migration, caller compatibility review.
- Non-goals: thermal bypass, overclocking, guessed vendor IDs, per-app product redesign, copying supplied UI.
- Baseline refs: design spec, implementation plan, current GPU backend/screens/ViewModels, navigation, AppMonitor.
- Owner lock: GpuHardwareBackend owns discovery/telemetry/mutation; UI and ViewModel never touch sysfs.
- Compatibility lock: old routes and backend methods remain bounded delegates; vendor-specific options require proven matching provider.
- Retirement: old screens/ViewModels become unreferenced and are removed after one migration release.
- Evidence: source contracts now; JUnit/Kotlin compilation when JDK 17 is available.
