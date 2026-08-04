# Track H: client UI and protocol

- **Branch:** `codex/beta-track-h-client-ui-protocol`
- **Worktree:** `beta-track-h-client-ui-protocol`
- **Contract lineage SHA:** `8ba653a8dc3f604dc23a142cca5f5a88f51682a9`
- **Actual branch start SHA:** the merge commit containing `wave-3-owner-decisions.md`; Track G and H must record the same `WAVE_3_BASE_SHA`.
- **PR base:** `integration/beta-full-build` for server; client changes use a separately reviewed client branch/PR.

## Scope

Aggregate protocol v1 with the owner-approved additive channels and canonical
payload-v1 capabilities; bounded server snapshots for HUD/party/elements/
equipment/crafting/enhancement/Mob Editor v2; old-client fallback, unknown
packet handling, permissions/rate limits, and lifecycle cleanup.

## Out of scope

Domain calculations, persistence authority, inventory/item mutation, reward logic, renaming existing channels, UI-driven permissions, client gameplay authority.

## Ownership

Owned server package: `network` and final small lifecycle/channel registration commits; owned client files only in the ProjectS-Client worktree/PR. Do not edit producer domain packages. Shared packet records are coordinated interfaces.

## Public interfaces and dependencies

Consume immutable display snapshots and commands from A-G. Publish capability/session state and decoded commands only; server revalidates every mutation. Existing channels remain. New channels are `projects:beta_caps_v1`, `projects:beta_ack_v1`, `projects:beta_state_v1`, and `projects:beta_command_v1`.

## Feature flags

`CLIENT_BETA_UI`; false stops advertising Beta capabilities and uses existing UI/fallbacks. It does not auto-enable producer features.

## Tests and manual verification

Old server/client, new server/old client, old server/new client, and new/new matrices; unknown versions/opcodes/trailing bytes; maximum packet/list/string; rate limits; permission denial; reconnect/reload/disable cleanup; missing capability fallback; no-client server behavior. Run server and client builds/tests separately and record hashes.

## Commit split

1. Capability pure records/tests. 2. server handshake/session. 3. payload adapters per producer. 4. client decoders/state. 5. screens. 6. compatibility/manual evidence/docs.

## Merge prerequisites and rollback

Producer payload contracts stable; handshake channel/version owner-approved; server/client compatibility matrix passes. H merges last. Rollback disables flag, stops capability advertisement, clears sessions, and keeps existing channels/decoders/fallbacks.

## Completion report

Report both repo start SHAs, protocol/capability versions, existing channels preserved, payload bounds, producer dependencies, commits, server/client tests and CI, hashes/manual matrix, flag, rollback, unsupported clients, final statuses.

