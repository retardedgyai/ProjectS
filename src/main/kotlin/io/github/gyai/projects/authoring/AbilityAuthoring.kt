package io.github.gyai.projects.authoring

import io.github.gyai.projects.ability.AbilityDefinition
import io.github.gyai.projects.ability.AbilityRuntime
import io.github.gyai.projects.ability.TargetSelector
import io.github.gyai.projects.combat.damage.AttackMetadata
import io.github.gyai.projects.combat.damage.AttackTag
import io.github.gyai.projects.combat.damage.DamageKind
import io.github.gyai.projects.combat.damage.DamageType
import io.github.gyai.projects.combat.damage.ElementProfile

/** Marks the compiled authoring scopes and prevents accidental receiver mixing. */
@DslMarker
annotation class AuthoringDsl

/** Builds the authoritative Java ability record and validates it with the canonical action registry. */
fun ability(id: String, displayName: String, block: AbilityBuilder.() -> Unit): AbilityDefinition {
    val builder = AbilityBuilder()
    builder.block()
    return builder.build(id, displayName)
}

@AuthoringDsl
class AbilityBuilder internal constructor() {
    private val actions = mutableListOf<AbilityDefinition.ActionSpec>()

    fun wait(ticks: Int) {
        actions += AbilityDefinition.Wait(ticks)
    }

    fun circleTelegraph(block: CircleTelegraphBuilder.() -> Unit) {
        val builder = CircleTelegraphBuilder()
        builder.block()
        actions += builder.build()
    }

    fun damage(block: DamageBuilder.() -> Unit) {
        val builder = DamageBuilder()
        builder.block()
        actions += builder.build()
    }

    internal fun build(id: String, displayName: String): AbilityDefinition {
        val result = AbilityDefinition(AbilityDefinition.SCHEMA_VERSION, id, displayName, actions.toList())
        // Keep numeric and action semantics in the Java runtime's one canonical validator.
        AbilityRuntime.standardActions().validate(result)
        return result
    }
}

@AuthoringDsl
class CircleTelegraphBuilder internal constructor() {
    var target: TargetSelector? = null
    var origin: TargetSelector? = null
    var radius: Double = 0.0
    var durationTicks: Int = 0
    var lockAtCreation: Boolean = true

    internal fun build() = AbilityDefinition.CircleTelegraph(
        target, origin, radius, durationTicks, lockAtCreation)
}

@AuthoringDsl
class DamageBuilder internal constructor() {
    var target: TargetSelector? = null
    var damageType: DamageType? = null
    var damageKind: DamageKind? = null
    var fixedDamage: Double = 0.0
    var coefficient: Double = 0.0
    var criticalAllowed: Boolean = true
    private var tagsValue: Set<AttackTag> = emptySet()
    private var elementsValue: ElementProfile = ElementProfile.EMPTY
    private var metadataOverride: AttackMetadata? = null

    /** Ergonomic immutable tag input; [metadata] remains available for existing-type escape hatches. */
    fun tags(vararg values: AttackTag) {
        tagsValue = values.toSet()
    }

    fun tags(values: Set<AttackTag>) {
        tagsValue = values.toSet()
    }

    fun elements(value: ElementProfile) {
        elementsValue = value
    }

    fun metadata(value: AttackMetadata) {
        metadataOverride = value
    }

    internal fun build() = AbilityDefinition.Damage(
        target, damageType, damageKind, fixedDamage, coefficient, criticalAllowed,
        metadataOverride ?: AttackMetadata(tagsValue, elementsValue))
}

/** Java-friendly entry point for callers that want canonical validation without Kotlin receivers. */
object AbilityAuthoring {
    @JvmStatic
    fun validate(definition: AbilityDefinition): AbilityDefinition {
        AbilityRuntime.standardActions().validate(definition)
        return definition
    }
}
