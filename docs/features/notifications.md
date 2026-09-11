---
status: canonical
scope: feature
owner: "feature:notifications + data:network"
last_verified_against: v315
---
# Notifications

## Purpose

Notification center, local cache, read-state commands, push-token lifecycle and FCM dispatch.

## User workflows

- View notification center
- Mark one/all read
- Create system/direct/audience notification
- Receive FCM/deep-link navigation

## Entry points

- notification feature UI/gateway
- `NotificationRepository`
- FCM token uploader/workers

## Business rules

- Server notification records and local Room cache are distinct.
- FCM delivery failure is not the same as notification-record creation failure.

## Domain model

Room `NotificationEntity`, remote notification DTOs, push token identity.

## Data ownership

Canonical business ownership: `feature:notifications + data:network`. Persistence/transport remains with declared data modules; this page does not reassign module ownership.

## Local persistence

Room `notifications` cache.

## Server dependencies

notification RPCs/direct `notifications` insert; Edge Function `send-notification-fcm`; push-token RPCs.

## Permissions

Audience creation and admin/system operations use role/permission/server contracts as implemented.

## Offline behavior

Cached notifications are readable; creating/marking server notifications and FCM delivery requires connectivity, then local cache is reconciled.

## Sync behavior

`get_my_notifications_cache` refreshes local cache; organization sync participant triggers notification pull.

## Validation

Nonblank title/body, current profile/user, push token/device ID.

## Error states

FCM invoke failures are logged separately; RPC/cache failures remain failures.

## Notifications/events

This feature is the notification owner; routing/deep links are handled by app notification/navigation boundaries.

## Critical invariants

- Push token revocation is attempted before sign-out.
- A read-state RPC failure must not be represented as server success.

## Testing notes

Notification feature/repository tests where present.

## Known limitations

Repository SQL definitions for most notification/push-token RPCs are absent.

## Related docs

- [Architecture Overview](../architecture/overview.md)
- [API / Server Reference](../api/README.md)

## Evidence

- `data/network/src/main/kotlin/com/verto/app/data/repository/NotificationRepository.kt`
- `data/network/src/main/kotlin/com/verto/app/data/remote/PushTokenRepository.kt`
- `feature/notifications/src/main/kotlin/com/verto/app/feature/notifications`
