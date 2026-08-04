# Item metadata contract

## Compatibility envelope

Existing items are identified by the unchanged PDC `projects:item_id`. Existing enhancement PDC keys remain authoritative for legacy fields until a tested writer migration explicitly commits a new representation. Merely reading an item must never rewrite it.

The `equipment-item` schema number is `REQUIRES_OWNER_DECISION` because current items have no overall schema field. Track B first implements a read-only migration view.

## Equipment metadata

| Field | Rule |
| --- | --- |
| legacy item ID | Required for existing custom items; exact existing value preserved |
| schema version | Positive supported equipment schema when v2 data exists; unknown isolated |
| equipment category | Canonical weapon/armor/accessory category; concrete category registry is track-owned |
| slot | One of `weapon`, `head`, `chest`, `legs`, `boots`, `necklace`, `ring_1`, `ring_2` |
| Tier | `t1`, `t2`, or `t3` |
| ILv | 1-45 and inside the Tier band; equip requirement equals ILv |
| rarity | `common`, `uncommon`, `rare`, `epic` |
| quality | Validated value/grade; scale and probability are `REQUIRES_OWNER_DECISION` |
| base roll | Immutable rolled base-stat entries with canonical stat ID and finite value |
| MOD slots | Exactly the capacity implied by rarity; occupied plus empty slots |
| MOD entries | Slot index, MOD ID, Rank, rolled finite value, definition revision/source |
| crafter | Stable player UUID and escaped display snapshot; UUID is authoritative |
| enhancement level | Integer 0-30, compatible with `projects:enhancement_level` |
| broken state | Compatible with presence/value semantics of `projects:weapon_broken` |
| binding | Explicit unbound/player/account/system-bound policy |
| trade policy | Independent allow/deny for direct trade and future market; starter/NPC items deny trade/market/dismantle |

Existing `projects:weapon_attack_power_bonus` and `projects:weapon_attack_speed_bonus` are preserved as legacy adjustment inputs until an approved stat migration.

## Identity and generated UUID

A generated item-instance UUID is required once an item can hold mutable high-value state (rolls, MODs, enhancement, binding, repair history) or participate in idempotent transactions. Stackable homogeneous materials may omit it if their full metadata is identical and quantity is handled atomically. UUID creation occurs once at committed production/migration, never on read, lore rendering, or inventory scan.

The UUID supports duplicate detection but is not proof of ownership. Server-side transaction/request records provide idempotency.

## Validation

- Reject unknown slot/Tier/rarity, invalid Rank, negative/oversized ILv or enhancement, NaN/Infinity, negative MOD capacity, duplicate slot indexes, and capacity mismatches.
- Preserve unknown MOD entries in an isolated opaque form and disable their effects.
- Lore/display text is derived output and never parsed as authoritative data.
- PDC payloads are bounded. No serialized Bukkit objects or Java native serialization.

## Legacy migration view

The view reads existing item ID, weapon definition, enhancement level/broken flag, and adjustment PDC without modifying the stack. Defaults for legacy Tier, ILv, rarity, quality, binding, and MOD capacity are `REQUIRES_OWNER_DECISION`; until decided the item remains legacy-compatible and cannot be silently promoted to v2. A committed migration writes new data atomically, retains required legacy keys, and is covered by byte/PDC fixtures and rollback.

