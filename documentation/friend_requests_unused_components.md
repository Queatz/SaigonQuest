# Unused friend-request UI components and listeners — rationale (2025-08-23)

This note documents why the following files and helpers currently appear unused in the codebase:

- src/commonMain/kotlin/main/components/IncomingRequestItem.kt
- src/commonMain/kotlin/main/components/OutgoingRequestItem.kt
- WebSocketApiGlobal helpers:
  - addFriendRequestCreatedListener
  - addFriendRequestDeclinedListener
  - addFriendRequestCancelledListener

## Why they are unused

Product decision (see recent discussions): We do not want a separate screen for friend requests. The desired UX is:
- Accept/ignore directly within the friend request Notification UI (FriendRequestNotificationContent)
- Accept from the OtherPlayerDialog (when the status is "Request received")

As a result:
- The previously implemented FriendRequestsContent (Requests screen) was removed.
- The list-item composables IncomingRequestItem and OutgoingRequestItem were only used by that screen; without it, they are orphaned.
- Client realtime for friend-requests is still supported server-side (WS actions FRIEND_REQUEST_CREATED/ACCEPTED/DECLINED/CANCELLED), but the frontend only uses FRIEND_REQUEST_ACCEPTED to refresh the Friends list. The other three listener helpers are available for future use, yet not needed with the current UI which relies on notifications and direct actions.

## Action taken
- Left the components and listener helpers in place but added KDoc explaining they are unused by design right now and safe to remove in a future cleanup if desired.

## Where friend requests are handled now
- Inline in Notifications: src/commonMain/kotlin/main/components/FriendRequestNotificationContent.kt
- Player dialog: src/commonMain/kotlin/main/components/player/OtherPlayerDialog.kt

## Future considerations
- If we ever bring back a dedicated Requests UI, these components and listeners can be re-used to power incoming/outgoing lists with realtime updates.
