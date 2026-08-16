# Ability VFX appearance runtime v0.1

`AbilityVisualDefinition.PrimitiveType` remains the ten-shape vocabulary.  Each
primitive now also carries a platform-neutral `Appearance`: `DEBUG_QUAD`
(`projects:debug_quad`) or `PARTICLE` from the bounded Minecraft catalog in
`src/test/resources/protocol/ability-vfx-appearance-v01-catalog.txt`.  There is
no free-form appearance data, scale, or DUST payload in v0.1.

Old Java constructors and Kotlin authoring default to `DEBUG_QUAD`.  The v1
editor payload is unchanged.  A v1 APPLY preserves the current authoritative
appearance by stable primitive ID; an ID new to that request defaults to debug
quad.  REVERT returns the complete authoritative base visual.

Editor v2 uses `projects:skill_editor_req_v2` and
`projects:skill_editor_state_v2`.  Its deterministic envelope is `u8(2),
u16(v1-body length), v1 body, u8(appearance-table count)`.  Each table contains
`u16 primitive count`, then `u16 UTF-8 primitive ID, u8 kind, u16 UTF-8 stable
appearance ID` records in document order.  V1 codecs remain the canonical body
for all pre-appearance fields.

The runtime outer channel and version stay `projects:ability_vfx_v1` and `1`.
Debug primitives retain their complete v1 bytes.  Particle primitives have
primitive version `2` and append `u8 kind, u16 UTF-8 appearance ID` after the
complete v1 primitive body, inside its existing length frame.  Old clients can
skip the version-2 primitive by frame length.

All-debug fingerprints use the original v1 canonical encoding.  Any particle
uses a SHA-256 input domain-prefixed with the UTF-8 bytes for
`projects:skill_vfx_appearance_v2` followed by one literal `0x00` byte
and the v2 canonical visual encoding, so appearance-only edits change revision
semantics without altering legacy fingerprints.
