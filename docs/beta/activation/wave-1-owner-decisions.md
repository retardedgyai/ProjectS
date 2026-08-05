# Beta Activation Wave 1 owner decisions

## Fixed baseline and non-activation rule

- Activation Kernel integration SHA: `3c385eeefec9ec2d1647f37d53f2ebc83cd5a9c3`
- Compatible Client SHA: `27f2c5e4b535dee19c860b711cac9662606540ff`
- Repository defaults remain audience `OFF`, target
  `TRAINING_DUMMY_ONLY`, mutation `READ_ONLY`, and every feature flag false.
- Wave 1 Track PRs publish providers and ports only. They do not register a
  module in the central Runtime plan, enable gameplay, deploy, or start a
  Server/Client.

Every Track publishes `BetaRuntimeModuleProvider`,
`BetaOperatorCommandContributor`, and its owned display/command ports. The
Integration Gate is the only future owner allowed to edit `ProjectSPlugin`,
`ProjectCommand`, the central `BetaRuntimeFactory` module list, `plugin.yml`,
config defaults, or existing channel registration.

## Staging isolation

All staged writes are confined beneath:

```text
plugins/ProjectS/beta-staging/players
plugins/ProjectS/beta-staging/transactions
plugins/ProjectS/beta-staging/mobs
plugins/ProjectS/beta-staging/exports
```

No Track writes to production player storage, the Mob v1 repository, or
production item data. READ_ONLY means no files, migrations, item mutation,
resource consumption, claims, rewards, or Mob saves.

## Track 1 — persistence and equipment observation

- Player persistence performs immutable legacy snapshot projection, staging
  load, comparison, diagnostics, revision checks, reconnect/drain behavior,
  and writes only under `beta-staging/players` at `STAGING_WRITE`.
- It never replaces `PlayerManager`, auto-migrates data, or blocks legacy login.
- Equipment scanning is read-only: legacy projection, `EquipmentItemV1`
  validation, unknown-MOD isolation, and display snapshots only.
- Inventory/PDC bytes are unchanged, instance UUIDs are never generated, and
  projected stats are not applied to combat.

## Track 2 — Training Dummy element fixtures

The operator profile is temporary per player, bounded, and cleared on logout
and disable. Profiles are `NONE`, `FIRE`, and `ICE`; default is `NONE`.

FIRE applies only to Starter Sword normal attacks and SpinSlash against a
Training Dummy: add `FIRE`, fire input 25, effective fire 10. Threshold 25
produces one stack per hit; stack 10 detonates, consumes 7, leaves 3, deals
effective fire ×2.5 at radius 4 with center 100% and nearby 60%. Detonation is
non-critical secondary/automatic damage, has no MOD replay/spread/recursion,
uses `DamageService` once per affected dummy, and never targets a player.

ICE applies input 25 with Cold I/II/Freeze at 25/50/100. Freeze lasts 3 seconds
and amplifies direct damage by 8%; automatic, DoT, and secondary damage are
excluded. The next valid SpinSlash explicitly composes
`SKILL/MELEE/PHYSICAL/ICE/SHATTER`. SHATTER is single-target, non-critical,
one-shot, shock 125%, ice core 50%, and leaves 40% cold. Training Dummy
refreeze immunity is 3 seconds.

These are staging fixtures, not production balance.

## Track 3 — staging economy catalog and operations

Canonical fixture IDs are registered in `staging-fixture-ids.md`. Recipes are:

```text
2 iron-ore -> 1 iron-ingot
3 iron-ingot -> 1 T1 test-blade
1 T1 test-blade + 2 iron-ingot -> 1 T2 test-blade
```

Enhancement provides an operator-selected, single-use outcome: `SUCCESS`,
`NO_CHANGE`, `DOWNGRADE`, or `BROKEN`; default is `NO_CHANGE`. It is consumed
once, never rerolled on retry, cleared at logout/disable, and denied under a
production mutation policy. Repair consumes an unbroken +0 donor of the same
Tier and family to repair the original broken T1/T2 test blade.

Every operation rechecks `ALLOWLIST`, `STAGING_WRITE`, allowed world, and
`projects.dev`. Preview does not generate UUIDs. Commit generates an instance
UUID once and uses the Track D transaction boundary for reservation, revision,
commit, rollback, and uncertain outcomes.

## Track 4 — temporary party, staging content, Mob v2, and protocol

- Party state is bounded and temporary; it is not written to PlayerData.
- The staged encounter `projects:staging/training-dummy` emits one deduplicated
  participation credit per valid direct hit.
- Quest `projects:staging/training-dummy-10-hits` completes at 10 valid hits.
- Reward `projects:staging/training-dummy-reward` delivers one
  `projects:staging/test-token` through Track D/Track 3 ports with a stable
  exactly-once claim key. Missing producers are `BLOCKED` or explicitly
  memory-only; production PlayerData is never an implicit fallback.
- Mob Editor v2 uses `beta-staging/mobs`; READ_ONLY permits preview/validate,
  STAGING_WRITE permits staged save, and production apply remains prohibited.
- The four Beta protocol channels may be registered only by a RUNNING protocol
  module and are all unregistered on stop. Advertised capabilities require a
  RUNNING producer. Server policy, permission, revision, request ID, current
  state, and transaction admission remain authoritative.
- Client UI remains hidden without Server advertisement, clears sessions on
  disconnect, rejects old sessions and duplicate terminal results, and never
  becomes an authority.

## Cross-Track ports

- Track 1: `StagingPlayerProgressPort`, `EquipmentInspectionPort`
- Track 2: `ElementRuntimeSnapshotPort`, `TrainingDummyParticipationPort`
- Track 3: `StagingItemDeliveryPort`, `StagingEconomyOperationPort`
- Track 4 consumes these contracts without copying their implementations.

Parallel Track builds use fake/missing-port behavior. The Integration Gate
connects real ports once, after focused review and CI for every Draft PR.
