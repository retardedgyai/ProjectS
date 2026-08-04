# starter_sword runtime shadow validation

## Purpose and safety boundary

This procedure validates the existing `starter_sword` normal attack on a test
Paper server. It does not switch applied damage to the shadow result. The
legacy calculation is performed once, its critical decision is passed into a
separate immutable snapshot, and the independently calculated shadow result is
observed only. Health damage, shield consumption, life steal, Bukkit damage,
scheduler registration, PDC writes, and player-data writes remain exclusively
in the legacy application path.

The snapshot resolver runs after the legacy calculation but before legacy
damage is applied. At that point the calculation has not changed target health
or absorption. The pure shadow calculation reads no Bukkit object. A shadow
exception is counted and rate-limited in the server log, then legacy
application continues.

## Runtime controls

The commands require `projects.dev` (OP by default):

```text
/projects damage-shadow status
/projects damage-shadow enable
/projects damage-shadow disable
/projects damage-shadow reset
/projects damage-shadow summary
/projects damage-shadow export
```

`enable` starts a new in-memory session without changing `config.yml`.
`disable` stops collection and writes a compact summary to the server log.
`reset` clears only validation metrics. `export` writes bounded YAML under:

```text
plugins/ProjectS/debug/damage-shadow/
starter-sword-shadow-YYYYMMDD-HHmmss.yml
```

The saved default remains:

```yaml
combat:
  damage-foundation:
    starter-sword-shadow-enabled: false
```

At most 50 first-mismatch details are retained and exported. Per-hit match
logs are never emitted. Mismatch/error log signatures are rate-limited to once
per 30 seconds and the signature cache is capped at 64 entries. Session maps
are bounded by enums and enhancement levels 0 through 30; no entity reference
is retained.

## Test-server preparation

1. Confirm the server is the disposable ProjectS test server, not production.
2. Stop it gracefully and back up its current ProjectS plugin jar.
3. Do not remove worlds, plugin data, or config.
4. Install the tested jar while keeping shadow disabled in config.
5. Start the server and confirm normal startup without new errors.
6. As an OP, run `/projects damage-shadow status`, then `enable`.
7. Confirm the response explicitly says applied damage remains legacy.

Codex does not claim completion of player-driven hit testing. The following
matrix requires a human operator in the test client.

## Manual validation matrix

Record at least 200 comparisons; 500 or more is preferred.

### Basic

- Use an unenhanced `starter_sword` against a training dummy for at least 50 hits.
- Verify observed health loss is not doubled.
- Run `summary` and confirm the comparison count increased by the hit count.

### Enhancement levels

Perform at least 20 hits at each level:

- +0
- +5
- +15
- +30

Confirm the export contains a count for every tested enhancement level.

### Critical decisions

- Include inputs producing non-critical attacks.
- Include inputs producing critical attacks.
- Accumulate at least 20 critical results in total.
- Confirm `critical + non-critical == comparisons` in the summary/export.

The comparison reuses the one legacy critical decision. It must not perform a
second random roll.

### Defense

Perform at least 20 hits for each available target profile:

- zero defense
- low defense
- high defense

### Shield

Perform at least 20 hits with no shield and 20 with a shield. Confirm shield is
consumed once and the exported shield/no-shield counts are correct.

### Target types

Test each safely available target:

- training dummy
- normal monster
- elite
- boss, only when the existing development environment provides a safe boss target

### Additional checks

- A broken weapon remains unable to attack according to existing behavior.
- With shadow disabled, additional attacks do not increase session counts.
- With shadow enabled, damage, shield consumption, and life steal occur once.
- After `disable`, additional attacks do not increase counts.
- `export` succeeds and the YAML contains session metrics and bounded mismatch details.
- No UUID or validation error is shown to normal players.

## Completion and cutover gate

At the end, run `disable`, `summary`, and `export`, then stop the test server
gracefully and preserve the export. A future cutover may be considered only if:

```text
mismatches = 0
shadow failures = 0
double applications = 0
no increase in existing server errors
maximum error <= configured comparator epsilon
```

Any meaningful mismatch or runtime exception blocks switching the applied
damage path. Phase 2.5 itself never performs that switch.
