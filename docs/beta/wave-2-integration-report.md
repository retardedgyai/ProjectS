# Wave 2 Integration Report

## Scope and integration sequence

Wave 2 integrates the disabled Track E enhancement, tier-promotion, and repair
foundations and the disabled Track F party, participation, quest, and reward
foundations into `integration/beta-full-build`. It adds no gameplay wiring,
production balance, production item writer, deployment, or Track G/H work.

The Wave 2 owner-decision merge and integration sequence are:

| Milestone | Commit |
|---|---|
| Wave 2 owner-decision merge / integration start | `33558ce869e38c3921210740273c38feebb67352` |
| Track E original head | `a09ed6081d883b4bc138985bbe94b9923e785e03` |
| Track E integration merge | `cb4c5ba7a89f3e3cd2f43926c6d690cca8b411ea` |
| Track F original head | `928ffa241b4a0aedb633fb2b65a600eefa072305` |
| Track F follow-up merge of Track E | `ffe153f234da8247936cc1ab3b45d70bc5732ae3` |
| Track F integration merge / validation base | `c4cb839b1b4abac9d6d75ede83ff656972267895` |

Track E required no follow-up merge because it was integrated first from the
owner-decision base. Track F then merged the updated integration branch before
its PR was released from Draft and merged.

## Conflict resolution and test graph

The only Track F follow-up conflict was `build.gradle.kts`. It was resolved
additively. Existing combat, starter-sword, SpinSlash, Mob Editor, shutdown,
Phase 0, Wave 1 integration, and Track A–D tasks were retained. Track E and
Track F tasks were each retained once, every JavaExec task kept `-ea`, and all
tasks remained dependencies of `check`. No production implementation was
changed to resolve the conflict.

`Wave2IntegratedFoundationTest` is the forty-third JavaExec verification task.
It runs on the same test/runtime classpath as the focused A–F suites and is
connected to `check` through `testClasses` with assertions enabled.

## Public interfaces and Track D sharing

Track E exposes immutable attempt, policy, transition, proposal, operation-plan,
promotion, and repair contracts. Resolution is deferred until a Track D
reservation exists. The public equipment writer, resource, and journal ports
remain infrastructure boundaries; no production implementation is introduced.

Track F exposes bounded in-memory party and participation services, immutable
quest commands/proposals, durable reward-claim admission, and a reward delivery
adapter over the Track D transaction interface. Temporary party state is not a
field in `PlayerProgressSnapshot`; the Track A quest view remains an explicit
mapping boundary rather than an invented persistence format.

The integrated test runs a Track E `EquipmentOperationParticipant` and a Track F
`TransactionRewardDeliveryPort` through one `TransactionEngine` and shared fake
infrastructure. It verifies distinct request IDs and reservation tokens,
request-keyed terminal results, isolated rollback, terminal replay without
re-execution, and terminal `COMMIT_UNCERTAIN` without automatic retry. An E
resolution failure does not roll back an F reward, and an F persistence failure
does not alter or rerun the committed E operation.

## Integrated behavior

- Enhancement starts from a +0 immutable equipment item, reserves before its
  single probability resolution, proposes +1 for the fixture policy, writes
  once, and replays the same terminal result without rerolling or rewriting.
- Tier promotion retains the focused Track E checks for T1→T2 and T2→T3,
  rejects promotion above T3 and family mismatch, and rejects an incomplete
  carry/reset policy.
- Repair retains target UUID, quality, MOD slots, crafter identity,
  enhancement, binding, trade policy, and extension data. Only `broken` changes
  to false; the donor input is consumed once in the proposal, and neither input
  object is mutated.
- Party creation, invite/accept, oldest-join leader transfer, final-member
  disband, bounded temporary state, reconnect, and cleanup remain covered.
- Participation credits a duplicate event once, rejects stale revisions, and
  rejects events after encounter closure.
- Quest progression covers start, increment, completion, claimed marker, stale
  revision rejection, unknown-definition isolation, and revision exhaustion.
- Reward claims provide durable terminal replay, stable Track D request UUIDs,
  once-only delivery under concurrent admission, and fail-closed unknown commit
  handling.
- One player UUID can be represented by the Track A snapshot, Track D request,
  Track E attempt, Track F party member, participation record, and reward key
  without those aggregates owning each other's internal models.

## Schemas and feature flags

| Schema | Version/status |
|---|---|
| `player-data` | `1` |
| `equipment-item` | `1` |
| `mod-definition` | `1` |
| `recipe-definition` | `1` |
| `mob-definition` | `1` |
| `client-protocol` | unresolved; owner decision required |

All Wave 1–2 flags remain false by default:

- `PLAYER_PERSISTENCE`
- `EQUIPMENT_V2`
- `MOD_SYSTEM`
- `FIRE_SYSTEM`
- `ICE_SYSTEM`
- `LIGHTNING_SYSTEM`
- `GATHERING`
- `REFINING`
- `CRAFTING`
- `TIER_PROMOTION`
- `ENHANCEMENT_V2`
- `REPAIR_V2`
- `PARTY`
- `QUESTS`
- `REWARD_V2`

Representative pure A–F public APIs are reflectively checked for Bukkit
`Player`, `ItemStack`, `World`, and `Location` leakage. Bukkit adapters are
explicitly outside that pure-type set. The `ProjectSPlugin` class contains no
field, getter, constructor reference, or startup reference to the E/F services.
Its existing legacy `EnhancementManager` wiring remains present.

## Validation, unresolved balance, and rollback

The validation branch is `codex/beta-wave2-integration-validation`, based on
`c4cb839b1b4abac9d6d75ede83ff656972267895`. Final local `clean check`,
`clean build`, default-true searches, GitHub CI, focused review, and validation
merge SHA are recorded in the validation PR and final integration report.

Enhancement probabilities/costs, tier-promotion carry/reset decisions and
costs, repair costs, party limits, participation eligibility, quest catalog,
reward contents, nearby experience, and endgame unlock IDs remain test-fixture
policies or unresolved owner/balance decisions. This gate does not approve any
of them for production.

Rollback the Wave 2 validation merge first, then Track F and Track E integration
merges in reverse order if isolation is required. Source branches remain
retained. Wave 3 can start only after the validation PR is merged into
`integration/beta-full-build`, all checks are green, flags remain disabled, and
the final integrated SHA is fixed. No Wave 3 track begins as part of this gate.
