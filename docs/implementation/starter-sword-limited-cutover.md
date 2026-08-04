# starter_sword limited authoritative cutover

## Scope

Phase 3 permits the pure snapshot result to become authoritative only for the
Phase 2.5 conditions validated on the Paper test server:

- item id `starter_sword`
- `NORMAL_ATTACK`, `PHYSICAL`, and `PVE`
- metadata is exactly `NORMAL_ATTACK / MELEE / PHYSICAL`, with no elements
- non-critical
- target absorption/shield is exactly zero
- no area damage, offense snapshot, or non-`normal_attack` skill id
- snapshot and pure calculation both complete successfully
- every result field is finite and non-negative

All other conditions use the precomputed legacy result. Critical and shielded
hits are never modified to force eligibility. Skills, DoT, reflected damage,
MAGICAL, TRUE, PVP, other items, and extra metadata are legacy-only.

## Single-application sequence

For an enabled route, `DamageService.calculate` runs once without applying
damage. This establishes the only critical decision and supplies a ready
legacy fallback. The policy is checked before snapshot work. If eligible, the
snapshot and authoritative result are completely built and validated before
the application boundary begins.

Exactly one of the following is then invoked:

```text
legacy result        -> existing DamageService application boundary
authoritative result -> existing DamageService application boundary
```

After one boundary begins, the router never applies the other route. Observer
and metrics failures are fail-open and cannot replace the selected result.

When shadow and authoritative modes are both enabled, the already calculated
legacy and authoritative objects are compared. The comparison does not run a
third damage calculation and its result is not applied.

## Configuration and commands

Both settings default to false and remain independent:

```yaml
combat:
  damage-foundation:
    starter-sword-authoritative-enabled: false
    starter-sword-shadow-enabled: false
```

Runtime controls require `projects.dev` (OP by default) and do not rewrite the
configuration file:

```text
/projects damage-route status
/projects damage-route enable
/projects damage-route disable
/projects damage-route summary
/projects damage-route reset
```

`disable` immediately returns subsequent attacks to complete legacy routing.
`summary` includes authoritative/fallback selection, each fallback reason,
new/legacy applied counts, completed single-application boundaries, new route
failures, and authoritative shadow matches/mismatches.

## Test-server procedure

1. Confirm `/projects damage-route status` reports disabled after startup.
2. Record baseline damage on a training dummy with the same unenhanced
   `starter_sword` used during Phase 2.5.
3. Run `/projects damage-route reset`, then `enable`.
4. Perform 50 non-critical, shieldless normal attacks against the dummy.
5. Confirm damage matches baseline, no hit is doubled, and no error appears.
6. Run `summary`. `newAuthoritativeCount` and `newRouteAppliedCount` should
   increase by the eligible attacks; ordinary fallback should remain zero.
7. Optionally enable Phase 2.5 shadow at the same time and confirm
   authoritative shadow mismatches remain zero.
8. Run `disable` and perform additional attacks. Confirm authoritative counts
   no longer increase and damage remains the legacy value.

Critical and shielded runtime inputs are not required for this phase. Unit
tests lock both to `LEGACY_CRITICAL` and `LEGACY_SHIELD`. If they occur during
manual testing, their fallback counters should increase and damage must remain
legacy.

Any mismatch, duplicate application, new-route failure, or damage change is a
stop condition. Run `disable` immediately and preserve the route and shadow
summaries for diagnosis. This phase does not migrate any other attack path.
