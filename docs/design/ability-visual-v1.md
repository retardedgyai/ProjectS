# Ability Visuals v1 (future boundary)

## Authority and identity

Gameplay and visuals are separate concerns. An `AbilityDefinition` and its server-side cast lifecycle remain Paper-authoritative; a visual may observe lifecycle state but cannot alter targeting, timing, damage, cancellation, or other gameplay decisions. Visual assets use stable namespaced visual IDs (for example, `projects:arcane-burst-circle`) rather than renderer class names or transient packet values.

## Lifecycle contract

Future visual hooks may be emitted for `cast`, `telegraph`, `travel`, `hit`, `expire`, and `cancel` (or a directly equivalent ProjectS lifecycle transition). They carry a stable visual ID, cast ID, and server-authoritative spatial/timing snapshot. A renderer treats hooks as idempotent hints: missing, late, or failed rendering must never block gameplay.

## Candidate primitives

The first renderer vocabulary should stay small: point, line, arc, circle,
cone, spiral, sphere, wave, and Bezier primitives; sectors, rings,
decals/ground projections, beams, trails, impact bursts, and optional
sound/camera-safe local cues. A visual definition may compose these
primitives with duration, color/theme, radius/width, and tracking policy, but
it must not add combat semantics.

## Future editor flow

A future in-game editor can browse stable visual IDs, preview a selected visual against a non-authoritative test marker, assign it to an ability action/lifecycle hook, validate known IDs, and save only the visual reference plus presentation parameters. It should not expose ability execution, damage, priority, cooldown, or Mob AI controls through a visual panel.

## Paper/Fabric split

Paper owns ability casts and decides which lifecycle snapshots are sent. The Fabric client is render-only: it consumes versioned visual messages, resolves known visual IDs, and may gracefully ignore unknown or unsupported visuals. No client UI or protocol is introduced by this document; a future protocol must preserve this authoritative/render-only separation.
