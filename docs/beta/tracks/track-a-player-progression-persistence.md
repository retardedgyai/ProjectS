# Track A: player progression and persistence

- **Branch:** `codex/beta-track-a-player-progression-persistence`
- **Worktree:** `beta-track-a-player-progression-persistence`
- **Contract lineage SHA:** `8ba653a8dc3f604dc23a142cca5f5a88f51682a9`
- **Actual branch start SHA:** exact `WAVE_1_BASE_SHA` after the Wave 1 owner-decision PR merges.
- **PR base:** `integration/beta-full-build`

## Scope

Versioned Bukkit-free player record/repository, load/save lifecycle, level cap boundary, XP/passive point accounting structure, profession/quest/unlock/settings containers, immutable snapshots, and unknown-version isolation. Concrete XP curve and passive nodes remain data/owner decisions.

## Out of scope

Equipment serialization, combat formulas, party logic, quest content, reward values, client UI, gameplay enablement before persistence tests.

## Ownership

Owned: `io.github.gyai.projects.player`, `io.github.gyai.projects.data`, new Track A tests/docs. Shared `ProjectSPlugin`/config wiring only in a final small commit. Do not edit combat, item, monster/editor, network, or other track packages.

## Public interfaces and dependencies

Publish immutable `PlayerProgressSnapshot`, repository load/save result, migration/isolation result, and commands/events using UUID and canonical IDs only. No Bukkit type crosses the repository/domain boundary. Inputs are `player-data-contract.md`, `SchemaVersions`, clock/executor boundaries. Consumers B/F/H depend on these records, not implementation classes.

## Feature flags

`PLAYER_PERSISTENCE`; `PASSIVE_TREE` remains inactive unless persistence is enabled and its own requirements pass.

## Tests and manual verification

Unit/property: bounds, finite quantities, immutable maps/sets, revision ordering, duplicate IDs. Persistence: legacy/missing/current/unknown version, atomic replace, stale completion, logout/disable/reconnect, corrupted file. Duplication: simultaneous save/retry. Paper: login/logout/restart with flag false then controlled integration-only enable.

## Commit split

1. Pure records/validation. 2. Repository and atomic migration. 3. Lifecycle adapters. 4. Tests/fixtures. 5. Disabled flag wiring/docs.

## Merge prerequisites and rollback

Phase 0 and Wave 1 owner decisions merged; use player-data v1 and the approved YAML boundary; all save failure injections pass; no temporary state persists. Rollback disables flags, stops writes, restores backup, and uses the legacy/in-memory path. Never downgrade a record in place.

## Completion report

Report start SHA, schema/version decision, persistent and excluded fields, migration fixtures, save lifecycle, changed files/commits, automated/manual tests, CI, flag state, rollback rehearsal, unresolved decisions, and final status.

