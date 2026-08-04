# Warrior SpinSlash shadow validation

## Scope

This phase observes only `spin_slash` hits. It does not make the pure result
authoritative and does not migrate any other Warrior, Painter, Scout, Boss, or
Mob Editor attack.

The established path remains:

```text
SpinSlashSkill
-> WarriorSkillSupport.damageTargets
-> one SkillHitSession for the cast
-> one DamageRequest per valid target
-> DamageService legacy calculation and application
```

## Attack metadata

`WarriorAttackMetadata.SPIN_SLASH` is an immutable catalog value with the exact
tag set `SKILL / MELEE / PHYSICAL` and `ElementProfile.EMPTY`. It intentionally
does not contain `NORMAL_ATTACK`, `PROJECTILE`, `MAGIC`, `SHATTER`, or any
element tag. Existing `WarriorSkillSupport` overloads still delegate with
`AttackMetadata.EMPTY`; only the SpinSlash call selects the new metadata value.

## Legacy and shadow order

When the subject controller is disabled, or the request does not satisfy the
route predicate, the dispatcher calls the original legacy application directly.
When enabled and supported, the order is:

```text
capture bounded runtime context
-> DamageService calculates the legacy DamageResult once
-> observer receives that exact result
-> snapshot resolver reuses legacyResult.critical()
-> DamageCalculationSnapshot.calculate() produces the pure result
-> generic comparator and the SpinSlash tracker observe both results
-> DamageService applies the original legacy result once
```

The shadow result is never passed to an application API. The dispatcher does
not catch and retry after a route has begun applying, because a retry at that
boundary could double damage.

## Route predicate

Observation requires all of the following:

- skill ID is `spin_slash`;
- type/kind/mode are `PHYSICAL / DIRECT_SKILL / PVE`;
- `areaDamage` is true;
- metadata is exactly `SKILL / MELEE / PHYSICAL` with empty elements;
- no offense snapshot is already supplied;
- attacker and target are valid;
- fixed damage and coefficient are finite.

A predicate mismatch never cancels the attack; it takes the normal legacy
path without recording a comparison.

## Critical, multiple targets, and area damage

The route never calls the critical resolver. It passes the boolean selected by
the legacy calculation to the snapshot resolver. The original `castId` is the
`SkillHitSession.sessionId`, so the existing attacker/cast critical cache
semantics remain in force across all targets of one SpinSlash.

Each target produces at most one comparison and exactly one legacy application.
Pure calculation emits no Bukkit event, so confirmed hit, Warrior spirit,
on-hit logic, Training Dummy recording, shield consumption, health changes,
and lifesteal are not duplicated. `areaDamage=true` remains on the request;
the derived area lifesteal efficiency is retained in the snapshot and the
lifesteal result is compared.

## Fail-open boundary

Context, snapshot, pure calculation, comparator, tracker, detail generation,
and debug logging failures are observational failures. They increment the
bounded session metric where possible and allow the already calculated legacy
result to continue to its one application. A context or predicate failure
occurs before legacy calculation and therefore falls through to one normal
application. A legacy calculation failure retains the existing DamageService
exception behavior and is not recalculated.

Detailed mismatch/failure logs are emitted only when debug logging is enabled.
They use `[DamageShadow:spin_slash]`, a 30-second signature rate limit, and a
64-entry bounded signature cache. No entity or unbounded UUID map is retained.

## Configuration and operator commands

The restart default is:

```yaml
combat:
  damage-foundation:
    warrior-spin-slash-shadow-enabled: false
```

Runtime commands require the existing `projects.dev` permission and do not save
the config:

```text
/projects damage-shadow spin-slash status
/projects damage-shadow spin-slash enable
/projects damage-shadow spin-slash disable
/projects damage-shadow spin-slash reset
/projects damage-shadow spin-slash summary
/projects damage-shadow spin-slash export
```

`starter-sword` is also accepted explicitly. The original short form, such as
`/projects damage-shadow enable`, still selects the starter-sword controller.

## Metrics and export

SpinSlash owns an independent controller, tracker, and session. Its metrics do
not change starter-sword totals. The bounded snapshot contains comparisons,
matches, mismatches, legacy/shadow failures, critical/non-critical,
shield/no-shield, damage type/kind/mode, target type, enhancement level,
maximum/average absolute error, maximum relative error, and at most 50 mismatch
details by default.

Exports use UTF-8 and atomic replacement where supported:

```text
plugins/ProjectS/debug/damage-shadow/spin-slash/
spin-slash-shadow-YYYYMMDD-HHmmss.yml
```

The filename stem accepts only a bounded lowercase slug. Same-second exports
receive a numeric suffix. The existing starter-sword directory and filename
stem remain unchanged.

## Manual validation

After a clean local check/build and test-server deployment:

1. Confirm damage route, starter shadow, and SpinSlash shadow start disabled.
2. Reset and enable only `damage-shadow spin-slash`.
3. Hit one valid target with SpinSlash at least 20 times.
4. Hit two or three valid targets with SpinSlash at least 10 casts.
5. When practical, include +0/enhanced weapons, normal/elite targets, shield,
   and naturally occurring criticals.
6. Disable, inspect summary, export, and confirm status is disabled.

Acceptance is `comparisons >= 30`, all comparisons matching, zero legacy and
shadow failures, maximum absolute and relative error within epsilon, no double
damage, no duplicated confirmed hits, no server errors, unchanged starter
metrics, and the authoritative starter route still disabled.

If no player is online, combat validation is recorded as not performed and the
SpinSlash controller is explicitly left disabled.

## Rollback and unmigrated skills

Immediate operational rollback is `/projects damage-shadow spin-slash disable`.
Restart also returns to the false config default. Code rollback removes the
SpinSlash dispatcher registration and returns `WarriorSkillSupport` to its
legacy applier; no persisted player, item, PDC, channel, or Mob schema data is
created by this phase.

Still unmigrated: `sweeping_slash`, `warrior_charge`, `execution_leap`,
`earth_shatter`, `armor_break`, `indomitable_spirit`, `end_war_strike`, all
Painter and Scout attacks, Boss attacks, and Mob Editor Mob attacks.
