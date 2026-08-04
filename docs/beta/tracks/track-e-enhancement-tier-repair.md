# Track E: enhancement, Tier promotion, and repair

- **Branch:** `codex/beta-track-e-enhancement-tier-repair`
- **Worktree:** `beta-track-e-enhancement-tier-repair`
- **Contract lineage SHA:** `8ba653a8dc3f604dc23a142cca5f5a88f51682a9`
- **Actual branch start SHA:** integration HEAD after Phase 0 (`REQUIRES_INTEGRATION_MERGE`).
- **PR base:** `integration/beta-full-build`

## Scope

Characterize existing +0..+30 enhancement/PDC behavior, adapt it to the equipment view, implement pure promotion/repair proposals and atomic operations after balance decisions, preserve repaired item identity/quality/MOD/crafter/name/enhancement, and use same-Tier/type unenhanced repair input.

## Out of scope

Guessing success/break rates, costs, repair materials, Tier recipes, changing combat balance, item schema internals, market/UI/client.

## Ownership

Owned: dedicated Track E package/tests and narrowly reviewed `EnhancementManager`/`EnhancementListener` adapters. Do not rename existing PDC/attribute keys or edit D transaction internals, B item internals, combat, persistence, editor, network.

## Public interfaces and dependencies

Consume Track B equipment view/writer and Track D transaction/material interface. Publish immutable operation proposal/result and legacy compatibility adapter. No listener may directly consume items outside the transaction boundary.

## Feature flags

`TIER_PROMOTION`, `ENHANCEMENT_V2`, `REPAIR_V2`; all require equipment v2 and appropriate transaction/material capability.

## Tests and manual verification

Fixtures for +0..+30, broken/unbroken, attack/speed bonus PDC, lore/attribute behavior with flags false; promotion same-type/Tier validation; repair field preservation; success/failure/break/full inventory/duplicate/restart rollback after balance approval. Paper inventory backup and exact legacy behavior comparison.

## Commit split

1. Legacy characterization. 2. adapters/proposals. 3. promotion. 4. enhancement v2. 5. repair v2. 6. lifecycle/tests/docs.

## Merge prerequisites and rollback

B and D merged; every probability/cost/break threshold owner-approved and versioned before runtime use. Rollback flags false and legacy enhancement remains; restore pre-migration item backup, never reset enhancement to zero.

## Completion report

Report start SHA, PDC compatibility, decisions/balance data, transaction invariants, preserved repair fields, commits/tests/CI/manual fixtures, flags, rollback, risks, final status.

