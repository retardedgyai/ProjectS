# Recipe and transaction contract

## Scope

This contract covers refine, craft, Tier promotion, enhancement, repair, reward claim, quest reward, and a future market-facing transaction boundary. Wave 1 defines `recipe-definition` schema version 1. Concrete recipes, times, costs, probabilities, and refund rates are `REQUIRES_BALANCE_DATA` or `REQUIRES_OWNER_DECISION`.

## Required order

```text
validate -> reserve -> consume -> produce -> persist -> commit
```

1. **validate:** authenticate actor/permission, feature flag, request ID, recipe/schema revision, quantities, item revisions, inventory capacity strategy, and prerequisites.
2. **reserve:** exclusively reserve exact input slots/resources/currency and output capacity without exposing outputs.
3. **consume:** move inputs into transaction custody; do not irreversibly delete them.
4. **produce:** calculate an immutable proposed output using recorded RNG seed/result where randomness is approved.
5. **persist:** atomically persist player/item/claim revisions and the transaction outcome.
6. **commit:** expose output, finalize consumed inputs, and mark the idempotency key committed.

No stage after reserve may call validate again against changed unreserved state. A failure before commit invokes rollback in reverse order.

## Idempotency and concurrency

- Every mutation has a bounded request/transaction ID, actor ID, operation ID, input revisions, and terminal result.
- Repeated packet, double click, command retry, or reconnect returns the same committed result and cannot consume/produce again.
- Per-actor/item locking or optimistic revisions prevent two operations from committing the same input.
- Idempotency records are bounded/expired only after the persistence retention window proves retry safety.

## Failure cases

- **Inventory full:** output capacity is reserved or output goes to an approved durable claim mailbox; if neither exists, validation fails before consume.
- **Logout:** transaction continues only in a durable repository with no Bukkit inventory dependency; otherwise rollback before clearing the player session.
- **Server stop/crash:** committed journal state recovers exactly once; uncommitted reservations rollback on recovery.
- **Persistence error:** no output is exposed and inputs are restored from custody.
- **Invalid/negative/huge quantity, NaN/Infinity:** reject before reserve.
- **Revision conflict:** return a conflict result; never merge item mutations implicitly.

## Operation-specific invariants

- Refine/craft/promotion/repair never partially consume a recipe.
- Tier promotion uses one same-type prior-Tier equipment item, not exponential multiple-item growth; exact ingredients/cost are balance data.
- Repair consumes a same-Tier, same equipment-type, unenhanced item plus approved costs and preserves the repaired item's enhancement, quality, MODs, crafter, name/engraving, identity, and other unique data.
- Reward/quest claims use a persisted claim key before delivery and cannot pay twice.
- Enhancement preserves existing +0..+30 and broken PDC compatibility until v2 migration is committed.
- A future market integration calls the same reservation/commit interfaces; it cannot directly mutate inventories or item metadata.

## Audit and rollback

Record bounded transaction ID, operation/recipe ID and revision, actor, timestamps, sanitized input/output identities, result, and rollback reason. Do not log secrets or entire unbounded metadata blobs. Rollback must be testable for every failure injection point.

