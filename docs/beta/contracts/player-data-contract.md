# Player data contract

## Boundary

Player persistence is a versioned, Bukkit-free aggregate keyed by player UUID. Runtime services consume immutable snapshots and commands; they do not retain or serialize Bukkit `Player`/entity/world objects. No player schema is currently persisted, so the first schema number is `REQUIRES_OWNER_DECISION`.

## Persistent candidates

| Field | Contract |
| --- | --- |
| level | 1-45 in Beta; level-up does not automatically add HP/attack |
| experience | finite non-negative integer/decimal representation selected with the XP curve; `REQUIRES_OWNER_DECISION` |
| passive points | non-negative granted/spent accounting; no negative balance |
| allocated passive nodes | canonical node IDs; unknown IDs isolated, not discarded |
| selected class | canonical class ID; unknown class preserved and gameplay falls back safely |
| profession mastery | map of canonical profession ID to validated progress |
| quest state | versioned quest ID, state, counters, claim markers |
| unlocks | canonical unlock IDs, including final-boss/endgame gate |
| currency | exact integral quantity by canonical currency ID; concrete IDs `REQUIRES_OWNER_DECISION` |
| persistent resources | only explicitly declared out-of-combat resources; each resource decides reconnect semantics |
| settings | whitelisted user settings, not arbitrary config or client payloads |

Starter/current equipment references may join the aggregate only through the equipment contract; item payloads must not be duplicated in incompatible formats.

## Never persist / temporary

- Current cooldowns or cooldown caches.
- Temporary buffs, debuffs, active CC, shields, active burn/cold/freeze state.
- Combat-only caches, critical cache, current target, entity references.
- Transient UI state, open screens, cursor state, packet rate-limit state.
- Active cast, skill session, telegraph, scheduled task, or in-flight attack.
- Shadow validation state, metrics, runtime feature overrides.

On reconnect these values start from their normal runtime defaults. A track may persist a temporary value only after a separate design decision changes this contract and supplies migration tests.

## Versioning and migration

- Every record carries schema ID `player-data`, positive schema version, player UUID, revision, and last successful save time.
- Current and legacy decoders are explicit. Unknown versions are copied to quarantine/read-only backup and never interpreted as current.
- Migration is pure and deterministic: old bytes -> validated proposed record. It cannot call Bukkit APIs or mutate the source.
- Migration requires a backup and atomic commit. Failed validation leaves the previous record authoritative.
- New fields require safe defaults documented per version. Removing or renaming canonical IDs requires an alias/migration decision.

## Save lifecycle

1. Capture an immutable domain snapshot on the main-thread boundary where Bukkit state is involved.
2. Validate all IDs, quantities, finite values, invariants, and revision.
3. Serialize and write a temporary file/transaction row off the main thread.
4. Flush and atomically replace/commit.
5. Publish the new saved revision only after commit succeeds.

Only one write per player/revision may commit. Coalesce newer dirty snapshots without allowing an older completion to overwrite a newer revision.

- **Login/reconnect:** load and validate before enabling persistence-backed features; duplicate login cannot create two writers.
- **Logout:** stop accepting new mutations, capture final snapshot, enqueue/await a bounded save, then clear runtime references.
- **Plugin disable:** reject new mutations, drain bounded saves, report failures, close repository idempotently.
- **Crash:** last committed revision remains valid; temporary files/incomplete transactions are ignored or recovered deterministically.

## Failure and rollback

Repository failure must not silently reset a player to level 1 or overwrite source data. Persistence-backed gameplay remains disabled or read-only, the error is rate-limited/logged, and operators restore the backup or repair the quarantined record. Duplicate save requests are idempotent by player ID plus revision/request ID.

