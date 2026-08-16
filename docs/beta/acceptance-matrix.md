# ProjectS Beta acceptance matrix

Legend: **R** required before enabling; **C** conditionally required when the feature has that boundary; **N/A** not applicable. A checked test must link to an automated task, CI run, or dated manual evidence.

| Feature | Unit | Property | Integration | Paper manual | Multiplayer | Persistence | Duplication | Rollback | Performance | Security/permission |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Player level/XP/passives | R | R | R | R | C | R | R | R | R | R |
| Player repository/migration | R | R | R | C | C | R | R | R | R | R |
| Equipment metadata/equip | R | R | R | R | C | R | R | R | R | R |
| MOD definition/application | R | R | R | R | C | R | R | R | R | R |
| Combat metadata/snapshots | R | R | R | R | C | C | R | R | R | C |
| Fire shared state | R | R | R | R | R | N/A | R | R | R | C |
| Ice/shared SHATTER | R | R | R | R | R | N/A | R | R | R | C |
| Lightning | R | R | R | R | R | N/A | R | R | R | C |
| Gathering | R | R | R | R | C | R | R | R | R | R |
| Refining | R | R | R | R | C | R | R | R | R | R |
| Crafting | R | R | R | R | C | R | R | R | R | R |
| Tier promotion | R | R | R | R | C | R | R | R | R | R |
| Enhancement v2 | R | R | R | R | C | R | R | R | R | R |
| Repair v2 | R | R | R | R | C | R | R | R | R | R |
| Party | R | R | R | R | R | R | R | R | R | R |
| Quests/participation | R | R | R | R | R | R | R | R | R | R |
| Reward claim | R | R | R | R | R | R | R | R | R | R |
| Mob Editor v2/content | R | R | R | R | C | R | R | R | R | R |
| Client capability/UI | R | R | R | R | R | C | R | R | R | R |
| Final boss/endgame gate | R | R | R | R | R | R | R | R | R | R |

## Mandatory scenarios

- Property tests cover finite values, bounds, ID/schema uniqueness, collection immutability, and deterministic calculations.
- Persistence tests cover atomic replacement, interrupted writes, old/current/unknown versions, logout, disable, and reconnect.
- Duplication tests cover repeated packets, double clicks, retries, simultaneous claims, full inventory, disconnect, and server stop.
- Rollback tests prove no partial item/currency/resource mutation and preserve unknown/legacy data.
- Performance tests assert bounded per-player/per-entity state and scheduler cleanup, then record measured tick/payload limits.
- Security tests cover permissions, malformed/oversized packets, path traversal, negative/huge quantities, NaN/Infinity, and rate limits.
- Paper evidence must state the exact build SHA, flag state, server version, player count, steps, result, and rollback state.

## Merge and public gates

Track PRs may merge into integration with pure tests and flags false. A feature may be enabled on integration only after every applicable **R** cell passes. A main/public release additionally requires a full vertical-loop run, restart/reconnect round trip, migration backup/restore rehearsal, old-client/old-data compatibility, and zero unresolved BLOCKER/HIGH findings.

## Wave 2 foundation gate

Track E and Track F may open Draft PRs when their pure public boundaries,
failure injection, idempotency, bounds, and Bukkit-free API tests pass while all
associated flags remain false. This gate does not satisfy the Paper manual,
multiplayer, persistence activation, balance approval, or public-enable cells.
Both Tracks must start from the same `WAVE_2_BASE_SHA` created by merging
`wave-2-owner-decisions.md`; neither Track is automatically merged.

## Wave 3 foundation gate

Track G and Track H start from the same merge commit containing
`wave-3-owner-decisions.md`. Draft PRs require schema-v1 read invariance,
mob-definition v2 current writes with v1/v2 reads, aggregate client protocol v1,
bounded codecs/repositories/sessions, malformed-input and permission tests,
old-client fallback, lifecycle cleanup, and all feature flags false. The gate
does not satisfy production content/balance, Paper/manual, gameplay activation,
deployment, or public-enable cells. G/H PRs remain Draft and are not
automatically merged.

## Activation Wave 1 adapter gate

All four Server Tracks start from the same merge commit containing
`activation/wave-1-owner-decisions.md`; Track 4 Client starts from compatible
Client `27f2c5e4b535dee19c860b711cac9662606540ff`. Draft PRs must expose module
providers and operator contributors without editing central Plugin/command/
config/channel registration.

Acceptance requires lifecycle idempotency, flag/policy/dependency denial,
bounded UUID state, no retained Bukkit entity/player object, staging path
isolation, READ_ONLY write count zero, stop cleanup, and every Track-specific
failure/duplicate/reconnect test. Protocol producers advertise only while
RUNNING. Production player/item/Mob paths, automatic migration, feature
activation, deployment, Paper/manual, and public-enable cells remain
unsatisfied until the later Integration and staging gates.

