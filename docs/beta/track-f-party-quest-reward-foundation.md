# Track F party, quest, participation, and reward foundation

## Baseline and scope

- Wave 2 base: `33558ce869e38c3921210740273c38feebb67352`.
- Branch: `codex/beta-track-f-party-quest-rewards`.
- Scope is limited to Bukkit-free party, participation, quest, reward, nearby-XP,
  and generic unlock boundaries.
- `ProjectSPlugin`, existing gameplay, Track A-D internals, client code, and
  configuration defaults are unchanged.

`PARTY`, `QUESTS`, and `REWARD_V2` remain disabled. No production policy,
balance value, quest content, reward payload, gameplay adapter, or persistence
writer is installed by this Track.

## Party lifecycle

`PartyService` owns bounded temporary runtime state using UUID-only
`PartyId`, immutable `PartyMember`, `PartySnapshot`, and `PartyInvite` values.
The injected `PartyPolicy` supplies party size, party count, invite-record,
invite-rate, expiry, and reconnect bounds. `Clock` controls all lifecycle time.

The service supports create, invite, accept, decline, expire, leave, kick,
disconnect/reconnect, leader transfer, disband, party-chat recipient snapshots,
and HP-summary inputs. A player belongs to at most one party. Invite transitions
are terminal once, invite identifiers reject conflicting reuse, leader transfer
uses the oldest join sequence, and join-sequence exhaustion rejects rather than
wrapping. `clear` and `close` are bounded and idempotent. No Bukkit entity is
retained and no party field is written to PlayerData.

## Participation

`ParticipationKey` contains encounter ID, player UUID, source ID, and
contribution revision. `ParticipationLedger` is bounded by encounter and record
counts, is synchronized for concurrent producers, rejects stale revisions,
deduplicates redelivery, and rejects additions to closed encounters.

`ParticipationEvent.ContributionSemantics` explicitly states `DELTA` or
`ABSOLUTE`. The ledger does not aggregate either interpretation. An injected
`ParticipationPolicy` decides eligibility and credited contribution, leaving
damage, distance, and contribution thresholds unapproved. Future combat and
gathering producers can publish through `ParticipationEventPort`.

## Quest progress

`QuestDefinitionRef`, `QuestProgressSnapshot`, `QuestProgressCommand`, and
`QuestProgressResult` model definition revision, progress revision, state,
counters, markers, completion, and claim markers as immutable values.
`QuestProgressService` publishes proposals only. It isolates unknown definitions
and rejects stale revisions.

Track A `QuestProgressState` does not contain definition revision, progress
revision, or explicit completion/claim fields. `PlayerProgressQuestView` is
therefore read-only and returns
`PERSISTENCE_MAPPING_REQUIRES_OWNER_DECISION`; it does not encode new fields in
old counter/marker maps. `QuestProgressPort` is the future durable adapter
boundary. No PlayerData writer is supplied in Wave 2.

## Reward claims and Track D delivery

`RewardClaimKey` contains player ID, source type, source instance UUID, reward
definition ID, and reward revision. `RewardClaimStore.executeExclusive` is the
durable atomic admission boundary. It coordinates multiple service instances,
executes at most one delivery for a key, and records a terminal result before
releasing ownership. `findTerminal` enables restart replay.

`RewardTransactionIdentity` deterministically maps a claim key to the Track D
transaction request UUID. `TransactionRewardDeliveryPort` rejects unstable
request mappings, runs an owner-supplied request/participant through
`TransactionEngine`, and maps full-inventory, persistence rollback, and
commit-uncertain results without embedding reward contents or quantities.

Whether full inventory or another delivery failure is terminal is deliberately
supplied by `RewardRetryPolicy`. Track F has no production retry default.
Concrete reward inputs/outputs come from the future approved transaction policy.

## Nearby XP and endgame unlock

`NearbyExperiencePolicy` owns world, distance, eligibility, and share-rate
decisions. `NearbyExperienceBoundary` only emits immutable proposals. Test
values are explicitly fixtures and do not define production balance.

`EndgameUnlockPort` accepts a generic `UnlockProposal` supplied with an unlock
ID. The foundation does not invent the final-boss unlock ID and does not write
Track A persistence.

## Verification

`TrackFPartyQuestRewardFoundationTest` is a pure Java `JavaExec` task attached
to `check` with assertions enabled. It covers:

- party create/invite/accept/decline/expiry, duplicate accept, membership and
  size bounds, deterministic leader transfer, disband, reconnect, rate limit,
  invite bound, chat/HP snapshots, and idempotent close;
- duplicate/stale/concurrent participation, multiple players, retention bounds,
  explicit contribution semantics, and encounter close;
- quest start/counter/marker/complete/claim, stale and unknown isolation,
  immutable collections, and the intentionally unresolved Track A mapping;
- duplicate and concurrent reward claims across service instances, durable
  restart replay, full inventory, persistence failure, commit uncertainty,
  policy-controlled retry, stable Track D transaction identity, and transaction
  replay;
- disabled feature flags and reflection checks preventing Bukkit types in public
  Track F APIs.

Final verification commands use `-PskipAutoStart`; this Track performs no JAR
deployment or server/client restart.

## Unresolved inputs and rollback

Unresolved inputs are production party sizes/times/rates, participation
thresholds and aggregation, quest content and PlayerData mapping, reward
contents/quantities/retry policy, nearby-XP distance/share rules, and the
endgame unlock ID. Multiplayer Paper evidence remains required before any flag
is enabled.

Rollback is to keep all three feature flags false, remove any future wiring,
clear transient party/participation state, preserve durable quest/claim records,
and continue the existing solo/legacy gameplay path.
