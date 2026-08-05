# Activation Track 4 — Party, staging content, and protocol runtime

## Scope

This Track publishes three unregistered `BetaRuntimeModule` implementations for
`PARTY_QUEST_REWARD`, `MOB_EDITOR_V2`, and `CLIENT_BETA_PROTOCOL`. It does not
edit `ProjectSPlugin`, `ProjectCommand`, `BetaRuntimeFactory`, `plugin.yml`, or
configuration defaults. Central registration belongs to the Activation Wave 1
integration gate.

## Staging content

- Encounter: `projects:staging/training-dummy`
- Quest: `projects:staging/training-dummy-10-hits`
- Counter: `projects:staging/training-dummy-hits`
- Reward definition: `projects:staging/training-dummy-reward`
- Reward: `projects:staging/test-token` x1

Training Dummy direct hits are converted to the existing participation and
quest domain types. Hit identity is bounded and deduplicated per encounter,
hit session, player, and target. Ten accepted hits complete the quest. Track 1
progress persistence is optional: when unavailable, quest state is explicitly
memory-only and is never written to production PlayerData. Reward delivery is
blocked until the Track 3 delivery port and claim store are available. The
claim store coordinates an exactly-once stable claim key.

## Party

The adapter retains UUIDs only and delegates create, invite, accept, leave,
leader transfer, reconnect grace, chat recipient isolation, and health summary
logic to the existing bounded `PartyService`. State is temporary and cleared
when the module stops.

## Mob Editor v2

The runtime boundary accepts only a repository rooted at
`plugins/ProjectS/beta-staging/mobs`. List, open, validate, and preview use
read admission. Save, rollback, and test spawn require the mutation policy to
be exactly `STAGING_WRITE`. Production apply and Mob v1 writes are absent.
Existing session, revision conflict, rollback-as-new-revision, test-spawn
bounds, and cleanup remain owned by `MobEditorV2Service`.

## Beta protocol

The protocol module registers exactly the four existing v1 channels while it
is RUNNING and unregisters them in reverse order on stop or partial startup
failure. Capability advertisement includes only producer modules whose state
is RUNNING. The existing session service and command router continue to enforce
capability negotiation, permission, producer feature state, session/content
revision, current state, transaction admission, authorization, rate limiting,
request identity, and terminal replay.

## Safety and activation

All repository feature flags remain false and activation audience remains OFF.
Consequently this branch registers zero modules and zero channels at runtime.
No production player, item, transaction, Mob, or boss data is written. The
operator contributor is published but unregistered. Activation requires the
dedicated integration gate to connect Track 1/2/3 ports, add central modules,
and retain fail-closed staging policy.
