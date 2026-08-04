# Beta Wave 1 owner decisions

Decision baseline: `f266aadca1c5b6a1c2ab098509814c177ff31d0d`  
Applies to Tracks A-D. These decisions do not enable gameplay.

## Schema version 1

The first new persisted/data-definition schemas are formally version 1:

- `player-data = 1`
- `equipment-item = 1`
- `mod-definition = 1`
- `recipe-definition = 1`
- `mob-definition = 1` (existing and unchanged)

`client-protocol` remains `REQUIRES_OWNER_DECISION` because current channels use independent payload versions.

Version 1 means the first version of each new schema. It does not mean legacy data already conforms to v1. Unversioned legacy equipment is accepted only through a read-only migration view; opening, reading, rendering lore, or scanning an inventory must not write v1 data or generate an instance UUID. Unknown versions are quarantined/preserved and downgrade is forbidden.

## Player persistence v1

- Storage unit: one UTF-8 YAML file per player UUID.
- Path: `plugins/ProjectS/data/players/<uuid>.yml`.
- Safety sequence: validated immutable snapshot -> temporary file -> flush -> atomic move -> previous backup.
- Unknown/corrupt data is quarantined without overwriting the source.
- Save ordering uses a non-negative revision; stale writes are rejected and duplicate saves of the same revision are idempotent.
- Domain/persistence records contain no Bukkit `Player`, entity, world, inventory, location, or API type.

Numeric storage decisions:

- `experience`: non-negative `long`.
- `currency`: canonical currency ID -> non-negative `long`.
- `profession mastery`: canonical profession ID -> non-negative `long` progress.
- `revision`: non-negative `long`.

XP curve, currency catalog/acquisition, profession catalog/curve, and gameplay rewards remain unimplemented and require later decisions. Temporary combat/UI state remains excluded by the player data contract.

## Legacy equipment and quality

Legacy Tier, ILv, rarity, quality, and MOD capacity are not inferred. Track B may expose a read-only legacy view but may not implement a legacy-to-v1 writer or mutate existing PDC.

Quality must represent `UNSPECIFIED`. No numeric default, grade, probability, or migration value may be invented. New v1 equipment validation can distinguish an explicitly specified future value from `UNSPECIFIED`, but production/write behavior remains out of scope.

## Fire Wave 1 policy

The Wave 1 fire engine is pure and policy-driven:

- One hit can emit at most one detonation event.
- Consuming stacks retains player and physical/magical contribution proportions.
- Detonation returns an immutable event; it never calls Bukkit damage directly.
- Undecided periodic burn/DoT coefficients and ticks are not implemented.
- PvP is out of scope.

The one-detonation and proportional-retention rules are Beta Wave 1 policies, not hard-coded global assumptions. The pure engine accepts or encapsulates a replaceable policy so a later approved policy can change without replacing state storage.

## Track start gate

Tracks A-D must start from the exact merge commit produced when this decision PR is merged into `integration/beta-full-build`. That value is recorded as `WAVE_1_BASE_SHA`; all four branches/worktrees must resolve to it before editing.

