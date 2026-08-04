# Track A implementation: player progression and persistence foundation

## Baseline and ownership

- Wave 1 base: `b491075ea98102e6266db46b7fe6f3589abfc3d2`.
- Owned packages: `io.github.gyai.projects.player.progress` and
  `io.github.gyai.projects.persistence.player`.
- Schema: `player-data` version 1.
- Storage root supplied to the file adapter is
  `plugins/ProjectS/data/players`; the adapter stores `<uuid>.yml` below it.
- Domain and repository interfaces use JDK types only. No Bukkit `Player`,
  entity, world, inventory, location, or API type crosses the domain boundary.

## Persistent aggregate

`PlayerProgressSnapshot` is immutable and includes level 1-45, non-negative
long experience, granted/spent passive-point accounting, allocated passive
node IDs, an optional selected class ID, profession mastery progress, generic
quest states/counters/claim markers, unlock IDs, currency quantities,
explicitly persistent resources, whitelisted settings, revision, and the last
successful save timestamp. `PlayerProgressRecordV1` supplies the versioned
envelope. Unknown canonical IDs are preserved; no catalog or gameplay effect is
inferred.

The aggregate intentionally contains no cooldown, buff, CC, burn/cold, shield,
cast, target, UI, shadow-validation, entity, world, or other temporary state.
Equipment payloads are not part of this record.

`PlayerProgressBuilder` is a command-style construction boundary. Settings can
only be added when their IDs are supplied in the caller's explicit whitelist.
The repository uses the same whitelist while decoding and saving.

## Repository contract

`PlayerProgressRepository` publishes synchronous load and bounded asynchronous
save operations. `FilePlayerProgressRepository` implements:

1. strict UTF-8 decoding and a one-MiB input/output bound;
2. validated v1 YAML serialization;
3. a flushed temporary file in the target directory;
4. a flushed `backups/<uuid>.previous.yml` copy of the prior committed file;
5. atomic replacement only (no non-atomic fallback);
6. preserved source plus bounded quarantine copies for corrupt or unknown data;
7. disk and in-flight revision comparisons;
8. stale completion rejection before atomic replacement;
9. idempotency by player/revision/content and player/request ID;
10. conflict reporting for reused revisions or request IDs with different data;
11. a configurable bounded write queue and a bounded completed-request cache;
12. drain-on-close behavior and rejection of new writes after close.

Failure never creates a default replacement for an unreadable source. Atomic
replacement failures leave the previous file authoritative and remove the
temporary file. Unknown versions are never downgraded.

## Lifecycle boundary and feature flag

`PlayerPersistenceCoordinator` is a Bukkit-free boundary for a later Paper
adapter. With `PLAYER_PERSISTENCE=false`, connect returns the memory-only status
without calling repository load/save, so the existing runtime remains intact.
With the flag enabled, a session becomes persistence-backed only after a valid
load or a confirmed missing file. Corrupt, unknown, or failed loads block the
persistence-backed session. Duplicate connections cannot create two writers;
logout removes the active writer before accepting a final save. Close rejects
new sessions and drains the repository.

No `ProjectSPlugin` or gameplay wiring is added in this track. The configuration
default remains `features.player-persistence: false`.

## Tests

The Track A JavaExec suites are connected to `check` with assertions enabled:

- `PlayerProgressDomainTest`: bounds, accounting, canonical IDs, immutable
  collections, setting whitelist, and absence of Bukkit/temporary fields.
- `PlayerProgressRepositoryTest`: v1 UTF-8 round trip, missing file, previous
  backup, corrupt/unknown quarantine, injected atomic replacement failure,
  stale disk revision, duplicate requests, simultaneous saves, stale completion,
  bounded queue rejection, close drain, and idempotent close.
- `PlayerPersistenceCoordinatorTest`: flag-disabled memory-only behavior,
  load-before-enable gating, duplicate connection, logout final save, reconnect,
  and disable drain.

## Explicitly unresolved and out of scope

XP curves, Mob XP grants, passive-node effects/cost catalog, quest content,
currency catalog/acquisition, profession mastery curve, UI, equipment saving,
and gameplay connection remain unimplemented. No balance values were invented.

## Rollback

Keep `PLAYER_PERSISTENCE=false`, close/drain the coordinator, and continue the
legacy in-memory path. If a stored file needs repair, preserve the quarantined
copy and restore `backups/<uuid>.previous.yml` through an operator-controlled
process. Never downgrade or overwrite an unknown-version source.
