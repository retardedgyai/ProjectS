# Track H server protocol foundation

Track H adds a pure Java, producer-neutral boundary for Beta client UI. It does
not register channels in `ProjectSPlugin`, enable gameplay, persist capability
state, or treat client data as authority. `CLIENT_BETA_UI` remains false.

## Protocol contract

The aggregate protocol and all Wave 3 capability payload versions are `1`.
The additive channels are:

- `projects:beta_caps_v1`
- `projects:beta_ack_v1`
- `projects:beta_state_v1`
- `projects:beta_command_v1`

The canonical capabilities are `projects:hud`, `projects:party`,
`projects:elements`, `projects:equipment`, `projects:crafting`,
`projects:enhancement`, and `projects:mob-editor-v2`. Existing Telegraph, Mob
Editor v1, HUD, loadout, and balance channels are unchanged.

`BetaProtocolCodec` uses an explicit deterministic binary envelope. It rejects
unsupported aggregate versions, unknown mandatory opcodes and capabilities,
invalid UTF-8, negative or oversized lengths, duplicates, truncation, and
trailing bytes. Handshakes are bounded to 8 KiB; state and command packets are
bounded to 32 KiB. Strings are bounded to 256 UTF-8 bytes, canonical IDs to 128
bytes, lists to 128 entries, maps to 64 entries, and Mob Editor list pages to 50
entries. Display DTOs reject non-finite numeric data. No Java serialization,
Bukkit object serialization, or Bukkit type appears in the public protocol API.

## Capability lifecycle

`BetaCapabilitySessionService` holds one ephemeral session per player UUID.
Advertisements contain an unpredictable session ID and monotonic advertisement
revision. Acknowledgement requires the exact aggregate version, session,
revision, canonical capability, payload version, producer availability,
permission, and visibility decision. Sessions are bounded and expire. Reconnect,
quit adapters, reload, disable, and idempotent close have explicit clear methods.
An advertisement call with the feature unavailable removes only that player's
session and treats that connection as an old client; it cannot disable another
player's acknowledged session. A successful enabled advertisement establishes
the globally enabled runtime state, while `reload(policy, false)` is the explicit
global disable boundary and clears all sessions.

## Command authority

`BetaCommandRouter` rechecks player/session identity, negotiated capability,
producer feature, permission, player and target revisions, current state, and
transaction admission before calling a public producer port. It provides
bounded idempotency result retention and injected token-bucket limits:

- reads: 10 per second, burst 20;
- persistent mutations: 2 per second, burst 4;
- Mob Editor save/apply: 1 per second, burst 2.

Raw packet bytes are decoded by an injected `BetaCommandDecoder` into a bounded
`BetaDecodedCommand` before a producer port is called. Client display state and
raw packet input never enter a producer domain mutation directly. Track G
integration uses only `MobEditorDisplayPort` and `MobEditorCommandPort`; no Track
G model is copied here.

## Display contracts and compatibility

Bounded immutable snapshots cover HUD, Party, target elements, equipment,
crafting, enhancement, and Mob Editor v2. Enhancement previews can explicitly
use `UNAVAILABLE_BALANCE_DATA`, which forbids inferred costs or outcomes.

Old clients receive no Beta packet before a valid acknowledgement. New clients
connected to an old server retain existing UI. Malformed or mismatched clients
fail closed without an implicit downgrade or disconnect retry loop. The tests
cover old/old, old/new, new/old, new/new, malformed, unsupported capability,
codec fuzz, lifecycle cleanup, stale revisions, permission denial, rate limits,
idempotency, bounds, finite values, and pure-Java API boundaries.

## Rollback and integration

Rollback keeps `CLIENT_BETA_UI=false`, clears ephemeral sessions, and removes
this additive branch. No existing channel or fallback changes. Track H server
should integrate after Track G, then the separately reviewed Client PR should be
validated against the integrated protocol constants. No artifact was deployed
and no server or client was started for this foundation.
