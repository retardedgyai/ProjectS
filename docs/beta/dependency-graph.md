# Beta full build dependency graph

## Shared boundary

All tracks branch only after Phase 0 is merged into `integration/beta-full-build`. The symbol `phase0-integration-head` means the exact integration SHA recorded at branch creation; it is intentionally not guessed in this document.

```text
A player persistence ----+----> F party/quest/rewards ----+
                         |                                  |
B equipment/MOD ---------+--> C combat/elements -----------+--> G content/editor --> H UI/protocol
       |                  |                                 |
       +--> D gather/refine/craft --> E enhance/tier/repair +-----------------------> H
```

An arrow means the consumer may integrate only after the provider's public records/interfaces are stable. Tracks may build fakes against an approved contract before that merge.

## Track contracts

| Track | Input contract | Output contract | Depends on | May change | Must not change | Merge order | Required integration test | Rollback |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A player-progression-persistence | canonical player/schema IDs, persistent/temporary classification | Bukkit-free player record, repository, migration result, immutable progression snapshot | Phase 0 | `player`, `data`, Track A docs/tests | combat formula, item PDC, channels | 1 | save/logout/disable/reconnect and unknown-version isolation | flag false, stop writes, restore backup/legacy reader |
| B equipment-item-mods | item metadata and MOD contracts, legacy PDC list | equipment read model, legacy migration view, MOD definition/value records | Phase 0; player identity interface from A when available | `item` and dedicated equipment/MOD packages | enhancement mutation, combat application, channels | 1 | old item -> view -> round trip without PDC loss | flags false, legacy reader only, restore item backup |
| C combat-elements-classes | attack metadata, stat snapshot, equipment stat output, mob category/weakness interfaces | pure element/state calculations and one-path-at-a-time adapters | A snapshot, B stat output; G fakes | `combat`, `skill`, `status` and C tests | persistence repositories, item serialization, Mob Editor wire schema | 2 | legacy parity, finite/property tests, one apply, shared contribution lifecycle | element flags false and legacy adapters authoritative |
| D gathering-refining-crafting | resource/recipe IDs, player mastery identity, item output factory, transaction contract | resource node/refine/craft definitions and atomic transaction service | A mastery, B item factory | dedicated gathering/refining/crafting packages | item PDC internals, enhancement, quest rewards | 2 | reserve/consume/produce/persist/commit with full-inventory/logout/duplicate cases | flags false, cancel reservations, remove no committed outputs |
| E enhancement-tier-repair | B equipment view/writer, D materials/transaction service, existing enhancement PDC | compatible promotion/enhancement/repair operations | B and D | `manager`/`listener` only through reviewed adapters plus dedicated E package | raw legacy PDC names, unrelated combat balance | 3 | +0..+30 fixture migration, break/repair rollback, duplicate-click | enhancement-v2/tier/repair flags false, legacy enhancement remains |
| F party-quest-rewards | A player/repository, canonical quest/reward IDs, C/G participation events | party snapshot, quest progress command/event, idempotent reward claim | A; C/G interfaces | dedicated party/quest/reward packages | combat application, item writer internals, client channels | 3 | reconnect/leader transfer, multiplayer credit, duplicate reward claim | flags false, preserve committed claims, revert to solo legacy flow |
| G mob-editor-content | existing Mob schema v1, B item/drop refs, C attack metadata/weakness, F reward refs | additive v2 draft contract, validator, v1 reader, content runtime adapters | B, C, F | `monster.editor`, content definitions, related tests | existing v1 channel names and destructive v1 rewrites | 4 | v1 load, v2 validate, revision conflict, atomic save, runtime rollback | mob-editor-v2 false, retain v1 repository and last good revision |
| H client-ui-protocol | immutable snapshots/capabilities from A-G, existing channel registry | versioned capability/UI packets and old-client fallback | A-G payloads; may scaffold handshake early | `network` and explicitly coordinated plugin wiring; client repo in separate PR | rename existing channels, domain/persistence logic | 5/last | old/new client matrix, packet bounds/rate limits, reconnect and unknown packet | client-beta-ui false, stop advertising capability, existing UI/channels remain |

## Feature dependencies

- `PASSIVE_TREE` requires `PLAYER_PERSISTENCE`.
- `MOD_SYSTEM` requires `EQUIPMENT_V2`.
- `REFINING` requires `GATHERING`; `CRAFTING` requires `REFINING` and `EQUIPMENT_V2` for equipment outputs.
- `TIER_PROMOTION`, `ENHANCEMENT_V2`, and `REPAIR_V2` require `EQUIPMENT_V2`; their material transactions require the D transaction service.
- `QUESTS` and `REWARD_V2` require `PLAYER_PERSISTENCE`; party-aware credit also requires `PARTY`.
- `FIRE_SYSTEM`, `ICE_SYSTEM`, and `LIGHTNING_SYSTEM` require the combat metadata/snapshot boundary; they do not require one another.
- `MOB_EDITOR_V2` consumes contracts from equipment, combat, and rewards but must remain usable in v1 mode without them.
- `CLIENT_BETA_UI` requires only capabilities actually advertised by the server and must tolerate every other flag being false.

Flags remain independent booleans. Dependency violations do not auto-enable providers; the consumer must remain inactive and report a validation error.

## Shared-file coordination

`ProjectSPlugin`, `build.gradle.kts`, `config.yml`, `plugin.yml`, canonical contract documents, and CI workflows are shared hotspots. A track may modify them only in a small integration commit after its owned implementation passes tests. Large registrations must be split into installer/factory objects to avoid eight tracks editing lifecycle code concurrently.

