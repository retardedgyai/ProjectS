# Track B: equipment, items, and MODs

- **Branch:** `codex/beta-track-b-equipment-item-mods`
- **Worktree:** `beta-track-b-equipment-item-mods`
- **Contract lineage SHA:** `8ba653a8dc3f604dc23a142cca5f5a88f51682a9`
- **Actual branch start SHA:** exact `WAVE_1_BASE_SHA` after the Wave 1 owner-decision PR merges.
- **PR base:** `integration/beta-full-build`

## Scope

Read-only legacy item migration view, typed equipment metadata validation, eight logical slots, T1-T3/ILv/rarity/MOD capacity, item identity policy, MOD definition/value schemas, unknown MOD isolation, and immutable stat-output interface. Persisted writer waits for schema/default decisions.

## Out of scope

Crafting recipes, MOD costs/weights/catalog, enhancement mutation, equip gameplay enforcement, combat application, market, client screens.

## Ownership

Owned: existing `item` with compatibility care and new dedicated equipment/MOD packages/tests. Shared item registration or config only in a small reviewed commit. Do not edit combat, data repository, enhancement listeners, monster/editor, or network.

## Public interfaces and dependencies

Publish `EquipmentView`, `EquipmentValidation`, `EquipmentStatContribution`, `ModDefinition`, `ModEntry`, and proposed-item writer boundary using canonical IDs and finite values. Consume Phase 0 contracts and optional Track A player identity. D/E/C consume these interfaces.

## Feature flags

`EQUIPMENT_V2`, `MOD_SYSTEM`; MOD requires equipment v2. Both default false.

## Tests and manual verification

PDC byte/value fixtures for every existing item/enhancement key, no-write-on-read, Tier/ILv/rarity capacity properties, UUID stability, unknown schema/MOD isolation, malformed/oversized values, old item inventory round trip. Paper manually compare legacy item names/damage/enhancement with both flags false.

## Commit split

1. Legacy fixtures/view. 2. Pure equipment model. 3. MOD schema/validator. 4. proposed writer/migration after decision. 5. disabled wiring/docs.

## Merge prerequisites and rollback

Phase 0 and Wave 1 owner decisions merged; v1 pure models are allowed, but legacy defaults/writer remain forbidden; existing PDC fixtures unchanged. Rollback disables both flags and selects the legacy reader; restore item backup for committed migration failures.

## Completion report

Report start SHA, legacy keys preserved, schema/default decisions, slot/Tier/rarity rules, MOD isolation, UUID policy, commits/tests/CI, manual inventory evidence, flags, rollback, blockers, final status.

