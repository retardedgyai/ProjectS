# Track G: Mob Editor v2 and content foundation

## Scope and start point

Track G starts at `WAVE_3_BASE_SHA`
`606b8f0f8693ed057df04fec3bb4437818a06cc9`. It adds a disabled,
pure-Java foundation for Mob schema v2. It does not wire services into
`ProjectSPlugin`, activate gameplay, provide production Mob/Boss content or
balance values, change client packets, or edit Track B/C/F internals.

## V1 compatibility

`MobDefinition.SCHEMA_VERSION` remains `1`. The existing v1 repository,
validator, YAML representation, revision rules, atomic save, permissions,
payload limits, reload flow, test-spawn flow, and channels
`projects:mob_editor_req_v1` / `projects:mob_editor_state_v1` are unchanged.

The v2 repository recognizes v1 as a read-only document and retains its exact
bytes. Read, list, preview, and validation do not write it. Upgrade requires an
explicit proposal, full v2 validation, an operator confirmation, a SHA-256
check that the source has not changed, a v1 backup, and a new revision commit.
There is no automatic upgrade or downgrade path.

## V2 model and references

`MobDefinitionV2` is immutable and contains schema/revision/ID, display data,
entity type, NORMAL/ELITE/MINIBOSS/BOSS category, finite attributes,
AttackMetadata-backed attacks, skill references, a phase DAG, canonical
drop/reward/participation references, spawn rules, PvE weaknesses, Track C
fire/ice target categories, and bounded extension fields.

Attack tags and `ElementProfile` use the existing Track C types directly.
They are never inferred from a weapon, skill name, or entity type. Skills,
items, resources, rewards, participation policies, and regions are resolved
through injected ports; Track G does not read another Track's repository.

Validation reports one of `VALID`, `INVALID`, `UNRESOLVED_REFERENCE`,
`UNKNOWN_VERSION`, `CONFLICT`, or `OVERSIZED`, with at most 32 bounded details.
It rejects duplicate IDs, missing references, non-finite data, oversized
collections/maps/strings, missing attack metadata, invalid element/tag pairs,
and phase graphs with no entry, missing targets, unreachable nodes, or cycles.

## Repository and rollback

The repository reads schema 1 and 2 and writes schema 2 only. Its deterministic
UTF-8 YAML envelope contains a bounded, explicitly encoded payload; Java and
Bukkit serialization are not used. Files are limited to 1 MiB. Invalid UTF-8,
unknown versions, corrupt data, symlinks, non-regular files, and unsafe IDs are
rejected; corrupt/unknown/oversized regular files are quarantined.

Writes compare revisions, flush a temporary file, and atomically move it into
place. A conflict returns the current snapshot without overwriting either
side. History defaults to 20 committed revisions and protects current,
last-known-good, and referenced rollback targets. Rollback reads a committed
revision and saves it as a new revision. Close is idempotent. The repository
does file I/O only and calls no Bukkit API.

## Runtime registry

`MobDefinitionRegistry` stores bounded immutable current and last-good
snapshots. An invalid reload retains current. A spawn pins its exact revision,
so later reloads affect new instances only and cannot mutate an in-flight
attack. Revision events contain IDs and numbers only; no entity reference is
retained. Clear and close release all state.

## Editor service

`MobEditorV2Service` provides list, open, validate, preview, save, conflict,
rollback, test-spawn request, cleanup, and session close operations through
pure ports. Permissions are decided by an injected port. Safety defaults are:

- 512 global editor sessions and 4 per player;
- 128 global test spawns and 8 per player;
- 50 definitions per list page;
- injected session expiry and feature-state policies.

`MOB_EDITOR_V2=false` rejects every v2 operation while leaving the existing v1
editor untouched. Test-spawn adapters return opaque handles; cleanup is bounded
and performed on clear/close. No Bukkit entity is retained by the service.

## Verification

`MobV2FoundationTest` covers v1 byte invariance and explicit upgrade, v2 round
trip and immutability, supported schemas, exact AttackMetadata, references,
phase DAG failures, finite/bounds checks, UTF-8 and filesystem rejection,
quarantine, revision conflict, simultaneous save, atomic failure, bounded
history, rollback-as-new-revision, last-good retention, pinned spawn revisions,
editor flag/permission/session/test-spawn limits and cleanup, and reflection
checks preventing Bukkit types in public pure APIs.

The existing `MobEditorFoundationTest` remains connected to `check` and keeps
characterizing v1 YAML, atomic save/reload, permissions and channels, payload
bounds, definition validation, and test-Mob infrastructure.

## Rollback and remaining work

Rollback of this foundation means keeping `MOB_EDITOR_V2=false` and using the
unchanged v1 repository/editor. A v2 content rollback is a new committed
revision selected from history, never a destructive history rewrite.

Production skill coefficients, Mob attributes, drops, spawn locations/timing,
weakness multipliers, reward catalogs, and Boss phase content remain
`REQUIRES_BALANCE_DATA`. Runtime wiring, Paper verification, client UI, network
codec integration, and approved content authoring are intentionally deferred.
