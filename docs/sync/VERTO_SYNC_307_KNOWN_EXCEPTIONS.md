# Verto Sync 307 — Known Static Exceptions

The user explicitly authorized continuing Session 307 while documenting the nine remaining static verifier exceptions.

This is a deviation from the original zero-exception acceptance gate. The final status must therefore be reported as `PASS_STATIC_WITH_DOCUMENTED_EXCEPTIONS`, not the contract's clean `PASS_STATIC_OUTBOX_FIRST_PRODUCERS` verdict.

The nine waived checks are recorded verbatim in `VERTO_SYNC_307_KNOWN_EXCEPTIONS.json`. They remain visible in the final verification JSON as `WAIVED_KNOWN_EXCEPTION` and are not rewritten as ordinary `PASS` results.

Runtime V2 remains disabled. Build, unit tests, instrumentation, process-death behavior, and network delivery are not claimed as verified.
