# Ability Runtime v0.1

## Purpose and authority

The Ability Runtime provides one small, data-driven execution path for abilities that must be usable by more than one kind of source. It keeps hit selection, lifecycle, timing, telegraphing, and damage authoritative on the server. It does not move balance, cooldown, status, or client decisions to the client.

## Domain model

`AbilityDefinition` is immutable, Bukkit-free data: supported schema version, repository-compatible namespaced id, display/debug name, and ordered immutable action specifications. `AbilityCastContext` carries a cast UUID, ability id, source entity reference, `SourceKind`, immutable origin value, optional primary target reference, and immutable runtime metadata. `SourceKind` supports `PLAYER` and `MOB` now, with explicit `BOSS`, `WEAPON`, and `ENVIRONMENT` future boundaries.

`TargetSelector` resolves `SELF` and `PRIMARY_TARGET` consistently. The action model uses stable data ids rather than Java class names. The `ActionRegistry` maps `wait`, `telegraph.circle`, and `damage` to validators/executors and rejects unknown ids, schema errors, invalid selectors, negative waits, invalid telegraph radius/duration, and malformed damage before registration/execution.

## Timeline and lifecycle

`AbilityRuntime` is the only scheduling boundary. Executors never call a Bukkit scheduler or sleep. A cast owns all scheduled callbacks and telegraph handles. Its lifecycle is `CREATED`, `RUNNING`, `COMPLETED`, `CANCELLED`, or `FAILED`; terminal casts leave active tracking and release owned references.

Invalid/removed source, missing or invalid required primary target, explicit cancellation, scheduler callback failure, and plugin shutdown cancel remaining callbacks and telegraph handles before cleanup. Callback failures are contained by the runtime so another same-due-tick continuation cannot reach damage. A completed cast releases references but does not cancel an already detonated telegraph. Duplicate active cast UUIDs are rejected.

## v0.1 actions and shared dev ability

The v0.1 action set is a tick `wait`, a circle telegraph with target and origin selectors/radius/duration/lock behavior, and damage with selector, type, kind, fixed amount, coefficient, critical policy, and immutable `AttackMetadata` intent. Circle duration is 1 through 1,199 ticks; the effective maximum reserves TelegraphRequest's final post-detonation expiry tick.

The registered development definition is exactly `projects:dev-shared-arcane-burst`. Its ordered values are: a fixed, locked circle centered on `PRIMARY_TARGET` (radius `3.0`, duration `20` ticks), `wait(20)`, then `PRIMARY_TARGET` damage with `MAGICAL`, `DIRECT_SKILL`, fixed `12.0`, coefficient `0.5`, critical enabled, and `MAGIC` plus `SKILL` tags. Telegraph detonation is scheduled before the wait continuation, so it runs first when both become due on tick 20.

## Shared Player and Mob operation

`DevAbilityService` owns one `AbilityRegistry` and one registered definition object. `/projects ability player` (permission `projects.dev`) acquires the sender's raycast living target and casts from the sender. `/projects ability mob` uses the sender's sight-targeted Editor Mob as source and the sender as primary target. Both retrieve and pass the same definition instance/id through the same runtime, selectors, ordered executors, lifecycle, and scheduler.

Paper interaction is isolated in `BukkitAbilityRuntime`. It reuses `TelegraphManager#create` and returns a cast-owned handle that detonates/cancels through the existing manager; no telegraph engine or client protocol is added. The Player damage adapter builds the existing `DamageRequest` with cast id, magical/direct-skill inputs, and `AttackMetadata`. The Editor Mob adapter reads the existing editor stats and calls `DamageService.applyMobAbility`; the old `applyMob` basic attack delegates its original `NORMAL_ATTACK` inputs through the same calculation seam. `DamageService` remains the sole direct Bukkit damage application boundary.

## Forward boundary and migration

The public registry lookup/list boundary is intentionally suitable for a v0.2 Mob Editor `abilityIds` integration. Mob definition schema and editor UI are deliberately deferred. Existing skills, bosses, and Mob basic attacks remain on their current paths; later migrations should be incremental and characterization-tested rather than broad replacement work.

Kotlin scripting was not adopted: v0.1 requires static Java validation, predictable server-side execution, and no runtime script loading or new dependency surface. ProjectS-Client is unchanged and no protocol messages were added.

## Current limitations and residual risk

This version supplies only a dev command and no Mob AI rotation, cooldown system, production UI, persistence, area selection, or migration of legacy abilities. Paper scheduling remains the adapter implementation (not Folia). Telegraph timing is deterministic within the registered scheduler ordering, while normal server tick health still governs actual execution timing.
