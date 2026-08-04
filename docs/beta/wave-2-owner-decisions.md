# Wave 2 owner decisions

Status: approved contract input for Track E and Track F foundation work.

This document fixes only the shared semantics needed for parallel development.
It does not approve production balance, feature activation, gameplay wiring,
item migration, deployment, or Tracks G/H. All Wave 2 feature flags remain
false.

## Shared rules

- Wave 2 operations use immutable requests, proposals, snapshots, and results.
- Persisted operation IDs use the canonical namespaced rules in
  `canonical-ids.md`; display names are never IDs.
- Policy revisions and request IDs are explicit and bounded. Retry-safe writes
  use the Track D transaction boundary.
- Pure domains contain no Bukkit `Player`, `ItemStack`, `World`, scheduler, or
  plugin lifecycle types.
- Production values that are not fixed below remain
  `REQUIRES_BALANCE_DATA`; tests may inject clearly labelled fixture policies.
- A proposal never authorizes mutation. Mutation may occur only after the
  transaction reaches commit through an approved writer.

## Track E: enhancement, Tier promotion, and repair

### Enhancement range and outcomes

The supported enhancement range is inclusive `0..30`. The pure domain can
represent `SUCCESS`, `NO_CHANGE`, `DOWNGRADE`, `BROKEN`, and `REJECTED`.

Success probability, cost, downgrade probability, and break probability are
not approved. A versioned immutable policy supplies them. Every probability is
finite and within `0..1`, the complete distribution is validated, every cost is
a non-negative `long`, and RNG is injected. The domain contains no production
policy or fixed probability.

The operation order is:

```text
validate
reserve item/material/currency
resolve proposed outcome
consume costs
produce replacement state
persist
commit
```

The existing item is not mutated before commit. `ENHANCEMENT_V2=false`
continues to use the current EnhancementManager/Listener behavior without an
automatic equipment-v1 migration.

### Tier promotion

- Only `T1 -> T2` and `T2 -> T3` are eligible.
- The source and destination belong to the same canonical equipment family.
- Exactly one source equipment item is reserved.
- Required materials and fees are versioned policy/recipe input.
- Source state is unchanged before transaction commit.
- Promotion beyond T3 is outside Beta.

Carry/reset behavior for MODs, quality, enhancement, and binding is not
approved. A promotion policy must explicitly decide every carried or reset
field. An incomplete policy is rejected; there is no implicit default.

### Broken repair

The repair target must have `broken=true`. A donor is eligible only when it:

- has the same Tier;
- has the same canonical equipment family;
- has enhancement level zero; and
- is not broken.

Donor quality, MOD configuration, crafter, and base-roll differences do not
affect eligibility.

The repair proposal preserves the target's instance UUID, family/base identity,
Tier, ILv, rarity, quality, base rolls, MOD slots and entries, crafter,
enhancement level, binding, trade policy, and a future extension boundary for
display name/engraving. Only `broken` changes to `false`. The donor is consumed
at commit. Fees and additional materials are unapproved policy input.

### Track E acceptance

- Legacy +0..+30, broken state, attack/speed bonus, PDC key/type, lore,
  attribute, and failure behavior are characterized without writes.
- Enhancement policy rejects invalid probabilities, costs, levels, and an
  incomplete distribution; fixture RNG is deterministic.
- Promotion rejects T3, family mismatch, invalid ILv/Tier, and incomplete carry
  policy.
- Repair proves complete target-field preservation and donor consumption.
- Duplicate requests, retry, every rollback stage, inventory-full, persistence
  failure, and commit-uncertain outcomes are isolated through Track D ports.
- `TIER_PROMOTION`, `ENHANCEMENT_V2`, and `REPAIR_V2` remain false.

## Track F: party, participation, quest, and rewards

### Party lifetime and membership

Party state is temporary runtime state. PlayerData does not persist party ID,
leader, invite, party chat state, reconnect timer, or HP summary.

- Party IDs are UUIDs.
- A player belongs to at most one party.
- Members are unique and the leader is always a member.
- A zero-member party disbands.
- When the leader leaves, leadership transfers to the remaining member with
  the oldest join sequence.
- Maximum party size, invite expiry/rate limit, and reconnect grace are injected
  policy values. Production values are unapproved.
- Lifecycle time uses an injected clock. Close/clear is bounded and idempotent.

An invite contains invite ID, party ID, inviter ID, invitee ID, created time,
expiry time, and status. Accept, decline, and expire transition exactly once to
a terminal result.

### Participation

The stable deduplication key contains encounter ID, player ID, participation
source ID, and contribution revision. Re-delivery cannot grant credit twice.
Contribution must be finite and non-negative. Damage, distance, and eligibility
thresholds are injected policy input. Encounter retention is bounded and a
closed encounter rejects new participation.

### Quest progress

Wave 2 defines no quest content. The foundation represents quest ID, quest
revision, state, counters, completion marker, claimed marker, immutable
commands/results, unknown-quest isolation, and stale-revision rejection. It
consumes Track A public snapshots/repository ports without editing Track A
internals.

### Reward delivery

A reward claim key contains player ID, reward source type, reward source
instance ID, reward definition ID, and reward revision. A repeated or concurrent
claim returns the persisted terminal result and never delivers twice. Delivery
uses a Track D transaction port and represents full inventory, persistence
failure, retry, and commit uncertainty. Concrete reward contents and quantities
are not approved.

Nearby XP exposes only a policy boundary for distance, world eligibility, and
share rate; there is no production policy. The endgame boundary records a
generic unlock through a port without inventing a concrete unlock ID.

### Track F acceptance

- Party create/invite/accept/decline/expire/leave/kick boundary, leader
  transfer, disband, reconnect, recipient/HP snapshots, limits, and close are
  deterministic and bounded.
- Participation rejects duplicates and stale revisions, supports multiple
  players, and closes encounters.
- Quest snapshots are immutable; unknown quests and stale revisions are
  isolated.
- Reward claims are idempotent across duplicate/concurrent/retry/restart paths
  and isolate inventory-full, persistence-failure, and commit-uncertain states.
- `PARTY`, `QUESTS`, and `REWARD_V2` remain false.

## Integration boundary

Track E owns `enhancement.v2`, `equipment.operation`, and `repair`. Track F owns
`party`, `quest`, `reward`, and `participation`. Neither Track edits
`ProjectSPlugin`, Track A-D internals, the other Track's packages, existing PDC
keys, or client/plugin channels. Both start from the same merge commit that
introduces this document and remain Draft PRs until a later integration gate.

