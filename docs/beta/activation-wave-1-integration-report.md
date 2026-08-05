# Beta Activation Wave 1 integration report

## Integration lineage

- Activation base: `2a991aa6ba3afc0b59ebd1f0874c00b195ec84cd`
- Track 1 source: `fc228810265941b3567fd71966c8de926fe94901`
- Track 1 integration: `da4aecd28c8a18edfd0dd9241a82ddd86021d903`
- Track 2 source: `49c34a5862f691390da19e5a36b034e6afba643d`
- Track 2 follow-up merge: `6c485da3f8cab302cd36427d5e3017247751e7b1`
- Track 2 integration: `f2e0191dd063d1f916c814c31e94c61f92e74b57`
- Track 3 source: `eaeffc92585cec0ebca6d6cf789b048922d1b4af`
- Track 3 follow-up merge: `87d27557256e5414a3f72dcc01fc2e3a4f402932`
- Track 3 integration: `a0a8914b78e52c5bb888979afcdfb51235615fbe`
- Track 4 Server source: `b10a5fa124dd91aa430c864e1f600eb43b0e117d`
- Track 4 follow-up merge: `66b11786d510cdfdd0e6783fffe7e58c1277359e`
- Track 4 Server integration: `5fc8f9dc35d418005ccec7d2d0884e072d757ba8`

Only `build.gradle.kts` conflicted. All pre-existing and Track 1–4 JavaExec
tasks were retained, use `-ea`, and remain attached to `check`.

## Runtime plan and defaults

The immutable central plan contains all eight `BetaRuntimeModuleId` values.
Repository defaults remain audience `OFF`, target scope `TRAINING_DUMMY_ONLY`,
mutation policy `READ_ONLY`, and all feature flags false. Consequently the
plan reports eight registered modules and zero running modules without
registering a Beta listener, channel, or scheduler. Runtime mutation commands
remain unavailable; activation requires an approved configuration and restart.

The central registration-only descriptors fail closed if an enabled staging
configuration is supplied before concrete provider infrastructure is approved.
They do not substitute memory or production PlayerData fallbacks. Concrete
Track providers and their immutable ports remain the activation boundary.

## Confirmed-hit observation

Starter sword normal attacks and SpinSlash now call one narrow observer only
after the existing application boundary reports an attempted hit. Existing
constructors delegate to a no-op observer for compatibility. The Track 2
adapter immediately exits unless `COMBAT_ELEMENTS` is `RUNNING`, the target is
a non-player Training Dummy, the hit is a supported direct hit, and the request
is not a secondary/offense-snapshot request. It reuses the existing calculation
and critical decision, never calculates or applies the direct hit, and uses a
stable per-hit identity so Track 2 deduplication remains authoritative.

## Fire display protocol

`ElementSnapshotProtocolAdapter` maps immutable Track 2 snapshots into the
existing `projects:elements` display model. It checks both module states,
capability v1 negotiation, target visibility, expiry, and strictly newer state
revision. The snapshot includes target network ID, state revision, stacks,
fractional gauge and progress, threshold, decay state and delay, detonation
pulse revision, and expiry. Viewer/target revision retention is bounded and
clearable. Disabled modules send zero packets.

No Track 2 code calls `setFireTicks`, `setVisualFire`, or vanilla fire damage.
Normal Fire state has no repeating particle; only the detonation pulse may emit
the short staging effect. The compatible-client fallback remains a rate-limited
`projects.dev` ActionBar and is display-only.

## Live capability handshake preflight

The capability v1 runtime previously had encoding, session, ACK, and state
gating support but no production advertisement trigger. The live Server path
now registers a bounded lifecycle publisher with the protocol module. A
`projects:beta_caps_v1` channel-registration event is the primary trigger;
already-online players are checked on the next tick after module start. Join
resets stale connection state and waits for channel registration. Quit, kick,
reconnect, stop, and close clear pending advertisements, sessions, and viewer
revision state.

Advertisement is admitted only while the protocol module is `RUNNING`,
`CLIENT_BETA_UI` is enabled, audience is `ALLOWLIST` or `GLOBAL`, the player is
in the selected audience and world, and the Client is listening on the
advertisement channel. `require-compatible-client` is deliberately not checked
before advertising because the ACK establishes compatibility. The current
composition advertises only `ELEMENTS`; Party and mob state remain absent.

A duplicate registration before ACK resends the exact original packet and
does not allocate a session. A duplicate after ACK sends nothing. ACKs are
decoded by the existing codec and accepted by the existing bounded, expiring
session service. Expired, old, malformed, or mismatched ACKs fail closed and do
not kick the player. Read-only health output exposes only bounded aggregate
send/resend/ACK/session counters and the last result, never player or packet
identifiers. Full lifecycle and staging instructions are in
`docs/beta/activation/capability-handshake-preflight.md`.

The branch now follows integration SHA
`865dc702b10a3990e5f4e6d4ac97f71efa48f11b`. A single five-second maintenance
task owns TTL recovery. It replaces expired pending or acknowledged sessions
with one newly admitted advertisement per TTL, using a new session ID,
revision, and expiry. Old ACKs fail closed, and Elements packets remain zero
until the new ACK succeeds. Repeated diagnostics are pure reads and cannot
expire sessions or mutate handshake state.

Join, quit, kick, reconnect, module stop, and plugin close now reach the actual
Track 2 provider through a UUID-only lifecycle port. They remove temporary
per-player Fire/Ice profiles while preserving shared target stacks,
fractional values, cold, contributions, and state revisions. Default audience
OFF and all-false flags still register zero listeners, channels, schedulers,
or packets and perform zero production writes.

## Durable transaction recovery

Recovery records live only under `beta-staging/transactions`. Each stable
request journal stores the transaction stage, player, operation, input
identities/revisions, reservation state, proposed output identity, terminal
outcome, and timestamp. Writes use a bounded UTF-8 file, forced temporary file,
atomic replace where supported, and directory force where supported. Paths are
normalized; symlinks, traversal, oversized entries, excess files, and corrupt
or unknown records are rejected or quarantined.

Restart policy is deliberately non-executing:

- `VALIDATE`/`RESERVE`: safe discard classification.
- `RESERVED`/`CONSUMED`/`PRODUCED`/`PERSISTED`: `RECOVERY_REQUIRED` and blocked.
- `COMMITTED`/`ROLLED_BACK`: terminal replay classification.
- `COMMIT_UNCERTAIN`: quarantine and block the request and player/operation key.

The recovery service has no transaction execution port, so it cannot reroll,
reconsume material, regenerate output, or redeliver a reward. Audit export can
write the recovery journal in addition to the existing evidence files.

## Operator surface and lifecycle

The bounded registry exposes the requested player, equipment, element,
economy, party, quest, reward, and mob subjects. With repository defaults all
return: `Beta module is disabled. Restart with approved staging policy.` No
enable, reload, or set command exists. Responses and contributor count are
bounded.

Shutdown closes the central runtime before the Track 2 observation provider and
then continues through existing managers. Each close is exception-isolated by
the existing `ShutdownSequence`.

## Validation and rollback

`BetaActivationWave1IntegratedTest` verifies central registration/defaults,
bounded disabled commands, committed and rolled-back replay classification,
incomplete and uncertain recovery behavior, idempotent restart classification,
Fire snapshot mapping/revision gating, disabled packet behavior, the two
single-application call sites, and the vanilla Fire API ban. All previous test
tasks remain enabled. `BetaCapabilityHandshakePreflightTest` additionally
verifies default-zero behavior, live admission and ACK gating, duplicate and
reconnect behavior, expiry and stale-ACK rejection, bounded retention,
stop/failure cleanup, producer scoping, and the absence of retained Bukkit
`Player` references.

The handshake test also covers three consecutive TTL renewals, old-ACK
rejection, ACK-before-state gating, no-spam behavior, diagnostics purity, the
single maintenance scheduler lifecycle, production profile cleanup, and
preservation of shared target state.

Rollback is the merge commit revert while flags remain false. No production
data migration, staging policy change, deployment, Paper launch, or Client
launch is part of this gate.

## Remaining activation prerequisites

- Replace the fail-closed central descriptors with approved concrete provider
  instances in the staging configuration gate.
- Exercise the capability advertisement and ACK with a matching real Client in
  the later manual staging phase; this preflight performs no Client launch.
- Exercise critical, shield, enhanced equipment, ordinary mob, elite, and boss
  paths in a later manual staging phase.
- Resolve uncertain transaction records through an explicit audited operator
  workflow; automatic retry is intentionally absent.
