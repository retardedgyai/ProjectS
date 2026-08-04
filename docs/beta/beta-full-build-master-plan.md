# ProjectS Beta full build master plan

Contract baseline: `origin/main` at `8ba653a8dc3f604dc23a142cca5f5a88f51682a9`  
Integration branch: `integration/beta-full-build`

## Beta completion definition

Beta is complete when a new player can progress from level 1 to the level 45 cap, unlock the first-island endgame by defeating its final boss, and continue a repeatable loop in which combat, gathering, refining, crafting, equipment, MODs, enhancement, repair, party play, quests, rewards, mobs, and the client UI exchange versioned data without destroying legacy data.

Completion requires a playable loop, not merely domain classes. Every enabled feature must be restart-safe, duplication-safe, permission-tested, observable, and reversible. Systems whose balance values remain undecided may ship only behind disabled flags with values isolated in validated balance data.

## Core loop

```text
combat / gathering / quests
-> resources, monster materials, MOD materials, catalysts, rewards
-> refine and craft equipment bases
-> add MODs, enhance, promote Tier, repair
-> improve a build and profession mastery
-> clear harder mobs, bosses, and the final-island gate
-> enter the repeatable Beta endgame and economy loop
```

Combat-only players must be able to progress through rewards and the market boundary without mandatory profession quests. Gatherers and crafters must create value without being forced into the same combat route.

## Included systems

- Level 1-45 progression, XP, passive points, unlocks, versioned persistence.
- Eight equipment slots, T1-T3, ILv 1-45, rarity, quality, base rolls, binding, and legacy views.
- Data-driven MOD definitions and item MOD entries.
- Existing combat plus staged metadata, elemental, class, and status-effect integration.
- Gathering, refining, crafting, Tier promotion, enhancement, repair, and atomic transactions.
- Party, quest participation, reward claims, and final-boss endgame unlock.
- Versioned Mob Editor/content definitions and runtime validation.
- Capability-negotiated client UI while preserving old-client fallbacks.

## Out of scope

- Levels above 45, T4+, light/dark elements, full magic civilization, guild territory, siege, and a production market implementation.
- Unapproved XP curves, passive nodes, MOD catalog/weights, profession timing, enhancement probabilities, drop rates, party XP distance, market tax, or PvP balance.
- Removing `DamageType.TRUE`, flat penetration, existing PDC keys, schema v1, channel names, or legacy player/item readers without a separate migration decision.

## Eight development tracks

| Track | Name | Primary output |
| --- | --- | --- |
| A | player-progression-persistence | Versioned player aggregate, repository, level/passive boundaries |
| B | equipment-item-mods | Equipment/item metadata and MOD definition/read model |
| C | combat-elements-classes | Combat metadata adoption, elements, class integration |
| D | gathering-refining-crafting | Resource/profession/recipe domains and atomic production |
| E | enhancement-tier-repair | Legacy-compatible enhancement, promotion, break/repair transactions |
| F | party-quest-rewards | Party lifecycle, participation, quests, idempotent rewards |
| G | mob-editor-content | Mob schema evolution, content validation, drops/spawns/skills |
| H | client-ui-protocol | Capability handshake, versioned UI state, legacy fallback |

Detailed ownership and contracts are in `dependency-graph.md` and the eight track briefs.

## Integration order

1. Merge Phase 0 contracts and disabled registries into `integration/beta-full-build`.
2. Merge A and B domain/persistence foundations; neither may enable gameplay.
3. Merge C and D pure models/adapters behind false flags.
4. Merge E after B and D transaction interfaces stabilize.
5. Merge F after A and reward interfaces stabilize.
6. Merge G after B/C/F schemas are available.
7. Merge H capability and UI support after producer payloads stabilize.
8. Enable one vertical slice at a time on integration, run the acceptance matrix, and only then prepare a main PR.

Tracks may develop concurrently against interfaces, but integration order is governed by dependency readiness rather than completion time.

## Feature flag strategy

- Every new Beta runtime begins disabled; flag IDs are listed in `canonical-ids.md`.
- `false` means the exact existing behavior and legacy storage path remain authoritative.
- Unknown, missing, null, or non-boolean config values resolve to `false`.
- Flags do not imply dependencies. A consumer must check that required provider capabilities exist and otherwise fail closed to legacy behavior.
- Runtime overrides are not part of Phase 0. Reload creates and atomically publishes a new immutable snapshot; wiring reload into gameplay is track-owned work.
- Existing starter-sword route/shadow and SpinSlash shadow settings are separate and unchanged.

## Migration strategy

1. Back up persisted data before any migration.
2. Read the schema ID/version before interpreting data.
3. Preserve the original bytes/PDC/YAML on unknown versions and isolate the record.
4. Build a read-only legacy view; never mutate on read.
5. Validate a proposed new representation, write atomically, then mark commit success.
6. Keep legacy readers until migration fixtures, restart tests, and rollback tests pass.
7. Do not change existing IDs, PDC keys, channel names, Mob schema v1, or legacy enhancement fields.

`SchemaVersions` records only approved versions. Wave 1 approves player-data, equipment-item, mod-definition, recipe-definition, and existing mob-definition as version 1. The aggregate client protocol remains an owner decision. Legacy data is not v1 by implication and is never rewritten on read.

## Test strategy

- Pure unit/property tests for validation, calculations, immutability, ID uniqueness, and finite-number rules.
- Repository round trips, atomic-save interruption, unknown-version isolation, and migration fixtures.
- Transaction tests for duplicate requests, inventory full, logout, shutdown, and rollback.
- Paper manual tests for gameplay boundaries, lifecycle, permissions, scheduler cleanup, and legacy behavior when flags are false.
- Multiplayer tests for party, shared elemental contributions, reward attribution, and simultaneous transactions.
- Protocol tests for payload bounds, capability fallback, unknown packets, rate limits, and old clients.
- Performance tests must prove bounded maps/caches and acceptable tick cost under target concurrency.

The detailed gate is `acceptance-matrix.md`. Every JavaExec test is connected to `check` with assertions enabled.

## Rollback strategy

- Operational rollback: set the affected feature flag to false and restart/reload at the documented boundary.
- Code rollback: revert the track merge commit without reverting unrelated tracks.
- Data rollback: stop writes, restore the pre-migration backup, and use the legacy reader. Never downgrade in place.
- Transaction rollback: release reservations and restore consumed inputs before exposing outputs.
- Protocol rollback: stop advertising the capability; old channel behavior remains available.

## Parallel development rules

- Do not edit another track's owned package; cross-track communication uses immutable interfaces/records.
- Do not expose Bukkit types in domain or persistence contracts.
- Do not persist cooldowns, buffs, CC, burn/cold runtime state, casts, targets, UI state, or shadow validation state.
- Item transactions cannot partially succeed.
- With a feature flag false, preserve existing gameplay.
- Isolate unknown schema versions and unknown MODs; never silently discard them.
- Do not scatter raw IDs; use registries or typed IDs at boundaries.
- No unbounded UUID maps. Every scheduler/cache must have stop/clear and bounded cleanup.
- Reload and disable must be idempotent and null-safe.
- Reject NaN, Infinity, negative quantities, oversized payloads, and invalid config values.
- Roll back on full inventory, logout, stop, duplicate request, or persistence failure.
- Back up before migration and never destroy legacy items or player records.

## Definition of Done

A track is done only when its public contract is documented, flag defaults false, compatibility adapters exist, unit/integration/lifecycle/rollback tests pass, unknown versions are isolated, persistence is atomic where applicable, resources are bounded, manual verification is recorded, and the feature can be disabled without data loss. Its PR must contain only owned files plus explicitly reviewed shared-contract changes.

## Beta public gate

- All mandatory acceptance-matrix cells pass or have an owner-approved waiver.
- `clean check` and `clean build` pass in CI without deployment.
- A clean test server starts with all migrations backed up and no severe errors.
- Level 1-45 and the final-boss gate work after reconnect and restart.
- No known item/reward duplication, partial transactions, or destructive migration.
- Old items, old player data, existing channels, Mob schema v1, and old clients have tested fallbacks.
- Feature flags and rollback runbooks have been exercised.
- Critical/shield/enhanced/normal/elite/boss combat paths required for an authoritative cutover are explicitly validated before any cutover.
- All `REQUIRES_OWNER_DECISION` blockers for enabled features are resolved in canonical design documents.

