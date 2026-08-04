# Track B equipment/item/MOD foundation implementation

## Boundary and compatibility

This track starts from `b491075ea98102e6266db46b7fe6f3589abfc3d2` and
implements only the Track B-owned read and pure-model boundaries. It does not
connect equipment or MODs to gameplay.

`BukkitLegacyItemAdapter` reads the existing `projects` PDC keys through the
read-only `LegacyPdcSource` projection:

- `item_id`
- `enhancement_level`
- `weapon_broken`
- `weapon_attack_power_bonus`
- `weapon_attack_speed_bonus`

No writer exists on that projection. Reads, lore rendering, and inventory
scans never generate an item-instance UUID. Invalid legacy numeric or oversized
input is isolated as a failed `LegacyItemReadResult`; it is not normalized or
written back.

## Equipment schema v1

`EquipmentItemV1` implements the immutable `EquipmentView` interface. The
model defines the eight logical slots, T1/1-15, T2/16-30, T3/31-45, rarity
capacities 1/2/3/4, `UNSPECIFIED` quality, finite base rolls, optional crafter
and instance identity, enhancement 0-30, broken state, binding, and independent
trade/market/dismantle policy.

An optional UUID is accepted only as input to a proposed immutable item. No
code in this track creates one. `EquipmentWriteBoundary` is an interface only;
there is deliberately no writer or legacy migration implementation.

## MOD schema v1

`ModDefinition` and `ModEntry` validate canonical namespaced IDs, Rank 1-3,
allowed logical slots, required/excluded attack tags, finite ordered value
ranges, explicit stacking layer, source, display metadata, and non-negative
definition revision. `UnknownModEntry` defensively retains at most 16 KiB of
opaque payload and always reports effects disabled. `ModValidation` emits an
immutable stat contribution only for a known, compatible entry.

This foundation does not provide a MOD catalog, weights, balance values,
mutation, combat aggregation, equip enforcement, crafting, enhancement
mutation, UI, or persisted writer.

## Flags and rollback

`EQUIPMENT_V2` and `MOD_SYSTEM` remain false. The latter is not connected and
therefore cannot bypass the documented dependency on equipment v2. Rollback is
the removal of this pure/read-only branch; existing item creation, enhancement,
PDC, lore, and gameplay paths are unchanged.

## Verification

The Track B JavaExec tests cover legacy PDC byte/value preservation, inventory
read round trips, absence/stability of instance UUID, Tier/ILv and rarity
properties, duplicate MOD slots, immutability, finite-number rejection,
unknown MOD isolation, payload bounds, and disabled flags. Both tasks run with
assertions enabled and are attached to `check`.
