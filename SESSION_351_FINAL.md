# SESSION 351 FINAL — Invoice Adjustments, Referrals & Post-Posting Corrections

**Source of truth:** `Verto-v350-invoice-details-tabs-collections.zip`  
**Output:** `Verto-v351-invoice-adjustments-referrals.zip`  
**Date:** 2026-08-24

## Scope implemented

1. **Sale edit UX uses the new progressive editor**
   - Local SALE edit no longer falls back to the replaced legacy sale editor.
   - Live purchase/international flows remain on their still-required editor.

2. **Invoice-level discount**
   - Discount is persisted as invoice data (`discount`, `discount_minor`).
   - Item unit prices remain unchanged.
   - Net total is gross minus invoice discount.
   - Validation rejects negative discount, discount >= gross, and payment > net.

3. **Commission beneficiary + referral attribution**
   - Buyer is MARKETER/WORKSHOP_OWNER -> buyer is beneficiary (`BUYER`).
   - Ordinary/company buyer + eligible marketer/workshop referrer -> referrer is beneficiary (`REFERRER`).
   - Otherwise no commission attribution (`NONE`).
   - Referrer UI is hidden when the buyer already qualifies as the beneficiary.
   - Commission attribution edits require commission-management permission.
   - Settled/withdrawable commission beneficiary is protected from silent reassignment in the packaged server migration.

4. **POSTED sale corrections without exposing accounting complexity to the user**
   - User edits the sale naturally.
   - Backend rejects category/customer/payment-mode flips that would corrupt settlement identity.
   - For financial changes, prior inventory effect is reversed then the corrected effect is posted.
   - Original `createdAt`/`postedAt` are preserved.
   - `lifecycleVersion` increments.
   - Audit summary records the post-posting correction.
   - Credit invoice net cannot be reduced below payments already recorded.

5. **Linked return flow**
   - SALE details expose Return when authorized and eligible.
   - Per-line return quantity is capped at original quantity.
   - Reason and optional cash refund are captured.
   - Return uses the existing immutable invoice-return use case rather than mutating the original sale silently.

6. **Persistence/sync**
   - Room schema upgraded 84 -> 85.
   - Invoice persistence/sync DTOs include discount and commission attribution.
   - Draft state persists discount/referrer.
   - Commission update uses lifecycle CAS semantics.

7. **Legacy removal**
   - Deleted `CommissionBeforeSaveDialog`.
   - Removed `showCommissionBeforeSave` and `draftCommission` state/effects.
   - Replaced SALE-edit routing no longer uses the old sale editor.

## Server migration packaged, not applied

`supabase/migrations/20260823235600_v351_invoice_adjustments_referrals.sql`

It adds server invoice discount/referral fields, beneficiary constraints/indexes, permission protection, beneficiary-aware commission ledger synchronization, eligibility view behavior, and admin commission-needed notification behavior.

**Production Supabase was inspected for compatibility, but this migration was NOT executed in this session.**

## Verification

`./scripts/verify-invoice-v351.sh`

Result:

```text
PASS: 349 progressive sale flow retained and extended
PASS: 350 details tabs retained
PASS: Room schema 85 persists discount/referral attribution
PASS: discount and buyer/referrer attribution wired
PASS: replaced legacy commission UI removed
PASS: posted sale correction preserves accounting controls
PASS: linked return flow exposed from invoice
PASS: server migration protects referral commission truth
PASS: commission UI resolves server beneficiary
INVOICE_351_STATIC_GATE=PASS
```

Additional checks:

```text
SETTLEMENT_POLICY=PASS
XML_PARSE=PASS (2)
BASH_SYNTAX=PASS
LEGACY_COMMISSION_UI_SCAN=PASS
```

## Android build status

A Gradle compile was attempted, but the environment could not obtain Gradle 8.9:

```text
Downloading https://services.gradle.org/distributions/gradle-8.9-bin.zip
java.net.UnknownHostException: services.gradle.org
```

Therefore:

- `INVOICE_351_STATIC_GATE=PASS`
- `ANDROID_COMPILE=BLOCKED_BY_ENVIRONMENT`
- No compile PASS is claimed.

## Acceptance result

**SESSION_351 = PASS_STATIC / BUILD_BLOCKED_ENVIRONMENT**
