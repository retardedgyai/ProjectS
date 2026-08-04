# Track F: party, quests, and rewards

- **Branch:** `codex/beta-track-f-party-quest-rewards`
- **Worktree:** `beta-track-f-party-quest-rewards`
- **Contract lineage SHA:** `8ba653a8dc3f604dc23a142cca5f5a88f51682a9`
- **Actual branch start SHA:** integration HEAD after Phase 0 (`REQUIRES_INTEGRATION_MERGE`).
- **PR base:** `integration/beta-full-build`

## Scope

Party invite/accept/leave/leader/reconnect/chat/HP summary, participation events, versioned quest state structures, endgame unlock marker, idempotent reward claims, and nearby-XP boundary once distance rules are approved.

## Out of scope

Quest content, XP distance/rates, Boss/drop reward quantities, economy/market, combat formula, item writer internals, client UI implementation.

## Ownership

Owned: new party/quest/reward packages and tests/docs. Do not edit player repository internals, combat application, item serialization, Mob Editor implementation, or client channels.

## Public interfaces and dependencies

Consume Track A player/repository, Track C/G immutable participation events, and Track D transaction delivery where applicable. Publish bounded `PartySnapshot`, `ParticipationRecord`, `QuestProgressCommand`, and idempotent `RewardClaimResult` using canonical IDs.

## Feature flags

`PARTY`, `QUESTS`, `REWARD_V2`; quests/rewards require persistence, party-aware credit requires party.

## Tests and manual verification

Invite expiry/rate limits, leader transfer, quit/reconnect, disband, cross-world/nearby boundaries, concurrent kills, duplicate participation, duplicate reward packet/retry/restart, full inventory, permission/chat isolation, bounded party state. Multiplayer Paper evidence is mandatory before enablement.

## Commit split

1. Party pure model. 2. party runtime/lifecycle. 3. quest state/participation. 4. reward idempotency. 5. final-boss gate adapter. 6. tests/docs/UI events.

## Merge prerequisites and rollback

A persistence merged; participation/reward contracts agreed with C/G/D; distance/content/reward values approved before activation. Rollback flags false, preserve committed quest/claim records, clear transient parties, and return to solo legacy flow.

## Completion report

Report start SHA, party lifecycle, persistence fields, participation/dedup keys, reward transaction, approved/unresolved values, commits/tests/CI/multiplayer evidence, flags, rollback, final status.

