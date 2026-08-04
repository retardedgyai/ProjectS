# Track C: combat, elements, and classes

- **Branch:** `codex/beta-track-c-combat-elements-classes`
- **Worktree:** `beta-track-c-combat-elements-classes`
- **Contract lineage SHA:** `8ba653a8dc3f604dc23a142cca5f5a88f51682a9`
- **Actual branch start SHA:** integration HEAD after Phase 0 (`REQUIRES_INTEGRATION_MERGE`).
- **PR base:** `integration/beta-full-build`

## Scope

Continue one-path-at-a-time AttackMetadata/snapshot adoption, immutable equipment/player stat inputs, pure fire/ice/lightning structures where design is complete, class adapters, and observational validation before authoritative switches. Preserve DamageService, critical cache, single application, and legacy gameplay with flags false.

## Out of scope

Unapproved lightning details, authoritative mass migration, critical 175->150 change, TRUE/flat penetration removal, other tracks' persistence/item/editor/UI code, PvP balance.

## Ownership

Owned: `combat`, `skill`, `status`, Track C tests/docs. `ProjectSPlugin` wiring is a final small commit. Do not edit player/data persistence, item serialization, transactions, Mob Editor wire schema, or client network.

## Public interfaces and dependencies

Consume Track A immutable player stats, Track B equipment contributions, and Track G mob category/weakness interfaces (fakes allowed until stable). Publish attack/result observations and bounded element contribution events without Bukkit domain leakage.

## Feature flags

`FIRE_SYSTEM`, `ICE_SYSTEM`, `LIGHTNING_SYSTEM`; each independent and false. Existing starter route and both shadow flags remain separate.

## Tests and manual verification

Legacy/pure golden parity; one calculation/application/critical roll; multi-target/cast semantics; finite/property/bounded contribution tests; fire stack/detonation and ice freeze/SHATTER order once balance blockers resolve. Paper matrix includes critical, shields, enhanced weapons, normal/elite/boss, multiplayer contributions, lifecycle cleanup.

## Commit split

Per attack path and per element: pure model/test, observational adapter, operator metrics, manual evidence, optional authoritative decision in a later PR.

## Merge prerequisites and rollback

Phase 0 and required A/B interfaces merged; owner decisions for any enabled runtime behavior; no unresolved parity mismatch. Rollback disables element flags and restores legacy adapter; observational state clears on disable/reload.

## Completion report

Report start SHA, exact paths/metadata, calculation/application counts, critical semantics, element rules, flags, metrics/exports, commits/tests/CI/Paper matrix, rollback, unmigrated paths, risks, final status.

