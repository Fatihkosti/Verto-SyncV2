# Verto v244 Report

## Implemented

- Added fixed-point Money Core (`Money`, `Quantity`, `ExchangeRate`) with Arabic/English parsing.
- Removed silent invoice quantity/required-price fallbacks from the save path.
- Corrected local purchases to calculate supplier invoices from `buyPrice` only.
- Made local-purchase `sellPrice` optional and independent.
- Unified valid draft calculation between AddDebt UI and invoice Domain.
- Added minor-unit persistence columns and additive Room migration 61→62.
- Made invoice cash-balance arithmetic use minor units after schema 62.
- Added focused Money/invoice acceptance tests and offline verification tools.

## Acceptance evidence

- F244 static contract verifier: **PASS**.
- Direct Kotlin acceptance harness: **PASS**.
- Migration SQL simulation: **PASS**.
- Global Kotlin quality metrics: **no regression versus v243**.
- Gradle/Android build: **not started** because Gradle 8.9 is not cached and network resolution is unavailable in the sandbox.

## Architecture

- Room schema: **62**.
- No destructive migration.
- No dependency changes.
- No logistics files changed.
- Legacy Double/REAL values remain only as compatibility projections for contracts scheduled in F246/F249/F250.

## Exit decision

**F244 code scope complete.** Continue from `Verto-v244-source-of-truth.zip` after running the normal Gradle verification in an environment that has Gradle 8.9 available.
