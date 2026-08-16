# Activation Track 3 staging economy runtime

## Status and scope

This Track publishes unregistered runtime adapters for the Activation Wave 1
staging economy. It starts at
`2a991aa6ba3afc0b59ebd1f0874c00b195ec84cd` and does not edit the central
Runtime plan, `ProjectSPlugin`, `ProjectCommand`, config defaults, `plugin.yml`,
or a plugin channel. All repository feature flags remain false.

The two module descriptors are `GATHERING_CRAFTING` and
`ENHANCEMENT_REPAIR`. A Track-local `BetaRuntimeModuleProvider` exposes them,
and a Track-local `BetaOperatorCommandContributor` describes the operator
commands. The later Integration Gate owns all central adaptation and
registration.

## Staging catalog

Only these owner-approved fixture item IDs are accepted:

- `projects:staging/iron-ore`
- `projects:staging/iron-ingot`
- `projects:staging/test-blade`
- `projects:staging/test-blade-t2`
- `projects:staging/test-token`

The recipes are exactly 2 ore to 1 ingot, 3 ingots to one T1 blade, and one
unmodified T1 blade plus 2 ingots to one T2 blade. Internal transaction and
policy IDs are staging-only and do not register production content.

## Admission and lifecycle

Every operation rechecks all four gates:

1. activation audience is exactly `ALLOWLIST` and contains the player UUID;
2. mutation policy is exactly `STAGING_WRITE`;
3. the current world is explicitly allowed; and
4. the caller has `projects.dev`.

`PRODUCTION_WRITE`, `READ_ONLY`, audience `OFF`/`GLOBAL`, an empty world list,
or a disabled module fails closed. Modules also require their complete feature
set and the staging inventory/journal infrastructure descriptors. Prepare,
start, stop, provider close, inventory close, and service close are idempotent.
No listener, channel, or scheduler is registered. Runtime state retains UUIDs
and immutable values, never Bukkit `Player` or `Entity` objects.

## Item writer and Bukkit edge

`StagingEquipmentWriter` implements Track B's `EquipmentWriteBoundary`.
Preview construction carries no instance UUID. A new blade receives an UUID
inside commit only; a replacement operation preserves the existing instance
UUID. Request replay returns its terminal transaction and does not allocate a
second UUID.

The deterministic bounded binary codec covers every `EquipmentItemV1` field,
including opaque unknown MOD payloads. Unknown MOD entries remain disabled.
The Bukkit edge creates an `IRON_SWORD` explicitly named `[STAGING]`, writes
only `beta_staging_*` PDC keys, and never writes the legacy `item_id`,
`enhancement_level`, `weapon_broken`, attack-bonus, or attack-speed keys.
Payload size is capped at 32 KiB. Lore is derived deterministically and is not
parsed as data.

`BukkitStagingInventoryBridge` resolves a player only during a main-thread
callback, clones snapshots, performs compare-before-replace, and restores the
old slot if replacement fails. It is published but not connected in this
Track.

## Transaction and operation ordering

Track D's `TransactionEngine` remains authoritative:

```text
validate -> reserve -> consume -> produce -> persist -> commit
```

Track E's `EquipmentOperationParticipant`, promotion service, enhancement
resolver, repair service, mutation proposal, resource plan, and journal ports
are reused. None of their internals are copied or edited.

Enhancement resolves the operator's one-shot fixture only after the inventory
reservation succeeds. The resolved immutable proposal is journaled before
consume and reused by retry; critical transaction failures never draw again.
The override defaults to `NO_CHANGE`, is removed on its first reserved use,
and is cleared on logout, module stop, or service close. +30 and broken sources
are rejected before resolution.

Promotion accepts only the owner-approved T1-to-T2 staging recipe. It requires
an unbroken +0 source with no occupied MOD entry. The fixture carry policy is
fully explicit and uses the T2 destination values, avoiding silent MOD
retiering. The original instance UUID remains unchanged.

Repair delegates donor validation to Track E. It consumes one distinct,
unbroken, +0 donor of the same Tier and staging family. The target proposal is
field-for-field identical except that `broken` becomes false. Inventory
custody removes target and donor together and commit adds one repaired target.

## Failure isolation and journal

Capacity and revision are checked before reserve. Resource/item custody keeps
an immutable pre-transaction snapshot. Failures after reservation and before
commit restore the snapshot once. A commit exception is not rolled back; it is
recorded as `COMMIT_UNCERTAIN`, retained as a terminal result, and its custody
is isolated until logout/disable cleanup. Duplicate or conflicting request IDs
cannot consume or produce twice.

The journal is bounded. An optional atomic UTF-8 audit sink writes only beneath:

```text
plugins/ProjectS/beta-staging/transactions
```

Resolved equipment payloads and bounded terminal summaries are written via a
temporary file and replace. No Track 3 code writes beneath
`plugins/ProjectS/data`, production item storage, or a Mob repository. The
default unregistered provider uses the no-op audit sink; the Integration Gate
must inject the staging sink before any staged durable run.

## Published ports and operator contribution

- `StagingItemDeliveryPort` delivers an owner-approved staging resource through
  the same Track D transaction boundary. Track 4 may later consume it.
- `StagingEconomyOperationPort` exposes give, refine, craft, promote, enhance,
  break, repair, status, logout, and one-shot outcome selection.
- `StagingEconomyOperatorContributor` describes the nine requested command
  paths but does not register them.

Resource give requires an explicit canonical staging ID and bounded positive
quantity. No concrete production reward or economy quantity is inferred.

## Verification and rollback

The Track-specific JavaExec tests are assertion-enabled and connected to
`check`. They cover catalog IDs, codec round trip, unknown MOD isolation, UUID
allocation, refine/craft/promotion, deterministic enhancement and request
replay, break/repair, donor consumption, full inventory, every injected Track D
stage, commit uncertainty, logout cleanup, access denial, lifecycle,
descriptor flags, central non-registration, staging-only audit path, and
production-path write zero.

Rollback is to leave the provider unregistered and all flags false. If a
future Integration Gate has registered it, stop both modules in reverse order,
clear one-shot overrides and reservations, and remove only disposable data
under `beta-staging/transactions`. Existing legacy items and production data
are untouched.

## Deferred integration work

This Track deliberately does not register a Bukkit listener or command, wire
the Bukkit inventory bridge, or enable a feature. A durable audit file records
resolved/terminal evidence but the in-memory runtime journal is the active
lookup implementation until the Integration Gate supplies its staging
recovery repository. Paper validation and deployment remain forbidden in this
Wave.
