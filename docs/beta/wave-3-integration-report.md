# Wave 3 integration report

## Integration lineage

- Wave 3 base: `606b8f0f8693ed057df04fec3bb4437818a06cc9`
- Track G source: `685f7e70022ca0b0fd74bf3ce10c4cdbe1c2d5da`
- Track G merge: `c691812931d570c4f78ed74b57fadf1130615089`
- Track H Server original source: `fc3ab2032bfd2f49ac1c7311e97ae3cac7ea1e06`
- Track H follow-up merge: `b3b37fa0afa7dd5d14b4c58371a753e438e06e24`
- Track H Server merge: `415f501d8c69442fae0e4a7faf6eb82f6b87add8`

The only Track H follow-up conflict was `build.gradle.kts`. It was resolved
additively: all pre-Wave-3, Wave 1/2, Track A-F, Mob Editor v1, Track G and
Track H JavaExec tasks remain connected to `check`, and every JavaExec task
uses assertions.

## Schema and protocol

Mob definition current write version is 2, supported reads are 1 and 2, and
the legacy `MobDefinition.SCHEMA_VERSION` remains 1. Client aggregate protocol
and all seven capability payload versions remain 1.

The byte-canonical protocol manifest is
`docs/protocol/beta-protocol-v1.json`:

```text
SHA-256 49d37172e5f5a95207876b328b52bf0d0a1a04aa6ec9a6f2e9f0bca8aa8937ac
```

The shared golden vectors are
`src/test/resources/protocol/beta-protocol-v1-vectors.json`:

```text
SHA-256 dde5a2e27d46e548b03abbc4f991c7542cf4b4f2d2a0a08c69bf292b3ec3bf1a
```

Server and Client copies are byte-identical, UTF-8, LF-only and newline
terminated. Tests bind manifest channels, capabilities, versions, bounds and
opcodes to source constants. Golden vectors cover advertisement,
acknowledgement, HUD, Party, Elements, Equipment, unavailable-balance
Crafting/Enhancement, Mob Editor list/conflict, valid/stale commands and a
trailing-byte failure.

## Track G/H boundary

Track G continues to own Mob v2 definitions, validation, repository, history,
last-good registry and editor service. Track H owns protocol packets, display
snapshots and command/display ports. `MobEditorDisplayPort` and
`MobEditorCommandPort` are compile-tested against a narrow adapter using the
public `MobEditorV2Service` result types. No Track G model is copied into H and
no H packet model is copied into G.

Revision conflict, bounded validation details, 50-entry pagination and
save/rollback/test-spawn command names remain representable. Server command
authorization tests deny stale sessions, permission/feature failures, stale
revisions and rate-limit violations before the producer port is called. Client
payloads cannot directly invoke the repository.

## Verification

`Wave3IntegratedFoundationTest` reuses the complete Track G and Track H
foundation suites, then verifies schema coexistence, all-disabled flags,
manifest and vector compatibility, G/H public-port compatibility and the
absence of Bukkit types from the pure APIs. It also checks that
`ProjectSPlugin` contains no G/H service field, startup registration or new
Beta channel registration.

The Client counterpart decodes the same vectors with production Client code,
encodes the acknowledgement and commands byte-for-byte, rejects unknown
capabilities and trailing bytes, rejects delayed old-session state, accepts a
terminal command result exactly once and enforces the 50-entry Mob Editor page
limit.

All feature flags, including `MOB_EDITOR_V2` and `CLIENT_BETA_UI`, remain false.
There is no runtime gameplay wiring, deployment, Paper start or Minecraft
Client launch.

## Unresolved risks and rollback

- Runtime adapters and plugin-message registration remain intentionally absent;
  Paper and live Client validation belong to the later Activation Phase.
- Persistent mutation destinations must retain their own durable idempotency
  boundary beyond the bounded in-memory protocol replay cache.
- Mob quarantine movement retains the documented non-atomic fallback; current
  definition writes themselves remain strict atomic/fail-closed.

Rollback is branch-level removal of this validation layer while leaving both
feature flags false. Existing Mob v1 storage/channels and old-client fallbacks
remain authoritative. Activation requires explicit runtime adapter review,
config policy approval, Paper/Client staging and rollback rehearsal.
