# SESSION 381 — Optimal company visibility repair backport

## Scope
Applied the previously verified Optimal company visibility repair on top of Verto v380 without overwriting v380's unrelated Home/Price List changes.

## Repair
- `OptimalCompaniesRemoteRefresher` no longer filters the live `clients` query using a potentially stale cached organization id.
- Server RLS is authoritative for the authenticated tenant.
- If live rows prove a different organization id, the local session cache is repaired through `SessionWriter`.
- Active Optimal links are read through `verto_list_optimal_links()` RPC instead of direct Data API access to `optimal_verto_links`.
- The secure tenant-scoped RPC migration is included in the source package.

## Expected behavior
- COMPANY clients belonging to the authenticated Verto organization appear in the Optimal-link selection screen.
- Already-linked clients remain excluded by the live link projection.
- Old Room rows cannot make cross-organization companies selectable.
