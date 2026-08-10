package io.github.gyai.projects.authoring

import io.github.gyai.projects.ability.AbilityLifecycleEvent
import io.github.gyai.projects.ability.AbilityVisualBinding
import io.github.gyai.projects.ability.AbilityVisualDefinition
import io.github.gyai.projects.ability.AbilityVisualRegistry
import io.github.gyai.projects.ability.MotionDirection
import io.github.gyai.projects.ability.MotionEasing
import io.github.gyai.projects.ability.MotionMode
import io.github.gyai.projects.ability.MotionSpec

fun literal(value: Double): AbilityVisualDefinition.Scalar = AbilityVisualDefinition.Literal(value)
fun actionRadius(): AbilityVisualDefinition.Scalar = AbilityVisualDefinition.ActionField.RADIUS
fun actionInnerRadius(): AbilityVisualDefinition.Scalar = AbilityVisualDefinition.ActionField.INNER_RADIUS
fun actionWidth(): AbilityVisualDefinition.Scalar = AbilityVisualDefinition.ActionField.WIDTH
fun actionLength(): AbilityVisualDefinition.Scalar = AbilityVisualDefinition.ActionField.LENGTH
val actionRadius: AbilityVisualDefinition.Scalar get() = actionRadius()
val actionInnerRadius: AbilityVisualDefinition.Scalar get() = actionInnerRadius()
val actionWidth: AbilityVisualDefinition.Scalar get() = actionWidth()
val actionLength: AbilityVisualDefinition.Scalar get() = actionLength()

fun visual(id: String, block: VisualBuilder.() -> Unit): AbilityVisualDefinition {
    val builder = VisualBuilder()
    builder.block()
    return builder.build(id)
}

fun visualBinding(abilityId: String, visualId: String) = AbilityVisualBinding(abilityId, visualId)

@AuthoringDsl
class VisualBuilder internal constructor() {
    private val bindings = mutableListOf<AbilityVisualDefinition.HookBinding>()

    fun hook(hook: AbilityLifecycleEvent.Hook, block: HookBuilder.() -> Unit) {
        require(hook in setOf(AbilityLifecycleEvent.Hook.CAST, AbilityLifecycleEvent.Hook.TELEGRAPH,
            AbilityLifecycleEvent.Hook.HIT, AbilityLifecycleEvent.Hook.EXPIRE, AbilityLifecycleEvent.Hook.CANCEL)) {
            "Visual authoring supports CAST, TELEGRAPH, HIT, EXPIRE, and CANCEL hooks"
        }
        val builder = HookBuilder()
        builder.block()
        bindings += AbilityVisualDefinition.HookBinding(hook, builder.build())
    }

    fun cast(block: HookBuilder.() -> Unit) = hook(AbilityLifecycleEvent.Hook.CAST, block)
    fun telegraph(block: HookBuilder.() -> Unit) = hook(AbilityLifecycleEvent.Hook.TELEGRAPH, block)
    fun hit(block: HookBuilder.() -> Unit) = hook(AbilityLifecycleEvent.Hook.HIT, block)
    fun expire(block: HookBuilder.() -> Unit) = hook(AbilityLifecycleEvent.Hook.EXPIRE, block)
    fun cancel(block: HookBuilder.() -> Unit) = hook(AbilityLifecycleEvent.Hook.CANCEL, block)
    fun onCast(block: HookBuilder.() -> Unit) = cast(block)
    fun onTelegraph(block: HookBuilder.() -> Unit) = telegraph(block)
    fun onHit(block: HookBuilder.() -> Unit) = hit(block)
    fun onExpire(block: HookBuilder.() -> Unit) = expire(block)
    fun onCancel(block: HookBuilder.() -> Unit) = cancel(block)

    internal fun build(id: String) = AbilityVisualDefinition(AbilityVisualDefinition.SCHEMA_VERSION, id, bindings.toList())
}

@AuthoringDsl
class HookBuilder internal constructor() {
    private val emissions = mutableListOf<AbilityVisualDefinition.Emission>()

    fun emission(id: String, actionIndex: Int = -1, block: EmissionBuilder.() -> Unit) {
        val builder = EmissionBuilder()
        builder.block()
        emissions += AbilityVisualDefinition.Emission(id, actionIndex, builder.build())
    }

    internal fun build() = emissions.toList()
}

@AuthoringDsl
class EmissionBuilder internal constructor() {
    private val primitives = mutableListOf<AbilityVisualDefinition.PrimitiveSpec>()

    fun point(id: String, block: PointBuilder.() -> Unit) = add(PointBuilder(id), block)
    fun line(id: String, block: LineBuilder.() -> Unit) = add(LineBuilder(id), block)
    fun arc(id: String, block: ArcBuilder.() -> Unit) = add(ArcBuilder(id), block)
    fun circle(id: String, block: CircleBuilder.() -> Unit) = add(CircleBuilder(id), block)
    fun cone(id: String, block: ConeBuilder.() -> Unit) = add(ConeBuilder(id), block)
    fun spiral(id: String, block: SpiralBuilder.() -> Unit) = add(SpiralBuilder(id), block)
    fun sphere(id: String, block: SphereBuilder.() -> Unit) = add(SphereBuilder(id), block)
    fun wave(id: String, block: WaveBuilder.() -> Unit) = add(WaveBuilder(id), block)
    fun bezier(id: String, block: BezierBuilder.() -> Unit) = add(BezierBuilder(id), block)
    fun burst(id: String, block: BurstBuilder.() -> Unit) = add(BurstBuilder(id), block)

    private fun <T : PrimitiveBuilder> add(builder: T, block: T.() -> Unit) {
        builder.block()
        primitives += builder.build()
    }

    internal fun build() = primitives.toList()
}

@AuthoringDsl
abstract class PrimitiveBuilder internal constructor(private val id: String) {
    var delayTicks: Int = 0
    var durationTicks: Int = 1
    var argb: Int = 0xffffffff.toInt()
    var width: Double = 1.0
    var density: Int = 1
    var seed: Long = 0
    var localOffset: AbilityVisualDefinition.Vec = AbilityVisualDefinition.Vec(0.0, 0.0, 0.0)
    var yawRadians: Double = 0.0
    var appearance: AbilityVisualDefinition.Appearance = AbilityVisualDefinition.Appearance.DEBUG_QUAD
    var motionSpec: MotionSpec = MotionSpec.LEGACY_DEFAULT
    fun particle(id: String) { appearance = AbilityVisualDefinition.Appearance.particle(id) }
    fun motion(mode: MotionMode, direction: MotionDirection = MotionDirection.FORWARD,
               easing: MotionEasing = MotionEasing.LINEAR, phase: Double = 0.0,
               trailFraction: Double = 0.0) {
        motionSpec = MotionSpec(mode, direction, easing, phase, trailFraction)
    }
    fun reveal(direction: MotionDirection = MotionDirection.FORWARD,
               easing: MotionEasing = MotionEasing.LINEAR, phase: Double = 0.0) =
        motion(MotionMode.REVEAL, direction, easing, phase, 0.0)
    fun travel(direction: MotionDirection = MotionDirection.FORWARD,
               easing: MotionEasing = MotionEasing.LINEAR, phase: Double = 0.0,
               trailFraction: Double = 0.0) =
        motion(MotionMode.TRAVEL, direction, easing, phase, trailFraction)
    fun staticMotion() = motion(MotionMode.STATIC)

    protected fun spec(type: AbilityVisualDefinition.PrimitiveType, size: AbilityVisualDefinition.Scalar? = null,
                       radius: AbilityVisualDefinition.Scalar? = null, length: AbilityVisualDefinition.Scalar? = null,
                       height: AbilityVisualDefinition.Scalar? = null, angle: AbilityVisualDefinition.Scalar? = null,
                       startAngle: AbilityVisualDefinition.Scalar? = null, sweepAngle: AbilityVisualDefinition.Scalar? = null,
                       turns: AbilityVisualDefinition.Scalar? = null, count: Int = 0,
                       controlPoints: List<AbilityVisualDefinition.Vec> = emptyList()) =
        AbilityVisualDefinition.PrimitiveSpec(id, type, delayTicks, durationTicks, argb, width, density, seed,
            localOffset, yawRadians, size, radius, length, height, angle, startAngle, sweepAngle, turns, count, controlPoints, appearance, motionSpec)

    internal abstract fun build(): AbilityVisualDefinition.PrimitiveSpec
}

class PointBuilder internal constructor(id: String) : PrimitiveBuilder(id) { var size: AbilityVisualDefinition.Scalar? = null; override fun build() = spec(AbilityVisualDefinition.PrimitiveType.POINT, size = size) }
class LineBuilder internal constructor(id: String) : PrimitiveBuilder(id) { var length: AbilityVisualDefinition.Scalar? = null; var controlPoints: List<AbilityVisualDefinition.Vec> = emptyList(); override fun build() = spec(AbilityVisualDefinition.PrimitiveType.LINE, length = length, controlPoints = controlPoints.toList()) }
class ArcBuilder internal constructor(id: String) : PrimitiveBuilder(id) { var radius: AbilityVisualDefinition.Scalar? = null; var startAngle: AbilityVisualDefinition.Scalar? = null; var sweepAngle: AbilityVisualDefinition.Scalar? = null; override fun build() = spec(AbilityVisualDefinition.PrimitiveType.ARC, radius = radius, startAngle = startAngle, sweepAngle = sweepAngle) }
class CircleBuilder internal constructor(id: String) : PrimitiveBuilder(id) { var radius: AbilityVisualDefinition.Scalar? = null; override fun build() = spec(AbilityVisualDefinition.PrimitiveType.CIRCLE, radius = radius) }
class ConeBuilder internal constructor(id: String) : PrimitiveBuilder(id) { var length: AbilityVisualDefinition.Scalar? = null; var angle: AbilityVisualDefinition.Scalar? = null; override fun build() = spec(AbilityVisualDefinition.PrimitiveType.CONE, length = length, angle = angle) }
class SpiralBuilder internal constructor(id: String) : PrimitiveBuilder(id) { var radius: AbilityVisualDefinition.Scalar? = null; var height: AbilityVisualDefinition.Scalar? = null; var turns: AbilityVisualDefinition.Scalar? = null; override fun build() = spec(AbilityVisualDefinition.PrimitiveType.SPIRAL, radius = radius, height = height, turns = turns) }
class SphereBuilder internal constructor(id: String) : PrimitiveBuilder(id) { var radius: AbilityVisualDefinition.Scalar? = null; override fun build() = spec(AbilityVisualDefinition.PrimitiveType.SPHERE, radius = radius) }
class WaveBuilder internal constructor(id: String) : PrimitiveBuilder(id) { var length: AbilityVisualDefinition.Scalar? = null; var radius: AbilityVisualDefinition.Scalar? = null; var height: AbilityVisualDefinition.Scalar? = null; override fun build() = spec(AbilityVisualDefinition.PrimitiveType.WAVE, radius = radius, length = length, height = height) }
class BezierBuilder internal constructor(id: String) : PrimitiveBuilder(id) { var controlPoints: List<AbilityVisualDefinition.Vec> = emptyList(); override fun build() = spec(AbilityVisualDefinition.PrimitiveType.BEZIER, controlPoints = controlPoints.toList()) }
class BurstBuilder internal constructor(id: String) : PrimitiveBuilder(id) { var radius: AbilityVisualDefinition.Scalar? = null; var count: Int = 0; override fun build() = spec(AbilityVisualDefinition.PrimitiveType.BURST, radius = radius, count = count) }

/** Java-facing migrated content facade; the existing Java Dev classes delegate here. */
object DevArcaneBurstAuthoring {
    const val ABILITY_ID: String = "projects:dev-shared-arcane-burst"
    const val VISUAL_ID: String = "projects:vfx/dev-arcane-burst"

    @JvmStatic
    fun sharedArcaneBurst() = ability(ABILITY_ID, "Dev Shared Arcane Burst") {
        circleTelegraph { target = io.github.gyai.projects.ability.TargetSelector.PRIMARY_TARGET; origin = io.github.gyai.projects.ability.TargetSelector.PRIMARY_TARGET; radius = 3.0; durationTicks = 20; lockAtCreation = true }
        wait(20)
        damage { target = io.github.gyai.projects.ability.TargetSelector.PRIMARY_TARGET; damageType = io.github.gyai.projects.combat.damage.DamageType.MAGICAL; damageKind = io.github.gyai.projects.combat.damage.DamageKind.DIRECT_SKILL; fixedDamage = 12.0; coefficient = 0.5; criticalAllowed = true; tags(io.github.gyai.projects.combat.damage.AttackTag.MAGIC, io.github.gyai.projects.combat.damage.AttackTag.SKILL) }
    }

    @JvmStatic
    fun arcaneBurst() = visual(VISUAL_ID) {
        cast { emission("cast", -1) { spiral("cast-spiral") { delayTicks = 2; durationTicks = 12; radius = literal(1.2); height = literal(1.0); turns = literal(1.0); arcaneCommon() }; sphere("cast-sphere") { durationTicks = 10; radius = literal(0.7); arcaneCommon() } } }
        telegraph { emission("telegraph", 0) { circle("telegraph-circle") { durationTicks = 20; radius = actionRadius(); arcaneCommon() } } }
        hit { emission("hit", 2) { burst("hit-burst") { durationTicks = 10; radius = literal(1.0); count = 12; arcaneCommon() } } }
        expire { emission("expire", -1) { sphere("expire-fade") { durationTicks = 8; radius = literal(0.5); arcaneCommon() } } }
        cancel { emission("cancel", -1) { burst("cancel-burst") { durationTicks = 6; radius = literal(0.4); count = 8; arcaneCommon() } } }
    }

    private fun PrimitiveBuilder.arcaneCommon() { argb = 0xAAA060FF.toInt(); width = 0.12; density = 8; seed = 17; localOffset = AbilityVisualDefinition.Vec(0.0, 0.0, 0.0); yawRadians = 0.0 }

    @JvmStatic
    fun registerInto(registry: AbilityVisualRegistry) {
        registry.register(arcaneBurst())
        registry.bind(AbilityVisualBinding(ABILITY_ID, VISUAL_ID))
    }
}
