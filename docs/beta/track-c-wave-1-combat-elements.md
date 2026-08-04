# Track C Wave 1: pure combat element foundation

Start SHA: `b491075ea98102e6266db46b7fe6f3589abfc3d2`.

This Wave 1 change is a pure Java foundation. It is not connected to
`DamageService`, starter sword, SpinSlash, Bukkit damage, particles, HUD,
Mob Editor, or player persistence. The existing `FIRE_SYSTEM`, `ICE_SYSTEM`,
and `LIGHTNING_SYSTEM` feature flags remain false.

## Track-owned inputs

Track A and Track B interfaces are not copied. The engines accept only small
immutable Track C inputs:

- `ElementAttackSchool`: physical or magical contribution lineage.
- `ElementTargetCategory`: normal, elite, miniboss, or boss.
- Fire and ice target profiles: caller-provided thresholds and stage inputs.
- Fire and ice hit records: abstract target ID, player UUID, accumulated value,
  elemental value, attack lineage, and monotonic event time.

Later adapters may translate stable player, equipment, or mob contracts into
these inputs without changing the state machines.

## Fire

`FireElementEngine` owns a bounded map of target states and a bounded immutable
contribution map per target. A caller supplies the category-specific stack
threshold; Wave 1 does not invent elite or miniboss thresholds.

The Wave 1 policy uses:

- maximum 10 stacks;
- detonation at 10 stacks;
- consume 7 and retain 3;
- at most one detonation event from one hit;
- effective fire value multiplied by 2.5;
- radius 4, center multiplier 1.0, nearby multiplier 0.6;
- no fire or burn spread;
- five-second hold after the last fire input;
- fractional burn decay of 25% of the target threshold each second;
- after the fraction is empty, one stack every two seconds.

Fractional accumulation is retained instead of rounded away. A detonation is
an immutable event only and never applies damage. Player and physical/magical
contributions are scaled proportionally when detonation or decay removes state.
Periodic burn damage and its coefficients are intentionally absent because
they remain undecided. PvP is absent.

Capacity exhaustion rejects a new target or a new contributor without
mutating existing state. `removeInactiveBefore` and `clear` provide explicit
bounded lifecycle cleanup.

## Ice

`IceElementEngine` stores one bounded shared cold gauge per target. Cold I and
Cold II boundaries and the target freeze threshold are required target-profile
inputs because their Beta values remain undecided. A frozen target receives a
1.08 multiplier only for eligible direct damage. Damage over time, periodic,
automatic secondary, and reflected origins are excluded.

An eligible direct `SHATTER` hit can shatter an already frozen target. The hit
that first fills the gauge can freeze, but cannot shatter that new freeze.
Clearing frozen state and tracking freeze generations ensures one shatter per
freeze.

The immutable shatter event contains:

- an impact component based on pre-critical direct damage at 125%;
- an ice-core component based on the frozen contribution core at 50%;
- the frozen direct multiplier applied once to both components;
- physical/magical component splits and player contribution shares;
- `criticalAllowed=false` and `singleTarget=true`.

After shatter, the gauge and contribution state retain 40% proportionally.
Refreeze immunity is normal 3 seconds, elite 4, miniboss 5, and boss 8. Cold
may accumulate to the cap during immunity. Merely reaching the immunity expiry
does not freeze a target; the next valid ice direct hit performs the freeze.

Freeze duration, movement/action slow values, cold decay, and boss break
behavior remain undecided and are not guessed here.

## Lightning

`LightningElementEngine` is a contract only. The Wave 1 implementation is
`DisabledLightningElementEngine`, which always returns
`LIGHTNING_SYSTEM_DISABLED` and emits no effect.

## Tests and integration

The JavaExec tests use assertions and are attached to `check`:

- `FireElementEngineTest`
- `IceElementEngineTest`
- `LightningElementEngineTest`

They cover fractional boundaries, detonation, proportional retention,
physical/magical splits, decay timing, stage boundaries, freeze/shatter order,
residual cold, immunity accumulation, no automatic freeze, exclusions,
finite-value validation, immutable snapshots, capacity rejection, and cleanup.

Integration order is after Track A and Track B contracts are stable. A later
small adapter PR may consume those interfaces behind false feature flags.
Rollback is removal of these pure types and their tests; no gameplay or saved
data migration is involved.
