# MaxManager — Final UI Audit

## Final design decisions
- Home is a single vertical dashboard: device state → focus/safety → profile/Max AI → CPU/GPU → cores → memory/ZRAM → storage/battery → thermal/history → device details → activity/actions.
- CPU and GPU remain side-by-side cards with live trend plots; no stacked CPU/GPU block.
- Gauge cards use the shared widget language for RAM, ZRAM, storage and battery.
- Shared `Max*` and `Neural*` surfaces use the same tinted depth language, spacing, shapes, typography roles and state tones.
- Missing/unsupported measurements render as `—`; no fake metrics were added.
- Technical values stay LTR inside RTL layouts.
- Shared typography now uses Noto Sans Arabic for prose/headings to avoid device-dependent Arabic fallback while preserving JetBrains Mono for live values.
- Shared panel geometry was tightened to 24dp panels / 18dp tiles with restrained elevation and glow so the UI reads as a premium dashboard rather than a neon gaming skin.

## Validation
- `python3 tools/kt_balance.py --assert` — PASS
- `python3 tools/i18n_coverage.py --assert` — PASS (85 locales, 0 blockers)
- `python3 tools/code_health.py --assert` — PASS
- Gradle APK build was attempted with `bash manager/gradlew :app:assembleDebug --offline` but the environment has no Android SDK/Gradle distribution available and cannot resolve `services.gradle.org` (UnknownHostException). Therefore APK compilation is not claimed as verified here.
