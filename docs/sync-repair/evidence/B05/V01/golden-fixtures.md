# B05 frozen-wire golden fixture

`FrozenMutationCodecTest` asserts this exact UTF-8 sequence (with no trailing newline in the hashed value):

```json
{"contractFamily":"verto-unified-sync","contractVersion":2,"mutationId":"m-1","organizationId":"org-1","aggregateType":"EXPENSE","aggregateId":"expense-1","operationType":"UPSERT","baseVersion":7,"localSequence":11,"aggregateSequence":3,"payloadVersion":1,"payload":{"amountMinor":123,"note":"x"},"createdAtEpochMillis":99,"commandBatchId":null,"commandOrder":null,"dependsOnMutationId":null}
```

- SHA-256: `3c92c39cedace92bc86f0c76ef368958e2b552bcbc39adbdd13ad33677835b16`
- Encoding: UTF-8, no BOM.
- Transport-only data: `leaseToken` is intentionally absent.
- Retry rule: `wire_json` and `wire_sha256` are written only while all frozen columns are null; every later preparation returns the stored text and verifies its hash.
- Batch rule: a batch stores an ordered manifest of original owner references, then freezes the exact member strings and their hashes. Members are not moved into a second outbox.

