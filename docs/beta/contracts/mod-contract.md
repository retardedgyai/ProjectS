# MOD contract

## Definition

A MOD is data, not a dedicated Java class. Wave 1 defines `mod-definition` schema version 1. The complete catalog, weights, and balance values are `REQUIRES_BALANCE_DATA`.

| Field | Contract |
| --- | --- |
| MOD ID | Unique `<namespace>:<lower-kebab-name>` canonical ID |
| Rank | 1=T1/ILv1-15, 2=T2/16-30, 3=T3/31-45 |
| allowed slots | Non-empty immutable set of logical equipment slot IDs |
| required attack tags | Exact/declared match policy against attack metadata |
| excluded attack tags | Any match rejects application; cannot overlap required tags |
| stat type | Canonical typed stat/effect ID, never display text |
| value range | Finite minimum/maximum with minimum <= maximum |
| roll | Finite value inside the definition range and Rank |
| stacking layer | Explicit base-flat, base-percent, increased, conditional, or final layer; no implicit ordering |
| source | Definition pack/revision and generated material/operation source |
| display | Localizable key/template only; display does not define behavior |
| serialization | Schema ID/version, MOD ID, Rank, roll, source revision, slot index |

## Attack tags and applicability

MODs inspect the actual attack metadata, not weapon type. Required/excluded tags use the existing canonical attack tags. Element and attack-family rules are explicit. A MOD cannot infer `MELEE`, `MAGIC`, or an element from class/item display names.

## Stacking

Definitions identify a calculation layer and compatible target. The combat/stat aggregator applies each source once and emits an immutable breakdown. Generic MODs must not be reapplied to secondary damage already based on a MOD-amplified direct hit. SHATTER, burn, detonation, DoT, reflected, and chained effects require explicit applicability data.

## Unknown and invalid MODs

- Unknown schema version or MOD ID is preserved as an opaque entry, shown as unsupported, and contributes zero gameplay effect.
- Invalid Rank, slot, tag combination, range, roll, duplicate slot, NaN/Infinity, or forbidden stacking layer rejects the proposed definition/item mutation.
- Loading one invalid definition does not discard other valid definitions. The invalid entry is isolated with bounded diagnostics.
- A save cannot normalize, delete, or replace an unknown entry without an explicit migration and owner confirmation.

## Mutation boundary

Add, reroll, and remove operations use the recipe/transaction contract. They validate the current item revision and slot, reserve inputs, produce a proposed immutable item, persist it, and commit once. Material costs, removal policy, weights, and trade implications remain `REQUIRES_OWNER_DECISION`/`REQUIRES_BALANCE_DATA`.

