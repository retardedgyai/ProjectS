# Activation Track 2: Fire/Ice Training Dummy runtime

## Scope and non-activation guarantee

This Track publishes the `COMBAT_ELEMENTS` runtime module provider and its
ports. It does not edit `ProjectSPlugin`, `ProjectCommand`, `BetaRuntimeFactory`,
`plugin.yml`, `config.yml`, or any existing channel. The module is absent from
the central Runtime plan, every repository feature flag remains false, and no
listener, scheduler, gameplay adapter, or channel starts in this branch.

The future Integration Gate may instantiate
`CombatElementsRuntimeModuleProvider` with
`BukkitTrainingDummyElementBoundary`. Construction is inert. The cleanup task
is registered only by `start()` after the Runtime Kernel has admitted both
element flags, a non-OFF audience, Training Dummy scope, READ_ONLY mutation,
and both required infrastructure capabilities. Every hit callback rechecks the
fixed audience/allowlist, compatible-client requirement, allowed world, and
Training Dummy scope snapshots. `stop()` cancels that task and
clears every temporary profile, target state, hit key, participation event,
visual rate key, and diagnostic.

## Staging attack fixture

Only these direct PVE inputs are supported:

- `starter_sword`, Starter Sword normal attack
- `spin_slash`, Warrior SpinSlash direct skill
- a live entity positively identified by `TrainingDummyManager`
- a non-Player target

`NONE` is the default. It returns the original immutable `AttackMetadata`
object and multiplier `1.0`; it does not allocate element target state or call
the secondary damage boundary. A bounded, deduplicated participation event is
still exposed for the staging quest adapter.

`FIRE` copies the existing metadata, adds `FIRE`, records gauge input `25`, and
sets the effective FIRE attribute to `10`. Existing tags and element values are
not mutated. The reused `FireElementEngine` uses the NORMAL dummy threshold
`25`: one stack per hit, detonation at 10, seven consumed, three retained,
effective Fire x2.5, radius 4, center 100%, nearby 60%, and no spread. Center
and nearby dummy UUIDs are deduplicated and bounded to 64. Each valid dummy is
submitted to `DamageService` once. The request uses a resolved non-critical
offense snapshot, zero lifesteal, and no coefficient, so the staging secondary
damage cannot replay attack stats, MOD effects, or critical selection.

`ICE` copies metadata and adds `ICE`. Cold input is `25`, producing Cold I at
25, Cold II at 50, and Freeze at 100 through the reused `IceElementEngine`.
Freeze lasts at most three seconds. While frozen, direct Starter Sword and
SpinSlash outcomes return multiplier `1.08`; automatic, secondary, periodic,
DoT, and reflected origins are rejected before state mutation. The next valid
SpinSlash adds `SHATTER`, consumes the freeze generation once, combines the
125% shock and 50% ice core into one non-critical single-target DamageService
submission, retains 40 cold, and sets NORMAL refreeze immunity to three
seconds. A second callback with the same hit ID/target or the same freeze
generation cannot apply SHATTER again.

The adapter accepts the already-resolved legacy critical boolean as input and
contains no random or critical resolver. It never applies the direct legacy
hit. Integration must use the returned immutable metadata/multiplier while
preserving exactly one existing direct application; only the explicitly
returned secondary event is applied by this Track boundary.

## Bounded state and cleanup

- temporary player profiles: maximum 512; removed on logout and stop
- target registry: maximum 512 UUIDs; no `Entity`/`Player` retention
- Fire/Ice contributors: maximum 64 per target with physical/magical split
- hit dedup keys: maximum 2,048, ten-second timeout
- participation ring: maximum 256; pull limit maximum 128
- diagnostics: maximum 64, detail maximum 160 characters
- visual rate keys: maximum 128 and one update per target per 500 ms
- target inactivity timeout: five minutes

`targetRemoved(UUID)` is the common chunk-unload, dummy-removal, and entity
replacement cleanup callback. The scheduled cleanup advances Fire decay,
expires Freeze, removes inactive UUID state, and never touches Bukkit off the
main-thread scheduler supplied by the boundary.

## Published contracts

- `BetaRuntimeModuleProvider` (Track-local SPI)
- `BetaOperatorCommandContributor` (Track-local SPI)
- `ElementRuntimeSnapshotPort`
- `TrainingDummyParticipationPort`
- `TrainingDummyElementBoundary`
- `CombatElementsRuntimeModuleProvider`

The Track-local SPI avoids four parallel branches editing the same shared
file. The Integration Gate owns the single adaptation/registration point.

The immutable snapshot port exposes Fire stacks/fractional carry, cold stage,
Freeze/immunity timestamps, last update, and bounded contributor count. The
participation port exposes only immutable UUID/string events and cannot mutate
element state.

## Operator contribution

The unregistered contributor owns:

```text
/projects beta staging element none
/projects beta staging element fire
/projects beta staging element ice
/projects beta staging element status
/projects beta staging element reset-target <dummy-uuid>
```

It requires `projects.dev`. Profile selection requires a Player UUID, is not
saved, and is cleared on logout/disable. The Integration Gate may supply a
selected dummy UUID to `reset-target`; malformed UUIDs fail closed.

## Visual fallback

When the callback explicitly says a debug viewer is authorized and near, the
Bukkit boundary emits at most six particles plus one actionbar update to
`projects.dev` viewers in the same world and within 32 blocks. The per-target
rate limiter prevents tick spam. Client Beta UI and protocol channels are not
required and are not registered here. The immutable snapshot port is the only
Track 4 protocol input.

## Automated acceptance

`CombatElementsActivationRuntimeTest` is a pure JavaExec test connected to
`check` with assertions enabled. It covers NONE parity, immutable metadata,
exact FIRE/ICE/SHATTER tags, ten-hit detonation, 7/3 stack handling, fractional
decay, multiple contributors, center/nearby exactly once, recursion/dedup,
25/50/100 ice stages, +8% direct multiplier, automatic/secondary rejection,
one-shot SHATTER, 40% residual cold, three-second immunity, Player/non-dummy
rejection, logout/entity/timeout/stop cleanup, zero scheduler under disabled
flags, idempotent module lifecycle, permission checks, bounded ports, and the
Bukkit/pure API boundary.

## Integration and rollback

The Integration Gate must wire in this order: construct the Bukkit boundary,
construct the provider, add the provider's module to the single central plan,
adapt the command contributor, then adapt direct Starter Sword/SpinSlash
callbacks. It must first prove one legacy direct application, no critical
reroll, and no element secondary recursion. Keep all flags false until a
separate Activation Gate approves staging.

Rollback is: set `fire-system` and `ice-system` false, restart so the fixed
snapshot returns disabled, stop/close the module, and remove the central
provider/command/attack adaptations. There is no data migration or production
write to undo because all state is memory-only.

## Deferred risks

- Concrete direct-hit wiring is intentionally deferred to the Integration Gate.
- Fire/Ice production balance, bosses, normal mobs, PvP, DoT, and spread remain disconnected.
- Freeze natural expiry clears the staging cold target state; production cold
  retention after timeout remains a later owner decision.
- Bukkit/Paper runtime behavior is not exercised in this no-deployment Track.
