# Testing Browser Notifications Locally

This guide shows how to test in‑app (foreground) and Web Push (background) notifications on your local machine using a single backend server.

## Overview

- Foreground (tab open): you get in‑app toasts and OS notifications via the Notifications API.
- Background (tab closed/no WS): the backend sends a Web Push to your Service Worker (SW), which shows a system notification. Clicking the notification focuses/opens the app and navigates to the right world when available.

The backend only sends a Web Push if there is no active WebSocket for the user.

## Prerequisites

- HTTPS or localhost (required by Service Workers and Notifications API).
- VAPID keys in your backend environment:
  - `VAPID_PUBLIC_KEY` (Base64URL P‑256)
  - `VAPID_PRIVATE_KEY` (Base64URL P‑256)
  - `VAPID_SUBJECT` (optional, e.g., `mailto:you@example.com`)

Generate keys (one‑time) using Node:

```bash
npx web-push generate-vapid-keys
export VAPID_PUBLIC_KEY=<publicKey>
export VAPID_PRIVATE_KEY=<privateKey>
export VAPID_SUBJECT=mailto:you@example.com
```

## Build and Run (Frontend and Backend on Separate Origins)

1) Build the service worker (one time or whenever SW changes):

```bash
./gradlew :sw:jsBrowserProductionWebpack
```

2) Run the backend:

```bash
./gradlew :backend:run
```

3) Run the frontend dev server (ensures the SW is available at the same origin):

```bash
./gradlew devApp
```
```

Notes:
- The dev server is configured to serve `/service-worker.js` from a static directory in `build/sw-static`, which is automatically prepared by `prepareDevServiceWorker`.
- If you change the service worker, re-run `:swBrowserProductionWebpack` to rebuild, or just stop and start the dev server (the gradle hook prepares it again).

## Player‑Only Subscription

Only logged‑in players subscribe to push. Visitors do not. On login/upgrade to player:
- The app registers the SW at `https://<backend-origin>/service-worker.js`.
- Requests notification permission (prompt).
- Subscribes to Push and POSTs the subscription to the backend.

Verify network calls in DevTools (frontend origin):
- `GET /notification/push/public-key`
- `POST /notification/push/subscribe`

## Testing Foreground Notifications

With the tab open (WS connected):
1) Trigger a server notification (any flow that calls `NotificationRepository.create(...)`).
2) Expected:
   - In‑app toast appears.
   - OS notification appears (if permission granted).
   - Web Push is suppressed (due to active WebSocket).

If you need a direct API call and have an admin user, you can use the admin route:

```bash
curl -X POST \
  -H "Authorization: Bearer <ADMIN_JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "userId":"<TARGET_PLAYER_ID>",
    "title":"Test", "description":"Foreground test",
    "type":"SYSTEM", "worldId":"", "objectId":"", "otherUserId":""
  }' \
  http://localhost:8080/notification
```

## Testing Background Push Notifications

With no WS connected for the target user:
1) Close the app tab (or ensure the WS disconnects).
2) Trigger a server notification (as above).
3) Expected:
   - A system notification appears via the SW.
   - Clicking the notification focuses/opens the app, opens the Notifications panel, and navigates to the world (when `worldId` → `worldUrl` is available).

Notes:
- The backend only sends a push when it detects no active WebSocket for that user (across worlds).
- If you don’t receive a push, double‑check that the tab is really closed and the user doesn’t have any other tabs connected.

## Using DevTools to Simulate SW Push

Chrome DevTools → Application → Service Workers → Push — you can simulate a push without the backend.

Example payload (must be JSON). The SW expects the shape `{ action, data, worldUrl? }` where `data` is a serialized NotificationData.

```json
{
  "action": "NOTIFICATION",
  "data": "{\"id\":\"demo\",\"userId\":\"u1\",\"title\":\"Hello\",\"description\":\"From DevTools\",\"isRead\":false,\"type\":\"SYSTEM\",\"worldId\":\"<WORLD_ID>\",\"objectId\":\"\",\"otherUserId\":\"\",\"timestamp\": 1730000000000}",
  "worldUrl": "<optional-world-url>"
}
```

This is useful to validate:
- SW shows a notification with icon and body.
- Clicking focuses/opens the app and opens the Notifications panel.
- When `worldUrl` is supplied, navigation goes to that world.

## Common Issues

- 404 for `/service-worker.js`:
  - Ensure you ran `./gradlew buildApp` first so the SW is copied into backend resources.
  - Restart the backend after rebuilding.

- No OS notification:
  - Check site permissions (allow notifications).
  - Make sure you’re on HTTPS or localhost.

- No push while tab is open:
  - Expected: push is suppressed when any WS for the user is connected.

- SW not updating:
  - Hard reload without cache or unregister/reload via DevTools → Application → Service Workers.

- Permissions stuck/denied:
  - Reset site permissions in browser settings and try again.

## Clean Up

On logout, the app unsubscribes locally and calls backend DELETE to remove the current endpoint. You can also manually remove subscriptions from DevTools (Application → Push Messaging) or the DB if needed.
