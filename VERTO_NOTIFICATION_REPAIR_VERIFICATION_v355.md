# Verto Notification Repair — v355 Verification

Date: 2026-08-24
Source of truth: `Verto-v354.zip`
Output: `Verto-v355-notifications-repaired.zip`

## Result

`PASS_STATIC + SERVER_VERIFIED / BUILD_BLOCKED_ENVIRONMENT`

The notification repair is implemented in source and on the connected Supabase project. The Android Gradle build could not be executed in this environment because the Gradle wrapper cannot resolve `services.gradle.org`; therefore this report does **not** claim build/test PASS.

## Server changes verified live

- Preserved the durable delivery pipeline: notification row -> PGMQ queue -> `process-notification-deliveries` -> FCM.
- Five notification migrations are present on the live project:
  - `20260824082559_notification_server_contract_hardening`
  - `20260824082749_notification_server_business_producers`
  - `20260824083904_internal_messages_to_durable_notification_outbox`
  - `20260824084032_remove_legacy_notification_webhook_trigger_function`
  - `20260824104056_notification_navigation_metadata_alignment_v1`
- `authenticated` cannot execute the broad system-notification creation RPC.
- Narrow direct/manual notification RPC remains executable for authenticated users and performs server-side authorization.
- `get_my_notifications_page_v1` is executable for authenticated users.
- Authenticated direct INSERT/UPDATE policies on `public.notifications` are absent.
- Notification type contract includes `AUTODRIVE_JOIN_REQUEST` and `PAYMENT_DUE_REMINDER`.
- Durable notification-delivery trigger is present.
- `process-notification-deliveries` is ACTIVE at version 8.
- Verto tokens receive data-only FCM so Android renders the notification locally.
- AutoDrive tokens retain the existing notification+data FCM payload contract.
- Canonical FCM fields are protected from notification `data` overrides.
- Priority is type-based for Verto; high priority is reserved for actionable/time-sensitive categories.

## Server-owned business events

Android no longer manufactures business/system notifications. The server owns notification production for authoritative events, including:

- credit sale invoice creation;
- purchase invoice creation;
- payment recorded;
- low stock transition;
- goods received;
- payment due reminders for the responsible employee;
- overdue payment escalation to managers;
- AutoDrive/admin events already emitted by server producers.

## Android/client changes

- Removed `:core:notification` from Gradle/settings and deleted its production module.
- Removed client-side system/business notification sender adapters.
- Removed the obsolete local due-notification worker path.
- Removed obsolete `BootReceiver` and app `NotificationWorker` path.
- Removed `SystemNotificationPayload` client business notification factory.
- Invoice/payment write flows now schedule sync only; business notification generation is server-owned.
- Notification Center no longer marks all notifications read when opened.
- Notification Center uses `LazyColumn` and no longer truncates to 10 items.
- Notification pull uses paginated `get_my_notifications_page_v1` and atomically replaces the Room cache.
- DAO read mutations are organization-scoped.
- Read actions are optimistic locally and persisted before local mutation in a tenant/user-scoped durable pending-read store; failed network acknowledgements replay on later sync.
- `notification_reads` remains the canonical server read state.
- One allow-listed `NotificationRoutePolicy` is used by FCM and Notification Center navigation.
- Invalid or unknown routes fall back to Notification Center instead of being passed directly to NavController.
- `LOW_STOCK -> inventory` is explicitly supported.
- Push permission is no longer requested immediately at app startup; it is requested contextually from Notification Center.
- FCM is treated as a display/wake-up signal and Room remains the application read model.

## Notification icon contract

- Status-bar small icon: monochrome resource generated from the current Verto infinity logo, with density-specific assets.
- Expanded notification large icon: the exact current colored launcher icon.
- SHA-256 of `drawable-nodpi/ic_notification_large.webp` equals the current `mipmap-xxxhdpi/ic_launcher.webp`:
  `6ece8ab32d7cad64b28b107fcff49ecfd05e6a30ab69856a1c8f3b109b276715`

This intentionally gives Android the monochrome small icon required by the status bar while displaying the current colored Verto icon in the expanded notification.

## Verification performed

### PASS

- Route policy compiled with local `kotlinc` and an executable harness: `ROUTE_POLICY_PASS`.
- 29 notification-specific static contract checks: `29/29 PASS`.
- Modified architecture-contract JSON files parse successfully.
- Android manifest XML parses successfully.
- No live Kotlin/Gradle/XML references remain to the removed notification Legacy production paths.
- `:core:notification` is absent from `settings.gradle.kts`.
- Deleted notification module and local due-worker directory are absent.
- SQL migration files pass basic delimiter/structure checks.
- Live Supabase critical-state verification returned:
  - notification migrations present = 5;
  - authenticated system creation = false;
  - authenticated direct/manual creation = true;
  - authenticated notification page read = true;
  - authenticated notifications INSERT/UPDATE policy = false;
  - notification type contract = true;
  - durable delivery trigger = true.
- Live Edge Function: `process-notification-deliveries` version 8, ACTIVE.

### Architecture Guard

`tools/architecture/verto_arch_guard.py verify` returns FAIL, but the **original unmodified v354 also returns the same 9 failure categories**. The repair did not introduce a new Architecture Guard failure category. Existing v354 debt includes undeclared external dependencies, missing ownership records for unrelated team-observation components, pre-existing public API drift, and broad complexity-baseline regressions.

### BLOCKED_ENVIRONMENT

`./gradlew --version` attempts to download Gradle 8.9 and fails with:

`java.net.UnknownHostException: services.gradle.org`

Therefore Gradle compilation/unit tests/instrumentation are **not run** in this environment. A local/CI build with Gradle network/cache available is still required before release admission.

## Legacy removal evidence

Production source no longer contains the removed notification paths:

- `core/notification/`
- `data/sync/.../notifications/`
- `SystemNotificationSenderAdapter.kt`
- `LocalDueNotificationPublisherAdapter.kt`
- `BootReceiver.kt`
- app-level `NotificationWorker.kt`
- `SystemNotificationPayload.kt`

Historical references can still exist in archived release reports and old complexity baselines; they are documentation/history, not executable production paths.

## Release admission

Source repair: **PASS_STATIC**  
Server migration/state: **VERIFIED**  
Edge delivery worker: **VERIFIED ACTIVE v8**  
Android build/tests: **BLOCKED_ENVIRONMENT**  
Release/APK admission: **NOT CLAIMED**
