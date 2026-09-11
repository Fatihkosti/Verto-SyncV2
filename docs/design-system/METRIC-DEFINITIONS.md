# Design System Metric Definitions

The authoritative scanner emits 15 independent metrics: legacy focused hardcoded debt, full user-facing hardcoded debt, historical core-visible debt, Material MUST_WRAP debt, forbidden Material2, raw dp/sp/color/motion, core-boundary violations, Design-System domain strings, external ds-string references, expired/permanent exceptions, and duplicate guarded primitives.

`legacyFocusedHardcodedCount` is compatibility evidence only. `hardcodedUserFacingStringsFull` is the authoritative migration surface.
