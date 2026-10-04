# Old spoof UI audit

See [CURRENT_SPOOF_AUDIT](CURRENT_SPOOF_AUDIT.md) for measured call paths.

The old surface mixes profile storage, package discovery, acknowledgments, selection, validation, apply and status in a 459-line composable. Its private editor cannot be reused by AppSettings. Its three tabs hide the relation between a global default and app overrides because no global default exists in the model.

Replacement design: one repository-owned StateFlow; overview → profiles → app policy → engine evidence; a shared profile editor; app policy controls independent from performance master switch. Saved policy and measured engine configuration are labeled separately. No Verified badge is earned by saving a draft.

Reuse the existing AtomicFile persistence, strict versioned import/export, documented COPG merge contract, canonical arbiter key, and Max design components. Do not destroy user profiles. Retire the old profile surface once the new repository-backed client is connected; retain explanatory/safety components only where they still have callers. No whole-screen layout, asset, branding or source is copied from reference apps.
