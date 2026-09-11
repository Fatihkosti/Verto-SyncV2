---
status: canonical
scope: feature
owner: "performance-engineering"
last_verified_against: v314
---
# Home performance budget — Verto v114

This retained verification artifact defines the device-measured budget for the Home work center.

- Measure at least five cold starts with `scripts/benchmark-v114-home.sh`.
- Record median and p95 `TotalTime` values from the same physical device and build type.
- Treat a p95 regression above 15% against the last accepted device baseline as blocking.
- Measure unified-search input-to-idle only with a signed-in account and representative local data.
- Keep Home quick actions, pending actions, activity feed, search screen, and search ViewModel in the baseline profile.

No benchmark number is claimed by this source-only archive because device output is not bundled.
