# Wave 3 Owner Decisions

These decisions are the shared contract for Track G and Track H. They do not
enable gameplay, register runtime services, supply production balance, deploy
artifacts, or authorize client input as gameplay authority.

## Schema versions

`mob-definition` has current write version `2` and supported read versions
`1` and `2`. Existing `MobDefinition.SCHEMA_VERSION` remains `1`; the v1 reader,
repository, channels, fixtures, preview, reload, and test-spawn behavior remain
supported without rewriting data. Upgrade to v2 requires an explicit operator
upgrade/save operation, full validation, backup, and a new revision. Unknown
versions and corrupt files are quarantined. Downgrade is forbidden. Rollback
commits a selected known-good definition as a new revision rather than
rewriting history.

`client-protocol` has aggregate protocol version `1`. Capability payloads in
Wave 3 also use version `1`. Aggregate-version or capability-version mismatch
does not decode, downgrade, or fall through to another operation.

## Additive channels

- `projects:beta_caps_v1` — server capability advertisement
- `projects:beta_ack_v1` — client capability acknowledgement
- `projects:beta_state_v1` — server display state
- `projects:beta_command_v1` — client command request

These are additive. Existing channels, including `projects:telegraph_hello_v1`,
`projects:mob_editor_req_v1`, and `projects:mob_editor_state_v1`, are frozen and
must not be renamed or repurposed.

## Canonical capabilities

The capability IDs are `projects:hud`, `projects:party`, `projects:elements`,
`projects:equipment`, `projects:crafting`, `projects:enhancement`, and
`projects:mob-editor-v2`. Negotiation succeeds only when server and client
aggregate versions are exactly 1, both sides advertise the canonical ID with
an exactly matching payload version, the producer feature is available, and
server permission and visibility checks pass. Capability state is per-session,
bounded, non-persistent, and cleared on quit, reconnect, reload, and disable.

## Envelope and authority

Every new packet carries aggregate version, message kind, capability ID,
capability payload version, request/session ID, payload length, and payload.
Commands additionally carry player-session revision, target/content revision,
and an idempotency request ID. Server code revalidates player identity,
permission, feature and capability availability, revisions, current state, and
transaction admission. Client state is never authoritative.

## Protocol bounds

- Advertisement and acknowledgement: 8 KiB.
- Normal state and command packet: 32 KiB.
- General UTF-8 string: 256 bytes.
- Canonical ID: 128 bytes.
- Normal list: 128 entries.
- Normal map: 64 entries.
- Mob Editor list page: 50 entries.
- Mob Editor detail: at most 32 KiB; larger data is paginated or requested in
  multiple messages.

Decoders reject negative or oversized lengths, invalid UTF-8, duplicate map
keys, invalid canonical IDs, NaN, Infinity, unknown mandatory enums, excessive
nesting, and trailing bytes. Java or Bukkit-object serialization is forbidden.

## Rate limits

Rate-limit policies are immutable and injectable. Server enforcement remains
mandatory even when a client also limits itself.

- Read requests: 10/second, burst 20.
- Persistent mutation commands: 2/second, burst 4.
- Mob Editor save/apply: 1/second, burst 2.

## Old-client fallback

No handshake means old client. The server sends no Beta packet and preserves
existing HUD, particle, chat, and inventory-GUI behavior. Absence of UI never
changes gameplay permissions. Unsupported commands fail closed, version
mismatch must not create a disconnect loop, and `CLIENT_BETA_UI=false` suppresses
advertisement, acknowledgement effects, and Beta state packets.

## Mob Editor operational bounds

- Definition file: 1 MiB maximum.
- History: 20 committed revisions per mob.
- Active editor sessions: 512 global, 4 per player.
- Test-spawn instances: 128 global, 8 per player.

These are safety defaults and must be configurable or policy-injected; they are
not production balance. History cleanup must preserve current, last-known-good,
and any currently referenced rollback target. `MOB_EDITOR_V2=false` disables
v2 operations without changing v1 behavior.

## Track ownership and integration

Track G owns v2 mob definition/content/repository/editor additions and a narrow
MonsterManager adapter. Track H server owns `network.beta`, protocol codecs,
capability sessions, and display/command ports. Track H client owns the matching
client protocol, state, screens, and fallback. G does not edit codecs; H does
not copy or edit mob definition/repository models. Integration order is owner
decisions, Track G, Track H server, then Track H client compatibility evidence.
