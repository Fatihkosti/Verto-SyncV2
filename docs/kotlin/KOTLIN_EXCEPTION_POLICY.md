---
status: canonical
scope: system
owner: "engineering-quality"
last_verified_against: v314
---
# Verto Kotlin Exception Policy

**Introduced:** v189  
**Applies to:** Production Kotlin

## Core rule

Exception handling must preserve cancellation and must not silently convert a failure into success or an empty result.

```text
CancellationException → rethrow
Expected domain/infrastructure failure → translate at the owning boundary
Unexpected failure → propagate or report at the owning boundary
```

## Broad catches

`catch (Exception)` and `catch (Throwable)` are legacy debt measured by the v188 baseline. New occurrences are forbidden by the monotonic quality gate.

Inside Domain and Application code, broad catches are not acceptable unless the location is an explicitly documented boundary. `catch (Throwable)` is especially prohibited as a general recovery mechanism.

## Suspend work and cancellation

Any broad catch surrounding suspend work must let `CancellationException` escape. A coroutine must not continue normal success processing after cancellation.

Conceptual form:

```kotlin
try {
    // suspend work
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (expected: ExpectedFailure) {
    // translate only when this boundary owns the translation
}
```

## Translation ownership

- Domain/Application: translate only failures that belong to an explicit application/domain contract.
- Data/Infrastructure: translate transport/storage failures only where the public port/repository contract requires it.
- Android/UI boundaries: may report user-facing failures, but must not redefine business success.
- Crash/reporting boundaries: may observe unexpected failures; observation is not permission to swallow them.

## Forbidden patterns

- Returning `Result.success`, `true`, an empty collection, or a normal state only because an unexpected exception was caught.
- Catching `Throwable` to keep a domain/application operation running.
- Swallowing `CancellationException`.
- Adding a broad catch only to reduce crashes without defining the owning recovery contract.
- Using a suppression to hide a new broad catch.

## v191 migration rule

v191 must review broad catches around suspend work and apply the execution contract exactly: cancellation rethrows; expected failures translate at an owned boundary; unexpected failures propagate/report. Existing behavior may not be reinterpreted during that refactor.

## v195 documented Application boundaries

The final v195 gate forbids broad `Exception` / `Throwable` catches in Domain/Application code unless the exact owning boundary is documented here. The currently approved boundaries are:

- `UnifiedHomeSearchUseCase.searchSafely` — isolates one independent search provider failure while rethrowing `CancellationException`; failure of one provider intentionally contributes no results and does not redefine the other providers' results.
- `SendOptimalAudioMessageUseCase.invoke` — owns translation of an infrastructure persistence exception to `PersistenceFailed`; it first deletes the owned preview and always rethrows `CancellationException`.
- `SaveLogisticsDocumentUseCase.invoke` — owns compensating deletion of the imported private document when persistence fails, then rethrows the original exception; cancellation is rethrown after the same compensation.

No other Domain/Application broad catch is allowed without first updating this policy and the static gate in the same reviewed change. `catch(Throwable)` remains forbidden in Domain/Application code.
