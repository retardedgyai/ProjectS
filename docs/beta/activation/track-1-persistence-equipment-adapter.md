# Activation Track 1 — persistence and equipment adapters

## Scope and ownership

This Track publishes two disabled-by-default runtime modules without adding
either module to the central `BetaRuntime` plan:

- `PLAYER_PERSISTENCE`: legacy progress shadow observation and isolated
  staging saves.
- `EQUIPMENT`: read-only inventory/equipped-item projection and validation.

The Track-local `BetaRuntimeModuleProvider` and
`BetaOperatorCommandContributor` are discovery contracts for the later
Integration Gate. This change does not edit `ProjectSPlugin`, `ProjectCommand`,
`BetaRuntimeFactory`, `plugin.yml`, `config.yml`, or any existing channel.

## Player persistence boundary

The Bukkit listener is inert until a future Gate supplies a registrar and
starts the module. A callback extracts UUID, world name, compatible-client
state, and an immutable `PlayerProgressSnapshot`; no `Player` or `Entity`
reference is retained.

On join, the adapter reads only
`plugins/ProjectS/beta-staging/players/<uuid>.yml`. Missing and malformed
records produce bounded observations and never block legacy login. A
malformed READ_ONLY record is diagnosed in memory: it is not copied to a
quarantine directory because that would itself be a write.

The shadow comparator reports bounded field names. It does not replace or
mutate `PlayerManager`. Sessions and observations are bounded to 512 UUIDs by
default. Reconnect re-establishes one writer session and revision tracking
advances from the highest observed revision.

### Mutation policy

- `READ_ONLY`: comparison/diagnostic only; no directory, file, migration, or
  PlayerManager mutation.
- `STAGING_WRITE`: logout and disable drain may write only beneath
  `beta-staging/players`. The file store rejects every other path at
  construction.
- `PRODUCTION_WRITE`: adds no production behavior; the Track still uses the
  staging-only store.

Audience allowlist and allowed-world checks run on join and again before a
save. Save conflicts and stale revisions are reported rather than overwritten.
Stop unregisters the listener first, drains active immutable sessions, closes
the repository, and is idempotent.

## Equipment inspection boundary

`EquipmentInspectionPort` accepts callback-scoped scan entries and publishes
an immutable display snapshot. The Bukkit reader clones each item before
reading its legacy PDC and serialized bytes. The service:

- reuses `LegacyItemCompatibilityReader`;
- creates a conservative `EquipmentItemV1` read projection;
- runs `EquipmentValidation`;
- keeps projected `instanceId` empty and never calls a UUID supplier;
- isolates unknown MOD identifiers with `effectEnabled=false`;
- fingerprints source bytes to support no-write verification;
- never applies projected stats to combat.

The latest display snapshots are bounded to 512 UUIDs and cleared on stop.
The callback-scoped legacy source and Bukkit `ItemStack` are never retained.

## Operator contribution

The contributor advertises these read-only routes for later central routing:

```text
/projects beta staging player status
/projects beta staging player snapshot
/projects beta staging equipment inspect
```

Every route requires `projects.dev`, an allowlisted player subject, and an
allowed world. There is no enable, disable, migration, write, stat-apply, or
policy command.

## Validation and rollback

`Track1ActivationFoundationTest` is a pure JavaExec task connected to `check`
with assertions enabled. It covers READ_ONLY file count zero, staging path
isolation, join/quit/reconnect, stale save, disable drain, malformed input,
comparison, allowlist/world denial, byte/PDC immutability, unknown MODs, UUID
absence, module lifecycle, failed registration cleanup, operator permissions,
Player-reference absence, and central-registration/config invariants.

Rollback is removal of the future central provider registration. In this PR
there is no registration to remove, no production data to migrate back, no
channel to unregister, and no deployed artifact. Existing feature flags remain
false.

## Deferred risks

- Paper event ordering and real inventory serialization require the future
  Integration Gate and staging-server validation.
- The legacy progress projection currently covers fields available from the
  existing `PlayerData`; future owners must explicitly map new authoritative
  fields before declaring shadow parity.
- Legacy item IDs without a known slot convention use a conservative weapon
  projection. This is display/validation data only and cannot affect stats.
