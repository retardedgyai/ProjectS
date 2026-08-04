# Beta Track E foundation report

## Baseline and scope

- Wave 2 base SHA: `33558ce869e38c3921210740273c38feebb67352`
- Branch: `codex/beta-track-e-enhancement-tier-repair`
- Owned packages: `enhancement.v2`, `equipment.operation`, and `repair`
- Runtime gameplay wiring, production balance, migration writes, PDC changes,
  deployment, and Tracks F/G/H are outside this change.

This foundation consumes the immutable Track B `EquipmentItemV1` and
`EquipmentWriteBoundary` contracts and the Track D `TransactionEngine` /
`TransactionParticipant` boundary. It does not edit their internals or supply a
production writer.

## Legacy enhancement characterization

`LegacyEnhancementCharacterization` reads through the existing
`LegacyItemCompatibilityReader`. Tests cover every enhancement value from +0
through +30 and verify that serialized fixture state is unchanged by reading.
The unchanged legacy keys and value types are characterized as:

| Field | PDC key | Type / presence |
| --- | --- | --- |
| enhancement | `projects:enhancement_level` | integer, 0..30 |
| broken | `projects:weapon_broken` | byte presence |
| attack power adjustment | `projects:weapon_attack_power_bonus` | double |
| attack speed adjustment | `projects:weapon_attack_speed_bonus` | double |

The legacy display prefix, enhancement lore facts, attack-speed attribute
presence, broken zero-attack behavior, material/repair cost boundaries,
success curve, break curve, and failure behavior are fixtures only. With
`ENHANCEMENT_V2=false`, `EnhancementManager` and `EnhancementListener` remain
the active behavior. No existing item is interpreted as equipment v1 or
rewritten.

## Enhancement policy and outcome

`EnhancementPolicy` is versioned and immutable. It accepts only a complete,
finite distribution for `SUCCESS`, `NO_CHANGE`, `DOWNGRADE`, and `BROKEN`;
every probability is in `0..1` and the total must be one. `REJECTED` is a
validation result and cannot receive probability. Material quantities are
positive, currency cost is a non-negative `long`, and no production policy or
probability is present.

`EnhancementResolver` accepts injected unit-interval randomness. Equal fixture
seed and input produce equal proposals. +30, broken sources, policy-level
mismatch, missing instance identity, and invalid RNG values are rejected or
isolated without changing the source item.

The transaction adapter deliberately defers random resolution until after the
resource reservation succeeds. It records the resolved immutable proposal in
`EquipmentOperationJournal` before costs are consumed. The same durable
proposal is reused after participant recreation; consume, produce, rollback,
and terminal replay never reroll the outcome.

## Tier promotion

`TierPromotionService` accepts only T1 to T2 and T2 to T3. T3, family mismatch,
equipment category/slot mismatch, invalid destination Tier/ILv, broken source,
and missing stable identity are rejected. The canonical family ID is explicit
and is never inferred from `itemId`.

The versioned carry policy must explicitly decide `QUALITY`, `MODS`,
`ENHANCEMENT`, and `BINDING`. A field may carry its source value or use the
explicit destination-template value; incomplete policies are rejected. In
particular, carrying a T1 MOD into a T2 item is rejected by equipment
validation. The foundation never silently retags or reties a MOD. Required
materials and currency remain immutable policy input, returned alongside the
Track D transaction request.

## Broken repair

`RepairService` requires a broken target and a distinct, unbroken, +0 donor
with the same Tier, canonical family, category, and slot. Donor quality, MODs,
crafter, and base-roll differences do not influence eligibility.

The proposed replacement is an exact copy of the target except that
`broken=false`. This preserves item instance UUID, base identity, family, Tier,
ILv, rarity, quality, base rolls, MOD slots/entries, crafter, enhancement,
binding, trade policy, and the bounded `EquipmentExtensionSnapshot` used for
future display name and engraving data. The donor is a second explicit Track D
input and is consumed only by the transaction boundary.

## Transaction safety

`EquipmentOperationParticipant` adapts proposals to the Track D order:

```text
validate -> reserve -> resolve and journal -> consume -> produce -> persist -> commit
```

No `EquipmentWriteBoundary.write` call occurs before commit. Full inventory is
rejected before reservation or RNG. Resolution failure restores a reservation;
consume and persistence failures roll back resources and staged proposal state.
A commit without an exact durable receipt becomes `COMMIT_UNCERTAIN` and is
retained as a terminal result, so retry cannot write or consume again. Duplicate
committed requests replay the existing terminal result.

The operation plan separately carries bounded material quantities, currency,
canonical family, source revisions, request ID, and future text extensions
because Track D intentionally has no equipment-specific metadata.

## Feature flags and integration state

- `TIER_PROMOTION=false`
- `ENHANCEMENT_V2=false`
- `REPAIR_V2=false`

All remain disabled by `FeatureFlagService` defaults. `ProjectSPlugin`, config
defaults, legacy listeners/managers, PDC keys, and gameplay entry points are
unchanged. Public pure APIs are checked for Bukkit parameter and return types.

## Verification

Track-specific JavaExec tests are connected to `check` with assertions enabled:

- `legacyEnhancementCharacterizationTest`
- `trackEFoundationTest`
- `enhancementTransactionSafetyTest`

They cover legacy +0..+30/no-write behavior, probability validation,
deterministic resolution, +30 rejection, sequential promotion, explicit carry
policy and cross-Tier MOD rejection, donor validation, complete repair field
preservation, donor consumption, duplicate/retry, reservation/consume/persist
rollback, full inventory, commit uncertainty, disabled flags, and the pure API
Bukkit boundary.

Required commands are:

```powershell
.\gradlew.bat clean check -PskipAutoStart
.\gradlew.bat clean build -PskipAutoStart
git diff --check
```

No JAR or client artifact is deployed by this Track.

## Unresolved balance and later dependencies

Production success, no-change, downgrade, break probability, costs, materials,
Tier recipes, and carry/reset transformations remain `REQUIRES_BALANCE_DATA` or
an owner decision. A durable production `EquipmentWriteBoundary`, resource
reservation port, and operation journal are required before feature activation.
Track E should integrate only after its CI and review pass, while preserving the
Track B item and Track D transaction contracts.

Rollback is branch/merge-commit removal while the three flags remain false. No
item migration or gameplay data rollback is required because this change does
not write production state.
