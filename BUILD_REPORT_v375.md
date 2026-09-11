# Build report — Verto v375

- Static contract verification: PASS.
- Live Supabase RPC signature verified: `verto_issue_optimal_registration_code(p_client_id uuid, p_expires_in_minutes integer)`.
- Gradle unit/build execution: BLOCKED_ENVIRONMENT.
- Reason: Gradle 8.9 distribution is not cached; network access to services.gradle.org is unavailable.
- No dependency versions or Gradle configuration were changed.
