# Session 375 — Optimal company-selection repair

- Source: Verto-v374.zip
- Restored explicit Verto COMPANY selection before issuing an Optimal registration code.
- Search/list shows Verto COMPANY clients and their linked/unlinked state.
- Linked companies cannot be selected for a new code.
- Selected client id is validated again in repository against the current organization.
- Server RPC corrected to `verto_issue_optimal_registration_code(p_client_id, p_expires_in_minutes)`.
- Organization-only RPC `verto_issue_optimal_company_join_code` is no longer used by this screen.
- Returned organization id, client id and company name are verified against the local selection.
- Registration code must be exactly 8 numeric digits and future-dated.
- Removed temporary organization-only UI strings from v374.
