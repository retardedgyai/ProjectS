# Combat Shape v1

Combat Shape is the shared, immutable, deterministic world-space geometry foundation for future abilities, bosses, and visual effects. It prevents systems from independently reimplementing combat volumes. The core package is pure Java and imports no Bukkit or Paper types. Minecraft coordinates are used directly: X and Z are horizontal, Y is up.

## Broad and narrow phase

`CombatShape.broadPhaseBounds()` returns a conservative `Aabb` for candidate lookup. `CombatShape.intersects(Aabb)` is the narrow phase and always tests the target's full AABB, never merely an entity or box centre. All broad bounds are inflated by the common `GeometryTolerance.LENGTH` (`1e-5`) so the broad phase is never smaller than the represented shape. `CombatShapeQuery` is a small pure helper which receives the broad bounds, applies eligibility, performs exact AABB intersection, and sorts with a supplied deterministic comparator.

Tangencies are hits. Invalid dimensions, unordered AABB faces, and zero directions are rejected. The one shared tolerance is a length tolerance, not a different hidden gameplay epsilon per shape. When arithmetic cannot establish a safe narrow-phase miss, the result is conservatively an intersection; targeting must not acquire a numerical false negative.

## Supported Numeric Domain

Combat Shape is a ProjectS gameplay geometry foundation, not a general-purpose library promising correctness for every finite IEEE-754 value.

- Position coordinates and AABB faces are finite and have absolute value at most `2^25` (`33,554,432`). Paper's documented maximum world radius is `29,999,984`, so the contract includes the whole supported Minecraft world plus over 3.5 million blocks of structural margin for entity bounds and attachment positions.
- Declared non-negative extents are at most `2^26` (`67,108,864`), the maximum possible separation between two supported coordinates. Every tolerance-expanded shape bound must also remain inside the coordinate cube. This is a numeric safety ceiling, not a skill balance cap: current ProjectS telegraph and Ability authoring stops at 128 blocks, while existing Warrior ranges stop at 32.
- Direction components may use any finite `double` magnitude. A direction must contain at least one non-zero representable component. Positive rescaling preserves direction semantics when the scaled components remain finite and retain their representable ratios; a scale that rounds the whole vector to zero is rejected.
- `NaN`, infinities, coordinates or derived bounds outside the cube, and extents outside the ceiling fail closed with `IllegalArgumentException` at a constructor or adapter boundary. Values are never clamped and abnormal input is not deferred to GJK.

The powers of two make the validation boundary exact. At Minecraft-scale coordinates, one `double` ULP is already several nanometres (`Math.ulp(30_000_000) ~= 3.73e-9`), so the old `1e-9` tolerance could not honestly cover the supported world. `1e-5` is still far below gameplay-visible distance while leaving a conservative arithmetic margin throughout the declared domain.

Paper platform reference: <https://docs.papermc.io/paper/reference/server-properties/#max-world-size>

## Floating Point Contract and Safe Normalization

Geometry algorithms are deterministic for the same representable inputs. `Vec3.normalized()` uses max-component scaling: divide components by `max(abs(x), abs(y), abs(z))`, compute the norm of that bounded triple, and divide again. It never squares raw huge components, reconstructs an overflow-prone original length, or computes `1 / maxComponent`. This accepts finite directions up to `Double.MAX_VALUE`, preserves representable subnormal directions such as `(Double.MIN_VALUE, 0, 0)`, rejects zero, and produces a finite approximately-unit vector.

World positions and extents stay bounded before they reach intersection code. A non-finite intermediate, degenerate simplex, duplicate support, or iteration exhaustion cannot certify separation and therefore produces a conservative hit.

## Shape semantics

- `SphereShape(center, radius)` is a solid sphere. A target hits when its closest AABB point is within radius.
- `UprightCylinderShape(center, radius, verticalHalfHeight)` is a solid Y-aligned cylinder. It requires overlapping Y slabs and uses horizontal rectangle distance.
- `HorizontalRingShape(center, innerRadius, outerRadius, verticalHalfHeight)` is a finite Y-aligned annulus. `0 <= inner < outer`; an AABB that reaches through the hole into the annulus is a hit.
- `AxisAlignedBoxShape(bounds)` is the given solid world-aligned box with inclusive AABB overlap.
- `LineShape(start, end, radius)` is the finite capsule swept by the segment, including both rounded endpoints.
- `ConeShape(origin, forward, length, halfAngleRadians)` is a finite solid circular cone. `origin` is its apex, internal `forward` is normalized, and its base is at `origin + forward * length`. Its half angle is strictly between zero and `PI / 2`; points behind the apex and past the base plane are absent.

## Exact line and cone algorithms

Line/AABB intersection partitions segment parameter `t` at every crossing of each target AABB min/max slab. On every distinct, non-zero-width interval, the point-to-box squared distance is a single quadratic and is minimized at its interval ends and, when present, stationary point. It compares that minimum to `(radius + tolerance)^2`. Cuts are not merged by a world length epsilon: tiny parameter intervals can still carry material distance on long segments. Overflow or non-finite intermediate values conservatively hit.

Cone/AABB intersection is package-private and cone-specific GJK, not a public collision framework. Cone support first safely normalizes the query direction `d` to `u`; positive scaling therefore cannot change the selected point. With stored normalized axis `n`, it calculates the base-disk projection directly as `p = u - n * (dot(u, n) / dot(n, n))`, using component-wise fused multiply-add for the subtraction. No raw huge cross product participates in support selection.

The projection still has a documented rounding envelope `E = 128 * ulp(1)` and a near-axis budget `B = LENGTH / 4`. For base radius `R`, the base centre is used only when `R * (|p| + E) <= B`; this bounds the omitted radial objective by `B` instead of normalizing a cancellation-sized vector to the full rim. Otherwise `p` is safely normalized and the rim candidate is used. The apex/base choice uses an apex-relative local score, `length * dot(u,n) + R * dot(u,radial)`, avoiding subtraction of large world-space dot products.

Across the declared `R <= 2^26` domain, the conservative rim-direction envelope `2 * R * E` is below `3.82e-6`. Together with the `2.5e-6` fallback budget it remains below the `1e-5` GJK separation margin. Cone broad bounds add the full shared tolerance, so any omitted near-axis radial term remains inside the broad phase as well.

AABB support sign-selects its min or max coordinate. A GJK miss still requires the scale-correct strict certificate `support.dot(direction) < -LENGTH * |direction|`. Support results, Minkowski differences, and search directions must remain finite; otherwise the query conservatively hits. Thus a tolerance-bounded Cone support approximation cannot by itself produce a false separation certificate.

## Bukkit boundary and future use

`combat.shape.bukkit.BukkitAabbAdapter` is the only core-adjacent Bukkit conversion boundary and converts between `org.bukkit.util.BoundingBox` and pure `Aabb`. `TargetingService.enemies(Player, CombatShape)` uses the pure query pipeline: Bukkit supplies broad candidates, existing enemy policy filters them, pure AABB narrow phase filters them, and UUID ordering determines gameplay order. The existing location/radius targeting method remains unchanged.

Future Ability Runtime, Boss logic, VFX telegraphs, and Kotlin DSLs can source-share these primitives. This v1 does not introduce persistence schemas, OBBs, NMS, a general-purpose GJK/collision API, ECS/spatial-tree work, VFX rewiring, Kotlin implementation, or any route change in current gameplay.

## Research alignment

The foundation remains aligned with `MONU-005`, `KERNEL-002`, and `P3`. It is an independent ProjectS implementation and does not copy Monumenta source code.
