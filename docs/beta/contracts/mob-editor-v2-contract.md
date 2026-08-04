# Mob Editor v2 contract

## Compatibility

Existing `MobDefinition.SCHEMA_VERSION = 1`, YAML, revision checks, atomic saves, permissions, payload bounds, and channels `projects:mob_editor_req_v1` / `projects:mob_editor_state_v1` remain supported. The current write version is `2`; supported read versions are `1` and `2`.

A v1 definition loads exactly as before. Opening, previewing, reloading, or test spawning never rewrites it. Conversion requires explicit upgrade/save confirmation, full validation, backup, and a new v2 revision. Unknown versions are quarantined and downgrade is forbidden.

## Proposed additive v2 sections

- `skills`: canonical skill/action references, cooldown/trigger data, attack metadata, finite coefficients.
- `phases`: ordered phase IDs, validated transitions, cancellation/cleanup rules.
- `drops`: canonical item/resource/reward references and balance-data tables.
- `spawns`: location/category/rules with bounded counts and scheduler lifecycle.
- `attack-metadata`: exact tags, physical/magical family, ElementProfile; no inferred tags.
- `attributes`: typed finite stat values and category defaults/overrides.
- `weaknesses`: PvE element weaknesses; no implicit player weakness.
- `fire-category`: normal/elite/miniboss/boss threshold category plus validated override.
- `ice-category`: cold/freeze/re-freeze category plus validated override.
- `rewards`: canonical reward references and participation policy.

Concrete skills, drops, spawn rates, weaknesses, and reward numbers are `REQUIRES_BALANCE_DATA`.

## Validation

- Validate ID/path safety, schema/version, revision, entity type, category, finite/non-negative stats, bounded lists/maps/strings, cross-reference existence, attack tag consistency, phase graph reachability, spawn caps, and reward/drop quantities.
- Reject NaN, Infinity, negative or huge values, path traversal, duplicate IDs, unknown enum ordinals, cyclic/impossible phases, and payload overflow.
- Unknown future sections/versions are isolated and preserved, never coerced to v1.
- Test spawn/apply keeps current permission checks and cannot bypass production validation.

## Revision conflict and save

Clients submit the base revision. Save compares it to the repository revision and returns a conflict snapshot without overwriting either version. A validated definition is written atomically; the last known good revision and bounded audit history support rollback. Async I/O never calls Bukkit API; applying the committed definition returns to the main thread.

## Runtime and rollback

Runtime instances retain the definition revision used at spawn. Reload swaps validated immutable definitions and does not mutate an attack already in progress. Failed apply leaves current mobs on the last good definition. `MOB_EDITOR_V2=false` disables v2 editing/runtime sections while v1 remains available. Rollback selects a known good revision and commits it as a new revision; it does not rewrite history.

Definition files are limited to 1 MiB. History is bounded to 20 committed
revisions per mob while retaining current, last-known-good, and referenced
rollback targets. Editor sessions are bounded to 512 global and 4 per player;
test spawns are bounded to 128 global and 8 per player. These are injectable
safety policies rather than content balance.

