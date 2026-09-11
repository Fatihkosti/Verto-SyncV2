# ADR: Compose Screenshot Testing

## Decision

Use the official Compose Preview Screenshot Testing plugin when its version is compatible with the current AGP/JDK. Keep the selected version pinned for the repair series. If the plugin cannot bootstrap, keep the test source and report `NOT RUN`; do not silently replace it with an untracked golden workflow.

## Matrix

- Light and dark themes.
- Arabic RTL and mixed Arabic/English values.
- Widths 320, 360, and 412dp.
- Font scales 1.0, 1.3, 1.5, and 2.0.
- Portrait and landscape where the screen supports it.
- Enabled, disabled, loading, error, empty, offline, permission, selected, and focused states.

## Golden policy

Golden generation is an explicit developer action. CI verifies existing references and never updates them automatically. Each reference change requires a Before/After image, reason, reviewer, device configuration, locale, density, and font scale.

## Commands

The repository will expose `screenshotTest` and `verifyScreenshotTest` after the plugin is available. Until then the deterministic inventory and contract checks are the runnable preflight gates.
