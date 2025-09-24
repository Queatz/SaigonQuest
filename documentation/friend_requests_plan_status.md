# Friend Requests Refactor — Plan Completeness & Status (2025-08-22)

This document tracks how the current implementation maps to the previously proposed end‑to‑end plan for a robust Friend Requests system.

Summary
- Design completeness: High (covers DB → Repo → Routes → Client → UI → Realtime → Tests).
- Implementation progress: Partial (~30%). Foundational pieces are in, but many endpoints and UI pieces remain.

What’s implemented (in repo)
- Data/DB
  - New collection: `friend_requests` created and initialized.
  - Shared models: `FriendRequest`, `FriendRequestStatus`.
- Repository
  - `FriendRequestRepository`: create, getPendingFromTo, acceptByParticipants, getIncoming, getOutgoing.
  - Existing `FriendRepository` remains for accepted friendships.
- Routes
  - POST `/friend/request`: persists a FriendRequest (idempotent by lookup) and still creates a Notification (keeps current UI working).
  - POST `/friend/accept`: validates pending request B→A before accepting, marks it Accepted, and creates the friendship rows.
- Compatibility
  - Existing client still relies on notifications; behavior maintained.

What’s not implemented yet
- Routes (recommended design)
  - GET `/friend-requests?direction=in|out` (listing pending). Repo support exists, no route yet.
  - POST `/friend-requests/{id}/accept` (accept by id). Current accept still uses friendId.
  - POST `/friend-requests/{id}/decline`.
  - POST `/friend-requests/{id}/cancel`.
  - GET `/friendships/status/{otherUserId}` (server-driven status: NotFriends | Friends | IncomingRequest | OutgoingRequest).
  - Auto-accept cross requests (A→B while B→A pending); current code does not auto-accept.
  - Idempotency returns (200 w/ current state) vs conflicts; current flow returns 409/400 in some cases.
  - Transactional accept (request update + friendship creation atomic).
- DB constraints/guards
  - Unique pending pair constraint (avoid duplicate pending in same direction). Not enforced at DB level yet.
  - Self-request is guarded (done), already-friends guarded (done).
- Realtime
  - WebSocket events for request lifecycle (created/accepted/declined/cancelled). Currently only generic Notification push exists.
- Frontend API/UI
  - FriendApi replacements: send/list/accept/decline/cancel by requestId; getFriendshipStatus endpoint usage. Not added.
  - Requests tab UI (incoming/outgoing) with Accept/Decline/Cancel. Not added.
  - De-couple status from Notifications (stop scraping). Not done.
  - Styling refactors (one @Composable per file, no inline styles in FriendsContent). Not addressed here.
- Tests
  - Unit tests for repositories and route auth/authorization, idempotency, cross-request merge. Not added.

Priority next steps
1) Backend endpoints and behavior
   - [ ] GET `/friend-requests?direction=in` → pending incoming via repo
   - [ ] GET `/friend-requests?direction=out` → pending outgoing
   - [ ] POST `/friend-requests` (optional new path) → migrate from `/friend/request`; keep legacy delegating
   - [ ] POST `/friend-requests/{id}/accept` → validate addressee
   - [ ] POST `/friend-requests/{id}/decline` → validate addressee; set Declined
   - [ ] POST `/friend-requests/{id}/cancel` → validate requester; set Cancelled
   - [ ] GET `/friendships/status/{otherUserId}` → server-driven status
   - [ ] Cross-requests auto-accept (A→B while B→A pending)
   - [ ] Transactions where necessary (accept path)
   - [ ] Idempotency rules (avoid 409 except true conflicts)
2) DB safety
   - [ ] Add an AQL/DB-side guard or a unique index pattern for pending `(requesterId, addresseeId)` pairs; or enforce via transaction + check.
3) Realtime
   - [ ] Emit WS events: FRIEND_REQUEST_CREATED/ACCEPTED/DECLINED/CANCELLED; broadcast to involved users.
4) Frontend API and UI
   - [ ] Add FriendApi methods: send, list incoming/outgoing, accept/decline/cancel by id, getFriendshipStatus.
   - [ ] Implement Requests tab (incoming/outgoing) + optimistic updates.
   - [ ] Replace notification scraping with status endpoint.
   - [ ] Optional: adjust Notifications to link to Requests tab (using requestId if present).
5) Tests
   - [ ] Repository tests: unique pending, cross-requests merge, idempotency.
   - [ ] Route tests: authorization checks and full lifecycle flows.
   - [ ] WS integration test covering event delivery.

Completion estimate
- Current coverage of the plan: approximately 30%.
  - Foundations (models, repo, minimal persistence and validation) are done.
  - Core UX/API endpoints and realtime are pending.

Notes
- The project is pre-production, so we can safely adjust/break legacy endpoints as we introduce the new ones; keep temporary delegation for a short period.
- Keeping Notifications for visibility is useful, but they should not be the source of truth for request state.
