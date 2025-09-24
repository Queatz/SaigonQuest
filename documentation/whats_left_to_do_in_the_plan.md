# Federated Object Inventory System — What’s left to finish the original plan

Last updated: 2025-08-12 10:09

This document captures the remaining work to fully complete the original implementation plan for the federated inventory system.

## What’s already done (current session)
- Atomic transfers
  - InventoryRepository.transfer(...) runs inside an ArangoDB transaction, validates source quantity, updates itemOrder, deletes zero‑qty entries, and combines destination quantities.
- Server‑authoritative endpoints
  - POST /inventory/transfer/drop creates the container object and transfers USER → OBJECT via inventories only.
  - POST /inventory/transfer/pickup transfers OBJECT → USER from object‑owned inventories; deletes container object when empty.
- Server broadcasts (authoritative)
  - ADD_OBJECT / OBJECT_UPDATE / REMOVE_OBJECT are broadcast from server routes for create/update/delete and transfer outcomes.
- Frontend migration (phase 1)
  - Client stopped self‑broadcasting for add/remove/update.
  - Rendering prefers object‑owned inventories via createObjectPreferInventory(); falls back to legacy only if inventory unavailable.
- Legacy endpoints deprecated
  - /object/drop and /object/collect now return HTTP 410 Gone.

## Remaining work to complete the plan

### 1) Backend cleanup and hardening
- [ ] Remove legacy object fields from the model and usages
  - Fields: Object.isItem, Object.itemId, Object.itemQuantity
  - Steps:
    - Stop setting isItem when creating container objects in /inventory/transfer/drop (purely use presence of OBJECT inventory).
    - Remove conditional logic that depends on legacy fields in backend.
    - Prepare a lightweight data migration or tolerant reads (fields may exist in old documents; ignore them).
    - Finally, remove fields from the Object data class once the client is fully migrated.
- [ ] Remove deprecated endpoints and client‑originated mutation handlers
  - Delete legacy routes: /object/drop, /object/collect (currently return 410 Gone).
  - Consider removing or no‑op’ing WebSocket handlers that allow client‑originated ADD_OBJECT/REMOVE_OBJECT to avoid misuse; rely solely on server‑originated broadcasts from HTTP routes.
- [ ] Permissions and validation
  - Define and enforce world access rules on /inventory/transfer/drop (currently permissive); ensure a user can drop only in permitted worlds/areas.
  - Confirm world scoping is correctly applied to broadcasts (already in use) and object lifecycle.
- [ ] Tests (backend)
  - Concurrency: parallel drops/pickups for same item/object; ensure atomicity and no negative quantities.
  - Lifecycle: partial pickup, exact‑zero deletes, multiple item types in an object inventory.
  - Transaction: verify itemOrder maintained and zero‑qty deletions occur inside the transaction.

### 2) Frontend migration off legacy fields (phase 2)
- [ ] Remove any residual reads/writes of Object.isItem, Object.itemId, Object.itemQuantity
  - SceneObjectManager.createObjectInScene currently has a legacy branch for isItem; refactor to rely exclusively on inventories, then delete the branch.
  - Ensure createObjectPreferInventory covers all render paths; keep a minimal fallback that does not depend on legacy fields.
- [ ] UX cues based on inventories
  - Detect “item piles/containers” purely by presence/contents of object inventory.
  - Drive pickup UX (available quantity, partial pickup options) from inventory items instead of legacy fields.

### 3) Translations and UX
- [ ] Audit and add missing translations; ensure sentence case
  - Common errors: “Nothing to pick up”, “Not enough quantity”, “Failed to drop item”, “Failed to pick up item”.
  - Ensure all new UI copy uses the Translated helpers and sentence case.

### 4) Reliability and idempotency
- [ ] WebSocket
  - Confirm a single authoritative broadcast per mutation, correct world scoping, and idempotent client handlers (adding/ removing objects only once).
- [ ] Observability
  - Add lightweight logging/metrics around transfer failures, transaction retries, and broadcast outcomes.

## Nice‑to‑have improvements (post‑MVP)
- [ ] Multi‑item container UI and tooltips showing exact contents from object inventory.
- [ ] Server‑side combine/normalize for object inventories with many small entries of the same item key (periodic job).
- [ ] Soft‑delete “container objects” with TTL to support undo or accidental pickup recovery (if desired by design).

## TL;DR
Core flows are implemented and transactional. To finish: remove legacy fields and endpoints, finalize the frontend’s inventory‑only rendering, add permissions, translations, and thorough tests, and optionally retire client‑originated WebSocket mutation handlers so the server remains the single source of truth.
