# Track D: gathering, refining, and crafting

- **Branch:** `codex/beta-track-d-gathering-refining-crafting`
- **Worktree:** `beta-track-d-gathering-refining-crafting`
- **Contract lineage SHA:** `8ba653a8dc3f604dc23a142cca5f5a88f51682a9`
- **Actual branch start SHA:** integration HEAD after Phase 0 (`REQUIRES_INTEGRATION_MERGE`).
- **PR base:** `integration/beta-full-build`

## Scope

Canonical resource/recipe definitions, profession mastery boundary, gathering node lifecycle contract, direct/refine recipe structures, atomic transaction engine, and equipment-base production interface. Undecided quantities/times/respawns/quality use validated balance data placeholders and cannot enable gameplay.

## Out of scope

Concrete economy/market, guessed recipes/timing/refund rates, item metadata internals, enhancement/repair, quest rewards, UI implementation.

## Ownership

Owned: new gathering/refining/crafting/transaction packages and tests/docs. Do not edit item internals, player repository, enhancement, party/quest, combat, Mob Editor, or network.

## Public interfaces and dependencies

Consume Track A mastery/player identity and Track B item factory/proposed output. Publish versioned `ResourceDefinition`, `RecipeDefinition`, reservation/transaction result, and production event. E and F may call the transaction interface, not inventories directly.

## Feature flags

`GATHERING`, `REFINING`, `CRAFTING`; refining requires gathering data, crafting equipment requires refining and equipment v2.

## Tests and manual verification

Recipe/resource ID uniqueness, quantity/finite validation, deterministic proposal, reservation conflicts, full inventory, double click/retry, logout, stop/recovery, every rollback injection point, bounded nodes/tasks. Paper manual tests only after approved balance data exists.

## Commit split

1. Pure IDs/definitions. 2. Transaction journal/reservation. 3. gathering adapter. 4. refining. 5. crafting output adapter. 6. tests/docs/disabled wiring.

## Merge prerequisites and rollback

Phase 0, A identity/mastery, and B output contracts stable; schema/recipe versions and minimum balance data approved before writes. Rollback flags false, cancel uncommitted reservations, recover journal, remove no committed output.

## Completion report

Report start SHA, schemas, dependencies, transaction sequence/idempotency, balance blockers, commits/tests/CI, manual scenarios, flags, recovery/rollback, unresolved decisions, final status.

