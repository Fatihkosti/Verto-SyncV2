# SESSION 368 — Sale credit invoice save repair

Date: 2026-08-28
Source: Verto-v367

## Root cause

The active organization had an empty `organization_settings.currency`. The invoice write pipeline
normalizes every LOCAL invoice to the organization's functional currency and intentionally rejects a
blank functional currency in `InvoiceWriteCoordinator.normalizeWriteIdentity()`. That rejection is an
`IllegalArgumentException`; the legacy `ErrorHumanizer` classified it as `Unknown`, so the UI showed
only the generic save failure.

Server evidence before repair: `currency = ''`. The organization setting was corrected to `SDG`.

## Code repair

1. `PreferencesInvoiceSettingsAdapter.functionalCurrencyCode()` now treats a blank pre-F246 migrated
   currency as the legacy SDG functional currency. Explicit organization settings still win.
2. `CrashReporter.recordException()` now includes a redacted operation, exception type, redacted
   message, and sanitized stack trace in logcat; local crash logs also include the operation.
3. `ErrorHumanizer` passes the business operation (for example, `حفظ الفاتورة`) to the crash reporter.

## Safety

- No invoice totals, stock rules, payment allocation rules, or credit settlement behavior changed.
- No secrets are logged. Existing `SensitiveDataRedactor` still strips tokens, email, phone, UUIDs,
  and credential assignments.
- The fallback applies only when the migrated functional-currency setting is blank.
