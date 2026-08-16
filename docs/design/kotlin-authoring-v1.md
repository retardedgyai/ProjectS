# Kotlin authoring layer v0.1

## Boundary

This is a compiled Kotlin/JVM DSL for authoring content only. It constructs the existing Java `AbilityDefinition`, `AbilityVisualDefinition`, `AbilityVisualBinding`, and concrete combat-shape classes directly. Java models, registries, and runtime remain authoritative; the DSL adds no wrappers, registry, reflection discovery, dynamic compilation, scripts, or reload mechanism.

Kotlin production sources are deliberately confined to `src/main/kotlin/io/github/gyai/projects/authoring`. The artifact contains compiled classes only. `.kts` remains a Gradle build concern, not content authoring.

## Grammar and validation

`ability(id, displayName) { circleTelegraph { ... }; wait(...); damage { ... } }` preserves declaration order. Damage accepts `tags(...)` and `elements(...)` convenience input, plus `metadata(AttackMetadata)` as an existing-type escape hatch. `AbilityRuntime.standardActions().validate(...)` is invoked after the Java record is constructed, so core numeric and action rules have one owner.

`visual(id) { cast { emission(...) { circle(...) { ... } } } }` supports CAST, TELEGRAPH, HIT, EXPIRE, and CANCEL. It provides POINT, LINE, ARC, CIRCLE, CONE, SPIRAL, SPHERE, WAVE, BEZIER, and BURST builders. Shared timing/render options live on the common primitive builder; each typed builder exposes only the scalar slots accepted by its primitive. `literal`, `actionRadius`, `actionInnerRadius`, `actionWidth`, and `actionLength` return the existing Java scalar values.

`CombatShapeAuthoring` returns existing `SphereShape`, `UprightCylinderShape`, `HorizontalRingShape`, `AxisAlignedBoxShape`, `LineShape`, and `ConeShape` values. Their Java constructors retain all geometry validation.

## Runtime packaging

Kotlin 2.4.10 is compiled for JVM 25 alongside Java 25. A dedicated, resolvable, non-consumable, non-transitive `embeddedKotlinRuntime` configuration resolves exactly `kotlin-stdlib:2.4.10`; its complete contents (including core `kotlin.reflect` interfaces, but never the separate `kotlin-reflect` implementation) are merged into the conventional ProjectS JAR. The build never embeds `runtimeClasspath`.

The JAR task fails on duplicates rather than silently excluding them, while excluding only dependency manifest/signature metadata. `inspectKotlinAuthoringJar` verifies the resolved stdlib provenance, requires Kotlin `Intrinsics` and `KClass`, rejects Bukkit/Paper/compiler/Gradle-plugin and full reflection implementation namespaces, and rejects duplicate ProjectS entries. A standalone Java smoke test uses only Java test output plus the plugin JAR; a scoped `jdeps` check confirms authored Kotlin classes have no unresolved Kotlin dependencies.

## Migration and non-goals

Dev Shared Arcane Burst is the sole v0.1 migration. `DevAbilityDefinitions` and `DevAbilityVisuals` retain their Java public constants and methods, delegating to the Java-friendly Kotlin `DevArcaneBurstAuthoring` facade; values, order, hooks, and the telegraph `ActionField.RADIUS` binding are unchanged.

This aligns LEPI-004 with a compiled authoring surface while retaining KERNEL-001's authoritative Java boundary and KERNEL-006's deterministic immutable definitions. It intentionally excludes runtime scripting, hot reload, KSP/kapt, coroutines, serialization, Kotlin runtime registries/model wrappers, runtime-core rewrites, gameplay migration beyond Dev Arcane Burst, and Client changes.
