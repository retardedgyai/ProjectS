package io.github.gyai.projects.authoring

import io.github.gyai.projects.ability.AbilityDefinition
import io.github.gyai.projects.ability.AbilityLifecycleEvent
import io.github.gyai.projects.ability.AbilityVisualBinding
import io.github.gyai.projects.ability.AbilityVisualDefinition
import io.github.gyai.projects.ability.DevAbilityDefinitions
import io.github.gyai.projects.ability.DevAbilityVisuals
import io.github.gyai.projects.ability.TargetSelector
import io.github.gyai.projects.combat.damage.AttackMetadata
import io.github.gyai.projects.combat.damage.AttackTag
import io.github.gyai.projects.combat.damage.DamageElement
import io.github.gyai.projects.combat.damage.DamageKind
import io.github.gyai.projects.combat.damage.DamageType
import io.github.gyai.projects.combat.damage.ElementProfile
import io.github.gyai.projects.combat.shape.Aabb
import io.github.gyai.projects.combat.shape.AxisAlignedBoxShape
import io.github.gyai.projects.combat.shape.ConeShape
import io.github.gyai.projects.combat.shape.CombatShape
import io.github.gyai.projects.combat.shape.HorizontalRingShape
import io.github.gyai.projects.combat.shape.LineShape
import io.github.gyai.projects.combat.shape.SphereShape
import io.github.gyai.projects.combat.shape.UprightCylinderShape
import io.github.gyai.projects.combat.shape.Vec3
import java.nio.file.Files
import java.nio.file.Path

/** Lightweight executable contract for the compiled Kotlin authoring boundary. */
object KotlinAuthoringFoundationTest {
    @JvmStatic
    fun main(args: Array<String>) {
        abilityParityAndValidation()
        visualParityAndTypedPrimitives()
        shapesAndArchitecture()
    }

    private fun abilityParityAndValidation() {
        val actual = DevAbilityDefinitions.sharedArcaneBurst()
        val expected = AbilityDefinition(1, DevAbilityDefinitions.SHARED_ARCANE_BURST_ID, "Dev Shared Arcane Burst", listOf(
            AbilityDefinition.CircleTelegraph(TargetSelector.PRIMARY_TARGET, TargetSelector.PRIMARY_TARGET, 3.0, 20, true),
            AbilityDefinition.Wait(20),
            AbilityDefinition.Damage(TargetSelector.PRIMARY_TARGET, DamageType.MAGICAL, DamageKind.DIRECT_SKILL, 12.0, 0.5, true,
                AttackMetadata(setOf(AttackTag.MAGIC, AttackTag.SKILL), ElementProfile.EMPTY))))
        check(actual == expected)
        check(actual.steps().map { it.actionId() } == listOf("telegraph.circle", "wait", "damage"))
        expect { ability("projects:invalid", "Invalid") { wait(-1) } }
        expect { ability("invalid", "Invalid") { wait(1) } }
        expect { ability("projects:blank", " ") { wait(1) } }
        expect { ability("projects:missing", "Missing") { circleTelegraph { radius = 1.0; durationTicks = 1 } } }
        expect { ability("projects:damage", "Damage") { damage { target = TargetSelector.SELF; damageType = DamageType.MAGICAL; damageKind = DamageKind.DIRECT_SKILL; fixedDamage = -1.0 } } }
        val profile = ElementProfile(mapOf(DamageElement.FIRE to 2.0), mapOf(DamageElement.FIRE to 0.5))
        val metadata = AttackMetadata(setOf(AttackTag.FIRE), profile)
        val escaped = ability("projects:escaped", "Escaped") { damage {
            target = TargetSelector.SELF; damageType = DamageType.MAGICAL; damageKind = DamageKind.DIRECT_SKILL
            metadata(metadata)
        } }
        check((escaped.steps().single() as AbilityDefinition.Damage).metadata() == metadata)
        val tagged = ability("projects:tagged", "Tagged") { damage {
            target = TargetSelector.SELF; damageType = DamageType.MAGICAL; damageKind = DamageKind.DIRECT_SKILL
            tags(AttackTag.MAGIC, AttackTag.SKILL); elements(profile)
        } }
        check((tagged.steps().single() as AbilityDefinition.Damage).metadata().tags() == setOf(AttackTag.MAGIC, AttackTag.SKILL))
    }

    private fun visualParityAndTypedPrimitives() {
        val actual = DevAbilityVisuals.arcaneBurst()
        val expected = AbilityVisualDefinition(1, DevAbilityVisuals.ARCANE_BURST_VISUAL_ID, listOf(
            hook(AbilityLifecycleEvent.Hook.CAST, emission("cast", -1,
                primitive("cast-spiral", AbilityVisualDefinition.PrimitiveType.SPIRAL, 2, 12, radius = literal(1.2), height = literal(1.0), turns = literal(1.0)),
                primitive("cast-sphere", AbilityVisualDefinition.PrimitiveType.SPHERE, 0, 10, radius = literal(0.7)))),
            hook(AbilityLifecycleEvent.Hook.TELEGRAPH, emission("telegraph", 0,
                primitive("telegraph-circle", AbilityVisualDefinition.PrimitiveType.CIRCLE, 0, 20, radius = actionRadius()))),
            hook(AbilityLifecycleEvent.Hook.HIT, emission("hit", 2,
                primitive("hit-burst", AbilityVisualDefinition.PrimitiveType.BURST, 0, 10, radius = literal(1.0), count = 12))),
            hook(AbilityLifecycleEvent.Hook.EXPIRE, emission("expire", -1,
                primitive("expire-fade", AbilityVisualDefinition.PrimitiveType.SPHERE, 0, 8, radius = literal(0.5)))),
            hook(AbilityLifecycleEvent.Hook.CANCEL, emission("cancel", -1,
                primitive("cancel-burst", AbilityVisualDefinition.PrimitiveType.BURST, 0, 6, radius = literal(0.4), count = 8)))
        ))
        check(actual == expected)
        check(actual.id() == DevAbilityVisuals.ARCANE_BURST_VISUAL_ID)
        check(actual.bindings().map { it.hook() } == listOf(AbilityLifecycleEvent.Hook.CAST, AbilityLifecycleEvent.Hook.TELEGRAPH, AbilityLifecycleEvent.Hook.HIT, AbilityLifecycleEvent.Hook.EXPIRE, AbilityLifecycleEvent.Hook.CANCEL))
        val telegraph = actual.emissions()[AbilityLifecycleEvent.Hook.TELEGRAPH]!!.single().primitives().single()
        check(telegraph.radius() == AbilityVisualDefinition.ActionField.RADIUS)

        val control = mutableListOf(AbilityVisualDefinition.Vec(0.0, 0.0, 0.0), AbilityVisualDefinition.Vec(1.0, 0.0, 0.0), AbilityVisualDefinition.Vec(2.0, 0.0, 0.0))
        val all = visual("projects:vfx/all") {
            onCast { emission("all") {
                point("point") { size = literal(1.0) }
                line("line") { length = literal(1.0) }
                arc("arc") { radius = literal(1.0); sweepAngle = literal(1.0) }
                circle("circle") { radius = actionRadius }
                cone("cone") { length = literal(1.0); angle = literal(1.0) }
                spiral("spiral") { radius = literal(1.0); height = literal(0.0); turns = literal(1.0) }
                sphere("sphere") { radius = literal(1.0) }
                wave("wave") { length = literal(1.0); radius = literal(1.0); height = literal(0.0) }
                bezier("bezier") { controlPoints = control }
                burst("burst") { radius = literal(1.0); count = 1 }
            } }
        }
        control += AbilityVisualDefinition.Vec(3.0, 0.0, 0.0)
        val primitives = all.bindings().single().emissions().single().primitives()
        check(primitives.map { it.type() } == AbilityVisualDefinition.PrimitiveType.entries)
        check(primitives.single { it.type() == AbilityVisualDefinition.PrimitiveType.BEZIER }.controlPoints().size == 3)
        check(literal(3.0) == AbilityVisualDefinition.Literal(3.0))
        check(actionInnerRadius() == AbilityVisualDefinition.ActionField.INNER_RADIUS)
        check(actionWidth() == AbilityVisualDefinition.ActionField.WIDTH)
        check(actionLength() == AbilityVisualDefinition.ActionField.LENGTH)
        check(actionRadius == AbilityVisualDefinition.ActionField.RADIUS)
        check(visualBinding("projects:ability", "projects:vfx/visual") ==
            AbilityVisualBinding("projects:ability", "projects:vfx/visual"))
        expect { visual("projects:vfx/bad") { hook(AbilityLifecycleEvent.Hook.TRAVEL) { } } }
        expect { visual("projects:vfx/bad-slot") { cast { emission("bad") { circle("circle") { radius = literal(0.0) } } } } }
        expect { visual("bad") { cast { emission("valid") { sphere("sphere") { radius = literal(1.0) } } } } }
        expect { visual("projects:vfx/missing") { cast { emission("missing") { sphere("sphere") { } } } } }
        expect { visual("projects:vfx/duration") { cast { emission("duration") { sphere("sphere") { durationTicks = 1201; radius = literal(1.0) } } } } }
        expect { visual("projects:vfx/duplicate-primitive") { cast { emission("duplicate") { sphere("same") { radius = literal(1.0) }; sphere("same") { radius = literal(1.0) } } } } }
        expect { visual("projects:vfx/duplicate-hook") { cast { emission("first") { sphere("a") { radius = literal(1.0) } } }; cast { emission("second") { sphere("b") { radius = literal(1.0) } } } } }
        expect { visual("projects:vfx/many") { cast { emission("many") { repeat(17) { sphere("p$it") { radius = literal(1.0) } } } } } }
        val namedHooks = visual("projects:vfx/named-hooks") {
            onCast { emission("cast") { sphere("sphere") { radius = literal(1.0) } } }
            onTelegraph { emission("telegraph") { sphere("sphere") { radius = literal(1.0) } } }
            onHit { emission("hit") { sphere("sphere") { radius = literal(1.0) } } }
            onExpire { emission("expire") { sphere("sphere") { radius = literal(1.0) } } }
            onCancel { emission("cancel") { sphere("sphere") { radius = literal(1.0) } } }
        }
        check(namedHooks.bindings().size == 5)
    }

    private fun shapesAndArchitecture() {
        val origin = Vec3(0.0, 0.0, 0.0)
        val sphere = CombatShapeAuthoring.sphere(origin, 1.0); val directSphere = SphereShape(origin, 1.0)
        val cylinder = CombatShapeAuthoring.uprightCylinder(origin, 1.0, 1.0); val directCylinder = UprightCylinderShape(origin, 1.0, 1.0)
        val ring = CombatShapeAuthoring.horizontalRing(origin, 0.5, 1.0, 1.0); val directRing = HorizontalRingShape(origin, 0.5, 1.0, 1.0)
        val box = CombatShapeAuthoring.axisAlignedBox(Aabb(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0)); val directBox = AxisAlignedBoxShape(Aabb(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0))
        val line = CombatShapeAuthoring.line(origin, Vec3(1.0, 0.0, 0.0), 0.1); val directLine = LineShape(origin, Vec3(1.0, 0.0, 0.0), 0.1)
        val cone = CombatShapeAuthoring.cone(origin, Vec3(1.0, 0.0, 0.0), 1.0, 0.5); val directCone = ConeShape(origin, Vec3(1.0, 0.0, 0.0), 1.0, 0.5)
        check(sphere == directSphere && cylinder == directCylinder && ring == directRing && box == directBox && line == directLine)
        check(cone.origin() == directCone.origin() && cone.forward() == directCone.forward() && cone.length() == directCone.length() && cone.halfAngleRadians() == directCone.halfAngleRadians())
        val shapes: List<CombatShape> = listOf(sphere, cylinder, ring, box, line, cone)
        check(shapes[0] is SphereShape && shapes[1] is UprightCylinderShape && shapes[2] is HorizontalRingShape && shapes[3] is AxisAlignedBoxShape && shapes[4] is LineShape && shapes[5] is ConeShape)
        val target = Aabb(-0.2, -0.2, -0.2, 0.2, 0.2, 0.2)
        listOf(sphere to directSphere, cylinder to directCylinder, ring to directRing, box to directBox, line to directLine, cone to directCone).forEach { (factory, direct) -> check(factory.broadPhaseBounds() == direct.broadPhaseBounds()); check(factory.intersects(target) == direct.intersects(target)) }
        val kotlinRoot = Path.of("src/main/kotlin").toAbsolutePath().normalize()
        val productionKotlin = kotlinRoot.resolve("io/github/gyai/projects/authoring")
        check(Files.exists(productionKotlin))
        check(Files.walk(kotlinRoot).use { paths -> paths.filter { Files.isRegularFile(it) }.allMatch { it.toString().endsWith(".kt") && it.toAbsolutePath().normalize().startsWith(productionKotlin) } })
        val productionFiles = Files.walk(Path.of("src/main")).use { paths -> paths.filter { Files.isRegularFile(it) }.toList() }
        check(productionFiles.none { it.toString().endsWith(".kts") })
        val forbidden = listOf("ScriptEngine", "javax.script", "kotlin.script", "KotlinCompiler", "URLClassLoader", "getResources(")
        check(productionFiles.none { path -> val text = Files.readString(path); forbidden.any(text::contains) })
        val coreJava = listOf("src/main/java/io/github/gyai/projects/ability/AbilityRuntime.java", "src/main/java/io/github/gyai/projects/ability/AbilityDefinition.java", "src/main/java/io/github/gyai/projects/ability/AbilityVisualDefinition.java", "src/main/java/io/github/gyai/projects/combat/damage/DamageService.java", "src/main/java/io/github/gyai/projects/combat/shape/CombatShape.java")
        check(coreJava.none { Files.readString(Path.of(it)).contains("authoring") })
        check(DevAbilityDefinitions.SHARED_ARCANE_BURST_ID == DevArcaneBurstAuthoring.ABILITY_ID)
        check(DevAbilityVisuals.ARCANE_BURST_VISUAL_ID == DevArcaneBurstAuthoring.VISUAL_ID)
    }

    private inline fun expect(block: () -> Unit) {
        try { block(); error("Expected IllegalArgumentException") } catch (_: IllegalArgumentException) { }
    }

    private fun primitive(id: String, type: AbilityVisualDefinition.PrimitiveType, delay: Int, duration: Int,
                          size: AbilityVisualDefinition.Scalar? = null, radius: AbilityVisualDefinition.Scalar? = null,
                          length: AbilityVisualDefinition.Scalar? = null, height: AbilityVisualDefinition.Scalar? = null,
                          angle: AbilityVisualDefinition.Scalar? = null, start: AbilityVisualDefinition.Scalar? = null,
                          sweep: AbilityVisualDefinition.Scalar? = null, turns: AbilityVisualDefinition.Scalar? = null,
                          count: Int = 0): AbilityVisualDefinition.PrimitiveSpec =
        AbilityVisualDefinition.PrimitiveSpec(id, type, delay, duration, 0xAAA060FF.toInt(), 0.12, 8, 17,
            AbilityVisualDefinition.Vec(0.0, 0.0, 0.0), 0.0, size, radius, length, height, angle, start, sweep, turns, count, emptyList())

    private fun emission(id: String, actionIndex: Int, vararg primitives: AbilityVisualDefinition.PrimitiveSpec) =
        AbilityVisualDefinition.Emission(id, actionIndex, primitives.toList())

    private fun hook(hook: AbilityLifecycleEvent.Hook, vararg emissions: AbilityVisualDefinition.Emission) =
        AbilityVisualDefinition.HookBinding(hook, emissions.toList())
}
