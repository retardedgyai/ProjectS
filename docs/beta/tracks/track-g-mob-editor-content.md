# Track G: Mob Editor and content

- **Branch:** `codex/beta-track-g-mob-editor-content`
- **Worktree:** `beta-track-g-mob-editor-content`
- **Contract lineage SHA:** `8ba653a8dc3f604dc23a142cca5f5a88f51682a9`
- **Actual branch start SHA:** integration HEAD after Phase 0 (`REQUIRES_INTEGRATION_MERGE`).
- **PR base:** `integration/beta-full-build`

## Scope

Preserve Mob schema v1 while adding an approved additive v2 definition/reader for skills, phases, drops, spawns, attack metadata, attributes, weaknesses, fire/ice category, and rewards; strengthen cross-reference validation, revision conflicts, atomic save/history/rollback, and runtime last-good-definition behavior.

## Out of scope

Changing v1 channel names, rewriting v1 on read, inventing skills/drops/spawn/reward numbers, client screen implementation, combat/item/reward domain internals.

## Ownership

Owned: `monster.editor`, content definition/validator/repository tests and narrow MonsterManager adapters. Network packet work is coordinated with H. Do not edit B/C/F internals or existing channel IDs.

## Public interfaces and dependencies

Consume Track B item/drop references, Track C AttackMetadata/category/weakness, Track F reward/participation references. Publish immutable validated mob/content snapshots and revisioned apply events. V1 remains a first-class reader.

## Feature flags

`MOB_EDITOR_V2`; false retains complete existing v1 behavior.

## Tests and manual verification

V1 fixtures/load/save invariance, v2 current/unknown versions, all finite/bounds/path/reference checks, phase graph, spawn caps, permission matrix, payload limits, concurrent revisions, async/main-thread boundaries, atomic failure/history/rollback, test-spawn cleanup. Paper verifies v1 and v2-capable fallback without content loss.

## Commit split

1. V1 fixtures. 2. additive v2 pure model. 3. validator/repository. 4. runtime adapter. 5. protocol adapter with H. 6. tests/docs/disabled wiring.

## Merge prerequisites and rollback

B/C/F public IDs/interfaces stable; schema/channel capability owner decision; approved content data for runtime. Rollback flag false, keep v1 repository, cancel v2 tasks, select last good revision as a new commit.

## Completion report

Report start SHA, v1 compatibility proof, v2 schema/fields, dependencies, validation/revision/atomicity, content decisions, commits/tests/CI/Paper evidence, flag, rollback/history, final status.

