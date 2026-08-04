# ProjectS Beta canonical ID registry

IDs are stable data contracts, not display names. Existing IDs are frozen. New persisted IDs use lowercase ASCII, begin with a letter, and contain only `a-z`, `0-9`, `_`, `-`, `.`, `/`, or `:` as allowed by their storage format. Validation must reject whitespace, path traversal (`..`), absolute paths, control characters, and overlong values.

## Existing item IDs

- `starter_sword`
- `starter_bow`
- `painter_staff` (registered only while the existing Painter feature is enabled)
- `enhancement_stone`
- `repair_crystal`

The legacy PDC value remains under `projects:item_id`. Do not rename or rewrite it during read.

## Existing class IDs

- `warrior`
- `scout`
- `painter_mage`

## Existing skill/action IDs

Warrior: `spin_slash`, `sweeping_slash`, `warrior_charge`, `execution_leap`, `earth_shatter`, `indomitable_spirit`, `battlefield_aura`, `endure`, `fighting_spirit_release`, `blood_battle`, `end_war_strike`.

Painter spell definition IDs retain their existing hyphenated values: `devastating-fire`, `severing-bolt`, `molten-fissure`, `fleeting-current`, `pool-of-reflection`, `stirring-lights`, `grim-visage`, `gaze-of-the-abyss`, `crushing-maw`, `spiraling-despair`. Existing cooldown IDs such as `painter_subject_destruction`, `painter_subject_harmony`, `painter_subject_binding`, and `painter_ultimate` are separate action/cooldown contracts.

Scout action IDs currently used by runtime are `scout_q`, `scout_e`, and `scout_blink`.

## Stat IDs

The existing Java IDs are the exact `StatType` enum constants and remain source-compatible. Persisted external stat IDs do not yet exist; Track A/B must introduce an explicit versioned mapping rather than serializing `Enum.name()` implicitly. Adding aliases or retiring flat penetration requires a migration decision.

## Equipment slot IDs

- `weapon`
- `head`
- `chest`
- `legs`
- `boots`
- `necklace`
- `ring_1`
- `ring_2`

These IDs identify logical slots. They do not rename Bukkit slots or existing inventory positions.

## Rarity and Tier IDs

Rarity: `common`, `uncommon`, `rare`, `epic`. Their MOD capacities are respectively 1, 2, 3, and 4. `unique` and `legendary` are reserved and are not Beta craft rarities.

Tier: `t1` (Lv/ILv 1-15), `t2` (16-30), `t3` (31-45).

## Namespaced future IDs

- MOD ID: `<namespace>:<lower-kebab-name>`, for example the namespace may be `projects`; the complete MOD catalog is `REQUIRES_BALANCE_DATA` and examples are not registrations.
- Recipe ID: `<namespace>:<category>/<lower-kebab-name>` where category is a validated domain such as refine/craft/promotion/repair. Concrete recipes are `REQUIRES_BALANCE_DATA`.
- Resource ID: `<namespace>:<lower-kebab-name>`. Existing runtime resource concepts `fighting_spirit` and `mana` must receive explicit adapters before persistence.
- Quest/reward IDs follow the same namespaced rule and are `REQUIRES_OWNER_DECISION` until content is approved.

Namespaces prevent cross-track collisions. Each registry rejects duplicate full IDs and does not accept a display name as an ID.

## Feature flag IDs

`fire-system`, `ice-system`, `lightning-system`, `equipment-v2`, `mod-system`, `player-persistence`, `passive-tree`, `gathering`, `refining`, `crafting`, `tier-promotion`, `enhancement-v2`, `repair-v2`, `party`, `quests`, `reward-v2`, `mob-editor-v2`, `client-beta-ui`.

All live under the YAML `features` section and default to false. Existing damage route/shadow keys are not feature aliases.

## Existing plugin channel IDs

- `projects:skill_input`
- `projects:hud_state_v2`
- `projects:loadout_req_v1`
- `projects:loadout_sel_v1`
- `projects:loadout_state_v1`
- `projects:balance_req_v1`
- `projects:balance_upd_v1`
- `projects:balance_act_v1`
- `projects:balance_state_v1`
- `projects:monster_ui_v1`
- `projects:mob_editor_req_v1`
- `projects:mob_editor_state_v1`
- `projects:telegraph_v1`
- `projects:telegraph_hello_v1`

Channel names and their current payload versions are frozen. A future global capability-handshake channel is `REQUIRES_OWNER_DECISION`; do not repurpose a feature-specific hello channel.

## Existing PDC and attribute IDs

Frozen PDC keys include `projects:item_id`, `projects:enhancement_level`, `projects:weapon_broken`, `projects:weapon_attack_power_bonus`, `projects:weapon_attack_speed_bonus`, `projects:training_dummy`, `projects:custom_monster_id`, `projects:mob_editor_test`, `projects:mob_editor_test_owner`, `projects:scout_arrow`, `projects:dev_cc_mode`, and `projects:dev_cc_duration_ticks`.

Frozen attribute modifier IDs include `projects:enhancement_attack_speed`, `projects:warrior_indomitable_attack_speed`, `projects:warrior_blood_battle_attack_speed`, `projects:hard_cc_movement`, and `projects:status_slow`.

## Schema IDs

- `player-data` — version `REQUIRES_OWNER_DECISION`; no persisted schema exists.
- `equipment-item` — version `REQUIRES_OWNER_DECISION`; legacy PDC is unversioned.
- `mod-definition` — version `REQUIRES_OWNER_DECISION`; no persisted schema exists.
- `recipe-definition` — version `REQUIRES_OWNER_DECISION`; no persisted schema exists.
- `mob-definition` — current supported version `1`, matching `MobDefinition.SCHEMA_VERSION`.
- `client-protocol` — version `REQUIRES_OWNER_DECISION`; existing packets are independently versioned (including HUD v2 and several v1 packets).

Unknown IDs or versions are isolated and preserved; they never silently fall back to a different schema.

