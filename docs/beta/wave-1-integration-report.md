# Wave 1 Integration Report

## Scope and integration order

Wave 1 integrates the disabled foundation contracts from Tracks A through D into
`integration/beta-full-build`. No gameplay wiring, deployment, balance choice,
or work from Tracks E through H is included.

| Track | Original head | Integration follow-up | PR merge commit |
|---|---|---|---|
| A — player progression and persistence | `7947b82376ad2f5fb222d9ca65538708d08bb0d6` | not required | `55abb02f74253b1552055882fd6d5fabc7ea6ca4` |
| B — equipment, items, and MODs | `6628bc2a3c9d973353edd0bd8beaae062f7ef2e9` | `6df17587898fda0085644ebfcac6bbd4870db70c`; task-brace correction `633f2453981f9e177feff0975edea7b9c839e785` | `5450d27891537c615904096b08da82154de7d905` |
| C — combat elements and class contracts | `10905a2b31585368abe35741f76dc67c8a2805bc` | `3f05c3ea4318e1ffc5639cb0e41fe5ad9d37c3f1` | `835b67163bfa8d09a17f07fe2aff83ec399ef677` |
| D — gathering, refining, crafting, and transactions | `cc96e93d40a933547dcb79ddfe04d0fc3a6c8a23` | `5b64ebab2f53dabb061ce7eea5c27fb205fd5c94` | `36c03b638a200745c23a6b01a66fb01c3f5bde77` |

The integration branch began at
`b491075ea98102e6266db46b7fe6f3589abfc3d2`. The post-Track-D validation base
is `36c03b638a200745c23a6b01a66fb01c3f5bde77`.

## Conflict resolution

The only merge-conflict file was `build.gradle.kts`. Each conflict was resolved
additively: existing combat, Mob Editor, shutdown, Phase 0, and previously
integrated Track tasks were retained; the incoming Track tasks were added once;
every JavaExec task kept assertions enabled with `-ea`; and every task remained
connected to `check`. No production implementation was changed to make the
tracks integrate.

The combined task graph includes the existing balance, CC, telegraph, stat,
damage, starter-sword, SpinSlash, Mob Editor, and lifecycle suites, plus:

- Phase 0: feature flags, schema registry, and beta contract presence
- Track A: player progress domain, repository, and persistence coordinator
- Track B: equipment/MOD foundation and legacy item compatibility
- Track C: Fire, Ice, and disabled-Lightning engines
- Track D: transaction and gathering foundation
- Integration: `Wave1IntegratedFoundationTest`

## Public boundaries

- Track A exposes immutable `PlayerProgressSnapshot` and
  `PlayerProgressRecordV1`, the `PlayerProgressRepository` storage boundary,
  and bounded file/coordinator implementations.
- Track B exposes schema-v1 equipment views, immutable MOD entries and
  validation, a read-only legacy PDC projection, and an explicit Bukkit adapter
  outside the pure domain boundary.
- Track C exposes pure Fire and Ice hit/state/event engines and a disabled
  Lightning contract. No approved runtime element behavior is connected.
- Track D exposes namespaced resource/recipe definitions, bounded gathering
  nodes, `TransactionRequest`, `TransactionParticipant`, and the staged,
  rollback-capable `TransactionEngine`.

The integrated smoke test reflects over representative public domain APIs and
rejects Bukkit `Player`, `ItemStack`, or `World` types. It does not instantiate
the plugin or require a server connection.

## Schema registry

| Schema | Version/status |
|---|---|
| `player-data` | `1` |
| `equipment-item` | `1` |
| `mod-definition` | `1` |
| `recipe-definition` | `1` |
| `mob-definition` | `1` |
| `client-protocol` | unresolved; owner decision required |

## Feature flags

The following Wave 1 flags remain false by default:

- `PLAYER_PERSISTENCE`
- `EQUIPMENT_V2`
- `MOD_SYSTEM`
- `FIRE_SYSTEM`
- `ICE_SYSTEM`
- `LIGHTNING_SYSTEM`
- `GATHERING`
- `REFINING`
- `CRAFTING`

Repository searches found no matching default-true or unconditional enable for
`features`, `FIRE_SYSTEM`, or `PLAYER_PERSISTENCE`. The existing plugin
entrypoint does not retain or expose the Wave 1 foundation services.

## Integrated smoke coverage

`Wave1IntegratedFoundationTest` verifies on one runtime classpath:

- the five version-1 schemas and unresolved client protocol;
- all nine Wave 1 flags disabled;
- a Track A v1 save/load/close round trip with UUID, revision, and level
  preserved, no leftover temporary file, and cleanup of the temporary root;
- a Track B T1/ILv12 common item with matching MOD capacity, an unknown MOD
  that cannot contribute effects, and a legacy compatibility read that does
  not mutate its source;
- Fire stack 9 to 10 producing exactly one detonation, consuming seven stacks,
  and retaining three; Freeze followed by exactly one valid Ice SHATTER; and
  disabled Lightning;
- all six successful Track D transaction stages exactly once and an injected
  production failure rolling back once;
- reuse of the Track A player UUID as the Track D request identity,
  non-colliding B/D ID forms, Bukkit-free public domain types, and no direct
  Wave 1 service field or method on the gameplay entrypoint.

Validation result on 2026-08-05:

- `gradlew clean check -PskipAutoStart`: successful, 42 actionable tasks
- focused `wave1IntegratedFoundationTest`: successful
- `git diff --check`: successful

The final clean build and GitHub CI result are recorded in the validation PR.

## Unresolved decisions and later-wave boundaries

- `client-protocol` remains intentionally unresolved.
- No balance values, live element application, persistence activation,
  equipment/MOD activation, or gathering/crafting activation are approved by
  this gate.
- Track E may consume the immutable player identity/progress and equipment/MOD
  contracts only after its own feature-gated integration plan is approved.
- Track F may consume the pure element and transaction outputs, but must not
  bypass their validation, bounded-state, idempotency, or rollback contracts.
- Bukkit adapters remain infrastructure boundaries and must not migrate into
  the pure domain interfaces.

## Rollback and next wave

The validation change can be rolled back as one merge commit without reverting
the A–D foundation merge commits. If an individual Track must be removed, revert
its integration merge in reverse order (D, C, B, A) and rerun the complete
`check` graph. Source branches are retained.

The candidate Wave 2 start is the final merge commit of the validation PR on
`integration/beta-full-build`; until that merge exists, the stable pre-validation
candidate is `36c03b638a200745c23a6b01a66fb01c3f5bde77`.
