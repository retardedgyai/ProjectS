# Client protocol contract

## Existing boundary

Existing channel names and their payload versions remain unchanged. Current code includes independent versions such as HUD v2 and multiple v1 packets; therefore an aggregate `client-protocol` schema number is `REQUIRES_OWNER_DECISION`. Phase 0 adds no packet or channel.

## Capability handshake

A future server handshake advertises a bounded protocol version plus explicit capabilities, each with its payload version and enabled/disabled state. The global handshake channel ID and version are `REQUIRES_OWNER_DECISION`; `projects:telegraph_hello_v1` remains telegraph-specific and is not repurposed.

The client responds only with supported capabilities. Server features send new packets only after capability agreement. Capability state is per connection, bounded, cleared on quit/disable/reload, and never persisted as player progression.

## Old client fallback

- No handshake or unknown capability means old client.
- Existing channels and server-side particle/chat/inventory fallbacks continue where currently supported.
- Gameplay cannot require a Beta UI packet unless a server-side safe fallback is defined and tested.
- A client version mismatch cannot grant permission, mutate data, or disconnect-loop the player; unsupported UI operations fail closed with a bounded message.

## Packet rules

- Unknown channel/opcode/version is ignored or rejected safely and rate-limited; it never falls through to another operation.
- Every packet has a strict byte limit, bounded string/list/map counts, exact trailing-byte validation, finite-number validation, and canonical ID validation.
- Per-player and per-operation rate limits protect reads and stricter persistent writes.
- Permission and current revision are checked server-side; client display state is never authoritative.
- Async decoding/storage does not call Bukkit API. Responses return on the proper server thread.

## Capability payloads

- **HUD state:** level/XP/resource/class/party summaries and only data the player may view.
- **Party state:** party ID/revision, leader/member summaries, nearby/status indicators; no entity references.
- **Burn/cold display:** target network identity plus bounded gauge/stacks/stage/immunity timing; runtime only.
- **Equipment details:** schema/read status, slots, Tier/ILv/rarity/quality/base rolls/MOD entries/binding/trade/broken state; unknown entries shown unsupported.
- **Crafting UI:** recipe revision, validated inputs/output preview, transaction/request ID, terminal result.
- **Enhancement UI:** item revision, level/broken state, validated balance-data preview, transaction result.
- **Mob Editor v2:** negotiated schema/capability, bounded lists/details, base revision, conflict response, permissions.

Exact screen design, new channel IDs, aggregate version, packet byte budgets beyond existing limits, and capability codes are `REQUIRES_OWNER_DECISION` with Track H.

## Lifecycle and rollback

Handshake/capability caches clear on quit, reload, and disable. Reload republishes capabilities from an immutable snapshot. When `CLIENT_BETA_UI=false`, the server stops advertising Beta capabilities and uses existing behavior. Protocol rollback never renames an existing channel; it withdraws a capability and keeps the prior decoder/fallback for its support window.

