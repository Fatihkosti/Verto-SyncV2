---
status: canonical
scope: system
owner: "data:network"
last_verified_against: "v315 historical registry; B10-V01 delta interface source update"
---
# RPC Reference

This reference is reconstructed from production v315 Kotlin plus repository SQL. Discovery result: **49 call sites / 48 unique RPCs / 21 repository-defined / 27 repository-definition absent**. Server semantics are never inferred from a client name alone. The historical discovery counts below are not a fresh B10 audit; the delta Inbox call was updated to B10-V01 with server verification explicitly pending.

## `admin_create_invite_code`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/remote/InviteRemoteSource.kt` (`rpc` call near line 85)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( "admin_create_invite_code", AdminCreateInviteCodeRequest( employeeName = cleanName, jobTitle = cleanJobTitle, actualJoinDate = cleanJoinDate, permissions = permissionsJson, expiresI`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/remote/InviteRemoteSource.kt` (`rpc` call near line 85).

## `admin_revoke_invite_code`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/remote/InviteRemoteSource.kt` (`rpc` call near line 108)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( "admin_revoke_invite_code", InviteCodeLookupRequest(inviteCode = code) ) Unit } } suspend fun getInviteCodeDetails(inviteCode: String): Result<InviteCodeDetails> = withContext(Dispatchers.IO) { runCatching { val cod`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/remote/InviteRemoteSource.kt` (`rpc` call near line 108).

## `allocate_invoice_number`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/remote/InvoiceNumberAllocator.kt` (`rpc` call near line 31)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc("allocate_invoice_number", AllocateInvoiceNumberRequest(orgId)) .data .trim() .toIntOrNull() }.getOrNull() } }`
- **Return type / source-derived response example:** `raw/scalar PostgREST response `.data` interpreted by caller`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/remote/InvoiceNumberAllocator.kt` (`rpc` call near line 31).

## `allocate_logistics_shipment_number`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/remote/LogisticsShipmentNumberAllocator.kt` (`rpc` call near line 21)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc( "allocate_logistics_shipment_number", AllocateLogisticsShipmentNumberRequest(organizationId), ) .data .trim() .toIntOrNull() }.getOrNull() } }`
- **Return type / source-derived response example:** `raw/scalar PostgREST response `.data` interpreted by caller`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/logistics_v2/006_operational_control.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/remote/LogisticsShipmentNumberAllocator.kt` (`rpc` call near line 21); `docs/sql/logistics_v2/006_operational_control.sql`.

## `approve_withdrawal`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/repository/WithdrawalCommandRemoteSource.kt` (`rpc` call near line 141)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc( "approve_withdrawal", ApproveWithdrawalParams( withdrawalId = id, transactionRef = transactionRef, clientRequestId = current.clientRequestId ) ) } } sus`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** PARTIAL — request/clientRequest identity is explicit at the call site; full server dedup semantics require SQL/runtime evidence.
- **Retry semantics:** Caller re-reads authoritative withdrawal row after RPC; timeout is not treated as final result.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `sql/approve_withdrawal.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/repository/WithdrawalCommandRemoteSource.kt` (`rpc` call near line 141); `sql/approve_withdrawal.sql`.

## `archive_inventory_item_v2`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventory.kt` (`rpc` call near line 64)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc("archive_inventory_item_v2", ArchiveInventoryItemRequest(id)) }.onFailure { e -> android.util.Log.e("SyncManager", "Remote deletion failed") }.isSuccess if (archived) { userPrefs.removePendingInventoryDeletion`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v262_inventory_atomic_sync.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventory.kt` (`rpc` call near line 64); `docs/sql/v262_inventory_atomic_sync.sql`.

## `complete_withdrawal`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/repository/WithdrawalCommandRemoteSource.kt` (`rpc` call near line 182)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( "complete_withdrawal", CompleteWithdrawalParams(withdrawalId = id, adminNote = adminNote) ) } } }`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** Caller re-reads authoritative withdrawal row after RPC; timeout is not treated as final result.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/repository/WithdrawalCommandRemoteSource.kt` (`rpc` call near line 182).

## `create_all_employees_notification`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/repository/NotificationRepository.kt` (`rpc` call near line 78)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( "create_all_employees_notification", CreateAudienceNotificationRequest( title = title.trim(), body = body.trim(), type = type.name, relatedE`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/repository/NotificationRepository.kt` (`rpc` call near line 78).

## `create_direct_employee_notification`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/repository/NotificationRepository.kt` (`rpc` call near line 63)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( "create_direct_employee_notification", CreateDirectEmployeeNotificationRequest( employeeId = targetUserId, title = title.trim(), body = body.trim(),`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/repository/NotificationRepository.kt` (`rpc` call near line 63).

## `create_manager_only_notification`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/repository/NotificationRepository.kt` (`rpc` call near line 92)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( "create_manager_only_notification", CreateAudienceNotificationRequest( title = title.trim(), body = body.trim(), type = type.name, relatedEn`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/repository/NotificationRepository.kt` (`rpc` call near line 92).

## `create_new_conversation`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/repository/InternalMessagingRemoteSource.kt` (`rpc` call near line 240)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( "create_new_conversation", CreateNewConversationParams(targetClientId, subject) ).decodeAs<ConversationDto>().also { conversation -> TenantIsolationPolicy.requireSameTenant(orgId, conversation.orgId) require(c`
- **Return type / source-derived response example:** `ConversationDto`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/repository/InternalMessagingRemoteSource.kt` (`rpc` call near line 240).

## `create_organization_with_admin`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/remote/AuthAccountRemoteSource.kt` (`rpc` call near line 51)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( "create_organization_with_admin", CreateOrgRequest(orgName = orgName, adminName = ownerName, adminUid = uid) ).data prefs.setShopName(orgName) prefs.setOwnerName(ownerName) sessionWriter.setUserId(uid) sessionWriter`
- **Return type / source-derived response example:** `raw/scalar PostgREST response `.data` interpreted by caller`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/remote/AuthAccountRemoteSource.kt` (`rpc` call near line 51).

## `create_system_all_employees_notification`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/repository/NotificationRepository.kt` (`rpc` call near line 248)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( "create_system_all_employees_notification", CreateAudienceNotificationRequest( title = payload.title.trim(), body = payload.body.trim(), type = payload.type.name, relatedEntityId = payload`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/repository/NotificationRepository.kt` (`rpc` call near line 248).

## `credit_marketer_balance`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/repository/CommissionRemoteSource.kt` (`rpc` call near line 158)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( "credit_marketer_balance", CreditBalanceParams( clientId = clientId, orgId = orgId, amount = amount.toRemoteDecimal(), referenceId = referenceId,`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/repository/CommissionRemoteSource.kt` (`rpc` call near line 158).

## `deactivate_org_member_admin`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/remote/OrganizationAccessRemoteSource.kt` (`rpc` call near line 197)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( "deactivate_org_member_admin", DeactivateMemberRequest(memberUid = targetUserId, reason = reason.trim().take(500)) ) Unit } } } private fun EmployeePermissionsRowDto.toDomain() = permissions.toDomain(userId, orgId) `
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/remote/OrganizationAccessRemoteSource.kt` (`rpc` call near line 197).

## `financial_sync_apply_event_v1`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncFinancialEvents.kt` (`rpc` call near line 86)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc( "financial_sync_apply_event_v1", event.toApplyRequest(), ).decodeList<FinancialEventApplyResult>().single() } catch (failure: Exception) { db.invoiceDao().retryFinancialOutbox(`
- **Return type / source-derived response example:** `List<FinancialEventApplyResult>`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** PARTIAL — invoked from durable sync/outbox identity paths; server enforcement is verified only when supported by SQL definition.
- **Retry semantics:** Durable outbox/inbox path records retry eligibility/backoff on failure.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v249_financial_event_sync.sql`; `docs/sql/v252_invoice_returns_financial_sync.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncFinancialEvents.kt` (`rpc` call near line 86); `docs/sql/v249_financial_event_sync.sql`; `docs/sql/v252_invoice_returns_financial_sync.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`.

## `financial_sync_pull_events_v1`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncFinancialEvents.kt` (`rpc` call near line 154)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc( "financial_sync_pull_events_v1", FinancialEventPullRequest(cursor, INBOX_BATCH_SIZE), ).decodeList<FinancialInboundEventDto>() if (remote.isEmpty()) return require(remote.all { it.organizationId == orgId }) { "finan`
- **Return type / source-derived response example:** `List<FinancialInboundEventDto>`.
- **Side effects:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** NOT APPLICABLE for client mutation identity; read retry remains subject to cursor/scope validation.
- **Retry semantics:** Durable outbox/inbox path records retry eligibility/backoff on failure.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v249_financial_event_sync.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncFinancialEvents.kt` (`rpc` call near line 154); `docs/sql/v249_financial_event_sync.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`.

## `get_invite_code_details`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/remote/InviteRemoteSource.kt` (`rpc` call near line 123)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( "get_invite_code_details", InviteCodeLookupRequest(inviteCode = code) ).data ) if (row.used) error("تم استخدام هذا الكود مسبقاً") if (SupabaseTimestamp.isExpired(row.expiresAt)) {`
- **Return type / source-derived response example:** `raw/scalar PostgREST response `.data` interpreted by caller`.
- **Side effects:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT APPLICABLE for client mutation identity; read retry remains subject to cursor/scope validation.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/remote/InviteRemoteSource.kt` (`rpc` call near line 123).

## `get_marketer_stats`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/repository/CommissionRemoteSource.kt` (`rpc` call near line 69)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc("get_marketer_stats") .decodeList<MarketerStatsDto>() } } /** * يرسل تذكير انقطاع (`INACTIVITY`) لمسوّق منقطع — يُستخدم من زر التذكير في لوحة المستخدمين. * يحوّل `clientId` إلى `user_id` عبر [getMarketerUserId] ثم يُ`
- **Return type / source-derived response example:** `List<MarketerStatsDto>`.
- **Side effects:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT APPLICABLE for client mutation identity; read retry remains subject to cursor/scope validation.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/repository/CommissionRemoteSource.kt` (`rpc` call near line 69).

## `get_my_notifications_cache`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncNotifications.kt` (`rpc` call near line 12)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc("get_my_notifications_cache") .decodeList<RemoteNotificationDto>() val localNotifications = remoteNotifications.mapNotNull { dto -> val notificationType = runCatching { NotificationType.valueOf(dto.type) }.getOrNull(`
- **Return type / source-derived response example:** `List<RemoteNotificationDto>`.
- **Side effects:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT APPLICABLE for client mutation identity; read retry remains subject to cursor/scope validation.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncNotifications.kt` (`rpc` call near line 12).

## `inventory_apply_commands_v2`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventoryLedgerV2.kt` (`rpc` call near line 47)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc("inventory_apply_commands_v2", InventoryCommandBatchRequest(commands)) .decodeList<InventoryCommandAckDto>() } catch (failure: Exception) { pending.forEach { row -> dao.retryInventoryStockOutbox(row.id, now + invento`
- **Return type / source-derived response example:** `List<InventoryCommandAckDto>`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** PARTIAL — invoked from durable sync/outbox identity paths; server enforcement is verified only when supported by SQL definition.
- **Retry semantics:** Durable outbox/inbox path records retry eligibility/backoff on failure.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v262_inventory_atomic_sync.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventoryLedgerV2.kt` (`rpc` call near line 47); `docs/sql/v262_inventory_atomic_sync.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`.

## `inventory_apply_cost_revisions_v2`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventoryLedgerV2.kt` (`rpc` call near line 98)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc("inventory_apply_cost_revisions_v2", InventoryCostBatchRequest(revisions)) .decodeList<InventoryCostAckDto>() } catch (failure: Exception) { pending.forEach { row -> dao.retryInventoryCostOutbox(row.id, now + invento`
- **Return type / source-derived response example:** `List<InventoryCostAckDto>`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** PARTIAL — invoked from durable sync/outbox identity paths; server enforcement is verified only when supported by SQL definition.
- **Retry semantics:** Durable outbox/inbox path records retry eligibility/backoff on failure.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v262_inventory_atomic_sync.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventoryLedgerV2.kt` (`rpc` call near line 98); `docs/sql/v262_inventory_atomic_sync.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`.

## `inventory_prepare_reconciliation_batch_v2`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventoryReconciliation.kt` (`rpc` call near line 41)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc( "inventory_prepare_reconciliation_batch_v2", InventoryReconciliationBatchRequest( contractVersion = INVENTORY_RECONCILIATION_CONTRACT_VERSION, afterItemId = afterItemId, limit = INVENT`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v258_inventory_reconciliation.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventoryReconciliation.kt` (`rpc` call near line 41); `docs/sql/v258_inventory_reconciliation.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`.

## `inventory_pull_cost_revisions_v2`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventoryLedgerV2.kt` (`rpc` call near line 155)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc("inventory_pull_cost_revisions_v2", InventoryMovementPullRequest(cursor, 500)) .decodeList<InventoryCostRevisionDto>() if (remote.isEmpty()) return require(remote.zipWithNext().all { (a, b) -> a.costSequence < b.cost`
- **Return type / source-derived response example:** `List<InventoryCostRevisionDto>`.
- **Side effects:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** NOT APPLICABLE for client mutation identity; read retry remains subject to cursor/scope validation.
- **Retry semantics:** Durable outbox/inbox path records retry eligibility/backoff on failure.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v262_inventory_atomic_sync.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventoryLedgerV2.kt` (`rpc` call near line 155); `docs/sql/v262_inventory_atomic_sync.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`.

## `inventory_pull_movements_v2`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventoryLedgerV2.kt` (`rpc` call near line 122)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc("inventory_pull_movements_v2", InventoryMovementPullRequest(cursor, 500)) .decodeList<InventoryMovementDto>() if (remote.isEmpty()) return require(remote.zipWithNext().all { (a, b) -> (a.serverSequence ?: 0L) < (b.se`
- **Return type / source-derived response example:** `List<InventoryMovementDto>`.
- **Side effects:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** NOT APPLICABLE for client mutation identity; read retry remains subject to cursor/scope validation.
- **Retry semantics:** Durable outbox/inbox path records retry eligibility/backoff on failure.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v262_inventory_atomic_sync.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncInventoryLedgerV2.kt` (`rpc` call near line 122); `docs/sql/v262_inventory_atomic_sync.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`.

## `join_organization_with_code`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/remote/InviteRemoteSource.kt` (`rpc` call near line 46)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( "join_organization_with_code", JoinOrgRequest( inviteCode = inviteCode.trim().uppercase(), memberName = invite.employeeName, memberUid = uid ) ) prefs.setOwnerName(invite.employeeName`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/remote/InviteRemoteSource.kt` (`rpc` call near line 46).

## `mark_all_notifications_read`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/repository/NotificationRepository.kt` (`rpc` call near line 148)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc("mark_all_notifications_read") } if (profile.role == "admin") { dao.markAllAsReadForManager(profile.id) } else { dao.markAllAsReadForUser(profile.id) } } suspend fun deleteOldNotifications(daysToKeep: Long = 90)`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/repository/NotificationRepository.kt` (`rpc` call near line 148).

## `mark_notification_read`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/repository/NotificationRepository.kt` (`rpc` call near line 140)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc("mark_notification_read", MarkNotificationReadRequest(id)) } dao.markAsRead(id) } suspend fun markAllAsReadForCurrentUser() { val profile = authRepository.getMyProfile() ?: return runCatching { supabase.postgrest.rpc`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/repository/NotificationRepository.kt` (`rpc` call near line 140).

## `optimal_backend_contract`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Caller(s):** `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/data/SupabaseOptimalRegistrationRemoteSource.kt` (`rpc` call near line 36)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc(function = "optimal_backend_contract") .decodeList<OptimalBackendContractRemoteDto>() .singleOrNull() ?: error("optimal_backend_contract_invalid_row_count") check(backendContract.vertoRegistrationReady) { "optimal_re`
- **Return type / source-derived response example:** `List<OptimalBackendContractRemoteDto>`.
- **Side effects:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT APPLICABLE for client mutation identity; read retry remains subject to cursor/scope validation.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/data/SupabaseOptimalRegistrationRemoteSource.kt` (`rpc` call near line 36).

## `pay_out_commission`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/repository/CommissionRemoteSource.kt` (`rpc` call near line 205)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( "pay_out_commission", PayOutCommissionParams( invoiceIds = invoiceIds, bankName = bankName, transactionRef = txRef, clientRequestId = clientRequestId`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** PARTIAL — request/clientRequest identity is explicit at the call site; full server dedup semantics require SQL/runtime evidence.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/repository/CommissionRemoteSource.kt` (`rpc` call near line 205).

## `pay_out_free_amount`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/repository/CommissionRemoteSource.kt` (`rpc` call near line 182)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( "pay_out_free_amount", PayOutFreeAmountParams( clientId = clientId, amount = amount.toRemoteDecimal(), bankName = bankName, transactionRef = txRef,`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/repository/CommissionRemoteSource.kt` (`rpc` call near line 182).

## `post_payment_v2`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/remote/FinancialPostingRemote.kt` (`rpc` call near line 49)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc("post_payment_v2", request).decodeAs() override suspend fun reversePayment(paymentId: String, requestId: String): FinancialPostingResult = VertoSupabase.client.postgrest.rpc( "reverse_payment_v2", ReversePaymentV2Req`
- **Return type / source-derived response example:** `typed by caller inference (`decodeAs()`); inspect enclosing return type`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** PARTIAL — request/clientRequest identity is explicit at the call site; full server dedup semantics require SQL/runtime evidence.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/remote/FinancialPostingRemote.kt` (`rpc` call near line 49).

## `register_push_token_v2`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/remote/PushTokenRepository.kt` (`rpc` call near line 37)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc("register_push_token_v2", request) } Unit }.onFailure { if (it is CancellationException) throw it } } suspend fun deleteCurrentUserToken(): Result<Unit> = withContext(Dispatchers.IO) { runCatching { val deviceId = Fc`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** Client retries up to 3 attempts with exponential delay (500 ms base).
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/remote/PushTokenRepository.kt` (`rpc` call near line 37).

## `reverse_payment_v2`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/remote/FinancialPostingRemote.kt` (`rpc` call near line 52)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( "reverse_payment_v2", ReversePaymentV2Request(paymentId, requestId) ).decodeAs() }`
- **Return type / source-derived response example:** `typed by caller inference (`decodeAs()`); inspect enclosing return type`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** PARTIAL — request/clientRequest identity is explicit at the call site; full server dedup semantics require SQL/runtime evidence.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/remote/FinancialPostingRemote.kt` (`rpc` call near line 52).

## `revoke_push_token_v2`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/remote/PushTokenRepository.kt` (`rpc` call near line 47)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc("revoke_push_token_v2", RevokePushTokenRequest(deviceId)) Unit }.onFailure { if (it is CancellationException) throw it } } private suspend fun <T> withRetry(maxAttempts: Int = 3, block: suspend () -> T): T { var last`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/remote/PushTokenRepository.kt` (`rpc` call near line 47).

## `sync_ack_v2`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** See caller; server side effects NOT VERIFIED FROM REPOSITORY.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncProtocolV2Remote.kt` (`rpc` call near line 58)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( function = "sync_ack_v2", parameters = SyncAckV2Request(revision) ) } } data class SyncV2PullTicket( val startingRevision: Long, val nextRevision: Long, val eventCount: Int, val tombstoneCount: Int ) /** * Drains an`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** See caller; server side effects NOT VERIFIED FROM REPOSITORY.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncProtocolV2Remote.kt` (`rpc` call near line 58).

## `sync_pull_v2`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=no; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** See caller; server side effects NOT VERIFIED FROM REPOSITORY.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncProtocolV2Remote.kt` (`rpc` call near line 52)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** **NOT VERIFIED FROM REPOSITORY**
- **Parameters / source-derived request example:** `rpc( function = "sync_pull_v2", parameters = SyncPullV2Request(sinceRevision, limit) ).decodeAs() override suspend fun acknowledge(revision: Long) { VertoSupabase.client.postgrest.rpc( function = "sync_ack_v2", parameter`
- **Return type / source-derived response example:** `typed by caller inference (`decodeAs()`); inspect enclosing return type`.
- **Side effects:** See caller; server side effects NOT VERIFIED FROM REPOSITORY.
- **Transaction boundary:** **NOT VERIFIED FROM REPOSITORY**
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** **NOT VERIFIED FROM REPOSITORY**
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncProtocolV2Remote.kt` (`rpc` call near line 52).

## `verto_apply_sync_mutation`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncPushRemote.kt` (`rpc` call near line 25)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc( function = "verto_apply_sync_mutation", parameters = UnifiedSyncPushRpcRequestWire(mutation.toPushWire()), ).decodeAs<UnifiedSyncPushResponseWire>() require(wire.contractFamily == UNIFIED_SYNC_CONTRACT_FAMILY && wir`
- **Return type / source-derived response example:** `UnifiedSyncPushResponseWire`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** PARTIAL — invoked from durable sync/outbox identity paths; server enforcement is verified only when supported by SQL definition.
- **Retry semantics:** Unified outbox engine may retry the same logical mutation identity.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `supabase/migrations/20260821123000_v309_verto_unified_sync_push.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncPushRemote.kt` (`rpc` call near line 25); `supabase/migrations/20260821123000_v309_verto_unified_sync_push.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`.

## `verto_begin_sync_bootstrap`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncBootstrapRemote.kt` (`rpc` call near line 27)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc( "verto_begin_sync_bootstrap", UnifiedSyncBootstrapBeginRequestWire(scope.scopeId) ).decodeAs<UnifiedSyncBootstrapStartWire>() require(w.scopeId == scope.scopeId && w.contractFamily == scope.contractFamily && w.contr`
- **Return type / source-derived response example:** `UnifiedSyncBootstrapStartWire`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v305_verto_unified_sync_server.sql`; `supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncBootstrapRemote.kt` (`rpc` call near line 27); `docs/sql/v305_verto_unified_sync_server.sql`; `supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql`.

## `verto_get_reconciliation_manifest`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncBootstrapRemote.kt` (`rpc` call near line 54)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc( "verto_get_reconciliation_manifest", UnifiedSyncManifestRequestWire(scope.scopeId, partitionToken) ).decodeAs<UnifiedSyncManifestPageWire>() require(w.scopeId == scope.scopeId) { "FAIL_BOOTSTRAP_SCOPE_MISMATCH: reco`
- **Return type / source-derived response example:** `UnifiedSyncManifestPageWire`.
- **Side effects:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** NOT APPLICABLE for client mutation identity; read retry remains subject to cursor/scope validation.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v305_verto_unified_sync_server.sql`; `supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncBootstrapRemote.kt` (`rpc` call near line 54); `docs/sql/v305_verto_unified_sync_server.sql`; `supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql`.

## `verto_issue_autodrive_join_code`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/remote/AutoDriveJoinCodeRemoteSource.kt` (`rpc` call near line 30)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc( "verto_issue_autodrive_join_code", IssueAutodriveJoinCodeRequest( organizationId = organizationId, clientId = targetClientId, accountType = targetAccountType, expiresInMinut`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v142_autodrive_join_code_rpc.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/remote/AutoDriveJoinCodeRemoteSource.kt` (`rpc` call near line 30); `docs/sql/v142_autodrive_join_code_rpc.sql`.

## `verto_issue_optimal_company_join_code`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/data/SupabaseOptimalRegistrationRemoteSource.kt` (`rpc` call near line 43)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc( function = "verto_issue_optimal_company_join_code", parameters = IssueOptimalCompanyJoinCodeRequest(clientId = normalizedClientId), ) .decodeList<OptimalCompanyJoinCodeRemoteDto>() .singleOrNull()`
- **Return type / source-derived response example:** `List<OptimalCompanyJoinCodeRemoteDto>`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v123_optimal_company_join_code_by_client.sql`
- **Verified version:** v315 source.
- **Evidence:** `feature/integration/optimal/src/main/kotlin/com/verto/app/feature/integration/optimal/data/SupabaseOptimalRegistrationRemoteSource.kt` (`rpc` call near line 43); `docs/sql/v123_optimal_company_join_code_by_client.sql`.

## `verto_pull_bootstrap_page`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncBootstrapRemote.kt` (`rpc` call near line 43)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc( "verto_pull_bootstrap_page", UnifiedSyncBootstrapPageRequestWire(sessionId, pageToken, limit) ).decodeAs<UnifiedSyncBootstrapPageWire>() return SyncBootstrapPage( w.bootstrapSessionId, w.baselineCursor, w.rows.map {`
- **Return type / source-derived response example:** `UnifiedSyncBootstrapPageWire`.
- **Side effects:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** NOT APPLICABLE for client mutation identity; read retry remains subject to cursor/scope validation.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v305_verto_unified_sync_server.sql`; `supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncBootstrapRemote.kt` (`rpc` call near line 43); `docs/sql/v305_verto_unified_sync_server.sql`; `supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql`.

## `verto_pull_sync_changes_v2`

- **Status:** CLIENT_IMPLEMENTED=yes (B10-V01 source); REPOSITORY_SERVER_DEFINED=no matching B08 definition supplied; RUNTIME_VERIFIED=no. Not release-ready.
- **Purpose:** Authorized complete-group delta feed with a durable receive cursor and canonical group manifests; business application is a separate client transaction.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncPullRemote.kt`, `SupabaseUnifiedSyncPullRemote.pull`.
- **Authentication:** Existing shared authenticated Supabase client and trusted scope resolver; token validity and server behavior were not tested in B10.
- **Authorization:** B08 must enforce principal/organization/scope visibility, including manifest keys/counts/dependencies. Client-provided scopeId is not authorization.
- **Parameters / source-derived request example:** `SyncInboxPullRequestV2(scopeId, cursorToken, softLimit, maxGroupBytes=2097152)`; exact camelCase JSON names; `softLimit` in1..1000 is not permission to split the first group.
- **Return type:** One `SyncPullPage` object, with `fromCursor`, `coveredThroughRevision`, complete ordered `groups` manifests and scope identity. No old `UnifiedSyncPullPageWire` fallback.
- **Side effects:** Read interface at client level. SQL implementation, retention and actual effects must be verified in B08/B20.
- **Transaction boundary:** All members/manifest/receivedCursor commit together in the client, followed by independent atomic business-group transactions. No network inside either Room transaction.
- **Idempotency:** Verified identical scoped event/manifest replay is a no-op; same identity with different immutable content is rejected.
- **Retry semantics:** Local waits use persisted generation; transient network retry follows existing error classification. Missing RPC/manifests or oversized groups are contract failures, not an endless MORE_AVAILABLE loop.
- **Expected errors:** Scope/cursor/group/coverage/hash/size errors are fail-closed at the client boundary; SQL error mapping and server behavior are NOT_RUN.
- **Database dependencies / repository SQL:** B08 implementation and isolated round-trip verification required. Older v305 SQL is not evidence of this new interface.
- **Verified version:** B10-V01 source only; actual Gradle/JSON/Room/Supabase runtime remains unverified.
- **Evidence:** [Required wire contract](../sync-repair/evidence/B10/V01/wire-contract.md); [B10 implementation and limits](../sync-repair/evidence/B10/V01/session-report.md).

Historical predecessor: `verto_pull_sync_changes` previously decoded `UnifiedSyncPullPageWire` from the v305 interface. Its historical SQL is retained in `docs/sql/v305_verto_unified_sync_server.sql` and the matching Supabase migration. It is no longer the production client delta call or an automatic fallback; this does not assert the historical server function was removed or changed.

## `verto_purchase_cycle_push_post_v253`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** See caller; server side effects NOT VERIFIED FROM REPOSITORY.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncPurchaseCycle.kt` (`rpc` call near line 76)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc( "verto_purchase_cycle_push_post_v253", PurchaseCyclePostPushRequest( matches = matches.map { it.toRemote() }, matchLines = matchLines.map { it.toRemote(orgId) }, allocations = allocations.map { it.toRemote() }, p`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** See caller; server side effects NOT VERIFIED FROM REPOSITORY.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v253_purchase_cycle.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncPurchaseCycle.kt` (`rpc` call near line 76); `docs/sql/v253_purchase_cycle.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`.

## `verto_purchase_cycle_push_pre_v253`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** See caller; server side effects NOT VERIFIED FROM REPOSITORY.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncPurchaseCycle.kt` (`rpc` call near line 50)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc( "verto_purchase_cycle_push_pre_v253", PurchaseCyclePrePushRequest( purchaseOrders = orders.map { it.toRemote() }, purchaseOrderLines = orderLines.map { it.toRemote(orgId) }, goodsReceipts = receipts.map { it.toRemot`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** See caller; server side effects NOT VERIFIED FROM REPOSITORY.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v253_purchase_cycle.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncPurchaseCycle.kt` (`rpc` call near line 50); `docs/sql/v253_purchase_cycle.sql`; `supabase/migrations/20260821150000_v310_verto_stronger_stream_bridge.sql`.

## `verto_resolve_sync_scope`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=yes — retained v314 SQL deployment report explicitly verifies live scope resolution.
- **Purpose:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncBootstrapRemote.kt` (`rpc` call near line 19); `data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncPullRemote.kt` (`rpc` call near line 17)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc("verto_resolve_sync_scope").decodeList<UnifiedSyncScopeWire>() require(rows.size == 1) { "FAIL_BOOTSTRAP_SCOPE_MISMATCH: expected exactly one trusted scope" } val row = rows.single() return SyncScope(row.organization`
- **Return type / source-derived response example:** `List<UnifiedSyncScopeWire>`.
- **Side effects:** Read/query contract at client level; exact server reads are defined only by repository SQL where present.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** NOT APPLICABLE for client mutation identity; read retry remains subject to cursor/scope validation.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v305_verto_unified_sync_server.sql`; `supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncBootstrapRemote.kt` (`rpc` call near line 19); `data/network/src/main/kotlin/com/verto/app/data/sync/UnifiedSyncPullRemote.kt` (`rpc` call near line 17); `docs/sql/v305_verto_unified_sync_server.sql`; `supabase/migrations/20260821062000_v305_verto_unified_sync_server.sql`.

## `verto_upsert_client_v1`

- **Status:** CLIENT_VERIFIED=yes; REPOSITORY_SERVER_DEFINED=yes; RUNTIME_VERIFIED=no per-RPC runtime invocation evidence retained for v316.
- **Purpose:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Caller(s):** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncClients.kt` (`rpc` call near line 59)
- **Authentication:** shared Supabase client is used; caller-side session prerequisites are preserved. Server authentication enforcement beyond repository SQL is **NOT VERIFIED**.
- **Authorization:** Repository SQL definition available; inspect cited function for server checks. Deployment/application is not inferred from file presence.
- **Parameters / source-derived request example:** `rpc( "verto_upsert_client_v1", UpsertClientRequest( clientId = c.id, name = c.name, phone = c.phone, address = c.address, workplace = c.workplace,`
- **Return type / source-derived response example:** `Unit / response payload not decoded by this call site`.
- **Side effects:** Command/write contract at client level; exact database side effects are only authoritative where SQL definition exists.
- **Transaction boundary:** Repository function body exists; no additional transaction guarantee is claimed beyond the cited SQL.
- **Idempotency:** NOT VERIFIED.
- **Retry semantics:** NOT VERIFIED / caller has no explicit retry contract at this RPC call site.
- **Expected errors:** caller validation/decoding/scope errors are visible in the cited source; exhaustive server error taxonomy is **NOT VERIFIED FROM REPOSITORY**.
- **Database dependencies / repository SQL definition:** Defined by cited SQL: `docs/sql/v145_client_sync_rpc.sql`
- **Verified version:** v315 source.
- **Evidence:** `data/network/src/main/kotlin/com/verto/app/data/sync/SyncClients.kt` (`rpc` call near line 59); `docs/sql/v145_client_sync_rpc.sql`.

## Evidence methodology

RPC names were extracted from production `rpc("name")` and `rpc(function = "name")` call forms. SQL definitions were matched against `CREATE [OR REPLACE] FUNCTION` in repository `.sql` files. Runtime verification is counted only when retained evidence names the operation explicitly.
