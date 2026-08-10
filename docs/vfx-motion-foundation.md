# VFX Motion Foundation v0.1

The server definition keeps three independent concerns: Shape describes the primitive
geometry, Appearance describes its bounded material, and Motion describes how an already
ordered production sample is revealed or travelled. Motion is platform-neutral; sampler
and renderer code stays in the client production path.

## Semantics and capability

`STATIC` renders a complete shape and is canonical (`FORWARD`, `LINEAR`, phase `0`, trail
`0`). `REVEAL` uses the existing primitive duration as the only 0-to-1 clock and preserves
the legacy `REVEAL/FORWARD/LINEAR/0/0` geometry. `TRAVEL` is a clamped, non-wrapping
head-plus-trail window over an ordered open path. The server accepts finite phase and trail
fractions in `[0,1]`. Easing is linear, `t²`, `1-(1-t)²`, or `3t²-2t³`; logical progress is
`phase + (1-phase)*easedTime`, then physical progress is `q` forward or `1-q` reverse.

All ten primitives support STATIC and REVEAL/FORWARD. REVEAL/REVERSE supports LINE, ARC,
CIRCLE, SPIRAL, WAVE, and BEZIER. TRAVEL supports LINE, ARC, SPIRAL, WAVE, and BEZIER;
CIRCLE wrap and unordered POINT, CONE, SPHERE, and BURST travel are intentionally deferred.
REVEAL has no trail; TRAVEL permits the complete `[0,1]` trail range.

## Compatibility and wire boundaries

The legacy domain constructor supplies the exact legacy Motion default. Existing Editor v1
and v2 bodies and Runtime v1 primitive bytes remain unchanged. Editor v3 wraps the v1 body
with a bounded stable-ID-keyed table containing Appearance and Motion. A Motion-bearing
fingerprint uses a domain-separated v3 canonical representation; legacy and appearance-only
fingerprints retain their existing paths. Runtime v2 is additive (`projects:ability_vfx_v2`)
and carries primitive envelope version 3 with Appearance and Motion. Legacy clients receive
only legacy Motion primitives on the existing v1 channel; nonlegacy primitives are omitted,
and an empty filtered cue is omitted.

## Hidden-field merge and ownership

Editor v1 and v2 never see Motion. On apply, the server merges hidden Motion by stable
primitive ID, gives new IDs the legacy default, drops deleted IDs, and rejects a primitive
type change when the preserved Motion is invalid for the new type. Editor v3 is fully
authoritative and validates the complete candidate. Session revision and fingerprint checks
remain in the runtime-only editor service.

The common `AbilityRuntime -> AbilityVisualAdapter -> CueSink` path remains shared by Player
and Mob sources. Shape sampling, normalized reveal/progress, quality ordering, and runtime
budgets remain client production responsibilities; Core contains only the typed domain and
transport contracts, while Bukkit channel registration stays at the network boundary.
