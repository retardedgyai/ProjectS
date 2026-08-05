# Beta activation master plan

## Baseline and scope

Activation starts from Server integration
`d4ac712fc70b7f780a6ccb026ba30b3160b4e710`. The compatible Client is
`27f2c5e4b535dee19c860b711cac9662606540ff`. Compatibility is anchored by
protocol manifest SHA-256
`49d37172e5f5a95207876b328b52bf0d0a1a04aa6ec9a6f2e9f0bca8aa8937ac`
and vectors SHA-256
`dde5a2e27d46e548b03abbc4f991c7542cf4b4f2d2a0a08c69bf292b3ec3bf1a`.

Foundation ownership remains unchanged:

- A: player progression and persistence
- B: equipment, item metadata, and mods
- C: combat elements and class contracts
- D: gathering, refining, crafting, and transaction contracts
- E: enhancement, tier promotion, repair, and transaction safety
- F: party, quest, and reward foundations
- G: Mob Editor v2 domain, repository, and history
- H: Server/Client protocol, state displays, and command ports

Phase 0 adds only the Runtime Kernel. No foundation is connected to gameplay,
no Beta protocol channel is registered, and no feature is enabled.

## Runtime modules and dependencies

The kernel owns eight responsibility-level module IDs:
`PLAYER_PERSISTENCE`, `EQUIPMENT`, `COMBAT_ELEMENTS`,
`GATHERING_CRAFTING`, `ENHANCEMENT_REPAIR`, `PARTY_QUEST_REWARD`,
`MOB_EDITOR_V2`, and `CLIENT_BETA_PROTOCOL`.

Descriptors, rather than feature keys alone, declare module dependencies,
activation flags, infrastructure requirements, minimum mutation policy, and
whether a read-only fallback is valid. Planned constraints are:

- Equipment can provide a read-only view without persistence; mutations need
  player persistence.
- Combat elements require the legacy combat boundary; equipment contribution
  is optional.
- Crafting equipment output needs Equipment, while mutations need transaction
  infrastructure.
- Enhancement and repair need Equipment, transaction infrastructure, and at
  least staging writes.
- Party can be temporary; Quest and Reward mutations need persistence, and
  Reward also needs transaction infrastructure.
- Mob Editor v2 preview can be read-only; save and apply need mutation rights.
- Client protocol advertises only capabilities produced by running modules.

The resolver rejects duplicate IDs and cycles, creates one immutable
topological start plan, and derives reverse shutdown order. A missing or
non-running dependency is `BLOCKED`. A fail-closed start failure rolls back
only modules started by that attempt. Legacy gameplay is outside Kernel
ownership and continues if Beta startup fails.

## Activation policy

Audience (`OFF`, `ALLOWLIST`, `GLOBAL`), target scope
(`TRAINING_DUMMY_ONLY`, `NON_PLAYER_PVE`, `ALL_PVE`), and mutation policy
(`READ_ONLY`, `STAGING_WRITE`, `PRODUCTION_WRITE`) are independent. Defaults
are `OFF`, `TRAINING_DUMMY_ONLY`, and `READ_ONLY`; PvP is excluded from every
target scope. Player and world allowlists are immutable and bounded. A missing
actor UUID or an empty world allowlist denies activation. Invalid values fail
to safe defaults. The policy and feature flags are snapshotted
once at startup; every change requires a restart.

## Activation stages and verification

Gates 0 through 5 are mandatory and sequential. The detailed entry/exit
criteria are in `staging-gates.md`. Every gate records configuration, artifact
hashes, module health, legacy comparison results, persistence effects, Server
logs, compatible Client state, and rollback rehearsal outcome.

The manual verification matrix grows by gate:

| Area | Gate 0 | Gate 1 | Gate 2 | Gate 3-4 | Gate 5 |
|---|---|---|---|---|---|
| Runtime lifecycle/diagnostics | Required | Required | Required | Required | Required |
| Training Dummy read-only | N/A | Required | Required | Required | Required |
| Staging mutations and backup restore | N/A | N/A | Required | Required | Required |
| Non-player PvE | N/A | N/A | N/A | Required | Required |
| Compatible Client/fallback | Static | Staging | Staging | Staging | Release candidate |
| Legacy regression | Full automated | Focused | Full | Full | Full |

## Deployment, data safety, and rollback

Deployment order is: stop Server, verify data and artifact backups, install
the reviewed Server artifact, install the exact compatible Client only when a
gate needs it, start Server, verify Runtime health before allowing test users,
then execute the gate matrix. Never raise audience, target scope, and mutation
policy in one change.

Before the first writable gate, back up player data, item-bearing inventories,
Mob definitions/history, configs, and both Server/Client artifacts. Each
module must document whether it writes, its transaction/idempotency boundary,
and how backup restoration is verified. The emergency procedure is in
`rollback-runbook.md`.

Main integration requires all prior gates, no BLOCKER/HIGH/MEDIUM review
findings, all checks green, bounded healthy diagnostics, compatible protocol
hashes, backup/restore evidence, no unplanned migration, and a successful
rollback rehearsal.

## Next Activation Tracks (documentation only)

1. Player persistence plus Equipment read adapter.
2. Fire/Ice plus Training Dummy combat adapter.
3. Gathering/refining/crafting/enhancement/repair adapter.
4. Party/quest/reward/Mob Editor/protocol adapter.

Each Track implements `BetaRuntimeModule`; it does not add independent startup
logic to `ProjectSPlugin`. These Tracks must not begin until this Runtime Kernel
Draft is reviewed and integrated.
